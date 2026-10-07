package com.pucp.skillb_ia.model.enums;

// usuario.registro_estado — pre-registro de un colaborador invitado por el Admin.
// NULL = colaborador anterior al pre-registro (se considera aprobado).
// Mientras esté PENDIENTE o RECHAZADO el colaborador solo ve la pantalla "Registro en revisión".
public enum EstadoRegistro {
    PENDIENTE,
    APROBADO,
    RECHAZADO
}
