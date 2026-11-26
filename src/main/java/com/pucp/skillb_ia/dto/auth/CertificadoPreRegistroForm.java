package com.pucp.skillb_ia.dto.auth;

import com.pucp.skillb_ia.model.enums.NivelDominio;
import org.springframework.web.multipart.MultipartFile;

/**
 * Una fila de certificado de habilidad del pre-registro: habilidad del catálogo + nivel + archivo. Igual que
 * la experiencia, una fila totalmente vacía se ignora.
 */
public class CertificadoPreRegistroForm {

    private Long habilidadId;
    private NivelDominio nivel;
    private MultipartFile archivo;

    // Id del archivo que ya se recibió en un envío anterior (ver PreRegistroArchivosService).
    private String archivoGuardado;

    /**
     * Indica si la fila no tiene ningún dato.
     *
     * @return {@code true} si está vacía
     */
    public boolean estaVacia() {
        return habilidadId == null && nivel == null && (archivo == null || archivo.isEmpty())
                && (archivoGuardado == null || archivoGuardado.isBlank());
    }

    /**
     * Devuelve {@code habilidadId}.
     *
     * @return el valor de {@code habilidadId}
     */
    public Long getHabilidadId() { return habilidadId; }
    /**
     * Asigna {@code habilidadId}.
     *
     * @param habilidadId id de la habilidad
     */
    public void setHabilidadId(Long habilidadId) { this.habilidadId = habilidadId; }
    /**
     * Devuelve {@code nivel}.
     *
     * @return el valor de {@code nivel}
     */
    public NivelDominio getNivel() { return nivel; }
    /**
     * Asigna {@code nivel}.
     *
     * @param nivel nivel de dominio
     */
    public void setNivel(NivelDominio nivel) { this.nivel = nivel; }
    /**
     * Devuelve {@code archivo}.
     *
     * @return el valor de {@code archivo}
     */
    public MultipartFile getArchivo() { return archivo; }
    /**
     * Asigna {@code archivo}.
     *
     * @param archivo archivo subido
     */
    public void setArchivo(MultipartFile archivo) { this.archivo = archivo; }
    /**
     * Devuelve {@code archivoGuardado}.
     *
     * @return el valor de {@code archivoGuardado}
     */
    public String getArchivoGuardado() { return archivoGuardado; }
    /**
     * Asigna {@code archivoGuardado}.
     *
     * @param archivoGuardado id del archivo ya recibido
     */
    public void setArchivoGuardado(String archivoGuardado) { this.archivoGuardado = archivoGuardado; }
}
