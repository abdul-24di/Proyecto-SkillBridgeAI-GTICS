-- ============================================================================
-- SkillBridge AI - Actualización de una base de datos existente (2026-10-21)
--
-- Para quien creó skillbridge_db antes del cierre de proyectos del RM: agrega lo que
-- skillbridge_db_v4.sql ya trae, sin borrar datos. Una instalación nueva no lo necesita.
--   1. proyecto.motivo_cierre (motivo que escribe el RM al cerrar un proyecto)
--   2. PROYECTO_FINALIZADO como motivo de finalización válido de una asignación
--
-- Sin esto, las vistas que cargan proyectos fallan con "Unknown column 'motivo_cierre'".
-- Se puede ejecutar más de una vez: cada paso revisa antes si ya está aplicado.
-- ============================================================================

USE skillbridge_db;

-- 1. Columna proyecto.motivo_cierre
SET @falta_columna = (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'proyecto'
                        AND COLUMN_NAME = 'motivo_cierre');
SET @sql = IF(@falta_columna,
              'ALTER TABLE proyecto ADD COLUMN motivo_cierre VARCHAR(500) NULL AFTER motivo_rechazo',
              'DO 0');
PREPARE paso FROM @sql;
EXECUTE paso;
DEALLOCATE PREPARE paso;

-- 2. Regla de motivos de finalización con PROYECTO_FINALIZADO
SET @falta_motivo = (SELECT COUNT(*) = 0 FROM information_schema.CHECK_CONSTRAINTS
                     WHERE CONSTRAINT_SCHEMA = DATABASE()
                       AND CONSTRAINT_NAME = 'chk_asignacion_finalizacion'
                       AND CHECK_CLAUSE LIKE '%PROYECTO_FINALIZADO%');
SET @sql = IF(@falta_motivo, 'ALTER TABLE asignacion DROP CHECK chk_asignacion_finalizacion', 'DO 0');
PREPARE paso FROM @sql;
EXECUTE paso;
DEALLOCATE PREPARE paso;

SET @sql = IF(@falta_motivo,
              'ALTER TABLE asignacion ADD CONSTRAINT chk_asignacion_finalizacion CHECK (motivo_finalizacion IS NULL OR motivo_finalizacion IN (''BAJO_DESEMPENO'',''PROYECTO_CANCELADO'',''PROYECTO_FINALIZADO'',''OTRO''))',
              'DO 0');
PREPARE paso FROM @sql;
EXECUTE paso;
DEALLOCATE PREPARE paso;
