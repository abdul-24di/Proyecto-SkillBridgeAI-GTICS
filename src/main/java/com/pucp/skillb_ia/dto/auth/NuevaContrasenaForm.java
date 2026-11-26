package com.pucp.skillb_ia.dto.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** El código de recuperación no viaja en este formulario: el controlador lo toma de la sesión. */
public class NuevaContrasenaForm {

    @NotBlank(message = "La contraseña es obligatoria.")
    @Pattern(regexp = PoliticaPassword.REGEX, message = PoliticaPassword.MENSAJE)
    private String password;

    @NotBlank(message = "Confirma tu contraseña.")
    private String confirmarPassword;

    /**
     * Comprueba que las dos contraseñas sean iguales.
     *
     * @return {@code true} si coinciden o falta alguna
     */
    @AssertTrue(message = "Las contraseñas no coinciden.")
    public boolean isPasswordsCoinciden() {
        return password == null || confirmarPassword == null || password.equals(confirmarPassword);
    }

    /**
     * Devuelve {@code password}.
     *
     * @return el valor de {@code password}
     */
    public String getPassword() { return password; }
    /**
     * Asigna {@code password}.
     *
     * @param password contraseña en texto plano; se guarda cifrada con BCrypt
     */
    public void setPassword(String password) { this.password = password; }
    /**
     * Devuelve {@code confirmarPassword}.
     *
     * @return el valor de {@code confirmarPassword}
     */
    public String getConfirmarPassword() { return confirmarPassword; }
    /**
     * Asigna {@code confirmarPassword}.
     *
     * @param confirmarPassword repetición de la contraseña
     */
    public void setConfirmarPassword(String confirmarPassword) { this.confirmarPassword = confirmarPassword; }
}
