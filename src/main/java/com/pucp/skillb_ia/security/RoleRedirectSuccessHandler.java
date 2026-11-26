package com.pucp.skillb_ia.security;

import com.pucp.skillb_ia.service.AuditoriaService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * "El usuario es redirigido a la interfaz correspondiente a su rol después de iniciar sesión correctamente"
 * — criterio de la Historia de Inicio de sesión.
 */
@Component
public class RoleRedirectSuccessHandler implements AuthenticationSuccessHandler {

    private final AuditoriaService auditoriaService;

    /** Crea el componente con sus dependencias, que Spring inyecta por el constructor. */
    public RoleRedirectSuccessHandler(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    /**
     * Registra el inicio de sesión y lleva al panel del rol.
     *
     * @param request petición HTTP
     * @param response respuesta HTTP
     * @param authentication autenticación vigente
     * @throws IOException si falla la redirección
     * @throws ServletException si falla la petición
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException, ServletException {
        UsuarioDetails details = (UsuarioDetails) authentication.getPrincipal();
        auditoriaService.registrar(details.getUsuario(), "LOGIN", "USUARIO", details.getUsuario().getId(),
                "Inició sesión correctamente.");

        response.sendRedirect(request.getContextPath() + panelSegunRol(authentication));
    }

    /**
     * Devuelve la ruta del panel de un rol.
     * <p>
     * Panel de inicio de cada rol; también lo usa SesionActivaLoginFilter.
     *
     * @param authentication autenticación vigente
     * @return ruta del panel, o {@code /login} si el rol no se reconoce
     */
    public static String panelSegunRol(Authentication authentication) {
        String rol = authentication.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("");

        return switch (rol) {
            case "ROLE_ADMINISTRADOR" -> "/admin/dashboard";
            case "ROLE_PROJECT_MANAGER" -> "/pm/proyectos";
            case "ROLE_RESOURCE_MANAGER" -> "/rm/dashboard";
            case "ROLE_COLABORADOR" -> "/colaborador/dashboard";
            default -> "/login";
        };
    }
}
