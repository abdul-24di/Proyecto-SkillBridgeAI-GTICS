package com.pucp.skillb_ia.dto.pm;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

public class PmEditarActividadForm implements Serializable {
    @NotNull(message = "El proyecto es obligatorio.")
    private Long proyectoId;

    @NotBlank(message = "El título de la actividad es obligatorio.")
    @Size(max = 150, message = "El título no puede exceder los 150 caracteres.")
    private String titulo;

    @Size(max = 500, message = "La descripción no puede exceder los 500 caracteres.")
    private String descripcion;

    @NotNull(message = "Las horas estimadas son obligatorias.")
    @DecimalMin(value = "0.5", message = "Las horas estimadas deben ser al menos 0.5.")
    @DecimalMax(value = "999.0", message = "Las horas estimadas no pueden exceder 999.")
    private BigDecimal horasEstimadas;

    @NotNull(message = "La fecha límite es obligatoria.")
    private LocalDate fechaLimite;

    public Long getProyectoId() { return proyectoId; }
    public void setProyectoId(Long proyectoId) { this.proyectoId = proyectoId; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public BigDecimal getHorasEstimadas() { return horasEstimadas; }
    public void setHorasEstimadas(BigDecimal horasEstimadas) { this.horasEstimadas = horasEstimadas; }
    public LocalDate getFechaLimite() { return fechaLimite; }
    public void setFechaLimite(LocalDate fechaLimite) { this.fechaLimite = fechaLimite; }
}
