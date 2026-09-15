package com.pucp.skillb_ia.model;

import com.pucp.skillb_ia.model.enums.TipoConversacion;
import jakarta.persistence.*;

import java.time.LocalDateTime;

// El chat del proyecto es GRUPAL — "miembros" = colaboradores con asignación
// activa + el PM del proyecto. El RM NO tiene acceso al chat (A9), a
// diferencia del foro (A7).
@Entity
@Table(name = "conversacion")
public class Conversacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TipoConversacion tipo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @PrePersist
    protected void onCreate() {
        if (fechaCreacion == null) fechaCreacion = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public TipoConversacion getTipo() { return tipo; }
    public void setTipo(TipoConversacion tipo) { this.tipo = tipo; }

    public Proyecto getProyecto() { return proyecto; }
    public void setProyecto(Proyecto proyecto) { this.proyecto = proyecto; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
}
