package com.pucp.skillb_ia.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

// Si ya hay una sesión iniciada, /login (ver el formulario o enviarlo) redirige al panel del rol actual.
// Así no se puede iniciar sesión con otra cuenta "encima" de la activa: primero hay que cerrar sesión.
// Va dentro de la cadena de Spring Security (SecurityConfig), antes del filtro que procesa el POST /login.
public class SesionActivaLoginFilter extends OncePerRequestFilter {

    private static final Set<String> RUTAS_DE_LOGIN = Set.of("/", "/login", "/login.html");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (RUTAS_DE_LOGIN.contains(ruta) && auth != null && auth.getPrincipal() instanceof UsuarioDetails) {
            response.sendRedirect(request.getContextPath() + RoleRedirectSuccessHandler.panelSegunRol(auth));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
