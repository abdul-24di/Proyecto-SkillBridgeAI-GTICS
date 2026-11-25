package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.security.UsuarioDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

// Decide qué proyectos puede ver el RM que tiene la sesión iniciada.
@Service
public class RmAlcanceService {

    // Devuelve el RM logueado o null si la sesión no es de un RM.
    public Usuario rmActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioDetails)) {
            return null;
        }
        Usuario usuario = ((UsuarioDetails) auth.getPrincipal()).getUsuario();
        return "RESOURCE_MANAGER".equals(usuario.getRol().getNombre()) ? usuario : null;
    }

    // Devuelve el id del RM logueado. Si no hay un RM
    // devuelve null y entonces no se filtra nada.
    public Long idRmActual() {
        Usuario rm = rmActual();
        return rm == null ? null : rm.getId();
    }

    // El RM ve los proyectos donde él es el responsable y los que aún no tienen responsable
    // (así puede tomarlos; ver RmResponsableService.validarResponsable).
    public boolean puedeVer(Proyecto proyecto, Long rmId) {
        if (rmId == null) {
            return true;
        }
        Usuario responsable = proyecto.getRmRevisor();
        return responsable == null || responsable.getId().equals(rmId);
    }


    // Para certificados, formaciones y cursos el RM ve lo que se le asignó a él. Lo que no tiene responsable
    // (datos anteriores al reparto o creados cuando no había un RM activo) lo puede revisar cualquier RM,
    // para que no quede sin revisión.
    public boolean puedeVerRevision(Usuario rmResponsable, Long rmId) {
        if (rmId == null) {
            return true;
        }
        return rmResponsable == null || rmResponsable.getId().equals(rmId);
    }


}