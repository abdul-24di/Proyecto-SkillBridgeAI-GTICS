package com.pucp.skillb_ia.security;

import com.pucp.skillb_ia.model.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

// Envuelve la entidad Usuario para que Spring Security la entienda. El rol de
// la BD (p.ej. "PROJECT_MANAGER") se expone como autoridad "ROLE_PROJECT_MANAGER".
//
// Las autoridades se calculan UNA vez aquí en el constructor (no en cada
// llamada a getAuthorities()) porque usuario.getRol() es una relación LAZY:
// si se recalculara después, fuera de la sesión de Hibernate que cargó al
// usuario (p.ej. en el AuthenticationSuccessHandler), lanzaría
// LazyInitializationException. Construir esto mientras la sesión sigue
// abierta (dentro de UsuarioDetailsService, ver @Transactional ahí) evita el problema.
public class UsuarioDetails implements UserDetails {

    private final Usuario usuario;
    private final List<GrantedAuthority> authorities;

    public UsuarioDetails(Usuario usuario) {
        this.usuario = usuario;
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().getNombre()));
    }

    public Usuario getUsuario() { return usuario; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }

    @Override
    public String getPassword() { return usuario.getPasswordHash(); }

    @Override
    public String getUsername() { return usuario.getCorreo(); }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    // Cuenta desactivada por el Admin, o todavía sin activar (A6: password_hash
    // sigue NULL hasta que el usuario complete la activación) -> no puede loguear.
    @Override
    public boolean isEnabled() { return usuario.isActivo() && usuario.getPasswordHash() != null; }
}
