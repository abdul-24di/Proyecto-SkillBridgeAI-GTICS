package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.dto.CursoDisponibleView;
import com.pucp.skillb_ia.model.ColaboradorCurso;
import com.pucp.skillb_ia.model.Curso;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoColaboradorCurso;
import com.pucp.skillb_ia.model.enums.OrigenCurso;
import com.pucp.skillb_ia.repository.ColaboradorCursoRepository;
import com.pucp.skillb_ia.repository.CursoRepository;
import com.pucp.skillb_ia.service.ArchivoAlmacenamientoService;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.NotificacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;


@Service
public class ColaboradorCursoService {

    //Mientras el colaborador tenga una solicitud pendiente, una inscripción activa o una
    //evidencia en revisión para un curso, no puede volver a solicitarlo.
    private static final List<EstadoColaboradorCurso> ESTADOS_QUE_BLOQUEAN =
            List.of(EstadoColaboradorCurso.SOLICITADO, EstadoColaboradorCurso.EN_CURSO, EstadoColaboradorCurso.EVIDENCIA_PENDIENTE);

    //Para la evidencia de finalización del curso
    private static final Set<String> TIPOS_EVIDENCIA_PERMITIDOS = Set.of("application/pdf", "image/jpeg", "image/png");
    private static final long TAMANO_MAXIMO_EVIDENCIA_BYTES = 10L * 1024 * 1024; // 10 MB

    private final CursoRepository cursoRepository;
    private final ColaboradorCursoRepository colaboradorCursoRepository;
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;
    private final ArchivoAlmacenamientoService archivoAlmacenamientoService;

    public ColaboradorCursoService(CursoRepository cursoRepository,
                                   ColaboradorCursoRepository colaboradorCursoRepository,
                                   AuditoriaService auditoriaService,
                                   NotificacionService notificacionService,
                                   ArchivoAlmacenamientoService archivoAlmacenamientoService) {
        this.cursoRepository = cursoRepository;
        this.colaboradorCursoRepository = colaboradorCursoRepository;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
        this.archivoAlmacenamientoService = archivoAlmacenamientoService;
    }

    // ============================================================
    // CATÁLOGO DE CURSOS DISPONIBLES (activos del Admin)
    // ============================================================
    @Transactional(readOnly = true)
    public List<CursoDisponibleView> listarCursosDisponibles(Usuario colaborador) {
        List<Curso> cursosActivos = cursoRepository.findByActivoTrueOrderByNombreAsc();
        List<ColaboradorCurso> misRegistros = colaboradorCursoRepository.findByColaborador(colaborador);
        List<CursoDisponibleView> resultado = new ArrayList<>();

        for (Curso curso : cursosActivos) {
            //Buscamos si el colaborador ya solicitó este curso antes, y nos quedamos con el registro más reciente para saber su estado actual.
            ColaboradorCurso masReciente = null;
            for (ColaboradorCurso registro : misRegistros) {
                if (!registro.getCurso().getId().equals(curso.getId())) {
                    continue;
                }
                //Si no existe un registro más reciente o el registro actual tiene una fecha de solicitud posterior,
                //se actualiza masReciente con el registro actual.
                if (masReciente == null || registro.getFechaSolicitud().isAfter(masReciente.getFechaSolicitud())) {
                    masReciente = registro;
                }
            }

            boolean puedeSolicitar = true;
            String estadoTexto = null;
            String estadoClase = null;

            if (masReciente != null) {
                estadoTexto = textoEstado(masReciente.getEstado());
                estadoClase = claseEstado(masReciente.getEstado());
                if (masReciente.getEstado() == EstadoColaboradorCurso.SOLICITADO
                        || masReciente.getEstado() == EstadoColaboradorCurso.EN_CURSO) {
                    puedeSolicitar = false;
                }
            }

            resultado.add(new CursoDisponibleView(curso, puedeSolicitar, estadoTexto, estadoClase));
        }
        return resultado;
    }

    // ============================================================
    // CATEGORÍAS DISPONIBLES
    // ============================================================
    @Transactional(readOnly = true)
    public List<String> listarCategorias() {
        List<Curso> cursosActivos = cursoRepository.findByActivoTrueOrderByNombreAsc();
        List<String> categorias = new ArrayList<>();

        for (Curso curso : cursosActivos) {
            String categoria = curso.getCategoria();
            if (categoria == null || categoria.isBlank()) {
                continue;
            }
            if (!categorias.contains(categoria)) {
                categorias.add(categoria);
            }
        }
        return categorias;
    }

    // ============================================================
    // SOLICITUDES DE CURSO ENVIADAS POR EL COLABORADOR
    // ============================================================
    @Transactional(readOnly = true)
    public List<ColaboradorCurso> listarMisSolicitudes(Usuario colaborador) {
        List<ColaboradorCurso> todosMisRegistros = colaboradorCursoRepository.findByColaborador(colaborador);
        List<ColaboradorCurso> misSolicitudes = new ArrayList<>();

        for (ColaboradorCurso registro : todosMisRegistros) {
            if (registro.getOrigen() == OrigenCurso.SOLICITUD_COLABORADOR) {
                misSolicitudes.add(registro);
            }
        }

        //Listamos de la más reciente a la más antigua
        misSolicitudes.sort(Comparator.comparing(ColaboradorCurso::getFechaSolicitud).reversed());
        return misSolicitudes;
    }

    // ============================================================
    // COLABORADOR SOLICITA INSCRIPCIÓN A UN CURSO
    // ============================================================
    @Transactional
    public void solicitarInscripcion(Usuario colaborador, Long cursoId, String justificacion) {
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> new IllegalArgumentException("El curso seleccionado no existe."));

        if (!curso.isActivo()) {
            throw new IllegalArgumentException("Este curso ya no está disponible.");
        }

        boolean yaTieneSolicitud = colaboradorCursoRepository.existsByColaboradorAndCursoAndEstadoIn(
                colaborador, curso, ESTADOS_QUE_BLOQUEAN);
        if (yaTieneSolicitud) {
            throw new IllegalArgumentException(
                    "Ya tienes una solicitud pendiente o una inscripción activa en este curso.");
        }

        ColaboradorCurso solicitud = new ColaboradorCurso();
        solicitud.setColaborador(colaborador);
        solicitud.setCurso(curso);
        solicitud.setOrigen(OrigenCurso.SOLICITUD_COLABORADOR);
        solicitud.setEstado(EstadoColaboradorCurso.SOLICITADO);
        solicitud.setJustificacion(validarJustificacion(justificacion));
        ColaboradorCurso guardada = colaboradorCursoRepository.save(solicitud);

        auditoriaService.registrar(colaborador, "SOLICITAR_CURSO", "COLABORADOR_CURSO", guardada.getId(),
                "Solicitó inscribirse al curso \"" + curso.getNombre() + "\".");

        notificacionService.crearParaTodosLosRm("CURSO_SOLICITADO", CategoriaNotificacion.CURSO,
                "Nueva solicitud de curso",
                colaborador.getNombre() + " " + colaborador.getApellido()
                        + " solicitó inscribirse al curso \"" + curso.getNombre() + "\".",
                "COLABORADOR_CURSO", guardada.getId());
    }

    // ============================================================
    // MIS CURSOS (perfil profesional). Listamos los cursos que se están llevando, con evidencia enviada o completados
    // ============================================================
    @Transactional(readOnly = true)
    public List<ColaboradorCurso> listarMisCursos(Usuario colaborador) {
        List<ColaboradorCurso> todos = colaboradorCursoRepository.findByColaborador(colaborador);
        List<ColaboradorCurso> misCursos = new ArrayList<>();

        for (ColaboradorCurso registro : todos) {
            if (registro.getEstado() == EstadoColaboradorCurso.EN_CURSO
                    || registro.getEstado() == EstadoColaboradorCurso.EVIDENCIA_PENDIENTE
                    || registro.getEstado() == EstadoColaboradorCurso.COMPLETADO) {
                misCursos.add(registro);
            }
        }

        //Listamos del más reciente al más antiguo.
        misCursos.sort(Comparator.comparing(ColaboradorCurso::getFechaSolicitud).reversed());
        return misCursos;
    }

    // ============================================================
    // COLABORADOR SUBE LA EVIDENCIA DE FINALIZACIÓN DE UN CURSO
    // ============================================================
    @Transactional
    public void subirEvidencia(Usuario colaborador, Long inscripcionId, MultipartFile evidencia) {
        ColaboradorCurso inscripcion = colaboradorCursoRepository.findByIdAndColaborador(inscripcionId, colaborador).orElseThrow(() -> new IllegalArgumentException("No se encontró esa inscripción a un curso."));

        if (inscripcion.getEstado() != EstadoColaboradorCurso.EN_CURSO) {
            throw new IllegalArgumentException("Solo puedes subir evidencia de un curso que esté \"En curso\".");
        }
        if (evidencia == null || evidencia.isEmpty()) {
            throw new IllegalArgumentException("Debes adjuntar una evidencia (PDF, JPG o PNG).");
        }
        if (!TIPOS_EVIDENCIA_PERMITIDOS.contains(evidencia.getContentType())) {
            throw new IllegalArgumentException("La evidencia debe estar en formato PDF, JPG o PNG.");
        }
        if (evidencia.getSize() > TAMANO_MAXIMO_EVIDENCIA_BYTES) {
            throw new IllegalArgumentException("La evidencia supera el máximo de 10MB.");
        }

        String extension = switch (evidencia.getContentType()) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            default -> ".jpg";
        };

        String nombreArchivo = "evidencia-curso-" + colaborador.getId() + "-" + inscripcionId + "-" + UUID.randomUUID() + extension;
        String evidenciaUrl = archivoAlmacenamientoService.guardar(evidencia, "cursos-evidencia", nombreArchivo);

        inscripcion.setEvidenciaUrl(evidenciaUrl);
        inscripcion.setFechaEvidencia(LocalDateTime.now());
        inscripcion.setEstado(EstadoColaboradorCurso.EVIDENCIA_PENDIENTE);
        colaboradorCursoRepository.save(inscripcion);

        auditoriaService.registrar(colaborador, "SUBIR_EVIDENCIA_CURSO", "COLABORADOR_CURSO", inscripcion.getId(),
                "Subió evidencia de finalización del curso \"" + inscripcion.getCurso().getNombre() + "\".");

        notificacionService.crearParaTodosLosRm("EVIDENCIA_CURSO_PENDIENTE", CategoriaNotificacion.CURSO,
                "Evidencia de curso pendiente de revisión",
                colaborador.getNombre() + " " + colaborador.getApellido()
                        + " subió evidencia para el curso \"" + inscripcion.getCurso().getNombre() + "\".",
                "COLABORADOR_CURSO", inscripcion.getId());
    }

    // ============================================================
    // VALIDACIONES
    // ============================================================
    private String validarJustificacion(String justificacion) {
        //Colocamos que la justificación sea opcional,
        if (justificacion == null || justificacion.isBlank()) {
            return null;
        }
        String limpio = justificacion.trim();
        if (limpio.length() > 500) {
            throw new IllegalArgumentException("La justificación no puede superar los 500 caracteres.");
        }
        return limpio;
    }

    private String textoEstado(EstadoColaboradorCurso estado) {
        if (estado == EstadoColaboradorCurso.SOLICITADO) return "Solicitud pendiente";
        if (estado == EstadoColaboradorCurso.EN_CURSO) return "Inscrito";
        if (estado == EstadoColaboradorCurso.EVIDENCIA_PENDIENTE) return "Evidencia en revisión";
        if (estado == EstadoColaboradorCurso.COMPLETADO) return "Completado";
        if (estado == EstadoColaboradorCurso.RECHAZADO) return "Solicitud rechazada";
        return "";
    }

    private String claseEstado(EstadoColaboradorCurso estado) {
        if (estado == EstadoColaboradorCurso.SOLICITADO) return "bg-yellow-lt";
        if (estado == EstadoColaboradorCurso.EN_CURSO) return "bg-green-lt";
        if (estado == EstadoColaboradorCurso.EVIDENCIA_PENDIENTE) return "bg-purple-lt";
        if (estado == EstadoColaboradorCurso.COMPLETADO) return "bg-blue-lt";
        if (estado == EstadoColaboradorCurso.RECHAZADO) return "bg-red-lt";
        return "bg-secondary-lt";
    }
}