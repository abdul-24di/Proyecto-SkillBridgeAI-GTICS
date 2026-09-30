-- ============================================================================
-- SkillBridge AI - Migracion: unicidad de asignacion solo para asignaciones abiertas
-- Ejecutar sobre una BD existente creada con una version de skillbridge_db_v4.sql
-- que tenga la restriccion uq_asignacion_proyecto_colaborador_estado.
-- Las instalaciones nuevas solo necesitan ejecutar skillbridge_db_v4.sql.
-- Se puede ejecutar mas de una vez: cada paso comprueba si ya se aplico.
--
-- Que hace:
--   1. Agrega la columna generada asignacion.estado_abierto: vale el estado
--      en PENDIENTE/ACTIVA y NULL en RECHAZADA/FINALIZADA.
--   2. Crea uq_asignacion_abierta UNIQUE (proyecto_id, colaborador_id, estado_abierto).
--   3. Elimina uq_asignacion_proyecto_colaborador_estado.
-- Resultado: un colaborador puede tener varias asignaciones RECHAZADA o FINALIZADA
-- en el mismo proyecto (historial), y sigue sin poder tener dos PENDIENTE ni dos
-- ACTIVA. No borra ni modifica registros. El paso 2 no puede fallar por los datos
-- existentes: para PENDIENTE/ACTIVA exige lo mismo que la restriccion anterior.
-- El indice nuevo se crea antes de eliminar el antiguo porque ambos empiezan por
-- proyecto_id y MySQL puede estar usando el antiguo para fk_asignacion_proyecto.
-- Requiere MySQL 5.7 o superior (columnas generadas).
-- ============================================================================

USE skillbridge_db;

-- 1. Columna generada estado_abierto
SET @columna_estado_abierto_existe := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'asignacion'
      AND column_name = 'estado_abierto'
);

SET @sql_migracion_asignacion := IF(
    @columna_estado_abierto_existe = 0,
    'ALTER TABLE asignacion ADD COLUMN estado_abierto VARCHAR(30) GENERATED ALWAYS AS (CASE WHEN estado IN (''PENDIENTE'',''ACTIVA'') THEN estado END) STORED AFTER fecha_finalizacion',
    'SELECT ''La columna estado_abierto ya existe.'' AS informacion'
);

PREPARE stmt_migracion_asignacion FROM @sql_migracion_asignacion;
EXECUTE stmt_migracion_asignacion;
DEALLOCATE PREPARE stmt_migracion_asignacion;

-- 2. Unicidad solo para asignaciones abiertas
SET @indice_abierta_existe := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'asignacion'
      AND index_name = 'uq_asignacion_abierta'
);

SET @sql_migracion_asignacion := IF(
    @indice_abierta_existe = 0,
    'ALTER TABLE asignacion ADD CONSTRAINT uq_asignacion_abierta UNIQUE (proyecto_id, colaborador_id, estado_abierto)',
    'SELECT ''El indice uq_asignacion_abierta ya existe.'' AS informacion'
);

PREPARE stmt_migracion_asignacion FROM @sql_migracion_asignacion;
EXECUTE stmt_migracion_asignacion;
DEALLOCATE PREPARE stmt_migracion_asignacion;

-- 3. Eliminar la restriccion anterior
SET @indice_anterior_existe := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'asignacion'
      AND index_name = 'uq_asignacion_proyecto_colaborador_estado'
);

SET @sql_migracion_asignacion := IF(
    @indice_anterior_existe > 0,
    'ALTER TABLE asignacion DROP INDEX uq_asignacion_proyecto_colaborador_estado',
    'SELECT ''La restriccion uq_asignacion_proyecto_colaborador_estado ya no existe.'' AS informacion'
);

PREPARE stmt_migracion_asignacion FROM @sql_migracion_asignacion;
EXECUTE stmt_migracion_asignacion;
DEALLOCATE PREPARE stmt_migracion_asignacion;

SELECT 'Migracion de unicidad de asignacion aplicada correctamente.' AS resultado;
