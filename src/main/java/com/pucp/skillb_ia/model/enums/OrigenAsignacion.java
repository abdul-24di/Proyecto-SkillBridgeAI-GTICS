package com.pucp.skillb_ia.model.enums;

// asignacion.origen (chk_asignacion_origen) — los 3 orígenes de la Historia A4.
// Define qué aprobaciones se necesitan (ver EstadoAsignacion y las columnas
// aprobado_por_pm / aprobado_por_rm en Asignacion):
//   PROPUESTA_PM             -> requiere aprobación del RM.
//   PROPUESTA_RM              -> requiere aprobación del PM del proyecto.
//   SOLICITADA_COLABORADOR    -> requiere aprobación de AMBOS (PM y RM).
public enum OrigenAsignacion {
    PROPUESTA_PM,
    PROPUESTA_RM,
    SOLICITADA_COLABORADOR
}
