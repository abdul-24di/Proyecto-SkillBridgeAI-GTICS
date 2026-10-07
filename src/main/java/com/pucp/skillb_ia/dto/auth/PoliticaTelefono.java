package com.pucp.skillb_ia.dto.auth;

// Misma regla de teléfono que ya usa el perfil del colaborador: exactamente 9 dígitos, sin letras,
// espacios, guiones ni signos.
public final class PoliticaTelefono {

    public static final String REGEX = "^[0-9]{9}$";
    public static final String MENSAJE =
            "El teléfono debe tener exactamente 9 dígitos numéricos, sin letras ni otros caracteres.";

    private PoliticaTelefono() {}
}
