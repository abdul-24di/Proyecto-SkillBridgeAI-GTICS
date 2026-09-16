package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Proyecto;

import java.util.List;

public class ColProyectoDisponibleView {
    private final Proyecto proyecto;
    private final int colaboradoresActivos;
    private final int cuposDisponibles;
    private final List<String> habilidadesRequeridas;
    private final boolean yaTieneSolicitud;
    private final boolean tieneHorasSuficientes;

    public ColProyectoDisponibleView(Proyecto proyecto, int colaboradoresActivos, int cuposDisponibles,
                                     List<String> habilidadesRequeridas, boolean yaTieneSolicitud,
                                     boolean tieneHorasSuficientes) {
        this.proyecto = proyecto;
        this.colaboradoresActivos = colaboradoresActivos;
        this.cuposDisponibles = cuposDisponibles;
        this.habilidadesRequeridas = habilidadesRequeridas;
        this.yaTieneSolicitud = yaTieneSolicitud;
        this.tieneHorasSuficientes = tieneHorasSuficientes;
    }

    public Proyecto getProyecto() { return proyecto; }
    public int getColaboradoresActivos() { return colaboradoresActivos; }
    public int getCuposDisponibles() { return cuposDisponibles; }
    public List<String> getHabilidadesRequeridas() { return habilidadesRequeridas; }
    public boolean isYaTieneSolicitud() { return yaTieneSolicitud; }
    public boolean isTieneHorasSuficientes() { return tieneHorasSuficientes; }
}