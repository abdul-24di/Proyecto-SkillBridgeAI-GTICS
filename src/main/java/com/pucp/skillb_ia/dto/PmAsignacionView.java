package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PmAsignacionView {

    private final Asignacion asignacion;
    private final String colaboradorNombre;
    private final String colaboradorIniciales;
    private final String colaboradorCargo;
    private final String origenTexto;
    private final String estadoTexto;

    public PmAsignacionView(Asignacion asignacion) {
        this.asignacion = asignacion;
        String nombre = asignacion.getColaborador().getNombre();
        String apellido = asignacion.getColaborador().getApellido();
        this.colaboradorNombre = nombre + " " + apellido;
        this.colaboradorIniciales = iniciales(nombre, apellido);
        this.colaboradorCargo = asignacion.getColaborador().getCargo() != null
                ? asignacion.getColaborador().getCargo() : "Colaborador";
        this.origenTexto = traducirOrigen(asignacion.getOrigen());
        this.estadoTexto = traducirEstado(asignacion.getEstado());
    }

    public Asignacion getAsignacion() { return asignacion; }
    public String getColaboradorNombre() { return colaboradorNombre; }
    public String getColaboradorIniciales() { return colaboradorIniciales; }
    public String getColaboradorCargo() { return colaboradorCargo; }
    public String getOrigenTexto() { return origenTexto; }
    public String getEstadoTexto() { return estadoTexto; }
    public BigDecimal getHorasSemanales() { return asignacion.getHorasSemanales(); }
    public LocalDateTime getFechaSolicitud() { return asignacion.getFechaSolicitud(); }

    /** El PM decide propuestas del RM y solicitudes directas del colaborador. */
    public boolean isPuedeAprobarPm() {
        return asignacion.getOrigen() != OrigenAsignacion.PROPUESTA_PM
                && asignacion.getEstado() == EstadoAsignacion.PENDIENTE
                && !asignacion.isAprobadoPorPm();
    }

    /** Rechazar sigue la misma regla de decisión que aprobar. */
    public boolean isPuedeRechazarPm() {
        return isPuedeAprobarPm();
    }

    /** El PM puede finalizar asignaciones activas (A18). */
    public boolean isPuedeFinalizarPm() {
        return asignacion.getEstado() == EstadoAsignacion.ACTIVA;
    }

    public String getEstadoClase() {
        return switch (asignacion.getEstado()) {
            case ACTIVA    -> "bg-green-lt text-green";
            case PENDIENTE -> "bg-yellow-lt text-yellow";
            case RECHAZADA -> "bg-red-lt text-red";
            case FINALIZADA -> "bg-secondary-lt text-secondary";
        };
    }

    private static String iniciales(String nombre, String apellido) {
        String n = (nombre != null && !nombre.isEmpty()) ? String.valueOf(nombre.charAt(0)) : "";
        String a = (apellido != null && !apellido.isEmpty()) ? String.valueOf(apellido.charAt(0)) : "";
        return (n + a).toUpperCase();
    }

    private static String traducirOrigen(OrigenAsignacion origen) {
        return switch (origen) {
            case PROPUESTA_PM           -> "Propuesta PM";
            case PROPUESTA_RM           -> "Propuesta RM";
            case SOLICITADA_COLABORADOR -> "Solicitud Colaborador";
        };
    }

    private static String traducirEstado(EstadoAsignacion estado) {
        return switch (estado) {
            case PENDIENTE  -> "Pendiente";
            case ACTIVA     -> "Activa";
            case RECHAZADA  -> "Rechazada";
            case FINALIZADA -> "Finalizada";
        };
    }
}
