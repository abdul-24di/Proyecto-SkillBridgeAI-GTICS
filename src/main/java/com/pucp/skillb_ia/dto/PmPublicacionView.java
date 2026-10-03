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

    public PmPublicacionView(PublicacionForo publicacion, List<RespuestaForo> respuestas) {
        this.publicacion = publicacion;
        String nombre = publicacion.getAutor().getNombre();
        String apellido = publicacion.getAutor().getApellido();
        this.autorNombre = nombre + " " + apellido;
        this.autorIniciales = iniciales(nombre, apellido);
        this.respuestas = respuestas.stream().map(RespuestaView::new).toList();
        this.totalRespuestas = respuestas.size();
    }

    public PublicacionForo getPublicacion() { return publicacion; }
    public String getAutorNombre() { return autorNombre; }
    public String getAutorIniciales() { return autorIniciales; }
    public int getTotalRespuestas() { return totalRespuestas; }
    public List<RespuestaView> getRespuestas() { return respuestas; }
    public LocalDateTime getFechaCreacion() { return publicacion.getFechaCreacion(); }

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

        public RespuestaView(RespuestaForo respuesta) {
            this.respuesta = respuesta;
            String nombre = respuesta.getAutor().getNombre();
            String apellido = respuesta.getAutor().getApellido();
            this.autorNombre = nombre + " " + apellido;
            this.autorIniciales = iniciales(nombre, apellido);
        }

        public RespuestaForo getRespuesta() { return respuesta; }
        public String getAutorNombre() { return autorNombre; }
        public String getAutorIniciales() { return autorIniciales; }
        public boolean isEsSolucion() { return respuesta.isEsSolucion(); }
    }

    private static String iniciales(String nombre, String apellido) {
        String n = (nombre != null && !nombre.isEmpty()) ? String.valueOf(nombre.charAt(0)) : "";
        String a = (apellido != null && !apellido.isEmpty()) ? String.valueOf(apellido.charAt(0)) : "";
        return (n + a).toUpperCase();
    }
}
