package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Proyecto;

import java.util.List;

public class ColProyectoDisponibleView {
    private final Proyecto proyecto;
    private final int colaboradoresActivos;
    private final int cuposDisponibles;
    private final List<ColPerfilRequeridoView> perfiles;
    private final boolean yaTieneSolicitud;
    private final boolean tieneHorasSuficientes;
    private final boolean postulacionesAbiertas;

    public ColProyectoDisponibleView(Proyecto proyecto, int colaboradoresActivos, int cuposDisponibles,
                                     List<ColPerfilRequeridoView> perfiles, boolean yaTieneSolicitud,
                                     boolean tieneHorasSuficientes, boolean postulacionesAbiertas) {
        this.proyecto = proyecto;
        this.colaboradoresActivos = colaboradoresActivos;
        this.cuposDisponibles = cuposDisponibles;
        this.perfiles = perfiles;
        this.yaTieneSolicitud = yaTieneSolicitud;
        this.tieneHorasSuficientes = tieneHorasSuficientes;
        this.postulacionesAbiertas = postulacionesAbiertas;
    }

    public Proyecto getProyecto() { return proyecto; }
    public int getColaboradoresActivos() { return colaboradoresActivos; }
    public int getCuposDisponibles() { return cuposDisponibles; }
    public List<ColPerfilRequeridoView> getPerfiles() { return perfiles; }
    public boolean isYaTieneSolicitud() { return yaTieneSolicitud; }
    public boolean isTieneHorasSuficientes() { return tieneHorasSuficientes; }
    public boolean isPostulacionesAbiertas() { return postulacionesAbiertas; }
}