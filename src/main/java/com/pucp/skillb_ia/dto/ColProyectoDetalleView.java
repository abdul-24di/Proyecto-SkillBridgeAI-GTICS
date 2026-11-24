package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.service.col.ColaboradorExplorarService;

import java.util.List;
import java.util.Map;

/**
 * Todos los datos de la pantalla de detalle de un proyecto del colaborador.
 * <p>
 * Contiene el proyecto, la asignación del colaborador, los integrantes (con su perfil),
 * las actividades de todo el proyecto y las propias, y los strikes que lleva el colaborador
 * en ese proyecto. Las actividades llegan paginadas, por eso se guardan también la página
 * actual, el total de páginas y el total de registros de cada lista ("todas" y "mías").
 * <p>
 * Un <b>strike</b> es una penalización de tipo {@code ACTIVIDAD_TARDIA}.
 *
 * @see com.pucp.skillb_ia.service.col.ColaboradorProyectoService#obtenerDetalleProyecto
 */
public class ColProyectoDetalleView {

    private final Proyecto proyecto;
    private final Asignacion asignacion;
    private final List<Asignacion> integrantes;
    private final List<Actividad> actividadesDelProyecto;
    private final List<Actividad> misActividades;
    private final Map<Long, ColaboradorExplorarService.PerfilExplorar> perfilesIntegrantes;
    private final int misStrikes;


    private final int paginaActualTodas;
    private final int totalPaginasTodas;
    private final long totalActividadesProyecto;
    private final int paginaActualMias;
    private final int totalPaginasMias;
    private final long totalMisActividades;

    /**
     * @param proyecto proyecto consultado
     * @param asignacion asignación del colaborador a ese proyecto
     * @param integrantes asignaciones de los demás miembros del proyecto
     * @param actividadesDelProyecto página actual de actividades de todo el proyecto
     * @param misActividades página actual de las actividades del colaborador
     * @param perfilesIntegrantes perfil público de cada integrante, indexado por id de usuario
     * @param misStrikes strikes del colaborador en este proyecto
     * @param paginaActualTodas página actual de la lista de todas las actividades
     * @param totalPaginasTodas total de páginas de esa lista
     * @param totalActividadesProyecto total de actividades del proyecto
     * @param paginaActualMias página actual de la lista de mis actividades
     * @param totalPaginasMias total de páginas de esa lista
     * @param totalMisActividades total de actividades del colaborador en el proyecto
     */
    public ColProyectoDetalleView(Proyecto proyecto, Asignacion asignacion, List<Asignacion> integrantes,
                                  List<Actividad> actividadesDelProyecto, List<Actividad> misActividades,
                                  Map<Long, ColaboradorExplorarService.PerfilExplorar> perfilesIntegrantes,
                                  int misStrikes,
                                  int paginaActualTodas, int totalPaginasTodas, long totalActividadesProyecto,
                                  int paginaActualMias, int totalPaginasMias, long totalMisActividades) {
        this.proyecto = proyecto;
        this.asignacion = asignacion;
        this.integrantes = integrantes;
        this.actividadesDelProyecto = actividadesDelProyecto;
        this.misActividades = misActividades;
        this.perfilesIntegrantes = perfilesIntegrantes;
        this.misStrikes = misStrikes;
        this.paginaActualTodas = paginaActualTodas;
        this.totalPaginasTodas = totalPaginasTodas;
        this.totalActividadesProyecto = totalActividadesProyecto;
        this.paginaActualMias = paginaActualMias;
        this.totalPaginasMias = totalPaginasMias;
        this.totalMisActividades = totalMisActividades;
    }

    public Proyecto getProyecto() { return proyecto; }
    public Asignacion getAsignacion() { return asignacion; }
    public List<Asignacion> getIntegrantes() { return integrantes; }
    public List<Actividad> getActividadesDelProyecto() { return actividadesDelProyecto; }
    public List<Actividad> getMisActividades() { return misActividades; }
    public Map<Long, ColaboradorExplorarService.PerfilExplorar> getPerfilesIntegrantes() { return perfilesIntegrantes; }
    public int getMisStrikes() { return misStrikes; }

    public int getPaginaActualTodas() { return paginaActualTodas; }
    public int getTotalPaginasTodas() { return totalPaginasTodas; }
    public long getTotalActividadesProyecto() { return totalActividadesProyecto; }
    public int getPaginaActualMias() { return paginaActualMias; }
    public int getTotalPaginasMias() { return totalPaginasMias; }
    public long getTotalMisActividades() { return totalMisActividades; }
}