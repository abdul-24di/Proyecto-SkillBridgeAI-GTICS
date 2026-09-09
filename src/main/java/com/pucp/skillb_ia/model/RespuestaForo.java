package com.pucp.skillb_ia.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

// El autor de la publicación puede marcar una respuesta como solución
// (es_solucion) — solo puede existir una activa por publicación; esa regla se
// aplica en el service.
@Entity
@Table(name = "respuesta_foro")
public class RespuestaForo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "publicacion_id", nullable = false)
    private PublicacionForo publicacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;

    @Lob
    @Column(nullable = false)
    private String contenido;

    @Column(name = "es_solucion", nullable = false)
    private boolean esSolucion = false;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @PrePersist
    protected void onCreate() {
        if (fechaCreacion == null) fechaCreacion = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PublicacionForo getPublicacion() { return publicacion; }
    public void setPublicacion(PublicacionForo publicacion) { this.publicacion = publicacion; }

    public Usuario getAutor() { return autor; }
    public void setAutor(Usuario autor) { this.autor = autor; }

    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }

    public boolean isEsSolucion() { return esSolucion; }
    public void setEsSolucion(boolean esSolucion) { this.esSolucion = esSolucion; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
}
