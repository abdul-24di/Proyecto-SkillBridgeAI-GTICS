package com.pucp.skillb_ia.dto;

import org.springframework.web.multipart.MultipartFile;

import java.io.Serializable;

public class ColDocumentoForm implements Serializable {


    private transient MultipartFile archivo;

    public MultipartFile getArchivo() { return archivo; }
    public void setArchivo(MultipartFile archivo) { this.archivo = archivo; }
}