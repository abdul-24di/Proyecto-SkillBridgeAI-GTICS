package com.pucp.skillb_ia.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Teléfono del perfil (Admin): misma regla que el RM. */
public class TelefonoPerfilForm {

    @NotBlank(message = "El teléfono es obligatorio.")
    @Pattern(regexp = PoliticaTelefono.REGEX, message = PoliticaTelefono.MENSAJE)
    private String telefono;

    /** Crea el formulario vacío. */
    public TelefonoPerfilForm() {}

    /** Crea el formulario con el teléfono actual. */
    public TelefonoPerfilForm(String telefono) {
        this.telefono = telefono;
    }

    /**
     * Devuelve {@code telefono}.
     *
     * @return el valor de {@code telefono}
     */
    public String getTelefono() { return telefono; }
    /**
     * Asigna {@code telefono}.
     *
     * @param telefono teléfono ingresado
     */
    public void setTelefono(String telefono) { this.telefono = telefono; }
}
