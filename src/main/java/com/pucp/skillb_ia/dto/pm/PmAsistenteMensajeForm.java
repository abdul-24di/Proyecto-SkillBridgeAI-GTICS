package com.pucp.skillb_ia.dto.pm;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PmAsistenteMensajeForm {

    @NotBlank(message = "Escribe tu consulta para el asistente.")
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
