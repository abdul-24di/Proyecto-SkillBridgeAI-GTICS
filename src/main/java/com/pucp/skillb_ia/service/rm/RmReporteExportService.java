package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmReporteView;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class RmReporteExportService {
    // Etiquetas compartidas por la vista, el PDF y el Excel: un solo lugar para que no se desalineen.
    public static final String TITULO = "Reporte de recursos";
    public static final String SECCION_PROYECTOS = "Comparación por proyecto";
    public static final String SECCION_DETALLE = "Detalle por colaborador";
    public static final List<String> ETIQUETAS_ENCABEZADO = List.of(
            "Periodo", "Filtros aplicados", "Fecha de generación", "Regla de horas trabajadas");
    public static final List<String> METRICAS = List.of(
            "Proyectos incluidos", "Presupuesto asignado", "Horas trabajadas", "Colaboradores involucrados");
    public static final List<String> COLUMNAS_PROYECTO = List.of(
            "Proyecto", "Estado", "Prioridad", "Presupuesto asignado", "Horas trabajadas", "Colaboradores");
    public static final List<String> COLUMNAS_DETALLE = List.of(
            "Colaborador", "Cargo", "Proyecto", "Horas trabajadas", "Tareas completadas");
    public static final String SIN_PROYECTOS = "No hay proyectos que coincidan con los filtros.";
    public static final String SIN_DETALLES = "No hay tareas completadas en este periodo.";
    public static final String NOTA_PRESUPUESTO = "El presupuesto es información del proyecto. "
            + "El reporte no incluye sueldos, bonos ni pagos mensuales.";

    // Formato válido para Microsoft Excel (TASK-045): el literal "S/" va entre comillas.
    private static final String FORMATO_DINERO_EXCEL = "\"S/\" #,##0.00";
    private static final String FORMATO_HORAS_EXCEL = "#,##0.00 \"h\"";
    private static final DateTimeFormatter FORMATO_GENERACION = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int ANCHO_PDF = 104;
    private static final int LARGO_SLUG = 40;
    private static final int CARACTERES_POR_LINEA_EXCEL = 70;

    /** Nombre del archivo sin extensión: periodo y filtros activos con caracteres seguros. */
    public String nombreArchivo(RmReporteView reporte) {
        StringBuilder nombre = new StringBuilder("reporte-recursos-").append(reporte.getPeriodoValor());
        if (reporte.getProyectoSeleccionado() != null) {
            String proyecto = slug(reporte.getProyectoSeleccionadoNombre());
            nombre.append("-proyecto-")
                    .append(proyecto.isEmpty() ? reporte.getProyectoSeleccionado().toString() : proyecto);
        }
        String estado = slug(reporte.getEstadoSeleccionado());
        if (!estado.isEmpty()) nombre.append("-estado-").append(estado);
        return nombre.toString();
    }

    public byte[] crearExcel(RmReporteView reporte) {
        String generado = fechaGeneracion();
        try (Workbook libro = new XSSFWorkbook();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            EstilosExcel estilos = new EstilosExcel(libro);

            Sheet proyectos = libro.createSheet("Proyectos");
            int columnasProyecto = COLUMNAS_PROYECTO.size();
            int fila = escribirEncabezado(proyectos, reporte, generado, estilos, columnasProyecto);
            escribirMetrica(proyectos, fila++, METRICAS.get(0), estilos)
                    .setCellValue(reporte.getTotalProyectos());
            asignarNumero(escribirMetrica(proyectos, fila++, METRICAS.get(1), estilos),
                    reporte.getPresupuestoTotal(), estilos.dinero);
            asignarNumero(escribirMetrica(proyectos, fila++, METRICAS.get(2), estilos),
                    reporte.getHorasTotales(), estilos.horas);
            escribirMetrica(proyectos, fila++, METRICAS.get(3), estilos)
                    .setCellValue(reporte.getColaboradoresInvolucrados());
            fila = escribirSeccion(proyectos, fila + 1, SECCION_PROYECTOS, COLUMNAS_PROYECTO, estilos);
            for (RmReporteView.ProyectoReporte item : reporte.getProyectos()) {
                Row row = proyectos.createRow(fila++);
                row.createCell(0).setCellValue(item.getNombre());
                row.createCell(1).setCellValue(item.getEstadoTexto());
                row.createCell(2).setCellValue(item.getPrioridadTexto());
                asignarNumero(row.createCell(3), item.getPresupuesto(), estilos.dinero);
                asignarNumero(row.createCell(4), item.getHoras(), estilos.horas);
                row.createCell(5).setCellValue(item.getColaboradores());
            }
            if (reporte.getProyectos().isEmpty()) {
                fila = escribirTexto(proyectos, fila, SIN_PROYECTOS, columnasProyecto);
            }
            escribirTexto(proyectos, fila + 1, NOTA_PRESUPUESTO, columnasProyecto);
            ajustarColumnas(proyectos, columnasProyecto);

            Sheet colaboradores = libro.createSheet("Colaboradores");
            int columnasDetalle = COLUMNAS_DETALLE.size();
            int filaDetalle = escribirEncabezado(colaboradores, reporte, generado, estilos, columnasDetalle);
            filaDetalle = escribirSeccion(colaboradores, filaDetalle, SECCION_DETALLE, COLUMNAS_DETALLE, estilos);
            for (RmReporteView.DetalleColaborador item : reporte.getDetalles()) {
                Row row = colaboradores.createRow(filaDetalle++);
                row.createCell(0).setCellValue(item.getColaborador());
                row.createCell(1).setCellValue(item.getCargo());
                row.createCell(2).setCellValue(item.getProyecto());
                asignarNumero(row.createCell(3), item.getHoras(), estilos.horas);
                row.createCell(4).setCellValue(item.getTareas());
            }
            if (reporte.getDetalles().isEmpty()) {
                escribirTexto(colaboradores, filaDetalle, SIN_DETALLES, columnasDetalle);
            }
            ajustarColumnas(colaboradores, columnasDetalle);

            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el archivo Excel.", ex);
        }
    }

    public byte[] crearPdf(RmReporteView reporte) {
        List<String> lineas = new ArrayList<>();
        lineas.add("SkillBridge AI - " + TITULO);
        lineas.add("");
        List<String> valores = valoresEncabezado(reporte, fechaGeneracion());
        for (int indice = 0; indice < valores.size(); indice++) {
            agregarParrafo(lineas, ETIQUETAS_ENCABEZADO.get(indice) + ": " + valores.get(indice));
        }
        lineas.add("");
        lineas.add(METRICAS.get(0) + ": " + reporte.getTotalProyectos());
        lineas.add(METRICAS.get(1) + ": " + dinero(reporte.getPresupuestoTotal()));
        lineas.add(METRICAS.get(2) + ": " + horas(reporte.getHorasTotales()));
        lineas.add(METRICAS.get(3) + ": " + reporte.getColaboradoresInvolucrados());
        lineas.add("");

        String formatoProyecto = "%-30.30s %-12.12s %-9.9s %20.20s %16.16s %13.13s";
        lineas.add(SECCION_PROYECTOS);
        lineas.add(String.format(Locale.ROOT, formatoProyecto, COLUMNAS_PROYECTO.toArray()));
        lineas.add("-".repeat(ANCHO_PDF + 2));
        for (RmReporteView.ProyectoReporte item : reporte.getProyectos()) {
            lineas.add(String.format(Locale.ROOT, formatoProyecto,
                    item.getNombre(), item.getEstadoTexto(), item.getPrioridadTexto(),
                    dinero(item.getPresupuesto()), horas(item.getHoras()),
                    String.valueOf(item.getColaboradores())));
        }
        if (reporte.getProyectos().isEmpty()) lineas.add(SIN_PROYECTOS);
        lineas.add("");

        String formatoDetalle = "%-24.24s %-20.20s %-24.24s %16.16s %18.18s";
        lineas.add(SECCION_DETALLE);
        lineas.add(String.format(Locale.ROOT, formatoDetalle, COLUMNAS_DETALLE.toArray()));
        lineas.add("-".repeat(ANCHO_PDF + 2));
        for (RmReporteView.DetalleColaborador item : reporte.getDetalles()) {
            lineas.add(String.format(Locale.ROOT, formatoDetalle,
                    item.getColaborador(), item.getCargo(), item.getProyecto(),
                    horas(item.getHoras()), String.valueOf(item.getTareas())));
        }
        if (reporte.getDetalles().isEmpty()) lineas.add(SIN_DETALLES);
        lineas.add("");
        agregarParrafo(lineas, NOTA_PRESUPUESTO);
        return PdfBasico.crear(lineas);
    }

    private List<String> valoresEncabezado(RmReporteView reporte, String generado) {
        return List.of(reporte.getPeriodoTexto(), reporte.getFiltrosTexto(), generado, reporte.getReglaHoras());
    }

    private String fechaGeneracion() {
        return LocalDateTime.now().format(FORMATO_GENERACION);
    }

    private String dinero(BigDecimal valor) {
        return "S/ " + String.format(Locale.US, "%,.2f", valor == null ? BigDecimal.ZERO : valor);
    }

    private String horas(BigDecimal valor) {
        return String.format(Locale.US, "%,.2f h", valor == null ? BigDecimal.ZERO : valor);
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

    private void agregarParrafo(List<String> lineas, String texto) {
        StringBuilder actual = new StringBuilder();
        for (String palabra : texto.split(" ")) {
            if (actual.length() > 0 && actual.length() + 1 + palabra.length() > ANCHO_PDF) {
                lineas.add(actual.toString());
                actual.setLength(0);
            }
            if (actual.length() > 0) actual.append(' ');
            actual.append(palabra);
        }
        lineas.add(actual.toString());
    }

    /** Título, periodo, filtros, fecha de generación y regla de horas; devuelve la siguiente fila libre. */
    private int escribirEncabezado(Sheet hoja, RmReporteView reporte, String generado,
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

    /** Escribe la etiqueta de la métrica y devuelve la celda de su valor. */
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
        // autoSizeColumn ignora las celdas combinadas del encabezado, así que el ancho sigue a la tabla.
        for (int indice = 0; indice < cantidad; indice++) {
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
            Font fuenteTitulo = libro.createFont();
            fuenteTitulo.setBold(true);
            fuenteTitulo.setFontHeightInPoints((short) 14);
            titulo = libro.createCellStyle();
            titulo.setFont(fuenteTitulo);

            Font negrita = libro.createFont();
            negrita.setBold(true);
            etiqueta = libro.createCellStyle();
            etiqueta.setFont(negrita);
            etiqueta.setVerticalAlignment(VerticalAlignment.TOP);

            texto = libro.createCellStyle();
            texto.setWrapText(true);
            texto.setVerticalAlignment(VerticalAlignment.TOP);

            Font blanca = libro.createFont();
            blanca.setBold(true);
            blanca.setColor(IndexedColors.WHITE.getIndex());
            encabezado = libro.createCellStyle();
            encabezado.setFont(blanca);
            encabezado.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            encabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            DataFormat formatos = libro.createDataFormat();
            dinero = libro.createCellStyle();
            dinero.setDataFormat(formatos.getFormat(FORMATO_DINERO_EXCEL));
            horas = libro.createCellStyle();
            horas.setDataFormat(formatos.getFormat(FORMATO_HORAS_EXCEL));
        }
    }

    private static final class PdfBasico {
        private static final int LINEAS_POR_PAGINA = 52;

        private static byte[] crear(List<String> lineas) {
            List<List<String>> paginas = new ArrayList<>();
            for (int inicio = 0; inicio < Math.max(1, lineas.size()); inicio += LINEAS_POR_PAGINA) {
                paginas.add(lineas.subList(inicio, Math.min(inicio + LINEAS_POR_PAGINA, lineas.size())));
            }

            int totalObjetos = 3 + paginas.size() * 2;
            byte[][] objetos = new byte[totalObjetos + 1][];
            objetos[1] = bytes("<< /Type /Catalog /Pages 2 0 R >>");
            StringBuilder hijos = new StringBuilder();
            for (int indice = 0; indice < paginas.size(); indice++) {
                hijos.append(4 + indice * 2).append(" 0 R ");
            }
            objetos[2] = bytes("<< /Type /Pages /Kids [" + hijos + "] /Count " + paginas.size() + " >>");
            objetos[3] = bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>");

            for (int indice = 0; indice < paginas.size(); indice++) {
                int paginaId = 4 + indice * 2;
                int contenidoId = paginaId + 1;
                objetos[paginaId] = bytes("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                        + "/Resources << /Font << /F1 3 0 R >> >> /Contents "
                        + contenidoId + " 0 R >>");
                StringBuilder contenido = new StringBuilder("BT\n/F1 8 Tf\n40 805 Td\n12 TL\n");
                for (String linea : paginas.get(indice)) {
                    contenido.append('(').append(escapar(linea)).append(") Tj\nT*\n");
                }
                contenido.append("ET\n");
                byte[] flujo = bytes(contenido.toString());
                objetos[contenidoId] = combinar(
                        bytes("<< /Length " + flujo.length + " >>\nstream\n"),
                        flujo, bytes("endstream"));
            }

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            escribir(salida, bytes("%PDF-1.4\n%âãÏÓ\n"));
            long[] offsets = new long[totalObjetos + 1];
            for (int id = 1; id <= totalObjetos; id++) {
                offsets[id] = salida.size();
                escribir(salida, bytes(id + " 0 obj\n"));
                escribir(salida, objetos[id]);
                escribir(salida, bytes("\nendobj\n"));
            }
            long inicioXref = salida.size();
            escribir(salida, bytes("xref\n0 " + (totalObjetos + 1) + "\n"));
            escribir(salida, bytes("0000000000 65535 f \n"));
            for (int id = 1; id <= totalObjetos; id++) {
                escribir(salida, bytes(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[id])));
            }
            escribir(salida, bytes("trailer\n<< /Size " + (totalObjetos + 1)
                    + " /Root 1 0 R >>\nstartxref\n" + inicioXref + "\n%%EOF"));
            return salida.toByteArray();
        }

        private static String escapar(String texto) {
            return texto.replace('–', '-').replace('—', '-')
                    .replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
        }

        private static byte[] combinar(byte[]... partes) {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            for (byte[] parte : partes) escribir(salida, parte);
            return salida.toByteArray();
        }

        private static byte[] bytes(String texto) {
            return texto.getBytes(StandardCharsets.ISO_8859_1);
        }

        private static void escribir(ByteArrayOutputStream salida, byte[] datos) {
            salida.write(datos, 0, datos.length);
        }
    }
}
