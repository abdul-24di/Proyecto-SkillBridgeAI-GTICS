package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.security.UsuarioDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Decide qué proyectos y revisiones puede ver el Resource Manager que tiene la sesión iniciada.
 * La usan los servicios de consulta del RM para filtrar listados y validar accesos por id.
 */
@Service
public class RmAlcanceService {

    /**
     * Obtiene el usuario autenticado si su rol es {@code RESOURCE_MANAGER}.
     *
     * @return el RM de la sesión, o {@code null} si no hay sesión o el usuario tiene otro rol
     */
    public Usuario rmActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioDetails)) {
            return null;
        }
        Usuario usuario = ((UsuarioDetails) auth.getPrincipal()).getUsuario();
        return "RESOURCE_MANAGER".equals(usuario.getRol().getNombre()) ? usuario : null;
    }

    /**
     * Obtiene el id del RM de la sesión. Si no hay un RM (por ejemplo, en un proceso interno),
     * devuelve {@code null} y los métodos {@code puedeVer*} no filtran nada.
     *
     * @return id del RM autenticado o {@code null}
     */
    public Long idRmActual() {
        Usuario rm = rmActual();
        return rm == null ? null : rm.getId();
    }

    /**
     * Indica si el RM puede ver un proyecto: los que tiene a cargo y los que aún no tienen
     * responsable, para poder tomarlos (ver {@link RmResponsableService#validarResponsable}).
     *
     * @param proyecto proyecto a evaluar
     * @param rmId id del RM; {@code null} permite ver todo
     * @return {@code true} si el proyecto es visible para el RM
     */
    public boolean puedeVer(Proyecto proyecto, Long rmId) {
        if (rmId == null) {
            return true;
        }
        Usuario responsable = proyecto.getRmRevisor();
        return responsable == null || responsable.getId().equals(rmId);
    }


    /**
     * Indica si el RM puede ver una revisión de certificado, formación o curso. Ve lo que se le asignó;
     * lo que no tiene responsable (datos anteriores al reparto o creados sin un RM activo) lo puede
     * revisar cualquier RM, para que no quede sin revisión.
     *
     * @param rmResponsable RM asignado a la revisión, o {@code null}
     * @param rmId id del RM; {@code null} permite ver todo
     * @return {@code true} si la revisión es visible para el RM
     */
    public boolean puedeVerRevision(Usuario rmResponsable, Long rmId) {
        if (rmId == null) {
            return true;
        }
        return rmResponsable == null || rmResponsable.getId().equals(rmId);
    }


}