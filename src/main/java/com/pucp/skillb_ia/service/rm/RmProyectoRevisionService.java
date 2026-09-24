package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class RmProyectoRevisionService {
    private static final String ROL_RM = "RESOURCE_MANAGER";
    private static final BigDecimal PRESUPUESTO_MAXIMO = new BigDecimal("9999999999.99");

    private final ProyectoRepository proyectoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;
    private final RmPresupuestoService presupuestoService;

    public RmProyectoRevisionService(ProyectoRepository proyectoRepository,
                                     UsuarioRepository usuarioRepository,
                                     AuditoriaService auditoriaService,
                                     RmPresupuestoService presupuestoService) {
        this.proyectoRepository = proyectoRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaService = auditoriaService;
        this.presupuestoService = presupuestoService;
    }

    @Transactional
    public EstadoProyecto asignarPresupuesto(Long proyectoId, BigDecimal presupuesto, Long rmId) {
        Usuario rm = obtenerRm(rmId);
        Proyecto proyecto = obtenerConPresupuestoEditable(proyectoId);
        BigDecimal presupuestoValidado = validarPresupuesto(presupuesto);
        RmPresupuestoService.ResumenPresupuesto resumen = presupuestoService.calcularResumen(proyecto);
        BigDecimal comprometidoYReservado = resumen.comprometido().add(resumen.reservado());
        if (presupuestoValidado.compareTo(comprometidoYReservado) < 0) {
            throw new IllegalArgumentException(
                    "El presupuesto no puede ser menor que el monto ya comprometido o reservado (S/ "
                            + comprometidoYReservado.setScale(2, RoundingMode.HALF_UP).toPlainString() + ").");
        }
        String anterior = proyecto.getPresupuesto() == null ? null : proyecto.getPresupuesto().toPlainString();

        proyecto.setPresupuesto(presupuestoValidado);
        proyectoRepository.save(proyecto);
        String accion = anterior == null ? "ASIGNAR_PRESUPUESTO_PROYECTO" : "ACTUALIZAR_PRESUPUESTO_PROYECTO";
        auditoriaService.registrar(rm, accion, "PROYECTO", proyectoId,
                "Se registró el presupuesto del proyecto " + proyecto.getNombre(),
                anterior, presupuestoValidado.toPlainString(), null);
        return proyecto.getEstado();
    }

    @Transactional
    public void aprobar(Long proyectoId, String observacion, Long rmId) {
        Usuario rm = obtenerRm(rmId);
        Proyecto proyecto = obtenerEnRevision(proyectoId);
        if (proyecto.getPresupuesto() == null || proyecto.getPresupuesto().signum() <= 0) {
            throw new IllegalArgumentException("Asigna un presupuesto válido antes de aprobar el proyecto.");
        }

        proyecto.setEstado(EstadoProyecto.ACTIVO);
        proyecto.setRmRevisor(rm);
        proyecto.setMotivoRechazo(null);
        proyectoRepository.save(proyecto);
        auditoriaService.registrar(rm, "APROBAR_PROYECTO", "PROYECTO", proyectoId,
                detalleDecision("Proyecto aprobado", observacion), "EN_REVISION", "ACTIVO", null);
    }

    @Transactional
    public void rechazar(Long proyectoId, String motivo, Long rmId) {
        Usuario rm = obtenerRm(rmId);
        Proyecto proyecto = obtenerEnRevision(proyectoId);
        String motivoValidado = motivo == null ? "" : motivo.trim();
        if (motivoValidado.isEmpty()) {
            throw new IllegalArgumentException("Debes registrar el motivo del rechazo.");
        }
        if (motivoValidado.length() > 500) {
            throw new IllegalArgumentException("El motivo del rechazo no puede superar los 500 caracteres.");
        }

        proyecto.setEstado(EstadoProyecto.RECHAZADO);
        proyecto.setRmRevisor(rm);
        proyecto.setMotivoRechazo(motivoValidado);
        proyectoRepository.save(proyecto);
        String detalleAuditoria = "Proyecto rechazado. Motivo: " + motivoValidado;
        auditoriaService.registrar(rm, "RECHAZAR_PROYECTO", "PROYECTO", proyectoId,
                detalleAuditoria.substring(0, Math.min(detalleAuditoria.length(), 500)),
                "EN_REVISION", "RECHAZADO", null);
    }

    private Proyecto obtenerEnRevision(Long proyectoId) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el proyecto solicitado."));
        if (proyecto.getEstado() != EstadoProyecto.EN_REVISION) {
            throw new IllegalStateException("El proyecto ya no se encuentra en revisión.");
        }
        return proyecto;
    }

    private Proyecto obtenerConPresupuestoEditable(Long proyectoId) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el proyecto solicitado."));
        if (proyecto.getEstado() != EstadoProyecto.EN_REVISION
                && proyecto.getEstado() != EstadoProyecto.ACTIVO
                && proyecto.getEstado() != EstadoProyecto.EN_ESPERA) {
            throw new IllegalStateException("El presupuesto no puede modificarse en el estado actual del proyecto.");
        }
        return proyecto;
    }

    private Usuario obtenerRm(Long rmId) {
        if (rmId == null) throw new IllegalArgumentException("Debes iniciar sesión como Resource Manager.");
        return usuarioRepository.findById(rmId)
                .filter(Usuario::isActivo)
                .filter(usuario -> ROL_RM.equals(usuario.getRol().getNombre()))
                .orElseThrow(() -> new IllegalArgumentException("El usuario autenticado no es un Resource Manager activo."));
    }

    private BigDecimal validarPresupuesto(BigDecimal presupuesto) {
        if (presupuesto == null || presupuesto.signum() <= 0) {
            throw new IllegalArgumentException("El presupuesto debe ser mayor que cero.");
        }
        if (presupuesto.compareTo(PRESUPUESTO_MAXIMO) > 0) {
            throw new IllegalArgumentException("El presupuesto supera el monto máximo permitido.");
        }
        return presupuesto.setScale(2, RoundingMode.HALF_UP);
    }

    private String detalleDecision(String base, String observacion) {
        if (observacion == null || observacion.isBlank()) return base;
        String texto = observacion.trim();
        return base + ". Observación: " + texto.substring(0, Math.min(texto.length(), 450));
    }
}
