package com.pucp.skillb_ia.dto.pm;
import jakarta.validation.constraints.*;

public class PmPerfilPasswordForm {
    @NotBlank(message = "La contrasea actual es obligatoria")
    private String passwordActual;

    @NotBlank(message = "La nueva contrasea es obligatoria")
    @Size(min = 6, message = "La contrasea debe tener al menos 6 caracteres")
    private String passwordNueva;

    @NotBlank(message = "Debes confirmar la nueva contrasea")
    private String passwordConfirm;

    public String getPasswordActual() { return passwordActual; }
    public void setPasswordActual(String passwordActual) { this.passwordActual = passwordActual; }
    public String getPasswordNueva() { return passwordNueva; }
    public void setPasswordNueva(String passwordNueva) { this.passwordNueva = passwordNueva; }
    public String getPasswordConfirm() { return passwordConfirm; }
    public void setPasswordConfirm(String passwordConfirm) { this.passwordConfirm = passwordConfirm; }
}
