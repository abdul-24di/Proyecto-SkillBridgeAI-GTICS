package com.pucp.skillb_ia.model;

import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.Prioridad;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "proyecto")
public class Proyecto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 1000)
    private String descripcion;

    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin_estimada")
    private LocalDate fechaFinEstimada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoProyecto estado = EstadoProyecto.EN_REVISION;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Prioridad prioridad;

    @Column(name = "justificacion_prioridad", nullable = false, length = 500)
    private String justificacionPrioridad;

    @Column(name = "motivo_rechazo", length = 500)
    private String motivoRechazo;

    @Column(name = "presupuesto_solicitado", precision = 12, scale = 2)
    private BigDecimal presupuestoSolicitado;

    @Column(name = "justificacion_presupuesto", length = 500)
    private String justificacionPresupuesto;

    // Asignado y editado exclusivamente por el RM (A20).
    @Column(precision = 12, scale = 2)
    private BigDecimal presupuesto;

    @Column(name = "colaboradores_requeridos", nullable = false)
    private int colaboradoresRequeridos = 1;

    @Column(name = "horas_semanales_requeridas", precision = 5, scale = 2, nullable = false)
    private BigDecimal horasSemanalesRequeridas = BigDecimal.valueOf(20);

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pm_id", nullable = false)
    private Usuario pm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rm_revisor_id")
    private Usuario rmRevisor;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @PrePersist
    protected void onCreate() {
        if (fechaCreacion == null) fechaCreacion = LocalDateTime.now();
    }

    @Column(name = "documento_contexto_url", length = 500)
    private String documentoContextoUrl;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDate getFechaFinEstimada() { return fechaFinEstimada; }
    public void setFechaFinEstimada(LocalDate fechaFinEstimada) { this.fechaFinEstimada = fechaFinEstimada; }

    public EstadoProyecto getEstado() { return estado; }
    public void setEstado(EstadoProyecto estado) { this.estado = estado; }

    public Prioridad getPrioridad() { return prioridad; }
    public void setPrioridad(Prioridad prioridad) { this.prioridad = prioridad; }

    public String getJustificacionPrioridad() { return justificacionPrioridad; }
    public void setJustificacionPrioridad(String justificacionPrioridad) { this.justificacionPrioridad = justificacionPrioridad; }

    public String getMotivoRechazo() { return motivoRechazo; }
    public void setMotivoRechazo(String motivoRechazo) { this.motivoRechazo = motivoRechazo; }

    public BigDecimal getPresupuestoSolicitado() { return presupuestoSolicitado; }
    public void setPresupuestoSolicitado(BigDecimal presupuestoSolicitado) { this.presupuestoSolicitado = presupuestoSolicitado; }

    public String getJustificacionPresupuesto() { return justificacionPresupuesto; }
    public void setJustificacionPresupuesto(String justificacionPresupuesto) { this.justificacionPresupuesto = justificacionPresupuesto; }

    public BigDecimal getPresupuesto() { return presupuesto; }
    public void setPresupuesto(BigDecimal presupuesto) { this.presupuesto = presupuesto; }

    public int getColaboradoresRequeridos() { return colaboradoresRequeridos; }
    public void setColaboradoresRequeridos(int colaboradoresRequeridos) { this.colaboradoresRequeridos = colaboradoresRequeridos; }

    public BigDecimal getHorasSemanalesRequeridas() { return horasSemanalesRequeridas; }
    public void setHorasSemanalesRequeridas(BigDecimal horasSemanalesRequeridas) { this.horasSemanalesRequeridas = horasSemanalesRequeridas; }

    public Usuario getPm() { return pm; }
    public void setPm(Usuario pm) { this.pm = pm; }

    public Usuario getRmRevisor() { return rmRevisor; }
    public void setRmRevisor(Usuario rmRevisor) { this.rmRevisor = rmRevisor; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public String getDocumentoContextoUrl() { return documentoContextoUrl; }
    public void setDocumentoContextoUrl(String documentoContextoUrl) { this.documentoContextoUrl = documentoContextoUrl; }
}