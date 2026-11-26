package com.pucp.skillb_ia.security;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * Los foros guardan HTML del editor Quill y las vistas lo muestran con th:utext, sin escapar. Por eso, antes
 * de guardarlo, se deja solo el formato permitido: desaparecen &lt;script&gt;, onerror=, javascript:, etc.
 */
public final class HtmlSeguro {

    private static final String BASE = "http://localhost/";

    private static final Safelist PERMITIDO = Safelist.relaxed()
            .addAttributes(":all", "class") // Quill alinea y sangra el texto con clases ql-*
            .preserveRelativeLinks(true);   // las imágenes locales se guardan como /uploads/foro/...

    /** Clase de utilidad: no se instancia. */
    private HtmlSeguro() {
    }

    /**
     * Quita del HTML todo lo que no esté permitido.
     *
     * @param html HTML escrito por el usuario
     * @return HTML seguro; {@code null} si se recibe {@code null}
     */
    public static String limpiar(String html) {
        return html == null ? null : Jsoup.clean(html, BASE, PERMITIDO);
    }
}
