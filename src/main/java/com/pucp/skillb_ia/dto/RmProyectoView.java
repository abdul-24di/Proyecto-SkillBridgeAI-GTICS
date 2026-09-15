package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Proyecto;

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

    public RmProyectoView(Proyecto proyecto, String pmNombre, String estadoTexto, String prioridadTexto,
                          int integrantesActivos, int vacantes, int pendientesRm,
                          List<MiembroEquipo> equipo, List<RequisitoTalento> requisitos) {
        this.proyecto = proyecto;
        this.pmNombre = pmNombre;
        this.estadoTexto = estadoTexto;
        this.prioridadTexto = prioridadTexto;
        this.integrantesActivos = integrantesActivos;
        this.vacantes = vacantes;
        this.pendientesRm = pendientesRm;
        this.equipo = List.copyOf(equipo);
        this.requisitos = List.copyOf(requisitos);
    }

    public Proyecto getProyecto() { return proyecto; }
    public String getPmNombre() { return pmNombre; }
    public String getEstadoTexto() { return estadoTexto; }
    public String getPrioridadTexto() { return prioridadTexto; }
    public int getIntegrantesActivos() { return integrantesActivos; }
    public int getVacantes() { return vacantes; }
    public int getPendientesRm() { return pendientesRm; }
    public List<MiembroEquipo> getEquipo() { return equipo; }
    public List<RequisitoTalento> getRequisitos() { return requisitos; }

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

        public MiembroEquipo(Long id, String nombre, String iniciales, String cargo,
                             String nivel, BigDecimal horasSemanales) {
            this.id = id;
            this.nombre = nombre;
            this.iniciales = iniciales;
            this.cargo = cargo;
            this.nivel = nivel;
            this.horasSemanales = horasSemanales;
        }

        public Long getId() { return id; }
        public String getNombre() { return nombre; }
        public String getIniciales() { return iniciales; }
        public String getCargo() { return cargo; }
        public String getNivel() { return nivel; }
        public BigDecimal getHorasSemanales() { return horasSemanales; }
    }

    public static class RequisitoTalento {
        private final String habilidad;
        private final String nivel;
        private final int cantidadPersonas;

        public RequisitoTalento(String habilidad, String nivel, int cantidadPersonas) {
            this.habilidad = habilidad;
            this.nivel = nivel;
            this.cantidadPersonas = cantidadPersonas;
        }

        public String getHabilidad() { return habilidad; }
        public String getNivel() { return nivel; }
        public int getCantidadPersonas() { return cantidadPersonas; }
    }
}
