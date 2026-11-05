package com.pucp.skillb_ia.dto.pm;

import jakarta.validation.constraints.*;
import java.io.Serializable;

public class PmForoRespuestaForm implements Serializable {
    @NotNull(message = "La publicación es obligatoria.")
    private Long publicacionId;

    private Long proyectoId;
    private Long foroId;

    @NotBlank(message = "El contenido de la respuesta no puede estar vacío.")
    @Size(max = 5000, message = "La respuesta no puede superar los 5000 caracteres.")
    private String contenido;

    public Long getPublicacionId() { return publicacionId; }
    public void setPublicacionId(Long publicacionId) { this.publicacionId = publicacionId; }
    public Long getProyectoId() { return proyectoId; }
    public void setProyectoId(Long proyectoId) { this.proyectoId = proyectoId; }
    public Long getForoId() { return foroId; }
    public void setForoId(Long foroId) { this.foroId = foroId; }
    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }
}
