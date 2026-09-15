-- ============================================================================
-- SkillBridge AI - Migracion: motivo de respuesta en inscripciones a cursos
-- Ejecutar UNA VEZ sobre una BD existente creada con skillbridge_db_v4.sql
-- antes de incorporar el flujo completo de Cursos del RM.
-- Las instalaciones nuevas solo necesitan ejecutar skillbridge_db_v4.sql.
-- ============================================================================

USE skillbridge_db;

SET @columna_motivo_existe := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'colaborador_curso'
      AND column_name = 'motivo_respuesta'
);

SET @sql_migracion_cursos := IF(
    @columna_motivo_existe = 0,
    'ALTER TABLE colaborador_curso ADD COLUMN motivo_respuesta VARCHAR(500) NULL AFTER fecha_completado',
    'SELECT ''La columna motivo_respuesta ya existe.'' AS informacion'
);

PREPARE stmt_migracion_cursos FROM @sql_migracion_cursos;
EXECUTE stmt_migracion_cursos;
DEALLOCATE PREPARE stmt_migracion_cursos;

SELECT 'Migracion de Cursos aplicada correctamente.' AS resultado;
