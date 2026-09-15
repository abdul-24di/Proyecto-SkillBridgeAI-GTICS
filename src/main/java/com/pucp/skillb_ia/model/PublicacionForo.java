package com.pucp.skillb_ia.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

// Una publicación tiene UNA sola etiqueta (FK directa) — interpretación de la
// observación del profesor sobre simplificar la relación N:M original; a
// confirmar con él (consideraciones_bd_v4.md, Parte 2.A).
@Entity
@Table(name = "publicacion_foro")
public class PublicacionForo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "foro_id", nullable = false)
    private Foro foro;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etiqueta_id")
    private Etiqueta etiqueta;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Lob
    @Column(nullable = false)
    private String contenido;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @PrePersist
    protected void onCreate() {
        if (fechaCreacion == null) fechaCreacion = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Foro getForo() { return foro; }
    public void setForo(Foro foro) { this.foro = foro; }

    public Usuario getAutor() { return autor; }
    public void setAutor(Usuario autor) { this.autor = autor; }

    public Etiqueta getEtiqueta() { return etiqueta; }
    public void setEtiqueta(Etiqueta etiqueta) { this.etiqueta = etiqueta; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
}
