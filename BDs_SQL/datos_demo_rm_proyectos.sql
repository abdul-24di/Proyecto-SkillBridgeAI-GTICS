-- ============================================================================
-- SkillBridge AI - Datos de demostracion para Proyectos y Presupuesto del RM
-- Compatible con: BDs_SQL/skillbridge_db_v4.sql
-- Base de datos:   MySQL 8.0.19+
-- ============================================================================
--
-- PROPOSITO
--   Cargar un conjunto pequeño y reconocible de datos para probar:
--   * /rm/proyectos
--   * detalle de proyecto
--   * revision, aprobacion y rechazo
--   * asignacion y actualizacion de presupuesto
--   * equipo, vacantes, habilidades y pendientes del RM
--
-- SEGURIDAD
--   * No elimina tablas ni registros normales.
--   * Todos los proyectos y usuarios creados usan el marcador DEMO.
--   * Si se vuelve a ejecutar, restaura SOLO los proyectos [DEMO] a su estado
--     inicial. Esto permite repetir las pruebas de aprobacion y rechazo.
--
-- CREDENCIALES DE DESARROLLO
--   Usuario RM: demo.rm@skillbridge.local
--   Contrasena: abc123
--
-- Antes de ejecutar este archivo debe existir la estructura creada por
-- skillbridge_db_v4.sql. Si la BD ya existía antes del modelo de solicitudes,
-- ejecutar primero migracion_solicitud_personal.sql.
-- ============================================================================
ROLLBACK;

SET NAMES utf8mb4;
USE skillbridge_db;

START TRANSACTION;

-- --------------------------------------------------------------------------
-- 1. Roles requeridos
-- --------------------------------------------------------------------------
INSERT INTO rol (nombre) VALUES
    ('RESOURCE_MANAGER'),
    ('PROJECT_MANAGER'),
    ('COLABORADOR')
AS nuevo
ON DUPLICATE KEY UPDATE nombre = nuevo.nombre;

-- --------------------------------------------------------------------------
-- 2. Usuarios de demostracion
-- La contrasena BCrypt de todos estos usuarios es: abc123
-- --------------------------------------------------------------------------
INSERT INTO usuario (
    correo, password_hash, nombre, apellido, telefono, descripcion,
    rol_id, activo, cargo, horas_contratadas_semana, horas_disponibles,
    anios_experiencia, nivel_experiencia, fecha_contratacion
) VALUES
(
    'demo.rm@skillbridge.local',
    '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom',
    'Rosa', 'Mendoza', '999111222', 'Resource Manager de demostracion.',
    (SELECT id FROM rol WHERE nombre = 'RESOURCE_MANAGER'),
    TRUE, NULL, NULL, NULL, NULL, NULL, NULL
),
(
    'demo.pm@skillbridge.local',
    '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom',
    'Pablo', 'Morales', '999222333', 'Project Manager de demostracion.',
    (SELECT id FROM rol WHERE nombre = 'PROJECT_MANAGER'),
    TRUE, NULL, NULL, NULL, NULL, NULL, NULL
),
(
    'demo.colaborador1@skillbridge.local',
    '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom',
    'Carla', 'Rojas', '999333444', 'Especialista backend para datos de prueba.',
    (SELECT id FROM rol WHERE nombre = 'COLABORADOR'),
    TRUE, 'Backend Developer', 40.00, 16.00, 5.0, 'SENIOR', '2023-03-20'
),
(
    'demo.colaborador2@skillbridge.local',
    '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom',
    'Diego', 'Salazar', '999444555', 'Especialista cloud para datos de prueba.',
    (SELECT id FROM rol WHERE nombre = 'COLABORADOR'),
    TRUE, 'Cloud Engineer', 40.00, 20.00, 4.0, 'SEMI_SENIOR', '2023-08-10'
),
(
    'demo.colaborador3@skillbridge.local',
    '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom',
    'Elena', 'Torres', '999555666', 'Analista de datos para datos de prueba.',
    (SELECT id FROM rol WHERE nombre = 'COLABORADOR'),
    TRUE, 'Data Analyst', 40.00, 32.00, 2.0, 'JUNIOR', '2024-05-06'
) AS nuevo
ON DUPLICATE KEY UPDATE
    password_hash = nuevo.password_hash,
    nombre = nuevo.nombre,
    apellido = nuevo.apellido,
    telefono = nuevo.telefono,
    descripcion = nuevo.descripcion,
    rol_id = nuevo.rol_id,
    activo = TRUE,
    cargo = nuevo.cargo,
    horas_contratadas_semana = nuevo.horas_contratadas_semana,
    horas_disponibles = nuevo.horas_disponibles,
    anios_experiencia = nuevo.anios_experiencia,
    nivel_experiencia = nuevo.nivel_experiencia,
    fecha_contratacion = nuevo.fecha_contratacion;

-- --------------------------------------------------------------------------
-- 3. Categoria y habilidades
-- --------------------------------------------------------------------------
INSERT INTO categoria_habilidad (nombre, descripcion, activa)
VALUES ('[DEMO] Tecnologia', 'Categoria utilizada por los datos de demostracion.', TRUE) AS nuevo
ON DUPLICATE KEY UPDATE descripcion = nuevo.descripcion, activa = TRUE;

INSERT INTO habilidad (nombre, categoria_id, activa) VALUES
    ('Java',       (SELECT id FROM categoria_habilidad WHERE nombre = '[DEMO] Tecnologia'), TRUE),
    ('Spring Boot',(SELECT id FROM categoria_habilidad WHERE nombre = '[DEMO] Tecnologia'), TRUE),
    ('AWS',        (SELECT id FROM categoria_habilidad WHERE nombre = '[DEMO] Tecnologia'), TRUE),
    ('Docker',     (SELECT id FROM categoria_habilidad WHERE nombre = '[DEMO] Tecnologia'), TRUE),
    ('Python',     (SELECT id FROM categoria_habilidad WHERE nombre = '[DEMO] Tecnologia'), TRUE),
    ('Power BI',   (SELECT id FROM categoria_habilidad WHERE nombre = '[DEMO] Tecnologia'), TRUE)
ON DUPLICATE KEY UPDATE activa = TRUE;

-- Habilidades visibles en el directorio de colaboradores.
INSERT INTO colaborador_habilidad (
    colaborador_id, habilidad_id, nivel_dominio, estado_validacion, activo
) VALUES
(
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador1@skillbridge.local'),
    (SELECT h.id FROM habilidad h JOIN categoria_habilidad c ON c.id = h.categoria_id
     WHERE h.nombre = 'Java' AND c.nombre = '[DEMO] Tecnologia'),
    'AVANZADO', 'VALIDADA', TRUE
),
(
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador1@skillbridge.local'),
    (SELECT h.id FROM habilidad h JOIN categoria_habilidad c ON c.id = h.categoria_id
     WHERE h.nombre = 'Spring Boot' AND c.nombre = '[DEMO] Tecnologia'),
    'AVANZADO', 'VALIDADA', TRUE
),
(
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador2@skillbridge.local'),
    (SELECT h.id FROM habilidad h JOIN categoria_habilidad c ON c.id = h.categoria_id
     WHERE h.nombre = 'AWS' AND c.nombre = '[DEMO] Tecnologia'),
    'INTERMEDIO', 'VALIDADA', TRUE
),
(
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador2@skillbridge.local'),
    (SELECT h.id FROM habilidad h JOIN categoria_habilidad c ON c.id = h.categoria_id
     WHERE h.nombre = 'Docker' AND c.nombre = '[DEMO] Tecnologia'),
    'INTERMEDIO', 'VALIDADA', TRUE
),
(
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador3@skillbridge.local'),
    (SELECT h.id FROM habilidad h JOIN categoria_habilidad c ON c.id = h.categoria_id
     WHERE h.nombre = 'Python' AND c.nombre = '[DEMO] Tecnologia'),
    'INTERMEDIO', 'PENDIENTE', TRUE
) AS nuevo
ON DUPLICATE KEY UPDATE
    nivel_dominio = nuevo.nivel_dominio,
    estado_validacion = nuevo.estado_validacion,
    activo = TRUE;

-- Certificados visibles en la bandeja y el historial de validaciones del RM.
INSERT INTO certificado (
    colaborador_id, habilidad_id, archivo_url, estado, motivo_rechazo,
    revisado_por, fecha_subida, fecha_revision
)
SELECT
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador1@skillbridge.local'),
    (SELECT h.id FROM habilidad h JOIN categoria_habilidad c ON c.id = h.categoria_id
     WHERE h.nombre = 'Java' AND c.nombre = '[DEMO] Tecnologia'),
    '/documentos/certificado-demo-java.txt', 'PENDIENTE', NULL, NULL,
    CURRENT_TIMESTAMP - INTERVAL 2 DAY, NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM certificado WHERE archivo_url = '/documentos/certificado-demo-java.txt');

INSERT INTO certificado (
    colaborador_id, habilidad_id, archivo_url, estado, motivo_rechazo,
    revisado_por, fecha_subida, fecha_revision
)
SELECT
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador2@skillbridge.local'),
    (SELECT h.id FROM habilidad h JOIN categoria_habilidad c ON c.id = h.categoria_id
     WHERE h.nombre = 'AWS' AND c.nombre = '[DEMO] Tecnologia'),
    '/documentos/certificado-demo-aws.txt', 'PENDIENTE', NULL, NULL,
    CURRENT_TIMESTAMP - INTERVAL 1 DAY, NULL
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM certificado WHERE archivo_url = '/documentos/certificado-demo-aws.txt');

INSERT INTO certificado (
    colaborador_id, habilidad_id, archivo_url, estado, motivo_rechazo,
    revisado_por, fecha_subida, fecha_revision
)
SELECT
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador1@skillbridge.local'),
    (SELECT h.id FROM habilidad h JOIN categoria_habilidad c ON c.id = h.categoria_id
     WHERE h.nombre = 'Spring Boot' AND c.nombre = '[DEMO] Tecnologia'),
    '/documentos/certificado-demo-spring.txt', 'APROBADO', NULL,
    (SELECT id FROM usuario WHERE correo = 'demo.rm@skillbridge.local'),
    CURRENT_TIMESTAMP - INTERVAL 12 DAY, CURRENT_TIMESTAMP - INTERVAL 10 DAY
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM certificado WHERE archivo_url = '/documentos/certificado-demo-spring.txt');

INSERT INTO certificado (
    colaborador_id, habilidad_id, archivo_url, estado, motivo_rechazo,
    revisado_por, fecha_subida, fecha_revision
)
SELECT
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador3@skillbridge.local'),
    (SELECT h.id FROM habilidad h JOIN categoria_habilidad c ON c.id = h.categoria_id
     WHERE h.nombre = 'Python' AND c.nombre = '[DEMO] Tecnologia'),
    '/documentos/certificado-demo-python.txt', 'RECHAZADO',
    'El documento de demostracion no identifica claramente a la entidad emisora.',
    (SELECT id FROM usuario WHERE correo = 'demo.rm@skillbridge.local'),
    CURRENT_TIMESTAMP - INTERVAL 9 DAY, CURRENT_TIMESTAMP - INTERVAL 8 DAY
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM certificado WHERE archivo_url = '/documentos/certificado-demo-python.txt');

-- --------------------------------------------------------------------------
-- 4. Proyectos de demostracion
-- Se insertan solo cuando todavia no existen.
-- --------------------------------------------------------------------------
INSERT INTO proyecto (
    nombre, descripcion, fecha_inicio, fecha_fin_estimada, estado, prioridad,
    justificacion_prioridad, motivo_rechazo, presupuesto_solicitado,
    justificacion_presupuesto, presupuesto, colaboradores_requeridos,
    horas_semanales_requeridas, pm_id, rm_revisor_id, fecha_creacion
)
SELECT
    '[DEMO] Portal de Atencion',
    'Portal web de autoservicio para clientes y seguimiento de solicitudes.',
    '2026-10-01', '2027-02-28', 'EN_REVISION', 'ALTA',
    'Debe estar disponible antes de la siguiente campana comercial.',
    NULL, 48000.00, 'Infraestructura, desarrollo y pruebas del portal.',
    NULL, 4, 20.00,
    (SELECT id FROM usuario WHERE correo = 'demo.pm@skillbridge.local'),
    NULL, CURRENT_TIMESTAMP - INTERVAL 4 DAY
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM proyecto WHERE nombre = '[DEMO] Portal de Atencion');

INSERT INTO proyecto (
    nombre, descripcion, fecha_inicio, fecha_fin_estimada, estado, prioridad,
    justificacion_prioridad, presupuesto_solicitado, justificacion_presupuesto,
    presupuesto, colaboradores_requeridos, horas_semanales_requeridas,
    pm_id, rm_revisor_id, fecha_creacion
)
SELECT
    '[DEMO] Migracion Cloud',
    'Migracion progresiva de servicios internos hacia infraestructura AWS.',
    '2026-09-01', '2027-01-31', 'ACTIVO', 'ALTA',
    'Reduce riesgos operativos y costos de infraestructura local.',
    58000.00, 'Servicios cloud, soporte y automatizacion.',
    60000.00, 3, 20.00,
    (SELECT id FROM usuario WHERE correo = 'demo.pm@skillbridge.local'),
    (SELECT id FROM usuario WHERE correo = 'demo.rm@skillbridge.local'),
    CURRENT_TIMESTAMP - INTERVAL 20 DAY
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM proyecto WHERE nombre = '[DEMO] Migracion Cloud');

INSERT INTO proyecto (
    nombre, descripcion, fecha_inicio, fecha_fin_estimada, estado, prioridad,
    justificacion_prioridad, presupuesto, colaboradores_requeridos,
    horas_semanales_requeridas, pm_id, rm_revisor_id, fecha_creacion
)
SELECT
    '[DEMO] Analitica Comercial',
    'Panel de indicadores de ventas y comportamiento de clientes.',
    '2026-11-01', '2027-04-30', 'EN_ESPERA', 'MEDIA',
    'Permitira centralizar indicadores actualmente dispersos.',
    38000.00, 3, 16.00,
    (SELECT id FROM usuario WHERE correo = 'demo.pm@skillbridge.local'),
    (SELECT id FROM usuario WHERE correo = 'demo.rm@skillbridge.local'),
    CURRENT_TIMESTAMP - INTERVAL 12 DAY
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM proyecto WHERE nombre = '[DEMO] Analitica Comercial');

INSERT INTO proyecto (
    nombre, descripcion, estado, prioridad, justificacion_prioridad,
    motivo_rechazo, presupuesto_solicitado, colaboradores_requeridos,
    horas_semanales_requeridas, pm_id, rm_revisor_id, fecha_creacion
)
SELECT
    '[DEMO] Proyecto Rechazado',
    'Proyecto de ejemplo para verificar estados no editables.',
    'RECHAZADO', 'BAJA', 'Iniciativa exploratoria sin impacto inmediato.',
    'El alcance y los beneficios necesitan mayor precision.',
    15000.00, 2, 12.00,
    (SELECT id FROM usuario WHERE correo = 'demo.pm@skillbridge.local'),
    (SELECT id FROM usuario WHERE correo = 'demo.rm@skillbridge.local'),
    CURRENT_TIMESTAMP - INTERVAL 30 DAY
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM proyecto WHERE nombre = '[DEMO] Proyecto Rechazado');

INSERT INTO proyecto (
    nombre, descripcion, fecha_inicio, fecha_fin_estimada, estado, prioridad,
    justificacion_prioridad, presupuesto, colaboradores_requeridos,
    horas_semanales_requeridas, pm_id, rm_revisor_id, fecha_creacion
)
SELECT
    '[DEMO] Proyecto Finalizado',
    'Proyecto concluido para comprobar la consulta historica.',
    '2026-01-10', '2026-07-30', 'FINALIZADO', 'BAJA',
    'Proyecto interno de alcance controlado.',
    25000.00, 2, 10.00,
    (SELECT id FROM usuario WHERE correo = 'demo.pm@skillbridge.local'),
    (SELECT id FROM usuario WHERE correo = 'demo.rm@skillbridge.local'),
    CURRENT_TIMESTAMP - INTERVAL 180 DAY
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM proyecto WHERE nombre = '[DEMO] Proyecto Finalizado');

-- Restaurar SOLO los proyectos DEMO para poder repetir las pruebas.
-- Se buscan primero sus IDs para que tambien funcione con SQL_SAFE_UPDATES.
SET @demo_portal_id := (SELECT MIN(id) FROM proyecto WHERE nombre = '[DEMO] Portal de Atencion');
SET @demo_migracion_id := (SELECT MIN(id) FROM proyecto WHERE nombre = '[DEMO] Migracion Cloud');
SET @demo_analitica_id := (SELECT MIN(id) FROM proyecto WHERE nombre = '[DEMO] Analitica Comercial');
SET @demo_rechazado_id := (SELECT MIN(id) FROM proyecto WHERE nombre = '[DEMO] Proyecto Rechazado');
SET @demo_finalizado_id := (SELECT MIN(id) FROM proyecto WHERE nombre = '[DEMO] Proyecto Finalizado');
SET @demo_rm_id := (SELECT id FROM usuario WHERE correo = 'demo.rm@skillbridge.local');

UPDATE proyecto SET
    estado = 'EN_REVISION', presupuesto = NULL, motivo_rechazo = NULL,
    rm_revisor_id = NULL
WHERE id = @demo_portal_id
LIMIT 1;

UPDATE proyecto SET
    estado = 'ACTIVO', presupuesto = 60000.00, motivo_rechazo = NULL,
    rm_revisor_id = @demo_rm_id
WHERE id = @demo_migracion_id
LIMIT 1;

UPDATE proyecto SET
    estado = 'EN_ESPERA', presupuesto = 38000.00, motivo_rechazo = NULL,
    rm_revisor_id = @demo_rm_id
WHERE id = @demo_analitica_id
LIMIT 1;

UPDATE proyecto SET
    estado = 'RECHAZADO', presupuesto = NULL,
    motivo_rechazo = 'El alcance y los beneficios necesitan mayor precision.',
    rm_revisor_id = @demo_rm_id
WHERE id = @demo_rechazado_id
LIMIT 1;

UPDATE proyecto SET
    estado = 'FINALIZADO', presupuesto = 25000.00,
    rm_revisor_id = @demo_rm_id
WHERE id = @demo_finalizado_id
LIMIT 1;

-- --------------------------------------------------------------------------
-- 5. Habilidades requeridas por proyecto
-- --------------------------------------------------------------------------
INSERT INTO proyecto_habilidad_requerida (
    proyecto_id, habilidad_id, nivel_requerido, cantidad_personas
) VALUES
(
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Portal de Atencion'),
    (SELECT h.id FROM habilidad h JOIN categoria_habilidad c ON c.id = h.categoria_id
     WHERE h.nombre = 'Java' AND c.nombre = '[DEMO] Tecnologia'),
    'AVANZADO', 2
),
(
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Portal de Atencion'),
    (SELECT h.id FROM habilidad h JOIN categoria_habilidad c ON c.id = h.categoria_id
     WHERE h.nombre = 'Spring Boot' AND c.nombre = '[DEMO] Tecnologia'),
    'AVANZADO', 2
),
(
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Migracion Cloud'),
    (SELECT h.id FROM habilidad h JOIN categoria_habilidad c ON c.id = h.categoria_id
     WHERE h.nombre = 'AWS' AND c.nombre = '[DEMO] Tecnologia'),
    'AVANZADO', 2
),
(
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Migracion Cloud'),
    (SELECT h.id FROM habilidad h JOIN categoria_habilidad c ON c.id = h.categoria_id
     WHERE h.nombre = 'Docker' AND c.nombre = '[DEMO] Tecnologia'),
    'INTERMEDIO', 1
),
(
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Analitica Comercial'),
    (SELECT h.id FROM habilidad h JOIN categoria_habilidad c ON c.id = h.categoria_id
     WHERE h.nombre = 'Python' AND c.nombre = '[DEMO] Tecnologia'),
    'INTERMEDIO', 2
),
(
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Analitica Comercial'),
    (SELECT h.id FROM habilidad h JOIN categoria_habilidad c ON c.id = h.categoria_id
     WHERE h.nombre = 'Power BI' AND c.nombre = '[DEMO] Tecnologia'),
    'INTERMEDIO', 1
) AS nuevo
ON DUPLICATE KEY UPDATE
    nivel_requerido = nuevo.nivel_requerido,
    cantidad_personas = nuevo.cantidad_personas;

-- --------------------------------------------------------------------------
-- 6. Solicitudes de personal: ejemplos de sus tres estados visibles
-- --------------------------------------------------------------------------
INSERT INTO solicitud_personal (
    proyecto_id, cantidad_colaboradores, perfiles_requeridos, mensaje_pm,
    estado, rm_responsable_id, fecha_solicitud,
    fecha_inicio_atencion, fecha_atencion
)
SELECT
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Migracion Cloud'),
    1,
    'Cloud Engineer con experiencia intermedia o avanzada en AWS y Docker.',
    'Se necesita completar el equipo para la siguiente fase de migracion.',
    'PENDIENTE', NULL, CURRENT_TIMESTAMP - INTERVAL 3 DAY, NULL, NULL
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM solicitud_personal
    WHERE mensaje_pm = 'Se necesita completar el equipo para la siguiente fase de migracion.'
);

INSERT INTO solicitud_personal (
    proyecto_id, cantidad_colaboradores, perfiles_requeridos, mensaje_pm,
    estado, rm_responsable_id, fecha_solicitud,
    fecha_inicio_atencion, fecha_atencion
)
SELECT
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Analitica Comercial'),
    2,
    'Data Analyst con Python y especialista en visualizacion con Power BI.',
    'Priorizar disponibilidad para iniciar durante este mes.',
    'EN_ATENCION',
    (SELECT id FROM usuario WHERE correo = 'demo.rm@skillbridge.local'),
    CURRENT_TIMESTAMP - INTERVAL 6 DAY,
    CURRENT_TIMESTAMP - INTERVAL 5 DAY,
    NULL
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM solicitud_personal
    WHERE mensaje_pm = 'Priorizar disponibilidad para iniciar durante este mes.'
);

INSERT INTO solicitud_personal (
    proyecto_id, cantidad_colaboradores, perfiles_requeridos, mensaje_pm,
    estado, rm_responsable_id, fecha_solicitud,
    fecha_inicio_atencion, fecha_atencion
)
SELECT
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Finalizado'),
    1,
    'Analista de datos junior.',
    'Solicitud historica para comprobar el estado atendido.',
    'ATENDIDA',
    (SELECT id FROM usuario WHERE correo = 'demo.rm@skillbridge.local'),
    CURRENT_TIMESTAMP - INTERVAL 170 DAY,
    CURRENT_TIMESTAMP - INTERVAL 169 DAY,
    CURRENT_TIMESTAMP - INTERVAL 165 DAY
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM solicitud_personal
    WHERE mensaje_pm = 'Solicitud historica para comprobar el estado atendido.'
);

-- Restaurar los estados de las solicitudes DEMO sin desactivar SQL_SAFE_UPDATES.
SET @demo_sol_migracion_id := (
    SELECT MIN(id) FROM solicitud_personal
    WHERE mensaje_pm = 'Se necesita completar el equipo para la siguiente fase de migracion.'
);
SET @demo_sol_analitica_id := (
    SELECT MIN(id) FROM solicitud_personal
    WHERE mensaje_pm = 'Priorizar disponibilidad para iniciar durante este mes.'
);
SET @demo_sol_finalizada_id := (
    SELECT MIN(id) FROM solicitud_personal
    WHERE mensaje_pm = 'Solicitud historica para comprobar el estado atendido.'
);

UPDATE solicitud_personal
SET estado = 'PENDIENTE', rm_responsable_id = NULL,
    fecha_inicio_atencion = NULL, fecha_atencion = NULL
WHERE id = @demo_sol_migracion_id
LIMIT 1;

UPDATE solicitud_personal
SET estado = 'EN_ATENCION', rm_responsable_id = @demo_rm_id,
    fecha_inicio_atencion = CURRENT_TIMESTAMP - INTERVAL 5 DAY,
    fecha_atencion = NULL
WHERE id = @demo_sol_analitica_id
LIMIT 1;

UPDATE solicitud_personal
SET estado = 'ATENDIDA', rm_responsable_id = @demo_rm_id,
    fecha_inicio_atencion = CURRENT_TIMESTAMP - INTERVAL 169 DAY,
    fecha_atencion = CURRENT_TIMESTAMP - INTERVAL 165 DAY
WHERE id = @demo_sol_finalizada_id
LIMIT 1;

-- --------------------------------------------------------------------------
-- 7. Asignaciones: ejemplos para cada bandeja del RM
-- --------------------------------------------------------------------------
INSERT INTO asignacion (
    proyecto_id, colaborador_id, horas_semanales, origen, mensaje_solicitud,
    estado, aprobado_por_pm, aprobado_por_rm, fecha_aprobacion_pm,
    fecha_aprobacion_rm, rechazado_por, desasignado_por, motivo_rechazo,
    motivo_finalizacion, fecha_solicitud, fecha_activacion, fecha_finalizacion
) VALUES
(
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Migracion Cloud'),
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador1@skillbridge.local'),
    20.00, 'PROPUESTA_PM', 'Asignacion activa de demostracion.',
    'ACTIVA', TRUE, TRUE, CURRENT_TIMESTAMP - INTERVAL 18 DAY,
    CURRENT_TIMESTAMP - INTERVAL 17 DAY, NULL, NULL, NULL, NULL,
    CURRENT_TIMESTAMP - INTERVAL 19 DAY, CURRENT_TIMESTAMP - INTERVAL 17 DAY, NULL
),
(
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Migracion Cloud'),
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador2@skillbridge.local'),
    20.00, 'PROPUESTA_RM', 'Asignacion activa de demostracion.',
    'ACTIVA', TRUE, TRUE, CURRENT_TIMESTAMP - INTERVAL 15 DAY,
    CURRENT_TIMESTAMP - INTERVAL 16 DAY, NULL, NULL, NULL, NULL,
    CURRENT_TIMESTAMP - INTERVAL 17 DAY, CURRENT_TIMESTAMP - INTERVAL 15 DAY, NULL
),
(
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Portal de Atencion'),
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador3@skillbridge.local'),
    16.00, 'PROPUESTA_PM', 'Solicitud pendiente para probar la bandeja del RM.',
    'PENDIENTE', TRUE, FALSE, CURRENT_TIMESTAMP - INTERVAL 2 DAY,
    NULL, NULL, NULL, NULL, NULL, CURRENT_TIMESTAMP - INTERVAL 3 DAY, NULL, NULL
),
(
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Analitica Comercial'),
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador2@skillbridge.local'),
    12.00, 'PROPUESTA_RM', 'Propuesta del RM pendiente de decision del PM.',
    'PENDIENTE', FALSE, TRUE, NULL,
    CURRENT_TIMESTAMP - INTERVAL 2 DAY, NULL, NULL, NULL, NULL,
    CURRENT_TIMESTAMP - INTERVAL 2 DAY, NULL, NULL
),
(
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Rechazado'),
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador1@skillbridge.local'),
    10.00, 'SOLICITADA_COLABORADOR', 'Solicitud historica rechazada de demostracion.',
    'RECHAZADA', FALSE, FALSE, NULL, NULL,
    (SELECT id FROM usuario WHERE correo = 'demo.rm@skillbridge.local'),
    NULL, 'No coincide con las habilidades requeridas.', NULL,
    CURRENT_TIMESTAMP - INTERVAL 25 DAY, NULL, NULL
),
(
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Finalizado'),
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador3@skillbridge.local'),
    10.00, 'PROPUESTA_PM', 'Participacion historica finalizada de demostracion.',
    'FINALIZADA', TRUE, TRUE, CURRENT_TIMESTAMP - INTERVAL 160 DAY,
    CURRENT_TIMESTAMP - INTERVAL 159 DAY, NULL,
    (SELECT id FROM usuario WHERE correo = 'demo.rm@skillbridge.local'),
    NULL, 'OTRO', CURRENT_TIMESTAMP - INTERVAL 165 DAY,
    CURRENT_TIMESTAMP - INTERVAL 159 DAY, CURRENT_TIMESTAMP - INTERVAL 30 DAY
) AS nuevo
ON DUPLICATE KEY UPDATE
    horas_semanales = nuevo.horas_semanales,
    origen = nuevo.origen,
    mensaje_solicitud = nuevo.mensaje_solicitud,
    aprobado_por_pm = nuevo.aprobado_por_pm,
    aprobado_por_rm = nuevo.aprobado_por_rm;

COMMIT;

-- --------------------------------------------------------------------------
-- 8. Comprobacion rapida
-- Debe devolver cinco proyectos y sus datos principales.
-- --------------------------------------------------------------------------
SELECT
    p.id,
    p.nombre,
    p.estado,
    p.prioridad,
    p.presupuesto_solicitado,
    p.presupuesto,
    CONCAT(pm.nombre, ' ', pm.apellido) AS project_manager
FROM proyecto p
JOIN usuario pm ON pm.id = p.pm_id
WHERE p.nombre LIKE '[DEMO]%'
ORDER BY p.fecha_creacion DESC;
