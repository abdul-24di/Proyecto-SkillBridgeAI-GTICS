package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.model.LogAuditoria;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.LogAuditoriaRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;


/** Registra en el log de auditoría las acciones relevantes de todos los módulos. */
@Service
public class AuditoriaService {

    private final LogAuditoriaRepository logAuditoriaRepository;

    /** Crea el componente con sus dependencias, que Spring inyecta por el constructor. */
    public AuditoriaService(LogAuditoriaRepository logAuditoriaRepository) {
        this.logAuditoriaRepository = logAuditoriaRepository;
    }

    /**
     * Registra una acción sin valores anterior y nuevo.
     *
     * @param usuario autor de la acción; {@code null} si la hizo el sistema
     * @param accion código de la acción
     * @param entidad entidad afectada
     * @param entidadId id de la entidad; puede ser {@code null}
     * @param detalle descripción de lo ocurrido
     */
    public void registrar(Usuario usuario, String accion, String entidad, Long entidadId, String detalle) {
        registrar(usuario, accion, entidad, entidadId, detalle, null, null, null);
    }


    /**
     * Registra una acción con el valor anterior y el nuevo.
     *
     * @param usuario autor de la acción; {@code null} si la hizo el sistema
     * @param accion código de la acción
     * @param entidad entidad afectada
     * @param entidadId id de la entidad; puede ser {@code null}
     * @param detalle descripción de lo ocurrido
     * @param valorAnterior valor antes del cambio; puede ser {@code null}
     * @param valorNuevo valor después del cambio; puede ser {@code null}
     * @param ip dirección IP; si es {@code null} se toma de la petición actual
     */
    public void registrar(Usuario usuario, String accion, String entidad, Long entidadId, String detalle,
                           String valorAnterior, String valorNuevo, String ip) {
        LogAuditoria log = new LogAuditoria();
        log.setUsuario(usuario);
        log.setAccion(accion);
        log.setEntidad(entidad);
        log.setEntidadId(entidadId);
        log.setDetalle(detalle);
        log.setValorAnterior(valorAnterior);
        log.setValorNuevo(valorNuevo);
        log.setIp(ip != null ? ip : ipDeLaPeticionActual());
        logAuditoriaRepository.save(log);
    }

    /**
     * Obtiene la IP de la petición en curso.
     * <p>
     * IP de quien hizo la petición HTTP en curso. Fuera de una petición (tareas programadas, tests de servicio)
     * no hay IP y queda null.
     *
     * @return la IP o {@code null}
     */
    private String ipDeLaPeticionActual() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
            return atributos.getRequest().getRemoteAddr();
        }
        return null;
    }
}
