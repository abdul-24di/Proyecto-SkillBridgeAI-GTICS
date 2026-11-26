package com.pucp.skillb_ia.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;


class ColHorasResumenViewTests {

    private static final BigDecimal META = new BigDecimal("160");

    private ColHorasResumenView vista(String contratadas, String disponibles, String trabajadas) {
        return new ColHorasResumenView(new BigDecimal(contratadas), new BigDecimal(disponibles),
                new BigDecimal(trabajadas), META);
    }

    @Test
    void lasHorasComprometidasSonContratadasMenosDisponibles() {
        ColHorasResumenView v = vista("40", "15", "0");
        assertThat(v.getHorasComprometidas()).isEqualByComparingTo("25");
    }

    @Test
    void lasHorasComprometidasNuncaSonNegativas() {
        ColHorasResumenView v = vista("20", "30", "0");
        assertThat(v.getHorasComprometidas()).isEqualByComparingTo("0");
    }

    @Test
    void conValoresNulosTodoSeTomaComoCero() {
        ColHorasResumenView v = new ColHorasResumenView(null, null, null, META);
        assertThat(v.getHorasContratadasSemana()).isEqualByComparingTo("0");
        assertThat(v.getHorasDisponibles()).isEqualByComparingTo("0");
        assertThat(v.getHorasTrabajadasMes()).isEqualByComparingTo("0");
        assertThat(v.getHorasFaltantesMes()).isEqualByComparingTo("160");
    }

    @Test
    void lasHorasFaltantesSonLaMetaMenosLasTrabajadas() {
        ColHorasResumenView v = vista("40", "10", "100");
        assertThat(v.getHorasFaltantesMes()).isEqualByComparingTo("60");
        assertThat(v.isMetaCumplida()).isFalse();
    }

    @Test
    void elPorcentajeSeCalculaSobreLaMeta() {
        ColHorasResumenView v = vista("40", "10", "80");
        assertThat(v.getPorcentajeMeta()).isEqualTo(50);
    }

    @Test
    void elPorcentajeTieneTopeDe100AunqueSeSupereLaMeta() {
        ColHorasResumenView v = vista("40", "10", "190");
        assertThat(v.getPorcentajeMeta()).isEqualTo(100);
        assertThat(v.getHorasFaltantesMes()).isEqualByComparingTo("0");
        assertThat(v.isMetaCumplida()).isTrue();
    }

    @Test
    void conMetaCeroElPorcentajeEsCeroYNoHayDivisionEntreCero() {
        ColHorasResumenView v = new ColHorasResumenView(BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO);
        assertThat(v.getPorcentajeMeta()).isEqualTo(0);
    }
}