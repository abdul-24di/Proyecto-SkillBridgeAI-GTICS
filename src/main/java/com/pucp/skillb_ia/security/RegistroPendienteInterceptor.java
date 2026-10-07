package com.pucp.skillb_ia.security;

import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoRegistro;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

// Mientras el pre-registro de un colaborador esté pendiente o rechazado, solo puede ver la
// pantalla "Registro en revisión". Lee el estado de la BD en cada petición porque el usuario
// guardado en la sesión no se entera cuando el Admin lo aprueba.
@Component
public class RegistroPendienteInterceptor implements HandlerInterceptor {

    private final UsuarioRepository usuarioRepository;

    public RegistroPendienteInterceptor(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioDetails details) {
            Usuario actual = usuarioRepository.findById(details.getUsuario().getId()).orElse(null);
            if (actual != null && (actual.getRegistroEstado() == EstadoRegistro.PENDIENTE
                    || actual.getRegistroEstado() == EstadoRegistro.RECHAZADO)) {
                response.sendRedirect(request.getContextPath() + "/colaborador/registro");
                return false;
            }
        }
        return true;
    }
}
