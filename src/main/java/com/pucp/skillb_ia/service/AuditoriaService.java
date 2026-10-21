package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.model.LogAuditoria;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.LogAuditoriaRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;


@Service
public class AuditoriaService {

    private final LogAuditoriaRepository logAuditoriaRepository;

    public AuditoriaService(LogAuditoriaRepository logAuditoriaRepository) {
        this.logAuditoriaRepository = logAuditoriaRepository;
    }

    public void registrar(Usuario usuario, String accion, String entidad, Long entidadId, String detalle) {
        registrar(usuario, accion, entidad, entidadId, detalle, null, null, null);
    }


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

    // IP de quien hizo la petición HTTP en curso. Fuera de una petición (tareas programadas, tests de
    // servicio) no hay IP y queda null.
    private String ipDeLaPeticionActual() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
            return atributos.getRequest().getRemoteAddr();
        }
        return null;
    }
}
