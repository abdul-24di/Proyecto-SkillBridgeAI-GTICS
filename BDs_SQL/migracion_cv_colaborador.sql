-- ============================================================================
-- SkillBridge AI - Migracion: CV del colaborador + revision de experiencia por el Admin
-- Ejecutar una vez sobre una BD existente creada con una version de
-- skillbridge_db_v4.sql cuya tabla usuario no tenga las columnas cv_url/cv_estado/
-- cv_fecha_subida, antes de desplegar la version de la aplicacion que las usa.
-- Las instalaciones nuevas solo necesitan ejecutar skillbridge_db_v4.sql.
-- Se puede ejecutar mas de una vez: comprueba si las columnas ya existen.
--
-- Que hace:
--   Agrega usuario.cv_url (VARCHAR 500), usuario.cv_estado (VARCHAR 20:
--   PENDIENTE o REVISADO) y usuario.cv_fecha_subida (DATETIME). El colaborador
--   sube su CV (reemplaza el anterior si ya tenia uno) y queda en PENDIENTE;
--   el Admin lo revisa, llena su experiencia profesional y lo marca REVISADO.
-- No borra ni modifica registros.
-- ============================================================================

USE skillbridge_db;

SET @columna_cv_url_existe := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'usuario' AND column_name = 'cv_url'
);
SET @sql_cv_url := IF(@columna_cv_url_existe = 0,
    'ALTER TABLE usuario ADD COLUMN cv_url VARCHAR(500) NULL AFTER descripcion',
    'SELECT ''La columna cv_url ya existe.'' AS informacion');
PREPARE stmt_cv_url FROM @sql_cv_url;
EXECUTE stmt_cv_url;
DEALLOCATE PREPARE stmt_cv_url;

SET @columna_cv_estado_existe := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'usuario' AND column_name = 'cv_estado'
);
SET @sql_cv_estado := IF(@columna_cv_estado_existe = 0,
    'ALTER TABLE usuario ADD COLUMN cv_estado VARCHAR(20) NULL AFTER cv_url',
    'SELECT ''La columna cv_estado ya existe.'' AS informacion');
PREPARE stmt_cv_estado FROM @sql_cv_estado;
EXECUTE stmt_cv_estado;
DEALLOCATE PREPARE stmt_cv_estado;

SET @columna_cv_fecha_existe := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'usuario' AND column_name = 'cv_fecha_subida'
);
SET @sql_cv_fecha := IF(@columna_cv_fecha_existe = 0,
    'ALTER TABLE usuario ADD COLUMN cv_fecha_subida DATETIME NULL AFTER cv_estado',
    'SELECT ''La columna cv_fecha_subida ya existe.'' AS informacion');
PREPARE stmt_cv_fecha FROM @sql_cv_fecha;
EXECUTE stmt_cv_fecha;
DEALLOCATE PREPARE stmt_cv_fecha;

-- ----------------------------------------------------------------------------
-- Pre-registro del colaborador invitado (aprobar / rechazar):
--   usuario.registro_estado (PENDIENTE, APROBADO o RECHAZADO; NULL = anterior al
--   pre-registro, se considera aprobado) y usuario.motivo_rechazo (VARCHAR 500,
--   motivo que escribe el Admin al rechazar el registro o el CV).
-- ----------------------------------------------------------------------------
SET @columna_registro_existe := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'usuario' AND column_name = 'registro_estado'
);
SET @sql_registro := IF(@columna_registro_existe = 0,
    'ALTER TABLE usuario ADD COLUMN registro_estado VARCHAR(20) NULL AFTER cv_fecha_subida',
    'SELECT ''La columna registro_estado ya existe.'' AS informacion');
PREPARE stmt_registro FROM @sql_registro;
EXECUTE stmt_registro;
DEALLOCATE PREPARE stmt_registro;

SET @columna_motivo_existe := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'usuario' AND column_name = 'motivo_rechazo'
);
SET @sql_motivo := IF(@columna_motivo_existe = 0,
    'ALTER TABLE usuario ADD COLUMN motivo_rechazo VARCHAR(500) NULL AFTER registro_estado',
    'SELECT ''La columna motivo_rechazo ya existe.'' AS informacion');
PREPARE stmt_motivo FROM @sql_motivo;
EXECUTE stmt_motivo;
DEALLOCATE PREPARE stmt_motivo;
