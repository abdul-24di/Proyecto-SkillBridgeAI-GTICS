package com.pucp.skillb_ia.model.enums;

// foro.tipo (chk_foro_tipo). GENERAL = foro de comunidad (proyecto_id NULL,
// visible para todos con foro público). PROYECTO = foro asociado a un proyecto
// específico (proyecto_id NOT NULL), sujeto a las reglas de acceso de A7/A9.
public enum TipoForo {
    GENERAL,
    PROYECTO
}
