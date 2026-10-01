-- ============================================================================
-- SkillBridge AI - Migracion: nivel aprobado en cada certificado (TASK-042)
-- Ejecutar una vez sobre una BD existente creada con una version de
-- skillbridge_db_v4.sql cuya tabla certificado no tenga la columna nivel_aprobado,
-- antes de desplegar la version de la aplicacion que la usa.
-- Las instalaciones nuevas solo necesitan ejecutar skillbridge_db_v4.sql.
-- Se puede ejecutar mas de una vez: comprueba si la columna ya existe.
--
-- Que hace:
--   Agrega certificado.nivel_aprobado VARCHAR(20) NULL (BASICO, INTERMEDIO o
--   AVANZADO), el nivel de dominio con el que el RM aprobo el certificado.
-- No borra ni modifica registros. No rellena los certificados existentes: los
-- aprobados antes de esta migracion quedan con NULL y el historial los muestra
-- como "Sin registro"; los pendientes y rechazados tambien quedan con NULL.
-- ============================================================================

USE skillbridge_db;

SET @columna_nivel_aprobado_existe := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'certificado'
      AND column_name = 'nivel_aprobado'
);

SET @sql_migracion_certificado := IF(
    @columna_nivel_aprobado_existe = 0,
    'ALTER TABLE certificado ADD COLUMN nivel_aprobado VARCHAR(20) NULL AFTER motivo_rechazo',
    'SELECT ''La columna nivel_aprobado ya existe.'' AS informacion'
);

PREPARE stmt_migracion_certificado FROM @sql_migracion_certificado;
EXECUTE stmt_migracion_certificado;
DEALLOCATE PREPARE stmt_migracion_certificado;
