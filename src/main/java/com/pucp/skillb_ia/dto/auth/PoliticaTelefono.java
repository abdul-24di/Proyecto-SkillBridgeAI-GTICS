package com.pucp.skillb_ia.dto.auth;

/**
 * Regla única de teléfono para todos los roles (Admin, RM, PM, Colaborador y activación de cuenta): entre 7
 * y 20 caracteres, solo dígitos y, opcionalmente, un signo + al inicio.
 */
public final class PoliticaTelefono {

    public static final String REGEX = "^(?:[0-9]{7,20}|\\+[0-9]{7,19})$";
    public static final String MENSAJE = "El teléfono debe tener entre 7 y 20 caracteres, solo dígitos "
            + "y, opcionalmente, un signo + al inicio (sin espacios, guiones ni paréntesis).";

    /** Clase de constantes: no se instancia. */
    private PoliticaTelefono() {}
}
