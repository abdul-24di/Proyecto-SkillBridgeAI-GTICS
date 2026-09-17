package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.RespuestaForo;

public class ColRespuestaForoView {

    private final RespuestaForo respuesta;
    private final String autorNombre;
    private final String autorIniciales;
    private final long totalLikes;
    private final boolean meGusta;

    public ColRespuestaForoView(RespuestaForo respuesta, long totalLikes, boolean meGusta) {
        this.respuesta = respuesta;
        this.totalLikes = totalLikes;
        this.meGusta = meGusta;

        String nombre = respuesta.getAutor().getNombre();
        String apellido = respuesta.getAutor().getApellido();

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

    public RespuestaForo getRespuesta() { return respuesta; }
    public String getAutorNombre() { return autorNombre; }
    public String getAutorIniciales() { return autorIniciales; }
    public long getTotalLikes() { return totalLikes; }
    public boolean isMeGusta() { return meGusta; }
}