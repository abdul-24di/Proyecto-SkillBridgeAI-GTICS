package com.pucp.skillb_ia.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Datos preparados para las vistas de consulta de foros del Resource Manager (publicaciones con la vista común ForoPublicacionView, TASK-046). */
public class RmForoView {
    private final Long id;
    private final String nombre;
    private final String tipo;
    /** En un foro de proyecto: {@code true} si es público y {@code false} si es privado del equipo. */
    private final boolean publico;
    /** Proyecto del foro; {@code null} en los foros generales (anuncios o comunidad). */
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
    /** Personas distintas que publicaron o respondieron en el foro. */
    private final int participantes;
    private final LocalDateTime ultimaActividad;
    private final List<ForoPublicacionView> publicaciones;
    private final List<String> etiquetas;
    private final boolean anuncios;
    private final boolean participa;

    /** Arma la vista del foro con los datos del proyecto, su actividad y sus publicaciones. */
    public RmForoView(Long id, String nombre, String tipo, boolean publico,
                      Long proyectoId, String proyectoNombre, String proyectoEstado,
                      String proyectoEstadoCodigo, String proyectoEstadoClase,
                      String proyectoPrioridad, String proyectoPrioridadClase,
                      String pmNombre, int integrantesActivos, int colaboradoresRequeridos,
                      int participantes, LocalDateTime ultimaActividad,
                      List<ForoPublicacionView> publicaciones, List<String> etiquetas,
                      boolean anuncios, boolean participa) {
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
        this.anuncios = anuncios;
        this.participa = participa;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    /** Foro del Admin donde solo se lee y se reacciona. */
    public boolean isAnuncios() { return anuncios; }
    /** El RM puede publicar y responder (foros de comunidad). */
    public boolean isParticipa() { return participa; }
    /** @return "Público" o "Privado" en foros de proyecto; "Anuncios" o "Comunidad" en los generales */
    public String getTipoForo() { return proyectoId != null ? (publico ? "Público" : "Privado") : (anuncios ? "Anuncios" : "Comunidad"); }
    /** @return clase CSS de la insignia del tipo: naranja para anuncios, azul si es público y morado en otro caso */
    public String getTipoForoClase() { return anuncios ? "bg-orange-lt" : (publico ? "bg-blue-lt" : "bg-purple-lt"); }
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
    /** @return suma de las respuestas de todas las publicaciones */
    public int getTotalRespuestas() {
        return publicaciones.stream().mapToInt(p -> p.getRespuestas().size()).sum();
    }
    /** @return {@code true} si hubo actividad en los últimos 7 días */
    public boolean isActividadReciente() {
        return ultimaActividad != null && !ultimaActividad.isBefore(LocalDateTime.now().minusDays(7));
    }
    /** @return título de la primera publicación de la lista, o "Sin publicaciones todavía" */
    public String getTemaReciente() {
        return publicaciones.isEmpty() ? "Sin publicaciones todavía" : publicaciones.get(0).getTitulo();
    }
    /** @return texto del buscador: foro, proyecto, PM, tema reciente y etiquetas */
    public String getTextoBusqueda() {
        return nombre + " " + proyectoNombre + " " + pmNombre + " " + getTemaReciente()
                + " " + String.join(" ", etiquetas);
    }
}
