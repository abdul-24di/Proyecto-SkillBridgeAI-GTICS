package com.pucp.skillb_ia.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

//Meta 160h, tope de horas extra y valor hora (sueldo 2400 / 160 = 15).
// Son los 3 casos del documento de requerimientos: 180h, 195h y 130h.
class ColBonoMensualViewTests {

    private static final BigDecimal META = new BigDecimal("160");
    private static final BigDecimal TOPE = new BigDecimal("20");
    private static final BigDecimal VALOR_HORA = new BigDecimal("15");

    private ColBonoMensualView bono(String trabajadas, boolean tuvoDescuento) {
        return new ColBonoMensualView(new BigDecimal(trabajadas), META, TOPE, tuvoDescuento, VALOR_HORA);
    }

    @Test
    void conExcesoDentroDelTopeSePagaTodoElExceso() {
        ColBonoMensualView b = bono("180", false);
        assertThat(b.getHorasExtra()).isEqualByComparingTo("20");
        assertThat(b.getHorasExtraBonificables()).isEqualByComparingTo("20");
        assertThat(b.getMontoBono()).isEqualByComparingTo("300");
    }

    @Test
    void conExcesoSobreElTopeSoloSePagaHastaElTope() {
        ColBonoMensualView b = bono("195", false);
        assertThat(b.getHorasExtra()).isEqualByComparingTo("35");
        assertThat(b.getHorasExtraBonificables()).isEqualByComparingTo("20");
        assertThat(b.getMontoBono()).isEqualByComparingTo("300");
    }

    @Test
    void conDeficitDeHorasNoHayBonoNiHorasExtra() {
        ColBonoMensualView b = bono("130", false);
        assertThat(b.getHorasExtra()).isEqualByComparingTo("0");
        assertThat(b.getMontoBono()).isEqualByComparingTo("0");
    }

    @Test
    void conExactamenteLaMetaNoHayBono() {
        assertThat(bono("160", false).getMontoBono()).isEqualByComparingTo("0");
    }

    @Test
    void siTuvoDescuentoPorPenalizacionElBonoEsCeroAunqueTengaHorasExtra() {
        ColBonoMensualView b = bono("190", true);
        assertThat(b.getHorasExtraBonificables()).isEqualByComparingTo("20");
        assertThat(b.getMontoBono()).isEqualByComparingTo("0");
    }

    @Test
    void siElAdminCambiaElTopeElBonoSeAjusta() {
        ColBonoMensualView b = new ColBonoMensualView(new BigDecimal("195"), META, new BigDecimal("10"), false, VALOR_HORA);
        assertThat(b.getMontoBono()).isEqualByComparingTo("150");
    }

    @Test
    void elProgresoDeLaBarraEsExcesoSobreTope() {
        assertThat(bono("170", false).getPorcentajeProgreso()).isEqualTo(50);
        assertThat(bono("195", false).getPorcentajeProgreso()).isEqualTo(100);
    }

    @Test
    void conTopeCeroElProgresoEsCero() {
        ColBonoMensualView b = new ColBonoMensualView(new BigDecimal("180"), META, BigDecimal.ZERO, false, VALOR_HORA);
        assertThat(b.getPorcentajeProgreso()).isEqualTo(0);
        assertThat(b.getMontoBono()).isEqualByComparingTo("0");
    }
}