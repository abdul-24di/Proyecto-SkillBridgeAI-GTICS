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

    /** @return la consulta escrita por el RM */
    public String getMensaje() {
        return mensaje;
    }

    /** @param mensaje consulta escrita por el RM */
    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    /** @return el id del proyecto de contexto, o {@code null} si no se eligió uno */
    public Long getProyectoId() {
        return proyectoId;
    }

    /** @param proyectoId id del proyecto de contexto; {@code null} = sin proyecto */
    public void setProyectoId(Long proyectoId) {
        this.proyectoId = proyectoId;
    }
}
