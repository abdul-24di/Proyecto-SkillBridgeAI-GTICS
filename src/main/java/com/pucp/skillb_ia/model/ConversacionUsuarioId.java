package com.pucp.skillb_ia.model;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ConversacionUsuarioId implements Serializable {

    private Long conversacion;
    private Long usuario;

    public ConversacionUsuarioId() {}

    public ConversacionUsuarioId(Long conversacion, Long usuario) {
        this.conversacion = conversacion;
        this.usuario = usuario;
    }

    public Long getConversacion() { return conversacion; }
    public void setConversacion(Long conversacion) { this.conversacion = conversacion; }

    public Long getUsuario() { return usuario; }
    public void setUsuario(Long usuario) { this.usuario = usuario; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConversacionUsuarioId that)) return false;
        return Objects.equals(conversacion, that.conversacion) && Objects.equals(usuario, that.usuario);
    }

    @Override
    public int hashCode() { return Objects.hash(conversacion, usuario); }
}
