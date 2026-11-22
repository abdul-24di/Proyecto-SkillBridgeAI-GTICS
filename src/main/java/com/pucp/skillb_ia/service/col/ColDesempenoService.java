package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.ColaboradorCurso;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoColaboradorCurso;
import com.pucp.skillb_ia.model.enums.EstadoEntrega;
import com.pucp.skillb_ia.repository.ActividadRepository;
import com.pucp.skillb_ia.repository.ColaboradorCursoRepository;
import com.pucp.skillb_ia.service.PenalizacionService;
import com.pucp.skillb_ia.util.Periodo;
import com.pucp.skillb_ia.util.ReporteExcel;
import com.pucp.skillb_ia.util.ReportePdf;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Reporte "Mi desempeño" del colaborador. Las horas de un mes son las horas estimadas de sus actividades
// COMPLETADAS con fecha de entrega en ese mes (misma regla que el reporte de horas del RM).
@Service
public class ColDesempenoService {

    public static final int MESES_HISTORIAL = 6;

    public record FilaProyecto(String proyecto, int completadas, int aTiempo, int tardias, BigDecimal horas) {
    }

    public record FilaMes(String mes, BigDecimal horas, int completadas, int strikes) {
    }

    public record CursoCompletado(String nombre, BigDecimal horas, String fecha) {
    }

    public record Desempeno(YearMonth mes, String etiquetaMes, BigDecimal horas, int completadas, int aTiempo,
                            int tardias, int devoluciones, int vencidas, int strikes,
                            List<FilaProyecto> porProyecto, List<CursoCompletado> cursos, List<FilaMes> historial) {
        public int puntualidad() {
            int entregas = aTiempo + tardias;
            return entregas == 0 ? 0 : Math.round(aTiempo * 100f / entregas);
        }
    }

    private final ActividadRepository actividadRepository;
    private final ColaboradorCursoRepository colaboradorCursoRepository;
    private final PenalizacionService penalizacionService;

    public ColDesempenoService(ActividadRepository actividadRepository,
                               ColaboradorCursoRepository colaboradorCursoRepository,
                               PenalizacionService penalizacionService) {
        this.actividadRepository = actividadRepository;
        this.colaboradorCursoRepository = colaboradorCursoRepository;
        this.penalizacionService = penalizacionService;
    }

    @Transactional(readOnly = true)
    public Desempeno obtener(Usuario colaborador, YearMonth mes) {
        List<Actividad> todas = actividadRepository.findByColaboradorAndActivoTrue(colaborador);
        LocalDate hoy = LocalDate.now();

        // Entregadas en el mes (completadas con fecha de entrega en el mes).
        List<Actividad> completadasDelMes = todas.stream()
                .filter(a -> a.getEstado() == EstadoActividad.COMPLETADA && a.getFechaEntrega() != null
                        && Periodo.contiene(mes, a.getFechaEntrega().toLocalDate()))
                .toList();
        // Con fecha límite en el mes (para puntualidad, devoluciones y vencidas).
        List<Actividad> delMes = todas.stream().filter(a -> Periodo.contiene(mes, a.getFechaLimite())).toList();

        Map<String, List<Actividad>> porProyecto = new LinkedHashMap<>();
        for (Actividad a : completadasDelMes) {
            porProyecto.computeIfAbsent(a.getProyecto().getNombre(), k -> new ArrayList<>()).add(a);
        }
        List<FilaProyecto> filas = new ArrayList<>();
        porProyecto.forEach((nombre, lista) -> filas.add(new FilaProyecto(nombre, lista.size(),
                contar(lista, EstadoEntrega.A_TIEMPO), contar(lista, EstadoEntrega.TARDIA), horas(lista))));
        filas.sort(Comparator.comparing(FilaProyecto::horas).reversed());

        List<CursoCompletado> cursos = colaboradorCursoRepository.findByColaborador(colaborador).stream()
                .filter(c -> c.getEstado() == EstadoColaboradorCurso.COMPLETADO && c.getFechaCompletado() != null
                        && Periodo.contiene(mes, c.getFechaCompletado().toLocalDate()))
                .map(c -> new CursoCompletado(c.getCurso().getNombre(), c.getCurso().getHoras(),
                        c.getFechaCompletado().toLocalDate().toString()))
                .toList();

        List<FilaMes> historial = new ArrayList<>();
        for (YearMonth m : Periodo.ultimos(mes, MESES_HISTORIAL)) {
            if (m.isBefore(YearMonth.from(colaborador.getFechaCreacion()))) {
                continue;
            }
            List<Actividad> completadas = todas.stream()
                    .filter(a -> a.getEstado() == EstadoActividad.COMPLETADA && a.getFechaEntrega() != null
                            && Periodo.contiene(m, a.getFechaEntrega().toLocalDate()))
                    .toList();
            historial.add(new FilaMes(Periodo.etiqueta(m), horas(completadas), completadas.size(),
                    penalizacionService.contarStrikesDelMes(colaborador, m)));
        }

        int vencidas = (int) delMes.stream().filter(a -> a.getEstado() != EstadoActividad.COMPLETADA
                && a.getFechaLimite().isBefore(hoy)).count();
        return new Desempeno(mes, Periodo.etiqueta(mes), horas(completadasDelMes), completadasDelMes.size(),
                contar(completadasDelMes, EstadoEntrega.A_TIEMPO), contar(completadasDelMes, EstadoEntrega.TARDIA),
                delMes.stream().mapToInt(Actividad::getVecesDevuelta).sum(), vencidas,
                penalizacionService.contarStrikesDelMes(colaborador, mes), filas, cursos, historial);
    }
    private List<ReporteExcel.Hoja> hojas(Usuario colaborador, Desempeno d) {
        List<List<Object>> resumen = List.of(
                List.of("Colaborador", colaborador.getNombre() + " " + colaborador.getApellido()),
                List.of("Mes", d.etiquetaMes()),
                List.of("Horas completadas", d.horas()),
                List.of("Actividades completadas", d.completadas()),
                List.of("Entregadas a tiempo", d.aTiempo()),
                List.of("Entregadas tarde", d.tardias()),
                List.of("Puntualidad (%)", d.puntualidad()),
                List.of("Devoluciones", d.devoluciones()),
                List.of("Vencidas sin completar", d.vencidas()),
                List.of("Strikes del mes", d.strikes()),
                List.of("Cursos completados", d.cursos().size()));
        return List.of(
                new ReporteExcel.Hoja("Resumen", List.of("Indicador", "Valor"), resumen),
                new ReporteExcel.Hoja("Por proyecto", List.of("Proyecto", "Completadas", "A tiempo", "Tarde", "Horas"),
                        d.porProyecto().stream().map(f -> List.<Object>of(f.proyecto(), f.completadas(), f.aTiempo(),
                                f.tardias(), f.horas())).toList()),
                new ReporteExcel.Hoja("Cursos", List.of("Curso", "Horas", "Completado"),
                        d.cursos().stream().map(c -> List.<Object>of(c.nombre(), c.horas() == null ? "" : c.horas(),
                                c.fecha())).toList()),
                new ReporteExcel.Hoja("Últimos meses", List.of("Mes", "Horas", "Completadas", "Strikes"),
                        d.historial().stream().map(f -> List.<Object>of(f.mes(), f.horas(), f.completadas(),
                                f.strikes())).toList()));
    }

    public byte[] excel(Usuario colaborador, Desempeno d) {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("Fecha de generación", ReportePdf.ahora());
        return ReporteExcel.crear("Mi desempeño — " + d.etiquetaMes(), datos, hojas(colaborador, d));
    }

    public byte[] pdf(Usuario colaborador, Desempeno d) {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("Fecha de generación", ReportePdf.ahora());
        return ReportePdf.crear("Mi desempeño — " + d.etiquetaMes(),
                "SkillBridge AI — Reporte del colaborador", datos, hojas(colaborador, d));
    }

    private int contar(List<Actividad> lista, EstadoEntrega estado) {
        return (int) lista.stream().filter(a -> a.getEstadoEntrega() == estado).count();
    }

    private BigDecimal horas(List<Actividad> lista) {
        return lista.stream().map(Actividad::getHorasEstimadas).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
