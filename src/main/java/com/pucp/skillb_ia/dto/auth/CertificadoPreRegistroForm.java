package com.pucp.skillb_ia.dto.auth;

import com.pucp.skillb_ia.model.enums.NivelDominio;
import org.springframework.web.multipart.MultipartFile;

// Una fila de certificado de habilidad del pre-registro: habilidad del catálogo + nivel + archivo.
// Igual que la experiencia, una fila totalmente vacía se ignora.
public class CertificadoPreRegistroForm {

    private Long habilidadId;
    private NivelDominio nivel;
    private MultipartFile archivo;

    // Id del archivo que ya se recibió en un envío anterior (ver PreRegistroArchivosService).
    private String archivoGuardado;

    public boolean estaVacia() {
        return habilidadId == null && nivel == null && (archivo == null || archivo.isEmpty())
                && (archivoGuardado == null || archivoGuardado.isBlank());
    }

    public Long getHabilidadId() { return habilidadId; }
    public void setHabilidadId(Long habilidadId) { this.habilidadId = habilidadId; }
    public NivelDominio getNivel() { return nivel; }
    public void setNivel(NivelDominio nivel) { this.nivel = nivel; }
    public MultipartFile getArchivo() { return archivo; }
    public void setArchivo(MultipartFile archivo) { this.archivo = archivo; }
    public String getArchivoGuardado() { return archivoGuardado; }
    public void setArchivoGuardado(String archivoGuardado) { this.archivoGuardado = archivoGuardado; }
}
