package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Educacion;
import com.pucp.skillb_ia.model.enums.EstadoCertificado;

/**
 * Fila de la bandeja de validación de formación académica del Resource Manager y de su detalle.
 * La arma {@code RmEducacionService}; envuelve la {@link Educacion} con datos del colaborador y
 * los textos de estado para la vista. Usa los mismos estados que los certificados.
 */
public class RmEducacionView {
    private final Educacion educacion;
    private final String colaboradorNombre;
    private final String colaboradorIniciales;
    private final String cargo;
    /** Nivel de experiencia general del colaborador. */
    private final String nivelGeneral;
    /** RM que revisó la formación, si ya se revisó. */
    private final String revisorNombre;
    /** Total de formaciones aprobadas del colaborador. */
    private final long formacionesAprobadas;

    public RmEducacionView(Educacion educacion, String colaboradorNombre,
                           String colaboradorIniciales, String cargo, String nivelGeneral,
                           String revisorNombre, long formacionesAprobadas) {
        this.educacion = educacion;
        this.colaboradorNombre = colaboradorNombre;
        this.colaboradorIniciales = colaboradorIniciales;
        this.cargo = cargo;
        this.nivelGeneral = nivelGeneral;
        this.revisorNombre = revisorNombre;
        this.formacionesAprobadas = formacionesAprobadas;
    }

    public Educacion getEducacion() { return educacion; }
    public String getColaboradorNombre() { return colaboradorNombre; }
    public String getColaboradorIniciales() { return colaboradorIniciales; }
    public String getCargo() { return cargo; }
    public String getNivelGeneral() { return nivelGeneral; }
    public String getRevisorNombre() { return revisorNombre; }
    public long getFormacionesAprobadas() { return formacionesAprobadas; }
    /** @return código visible de la formación, por ejemplo {@code EDU-012} */
    public String getCodigo() { return String.format("EDU-%03d", educacion.getId()); }

    /** @return {@code true} si la formación tiene un documento adjunto */
    public boolean isTieneArchivo() {
        return educacion.getArchivoUrl() != null && !educacion.getArchivoUrl().isBlank();
    }

    /** @return nombre del archivo (último tramo de la ruta), o "Sin documento" si no tiene */
    public String getNombreArchivo() {
        if (!isTieneArchivo()) return "Sin documento";
        String ruta = educacion.getArchivoUrl().replace('\\', '/');
        int indice = ruta.lastIndexOf('/');
        return indice >= 0 ? ruta.substring(indice + 1) : ruta;
    }

    /** @return estado legible de la formación */
    public String getEstadoTexto() {
        return switch (educacion.getEstado()) {
            case PENDIENTE -> "Pendiente";
            case APROBADO -> "Aprobado";
            case RECHAZADO -> "Rechazado";
        };
    }

    /** @return clase CSS de la insignia del estado */
    public String getEstadoClase() {
        return switch (educacion.getEstado()) {
            case PENDIENTE -> "bg-yellow-lt";
            case APROBADO -> "bg-green-lt";
            case RECHAZADO -> "bg-red-lt";
        };
    }

    public boolean isPendiente() { return educacion.getEstado() == EstadoCertificado.PENDIENTE; }

    /** @return motivo de rechazo si fue rechazada; si no, un texto fijo según esté aprobada o pendiente */
    public String getObservacion() {
        if (educacion.getEstado() == EstadoCertificado.RECHAZADO) {
            return educacion.getMotivoRechazo();
        }
        if (educacion.getEstado() == EstadoCertificado.APROBADO) {
            return "Formación académica validada por el Resource Manager.";
        }
        return "Pendiente de revisión por el Resource Manager.";
    }

    /** @return texto del buscador: colaborador, correo, cargo, título e institución */
    public String getTextoBusqueda() {
        return colaboradorNombre + " " + valor(educacion.getColaborador().getCorreo()) + " " + cargo + " "
                + educacion.getTitulo() + " " + educacion.getInstitucion();
    }

    private String valor(String texto) {
        return texto == null ? "" : texto;
    }
}
