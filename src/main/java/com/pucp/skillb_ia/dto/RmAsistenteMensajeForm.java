package com.pucp.skillb_ia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * Formulario del chat del asistente IA del Resource Manager ({@code RmChatViewController}).
 * Se valida con Bean Validation: el mensaje es obligatorio y admite hasta 1000 caracteres.
 */
public class RmAsistenteMensajeForm implements Serializable {

    /** Consulta escrita por el RM. */
    @NotBlank(message = "Escribe tu consulta para el asistente del RM.")
    @Size(max = 1000, message = "Tu mensaje es muy largo (máximo 1000 caracteres).")
    private String mensaje;

    /** Proyecto elegido como contexto de la consulta; opcional ({@code null} = sin proyecto). */
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
