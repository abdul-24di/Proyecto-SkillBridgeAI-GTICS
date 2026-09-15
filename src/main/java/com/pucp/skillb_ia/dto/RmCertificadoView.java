package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Certificado;
import com.pucp.skillb_ia.model.enums.EstadoCertificado;

import java.util.List;

public class RmCertificadoView {
    private final Certificado certificado;
    private final String colaboradorNombre;
    private final String colaboradorIniciales;
    private final String cargo;
    private final String nivelHabilidad;
    private final String nivelGeneral;
    private final String revisorNombre;
    private final long certificadosAprobados;
    private final List<String> habilidades;

    public RmCertificadoView(Certificado certificado, String colaboradorNombre,
                             String colaboradorIniciales, String cargo,
                             String nivelHabilidad, String nivelGeneral,
                             String revisorNombre, long certificadosAprobados,
                             List<String> habilidades) {
        this.certificado = certificado;
        this.colaboradorNombre = colaboradorNombre;
        this.colaboradorIniciales = colaboradorIniciales;
        this.cargo = cargo;
        this.nivelHabilidad = nivelHabilidad;
        this.nivelGeneral = nivelGeneral;
        this.revisorNombre = revisorNombre;
        this.certificadosAprobados = certificadosAprobados;
        this.habilidades = List.copyOf(habilidades);
    }

    public Certificado getCertificado() { return certificado; }
    public String getColaboradorNombre() { return colaboradorNombre; }
    public String getColaboradorIniciales() { return colaboradorIniciales; }
    public String getCargo() { return cargo; }
    public String getNivelHabilidad() { return nivelHabilidad; }
    public String getNivelGeneral() { return nivelGeneral; }
    public String getRevisorNombre() { return revisorNombre; }
    public long getCertificadosAprobados() { return certificadosAprobados; }
    public List<String> getHabilidades() { return habilidades; }
    public String getCodigo() { return String.format("CERT-%03d", certificado.getId()); }

    public String getNombreArchivo() {
        String ruta = certificado.getArchivoUrl().replace('\\', '/');
        int indice = ruta.lastIndexOf('/');
        return indice >= 0 ? ruta.substring(indice + 1) : ruta;
    }

    public String getEstadoTexto() {
        return switch (certificado.getEstado()) {
            case PENDIENTE -> "Pendiente";
            case APROBADO -> "Aprobado";
            case RECHAZADO -> "Rechazado";
        };
    }

    public String getEstadoClase() {
        return switch (certificado.getEstado()) {
            case PENDIENTE -> "bg-yellow-lt";
            case APROBADO -> "bg-green-lt";
            case RECHAZADO -> "bg-red-lt";
        };
    }

    public boolean isPendiente() { return certificado.getEstado() == EstadoCertificado.PENDIENTE; }

    public String getObservacion() {
        if (certificado.getEstado() == EstadoCertificado.RECHAZADO) {
            return certificado.getMotivoRechazo();
        }
        if (certificado.getEstado() == EstadoCertificado.APROBADO) {
            return "Certificado aprobado; la habilidad quedó validada.";
        }
        return "Pendiente de revisión por el Resource Manager.";
    }

    public String getTextoBusqueda() {
        return colaboradorNombre + " " + cargo + " " + getNombreArchivo() + " "
                + certificado.getHabilidad().getNombre() + " " + nivelHabilidad;
    }
}
