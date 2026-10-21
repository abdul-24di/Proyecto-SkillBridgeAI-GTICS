package com.pucp.skillb_ia.security;

import com.pucp.skillb_ia.service.AuditoriaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import java.io.IOException;

// Un usuario con sesión intenta entrar a una zona que no corresponde a su rol (p. ej. un Colaborador a
// /admin/**). Se registra el intento en la auditoría y se responde 403, que Spring Boot muestra con la
// página templates/error/403.html. Sin sesión no se llega aquí: Spring Security redirige al login.
@Component
public class RegistroAccesoDenegadoHandler implements AccessDeniedHandler {

    private final AuditoriaService auditoriaService;

    public RegistroAccesoDenegadoHandler(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        // Un formulario sin token CSRF válido (p. ej. la sesión expiró con la página abierta) también llega aquí,
        // pero no es un problema de rol: se registra aparte y la página 403 muestra otro mensaje.
        boolean csrfInvalido = accessDeniedException instanceof CsrfException;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioDetails details) {
            String ruta = request.getMethod() + " " + request.getRequestURI();
            auditoriaService.registrar(details.getUsuario(), "ACCESO_DENEGADO", "RUTA", null, csrfInvalido
                    ? "Petición rechazada por token CSRF ausente o inválido: " + ruta
                    : "Intentó acceder a una ruta no permitida para su rol: " + ruta);
        }
        request.setAttribute("csrfInvalido", csrfInvalido);
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }
}
