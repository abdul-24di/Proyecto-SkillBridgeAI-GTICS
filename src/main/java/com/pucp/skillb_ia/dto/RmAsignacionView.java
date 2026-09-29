package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;

import java.math.BigDecimal;
import java.util.List;

public class RmAsignacionView {

    private final Asignacion asignacion;
    private final String colaboradorNombre;
    private final String colaboradorIniciales;
    private final String projectManagerNombre;
    private final int asignacionesActivas;
    private final int maxAsignaciones;
    private final BigDecimal horasComprometidas;
    private final int equipoActual;
    private final int vacantes;
    private final List<String> habilidades;

    public RmAsignacionView(
            Asignacion asignacion,
            String colaboradorNombre,
            String colaboradorIniciales,
            String projectManagerNombre,
            int asignacionesActivas,
            int maxAsignaciones,
            BigDecimal horasComprometidas,
            int equipoActual,
            int vacantes,
            List<String> habilidades) {
        this.asignacion = asignacion;
        this.colaboradorNombre = colaboradorNombre;
        this.colaboradorIniciales = colaboradorIniciales;
        this.projectManagerNombre = projectManagerNombre;
        this.asignacionesActivas = asignacionesActivas;
        this.maxAsignaciones = maxAsignaciones;
        this.horasComprometidas = horasComprometidas;
        this.equipoActual = equipoActual;
        this.vacantes = vacantes;
        this.habilidades = List.copyOf(habilidades);
    }

    public Asignacion getAsignacion() { return asignacion; }
    public String getColaboradorNombre() { return colaboradorNombre; }
    public String getColaboradorIniciales() { return colaboradorIniciales; }
    public String getProjectManagerNombre() { return projectManagerNombre; }
    public int getAsignacionesActivas() { return asignacionesActivas; }
    public int getMaxAsignaciones() { return maxAsignaciones; }
    public BigDecimal getHorasComprometidas() { return horasComprometidas; }
    public int getEquipoActual() { return equipoActual; }
    public int getVacantes() { return vacantes; }
    public List<String> getHabilidades() { return habilidades; }

    public String getGrupo() {
        if (asignacion.getEstado() == EstadoAsignacion.PENDIENTE) return "pending";
        if (asignacion.getEstado() == EstadoAsignacion.ACTIVA) return "active";
        return "history";
    }

    public boolean isRequiereDecisionRm() {
        return asignacion.getEstado() == EstadoAsignacion.PENDIENTE
                && !asignacion.isAprobadoPorRm()
                && asignacion.getOrigen() != OrigenAsignacion.PROPUESTA_RM;
    }

    public boolean isPendientePm() {
        return asignacion.getEstado() == EstadoAsignacion.PENDIENTE
                && asignacion.isAprobadoPorRm()
                && !asignacion.isAprobadoPorPm();
    }

    public boolean isSolicitudColaborador() {
        return asignacion.getOrigen() == OrigenAsignacion.SOLICITADA_COLABORADOR;
    }

    public boolean isActiva() { return asignacion.getEstado() == EstadoAsignacion.ACTIVA; }

    public boolean isSuperaLimite() { return asignacionesActivas >= maxAsignaciones; }

    public boolean isSuperaDisponibilidad() {
        BigDecimal disponibles = getHorasDisponibles();
        return asignacion.getHorasSemanales() != null
                && asignacion.getHorasSemanales().compareTo(disponibles) > 0;
    }

    public BigDecimal getHorasDisponibles() {
        return asignacion.getColaborador().getHorasDisponibles() == null
                ? BigDecimal.ZERO : asignacion.getColaborador().getHorasDisponibles();
    }

    public BigDecimal getHorasContratadas() {
        return asignacion.getColaborador().getHorasContratadasSemana() == null
                ? BigDecimal.ZERO : asignacion.getColaborador().getHorasContratadasSemana();
    }

    public String getOrigenTexto() {
        return switch (asignacion.getOrigen()) {
            case PROPUESTA_PM -> "Propuesta del PM";
            case PROPUESTA_RM -> "Propuesta del RM";
            case SOLICITADA_COLABORADOR -> "Solicitud del colaborador";
        };
    }

    public String getOrigenCodigo() {
        return switch (asignacion.getOrigen()) {
            case PROPUESTA_PM -> "PM";
            case PROPUESTA_RM -> "RM";
            case SOLICITADA_COLABORADOR -> "Colaborador";
        };
    }

    public String getOrigenClase() {
        return switch (asignacion.getOrigen()) {
            case PROPUESTA_PM -> "bg-blue-lt";
            case PROPUESTA_RM -> "bg-azure-lt";
            case SOLICITADA_COLABORADOR -> "bg-purple-lt";
        };
    }

    public String getEstadoTexto() {
        if (isRequiereDecisionRm()) return "Pendiente RM";
        if (isPendientePm()) return "Pendiente PM";
        return switch (asignacion.getEstado()) {
            case PENDIENTE -> "Pendiente";
            case ACTIVA -> "Activa";
            case RECHAZADA -> "Rechazada";
            case FINALIZADA -> "Finalizada";
        };
    }

    // Estado que ve el RM en la bandeja y valor del filtro "estado": una postulación
    // pendiente sin ninguna aprobación se muestra como "Pendiente RM y PM" (TASK-027).
    public String getEstadoBandeja() {
        if (isSolicitudColaborador()
                && asignacion.getEstado() == EstadoAsignacion.PENDIENTE
                && !asignacion.isAprobadoPorPm()
                && !asignacion.isAprobadoPorRm()) {
            return "Pendiente RM y PM";
        }
        return getEstadoTexto();
    }

    public String getEstadoClase() {
        if (isRequiereDecisionRm()) return "bg-yellow-lt";
        if (isPendientePm()) return "bg-blue-lt";
        return switch (asignacion.getEstado()) {
            case ACTIVA -> "bg-green-lt";
            case RECHAZADA -> "bg-red-lt text-red";
            case FINALIZADA -> "bg-secondary-lt";
            case PENDIENTE -> "bg-yellow-lt";
        };
    }

    public String getAprobacionPmTexto() {
        if (asignacion.getEstado() == EstadoAsignacion.RECHAZADA && !asignacion.isAprobadoPorPm()) return "PM rechazó/pendiente";
        return asignacion.isAprobadoPorPm() ? "PM ✓" : "PM ·";
    }

    public String getAprobacionRmTexto() {
        if (asignacion.getEstado() == EstadoAsignacion.RECHAZADA && !asignacion.isAprobadoPorRm()) return "RM rechazó/pendiente";
        return asignacion.isAprobadoPorRm() ? "RM ✓" : "RM ·";
    }
}
