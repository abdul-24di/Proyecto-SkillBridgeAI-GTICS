package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.model.LogAuditoria;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.LogAuditoriaRepository;
import org.springframework.stereotype.Service;

// Servicio central de auditoría (Épica 5, Historia de Log de auditoría) —
// se llama desde los flujos de: login/logout, alta/baja de usuarios, cambios
// de rol, cambios de estado de proyecto, propuesta/aprobación/rechazo de
// asignaciones (A11), activación de cuenta completada (C3), y cambios en
// parámetros de configuración (C10).
@Service
public class AuditoriaService {

    private final LogAuditoriaRepository logAuditoriaRepository;

    public AuditoriaService(LogAuditoriaRepository logAuditoriaRepository) {
        this.logAuditoriaRepository = logAuditoriaRepository;
    }

    // Uso general: acciones que no involucran un cambio de valor puntual
    // (crear, activar, aprobar, rechazar, login, etc.).
    public void registrar(Usuario usuario, String accion, String entidad, Long entidadId, String detalle) {
        registrar(usuario, accion, entidad, entidadId, detalle, null, null, null);
    }

    // Para cambios de configuración (C10) u otros donde interesa dejar el
    // valor anterior y el nuevo (p.ej. MAX_ASIGNACIONES_POR_COLABORADOR: 2 -> 3).
    public void registrar(Usuario usuario, String accion, String entidad, Long entidadId, String detalle,
                           String valorAnterior, String valorNuevo, String ip) {
        LogAuditoria log = new LogAuditoria();
        log.setUsuario(usuario); // null permitido: acciones automáticas del sistema (p.ej. envío de correo de activación).
        log.setAccion(accion);
        log.setEntidad(entidad);
        log.setEntidadId(entidadId);
        log.setDetalle(detalle);
        log.setValorAnterior(valorAnterior);
        log.setValorNuevo(valorNuevo);
        log.setIp(ip);
        logAuditoriaRepository.save(log);
    }
}
