package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.service.rm.RmPresupuestoService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class RmProyectoView {
    private final Proyecto proyecto;
    private final String pmNombre;
    private final String estadoTexto;
    private final String prioridadTexto;
    private final int integrantesActivos;
    private final int vacantes;
    private final int pendientesRm;
    private final List<MiembroEquipo> equipo;
    private final List<RequisitoTalento> requisitos;
    private final RmPresupuestoService.ResumenPresupuesto resumenPresupuesto;

    public RmProyectoView(Proyecto proyecto, String pmNombre, String estadoTexto, String prioridadTexto,
                          int integrantesActivos, int vacantes, int pendientesRm,
                          List<MiembroEquipo> equipo, List<RequisitoTalento> requisitos,
                          RmPresupuestoService.ResumenPresupuesto resumenPresupuesto) {
        this.proyecto = proyecto;
        this.pmNombre = pmNombre;
        this.estadoTexto = estadoTexto;
        this.prioridadTexto = prioridadTexto;
        this.integrantesActivos = integrantesActivos;
        this.vacantes = vacantes;
        this.pendientesRm = pendientesRm;
        this.equipo = List.copyOf(equipo);
        this.requisitos = List.copyOf(requisitos);
        this.resumenPresupuesto = resumenPresupuesto;
    }

    public RmPresupuestoService.ResumenPresupuesto getResumenPresupuesto() { return resumenPresupuesto; }

    public Proyecto getProyecto() { return proyecto; }
    public String getPmNombre() { return pmNombre; }
    public String getEstadoTexto() { return estadoTexto; }
    public String getPrioridadTexto() { return prioridadTexto; }
    public int getIntegrantesActivos() { return integrantesActivos; }
    public int getVacantes() { return vacantes; }
    public int getPendientesRm() { return pendientesRm; }
    public List<MiembroEquipo> getEquipo() { return equipo; }
    public List<RequisitoTalento> getRequisitos() { return requisitos; }

    public String getRequisitosJson() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < requisitos.size(); i++) {
            RequisitoTalento r = requisitos.get(i);
            sb.append("{")
              .append("\"habilidad\":\"").append(r.habilidad.replace("\"", "\\\"")).append("\",")
              .append("\"nivel\":\"").append(r.nivel.replace("\"", "\\\"")).append("\",")
              .append("\"horas\":").append(r.horasSemanales != null ? r.horasSemanales : "null").append(",")
              .append("\"cantidad\":").append(r.cantidadPersonas)
              .append("}");
            if (i < requisitos.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    public String getEstadoClase() {
        return switch (proyecto.getEstado()) {
            case ACTIVO -> "bg-green-lt";
            case EN_REVISION -> "bg-yellow-lt";
            case RECHAZADO -> "bg-red-lt";
            case EN_ESPERA -> "bg-orange-lt";
            case FINALIZADO -> "bg-blue-lt";
            case CANCELADO -> "bg-secondary-lt";
        };
    }

    public String getPrioridadClase() {
        return switch (proyecto.getPrioridad()) {
            case ALTA -> "bg-red-lt text-red";
            case MEDIA -> "bg-yellow-lt text-yellow";
            case BAJA -> "bg-blue-lt text-blue";
        };
    }

    public BigDecimal getPresupuestoVisible() {
        return proyecto.getPresupuesto() != null ? proyecto.getPresupuesto() : proyecto.getPresupuestoSolicitado();
    }

    public String getPresupuestoEtiqueta() {
        return proyecto.getPresupuesto() != null ? "Presupuesto asignado" : "Presupuesto solicitado";
    }

    public String getTextoBusqueda() {
        return proyecto.getNombre() + " " + pmNombre;
    }

    public boolean isConVacantesParaDotacion() {
        return vacantes > 0 && switch (proyecto.getEstado()) {
            case EN_REVISION, ACTIVO, EN_ESPERA -> true;
            case RECHAZADO, CANCELADO, FINALIZADO -> false;
        };
    }

    public static class MiembroEquipo {
        private final Long id;
        private final String nombre;
        private final String iniciales;
        private final String cargo;
        private final String nivel;
        private final BigDecimal horasSemanales;
        private final BigDecimal costoSemanal;
        private final BigDecimal costoTotal;
        private final boolean costoCalculable;

        public MiembroEquipo(Long id, String nombre, String iniciales, String cargo,
                             String nivel, BigDecimal horasSemanales,
                             BigDecimal costoSemanal, BigDecimal costoTotal, boolean costoCalculable) {
            this.id = id;
            this.nombre = nombre;
            this.iniciales = iniciales;
            this.cargo = cargo;
            this.nivel = nivel;
            this.horasSemanales = horasSemanales;
            this.costoSemanal = costoSemanal;
            this.costoTotal = costoTotal;
            this.costoCalculable = costoCalculable;
        }

        public Long getId() { return id; }
        public String getNombre() { return nombre; }
        public String getIniciales() { return iniciales; }
        public String getCargo() { return cargo; }
        public String getNivel() { return nivel; }
        public BigDecimal getHorasSemanales() { return horasSemanales; }
        public BigDecimal getCostoSemanal() { return costoSemanal; }
        public BigDecimal getCostoTotal() { return costoTotal; }
        public boolean isCostoCalculable() { return costoCalculable; }
    }

    public static class RequisitoTalento {
        private final String habilidad;
        private final String nivel;
        private final int cantidadPersonas;
        private final java.math.BigDecimal horasSemanales;

        public RequisitoTalento(String habilidad, String nivel, int cantidadPersonas, java.math.BigDecimal horasSemanales) {
            this.habilidad = habilidad;
            this.nivel = nivel;
            this.cantidadPersonas = cantidadPersonas;
            this.horasSemanales = horasSemanales;
        }

        public String getHabilidad() { return habilidad; }
        public String getNivel() { return nivel; }
        public int getCantidadPersonas() { return cantidadPersonas; }
        public java.math.BigDecimal getHorasSemanales() { return horasSemanales; }
    }
}
