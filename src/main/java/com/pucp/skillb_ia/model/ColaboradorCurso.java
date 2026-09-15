package com.pucp.skillb_ia.model;

import com.pucp.skillb_ia.model.enums.EstadoColaboradorCurso;
import com.pucp.skillb_ia.model.enums.OrigenCurso;
import jakarta.persistence.*;

import java.time.LocalDateTime;

// Historias A26/A27. `origen` distingue si lo solicitó el colaborador o si el
// RM lo asignó directamente (p.ej. por bajo cumplimiento de horas).
@Entity
@Table(name = "colaborador_curso")
public class ColaboradorCurso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "colaborador_id", nullable = false)
    private Usuario colaborador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curso_id", nullable = false)
    private Curso curso;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private OrigenCurso origen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoColaboradorCurso estado = EstadoColaboradorCurso.SOLICITADO;

    // El RM, cuando el origen es ASIGNADO_POR_RM.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asignado_por")
    private Usuario asignadoPor;

    @Column(name = "fecha_solicitud", nullable = false, updatable = false)
    private LocalDateTime fechaSolicitud;

    @Column(name = "fecha_respuesta")
    private LocalDateTime fechaRespuesta;

    @Column(name = "fecha_completado")
    private LocalDateTime fechaCompletado;

    // Motivo del rechazo o justificación de una asignación directa del RM.
    @Column(name = "motivo_respuesta", length = 500)
    private String motivoRespuesta;

    @PrePersist
    protected void onCreate() {
        if (fechaSolicitud == null) fechaSolicitud = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Usuario getColaborador() { return colaborador; }
    public void setColaborador(Usuario colaborador) { this.colaborador = colaborador; }

    public Curso getCurso() { return curso; }
    public void setCurso(Curso curso) { this.curso = curso; }

    public OrigenCurso getOrigen() { return origen; }
    public void setOrigen(OrigenCurso origen) { this.origen = origen; }

    public EstadoColaboradorCurso getEstado() { return estado; }
    public void setEstado(EstadoColaboradorCurso estado) { this.estado = estado; }

    public Usuario getAsignadoPor() { return asignadoPor; }
    public void setAsignadoPor(Usuario asignadoPor) { this.asignadoPor = asignadoPor; }

    public LocalDateTime getFechaSolicitud() { return fechaSolicitud; }
    public void setFechaSolicitud(LocalDateTime fechaSolicitud) { this.fechaSolicitud = fechaSolicitud; }

    public LocalDateTime getFechaRespuesta() { return fechaRespuesta; }
    public void setFechaRespuesta(LocalDateTime fechaRespuesta) { this.fechaRespuesta = fechaRespuesta; }

    public LocalDateTime getFechaCompletado() { return fechaCompletado; }
    public void setFechaCompletado(LocalDateTime fechaCompletado) { this.fechaCompletado = fechaCompletado; }

    public String getMotivoRespuesta() { return motivoRespuesta; }
    public void setMotivoRespuesta(String motivoRespuesta) { this.motivoRespuesta = motivoRespuesta; }
}
