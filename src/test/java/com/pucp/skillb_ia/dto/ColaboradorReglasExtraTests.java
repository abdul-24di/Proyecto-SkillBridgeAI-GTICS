package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.security.HtmlSeguro;
import com.pucp.skillb_ia.service.col.ColDesempenoService;
import com.pucp.skillb_ia.util.Periodo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Más pruebas del colaborador:
 * puntualidad del desempeño, filtro por mes de los reportes y limpieza del HTML del foro.
 */
class ColaboradorReglasExtraTests {

    // Crea un desempeño solo con las entregas a tiempo y tardías; lo demás va en cero o vacío.
    private ColDesempenoService.Desempeno desempeno(int aTiempo, int tardias) {
        return new ColDesempenoService.Desempeno(YearMonth.of(2026, 11), "noviembre 2026",
                BigDecimal.ZERO, aTiempo + tardias, aTiempo, tardias, 0, 0, 0,
                new ArrayList<ColDesempenoService.FilaProyecto>(),
                new ArrayList<ColDesempenoService.CursoCompletado>(),
                new ArrayList<ColDesempenoService.FilaMes>());
    }

    // =====================================================================
    // Puntualidad del colaborador (reporte de desempeño)
    // =====================================================================

    @Test
    void sinEntregasLaPuntualidadEsCero() {
        assertEquals(0, desempeno(0, 0).puntualidad());
    }

    @Test
    void todasLasEntregasATiempoDanPuntualidad100() {
        assertEquals(100, desempeno(5, 0).puntualidad());
    }

    @Test
    void tresATiempoYUnaTardiaDanPuntualidad75() {
        assertEquals(75, desempeno(3, 1).puntualidad());
    }

    @Test
    void dosATiempoYUnaTardiaSeRedondeaA67() {
        assertEquals(67, desempeno(2, 1).puntualidad());
    }

    @Test
    void todasLasEntregasTardiasDanPuntualidad0() {
        assertEquals(0, desempeno(0, 4).puntualidad());
    }

    @Test
    void elHistorialDeDesempenoEsDe6Meses() {
        assertEquals(6, ColDesempenoService.MESES_HISTORIAL);
    }

    // =====================================================================
    // Filtro por mes de los reportes
    // =====================================================================

    @Test
    void unMesBienEscritoSeLeeTalCual() {
        assertEquals(YearMonth.of(2025, 5), Periodo.leer("2025-05"));
    }

    @Test
    void unTextoNuloUsaElMesActual() {
        assertEquals(YearMonth.now(), Periodo.leer(null));
    }

    @Test
    void unTextoVacioUsaElMesActual() {
        assertEquals(YearMonth.now(), Periodo.leer("   "));
    }

    @Test
    void unTextoMalEscritoUsaElMesActual() {
        assertEquals(YearMonth.now(), Periodo.leer("hola"));
    }

    @Test
    void unMesFuturoUsaElMesActual() {
        assertEquals(YearMonth.now(), Periodo.leer("2999-01"));
    }

    @Test
    void losUltimosTresMesesVanDelMasAntiguoAlMasReciente() {
        List<YearMonth> meses = Periodo.ultimos(YearMonth.of(2026, 11), 3);

        assertEquals(3, meses.size());
        assertEquals(YearMonth.of(2026, 9), meses.get(0));
        assertEquals(YearMonth.of(2026, 10), meses.get(1));
        assertEquals(YearMonth.of(2026, 11), meses.get(2));
    }

    @Test
    void losUltimosMesesCruzanElCambioDeAnio() {
        List<YearMonth> meses = Periodo.ultimos(YearMonth.of(2026, 2), 3);

        assertEquals(YearMonth.of(2025, 12), meses.get(0));
        assertEquals(YearMonth.of(2026, 2), meses.get(2));
    }

    @Test
    void unaFechaDentroDelMesSeConsideraIncluida() {
        assertTrue(Periodo.contiene(YearMonth.of(2026, 11), LocalDate.of(2026, 11, 30)));
    }

    @Test
    void unaFechaDeOtroMesNoSeConsideraIncluida() {
        assertFalse(Periodo.contiene(YearMonth.of(2026, 11), LocalDate.of(2026, 12, 1)));
    }

    @Test
    void unaFechaNulaNoSeConsideraIncluida() {
        assertFalse(Periodo.contiene(YearMonth.of(2026, 11), null));
    }

    // =====================================================================
    // Seguridad: limpieza del HTML que se publica en el foro
    // =====================================================================

    @Test
    void elHtmlNuloSeQuedaNulo() {
        assertNull(HtmlSeguro.limpiar(null));
    }

    @Test
    void elFormatoPermitidoSeConserva() {
        String resultado = HtmlSeguro.limpiar("<b>negrita</b>");

        assertTrue(resultado.contains("<b>negrita</b>"));
    }

    @Test
    void unScriptEnUnaPublicacionSeElimina() {
        String resultado = HtmlSeguro.limpiar("<p>Hola</p><script>alert(1)</script>");

        assertTrue(resultado.contains("Hola"));
        assertFalse(resultado.contains("<script"));
        assertFalse(resultado.contains("alert(1)"));
    }

    @Test
    void seEliminaElEventoOnerrorDeUnaImagen() {
        String resultado = HtmlSeguro.limpiar("<img src=\"x\" onerror=\"alert(1)\">");

        assertFalse(resultado.contains("onerror"));
    }

    @Test
    void seEliminaUnEnlaceJavascript() {
        String resultado = HtmlSeguro.limpiar("<a href=\"javascript:alert(1)\">clic</a>");

        assertFalse(resultado.contains("javascript:"));
        assertTrue(resultado.contains("clic"));
    }
}