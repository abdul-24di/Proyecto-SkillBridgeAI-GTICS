package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Proyecto;

import java.math.BigDecimal;
import java.util.List;

/** Vista de reporte de un proyecto para el PM (solo lectura de métricas). */
public class PmReporteView {

    private final Proyecto proyecto;
    private final int totalActividades;
    private final int actividadesPendientes;
    private final int actividadesEnProgreso;
    private final int actividadesEnRevision;
    private final int actividadesCompletadas;
    private final int integrantesActivos;
    private final BigDecimal horasTotalesEstimadas;
    private final List<ColaboradorMetrica> colaboradores;

    public PmReporteView(Proyecto proyecto,
                         int totalActividades,
                         int actividadesPendientes,
                         int actividadesEnProgreso,
                         int actividadesEnRevision,
                         int actividadesCompletadas,
                         int integrantesActivos,
                         BigDecimal horasTotalesEstimadas,
                         List<ColaboradorMetrica> colaboradores) {
        this.proyecto = proyecto;
        this.totalActividades = totalActividades;
        this.actividadesPendientes = actividadesPendientes;
        this.actividadesEnProgreso = actividadesEnProgreso;
        this.actividadesEnRevision = actividadesEnRevision;
        this.actividadesCompletadas = actividadesCompletadas;
        this.integrantesActivos = integrantesActivos;
        this.horasTotalesEstimadas = horasTotalesEstimadas;
        this.colaboradores = List.copyOf(colaboradores);
    }

    public Proyecto getProyecto() { return proyecto; }
    public int getTotalActividades() { return totalActividades; }
    public int getActividadesPendientes() { return actividadesPendientes; }
    public int getActividadesEnProgreso() { return actividadesEnProgreso; }
    public int getActividadesEnRevision() { return actividadesEnRevision; }
    public int getActividadesCompletadas() { return actividadesCompletadas; }
    public int getIntegrantesActivos() { return integrantesActivos; }
    public BigDecimal getHorasTotalesEstimadas() { return horasTotalesEstimadas; }
    public List<ColaboradorMetrica> getColaboradores() { return colaboradores; }

    public int getProgresoPorc() {
        if (totalActividades == 0) return 0;
        return (int) Math.round((actividadesCompletadas * 100.0) / totalActividades);
    }

    public BigDecimal getPresupuestoVisible() {
        return proyecto.getPresupuesto() != null
                ? proyecto.getPresupuesto()
                : proyecto.getPresupuestoSolicitado();
    }

    public static class ColaboradorMetrica {
        private final String nombre;
        private final String iniciales;
        private final BigDecimal horasSemanales;
        private final int actividadesAsignadas;
        private final int actividadesCompletadas;

        public ColaboradorMetrica(String nombre, String iniciales,
                                  BigDecimal horasSemanales,
                                  int actividadesAsignadas, int actividadesCompletadas) {
            this.nombre = nombre;
            this.iniciales = iniciales;
            this.horasSemanales = horasSemanales;
            this.actividadesAsignadas = actividadesAsignadas;
            this.actividadesCompletadas = actividadesCompletadas;
        }

        public String getNombre() { return nombre; }
        public String getIniciales() { return iniciales; }
        public BigDecimal getHorasSemanales() { return horasSemanales; }
        public int getActividadesAsignadas() { return actividadesAsignadas; }
        public int getActividadesCompletadas() { return actividadesCompletadas; }

        public int getRendimientoPorc() {
            if (actividadesAsignadas == 0) return 0;
            return (int) Math.round((actividadesCompletadas * 100.0) / actividadesAsignadas);
        }
    }
}
