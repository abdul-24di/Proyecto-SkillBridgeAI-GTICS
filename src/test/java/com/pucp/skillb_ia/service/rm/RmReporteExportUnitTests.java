package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmReporteView;
import com.pucp.skillb_ia.service.rm.RmReporteExportService.OpcionesExcel;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static com.pucp.skillb_ia.service.rm.RmReporteExportService.COLUMNAS_DETALLE;
import static com.pucp.skillb_ia.service.rm.RmReporteExportService.COLUMNAS_PROYECTO;
import static com.pucp.skillb_ia.service.rm.RmReporteExportService.HOJA_COLABORADORES;
import static com.pucp.skillb_ia.service.rm.RmReporteExportService.HOJA_PROYECTOS;
import static com.pucp.skillb_ia.service.rm.RmReporteExportService.METRICAS;
import static com.pucp.skillb_ia.service.rm.RmReporteExportService.NOTA_PRESUPUESTO;
import static com.pucp.skillb_ia.service.rm.RmReporteExportService.SIN_DETALLES;
import static com.pucp.skillb_ia.service.rm.RmReporteExportService.SIN_PROYECTOS;
import static com.pucp.skillb_ia.service.rm.RmReporteExportService.TITULO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Unitarias de RmReporteExportService (TASK-087): sin repositorios. El Excel se genera en memoria
// y se vuelve a leer con POI; no se escribe ningún archivo en disco.
class RmReporteExportUnitTests {

    private final RmReporteExportService service = new RmReporteExportService();

    private static RmReporteView.ProyectoReporte proyecto(Long id, String nombre, String presupuesto, String horas) {
        return new RmReporteView.ProyectoReporte(id, nombre, "ACTIVO", "Activo", "bg-green-lt",
                "Alta", "bg-red-lt text-red", new BigDecimal(presupuesto), new BigDecimal(horas), 2, 100);
    }

    private static RmReporteView.DetalleColaborador detalle(Long colaboradorId, String colaborador, String proyecto,
                                                            String horas, long tareas) {
        return new RmReporteView.DetalleColaborador(colaboradorId, colaborador, "Analista", 10L, proyecto,
                new BigDecimal(horas), tareas);
    }

    private static RmReporteView reporte(YearMonth desde, YearMonth hasta, Long proyectoId, String proyectoNombre,
                                         String estado, String prioridad,
                                         List<RmReporteView.ProyectoReporte> proyectos,
                                         List<RmReporteView.DetalleColaborador> detalles) {
        return new RmReporteView(desde, hasta, "Enero 2026", proyectoId, estado, proyectoNombre,
                estado.isEmpty() ? null : "Activo", prioridad, prioridad.isEmpty() ? null : "Alta",
                proyectos, detalles, List.of());
    }

    private static RmReporteView reporteSimple(Long proyectoId, String proyectoNombre, String estado, String prioridad) {
        YearMonth enero = YearMonth.of(2026, 1);
        return reporte(enero, enero, proyectoId, proyectoNombre, estado, prioridad, List.of(), List.of());
    }

    private static RmReporteView reporteConDatos() {
        YearMonth enero = YearMonth.of(2026, 1);
        return reporte(enero, enero, null, null, "", "",
                List.of(proyecto(10L, "Alfa", "5000", "12"), proyecto(20L, "Beta", "0", "6")),
                List.of(detalle(100L, "Ana Ruiz", "Alfa", "8", 2), detalle(200L, "Bruno Soto", "Beta", "6", 1)));
    }

    private static Workbook leer(byte[] contenido) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(contenido));
    }

    // Busca la fila cuya primera celda tiene ese texto; -1 si no está.
    private static int buscarFila(Sheet hoja, String texto) {
        for (Row row : hoja) {
            if (row.getCell(0) != null && texto.equals(row.getCell(0).toString())) return row.getRowNum();
        }
        return -1;
    }

    private static List<String> textos(Row row) {
        List<String> valores = new ArrayList<>();
        row.forEach(celda -> valores.add(celda.getStringCellValue()));
        return valores;
    }

    @Nested
    class NombreArchivo {

        @Test
        void soloPeriodoSinFiltros() {
            assertEquals("reporte-recursos-2026-01", service.nombreArchivo(reporteSimple(null, null, "", "")));
        }

        @Test
        void rangoAgregaElMesFinal() {
            RmReporteView reporte = reporte(YearMonth.of(2025, 11), YearMonth.of(2026, 1), null, null, "", "",
                    List.of(), List.of());
            assertEquals("reporte-recursos-2025-11-a-2026-01", service.nombreArchivo(reporte));
        }

        @Test
        void proyectoEstadoYPrioridadConCaracteresSeguros() {
            RmReporteView reporte = reporteSimple(7L, "  Migración / Núcleo #2! ", "EN_REVISION", "ALTA");
            assertEquals("reporte-recursos-2026-01-proyecto-migracion-nucleo-2-estado-en-revision-prioridad-alta",
                    service.nombreArchivo(reporte));
        }

        @Test
        void proyectoSinNombreUtilUsaSuId() {
            assertEquals("reporte-recursos-2026-01-proyecto-7", service.nombreArchivo(reporteSimple(7L, null, "", "")));
            assertEquals("reporte-recursos-2026-01-proyecto-7", service.nombreArchivo(reporteSimple(7L, "¿?", "", "")));
        }

        @Test
        void nombreLargoSeRecortaACuarentaCaracteresSinGuionFinal() {
            String nombre = "a".repeat(39) + " bcd";
            String archivo = service.nombreArchivo(reporteSimple(7L, nombre, "", ""));
            assertEquals("reporte-recursos-2026-01-proyecto-" + "a".repeat(39), archivo);
        }

        @Test
        void nombreArchivoHorasUsaElPeriodo() {
            assertEquals("horas-colaboradores-2026-01", service.nombreArchivoHoras(reporteSimple(null, null, "", "")));
        }
    }

    @Nested
    class Normalizar {

        @Test
        void sinPersonalizarExportaTodo() {
            OpcionesExcel opciones = OpcionesExcel.normalizar(false, false, null, null, null);
            assertEquals(OpcionesExcel.completo(), opciones);
            assertEquals(List.of(0, 1, 2, 3, 4, 5), opciones.columnasProyecto());
            assertEquals(List.of(0, 1, 2, 3, 4), opciones.columnasDetalle());
        }

        @Test
        void sinHojasLanza() {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> OpcionesExcel.normalizar(true, true, List.of(), List.of(1), List.of(1)));
            assertEquals("Selecciona al menos una hoja para exportar.", error.getMessage());
            assertThrows(IllegalArgumentException.class,
                    () -> OpcionesExcel.normalizar(true, true, null, null, null));
            assertThrows(IllegalArgumentException.class,
                    () -> OpcionesExcel.normalizar(true, true, List.of("otra"), null, null));
        }

        @Test
        void conservaLaPrimeraColumnaEIgnoraIndicesInvalidos() {
            OpcionesExcel opciones = OpcionesExcel.normalizar(true, false, List.of(HOJA_PROYECTOS),
                    List.of(9, 3, -1, 3), null);
            assertFalse(opciones.resumen());
            assertTrue(opciones.hojaProyectos());
            assertFalse(opciones.hojaColaboradores());
            assertEquals(List.of(0, 3), opciones.columnasProyecto());
            assertEquals(List.of(0), opciones.columnasDetalle());
        }

        @Test
        void ambasHojasConColumnasEnOrden() {
            OpcionesExcel opciones = OpcionesExcel.normalizar(true, true,
                    List.of(HOJA_COLABORADORES, HOJA_PROYECTOS), List.of(5, 1), List.of(4, 2));
            assertTrue(opciones.hojaProyectos() && opciones.hojaColaboradores() && opciones.resumen());
            assertEquals(List.of(0, 1, 5), opciones.columnasProyecto());
            assertEquals(List.of(0, 2, 4), opciones.columnasDetalle());
        }
    }

    @Nested
    class CrearExcel {

        @Test
        void completoTieneDosHojasConEncabezadoMetricasCabecerasYFilas() throws IOException {
            try (Workbook libro = leer(service.crearExcel(reporteConDatos()))) {
                assertEquals(2, libro.getNumberOfSheets());
                Sheet proyectos = libro.getSheet("Proyectos");
                Sheet colaboradores = libro.getSheet("Colaboradores");
                assertEquals(TITULO, proyectos.getRow(0).getCell(0).getStringCellValue());
                assertEquals("Enero 2026", proyectos.getRow(1).getCell(1).getStringCellValue());

                int metricas = buscarFila(proyectos, METRICAS.get(0));
                assertEquals(2.0, proyectos.getRow(metricas).getCell(1).getNumericCellValue());
                assertEquals(5000.0, proyectos.getRow(metricas + 1).getCell(1).getNumericCellValue());
                assertEquals(18.0, proyectos.getRow(metricas + 2).getCell(1).getNumericCellValue());
                assertEquals(2.0, proyectos.getRow(metricas + 3).getCell(1).getNumericCellValue());

                int cabecera = buscarFila(proyectos, COLUMNAS_PROYECTO.get(0));
                assertEquals(COLUMNAS_PROYECTO, textos(proyectos.getRow(cabecera)));
                Row alfa = proyectos.getRow(cabecera + 1);
                assertEquals("Alfa", alfa.getCell(0).getStringCellValue());
                assertEquals("Activo", alfa.getCell(1).getStringCellValue());
                assertEquals(5000.0, alfa.getCell(3).getNumericCellValue());
                assertEquals(12.0, alfa.getCell(4).getNumericCellValue());
                assertEquals("Beta", proyectos.getRow(cabecera + 2).getCell(0).getStringCellValue());
                assertTrue(buscarFila(proyectos, NOTA_PRESUPUESTO) > cabecera + 2);

                int cabeceraDetalle = buscarFila(colaboradores, COLUMNAS_DETALLE.get(0));
                assertEquals(COLUMNAS_DETALLE, textos(colaboradores.getRow(cabeceraDetalle)));
                Row ana = colaboradores.getRow(cabeceraDetalle + 1);
                assertEquals("Ana Ruiz", ana.getCell(0).getStringCellValue());
                assertEquals(8.0, ana.getCell(3).getNumericCellValue());
                assertEquals(2.0, ana.getCell(4).getNumericCellValue());
                assertEquals("Bruno Soto", colaboradores.getRow(cabeceraDetalle + 2).getCell(0).getStringCellValue());
                assertEquals(-1, buscarFila(colaboradores, METRICAS.get(0)));
            }
        }

        @Test
        void reporteVacioMuestraLosMensajesSinDatos() throws IOException {
            try (Workbook libro = leer(service.crearExcel(reporteSimple(null, null, "", "")))) {
                assertTrue(buscarFila(libro.getSheet("Proyectos"), SIN_PROYECTOS) > 0);
                assertTrue(buscarFila(libro.getSheet("Colaboradores"), SIN_DETALLES) > 0);
            }
        }

        @Test
        void personalizadoSoloProyectosSinResumenNiPresupuesto() throws IOException {
            OpcionesExcel opciones = OpcionesExcel.normalizar(true, false, List.of(HOJA_PROYECTOS),
                    List.of(4), null);
            try (Workbook libro = leer(service.crearExcel(reporteConDatos(), opciones))) {
                assertEquals(1, libro.getNumberOfSheets());
                Sheet proyectos = libro.getSheet("Proyectos");
                assertEquals(-1, buscarFila(proyectos, METRICAS.get(0)));
                assertEquals(-1, buscarFila(proyectos, NOTA_PRESUPUESTO));
                int cabecera = buscarFila(proyectos, COLUMNAS_PROYECTO.get(0));
                assertEquals(List.of("Proyecto", "Horas trabajadas"), textos(proyectos.getRow(cabecera)));
                assertEquals(12.0, proyectos.getRow(cabecera + 1).getCell(1).getNumericCellValue());
            }
        }

        @Test
        void crearPdfDevuelveUnDocumentoPdf() {
            byte[] pdf = service.crearPdf(reporteConDatos());
            assertTrue(pdf.length > 0);
            assertEquals("%PDF", new String(pdf, 0, 4));
        }
    }
}
