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
--   * sueldo base, costo semanal y costo total de cada asignacion
--   * presupuesto comprometido, reservado y disponible
--   * asignaciones con presupuesto suficiente e insuficiente
--   * solicitudes de colaborador pendientes visibles en el dashboard
--   * asignacion y actualizacion de presupuesto
--   * equipo, vacantes, habilidades y pendientes del RM
--   * foros, publicaciones, respuestas, soluciones y votos de solo lectura
--   * catalogo, solicitudes e inscripciones de cursos
--   * evidencias de finalizacion de cursos por revisar, su notificacion
--     y un curso ya completado (seccion 10.1)
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
-- Si la BD ya existía antes del flujo completo de Cursos, ejecutar también
-- migracion_cursos.sql antes de cargar estos datos.
-- La seccion 10.1 usa las columnas colaborador_curso.evidencia_url y
-- fecha_evidencia y el estado EVIDENCIA_PENDIENTE (v4 desde el 2026-09-30).
-- Aun no existe migracion para bases anteriores: reinstalar con
-- skillbridge_db_v4.sql si esas columnas no existen.
-- ============================================================================
ROLLBACK;

SET NAMES utf8mb4;
USE skillbridge_db;

START TRANSACTION;

-- --------------------------------------------------------------------------
-- 1. Roles requeridos
-- --------------------------------------------------------------------------
INSERT INTO rol (nombre) VALUES
    ('ADMINISTRADOR'),
    ('RESOURCE_MANAGER'),
    ('PROJECT_MANAGER'),
    ('COLABORADOR')
AS nuevo
ON DUPLICATE KEY UPDATE nombre = nuevo.nombre;

-- --------------------------------------------------------------------------
-- 1.5 Cargos usados por los colaboradores demo
-- Si el cargo ya existe se conservan sus tarifas actuales.
-- --------------------------------------------------------------------------
INSERT INTO cargo (nombre, sueldo_junior, sueldo_semi_senior, sueldo_senior) VALUES
    ('Backend Developer', 2000.00, 3500.00, 5000.00),
    ('DevOps Engineer', 2500.00, 4000.00, 6000.00),
    ('Cloud Engineer', 5000.00, 8000.00, 11000.00),
    ('Data Analyst', 4800.00, 6500.00, 8500.00)
AS nuevo
ON DUPLICATE KEY UPDATE nombre = nuevo.nombre;

-- --------------------------------------------------------------------------
-- 2. Usuarios de demostracion
-- La contrasena BCrypt de todos estos usuarios es: abc123
-- --------------------------------------------------------------------------
INSERT INTO usuario (
    correo, password_hash, nombre, apellido, telefono, descripcion,
    rol_id, activo, cargo_id, horas_contratadas_semana, horas_disponibles,
    sueldo_base, anios_experiencia, nivel_experiencia, fecha_contratacion
) VALUES
(
    'demo.admin@skillbridge.local',
    '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom',
    'Adriana', 'Campos', '999000111', 'Administradora de demostracion.',
    (SELECT id FROM rol WHERE nombre = 'ADMINISTRADOR'),
    TRUE, NULL, NULL, NULL, NULL, NULL, NULL, NULL
),
(
    'demo.rm@skillbridge.local',
    '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom',
    'Rosa', 'Mendoza', '999111222', 'Resource Manager de demostracion.',
    (SELECT id FROM rol WHERE nombre = 'RESOURCE_MANAGER'),
    TRUE, NULL, NULL, NULL, NULL, NULL, NULL, NULL
),
(
    'demo.pm@skillbridge.local',
    '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom',
    'Pablo', 'Morales', '999222333', 'Project Manager de demostracion.',
    (SELECT id FROM rol WHERE nombre = 'PROJECT_MANAGER'),
    TRUE, NULL, NULL, NULL, NULL, NULL, NULL, NULL
),
(
    'demo.colaborador1@skillbridge.local',
    '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom',
    'Carla', 'Rojas', '999333444', 'Especialista backend para datos de prueba.',
    (SELECT id FROM rol WHERE nombre = 'COLABORADOR'),
    TRUE, (SELECT id FROM cargo WHERE nombre = 'Backend Developer'), 40.00, 16.00, 7600.00, 5.0, 'SENIOR', '2023-03-20'
),
(
    'demo.colaborador2@skillbridge.local',
    '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom',
    'Diego', 'Salazar', '999444555', 'Especialista cloud para datos de prueba.',
    (SELECT id FROM rol WHERE nombre = 'COLABORADOR'),
    TRUE, (SELECT id FROM cargo WHERE nombre = 'Cloud Engineer'), 40.00, 20.00, 8000.00, 4.0, 'SEMI_SENIOR', '2023-08-10'
),
(
    'demo.colaborador3@skillbridge.local',
    '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom',
    'Elena', 'Torres', '999555666', 'Analista de datos para datos de prueba.',
    (SELECT id FROM rol WHERE nombre = 'COLABORADOR'),
    TRUE, (SELECT id FROM cargo WHERE nombre = 'Data Analyst'), 40.00, 32.00, 4800.00, 2.0, 'JUNIOR', '2024-05-06'
),
(
    'demo.colaborador4@skillbridge.local',
    '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom',
    'Mateo', 'Vega', '999666777', 'Especialista DevOps con sueldo alto para probar el bloqueo presupuestario.',
    (SELECT id FROM rol WHERE nombre = 'COLABORADOR'),
    TRUE, (SELECT id FROM cargo WHERE nombre = 'DevOps Engineer'), 40.00, 40.00, 10000.00, 6.0, 'SENIOR', '2022-11-14'
) AS nuevo
ON DUPLICATE KEY UPDATE
    password_hash = nuevo.password_hash,
    nombre = nuevo.nombre,
    apellido = nuevo.apellido,
    telefono = nuevo.telefono,
    descripcion = nuevo.descripcion,
    rol_id = nuevo.rol_id,
    activo = TRUE,
    cargo_id = nuevo.cargo_id,
    horas_contratadas_semana = nuevo.horas_contratadas_semana,
    horas_disponibles = nuevo.horas_disponibles,
    sueldo_base = nuevo.sueldo_base,
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
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador4@skillbridge.local'),
    (SELECT h.id FROM habilidad h JOIN categoria_habilidad c ON c.id = h.categoria_id
     WHERE h.nombre = 'Docker' AND c.nombre = '[DEMO] Tecnologia'),
    'AVANZADO', 'VALIDADA', TRUE
),
(
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador4@skillbridge.local'),
    (SELECT h.id FROM habilidad h JOIN categoria_habilidad c ON c.id = h.categoria_id
     WHERE h.nombre = 'AWS' AND c.nombre = '[DEMO] Tecnologia'),
    'AVANZADO', 'VALIDADA', TRUE
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
    60000.00, 4, 20.00,
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
    colaboradores_requeridos = 4, rm_revisor_id = @demo_rm_id
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
--
-- Escenarios financieros principales:
--   * Migracion Cloud: tres asignaciones activas consumen mas del 90 %.
--   * Migracion Cloud / Mateo: pendiente del RM y bloqueada por deficit.
--   * Analitica Comercial / Diego: presupuesto reservado, pendiente del PM.
--   * Portal de Atencion / Elena: no puede aprobarse sin presupuesto asignado.
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
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Migracion Cloud'),
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador3@skillbridge.local'),
    20.00, 'PROPUESTA_PM', 'Asignacion activa que deja el presupuesto del proyecto en estado critico.',
    'ACTIVA', TRUE, TRUE, CURRENT_TIMESTAMP - INTERVAL 12 DAY,
    CURRENT_TIMESTAMP - INTERVAL 11 DAY, NULL, NULL, NULL, NULL,
    CURRENT_TIMESTAMP - INTERVAL 13 DAY, CURRENT_TIMESTAMP - INTERVAL 11 DAY, NULL
),
(
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Migracion Cloud'),
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador4@skillbridge.local'),
    10.00, 'PROPUESTA_PM', 'Solicitud pendiente que debe bloquearse por presupuesto insuficiente.',
    'PENDIENTE', TRUE, FALSE, CURRENT_TIMESTAMP - INTERVAL 1 DAY,
    NULL, NULL, NULL, NULL, NULL, CURRENT_TIMESTAMP - INTERVAL 2 DAY, NULL, NULL
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

-- Solicitudes recientes iniciadas por colaboradores. Tambien permiten abrir
-- la vista de revision y comprobar sus cambios visuales:
--   * Carla / Analitica Comercial: [DEMO APROBAR], con presupuesto suficiente.
--   * Elena / Analitica Comercial: [DEMO RECHAZAR], disponible para probar el motivo.
-- Las dos comienzan sin aprobacion de PM ni RM y muestran "Pendiente RM y PM".
-- Ambas incluyen un mensaje visible dentro del recuadro "Justificacion o mensaje".
-- Se eliminan solamente estas solicitudes DEMO para restaurarlas tras cada prueba.
DELETE FROM asignacion
WHERE id > 0
  AND origen = 'SOLICITADA_COLABORADOR'
  AND mensaje_solicitud IN (
      'Me interesa participar en el portal porque cuento con experiencia en Java y Spring Boot.',
      '[DEMO APROBAR] Solicitud preparada con carga y presupuesto suficientes para confirmar la aprobacion.',
      '[DEMO RECHAZAR] Solicitud preparada para comprobar el modal y registrar el motivo del rechazo.'
  );

INSERT INTO asignacion (
    proyecto_id, colaborador_id, horas_semanales, habilidades_relevantes,
    origen, mensaje_solicitud, habilidad_solicitada_id, estado,
    aprobado_por_pm, aprobado_por_rm, fecha_aprobacion_pm, fecha_aprobacion_rm,
    fecha_solicitud
) VALUES
(
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Analitica Comercial'),
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador1@skillbridge.local'),
    8.00, 'Java, integracion de datos',
    'SOLICITADA_COLABORADOR',
    '[DEMO APROBAR] Solicitud preparada con carga y presupuesto suficientes para confirmar la aprobacion.',
    (SELECT h.id FROM habilidad h JOIN categoria_habilidad c ON c.id = h.categoria_id
     WHERE h.nombre = 'Java' AND c.nombre = '[DEMO] Tecnologia'),
    'PENDIENTE', FALSE, FALSE, NULL, NULL,
    CURRENT_TIMESTAMP - INTERVAL 30 MINUTE
),
(
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Analitica Comercial'),
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador3@skillbridge.local'),
    16.00, 'Python, analisis de datos',
    'SOLICITADA_COLABORADOR',
    '[DEMO RECHAZAR] Solicitud preparada para comprobar el modal y registrar el motivo del rechazo.',
    (SELECT h.id FROM habilidad h JOIN categoria_habilidad c ON c.id = h.categoria_id
     WHERE h.nombre = 'Python' AND c.nombre = '[DEMO] Tecnologia'),
    'PENDIENTE', FALSE, FALSE, NULL, NULL,
    CURRENT_TIMESTAMP - INTERVAL 2 HOUR
) AS nuevo
ON DUPLICATE KEY UPDATE
    horas_semanales = nuevo.horas_semanales,
    habilidades_relevantes = nuevo.habilidades_relevantes,
    origen = nuevo.origen,
    mensaje_solicitud = nuevo.mensaje_solicitud,
    habilidad_solicitada_id = nuevo.habilidad_solicitada_id,
    estado = nuevo.estado,
    aprobado_por_pm = nuevo.aprobado_por_pm,
    aprobado_por_rm = nuevo.aprobado_por_rm,
    fecha_aprobacion_pm = nuevo.fecha_aprobacion_pm,
    fecha_aprobacion_rm = nuevo.fecha_aprobacion_rm,
    fecha_solicitud = nuevo.fecha_solicitud;

-- --------------------------------------------------------------------------
-- 8. Foros de demostracion para la consulta de solo lectura del RM
-- --------------------------------------------------------------------------
INSERT INTO etiqueta (nombre) VALUES
    ('Arquitectura'),
    ('Incidente'),
    ('Pregunta'),
    ('Anuncio') AS nuevo
ON DUPLICATE KEY UPDATE nombre = nuevo.nombre;

INSERT INTO foro (proyecto_id, tipo, es_publico, nombre, fecha_creacion)
SELECT (SELECT id FROM proyecto WHERE nombre = '[DEMO] Migracion Cloud'),
       'PROYECTO', FALSE, '[DEMO] Foro Migracion Cloud',
       CURRENT_TIMESTAMP - INTERVAL 18 DAY
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM foro WHERE nombre = '[DEMO] Foro Migracion Cloud');

INSERT INTO foro (proyecto_id, tipo, es_publico, nombre, fecha_creacion)
SELECT (SELECT id FROM proyecto WHERE nombre = '[DEMO] Portal de Atencion'),
       'PROYECTO', TRUE, '[DEMO] Foro Portal de Atencion',
       CURRENT_TIMESTAMP - INTERVAL 4 DAY
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM foro WHERE nombre = '[DEMO] Foro Portal de Atencion');

INSERT INTO foro (proyecto_id, tipo, es_publico, nombre, fecha_creacion)
SELECT NULL, 'GENERAL', TRUE, '[DEMO] Comunidad Tecnica',
       CURRENT_TIMESTAMP - INTERVAL 30 DAY
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM foro WHERE nombre = '[DEMO] Comunidad Tecnica');

SET @demo_foro_migracion_id := (SELECT MIN(id) FROM foro WHERE nombre = '[DEMO] Foro Migracion Cloud');
SET @demo_foro_portal_id := (SELECT MIN(id) FROM foro WHERE nombre = '[DEMO] Foro Portal de Atencion');
SET @demo_foro_general_id := (SELECT MIN(id) FROM foro WHERE nombre = '[DEMO] Comunidad Tecnica');

INSERT INTO publicacion_foro (foro_id, autor_id, etiqueta_id, titulo, contenido, fecha_creacion)
SELECT @demo_foro_migracion_id,
       (SELECT id FROM usuario WHERE correo = 'demo.pm@skillbridge.local'),
       (SELECT id FROM etiqueta WHERE nombre = 'Arquitectura'),
       '[DEMO] Estrategia de migracion por etapas',
       'Propongo migrar primero los servicios sin estado y dejar las bases de datos para la segunda etapa. Revisemos dependencias y riesgos antes del corte.',
       CURRENT_TIMESTAMP - INTERVAL 3 DAY
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM publicacion_foro
    WHERE foro_id = @demo_foro_migracion_id
      AND titulo = '[DEMO] Estrategia de migracion por etapas'
);

INSERT INTO publicacion_foro (foro_id, autor_id, etiqueta_id, titulo, contenido, fecha_creacion)
SELECT @demo_foro_migracion_id,
       (SELECT id FROM usuario WHERE correo = 'demo.colaborador2@skillbridge.local'),
       (SELECT id FROM etiqueta WHERE nombre = 'Incidente'),
       '[DEMO] Incidente en despliegue AWS',
       'El servicio de integracion reinicio dos veces luego del despliegue. Los eventos indican que el limite de memoria es insuficiente durante la inicializacion.',
       CURRENT_TIMESTAMP - INTERVAL 1 DAY
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM publicacion_foro
    WHERE foro_id = @demo_foro_migracion_id
      AND titulo = '[DEMO] Incidente en despliegue AWS'
);

INSERT INTO publicacion_foro (foro_id, autor_id, etiqueta_id, titulo, contenido, fecha_creacion)
SELECT @demo_foro_portal_id,
       (SELECT id FROM usuario WHERE correo = 'demo.colaborador1@skillbridge.local'),
       (SELECT id FROM etiqueta WHERE nombre = 'Pregunta'),
       '[DEMO] Contrato del API de solicitudes',
       'Antes de implementar la pantalla necesitamos confirmar los estados y los mensajes de error que devolvera el API de solicitudes.',
       CURRENT_TIMESTAMP - INTERVAL 2 DAY
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM publicacion_foro
    WHERE foro_id = @demo_foro_portal_id
      AND titulo = '[DEMO] Contrato del API de solicitudes'
);

INSERT INTO publicacion_foro (foro_id, autor_id, etiqueta_id, titulo, contenido, fecha_creacion)
SELECT @demo_foro_general_id,
       (SELECT id FROM usuario WHERE correo = 'demo.pm@skillbridge.local'),
       (SELECT id FROM etiqueta WHERE nombre = 'Anuncio'),
       '[DEMO] Estandares para documentar APIs',
       'Desde esta semana utilizaremos el mismo formato para describir endpoints, parametros, respuestas y ejemplos de cada API.',
       CURRENT_TIMESTAMP - INTERVAL 8 DAY
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM publicacion_foro
    WHERE foro_id = @demo_foro_general_id
      AND titulo = '[DEMO] Estandares para documentar APIs'
);

SET @demo_pub_incidente_id := (
    SELECT MIN(id) FROM publicacion_foro
    WHERE foro_id = @demo_foro_migracion_id
      AND titulo = '[DEMO] Incidente en despliegue AWS'
);
SET @demo_pub_estrategia_id := (
    SELECT MIN(id) FROM publicacion_foro
    WHERE foro_id = @demo_foro_migracion_id
      AND titulo = '[DEMO] Estrategia de migracion por etapas'
);
SET @demo_pub_portal_id := (
    SELECT MIN(id) FROM publicacion_foro
    WHERE foro_id = @demo_foro_portal_id
      AND titulo = '[DEMO] Contrato del API de solicitudes'
);

INSERT INTO respuesta_foro (publicacion_id, autor_id, contenido, es_solucion, fecha_creacion)
SELECT @demo_pub_incidente_id,
       (SELECT id FROM usuario WHERE correo = 'demo.colaborador1@skillbridge.local'),
       'Aumente el limite de memoria manteniendo el request actual. Los pods completaron la inicializacion y permanecen estables.',
       TRUE, CURRENT_TIMESTAMP - INTERVAL 20 HOUR
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM respuesta_foro
    WHERE publicacion_id = @demo_pub_incidente_id
      AND contenido LIKE 'Aumente el limite de memoria%'
);

INSERT INTO respuesta_foro (publicacion_id, autor_id, contenido, es_solucion, fecha_creacion)
SELECT @demo_pub_incidente_id,
       (SELECT id FROM usuario WHERE correo = 'demo.pm@skillbridge.local'),
       'Confirmado en el ambiente de pruebas. Conservaremos esta configuracion para el siguiente despliegue.',
       FALSE, CURRENT_TIMESTAMP - INTERVAL 18 HOUR
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM respuesta_foro
    WHERE publicacion_id = @demo_pub_incidente_id
      AND contenido LIKE 'Confirmado en el ambiente de pruebas%'
);

INSERT INTO respuesta_foro (publicacion_id, autor_id, contenido, es_solucion, fecha_creacion)
SELECT @demo_pub_estrategia_id,
       (SELECT id FROM usuario WHERE correo = 'demo.colaborador2@skillbridge.local'),
       'El inventario de dependencias esta listo. Sugiero incluir las tareas programadas antes de cerrar la primera etapa.',
       FALSE, CURRENT_TIMESTAMP - INTERVAL 2 DAY
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM respuesta_foro
    WHERE publicacion_id = @demo_pub_estrategia_id
      AND contenido LIKE 'El inventario de dependencias esta listo%'
);

INSERT INTO respuesta_foro (publicacion_id, autor_id, contenido, es_solucion, fecha_creacion)
SELECT @demo_pub_portal_id,
       (SELECT id FROM usuario WHERE correo = 'demo.pm@skillbridge.local'),
       'Los estados acordados son pendiente, en proceso, atendida y cancelada. Publicare el contrato definitivo en la documentacion del proyecto.',
       TRUE, CURRENT_TIMESTAMP - INTERVAL 1 DAY
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM respuesta_foro
    WHERE publicacion_id = @demo_pub_portal_id
      AND contenido LIKE 'Los estados acordados son%'
);

SET @demo_resp_solucion_id := (
    SELECT MIN(id) FROM respuesta_foro
    WHERE publicacion_id = @demo_pub_incidente_id
      AND contenido LIKE 'Aumente el limite de memoria%'
);

INSERT INTO voto_publicacion (usuario_id, publicacion_id, tipo)
SELECT (SELECT id FROM usuario WHERE correo = 'demo.colaborador1@skillbridge.local'),
       @demo_pub_incidente_id, 'POSITIVO'
FROM DUAL
ON DUPLICATE KEY UPDATE tipo = 'POSITIVO';

INSERT INTO voto_publicacion (usuario_id, publicacion_id, tipo)
SELECT (SELECT id FROM usuario WHERE correo = 'demo.colaborador3@skillbridge.local'),
       @demo_pub_incidente_id, 'POSITIVO'
FROM DUAL
ON DUPLICATE KEY UPDATE tipo = 'POSITIVO';

INSERT INTO voto_publicacion (usuario_id, publicacion_id, tipo)
SELECT (SELECT id FROM usuario WHERE correo = 'demo.colaborador2@skillbridge.local'),
       @demo_pub_estrategia_id, 'POSITIVO'
FROM DUAL
ON DUPLICATE KEY UPDATE tipo = 'POSITIVO';

INSERT INTO voto_respuesta (usuario_id, respuesta_id, tipo)
SELECT (SELECT id FROM usuario WHERE correo = 'demo.colaborador2@skillbridge.local'),
       @demo_resp_solucion_id, 'POSITIVO'
FROM DUAL
ON DUPLICATE KEY UPDATE tipo = 'POSITIVO';

INSERT INTO voto_respuesta (usuario_id, respuesta_id, tipo)
SELECT (SELECT id FROM usuario WHERE correo = 'demo.colaborador3@skillbridge.local'),
       @demo_resp_solucion_id, 'POSITIVO'
FROM DUAL
ON DUPLICATE KEY UPDATE tipo = 'POSITIVO';

-- --------------------------------------------------------------------------
-- 9. Actividades para probar los reportes mensuales del RM
-- El reporte solo considera las actividades COMPLETADA del mes actual.
-- --------------------------------------------------------------------------
SET @demo_pm_id := (SELECT id FROM usuario WHERE correo = 'demo.pm@skillbridge.local');
SET @demo_colaborador1_id := (SELECT id FROM usuario WHERE correo = 'demo.colaborador1@skillbridge.local');
SET @demo_colaborador2_id := (SELECT id FROM usuario WHERE correo = 'demo.colaborador2@skillbridge.local');
SET @demo_colaborador3_id := (SELECT id FROM usuario WHERE correo = 'demo.colaborador3@skillbridge.local');

INSERT INTO actividad (proyecto_id, colaborador_id, titulo, descripcion, horas_estimadas,
                       fecha_asignacion, fecha_limite, fecha_marcado_revision, fecha_entrega,
                       estado_entrega, veces_devuelta, estado, creado_por)
SELECT @demo_migracion_id, @demo_colaborador1_id, '[DEMO] Inventario de servicios',
       'Inventario tecnico utilizado para planificar la migracion.', 42.00,
       DATE_ADD(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 1 DAY), LAST_DAY(CURRENT_DATE),
       DATE_ADD(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 5 DAY),
       DATE_ADD(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 6 DAY),
       'A_TIEMPO', 0, 'COMPLETADA', @demo_pm_id
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM actividad WHERE proyecto_id = @demo_migracion_id
      AND titulo = '[DEMO] Inventario de servicios'
);

INSERT INTO actividad (proyecto_id, colaborador_id, titulo, descripcion, horas_estimadas,
                       fecha_asignacion, fecha_limite, fecha_marcado_revision, fecha_entrega,
                       estado_entrega, veces_devuelta, estado, creado_por)
SELECT @demo_migracion_id, @demo_colaborador2_id, '[DEMO] Configuracion de infraestructura',
       'Preparacion de la infraestructura cloud del proyecto.', 36.00,
       DATE_ADD(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 2 DAY), LAST_DAY(CURRENT_DATE),
       DATE_ADD(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 7 DAY),
       DATE_ADD(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 8 DAY),
       'A_TIEMPO', 0, 'COMPLETADA', @demo_pm_id
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM actividad WHERE proyecto_id = @demo_migracion_id
      AND titulo = '[DEMO] Configuracion de infraestructura'
);

INSERT INTO actividad (proyecto_id, colaborador_id, titulo, descripcion, horas_estimadas,
                       fecha_asignacion, fecha_limite, fecha_marcado_revision, fecha_entrega,
                       estado_entrega, veces_devuelta, estado, creado_por)
SELECT @demo_portal_id, @demo_colaborador1_id, '[DEMO] API de solicitudes',
       'Implementacion del contrato principal de solicitudes.', 28.00,
       DATE_ADD(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 1 DAY), LAST_DAY(CURRENT_DATE),
       DATE_ADD(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 9 DAY),
       DATE_ADD(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 10 DAY),
       'A_TIEMPO', 1, 'COMPLETADA', @demo_pm_id
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM actividad WHERE proyecto_id = @demo_portal_id
      AND titulo = '[DEMO] API de solicitudes'
);

INSERT INTO actividad (proyecto_id, colaborador_id, titulo, descripcion, horas_estimadas,
                       fecha_asignacion, fecha_limite, fecha_marcado_revision, fecha_entrega,
                       estado_entrega, veces_devuelta, estado, creado_por)
SELECT @demo_portal_id, @demo_colaborador3_id, '[DEMO] Pruebas de aceptacion',
       'Ejecucion de casos funcionales para el portal.', 52.00,
       DATE_ADD(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 3 DAY), LAST_DAY(CURRENT_DATE),
       DATE_ADD(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 11 DAY),
       DATE_ADD(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 12 DAY),
       'A_TIEMPO', 0, 'COMPLETADA', @demo_pm_id
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM actividad WHERE proyecto_id = @demo_portal_id
      AND titulo = '[DEMO] Pruebas de aceptacion'
);

INSERT INTO actividad (proyecto_id, colaborador_id, titulo, descripcion, horas_estimadas,
                       fecha_asignacion, fecha_limite, fecha_marcado_revision, fecha_entrega,
                       estado_entrega, veces_devuelta, estado, creado_por)
SELECT @demo_analitica_id, @demo_colaborador2_id, '[DEMO] Modelo de indicadores',
       'Construccion del modelo inicial de indicadores comerciales.', 44.00,
       DATE_ADD(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 2 DAY), LAST_DAY(CURRENT_DATE),
       DATE_ADD(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 8 DAY),
       DATE_ADD(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 9 DAY),
       'A_TIEMPO', 0, 'COMPLETADA', @demo_pm_id
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM actividad WHERE proyecto_id = @demo_analitica_id
      AND titulo = '[DEMO] Modelo de indicadores'
);

-- Esta actividad aparece en otro mes y permite comprobar el filtro de periodo.
INSERT INTO actividad (proyecto_id, colaborador_id, titulo, descripcion, horas_estimadas,
                       fecha_asignacion, fecha_limite, fecha_marcado_revision, fecha_entrega,
                       estado_entrega, veces_devuelta, estado, creado_por)
SELECT @demo_migracion_id, @demo_colaborador1_id, '[DEMO] Diagnostico del mes anterior',
       'Actividad completada antes del mes actual.', 32.00,
       DATE_SUB(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 10 DAY),
       DATE_SUB(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 1 DAY),
       DATE_SUB(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 3 DAY),
       DATE_SUB(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 2 DAY),
       'A_TIEMPO', 0, 'COMPLETADA', @demo_pm_id
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM actividad WHERE proyecto_id = @demo_migracion_id
      AND titulo = '[DEMO] Diagnostico del mes anterior'
);

-- Esta actividad sigue en revision y no debe sumarse como hora trabajada.
INSERT INTO actividad (proyecto_id, colaborador_id, titulo, descripcion, horas_estimadas,
                       fecha_asignacion, fecha_limite, fecha_marcado_revision, fecha_entrega,
                       estado_entrega, veces_devuelta, estado, creado_por)
SELECT @demo_analitica_id, @demo_colaborador3_id, '[DEMO] Tablero comercial en revision',
       'Actividad intencionalmente pendiente de aprobacion.', 60.00,
       DATE_ADD(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 3 DAY), LAST_DAY(CURRENT_DATE),
       DATE_ADD(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 12 DAY), NULL,
       NULL, 0, 'EN_REVISION', @demo_pm_id
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM actividad WHERE proyecto_id = @demo_analitica_id
      AND titulo = '[DEMO] Tablero comercial en revision'
);

-- Al volver a ejecutar el archivo, mueve los registros demo al periodo actual.
-- Solo restaura estas actividades identificadas explícitamente con [DEMO].
SET @demo_act_inventario_id := (SELECT MIN(id) FROM actividad WHERE proyecto_id = @demo_migracion_id AND titulo = '[DEMO] Inventario de servicios');
SET @demo_act_infraestructura_id := (SELECT MIN(id) FROM actividad WHERE proyecto_id = @demo_migracion_id AND titulo = '[DEMO] Configuracion de infraestructura');
SET @demo_act_api_id := (SELECT MIN(id) FROM actividad WHERE proyecto_id = @demo_portal_id AND titulo = '[DEMO] API de solicitudes');
SET @demo_act_pruebas_id := (SELECT MIN(id) FROM actividad WHERE proyecto_id = @demo_portal_id AND titulo = '[DEMO] Pruebas de aceptacion');
SET @demo_act_indicadores_id := (SELECT MIN(id) FROM actividad WHERE proyecto_id = @demo_analitica_id AND titulo = '[DEMO] Modelo de indicadores');
SET @demo_act_anterior_id := (SELECT MIN(id) FROM actividad WHERE proyecto_id = @demo_migracion_id AND titulo = '[DEMO] Diagnostico del mes anterior');
SET @demo_act_revision_id := (SELECT MIN(id) FROM actividad WHERE proyecto_id = @demo_analitica_id AND titulo = '[DEMO] Tablero comercial en revision');

UPDATE actividad
SET fecha_asignacion = DATE_ADD(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 1 DAY),
    fecha_limite = LAST_DAY(CURRENT_DATE),
    fecha_marcado_revision = DATE_ADD(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 10 DAY),
    fecha_entrega = DATE_ADD(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 11 DAY),
    estado_entrega = 'A_TIEMPO',
    estado = 'COMPLETADA'
WHERE id IN (@demo_act_inventario_id, @demo_act_infraestructura_id,
             @demo_act_api_id, @demo_act_pruebas_id, @demo_act_indicadores_id);

UPDATE actividad
SET fecha_asignacion = DATE_SUB(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 10 DAY),
    fecha_limite = DATE_SUB(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 1 DAY),
    fecha_marcado_revision = DATE_SUB(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 3 DAY),
    fecha_entrega = DATE_SUB(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 2 DAY),
    estado_entrega = 'A_TIEMPO',
    estado = 'COMPLETADA'
WHERE id = @demo_act_anterior_id;

UPDATE actividad
SET fecha_asignacion = DATE_ADD(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 3 DAY),
    fecha_limite = LAST_DAY(CURRENT_DATE),
    fecha_marcado_revision = DATE_ADD(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01'), INTERVAL 12 DAY),
    fecha_entrega = NULL,
    estado_entrega = NULL,
    estado = 'EN_REVISION'
WHERE id = @demo_act_revision_id;

-- --------------------------------------------------------------------------
-- 10. Catalogo y solicitudes de cursos del RM
-- --------------------------------------------------------------------------
SET @demo_admin_id := (SELECT id FROM usuario WHERE correo = 'demo.admin@skillbridge.local');

INSERT INTO curso (nombre, descripcion, categoria, horas, activo, creado_por)
SELECT '[DEMO] Spring Boot avanzado', 'Arquitectura, seguridad y persistencia con Spring Boot.',
       'Tecnico', 20.00, TRUE, @demo_admin_id
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM curso WHERE nombre = '[DEMO] Spring Boot avanzado');

INSERT INTO curso (nombre, descripcion, categoria, horas, activo, creado_por)
SELECT '[DEMO] AWS Cloud Practitioner', 'Fundamentos de servicios, seguridad y arquitectura en AWS.',
       'Certificacion', 32.00, TRUE, @demo_admin_id
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM curso WHERE nombre = '[DEMO] AWS Cloud Practitioner');

INSERT INTO curso (nombre, descripcion, categoria, horas, activo, creado_por)
SELECT '[DEMO] Comunicacion efectiva', 'Comunicacion profesional y coordinacion de equipos.',
       'Habilidades blandas', 8.00, TRUE, @demo_admin_id
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM curso WHERE nombre = '[DEMO] Comunicacion efectiva');

INSERT INTO curso (nombre, descripcion, categoria, horas, activo, creado_por)
SELECT '[DEMO] Kubernetes productivo', 'Despliegue y operacion de cargas en Kubernetes.',
       'Tecnico', 24.00, TRUE, @demo_admin_id
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM curso WHERE nombre = '[DEMO] Kubernetes productivo');

INSERT INTO curso (nombre, descripcion, categoria, horas, activo, creado_por)
SELECT '[DEMO] Curso inactivo', 'Curso desactivado que no debe aparecer en el catalogo del RM.',
       'Tecnico', 10.00, FALSE, @demo_admin_id
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM curso WHERE nombre = '[DEMO] Curso inactivo');

SET @demo_curso_spring_id := (SELECT MIN(id) FROM curso WHERE nombre = '[DEMO] Spring Boot avanzado');
SET @demo_curso_aws_id := (SELECT MIN(id) FROM curso WHERE nombre = '[DEMO] AWS Cloud Practitioner');
SET @demo_curso_comunicacion_id := (SELECT MIN(id) FROM curso WHERE nombre = '[DEMO] Comunicacion efectiva');
SET @demo_curso_kubernetes_id := (SELECT MIN(id) FROM curso WHERE nombre = '[DEMO] Kubernetes productivo');
SET @demo_curso_inactivo_id := (SELECT MIN(id) FROM curso WHERE nombre = '[DEMO] Curso inactivo');

UPDATE curso SET activo = TRUE WHERE id IN (
    @demo_curso_spring_id, @demo_curso_aws_id,
    @demo_curso_comunicacion_id, @demo_curso_kubernetes_id
);
UPDATE curso SET activo = FALSE WHERE id = @demo_curso_inactivo_id;

INSERT INTO colaborador_curso (
    colaborador_id, curso_id, origen, estado, asignado_por,
    fecha_solicitud, fecha_respuesta, motivo_respuesta
)
SELECT @demo_colaborador1_id, @demo_curso_spring_id, 'SOLICITUD_COLABORADOR',
       'SOLICITADO', NULL, CURRENT_TIMESTAMP - INTERVAL 2 DAY, NULL, NULL
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM colaborador_curso
    WHERE colaborador_id = @demo_colaborador1_id AND curso_id = @demo_curso_spring_id
);

INSERT INTO colaborador_curso (
    colaborador_id, curso_id, origen, estado, asignado_por,
    fecha_solicitud, fecha_respuesta, motivo_respuesta
)
SELECT @demo_colaborador2_id, @demo_curso_aws_id, 'SOLICITUD_COLABORADOR',
       'SOLICITADO', NULL, CURRENT_TIMESTAMP - INTERVAL 1 DAY, NULL, NULL
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM colaborador_curso
    WHERE colaborador_id = @demo_colaborador2_id AND curso_id = @demo_curso_aws_id
);

INSERT INTO colaborador_curso (
    colaborador_id, curso_id, origen, estado, asignado_por,
    fecha_solicitud, fecha_respuesta, motivo_respuesta
)
SELECT @demo_colaborador3_id, @demo_curso_comunicacion_id, 'SOLICITUD_COLABORADOR',
       'EN_CURSO', @demo_rm_id, CURRENT_TIMESTAMP - INTERVAL 7 DAY,
       CURRENT_TIMESTAMP - INTERVAL 5 DAY, NULL
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM colaborador_curso
    WHERE colaborador_id = @demo_colaborador3_id AND curso_id = @demo_curso_comunicacion_id
);

INSERT INTO colaborador_curso (
    colaborador_id, curso_id, origen, estado, asignado_por,
    fecha_solicitud, fecha_respuesta, motivo_respuesta
)
SELECT @demo_colaborador1_id, @demo_curso_kubernetes_id, 'SOLICITUD_COLABORADOR',
       'RECHAZADO', @demo_rm_id, CURRENT_TIMESTAMP - INTERVAL 8 DAY,
       CURRENT_TIMESTAMP - INTERVAL 6 DAY,
       'Se recomienda completar primero la capacitacion base de contenedores.'
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM colaborador_curso
    WHERE colaborador_id = @demo_colaborador1_id AND curso_id = @demo_curso_kubernetes_id
);

INSERT INTO colaborador_curso (
    colaborador_id, curso_id, origen, estado, asignado_por,
    fecha_solicitud, fecha_respuesta, motivo_respuesta
)
SELECT @demo_colaborador2_id, @demo_curso_kubernetes_id, 'ASIGNADO_POR_RM',
       'EN_CURSO', @demo_rm_id, CURRENT_TIMESTAMP - INTERVAL 4 DAY,
       CURRENT_TIMESTAMP - INTERVAL 4 DAY,
       'Capacitacion recomendada para fortalecer la operacion cloud.'
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM colaborador_curso
    WHERE colaborador_id = @demo_colaborador2_id AND curso_id = @demo_curso_kubernetes_id
);

SET @demo_cc_spring_id := (SELECT MIN(id) FROM colaborador_curso WHERE colaborador_id = @demo_colaborador1_id AND curso_id = @demo_curso_spring_id);
SET @demo_cc_aws_id := (SELECT MIN(id) FROM colaborador_curso WHERE colaborador_id = @demo_colaborador2_id AND curso_id = @demo_curso_aws_id);
SET @demo_cc_comunicacion_id := (SELECT MIN(id) FROM colaborador_curso WHERE colaborador_id = @demo_colaborador3_id AND curso_id = @demo_curso_comunicacion_id);
SET @demo_cc_rechazado_id := (SELECT MIN(id) FROM colaborador_curso WHERE colaborador_id = @demo_colaborador1_id AND curso_id = @demo_curso_kubernetes_id);
SET @demo_cc_asignado_id := (SELECT MIN(id) FROM colaborador_curso WHERE colaborador_id = @demo_colaborador2_id AND curso_id = @demo_curso_kubernetes_id);

UPDATE colaborador_curso SET origen = 'SOLICITUD_COLABORADOR', estado = 'SOLICITADO',
    asignado_por = NULL, fecha_solicitud = CURRENT_TIMESTAMP - INTERVAL 2 DAY,
    fecha_respuesta = NULL, motivo_respuesta = NULL WHERE id = @demo_cc_spring_id;
UPDATE colaborador_curso SET origen = 'SOLICITUD_COLABORADOR', estado = 'SOLICITADO',
    asignado_por = NULL, fecha_solicitud = CURRENT_TIMESTAMP - INTERVAL 1 DAY,
    fecha_respuesta = NULL, motivo_respuesta = NULL WHERE id = @demo_cc_aws_id;
UPDATE colaborador_curso SET origen = 'SOLICITUD_COLABORADOR', estado = 'EN_CURSO',
    asignado_por = @demo_rm_id, fecha_solicitud = CURRENT_TIMESTAMP - INTERVAL 7 DAY,
    fecha_respuesta = CURRENT_TIMESTAMP - INTERVAL 5 DAY, motivo_respuesta = NULL
WHERE id = @demo_cc_comunicacion_id;
UPDATE colaborador_curso SET origen = 'SOLICITUD_COLABORADOR', estado = 'RECHAZADO',
    asignado_por = @demo_rm_id, fecha_solicitud = CURRENT_TIMESTAMP - INTERVAL 8 DAY,
    fecha_respuesta = CURRENT_TIMESTAMP - INTERVAL 6 DAY,
    motivo_respuesta = 'Se recomienda completar primero la capacitacion base de contenedores.'
WHERE id = @demo_cc_rechazado_id;
UPDATE colaborador_curso SET origen = 'ASIGNADO_POR_RM', estado = 'EN_CURSO',
    asignado_por = @demo_rm_id, fecha_solicitud = CURRENT_TIMESTAMP - INTERVAL 4 DAY,
    fecha_respuesta = CURRENT_TIMESTAMP - INTERVAL 4 DAY,
    motivo_respuesta = 'Capacitacion recomendada para fortalecer la operacion cloud.'
WHERE id = @demo_cc_asignado_id;
-- Si en una prueba anterior se subio evidencia a estas inscripciones, se limpia.
UPDATE colaborador_curso SET evidencia_url = NULL, fecha_evidencia = NULL, fecha_completado = NULL
WHERE id IN (@demo_cc_spring_id, @demo_cc_aws_id, @demo_cc_comunicacion_id,
             @demo_cc_rechazado_id, @demo_cc_asignado_id);

-- --------------------------------------------------------------------------
-- 10.1 Evidencias de finalizacion de cursos (TASK-049)
-- Ver en: RM -> Cursos -> Solicitudes -> Estado "Evidencia en revision"
--         /rm/cursos/solicitudes?estado=EVIDENCIA_PENDIENTE
--   * Carla Rojas  / Comunicacion efectiva : evidencia pendiente (para validar).
--   * Elena Torres / AWS Cloud Practitioner: evidencia pendiente (para rechazar
--     con motivo; vuelve a EN_CURSO y la colaboradora puede reenviarla).
--   * Diego Salazar / Spring Boot avanzado : curso COMPLETADO este mes (historial).
-- Los archivos son ficticios y se sirven desde static/documentos.
-- Al volver a ejecutar el script, las tres inscripciones regresan a este estado.
-- --------------------------------------------------------------------------
INSERT INTO colaborador_curso (
    colaborador_id, curso_id, origen, estado, asignado_por,
    fecha_solicitud, fecha_respuesta, motivo_respuesta, evidencia_url, fecha_evidencia
)
SELECT @demo_colaborador1_id, @demo_curso_comunicacion_id, 'SOLICITUD_COLABORADOR',
       'EVIDENCIA_PENDIENTE', @demo_rm_id, CURRENT_TIMESTAMP - INTERVAL 12 DAY,
       CURRENT_TIMESTAMP - INTERVAL 10 DAY, NULL,
       '/documentos/evidencia-curso-demo-comunicacion.txt', CURRENT_TIMESTAMP - INTERVAL 1 DAY
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM colaborador_curso
    WHERE colaborador_id = @demo_colaborador1_id AND curso_id = @demo_curso_comunicacion_id
);

INSERT INTO colaborador_curso (
    colaborador_id, curso_id, origen, estado, asignado_por,
    fecha_solicitud, fecha_respuesta, motivo_respuesta, evidencia_url, fecha_evidencia
)
SELECT @demo_colaborador3_id, @demo_curso_aws_id, 'ASIGNADO_POR_RM',
       'EVIDENCIA_PENDIENTE', @demo_rm_id, CURRENT_TIMESTAMP - INTERVAL 20 DAY,
       CURRENT_TIMESTAMP - INTERVAL 20 DAY,
       'Preparacion para los despliegues del proyecto Migracion Cloud.',
       '/documentos/evidencia-curso-demo-aws.txt', CURRENT_TIMESTAMP - INTERVAL 3 HOUR
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM colaborador_curso
    WHERE colaborador_id = @demo_colaborador3_id AND curso_id = @demo_curso_aws_id
);

INSERT INTO colaborador_curso (
    colaborador_id, curso_id, origen, estado, asignado_por,
    fecha_solicitud, fecha_respuesta, fecha_completado, motivo_respuesta, evidencia_url, fecha_evidencia
)
SELECT @demo_colaborador2_id, @demo_curso_spring_id, 'SOLICITUD_COLABORADOR',
       'COMPLETADO', @demo_rm_id, CURRENT_TIMESTAMP - INTERVAL 40 DAY,
       CURRENT_TIMESTAMP - INTERVAL 38 DAY,
       CAST(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01') AS DATETIME) + INTERVAL 9 HOUR, NULL,
       '/documentos/evidencia-curso-demo-spring.txt',
       CAST(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01') AS DATETIME) + INTERVAL 8 HOUR
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM colaborador_curso
    WHERE colaborador_id = @demo_colaborador2_id AND curso_id = @demo_curso_spring_id
);

SET @demo_cc_evid_validar_id := (SELECT MIN(id) FROM colaborador_curso WHERE colaborador_id = @demo_colaborador1_id AND curso_id = @demo_curso_comunicacion_id);
SET @demo_cc_evid_rechazar_id := (SELECT MIN(id) FROM colaborador_curso WHERE colaborador_id = @demo_colaborador3_id AND curso_id = @demo_curso_aws_id);
SET @demo_cc_completado_id := (SELECT MIN(id) FROM colaborador_curso WHERE colaborador_id = @demo_colaborador2_id AND curso_id = @demo_curso_spring_id);

UPDATE colaborador_curso SET origen = 'SOLICITUD_COLABORADOR', estado = 'EVIDENCIA_PENDIENTE',
    asignado_por = @demo_rm_id, fecha_solicitud = CURRENT_TIMESTAMP - INTERVAL 12 DAY,
    fecha_respuesta = CURRENT_TIMESTAMP - INTERVAL 10 DAY, fecha_completado = NULL,
    motivo_respuesta = NULL,
    evidencia_url = '/documentos/evidencia-curso-demo-comunicacion.txt',
    fecha_evidencia = CURRENT_TIMESTAMP - INTERVAL 1 DAY
WHERE id = @demo_cc_evid_validar_id;
UPDATE colaborador_curso SET origen = 'ASIGNADO_POR_RM', estado = 'EVIDENCIA_PENDIENTE',
    asignado_por = @demo_rm_id, fecha_solicitud = CURRENT_TIMESTAMP - INTERVAL 20 DAY,
    fecha_respuesta = CURRENT_TIMESTAMP - INTERVAL 20 DAY, fecha_completado = NULL,
    motivo_respuesta = 'Preparacion para los despliegues del proyecto Migracion Cloud.',
    evidencia_url = '/documentos/evidencia-curso-demo-aws.txt',
    fecha_evidencia = CURRENT_TIMESTAMP - INTERVAL 3 HOUR
WHERE id = @demo_cc_evid_rechazar_id;
UPDATE colaborador_curso SET origen = 'SOLICITUD_COLABORADOR', estado = 'COMPLETADO',
    asignado_por = @demo_rm_id, fecha_solicitud = CURRENT_TIMESTAMP - INTERVAL 40 DAY,
    fecha_respuesta = CURRENT_TIMESTAMP - INTERVAL 38 DAY,
    fecha_completado = CAST(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01') AS DATETIME) + INTERVAL 9 HOUR,
    motivo_respuesta = NULL,
    evidencia_url = '/documentos/evidencia-curso-demo-spring.txt',
    fecha_evidencia = CAST(DATE_FORMAT(CURRENT_DATE, '%Y-%m-01') AS DATETIME) + INTERVAL 8 HOUR
WHERE id = @demo_cc_completado_id;

-- Notificaciones del RM demo: al abrirlas llevan a la bandeja filtrada por evidencias.
INSERT INTO notificacion (usuario_id, tipo, categoria, titulo, descripcion, entidad, entidad_id, leida, fecha_creacion)
SELECT @demo_rm_id, 'EVIDENCIA_CURSO_PENDIENTE', 'CURSO', 'Evidencia de curso pendiente de revision',
       'Carla Rojas subio evidencia para el curso "[DEMO] Comunicacion efectiva".',
       'COLABORADOR_CURSO', @demo_cc_evid_validar_id, FALSE, CURRENT_TIMESTAMP - INTERVAL 1 DAY
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM notificacion
    WHERE usuario_id = @demo_rm_id AND tipo = 'EVIDENCIA_CURSO_PENDIENTE'
      AND entidad_id = @demo_cc_evid_validar_id
);

INSERT INTO notificacion (usuario_id, tipo, categoria, titulo, descripcion, entidad, entidad_id, leida, fecha_creacion)
SELECT @demo_rm_id, 'EVIDENCIA_CURSO_PENDIENTE', 'CURSO', 'Evidencia de curso pendiente de revision',
       'Elena Torres subio evidencia para el curso "[DEMO] AWS Cloud Practitioner".',
       'COLABORADOR_CURSO', @demo_cc_evid_rechazar_id, FALSE, CURRENT_TIMESTAMP - INTERVAL 3 HOUR
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM notificacion
    WHERE usuario_id = @demo_rm_id AND tipo = 'EVIDENCIA_CURSO_PENDIENTE'
      AND entidad_id = @demo_cc_evid_rechazar_id
);

UPDATE notificacion SET leida = FALSE
WHERE usuario_id = @demo_rm_id AND tipo = 'EVIDENCIA_CURSO_PENDIENTE'
  AND entidad_id IN (@demo_cc_evid_validar_id, @demo_cc_evid_rechazar_id);

COMMIT;

-- --------------------------------------------------------------------------
-- 11. Comprobacion rapida
-- Debe devolver proyectos, costos, foros, actividades y cursos de demostracion.
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

-- Costos que utiliza RmPresupuestoService. Permite comprobar los escenarios
-- ACTIVA (comprometido), PENDIENTE aprobada por RM (reservado) y pendiente RM.
SELECT
    p.nombre AS proyecto,
    CONCAT(u.nombre, ' ', u.apellido) AS colaborador,
    u.sueldo_base,
    a.horas_semanales,
    a.origen,
    a.estado,
    a.aprobado_por_pm,
    a.aprobado_por_rm,
    ROUND(u.sueldo_base / 160, 2) AS valor_hora,
    ROUND(DATEDIFF(p.fecha_fin_estimada, p.fecha_inicio) / 7, 2) AS duracion_semanas,
    ROUND(
        ROUND(u.sueldo_base / 160, 2)
        * ROUND(a.horas_semanales * ROUND(DATEDIFF(p.fecha_fin_estimada, p.fecha_inicio) / 7, 2), 2),
        2
    ) AS costo_total
FROM asignacion a
JOIN proyecto p ON p.id = a.proyecto_id
JOIN usuario u ON u.id = a.colaborador_id
WHERE p.nombre LIKE '[DEMO]%'
ORDER BY p.nombre, a.estado, colaborador;

-- Resumen presupuestario esperado por proyecto, usando las mismas reglas de
-- comprometido y reservado que la aplicacion.
SELECT
    p.nombre AS proyecto,
    p.presupuesto AS presupuesto_total,
    ROUND(SUM(CASE
        WHEN a.estado = 'ACTIVA' THEN
            ROUND(u.sueldo_base / 160, 2)
            * ROUND(a.horas_semanales * ROUND(DATEDIFF(p.fecha_fin_estimada, p.fecha_inicio) / 7, 2), 2)
        ELSE 0 END), 2) AS comprometido,
    ROUND(SUM(CASE
        WHEN a.estado = 'PENDIENTE' AND a.aprobado_por_rm = TRUE THEN
            ROUND(u.sueldo_base / 160, 2)
            * ROUND(a.horas_semanales * ROUND(DATEDIFF(p.fecha_fin_estimada, p.fecha_inicio) / 7, 2), 2)
        ELSE 0 END), 2) AS reservado,
    GREATEST(
        COALESCE(p.presupuesto, 0) - ROUND(SUM(CASE
            WHEN a.estado = 'ACTIVA'
                 OR (a.estado = 'PENDIENTE' AND a.aprobado_por_rm = TRUE) THEN
                ROUND(u.sueldo_base / 160, 2)
                * ROUND(a.horas_semanales * ROUND(DATEDIFF(p.fecha_fin_estimada, p.fecha_inicio) / 7, 2), 2)
            ELSE 0 END), 2),
        0
    ) AS disponible
FROM proyecto p
LEFT JOIN asignacion a ON a.proyecto_id = p.id
LEFT JOIN usuario u ON u.id = a.colaborador_id
WHERE p.nombre LIKE '[DEMO]%'
GROUP BY p.id, p.nombre, p.presupuesto
ORDER BY p.nombre;

SELECT
    f.id,
    f.nombre,
    f.tipo,
    f.es_publico,
    COALESCE(p.nombre, 'Comunidad general') AS proyecto,
    COUNT(DISTINCT pub.id) AS publicaciones,
    COUNT(DISTINCT resp.id) AS respuestas
FROM foro f
LEFT JOIN proyecto p ON p.id = f.proyecto_id
LEFT JOIN publicacion_foro pub ON pub.foro_id = f.id
LEFT JOIN respuesta_foro resp ON resp.publicacion_id = pub.id
WHERE f.nombre LIKE '[DEMO]%'
GROUP BY f.id, f.nombre, f.tipo, f.es_publico, p.nombre
ORDER BY f.fecha_creacion DESC;

SELECT
    DATE_FORMAT(a.fecha_entrega, '%Y-%m') AS periodo,
    p.nombre AS proyecto,
    CONCAT(c.nombre, ' ', c.apellido) AS colaborador,
    a.estado,
    SUM(a.horas_estimadas) AS horas
FROM actividad a
JOIN proyecto p ON p.id = a.proyecto_id
JOIN usuario c ON c.id = a.colaborador_id
WHERE a.titulo LIKE '[DEMO]%'
GROUP BY DATE_FORMAT(a.fecha_entrega, '%Y-%m'), p.nombre,
         CONCAT(c.nombre, ' ', c.apellido), a.estado
ORDER BY periodo DESC, proyecto, colaborador;

SELECT
    c.nombre AS curso,
    c.activo,
    CONCAT(u.nombre, ' ', u.apellido) AS colaborador,
    cc.origen,
    cc.estado,
    cc.motivo_respuesta,
    cc.evidencia_url,
    cc.fecha_evidencia,
    cc.fecha_completado
FROM curso c
LEFT JOIN colaborador_curso cc ON cc.curso_id = c.id
LEFT JOIN usuario u ON u.id = cc.colaborador_id
WHERE c.nombre LIKE '[DEMO]%'
ORDER BY c.nombre, colaborador;



