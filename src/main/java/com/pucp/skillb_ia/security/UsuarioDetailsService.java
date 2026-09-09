package com.pucp.skillb_ia.security;

import com.pucp.skillb_ia.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// El "username" de Spring Security es el correo (C4: identificador de login).
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    // @Transactional mantiene la sesión de Hibernate abierta mientras se
    // construye UsuarioDetails, para que la carga LAZY de usuario.getRol()
    // (dentro del constructor de UsuarioDetails) no falle.
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {
        return usuarioRepository.findByCorreo(correo)
                .map(UsuarioDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Correo o contraseña incorrectos."));
    }
}
