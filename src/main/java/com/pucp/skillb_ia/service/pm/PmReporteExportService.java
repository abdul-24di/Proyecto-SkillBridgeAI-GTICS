package com.pucp.skillb_ia.service.pm;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.pucp.skillb_ia.dto.PmReporteConsolidadoView;
import com.pucp.skillb_ia.dto.PmReporteView;
import com.pucp.skillb_ia.model.Proyecto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * Servicio de exportación de reportes del Project Manager en formato Excel (XLSX) y PDF (OpenPDF),
 * tanto para el consolidado multicriterio (con selección de hojas y columnas) como para el detalle por proyecto.
 */
@Service
public class PmReporteExportService {

    public static final String TIPO_CONTENIDO_PDF = "application/pdf";
    public static final String TITULO = "Reporte de proyectos y desempeño del equipo (PM)";
    public static final String SECCION_PROYECTOS = "Comparativa por proyecto";
    public static final String SECCION_DETALLE = "Desempeño y puntualidad por colaborador";
    public static final List<String> ETIQUETAS_ENCABEZADO = List.of(
            "Periodo", "Filtros aplicados", "Fecha de generación", "Regla de cálculo");
    public static final List<String> METRICAS = List.of(
            "Proyectos incluidos", "Presupuesto total", "Actividades completadas",
            "Puntualidad global (%)", "Horas completadas", "Colaboradores involucrados");
    public static final List<String> COLUMNAS_PROYECTO = List.of(
            "Proyecto", "Estado", "Prioridad", "Presupuesto", "Equipo",
            "Progreso (%)", "Tareas (Comp./Total)", "Puntualidad (%)", "Horas completadas");
    public static final List<String> COLUMNAS_DETALLE = List.of(
            "Colaborador", "Proyecto", "Horas semanales", "Asignadas", "Completadas",
            "A tiempo", "Tarde", "Vencidas", "Devoluciones", "Horas completadas", "Strikes");
    public static final String SIN_PROYECTOS = "No hay proyectos que coincidan con los filtros.";
    public static final String SIN_DETALLES = "No hay registros de colaboradores en este periodo.";
    public static final String NOTA_PRESUPUESTO = "El presupuesto es información del proyecto. "
            + "El PM no visualiza sueldo, bono, pago mensual ni costo del colaborador.";
    public static final String HOJA_PROYECTOS = "proyectos";
    public static final String HOJA_COLABORADORES = "colaboradores";

    private static final String FORMATO_DINERO_EXCEL = "\"S/\" #,##0.00";
    private static final String FORMATO_HORAS_EXCEL = "#,##0.00 \"h\"";
    private static final DateTimeFormatter FORMATO_GENERACION = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int LARGO_SLUG = 40;
    private static final int CARACTERES_POR_LINEA_EXCEL = 70;

    private static final Color PDF_PRIMARY = new Color(32, 107, 196);
    private static final Color PDF_HEADER_BG = new Color(241, 245, 249);
    private static final Color PDF_BORDER = new Color(203, 213, 225);
    private static final Color PDF_TEXT_MUTED = new Color(100, 116, 139);
    private static final Color PDF_ZEBRA = new Color(248, 250, 252);

    public record OpcionesExcel(boolean resumen, boolean hojaProyectos, boolean hojaColaboradores,
                                List<Integer> columnasProyecto, List<Integer> columnasDetalle) {
        public static OpcionesExcel completo() {
            return new OpcionesExcel(true, true, true, todas(COLUMNAS_PROYECTO), todas(COLUMNAS_DETALLE));
        }

        public static OpcionesExcel normalizar(boolean personalizado, boolean resumen, List<String> hojas,
                                               List<Integer> columnasProyecto, List<Integer> columnasDetalle) {
            if (!personalizado) return completo();
            boolean proyectos = hojas != null && hojas.contains(HOJA_PROYECTOS);
            boolean colaboradores = hojas != null && hojas.contains(HOJA_COLABORADORES);
            if (!proyectos && !colaboradores) {
                throw new IllegalArgumentException("Selecciona al menos una hoja para exportar.");
            }
            return new OpcionesExcel(resumen, proyectos, colaboradores,
                    columnas(columnasProyecto, COLUMNAS_PROYECTO.size()),
                    columnas(columnasDetalle, COLUMNAS_DETALLE.size()));
        }

        private static List<Integer> todas(List<String> columnas) {
            return IntStream.range(0, columnas.size()).boxed().toList();
        }

        private static List<Integer> columnas(List<Integer> elegidas, int total) {
            return IntStream.range(0, total)
                    .filter(indice -> indice == 0 || (elegidas != null && elegidas.contains(indice)))
                    .boxed().toList();
        }
    }

    /** Construye el nombre base de descarga del reporte consolidado incluyendo rango y filtros activos. */
    public String nombreArchivo(PmReporteConsolidadoView reporte) {
        StringBuilder nombre = new StringBuilder("reporte-pm-");
        if (reporte.isTodoPeriodo()) {
            nombre.append("historial-completo");
        } else {
            nombre.append(reporte.getPeriodoValor());
            if (reporte.esRango()) nombre.append("-a-").append(reporte.getPeriodoHastaValor());
        }
        if (reporte.getProyectoSeleccionado() != null) {
            String proyecto = slug(reporte.getProyectoSeleccionadoNombre());
            nombre.append("-proyecto-")
                    .append(proyecto.isEmpty() ? reporte.getProyectoSeleccionado().toString() : proyecto);
        }
        String estado = slug(reporte.getEstadoSeleccionado());
        if (!estado.isEmpty()) nombre.append("-estado-").append(estado);
        String prioridad = slug(reporte.getPrioridadSeleccionada());
        if (!prioridad.isEmpty()) nombre.append("-prioridad-").append(prioridad);
        return nombre.toString();
    }

    /** Construye el nombre base de descarga del reporte detallado de un proyecto. */
    public String nombreArchivoDetalle(PmReporteView reporte, String mes) {
        String base = slug(reporte.getProyecto().getNombre());
        if (base.isEmpty()) base = "proyecto-" + reporte.getProyecto().getId();
        String sufijoMes = (mes != null && !mes.isBlank()) ? "-" + slug(mes) : "-general";
        return "reporte-proyecto-" + base + sufijoMes;
    }

    /** Genera el libro Excel (XLSX) completo del reporte consolidado del PM. */
    public byte[] crearExcel(PmReporteConsolidadoView reporte) {
        return crearExcel(reporte, OpcionesExcel.completo());
    }

    /** Genera el libro Excel (XLSX) del reporte consolidado aplicando las hojas y columnas elegidas. */
    public byte[] crearExcel(PmReporteConsolidadoView reporte, OpcionesExcel opciones) {
        String generado = LocalDateTime.now().format(FORMATO_GENERACION);
        try (Workbook libro = new XSSFWorkbook();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            EstilosExcel estilos = new EstilosExcel(libro);

            if (opciones.hojaProyectos()) {
                Sheet proyectos = libro.createSheet("Proyectos");
                List<Integer> columnas = opciones.columnasProyecto();
                int ancho = Math.max(2, columnas.size());
                int fila = escribirEncabezado(proyectos, reporte, generado, estilos, ancho);
                if (opciones.resumen()) {
                    escribirMetrica(proyectos, fila++, METRICAS.get(0), estilos)
                            .setCellValue(reporte.getTotalProyectos());
                    asignarNumero(escribirMetrica(proyectos, fila++, METRICAS.get(1), estilos),
                            reporte.getPresupuestoTotal(), estilos.dinero);
                    escribirMetrica(proyectos, fila++, METRICAS.get(2), estilos)
                            .setCellValue(reporte.getActividadesCompletadas() + " / " + reporte.getTotalActividades());
                    escribirMetrica(proyectos, fila++, METRICAS.get(3), estilos)
                            .setCellValue(reporte.getPuntualidadGlobal());
                    asignarNumero(escribirMetrica(proyectos, fila++, METRICAS.get(4), estilos),
                            reporte.getHorasCompletadasTotales(), estilos.horas);
                    escribirMetrica(proyectos, fila++, METRICAS.get(5), estilos)
                            .setCellValue(reporte.getColaboradoresInvolucrados());
                    fila++;
                }
                fila = escribirSeccion(proyectos, fila, SECCION_PROYECTOS,
                        etiquetas(COLUMNAS_PROYECTO, columnas), estilos);
                for (PmReporteConsolidadoView.ProyectoFila item : reporte.getProyectos()) {
                    Row row = proyectos.createRow(fila++);
                    for (int posicion = 0; posicion < columnas.size(); posicion++) {
                        escribirProyecto(row.createCell(posicion), item, columnas.get(posicion), estilos);
                    }
                }
                if (reporte.getProyectos().isEmpty()) {
                    fila = escribirTexto(proyectos, fila, SIN_PROYECTOS, ancho);
                }
                if (columnas.contains(3)) escribirTexto(proyectos, fila + 1, NOTA_PRESUPUESTO, ancho);
                ajustarColumnas(proyectos, columnas.size());
            }

            if (opciones.hojaColaboradores()) {
                Sheet colaboradores = libro.createSheet("Colaboradores");
                List<Integer> columnas = opciones.columnasDetalle();
                int ancho = Math.max(2, columnas.size());
                int fila = escribirEncabezado(colaboradores, reporte, generado, estilos, ancho);
                fila = escribirSeccion(colaboradores, fila, SECCION_DETALLE,
                        etiquetas(COLUMNAS_DETALLE, columnas), estilos);
                for (PmReporteConsolidadoView.DetalleColaboradorFila item : reporte.getDetalles()) {
                    Row row = colaboradores.createRow(fila++);
                    for (int posicion = 0; posicion < columnas.size(); posicion++) {
                        escribirDetalle(row.createCell(posicion), item, columnas.get(posicion), estilos);
                    }
                }
                if (reporte.getDetalles().isEmpty()) {
                    escribirTexto(colaboradores, fila, SIN_DETALLES, ancho);
                }
                ajustarColumnas(colaboradores, columnas.size());
            }

            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el archivo Excel.", ex);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // EXPORTACIÓN PDF - REPORTE CONSOLIDADO DEL PM
    // ═══════════════════════════════════════════════════════════

    /** Genera el documento PDF completo del reporte consolidado del PM. */
    public byte[] crearPdf(PmReporteConsolidadoView reporte) {
        return crearPdf(reporte, OpcionesExcel.completo());
    }

    /** Genera el documento PDF del reporte consolidado aplicando la selección de resumen, secciones y columnas. */
    public byte[] crearPdf(PmReporteConsolidadoView reporte, OpcionesExcel opciones) {
        String generado = LocalDateTime.now().format(FORMATO_GENERACION);
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 28f, 28f, 28f, 28f);
            PdfWriter.getInstance(doc, salida);
            doc.open();

            EstilosPdf estilos = new EstilosPdf();

            Paragraph pTitulo = new Paragraph(TITULO, estilos.titulo);
            pTitulo.setSpacingAfter(4f);
            doc.add(pTitulo);

            Paragraph pSub = new Paragraph("SkillBridge AI — Panel de Gestión del Project Manager", estilos.subtitulo);
            pSub.setSpacingAfter(10f);
            doc.add(pSub);

            // Tabla de metadatos
            PdfPTable meta = new PdfPTable(new float[]{1.8f, 6.2f});
            meta.setWidthPercentage(100f);
            meta.setSpacingAfter(12f);
            List<String> valores = valoresEncabezado(reporte, generado);
            for (int i = 0; i < ETIQUETAS_ENCABEZADO.size(); i++) {
                agregarFilaMetaPdf(meta, ETIQUETAS_ENCABEZADO.get(i), valores.get(i), estilos);
            }
            doc.add(meta);

            // Resumen de KPIs ejecutivos
            if (opciones.resumen()) {
                Paragraph pKpi = new Paragraph("Resumen ejecutivo del periodo", estilos.seccion);
                pKpi.setSpacingAfter(6f);
                doc.add(pKpi);

                PdfPTable kpis = new PdfPTable(6);
                kpis.setWidthPercentage(100f);
                kpis.setSpacingAfter(14f);
                agregarTarjetaKpiPdf(kpis, METRICAS.get(0), String.valueOf(reporte.getTotalProyectos()), estilos);
                agregarTarjetaKpiPdf(kpis, METRICAS.get(1), formatearDinero(reporte.getPresupuestoTotal()), estilos);
                agregarTarjetaKpiPdf(kpis, METRICAS.get(2),
                        reporte.getActividadesCompletadas() + " / " + reporte.getTotalActividades()
                                + " (" + reporte.getProgresoGlobal() + "%)", estilos);
                agregarTarjetaKpiPdf(kpis, METRICAS.get(3), reporte.getPuntualidadGlobal() + "%", estilos);
                agregarTarjetaKpiPdf(kpis, METRICAS.get(4),
                        formatearHoras(reporte.getHorasCompletadasTotales()) + " / "
                                + formatearHoras(reporte.getHorasEstimadasTotales()), estilos);
                agregarTarjetaKpiPdf(kpis, METRICAS.get(5), String.valueOf(reporte.getColaboradoresInvolucrados()), estilos);
                doc.add(kpis);
            }

            // Sección: Proyectos
            if (opciones.hojaProyectos()) {
                Paragraph pSecProy = new Paragraph(SECCION_PROYECTOS, estilos.seccion);
                pSecProy.setSpacingAfter(6f);
                doc.add(pSecProy);

                List<Integer> cols = opciones.columnasProyecto();
                PdfPTable tablaProy = new PdfPTable(cols.size());
                tablaProy.setWidthPercentage(100f);
                tablaProy.setHeaderRows(1);
                tablaProy.setSpacingAfter(8f);

                for (String etq : etiquetas(COLUMNAS_PROYECTO, cols)) {
                    tablaProy.addCell(celdaCabeceraPdf(etq, estilos));
                }
                if (reporte.getProyectos().isEmpty()) {
                    PdfPCell vacia = new PdfPCell(new Phrase(SIN_PROYECTOS, estilos.celdaMuted));
                    vacia.setColspan(cols.size());
                    vacia.setPadding(8f);
                    vacia.setBorderColor(PDF_BORDER);
                    tablaProy.addCell(vacia);
                } else {
                    int idxFila = 0;
                    for (PmReporteConsolidadoView.ProyectoFila item : reporte.getProyectos()) {
                        boolean par = (idxFila++ % 2 == 1);
                        for (int col : cols) {
                            tablaProy.addCell(celdaDatoPdf(valorProyectoTexto(item, col), alineacionProyecto(col), par, estilos));
                        }
                    }
                }
                doc.add(tablaProy);

                if (cols.contains(3)) {
                    Paragraph nota = new Paragraph(NOTA_PRESUPUESTO, estilos.nota);
                    nota.setSpacingAfter(12f);
                    doc.add(nota);
                }
            }

            // Sección: Colaboradores
            if (opciones.hojaColaboradores()) {
                Paragraph pSecCol = new Paragraph(SECCION_DETALLE, estilos.seccion);
                pSecCol.setSpacingBefore(4f);
                pSecCol.setSpacingAfter(6f);
                doc.add(pSecCol);

                List<Integer> cols = opciones.columnasDetalle();
                PdfPTable tablaCol = new PdfPTable(cols.size());
                tablaCol.setWidthPercentage(100f);
                tablaCol.setHeaderRows(1);
                tablaCol.setSpacingAfter(8f);

                for (String etq : etiquetas(COLUMNAS_DETALLE, cols)) {
                    tablaCol.addCell(celdaCabeceraPdf(etq, estilos));
                }
                if (reporte.getDetalles().isEmpty()) {
                    PdfPCell vacia = new PdfPCell(new Phrase(SIN_DETALLES, estilos.celdaMuted));
                    vacia.setColspan(cols.size());
                    vacia.setPadding(8f);
                    vacia.setBorderColor(PDF_BORDER);
                    tablaCol.addCell(vacia);
                } else {
                    int idxFila = 0;
                    for (PmReporteConsolidadoView.DetalleColaboradorFila item : reporte.getDetalles()) {
                        boolean par = (idxFila++ % 2 == 1);
                        for (int col : cols) {
                            tablaCol.addCell(celdaDatoPdf(valorDetalleTexto(item, col), alineacionDetalle(col), par, estilos));
                        }
                    }
                }
                doc.add(tablaCol);
            }

            doc.close();
            return salida.toByteArray();
        } catch (DocumentException | IOException ex) {
            throw new IllegalStateException("No se pudo generar el archivo PDF.", ex);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // EXPORTACIÓN PDF - REPORTE DETALLADO DE UN PROYECTO
    // ═══════════════════════════════════════════════════════════

    /** Genera el documento PDF del reporte individual y avance mensual de un proyecto del PM. */
    public byte[] crearPdfDetalle(PmReporteView reporte, PmReporteService.Avance avance) {
        String generado = LocalDateTime.now().format(FORMATO_GENERACION);
        Proyecto proy = reporte.getProyecto();
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 28f, 28f, 28f, 28f);
            PdfWriter.getInstance(doc, salida);
            doc.open();

            EstilosPdf estilos = new EstilosPdf();

            Paragraph pTitulo = new Paragraph("Reporte detallado del proyecto: " + proy.getNombre(), estilos.titulo);
            pTitulo.setSpacingAfter(4f);
            doc.add(pTitulo);

            Paragraph pSub = new Paragraph("SkillBridge AI — Reporte individual de progreso, puntualidad y equipo (PM)", estilos.subtitulo);
            pSub.setSpacingAfter(10f);
            doc.add(pSub);

            // Metadatos del proyecto
            PdfPTable meta = new PdfPTable(new float[]{1.8f, 3.2f, 1.8f, 3.2f});
            meta.setWidthPercentage(100f);
            meta.setSpacingAfter(12f);
            agregarFilaMetaDoblePdf(meta,
                    "Estado del proyecto", proy.getEstado() != null ? proy.getEstado().name() : "-",
                    "Prioridad", proy.getPrioridad() != null ? proy.getPrioridad().name() : "MEDIA", estilos);
            agregarFilaMetaDoblePdf(meta,
                    "Fecha inicio", proy.getFechaInicio() != null ? proy.getFechaInicio().format(FORMATO_FECHA) : "Por definir",
                    "Fecha fin estimada", proy.getFechaFinEstimada() != null ? proy.getFechaFinEstimada().format(FORMATO_FECHA) : "Por definir", estilos);
            agregarFilaMetaDoblePdf(meta,
                    "Presupuesto del proyecto", formatearDinero(reporte.getPresupuestoVisible()),
                    "Periodo de avance analizado", avance != null ? avance.periodo() : "Todo el proyecto", estilos);
            agregarFilaMetaDoblePdf(meta,
                    "Equipo asignado", reporte.getIntegrantesActivos() + " / " + proy.getColaboradoresRequeridos() + " colaboradores",
                    "Fecha de generación", generado, estilos);
            doc.add(meta);

            // Resumen de estado de actividades del proyecto
            Paragraph pResumen = new Paragraph("Estado general de actividades del proyecto", estilos.seccion);
            pResumen.setSpacingAfter(6f);
            doc.add(pResumen);

            PdfPTable kpis = new PdfPTable(6);
            kpis.setWidthPercentage(100f);
            kpis.setSpacingAfter(14f);
            agregarTarjetaKpiPdf(kpis, "Progreso general", reporte.getProgresoPorc() + "%", estilos);
            agregarTarjetaKpiPdf(kpis, "Total actividades", String.valueOf(reporte.getTotalActividades()), estilos);
            agregarTarjetaKpiPdf(kpis, "Completadas", String.valueOf(reporte.getActividadesCompletadas()), estilos);
            agregarTarjetaKpiPdf(kpis, "En revisión / progreso",
                    reporte.getActividadesEnRevision() + " / " + reporte.getActividadesEnProgreso(), estilos);
            agregarTarjetaKpiPdf(kpis, "Pendientes", String.valueOf(reporte.getActividadesPendientes()), estilos);
            agregarTarjetaKpiPdf(kpis, "Horas estimadas", formatearHoras(reporte.getHorasTotalesEstimadas()), estilos);
            doc.add(kpis);

            // Avance y puntualidad del periodo seleccionado
            if (avance != null) {
                Paragraph pAvance = new Paragraph("Avance y puntualidad (" + avance.periodo() + ")", estilos.seccion);
                pAvance.setSpacingAfter(6f);
                doc.add(pAvance);

                PdfPTable kpisAvance = new PdfPTable(5);
                kpisAvance.setWidthPercentage(100f);
                kpisAvance.setSpacingAfter(10f);
                agregarTarjetaKpiPdf(kpisAvance, "Completadas en periodo", avance.completadas() + " / " + avance.total(), estilos);
                agregarTarjetaKpiPdf(kpisAvance, "Puntualidad",
                        avance.puntualidad() + "% (" + avance.aTiempo() + " a tiempo · " + avance.tardias() + " tarde)", estilos);
                agregarTarjetaKpiPdf(kpisAvance, "Vencidas sin completar", String.valueOf(avance.vencidas()), estilos);
                agregarTarjetaKpiPdf(kpisAvance, "Devoluciones en revisión", String.valueOf(avance.devoluciones()), estilos);
                agregarTarjetaKpiPdf(kpisAvance, "Horas completadas",
                        formatearHoras(avance.horasCompletadas()) + " / " + formatearHoras(avance.horasEstimadas()), estilos);
                doc.add(kpisAvance);

                List<String> colsAvance = List.of(
                        "Colaborador", "Asignadas", "Completadas", "A tiempo", "Tarde",
                        "Vencidas", "Devoluciones", "Horas completadas", "Strikes");
                PdfPTable tablaAvance = new PdfPTable( new float[]{2.6f, 1.1f, 1.2f, 1.1f, 1.0f, 1.1f, 1.3f, 1.5f, 1.0f});
                tablaAvance.setWidthPercentage(100f);
                tablaAvance.setHeaderRows(1);
                tablaAvance.setSpacingAfter(14f);

                for (String col : colsAvance) {
                    tablaAvance.addCell(celdaCabeceraPdf(col, estilos));
                }
                if (avance.colaboradores().isEmpty()) {
                    PdfPCell vacia = new PdfPCell(new Phrase("No hay colaboradores activos en este proyecto.", estilos.celdaMuted));
                    vacia.setColspan(colsAvance.size());
                    vacia.setPadding(8f);
                    vacia.setBorderColor(PDF_BORDER);
                    tablaAvance.addCell(vacia);
                } else {
                    int idx = 0;
                    for (PmReporteService.AvanceColaborador c : avance.colaboradores()) {
                        boolean par = (idx++ % 2 == 1);
                        tablaAvance.addCell(celdaDatoPdf(c.nombre(), Element.ALIGN_LEFT, par, estilos));
                        tablaAvance.addCell(celdaDatoPdf(String.valueOf(c.asignadas()), Element.ALIGN_RIGHT, par, estilos));
                        tablaAvance.addCell(celdaDatoPdf(String.valueOf(c.completadas()), Element.ALIGN_RIGHT, par, estilos));
                        tablaAvance.addCell(celdaDatoPdf(String.valueOf(c.aTiempo()), Element.ALIGN_RIGHT, par, estilos));
                        tablaAvance.addCell(celdaDatoPdf(String.valueOf(c.tardias()), Element.ALIGN_RIGHT, par, estilos));
                        tablaAvance.addCell(celdaDatoPdf(String.valueOf(c.vencidas()), Element.ALIGN_RIGHT, par, estilos));
                        tablaAvance.addCell(celdaDatoPdf(String.valueOf(c.devoluciones()), Element.ALIGN_RIGHT, par, estilos));
                        tablaAvance.addCell(celdaDatoPdf(formatearHoras(c.horasCompletadas()), Element.ALIGN_RIGHT, par, estilos));
                        tablaAvance.addCell(celdaDatoPdf(c.strikes() + " / 3", Element.ALIGN_CENTER, par, estilos));
                    }
                }
                doc.add(tablaAvance);
            }

            // Rendimiento global por colaborador en el proyecto
            Paragraph pEquipo = new Paragraph("Rendimiento acumulado por colaborador en el proyecto", estilos.seccion);
            pEquipo.setSpacingAfter(6f);
            doc.add(pEquipo);

            List<String> colsEquipo = List.of("Colaborador", "Horas semanales", "Actividades asignadas", "Completadas", "Rendimiento (%)");
            PdfPTable tablaEquipo = new PdfPTable(new float[]{3.0f, 1.5f, 1.6f, 1.5f, 1.5f});
            tablaEquipo.setWidthPercentage(100f);
            tablaEquipo.setHeaderRows(1);
            tablaEquipo.setSpacingAfter(10f);

            for (String col : colsEquipo) {
                tablaEquipo.addCell(celdaCabeceraPdf(col, estilos));
            }
            if (reporte.getColaboradores().isEmpty()) {
                PdfPCell vacia = new PdfPCell(new Phrase("No hay colaboradores activos en este proyecto.", estilos.celdaMuted));
                vacia.setColspan(colsEquipo.size());
                vacia.setPadding(8f);
                vacia.setBorderColor(PDF_BORDER);
                tablaEquipo.addCell(vacia);
            } else {
                int idx = 0;
                for (PmReporteView.ColaboradorMetrica m : reporte.getColaboradores()) {
                    boolean par = (idx++ % 2 == 1);
                    tablaEquipo.addCell(celdaDatoPdf(m.getNombre(), Element.ALIGN_LEFT, par, estilos));
                    tablaEquipo.addCell(celdaDatoPdf(formatearHoras(m.getHorasSemanales()) + "/sem", Element.ALIGN_RIGHT, par, estilos));
                    tablaEquipo.addCell(celdaDatoPdf(String.valueOf(m.getActividadesAsignadas()), Element.ALIGN_RIGHT, par, estilos));
                    tablaEquipo.addCell(celdaDatoPdf(String.valueOf(m.getActividadesCompletadas()), Element.ALIGN_RIGHT, par, estilos));
                    tablaEquipo.addCell(celdaDatoPdf(m.getRendimientoPorc() + "%", Element.ALIGN_RIGHT, par, estilos));
                }
            }
            doc.add(tablaEquipo);

            Paragraph nota = new Paragraph(NOTA_PRESUPUESTO, estilos.nota);
            doc.add(nota);

            doc.close();
            return salida.toByteArray();
        } catch (DocumentException | IOException ex) {
            throw new IllegalStateException("No se pudo generar el archivo PDF del proyecto.", ex);
        }
    }

    private void escribirProyecto(Cell celda, PmReporteConsolidadoView.ProyectoFila item, int columna,
                                  EstilosExcel estilos) {
        switch (columna) {
            case 0 -> celda.setCellValue(item.getNombre());
            case 1 -> celda.setCellValue(item.getEstadoTexto());
            case 2 -> celda.setCellValue(item.getPrioridadTexto());
            case 3 -> asignarNumero(celda, item.getPresupuesto(), estilos.dinero);
            case 4 -> celda.setCellValue(item.getEquipoTexto());
            case 5 -> celda.setCellValue(item.getProgresoPorc());
            case 6 -> celda.setCellValue(item.getTareasTexto());
            case 7 -> celda.setCellValue(item.getPuntualidadPorc());
            default -> asignarNumero(celda, item.getHorasCompletadas(), estilos.horas);
        }
    }

    private void escribirDetalle(Cell celda, PmReporteConsolidadoView.DetalleColaboradorFila item, int columna,
                                 EstilosExcel estilos) {
        switch (columna) {
            case 0 -> celda.setCellValue(item.getColaborador());
            case 1 -> celda.setCellValue(item.getProyecto());
            case 2 -> asignarNumero(celda, item.getHorasSemanales(), estilos.horas);
            case 3 -> celda.setCellValue(item.getAsignadas());
            case 4 -> celda.setCellValue(item.getCompletadas());
            case 5 -> celda.setCellValue(item.getATiempo());
            case 6 -> celda.setCellValue(item.getTardias());
            case 7 -> celda.setCellValue(item.getVencidas());
            case 8 -> celda.setCellValue(item.getDevoluciones());
            case 9 -> asignarNumero(celda, item.getHorasCompletadas(), estilos.horas);
            default -> celda.setCellValue(item.getStrikes());
        }
    }

    private String valorProyectoTexto(PmReporteConsolidadoView.ProyectoFila item, int columna) {
        return switch (columna) {
            case 0 -> item.getNombre();
            case 1 -> item.getEstadoTexto();
            case 2 -> item.getPrioridadTexto();
            case 3 -> formatearDinero(item.getPresupuesto());
            case 4 -> item.getEquipoTexto();
            case 5 -> item.getProgresoPorc() + "%";
            case 6 -> item.getTareasTexto();
            case 7 -> item.getPuntualidadPorc() + "%";
            default -> formatearHoras(item.getHorasCompletadas()) + " / " + formatearHoras(item.getHorasEstimadas());
        };
    }

    private int alineacionProyecto(int columna) {
        return (columna == 0 || columna == 1 || columna == 2) ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT;
    }

    private String valorDetalleTexto(PmReporteConsolidadoView.DetalleColaboradorFila item, int columna) {
        return switch (columna) {
            case 0 -> item.getColaborador();
            case 1 -> item.getProyecto();
            case 2 -> formatearHoras(item.getHorasSemanales()) + "/sem";
            case 3 -> String.valueOf(item.getAsignadas());
            case 4 -> String.valueOf(item.getCompletadas());
            case 5 -> String.valueOf(item.getATiempo());
            case 6 -> String.valueOf(item.getTardias());
            case 7 -> String.valueOf(item.getVencidas());
            case 8 -> String.valueOf(item.getDevoluciones());
            case 9 -> formatearHoras(item.getHorasCompletadas());
            default -> item.getStrikes() + " / 3";
        };
    }

    private int alineacionDetalle(int columna) {
        return (columna == 0 || columna == 1) ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT;
    }

    private String formatearDinero(BigDecimal valor) {
        BigDecimal limpio = valor == null ? BigDecimal.ZERO : valor;
        DecimalFormat df = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return "S/ " + df.format(limpio);
    }

    private String formatearHoras(BigDecimal valor) {
        BigDecimal limpio = valor == null ? BigDecimal.ZERO : valor;
        DecimalFormat df = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return df.format(limpio) + " h";
    }

    private void agregarFilaMetaPdf(PdfPTable tabla, String etiqueta, String valor, EstilosPdf estilos) {
        PdfPCell cEtq = new PdfPCell(new Phrase(etiqueta, estilos.metaEtiqueta));
        cEtq.setBackgroundColor(PDF_HEADER_BG);
        cEtq.setBorderColor(PDF_BORDER);
        cEtq.setPadding(5f);
        tabla.addCell(cEtq);

        PdfPCell cVal = new PdfPCell(new Phrase(valor != null ? valor : "-", estilos.celda));
        cVal.setBorderColor(PDF_BORDER);
        cVal.setPadding(5f);
        tabla.addCell(cVal);
    }

    private void agregarFilaMetaDoblePdf(PdfPTable tabla, String etq1, String val1, String etq2, String val2, EstilosPdf estilos) {
        agregarFilaMetaPdf(tabla, etq1, val1, estilos);
        agregarFilaMetaPdf(tabla, etq2, val2, estilos);
    }

    private void agregarTarjetaKpiPdf(PdfPTable tabla, String etiqueta, String valor, EstilosPdf estilos) {
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(PDF_BORDER);
        cell.setBackgroundColor(PDF_ZEBRA);
        cell.setPadding(7f);
        Paragraph pEtq = new Paragraph(etiqueta.toUpperCase(Locale.ROOT), estilos.kpiEtiqueta);
        pEtq.setSpacingAfter(3f);
        Paragraph pVal = new Paragraph(valor, estilos.kpiValor);
        cell.addElement(pEtq);
        cell.addElement(pVal);
        tabla.addCell(cell);
    }

    private PdfPCell celdaCabeceraPdf(String texto, EstilosPdf estilos) {
        PdfPCell c = new PdfPCell(new Phrase(texto, estilos.cabeceraTabla));
        c.setBackgroundColor(PDF_PRIMARY);
        c.setBorderColor(PDF_BORDER);
        c.setPadding(6f);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return c;
    }

    private PdfPCell celdaDatoPdf(String texto, int alineacion, boolean filaPar, EstilosPdf estilos) {
        PdfPCell c = new PdfPCell(new Phrase(texto != null ? texto : "-", estilos.celda));
        c.setHorizontalAlignment(alineacion);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setBorderColor(PDF_BORDER);
        if (filaPar) c.setBackgroundColor(PDF_ZEBRA);
        c.setPadding(5f);
        return c;
    }

    private List<String> etiquetas(List<String> columnas, List<Integer> indices) {
        return indices.stream().map(columnas::get).toList();
    }

    private List<String> valoresEncabezado(PmReporteConsolidadoView reporte, String generado) {
        return List.of(reporte.getPeriodoTexto(), reporte.getFiltrosTexto(), generado, reporte.getReglaCalculo());
    }

    private String slug(String texto) {
        if (texto == null) return "";
        String limpio = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (limpio.length() > LARGO_SLUG) limpio = limpio.substring(0, LARGO_SLUG).replaceAll("-+$", "");
        return limpio;
    }

    private int escribirEncabezado(Sheet hoja, PmReporteConsolidadoView reporte, String generado,
                                   EstilosExcel estilos, int columnas) {
        Cell titulo = hoja.createRow(0).createCell(0);
        titulo.setCellValue(TITULO);
        titulo.setCellStyle(estilos.titulo);
        combinar(hoja, 0, 0, columnas - 1);
        List<String> valores = valoresEncabezado(reporte, generado);
        int fila = 1;
        for (int indice = 0; indice < valores.size(); indice++, fila++) {
            Row row = hoja.createRow(fila);
            Cell etiqueta = row.createCell(0);
            etiqueta.setCellValue(ETIQUETAS_ENCABEZADO.get(indice));
            etiqueta.setCellStyle(estilos.etiqueta);
            Cell valor = row.createCell(1);
            valor.setCellValue(valores.get(indice));
            valor.setCellStyle(estilos.texto);
            combinar(hoja, fila, 1, columnas - 1);
            int lineas = (int) Math.ceil(valores.get(indice).length() / (double) CARACTERES_POR_LINEA_EXCEL);
            if (lineas > 1) row.setHeightInPoints(hoja.getDefaultRowHeightInPoints() * lineas);
        }
        return fila + 1;
    }

    private Cell escribirMetrica(Sheet hoja, int fila, String etiqueta, EstilosExcel estilos) {
        Row row = hoja.createRow(fila);
        Cell celda = row.createCell(0);
        celda.setCellValue(etiqueta);
        celda.setCellStyle(estilos.etiqueta);
        return row.createCell(1);
    }

    private int escribirSeccion(Sheet hoja, int fila, String titulo, List<String> columnas,
                                EstilosExcel estilos) {
        Cell celda = hoja.createRow(fila).createCell(0);
        celda.setCellValue(titulo);
        celda.setCellStyle(estilos.etiqueta);
        Row cabecera = hoja.createRow(fila + 1);
        for (int indice = 0; indice < columnas.size(); indice++) {
            Cell columna = cabecera.createCell(indice);
            columna.setCellValue(columnas.get(indice));
            columna.setCellStyle(estilos.encabezado);
        }
        return fila + 2;
    }

    private int escribirTexto(Sheet hoja, int fila, String texto, int columnas) {
        Cell celda = hoja.createRow(fila).createCell(0);
        celda.setCellValue(texto);
        combinar(hoja, fila, 0, columnas - 1);
        return fila + 1;
    }

    private void combinar(Sheet hoja, int fila, int desde, int hasta) {
        if (hasta > desde) hoja.addMergedRegion(new CellRangeAddress(fila, fila, desde, hasta));
    }

    private void asignarNumero(Cell celda, BigDecimal valor, CellStyle estilo) {
        celda.setCellValue((valor == null ? BigDecimal.ZERO : valor).doubleValue());
        celda.setCellStyle(estilo);
    }

    private void ajustarColumnas(Sheet hoja, int cantidad) {
        for (int indice = 0; indice < Math.max(2, cantidad); indice++) {
            hoja.autoSizeColumn(indice);
            hoja.setColumnWidth(indice, Math.min(hoja.getColumnWidth(indice) + 700, 12000));
        }
    }

    private static final class EstilosExcel {
        private final CellStyle titulo;
        private final CellStyle etiqueta;
        private final CellStyle texto;
        private final CellStyle encabezado;
        private final CellStyle dinero;
        private final CellStyle horas;

        private EstilosExcel(Workbook libro) {
            org.apache.poi.ss.usermodel.Font fuenteTitulo = libro.createFont();
            fuenteTitulo.setBold(true);
            fuenteTitulo.setFontHeightInPoints((short) 14);
            titulo = libro.createCellStyle();
            titulo.setFont(fuenteTitulo);

            org.apache.poi.ss.usermodel.Font fuenteNegrita = libro.createFont();
            fuenteNegrita.setBold(true);
            etiqueta = libro.createCellStyle();
            etiqueta.setFont(fuenteNegrita);
            etiqueta.setVerticalAlignment(VerticalAlignment.TOP);

            texto = libro.createCellStyle();
            texto.setWrapText(true);
            texto.setVerticalAlignment(VerticalAlignment.TOP);

            encabezado = libro.createCellStyle();
            encabezado.setFont(fuenteNegrita);
            encabezado.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            encabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            DataFormat formato = libro.createDataFormat();
            dinero = libro.createCellStyle();
            dinero.setDataFormat(formato.getFormat(FORMATO_DINERO_EXCEL));

            horas = libro.createCellStyle();
            horas.setDataFormat(formato.getFormat(FORMATO_HORAS_EXCEL));
        }
    }

    private static final class EstilosPdf {
        private final com.lowagie.text.Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14f, new Color(15, 23, 42));
        private final com.lowagie.text.Font subtitulo = FontFactory.getFont(FontFactory.HELVETICA, 9f, PDF_TEXT_MUTED);
        private final com.lowagie.text.Font seccion = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11f, PDF_PRIMARY);
        private final com.lowagie.text.Font metaEtiqueta = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, new Color(30, 41, 59));
        private final com.lowagie.text.Font kpiEtiqueta = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7f, PDF_TEXT_MUTED);
        private final com.lowagie.text.Font kpiValor = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10.5f, new Color(15, 23, 42));
        private final com.lowagie.text.Font cabeceraTabla = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Color.WHITE);
        private final com.lowagie.text.Font celda = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, new Color(30, 41, 59));
        private final com.lowagie.text.Font celdaMuted = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8.5f, PDF_TEXT_MUTED);
        private final com.lowagie.text.Font nota = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7.5f, PDF_TEXT_MUTED);
    }
}
