package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmReporteView;
import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.repository.ActividadRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class RmReporteService {
    private static final Locale LOCALE_ES = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter FORMATO_PERIODO =
            DateTimeFormatter.ofPattern("MMMM yyyy", LOCALE_ES);

    private final ProyectoRepository proyectoRepository;
    private final ActividadRepository actividadRepository;

    public RmReporteService(ProyectoRepository proyectoRepository,
                            ActividadRepository actividadRepository) {
        this.proyectoRepository = proyectoRepository;
        this.actividadRepository = actividadRepository;
    }

    @Transactional(readOnly = true)
    public RmReporteView generar(String periodoValor, Long proyectoId, String estadoValor) {
        YearMonth periodo = obtenerPeriodo(periodoValor);
        EstadoProyecto estado = obtenerEstado(estadoValor);
        List<Actividad> actividades = actividadRepository
                .findByEstadoAndFechaEntregaBetweenConDetalle(
                        EstadoActividad.COMPLETADA,
                        periodo.atDay(1).atStartOfDay(),
                        periodo.plusMonths(1).atDay(1).atStartOfDay());

        Map<Long, List<Actividad>> actividadesPorProyecto = new HashMap<>();
        actividades.forEach(actividad -> actividadesPorProyecto
                .computeIfAbsent(actividad.getProyecto().getId(), clave -> new ArrayList<>())
                .add(actividad));

        List<Proyecto> proyectos = proyectoRepository.findAllConPmOrderByFechaCreacionDesc().stream()
                .filter(proyecto -> proyectoId == null || proyecto.getId().equals(proyectoId))
                .filter(proyecto -> estado == null || proyecto.getEstado() == estado)
                .toList();

        Map<Long, BigDecimal> horasPorProyecto = new HashMap<>();
        proyectos.forEach(proyecto -> horasPorProyecto.put(proyecto.getId(),
                sumarHoras(actividadesPorProyecto.getOrDefault(proyecto.getId(), List.of()))));
        BigDecimal maximoHoras = horasPorProyecto.values().stream()
                .max(BigDecimal::compareTo)
                .filter(valor -> valor.signum() > 0)
                .orElse(BigDecimal.ONE);

        List<RmReporteView.ProyectoReporte> filasProyecto = proyectos.stream()
                .map(proyecto -> crearProyectoReporte(
                        proyecto,
                        actividadesPorProyecto.getOrDefault(proyecto.getId(), List.of()),
                        horasPorProyecto.get(proyecto.getId()),
                        maximoHoras))
                .toList();

        Set<Long> proyectosIncluidos = proyectos.stream()
                .map(Proyecto::getId)
                .collect(java.util.stream.Collectors.toSet());
        List<RmReporteView.DetalleColaborador> detalles = crearDetalles(
                actividades.stream()
                        .filter(actividad -> proyectosIncluidos.contains(actividad.getProyecto().getId()))
                        .toList());
        List<RmReporteView.ColaboradorReporte> colaboradores = crearColaboradores(detalles);

        return new RmReporteView(
                periodo, textoPeriodo(periodo), proyectoId,
                estado == null ? "" : estado.name(),
                filasProyecto, detalles, colaboradores);
    }

    @Transactional(readOnly = true)
    public List<RmReporteView.Opcion> listarProyectos() {
        return proyectoRepository.findAllConPmOrderByFechaCreacionDesc().stream()
                .map(proyecto -> new RmReporteView.Opcion(
                        proyecto.getId().toString(), proyecto.getNombre()))
                .toList();
    }

    public List<RmReporteView.Opcion> listarPeriodos(String periodoValor) {
        YearMonth seleccionado = obtenerPeriodo(periodoValor);
        LinkedHashSet<YearMonth> periodos = new LinkedHashSet<>();
        periodos.add(seleccionado);
        YearMonth actual = YearMonth.now();
        for (int indice = 0; indice < 12; indice++) periodos.add(actual.minusMonths(indice));
        return periodos.stream()
                .sorted(Comparator.reverseOrder())
                .map(periodo -> new RmReporteView.Opcion(periodo.toString(), textoPeriodo(periodo)))
                .toList();
    }

    public List<RmReporteView.Opcion> listarEstados() {
        return Arrays.stream(EstadoProyecto.values())
                .map(estado -> new RmReporteView.Opcion(estado.name(), textoEnum(estado.name())))
                .toList();
    }

    public List<RmReporteView.ColaboradorReporte> filtrarColaboradores(
            RmReporteView reporte, String busqueda) {
        String filtro = normalizar(busqueda);
        if (filtro.isBlank()) return reporte.getColaboradores();
        return reporte.getColaboradores().stream()
                .filter(item -> normalizar(item.getNombre() + " " + item.getCargo()).contains(filtro))
                .toList();
    }

    private RmReporteView.ProyectoReporte crearProyectoReporte(
            Proyecto proyecto, List<Actividad> actividades,
            BigDecimal horas, BigDecimal maximoHoras) {
        long colaboradores = actividades.stream()
                .map(actividad -> actividad.getColaborador().getId())
                .distinct().count();
        int porcentaje = horas.multiply(BigDecimal.valueOf(100))
                .divide(maximoHoras, 0, RoundingMode.HALF_UP).intValue();
        return new RmReporteView.ProyectoReporte(
                proyecto.getId(), proyecto.getNombre(), proyecto.getEstado().name(),
                textoEnum(proyecto.getEstado().name()), claseEstado(proyecto.getEstado()),
                textoEnum(proyecto.getPrioridad().name()), clasePrioridad(proyecto),
                proyecto.getPresupuesto() == null ? BigDecimal.ZERO : proyecto.getPresupuesto(),
                horas, colaboradores, porcentaje);
    }

    private List<RmReporteView.DetalleColaborador> crearDetalles(List<Actividad> actividades) {
        Map<DetalleKey, MutableDetalle> agrupados = new LinkedHashMap<>();
        actividades.forEach(actividad -> {
            Usuario colaborador = actividad.getColaborador();
            Proyecto proyecto = actividad.getProyecto();
            DetalleKey clave = new DetalleKey(colaborador.getId(), proyecto.getId());
            MutableDetalle detalle = agrupados.computeIfAbsent(clave,
                    ignorada -> new MutableDetalle(
                            colaborador.getId(), nombreCompleto(colaborador),
                            valor(colaborador.getCargo(), "Cargo sin registrar"),
                            proyecto.getId(), proyecto.getNombre()));
            detalle.horas = detalle.horas.add(actividad.getHorasEstimadas());
            detalle.tareas++;
        });
        return agrupados.values().stream()
                .map(item -> new RmReporteView.DetalleColaborador(
                        item.colaboradorId, item.colaborador, item.cargo,
                        item.proyectoId, item.proyecto, item.horas, item.tareas))
                .sorted(Comparator.comparing(RmReporteView.DetalleColaborador::getColaborador,
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(RmReporteView.DetalleColaborador::getProyecto,
                                String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<RmReporteView.ColaboradorReporte> crearColaboradores(
            List<RmReporteView.DetalleColaborador> detalles) {
        Map<Long, MutableColaborador> agrupados = new LinkedHashMap<>();
        detalles.forEach(detalle -> {
            MutableColaborador colaborador = agrupados.computeIfAbsent(
                    detalle.getColaboradorId(),
                    ignorada -> new MutableColaborador(
                            detalle.getColaboradorId(), detalle.getColaborador(), detalle.getCargo()));
            colaborador.horas = colaborador.horas.add(detalle.getHoras());
            colaborador.tareas += detalle.getTareas();
            colaborador.proyectos.add(new RmReporteView.DesgloseProyecto(
                    detalle.getProyectoId(), detalle.getProyecto(),
                    detalle.getHoras(), detalle.getTareas()));
        });
        return agrupados.values().stream()
                .map(item -> new RmReporteView.ColaboradorReporte(
                        item.id, item.nombre, item.cargo,
                        item.horas, item.tareas, item.proyectos))
                .sorted(Comparator.comparing(RmReporteView.ColaboradorReporte::getHoras).reversed()
                        .thenComparing(RmReporteView.ColaboradorReporte::getNombre,
                                String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private BigDecimal sumarHoras(List<Actividad> actividades) {
        return actividades.stream().map(Actividad::getHorasEstimadas)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private YearMonth obtenerPeriodo(String valor) {
        if (valor != null && valor.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            try {
                return YearMonth.parse(valor);
            } catch (RuntimeException ignored) {
                // Se usa el mes actual si el periodo recibido no es válido.
            }
        }
        return YearMonth.now();
    }

    private EstadoProyecto obtenerEstado(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return EstadoProyecto.valueOf(valor);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String textoPeriodo(YearMonth periodo) {
        String texto = periodo.format(FORMATO_PERIODO);
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    private String textoEnum(String valor) {
        String texto = valor.toLowerCase(LOCALE_ES).replace('_', ' ');
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    private String claseEstado(EstadoProyecto estado) {
        return switch (estado) {
            case ACTIVO -> "bg-green-lt";
            case EN_REVISION -> "bg-yellow-lt";
            case EN_ESPERA -> "bg-orange-lt";
            case FINALIZADO -> "bg-blue-lt";
            case RECHAZADO -> "bg-red-lt";
            case CANCELADO -> "bg-secondary-lt";
        };
    }

    private String clasePrioridad(Proyecto proyecto) {
        return switch (proyecto.getPrioridad()) {
            case ALTA -> "bg-red-lt text-red";
            case MEDIA -> "bg-yellow-lt text-yellow";
            case BAJA -> "bg-blue-lt text-blue";
        };
    }

    private String nombreCompleto(Usuario usuario) {
        String completo = (valor(usuario.getNombre(), "") + " "
                + valor(usuario.getApellido(), "")).trim();
        return completo.isBlank() ? usuario.getCorreo() : completo;
    }

    private String valor(String texto, String alternativa) {
        return texto == null || texto.isBlank() ? alternativa : texto;
    }

    private String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(LOCALE_ES).trim();
    }

    private record DetalleKey(Long colaboradorId, Long proyectoId) {}

    private static class MutableDetalle {
        private final Long colaboradorId;
        private final String colaborador;
        private final String cargo;
        private final Long proyectoId;
        private final String proyecto;
        private BigDecimal horas = BigDecimal.ZERO;
        private long tareas;

        private MutableDetalle(Long colaboradorId, String colaborador, String cargo,
                               Long proyectoId, String proyecto) {
            this.colaboradorId = colaboradorId;
            this.colaborador = colaborador;
            this.cargo = cargo;
            this.proyectoId = proyectoId;
            this.proyecto = proyecto;
        }
    }

    private static class MutableColaborador {
        private final Long id;
        private final String nombre;
        private final String cargo;
        private BigDecimal horas = BigDecimal.ZERO;
        private long tareas;
        private final List<RmReporteView.DesgloseProyecto> proyectos = new ArrayList<>();

        private MutableColaborador(Long id, String nombre, String cargo) {
            this.id = id;
            this.nombre = nombre;
            this.cargo = cargo;
        }
    }
}
