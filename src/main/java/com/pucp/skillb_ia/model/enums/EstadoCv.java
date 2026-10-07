package com.pucp.skillb_ia.model.enums;

// usuario.cv_estado — solo aplica a colaboradores con un CV subido (usuario.cv_url != NULL).
// REVISADO = el Admin lo aprobó (y completó la experiencia profesional); RECHAZADO = lo devolvió
// con observaciones (usuario.motivo_rechazo) para que el colaborador suba uno corregido.
public enum EstadoCv {
    PENDIENTE,
    REVISADO,
    RECHAZADO
}
