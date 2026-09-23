package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.enums.EstadoActividad;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class PmActividadView {

    private final Actividad actividad;
    private final String colaboradorNombre;
    private final String colaboradorIniciales;
    private final String estadoTexto;

    public PmActividadView(Actividad actividad) {
        this.actividad = actividad;
        String nombre = actividad.getColaborador().getNombre();
        String apellido = actividad.getColaborador().getApellido();
        this.colaboradorNombre = nombre + " " + apellido;
        this.colaboradorIniciales = iniciales(nombre, apellido);
        this.estadoTexto = traducirEstado(actividad.getEstado());
    }

    public Actividad getActividad() { return actividad; }
    public String getColaboradorNombre() { return colaboradorNombre; }
    public String getColaboradorIniciales() { return colaboradorIniciales; }
    public String getEstadoTexto() { return estadoTexto; }
    public String getTitulo() { return actividad.getTitulo(); }
    public String getDescripcion() { return actividad.getDescripcion(); }
    public BigDecimal getHorasEstimadas() { return actividad.getHorasEstimadas(); }
    public LocalDate getFechaLimite() { return actividad.getFechaLimite(); }
    public LocalDateTime getFechaMarcadoRevision() { return actividad.getFechaMarcadoRevision(); }
    public String getComentarioDevolucion() { return actividad.getComentarioDevolucion(); }
    public int getVecesDevuelta() { return actividad.getVecesDevuelta(); }

    /** El PM puede confirmar cuando el colaborador la marcó EN_REVISION. */
    public boolean isPuedeConfirmar() {
        return actividad.getEstado() == EstadoActividad.EN_REVISION;
    }

    /** El PM puede devolver cuando está EN_REVISION. */
    public boolean isPuedeDevolver() {
        return actividad.getEstado() == EstadoActividad.EN_REVISION;
    }

    public boolean isPuedeEditar() {
        return actividad.getEstado() != EstadoActividad.COMPLETADA;
    }

    public boolean isPuedeEliminar() {
        return actividad.getEstado() == EstadoActividad.PENDIENTE || actividad.getEstado() == EstadoActividad.EN_PROGRESO;
    }

    public boolean isVencida() {
        return actividad.getEstado() != EstadoActividad.COMPLETADA
                && actividad.getFechaLimite() != null
                && actividad.getFechaLimite().isBefore(LocalDate.now());
    }

    public String getEstadoClase() {
        return switch (actividad.getEstado()) {
            case PENDIENTE   -> "bg-secondary-lt text-secondary";
            case EN_PROGRESO -> "bg-blue-lt text-blue";
            case EN_REVISION -> "bg-yellow-lt text-yellow";
            case COMPLETADA  -> "bg-green-lt text-green";
        };
    }

    private static String iniciales(String nombre, String apellido) {
        String n = (nombre != null && !nombre.isEmpty()) ? String.valueOf(nombre.charAt(0)) : "";
        String a = (apellido != null && !apellido.isEmpty()) ? String.valueOf(apellido.charAt(0)) : "";
        return (n + a).toUpperCase();
    }

    private static String traducirEstado(EstadoActividad estado) {
        return switch (estado) {
            case PENDIENTE   -> "Pendiente";
            case EN_PROGRESO -> "En progreso";
            case EN_REVISION -> "En revisión";
            case COMPLETADA  -> "Completada";
        };
    }
}
