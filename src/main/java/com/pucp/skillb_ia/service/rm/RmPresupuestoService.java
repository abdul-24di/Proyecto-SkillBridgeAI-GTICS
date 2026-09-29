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

// Especificación funcional "presupuesto y costos de asignaciones del RM" que
// trajo el equipo. Dueño único de todo el cálculo de costos/presupuesto —
// nadie más debería reimplementar estas fórmulas.
//
// Decisión de diseño: "comprometido" y "reservado" NO se guardan como
// columnas — se recalculan siempre sumando las asignaciones ACTIVA/PENDIENTE
// del proyecto. Así nunca pueden desincronizarse ni descontarse dos veces
// (la sección 7.3 de la especificación pide exactamente evitar eso).
@Service
public class RmPresupuestoService {

    private static final BigDecimal HORAS_MENSUALES = BigDecimal.valueOf(160);
    private static final BigDecimal DIAS_POR_SEMANA = BigDecimal.valueOf(7);
    private static final int ESCALA = 2;

    private final AsignacionRepository asignacionRepository;

    public RmPresupuestoService(AsignacionRepository asignacionRepository) {
        this.asignacionRepository = asignacionRepository;
    }

    public record CostoAsignacion(BigDecimal valorHora, BigDecimal duracionSemanas, BigDecimal horasTotales,
                                   BigDecimal costoSemanal, BigDecimal costoTotal, boolean calculable) {
    }

    public record ResumenPresupuesto(BigDecimal total, BigDecimal comprometido, BigDecimal reservado,
                                      BigDecimal disponible, int porcentajeConsumido) {
    }

    public record ImpactoPresupuesto(CostoAsignacion costo, ResumenPresupuesto resumen,
                                      BigDecimal disponibleDespues, boolean suficiente, BigDecimal deficit) {
    }

    // 4.1-4.4 de la especificación: valor/hora = sueldo/160; duración = fechas
    // del PROYECTO (no de la asignación, que no tiene fechas propias); costo
    // semanal/total en base a esa duración.
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

    // Sección 6: presupuesto total / comprometido (activas) / reservado
    // (pendientes ya aprobadas por el RM, esperando al PM) / disponible.
    @Transactional(readOnly = true)
    public ResumenPresupuesto calcularResumen(Proyecto proyecto) {
        return calcularResumen(proyecto, proyecto);
    }

    // TASK-021: el mismo resumen si el proyecto tuviera otras fechas, sin modificar la entidad
    // (se valida antes de guardar un cambio de fechas).
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

    // Sección 8: bloque "Impacto en el presupuesto" (disponible antes - costo = disponible después).
    @Transactional(readOnly = true)
    public ImpactoPresupuesto calcularImpacto(Proyecto proyecto, Usuario colaborador, BigDecimal horasSemanales) {
        CostoAsignacion costo = calcularCosto(proyecto, colaborador, horasSemanales);
        ResumenPresupuesto resumen = calcularResumen(proyecto);
        BigDecimal disponibleDespues = resumen.disponible().subtract(costo.costoTotal());
        boolean suficiente = costo.calculable() && disponibleDespues.signum() >= 0;
        BigDecimal deficit = suficiente ? BigDecimal.ZERO : disponibleDespues.abs();
        return new ImpactoPresupuesto(costo, resumen, disponibleDespues.max(BigDecimal.ZERO), suficiente, deficit);
    }

    // Sección 13: validación obligatoria — se llama antes de proponer/aprobar
    // cualquier asignación. Nunca se permite pasar por excepción (13, último párrafo).
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
