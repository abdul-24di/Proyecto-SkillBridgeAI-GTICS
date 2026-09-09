package com.pucp.skillb_ia.model.enums;

// actividad.estado_entrega (chk_actividad_entrega) — Épica 9 (Tier 3, diferida).
// La fecha en que el PM confirma la entrega determina A_TIEMPO o TARDIA (C11).
// Solo las horas A_TIEMPO cuentan para el bono (C7/A16); las TARDIA suman a las
// 160 horas del mes pero no generan bono.
public enum EstadoEntrega {
    A_TIEMPO,
    TARDIA
}
