package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmReporteView;
import com.pucp.skillb_ia.util.ReporteExcel;
import com.pucp.skillb_ia.util.ReportePdf;
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
import java.util.*;
import java.util.stream.IntStream;

@Service
public class RmReporteExportService {
    // Etiquetas compartidas por la vista y el Excel: un solo lugar para que no se desalineen.
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
    public static final String HOJA_PROYECTOS = "proyectos";
    public static final String HOJA_COLABORADORES = "colaboradores";

    // Formato válido para Microsoft Excel (TASK-045): el literal "S/" va entre comillas.
    private static final String FORMATO_DINERO_EXCEL = "\"S/\" #,##0.00";
    private static final String FORMATO_HORAS_EXCEL = "#,##0.00 \"h\"";
    private static final DateTimeFormatter FORMATO_GENERACION = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int LARGO_SLUG = 40;
    private static final int CARACTERES_POR_LINEA_EXCEL = 70;

    /**
     * Qué incluye el Excel: resumen (métricas), hojas y columnas (índices de {@link #COLUMNAS_PROYECTO}
     * y {@link #COLUMNAS_DETALLE}, en ese orden). La primera columna de cada hoja siempre se incluye.
     */
    public record OpcionesExcel(boolean resumen, boolean hojaProyectos, boolean hojaColaboradores,
                                List<Integer> columnasProyecto, List<Integer> columnasDetalle) {
        public static OpcionesExcel completo() {
            return new OpcionesExcel(true, true, true, todas(COLUMNAS_PROYECTO), todas(COLUMNAS_DETALLE));
        }

        /**
         * Sin {@code personalizado} (enlaces antiguos o descarga directa) se exporta todo. Con él, las
         * casillas desmarcadas no llegan: resumen ausente = sin métricas e índices inválidos se ignoran.
         */
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

    /** Nombre del archivo sin extensión: periodo y filtros activos con caracteres seguros. */
    public String nombreArchivo(RmReporteView reporte) {
        StringBuilder nombre = new StringBuilder("reporte-recursos-").append(reporte.getPeriodoValor());
        if (reporte.esRango()) nombre.append("-a-").append(reporte.getPeriodoHastaValor());
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

    public byte[] crearExcel(RmReporteView reporte) {
        return crearExcel(reporte, OpcionesExcel.completo());
    }

    public byte[] crearExcel(RmReporteView reporte, OpcionesExcel opciones) {
        String generado = fechaGeneracion();
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
                    asignarNumero(escribirMetrica(proyectos, fila++, METRICAS.get(2), estilos),
                            reporte.getHorasTotales(), estilos.horas);
                    escribirMetrica(proyectos, fila++, METRICAS.get(3), estilos)
                            .setCellValue(reporte.getColaboradoresInvolucrados());
                    fila++;
                }
                fila = escribirSeccion(proyectos, fila, SECCION_PROYECTOS,
                        etiquetas(COLUMNAS_PROYECTO, columnas), estilos);
                for (RmReporteView.ProyectoReporte item : reporte.getProyectos()) {
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
                for (RmReporteView.DetalleColaborador item : reporte.getDetalles()) {
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

    public byte[] crearPdf(RmReporteView reporte) {
        return crearPdf(reporte, OpcionesExcel.completo());
    }

    public byte[] crearPdf(RmReporteView reporte, OpcionesExcel opciones) {
        Map<String, String> datos = new LinkedHashMap<>();
        List<String> valores = valoresEncabezado(reporte, fechaGeneracion());
        for (int i = 0; i < ETIQUETAS_ENCABEZADO.size(); i++) {
            datos.put(ETIQUETAS_ENCABEZADO.get(i), valores.get(i));
        }
        if (opciones.hojaProyectos() && opciones.columnasProyecto().contains(3)) {
            datos.put("Nota", NOTA_PRESUPUESTO);
        }

        List<ReporteExcel.Hoja> secciones = new ArrayList<>();

        if (opciones.resumen()) {
            List<List<Object>> resumen = new ArrayList<>();
            resumen.add(Arrays.<Object>asList(METRICAS.get(0), reporte.getTotalProyectos()));
            resumen.add(Arrays.<Object>asList(METRICAS.get(1), dineroPdf(reporte.getPresupuestoTotal())));
            resumen.add(Arrays.<Object>asList(METRICAS.get(2), horasPdf(reporte.getHorasTotales())));
            resumen.add(Arrays.<Object>asList(METRICAS.get(3), reporte.getColaboradoresInvolucrados()));
            secciones.add(new ReporteExcel.Hoja("Resumen", List.of("Indicador", "Valor"), resumen));
        }

        if (opciones.hojaProyectos()) {
            List<Integer> columnas = opciones.columnasProyecto();
            List<List<Object>> filas = new ArrayList<>();
            for (RmReporteView.ProyectoReporte item : reporte.getProyectos()) {
                List<Object> fila = new ArrayList<>();
                for (int columna : columnas) {
                    fila.add(valorProyectoPdf(item, columna));
                }
                filas.add(fila);
            }
            secciones.add(new ReporteExcel.Hoja(SECCION_PROYECTOS, etiquetas(COLUMNAS_PROYECTO, columnas), filas));
        }

        if (opciones.hojaColaboradores()) {
            List<Integer> columnas = opciones.columnasDetalle();
            List<List<Object>> filas = new ArrayList<>();
            for (RmReporteView.DetalleColaborador item : reporte.getDetalles()) {
                List<Object> fila = new ArrayList<>();
                for (int columna : columnas) {
                    fila.add(valorDetallePdf(item, columna));
                }
                filas.add(fila);
            }
            secciones.add(new ReporteExcel.Hoja(SECCION_DETALLE, etiquetas(COLUMNAS_DETALLE, columnas), filas));
        }

        return ReportePdf.crear(TITULO, "SkillBridge AI — Panel del Resource Manager", datos, secciones);
    }

    private Object valorProyectoPdf(RmReporteView.ProyectoReporte item, int columna) {
        switch (columna) {
            case 0: return item.getNombre();
            case 1: return item.getEstadoTexto();
            case 2: return item.getPrioridadTexto();
            case 3: return dineroPdf(item.getPresupuesto());
            case 4: return horasPdf(item.getHoras());
            default: return item.getColaboradores();
        }
    }

    private Object valorDetallePdf(RmReporteView.DetalleColaborador item, int columna) {
        switch (columna) {
            case 0: return item.getColaborador();
            case 1: return item.getCargo();
            case 2: return item.getProyecto();
            case 3: return horasPdf(item.getHoras());
            default: return item.getTareas();
        }
    }

    private String dineroPdf(BigDecimal valor) {
        BigDecimal monto = (valor == null) ? BigDecimal.ZERO : valor;
        return "S/ " + String.format(Locale.US, "%,.2f", monto);
    }

    private String horasPdf(BigDecimal valor) {
        BigDecimal horas = (valor == null) ? BigDecimal.ZERO : valor;
        return String.format(Locale.US, "%,.2f", horas) + " h";
    }

    /** Índice de {@link #COLUMNAS_PROYECTO}: montos y horas como números con formato. */
    private void escribirProyecto(Cell celda, RmReporteView.ProyectoReporte item, int columna,
                                  EstilosExcel estilos) {
        switch (columna) {
            case 0 -> celda.setCellValue(item.getNombre());
            case 1 -> celda.setCellValue(item.getEstadoTexto());
            case 2 -> celda.setCellValue(item.getPrioridadTexto());
            case 3 -> asignarNumero(celda, item.getPresupuesto(), estilos.dinero);
            case 4 -> asignarNumero(celda, item.getHoras(), estilos.horas);
            default -> celda.setCellValue(item.getColaboradores());
        }
    }

    /** Índice de {@link #COLUMNAS_DETALLE}. */
    private void escribirDetalle(Cell celda, RmReporteView.DetalleColaborador item, int columna,
                                 EstilosExcel estilos) {
        switch (columna) {
            case 0 -> celda.setCellValue(item.getColaborador());
            case 1 -> celda.setCellValue(item.getCargo());
            case 2 -> celda.setCellValue(item.getProyecto());
            case 3 -> asignarNumero(celda, item.getHoras(), estilos.horas);
            default -> celda.setCellValue(item.getTareas());
        }
    }

    private List<String> etiquetas(List<String> columnas, List<Integer> indices) {
        return indices.stream().map(columnas::get).toList();
    }

    private List<String> valoresEncabezado(RmReporteView reporte, String generado) {
        return List.of(reporte.getPeriodoTexto(), reporte.getFiltrosTexto(), generado, reporte.getReglaHoras());
    }

    private String fechaGeneracion() {
        return LocalDateTime.now().format(FORMATO_GENERACION);
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

    // ============================================================
    // HORAS DE COLABORADORES (Excel y PDF con las mismas dos hojas)
    // ============================================================

    // colaboradores: el conjunto completo según los filtros de la pantalla, no solo la página visible.
    public byte[] crearExcelHoras(RmReporteView reporte, List<RmReporteView.ColaboradorReporte> colaboradores) {
        return ReporteExcel.crear("Horas de colaboradores — " + reporte.getPeriodoTexto(),
                datosHoras(reporte, colaboradores), hojasHoras(colaboradores));
    }

    public byte[] crearPdfHoras(RmReporteView reporte, List<RmReporteView.ColaboradorReporte> colaboradores) {
        return ReportePdf.crear("Horas de colaboradores — " + reporte.getPeriodoTexto(),
                "SkillBridge AI — Reporte del Resource Manager", datosHoras(reporte, colaboradores),
                hojasHoras(colaboradores));
    }

    public String nombreArchivoHoras(RmReporteView reporte) {
        return "horas-colaboradores-" + reporte.getPeriodoValor();
    }

    private Map<String, String> datosHoras(RmReporteView reporte, List<RmReporteView.ColaboradorReporte> colaboradores) {
        BigDecimal total = colaboradores.stream().map(RmReporteView.ColaboradorReporte::getHoras)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long enReferencia = colaboradores.stream().filter(RmReporteView.ColaboradorReporte::isAlcanzaReferencia).count();
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("Periodo", reporte.getPeriodoTexto());
        if (reporte.getProyectoSeleccionadoNombre() != null) {
            datos.put("Proyecto", reporte.getProyectoSeleccionadoNombre());
        }
        datos.put("Colaboradores", String.valueOf(colaboradores.size()));
        datos.put("Horas acumuladas", total.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + " h");
        datos.put("En la referencia de 160 h", enReferencia + " de " + colaboradores.size());
        datos.put("Fecha de generación", ReportePdf.ahora());
        return datos;
    }

    private List<ReporteExcel.Hoja> hojasHoras(List<RmReporteView.ColaboradorReporte> colaboradores) {
        List<List<Object>> resumen = new ArrayList<>();
        List<List<Object>> desglose = new ArrayList<>();
        for (RmReporteView.ColaboradorReporte c : colaboradores) {
            resumen.add(List.of(c.getNombre(), c.getCargo() == null ? "—" : c.getCargo(), c.getHoras(),
                    c.getTareas(), c.getCantidadProyectos(), c.isAlcanzaReferencia() ? "Sí" : "No"));
            for (RmReporteView.DesgloseProyecto p : c.getProyectos()) {
                desglose.add(List.of(c.getNombre(), p.getProyecto(), p.getHoras(), p.getTareas()));
            }
        }
        return List.of(
                new ReporteExcel.Hoja("Colaboradores",
                        List.of("Colaborador", "Rol", "Horas del mes", "Tareas completadas", "Proyectos",
                                "Alcanza 160 h"), resumen),
                new ReporteExcel.Hoja("Desglose por proyecto",
                        List.of("Colaborador", "Proyecto", "Horas", "Tareas completadas"), desglose));
    }
}
