package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.dto.PmReporteConsolidadoView;
import com.pucp.skillb_ia.dto.PmReporteView;
import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.ActividadRepository;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PmReporteService {

    public static final int TAMANIO_PAGINA = 10;
    public static final int MAXIMO_MESES_RANGO = 12;
    private static final int ANIOS_FILTRO = 5;
    private static final Locale LOCALE_ES = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter FORMATO_PERIODO =
            DateTimeFormatter.ofPattern("MMMM yyyy", LOCALE_ES);

    private final ProyectoRepository proyectoRepository;
    private final ActividadRepository actividadRepository;
    private final AsignacionRepository asignacionRepository;
    private final com.pucp.skillb_ia.service.PenalizacionService penalizacionService;

    // ---------- Avance del proyecto (puntualidad, vencidas, devoluciones, horas y strikes) ----------

    public record AvanceColaborador(String nombre, int asignadas, int completadas, int aTiempo, int tardias,
                                    int vencidas, int devoluciones, BigDecimal horasCompletadas, int strikes) {
    }

    public record Avance(String periodo, int total, int completadas, int aTiempo, int tardias, int vencidas,
                         int devoluciones, BigDecimal horasEstimadas, BigDecimal horasCompletadas,
                         List<AvanceColaborador> colaboradores) {
        /** % de entregas a tiempo sobre las entregas registradas; 0 si aún no hay entregas. */
        public int puntualidad() {
            int entregas = aTiempo + tardias;
            return entregas == 0 ? 0 : Math.round(aTiempo * 100f / entregas);
        }
    }

    // ---------- Consolidado de proyectos del PM (al estilo RM) ----------

    public record FiltrosConsolidado(String periodo, String hasta, boolean todoPeriodo,
                                     Long proyecto, String estado, String prioridad) {
    }

    public record PaginaReporte<T>(List<T> filas, int paginaActual, int totalPaginas, long totalRegistros) {
    }

    public record PaginaConsolidado(PmReporteConsolidadoView reporte,
                                    PaginaReporte<PmReporteConsolidadoView.ProyectoFila> proyectos,
                                    PaginaReporte<PmReporteConsolidadoView.DetalleColaboradorFila> colaboradores) {
    }

    public PmReporteService(ProyectoRepository proyectoRepository,
                            ActividadRepository actividadRepository,
                            AsignacionRepository asignacionRepository,
                            com.pucp.skillb_ia.service.PenalizacionService penalizacionService) {
        this.penalizacionService = penalizacionService;
        this.proyectoRepository = proyectoRepository;
        this.actividadRepository = actividadRepository;
        this.asignacionRepository = asignacionRepository;
    }

    // ═══════════════════════════════════════════════════════════
    // REPORTE CONSOLIDADO DEL PM (MULTICRITERIO + RANGO DE MESES)
    // ═══════════════════════════════════════════════════════════

    public FiltrosConsolidado normalizarFiltrosConsolidado(String periodo, String hasta, boolean todoPeriodo,
                                                           Long proyectoId, String estado, String prioridad) {
        YearMonth inicio = obtenerPeriodo(periodo);
        YearMonth fin = obtenerHasta(inicio, hasta);
        EstadoProyecto estadoValido = obtenerEstado(estado);
        Prioridad prioridadValida = obtenerPrioridad(prioridad);
        return new FiltrosConsolidado(
                inicio.toString(),
                fin.equals(inicio) ? null : fin.toString(),
                todoPeriodo,
                proyectoId,
                estadoValido == null ? null : estadoValido.name(),
                prioridadValida == null ? null : prioridadValida.name());
    }

    @Transactional(readOnly = true)
    public PaginaConsolidado listarPaginaConsolidado(Usuario pm, FiltrosConsolidado filtros,
                                                     String paginaProyectos, String paginaColaboradores) {
        PmReporteConsolidadoView reporte = generarConsolidado(
                pm, filtros.periodo(), filtros.hasta(), filtros.todoPeriodo(),
                filtros.proyecto(), filtros.estado(), filtros.prioridad());
        return new PaginaConsolidado(
                reporte,
                paginar(reporte.getProyectos(), paginaProyectos),
                paginar(reporte.getDetalles(), paginaColaboradores));
    }

    @Transactional(readOnly = true)
    public PmReporteConsolidadoView generarConsolidado(Usuario pm, String periodoValor, String hastaValor,
                                                       boolean todoPeriodo, Long proyectoId,
                                                       String estadoValor, String prioridadValor) {
        YearMonth periodo = obtenerPeriodo(periodoValor);
        YearMonth hasta = obtenerHasta(periodo, hastaValor);
        EstadoProyecto estado = obtenerEstado(estadoValor);
        Prioridad prioridad = obtenerPrioridad(prioridadValor);
        LocalDate hoy = LocalDate.now();

        List<Proyecto> todosDelPm = proyectoRepository.findByPmOrderByFechaCreacionDesc(pm);
        String proyectoNombre = todosDelPm.stream()
                .filter(p -> p.getId().equals(proyectoId))
                .map(Proyecto::getNombre)
                .findFirst().orElse(null);

        List<Proyecto> proyectosFiltrados = todosDelPm.stream()
                .filter(p -> proyectoId == null || p.getId().equals(proyectoId))
                .filter(p -> estado == null || p.getEstado() == estado)
                .filter(p -> prioridad == null || p.getPrioridad() == prioridad)
                .toList();

        Map<Long, List<Actividad>> actividadesPorProyecto = new LinkedHashMap<>();
        Map<Long, BigDecimal> horasCompPorProyecto = new LinkedHashMap<>();

        for (Proyecto p : proyectosFiltrados) {
            List<Actividad> acts = actividadRepository.findByProyectoAndActivoTrue(p).stream()
                    .filter(a -> todoPeriodo || perteneceAlRango(a, periodo, hasta))
                    .toList();
            actividadesPorProyecto.put(p.getId(), acts);
            horasCompPorProyecto.put(p.getId(), horasCompletadas(acts));
        }

        BigDecimal maximoHoras = horasCompPorProyecto.values().stream()
                .max(BigDecimal::compareTo)
                .filter(v -> v.signum() > 0)
                .orElse(BigDecimal.ONE);

        List<PmReporteConsolidadoView.ProyectoFila> filasProyecto = new ArrayList<>();
        List<PmReporteConsolidadoView.DetalleColaboradorFila> filasDetalle = new ArrayList<>();

        for (Proyecto p : proyectosFiltrados) {
            List<Actividad> acts = actividadesPorProyecto.getOrDefault(p.getId(), List.of());
            List<Asignacion> activas = asignacionRepository.findByProyectoAndEstado(p, EstadoAsignacion.ACTIVA);
            BigDecimal hEstimadas = acts.stream()
                    .map(Actividad::getHorasEstimadas)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal hCompletadas = horasCompPorProyecto.getOrDefault(p.getId(), BigDecimal.ZERO);
            int porcentajeHoras = hCompletadas.multiply(BigDecimal.valueOf(100))
                    .divide(maximoHoras, 0, RoundingMode.HALF_UP).intValue();

            BigDecimal presupuestoVisible = p.getPresupuesto() != null
                    ? p.getPresupuesto()
                    : (p.getPresupuestoSolicitado() != null ? p.getPresupuestoSolicitado() : BigDecimal.ZERO);

            filasProyecto.add(new PmReporteConsolidadoView.ProyectoFila(
                    p.getId(),
                    p.getNombre(),
                    p.getEstado().name(),
                    traducirEstado(p.getEstado()),
                    claseEstado(p.getEstado()),
                    textoPrioridad(p.getPrioridad()),
                    clasePrioridad(p.getPrioridad()),
                    presupuestoVisible,
                    activas.size(),
                    p.getColaboradoresRequeridos(),
                    acts.size(),
                    completadas(acts),
                    aTiempo(acts),
                    tardias(acts),
                    vencidas(acts, hoy),
                    hEstimadas,
                    hCompletadas,
                    porcentajeHoras
            ));

            Map<Long, List<Actividad>> porColab = acts.stream()
                    .collect(Collectors.groupingBy(a -> a.getColaborador().getId()));

            for (Asignacion asig : activas) {
                Usuario col = asig.getColaborador();
                List<Actividad> suyas = porColab.getOrDefault(col.getId(), List.of());
                filasDetalle.add(new PmReporteConsolidadoView.DetalleColaboradorFila(
                        col.getId(),
                        col.getNombre() + " " + col.getApellido(),
                        p.getId(),
                        p.getNombre(),
                        asig.getHorasSemanales(),
                        suyas.size(),
                        completadas(suyas),
                        aTiempo(suyas),
                        tardias(suyas),
                        vencidas(suyas, hoy),
                        devoluciones(suyas),
                        horasCompletadas(suyas),
                        penalizacionService.contarStrikes(col, p)
                ));
            }
        }

        filasDetalle.sort(Comparator.comparing(PmReporteConsolidadoView.DetalleColaboradorFila::getHorasCompletadas).reversed()
                .thenComparing(PmReporteConsolidadoView.DetalleColaboradorFila::getColaborador));

        String textoRango = todoPeriodo
                ? "Todo el historial"
                : (hasta.equals(periodo) ? textoPeriodo(periodo) : textoPeriodo(periodo) + " – " + textoPeriodo(hasta));

        return new PmReporteConsolidadoView(
                periodo, hasta, todoPeriodo, textoRango,
                proyectoId, proyectoNombre,
                estado == null ? "" : estado.name(),
                estado == null ? null : traducirEstado(estado),
                prioridad == null ? "" : prioridad.name(),
                prioridad == null ? null : textoPrioridad(prioridad),
                filasProyecto, filasDetalle
        );
    }

    @Transactional(readOnly = true)
    public List<PmReporteConsolidadoView.Opcion> listarProyectos(Usuario pm) {
        return proyectoRepository.findByPmOrderByFechaCreacionDesc(pm).stream()
                .map(p -> new PmReporteConsolidadoView.Opcion(p.getId().toString(), p.getNombre()))
                .toList();
    }

    public String componerPeriodo(String anio, String mes, String periodo) {
        if (anio != null && mes != null && anio.trim().matches("\\d{4}")
                && mes.trim().matches("0[1-9]|1[0-2]")) {
            return anio.trim() + "-" + mes.trim();
        }
        return periodo;
    }

    public List<Integer> listarAnios(PmReporteConsolidadoView reporte) {
        TreeSet<Integer> anios = new TreeSet<>(Comparator.reverseOrder());
        int actual = YearMonth.now().getYear();
        for (int indice = 0; indice < ANIOS_FILTRO; indice++) anios.add(actual - indice);
        anios.add(reporte.getPeriodo().getYear());
        anios.add(reporte.getPeriodoHasta().getYear());
        return List.copyOf(anios);
    }

    public List<PmReporteConsolidadoView.Opcion> listarMeses() {
        return Arrays.stream(Month.values())
                .map(mes -> {
                    String texto = mes.getDisplayName(TextStyle.FULL_STANDALONE, LOCALE_ES);
                    return new PmReporteConsolidadoView.Opcion(String.format("%02d", mes.getValue()),
                            Character.toUpperCase(texto.charAt(0)) + texto.substring(1));
                })
                .toList();
    }

    public List<PmReporteConsolidadoView.Opcion> listarEstados() {
        return Arrays.stream(EstadoProyecto.values())
                .map(e -> new PmReporteConsolidadoView.Opcion(e.name(), traducirEstado(e)))
                .toList();
    }

    public List<PmReporteConsolidadoView.Opcion> listarPrioridades() {
        return Arrays.stream(Prioridad.values())
                .map(p -> new PmReporteConsolidadoView.Opcion(p.name(), textoPrioridad(p)))
                .toList();
    }

    // ═══════════════════════════════════════════════════════════
    // REPORTE INDIVIDUAL DE UN PROYECTO (DETALLE)
    // ═══════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public PmReporteView obtener(Long proyectoId, Usuario pm) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));
        if (!proyecto.getPm().getId().equals(pm.getId())) {
            throw new SecurityException("No tienes permiso sobre este proyecto.");
        }

        List<Actividad> actividades = actividadRepository.findByProyectoAndActivoTrue(proyecto);
        int total       = actividades.size();
        int pendientes  = (int) actividades.stream().filter(a -> a.getEstado() == EstadoActividad.PENDIENTE).count();
        int enProgreso  = (int) actividades.stream().filter(a -> a.getEstado() == EstadoActividad.EN_PROGRESO).count();
        int enRevision  = (int) actividades.stream().filter(a -> a.getEstado() == EstadoActividad.EN_REVISION).count();
        int completadas = (int) actividades.stream().filter(a -> a.getEstado() == EstadoActividad.COMPLETADA).count();

        BigDecimal horasTotal = actividades.stream()
                .map(Actividad::getHorasEstimadas)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Asignacion> activas = asignacionRepository.findByProyectoAndEstado(
                proyecto, EstadoAsignacion.ACTIVA);

        Map<Long, List<Actividad>> porColaborador = actividades.stream()
                .collect(Collectors.groupingBy(a -> a.getColaborador().getId()));

        List<PmReporteView.ColaboradorMetrica> metricas = activas.stream()
                .map(asig -> {
                    Usuario col = asig.getColaborador();
                    List<Actividad> acts = porColaborador.getOrDefault(col.getId(), List.of());
                    int asignadas = acts.size();
                    int comp = (int) acts.stream()
                            .filter(a -> a.getEstado() == EstadoActividad.COMPLETADA).count();
                    String nombre = col.getNombre() + " " + col.getApellido();
                    String iniciales = iniciales(col.getNombre(), col.getApellido());
                    return new PmReporteView.ColaboradorMetrica(
                            nombre, iniciales, asig.getHorasSemanales(), asignadas, comp);
                })
                .toList();

        return new PmReporteView(proyecto, total, pendientes, enProgreso, enRevision,
                completadas, activas.size(), horasTotal, metricas);
    }

    /**
     * Avance del proyecto del PM. Con un mes, solo cuenta las actividades cuya fecha límite cae en ese mes;
     * sin mes (null), todas las actividades del proyecto.
     */
    @Transactional(readOnly = true)
    public Avance avance(Long proyectoId, Usuario pm, java.time.YearMonth mes) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));
        if (!proyecto.getPm().getId().equals(pm.getId())) {
            throw new SecurityException("No tienes permiso sobre este proyecto.");
        }
        java.time.LocalDate hoy = java.time.LocalDate.now();
        List<Actividad> actividades = actividadRepository.findByProyectoAndActivoTrue(proyecto).stream()
                .filter(a -> mes == null || com.pucp.skillb_ia.util.Periodo.contiene(mes, a.getFechaLimite()))
                .toList();

        Map<Long, List<Actividad>> porColaborador = actividades.stream()
                .collect(Collectors.groupingBy(a -> a.getColaborador().getId()));
        List<AvanceColaborador> filas = new ArrayList<>();
        for (Asignacion asignacion : asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA)) {
            Usuario colaborador = asignacion.getColaborador();
            List<Actividad> suyas = porColaborador.getOrDefault(colaborador.getId(), List.of());
            filas.add(new AvanceColaborador(colaborador.getNombre() + " " + colaborador.getApellido(),
                    suyas.size(), completadas(suyas), aTiempo(suyas), tardias(suyas), vencidas(suyas, hoy),
                    devoluciones(suyas), horasCompletadas(suyas), penalizacionService.contarStrikes(colaborador, proyecto)));
        }
        filas.sort(java.util.Comparator.comparing(AvanceColaborador::nombre));

        BigDecimal horasEstimadas = actividades.stream().map(Actividad::getHorasEstimadas)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        String periodo = mes == null ? "Todo el proyecto" : com.pucp.skillb_ia.util.Periodo.etiqueta(mes);
        return new Avance(periodo, actividades.size(), completadas(actividades), aTiempo(actividades),
                tardias(actividades), vencidas(actividades, hoy), devoluciones(actividades), horasEstimadas,
                horasCompletadas(actividades), filas);
    }

    /** Excel del avance: hoja de resumen y hoja por colaborador. */
    public byte[] excelAvance(String nombreProyecto, Avance a) {
        List<List<Object>> resumen = List.of(
                List.of("Proyecto", nombreProyecto),
                List.of("Periodo", a.periodo()),
                List.of("Actividades", a.total()),
                List.of("Completadas", a.completadas()),
                List.of("Entregadas a tiempo", a.aTiempo()),
                List.of("Entregadas tarde", a.tardias()),
                List.of("Puntualidad (%)", a.puntualidad()),
                List.of("Vencidas sin completar", a.vencidas()),
                List.of("Devoluciones", a.devoluciones()),
                List.of("Horas estimadas", a.horasEstimadas()),
                List.of("Horas completadas", a.horasCompletadas()));
        List<List<Object>> colaboradores = a.colaboradores().stream()
                .map(c -> List.<Object>of(c.nombre(), c.asignadas(), c.completadas(), c.aTiempo(), c.tardias(),
                        c.vencidas(), c.devoluciones(), c.horasCompletadas(), c.strikes()))
                .toList();
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("Fecha de generación", com.pucp.skillb_ia.util.ReportePdf.ahora());
        return com.pucp.skillb_ia.util.ReporteExcel.crear("Reporte del proyecto: " + nombreProyecto, datos, List.of(
                new com.pucp.skillb_ia.util.ReporteExcel.Hoja("Resumen", List.of("Indicador", "Valor"), resumen),
                new com.pucp.skillb_ia.util.ReporteExcel.Hoja("Colaboradores", List.of("Colaborador", "Asignadas",
                        "Completadas", "A tiempo", "Tarde", "Vencidas", "Devoluciones", "Horas completadas",
                        "Strikes"), colaboradores)));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private boolean perteneceAlRango(Actividad a, YearMonth inicio, YearMonth fin) {
        if (a.getFechaEntrega() != null) {
            YearMonth ymEntrega = YearMonth.from(a.getFechaEntrega());
            if (!ymEntrega.isBefore(inicio) && !ymEntrega.isAfter(fin)) return true;
        }
        if (a.getFechaLimite() != null) {
            YearMonth ymLimite = YearMonth.from(a.getFechaLimite());
            return !ymLimite.isBefore(inicio) && !ymLimite.isAfter(fin);
        }
        return false;
    }

    private YearMonth obtenerPeriodo(String valor) {
        YearMonth actual = YearMonth.now();
        if (valor == null || valor.isBlank()) return actual;
        try {
            YearMonth ym = YearMonth.parse(valor.trim());
            return ym.isAfter(actual) ? actual : ym;
        } catch (DateTimeParseException ex) {
            return actual;
        }
    }

    private YearMonth obtenerHasta(YearMonth inicio, String hastaValor) {
        if (hastaValor == null || hastaValor.isBlank()) return inicio;
        try {
            YearMonth hasta = YearMonth.parse(hastaValor.trim());
            YearMonth actual = YearMonth.now();
            if (hasta.isAfter(actual)) hasta = actual;
            if (hasta.isBefore(inicio)) return inicio;
            YearMonth tope = inicio.plusMonths(MAXIMO_MESES_RANGO - 1);
            return hasta.isAfter(tope) ? tope : hasta;
        } catch (DateTimeParseException ex) {
            return inicio;
        }
    }

    private EstadoProyecto obtenerEstado(String valor) {
        if (valor == null || valor.isBlank()) return null;
        return Arrays.stream(EstadoProyecto.values())
                .filter(e -> e.name().equalsIgnoreCase(valor.trim()))
                .findFirst().orElse(null);
    }

    private Prioridad obtenerPrioridad(String valor) {
        if (valor == null || valor.isBlank()) return null;
        return Arrays.stream(Prioridad.values())
                .filter(p -> p.name().equalsIgnoreCase(valor.trim()))
                .findFirst().orElse(null);
    }

    private String textoPeriodo(YearMonth periodo) {
        String texto = periodo.format(FORMATO_PERIODO);
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    private static String traducirEstado(EstadoProyecto estado) {
        return switch (estado) {
            case EN_REVISION -> "En revisión";
            case ACTIVO      -> "Activo";
            case RECHAZADO   -> "Rechazado";
            case EN_ESPERA   -> "En espera";
            case CANCELADO   -> "Cancelado";
            case FINALIZADO  -> "Finalizado";
        };
    }

    private static String claseEstado(EstadoProyecto estado) {
        return switch (estado) {
            case ACTIVO      -> "bg-green-lt text-green";
            case EN_REVISION -> "bg-yellow-lt text-yellow";
            case EN_ESPERA   -> "bg-orange-lt text-orange";
            case FINALIZADO  -> "bg-blue-lt text-blue";
            case RECHAZADO, CANCELADO -> "bg-red-lt text-red";
        };
    }

    private static String textoPrioridad(Prioridad prioridad) {
        if (prioridad == null) return "Media";
        return switch (prioridad) {
            case ALTA  -> "Alta";
            case MEDIA -> "Media";
            case BAJA  -> "Baja";
        };
    }

    private static String clasePrioridad(Prioridad prioridad) {
        if (prioridad == null) return "bg-yellow-lt text-yellow";
        return switch (prioridad) {
            case ALTA  -> "bg-red-lt text-red";
            case MEDIA -> "bg-yellow-lt text-yellow";
            case BAJA  -> "bg-blue-lt text-blue";
        };
    }

    private <T> PaginaReporte<T> paginar(List<T> elementos, String paginaValor) {
        int totalPaginas = Math.max(1, (int) Math.ceil(elementos.size() / (double) TAMANIO_PAGINA));
        int paginaActual = Math.min(Math.max(1, numeroPagina(paginaValor)), totalPaginas);
        int desde = (paginaActual - 1) * TAMANIO_PAGINA;
        int hasta = Math.min(desde + TAMANIO_PAGINA, elementos.size());
        List<T> filas = desde < hasta ? elementos.subList(desde, hasta) : List.of();
        return new PaginaReporte<>(filas, paginaActual, totalPaginas, elementos.size());
    }

    private int numeroPagina(String valor) {
        if (valor == null) return 1;
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private int completadas(List<Actividad> lista) {
        return (int) lista.stream().filter(a -> a.getEstado() == EstadoActividad.COMPLETADA).count();
    }

    private int aTiempo(List<Actividad> lista) {
        return (int) lista.stream().filter(a -> a.getEstadoEntrega() == com.pucp.skillb_ia.model.enums.EstadoEntrega.A_TIEMPO).count();
    }

    private int tardias(List<Actividad> lista) {
        return (int) lista.stream().filter(a -> a.getEstadoEntrega() == com.pucp.skillb_ia.model.enums.EstadoEntrega.TARDIA).count();
    }

    private int vencidas(List<Actividad> lista, java.time.LocalDate hoy) {
        return (int) lista.stream().filter(a -> a.getEstado() != EstadoActividad.COMPLETADA
                && a.getFechaLimite() != null && a.getFechaLimite().isBefore(hoy)).count();
    }

    private int devoluciones(List<Actividad> lista) {
        return lista.stream().mapToInt(Actividad::getVecesDevuelta).sum();
    }

    private BigDecimal horasCompletadas(List<Actividad> lista) {
        return lista.stream().filter(a -> a.getEstado() == EstadoActividad.COMPLETADA)
                .map(Actividad::getHorasEstimadas).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String iniciales(String nombre, String apellido) {
        String n = (nombre != null && !nombre.isEmpty()) ? String.valueOf(nombre.charAt(0)) : "";
        String a = (apellido != null && !apellido.isEmpty()) ? String.valueOf(apellido.charAt(0)) : "";
        return (n + a).toUpperCase();
    }
}
