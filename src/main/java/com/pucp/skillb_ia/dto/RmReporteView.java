package com.pucp.skillb_ia.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Reporte de horas y presupuesto del Resource Manager para un mes o un rango de meses.
 * Lo arma {@code RmReporteService} y lo usan la vista del reporte y el Excel de
 * {@code RmReporteExportService}. Guarda los filtros aplicados y calcula los totales.
 * La referencia mensual de un colaborador es de 160 horas.
 */
public class RmReporteView {
    /** Regla única de horas: la usan la vista y el Excel. */
    public static final String REGLA_HORAS = "Se suman las horas estimadas de las tareas completadas "
            + "cuya fecha de entrega pertenece al periodo. El periodo solo filtra las horas; "
            + "el presupuesto asignado es el registrado en cada proyecto (S/ 0.00 si no tiene).";

    private final YearMonth periodo;
    private final YearMonth periodoHasta;
    private final String periodoTexto;
    private final Long proyectoSeleccionado;
    private final String estadoSeleccionado;
    private final String proyectoSeleccionadoNombre;
    private final String estadoSeleccionadoTexto;
    private final String prioridadSeleccionada;
    private final String prioridadSeleccionadaTexto;
    private final List<ProyectoReporte> proyectos;
    private final List<DetalleColaborador> detalles;
    private final List<ColaboradorReporte> colaboradores;
    /** Suma de los presupuestos de los proyectos del reporte. */
    private final BigDecimal presupuestoTotal;
    /** Suma de las horas de los proyectos del reporte. */
    private final BigDecimal horasTotales;
    /** Colaboradores distintos que aparecen en el detalle. */
    private final long colaboradoresInvolucrados;

    public RmReporteView(YearMonth periodo, String periodoTexto,
                         Long proyectoSeleccionado, String estadoSeleccionado,
                         String proyectoSeleccionadoNombre, String estadoSeleccionadoTexto,
                         List<ProyectoReporte> proyectos,
                         List<DetalleColaborador> detalles,
                         List<ColaboradorReporte> colaboradores) {
        this(periodo, periodo, periodoTexto, proyectoSeleccionado, estadoSeleccionado,
                proyectoSeleccionadoNombre, estadoSeleccionadoTexto, "", null,
                proyectos, detalles, colaboradores);
    }

    /** {@code periodoHasta} igual a {@code periodo} = un solo mes; prioridad vacía = Todas. */
    public RmReporteView(YearMonth periodo, YearMonth periodoHasta, String periodoTexto,
                         Long proyectoSeleccionado, String estadoSeleccionado,
                         String proyectoSeleccionadoNombre, String estadoSeleccionadoTexto,
                         String prioridadSeleccionada, String prioridadSeleccionadaTexto,
                         List<ProyectoReporte> proyectos,
                         List<DetalleColaborador> detalles,
                         List<ColaboradorReporte> colaboradores) {
        this.periodo = periodo;
        this.periodoHasta = periodoHasta == null ? periodo : periodoHasta;
        this.periodoTexto = periodoTexto;
        this.proyectoSeleccionado = proyectoSeleccionado;
        this.estadoSeleccionado = estadoSeleccionado;
        this.proyectoSeleccionadoNombre = proyectoSeleccionadoNombre;
        this.estadoSeleccionadoTexto = estadoSeleccionadoTexto;
        this.prioridadSeleccionada = prioridadSeleccionada == null ? "" : prioridadSeleccionada;
        this.prioridadSeleccionadaTexto = prioridadSeleccionadaTexto;
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
    public YearMonth getPeriodoHasta() { return periodoHasta; }
    /** Vacío si el reporte es de un solo mes, así los enlaces no arrastran un "hasta" redundante. */
    public String getPeriodoHastaValor() { return esRango() ? periodoHasta.toString() : ""; }
    /** @return {@code true} si el reporte abarca más de un mes */
    public boolean esRango() { return !periodoHasta.equals(periodo); }
    public String getPeriodoTexto() { return periodoTexto; }
    public Long getProyectoSeleccionado() { return proyectoSeleccionado; }
    public String getProyectoSeleccionadoValor() {
        return proyectoSeleccionado == null ? "" : proyectoSeleccionado.toString();
    }
    public String getEstadoSeleccionado() { return estadoSeleccionado; }
    public String getProyectoSeleccionadoNombre() { return proyectoSeleccionadoNombre; }
    /** @return "Todos los proyectos", el nombre del proyecto filtrado o "No encontrado (ID n)" si el id no existe */
    public String getProyectoFiltroTexto() {
        if (proyectoSeleccionado == null) return "Todos los proyectos";
        return proyectoSeleccionadoNombre == null
                ? "No encontrado (ID " + proyectoSeleccionado + ")" : proyectoSeleccionadoNombre;
    }
    public String getEstadoFiltroTexto() {
        return estadoSeleccionadoTexto == null || estadoSeleccionadoTexto.isBlank()
                ? "Todos" : estadoSeleccionadoTexto;
    }
    public String getPrioridadSeleccionada() { return prioridadSeleccionada; }
    public String getPrioridadFiltroTexto() {
        return prioridadSeleccionadaTexto == null || prioridadSeleccionadaTexto.isBlank()
                ? "Todas" : prioridadSeleccionadaTexto;
    }
    /** Texto único de los filtros aplicados para la vista y el Excel; la prioridad, solo si se eligió. */
    public String getFiltrosTexto() {
        String texto = "Proyecto: " + getProyectoFiltroTexto() + " | Estado: " + getEstadoFiltroTexto();
        return prioridadSeleccionada.isEmpty() ? texto : texto + " | Prioridad: " + getPrioridadFiltroTexto();
    }
    public String getReglaHoras() { return REGLA_HORAS; }
    public List<ProyectoReporte> getProyectos() { return proyectos; }
    public List<DetalleColaborador> getDetalles() { return detalles; }
    public List<ColaboradorReporte> getColaboradores() { return colaboradores; }
    public int getTotalProyectos() { return proyectos.size(); }
    public BigDecimal getPresupuestoTotal() { return presupuestoTotal; }
    public BigDecimal getHorasTotales() { return horasTotales; }
    public long getColaboradoresInvolucrados() { return colaboradoresInvolucrados; }
    /** @return colaboradores con menos de 160 horas en el periodo */
    public long getColaboradoresDebajoReferencia() {
        return colaboradores.stream().filter(item -> item.getHoras().compareTo(BigDecimal.valueOf(160)) < 0).count();
    }
    /** @return colaboradores con 160 horas o más en el periodo */
    public long getColaboradoresEnReferencia() {
        return colaboradores.stream().filter(item -> item.getHoras().compareTo(BigDecimal.valueOf(160)) >= 0).count();
    }

    /** Fila de un proyecto: presupuesto, horas del periodo, colaboradores y su parte de las horas totales. */
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
        /** Porcentaje (0-100) de las horas totales del reporte que corresponde al proyecto. */
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

    /** Horas y tareas completadas de un colaborador en un proyecto durante el periodo. */
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

    /** Totales de un colaborador en el periodo con el desglose por proyecto. */
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
        /** @return {@code true} si sus horas llegan a la referencia de 160 */
        public boolean isAlcanzaReferencia() { return horas.compareTo(BigDecimal.valueOf(160)) >= 0; }
    }

    /** Horas y tareas de un colaborador en uno de sus proyectos. */
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

    /** Opción de un desplegable de filtro: valor enviado y texto visible. */
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
