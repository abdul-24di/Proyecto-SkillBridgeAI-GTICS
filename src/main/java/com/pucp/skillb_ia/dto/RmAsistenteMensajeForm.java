package com.pucp.skillb_ia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

public class RmAsistenteMensajeForm implements Serializable {

    @NotBlank(message = "Escribe tu consulta para el asistente del RM.")
    @Size(max = 1000, message = "Tu mensaje es muy largo (máximo 1000 caracteres).")
    private String mensaje;

    private Long proyectoId;

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public Long getProyectoId() {
        return proyectoId;
    }

    public void setProyectoId(Long proyectoId) {
        this.proyectoId = proyectoId;
    }
}
