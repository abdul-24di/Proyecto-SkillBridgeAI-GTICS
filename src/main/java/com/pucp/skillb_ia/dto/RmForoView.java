package com.pucp.skillb_ia.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Datos preparados para las vistas de consulta de foros del Resource Manager (publicaciones con la vista común ForoPublicacionView, TASK-046). */
public class RmForoView {
    private final Long id;
    private final String nombre;
    private final String tipo;
    private final boolean publico;
    private final Long proyectoId;
    private final String proyectoNombre;
    private final String proyectoEstado;
    private final String proyectoEstadoCodigo;
    private final String proyectoEstadoClase;
    private final String proyectoPrioridad;
    private final String proyectoPrioridadClase;
    private final String pmNombre;
    private final int integrantesActivos;
    private final int colaboradoresRequeridos;
    private final int participantes;
    private final LocalDateTime ultimaActividad;
    private final List<ForoPublicacionView> publicaciones;
    private final List<String> etiquetas;

    public RmForoView(Long id, String nombre, String tipo, boolean publico,
                      Long proyectoId, String proyectoNombre, String proyectoEstado,
                      String proyectoEstadoCodigo, String proyectoEstadoClase,
                      String proyectoPrioridad, String proyectoPrioridadClase,
                      String pmNombre, int integrantesActivos, int colaboradoresRequeridos,
                      int participantes, LocalDateTime ultimaActividad,
                      List<ForoPublicacionView> publicaciones, List<String> etiquetas) {
        this.id = id;
        this.nombre = nombre;
        this.tipo = tipo;
        this.publico = publico;
        this.proyectoId = proyectoId;
        this.proyectoNombre = proyectoNombre;
        this.proyectoEstado = proyectoEstado;
        this.proyectoEstadoCodigo = proyectoEstadoCodigo;
        this.proyectoEstadoClase = proyectoEstadoClase;
        this.proyectoPrioridad = proyectoPrioridad;
        this.proyectoPrioridadClase = proyectoPrioridadClase;
        this.pmNombre = pmNombre;
        this.integrantesActivos = integrantesActivos;
        this.colaboradoresRequeridos = colaboradoresRequeridos;
        this.participantes = participantes;
        this.ultimaActividad = ultimaActividad;
        this.publicaciones = List.copyOf(publicaciones);
        this.etiquetas = List.copyOf(etiquetas);
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getTipo() { return tipo; }
    public boolean isPublico() { return publico; }
    public Long getProyectoId() { return proyectoId; }
    public String getProyectoNombre() { return proyectoNombre; }
    public String getProyectoEstado() { return proyectoEstado; }
    public String getProyectoEstadoCodigo() { return proyectoEstadoCodigo; }
    public String getProyectoEstadoClase() { return proyectoEstadoClase; }
    public String getProyectoPrioridad() { return proyectoPrioridad; }
    public String getProyectoPrioridadClase() { return proyectoPrioridadClase; }
    public String getPmNombre() { return pmNombre; }
    public int getIntegrantesActivos() { return integrantesActivos; }
    public int getColaboradoresRequeridos() { return colaboradoresRequeridos; }
    public int getParticipantes() { return participantes; }
    public LocalDateTime getUltimaActividad() { return ultimaActividad; }
    public List<ForoPublicacionView> getPublicaciones() { return publicaciones; }
    public List<String> getEtiquetas() { return etiquetas; }
    public int getTotalPublicaciones() { return publicaciones.size(); }
    public int getTotalRespuestas() {
        return publicaciones.stream().mapToInt(p -> p.getRespuestas().size()).sum();
    }
    public boolean isActividadReciente() {
        return ultimaActividad != null && !ultimaActividad.isBefore(LocalDateTime.now().minusDays(7));
    }
    public String getTemaReciente() {
        return publicaciones.isEmpty() ? "Sin publicaciones todavía" : publicaciones.get(0).getTitulo();
    }
    public String getTextoBusqueda() {
        return nombre + " " + proyectoNombre + " " + pmNombre + " " + getTemaReciente()
                + " " + String.join(" ", etiquetas);
    }
}
