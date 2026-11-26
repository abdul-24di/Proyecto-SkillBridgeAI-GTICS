package com.pucp.skillb_ia.util;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utilidades para paginar listas en memoria y para aplicar filtros de texto sencillos
 * en las vistas de listado.
 * <p>
 * La paginación se hace sobre una {@link List} ya cargada (no en la base de datos), y el
 * resultado se devuelve como un {@link Page} de Spring Data para poder reutilizarlo con
 * los componentes de paginación de las vistas.
 * <p>
 * Es una clase de utilidad: todos sus métodos son estáticos y no se puede instanciar.
 */
public final class Paginador {

    /** Cantidad de elementos por página que se usa por defecto. */
    public static final int TAMANIO_PAGINA = 10;

    private Paginador() {
    }

    /**
     * Obtiene una página de una lista en memoria.
     * <p>
     * El número de página se recibe tal como lo ve el usuario (empieza en 1). Si es nulo
     * se usa la página 1, y si está fuera de rango se corrige al límite más cercano
     * (nunca menor que 1 ni mayor que la última página). Una lista vacía produce una
     * única página sin contenido.
     *
     * @param <T>     tipo de los elementos de la lista
     * @param lista   lista completa a paginar; no debe ser {@code null}
     * @param pagina  número de página solicitado, empezando en 1; puede ser {@code null}
     * @param tamanio cantidad de elementos por página; debe ser mayor que 0
     * @return la página solicitada, con el total de elementos de la lista original
     * @throws IllegalArgumentException si {@code tamanio} es menor que 1
     */

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

    /**
     * Obtiene una página de una lista en memoria usando el tamaño por defecto
     * ({@link #TAMANIO_PAGINA}).
     *
     * @param <T>    tipo de los elementos de la lista
     * @param lista  lista completa a paginar; no debe ser {@code null}
     * @param pagina número de página solicitado, empezando en 1; puede ser {@code null}
     * @return la página solicitada
     * @see #paginar(List, Integer, int)
     */
    public static <T> Page<T> paginar(List<T> lista, Integer pagina) {
        return paginar(lista, pagina, TAMANIO_PAGINA);
    }

    /**
     * Normaliza un texto para compararlo en los filtros: lo pasa a minúsculas y le quita
     * los espacios del inicio y del final.
     *
     * @param texto texto a normalizar; puede ser {@code null}
     * @return el texto normalizado, o una cadena vacía si es {@code null}
     */
    public static String normalizar(String texto) {
        return texto == null ? "" : texto.trim().toLowerCase();
    }

    /**
     * Indica si un texto contiene el filtro, sin distinguir mayúsculas ni espacios en los
     * bordes. Un filtro nulo o vacío siempre coincide.
     *
     * @param texto  texto donde se busca; puede ser {@code null} (se trata como vacío)
     * @param filtro texto a buscar; puede ser {@code null}
     * @return {@code true} si el filtro está vacío o el texto lo contiene
     */
    public static boolean contiene(String texto, String filtro) {
        String f = normalizar(filtro);
        return f.isEmpty() || normalizar(texto).contains(f);
    }

    /**
     * Indica si un valor coincide con el filtro de un select o desplegable.
     * <p>
     * Coincide siempre cuando el filtro es nulo, está en blanco o vale {@code "all"} o
     * {@code "Todos"} (sin distinguir mayúsculas). En otro caso, exige igualdad exacta
     * con el valor, ignorando mayúsculas.
     *
     * @param valor  valor del elemento que se está evaluando; puede ser {@code null}
     * @param filtro opción elegida en el filtro; puede ser {@code null}
     * @return {@code true} si no hay filtro efectivo o si el valor es igual al filtro
     */
    public static boolean coincide(String valor, String filtro) {
        if (filtro == null || filtro.isBlank() || filtro.equalsIgnoreCase("all") || filtro.equalsIgnoreCase("Todos")) {
            return true;
        }
        return filtro.equalsIgnoreCase(valor);
    }

    /**
     * Igual que {@link #coincide(String, String)}, pero sin distinguir tildes: "Tecnico" coincide con "Técnico".
     *
     * @param valor  valor del elemento que se está evaluando; puede ser {@code null}
     * @param filtro opción elegida en el filtro; puede ser {@code null}
     * @return {@code true} si no hay filtro efectivo o si ambos textos son iguales sin tildes
     */
    public static boolean coincideSinTildes(String valor, String filtro) {
        return coincide(sinTildes(valor), filtro == null ? null : sinTildes(filtro));
    }

    /**
     * Opciones de un desplegable sin repetidos: si la misma aparece con y sin tilde, queda la que lleva tilde.
     *
     * @param valores valores tal como están en la base; los nulos y vacíos se descartan
     * @return las opciones en orden alfabético
     */
    public static List<String> opcionesUnicas(List<String> valores) {
        Map<String, String> opciones = new LinkedHashMap<>();
        for (String valor : valores) {
            if (valor == null || valor.isBlank()) {
                continue;
            }
            String clave = sinTildes(valor);
            boolean llevaTilde = !normalizar(valor).equals(clave);
            if (llevaTilde || !opciones.containsKey(clave)) {
                opciones.put(clave, valor.trim());
            }
        }
        return opciones.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList();
    }

    private static String sinTildes(String texto) {
        return Normalizer.normalize(normalizar(texto), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
