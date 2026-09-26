package com.pucp.skillb_ia.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ColHorasResumenView {

    private final BigDecimal horasContratadasSemana;
    private final BigDecimal horasDisponibles;
    private final BigDecimal horasComprometidas;
    private final BigDecimal horasTrabajadasMes;
    private final BigDecimal metaMensual;
    private final BigDecimal horasFaltantesMes;
    private final int porcentajeMeta;

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
    public boolean isMetaCumplida() { return horasFaltantesMes.signum() == 0; }
}