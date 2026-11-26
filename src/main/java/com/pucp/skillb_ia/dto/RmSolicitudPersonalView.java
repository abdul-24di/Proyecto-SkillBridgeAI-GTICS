package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.SolicitudPersonal;
import com.pucp.skillb_ia.model.enums.EstadoSolicitudPersonal;

import java.util.List;

/**
 * Solicitud de personal que el PM envía al Resource Manager, tal como se ve en su bandeja y detalle.
 * La arma {@code RmSolicitudPersonalService}; reúne la {@link SolicitudPersonal}, el proyecto y los
 * requisitos de talento, con los textos y clases CSS del estado.
 */
public class RmSolicitudPersonalView {
    private final SolicitudPersonal solicitud;
    private final RmProyectoView proyecto;
    private final String pmNombre;
    private final String rmNombre;
    private final List<RmProyectoView.RequisitoTalento> requisitos;

    /** Arma la vista de la solicitud con su proyecto, el PM, el RM responsable y los requisitos. */
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
    /** @return código visible de la solicitud, por ejemplo {@code SOL-004} */
    public String getCodigo() { return String.format("SOL-%03d", solicitud.getId()); }

    /** @return estado legible de la solicitud */
    public String getEstadoTexto() {
        return switch (solicitud.getEstado()) {
            case PENDIENTE -> "Pendiente";
            case EN_ATENCION -> "En atención";
            case ATENDIDA -> "Atendida";
            case CANCELADA -> "Cancelada";
        };
    }

    /** @return clase CSS de la insignia del estado */
    public String getEstadoClase() {
        return switch (solicitud.getEstado()) {
            case PENDIENTE -> "bg-yellow-lt";
            case EN_ATENCION -> "bg-blue-lt";
            case ATENDIDA -> "bg-green-lt";
            case CANCELADA -> "bg-secondary-lt";
        };
    }

    /** @return texto del botón de la fila: "Atender" si está pendiente, "Continuar" en atención y "Ver" si está cerrada */
    public String getAccionTexto() {
        return switch (solicitud.getEstado()) {
            case PENDIENTE -> "Atender";
            case EN_ATENCION -> "Continuar";
            case ATENDIDA, CANCELADA -> "Ver";
        };
    }

    public boolean isPendiente() { return solicitud.getEstado() == EstadoSolicitudPersonal.PENDIENTE; }
    public boolean isEnAtencion() { return solicitud.getEstado() == EstadoSolicitudPersonal.EN_ATENCION; }
    /** @return {@code true} si la solicitud está atendida o cancelada */
    public boolean isCerrada() {
        return solicitud.getEstado() == EstadoSolicitudPersonal.ATENDIDA
                || solicitud.getEstado() == EstadoSolicitudPersonal.CANCELADA;
    }

    /** @return texto del buscador: proyecto, PM y habilidades requeridas */
    public String getTextoBusqueda() {
        String habilidades = requisitos.stream()
                .map(RmProyectoView.RequisitoTalento::getHabilidad)
                .reduce("", (a, b) -> a + " " + b);
        return solicitud.getProyecto().getNombre() + " " + pmNombre + habilidades;
    }
}
