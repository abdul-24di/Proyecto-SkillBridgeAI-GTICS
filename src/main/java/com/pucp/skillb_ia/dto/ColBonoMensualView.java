package com.pucp.skillb_ia.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Resumen del bono por horas extra del colaborador en el mes actual.
 * <p>
 * Reglas que aplica el constructor:
 * <ol>
 *   <li>{@code horasExtra} = horas trabajadas - meta mensual (mínimo 0).</li>
 *   <li>{@code horasExtraBonificables} = la menor entre las horas extra y el tope
 *       configurado por el Administrador ({@code TOPE_HORAS_EXTRA_BONO}).</li>
 *   <li>{@code montoBono} = bonificables * valor de la hora, <b>salvo</b> que el colaborador
 *       haya tenido un descuento por penalización este mes: en ese caso el bono es 0.</li>
 * </ol>
 *
 * @see com.pucp.skillb_ia.service.col.ColaboradorActividadService#obtenerResumenBonoMensual
 */
public class ColBonoMensualView {

    private final BigDecimal horasTrabajadasMes;
    private final BigDecimal metaMensual;
    private final BigDecimal horasExtra;
    private final BigDecimal horasExtraBonificables;
    private final BigDecimal topeHorasExtra;
    private final boolean tuvoDescuentoEsteMes;
    private final BigDecimal montoBono;

    /**
     * Construye el resumen del bono y calcula horas extra, horas bonificables y monto.
     *
     * @param horasTrabajadasMes horas acumuladas en el mes actual
     * @param metaMensual meta de horas del mes (160)
     * @param topeHorasExtra máximo de horas extra que se pagan por mes
     * @param tuvoDescuentoEsteMes {@code true} si tiene un descuento por penalización este mes
     *                             (anula el bono)
     * @param valorHora pago por hora: sueldo base / horas de referencia del mes
     */
    public ColBonoMensualView(BigDecimal horasTrabajadasMes, BigDecimal metaMensual, BigDecimal topeHorasExtra,
                              boolean tuvoDescuentoEsteMes, BigDecimal valorHora) {
        this.horasTrabajadasMes = horasTrabajadasMes;
        this.metaMensual = metaMensual;
        this.topeHorasExtra = topeHorasExtra;
        this.tuvoDescuentoEsteMes = tuvoDescuentoEsteMes;

        BigDecimal extra = horasTrabajadasMes.subtract(metaMensual);
        this.horasExtra = extra.signum() < 0 ? BigDecimal.ZERO : extra;
        this.horasExtraBonificables = this.horasExtra.min(topeHorasExtra);

        this.montoBono = tuvoDescuentoEsteMes ? BigDecimal.ZERO : this.horasExtraBonificables.multiply(valorHora);
    }

    public BigDecimal getHorasTrabajadasMes() { return horasTrabajadasMes; }
    public BigDecimal getMetaMensual() { return metaMensual; }
    public BigDecimal getHorasExtra() { return horasExtra; }
    public BigDecimal getHorasExtraBonificables() { return horasExtraBonificables; }
    public BigDecimal getTopeHorasExtra() { return topeHorasExtra; }
    public boolean isTuvoDescuentoEsteMes() { return tuvoDescuentoEsteMes; }
    public BigDecimal getMontoBono() { return montoBono; }

    /**
     * Calcula qué tanto del tope de horas extra ya se alcanzó (para la barra de progreso).
     *
     * @return porcentaje de 0 a 100; devuelve 0 si el tope es 0
     */
    public int getPorcentajeProgreso() {

        if (topeHorasExtra.signum() == 0) return 0;

        int pct = horasExtraBonificables.multiply(BigDecimal.valueOf(100)).divide(topeHorasExtra, 0, RoundingMode.HALF_UP).intValue();


        return Math.min(pct, 100);
    }
}