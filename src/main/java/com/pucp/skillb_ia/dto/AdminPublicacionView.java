package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.PublicacionForo;
import com.pucp.skillb_ia.model.RespuestaForo;

import java.util.List;

public class AdminPublicacionView {
    private final PublicacionForo publicacion;
    private final List<RespuestaForo> respuestas;

    public AdminPublicacionView(PublicacionForo publicacion, List<RespuestaForo> respuestas) {
        this.publicacion = publicacion;
        this.respuestas = respuestas;
    }

    public PublicacionForo getPublicacion() { return publicacion; }
    public List<RespuestaForo> getRespuestas() { return respuestas; }
}
