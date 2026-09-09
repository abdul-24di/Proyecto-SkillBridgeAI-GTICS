package com.pucp.skillb_ia.model.enums;

// proyecto.estado (chk_proyecto_estado). Flujo acordado en el Bloque B punto 3:
// EN_REVISION -> ACTIVO | RECHAZADO -> EN_ESPERA | CANCELADO | FINALIZADO.
// El PM puede cancelar su proyecto mientras esté EN_REVISION, antes de que el RM decida.
public enum EstadoProyecto {
    EN_REVISION,
    RECHAZADO,
    ACTIVO,
    EN_ESPERA,
    CANCELADO,
    FINALIZADO
}
