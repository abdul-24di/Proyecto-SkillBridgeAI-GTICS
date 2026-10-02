package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.dto.ColBonoMensualView;
import com.pucp.skillb_ia.dto.ColHorasResumenView;
import com.pucp.skillb_ia.dto.ColProyectoAvanceView;
import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoColaboradorCurso;
import com.pucp.skillb_ia.repository.ActividadRepository;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ColaboradorCursoRepository;
import com.pucp.skillb_ia.repository.ConfiguracionSistemaRepository;
import com.pucp.skillb_ia.service.PenalizacionService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


@Service
public class ColaboradorActividadService {

    private final ActividadRepository actividadRepository;
    private final PenalizacionService penalizacionService;
    private final AsignacionRepository asignacionRepository;
    private final ConfiguracionSistemaRepository configuracionSistemaRepository;
    private final ColaboradorCursoRepository colaboradorCursoRepository;
    private final ColaboradorCursoService colaboradorCursoService;

    //Establecemos que la meta mensual de horas sea de 160 horas al mes
    private static final BigDecimal META_MENSUAL_HORAS = new BigDecimal("160");

    public ColaboradorActividadService(ActividadRepository actividadRepository,
                                       PenalizacionService penalizacionService,
                                       AsignacionRepository asignacionRepository,
                                       ConfiguracionSistemaRepository configuracionSistemaRepository,
                                       ColaboradorCursoRepository colaboradorCursoRepository,
                                       ColaboradorCursoService colaboradorCursoService) {

        this.actividadRepository = actividadRepository;
        this.penalizacionService = penalizacionService;
        this.asignacionRepository = asignacionRepository;
        this.configuracionSistemaRepository = configuracionSistemaRepository;
        this.colaboradorCursoRepository = colaboradorCursoRepository;
        this.colaboradorCursoService = colaboradorCursoService;
    }

    //Listamos todas las actividades del colaborador, de la fecha límite más próxima a la más lejana
    public List<Actividad> listarMisActividades(Usuario colaborador) {
        List<Actividad> encontradas = actividadRepository.findByColaborador(colaborador);

        //Cada vez que el colaborador entra a ver sus actividades: si alguna ya venció y nunca la entregó, se le aplica el strike recién ahora.
        penalizacionService.revisarVencidasSinEntregar(encontradas);

        List<Actividad> ordenadas = new ArrayList<>(encontradas);
        ordenadas.sort(Comparator.comparing(Actividad::getFechaLimite));
        return ordenadas;
    }

    //Listamos las actividades que todavía no están completas para el contador y la tabla del dashboard.
    //Solo se cuentan las de proyectos donde el colaborador sigue activo
    //En caso de que el colaborador haya sido expulsado de uno, sus actividades
    //sin terminar ya no son su responsabilidad por lo que no aparecen en el dashboard.
    public List<Actividad> listarActividadesPendientes(Usuario colaborador) {
        List<Actividad> todas = listarMisActividades(colaborador);
        List<Actividad> pendientes = new ArrayList<>();

        for (Actividad actividad : todas) {
            boolean noEstaCompletada = actividad.getEstado() != EstadoActividad.COMPLETADA;
            boolean sigoActivoEnEseProyecto = asignacionRepository.existsByProyectoAndColaboradorAndEstado(
                    actividad.getProyecto(), colaborador, EstadoAsignacion.ACTIVA);

            if (noEstaCompletada && sigoActivoEnEseProyecto) {
                pendientes.add(actividad);
            }
        }
        return pendientes;
    }



    //Calculamos el porcentaje de avance del colaborador en un proyecto
    //Sus actividades completadas sobre el total de las que tiene en ese proyecto. Si no tiene ninguna, es 0.
    public int calcularAvance(Proyecto proyecto, Usuario colaborador) {
        List<Actividad> actividades = actividadRepository.findByProyectoAndColaborador(proyecto, colaborador);
        if (actividades.isEmpty()) {
            return 0;
        }

        int completadas = 0;
        for (Actividad actividad : actividades) {
            if (actividad.getEstado() == EstadoActividad.COMPLETADA) {
                completadas++;
            }
        }
        return Math.round((completadas * 100f) / actividades.size());
    }


    public List<ColProyectoAvanceView> listarProyectosActivosConAvance(Usuario colaborador, List<Asignacion> misAsignaciones) {
        List<ColProyectoAvanceView> resultado = new ArrayList<>();

        for (Asignacion asignacion : misAsignaciones) {
            if (asignacion.getEstado() == EstadoAsignacion.ACTIVA) {
                int avance = calcularAvance(asignacion.getProyecto(), colaborador);
                resultado.add(new ColProyectoAvanceView(asignacion.getProyecto(), avance));
            }
        }
        return resultado;
    }

    // ============================================================
    // RESUMEN DE HORAS PARA EL DASHBOARD (semanales y meta mensual)
    // ============================================================

    public ColHorasResumenView obtenerResumenHoras(Usuario colaborador) {

        //Si algún curso ya venció su fecha fin sin evidencia aprobada, lo marcamos como no completado.
        colaboradorCursoService.marcarCursosSinEvidenciaVencidos(colaborador);

        List<Actividad> todas = actividadRepository.findByColaborador(colaborador);

        YearMonth mesActual = YearMonth.now();

        BigDecimal horasTrabajadasMes = BigDecimal.ZERO;

        for (Actividad actividad : todas) {

            boolean estaCompletada = actividad.getEstado() == EstadoActividad.COMPLETADA;

            boolean esDeEsteMes = actividad.getFechaEntrega() != null && YearMonth.from(actividad.getFechaEntrega()).equals(mesActual);

            if (estaCompletada && esDeEsteMes && actividad.getHorasEstimadas() != null) {
                horasTrabajadasMes = horasTrabajadasMes.add(actividad.getHorasEstimadas());
            }
        }

        //Para la meta mensual, también contamos las horas de capacitación en los cursos que ya fueron validados por el RM.

        List<ColaboradorCurso> misCursos = colaboradorCursoRepository.findByColaborador(colaborador);

        for (ColaboradorCurso registro : misCursos) {

            boolean estaCompletado = registro.getEstado() == EstadoColaboradorCurso.COMPLETADO;

            boolean esDeEsteMes = registro.getFechaCompletado() != null && YearMonth.from(registro.getFechaCompletado()).equals(mesActual);

            if (estaCompletado && esDeEsteMes && registro.getCurso().getHoras() != null) {
                horasTrabajadasMes = horasTrabajadasMes.add(registro.getCurso().getHoras());
            }
        }

        return new ColHorasResumenView(colaborador.getHorasContratadasSemana(), colaborador.getHorasDisponibles(), horasTrabajadasMes, META_MENSUAL_HORAS);
    }


    //Listamos las penalizaciones del colaborador
    public List<Penalizacion> listarMisPenalizaciones(Usuario colaborador) {
        return penalizacionService.listarPenalizaciones(colaborador);
    }

    //Obtenemos el porcentaje y monto real que se le descontara al colaborador, sumando todos sus strikes por mes
    public int obtenerPorcentajeDescuentoMesActual(Usuario colaborador) {
        return penalizacionService.calcularPorcentajeDescuentoMes(colaborador, YearMonth.now());
    }

    public java.math.BigDecimal obtenerMontoDescuentoMesActual(Usuario colaborador) {
        return penalizacionService.calcularMontoDescuentoMes(colaborador, java.time.YearMonth.now());
    }

    // ============================================================
    // BONO MENSUAL POR HORAS EXTRA
    // ============================================================
    private static final String CLAVE_TOPE_HORAS_EXTRA = "TOPE_HORAS_EXTRA_BONO";
    private static final BigDecimal TOPE_HORAS_EXTRA_POR_DEFECTO = new BigDecimal("20");
    private static final BigDecimal HORAS_MES_REFERENCIA = new BigDecimal("160");

    private BigDecimal obtenerTopeHorasExtra() {

        return configuracionSistemaRepository.findByClave(CLAVE_TOPE_HORAS_EXTRA)

                //En caso de que exista la configuración, convertimos su valor de String a BigDecimal.
                .map(configuracion -> {
                    try {
                        return new BigDecimal(configuracion.getValor());

                        //Si el valor no es numérico, usamos el valor por defecto.
                    } catch (NumberFormatException e) {
                        return TOPE_HORAS_EXTRA_POR_DEFECTO;
                    }


                    //Si no existe la configuración, usamos el valor por defecto.
                }).orElse(TOPE_HORAS_EXTRA_POR_DEFECTO);


    }

    public ColBonoMensualView obtenerResumenBonoMensual(Usuario colaborador) {
        ColHorasResumenView resumenHoras = obtenerResumenHoras(colaborador);
        BigDecimal topeHorasExtra = obtenerTopeHorasExtra();

        boolean tuvoDescuentoEsteMes = penalizacionService.calcularPorcentajeDescuentoMes(colaborador, YearMonth.now()) > 0;

        BigDecimal valorHora = colaborador.getSueldoBase() == null ? BigDecimal.ZERO : colaborador.getSueldoBase().divide(HORAS_MES_REFERENCIA, 4, RoundingMode.HALF_UP);

        return new ColBonoMensualView(resumenHoras.getHorasTrabajadasMes(), resumenHoras.getMetaMensual(), topeHorasExtra, tuvoDescuentoEsteMes, valorHora);
    }






}




