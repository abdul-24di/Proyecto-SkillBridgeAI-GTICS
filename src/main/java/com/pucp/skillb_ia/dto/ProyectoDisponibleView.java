package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Proyecto;

import java.util.List;

public class ProyectoDisponibleView {
    private final Proyecto proyecto;
    private final int colaboradoresActivos;
    private final int cuposDisponibles;
    private final List<String> habilidadesRequeridas;
    private final boolean yaTieneSolicitud;

    public ProyectoDisponibleView(Proyecto proyecto, int colaboradoresActivos, int cuposDisponibles,
                                  List<String> habilidadesRequeridas, boolean yaTieneSolicitud) {
        this.proyecto = proyecto;
        this.colaboradoresActivos = colaboradoresActivos;
        this.cuposDisponibles = cuposDisponibles;
        this.habilidadesRequeridas = habilidadesRequeridas;
        this.yaTieneSolicitud = yaTieneSolicitud;
    }

    public Proyecto getProyecto() { return proyecto; }
    public int getColaboradoresActivos() { return colaboradoresActivos; }
    public int getCuposDisponibles() { return cuposDisponibles; }
    public List<String> getHabilidadesRequeridas() { return habilidadesRequeridas; }
    public boolean isYaTieneSolicitud() { return yaTieneSolicitud; }



}
