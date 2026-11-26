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

/**
 * Lógica del reporte "Mi desempeño" del colaborador: indicadores de un mes, desglose por
 * proyecto, cursos completados e historial de los últimos meses, con exportación a Excel y PDF.
 * <p>
 * Las <b>horas</b> de un mes son las horas estimadas de sus actividades {@code COMPLETADAS} con
 * fecha de entrega en ese mes (misma regla que el reporte de horas del RM). A diferencia del
 * dashboard, estas horas <b>no suman</b> las de los cursos: los cursos se listan aparte.
 */
@Service
public class ColDesempenoService {

    /** Cantidad de meses (contando el consultado) que se muestran en el historial. */
    public static final int MESES_HISTORIAL = 6;

    /**
     * Fila del desglose por proyecto.
     *
     * @param proyecto nombre del proyecto
     * @param completadas actividades completadas y entregadas en el mes
     * @param aTiempo de ellas, las entregadas a tiempo
     * @param tardias de ellas, las entregadas tarde
     * @param horas suma de horas estimadas de esas actividades
     */
    public record FilaProyecto(String proyecto, int completadas, int aTiempo, int tardias, BigDecimal horas) {
    }

    /**
     * Fila del historial mensual.
     *
     * @param mes etiqueta del mes (por ejemplo "Noviembre 2026")
     * @param horas horas de actividades completadas ese mes
     * @param completadas cantidad de actividades completadas ese mes
     * @param strikes strikes acumulados en ese mes
     */
    public record FilaMes(String mes, BigDecimal horas, int completadas, int strikes) {
    }

    /**
     * Curso completado en el mes consultado.
     *
     * @param nombre nombre del curso
     * @param horas duración del curso en horas
     * @param fecha fecha de completado, como texto {@code yyyy-MM-dd}
     */
    public record CursoCompletado(String nombre, BigDecimal horas, String fecha) {
    }

    /**
     * Resultado completo del reporte de un mes.
     *
     * @param mes mes consultado
     * @param etiquetaMes mes en texto legible
     * @param horas horas de actividades completadas y entregadas en el mes
     * @param completadas cantidad de actividades completadas en el mes
     * @param aTiempo de ellas, las entregadas a tiempo
     * @param tardias de ellas, las entregadas tarde
     * @param devoluciones veces que el PM devolvió actividades cuya fecha límite cae en el mes
     * @param vencidas actividades con fecha límite en el mes, ya vencidas y sin completar
     * @param strikes strikes del colaborador en el mes
     * @param porProyecto desglose por proyecto, de mayor a menor cantidad de horas
     * @param cursos cursos completados en el mes
     * @param historial últimos meses, sin incluir meses anteriores a la creación de la cuenta
     */
    public record Desempeno(YearMonth mes, String etiquetaMes, BigDecimal horas, int completadas, int aTiempo,
                            int tardias, int devoluciones, int vencidas, int strikes,
                            List<FilaProyecto> porProyecto, List<CursoCompletado> cursos, List<FilaMes> historial) {


        /**
         * Calcula el porcentaje de puntualidad del colaborador.
         * Para ello, divide la cantidad de entregas realizadas a tiempo
         * entre el total de entregas (a tiempo + tardías) y multiplica
         * el resultado por 100.
         *
         * Si no hay entregas, devuelve 0 para evitar una división entre cero.
         * Math.round() redondea el resultado al número entero más cercano.
         *
         * @return porcentaje de puntualidad entre 0 y 100.
         */
        public int puntualidad() {
            int entregas = aTiempo + tardias;
            return entregas == 0 ? 0 : Math.round(aTiempo * 100f / entregas);
        }
    }

    private final ActividadRepository actividadRepository;
    private final ColaboradorCursoRepository colaboradorCursoRepository;
    private final PenalizacionService penalizacionService;

    /**
     * Inyecta por constructor las dependencias del service.
     *
     * @param actividadRepository acceso a actividades
     * @param colaboradorCursoRepository cursos del colaborador
     * @param penalizacionService cuenta los strikes de cada mes
     */
    public ColDesempenoService(ActividadRepository actividadRepository,
                               ColaboradorCursoRepository colaboradorCursoRepository,
                               PenalizacionService penalizacionService) {
        this.actividadRepository = actividadRepository;
        this.colaboradorCursoRepository = colaboradorCursoRepository;
        this.penalizacionService = penalizacionService;
    }



    /**
     * Calcula el reporte de desempeño de un mes. Es una consulta de solo lectura.
     * <p>
     * Se usan dos conjuntos distintos de actividades:
     * <ul>
     *   <li><b>Completadas del mes</b>: estado {@code COMPLETADA} con fecha de entrega en el
     *       mes. De aquí salen las horas, las completadas, las entregas a tiempo y tardías, y el
     *       desglose por proyecto.</li>
     *   <li><b>Del mes</b>: con fecha límite en el mes. De aquí salen las devoluciones (suma de
     *       las veces devueltas) y las vencidas (no completadas con fecha límite ya pasada).</li>
     * </ul>
     * Los cursos son los {@code COMPLETADO} con fecha de completado en el mes. El historial
     * recorre los últimos {@value #MESES_HISTORIAL} meses terminando en el consultado, omitiendo
     * los anteriores a la creación de la cuenta del colaborador.
     *
     * @param colaborador colaborador consultado
     * @param mes mes del reporte
     * @return el reporte del mes
     */
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
    /**
     * Arma las 4 hojas que usan tanto el Excel como el PDF: "Resumen" (indicadores), "Por
     * proyecto", "Cursos" y "Últimos meses". Así ambos formatos muestran exactamente lo mismo.
     *
     * @param colaborador colaborador del reporte
     * @param d datos del reporte
     * @return las hojas con sus encabezados y filas
     */
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

    /**
     * Genera el reporte en formato Excel, con la fecha de generación en el encabezado.
     *
     * @param colaborador colaborador del reporte
     * @param d datos del reporte
     * @return el contenido del archivo {@code .xlsx}
     */
    public byte[] excel(Usuario colaborador, Desempeno d) {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("Fecha de generación", ReportePdf.ahora());
        return ReporteExcel.crear("Mi desempeño — " + d.etiquetaMes(), datos, hojas(colaborador, d));
    }

    /**
     * Genera el reporte en formato PDF, con el título "SkillBridge AI — Reporte del colaborador"
     * y la fecha de generación.
     *
     * @param colaborador colaborador del reporte
     * @param d datos del reporte
     * @return el contenido del archivo {@code .pdf}
     */
    public byte[] pdf(Usuario colaborador, Desempeno d) {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("Fecha de generación", ReportePdf.ahora());
        return ReportePdf.crear("Mi desempeño — " + d.etiquetaMes(),
                "SkillBridge AI — Reporte del colaborador", datos, hojas(colaborador, d));
    }

    /**
     * @param lista actividades a revisar
     * @param estado estado de entrega buscado ({@code A_TIEMPO} o {@code TARDIA})
     * @return cuántas actividades de la lista tienen ese estado de entrega
     */
    private int contar(List<Actividad> lista, EstadoEntrega estado) {
        return (int) lista.stream().filter(a -> a.getEstadoEntrega() == estado).count();
    }

    /**
     * @param lista actividades a sumar
     * @return suma de las horas estimadas; las actividades sin horas se ignoran
     */
    private BigDecimal horas(List<Actividad> lista) {
        return lista.stream().map(Actividad::getHorasEstimadas).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
