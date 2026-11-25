package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.dto.PmReporteConsolidadoView;
import com.pucp.skillb_ia.dto.PmReporteView;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.service.pm.PmReporteExportService.OpcionesExcel;
import com.pucp.skillb_ia.service.pm.PmReporteService.Avance;
import com.pucp.skillb_ia.service.pm.PmReporteService.AvanceColaborador;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas unitarias de {@link PmReporteExportService} con JUnit 5 (sin Spring ni BD). */
class PmReporteExportUnitTests {

    private final PmReporteExportService servicio = new PmReporteExportService();

    private static PmReporteConsolidadoView reporteMuestra(boolean vacio) {
        List<PmReporteConsolidadoView.ProyectoFila> proyectos = vacio ? List.of() : List.of(
                new PmReporteConsolidadoView.ProyectoFila(
                        10L, "Portal Clientes", "ACTIVO", "Activo", "bg-green-lt text-green",
                        "Alta", "bg-red-lt text-red", new BigDecimal("75000"),
                        2, 3, 4, 3, 2, 1, 0,
                        new BigDecimal("40"), new BigDecimal("30"), 100));
        List<PmReporteConsolidadoView.DetalleColaboradorFila> detalles = vacio ? List.of() : List.of(
                new PmReporteConsolidadoView.DetalleColaboradorFila(
                        5L, "Ana López", 10L, "Portal Clientes", new BigDecimal("20"),
                        4, 3, 2, 1, 0, 1, new BigDecimal("30"), 1));
        return new PmReporteConsolidadoView(
                YearMonth.of(2026, 2), YearMonth.of(2026, 3), false, "Febrero 2026 – Marzo 2026",
                10L, "Portal Clientes", "ACTIVO", "Activo", "ALTA", "Alta",
                proyectos, detalles);
    }

    @Test
    void opcionesExcelValidaAlMenosUnaHojaYConservaPrimeraColumna() {
        assertThrows(IllegalArgumentException.class,
                () -> OpcionesExcel.normalizar(true, true, List.of(), List.of(1), List.of(1)));

        OpcionesExcel personalizadas = OpcionesExcel.normalizar(
                true, false, List.of(PmReporteExportService.HOJA_PROYECTOS), List.of(2, 3), List.of());

        assertTrue(personalizadas.hojaProyectos());
        assertEquals(List.of(0, 2, 3), personalizadas.columnasProyecto());
    }

    @Test
    void nombreArchivoIncluyeRangoYFiltrosNormalizados() {
        PmReporteConsolidadoView rep = reporteMuestra(false);

        String nombre = servicio.nombreArchivo(rep);

        assertEquals("reporte-pm-2026-02-a-2026-03-proyecto-portal-clientes-estado-activo-prioridad-alta", nombre);
    }

    @Test
    void crearExcelGeneraLibroValidoConHojasEsperadas() throws Exception {
        byte[] bytes = servicio.crearExcel(reporteMuestra(false));
        assertTrue(bytes.length > 0);

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(2, wb.getNumberOfSheets());
            assertNotNull(wb.getSheet("Proyectos"));
            assertNotNull(wb.getSheet("Colaboradores"));
        }

        byte[] vacioBytes = servicio.crearExcel(reporteMuestra(true));
        assertTrue(vacioBytes.length > 0);
    }

    @Test
    void crearPdfYCrearPdfDetalleGeneranDocumentosPdfValidos() {
        byte[] pdfConsolidado = servicio.crearPdf(reporteMuestra(false));
        String cabecera = new String(pdfConsolidado, 0, 5, StandardCharsets.US_ASCII);
        assertEquals("%PDF-", cabecera);

        Usuario pm = PmDatosPrueba.pm(1L);
        Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm);
        PmReporteView vista = new PmReporteView(proyecto, 2, 0, 1, 0, 1, 1, new BigDecimal("16"),
                List.of(new PmReporteView.ColaboradorMetrica("Ana López", "AL", new BigDecimal("20"), 2, 1)));
        Avance avance = new Avance("Marzo 2026", 2, 1, 1, 0, 0, 0, new BigDecimal("16"), new BigDecimal("8"),
                List.of(new AvanceColaborador("Ana López", 2, 1, 1, 0, 0, 0, new BigDecimal("8"), 0)));

        byte[] pdfDetalle = servicio.crearPdfDetalle(vista, avance);
        assertEquals("%PDF-", new String(pdfDetalle, 0, 5, StandardCharsets.US_ASCII));
        assertEquals("reporte-proyecto-proyecto-10-2026-03", servicio.nombreArchivoDetalle(vista, "2026-03"));
    }
}
