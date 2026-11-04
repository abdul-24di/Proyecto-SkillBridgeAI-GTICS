package com.pucp.skillb_ia.dto.auth;

import java.io.Serializable;

// Archivo válido del pre-registro que espera en una carpeta temporal a que el formulario se envíe sin
// errores. Serializable porque se guarda en la sesión HTTP (Spring Session JDBC).
public record ArchivoPendiente(String id, String nombre, String tipo, long tamano) implements Serializable {

    public String tamanoLegible() {
        return tamano < 1024 * 1024
                ? Math.max(1, tamano / 1024) + " KB"
                : String.format("%.1f MB", tamano / (1024.0 * 1024.0));
    }
}
