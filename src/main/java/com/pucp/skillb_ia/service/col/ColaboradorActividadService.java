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


/**
 * Lógica de las actividades, horas y bono del colaborador, usada sobre todo por el dashboard.
 * <p>
 * Responde tres preguntas: ¿qué actividades tengo y cuáles están pendientes?, ¿cuántas horas
 * llevo este mes frente a la meta? y ¿cuánto descuento o bono me corresponde este mes?
 * Los strikes y descuentos los calcula {@link PenalizacionService}.
 */
@Service
public class ColaboradorActividadService {

    private final ActividadRepository actividadRepository;
    private final PenalizacionService penalizacionService;
    private final AsignacionRepository asignacionRepository;
    private final ConfiguracionSistemaRepository configuracionSistemaRepository;
    private final ColaboradorCursoRepository colaboradorCursoRepository;
    private final ColaboradorCursoService colaboradorCursoService;

    /** Meta mensual de horas de todo colaborador: 160 horas al mes. */
    public static final BigDecimal META_MENSUAL_HORAS = new BigDecimal("160");

    /**
     * Inyecta por constructor las dependencias del service.
     *
     * @param actividadRepository acceso a actividades
     * @param penalizacionService strikes y descuentos
     * @param asignacionRepository acceso a asignaciones
     * @param configuracionSistemaRepository parámetros globales (por ejemplo el tope de horas extra)
     * @param colaboradorCursoRepository inscripciones a cursos
     * @param colaboradorCursoService lógica de cursos (marca los vencidos sin evidencia)
     */
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

    /**
     * Lista las actividades activas del colaborador, de la fecha límite más próxima a la más lejana.
     * <p>
     * <b>Efecto secundario:</b> cada vez que se llama, las actividades vencidas que nunca se
     * entregaron reciben su strike en ese momento (no antes).
     *
     * @param colaborador colaborador consultado
     * @return sus actividades ordenadas por fecha límite
     */
    public List<Actividad> listarMisActividades(Usuario colaborador) {
        List<Actividad> encontradas = actividadRepository.findByColaboradorAndActivoTrue(colaborador);
        //Cada vez que el colaborador entra a ver sus actividades: si alguna ya venció y nunca la entregó, se le aplica el strike recién ahora.
        penalizacionService.revisarVencidasSinEntregar(encontradas);

        List<Actividad> ordenadas = new ArrayList<>(encontradas);
        ordenadas.sort(Comparator.comparing(Actividad::getFechaLimite));
        return ordenadas;
    }

    /**
     * Lista las actividades que todavía no están {@code COMPLETADAS}, para el contador y la tabla
     * del dashboard.
     * <p>
     * Solo cuenta las de proyectos donde el colaborador sigue con asignación {@code ACTIVA}: si
     * lo sacaron de un proyecto, sus actividades sin terminar dejan de ser su responsabilidad y
     * ya no aparecen.
     *
     * @param colaborador colaborador consultado
     * @return actividades pendientes
     */
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



    /**
     * Calcula el avance personal del colaborador en un proyecto: actividades {@code COMPLETADAS}
     * entre el total de sus actividades activas en él, redondeado. Si no tiene ninguna, es 0.
     *
     * @param proyecto proyecto evaluado
     * @param colaborador colaborador evaluado
     * @return porcentaje de 0 a 100
     */
    public int calcularAvance(Proyecto proyecto, Usuario colaborador) {
        List<Actividad> actividades = actividadRepository.findByProyectoAndColaboradorAndActivoTrue(proyecto, colaborador);
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


    /**
     * Arma la lista de proyectos con asignación {@code ACTIVA} junto con el avance del colaborador
     * en cada uno (para las barras de progreso del dashboard).
     *
     * @param colaborador colaborador consultado
     * @param misAsignaciones todas sus asignaciones; solo se usan las activas
     * @return un elemento por proyecto activo
     * @see #calcularAvance
     */
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

    /**
     * Calcula el resumen de horas del colaborador para el mes actual.
     * <p>
     * Las <b>horas trabajadas del mes</b> son la suma de:
     * <ul>
     *   <li>las horas estimadas de sus actividades {@code COMPLETADAS} cuya fecha de entrega cae
     *       en este mes, y</li>
     *   <li>las horas de los cursos {@code COMPLETADOS} (validados por el RM) cuya fecha de
     *       completado cae en este mes.</li>
     * </ul>
     * La meta es {@link #META_MENSUAL_HORAS}. Antes de calcular, marca como {@code NO_COMPLETADO}
     * los cursos en curso cuya fecha de fin ya llegó sin que se haya subido evidencia.
     *
     * @param colaborador colaborador consultado
     * @return el resumen de horas
     * @see ColHorasResumenView
     */
    public ColHorasResumenView obtenerResumenHoras(Usuario colaborador) {

        //Si algún curso ya venció su fecha fin sin evidencia aprobada, lo marcamos como no completado.
        colaboradorCursoService.marcarCursosSinEvidenciaVencidos(colaborador);

        List<Actividad> todas = actividadRepository.findByColaboradorAndActivoTrue(colaborador);

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


    /**
     * Lista las penalizaciones (strikes) del colaborador, de la más reciente a la más antigua.
     *
     * @param colaborador colaborador consultado
     * @return sus penalizaciones
     */
    public List<Penalizacion> listarMisPenalizaciones(Usuario colaborador) {
        return penalizacionService.listarPenalizaciones(colaborador);
    }

    /**
     * Obtiene el porcentaje de descuento del mes actual: 5% por cada bloque completo de 3
     * strikes del mes, sumando todos sus proyectos.
     *
     * @param colaborador colaborador consultado
     * @return porcentaje de descuento (0, 5, 10, ...)
     * @see PenalizacionService#calcularPorcentajeDescuentoMes
     */
    public int obtenerPorcentajeDescuentoMesActual(Usuario colaborador) {
        return penalizacionService.calcularPorcentajeDescuentoMes(colaborador, YearMonth.now());
    }

    /**
     * Obtiene el monto en soles que se descuenta este mes: sueldo base por el porcentaje de descuento.
     *
     * @param colaborador colaborador consultado
     * @return monto a descontar; 0 si no hay descuento o no tiene sueldo base
     * @see PenalizacionService#calcularMontoDescuentoMes
     */
    public java.math.BigDecimal obtenerMontoDescuentoMesActual(Usuario colaborador) {
        return penalizacionService.calcularMontoDescuentoMes(colaborador, java.time.YearMonth.now());
    }

    // ============================================================
    // BONO MENSUAL POR HORAS EXTRA
    // ============================================================
    /** Clave del parámetro global que el Administrador edita para fijar el tope de horas extra pagables. */
    private static final String CLAVE_TOPE_HORAS_EXTRA = "TOPE_HORAS_EXTRA_BONO";

    /** Tope de horas extra pagables si el parámetro no existe o no es numérico: 20 horas. */
    private static final BigDecimal TOPE_HORAS_EXTRA_POR_DEFECTO = new BigDecimal("20");

    /** Horas de un mes de referencia: se usa para obtener el valor de la hora (sueldo base / 160). */
    private static final BigDecimal HORAS_MES_REFERENCIA = new BigDecimal("160");

    /**
     * Lee el tope de horas extra pagables desde la configuración del sistema.
     *
     * @return el valor configurado por el Administrador; 20 si el parámetro no existe o su valor
     *         no es un número
     */
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

    /**
     * Calcula el resumen del bono por horas extra del mes actual.
     * <ol>
     *   <li>Toma las horas trabajadas del mes y la meta (160).</li>
     *   <li>Lee el tope de horas extra configurado.</li>
     *   <li>Determina si hubo descuento este mes (al menos un bloque de 3 strikes).</li>
     *   <li>Calcula el valor de la hora: sueldo base / 160 (0 si no tiene sueldo base).</li>
     * </ol>
     * El monto final lo calcula {@link ColBonoMensualView}; si hubo descuento, el bono es 0.
     *
     * @param colaborador colaborador consultado
     * @return el resumen del bono
     */
    public ColBonoMensualView obtenerResumenBonoMensual(Usuario colaborador) {
        ColHorasResumenView resumenHoras = obtenerResumenHoras(colaborador);
        BigDecimal topeHorasExtra = obtenerTopeHorasExtra();

        boolean tuvoDescuentoEsteMes = penalizacionService.calcularPorcentajeDescuentoMes(colaborador, YearMonth.now()) > 0;

        BigDecimal valorHora = colaborador.getSueldoBase() == null ? BigDecimal.ZERO : colaborador.getSueldoBase().divide(HORAS_MES_REFERENCIA, 4, RoundingMode.HALF_UP);

        return new ColBonoMensualView(resumenHoras.getHorasTrabajadasMes(), resumenHoras.getMetaMensual(), topeHorasExtra, tuvoDescuentoEsteMes, valorHora);
    }






}




