package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Cálculo de costos de asignación y presupuesto de proyectos, según la especificación
 * "presupuesto y costos de asignaciones del RM". Es el único dueño de estas fórmulas.
 *
 * <p>Decisión de diseño: "comprometido" y "reservado" no se guardan como columnas; se recalculan
 * siempre sumando las asignaciones ACTIVA y PENDIENTE del proyecto. Así no pueden desincronizarse
 * ni descontarse dos veces (sección 7.3 de la especificación).</p>
 */
@Service
public class RmPresupuestoService {

    private static final BigDecimal HORAS_MENSUALES = BigDecimal.valueOf(160);
    private static final BigDecimal DIAS_POR_SEMANA = BigDecimal.valueOf(7);
    private static final int ESCALA = 2;

    private final AsignacionRepository asignacionRepository;

    /** Crea el componente con sus dependencias, que Spring inyecta por el constructor. */
    public RmPresupuestoService(AsignacionRepository asignacionRepository) {
        this.asignacionRepository = asignacionRepository;
    }

    /**
     * Costo estimado de una asignación.
     *
     * @param valorHora sueldo base / 160
     * @param duracionSemanas días entre las fechas del proyecto / 7
     * @param horasTotales horas semanales × duración
     * @param costoSemanal valor hora × horas semanales
     * @param costoTotal valor hora × horas totales
     * @param calculable {@code false} si faltan sueldo, horas o fechas (todos los montos quedan en cero)
     */
    public record CostoAsignacion(BigDecimal valorHora, BigDecimal duracionSemanas, BigDecimal horasTotales,
                                   BigDecimal costoSemanal, BigDecimal costoTotal, boolean calculable) {
    }

    /**
     * Estado del presupuesto de un proyecto.
     *
     * @param total presupuesto aprobado (cero si no tiene)
     * @param comprometido suma del costo de las asignaciones ACTIVA
     * @param reservado suma del costo de las asignaciones PENDIENTE ya aprobadas por el RM
     * @param disponible total − comprometido − reservado, nunca negativo
     * @param porcentajeConsumido (comprometido + reservado) / total, de 0 a 100
     */
    public record ResumenPresupuesto(BigDecimal total, BigDecimal comprometido, BigDecimal reservado,
                                      BigDecimal disponible, int porcentajeConsumido) {
    }

    /**
     * Efecto de una asignación nueva sobre el presupuesto.
     *
     * @param costo costo de la asignación
     * @param resumen presupuesto antes de la asignación
     * @param disponibleDespues disponible − costo total, nunca negativo
     * @param suficiente {@code true} si el costo es calculable y cabe en el disponible
     * @param deficit monto que falta cuando no es suficiente; cero en otro caso
     */
    public record ImpactoPresupuesto(CostoAsignacion costo, ResumenPresupuesto resumen,
                                      BigDecimal disponibleDespues, boolean suficiente, BigDecimal deficit) {
    }

    /**
     * Calcula el costo de asignar un colaborador (secciones 4.1 a 4.4): valor hora = sueldo / 160 y
     * duración según las fechas del proyecto (la asignación no tiene fechas propias).
     *
     * @param proyecto proyecto con fecha de inicio y fin estimada
     * @param colaborador colaborador con sueldo base
     * @param horasSemanales horas por semana de la asignación
     * @return el costo; con {@code calculable = false} y montos en cero si falta algún dato o no es positivo
     */
    public CostoAsignacion calcularCosto(Proyecto proyecto, Usuario colaborador, BigDecimal horasSemanales) {
        if (colaborador == null || colaborador.getSueldoBase() == null || colaborador.getSueldoBase().signum() <= 0
                || horasSemanales == null || horasSemanales.signum() <= 0
                || proyecto == null || proyecto.getFechaInicio() == null || proyecto.getFechaFinEstimada() == null) {
            return new CostoAsignacion(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false);
        }

        BigDecimal valorHora = colaborador.getSueldoBase().divide(HORAS_MENSUALES, ESCALA, RoundingMode.HALF_UP);

        long dias = ChronoUnit.DAYS.between(proyecto.getFechaInicio(), proyecto.getFechaFinEstimada());
        BigDecimal duracionSemanas = BigDecimal.valueOf(Math.max(dias, 0))
                .divide(DIAS_POR_SEMANA, ESCALA, RoundingMode.HALF_UP);

        BigDecimal costoSemanal = valorHora.multiply(horasSemanales).setScale(ESCALA, RoundingMode.HALF_UP);
        BigDecimal horasTotales = horasSemanales.multiply(duracionSemanas).setScale(ESCALA, RoundingMode.HALF_UP);
        BigDecimal costoTotal = valorHora.multiply(horasTotales).setScale(ESCALA, RoundingMode.HALF_UP);

        return new CostoAsignacion(valorHora, duracionSemanas, horasTotales, costoSemanal, costoTotal, true);
    }

    /**
     * Calcula el resumen del presupuesto (sección 6): total, comprometido (asignaciones activas),
     * reservado (pendientes ya aprobadas por el RM, a la espera del PM) y disponible.
     *
     * @param proyecto proyecto a resumir
     * @return el resumen del presupuesto
     */
    @Transactional(readOnly = true)
    public ResumenPresupuesto calcularResumen(Proyecto proyecto) {
        return calcularResumen(proyecto, proyecto);
    }

    /**
     * Calcula el mismo resumen como si el proyecto tuviera otras fechas, sin modificar la entidad.
     * Se usa para validar un cambio de fechas antes de guardarlo (TASK-021).
     *
     * @param proyecto proyecto del que salen el presupuesto y las asignaciones
     * @param fechaInicio fecha de inicio a simular
     * @param fechaFin fecha de fin a simular
     * @return el resumen con los costos recalculados para esas fechas
     */
    @Transactional(readOnly = true)
    public ResumenPresupuesto calcularResumenConFechas(Proyecto proyecto, LocalDate fechaInicio, LocalDate fechaFin) {
        Proyecto conFechas = new Proyecto();
        conFechas.setFechaInicio(fechaInicio);
        conFechas.setFechaFinEstimada(fechaFin);
        return calcularResumen(proyecto, conFechas);
    }

    // "fechas" solo aporta la duración para calcularCosto; asignaciones y total salen de "proyecto".
    private ResumenPresupuesto calcularResumen(Proyecto proyecto, Proyecto fechas) {
        BigDecimal total = proyecto.getPresupuesto() != null ? proyecto.getPresupuesto() : BigDecimal.ZERO;

        BigDecimal comprometido = asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA).stream()
                .map(a -> calcularCosto(fechas, a.getColaborador(), a.getHorasSemanales()).costoTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal reservado = asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.PENDIENTE).stream()
                .filter(Asignacion::isAprobadoPorRm)
                .map(a -> calcularCosto(fechas, a.getColaborador(), a.getHorasSemanales()).costoTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal disponible = total.subtract(comprometido).subtract(reservado);
        if (disponible.signum() < 0) disponible = BigDecimal.ZERO;

        int porcentaje = 0;
        if (total.signum() > 0) {
            porcentaje = comprometido.add(reservado)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(total, 0, RoundingMode.HALF_UP)
                    .intValue();
            porcentaje = Math.min(porcentaje, 100);
        }

        return new ResumenPresupuesto(total, comprometido, reservado, disponible, porcentaje);
    }

    /**
     * Calcula el bloque "Impacto en el presupuesto" (sección 8): disponible antes − costo = disponible después.
     *
     * @param proyecto proyecto destino
     * @param colaborador colaborador a asignar
     * @param horasSemanales horas por semana propuestas
     * @return el impacto de la asignación
     */
    @Transactional(readOnly = true)
    public ImpactoPresupuesto calcularImpacto(Proyecto proyecto, Usuario colaborador, BigDecimal horasSemanales) {
        return calcularImpacto(proyecto, colaborador, horasSemanales, calcularResumen(proyecto));
    }

    /**
     * Calcula el mismo impacto con un resumen ya calculado, para no recalcularlo por cada candidato (TASK-069).
     *
     * @param proyecto proyecto destino
     * @param colaborador colaborador a asignar
     * @param horasSemanales horas por semana propuestas
     * @param resumen resumen actual del presupuesto del proyecto
     * @return el impacto de la asignación
     */
    public ImpactoPresupuesto calcularImpacto(Proyecto proyecto, Usuario colaborador, BigDecimal horasSemanales,
                                              ResumenPresupuesto resumen) {
        CostoAsignacion costo = calcularCosto(proyecto, colaborador, horasSemanales);
        BigDecimal disponibleDespues = resumen.disponible().subtract(costo.costoTotal());
        boolean suficiente = costo.calculable() && disponibleDespues.signum() >= 0;
        BigDecimal deficit = suficiente ? BigDecimal.ZERO : disponibleDespues.abs();
        return new ImpactoPresupuesto(costo, resumen, disponibleDespues.max(BigDecimal.ZERO), suficiente, deficit);
    }

    /**
     * Validación obligatoria antes de proponer o aprobar cualquier asignación (sección 13).
     * No admite excepciones.
     *
     * @param proyecto proyecto destino
     * @param colaborador colaborador a asignar
     * @param horasSemanales horas por semana propuestas
     * @throws IllegalArgumentException si el proyecto no tiene presupuesto aprobado o fechas, el colaborador
     *         no tiene sueldo base válido, las horas no son mayores a cero o el disponible no alcanza
     *         (el mensaje indica el déficit en soles)
     */
    @Transactional(readOnly = true)
    public void validarPresupuestoSuficiente(Proyecto proyecto, Usuario colaborador, BigDecimal horasSemanales) {
        if (proyecto.getPresupuesto() == null || proyecto.getPresupuesto().signum() <= 0) {
            throw new IllegalArgumentException("El proyecto no tiene presupuesto aprobado todavía.");
        }
        if (colaborador.getSueldoBase() == null || colaborador.getSueldoBase().signum() <= 0) {
            throw new IllegalArgumentException("El colaborador no tiene un sueldo base válido registrado.");
        }
        if (proyecto.getFechaInicio() == null || proyecto.getFechaFinEstimada() == null) {
            throw new IllegalArgumentException("El proyecto no tiene fechas de inicio y fin definidas.");
        }
        if (horasSemanales == null || horasSemanales.signum() <= 0) {
            throw new IllegalArgumentException("Las horas semanales deben ser mayores a cero.");
        }

        ImpactoPresupuesto impacto = calcularImpacto(proyecto, colaborador, horasSemanales);
        if (!impacto.suficiente()) {
            throw new IllegalArgumentException("Presupuesto insuficiente: faltan S/ "
                    + impacto.deficit().toPlainString() + " para esta asignación.");
        }
    }
}
