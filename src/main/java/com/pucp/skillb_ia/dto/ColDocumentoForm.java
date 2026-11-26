package com.pucp.skillb_ia.dto;

import org.springframework.web.multipart.MultipartFile;

import java.io.Serializable;

/**
 * Formulario para que el colaborador suba un documento a un proyecto en el que participa.
 * <p>
 * Solo transporta el archivo. La validación del archivo y el guardado los hace
 * {@code ColaboradorDocumentoService}. El campo es {@code transient} porque la clase es
 * {@link java.io.Serializable} y {@code MultipartFile} no lo es.
 *
 * @see com.pucp.skillb_ia.controller.ColaboradorViewController#subirDocumentoProyecto
 */
public class ColDocumentoForm implements Serializable {


    private transient MultipartFile archivo;

    public MultipartFile getArchivo() { return archivo; }
    public void setArchivo(MultipartFile archivo) { this.archivo = archivo; }
}