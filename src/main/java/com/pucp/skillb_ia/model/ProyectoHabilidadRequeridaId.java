package com.pucp.skillb_ia.model;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ProyectoHabilidadRequeridaId implements Serializable {

    private Long proyecto;
    private Long habilidad;

    public ProyectoHabilidadRequeridaId() {}

    public ProyectoHabilidadRequeridaId(Long proyecto, Long habilidad) {
        this.proyecto = proyecto;
        this.habilidad = habilidad;
    }

    public Long getProyecto() { return proyecto; }
    public void setProyecto(Long proyecto) { this.proyecto = proyecto; }

    public Long getHabilidad() { return habilidad; }
    public void setHabilidad(Long habilidad) { this.habilidad = habilidad; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProyectoHabilidadRequeridaId that)) return false;
        return Objects.equals(proyecto, that.proyecto) && Objects.equals(habilidad, that.habilidad);
    }

    @Override
    public int hashCode() { return Objects.hash(proyecto, habilidad); }
}
