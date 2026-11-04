package com.pucp.skillb_ia.security;

import com.pucp.skillb_ia.model.LogAuditoria;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.LogAuditoriaRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// El "username" de Spring Security es el correo (C4: identificador de login).
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private static final int MAX_INTENTOS_LOGIN = 5;
    private static final int MINUTOS_BLOQUEO = 15;

    private final UsuarioRepository usuarioRepository;
    private final LogAuditoriaRepository logAuditoriaRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository, LogAuditoriaRepository logAuditoriaRepository) {
        this.usuarioRepository = usuarioRepository;
        this.logAuditoriaRepository = logAuditoriaRepository;
    }

    // @Transactional mantiene la sesión de Hibernate abierta mientras se
    // construye UsuarioDetails, para que la carga LAZY de usuario.getRol()
    // (dentro del constructor de UsuarioDetails) no falle.
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new UsernameNotFoundException("Correo o contraseña incorrectos."));
        if (estaBloqueada(usuario)) {
            // Spring Security revisa esto ANTES de comparar la contraseña, así que la cuenta queda protegida
            // aunque se pruebe la clave correcta y no se gasta CPU en BCrypt. Nunca llega a guardarse en sesión.
            return new UsuarioDetails(usuario) {
                @Override
                public boolean isAccountNonLocked() {
                    return false;
                }
            };
        }
        return new UsuarioDetails(usuario);
    }

    // Bloqueada: MAX_INTENTOS_LOGIN contraseñas incorrectas en los últimos minutos, sin contar las anteriores
    // a un inicio de sesión correcto o a un cambio de contraseña por recuperación.
    private boolean estaBloqueada(Usuario usuario) {
        LocalDateTime desde = LocalDateTime.now().minusMinutes(MINUTOS_BLOQUEO);
        LocalDateTime ultimoCorrecto = logAuditoriaRepository
                .findTopByUsuarioAndAccionInOrderByFechaHoraDesc(usuario, List.of("LOGIN", "RECUPERACION_CONTRASENA"))
                .map(LogAuditoria::getFechaHora)
                .orElse(desde);
        LocalDateTime contarDesde = ultimoCorrecto.isAfter(desde) ? ultimoCorrecto : desde;
        return logAuditoriaRepository.countByUsuarioAndAccionAndFechaHoraGreaterThanEqual(
                usuario, "LOGIN_FALLIDO", contarDesde) >= MAX_INTENTOS_LOGIN;
    }
}
