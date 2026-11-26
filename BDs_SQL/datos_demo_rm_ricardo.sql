-- ============================================================================
-- SkillBridge AI - Datos de demostracion para el RM Ricardo Mendez
-- Compatible con: BDs_SQL/skillbridge_db_v4.sql (usuarios base del final)
-- Base de datos:   MySQL 8.0.19+
-- ============================================================================
--
-- Deja listo para rm@skillbridge.com (Ricardo Mendez):
--   1. 1 proyecto EN_REVISION                 -> [RICARDO] App de Inventarios
--   2. 1 proyecto ACTIVO con equipo           -> [RICARDO] Portal de Clientes (Carlos activo)
--   3. 1 asignacion pendiente del RM          -> Lucia, propuesta por el PM (aprobada por PM)
--   4. 1 postulacion de un colaborador        -> Andres, solicitada por el colaborador
--   5. 1 solicitud de personal PENDIENTE      -> Portal de Clientes
--   6. 1 certificado y 1 formacion pendientes -> Lucia
--   7. 1 solicitud de curso y 1 evidencia     -> Andres (SOLICITADO) y Lucia (EVIDENCIA_PENDIENTE)
--   8. Mensajes con el PM                     -> chat privado Pedro <-> Ricardo
--
-- Usuarios base: rm@ (Ricardo), pm@ (Pedro Martinez), col@ (Carlos Lopez).
-- Se crean dos colaboradores con la misma contrasena que los usuarios base:
--   lucia.paredes@skillbridge.com, andres.quispe@skillbridge.com
--
-- Re-ejecutable: inserta solo lo que falta (marcador [RICARDO] y correos fijos).
-- Los proyectos quedan con rm_revisor_id = Ricardo y las revisiones con
-- rm_responsable_id = Ricardo, para que RmAlcanceService se las muestre a el.
-- ============================================================================
SET NAMES utf8mb4;
USE skillbridge_db;

START TRANSACTION;

SET @rm_id    := (SELECT id FROM usuario WHERE correo = 'rm@skillbridge.com');
SET @pm_id    := (SELECT id FROM usuario WHERE correo = 'pm@skillbridge.com');
SET @col_id   := (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com');
SET @admin_id := (SELECT id FROM usuario WHERE correo = 'admin@skillbridge.com');
SET @hash     := (SELECT password_hash FROM usuario WHERE correo = 'rm@skillbridge.com');

SET @hab_java   := (SELECT h.id FROM habilidad h JOIN categoria_habilidad c ON c.id = h.categoria_id
                    WHERE h.nombre = 'Java' AND c.nombre = 'Técnico');
SET @hab_spring := (SELECT h.id FROM habilidad h JOIN categoria_habilidad c ON c.id = h.categoria_id
                    WHERE h.nombre = 'Spring Boot' AND c.nombre = 'Técnico');
SET @hab_react  := (SELECT h.id FROM habilidad h JOIN categoria_habilidad c ON c.id = h.categoria_id
                    WHERE h.nombre = 'React' AND c.nombre = 'Técnico');
SET @hab_aws    := (SELECT h.id FROM habilidad h JOIN categoria_habilidad c ON c.id = h.categoria_id
                    WHERE h.nombre = 'AWS' AND c.nombre = 'Técnico');

-- --------------------------------------------------------------------------
-- 0. Colaboradores (Carlos recibe sueldo si no tenia, para calcular costos)
-- --------------------------------------------------------------------------
UPDATE usuario
SET sueldo_base = COALESCE(sueldo_base, 3500.00),
    horas_contratadas_semana = COALESCE(horas_contratadas_semana, 40.00),
    nivel_experiencia = COALESCE(nivel_experiencia, 'SEMI_SENIOR'),
    anios_experiencia = COALESCE(anios_experiencia, 3.0)
WHERE id = @col_id;

INSERT INTO usuario (
    correo, password_hash, nombre, apellido, telefono, descripcion, rol_id, activo,
    cargo_id, horas_contratadas_semana, horas_disponibles, sueldo_base,
    anios_experiencia, nivel_experiencia, fecha_contratacion
)
SELECT 'lucia.paredes@skillbridge.com', @hash, 'Lucia', 'Paredes', '987654321',
       'Desarrolladora frontend para la demo del RM.',
       (SELECT id FROM rol WHERE nombre = 'COLABORADOR'), TRUE,
       (SELECT id FROM cargo WHERE nombre = 'Frontend Developer'), 40.00, 40.00, 3200.00,
       3.0, 'SEMI_SENIOR', '2024-02-12'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE correo = 'lucia.paredes@skillbridge.com');

INSERT INTO usuario (
    correo, password_hash, nombre, apellido, telefono, descripcion, rol_id, activo,
    cargo_id, horas_contratadas_semana, horas_disponibles, sueldo_base,
    anios_experiencia, nivel_experiencia, fecha_contratacion
)
SELECT 'andres.quispe@skillbridge.com', @hash, 'Andres', 'Quispe', '987654322',
       'Desarrollador backend junior para la demo del RM.',
       (SELECT id FROM rol WHERE nombre = 'COLABORADOR'), TRUE,
       (SELECT id FROM cargo WHERE nombre = 'Backend Developer'), 40.00, 40.00, 2000.00,
       1.0, 'JUNIOR', '2025-06-02'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE correo = 'andres.quispe@skillbridge.com');

SET @lucia_id  := (SELECT id FROM usuario WHERE correo = 'lucia.paredes@skillbridge.com');
SET @andres_id := (SELECT id FROM usuario WHERE correo = 'andres.quispe@skillbridge.com');

-- --------------------------------------------------------------------------
-- 1. Proyecto EN_REVISION
-- --------------------------------------------------------------------------
INSERT INTO proyecto (
    nombre, descripcion, fecha_inicio, fecha_fin_estimada, estado, prioridad,
    justificacion_prioridad, presupuesto_solicitado, justificacion_presupuesto,
    presupuesto, colaboradores_requeridos, horas_semanales_requeridas,
    pm_id, rm_revisor_id, fecha_creacion
)
SELECT '[RICARDO] App de Inventarios',
       'Aplicacion para controlar el stock de almacenes en tiempo real.',
       CURRENT_DATE + INTERVAL 20 DAY, CURRENT_DATE + INTERVAL 140 DAY, 'EN_REVISION', 'MEDIA',
       'Reduce quiebres de stock reportados por logistica.',
       35000.00, 'Desarrollo web, base de datos y pruebas.',
       NULL, 3, 20.00, @pm_id, @rm_id, CURRENT_TIMESTAMP - INTERVAL 1 DAY
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM proyecto WHERE nombre = '[RICARDO] App de Inventarios');

-- --------------------------------------------------------------------------
-- 2. Proyecto ACTIVO con equipo
-- --------------------------------------------------------------------------
INSERT INTO proyecto (
    nombre, descripcion, fecha_inicio, fecha_fin_estimada, estado, prioridad,
    justificacion_prioridad, presupuesto_solicitado, justificacion_presupuesto,
    presupuesto, colaboradores_requeridos, horas_semanales_requeridas,
    pm_id, rm_revisor_id, fecha_creacion
)
SELECT '[RICARDO] Portal de Clientes',
       'Portal de autoservicio para que los clientes consulten pedidos y facturas.',
       CURRENT_DATE - INTERVAL 15 DAY, CURRENT_DATE + INTERVAL 120 DAY, 'ACTIVO', 'ALTA',
       'Compromiso con el area comercial para el siguiente trimestre.',
       60000.00, 'Equipo backend, frontend y despliegue en AWS.',
       60000.00, 3, 20.00, @pm_id, @rm_id, CURRENT_TIMESTAMP - INTERVAL 25 DAY
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM proyecto WHERE nombre = '[RICARDO] Portal de Clientes');

SET @proy_rev := (SELECT id FROM proyecto WHERE nombre = '[RICARDO] App de Inventarios');
SET @proy_act := (SELECT id FROM proyecto WHERE nombre = '[RICARDO] Portal de Clientes');

INSERT IGNORE INTO proyecto_habilidad_requerida (proyecto_id, habilidad_id, nivel_requerido, cantidad_personas, horas_semanales) VALUES
    (@proy_rev, @hab_java,   'INTERMEDIO', 2, 20.00),
    (@proy_rev, @hab_react,  'BASICO',     1, 20.00),
    (@proy_act, @hab_spring, 'INTERMEDIO', 1, 20.00),
    (@proy_act, @hab_react,  'INTERMEDIO', 1, 20.00),
    (@proy_act, @hab_java,   'BASICO',     1, 20.00);

-- --------------------------------------------------------------------------
-- 3 y 4. Asignaciones: equipo activo, pendiente del RM y postulacion
-- --------------------------------------------------------------------------
-- Carlos: ACTIVA (equipo del proyecto)
INSERT INTO asignacion (
    proyecto_id, colaborador_id, horas_semanales, origen, mensaje_solicitud, estado,
    aprobado_por_pm, aprobado_por_rm, fecha_aprobacion_pm, fecha_aprobacion_rm,
    fecha_solicitud, fecha_activacion
)
SELECT @proy_act, @col_id, 20.00, 'PROPUESTA_PM', 'Backend del portal.', 'ACTIVA',
       TRUE, TRUE, CURRENT_TIMESTAMP - INTERVAL 14 DAY, CURRENT_TIMESTAMP - INTERVAL 13 DAY,
       CURRENT_TIMESTAMP - INTERVAL 15 DAY, CURRENT_TIMESTAMP - INTERVAL 13 DAY
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM asignacion WHERE proyecto_id = @proy_act AND colaborador_id = @col_id AND estado = 'ACTIVA');

-- Lucia: propuesta por el PM, ya aprobada por el PM -> pendiente del RM
INSERT INTO asignacion (
    proyecto_id, colaborador_id, horas_semanales, origen, mensaje_solicitud,
    habilidad_solicitada_id, estado, aprobado_por_pm, aprobado_por_rm,
    fecha_aprobacion_pm, fecha_solicitud
)
SELECT @proy_act, @lucia_id, 20.00, 'PROPUESTA_PM', 'Necesitamos frontend en React para el portal.',
       @hab_react, 'PENDIENTE', TRUE, FALSE, CURRENT_TIMESTAMP - INTERVAL 1 DAY, CURRENT_TIMESTAMP - INTERVAL 1 DAY
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM asignacion WHERE proyecto_id = @proy_act AND colaborador_id = @lucia_id AND estado = 'PENDIENTE');

-- Andres: postulacion del colaborador (Explorar -> Postular)
INSERT INTO asignacion (
    proyecto_id, colaborador_id, horas_semanales, origen, mensaje_solicitud,
    habilidad_solicitada_id, estado, aprobado_por_pm, aprobado_por_rm, fecha_solicitud
)
SELECT @proy_act, @andres_id, 20.00, 'SOLICITADA_COLABORADOR',
       'Me interesa apoyar en el backend con Java y aprender del equipo.',
       @hab_java, 'PENDIENTE', FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL 2 HOUR
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM asignacion WHERE proyecto_id = @proy_act AND colaborador_id = @andres_id AND estado = 'PENDIENTE');

-- --------------------------------------------------------------------------
-- 5. Solicitud de personal PENDIENTE
-- --------------------------------------------------------------------------
INSERT INTO solicitud_personal (
    proyecto_id, cantidad_colaboradores, perfiles_requeridos, mensaje_pm,
    estado, rm_responsable_id, fecha_solicitud
)
SELECT @proy_act, 1, 'Backend Developer con Spring Boot (intermedio).',
       '[RICARDO] Necesitamos reforzar el backend para la fase de facturacion.',
       'PENDIENTE', @rm_id, CURRENT_TIMESTAMP - INTERVAL 3 HOUR
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM solicitud_personal WHERE mensaje_pm LIKE '[RICARDO]%');

-- --------------------------------------------------------------------------
-- 6. Certificado y formacion academica pendientes (Lucia)
-- --------------------------------------------------------------------------
INSERT INTO certificado (colaborador_id, habilidad_id, archivo_url, estado, rm_responsable_id, fecha_subida)
SELECT @lucia_id, @hab_aws, '/documentos/certificado-demo-aws.txt', 'PENDIENTE', @rm_id,
       CURRENT_TIMESTAMP - INTERVAL 5 HOUR
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM certificado WHERE colaborador_id = @lucia_id AND habilidad_id = @hab_aws AND estado = 'PENDIENTE');

INSERT INTO educacion (
    colaborador_id, institucion, titulo, archivo_url, fecha_inicio, fecha_fin,
    actual, estado, rm_responsable_id, activo, fecha_creacion
)
SELECT @lucia_id, 'Universidad Nacional de Ingenieria', 'Ingenieria de Sistemas',
       '/documentos/formacion-demo-electronica.txt', '2016-03-01', '2021-12-15',
       FALSE, 'PENDIENTE', @rm_id, TRUE, CURRENT_TIMESTAMP - INTERVAL 4 HOUR
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM educacion WHERE colaborador_id = @lucia_id AND titulo = 'Ingenieria de Sistemas');

-- --------------------------------------------------------------------------
-- 7. Solicitud de curso (Andres) y evidencia pendiente (Lucia)
-- --------------------------------------------------------------------------
INSERT INTO curso (nombre, descripcion, categoria, modalidad, autodidacta, horas, activo, creado_por)
SELECT '[RICARDO] React profesional', 'Componentes, hooks y pruebas en React.',
       'Técnico', 'VIRTUAL', TRUE, 16.00, TRUE, @admin_id
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM curso WHERE nombre = '[RICARDO] React profesional');

INSERT INTO curso (nombre, descripcion, categoria, modalidad, autodidacta, horas, activo, creado_por)
SELECT '[RICARDO] Spring Boot desde cero', 'APIs REST, JPA y seguridad con Spring Boot.',
       'Técnico', 'VIRTUAL', TRUE, 20.00, TRUE, @admin_id
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM curso WHERE nombre = '[RICARDO] Spring Boot desde cero');

SET @curso_react  := (SELECT id FROM curso WHERE nombre = '[RICARDO] React profesional');
SET @curso_spring := (SELECT id FROM curso WHERE nombre = '[RICARDO] Spring Boot desde cero');

INSERT INTO colaborador_curso (
    colaborador_id, curso_id, origen, estado, rm_responsable_id,
    justificacion_colaborador, fecha_solicitud
)
SELECT @andres_id, @curso_spring, 'SOLICITUD_COLABORADOR', 'SOLICITADO', @rm_id,
       'Lo necesito para postular a proyectos backend.', CURRENT_TIMESTAMP - INTERVAL 6 HOUR
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM colaborador_curso WHERE colaborador_id = @andres_id AND curso_id = @curso_spring);

INSERT INTO colaborador_curso (
    colaborador_id, curso_id, origen, estado, asignado_por, rm_responsable_id,
    fecha_solicitud, fecha_respuesta, evidencia_url, fecha_evidencia
)
SELECT @lucia_id, @curso_react, 'SOLICITUD_COLABORADOR', 'EVIDENCIA_PENDIENTE', @rm_id, @rm_id,
       CURRENT_TIMESTAMP - INTERVAL 20 DAY, CURRENT_TIMESTAMP - INTERVAL 19 DAY,
       '/documentos/evidencia-curso-demo-spring.txt', CURRENT_TIMESTAMP - INTERVAL 2 HOUR
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM colaborador_curso WHERE colaborador_id = @lucia_id AND curso_id = @curso_react);

-- --------------------------------------------------------------------------
-- 8. Chat privado PM (Pedro) <-> RM (Ricardo) en el proyecto activo
-- --------------------------------------------------------------------------
INSERT INTO conversacion (tipo, proyecto_id, fecha_creacion)
SELECT 'PRIVADA', @proy_act, CURRENT_TIMESTAMP - INTERVAL 2 DAY
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM conversacion c
    JOIN conversacion_usuario a ON a.conversacion_id = c.id AND a.usuario_id = @rm_id
    JOIN conversacion_usuario b ON b.conversacion_id = c.id AND b.usuario_id = @pm_id
    WHERE c.proyecto_id = @proy_act AND c.tipo = 'PRIVADA');

SET @conv_id := (
    SELECT c.id FROM conversacion c
    WHERE c.proyecto_id = @proy_act AND c.tipo = 'PRIVADA'
      AND (SELECT COUNT(*) FROM conversacion_usuario cu WHERE cu.conversacion_id = c.id) = 0
    ORDER BY c.id DESC LIMIT 1);

INSERT INTO conversacion_usuario (conversacion_id, usuario_id)
SELECT @conv_id, @rm_id FROM DUAL WHERE @conv_id IS NOT NULL;
INSERT INTO conversacion_usuario (conversacion_id, usuario_id)
SELECT @conv_id, @pm_id FROM DUAL WHERE @conv_id IS NOT NULL;

-- Mensajes solo cuando la conversacion se acaba de crear
INSERT INTO mensaje (conversacion_id, autor_id, contenido, fecha_hora)
SELECT @conv_id, x.autor, x.texto, x.fecha FROM (
    SELECT @pm_id AS autor, 'Hola Ricardo, te propuse a Lucia para el frontend del portal.' AS texto,
           CURRENT_TIMESTAMP - INTERVAL 1 DAY AS fecha
    UNION ALL SELECT @rm_id, 'Gracias Pedro, reviso su disponibilidad y el presupuesto hoy.',
           CURRENT_TIMESTAMP - INTERVAL 23 HOUR
    UNION ALL SELECT @pm_id, 'Tambien te envie una solicitud de personal para backend.',
           CURRENT_TIMESTAMP - INTERVAL 3 HOUR
) x WHERE @conv_id IS NOT NULL;

COMMIT;

-- --------------------------------------------------------------------------
-- Comprobacion rapida
-- --------------------------------------------------------------------------
SELECT 'Proyecto EN_REVISION' AS dato, COUNT(*) AS total FROM proyecto WHERE nombre = '[RICARDO] App de Inventarios' AND estado = 'EN_REVISION'
UNION ALL SELECT 'Proyecto ACTIVO', COUNT(*) FROM proyecto WHERE nombre = '[RICARDO] Portal de Clientes' AND estado = 'ACTIVO'
UNION ALL SELECT 'Asignacion pendiente RM', COUNT(*) FROM asignacion WHERE proyecto_id = @proy_act AND estado = 'PENDIENTE' AND aprobado_por_pm = TRUE
UNION ALL SELECT 'Postulacion colaborador', COUNT(*) FROM asignacion WHERE proyecto_id = @proy_act AND estado = 'PENDIENTE' AND origen = 'SOLICITADA_COLABORADOR'
UNION ALL SELECT 'Solicitud de personal', COUNT(*) FROM solicitud_personal WHERE mensaje_pm LIKE '[RICARDO]%' AND estado = 'PENDIENTE'
UNION ALL SELECT 'Certificado pendiente', COUNT(*) FROM certificado WHERE colaborador_id = @lucia_id AND estado = 'PENDIENTE'
UNION ALL SELECT 'Formacion pendiente', COUNT(*) FROM educacion WHERE colaborador_id = @lucia_id AND estado = 'PENDIENTE'
UNION ALL SELECT 'Solicitud de curso', COUNT(*) FROM colaborador_curso WHERE curso_id = @curso_spring AND estado = 'SOLICITADO'
UNION ALL SELECT 'Evidencia de curso', COUNT(*) FROM colaborador_curso WHERE curso_id = @curso_react AND estado = 'EVIDENCIA_PENDIENTE'
UNION ALL SELECT 'Mensajes PM-RM', COUNT(*) FROM mensaje m JOIN conversacion c ON c.id = m.conversacion_id
          WHERE c.proyecto_id = @proy_act AND c.tipo = 'PRIVADA';
