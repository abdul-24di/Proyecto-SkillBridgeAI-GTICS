package com.pucp.skillb_ia.model;

import com.pucp.skillb_ia.model.enums.NivelDominio;
import jakarta.persistence.*;

// Selector múltiple de habilidades requeridas al crear el proyecto (Épica 4,
// Historia de Gestión de información del proyecto). Alimenta el % de
// habilidades cubiertas del dashboard del PM (Épica 6) y el AI Talent Matching.
@Entity
@Table(name = "proyecto_habilidad_requerida")
public class ProyectoHabilidadRequerida {

    @EmbeddedId
    private ProyectoHabilidadRequeridaId id = new ProyectoHabilidadRequeridaId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("proyecto")
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("habilidad")
    @JoinColumn(name = "habilidad_id", nullable = false)
    private Habilidad habilidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_requerido", length = 20)
    private NivelDominio nivelRequerido;

    @Column(name = "cantidad_personas", nullable = false)
    private int cantidadPersonas = 1;

    @Column(name = "horas_semanales", precision = 5, scale = 2)
    private java.math.BigDecimal horasSemanales = new java.math.BigDecimal("20.00");

    public ProyectoHabilidadRequeridaId getId() { return id; }
    public void setId(ProyectoHabilidadRequeridaId id) { this.id = id; }

    public Proyecto getProyecto() { return proyecto; }
    public void setProyecto(Proyecto proyecto) { this.proyecto = proyecto; }

    public Habilidad getHabilidad() { return habilidad; }
    public void setHabilidad(Habilidad habilidad) { this.habilidad = habilidad; }

    public NivelDominio getNivelRequerido() { return nivelRequerido; }
    public void setNivelRequerido(NivelDominio nivelRequerido) { this.nivelRequerido = nivelRequerido; }

    public int getCantidadPersonas() { return cantidadPersonas; }
    public void setCantidadPersonas(int cantidadPersonas) { this.cantidadPersonas = cantidadPersonas; }
    public java.math.BigDecimal getHorasSemanales() { return horasSemanales; }
    public void setHorasSemanales(java.math.BigDecimal horasSemanales) { this.horasSemanales = horasSemanales; }
}
