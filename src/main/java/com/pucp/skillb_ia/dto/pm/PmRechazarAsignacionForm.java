package com.pucp.skillb_ia.dto.pm;

import jakarta.validation.constraints.*;
import java.io.Serializable;

public class PmRechazarAsignacionForm implements Serializable {
    @NotNull(message = "El proyecto es obligatorio.")
    private Long proyectoId;

    @NotBlank(message = "Debes indicar el motivo del rechazo.")
    @Size(max = 300, message = "El motivo del rechazo no puede superar los 300 caracteres.")
    private String motivo;

    public Long getProyectoId() { return proyectoId; }
    public void setProyectoId(Long proyectoId) { this.proyectoId = proyectoId; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
}
