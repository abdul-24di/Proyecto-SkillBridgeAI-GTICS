package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;

import java.util.List;

/**
 * Elemento del historial de proyectos del colaborador (sección de perfil profesional).
 * <p>
 * Agrupa un proyecto en el que el colaborador ya participó (asignación {@code FINALIZADA}),
 * la asignación correspondiente y los nombres de las habilidades que ese proyecto requería.
 *
 * @see com.pucp.skillb_ia.service.col.ColaboradorProyectoService#listarHistorialProyectos
 */
public class ColHistorialProyectoView {

    private final Proyecto proyecto;
    private final Asignacion asignacion;
    private final List<String> habilidadesRequeridas;

    /**
     * @param proyecto proyecto en el que participó el colaborador
     * @param asignacion asignación finalizada del colaborador a ese proyecto
     * @param habilidadesRequeridas nombres de las habilidades que pedía el proyecto
     */
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