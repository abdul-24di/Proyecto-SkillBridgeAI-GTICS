package com.pucp.skillb_ia.security;

import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

// Login fallido: si el correo existe, se registra en la auditoría; esos registros son el contador con el
// que UsuarioDetailsService bloquea la cuenta unos minutos. Así no hizo falta cambiar la base de datos.
@Component
public class RegistroLoginFallidoHandler implements AuthenticationFailureHandler {

    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    public RegistroLoginFallidoHandler(UsuarioRepository usuarioRepository, AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.auditoriaService = auditoriaService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        if (exception instanceof LockedException) {
            response.sendRedirect(request.getContextPath() + "/login?bloqueado");
            return;
        }
        if (exception instanceof BadCredentialsException) {
            usuarioRepository.findByCorreo(request.getParameter("username")).ifPresent(usuario ->
                    auditoriaService.registrar(usuario, "LOGIN_FALLIDO", "USUARIO", usuario.getId(),
                            "Intento de inicio de sesión con contraseña incorrecta."));
        }
        response.sendRedirect(request.getContextPath() + "/login?error");
    }
}
