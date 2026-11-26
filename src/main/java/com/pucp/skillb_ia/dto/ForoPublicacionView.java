package com.pucp.skillb_ia.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Publicación de foro con sus respuestas, igual para RM, PM, Colaborador y Admin
 * (fragmento {@code fragments/foro-publicacion}). Los permisos ya vienen calculados por el servidor.
 */
public class ForoPublicacionView {
    private final Long id;
    private final Long autorId;
    private final String titulo;
    private final String contenido;
    private final String autorNombre;
    private final String autorIniciales;
    private final String autorCargo;
    private final String etiqueta;
    private final LocalDateTime fechaCreacion;
    private final long totalLikes;
    private final boolean meGusta;
    private final List<ForoRespuestaView> respuestas;
    private final boolean puedeDarLike;
    private final boolean puedeResponder;
    private final boolean puedeEliminar;
    private final boolean anuncio;

    public ForoPublicacionView(Long id, Long autorId, String titulo, String contenido,
                               String autorNombre, String autorIniciales, String autorCargo,
                               String etiqueta, LocalDateTime fechaCreacion, long totalLikes,
                               boolean meGusta, List<ForoRespuestaView> respuestas,
                               boolean puedeDarLike, boolean puedeResponder, boolean puedeEliminar,
                               boolean anuncio) {
        this.id = id;
        this.autorId = autorId;
        this.titulo = titulo;
        this.contenido = contenido;
        this.autorNombre = autorNombre;
        this.autorIniciales = autorIniciales;
        this.autorCargo = autorCargo;
        this.etiqueta = etiqueta;
        this.fechaCreacion = fechaCreacion;
        this.totalLikes = totalLikes;
        this.meGusta = meGusta;
        this.respuestas = List.copyOf(respuestas);
        this.puedeDarLike = puedeDarLike;
        this.puedeResponder = puedeResponder;
        this.puedeEliminar = puedeEliminar;
        this.anuncio = anuncio;
    }

    public Long getId() { return id; }
    public Long getAutorId() { return autorId; }
    public String getTitulo() { return titulo; }
    /** HTML ya saneado al guardarse ({@code HtmlSeguro}); se muestra con th:utext. */
    public String getContenido() { return contenido; }
    public String getAutorNombre() { return autorNombre; }
    public String getAutorIniciales() { return autorIniciales; }
    public String getAutorCargo() { return autorCargo; }
    public String getEtiqueta() { return etiqueta; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public long getTotalLikes() { return totalLikes; }
    public boolean isMeGusta() { return meGusta; }
    public List<ForoRespuestaView> getRespuestas() { return respuestas; }
    public int getTotalRespuestas() { return respuestas.size(); }
    public boolean isResuelta() { return respuestas.stream().anyMatch(ForoRespuestaView::isSolucion); }
    public boolean isPuedeDarLike() { return puedeDarLike; }
    public boolean isPuedeResponder() { return puedeResponder; }
    public boolean isPuedeEliminar() { return puedeEliminar; }
    /** Publicación de un foro de anuncios: no tiene respuestas. */
    public boolean isAnuncio() { return anuncio; }
}
