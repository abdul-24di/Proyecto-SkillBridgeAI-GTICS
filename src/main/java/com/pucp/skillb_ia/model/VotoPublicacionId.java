package com.pucp.skillb_ia.model;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class VotoPublicacionId implements Serializable {

    private Long usuario;
    private Long publicacion;

    public VotoPublicacionId() {}

    public VotoPublicacionId(Long usuario, Long publicacion) {
        this.usuario = usuario;
        this.publicacion = publicacion;
    }

    public Long getUsuario() { return usuario; }
    public void setUsuario(Long usuario) { this.usuario = usuario; }

    public Long getPublicacion() { return publicacion; }
    public void setPublicacion(Long publicacion) { this.publicacion = publicacion; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VotoPublicacionId that)) return false;
        return Objects.equals(usuario, that.usuario) && Objects.equals(publicacion, that.publicacion);
    }

    @Override
    public int hashCode() { return Objects.hash(usuario, publicacion); }
}
