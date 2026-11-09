package com.pucp.skillb_ia.util;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Paginación en el servidor (tema 5.7 del curso: Page, Pageable y PageRequest).
 * Recibe la lista ya filtrada por el servicio y devuelve solo la página pedida.
 * La vista usa page.content, page.number, page.totalPages y page.totalElements
 * con el fragmento fragments/paginacion.html, sin JavaScript.
 */
public final class Paginador {

    public static final int TAMANIO_PAGINA = 10;

    private Paginador() {
    }

    /** pagina empieza en 1 (como la ve el usuario); se corrige si está fuera de rango. */
    public static <T> Page<T> paginar(List<T> lista, Integer pagina, int tamanio) {
        int total = lista.size();
        int totalPaginas = Math.max(1, (int) Math.ceil(total / (double) tamanio));
        int paginaActual = pagina == null ? 1 : Math.min(Math.max(1, pagina), totalPaginas);

        Pageable pageable = PageRequest.of(paginaActual - 1, tamanio);
        int desde = (paginaActual - 1) * tamanio;
        int hasta = Math.min(desde + tamanio, total);
        List<T> contenido = desde < hasta ? lista.subList(desde, hasta) : List.of();
        return new PageImpl<>(contenido, pageable, total);
    }

    public static <T> Page<T> paginar(List<T> lista, Integer pagina) {
        return paginar(lista, pagina, TAMANIO_PAGINA);
    }

    /** Texto que se compara en los filtros: minúsculas y sin espacios al borde. */
    public static String normalizar(String texto) {
        return texto == null ? "" : texto.trim().toLowerCase();
    }

    /** true si el filtro está vacío o el texto lo contiene (sin importar mayúsculas). */
    public static boolean contiene(String texto, String filtro) {
        String f = normalizar(filtro);
        return f.isEmpty() || normalizar(texto).contains(f);
    }

    /** true si el filtro está vacío / "all" / "Todos" o coincide exactamente. */
    public static boolean coincide(String valor, String filtro) {
        if (filtro == null || filtro.isBlank() || filtro.equalsIgnoreCase("all") || filtro.equalsIgnoreCase("Todos")) {
            return true;
        }
        return filtro.equalsIgnoreCase(valor);
    }
}
