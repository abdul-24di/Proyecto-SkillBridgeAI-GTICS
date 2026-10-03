package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.PublicacionForo;

import java.util.List;

public class ColPublicacionForoView {

    private final PublicacionForo publicacion;
    private final List<ColRespuestaForoView> respuestas;
    private final String autorNombre;
    private final String autorIniciales;
    private final long totalLikes;
    private final boolean meGusta;

    public ColPublicacionForoView(PublicacionForo publicacion, List<ColRespuestaForoView> respuestas,
                                  long totalLikes, boolean meGusta) {
        this.publicacion = publicacion;
        this.respuestas = respuestas;
        this.totalLikes = totalLikes;
        this.meGusta = meGusta;

        String nombre = publicacion.getAutor().getNombre();
        String apellido = publicacion.getAutor().getApellido();

        String nombreCompleto = "";
        if (nombre != null) {
            nombreCompleto = nombre;
        }
        if (apellido != null) {
            nombreCompleto = (nombreCompleto + " " + apellido).trim();
        }
        if (nombreCompleto.isEmpty()) {
            nombreCompleto = "Usuario";
        }
        this.autorNombre = nombreCompleto;

        String iniciales = "";
        if (nombre != null && !nombre.isEmpty()) {
            iniciales = iniciales + nombre.substring(0, 1);
        }
        if (apellido != null && !apellido.isEmpty()) {
            iniciales = iniciales + apellido.substring(0, 1);
        }
        if (iniciales.isEmpty()) {
            iniciales = "US";
        }
        this.autorIniciales = iniciales.toUpperCase();
    }

    public PublicacionForo getPublicacion() { return publicacion; }
    public List<ColRespuestaForoView> getRespuestas() { return respuestas; }
    public int getTotalRespuestas() { return respuestas.size(); }
    public String getAutorNombre() { return autorNombre; }
    public String getAutorIniciales() { return autorIniciales; }
    public long getTotalLikes() { return totalLikes; }
    public boolean isMeGusta() { return meGusta; }


    //Cuando una de las respuestas esté marcada como activa entonces la publicación quedará resuelta.
    public boolean isResuelta() {
        for (ColRespuestaForoView respuestaVista : respuestas) {
            if (respuestaVista.getRespuesta().isEsSolucion()) {
                return true;
            }
        }
        return false;
    }
}