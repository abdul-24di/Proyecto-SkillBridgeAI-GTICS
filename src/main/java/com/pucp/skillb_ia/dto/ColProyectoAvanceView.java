package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Proyecto;

/**
 * Proyecto activo del colaborador junto con su porcentaje de avance personal.
 * <p>
 * El avance es: actividades {@code COMPLETADAS} del colaborador en ese proyecto entre el total
 * de sus actividades en él (0 si no tiene ninguna). Se muestra en el dashboard.
 *
 * @see com.pucp.skillb_ia.service.col.ColaboradorActividadService#calcularAvance
 */
public class ColProyectoAvanceView {

    private final Proyecto proyecto;
    private final int porcentajeAvance;

    /**
     * @param proyecto proyecto activo del colaborador
     * @param porcentajeAvance avance de 0 a 100
     */
    public ColProyectoAvanceView(Proyecto proyecto, int porcentajeAvance) {
        this.proyecto = proyecto;
        this.porcentajeAvance = porcentajeAvance;
    }

    public Proyecto getProyecto() { return proyecto; }
    public int getPorcentajeAvance() { return porcentajeAvance; }
}