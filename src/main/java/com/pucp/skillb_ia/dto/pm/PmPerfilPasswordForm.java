package com.pucp.skillb_ia.dto.pm;

import com.pucp.skillb_ia.dto.auth.PoliticaPassword;
import jakarta.validation.constraints.*;

public class PmPerfilPasswordForm {
    @NotBlank(message = "La contraseña actual es obligatoria")
    private String passwordActual;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Pattern(regexp = PoliticaPassword.REGEX, message = PoliticaPassword.MENSAJE)
    private String passwordNueva;

    @NotBlank(message = "Debes confirmar la nueva contraseña")
    private String passwordConfirm;

    public String getPasswordActual() { return passwordActual; }
    public void setPasswordActual(String passwordActual) { this.passwordActual = passwordActual; }
    public String getPasswordNueva() { return passwordNueva; }
    public void setPasswordNueva(String passwordNueva) { this.passwordNueva = passwordNueva; }
    public String getPasswordConfirm() { return passwordConfirm; }
    public void setPasswordConfirm(String passwordConfirm) { this.passwordConfirm = passwordConfirm; }
}
