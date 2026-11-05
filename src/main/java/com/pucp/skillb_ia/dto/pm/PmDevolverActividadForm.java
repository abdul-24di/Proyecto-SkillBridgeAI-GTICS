package com.pucp.skillb_ia.dto.pm;

import jakarta.validation.constraints.*;
import java.io.Serializable;

public class PmDevolverActividadForm implements Serializable {
    @NotNull(message = "El proyecto es obligatorio.")
    private Long proyectoId;

    @NotBlank(message = "Debes escribir un comentario para devolver la actividad.")
    @Size(max = 500, message = "El comentario no puede exceder los 500 caracteres.")
    private String comentario;

    private boolean aplicarStrike;

    public Long getProyectoId() { return proyectoId; }
    public void setProyectoId(Long proyectoId) { this.proyectoId = proyectoId; }
    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }
    public boolean isAplicarStrike() { return aplicarStrike; }
    public void setAplicarStrike(boolean aplicarStrike) { this.aplicarStrike = aplicarStrike; }
}
