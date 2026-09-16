package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;

import java.util.List;

public class ColHistorialProyectoView {

    private final Proyecto proyecto;
    private final Asignacion asignacion;
    private final List<String> habilidadesRequeridas;

    public ColHistorialProyectoView(Proyecto proyecto, Asignacion asignacion, List<String> habilidadesRequeridas) {
        this.proyecto = proyecto;
        this.asignacion = asignacion;
        this.habilidadesRequeridas = habilidadesRequeridas;
    }

    public Proyecto getProyecto() {
        return proyecto;
    }

    public Asignacion getAsignacion() {
        return asignacion;
    }

    public List<String> getHabilidadesRequeridas() {
        return habilidadesRequeridas;
    }
}