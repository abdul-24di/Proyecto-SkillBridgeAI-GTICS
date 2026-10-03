package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.service.col.ColaboradorExplorarService;

import java.util.List;
import java.util.Map;

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