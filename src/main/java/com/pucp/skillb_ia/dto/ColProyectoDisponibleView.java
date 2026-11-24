package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Proyecto;

import java.util.List;

/**
 * Tarjeta de un proyecto en la pantalla "Explorar proyectos", ya calculada para un colaborador
 * en concreto.
 * <p>
 * Además del proyecto, indica cuántos colaboradores activos tiene, cuántos cupos quedan,
 * los perfiles que pide y tres banderas que la vista usa para habilitar o bloquear el botón
 * de solicitar:
 * <ul>
 *   <li>{@code yaTieneSolicitud}: el colaborador ya tiene una asignación {@code PENDIENTE}
 *       o {@code ACTIVA} en ese proyecto.</li>
 *   <li>{@code tieneHorasSuficientes}: existe al menos un perfil con cupo cuyas horas
 *       semanales caben en las horas disponibles del colaborador.</li>
 *   <li>{@code postulacionesAbiertas}: el proyecto está {@code ACTIVO} y al menos un perfil
 *       tiene cupo.</li>
 * </ul>
 *
 * @see com.pucp.skillb_ia.service.col.ColaboradorProyectoService#listarTodosLosProyectos
 */
public class ColProyectoDisponibleView {
    private final Proyecto proyecto;
    private final int colaboradoresActivos;
    private final int cuposDisponibles;
    private final List<ColPerfilRequeridoView> perfiles;
    private final boolean yaTieneSolicitud;
    private final boolean tieneHorasSuficientes;
    private final boolean postulacionesAbiertas;

    /**
     * @param proyecto proyecto a mostrar
     * @param colaboradoresActivos cantidad de asignaciones {@code ACTIVAS} del proyecto
     * @param cuposDisponibles colaboradores requeridos por el proyecto menos los activos
     * @param perfiles perfiles (habilidades) que pide el proyecto
     * @param yaTieneSolicitud si el colaborador ya tiene una asignación pendiente o activa
     * @param tieneHorasSuficientes si le alcanzan las horas disponibles para algún perfil con cupo
     * @param postulacionesAbiertas si el proyecto está activo y algún perfil tiene cupo
     */
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