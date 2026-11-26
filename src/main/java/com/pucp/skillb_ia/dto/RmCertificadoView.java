package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Certificado;
import com.pucp.skillb_ia.model.enums.EstadoCertificado;
import com.pucp.skillb_ia.model.enums.NivelDominio;

import java.util.List;

/**
 * Fila de la bandeja de validación de certificados del Resource Manager y de su detalle.
 * La arma {@code RmCertificadoService}; envuelve el {@link Certificado} con datos del colaborador
 * y los textos de estado para la vista.
 */
public class RmCertificadoView {
    private final Certificado certificado;
    private final String colaboradorNombre;
    private final String colaboradorIniciales;
    private final String cargo;
    /** Nivel actual de la habilidad del certificado en el perfil del colaborador. */
    private final String nivelHabilidad;
    /** Nivel de experiencia general del colaborador. */
    private final String nivelGeneral;
    /** RM que revisó el certificado, si ya se revisó. */
    private final String revisorNombre;
    /** Total de certificados aprobados del colaborador. */
    private final long certificadosAprobados;
    private final List<String> habilidades;

    /** Arma la vista del certificado con los datos del colaborador, su nivel y el revisor. */
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
    /** @return código visible del certificado, por ejemplo {@code CERT-007} */
    public String getCodigo() { return String.format("CERT-%03d", certificado.getId()); }

    /** @return nombre del archivo (último tramo de la ruta guardada, con {@code \} o {@code /}) */
    public String getNombreArchivo() {
        String ruta = certificado.getArchivoUrl().replace('\\', '/');
        int indice = ruta.lastIndexOf('/');
        return indice >= 0 ? ruta.substring(indice + 1) : ruta;
    }

    /** @return estado legible del certificado */
    public String getEstadoTexto() {
        return switch (certificado.getEstado()) {
            case PENDIENTE -> "Pendiente";
            case APROBADO -> "Aprobado";
            case RECHAZADO -> "Rechazado";
        };
    }

    /** @return clase CSS de la insignia del estado */
    public String getEstadoClase() {
        return switch (certificado.getEstado()) {
            case PENDIENTE -> "bg-yellow-lt";
            case APROBADO -> "bg-green-lt";
            case RECHAZADO -> "bg-red-lt";
        };
    }

    /**
     * Nivel guardado en el certificado al aprobarlo; nunca se deduce del nivel actual de la habilidad.
     *
     * @return el nivel legible; "Sin registro" si está aprobado sin nivel guardado y "—" si no está aprobado
     */
    public String getNivelAprobadoTexto() {
        NivelDominio nivel = certificado.getNivelAprobado();
        if (nivel == null) {
            return certificado.getEstado() == EstadoCertificado.APROBADO ? "Sin registro" : "—";
        }
        return switch (nivel) {
            case BASICO -> "Básico";
            case INTERMEDIO -> "Intermedio";
            case AVANZADO -> "Avanzado";
        };
    }

    public boolean isPendiente() { return certificado.getEstado() == EstadoCertificado.PENDIENTE; }

    /** @return motivo de rechazo si fue rechazado; si no, un texto fijo según esté aprobado o pendiente */
    public String getObservacion() {
        if (certificado.getEstado() == EstadoCertificado.RECHAZADO) {
            return certificado.getMotivoRechazo();
        }
        if (certificado.getEstado() == EstadoCertificado.APROBADO) {
            return "Certificado aprobado; la habilidad quedó validada.";
        }
        return "Pendiente de revisión por el Resource Manager.";
    }

    /** @return texto que usa el buscador de la bandeja: colaborador, cargo, archivo, habilidad y nivel */
    public String getTextoBusqueda() {
        return colaboradorNombre + " " + cargo + " " + getNombreArchivo() + " "
                + certificado.getHabilidad().getNombre() + " " + nivelHabilidad;
    }
}
