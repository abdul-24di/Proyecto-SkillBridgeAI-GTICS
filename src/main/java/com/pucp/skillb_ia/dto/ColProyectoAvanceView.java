package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Proyecto;

public class ColProyectoAvanceView {

    private final Proyecto proyecto;
    private final int porcentajeAvance;

    public ColProyectoAvanceView(Proyecto proyecto, int porcentajeAvance) {
        this.proyecto = proyecto;
        this.porcentajeAvance = porcentajeAvance;
    }

    public Proyecto getProyecto() { return proyecto; }
    public int getPorcentajeAvance() { return porcentajeAvance; }
}