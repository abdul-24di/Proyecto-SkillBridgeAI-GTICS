package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.dto.PmAsignacionView;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Service
public class PmAsignacionService {

    private final AsignacionRepository asignacionRepository;
    private final ProyectoRepository proyectoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    public PmAsignacionService(AsignacionRepository asignacionRepository,
                               ProyectoRepository proyectoRepository,
                               UsuarioRepository usuarioRepository,
                               AuditoriaService auditoriaService) {
        this.asignacionRepository = asignacionRepository;
        this.proyectoRepository = proyectoRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaService = auditoriaService;
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
        asignacion.setMensajeSolicitud(mensajeSolicitud);
        asignacion.setEstado(EstadoAsignacion.PENDIENTE);
        asignacion.setAprobadoPorPm(true);           // PM propone → ya aprobó su lado
        asignacion.setAprobadoPorRm(false);
        asignacion.setFechaAprobacionPm(LocalDateTime.now());
        Asignacion saved = asignacionRepository.save(asignacion);

        auditoriaService.registrar(pm, "PROPONER", "ASIGNACION", saved.getId(),
                "PM propuso al colaborador ID " + colaboradorId
                + " para el proyecto '" + proyecto.getNombre() + "'.");
        return saved;
    }

    @Transactional
    public void aprobar(Long asignacionId, Usuario pm) {
        Asignacion asignacion = obtenerAsignacionDelPm(asignacionId, pm);

        if (asignacion.getOrigen() != OrigenAsignacion.PROPUESTA_RM) {
            throw new IllegalStateException("Solo se pueden aprobar propuestas originadas por el RM.");
        }
        if (asignacion.getEstado() != EstadoAsignacion.PENDIENTE) {
            throw new IllegalStateException("La asignación ya no está pendiente.");
        }
        asignacion.setAprobadoPorPm(true);
        asignacion.setFechaAprobacionPm(LocalDateTime.now());

        // Si el RM ya aprobó también → activar
        if (asignacion.isAprobadoPorRm()) {
            asignacion.setEstado(EstadoAsignacion.ACTIVA);
        }
        asignacionRepository.save(asignacion);

        auditoriaService.registrar(pm, "APROBAR", "ASIGNACION", asignacionId,
                "PM aprobó la asignación propuesta por el RM.");
    }

    @Transactional
    public void rechazar(Long asignacionId, String motivo, Usuario pm) {
        Asignacion asignacion = obtenerAsignacionDelPm(asignacionId, pm);

        if (asignacion.getEstado() != EstadoAsignacion.PENDIENTE) {
            throw new IllegalStateException("La asignación ya no está pendiente.");
        }
        asignacion.setEstado(EstadoAsignacion.RECHAZADA);
        asignacion.setRechazadoPor(pm);
        asignacion.setMotivoRechazo(motivo);
        asignacionRepository.save(asignacion);

        auditoriaService.registrar(pm, "RECHAZAR", "ASIGNACION", asignacionId,
                "PM rechazó la asignación. Motivo: " + motivo);
    }

    @Transactional
    public void finalizar(Long asignacionId, Usuario pm) {
        Asignacion asignacion = obtenerAsignacionDelPm(asignacionId, pm);

        if (asignacion.getEstado() != EstadoAsignacion.ACTIVA) {
            throw new IllegalStateException("Solo se pueden finalizar asignaciones activas.");
        }
        asignacion.setEstado(EstadoAsignacion.FINALIZADA);
        asignacion.setFechaFinalizacion(LocalDateTime.now());
        asignacion.setMotivoFinalizacion(
                com.pucp.skillb_ia.model.enums.MotivoFinalizacion.OTRO);
        asignacionRepository.save(asignacion);

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
                .orElseThrow(() -> new IllegalArgumentException("Colaborador no encontrado."));
    }
}
