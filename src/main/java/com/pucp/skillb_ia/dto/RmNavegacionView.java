package com.pucp.skillb_ia.dto;

import java.util.List;

/**
 * Ruta de navegación (migas) y enlace de regreso de una vista secundaria del RM (TASK-026).
 * Las URL se arman en el servidor con identificadores de las entidades; nunca con valores del navegador.
 */
public class RmNavegacionView {

    private final String origen;
    private final List<Miga> migas;
    private final String actual;
    private final String volverUrl;
    private final String volverTexto;

    public RmNavegacionView(String origen, List<Miga> migas, String actual, String volverUrl, String volverTexto) {
        this.origen = origen;
        this.migas = migas;
        this.actual = actual;
        this.volverUrl = volverUrl;
        this.volverTexto = volverTexto;
    }

    /** Origen cerrado reconocido, o {@code null} cuando se usa la ruta canónica. */
    public String getOrigen() { return origen; }
    public List<Miga> getMigas() { return migas; }
    public String getActual() { return actual; }
    public String getVolverUrl() { return volverUrl; }
    public String getVolverTexto() { return volverTexto; }

    public static class Miga {
        private final String texto;
        private final String url;

        public Miga(String texto, String url) {
            this.texto = texto;
            this.url = url;
        }

        public String getTexto() { return texto; }
        public String getUrl() { return url; }
    }
}
