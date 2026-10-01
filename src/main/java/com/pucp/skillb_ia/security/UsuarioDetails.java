package com.pucp.skillb_ia.security;

import com.pucp.skillb_ia.model.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

// Serializable: es el "principal" guardado en el SecurityContext, que Spring
// Session persiste en MySQL dentro de la sesión HTTP (clase 6.2).
public class UsuarioDetails implements UserDetails, Serializable {

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


    @Override
    public boolean isEnabled() { return usuario.isActivo() && usuario.getPasswordHash() != null; }
}
