package com.pucp.skillb_ia.model.enums;

// asignacion.estado (chk_asignacion_estado).
// PENDIENTE -> ACTIVA (cuando se cumplen todas las aprobaciones requeridas
// según el origen) | RECHAZADA (si cualquier rol requerido rechaza) -> FINALIZADA
// (el PM o el RM pueden finalizarla directamente, sin aprobación del otro — A18).
public enum EstadoAsignacion {
    PENDIENTE,
    ACTIVA,
    RECHAZADA,
    FINALIZADA
}
