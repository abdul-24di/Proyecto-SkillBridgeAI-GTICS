package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.dto.PmReporteView;
import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.repository.ActividadRepository;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PmReporteService {

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

    public PmReporteService(ProyectoRepository proyectoRepository,
                            ActividadRepository actividadRepository,
                            AsignacionRepository asignacionRepository,
                            com.pucp.skillb_ia.service.PenalizacionService penalizacionService) {
        this.penalizacionService = penalizacionService;
        this.proyectoRepository = proyectoRepository;
        this.actividadRepository = actividadRepository;
        this.asignacionRepository = asignacionRepository;
    }

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
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Asignacion> activas = asignacionRepository.findByProyectoAndEstado(
                proyecto, EstadoAsignacion.ACTIVA);

        // Métricas por colaborador
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
     *
     * @throws IllegalArgumentException si el proyecto no existe
     * @throws SecurityException        si el proyecto no es del PM
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
        return com.pucp.skillb_ia.util.ReporteExcel.crear(List.of(
                new com.pucp.skillb_ia.util.ReporteExcel.Hoja("Resumen", List.of("Indicador", "Valor"), resumen),
                new com.pucp.skillb_ia.util.ReporteExcel.Hoja("Colaboradores", List.of("Colaborador", "Asignadas",
                        "Completadas", "A tiempo", "Tarde", "Vencidas", "Devoluciones", "Horas completadas",
                        "Strikes"), colaboradores)));
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

    // Vencida: su fecha límite ya pasó y todavía no está completada.
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
