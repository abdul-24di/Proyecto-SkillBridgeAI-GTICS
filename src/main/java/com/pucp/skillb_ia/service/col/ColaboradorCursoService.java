package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.dto.ColBonoMensualView;
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
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;


@Service
public class ColaboradorCursoService {

    //Mientras el colaborador tenga una solicitud pendiente, una inscripción activa, una
    //evidencia en revisión, o ya haya completado el curso, no puede volver a solicitarlo.
    private static final List<EstadoColaboradorCurso> ESTADOS_QUE_BLOQUEAN =
            List.of(EstadoColaboradorCurso.SOLICITADO, EstadoColaboradorCurso.EN_CURSO,
                    EstadoColaboradorCurso.EVIDENCIA_PENDIENTE, EstadoColaboradorCurso.COMPLETADO);

    //Para la evidencia de finalización del curso
    private static final Set<String> TIPOS_EVIDENCIA_PERMITIDOS = Set.of("application/pdf", "image/jpeg", "image/png");
    private static final long TAMANO_MAXIMO_EVIDENCIA_BYTES = 10L * 1024 * 1024; // 10 MB

    private final CursoRepository cursoRepository;
    private final ColaboradorCursoRepository colaboradorCursoRepository;
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;
    private final ArchivoAlmacenamientoService archivoAlmacenamientoService;
    private final ColaboradorActividadService colaboradorActividadService;

    public ColaboradorCursoService(CursoRepository cursoRepository,
                                   ColaboradorCursoRepository colaboradorCursoRepository,
                                   AuditoriaService auditoriaService,
                                   NotificacionService notificacionService,
                                   ArchivoAlmacenamientoService archivoAlmacenamientoService,
                                   //@Lazy evita una dependencia circular entre los servicios,
                                   //haciendo que ColaboradorActividadService se inicialice cuando sea necesario.
                                   @Lazy ColaboradorActividadService colaboradorActividadService) {
        this.cursoRepository = cursoRepository;
        this.colaboradorCursoRepository = colaboradorCursoRepository;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
        this.archivoAlmacenamientoService = archivoAlmacenamientoService;
        this.colaboradorActividadService = colaboradorActividadService;
    }

    // ============================================================
    // CATÁLOGO DE CURSOS DISPONIBLES (activos del Admin)
    // ============================================================
    @Transactional
    public List<CursoDisponibleView> listarCursosDisponibles(Usuario colaborador) {
        marcarCursosSinEvidenciaVencidos(colaborador);
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

            // Un curso caducado (su fecha de fin ya pasó) no se ofrece como disponible; solo se sigue
            // mostrando si el colaborador ya tiene algo con él (para ver su estado o subir su evidencia).
            if (masReciente == null && curso.getFechaFin() != null
                    && curso.getFechaFin().isBefore(LocalDate.now())) {
                continue;
            }

            boolean puedeSolicitar = true;
            String estadoTexto = null;
            String estadoClase = null;

            if (masReciente != null) {
                estadoTexto = textoEstado(masReciente.getEstado());
                estadoClase = claseEstado(masReciente.getEstado());
                if (ESTADOS_QUE_BLOQUEAN.contains(masReciente.getEstado())) {
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
        if (curso.getFechaFin() != null && curso.getFechaFin().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Este curso ya terminó: su fecha de fin ya pasó.");
        }

        //Si ya llegó al tope de horas que cuentan para el bono este mes, un curso más no le
        //suma nada (esas horas no se pagarían de todas formas), así que no lo dejamos solicitar.
        ColBonoMensualView resumenBono = colaboradorActividadService.obtenerResumenBonoMensual(colaborador);
        if (resumenBono.getHorasExtraBonificables().compareTo(resumenBono.getTopeHorasExtra()) >= 0) {
            throw new IllegalArgumentException(
                    "No puedes solicitar este curso: ya alcanzaste el máximo de horas que cuentan para tu bono este mes, "
                            + "así que no te quedan horas disponibles para sumar.");
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
    // MIS CURSOS (perfil profesional). Listamos los cursos que se están llevando, con evidencia enviada,
    // completados o no completados (vencidos sin evidencia)
    // ============================================================
    @Transactional
    public List<ColaboradorCurso> listarMisCursos(Usuario colaborador) {
        marcarCursosSinEvidenciaVencidos(colaborador);

        List<ColaboradorCurso> todos = colaboradorCursoRepository.findByColaborador(colaborador);
        List<ColaboradorCurso> misCursos = new ArrayList<>();

        for (ColaboradorCurso registro : todos) {
            if (registro.getEstado() == EstadoColaboradorCurso.EN_CURSO
                    || registro.getEstado() == EstadoColaboradorCurso.EVIDENCIA_PENDIENTE
                    || registro.getEstado() == EstadoColaboradorCurso.COMPLETADO
                    || registro.getEstado() == EstadoColaboradorCurso.NO_COMPLETADO) {
                misCursos.add(registro);
            }
        }

        //Listamos del más reciente al más antiguo.
        misCursos.sort(Comparator.comparing(ColaboradorCurso::getFechaSolicitud).reversed());
        return misCursos;
    }

    // ============================================================
    // MARCAMOS COMO "NO COMPLETADO" LOS CURSOS (AUTODIDACTA O NO) "EN CURSO" CUYA FECHA FIN
    // VENCIÓ SIN EVIDENCIA. Al cambiar el estado, una segunda ejecución ya no los vuelve a tocar.
    // ============================================================
    public void marcarCursosSinEvidenciaVencidos(Usuario colaborador) {
        marcarSinEvidenciaVencidos(colaboradorCursoRepository.findByColaborador(colaborador));
    }

    //Misma regla para todos los colaboradores: el RM la ejecuta al abrir su dashboard y la bandeja de cursos,
    //así no ve "En curso" un curso vencido aunque el colaborador no haya entrado.
    @Transactional
    public void marcarTodosLosCursosSinEvidenciaVencidos() {
        marcarSinEvidenciaVencidos(colaboradorCursoRepository.findByEstado(EstadoColaboradorCurso.EN_CURSO));
    }

    private void marcarSinEvidenciaVencidos(List<ColaboradorCurso> inscripciones) {

        LocalDate hoy = LocalDate.now();

        for (ColaboradorCurso registro : inscripciones) {
            if (registro.getEstado() != EstadoColaboradorCurso.EN_CURSO) {
                continue;
            }

            Curso curso = registro.getCurso();

            //Sin fecha fin no hay plazo, el curso se queda "En curso" hasta que se suba su
            //evidencia, sea autodidacta o no.
            if (curso.getFechaFin() == null) {
                continue;
            }

            //Todavía no llega la fecha fin, así que sigue "En curso".
            if (curso.getFechaFin().isAfter(hoy)) {
                continue;
            }

            if (registro.getEvidenciaUrl() != null) {
                continue;
            }

            registro.setEstado(EstadoColaboradorCurso.NO_COMPLETADO);

            colaboradorCursoRepository.save(registro);

            Usuario colaborador = registro.getColaborador();

            auditoriaService.registrar(colaborador, "CURSO_NO_COMPLETADO", "COLABORADOR_CURSO", registro.getId(),
                    "El curso \"" + curso.getNombre() + "\" quedó como no completado: venció su fecha fin sin una evidencia aprobada.");

            notificacionService.crear(colaborador, "CURSO_NO_COMPLETADO", CategoriaNotificacion.CURSO,
                    "Curso no completado",
                    "El plazo del curso \"" + curso.getNombre() + "\" venció sin que se aprobara una evidencia. No se te aplicó ninguna penalización, pero tampoco se contaron sus horas.",
                    "COLABORADOR_CURSO", registro.getId());
        }
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
        boolean esPrimerIntento = inscripcion.getEvidenciaUrl() == null;
        //Misma regla que el paso a NO_COMPLETADO: el plazo vence el mismo día de la fecha fin (fechaFin <= hoy).
        if (esPrimerIntento && inscripcion.getCurso().getFechaFin() != null
                && !inscripcion.getCurso().getFechaFin().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("El plazo para subir evidencia de este curso ya venció.");
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
        // Al reenviar tras un rechazo, la revisión anterior deja de aplicar (queda en la auditoría).
        inscripcion.setEvidenciaRevisadaPor(null);
        inscripcion.setFechaRevisionEvidencia(null);
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
        if (estado == EstadoColaboradorCurso.NO_COMPLETADO) return "No completado";
        return "";
    }

    private String claseEstado(EstadoColaboradorCurso estado) {
        if (estado == EstadoColaboradorCurso.SOLICITADO) return "bg-yellow-lt";
        if (estado == EstadoColaboradorCurso.EN_CURSO) return "bg-green-lt";
        if (estado == EstadoColaboradorCurso.EVIDENCIA_PENDIENTE) return "bg-purple-lt";
        if (estado == EstadoColaboradorCurso.COMPLETADO) return "bg-blue-lt";
        if (estado == EstadoColaboradorCurso.RECHAZADO) return "bg-red-lt";
        if (estado == EstadoColaboradorCurso.NO_COMPLETADO) return "bg-secondary-lt";
        return "bg-secondary-lt";
    }
}