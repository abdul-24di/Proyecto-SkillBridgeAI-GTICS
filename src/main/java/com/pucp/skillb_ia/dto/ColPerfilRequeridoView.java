package com.pucp.skillb_ia.dto;

import java.math.BigDecimal;

public class ColPerfilRequeridoView {

    private final Long habilidadId;
    private final String etiqueta;
    private final int cuposDisponibles;
    private final BigDecimal horasSemanales;

    public ColPerfilRequeridoView(Long habilidadId, String etiqueta, int cuposDisponibles, BigDecimal horasSemanales) {
        this.habilidadId = habilidadId;
        this.etiqueta = etiqueta;
        this.cuposDisponibles = cuposDisponibles;
        this.horasSemanales = horasSemanales;
    }

    public Long getHabilidadId() { return habilidadId; }
    public String getEtiqueta() { return etiqueta; }
    public int getCuposDisponibles() { return cuposDisponibles; }
    public BigDecimal getHorasSemanales() { return horasSemanales; }
}