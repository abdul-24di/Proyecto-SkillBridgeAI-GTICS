package com.pucp.skillb_ia.model.enums;

// actividad.estado (chk_actividad_estado) — Épica 9 (Tier 3, diferida). Flujo de
// 2 pasos (C11): el colaborador marca EN_REVISION ("Listo para revisar"), el PM
// confirma (-> COMPLETADA) o devuelve (vuelve a EN_PROGRESO) con comentario.
public enum EstadoActividad {
    PENDIENTE,
    EN_PROGRESO,
    EN_REVISION,
    COMPLETADA
}
