package com.pucp.skillb_ia.util;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;

// Arma un Excel (.xlsx) con Apache POI a partir de hojas con encabezados y filas. Lo usan los reportes del
// Admin, del PM y del colaborador, con la misma técnica que la exportación de la auditoría.
public final class ReporteExcel {

    // Una hoja del libro: nombre de la pestaña, encabezados de columnas y filas (texto o números).
    public record Hoja(String nombre, List<String> encabezados, List<List<Object>> filas) {
    }

    public static final String TIPO_CONTENIDO = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private ReporteExcel() {
    }

    public static byte[] crear(List<Hoja> hojas) {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            CellStyle negrita = libro.createCellStyle();
            Font fuente = libro.createFont();
            fuente.setBold(true);
            negrita.setFont(fuente);

            for (Hoja hoja : hojas) {
                Sheet sheet = libro.createSheet(hoja.nombre());
                Row encabezado = sheet.createRow(0);
                for (int i = 0; i < hoja.encabezados().size(); i++) {
                    encabezado.createCell(i).setCellValue(hoja.encabezados().get(i));
                    encabezado.getCell(i).setCellStyle(negrita);
                }
                int numeroFila = 1;
                for (List<Object> valores : hoja.filas()) {
                    Row fila = sheet.createRow(numeroFila++);
                    for (int i = 0; i < valores.size(); i++) {
                        Object valor = valores.get(i);
                        if (valor instanceof Number numero) {
                            fila.createCell(i).setCellValue(numero instanceof BigDecimal decimal
                                    ? decimal.doubleValue() : numero.doubleValue());
                        } else {
                            fila.createCell(i).setCellValue(valor == null ? "" : valor.toString());
                        }
                    }
                }
                for (int i = 0; i < hoja.encabezados().size(); i++) {
                    sheet.autoSizeColumn(i);
                }
            }
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el Excel.", e);
        }
    }
}
