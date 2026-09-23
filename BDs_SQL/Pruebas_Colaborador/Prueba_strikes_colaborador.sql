-- ============================================================
-- Datos adicionales: foro/documento en proyecto finalizado 
-- y actividades para probarevidencia + strikes
-- ============================================================
USE skillbridge_db;

-- Borramos sus strikes de prueba anteriores
DELETE FROM penalizacion
WHERE colaborador_id = (SELECT id FROM usuario WHERE correo = 'demo.colaborador2@skillbridge.local')
  AND proyecto_id = (SELECT id FROM proyecto WHERE nombre = '[DEMO] Migracion Cloud');

-- Lo regresamos a ACTIVA (por si quedó FINALIZADA por bajo desempeño)
UPDATE asignacion
SET estado = 'ACTIVA', motivo_finalizacion = NULL, fecha_finalizacion = NULL
WHERE proyecto_id = (SELECT id FROM proyecto WHERE nombre = '[DEMO] Migracion Cloud')
  AND colaborador_id = (SELECT id FROM usuario WHERE correo = 'demo.colaborador2@skillbridge.local')
  AND estado = 'FINALIZADA';

-- Borramos las notificaciones viejas para que la campanita empiece limpia
DELETE FROM notificacion
WHERE id IN (
    SELECT id FROM (
        SELECT n.id
        FROM notificacion n
        JOIN usuario u ON u.id = n.usuario_id
        WHERE u.correo IN (
            'demo.colaborador2@skillbridge.local',
            'demo.colaborador3@skillbridge.local',
            'demo.pm@skillbridge.local'
        )
    ) AS tmp
);


-- //---------------------------------------------

-- Actividad nueva para colaborador3, con fecha límite futura
INSERT INTO actividad (proyecto_id, colaborador_id, titulo, descripcion, horas_estimadas,
                       fecha_limite, estado, creado_por)
SELECT
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Migracion Cloud'),
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador3@skillbridge.local'),
    '[DEMO] Probar flujo de notificaciones',
    'Actividad creada para probar el sistema de notificaciones end-to-end.',
    4.00,
    DATE_ADD(CURRENT_DATE, INTERVAL 7 DAY),
    'PENDIENTE',
    (SELECT id FROM usuario WHERE correo = 'demo.pm@skillbridge.local')
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM actividad WHERE titulo = '[DEMO] Probar flujo de notificaciones');
-- //-----------------------------------



-- 1) Foro y documento en el proyecto YA FINALIZADO, para comprobar que ahora
--    sí se pueden ver (solo lectura) tras el fix.
INSERT INTO foro (proyecto_id, tipo, es_publico, nombre, fecha_creacion)
SELECT (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Finalizado'),
       'PROYECTO', FALSE, '[DEMO] Foro Proyecto Finalizado', CURRENT_TIMESTAMP - INTERVAL 100 DAY
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM foro WHERE nombre = '[DEMO] Foro Proyecto Finalizado');

INSERT INTO documento (proyecto_id, subido_por_id, nombre, categoria, archivo_url, activo, fecha_creacion)
SELECT
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Finalizado'),
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador3@skillbridge.local'),
    '[DEMO] Acta de cierre.pdf', 'PDF', '/uploads/documentos/demo-cierre.pdf', TRUE, CURRENT_TIMESTAMP - INTERVAL 31 DAY
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM documento WHERE nombre = '[DEMO] Acta de cierre.pdf');

-- 2) demo.colaborador1 (ACTIVA en [DEMO] Migracion Cloud):

-- 2a) Actividad pendiente sin vencer -> clic -> "Marcar listo" con evidencia
INSERT INTO actividad (proyecto_id, colaborador_id, titulo, descripcion, horas_estimadas,
                       fecha_limite, estado, creado_por)
SELECT
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Migracion Cloud'),
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador1@skillbridge.local'),
    '[DEMO] Documentar plan de rollback',
    'Redactar el procedimiento de reversión en caso de fallo en la migración.',
    6.00,
    DATE_ADD(CURRENT_DATE, INTERVAL 5 DAY),
    'PENDIENTE',
    (SELECT id FROM usuario WHERE correo = 'demo.pm@skillbridge.local')
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM actividad WHERE titulo = '[DEMO] Documentar plan de rollback');

-- 2b) Ya marcada "lista para revisar" con evidencia -> aparece en la bandeja del PM
INSERT INTO actividad (proyecto_id, colaborador_id, titulo, descripcion, horas_estimadas,
                       fecha_limite, fecha_marcado_revision, evidencia_url, comentario_colaborador,
                       estado, creado_por)
SELECT
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Migracion Cloud'),
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador1@skillbridge.local'),
    '[DEMO] Migrar base de datos de staging',
    'Migrar la base de datos del ambiente de staging al nuevo proveedor cloud.',
    12.00,
    DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY),
    CURRENT_TIMESTAMP - INTERVAL 1 DAY,
    '/uploads/evidencias-actividades/demo-migracion-staging.pdf',
    'Migración completada, adjunto el log sin errores.',
    'EN_REVISION',
    (SELECT id FROM usuario WHERE correo = 'demo.pm@skillbridge.local')
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM actividad WHERE titulo = '[DEMO] Migrar base de datos de staging');

-- 3) demo.colaborador2 (ACTIVA en [DEMO] Migracion Cloud): ya tiene 2 strikes
--    previos + 1 actividad vencida sin entregar -> al ver su proyecto o sus
--    actividades, debe aplicarse el 3er strike y expulsarlo automáticamente.

INSERT INTO actividad (proyecto_id, colaborador_id, titulo, descripcion, horas_estimadas,
                       fecha_limite, estado, creado_por)
SELECT
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Migracion Cloud'),
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador2@skillbridge.local'),
    '[DEMO] Configurar balanceador de carga',
    'Configurar el balanceador de carga para el nuevo cluster productivo.',
    10.00,
    DATE_SUB(CURRENT_DATE, INTERVAL 3 DAY),
    'PENDIENTE',
    (SELECT id FROM usuario WHERE correo = 'demo.pm@skillbridge.local')
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM actividad WHERE titulo = '[DEMO] Configurar balanceador de carga');

INSERT INTO penalizacion (colaborador_id, proyecto_id, tipo, motivo, monto, fecha)
SELECT
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador2@skillbridge.local'),
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Migracion Cloud'),
    'ACTIVIDAD_TARDIA',
    '[DEMO] Strike de prueba #1: entregó una actividad fuera de plazo.',
    1.00, CURRENT_TIMESTAMP - INTERVAL 10 DAY
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM penalizacion WHERE motivo = '[DEMO] Strike de prueba #1: entregó una actividad fuera de plazo.');

INSERT INTO penalizacion (colaborador_id, proyecto_id, tipo, motivo, monto, fecha)
SELECT
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador2@skillbridge.local'),
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Migracion Cloud'),
    'ACTIVIDAD_TARDIA',
    '[DEMO] Strike de prueba #2: el PM devolvió una actividad mal hecha.',
    1.00, CURRENT_TIMESTAMP - INTERVAL 5 DAY
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM penalizacion WHERE motivo = '[DEMO] Strike de prueba #2: el PM devolvió una actividad mal hecha.');