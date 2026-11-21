package com.pucp.skillb_ia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.io.Serializable;

public class ColEntregaActividadForm implements Serializable {

    @NotBlank(message = "Debes escribir un comentario para marcar la actividad como lista.")
    @Size(max = 300, message = "El comentario no puede superar los 300 caracteres.")
    private String comentario;


    private transient MultipartFile evidencia;

    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }
    public MultipartFile getEvidencia() { return evidencia; }
    public void setEvidencia(MultipartFile evidencia) { this.evidencia = evidencia; }
}