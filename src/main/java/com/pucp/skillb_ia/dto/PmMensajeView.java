package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Mensaje;
import java.time.format.DateTimeFormatter;

public class PmMensajeView {
    private final Long id;
    private final String autorNombre;
    private final String autorIniciales;
    private final String contenido;
    private final String hora;
    private final boolean esPropio;

    public PmMensajeView(Mensaje mensaje) {
        this.id = mensaje.getId();
        this.autorNombre = mensaje.getAutor().getNombre() + " " + mensaje.getAutor().getApellido();
        this.autorIniciales = iniciales(mensaje.getAutor().getNombre(), mensaje.getAutor().getApellido());
        this.contenido = mensaje.getContenido();
        this.hora = mensaje.getFechaHora().format(DateTimeFormatter.ofPattern("HH:mm"));
        // For simplicity, we assume the DTO is created in the context of the requesting user.
        // Wait, to know if it's their own message, we can pass the PM ID.
        // Or we can just include the autorId and let the frontend decide!
        this.esPropio = false; 
    }

    public PmMensajeView(Mensaje mensaje, Long currentUserId) {
        this.id = mensaje.getId();
        this.autorNombre = mensaje.getAutor().getNombre() + " " + mensaje.getAutor().getApellido();
        this.autorIniciales = iniciales(mensaje.getAutor().getNombre(), mensaje.getAutor().getApellido());
        this.contenido = mensaje.getContenido();
        this.hora = mensaje.getFechaHora().format(DateTimeFormatter.ofPattern("HH:mm"));
        this.esPropio = mensaje.getAutor().getId().equals(currentUserId);
    }

    private static String iniciales(String nombre, String apellido) {
        String n = (nombre != null && !nombre.isEmpty()) ? String.valueOf(nombre.charAt(0)) : "";
        String a = (apellido != null && !apellido.isEmpty()) ? String.valueOf(apellido.charAt(0)) : "";
        return (n + a).toUpperCase();
    }

    public Long getId() { return id; }
    public String getAutorNombre() { return autorNombre; }
    public String getAutorIniciales() { return autorIniciales; }
    public String getContenido() { return contenido; }
    public String getHora() { return hora; }
    public boolean isEsPropio() { return esPropio; }
}
