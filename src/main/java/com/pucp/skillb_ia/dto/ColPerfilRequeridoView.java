package com.pucp.skillb_ia.dto;

public class ColPerfilRequeridoView {

    private final Long habilidadId;
    private final String etiqueta;
    private final int cuposDisponibles;

    public ColPerfilRequeridoView(Long habilidadId, String etiqueta, int cuposDisponibles) {
        this.habilidadId = habilidadId;
        this.etiqueta = etiqueta;
        this.cuposDisponibles = cuposDisponibles;
    }

    public Long getHabilidadId() { return habilidadId; }
    public String getEtiqueta() { return etiqueta; }
    public int getCuposDisponibles() { return cuposDisponibles; }
}