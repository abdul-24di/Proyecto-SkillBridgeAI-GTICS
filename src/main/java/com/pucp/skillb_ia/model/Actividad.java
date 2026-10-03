package com.pucp.skillb_ia.model;

import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoEntrega;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "actividad")
public class Actividad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "colaborador_id", nullable = false)
    private Usuario colaborador;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(length = 500)
    private String descripcion;

    @Column(name = "horas_estimadas", nullable = false, precision = 6, scale = 2)
    private BigDecimal horasEstimadas;

    @Column(name = "fecha_asignacion", nullable = false, updatable = false)
    private LocalDateTime fechaAsignacion;

    @Column(name = "fecha_limite", nullable = false)
    private LocalDate fechaLimite;

    @Column(name = "fecha_marcado_revision")
    private LocalDateTime fechaMarcadoRevision;

    @Column(name = "fecha_entrega")
    private LocalDateTime fechaEntrega;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_entrega", length = 10)
    private EstadoEntrega estadoEntrega;

    @Column(name = "veces_devuelta", nullable = false)
    private int vecesDevuelta = 0;

    @Column(name = "comentario_devolucion", length = 300)
    private String comentarioDevolucion;

    @Column(name = "evidencia_url", length = 500)
    private String evidenciaUrl;

    @Column(name = "comentario_colaborador", length = 300)
    private String comentarioColaborador;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoActividad estado = EstadoActividad.PENDIENTE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creado_por", nullable = false)
    private Usuario creadoPor;

    private boolean activo = true;

    @PrePersist
    protected void onCreate() {
        if (fechaAsignacion == null) fechaAsignacion = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Proyecto getProyecto() { return proyecto; }
    public void setProyecto(Proyecto proyecto) { this.proyecto = proyecto; }

    public Usuario getColaborador() { return colaborador; }
    public void setColaborador(Usuario colaborador) { this.colaborador = colaborador; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public BigDecimal getHorasEstimadas() { return horasEstimadas; }
    public void setHorasEstimadas(BigDecimal horasEstimadas) { this.horasEstimadas = horasEstimadas; }

    public LocalDateTime getFechaAsignacion() { return fechaAsignacion; }
    public void setFechaAsignacion(LocalDateTime fechaAsignacion) { this.fechaAsignacion = fechaAsignacion; }

    public LocalDate getFechaLimite() { return fechaLimite; }
    public void setFechaLimite(LocalDate fechaLimite) { this.fechaLimite = fechaLimite; }

    public LocalDateTime getFechaMarcadoRevision() { return fechaMarcadoRevision; }
    public void setFechaMarcadoRevision(LocalDateTime fechaMarcadoRevision) { this.fechaMarcadoRevision = fechaMarcadoRevision; }

    public LocalDateTime getFechaEntrega() { return fechaEntrega; }
    public void setFechaEntrega(LocalDateTime fechaEntrega) { this.fechaEntrega = fechaEntrega; }

    public EstadoEntrega getEstadoEntrega() { return estadoEntrega; }
    public void setEstadoEntrega(EstadoEntrega estadoEntrega) { this.estadoEntrega = estadoEntrega; }

    public int getVecesDevuelta() { return vecesDevuelta; }
    public void setVecesDevuelta(int vecesDevuelta) { this.vecesDevuelta = vecesDevuelta; }

    public String getComentarioDevolucion() { return comentarioDevolucion; }
    public void setComentarioDevolucion(String comentarioDevolucion) { this.comentarioDevolucion = comentarioDevolucion; }

    public String getEvidenciaUrl() { return evidenciaUrl; }
    public void setEvidenciaUrl(String evidenciaUrl) { this.evidenciaUrl = evidenciaUrl; }

    public String getComentarioColaborador() { return comentarioColaborador; }
    public void setComentarioColaborador(String comentarioColaborador) { this.comentarioColaborador = comentarioColaborador; }

    public EstadoActividad getEstado() { return estado; }
    public void setEstado(EstadoActividad estado) { this.estado = estado; }

    public Usuario getCreadoPor() { return creadoPor; }
    public void setCreadoPor(Usuario creadoPor) { this.creadoPor = creadoPor; }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
