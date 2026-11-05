package com.pucp.skillb_ia.dto.pm;

import jakarta.validation.constraints.*;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class PmProyectoForm {
    @NotBlank(message = "El nombre del proyecto es obligatorio")
    @Size(max = 100, message = "El nombre no puede exceder los 100 caracteres")
    private String nombre;

    @NotBlank(message = "La descripción es obligatoria")
    @Size(max = 1000, message = "La descripción no puede exceder los 1000 caracteres")
    private String descripcion;

    @NotNull(message = "La fecha de inicio es obligatoria")
    @FutureOrPresent(message = "La fecha de inicio no puede ser anterior a hoy")
    private LocalDate fechaInicio;

    @NotNull(message = "La fecha de fin estimada es obligatoria")
    private LocalDate fechaFinEstimada;

    @NotBlank(message = "La prioridad es obligatoria")
    @Pattern(regexp = "ALTA|MEDIA|BAJA", message = "Selecciona una prioridad válida")
    private String prioridad;

    @NotBlank(message = "Debes justificar la prioridad")
    @Size(max = 400, message = "La justificación no puede exceder 400 caracteres")
    private String justificacionPrioridad;

    @DecimalMin(value = "0.0", inclusive = true, message = "El presupuesto no puede ser negativo")
    @Digits(integer = 10, fraction = 2, message = "El presupuesto debe ser un monto válido con hasta 2 decimales")
    private BigDecimal presupuestoSolicitado;

    @Size(max = 400, message = "La justificación de presupuesto no puede exceder 400 caracteres")
    private String justificacionPresupuesto;

    @NotNull(message = "El número de colaboradores requeridos es obligatorio")
    @Min(value = 1, message = "Debe haber al menos 1 colaborador requerido")
    @Max(value = 500, message = "El número de colaboradores no puede exceder 500")
    private Integer colaboradoresRequeridos = 1;

    @NotEmpty(message = "Debes agregar al menos un requerimiento de talento (habilidad)")
    private List<Long> habilidadIds;
    private List<String> nivelesRequeridos;
    private List<Integer> cantidadesPersonas;
    private List<BigDecimal> horasSemanalesHab;
    private MultipartFile documentoProyecto;

    @Size(max = 500, message = "Las habilidades extra no pueden exceder 500 caracteres")
    private String habilidadesExtra;

    @Size(max = 1000, message = "Las notas para el Resource Manager no pueden exceder 1000 caracteres")
    private String notasRM;

    // Getters and Setters
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }
    public LocalDate getFechaFinEstimada() { return fechaFinEstimada; }
    public void setFechaFinEstimada(LocalDate fechaFinEstimada) { this.fechaFinEstimada = fechaFinEstimada; }
    public String getPrioridad() { return prioridad; }
    public void setPrioridad(String prioridad) { this.prioridad = prioridad; }
    public String getJustificacionPrioridad() { return justificacionPrioridad; }
    public void setJustificacionPrioridad(String justificacionPrioridad) { this.justificacionPrioridad = justificacionPrioridad; }
    public BigDecimal getPresupuestoSolicitado() { return presupuestoSolicitado; }
    public void setPresupuestoSolicitado(BigDecimal presupuestoSolicitado) { this.presupuestoSolicitado = presupuestoSolicitado; }
    public String getJustificacionPresupuesto() { return justificacionPresupuesto; }
    public void setJustificacionPresupuesto(String justificacionPresupuesto) { this.justificacionPresupuesto = justificacionPresupuesto; }
    public Integer getColaboradoresRequeridos() { return colaboradoresRequeridos; }
    public void setColaboradoresRequeridos(Integer colaboradoresRequeridos) { this.colaboradoresRequeridos = colaboradoresRequeridos; }
    public List<Long> getHabilidadIds() { return habilidadIds; }
    public void setHabilidadIds(List<Long> habilidadIds) { this.habilidadIds = habilidadIds; }
    public List<String> getNivelesRequeridos() { return nivelesRequeridos; }
    public void setNivelesRequeridos(List<String> nivelesRequeridos) { this.nivelesRequeridos = nivelesRequeridos; }
    public List<Integer> getCantidadesPersonas() { return cantidadesPersonas; }
    public void setCantidadesPersonas(List<Integer> cantidadesPersonas) { this.cantidadesPersonas = cantidadesPersonas; }
    public List<BigDecimal> getHorasSemanalesHab() { return horasSemanalesHab; }
    public void setHorasSemanalesHab(List<BigDecimal> horasSemanalesHab) { this.horasSemanalesHab = horasSemanalesHab; }
    public MultipartFile getDocumentoProyecto() { return documentoProyecto; }
    public void setDocumentoProyecto(MultipartFile documentoProyecto) { this.documentoProyecto = documentoProyecto; }
    public String getHabilidadesExtra() { return habilidadesExtra; }
    public void setHabilidadesExtra(String habilidadesExtra) { this.habilidadesExtra = habilidadesExtra; }
    public String getNotasRM() { return notasRM; }
    public void setNotasRM(String notasRM) { this.notasRM = notasRM; }
}
