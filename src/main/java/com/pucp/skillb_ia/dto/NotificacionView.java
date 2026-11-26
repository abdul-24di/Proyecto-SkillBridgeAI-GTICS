package com.pucp.skillb_ia.dto;

import java.time.LocalDateTime;

public record NotificacionView(
        Long id,
        String titulo,
        String descripcion,
        String categoria,
        boolean leida,
        LocalDateTime fechaCreacion,
        String url) {
}
