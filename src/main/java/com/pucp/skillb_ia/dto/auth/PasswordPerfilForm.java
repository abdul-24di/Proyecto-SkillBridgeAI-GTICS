package com.pucp.skillb_ia.dto.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Cambio de contraseña desde el perfil (Admin). */
public class PasswordPerfilForm {

    @NotBlank(message = "La contraseña actual es obligatoria.")
    private String passwordActual;

    @NotBlank(message = "La nueva contraseña es obligatoria.")
    @Pattern(regexp = PoliticaPassword.REGEX, message = PoliticaPassword.MENSAJE)
    private String passwordNueva;

    @NotBlank(message = "Confirma la nueva contraseña.")
    private String passwordConfirm;

    /**
     * Comprueba que las dos contraseñas nuevas sean iguales.
     *
     * @return {@code true} si coinciden o falta alguna
     */
    @AssertTrue(message = "Las contraseñas nuevas no coinciden.")
    public boolean isPasswordsCoinciden() {
        return passwordNueva == null || passwordConfirm == null || passwordNueva.equals(passwordConfirm);
    }

    /**
     * Devuelve {@code passwordActual}.
     *
     * @return el valor de {@code passwordActual}
     */
    public String getPasswordActual() { return passwordActual; }
    /**
     * Asigna {@code passwordActual}.
     *
     * @param passwordActual contraseña vigente
     */
    public void setPasswordActual(String passwordActual) { this.passwordActual = passwordActual; }
    /**
     * Devuelve {@code passwordNueva}.
     *
     * @return el valor de {@code passwordNueva}
     */
    public String getPasswordNueva() { return passwordNueva; }
    /**
     * Asigna {@code passwordNueva}.
     *
     * @param passwordNueva contraseña nueva
     */
    public void setPasswordNueva(String passwordNueva) { this.passwordNueva = passwordNueva; }
    /**
     * Devuelve {@code passwordConfirm}.
     *
     * @return el valor de {@code passwordConfirm}
     */
    public String getPasswordConfirm() { return passwordConfirm; }
    /**
     * Asigna {@code passwordConfirm}.
     *
     * @param passwordConfirm repetición de la contraseña nueva
     */
    public void setPasswordConfirm(String passwordConfirm) { this.passwordConfirm = passwordConfirm; }
}
