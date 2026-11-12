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

    // Devuelve el id del RM logueado. Si no hay un RM
    // devuelve null y entonces no se filtra nada.
    public Long idRmActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioDetails)) {
            return null;
        }
        Usuario usuario = ((UsuarioDetails) auth.getPrincipal()).getUsuario();
        if (!"RESOURCE_MANAGER".equals(usuario.getRol().getNombre())) {
            return null;
        }
        return usuario.getId();
    }

    // El RM solo puede ver los proyectos donde él es el responsable.
    public boolean puedeVer(Proyecto proyecto, Long rmId) {
        if (rmId == null) {
            return true;
        }
        Usuario responsable = proyecto.getRmRevisor();
        return responsable != null && responsable.getId().equals(rmId);
    }


    // Para certificados, formaciones y cursos el RM solo ve lo que se le asignó a él
    public boolean puedeVerRevision(Usuario rmResponsable, Long rmId) {
        if (rmId == null) {
            return true;
        }
        return rmResponsable != null && rmResponsable.getId().equals(rmId);
    }


}