package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Educacion;
import com.pucp.skillb_ia.model.enums.EstadoCertificado;

public class RmEducacionView {
    private final Educacion educacion;
    private final String colaboradorNombre;
    private final String colaboradorIniciales;
    private final String cargo;
    private final String nivelGeneral;
    private final String revisorNombre;
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
    public String getCodigo() { return String.format("EDU-%03d", educacion.getId()); }

    public boolean isTieneArchivo() {
        return educacion.getArchivoUrl() != null && !educacion.getArchivoUrl().isBlank();
    }

    public String getNombreArchivo() {
        if (!isTieneArchivo()) return "Sin documento";
        String ruta = educacion.getArchivoUrl().replace('\\', '/');
        int indice = ruta.lastIndexOf('/');
        return indice >= 0 ? ruta.substring(indice + 1) : ruta;
    }

    public String getEstadoTexto() {
        return switch (educacion.getEstado()) {
            case PENDIENTE -> "Pendiente";
            case APROBADO -> "Aprobado";
            case RECHAZADO -> "Rechazado";
        };
    }

    public String getEstadoClase() {
        return switch (educacion.getEstado()) {
            case PENDIENTE -> "bg-yellow-lt";
            case APROBADO -> "bg-green-lt";
            case RECHAZADO -> "bg-red-lt";
        };
    }

    public boolean isPendiente() { return educacion.getEstado() == EstadoCertificado.PENDIENTE; }

    public String getObservacion() {
        if (educacion.getEstado() == EstadoCertificado.RECHAZADO) {
            return educacion.getMotivoRechazo();
        }
        if (educacion.getEstado() == EstadoCertificado.APROBADO) {
            return "Formación académica validada por el Resource Manager.";
        }
        return "Pendiente de revisión por el Resource Manager.";
    }

    public String getTextoBusqueda() {
        return colaboradorNombre + " " + valor(educacion.getColaborador().getCorreo()) + " " + cargo + " "
                + educacion.getTitulo() + " " + educacion.getInstitucion();
    }

    private String valor(String texto) {
        return texto == null ? "" : texto;
    }
}
