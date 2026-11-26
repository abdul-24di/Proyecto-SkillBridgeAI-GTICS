package com.pucp.skillb_ia.util;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Filtro por mes de los reportes: el parámetro llega como texto "yyyy-MM" (input type="month").
// Vacío, mal escrito o futuro => mes actual.
public final class Periodo {

    private static final DateTimeFormatter ETIQUETA = DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("es", "PE"));

    private Periodo() {
    }

    public static YearMonth leer(String texto) {
        YearMonth actual = YearMonth.now();
        if (texto == null || texto.isBlank()) {
            return actual;
        }
        try {
            YearMonth mes = YearMonth.parse(texto.trim());
            return mes.isAfter(actual) ? actual : mes;
        } catch (DateTimeParseException e) {
            return actual;
        }
    }

    /** "noviembre 2026" */
    public static String etiqueta(YearMonth mes) {
        return mes.format(ETIQUETA);
    }

    /** Los últimos n meses que terminan en el mes indicado, del más antiguo al más reciente. */
    public static List<YearMonth> ultimos(YearMonth hasta, int n) {
        List<YearMonth> meses = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) {
            meses.add(hasta.minusMonths(i));
        }
        return meses;
    }

    public static boolean contiene(YearMonth mes, LocalDate fecha) {
        return fecha != null && YearMonth.from(fecha).equals(mes);
    }
}
