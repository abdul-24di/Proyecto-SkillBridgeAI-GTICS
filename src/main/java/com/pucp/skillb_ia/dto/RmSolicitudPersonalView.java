package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.SolicitudPersonal;
import com.pucp.skillb_ia.model.enums.EstadoSolicitudPersonal;

import java.util.List;

public class RmSolicitudPersonalView {
    private final SolicitudPersonal solicitud;
    private final RmProyectoView proyecto;
    private final String pmNombre;
    private final String rmNombre;
    private final List<RmProyectoView.RequisitoTalento> requisitos;

    public RmSolicitudPersonalView(SolicitudPersonal solicitud, RmProyectoView proyecto,
                                   String pmNombre, String rmNombre,
                                   List<RmProyectoView.RequisitoTalento> requisitos) {
        this.solicitud = solicitud;
        this.proyecto = proyecto;
        this.pmNombre = pmNombre;
        this.rmNombre = rmNombre;
        this.requisitos = List.copyOf(requisitos);
    }

    public SolicitudPersonal getSolicitud() { return solicitud; }
    public RmProyectoView getProyecto() { return proyecto; }
    public String getPmNombre() { return pmNombre; }
    public String getRmNombre() { return rmNombre; }
    public List<RmProyectoView.RequisitoTalento> getRequisitos() { return requisitos; }
    public String getCodigo() { return String.format("SOL-%03d", solicitud.getId()); }

    public String getEstadoTexto() {
        return switch (solicitud.getEstado()) {
            case PENDIENTE -> "Pendiente";
            case EN_ATENCION -> "En atención";
            case ATENDIDA -> "Atendida";
            case CANCELADA -> "Cancelada";
        };
    }

    public String getEstadoClase() {
        return switch (solicitud.getEstado()) {
            case PENDIENTE -> "bg-yellow-lt";
            case EN_ATENCION -> "bg-blue-lt";
            case ATENDIDA -> "bg-green-lt";
            case CANCELADA -> "bg-secondary-lt";
        };
    }

    public String getAccionTexto() {
        return switch (solicitud.getEstado()) {
            case PENDIENTE -> "Atender";
            case EN_ATENCION -> "Continuar";
            case ATENDIDA, CANCELADA -> "Ver";
        };
    }

    public boolean isPendiente() { return solicitud.getEstado() == EstadoSolicitudPersonal.PENDIENTE; }
    public boolean isEnAtencion() { return solicitud.getEstado() == EstadoSolicitudPersonal.EN_ATENCION; }
    public boolean isCerrada() {
        return solicitud.getEstado() == EstadoSolicitudPersonal.ATENDIDA
                || solicitud.getEstado() == EstadoSolicitudPersonal.CANCELADA;
    }

    public String getTextoBusqueda() {
        String habilidades = requisitos.stream()
                .map(RmProyectoView.RequisitoTalento::getHabilidad)
                .reduce("", (a, b) -> a + " " + b);
        return solicitud.getProyecto().getNombre() + " " + pmNombre + habilidades;
    }
}
