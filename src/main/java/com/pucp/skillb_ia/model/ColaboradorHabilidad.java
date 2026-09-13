package com.pucp.skillb_ia.model;

import com.pucp.skillb_ia.model.enums.EstadoValidacion;
import com.pucp.skillb_ia.model.enums.NivelDominio;
import jakarta.persistence.*;


@Entity
@Table(name = "colaborador_habilidad")
public class ColaboradorHabilidad {

    @EmbeddedId
    private ColaboradorHabilidadId id = new ColaboradorHabilidadId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("colaborador")
    @JoinColumn(name = "colaborador_id", nullable = false)
    private Usuario colaborador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("habilidad")
    @JoinColumn(name = "habilidad_id", nullable = false)
    private Habilidad habilidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_dominio", nullable = false, length = 20)
    private NivelDominio nivelDominio;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_validacion", nullable = false, length = 20)
    private EstadoValidacion estadoValidacion = EstadoValidacion.PENDIENTE;

    @Column(nullable = false)
    private boolean activo = true;

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public ColaboradorHabilidadId getId() { return id; }
    public void setId(ColaboradorHabilidadId id) { this.id = id; }

    public Usuario getColaborador() { return colaborador; }
    public void setColaborador(Usuario colaborador) { this.colaborador = colaborador; }

    public Habilidad getHabilidad() { return habilidad; }
    public void setHabilidad(Habilidad habilidad) { this.habilidad = habilidad; }

    public NivelDominio getNivelDominio() { return nivelDominio; }
    public void setNivelDominio(NivelDominio nivelDominio) { this.nivelDominio = nivelDominio; }

    public EstadoValidacion getEstadoValidacion() { return estadoValidacion; }
    public void setEstadoValidacion(EstadoValidacion estadoValidacion) { this.estadoValidacion = estadoValidacion; }
}