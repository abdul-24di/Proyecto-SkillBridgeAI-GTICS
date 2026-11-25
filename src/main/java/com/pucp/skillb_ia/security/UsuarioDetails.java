package com.pucp.skillb_ia.security;

import com.pucp.skillb_ia.model.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * Serializable: es el "principal" guardado en el SecurityContext, que Spring Session persiste en MySQL dentro
 * de la sesión HTTP (clase 6.2).
 */
public class UsuarioDetails implements UserDetails, Serializable {

    private final Usuario usuario;
    private final List<GrantedAuthority> authorities;

    /** Adapta un usuario del sistema a Spring Security. */
    public UsuarioDetails(Usuario usuario) {
        this.usuario = usuario;
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().getNombre()));
    }

    /**
     * Devuelve el usuario autenticado.
     *
     * @return el usuario
     */
    public Usuario getUsuario() { return usuario; }

    /**
     * Devuelve el rol con el prefijo {@code ROLE_}.
     *
     * @return autoridad del usuario
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }

    /**
     * Devuelve la contraseña cifrada.
     *
     * @return hash BCrypt
     */
    @Override
    public String getPassword() { return usuario.getPasswordHash(); }

    /**
     * Devuelve el correo, que es el identificador de acceso.
     *
     * @return el correo
     */
    @Override
    public String getUsername() { return usuario.getCorreo(); }

    /**
     * Las cuentas no caducan.
     *
     * @return siempre {@code true}
     */
    @Override
    public boolean isAccountNonExpired() { return true; }

    /**
     * El bloqueo temporal lo resuelve {@link UsuarioDetailsService}.
     *
     * @return {@code true}
     */
    @Override
    public boolean isAccountNonLocked() { return true; }

    /**
     * Las contraseñas no caducan.
     *
     * @return siempre {@code true}
     */
    @Override
    public boolean isCredentialsNonExpired() { return true; }


    /**
     * Indica si la cuenta puede ingresar.
     *
     * @return {@code true} si está activa y ya tiene contraseña
     */
    @Override
    public boolean isEnabled() { return usuario.isActivo() && usuario.getPasswordHash() != null; }
}
