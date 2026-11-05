package com.pucp.skillb_ia.dto.pm;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;

public class PmProponerAsignacionForm implements Serializable {
    @NotNull(message = "El proyecto es obligatorio.")
    private Long proyectoId;

    @NotNull(message = "Debes seleccionar un colaborador.")
    private Long colaboradorId;

    @NotNull(message = "Las horas semanales son obligatorias.")
    @DecimalMin(value = "0.5", message = "Las horas semanales deben ser al menos 0.5.")
    @DecimalMax(value = "168.0", message = "Las horas semanales no pueden superar 168.")
    private BigDecimal horasSemanales = new BigDecimal("16");

    @Size(max = 500, message = "La justificación no puede superar los 500 caracteres.")
    private String mensajeSolicitud;

    public Long getProyectoId() { return proyectoId; }
    public void setProyectoId(Long proyectoId) { this.proyectoId = proyectoId; }
    public Long getColaboradorId() { return colaboradorId; }
    public void setColaboradorId(Long colaboradorId) { this.colaboradorId = colaboradorId; }
    public BigDecimal getHorasSemanales() { return horasSemanales; }
    public void setHorasSemanales(BigDecimal horasSemanales) { this.horasSemanales = horasSemanales; }
    public String getMensajeSolicitud() { return mensajeSolicitud; }
    public void setMensajeSolicitud(String mensajeSolicitud) { this.mensajeSolicitud = mensajeSolicitud; }
}
