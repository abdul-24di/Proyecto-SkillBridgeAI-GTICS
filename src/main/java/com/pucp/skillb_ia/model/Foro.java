package com.pucp.skillb_ia.model;

import com.pucp.skillb_ia.model.enums.TipoForo;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "foro")
public class Foro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proyecto_id")
    private Proyecto proyecto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TipoForo tipo;

    @Column(name = "es_publico", nullable = false)
    private boolean esPublico = true;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @PrePersist
    protected void onCreate() {
        if (fechaCreacion == null) fechaCreacion = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Proyecto getProyecto() { return proyecto; }
    public void setProyecto(Proyecto proyecto) { this.proyecto = proyecto; }

    public TipoForo getTipo() { return tipo; }
    public void setTipo(TipoForo tipo) { this.tipo = tipo; }

    public boolean isEsPublico() { return esPublico; }
    public void setEsPublico(boolean esPublico) { this.esPublico = esPublico; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
}
