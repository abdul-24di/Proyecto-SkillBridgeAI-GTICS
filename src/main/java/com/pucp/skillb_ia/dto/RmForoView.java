package com.pucp.skillb_ia.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Datos preparados para las vistas de consulta de foros del Resource Manager. */
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
    private final List<Publicacion> publicaciones;
    private final List<String> etiquetas;

    public RmForoView(Long id, String nombre, String tipo, boolean publico,
                      Long proyectoId, String proyectoNombre, String proyectoEstado,
                      String proyectoEstadoCodigo, String proyectoEstadoClase,
                      String proyectoPrioridad, String proyectoPrioridadClase,
                      String pmNombre, int integrantesActivos, int colaboradoresRequeridos,
                      int participantes, LocalDateTime ultimaActividad,
                      List<Publicacion> publicaciones, List<String> etiquetas) {
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
    public List<Publicacion> getPublicaciones() { return publicaciones; }
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

    public static class Publicacion {
        private final Long id;
        private final Long autorId;
        private final String titulo;
        private final String contenido;
        private final String autorNombre;
        private final String autorIniciales;
        private final String autorCargo;
        private final String etiqueta;
        private final LocalDateTime fechaCreacion;
        private final long positivos;
        private final long negativos;
        private final List<Respuesta> respuestas;

        public Publicacion(Long id, Long autorId, String titulo, String contenido, String autorNombre,
                           String autorIniciales, String autorCargo, String etiqueta,
                           LocalDateTime fechaCreacion, long positivos, long negativos,
                           List<Respuesta> respuestas) {
            this.id = id;
            this.autorId = autorId;
            this.titulo = titulo;
            this.contenido = contenido;
            this.autorNombre = autorNombre;
            this.autorIniciales = autorIniciales;
            this.autorCargo = autorCargo;
            this.etiqueta = etiqueta;
            this.fechaCreacion = fechaCreacion;
            this.positivos = positivos;
            this.negativos = negativos;
            this.respuestas = List.copyOf(respuestas);
        }

        public Long getId() { return id; }
        public Long getAutorId() { return autorId; }
        public String getTitulo() { return titulo; }
        public String getContenido() { return contenido; }
        public String getAutorNombre() { return autorNombre; }
        public String getAutorIniciales() { return autorIniciales; }
        public String getAutorCargo() { return autorCargo; }
        public String getEtiqueta() { return etiqueta; }
        public LocalDateTime getFechaCreacion() { return fechaCreacion; }
        public long getPositivos() { return positivos; }
        public long getNegativos() { return negativos; }
        public long getPuntaje() { return positivos - negativos; }
        public List<Respuesta> getRespuestas() { return respuestas; }
    }

    public static class Respuesta {
        private final Long id;
        private final Long autorId;
        private final String contenido;
        private final String autorNombre;
        private final String autorIniciales;
        private final String autorCargo;
        private final boolean solucion;
        private final LocalDateTime fechaCreacion;
        private final long positivos;
        private final long negativos;

        public Respuesta(Long id, Long autorId, String contenido, String autorNombre,
                         String autorIniciales, String autorCargo, boolean solucion,
                         LocalDateTime fechaCreacion, long positivos, long negativos) {
            this.id = id;
            this.autorId = autorId;
            this.contenido = contenido;
            this.autorNombre = autorNombre;
            this.autorIniciales = autorIniciales;
            this.autorCargo = autorCargo;
            this.solucion = solucion;
            this.fechaCreacion = fechaCreacion;
            this.positivos = positivos;
            this.negativos = negativos;
        }

        public Long getId() { return id; }
        public Long getAutorId() { return autorId; }
        public String getContenido() { return contenido; }
        public String getAutorNombre() { return autorNombre; }
        public String getAutorIniciales() { return autorIniciales; }
        public String getAutorCargo() { return autorCargo; }
        public boolean isSolucion() { return solucion; }
        public LocalDateTime getFechaCreacion() { return fechaCreacion; }
        public long getPositivos() { return positivos; }
        public long getNegativos() { return negativos; }
        public long getPuntaje() { return positivos - negativos; }
    }
}
