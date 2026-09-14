package com.pucp.skillb_ia.model;

import com.pucp.skillb_ia.model.enums.EstadoSolicitudPersonal;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Requerimiento genérico de personal para un proyecto.
 * No representa una asignación: todavía no existe un colaborador concreto.
 */
@Entity
@Table(name = "solicitud_personal")
public class SolicitudPersonal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    @Column(name = "cantidad_colaboradores", nullable = false)
    private int cantidadColaboradores;

    @Column(name = "perfiles_requeridos", length = 1000)
    private String perfilesRequeridos;

    @Column(name = "mensaje_pm", length = 1000)
    private String mensajePm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSolicitudPersonal estado = EstadoSolicitudPersonal.PENDIENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rm_responsable_id")
    private Usuario rmResponsable;

    @Column(name = "fecha_solicitud", nullable = false, updatable = false)
    private LocalDateTime fechaSolicitud;

    @Column(name = "fecha_inicio_atencion")
    private LocalDateTime fechaInicioAtencion;

    @Column(name = "fecha_atencion")
    private LocalDateTime fechaAtencion;

    @PrePersist
    protected void onCreate() {
        if (fechaSolicitud == null) fechaSolicitud = LocalDateTime.now();
        if (estado == null) estado = EstadoSolicitudPersonal.PENDIENTE;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Proyecto getProyecto() { return proyecto; }
    public void setProyecto(Proyecto proyecto) { this.proyecto = proyecto; }
    public int getCantidadColaboradores() { return cantidadColaboradores; }
    public void setCantidadColaboradores(int cantidadColaboradores) { this.cantidadColaboradores = cantidadColaboradores; }
    public String getPerfilesRequeridos() { return perfilesRequeridos; }
    public void setPerfilesRequeridos(String perfilesRequeridos) { this.perfilesRequeridos = perfilesRequeridos; }
    public String getMensajePm() { return mensajePm; }
    public void setMensajePm(String mensajePm) { this.mensajePm = mensajePm; }
    public EstadoSolicitudPersonal getEstado() { return estado; }
    public void setEstado(EstadoSolicitudPersonal estado) { this.estado = estado; }
    public Usuario getRmResponsable() { return rmResponsable; }
    public void setRmResponsable(Usuario rmResponsable) { this.rmResponsable = rmResponsable; }
    public LocalDateTime getFechaSolicitud() { return fechaSolicitud; }
    public void setFechaSolicitud(LocalDateTime fechaSolicitud) { this.fechaSolicitud = fechaSolicitud; }
    public LocalDateTime getFechaInicioAtencion() { return fechaInicioAtencion; }
    public void setFechaInicioAtencion(LocalDateTime fechaInicioAtencion) { this.fechaInicioAtencion = fechaInicioAtencion; }
    public LocalDateTime getFechaAtencion() { return fechaAtencion; }
    public void setFechaAtencion(LocalDateTime fechaAtencion) { this.fechaAtencion = fechaAtencion; }
}
