package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.dto.PmAsignacionView;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.DisponibilidadService;
import com.pucp.skillb_ia.service.NotificacionService;
import com.pucp.skillb_ia.service.EvaluacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Service
public class PmAsignacionService {

    private static final String ROL_COLABORADOR = "COLABORADOR";
    private static final BigDecimal MAX_HORAS_SEMANALES = new BigDecimal("168");

    private final AsignacionRepository asignacionRepository;
    private final ProyectoRepository proyectoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;
    private final DisponibilidadService disponibilidadService;
    private final NotificacionService notificacionService;
    private final EvaluacionService evaluacionService;

    public PmAsignacionService(AsignacionRepository asignacionRepository,
                               ProyectoRepository proyectoRepository,
                               UsuarioRepository usuarioRepository,
                               AuditoriaService auditoriaService,
                               DisponibilidadService disponibilidadService,
                               NotificacionService notificacionService,
                               EvaluacionService evaluacionService) {
        this.asignacionRepository = asignacionRepository;
        this.proyectoRepository = proyectoRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaService = auditoriaService;
        this.disponibilidadService = disponibilidadService;
        this.notificacionService = notificacionService;
        this.evaluacionService = evaluacionService;
    }



    @Transactional(readOnly = true)
    public List<PmAsignacionView> listarPorProyecto(Long proyectoId, Usuario pm) {
        Proyecto proyecto = obtenerProyectoDelPm(proyectoId, pm);
        return asignacionRepository.findByProyectoOrderByFechaSolicitudDesc(proyecto)
                .stream()
                .map(PmAsignacionView::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PmAsignacionView> listarPendientesPm(Long proyectoId, Usuario pm) {
        Proyecto proyecto = obtenerProyectoDelPm(proyectoId, pm);
        return asignacionRepository.findPendientesPmByProyecto(proyecto)
                .stream()
                .map(PmAsignacionView::new)
                .toList();
    }

    @Transactional
    public Asignacion proponer(Long proyectoId, Long colaboradorId,
                               BigDecimal horasSemanales, String mensajeSolicitud,
                               Usuario pm) {
        Proyecto proyecto = obtenerProyectoDelPm(proyectoId, pm);
        Usuario colaborador = obtenerColaborador(colaboradorId);
        validarProyectoAsignable(proyecto);
        validarHoras(horasSemanales);
        String mensaje = textoOpcional(mensajeSolicitud, 500,
                "El mensaje no puede superar los 500 caracteres.");

        // Validar que no tenga ya una asignación activa o pendiente en este proyecto
        boolean yaExiste = asignacionRepository.existsByProyectoAndColaboradorAndEstadoIn(
                proyecto, colaborador,
                EnumSet.of(EstadoAsignacion.ACTIVA, EstadoAsignacion.PENDIENTE));
        if (yaExiste) {
            throw new IllegalStateException(
                    "El colaborador ya tiene una asignación activa o pendiente en este proyecto.");
        }

        Asignacion asignacion = new Asignacion();
        asignacion.setProyecto(proyecto);
        asignacion.setColaborador(colaborador);
        asignacion.setHorasSemanales(horasSemanales);
        asignacion.setOrigen(OrigenAsignacion.PROPUESTA_PM);
        asignacion.setMensajeSolicitud(mensaje);
        asignacion.setEstado(EstadoAsignacion.PENDIENTE);
        asignacion.setAprobadoPorPm(true);           // PM propone → ya aprobó su lado
        asignacion.setAprobadoPorRm(false);
        asignacion.setFechaAprobacionPm(LocalDateTime.now());
        Asignacion saved = asignacionRepository.save(asignacion);

        auditoriaService.registrar(pm, "PROPONER", "ASIGNACION", saved.getId(),
                "PM propuso al colaborador ID " + colaboradorId
                + " para el proyecto '" + proyecto.getNombre() + "'.");

        notificacionService.crearParaTodosLosRm("ASIGNACION_PENDIENTE_RM", CategoriaNotificacion.ASIGNACION,
                "Asignación pendiente de tu aprobación",
                "El PM del proyecto \"" + proyecto.getNombre() + "\" propuso a " + colaborador.getNombre()
                        + " " + colaborador.getApellido() + ".",
                "ASIGNACION", saved.getId());


        return saved;
    }

    @Transactional
    public void aprobar(Long asignacionId, Usuario pm) {
        Asignacion asignacion = obtenerAsignacionDelPm(asignacionId, pm);

        validarDecisionPm(asignacion);
        validarProyectoAsignable(asignacion.getProyecto());
        asignacion.setAprobadoPorPm(true);
        asignacion.setFechaAprobacionPm(LocalDateTime.now());

        // Si el RM ya aprobó también → activar
        if (asignacion.isAprobadoPorRm()) {
            validarSinAsignacionActivaDuplicada(asignacion);
            asignacion.setEstado(EstadoAsignacion.ACTIVA);
            asignacion.setFechaActivacion(LocalDateTime.now());
        }


        asignacionRepository.save(asignacion);

        //En caso de que quede activa, recalculamos la disponibilidad del colaborador
        if (asignacion.getEstado() == EstadoAsignacion.ACTIVA) {
            disponibilidadService.recalcular(asignacion.getColaborador());
            notificacionService.crear(asignacion.getColaborador(), "ASIGNACION_APROBADA", CategoriaNotificacion.ASIGNACION,
                    "Asignación aprobada",
                    "Has sido asignado al proyecto \"" + asignacion.getProyecto().getNombre() + "\".",
                    "ASIGNACION", asignacion.getId());
        }

        auditoriaService.registrar(pm, "APROBAR", "ASIGNACION", asignacionId,
                "PM aprobó la asignación propuesta por el RM.");
    }

    @Transactional
    public void rechazar(Long asignacionId, String motivo, Usuario pm) {
        Asignacion asignacion = obtenerAsignacionDelPm(asignacionId, pm);
        validarDecisionPm(asignacion);
        String motivoLimpio = textoObligatorio(motivo,
                "Debes indicar el motivo del rechazo.", 300);


        asignacion.setEstado(EstadoAsignacion.RECHAZADA);
        asignacion.setRechazadoPor(pm);
        asignacion.setMotivoRechazo(motivoLimpio);

        asignacionRepository.save(asignacion);

        notificacionService.crear(asignacion.getColaborador(), "ASIGNACION_RECHAZADA", CategoriaNotificacion.ASIGNACION,
                "Asignación rechazada",
                "Tu asignación al proyecto \"" + asignacion.getProyecto().getNombre()
                        + "\" fue rechazada. Motivo: " + motivoLimpio,
                "ASIGNACION", asignacion.getId());

        auditoriaService.registrar(pm, "RECHAZAR", "ASIGNACION", asignacionId,
                "PM rechazó la asignación. Motivo: " + motivoLimpio);
    }

    @Transactional
    public void finalizar(Long asignacionId, Integer calificacion, String feedback, Usuario pm) {
        Asignacion asignacion = obtenerAsignacionDelPm(asignacionId, pm);

        if (asignacion.getEstado() != EstadoAsignacion.ACTIVA) {
            throw new IllegalStateException("Solo se pueden finalizar asignaciones activas.");
        }
        asignacion.setEstado(EstadoAsignacion.FINALIZADA);
        asignacion.setFechaFinalizacion(LocalDateTime.now());
        asignacion.setMotivoFinalizacion(
                com.pucp.skillb_ia.model.enums.MotivoFinalizacion.OTRO);

        asignacionRepository.save(asignacion);
        if (calificacion != null) {
            evaluacionService.evaluarAsignacion(asignacion.getId(), calificacion, feedback, pm);
        }


        //En caso de que se desasigne a un colaborador de un proyecto, tendremos que recalcular su disponibilidad
        disponibilidadService.recalcular(asignacion.getColaborador());

        notificacionService.crear(asignacion.getColaborador(), "ASIGNACION_FINALIZADA", CategoriaNotificacion.ASIGNACION,
                "Te desasignaron de un proyecto",
                "El PM te desasignó del proyecto \"" + asignacion.getProyecto().getNombre() + "\".",
                "ASIGNACION", asignacion.getId());

        auditoriaService.registrar(pm, "FINALIZAR", "ASIGNACION", asignacionId,
                "PM finalizó la asignación del colaborador ID "
                        + asignacion.getColaborador().getId()
                        + " en el proyecto '" + asignacion.getProyecto().getNombre() + "'.");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Proyecto obtenerProyectoDelPm(Long proyectoId, Usuario pm) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));
        if (!proyecto.getPm().getId().equals(pm.getId())) {
            throw new SecurityException("No tienes permiso sobre este proyecto.");
        }
        return proyecto;
    }

    private Asignacion obtenerAsignacionDelPm(Long asignacionId, Usuario pm) {
        Asignacion asignacion = asignacionRepository.findById(asignacionId)
                .orElseThrow(() -> new IllegalArgumentException("Asignación no encontrada."));
        if (!asignacion.getProyecto().getPm().getId().equals(pm.getId())) {
            throw new SecurityException("No tienes permiso sobre esta asignación.");
        }
        return asignacion;
    }

    private Usuario obtenerColaborador(Long colaboradorId) {
        return usuarioRepository.findById(colaboradorId)
                .filter(Usuario::isActivo)
                .filter(usuario -> usuario.getRol() != null
                        && ROL_COLABORADOR.equals(usuario.getRol().getNombre()))
                .orElseThrow(() -> new IllegalArgumentException("No se encontró un colaborador activo válido."));
    }

    private void validarProyectoAsignable(Proyecto proyecto) {
        if (proyecto.getEstado() != EstadoProyecto.ACTIVO
                && proyecto.getEstado() != EstadoProyecto.EN_ESPERA) {
            throw new IllegalStateException(
                    "Solo se pueden proponer asignaciones para proyectos activos o en espera.");
        }
    }

    private void validarHoras(BigDecimal horas) {
        if (horas == null || horas.signum() <= 0) {
            throw new IllegalArgumentException("Las horas semanales deben ser mayores que cero.");
        }
        if (horas.compareTo(MAX_HORAS_SEMANALES) > 0) {
            throw new IllegalArgumentException("Las horas semanales no pueden superar 168.");
        }
    }

    private void validarDecisionPm(Asignacion asignacion) {
        if (asignacion.getEstado() != EstadoAsignacion.PENDIENTE) {
            throw new IllegalStateException("La asignación ya no está pendiente.");
        }
        if (asignacion.isAprobadoPorPm()
                || asignacion.getOrigen() == OrigenAsignacion.PROPUESTA_PM) {
            throw new IllegalStateException("Esta asignación no está pendiente de decisión del PM.");
        }
    }

    private void validarSinAsignacionActivaDuplicada(Asignacion asignacion) {
        if (asignacionRepository.existsByProyectoAndColaboradorAndEstado(
                asignacion.getProyecto(), asignacion.getColaborador(), EstadoAsignacion.ACTIVA)) {
            throw new IllegalStateException(
                    "El colaborador ya tiene una asignación activa en este proyecto.");
        }
    }

    private String textoObligatorio(String valor, String mensaje, int maximo) {
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException(mensaje);
        String limpio = valor.trim();
        if (limpio.length() > maximo) {
            throw new IllegalArgumentException("El texto no puede superar " + maximo + " caracteres.");
        }
        return limpio;
    }

    private String textoOpcional(String valor, int maximo, String mensaje) {
        if (valor == null || valor.isBlank()) return null;
        String limpio = valor.trim();
        if (limpio.length() > maximo) throw new IllegalArgumentException(mensaje);
        return limpio;
    }
}
