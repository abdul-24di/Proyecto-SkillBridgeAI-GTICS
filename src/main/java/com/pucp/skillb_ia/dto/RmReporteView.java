package com.pucp.skillb_ia.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public class RmReporteView {
    private final YearMonth periodo;
    private final String periodoTexto;
    private final Long proyectoSeleccionado;
    private final String estadoSeleccionado;
    private final List<ProyectoReporte> proyectos;
    private final List<DetalleColaborador> detalles;
    private final List<ColaboradorReporte> colaboradores;
    private final BigDecimal presupuestoTotal;
    private final BigDecimal horasTotales;
    private final long colaboradoresInvolucrados;

    public RmReporteView(YearMonth periodo, String periodoTexto,
                         Long proyectoSeleccionado, String estadoSeleccionado,
                         List<ProyectoReporte> proyectos,
                         List<DetalleColaborador> detalles,
                         List<ColaboradorReporte> colaboradores) {
        this.periodo = periodo;
        this.periodoTexto = periodoTexto;
        this.proyectoSeleccionado = proyectoSeleccionado;
        this.estadoSeleccionado = estadoSeleccionado;
        this.proyectos = List.copyOf(proyectos);
        this.detalles = List.copyOf(detalles);
        this.colaboradores = List.copyOf(colaboradores);
        this.presupuestoTotal = proyectos.stream()
                .map(ProyectoReporte::getPresupuesto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.horasTotales = proyectos.stream()
                .map(ProyectoReporte::getHoras)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.colaboradoresInvolucrados = detalles.stream()
                .map(DetalleColaborador::getColaboradorId)
                .distinct()
                .count();
    }

    public YearMonth getPeriodo() { return periodo; }
    public String getPeriodoValor() { return periodo.toString(); }
    public String getPeriodoTexto() { return periodoTexto; }
    public Long getProyectoSeleccionado() { return proyectoSeleccionado; }
    public String getProyectoSeleccionadoValor() {
        return proyectoSeleccionado == null ? "" : proyectoSeleccionado.toString();
    }
    public String getEstadoSeleccionado() { return estadoSeleccionado; }
    public List<ProyectoReporte> getProyectos() { return proyectos; }
    public List<DetalleColaborador> getDetalles() { return detalles; }
    public List<ColaboradorReporte> getColaboradores() { return colaboradores; }
    public int getTotalProyectos() { return proyectos.size(); }
    public BigDecimal getPresupuestoTotal() { return presupuestoTotal; }
    public BigDecimal getHorasTotales() { return horasTotales; }
    public long getColaboradoresInvolucrados() { return colaboradoresInvolucrados; }
    public long getColaboradoresDebajoReferencia() {
        return colaboradores.stream().filter(item -> item.getHoras().compareTo(BigDecimal.valueOf(160)) < 0).count();
    }
    public long getColaboradoresEnReferencia() {
        return colaboradores.stream().filter(item -> item.getHoras().compareTo(BigDecimal.valueOf(160)) >= 0).count();
    }

    public static class ProyectoReporte {
        private final Long id;
        private final String nombre;
        private final String estadoCodigo;
        private final String estadoTexto;
        private final String estadoClase;
        private final String prioridadTexto;
        private final String prioridadClase;
        private final BigDecimal presupuesto;
        private final BigDecimal horas;
        private final long colaboradores;
        private final int porcentajeHoras;

        public ProyectoReporte(Long id, String nombre, String estadoCodigo,
                               String estadoTexto, String estadoClase,
                               String prioridadTexto, String prioridadClase,
                               BigDecimal presupuesto, BigDecimal horas,
                               long colaboradores, int porcentajeHoras) {
            this.id = id;
            this.nombre = nombre;
            this.estadoCodigo = estadoCodigo;
            this.estadoTexto = estadoTexto;
            this.estadoClase = estadoClase;
            this.prioridadTexto = prioridadTexto;
            this.prioridadClase = prioridadClase;
            this.presupuesto = presupuesto;
            this.horas = horas;
            this.colaboradores = colaboradores;
            this.porcentajeHoras = porcentajeHoras;
        }

        public Long getId() { return id; }
        public String getNombre() { return nombre; }
        public String getEstadoCodigo() { return estadoCodigo; }
        public String getEstadoTexto() { return estadoTexto; }
        public String getEstadoClase() { return estadoClase; }
        public String getPrioridadTexto() { return prioridadTexto; }
        public String getPrioridadClase() { return prioridadClase; }
        public BigDecimal getPresupuesto() { return presupuesto; }
        public BigDecimal getHoras() { return horas; }
        public long getColaboradores() { return colaboradores; }
        public int getPorcentajeHoras() { return porcentajeHoras; }
    }

    public static class DetalleColaborador {
        private final Long colaboradorId;
        private final String colaborador;
        private final String cargo;
        private final Long proyectoId;
        private final String proyecto;
        private final BigDecimal horas;
        private final long tareas;

        public DetalleColaborador(Long colaboradorId, String colaborador, String cargo,
                                  Long proyectoId, String proyecto,
                                  BigDecimal horas, long tareas) {
            this.colaboradorId = colaboradorId;
            this.colaborador = colaborador;
            this.cargo = cargo;
            this.proyectoId = proyectoId;
            this.proyecto = proyecto;
            this.horas = horas;
            this.tareas = tareas;
        }

        public Long getColaboradorId() { return colaboradorId; }
        public String getColaborador() { return colaborador; }
        public String getCargo() { return cargo; }
        public Long getProyectoId() { return proyectoId; }
        public String getProyecto() { return proyecto; }
        public BigDecimal getHoras() { return horas; }
        public long getTareas() { return tareas; }
    }

    public static class ColaboradorReporte {
        private final Long id;
        private final String nombre;
        private final String cargo;
        private final BigDecimal horas;
        private final long tareas;
        private final List<DesgloseProyecto> proyectos;

        public ColaboradorReporte(Long id, String nombre, String cargo,
                                  BigDecimal horas, long tareas,
                                  List<DesgloseProyecto> proyectos) {
            this.id = id;
            this.nombre = nombre;
            this.cargo = cargo;
            this.horas = horas;
            this.tareas = tareas;
            this.proyectos = List.copyOf(proyectos);
        }

        public Long getId() { return id; }
        public String getNombre() { return nombre; }
        public String getCargo() { return cargo; }
        public BigDecimal getHoras() { return horas; }
        public long getTareas() { return tareas; }
        public List<DesgloseProyecto> getProyectos() { return proyectos; }
        public int getCantidadProyectos() { return proyectos.size(); }
        public boolean isAlcanzaReferencia() { return horas.compareTo(BigDecimal.valueOf(160)) >= 0; }
    }

    public static class DesgloseProyecto {
        private final Long proyectoId;
        private final String proyecto;
        private final BigDecimal horas;
        private final long tareas;

        public DesgloseProyecto(Long proyectoId, String proyecto,
                                BigDecimal horas, long tareas) {
            this.proyectoId = proyectoId;
            this.proyecto = proyecto;
            this.horas = horas;
            this.tareas = tareas;
        }

        public Long getProyectoId() { return proyectoId; }
        public String getProyecto() { return proyecto; }
        public BigDecimal getHoras() { return horas; }
        public long getTareas() { return tareas; }
    }

    public static class Opcion {
        private final String valor;
        private final String texto;

        public Opcion(String valor, String texto) {
            this.valor = valor;
            this.texto = texto;
        }

        public String getValor() { return valor; }
        public String getTexto() { return texto; }
    }
}
