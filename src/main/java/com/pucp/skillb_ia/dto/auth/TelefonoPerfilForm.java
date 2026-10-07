package com.pucp.skillb_ia.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// Teléfono del perfil (Admin): misma regla que el RM.
public class TelefonoPerfilForm {

    @NotBlank(message = "El teléfono es obligatorio.")
    @Pattern(regexp = PoliticaTelefono.REGEX, message = PoliticaTelefono.MENSAJE)
    private String telefono;

    public TelefonoPerfilForm() {}

    public TelefonoPerfilForm(String telefono) {
        this.telefono = telefono;
    }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
}
