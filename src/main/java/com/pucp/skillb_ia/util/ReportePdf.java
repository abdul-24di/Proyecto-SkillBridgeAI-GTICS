package com.pucp.skillb_ia.util;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;


public final class ReportePdf {

    public static final String TIPO_CONTENIDO = "application/pdf";

    private static final Color AZUL = new Color(32, 107, 196);
    private static final Color BORDE = new Color(203, 213, 225);
    private static final Color GRIS = new Color(100, 116, 139);
    private static final Color ZEBRA = new Color(248, 250, 252);
    private static final Color TEXTO = new Color(30, 41, 59);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Font F_TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14f, new Color(15, 23, 42));
    private static final Font F_SUBTITULO = FontFactory.getFont(FontFactory.HELVETICA, 9f, GRIS);
    private static final Font F_SECCION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11f, AZUL);
    private static final Font F_META = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, TEXTO);
    private static final Font F_CABECERA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Color.WHITE);
    private static final Font F_CELDA = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, TEXTO);
    private static final Font F_VACIO = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8.5f, GRIS);

    private ReportePdf() {
    }

    public static String ahora() {
        return LocalDateTime.now().format(FECHA);
    }


    public static byte[] crear(String titulo, String subtitulo, Map<String, String> datos,
                               List<ReporteExcel.Hoja> secciones) {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 28f, 28f, 28f, 28f);
            PdfWriter.getInstance(doc, salida);
            doc.open();

            Paragraph pTitulo = new Paragraph(titulo, F_TITULO);
            pTitulo.setSpacingAfter(4f);
            doc.add(pTitulo);

            Paragraph pSub = new Paragraph(subtitulo, F_SUBTITULO);
            pSub.setSpacingAfter(10f);
            doc.add(pSub);

            if (datos != null && !datos.isEmpty()) {
                PdfPTable meta = new PdfPTable(new float[]{1.8f, 6.2f});
                meta.setWidthPercentage(100f);
                meta.setSpacingAfter(12f);
                for (Map.Entry<String, String> dato : datos.entrySet()) {
                    meta.addCell(celdaMeta(dato.getKey(), F_META));
                    meta.addCell(celdaMeta(dato.getValue(), F_CELDA));
                }
                doc.add(meta);
            }

            for (ReporteExcel.Hoja seccion : secciones) {
                Paragraph pSeccion = new Paragraph(seccion.nombre(), F_SECCION);
                pSeccion.setSpacingBefore(4f);
                pSeccion.setSpacingAfter(6f);
                doc.add(pSeccion);

                int columnas = seccion.encabezados().size();
                PdfPTable tabla = new PdfPTable(calcularAnchos(seccion));
                tabla.setWidthPercentage(100f);
                tabla.setHeaderRows(1);
                tabla.setSpacingAfter(12f);

                for (String encabezado : seccion.encabezados()) {
                    tabla.addCell(celdaCabecera(encabezado));
                }

                if (seccion.filas().isEmpty()) {
                    PdfPCell vacia = new PdfPCell(new Phrase("No hay datos para mostrar.", F_VACIO));
                    vacia.setColspan(columnas);
                    vacia.setPadding(8f);
                    vacia.setBorderColor(BORDE);
                    tabla.addCell(vacia);
                } else {
                    int numeroFila = 0;
                    for (List<Object> fila : seccion.filas()) {
                        boolean par = (numeroFila % 2 == 1);
                        numeroFila++;
                        for (int i = 0; i < columnas; i++) {
                            Object valor = null;
                            if (i < fila.size()) {
                                valor = fila.get(i);
                            }
                            tabla.addCell(celdaDato(valor, par));
                        }
                    }
                }
                doc.add(tabla);
            }

            doc.close();
            return salida.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("No se pudo generar el archivo PDF.", e);
        }
    }


    private static float[] calcularAnchos(ReporteExcel.Hoja hoja) {
        int columnas = hoja.encabezados().size();
        float[] anchos = new float[columnas];
        for (int i = 0; i < columnas; i++) {
            int largo = hoja.encabezados().get(i).length();
            for (List<Object> fila : hoja.filas()) {
                if (i < fila.size()) {
                    int largoCelda = texto(fila.get(i)).length();
                    if (largoCelda > largo) {
                        largo = largoCelda;
                    }
                }
            }
            if (largo > 45) {
                largo = 45;
            }
            if (largo < 6) {
                largo = 6;
            }
            anchos[i] = largo;
        }
        return anchos;
    }

    private static String texto(Object valor) {
        if (valor == null) {
            return "";
        }
        if (valor instanceof BigDecimal decimal) {
            return decimal.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
        if (valor instanceof Double || valor instanceof Float) {
            return BigDecimal.valueOf(((Number) valor).doubleValue()).setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
        return valor.toString();
    }

    private static boolean esNumerico(Object valor) {
        if (valor instanceof Number) {
            return true;
        }
        return valor instanceof String cadena && cadena.matches("(S/ )?[\\d.,]+( h| %)?");
    }

    private static PdfPCell celdaMeta(String texto, Font fuente) {
        PdfPCell celda = new PdfPCell(new Phrase(texto == null ? "" : texto, fuente));
        celda.setBorder(Rectangle.NO_BORDER);
        celda.setPadding(3f);
        return celda;
    }

    private static PdfPCell celdaCabecera(String texto) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, F_CABECERA));
        celda.setBackgroundColor(AZUL);
        celda.setBorderColor(BORDE);
        celda.setPadding(5f);
        return celda;
    }

    private static PdfPCell celdaDato(Object valor, boolean par) {
        PdfPCell celda = new PdfPCell(new Phrase(texto(valor), F_CELDA));
        celda.setHorizontalAlignment(esNumerico(valor) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        celda.setBorderColor(BORDE);
        celda.setPadding(5f);
        if (par) {
            celda.setBackgroundColor(ZEBRA);
        }
        return celda;
    }
}