package com.pucp.skillb_ia.model;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class VotoRespuestaId implements Serializable {

    private Long usuario;
    private Long respuesta;

    public VotoRespuestaId() {}

    public VotoRespuestaId(Long usuario, Long respuesta) {
        this.usuario = usuario;
        this.respuesta = respuesta;
    }

    public Long getUsuario() { return usuario; }
    public void setUsuario(Long usuario) { this.usuario = usuario; }

    public Long getRespuesta() { return respuesta; }
    public void setRespuesta(Long respuesta) { this.respuesta = respuesta; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VotoRespuestaId that)) return false;
        return Objects.equals(usuario, that.usuario) && Objects.equals(respuesta, that.respuesta);
    }

    @Override
    public int hashCode() { return Objects.hash(usuario, respuesta); }
}
