package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmReporteView;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class RmReporteExportService {

    public byte[] crearExcel(RmReporteView reporte) {
        try (Workbook libro = new XSSFWorkbook();
             ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            CellStyle encabezado = estiloEncabezado(libro);
            CellStyle dinero = estiloNumero(libro, "\"S/\" #,##0.00");
            CellStyle horas = estiloNumero(libro, "0.00 \"h\"");

            Sheet proyectos = libro.createSheet("Proyectos");
            int fila = 0;
            Row titulo = proyectos.createRow(fila++);
            titulo.createCell(0).setCellValue("Reporte de recursos - " + reporte.getPeriodoTexto());
            fila++;
            Row metricas = proyectos.createRow(fila++);
            metricas.createCell(0).setCellValue("Proyectos");
            metricas.createCell(1).setCellValue(reporte.getTotalProyectos());
            metricas.createCell(2).setCellValue("Presupuesto asignado");
            asignarNumero(metricas.createCell(3), reporte.getPresupuestoTotal(), dinero);
            metricas.createCell(4).setCellValue("Horas trabajadas");
            asignarNumero(metricas.createCell(5), reporte.getHorasTotales(), horas);
            metricas.createCell(6).setCellValue("Colaboradores");
            metricas.createCell(7).setCellValue(reporte.getColaboradoresInvolucrados());
            fila++;

            Row cabecera = proyectos.createRow(fila++);
            String[] columnasProyecto = {"Proyecto", "Estado", "Prioridad",
                    "Presupuesto asignado", "Horas trabajadas", "Colaboradores"};
            crearCabecera(cabecera, columnasProyecto, encabezado);
            for (RmReporteView.ProyectoReporte item : reporte.getProyectos()) {
                Row row = proyectos.createRow(fila++);
                row.createCell(0).setCellValue(item.getNombre());
                row.createCell(1).setCellValue(item.getEstadoTexto());
                row.createCell(2).setCellValue(item.getPrioridadTexto());
                asignarNumero(row.createCell(3), item.getPresupuesto(), dinero);
                asignarNumero(row.createCell(4), item.getHoras(), horas);
                row.createCell(5).setCellValue(item.getColaboradores());
            }
            ajustarColumnas(proyectos, columnasProyecto.length);

            Sheet colaboradores = libro.createSheet("Colaboradores");
            Row cabeceraDetalle = colaboradores.createRow(0);
            String[] columnasDetalle = {"Colaborador", "Cargo", "Proyecto",
                    "Horas trabajadas", "Tareas completadas"};
            crearCabecera(cabeceraDetalle, columnasDetalle, encabezado);
            int filaDetalle = 1;
            for (RmReporteView.DetalleColaborador item : reporte.getDetalles()) {
                Row row = colaboradores.createRow(filaDetalle++);
                row.createCell(0).setCellValue(item.getColaborador());
                row.createCell(1).setCellValue(item.getCargo());
                row.createCell(2).setCellValue(item.getProyecto());
                asignarNumero(row.createCell(3), item.getHoras(), horas);
                row.createCell(4).setCellValue(item.getTareas());
            }
            ajustarColumnas(colaboradores, columnasDetalle.length);

            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo generar el archivo Excel.", ex);
        }
    }

    public byte[] crearPdf(RmReporteView reporte) {
        List<String> lineas = new ArrayList<>();
        lineas.add("SKILLBRIDGE AI - REPORTE DE RECURSOS");
        lineas.add("Periodo: " + reporte.getPeriodoTexto());
        lineas.add(String.format(Locale.ROOT,
                "Proyectos: %d   Presupuesto: S/ %s   Horas: %s   Colaboradores: %d",
                reporte.getTotalProyectos(), numero(reporte.getPresupuestoTotal()),
                numero(reporte.getHorasTotales()), reporte.getColaboradoresInvolucrados()));
        lineas.add("");
        lineas.add(String.format("%-28s %-13s %-10s %12s %9s", "PROYECTO", "ESTADO",
                "PRIORIDAD", "PRESUPUESTO", "HORAS"));
        lineas.add("----------------------------------------------------------------------------");
        for (RmReporteView.ProyectoReporte item : reporte.getProyectos()) {
            lineas.add(String.format(Locale.ROOT, "%-28.28s %-13.13s %-10.10s %12s %9s",
                    item.getNombre(), item.getEstadoTexto(), item.getPrioridadTexto(),
                    numero(item.getPresupuesto()), numero(item.getHoras())));
        }
        if (reporte.getProyectos().isEmpty()) lineas.add("No hay proyectos para los filtros seleccionados.");
        lineas.add("");
        lineas.add("DETALLE DE HORAS POR COLABORADOR");
        lineas.add(String.format("%-24s %-24s %10s %7s", "COLABORADOR", "PROYECTO", "HORAS", "TAREAS"));
        lineas.add("----------------------------------------------------------------------------");
        for (RmReporteView.DetalleColaborador item : reporte.getDetalles()) {
            lineas.add(String.format(Locale.ROOT, "%-24.24s %-24.24s %10s %7d",
                    item.getColaborador(), item.getProyecto(), numero(item.getHoras()), item.getTareas()));
        }
        if (reporte.getDetalles().isEmpty()) lineas.add("No hay actividades completadas en el periodo.");
        lineas.add("");
        lineas.add("Las horas corresponden a actividades completadas durante el periodo.");
        lineas.add("El reporte no incluye sueldo, bonos ni pagos mensuales.");
        return PdfBasico.crear(lineas);
    }

    private CellStyle estiloEncabezado(Workbook libro) {
        CellStyle estilo = libro.createCellStyle();
        Font fuente = libro.createFont();
        fuente.setBold(true);
        fuente.setColor(IndexedColors.WHITE.getIndex());
        estilo.setFont(fuente);
        estilo.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return estilo;
    }

    private CellStyle estiloNumero(Workbook libro, String formato) {
        CellStyle estilo = libro.createCellStyle();
        estilo.setDataFormat(libro.createDataFormat().getFormat(formato));
        return estilo;
    }

    private void crearCabecera(Row fila, String[] columnas, CellStyle estilo) {
        for (int indice = 0; indice < columnas.length; indice++) {
            Cell celda = fila.createCell(indice);
            celda.setCellValue(columnas[indice]);
            celda.setCellStyle(estilo);
        }
    }

    private void asignarNumero(Cell celda, BigDecimal valor, CellStyle estilo) {
        celda.setCellValue(valor.doubleValue());
        celda.setCellStyle(estilo);
    }

    private void ajustarColumnas(Sheet hoja, int cantidad) {
        for (int indice = 0; indice < cantidad; indice++) {
            hoja.autoSizeColumn(indice);
            hoja.setColumnWidth(indice, Math.min(hoja.getColumnWidth(indice) + 700, 12000));
        }
        hoja.createFreezePane(0, hoja.getSheetName().equals("Proyectos") ? 5 : 1);
    }

    private String numero(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString();
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
