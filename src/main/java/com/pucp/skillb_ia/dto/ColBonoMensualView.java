package com.pucp.skillb_ia.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ColBonoMensualView {

    private final BigDecimal horasTrabajadasMes;
    private final BigDecimal metaMensual;
    private final BigDecimal horasExtra;
    private final BigDecimal horasExtraBonificables;
    private final BigDecimal topeHorasExtra;
    private final boolean tuvoDescuentoEsteMes;
    private final BigDecimal montoBono;

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

    public int getPorcentajeProgreso() {

        if (topeHorasExtra.signum() == 0) return 0;

        int pct = horasExtraBonificables.multiply(BigDecimal.valueOf(100)).divide(topeHorasExtra, 0, RoundingMode.HALF_UP).intValue();


        return Math.min(pct, 100);
    }
}