package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.PublicacionForo;
import com.pucp.skillb_ia.model.RespuestaForo;

import java.time.LocalDateTime;
import java.util.List;

/** Vista del detalle de una publicación en el foro del PM. */
public class PmPublicacionView {

    private final PublicacionForo publicacion;
    private final String autorNombre;
    private final String autorIniciales;
    private final int totalRespuestas;
    private final List<RespuestaView> respuestas;
    private final long totalLikes;
    private final boolean meGusta;

    public PmPublicacionView(PublicacionForo publicacion, List<RespuestaView> respuestas, long totalLikes, boolean meGusta) {
        this.publicacion = publicacion;
        String nombre = publicacion.getAutor().getNombre();
        String apellido = publicacion.getAutor().getApellido();
        this.autorNombre = (nombre + " " + apellido).trim();
        this.autorIniciales = iniciales(nombre, apellido);
        this.respuestas = respuestas;
        this.totalRespuestas = respuestas.size();
        this.totalLikes = totalLikes;
        this.meGusta = meGusta;
    }

    public PublicacionForo getPublicacion() { return publicacion; }
    public String getAutorNombre() { return autorNombre; }
    public String getAutorIniciales() { return autorIniciales; }
    public int getTotalRespuestas() { return totalRespuestas; }
    public List<RespuestaView> getRespuestas() { return respuestas; }
    public LocalDateTime getFechaCreacion() { return publicacion.getFechaCreacion(); }
    public long getTotalLikes() { return totalLikes; }
    public boolean isMeGusta() { return meGusta; }

    public boolean isResuelta() {
        for (RespuestaView r : respuestas) {
            if (r.isEsSolucion()) return true;
        }
        return false;
    }

    public static class RespuestaView {
        private final RespuestaForo respuesta;
        private final String autorNombre;
        private final String autorIniciales;
        private final long totalLikes;
        private final boolean meGusta;

        public RespuestaView(RespuestaForo respuesta, long totalLikes, boolean meGusta) {
            this.respuesta = respuesta;
            String nombre = respuesta.getAutor().getNombre();
            String apellido = respuesta.getAutor().getApellido();
            this.autorNombre = (nombre + " " + apellido).trim();
            this.autorIniciales = iniciales(nombre, apellido);
            this.totalLikes = totalLikes;
            this.meGusta = meGusta;
        }

        public RespuestaForo getRespuesta() { return respuesta; }
        public String getAutorNombre() { return autorNombre; }
        public String getAutorIniciales() { return autorIniciales; }
        public boolean isEsSolucion() { return respuesta.isEsSolucion(); }
        public long getTotalLikes() { return totalLikes; }
        public boolean isMeGusta() { return meGusta; }
    }

    private static String iniciales(String nombre, String apellido) {
        String n = (nombre != null && !nombre.isEmpty()) ? String.valueOf(nombre.charAt(0)) : "";
        String a = (apellido != null && !apellido.isEmpty()) ? String.valueOf(apellido.charAt(0)) : "";
        String res = (n + a).toUpperCase();
        return res.isEmpty() ? "US" : res;
    }
}
