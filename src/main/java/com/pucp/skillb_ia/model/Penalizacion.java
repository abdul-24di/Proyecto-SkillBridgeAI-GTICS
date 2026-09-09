package com.pucp.skillb_ia.model;

import com.pucp.skillb_ia.model.enums.TipoPenalizacion;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Épica 9 — Tier 3, diferida. Bloque B punto 7: si el colaborador es removido
// a mitad de mes por bajo desempeño se penaliza; si es porque el proyecto se
// canceló, no. La fórmula exacta del `monto` sigue sin definir
// (consideraciones_bd_v4.md, Parte 2.C).
@Entity
@Table(name = "penalizacion")
public class Penalizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "colaborador_id", nullable = false)
    private Usuario colaborador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proyecto_id")
    private Proyecto proyecto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actividad_id")
    private Actividad actividad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoPenalizacion tipo;

    @Column(nullable = false, length = 500)
    private String motivo;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha;

    @PrePersist
    protected void onCreate() {
        if (fecha == null) fecha = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Usuario getColaborador() { return colaborador; }
    public void setColaborador(Usuario colaborador) { this.colaborador = colaborador; }

    public Proyecto getProyecto() { return proyecto; }
    public void setProyecto(Proyecto proyecto) { this.proyecto = proyecto; }

    public Actividad getActividad() { return actividad; }
    public void setActividad(Actividad actividad) { this.actividad = actividad; }

    public TipoPenalizacion getTipo() { return tipo; }
    public void setTipo(TipoPenalizacion tipo) { this.tipo = tipo; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
