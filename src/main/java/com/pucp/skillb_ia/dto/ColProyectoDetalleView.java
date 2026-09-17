package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;

import java.util.List;

public class ColProyectoDetalleView {

    private final Proyecto proyecto;
    private final Asignacion asignacion;
    private final List<Asignacion> integrantes;
    private final List<Actividad> misActividades;

    public ColProyectoDetalleView(Proyecto proyecto, Asignacion asignacion,
                                  List<Asignacion> integrantes, List<Actividad> misActividades) {
        this.proyecto = proyecto;
        this.asignacion = asignacion;
        this.integrantes = integrantes;
        this.misActividades = misActividades;
    }

    public Proyecto getProyecto() { return proyecto; }
    public Asignacion getAsignacion() { return asignacion; }
    public List<Asignacion> getIntegrantes() { return integrantes; }
    public List<Actividad> getMisActividades() { return misActividades; }
}