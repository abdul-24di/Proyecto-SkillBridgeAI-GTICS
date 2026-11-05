package com.pucp.skillb_ia.dto.pm;

import jakarta.validation.constraints.*;
import java.io.Serializable;

public class PmFinalizarAsignacionForm implements Serializable {
    @NotNull(message = "El proyecto es obligatorio.")
    private Long proyectoId;

    @Min(value = 1, message = "La calificación mínima es 1.")
    @Max(value = 5, message = "La calificación máxima es 5.")
    private Integer calificacion;

    @Size(max = 500, message = "El feedback no puede superar los 500 caracteres.")
    private String feedback;

    public Long getProyectoId() { return proyectoId; }
    public void setProyectoId(Long proyectoId) { this.proyectoId = proyectoId; }
    public Integer getCalificacion() { return calificacion; }
    public void setCalificacion(Integer calificacion) { this.calificacion = calificacion; }
    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
}
