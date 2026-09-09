package com.pucp.skillb_ia.model;

import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.MotivoFinalizacion;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Historia A4 (13 pts) — el corazón del sistema. Triple origen + doble
// aprobación condicional:
//   PROPUESTA_PM            -> requiere aprobado_por_rm = true.
//   PROPUESTA_RM            -> requiere aprobado_por_pm = true.
//   SOLICITADA_COLABORADOR  -> requiere AMBOS.
// Pasa a ACTIVA solo cuando se cumplen todas las aprobaciones según el
// origen; si cualquier rol requerido rechaza, queda RECHAZADA. A18: el PM o
// el RM pueden finalizarla directamente (motivo_finalizacion determina si
// aplica penalización — Bloque B punto 7). El colaborador nunca
// aprueba/rechaza, solo consulta (A2) o solicita (A3).
@Entity
@Table(name = "asignacion")
public class Asignacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "colaborador_id", nullable = false)
    private Usuario colaborador;

    @Column(name = "horas_semanales", nullable = false, precision = 5, scale = 2)
    private BigDecimal horasSemanales;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrigenAsignacion origen;

    @Column(name = "mensaje_solicitud", length = 500)
    private String mensajeSolicitud;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoAsignacion estado = EstadoAsignacion.PENDIENTE;

    @Column(name = "aprobado_por_pm", nullable = false)
    private boolean aprobadoPorPm = false;

    @Column(name = "aprobado_por_rm", nullable = false)
    private boolean aprobadoPorRm = false;

    @Column(name = "fecha_aprobacion_pm")
    private LocalDateTime fechaAprobacionPm;

    @Column(name = "fecha_aprobacion_rm")
    private LocalDateTime fechaAprobacionRm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rechazado_por")
    private Usuario rechazadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "desasignado_por")
    private Usuario desasignadoPor;

    @Column(name = "motivo_rechazo", length = 300)
    private String motivoRechazo;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_finalizacion", length = 30)
    private MotivoFinalizacion motivoFinalizacion;

    @Column(name = "fecha_solicitud", nullable = false, updatable = false)
    private LocalDateTime fechaSolicitud;

    @Column(name = "fecha_activacion")
    private LocalDateTime fechaActivacion;

    @Column(name = "fecha_finalizacion")
    private LocalDateTime fechaFinalizacion;

    @PrePersist
    protected void onCreate() {
        if (fechaSolicitud == null) fechaSolicitud = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Proyecto getProyecto() { return proyecto; }
    public void setProyecto(Proyecto proyecto) { this.proyecto = proyecto; }

    public Usuario getColaborador() { return colaborador; }
    public void setColaborador(Usuario colaborador) { this.colaborador = colaborador; }

    public BigDecimal getHorasSemanales() { return horasSemanales; }
    public void setHorasSemanales(BigDecimal horasSemanales) { this.horasSemanales = horasSemanales; }

    public OrigenAsignacion getOrigen() { return origen; }
    public void setOrigen(OrigenAsignacion origen) { this.origen = origen; }

    public String getMensajeSolicitud() { return mensajeSolicitud; }
    public void setMensajeSolicitud(String mensajeSolicitud) { this.mensajeSolicitud = mensajeSolicitud; }

    public EstadoAsignacion getEstado() { return estado; }
    public void setEstado(EstadoAsignacion estado) { this.estado = estado; }

    public boolean isAprobadoPorPm() { return aprobadoPorPm; }
    public void setAprobadoPorPm(boolean aprobadoPorPm) { this.aprobadoPorPm = aprobadoPorPm; }

    public boolean isAprobadoPorRm() { return aprobadoPorRm; }
    public void setAprobadoPorRm(boolean aprobadoPorRm) { this.aprobadoPorRm = aprobadoPorRm; }

    public LocalDateTime getFechaAprobacionPm() { return fechaAprobacionPm; }
    public void setFechaAprobacionPm(LocalDateTime fechaAprobacionPm) { this.fechaAprobacionPm = fechaAprobacionPm; }

    public LocalDateTime getFechaAprobacionRm() { return fechaAprobacionRm; }
    public void setFechaAprobacionRm(LocalDateTime fechaAprobacionRm) { this.fechaAprobacionRm = fechaAprobacionRm; }

    public Usuario getRechazadoPor() { return rechazadoPor; }
    public void setRechazadoPor(Usuario rechazadoPor) { this.rechazadoPor = rechazadoPor; }

    public Usuario getDesasignadoPor() { return desasignadoPor; }
    public void setDesasignadoPor(Usuario desasignadoPor) { this.desasignadoPor = desasignadoPor; }

    public String getMotivoRechazo() { return motivoRechazo; }
    public void setMotivoRechazo(String motivoRechazo) { this.motivoRechazo = motivoRechazo; }

    public MotivoFinalizacion getMotivoFinalizacion() { return motivoFinalizacion; }
    public void setMotivoFinalizacion(MotivoFinalizacion motivoFinalizacion) { this.motivoFinalizacion = motivoFinalizacion; }

    public LocalDateTime getFechaSolicitud() { return fechaSolicitud; }
    public void setFechaSolicitud(LocalDateTime fechaSolicitud) { this.fechaSolicitud = fechaSolicitud; }

    public LocalDateTime getFechaActivacion() { return fechaActivacion; }
    public void setFechaActivacion(LocalDateTime fechaActivacion) { this.fechaActivacion = fechaActivacion; }

    public LocalDateTime getFechaFinalizacion() { return fechaFinalizacion; }
    public void setFechaFinalizacion(LocalDateTime fechaFinalizacion) { this.fechaFinalizacion = fechaFinalizacion; }
}
