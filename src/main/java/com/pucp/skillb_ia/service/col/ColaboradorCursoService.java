package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.dto.ColBonoMensualView;
import com.pucp.skillb_ia.dto.CursoDisponibleView;
import com.pucp.skillb_ia.model.ColaboradorCurso;
import com.pucp.skillb_ia.model.Curso;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoColaboradorCurso;
import com.pucp.skillb_ia.model.enums.OrigenCurso;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.service.ArchivoAlmacenamientoService;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.NotificacionService;
import com.pucp.skillb_ia.service.RmRevisionAsignadorService;
import com.pucp.skillb_ia.util.Paginador;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;


/**
 * Lógica de los cursos y capacitaciones desde el lado del colaborador.
 * <p>
 * Cubre el catálogo de cursos disponibles, la solicitud de inscripción, la subida de la
 * evidencia de finalización y el control de los cursos que vencen sin evidencia. El Resource
 * Manager aprueba las solicitudes y revisa las evidencias; este service solo registra lo que
 * hace el colaborador.
 * <p>
 * Ciclo de vida de una inscripción ({@code EstadoColaboradorCurso}):
 * <pre>
 * SOLICITADO ──(RM aprueba)──► EN_CURSO ──(sube evidencia)──► EVIDENCIA_PENDIENTE ──(RM aprueba)──► COMPLETADO
 *     │                           │                                   │
 *     └─(RM rechaza)─► RECHAZADO  └─(vence sin evidencia)─► NO_COMPLETADO   └─(RM rechaza)─► vuelve a EN_CURSO
 * </pre>
 * Solo los cursos {@code COMPLETADO} suman horas a la meta mensual.
 */
@Service
public class ColaboradorCursoService {

    /**
     * Estados que impiden volver a solicitar el mismo curso: solicitud pendiente, inscripción en
     * curso, evidencia en revisión o curso ya completado. Con {@code RECHAZADO} o
     * {@code NO_COMPLETADO} sí puede volver a solicitarlo.
     */
    private static final List<EstadoColaboradorCurso> ESTADOS_QUE_BLOQUEAN =
            List.of(EstadoColaboradorCurso.SOLICITADO, EstadoColaboradorCurso.EN_CURSO,
                    EstadoColaboradorCurso.EVIDENCIA_PENDIENTE, EstadoColaboradorCurso.COMPLETADO);

    /** Clave del parámetro global con el máximo de asignaciones activas por colaborador. */
    private static final String CLAVE_MAX_ASIGNACIONES = "MAX_ASIGNACIONES_POR_COLABORADOR";
    /** Máximo de asignaciones activas si el parámetro no existe o no es válido: 3. */
    private static final int MAX_ASIGNACIONES_POR_DEFECTO = 3;

    /** Tipos MIME aceptados para la evidencia de finalización del curso: PDF, JPG y PNG. */
    private static final Set<String> TIPOS_EVIDENCIA_PERMITIDOS = Set.of("application/pdf", "image/jpeg", "image/png");
    public static final long TAMANO_MAXIMO_EVIDENCIA_BYTES = 10L * 1024 * 1024; // 10 MB

    /** Días antes de la fecha fin en los que se avisa, una sola vez, al colaborador que aún no sube evidencia. */
    private static final int DIAS_AVISO_FIN_CURSO = 3;

    private final CursoRepository cursoRepository;
    private final ColaboradorCursoRepository colaboradorCursoRepository;
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;
    private final ArchivoAlmacenamientoService archivoAlmacenamientoService;
    private final ColaboradorActividadService colaboradorActividadService;
    private final NotificacionRepository notificacionRepository;
    private final RmRevisionAsignadorService rmRevisionAsignadorService;
    private final AsignacionRepository asignacionRepository;
    private final ConfiguracionSistemaRepository configuracionSistemaRepository;

    /**
     * Inyecta por constructor las dependencias del service.
     * <p>
     * {@code colaboradorActividadService} se inyecta con {@code @Lazy} porque hay una dependencia
     * circular: ese service usa este para marcar cursos vencidos, y este usa aquel para calcular
     * el resumen del bono. {@code @Lazy} hace que se cree solo cuando se necesita.
     *
     * @param cursoRepository catálogo de cursos
     * @param colaboradorCursoRepository inscripciones de colaboradores a cursos
     * @param auditoriaService registro en el log de auditoría
     * @param notificacionService envío de notificaciones
     * @param notificacionRepository consulta de notificaciones ya enviadas (para no repetir avisos)
     * @param archivoAlmacenamientoService guardado de archivos
     * @param colaboradorActividadService horas y bono del colaborador
     * @param rmRevisionAsignadorService elige qué RM revisa cada solicitud
     * @param asignacionRepository acceso a asignaciones
     * @param configuracionSistemaRepository parámetros globales
     */
    public ColaboradorCursoService(CursoRepository cursoRepository,
                                   ColaboradorCursoRepository colaboradorCursoRepository,
                                   AuditoriaService auditoriaService,
                                   NotificacionService notificacionService,
                                   NotificacionRepository notificacionRepository,
                                   ArchivoAlmacenamientoService archivoAlmacenamientoService,
                                   //@Lazy evita una dependencia circular entre los servicios,
                                   //haciendo que ColaboradorActividadService se inicialice cuando sea necesario.
                                   @Lazy ColaboradorActividadService colaboradorActividadService,
                                   RmRevisionAsignadorService rmRevisionAsignadorService,
                                   AsignacionRepository asignacionRepository,
                                   ConfiguracionSistemaRepository configuracionSistemaRepository) {
        this.cursoRepository = cursoRepository;
        this.colaboradorCursoRepository = colaboradorCursoRepository;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
        this.notificacionRepository = notificacionRepository;
        this.archivoAlmacenamientoService = archivoAlmacenamientoService;
        this.colaboradorActividadService = colaboradorActividadService;
        this.rmRevisionAsignadorService = rmRevisionAsignadorService;
        this.asignacionRepository = asignacionRepository;
        this.configuracionSistemaRepository = configuracionSistemaRepository;
    }

    // ============================================================
    // CATÁLOGO DE CURSOS DISPONIBLES (activos del Admin)
    // ============================================================
    /**
     * Arma el catálogo de cursos con el estado de cada uno para este colaborador.
     * <p>
     * Antes de listar, marca como no completados los cursos suyos que vencieron sin evidencia.
     * Para cada curso activo (orden alfabético) toma la inscripción más reciente del colaborador:
     * <ul>
     *   <li>Un curso <b>caducado</b> (fecha de fin pasada) no se ofrece, salvo que el colaborador
     *       ya tenga algo con él (para ver su estado o subir evidencia).</li>
     *   <li>{@code puedeSolicitar} es verdadero si no hay un bloqueo general (ver
     *       {@link #motivoBloqueoGeneral}) y su inscripción más reciente no está en un estado
     *       que lo impida.</li>
     *   <li>Si ya tiene una inscripción, se muestra el texto y color de su estado.</li>
     * </ul>
     *
     * @param colaborador colaborador que consulta el catálogo
     * @return una tarjeta por curso
     */

    @Transactional
    public List<CursoDisponibleView> listarCursosDisponibles(Usuario colaborador) {
        marcarCursosSinEvidenciaVencidos(colaborador);
        List<Curso> cursosActivos = cursoRepository.findByActivoTrueOrderByNombreAsc();

        List<ColaboradorCurso> misRegistros = colaboradorCursoRepository.findByColaborador(colaborador);
        List<CursoDisponibleView> resultado = new ArrayList<>();
        String motivoBloqueoGeneral = motivoBloqueoGeneral(colaborador);

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

            boolean puedeSolicitar = motivoBloqueoGeneral == null;
            String motivoBloqueo = motivoBloqueoGeneral;
            String estadoTexto = null;
            String estadoClase = null;

            if (masReciente != null) {
                estadoTexto = textoEstado(masReciente.getEstado());
                estadoClase = claseEstado(masReciente.getEstado());
                if (ESTADOS_QUE_BLOQUEAN.contains(masReciente.getEstado())) {
                    puedeSolicitar = false;
                    motivoBloqueo = null;
                }
            }

            resultado.add(new CursoDisponibleView(curso, puedeSolicitar, estadoTexto, estadoClase, motivoBloqueo));
        }
        return resultado;
    }

    // ============================================================
    // CATEGORÍAS DISPONIBLES
    // ============================================================
    /**
     * Lista las categorías distintas de los cursos activos, sin repetir y sin contar las vacías.
     * Alimenta el filtro por categoría de "Explorar". Es una consulta de solo lectura.
     *
     * @return nombres de categorías
     */
    @Transactional(readOnly = true)
    public List<String> listarCategorias() {
        return Paginador.opcionesUnicas(cursoRepository.findByActivoTrueOrderByNombreAsc().stream()
                .map(Curso::getCategoria).toList());
    }

    // ============================================================
    // SOLICITUDES DE CURSO ENVIADAS POR EL COLABORADOR
    // ============================================================

    /**
     * Lista las solicitudes de curso que el propio colaborador envió (origen
     * {@code SOLICITUD_COLABORADOR}), de la más reciente a la más antigua. No incluye los cursos
     * que le inscribió el RM directamente. Es una consulta de solo lectura.
     *
     * @param colaborador colaborador consultado
     * @return sus solicitudes
     */
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

    /**
     * Registra la solicitud del colaborador para inscribirse a un curso.
     * <p>
     * Validaciones, en este orden: el curso existe, está activo y su fecha de fin no pasó; no hay
     * un bloqueo general (ver {@link #motivoBloqueoGeneral}); no tiene ya una inscripción en un
     * estado que bloquea; y la justificación, si viene, no supera 500 caracteres.
     * <p>
     * Si todo es válido, guarda la inscripción en estado {@code SOLICITADO} con origen
     * {@code SOLICITUD_COLABORADOR} y un RM responsable asignado, registra auditoría y notifica
     * a ese RM. El colaborador no puede aprobarla: la decide el RM.
     *
     * @param colaborador colaborador que solicita
     * @param cursoId curso al que quiere inscribirse
     * @param justificacion motivo de la solicitud (opcional)
     * @throws IllegalArgumentException si no se cumple alguna validación
     */
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
        String motivoBloqueo = motivoBloqueoGeneral(colaborador);
        if (motivoBloqueo != null) {
            throw new IllegalArgumentException(motivoBloqueo);
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
        solicitud.setRmResponsable(rmRevisionAsignadorService.elegirRm());
        ColaboradorCurso guardada = colaboradorCursoRepository.save(solicitud);

        auditoriaService.registrar(colaborador, "SOLICITAR_CURSO", "COLABORADOR_CURSO", guardada.getId(),
                "Solicitó inscribirse al curso \"" + curso.getNombre() + "\".");

        notificacionService.crearParaRm(guardada.getRmResponsable(), "CURSO_SOLICITADO", CategoriaNotificacion.CURSO,
                "Nueva solicitud de curso",
                colaborador.getNombre() + " " + colaborador.getApellido()
                        + " solicitó inscribirse al curso \"" + curso.getNombre() + "\".",
                "COLABORADOR_CURSO", guardada.getId());
    }

    // ============================================================
    // MIS CURSOS (perfil profesional). Listamos los cursos que se están llevando, con evidencia enviada,
    // completados o no completados (vencidos sin evidencia)
    // ============================================================

    /**
     * Lista los cursos que el colaborador está llevando o ya terminó, para su perfil profesional:
     * estados {@code EN_CURSO}, {@code EVIDENCIA_PENDIENTE}, {@code COMPLETADO} y
     * {@code NO_COMPLETADO}, del más reciente al más antiguo. Las solicitudes pendientes o
     * rechazadas no se incluyen. Antes de listar, marca como no completados los cursos vencidos.
     *
     * @param colaborador colaborador consultado
     * @return sus cursos
     */
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
    // Si aún no vence pero faltan DIAS_AVISO_FIN_CURSO días o menos, se avisa una sola vez al colaborador.
    // ============================================================

    /**
     * Revisa los cursos {@code EN_CURSO} del colaborador y marca como {@code NO_COMPLETADO} los
     * que ya vencieron sin evidencia. Si aún no vencen pero faltan pocos días, le avisa una sola vez.
     * La regla completa está en {@link #marcarSinEvidenciaVencidos}.
     *
     * @param colaborador colaborador cuyos cursos se revisan
     */
    @Transactional
    public void marcarCursosSinEvidenciaVencidos(Usuario colaborador) {
        marcarSinEvidenciaVencidos(colaboradorCursoRepository.findByColaborador(colaborador));
    }

    /**
     * Aplica la misma regla a <b>todos</b> los colaboradores con inscripciones {@code EN_CURSO}.
     * La ejecuta el RM al abrir su dashboard y la bandeja de cursos, para que no vea "En curso"
     * un curso ya vencido aunque el colaborador no haya entrado a la plataforma.
     */
    @Transactional
    public void marcarTodosLosCursosSinEvidenciaVencidos() {
        marcarSinEvidenciaVencidos(colaboradorCursoRepository.findByEstado(EstadoColaboradorCurso.EN_CURSO));
    }

    /**
     * Regla de vencimiento de cursos. Solo mira inscripciones {@code EN_CURSO}:
     * <ul>
     *   <li>Sin fecha de fin: no hay plazo, sigue en curso hasta que suba evidencia.</li>
     *   <li>Fecha de fin futura: sigue en curso y, si faltan {@code DIAS_AVISO_FIN_CURSO} días o
     *       menos, se le avisa una sola vez.</li>
     *   <li>Fecha de fin hoy o pasada y ya subió evidencia: no se toca.</li>
     *   <li>Fecha de fin hoy o pasada y sin evidencia: pasa a {@code NO_COMPLETADO}, se audita y
     *       se le notifica. No recibe penalización, pero esas horas no se cuentan.</li>
     * </ul>
     * Como el estado cambia, una segunda ejecución ya no vuelve a tocar el mismo curso.
     *
     * @param inscripciones inscripciones a revisar
     */
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
                avisarCursoPorVencer(registro, hoy);
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

    /**
     * Avisa que el curso está por vencer. Es un aviso único por inscripción: solo se envía si
     * faltan {@code DIAS_AVISO_FIN_CURSO} días o menos, aún no hay evidencia y no existe ya una
     * notificación {@code CURSO_POR_VENCER} para esa inscripción.
     *
     * @param registro inscripción en curso
     * @param hoy fecha actual
     */
    private void avisarCursoPorVencer(ColaboradorCurso registro, LocalDate hoy) {
        Curso curso = registro.getCurso();

        if (registro.getEvidenciaUrl() != null || curso.getFechaFin().isAfter(hoy.plusDays(DIAS_AVISO_FIN_CURSO))) {
            return;
        }

        Usuario colaborador = registro.getColaborador();

        if (notificacionRepository.existsByUsuarioAndTipoAndEntidadAndEntidadId(colaborador, "CURSO_POR_VENCER", "COLABORADOR_CURSO", registro.getId())) {
            return;
        }

        long diasRestantes = java.time.temporal.ChronoUnit.DAYS.between(hoy, curso.getFechaFin());
        String fechaFin = curso.getFechaFin().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        notificacionService.crear(colaborador, "CURSO_POR_VENCER", CategoriaNotificacion.CURSO,
                "Curso por vencer",
                "Falta" + (diasRestantes == 1 ? " 1 día" : "n " + diasRestantes + " días") + " para la fecha fin del curso \"" + curso.getNombre() + "\" (" + fechaFin + "). Sube tu evidencia de finalización antes de esa fecha.",
                "COLABORADOR_CURSO", registro.getId());
    }

    // ============================================================
    // COLABORADOR SUBE LA EVIDENCIA DE FINALIZACIÓN DE UN CURSO
    // ============================================================

    /**
     * Sube la evidencia de finalización de un curso para que el RM la revise.
     * <p>
     * Validaciones: la inscripción existe y es del colaborador; está {@code EN_CURSO}; si es el
     * primer intento, el plazo no venció (la fecha de fin debe ser posterior a hoy); y la
     * evidencia existe, es PDF/JPG/PNG y pesa como máximo 10 MB.
     * <p>
     * Si todo es válido: guarda el archivo, asigna un RM si no tenía, pasa la inscripción a
     * {@code EVIDENCIA_PENDIENTE} y borra la revisión anterior (queda en la auditoría). Registra
     * auditoría y notifica al RM. Si el RM la rechazó, la inscripción vuelve a {@code EN_CURSO} y
     * el colaborador puede reenviar sin que cuente como primer intento, aunque el plazo haya vencido.
     *
     * @param colaborador colaborador que sube la evidencia
     * @param inscripcionId inscripción al curso
     * @param evidencia archivo de evidencia
     * @throws IllegalArgumentException si no se cumple alguna validación
     */
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

        if (inscripcion.getRmResponsable() == null) {
            inscripcion.setRmResponsable(rmRevisionAsignadorService.elegirRm());
        }
        inscripcion.setEvidenciaUrl(evidenciaUrl);

        inscripcion.setFechaEvidencia(LocalDateTime.now());
        inscripcion.setEstado(EstadoColaboradorCurso.EVIDENCIA_PENDIENTE);
        // Al reenviar tras un rechazo, la revisión anterior deja de aplicar (queda en la auditoría).
        inscripcion.setEvidenciaRevisadaPor(null);
        inscripcion.setFechaRevisionEvidencia(null);
        colaboradorCursoRepository.save(inscripcion);

        auditoriaService.registrar(colaborador, "SUBIR_EVIDENCIA_CURSO", "COLABORADOR_CURSO", inscripcion.getId(),
                "Subió evidencia de finalización del curso \"" + inscripcion.getCurso().getNombre() + "\".");

        notificacionService.crearParaRm(inscripcion.getRmResponsable(), "EVIDENCIA_CURSO_PENDIENTE", CategoriaNotificacion.CURSO,
                "Evidencia de curso pendiente de revisión",
                colaborador.getNombre() + " " + colaborador.getApellido()
                        + " subió evidencia para el curso \"" + inscripcion.getCurso().getNombre() + "\".",
                "COLABORADOR_CURSO", inscripcion.getId());
    }


    /** Mensaje cuando no puede solicitar un curso por tener el máximo de asignaciones activas. */
    private static final String MSG_TOPE_ASIGNACIONES =
            "No puedes solicitar este curso: ya tienes el máximo de asignaciones activas en proyectos permitido por la organización.";

    /** Mensaje cuando no puede solicitar un curso por haber llegado al tope de horas extra del bono. */
    private static final String MSG_TOPE_HORAS =
            "No puedes solicitar este curso: ya alcanzaste el máximo de horas que cuentan para tu bono este mes, "
                    + "así que no te quedan horas disponibles para sumar.";


    /**
     * Determina si el colaborador está bloqueado para solicitar cualquier curso. Hay dos motivos:
     * <ol>
     *   <li>ya tiene el máximo de asignaciones activas permitido por la organización;</li>
     *   <li>ya alcanzó el tope de horas extra bonificables del mes, así que las horas de un curso
     *       ya no le sumarían.</li>
     * </ol>
     *
     * @param colaborador colaborador evaluado
     * @return el mensaje del bloqueo, o {@code null} si puede solicitar
     */
    private String motivoBloqueoGeneral(Usuario colaborador) {
        if (alcanzoMaximoAsignaciones(colaborador)) {
            return MSG_TOPE_ASIGNACIONES;
        }
        ColBonoMensualView resumen = colaboradorActividadService.obtenerResumenBonoMensual(colaborador);
        if (resumen.getHorasExtraBonificables().compareTo(resumen.getTopeHorasExtra()) >= 0) {
            return MSG_TOPE_HORAS;
        }
        return null;
    }

    /**
     * @param colaborador colaborador evaluado
     * @return {@code true} si sus asignaciones {@code ACTIVAS} igualan o superan el máximo configurado
     */
    private boolean alcanzoMaximoAsignaciones(Usuario colaborador) {
        long activas = asignacionRepository.countByColaboradorAndEstado(colaborador, EstadoAsignacion.ACTIVA);
        return activas >= obtenerMaxAsignaciones();
    }

    /**
     * Lee el máximo de asignaciones activas desde la configuración del sistema.
     *
     * @return el valor configurado por el Administrador; 3 si el parámetro no existe, no es
     *         numérico o no es mayor que 0
     */
    private int obtenerMaxAsignaciones() {
        return configuracionSistemaRepository.findByClave(CLAVE_MAX_ASIGNACIONES)
                .map(c -> {
                    try {
                        int n = Integer.parseInt(c.getValor().trim());
                        return n > 0 ? n : MAX_ASIGNACIONES_POR_DEFECTO;
                    } catch (NumberFormatException ex) {
                        return MAX_ASIGNACIONES_POR_DEFECTO;
                    }
                })
                .orElse(MAX_ASIGNACIONES_POR_DEFECTO);
    }

    // ============================================================
    // VALIDACIONES
    // ============================================================
    /**
     * Valida la justificación de la solicitud. Es opcional.
     *
     * @param justificacion texto ingresado
     * @return el texto recortado, o {@code null} si viene vacío
     * @throws IllegalArgumentException si supera 500 caracteres
     */
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

    /**
     * Traduce un estado de inscripción al texto que ve el colaborador (por ejemplo,
     * {@code EN_CURSO} se muestra como "Inscrito").
     *
     * @param estado estado de la inscripción
     * @return texto visible; vacío si el estado no tiene texto definido
     */
    private String textoEstado(EstadoColaboradorCurso estado) {
        if (estado == EstadoColaboradorCurso.SOLICITADO) return "Solicitud pendiente";
        if (estado == EstadoColaboradorCurso.EN_CURSO) return "Inscrito";
        if (estado == EstadoColaboradorCurso.EVIDENCIA_PENDIENTE) return "Evidencia en revisión";
        if (estado == EstadoColaboradorCurso.COMPLETADO) return "Completado";
        if (estado == EstadoColaboradorCurso.RECHAZADO) return "Solicitud rechazada";
        if (estado == EstadoColaboradorCurso.NO_COMPLETADO) return "No completado";
        return "";
    }

    /**
     * Devuelve la clase CSS (de Tabler) con la que se colorea la etiqueta de cada estado.
     *
     * @param estado estado de la inscripción
     * @return clase CSS; gris por defecto
     */
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