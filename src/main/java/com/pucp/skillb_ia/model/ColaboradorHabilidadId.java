package com.pucp.skillb_ia.model;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ColaboradorHabilidadId implements Serializable {

    private Long colaborador;
    private Long habilidad;

    public ColaboradorHabilidadId() {}

    public ColaboradorHabilidadId(Long colaborador, Long habilidad) {
        this.colaborador = colaborador;
        this.habilidad = habilidad;
    }

    public Long getColaborador() { return colaborador; }
    public void setColaborador(Long colaborador) { this.colaborador = colaborador; }

    public Long getHabilidad() { return habilidad; }
    public void setHabilidad(Long habilidad) { this.habilidad = habilidad; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ColaboradorHabilidadId that)) return false;
        return Objects.equals(colaborador, that.colaborador) && Objects.equals(habilidad, that.habilidad);
    }

    @Override
    public int hashCode() { return Objects.hash(colaborador, habilidad); }
}
