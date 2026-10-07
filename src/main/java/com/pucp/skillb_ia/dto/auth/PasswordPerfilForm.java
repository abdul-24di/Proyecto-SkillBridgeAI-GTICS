package com.pucp.skillb_ia.dto.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// Cambio de contraseña desde el perfil (Admin).
public class PasswordPerfilForm {

    @NotBlank(message = "La contraseña actual es obligatoria.")
    private String passwordActual;

    @NotBlank(message = "La nueva contraseña es obligatoria.")
    @Pattern(regexp = PoliticaPassword.REGEX, message = PoliticaPassword.MENSAJE)
    private String passwordNueva;

    @NotBlank(message = "Confirma la nueva contraseña.")
    private String passwordConfirm;

    @AssertTrue(message = "Las contraseñas nuevas no coinciden.")
    public boolean isPasswordsCoinciden() {
        return passwordNueva == null || passwordConfirm == null || passwordNueva.equals(passwordConfirm);
    }

    public String getPasswordActual() { return passwordActual; }
    public void setPasswordActual(String passwordActual) { this.passwordActual = passwordActual; }
    public String getPasswordNueva() { return passwordNueva; }
    public void setPasswordNueva(String passwordNueva) { this.passwordNueva = passwordNueva; }
    public String getPasswordConfirm() { return passwordConfirm; }
    public void setPasswordConfirm(String passwordConfirm) { this.passwordConfirm = passwordConfirm; }
}
