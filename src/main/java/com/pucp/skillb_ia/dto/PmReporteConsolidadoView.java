package com.pucp.skillb_ia.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Vista consolidada del reporte de proyectos del Project Manager (PM),
 * con filtros por rango de meses (o historial completo), proyecto, estado y prioridad.
 */
public class PmReporteConsolidadoView {

    public static final String REGLA_CALCULO = "Se contabilizan las actividades activas de tus proyectos "
            + "cuya fecha límite o fecha de entrega pertenece al periodo seleccionado (o todas si se elige 'Todo el historial'). "
            + "El presupuesto corresponde al monto aprobado o solicitado de cada proyecto.";

    private final YearMonth periodo;
    private final YearMonth periodoHasta;
    private final boolean todoPeriodo;
    private final String periodoTexto;
    private final Long proyectoSeleccionado;
    private final String proyectoSeleccionadoNombre;
    private final String estadoSeleccionado;
    private final String estadoSeleccionadoTexto;
    private final String prioridadSeleccionada;
    private final String prioridadSeleccionadaTexto;
    private final List<ProyectoFila> proyectos;
    private final List<DetalleColaboradorFila> detalles;
    private final BigDecimal presupuestoTotal;
    private final BigDecimal horasEstimadasTotales;
    private final BigDecimal horasCompletadasTotales;
    private final int totalActividades;
    private final int actividadesCompletadas;
    private final int aTiempoTotal;
    private final int tardiasTotal;
    private final int vencidasTotal;
    private final long colaboradoresInvolucrados;

    public PmReporteConsolidadoView(YearMonth periodo,
                                    YearMonth periodoHasta,
                                    boolean todoPeriodo,
                                    String periodoTexto,
                                    Long proyectoSeleccionado,
                                    String proyectoSeleccionadoNombre,
                                    String estadoSeleccionado,
                                    String estadoSeleccionadoTexto,
                                    String prioridadSeleccionada,
                                    String prioridadSeleccionadaTexto,
                                    List<ProyectoFila> proyectos,
                                    List<DetalleColaboradorFila> detalles) {
        this.periodo = periodo;
        this.periodoHasta = periodoHasta == null ? periodo : periodoHasta;
        this.todoPeriodo = todoPeriodo;
        this.periodoTexto = periodoTexto;
        this.proyectoSeleccionado = proyectoSeleccionado;
        this.proyectoSeleccionadoNombre = proyectoSeleccionadoNombre;
        this.estadoSeleccionado = estadoSeleccionado == null ? "" : estadoSeleccionado;
        this.estadoSeleccionadoTexto = estadoSeleccionadoTexto;
        this.prioridadSeleccionada = prioridadSeleccionada == null ? "" : prioridadSeleccionada;
        this.prioridadSeleccionadaTexto = prioridadSeleccionadaTexto;
        this.proyectos = List.copyOf(proyectos);
        this.detalles = List.copyOf(detalles);

        this.presupuestoTotal = proyectos.stream()
                .map(ProyectoFila::getPresupuesto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.horasEstimadasTotales = proyectos.stream()
                .map(ProyectoFila::getHorasEstimadas)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.horasCompletadasTotales = proyectos.stream()
                .map(ProyectoFila::getHorasCompletadas)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.totalActividades = proyectos.stream()
                .mapToInt(ProyectoFila::getTotalActividades)
                .sum();
        this.actividadesCompletadas = proyectos.stream()
                .mapToInt(ProyectoFila::getActividadesCompletadas)
                .sum();
        this.aTiempoTotal = proyectos.stream()
                .mapToInt(ProyectoFila::getATiempo)
                .sum();
        this.tardiasTotal = proyectos.stream()
                .mapToInt(ProyectoFila::getTardias)
                .sum();
        this.vencidasTotal = proyectos.stream()
                .mapToInt(ProyectoFila::getVencidas)
                .sum();
        this.colaboradoresInvolucrados = detalles.stream()
                .map(DetalleColaboradorFila::getColaboradorId)
                .distinct()
                .count();
    }

    public YearMonth getPeriodo() { return periodo; }
    public String getPeriodoValor() { return periodo.toString(); }
    public YearMonth getPeriodoHasta() { return periodoHasta; }
    public String getPeriodoHastaValor() { return esRango() ? periodoHasta.toString() : ""; }
    public boolean isTodoPeriodo() { return todoPeriodo; }
    public boolean esRango() { return !todoPeriodo && !periodoHasta.equals(periodo); }
    public String getPeriodoTexto() { return periodoTexto; }
    public Long getProyectoSeleccionado() { return proyectoSeleccionado; }
    public String getProyectoSeleccionadoValor() {
        return proyectoSeleccionado == null ? "" : proyectoSeleccionado.toString();
    }
    public String getProyectoSeleccionadoNombre() { return proyectoSeleccionadoNombre; }
    public String getEstadoSeleccionado() { return estadoSeleccionado; }
    public String getPrioridadSeleccionada() { return prioridadSeleccionada; }

    public String getProyectoFiltroTexto() {
        if (proyectoSeleccionado == null) return "Todos mis proyectos";
        return proyectoSeleccionadoNombre == null
                ? "No encontrado (ID " + proyectoSeleccionado + ")" : proyectoSeleccionadoNombre;
    }

    public String getEstadoFiltroTexto() {
        return estadoSeleccionadoTexto == null || estadoSeleccionadoTexto.isBlank()
                ? "Todos" : estadoSeleccionadoTexto;
    }

    public String getPrioridadFiltroTexto() {
        return prioridadSeleccionadaTexto == null || prioridadSeleccionadaTexto.isBlank()
                ? "Todas" : prioridadSeleccionadaTexto;
    }

    public String getFiltrosTexto() {
        String texto = "Proyecto: " + getProyectoFiltroTexto() + " | Estado: " + getEstadoFiltroTexto();
        return prioridadSeleccionada.isEmpty() ? texto : texto + " | Prioridad: " + getPrioridadFiltroTexto();
    }

    public String getReglaCalculo() { return REGLA_CALCULO; }
    public List<ProyectoFila> getProyectos() { return proyectos; }
    public List<DetalleColaboradorFila> getDetalles() { return detalles; }
    public int getTotalProyectos() { return proyectos.size(); }
    public BigDecimal getPresupuestoTotal() { return presupuestoTotal; }
    public BigDecimal getHorasEstimadasTotales() { return horasEstimadasTotales; }
    public BigDecimal getHorasCompletadasTotales() { return horasCompletadasTotales; }
    public int getTotalActividades() { return totalActividades; }
    public int getActividadesCompletadas() { return actividadesCompletadas; }
    public int getATiempoTotal() { return aTiempoTotal; }
    public int getTardiasTotal() { return tardiasTotal; }
    public int getVencidasTotal() { return vencidasTotal; }
    public long getColaboradoresInvolucrados() { return colaboradoresInvolucrados; }

    public int getPuntualidadGlobal() {
        int entregas = aTiempoTotal + tardiasTotal;
        return entregas == 0 ? 0 : Math.round(aTiempoTotal * 100f / entregas);
    }

    public int getProgresoGlobal() {
        return totalActividades == 0 ? 0 : Math.round(actividadesCompletadas * 100f / totalActividades);
    }

    public static class ProyectoFila {
        private final Long id;
        private final String nombre;
        private final String estadoCodigo;
        private final String estadoTexto;
        private final String estadoClase;
        private final String prioridadTexto;
        private final String prioridadClase;
        private final BigDecimal presupuesto;
        private final int integrantesActivos;
        private final int colaboradoresRequeridos;
        private final int totalActividades;
        private final int actividadesCompletadas;
        private final int aTiempo;
        private final int tardias;
        private final int vencidas;
        private final BigDecimal horasEstimadas;
        private final BigDecimal horasCompletadas;
        private final int porcentajeHoras;

        public ProyectoFila(Long id, String nombre, String estadoCodigo,
                            String estadoTexto, String estadoClase,
                            String prioridadTexto, String prioridadClase,
                            BigDecimal presupuesto,
                            int integrantesActivos, int colaboradoresRequeridos,
                            int totalActividades, int actividadesCompletadas,
                            int aTiempo, int tardias, int vencidas,
                            BigDecimal horasEstimadas, BigDecimal horasCompletadas,
                            int porcentajeHoras) {
            this.id = id;
            this.nombre = nombre;
            this.estadoCodigo = estadoCodigo;
            this.estadoTexto = estadoTexto;
            this.estadoClase = estadoClase;
            this.prioridadTexto = prioridadTexto;
            this.prioridadClase = prioridadClase;
            this.presupuesto = presupuesto;
            this.integrantesActivos = integrantesActivos;
            this.colaboradoresRequeridos = colaboradoresRequeridos;
            this.totalActividades = totalActividades;
            this.actividadesCompletadas = actividadesCompletadas;
            this.aTiempo = aTiempo;
            this.tardias = tardias;
            this.vencidas = vencidas;
            this.horasEstimadas = horasEstimadas;
            this.horasCompletadas = horasCompletadas;
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
        public int getIntegrantesActivos() { return integrantesActivos; }
        public int getColaboradoresRequeridos() { return colaboradoresRequeridos; }
        public String getEquipoTexto() { return integrantesActivos + " / " + colaboradoresRequeridos; }
        public int getTotalActividades() { return totalActividades; }
        public int getActividadesCompletadas() { return actividadesCompletadas; }
        public String getTareasTexto() { return actividadesCompletadas + " / " + totalActividades; }
        public int getATiempo() { return aTiempo; }
        public int getTardias() { return tardias; }
        public int getVencidas() { return vencidas; }
        public BigDecimal getHorasEstimadas() { return horasEstimadas; }
        public BigDecimal getHorasCompletadas() { return horasCompletadas; }
        public int getPorcentajeHoras() { return porcentajeHoras; }

        public int getProgresoPorc() {
            return totalActividades == 0 ? 0 : Math.round(actividadesCompletadas * 100f / totalActividades);
        }

        public int getPuntualidadPorc() {
            int entregas = aTiempo + tardias;
            return entregas == 0 ? 0 : Math.round(aTiempo * 100f / entregas);
        }
    }

    public static class DetalleColaboradorFila {
        private final Long colaboradorId;
        private final String colaborador;
        private final Long proyectoId;
        private final String proyecto;
        private final BigDecimal horasSemanales;
        private final int asignadas;
        private final int completadas;
        private final int aTiempo;
        private final int tardias;
        private final int vencidas;
        private final int devoluciones;
        private final BigDecimal horasCompletadas;
        private final int strikes;

        public DetalleColaboradorFila(Long colaboradorId, String colaborador,
                                      Long proyectoId, String proyecto,
                                      BigDecimal horasSemanales,
                                      int asignadas, int completadas,
                                      int aTiempo, int tardias, int vencidas,
                                      int devoluciones, BigDecimal horasCompletadas,
                                      int strikes) {
            this.colaboradorId = colaboradorId;
            this.colaborador = colaborador;
            this.proyectoId = proyectoId;
            this.proyecto = proyecto;
            this.horasSemanales = horasSemanales;
            this.asignadas = asignadas;
            this.completadas = completadas;
            this.aTiempo = aTiempo;
            this.tardias = tardias;
            this.vencidas = vencidas;
            this.devoluciones = devoluciones;
            this.horasCompletadas = horasCompletadas;
            this.strikes = strikes;
        }

        public Long getColaboradorId() { return colaboradorId; }
        public String getColaborador() { return colaborador; }
        public Long getProyectoId() { return proyectoId; }
        public String getProyecto() { return proyecto; }
        public BigDecimal getHorasSemanales() { return horasSemanales; }
        public int getAsignadas() { return asignadas; }
        public int getCompletadas() { return completadas; }
        public int getATiempo() { return aTiempo; }
        public int getTardias() { return tardias; }
        public int getVencidas() { return vencidas; }
        public int getDevoluciones() { return devoluciones; }
        public BigDecimal getHorasCompletadas() { return horasCompletadas; }
        public int getStrikes() { return strikes; }

        public int getRendimientoPorc() {
            return asignadas == 0 ? 0 : Math.round(completadas * 100f / asignadas);
        }
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
