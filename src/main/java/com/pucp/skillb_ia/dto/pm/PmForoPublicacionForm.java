package com.pucp.skillb_ia.dto.pm;

import jakarta.validation.constraints.*;
import java.io.Serializable;

public class PmForoPublicacionForm implements Serializable {
    private Long proyectoId;
    private Long foroId;

    @NotBlank(message = "El título de la publicación es obligatorio.")
    @Size(max = 200, message = "El título no puede superar los 200 caracteres.")
    private String titulo;

    @NotBlank(message = "El contenido de la publicación es obligatorio.")
    @Size(max = 10000, message = "El contenido no puede superar los 10000 caracteres.")
    private String contenido;

    public Long getProyectoId() { return proyectoId; }
    public void setProyectoId(Long proyectoId) { this.proyectoId = proyectoId; }
    public Long getForoId() { return foroId; }
    public void setForoId(Long foroId) { this.foroId = foroId; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }
}
