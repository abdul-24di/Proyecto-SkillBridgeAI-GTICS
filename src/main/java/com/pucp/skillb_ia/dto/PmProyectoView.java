package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Proyecto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class PmProyectoView {

    private final Proyecto proyecto;
    private final String estadoTexto;
    private final String prioridadTexto;
    private final int integrantesActivos;
    private final int pendientesPm;      // asignaciones propuestas por RM esperando OK del PM
    private final int totalActividades;
    private final int actividadesCompletadas;
    private final List<RequisitoHabilidad> requisitos;

    public PmProyectoView(Proyecto proyecto, String estadoTexto, String prioridadTexto,
                          int integrantesActivos, int pendientesPm,
                          int totalActividades, int actividadesCompletadas,
                          List<RequisitoHabilidad> requisitos) {
        this.proyecto = proyecto;
        this.estadoTexto = estadoTexto;
        this.prioridadTexto = prioridadTexto;
        this.integrantesActivos = integrantesActivos;
        this.pendientesPm = pendientesPm;
        this.totalActividades = totalActividades;
        this.actividadesCompletadas = actividadesCompletadas;
        this.requisitos = List.copyOf(requisitos);
    }

    public Proyecto getProyecto() { return proyecto; }
    public String getEstadoTexto() { return estadoTexto; }
    public String getPrioridadTexto() { return prioridadTexto; }
    public int getIntegrantesActivos() { return integrantesActivos; }
    public int getVacantes() { return Math.max(0, proyecto.getColaboradoresRequeridos() - integrantesActivos); }
    public int getPendientesPm() { return pendientesPm; }
    public int getTotalActividades() { return totalActividades; }
    public int getActividadesCompletadas() { return actividadesCompletadas; }
    public List<RequisitoHabilidad> getRequisitos() { return requisitos; }

    public int getProgresoActividades() {
        if (totalActividades == 0) return 0;
        return (int) Math.round((actividadesCompletadas * 100.0) / totalActividades);
    }

    public String getEstadoClase() {
        return switch (proyecto.getEstado()) {
            case ACTIVO     -> "bg-green-lt text-green";
            case EN_REVISION -> "bg-yellow-lt text-yellow";
            case RECHAZADO  -> "bg-red-lt text-red";
            case EN_ESPERA  -> "bg-orange-lt text-orange";
            case FINALIZADO -> "bg-blue-lt text-blue";
            case CANCELADO  -> "bg-secondary-lt text-secondary";
        };
    }

    public String getPrioridadClase() {
        return switch (proyecto.getPrioridad()) {
            case ALTA  -> "bg-red-lt text-red";
            case MEDIA -> "bg-yellow-lt text-yellow";
            case BAJA  -> "bg-blue-lt text-blue";
        };
    }

    public boolean isPuedeCrearActividades() {
        return switch (proyecto.getEstado()) {
            case ACTIVO, EN_ESPERA -> true;
            default -> false;
        };
    }

    public boolean isPuedeCancelar() {
        return switch (proyecto.getEstado()) {
            case EN_REVISION, EN_ESPERA -> true;
            default -> false;
        };
    }

    public BigDecimal getPresupuestoVisible() {
        return proyecto.getPresupuesto() != null
                ? proyecto.getPresupuesto()
                : proyecto.getPresupuestoSolicitado();
    }

    public String getPresupuestoEtiqueta() {
        return proyecto.getPresupuesto() != null ? "Presupuesto asignado" : "Presupuesto solicitado";
    }

    public static class RequisitoHabilidad {
        private final Long habilidadId;
        private final String habilidad;
        private final String nivel;
        private final int cantidadPersonas;

        public RequisitoHabilidad(Long habilidadId, String habilidad, String nivel, int cantidadPersonas) {
            this.habilidadId = habilidadId;
            this.habilidad = habilidad;
            this.nivel = nivel;
            this.cantidadPersonas = cantidadPersonas;
        }

        public Long getHabilidadId() { return habilidadId; }
        public String getHabilidad() { return habilidad; }
        public String getNivel() { return nivel; }
        public int getCantidadPersonas() { return cantidadPersonas; }
    }
}
