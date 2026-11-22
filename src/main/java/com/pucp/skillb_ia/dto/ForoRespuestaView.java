package com.pucp.skillb_ia.dto;

import java.time.LocalDateTime;

/**
 * Respuesta de una publicación de foro tal como la ven los 4 roles (fragmento
 * {@code fragments/foro-publicacion}). Los permisos ya vienen calculados por el servidor.
 */
public class ForoRespuestaView {
    private final Long id;
    private final Long autorId;
    private final String contenido;
    private final String autorNombre;
    private final String autorIniciales;
    private final String autorCargo;
    private final LocalDateTime fechaCreacion;
    private final boolean solucion;
    private final long totalLikes;
    private final boolean meGusta;
    private final boolean puedeDarLike;
    private final boolean puedeEliminar;
    private final boolean puedeMarcarSolucion;

    public ForoRespuestaView(Long id, Long autorId, String contenido, String autorNombre,
                             String autorIniciales, String autorCargo, LocalDateTime fechaCreacion,
                             boolean solucion, long totalLikes, boolean meGusta,
                             boolean puedeDarLike, boolean puedeEliminar, boolean puedeMarcarSolucion) {
        this.id = id;
        this.autorId = autorId;
        this.contenido = contenido;
        this.autorNombre = autorNombre;
        this.autorIniciales = autorIniciales;
        this.autorCargo = autorCargo;
        this.fechaCreacion = fechaCreacion;
        this.solucion = solucion;
        this.totalLikes = totalLikes;
        this.meGusta = meGusta;
        this.puedeDarLike = puedeDarLike;
        this.puedeEliminar = puedeEliminar;
        this.puedeMarcarSolucion = puedeMarcarSolucion;
    }

    public Long getId() { return id; }
    public Long getAutorId() { return autorId; }
    /** HTML ya saneado al guardarse ({@code HtmlSeguro}); se muestra con th:utext. */
    public String getContenido() { return contenido; }
    public String getAutorNombre() { return autorNombre; }
    public String getAutorIniciales() { return autorIniciales; }
    public String getAutorCargo() { return autorCargo; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public boolean isSolucion() { return solucion; }
    public long getTotalLikes() { return totalLikes; }
    public boolean isMeGusta() { return meGusta; }
    public boolean isPuedeDarLike() { return puedeDarLike; }
    public boolean isPuedeEliminar() { return puedeEliminar; }
    public boolean isPuedeMarcarSolucion() { return puedeMarcarSolucion; }
}
