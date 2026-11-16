package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.dto.PmReporteConsolidadoView;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

@Service
public class PmReporteExportService {

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
    private static final int LARGO_SLUG = 40;
    private static final int CARACTERES_POR_LINEA_EXCEL = 70;

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

    public byte[] crearExcel(PmReporteConsolidadoView reporte) {
        return crearExcel(reporte, OpcionesExcel.completo());
    }

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
            Font fuenteTitulo = libro.createFont();
            fuenteTitulo.setBold(true);
            fuenteTitulo.setFontHeightInPoints((short) 14);
            titulo = libro.createCellStyle();
            titulo.setFont(fuenteTitulo);

            Font fuenteNegrita = libro.createFont();
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
}
