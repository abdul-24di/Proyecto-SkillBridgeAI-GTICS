package com.pucp.skillb_ia.dto.pm;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public class PmActividadForm {
    @NotNull(message = "El proyecto es obligatorio")
    private Long proyectoId;

    @NotNull(message = "El colaborador es obligatorio")
    private Long colaboradorId;

    @NotBlank(message = "El ttulo es obligatorio")
    @Size(max = 150, message = "El ttulo no puede exceder 150 caracteres")
    private String titulo;

    @Size(max = 500, message = "La descripcin no puede exceder 500 caracteres")
    private String descripcion;

    @NotNull(message = "Las horas estimadas son obligatorias")
    @DecimalMin(value = "0.5", message = "Debe ser mayor a 0")
    private BigDecimal horasEstimadas;

    @NotNull(message = "La fecha lmite es obligatoria")
    private LocalDate fechaLimite;

    public Long getProyectoId() { return proyectoId; }
    public void setProyectoId(Long proyectoId) { this.proyectoId = proyectoId; }
    public Long getColaboradorId() { return colaboradorId; }
    public void setColaboradorId(Long colaboradorId) { this.colaboradorId = colaboradorId; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public BigDecimal getHorasEstimadas() { return horasEstimadas; }
    public void setHorasEstimadas(BigDecimal horasEstimadas) { this.horasEstimadas = horasEstimadas; }
    public LocalDate getFechaLimite() { return fechaLimite; }
    public void setFechaLimite(LocalDate fechaLimite) { this.fechaLimite = fechaLimite; }
}
