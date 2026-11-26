package com.pucp.skillb_ia.dto.auth;

/**
 * Regla única de contraseña fuerte para los formularios con Bean Validation ({@literal @}Pattern necesita
 * una constante).
 */
public final class PoliticaPassword {

    public static final String REGEX = "(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).{8,}";
    public static final String MENSAJE =
            "Debe tener al menos 8 caracteres, una mayúscula, un número y un símbolo.";

    /** Clase de constantes: no se instancia. */
    private PoliticaPassword() {}
}
