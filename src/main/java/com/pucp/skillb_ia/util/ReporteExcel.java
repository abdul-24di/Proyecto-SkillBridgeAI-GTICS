package com.pucp.skillb_ia.util;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;


public final class ReporteExcel {

    // Una hoja del libro: nombre de la pestaña, encabezados de columnas y filas (texto o números).
    public record Hoja(String nombre, List<String> encabezados, List<List<Object>> filas) {
    }

    public static final String TIPO_CONTENIDO = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private ReporteExcel() {
    }


    public static byte[] crear(List<Hoja> hojas) {
        return crear(null, null, hojas);
    }


    public static byte[] crear(String titulo, Map<String, String> datos, List<Hoja> hojas) {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Estilos estilos = new Estilos(libro);

            for (Hoja hoja : hojas) {
                Sheet sheet = libro.createSheet(hoja.nombre());
                int columnas = hoja.encabezados().size();
                int ancho = Math.max(2, columnas);
                int fila = 0;

                if (titulo != null) {
                    Cell celdaTitulo = sheet.createRow(fila).createCell(0);
                    celdaTitulo.setCellValue(titulo);
                    celdaTitulo.setCellStyle(estilos.titulo);
                    combinar(sheet, fila, 0, ancho - 1);
                    fila++;

                    if (datos != null) {
                        for (Map.Entry<String, String> dato : datos.entrySet()) {
                            Row filaDato = sheet.createRow(fila);
                            Cell etiqueta = filaDato.createCell(0);
                            etiqueta.setCellValue(dato.getKey());
                            etiqueta.setCellStyle(estilos.etiqueta);
                            Cell valor = filaDato.createCell(1);
                            valor.setCellValue(dato.getValue() == null ? "" : dato.getValue());
                            valor.setCellStyle(estilos.valor);
                            combinar(sheet, fila, 1, ancho - 1);
                            fila++;
                        }
                    }
                    fila++;
                }

                int filaCabecera = fila;
                Row cabecera = sheet.createRow(fila);
                fila++;
                for (int i = 0; i < columnas; i++) {
                    Cell celda = cabecera.createCell(i);
                    celda.setCellValue(hoja.encabezados().get(i));
                    celda.setCellStyle(estilos.cabecera);
                }

                if (hoja.filas().isEmpty()) {
                    Cell vacia = sheet.createRow(fila).createCell(0);
                    vacia.setCellValue("No hay datos para mostrar.");
                    vacia.setCellStyle(estilos.vacio);
                    combinar(sheet, fila, 0, ancho - 1);
                    fila++;
                } else {
                    int numeroFila = 0;
                    for (List<Object> valores : hoja.filas()) {
                        int par = numeroFila % 2; // 0 = fila blanca, 1 = fila gris
                        numeroFila++;
                        Row row = sheet.createRow(fila);
                        fila++;
                        for (int i = 0; i < valores.size(); i++) {
                            Object valor = valores.get(i);
                            Cell celda = row.createCell(i);
                            if (valor instanceof BigDecimal || valor instanceof Double || valor instanceof Float) {
                                celda.setCellValue(((Number) valor).doubleValue());
                                celda.setCellStyle(estilos.decimal[par]);
                            } else if (valor instanceof Number) {
                                celda.setCellValue(((Number) valor).doubleValue());
                                celda.setCellStyle(estilos.entero[par]);
                            } else {
                                celda.setCellValue(valor == null ? "" : valor.toString());
                                celda.setCellStyle(estilos.texto[par]);
                            }
                        }
                    }

                    sheet.setAutoFilter(new CellRangeAddress(filaCabecera, fila - 1, 0, columnas - 1));
                }


                sheet.createFreezePane(0, filaCabecera + 1);

                for (int i = 0; i < ancho; i++) {
                    sheet.autoSizeColumn(i);
                    int medida = sheet.getColumnWidth(i) + 700;
                    if (medida > 12000) {
                        medida = 12000;
                    }
                    if (medida < 3500) {
                        medida = 3500;
                    }
                    sheet.setColumnWidth(i, medida);
                }
            }
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el Excel.", e);
        }
    }

    private static void combinar(Sheet hoja, int fila, int desde, int hasta) {
        if (hasta > desde) {
            hoja.addMergedRegion(new CellRangeAddress(fila, fila, desde, hasta));
        }
    }


    private static final class Estilos {
        private final XSSFCellStyle titulo;
        private final XSSFCellStyle etiqueta;
        private final XSSFCellStyle valor;
        private final XSSFCellStyle cabecera;
        private final XSSFCellStyle vacio;
        private final XSSFCellStyle[] texto = new XSSFCellStyle[2];
        private final XSSFCellStyle[] entero = new XSSFCellStyle[2];
        private final XSSFCellStyle[] decimal = new XSSFCellStyle[2];

        private Estilos(XSSFWorkbook libro) {
            XSSFColor azul = new XSSFColor(new Color(32, 107, 196), null);
            XSSFColor gris = new XSSFColor(new Color(248, 250, 252), null);
            XSSFColor borde = new XSSFColor(new Color(203, 213, 225), null);

            XSSFFont fuenteTitulo = libro.createFont();
            fuenteTitulo.setBold(true);
            fuenteTitulo.setFontHeightInPoints((short) 14);
            fuenteTitulo.setColor(new XSSFColor(new Color(15, 23, 42), null));

            XSSFFont negrita = libro.createFont();
            negrita.setBold(true);

            XSSFFont blanca = libro.createFont();
            blanca.setBold(true);
            blanca.setColor(new XSSFColor(Color.WHITE, null));

            XSSFFont cursiva = libro.createFont();
            cursiva.setItalic(true);
            cursiva.setColor(new XSSFColor(new Color(100, 116, 139), null));

            titulo = libro.createCellStyle();
            titulo.setFont(fuenteTitulo);

            etiqueta = libro.createCellStyle();
            etiqueta.setFont(negrita);
            etiqueta.setVerticalAlignment(VerticalAlignment.TOP);

            valor = libro.createCellStyle();
            valor.setWrapText(true);
            valor.setVerticalAlignment(VerticalAlignment.TOP);

            cabecera = libro.createCellStyle();
            cabecera.setFont(blanca);
            cabecera.setFillForegroundColor(azul);
            cabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cabecera.setAlignment(HorizontalAlignment.CENTER);
            cabecera.setVerticalAlignment(VerticalAlignment.CENTER);
            cabecera.setWrapText(true);
            conBorde(cabecera, borde);

            vacio = libro.createCellStyle();
            vacio.setFont(cursiva);

            for (int par = 0; par < 2; par++) {
                texto[par] = estiloDato(libro, par == 1, gris, borde, HorizontalAlignment.LEFT, null);
                texto[par].setWrapText(true);
                entero[par] = estiloDato(libro, par == 1, gris, borde, HorizontalAlignment.RIGHT, "#,##0");
                decimal[par] = estiloDato(libro, par == 1, gris, borde, HorizontalAlignment.RIGHT, "#,##0.00");
            }
        }

        private static XSSFCellStyle estiloDato(XSSFWorkbook libro, boolean filaGris, XSSFColor gris, XSSFColor borde,
                                                HorizontalAlignment alineacion, String formato) {
            XSSFCellStyle estilo = libro.createCellStyle();
            estilo.setAlignment(alineacion);
            estilo.setVerticalAlignment(VerticalAlignment.CENTER);
            if (filaGris) {
                estilo.setFillForegroundColor(gris);
                estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            if (formato != null) {
                estilo.setDataFormat(libro.createDataFormat().getFormat(formato));
            }
            conBorde(estilo, borde);
            return estilo;
        }

        private static void conBorde(XSSFCellStyle estilo, XSSFColor color) {
            estilo.setBorderTop(BorderStyle.THIN);
            estilo.setBorderBottom(BorderStyle.THIN);
            estilo.setBorderLeft(BorderStyle.THIN);
            estilo.setBorderRight(BorderStyle.THIN);
            estilo.setTopBorderColor(color);
            estilo.setBottomBorderColor(color);
            estilo.setLeftBorderColor(color);
            estilo.setRightBorderColor(color);
        }
    }
}