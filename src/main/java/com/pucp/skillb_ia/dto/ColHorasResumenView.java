package com.pucp.skillb_ia.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Resumen de horas del colaborador que se muestra en su dashboard.
 * <p>
 * Reúne en un solo objeto de solo lectura tres métricas distintas que no deben confundirse:
 * <ul>
 *   <li><b>Disponibilidad</b>: horas por semana que el colaborador declara tener libres.</li>
 *   <li><b>Horas comprometidas</b>: horas contratadas por semana menos las disponibles.</li>
 *   <li><b>Horas trabajadas del mes</b>: horas de actividades completadas y de cursos
 *       completados en el mes actual, comparadas contra la meta mensual.</li>
 * </ul>
 * Todos los campos son {@code final}: los valores derivados (comprometidas, faltantes y
 * porcentaje) se calculan una sola vez en el constructor.
 *
 * @see com.pucp.skillb_ia.service.col.ColaboradorActividadService#obtenerResumenHoras
 */
public class ColHorasResumenView {

    private final BigDecimal horasContratadasSemana;
    private final BigDecimal horasDisponibles;
    private final BigDecimal horasComprometidas;
    private final BigDecimal horasTrabajadasMes;
    private final BigDecimal metaMensual;
    private final BigDecimal horasFaltantesMes;
    private final int porcentajeMeta;

    /**
     * Construye el resumen y calcula los valores derivados.
     * <ul>
     *   <li>{@code horasComprometidas} = contratadas - disponibles (nunca menor a 0).</li>
     *   <li>{@code horasFaltantesMes} = meta - trabajadas (nunca menor a 0).</li>
     *   <li>{@code porcentajeMeta} = trabajadas * 100 / meta, redondeado y con tope de 100.</li>
     * </ul>
     *
     * @param horasContratadasSemana horas semanales contratadas; si es {@code null} se toma 0
     * @param horasDisponibles horas semanales libres declaradas; si es {@code null} se toma 0
     * @param horasTrabajadasMes horas acumuladas en el mes actual; si es {@code null} se toma 0
     * @param metaMensual meta de horas del mes (160); no debe ser {@code null}
     */
    public ColHorasResumenView(BigDecimal horasContratadasSemana, BigDecimal horasDisponibles,
                               BigDecimal horasTrabajadasMes, BigDecimal metaMensual) {
        this.horasContratadasSemana = horasContratadasSemana == null ? BigDecimal.ZERO : horasContratadasSemana;
        this.horasDisponibles = horasDisponibles == null ? BigDecimal.ZERO : horasDisponibles;

        BigDecimal comprometidas = this.horasContratadasSemana.subtract(this.horasDisponibles);
        this.horasComprometidas = comprometidas.signum() < 0 ? BigDecimal.ZERO : comprometidas;

        this.horasTrabajadasMes = horasTrabajadasMes == null ? BigDecimal.ZERO : horasTrabajadasMes;
        this.metaMensual = metaMensual;

        BigDecimal faltantes = this.metaMensual.subtract(this.horasTrabajadasMes);
        this.horasFaltantesMes = faltantes.signum() < 0 ? BigDecimal.ZERO : faltantes;

        int porcentaje = this.metaMensual.signum() == 0 ? 0 : this.horasTrabajadasMes.multiply(BigDecimal.valueOf(100)).divide(this.metaMensual, 0, RoundingMode.HALF_UP).intValue();
        this.porcentajeMeta = Math.min(porcentaje, 100);

    }

    public BigDecimal getHorasContratadasSemana() { return horasContratadasSemana; }
    public BigDecimal getHorasDisponibles() { return horasDisponibles; }
    public BigDecimal getHorasComprometidas() { return horasComprometidas; }
    public BigDecimal getHorasTrabajadasMes() { return horasTrabajadasMes; }
    public BigDecimal getMetaMensual() { return metaMensual; }
    public BigDecimal getHorasFaltantesMes() { return horasFaltantesMes; }
    public int getPorcentajeMeta() { return porcentajeMeta; }

    /**
     * Indica si el colaborador ya llegó a la meta mensual.
     *
     * @return {@code true} si no le faltan horas para la meta del mes
     */
    public boolean isMetaCumplida() { return horasFaltantesMes.signum() == 0; }


}