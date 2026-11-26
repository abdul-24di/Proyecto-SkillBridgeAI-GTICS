-- ============================================================================
-- SkillBridge AI - Datos demo para la PRESENTACION FINAL (equipo y revisores)
-- Compatible con: BDs_SQL/skillbridge_db_v4.sql
-- Base de datos:   MySQL 8.0.19+
-- ============================================================================
--
-- QUE HACE
--   Crea un "mundo" demo completo (Admin, RM, PM, 4 colaboradores, invitacion
--   pendiente y usuario inactivo) con datos en TODOS los estados para recorrer
--   cada flujo de los 4 roles. El mundo se identifica por @grupo:
--
--     @grupo = 'expo' -> cuentas expo.*@skillbridge.demo  y datos [EXPO] (equipo)
--     @grupo = 'jp'   -> cuentas jp.*@skillbridge.demo    y datos [JP]   (JPs/profesora)
--
--   Cada grupo usa una contrasena distinta (solo esta su hash BCrypt). Las
--   contrasenas en texto plano NO se versionan: estan en la guia local
--   .claude/CREDENCIALES_PRESENTACION.md.
--
-- RE-EJECUTABLE (sirve de "RESET")
--   Antes de insertar BORRA todo lo del grupo: sus usuarios (correo
--   <grupo>.*@skillbridge.demo), los proyectos de su PM o con la etiqueta
--   [GRUPO], sus cursos, foros [GRUPO], mensajes, notificaciones, auditoria
--   y sesiones. Lo de otros grupos y los usuarios base del v4 no se borra.
--   Cerrar sesion con las cuentas del grupo antes de ejecutarlo.
--
-- AISLAMIENTO (leer antes de usar)
--   La app reparte proyectos, certificados, formacion y cursos al RM ACTIVO
--   con menos carga, y el Admin ve y gestiona a TODOS los usuarios. Por eso
--   lo recomendado es una BASE DISTINTA por grupo (p. ej. skillbridge_db para
--   'jp' y skillbridge_expo para 'expo', cada una con su contenedor).
--   Con @solo_rm_del_grupo = 1 se desactivan los demas RM de la base para que
--   todo lo nuevo llegue al RM del grupo. Si se cargan los 2 grupos en la
--   MISMA base, poner @solo_rm_del_grupo = 0 en ambas ejecuciones.
--
-- ARCHIVOS
--   Las URL de certificados, CV, evidencias y documentos son de ejemplo (no
--   existen en S3). Aprobar/rechazar funciona; "ver archivo" no. Para mostrar
--   un archivo real, subirlo desde la app (perfil del colaborador, actividad).
-- ============================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;   -- misma intercalacion que las tablas (evita el error 1267)
-- USE skillbridge_db;   -- descomentar y ajustar el nombre de la base

-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>> PARAMETROS <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
SET @grupo             := 'expo';   -- 'expo' (equipo) o 'jp' (revisores)
SET @solo_rm_del_grupo := 1;        -- 1 = base exclusiva del grupo; 0 = base compartida
-- >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>><<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

SET SQL_SAFE_UPDATES = 0;

SET @expo := (@grupo = 'expo');
SET @pat  := CONCAT(@grupo, '.%@skillbridge.demo');
SET @etq  := CONCAT('[', UPPER(@grupo), '] ');
SET @lbl  := CONCAT('[', UPPER(@grupo), ']%');
SET @hash := IF(@expo,
    '$2a$10$yvuivJeMOAQYbSVy7GcwsOOyCT8BUZDydazV9OUSRXnbtp/6k48mO',   -- grupo expo
    '$2a$10$Hayn8AvmLmpbUbCEzCCPsebV/UkkWV3AEeSH8duXUO08ZaKPgyBIS');  -- grupo jp (y otros)

-- ============================================================================
-- 0. LIMPIEZA DEL GRUPO (reset)
-- ============================================================================
DROP TABLE IF EXISTS _demo_u, _demo_p, _demo_f, _demo_pub, _demo_res, _demo_c,
                     _demo_asig, _demo_act, _demo_conv;

CREATE TABLE _demo_u    AS SELECT id FROM usuario WHERE correo LIKE @pat;
CREATE TABLE _demo_p    AS SELECT id FROM proyecto
                           WHERE nombre LIKE @lbl OR pm_id IN (SELECT id FROM _demo_u);
CREATE TABLE _demo_f    AS SELECT id FROM foro
                           WHERE nombre LIKE @lbl OR proyecto_id IN (SELECT id FROM _demo_p);
CREATE TABLE _demo_pub  AS SELECT id FROM publicacion_foro
                           WHERE foro_id IN (SELECT id FROM _demo_f) OR autor_id IN (SELECT id FROM _demo_u);
CREATE TABLE _demo_res  AS SELECT id FROM respuesta_foro
                           WHERE publicacion_id IN (SELECT id FROM _demo_pub) OR autor_id IN (SELECT id FROM _demo_u);
CREATE TABLE _demo_c    AS SELECT id FROM curso
                           WHERE nombre LIKE @lbl OR creado_por IN (SELECT id FROM _demo_u);
CREATE TABLE _demo_asig AS SELECT id FROM asignacion
                           WHERE proyecto_id IN (SELECT id FROM _demo_p)
                              OR colaborador_id IN (SELECT id FROM _demo_u)
                              OR rechazado_por IN (SELECT id FROM _demo_u)
                              OR desasignado_por IN (SELECT id FROM _demo_u);
CREATE TABLE _demo_act  AS SELECT id FROM actividad
                           WHERE proyecto_id IN (SELECT id FROM _demo_p)
                              OR colaborador_id IN (SELECT id FROM _demo_u)
                              OR creado_por IN (SELECT id FROM _demo_u);
CREATE TABLE _demo_conv AS SELECT id FROM conversacion WHERE proyecto_id IN (SELECT id FROM _demo_p);

DELETE FROM voto_respuesta   WHERE usuario_id IN (SELECT id FROM _demo_u) OR respuesta_id IN (SELECT id FROM _demo_res);
DELETE FROM voto_publicacion WHERE usuario_id IN (SELECT id FROM _demo_u) OR publicacion_id IN (SELECT id FROM _demo_pub);
DELETE FROM respuesta_foro   WHERE id IN (SELECT id FROM _demo_res);
DELETE FROM publicacion_foro WHERE id IN (SELECT id FROM _demo_pub);
DELETE FROM foro             WHERE id IN (SELECT id FROM _demo_f);

DELETE FROM mensaje              WHERE conversacion_id IN (SELECT id FROM _demo_conv) OR autor_id IN (SELECT id FROM _demo_u);
DELETE FROM conversacion_usuario WHERE conversacion_id IN (SELECT id FROM _demo_conv) OR usuario_id IN (SELECT id FROM _demo_u);
DELETE FROM conversacion         WHERE id IN (SELECT id FROM _demo_conv);

DELETE FROM evaluacion   WHERE asignacion_id IN (SELECT id FROM _demo_asig)
                            OR colaborador_id IN (SELECT id FROM _demo_u) OR evaluador_id IN (SELECT id FROM _demo_u);
DELETE FROM penalizacion WHERE actividad_id IN (SELECT id FROM _demo_act)
                            OR proyecto_id IN (SELECT id FROM _demo_p) OR colaborador_id IN (SELECT id FROM _demo_u);
DELETE FROM actividad    WHERE id IN (SELECT id FROM _demo_act);
DELETE FROM asignacion   WHERE id IN (SELECT id FROM _demo_asig);
DELETE FROM documento    WHERE proyecto_id IN (SELECT id FROM _demo_p) OR subido_por_id IN (SELECT id FROM _demo_u);

DELETE FROM solicitud_personal WHERE proyecto_id IN (SELECT id FROM _demo_p);
UPDATE solicitud_personal SET rm_responsable_id = NULL WHERE rm_responsable_id IN (SELECT id FROM _demo_u);
DELETE FROM proyecto_habilidad_requerida WHERE proyecto_id IN (SELECT id FROM _demo_p);

DELETE FROM colaborador_curso WHERE curso_id IN (SELECT id FROM _demo_c) OR colaborador_id IN (SELECT id FROM _demo_u);
UPDATE colaborador_curso SET asignado_por = NULL           WHERE asignado_por IN (SELECT id FROM _demo_u);
UPDATE colaborador_curso SET evidencia_revisada_por = NULL WHERE evidencia_revisada_por IN (SELECT id FROM _demo_u);
UPDATE colaborador_curso SET rm_responsable_id = NULL      WHERE rm_responsable_id IN (SELECT id FROM _demo_u);
DELETE FROM curso WHERE id IN (SELECT id FROM _demo_c);

DELETE FROM certificado WHERE colaborador_id IN (SELECT id FROM _demo_u);
UPDATE certificado SET revisado_por = NULL      WHERE revisado_por IN (SELECT id FROM _demo_u);
UPDATE certificado SET rm_responsable_id = NULL WHERE rm_responsable_id IN (SELECT id FROM _demo_u);
DELETE FROM educacion WHERE colaborador_id IN (SELECT id FROM _demo_u);
UPDATE educacion SET revisado_por = NULL      WHERE revisado_por IN (SELECT id FROM _demo_u);
UPDATE educacion SET rm_responsable_id = NULL WHERE rm_responsable_id IN (SELECT id FROM _demo_u);

DELETE FROM colaborador_habilidad   WHERE colaborador_id IN (SELECT id FROM _demo_u);
DELETE FROM experiencia_profesional WHERE colaborador_id IN (SELECT id FROM _demo_u);
DELETE FROM token_usuario           WHERE usuario_id IN (SELECT id FROM _demo_u);
DELETE FROM notificacion            WHERE usuario_id IN (SELECT id FROM _demo_u);
DELETE FROM nomina_mensual          WHERE colaborador_id IN (SELECT id FROM _demo_u);
DELETE FROM log_auditoria           WHERE usuario_id IN (SELECT id FROM _demo_u);

UPDATE proyecto SET rm_revisor_id = NULL
WHERE rm_revisor_id IN (SELECT id FROM _demo_u) AND id NOT IN (SELECT id FROM _demo_p);
DELETE FROM proyecto WHERE id IN (SELECT id FROM _demo_p);
DELETE FROM usuario  WHERE id IN (SELECT id FROM _demo_u);

DROP TABLE IF EXISTS _demo_u, _demo_p, _demo_f, _demo_pub, _demo_res, _demo_c,
                     _demo_asig, _demo_act, _demo_conv;

-- ============================================================================
-- 1. CATALOGOS QUE SE USAN
-- ============================================================================
SET @r_admin := (SELECT id FROM rol WHERE nombre = 'ADMINISTRADOR');
SET @r_pm    := (SELECT id FROM rol WHERE nombre = 'PROJECT_MANAGER');
SET @r_rm    := (SELECT id FROM rol WHERE nombre = 'RESOURCE_MANAGER');
SET @r_col   := (SELECT id FROM rol WHERE nombre = 'COLABORADOR');

SET @c_back   := (SELECT id FROM cargo WHERE nombre = 'Backend Developer');
SET @c_front  := (SELECT id FROM cargo WHERE nombre = 'Frontend Developer');
SET @c_ux     := (SELECT id FROM cargo WHERE nombre = 'UX Designer');
SET @c_devops := (SELECT id FROM cargo WHERE nombre = 'DevOps Engineer');

SET @h_java   := (SELECT MIN(id) FROM habilidad WHERE nombre = 'Java');
SET @h_python := (SELECT MIN(id) FROM habilidad WHERE nombre = 'Python');
SET @h_js     := (SELECT MIN(id) FROM habilidad WHERE nombre = 'JavaScript');
SET @h_spring := (SELECT MIN(id) FROM habilidad WHERE nombre = 'Spring Boot');
SET @h_react  := (SELECT MIN(id) FROM habilidad WHERE nombre = 'React');
SET @h_ang    := (SELECT MIN(id) FROM habilidad WHERE nombre = 'Angular');
SET @h_mysql  := (SELECT MIN(id) FROM habilidad WHERE nombre = 'MySQL');
SET @h_pg     := (SELECT MIN(id) FROM habilidad WHERE nombre = 'PostgreSQL');
SET @h_aws    := (SELECT MIN(id) FROM habilidad WHERE nombre = 'AWS');
SET @h_gcp    := (SELECT MIN(id) FROM habilidad WHERE nombre = 'GCP');
SET @h_docker := (SELECT MIN(id) FROM habilidad WHERE nombre = 'Docker');
SET @h_git    := (SELECT MIN(id) FROM habilidad WHERE nombre = 'Git');
SET @h_scrum  := (SELECT MIN(id) FROM habilidad WHERE nombre = 'Scrum');

INSERT IGNORE INTO etiqueta (nombre) VALUES ('Consulta'), ('Aviso'), ('Buenas prácticas');
SET @e_consulta := (SELECT id FROM etiqueta WHERE nombre = 'Consulta');
SET @e_aviso    := (SELECT id FROM etiqueta WHERE nombre = 'Aviso');
SET @e_bp       := (SELECT id FROM etiqueta WHERE nombre = 'Buenas prácticas');

-- Mas holgura para que el RM del grupo reciba los proyectos nuevos que se creen en vivo.
UPDATE configuracion_sistema SET valor = '10'
WHERE clave = 'MAX_PROYECTOS_POR_RM' AND CAST(valor AS UNSIGNED) < 10;

-- ============================================================================
-- 2. USUARIOS
-- ============================================================================
INSERT INTO usuario (correo, password_hash, nombre, apellido, telefono, descripcion, rol_id, activo) VALUES
 (CONCAT(@grupo, '.admin@skillbridge.demo'), @hash, IF(@expo, 'Mariana', 'Elena'), IF(@expo, 'Salazar', 'Quispe'),
  '987100001', 'Administradora de la plataforma.', @r_admin, TRUE),
 (CONCAT(@grupo, '.rm@skillbridge.demo'),    @hash, IF(@expo, 'Valeria', 'Martín'), IF(@expo, 'Rojas', 'Chávez'),
  '987100002', 'Resource Manager: aprueba proyectos, asignaciones, certificados y cursos.', @r_rm, TRUE),
 (CONCAT(@grupo, '.pm@skillbridge.demo'),    @hash, IF(@expo, 'Diego', 'Andrea'), IF(@expo, 'Castillo', 'Flores'),
  '987100003', 'Project Manager de los proyectos de demostración.', @r_pm, TRUE);

INSERT INTO usuario (correo, password_hash, nombre, apellido, telefono, descripcion, rol_id, activo,
    cargo_id, horas_contratadas_semana, horas_disponibles, sueldo_base, anios_experiencia,
    nivel_experiencia, fecha_contratacion, registro_estado, cv_estado, cv_url, cv_fecha_subida) VALUES
 -- col1: el colaborador "completo" (proyecto activo, actividades, cursos, certificados, historial)
 (CONCAT(@grupo, '.col1@skillbridge.demo'), @hash, IF(@expo, 'Sofía', 'Luis'), IF(@expo, 'Huamán', 'Mendoza'),
  '987100011', 'Backend developer con experiencia en Java, Spring Boot y bases de datos relacionales.',
  @r_col, TRUE, @c_back, 40, 20, 3500.00, 3.0, 'SEMI_SENIOR', DATE_SUB(CURDATE(), INTERVAL 2 YEAR),
  'APROBADO', 'REVISADO', 'demo/cv/cv-col1.pdf', NOW() - INTERVAL 300 DAY),
 -- col2: frontend, activo en el proyecto y con una postulacion pendiente del PM
 (CONCAT(@grupo, '.col2@skillbridge.demo'), @hash, IF(@expo, 'Mateo', 'Paula'), IF(@expo, 'Vargas', 'Ríos'),
  '987100012', 'Frontend developer enfocado en React y accesibilidad.',
  @r_col, TRUE, @c_front, 40, 20, 3200.00, 2.5, 'SEMI_SENIOR', DATE_SUB(CURDATE(), INTERVAL 18 MONTH),
  'APROBADO', 'REVISADO', 'demo/cv/cv-col2.pdf', NOW() - INTERVAL 200 DAY),
 -- col3: devops libre, candidato para Talent Matching y propuestas
 (CONCAT(@grupo, '.col3@skillbridge.demo'), @hash, IF(@expo, 'Camila', 'Bruno'), IF(@expo, 'Torres', 'Salas'),
  '987100013', 'DevOps junior: AWS, Docker y pipelines de CI/CD.',
  @r_col, TRUE, @c_devops, 40, 40, 2500.00, 1.0, 'JUNIOR', DATE_SUB(CURDATE(), INTERVAL 8 MONTH),
  'APROBADO', 'REVISADO', 'demo/cv/cv-col3.pdf', NOW() - INTERVAL 120 DAY),
 -- col4: pre-registro PENDIENTE (lo aprueba o rechaza el Admin)
 (CONCAT(@grupo, '.col4@skillbridge.demo'), @hash, IF(@expo, 'Joaquín', 'Gabriela'), IF(@expo, 'Paredes', 'Núñez'),
  '987100014', 'Diseñador UX recién incorporado; espera la revisión de su pre-registro.',
  @r_col, TRUE, @c_ux, 40, 40, 1900.00, 0.5, 'JUNIOR', CURDATE(),
  'PENDIENTE', 'PENDIENTE', 'demo/cv/cv-col4.pdf', NOW() - INTERVAL 1 DAY),
 -- inactivo: para probar "reactivar" en el Admin
 (CONCAT(@grupo, '.inactivo@skillbridge.demo'), @hash, IF(@expo, 'Renato', 'Hugo'), IF(@expo, 'Díaz', 'Cárdenas'),
  '987100015', 'Colaborador dado de baja.',
  @r_col, FALSE, @c_back, 40, 40, 2000.00, 1.0, 'JUNIOR', DATE_SUB(CURDATE(), INTERVAL 1 YEAR),
  'APROBADO', 'REVISADO', NULL, NULL);

-- invitado: cuenta creada por el Admin sin activar (sin nombre ni contrasena)
INSERT INTO usuario (correo, rol_id, activo, horas_contratadas_semana, horas_disponibles)
VALUES (CONCAT(@grupo, '.invitado@skillbridge.demo'), @r_col, TRUE, 40, 40);

SET @admin  := (SELECT id FROM usuario WHERE correo = CONCAT(@grupo, '.admin@skillbridge.demo'));
SET @rm     := (SELECT id FROM usuario WHERE correo = CONCAT(@grupo, '.rm@skillbridge.demo'));
SET @pm     := (SELECT id FROM usuario WHERE correo = CONCAT(@grupo, '.pm@skillbridge.demo'));
SET @col1   := (SELECT id FROM usuario WHERE correo = CONCAT(@grupo, '.col1@skillbridge.demo'));
SET @col2   := (SELECT id FROM usuario WHERE correo = CONCAT(@grupo, '.col2@skillbridge.demo'));
SET @col3   := (SELECT id FROM usuario WHERE correo = CONCAT(@grupo, '.col3@skillbridge.demo'));
SET @col4   := (SELECT id FROM usuario WHERE correo = CONCAT(@grupo, '.col4@skillbridge.demo'));
SET @invit  := (SELECT id FROM usuario WHERE correo = CONCAT(@grupo, '.invitado@skillbridge.demo'));

INSERT INTO token_usuario (usuario_id, tipo, token, fecha_expiracion)
VALUES (@invit, 'ACTIVACION', CONCAT('demo-', @grupo, '-', UUID()), NOW() + INTERVAL 1 DAY);

-- Con base exclusiva, el RM del grupo es el unico RM activo (recibe todo lo nuevo).
UPDATE usuario SET activo = FALSE
WHERE @solo_rm_del_grupo = 1 AND rol_id = @r_rm AND activo = TRUE AND id <> @rm;

-- ============================================================================
-- 3. PERFIL PROFESIONAL: experiencia, habilidades, certificados, formacion
-- ============================================================================
INSERT INTO experiencia_profesional (colaborador_id, empresa, cargo, descripcion, fecha_inicio, fecha_fin, actual) VALUES
 (@col1, 'Banco Andino', 'Desarrollador Java', 'APIs REST para banca por internet con Spring Boot.', '2021-03-01', '2023-12-31', FALSE),
 (@col2, 'Retail Digital SAC', 'Frontend Developer', 'Tienda virtual en React con pruebas end-to-end.', '2022-01-10', '2024-05-31', FALSE),
 (@col3, 'Cloud Partners', 'Practicante DevOps', 'Pipelines de CI/CD y despliegues en AWS.', '2024-02-01', '2025-01-31', FALSE),
 (@col4, 'Estudio Creativo', 'Practicante UX', 'Prototipos en Figma y pruebas de usabilidad.', '2025-01-15', '2025-12-15', FALSE);

INSERT INTO colaborador_habilidad (colaborador_id, habilidad_id, nivel_dominio, estado_validacion) VALUES
 (@col1, @h_java,   'AVANZADO',   'VALIDADA'),
 (@col1, @h_spring, 'INTERMEDIO', 'VALIDADA'),
 (@col1, @h_mysql,  'INTERMEDIO', 'VALIDADA'),
 (@col1, @h_git,    'INTERMEDIO', 'VALIDADA'),
 (@col1, @h_docker, 'BASICO',     'PENDIENTE'),
 (@col1, @h_python, 'BASICO',     'RECHAZADA'),
 (@col2, @h_react,  'AVANZADO',   'VALIDADA'),
 (@col2, @h_js,     'AVANZADO',   'VALIDADA'),
 (@col2, @h_git,    'INTERMEDIO', 'VALIDADA'),
 (@col2, @h_ang,    'INTERMEDIO', 'PENDIENTE'),
 (@col3, @h_aws,    'INTERMEDIO', 'VALIDADA'),
 (@col3, @h_docker, 'INTERMEDIO', 'VALIDADA'),
 (@col3, @h_java,   'BASICO',     'VALIDADA'),
 (@col3, @h_mysql,  'BASICO',     'VALIDADA'),
 (@col4, @h_scrum,  'BASICO',     'PENDIENTE');

INSERT INTO certificado (colaborador_id, habilidad_id, archivo_url, estado, nivel_aprobado, revisado_por, rm_responsable_id, fecha_subida, fecha_revision, motivo_rechazo) VALUES
 (@col1, @h_java,   'demo/certificados/col1-java.pdf',   'APROBADO',  'AVANZADO',   @rm, @rm, NOW() - INTERVAL 90 DAY, NOW() - INTERVAL 88 DAY, NULL),
 (@col1, @h_python, 'demo/certificados/col1-python.pdf', 'RECHAZADO', NULL,         @rm, @rm, NOW() - INTERVAL 40 DAY, NOW() - INTERVAL 39 DAY,
  'El certificado no muestra el nombre del colaborador.'),
 (@col3, @h_aws,    'demo/certificados/col3-aws.pdf',    'APROBADO',  'INTERMEDIO', @rm, @rm, NOW() - INTERVAL 60 DAY, NOW() - INTERVAL 59 DAY, NULL);
INSERT INTO certificado (colaborador_id, habilidad_id, archivo_url, estado, rm_responsable_id, fecha_subida) VALUES
 (@col1, @h_docker, 'demo/certificados/col1-docker.pdf',  'PENDIENTE', @rm, NOW() - INTERVAL 2 DAY);
SET @cert_pend := LAST_INSERT_ID();
INSERT INTO certificado (colaborador_id, habilidad_id, archivo_url, estado, rm_responsable_id, fecha_subida) VALUES
 (@col2, @h_ang,    'demo/certificados/col2-angular.pdf', 'PENDIENTE', @rm, NOW() - INTERVAL 1 DAY),
 (@col4, @h_scrum,  'demo/certificados/col4-scrum.pdf',   'PENDIENTE', @rm, NOW() - INTERVAL 1 DAY);

INSERT INTO educacion (colaborador_id, institucion, titulo, archivo_url, fecha_inicio, fecha_fin, actual, estado, motivo_rechazo, revisado_por, rm_responsable_id, fecha_revision) VALUES
 (@col1, 'Pontificia Universidad Católica del Perú', 'Ingeniería de las Telecomunicaciones', 'demo/educacion/col1-titulo.pdf',
  '2015-03-01', '2020-12-15', FALSE, 'APROBADO', NULL, @rm, @rm, NOW() - INTERVAL 100 DAY),
 (@col2, 'Instituto Tecnológico del Sur', 'Diseño Web', 'demo/educacion/col2-diploma.pdf',
  '2019-03-01', '2021-12-15', FALSE, 'RECHAZADO', 'El documento está ilegible; vuelve a subirlo.', @rm, @rm, NOW() - INTERVAL 20 DAY),
 (@col4, 'Universidad de Lima', 'Diseño Gráfico', 'demo/educacion/col4-titulo.pdf',
  '2019-03-01', '2024-12-15', FALSE, 'PENDIENTE', NULL, NULL, @rm, NULL);
INSERT INTO educacion (colaborador_id, institucion, titulo, archivo_url, fecha_inicio, fecha_fin, actual, estado, rm_responsable_id) VALUES
 (@col1, 'Universidad del Pacífico', 'Diplomado en Arquitectura de Software', 'demo/educacion/col1-diplomado.pdf',
  '2024-03-01', '2024-11-30', FALSE, 'PENDIENTE', @rm);
SET @edu_pend := LAST_INSERT_ID();

-- ============================================================================
-- 4. PROYECTOS (uno por estado)
-- ============================================================================
INSERT INTO proyecto (nombre, descripcion, fecha_inicio, fecha_fin_estimada, estado, prioridad, justificacion_prioridad,
    presupuesto_solicitado, justificacion_presupuesto, presupuesto, colaboradores_requeridos, horas_semanales_requeridas,
    pm_id, rm_revisor_id, fecha_creacion, motivo_rechazo, motivo_cierre) VALUES
 (CONCAT(@etq, 'Plataforma de Talento'), 'Portal interno para gestionar perfiles, habilidades y asignaciones del personal.',
  CURDATE() - INTERVAL 30 DAY, CURDATE() + INTERVAL 120 DAY, 'ACTIVO', 'ALTA', 'Lo pide la gerencia para el cierre del año.',
  65000.00, 'Tres perfiles a tiempo parcial durante cinco meses.', 60000.00, 3, 60, @pm, @rm, NOW() - INTERVAL 40 DAY, NULL, NULL),
 (CONCAT(@etq, 'App Móvil de Ventas'), 'Aplicación para que la fuerza de ventas registre pedidos desde el celular.',
  CURDATE() - INTERVAL 10 DAY, CURDATE() + INTERVAL 90 DAY, 'ACTIVO', 'MEDIA', 'Reduce el tiempo de registro de pedidos.',
  40000.00, 'Equipo pequeño durante tres meses.', 40000.00, 3, 30, @pm, @rm, NOW() - INTERVAL 15 DAY, NULL, NULL),
 (CONCAT(@etq, 'Portal de Proveedores'), 'Portal para que los proveedores suban facturas y consulten pagos.',
  CURDATE() + INTERVAL 12 DAY, CURDATE() + INTERVAL 100 DAY, 'EN_REVISION', 'ALTA', 'Requisito de auditoría financiera.',
  35000.00, 'Backend y base de datos durante tres meses.', NULL, 2, 40, @pm, @rm, NOW() - INTERVAL 1 DAY, NULL, NULL),
 (CONCAT(@etq, 'Sistema de Inventarios'), 'Control de stock por almacén con alertas de reposición.',
  CURDATE() + INTERVAL 20 DAY, CURDATE() + INTERVAL 80 DAY, 'EN_REVISION', 'BAJA', 'Mejora operativa sin fecha límite.',
  20000.00, 'Un desarrollador durante dos meses.', NULL, 1, 20, @pm, @rm, NOW() - INTERVAL 2 DAY, NULL, NULL),
 (CONCAT(@etq, 'Analítica Comercial'), 'Tableros de ventas por región en la nube.',
  CURDATE() + INTERVAL 15 DAY, CURDATE() + INTERVAL 75 DAY, 'EN_ESPERA', 'MEDIA', 'Esperando la licencia del proveedor de BI.',
  25000.00, 'Un perfil cloud durante dos meses.', 25000.00, 1, 20, @pm, @rm, NOW() - INTERVAL 20 DAY, NULL, NULL),
 (CONCAT(@etq, 'Migración de Correos'), 'Migración de buzones al nuevo servidor de correo.',
  CURDATE() - INTERVAL 200 DAY, CURDATE() - INTERVAL 20 DAY, 'FINALIZADO', 'BAJA', 'Fin del soporte del servidor anterior.',
  15000.00, 'Un perfil backend durante seis meses.', 15000.00, 1, 20, @pm, @rm, NOW() - INTERVAL 210 DAY, NULL,
  'Proyecto completado y entregado al área de TI.'),
 (CONCAT(@etq, 'Chatbot Interno'), 'Asistente para responder preguntas frecuentes de RR. HH.',
  CURDATE() + INTERVAL 30 DAY, CURDATE() + INTERVAL 120 DAY, 'RECHAZADO', 'BAJA', 'Idea del área de RR. HH.',
  80000.00, 'Licencias y tres perfiles.', NULL, 3, 60, @pm, @rm, NOW() - INTERVAL 30 DAY,
  'El presupuesto solicitado supera lo disponible este semestre.', NULL),
 (CONCAT(@etq, 'Intranet Legacy'), 'Mantenimiento de la intranet antigua.',
  CURDATE() - INTERVAL 90 DAY, CURDATE() + INTERVAL 10 DAY, 'CANCELADO', 'MEDIA', 'Soporte a usuarios internos.',
  12000.00, 'Un perfil frontend.', 12000.00, 1, 10, @pm, @rm, NOW() - INTERVAL 100 DAY, NULL,
  'Se reemplazará por la Plataforma de Talento.');

SET @p_talento := (SELECT id FROM proyecto WHERE nombre = CONCAT(@etq, 'Plataforma de Talento'));
SET @p_ventas  := (SELECT id FROM proyecto WHERE nombre = CONCAT(@etq, 'App Móvil de Ventas'));
SET @p_prov    := (SELECT id FROM proyecto WHERE nombre = CONCAT(@etq, 'Portal de Proveedores'));
SET @p_inv     := (SELECT id FROM proyecto WHERE nombre = CONCAT(@etq, 'Sistema de Inventarios'));
SET @p_anal    := (SELECT id FROM proyecto WHERE nombre = CONCAT(@etq, 'Analítica Comercial'));
SET @p_correo  := (SELECT id FROM proyecto WHERE nombre = CONCAT(@etq, 'Migración de Correos'));
SET @p_chatbot := (SELECT id FROM proyecto WHERE nombre = CONCAT(@etq, 'Chatbot Interno'));
SET @p_legacy  := (SELECT id FROM proyecto WHERE nombre = CONCAT(@etq, 'Intranet Legacy'));

INSERT INTO proyecto_habilidad_requerida (proyecto_id, habilidad_id, nivel_requerido, cantidad_personas, horas_semanales) VALUES
 (@p_talento, @h_java,   'AVANZADO',   1, 20),
 (@p_talento, @h_react,  'INTERMEDIO', 1, 20),
 (@p_talento, @h_aws,    'INTERMEDIO', 1, 20),
 (@p_ventas,  @h_react,  'INTERMEDIO', 1, 10),
 (@p_ventas,  @h_mysql,  'BASICO',     1, 10),
 (@p_ventas,  @h_docker, 'BASICO',     1, 10),
 (@p_prov,    @h_java,   'INTERMEDIO', 1, 20),
 (@p_prov,    @h_mysql,  'INTERMEDIO', 1, 20),
 (@p_inv,     @h_python, 'INTERMEDIO', 1, 20),
 (@p_anal,    @h_gcp,    'INTERMEDIO', 1, 20),
 (@p_correo,  @h_java,   'INTERMEDIO', 1, 20),
 (@p_chatbot, @h_python, 'AVANZADO',   3, 20),
 (@p_legacy,  @h_js,     'BASICO',     1, 10);

-- ============================================================================
-- 5. ASIGNACIONES (activas, pendientes del PM, pendientes del RM, historial)
-- ============================================================================
INSERT INTO asignacion (proyecto_id, colaborador_id, horas_semanales, habilidades_relevantes, origen, habilidad_solicitada_id,
    estado, aprobado_por_pm, aprobado_por_rm, fecha_aprobacion_pm, fecha_aprobacion_rm, fecha_solicitud, fecha_activacion) VALUES
 (@p_talento, @col1, 20, 'Java, Spring Boot', 'PROPUESTA_PM', @h_java,  'ACTIVA', TRUE, TRUE,
  NOW() - INTERVAL 32 DAY, NOW() - INTERVAL 31 DAY, NOW() - INTERVAL 32 DAY, NOW() - INTERVAL 31 DAY),
 (@p_talento, @col2, 20, 'React, JavaScript', 'PROPUESTA_PM', @h_react, 'ACTIVA', TRUE, TRUE,
  NOW() - INTERVAL 32 DAY, NOW() - INTERVAL 31 DAY, NOW() - INTERVAL 32 DAY, NOW() - INTERVAL 31 DAY);
SET @a_col1 := (SELECT id FROM asignacion WHERE proyecto_id = @p_talento AND colaborador_id = @col1);
SET @a_col2 := (SELECT id FROM asignacion WHERE proyecto_id = @p_talento AND colaborador_id = @col2);

-- Pendiente del RM: propuesta del PM (col3 a Plataforma de Talento)
INSERT INTO asignacion (proyecto_id, colaborador_id, horas_semanales, habilidades_relevantes, origen, habilidad_solicitada_id,
    estado, aprobado_por_pm, fecha_aprobacion_pm, fecha_solicitud) VALUES
 (@p_talento, @col3, 20, 'AWS, Docker', 'PROPUESTA_PM', @h_aws, 'PENDIENTE', TRUE, NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY);
SET @a_rm_pend := LAST_INSERT_ID();

-- Pendiente del RM: postulacion de col3 ya aceptada por el PM (App Movil de Ventas)
INSERT INTO asignacion (proyecto_id, colaborador_id, horas_semanales, habilidades_relevantes, origen, mensaje_solicitud,
    habilidad_solicitada_id, estado, aprobado_por_pm, fecha_aprobacion_pm, fecha_solicitud) VALUES
 (@p_ventas, @col3, 10, 'Docker', 'SOLICITADA_COLABORADOR', 'Puedo dockerizar el backend y armar el pipeline de despliegue.',
  @h_docker, 'PENDIENTE', TRUE, NOW() - INTERVAL 12 HOUR, NOW() - INTERVAL 2 DAY);

-- Pendiente del PM: postulacion de col2 (App Movil de Ventas)
INSERT INTO asignacion (proyecto_id, colaborador_id, horas_semanales, habilidades_relevantes, origen, mensaje_solicitud,
    habilidad_solicitada_id, estado, fecha_solicitud) VALUES
 (@p_ventas, @col2, 10, 'React', 'SOLICITADA_COLABORADOR', 'Tengo experiencia en apps de ventas con React; me gustaría apoyar.',
  @h_react, 'PENDIENTE', NOW() - INTERVAL 6 HOUR);
SET @a_pm_pend := LAST_INSERT_ID();

-- Historial: col1 en un proyecto finalizado y col2 rechazada por cancelacion
INSERT INTO asignacion (proyecto_id, colaborador_id, horas_semanales, habilidades_relevantes, origen, habilidad_solicitada_id,
    estado, aprobado_por_pm, aprobado_por_rm, fecha_aprobacion_pm, fecha_aprobacion_rm, desasignado_por, motivo_finalizacion,
    fecha_solicitud, fecha_activacion, fecha_finalizacion) VALUES
 (@p_correo, @col1, 20, 'Java', 'PROPUESTA_PM', @h_java, 'FINALIZADA', TRUE, TRUE,
  NOW() - INTERVAL 205 DAY, NOW() - INTERVAL 204 DAY, @pm, 'PROYECTO_FINALIZADO',
  NOW() - INTERVAL 205 DAY, NOW() - INTERVAL 204 DAY, NOW() - INTERVAL 20 DAY);
SET @a_hist := LAST_INSERT_ID();
INSERT INTO asignacion (proyecto_id, colaborador_id, horas_semanales, habilidades_relevantes, origen, habilidad_solicitada_id,
    estado, aprobado_por_pm, rechazado_por, motivo_rechazo, fecha_solicitud, fecha_finalizacion) VALUES
 (@p_legacy, @col2, 10, 'JavaScript', 'PROPUESTA_PM', @h_js, 'RECHAZADA', TRUE, @pm, 'Proyecto cancelado',
  NOW() - INTERVAL 95 DAY, NOW() - INTERVAL 60 DAY);

INSERT INTO evaluacion (colaborador_id, evaluador_id, asignacion_id, calificacion, comentarios, fecha_creacion) VALUES
 (@col1, @pm, @a_hist, 5, 'Excelente compromiso: entregó la migración antes de la fecha y documentó todo el proceso.', NOW() - INTERVAL 20 DAY);

-- ============================================================================
-- 6. ACTIVIDADES (todos los estados) y STRIKE
-- ============================================================================
INSERT INTO actividad (proyecto_id, colaborador_id, titulo, descripcion, horas_estimadas, fecha_asignacion, fecha_limite,
    fecha_marcado_revision, fecha_entrega, estado_entrega, veces_devuelta, comentario_devolucion, evidencia_url,
    comentario_colaborador, estado, creado_por) VALUES
 (@p_talento, @col1, 'Diseñar el modelo de datos', 'Modelo entidad-relación de perfiles y habilidades.', 12,
  NOW() - INTERVAL 28 DAY, CURDATE() - INTERVAL 18 DAY, NOW() - INTERVAL 21 DAY, NOW() - INTERVAL 20 DAY, 'A_TIEMPO', 0, NULL,
  'demo/evidencias/modelo-datos.pdf', 'Adjunto el diagrama y el script SQL.', 'COMPLETADA', @pm),
 (@p_talento, @col1, 'API de autenticación', 'Login con roles y recuperación de contraseña.', 10,
  NOW() - INTERVAL 10 DAY, CURDATE() + INTERVAL 3 DAY, NOW() - INTERVAL 1 DAY, NULL, NULL, 0, NULL,
  'demo/evidencias/api-auth.pdf', 'Endpoints listos y probados con Postman.', 'EN_REVISION', @pm),
 (@p_talento, @col1, 'Pruebas unitarias del módulo de usuarios', 'Cobertura mínima de 70 %.', 8,
  NOW() - INTERVAL 8 DAY, CURDATE() + INTERVAL 7 DAY, NULL, NULL, NULL, 1, 'Faltan los casos de error del registro.',
  NULL, NULL, 'EN_PROGRESO', @pm),
 (@p_talento, @col1, 'Endpoint de reportes', 'Reporte de horas por colaborador en JSON.', 6,
  NOW() - INTERVAL 2 DAY, CURDATE() + INTERVAL 10 DAY, NULL, NULL, NULL, 0, NULL, NULL, NULL, 'PENDIENTE', @pm),
 (@p_talento, @col2, 'Maquetar el dashboard', 'Vista principal con tarjetas e indicadores.', 14,
  NOW() - INTERVAL 25 DAY, CURDATE() - INTERVAL 8 DAY, NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 5 DAY, 'TARDIA', 0, NULL,
  'demo/evidencias/dashboard.png', 'Entregado con retraso por cambios de diseño.', 'COMPLETADA', @pm),
 (@p_talento, @col2, 'Formulario de registro de habilidades', 'Formulario con validaciones en el cliente.', 8,
  NOW() - INTERVAL 3 DAY, CURDATE() + INTERVAL 5 DAY, NULL, NULL, NULL, 0, NULL, NULL, NULL, 'PENDIENTE', @pm),
 (@p_talento, @col2, 'Guía de estilos', 'Paleta, tipografía y componentes base.', 6,
  NOW() - INTERVAL 31 DAY, CURDATE() - INTERVAL 27 DAY, NOW() - INTERVAL 28 DAY, NOW() - INTERVAL 28 DAY, 'A_TIEMPO', 0, NULL,
  'demo/evidencias/guia-estilos.pdf', 'Guía publicada en el repositorio.', 'COMPLETADA', @pm),
 (@p_correo, @col1, 'Inventario de buzones', 'Listado de buzones y tamaños.', 16,
  NOW() - INTERVAL 190 DAY, CURDATE() - INTERVAL 180 DAY, NOW() - INTERVAL 182 DAY, NOW() - INTERVAL 182 DAY, 'A_TIEMPO', 0, NULL,
  'demo/evidencias/inventario.xlsx', 'Inventario completo.', 'COMPLETADA', @pm),
 (@p_correo, @col1, 'Migración final y cierre', 'Migrar los buzones restantes y apagar el servidor.', 24,
  NOW() - INTERVAL 40 DAY, CURDATE() - INTERVAL 22 DAY, NOW() - INTERVAL 23 DAY, NOW() - INTERVAL 23 DAY, 'A_TIEMPO', 0, NULL,
  'demo/evidencias/cierre.pdf', 'Migración terminada sin pérdida de correos.', 'COMPLETADA', @pm);

SET @act_tardia := (SELECT id FROM actividad WHERE proyecto_id = @p_talento AND titulo = 'Maquetar el dashboard');
INSERT INTO penalizacion (colaborador_id, proyecto_id, actividad_id, tipo, motivo, monto, fecha) VALUES
 (@col2, @p_talento, @act_tardia, 'ACTIVIDAD_TARDIA', 'Actividad entregada después de la fecha límite.', 1, NOW() - INTERVAL 5 DAY);

-- ============================================================================
-- 7. SOLICITUDES DE PERSONAL (PM -> RM) y DOCUMENTOS DEL PROYECTO
-- ============================================================================
INSERT INTO solicitud_personal (proyecto_id, cantidad_colaboradores, perfiles_requeridos, mensaje_pm, estado,
    rm_responsable_id, fecha_solicitud, fecha_inicio_atencion) VALUES
 (@p_talento, 1, 'AWS (INTERMEDIO): 1', 'Necesitamos un perfil cloud para el despliegue antes de fin de mes.',
  'PENDIENTE', @rm, NOW() - INTERVAL 1 DAY, NULL),
 (@p_ventas, 2, 'MySQL (BASICO): 1; Docker (BASICO): 1', 'Buscamos apoyo en base de datos y contenedores.',
  'EN_ATENCION', @rm, NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 2 DAY);

INSERT INTO documento (proyecto_id, subido_por_id, nombre, categoria, archivo_url, fecha_creacion) VALUES
 (@p_talento, @pm,   'Acta de constitución.pdf',      'PDF',    'demo/documentos/acta.pdf',          NOW() - INTERVAL 30 DAY),
 (@p_talento, @col1, 'Diagrama de arquitectura.png',  'IMAGEN', 'demo/documentos/arquitectura.png',  NOW() - INTERVAL 12 DAY),
 (@p_talento, @col2, 'Cronograma del sprint.xlsx',    'EXCEL',  'demo/documentos/cronograma.xlsx',   NOW() - INTERVAL 6 DAY);

-- ============================================================================
-- 8. CURSOS (catalogo del Admin) e INSCRIPCIONES en todos los estados
-- ============================================================================
INSERT INTO curso (nombre, descripcion, categoria, modalidad, autodidacta, dias, fecha_inicio, fecha_fin, horas, creado_por) VALUES
 (CONCAT(@etq, 'Spring Boot Avanzado'), 'Microservicios, seguridad y pruebas con Spring Boot.', 'Desarrollo', 'VIRTUAL', FALSE,
  'Martes y jueves', CURDATE() + INTERVAL 7 DAY, CURDATE() + INTERVAL 60 DAY, 24, @admin),
 (CONCAT(@etq, 'AWS Cloud Practitioner'), 'Preparación para la certificación de AWS.', 'Cloud', 'VIRTUAL', TRUE,
  NULL, NULL, NULL, 20, @admin),
 (CONCAT(@etq, 'Scrum Fundamentals'), 'Roles, eventos y artefactos de Scrum.', 'Gestión', 'PRESENCIAL', FALSE,
  'Sábados', CURDATE() + INTERVAL 10 DAY, CURDATE() + INTERVAL 40 DAY, 12, @admin),
 (CONCAT(@etq, 'Docker desde cero'), 'Imágenes, contenedores y docker compose.', 'DevOps', 'HIBRIDO', FALSE,
  'Lunes y miércoles', CURDATE() - INTERVAL 10 DAY, CURDATE() + INTERVAL 30 DAY, 16, @admin),
 (CONCAT(@etq, 'React con Hooks'), 'Componentes funcionales, hooks y manejo de estado.', 'Frontend', 'VIRTUAL', TRUE,
  NULL, NULL, NULL, 10, @admin);

SET @cu_spring := (SELECT id FROM curso WHERE nombre = CONCAT(@etq, 'Spring Boot Avanzado'));
SET @cu_aws    := (SELECT id FROM curso WHERE nombre = CONCAT(@etq, 'AWS Cloud Practitioner'));
SET @cu_scrum  := (SELECT id FROM curso WHERE nombre = CONCAT(@etq, 'Scrum Fundamentals'));
SET @cu_docker := (SELECT id FROM curso WHERE nombre = CONCAT(@etq, 'Docker desde cero'));
SET @cu_react  := (SELECT id FROM curso WHERE nombre = CONCAT(@etq, 'React con Hooks'));

INSERT INTO colaborador_curso (colaborador_id, curso_id, origen, estado, asignado_por, rm_responsable_id, motivo_respuesta,
    justificacion_colaborador, fecha_solicitud, fecha_respuesta, fecha_completado, evidencia_url, fecha_evidencia,
    evidencia_revisada_por, fecha_revision_evidencia) VALUES
 -- SOLICITADO: el RM lo aprueba o rechaza
 (@col1, @cu_spring, 'SOLICITUD_COLABORADOR', 'SOLICITADO', NULL, @rm, NULL,
  'Lo necesito para el módulo de seguridad de la Plataforma de Talento.', NOW() - INTERVAL 1 DAY, NULL, NULL, NULL, NULL, NULL, NULL),
 -- EN_CURSO: el colaborador sube la evidencia
 (@col1, @cu_docker, 'SOLICITUD_COLABORADOR', 'EN_CURSO', NULL, @rm, 'Aprobado: útil para el despliegue.',
  'Quiero aprender a contenerizar los servicios.', NOW() - INTERVAL 15 DAY, NOW() - INTERVAL 14 DAY, NULL, NULL, NULL, NULL, NULL),
 -- COMPLETADO: suma horas al mes
 (@col1, @cu_aws, 'ASIGNADO_POR_RM', 'COMPLETADO', @rm, @rm, NULL,
  NULL, NOW() - INTERVAL 25 DAY, NOW() - INTERVAL 25 DAY, NOW() - INTERVAL 4 DAY, 'demo/cursos/col1-aws.pdf', NOW() - INTERVAL 5 DAY, @rm, NOW() - INTERVAL 4 DAY),
 -- RECHAZADO
 (@col2, @cu_scrum, 'SOLICITUD_COLABORADOR', 'RECHAZADO', NULL, @rm, 'Este mes prioriza las entregas del proyecto.',
  'Me interesa la gestión ágil.', NOW() - INTERVAL 7 DAY, NOW() - INTERVAL 6 DAY, NULL, NULL, NULL, NULL, NULL),
 -- EN_CURSO asignado por el RM
 (@col3, @cu_scrum, 'ASIGNADO_POR_RM', 'EN_CURSO', @rm, @rm, NULL,
  NULL, NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY, NULL, NULL, NULL, NULL, NULL);
INSERT INTO colaborador_curso (colaborador_id, curso_id, origen, estado, rm_responsable_id, motivo_respuesta,
    justificacion_colaborador, fecha_solicitud, fecha_respuesta, evidencia_url, fecha_evidencia) VALUES
 -- EVIDENCIA_PENDIENTE: el RM aprueba o rechaza la evidencia
 (@col2, @cu_react, 'SOLICITUD_COLABORADOR', 'EVIDENCIA_PENDIENTE', @rm, 'Aprobado.',
  'Para mejorar el frontend de la Plataforma de Talento.', NOW() - INTERVAL 12 DAY, NOW() - INTERVAL 11 DAY,
  'demo/cursos/col2-react.pdf', NOW() - INTERVAL 1 DAY);
SET @cc_evid := LAST_INSERT_ID();

-- ============================================================================
-- 9. FOROS: comunidad, anuncios y foro del proyecto
-- ============================================================================
INSERT INTO foro (proyecto_id, tipo, es_publico, nombre) VALUES
 (NULL, 'GENERAL', TRUE,  CONCAT(@etq, 'Comunidad SkillBridge')),
 (NULL, 'GENERAL', FALSE, CONCAT(@etq, 'Anuncios')),
 (@p_talento, 'PROYECTO', FALSE, CONCAT(@etq, 'Foro - Plataforma de Talento'));
SET @f_com  := (SELECT id FROM foro WHERE nombre = CONCAT(@etq, 'Comunidad SkillBridge'));
SET @f_anun := (SELECT id FROM foro WHERE nombre = CONCAT(@etq, 'Anuncios'));
SET @f_proy := (SELECT id FROM foro WHERE nombre = CONCAT(@etq, 'Foro - Plataforma de Talento'));

INSERT INTO publicacion_foro (foro_id, autor_id, etiqueta_id, titulo, contenido, fecha_creacion) VALUES
 (@f_com, @col1, @e_bp, '¿Cómo nombran sus ramas en Git?',
  '<p>Estoy ordenando el repositorio. ¿Usan <strong>feature/</strong>, <strong>fix/</strong> o algún otro formato?</p>', NOW() - INTERVAL 3 DAY);
SET @pub_git := LAST_INSERT_ID();
INSERT INTO publicacion_foro (foro_id, autor_id, etiqueta_id, titulo, contenido, fecha_creacion) VALUES
 (@f_com, @rm, @e_aviso, 'Nuevos cursos de noviembre',
  '<p>Ya están disponibles los cursos de Spring Boot, Docker y React. Solicítenlos desde <em>Explorar</em>.</p>', NOW() - INTERVAL 2 DAY);
SET @pub_cursos := LAST_INSERT_ID();
INSERT INTO publicacion_foro (foro_id, autor_id, etiqueta_id, titulo, contenido, fecha_creacion) VALUES
 (@f_anun, @admin, @e_aviso, 'Bienvenidos a SkillBridge AI',
  '<p>Mantengan su perfil y sus certificados al día: el RM los usa para asignarlos a proyectos.</p>', NOW() - INTERVAL 5 DAY),
 (@f_proy, @pm, @e_consulta, 'Kick-off del sprint 3',
  '<p>Objetivo del sprint: autenticación y dashboard. Dejen aquí sus dudas.</p>', NOW() - INTERVAL 4 DAY);
SET @pub_sprint := LAST_INSERT_ID();

INSERT INTO respuesta_foro (publicacion_id, autor_id, contenido, es_solucion, fecha_creacion) VALUES
 (@pub_git, @col2, '<p>Nosotros usamos <code>feature/nombre-corto</code> y borramos la rama al hacer merge.</p>', FALSE, NOW() - INTERVAL 2 DAY);
SET @res_git1 := LAST_INSERT_ID();
INSERT INTO respuesta_foro (publicacion_id, autor_id, contenido, es_solucion, fecha_creacion) VALUES
 (@pub_git, @rm, '<p>La convención del área: <code>feature/</code>, <code>fix/</code> y <code>hotfix/</code>, siempre desde <code>main</code>.</p>', TRUE, NOW() - INTERVAL 2 DAY);
SET @res_git2 := LAST_INSERT_ID();
INSERT INTO respuesta_foro (publicacion_id, autor_id, contenido, es_solucion, fecha_creacion) VALUES
 (@pub_sprint, @col1, '<p>¿La recuperación de contraseña entra en este sprint?</p>', FALSE, NOW() - INTERVAL 3 DAY),
 (@pub_sprint, @col2, '<p>Yo tomo el dashboard; lo subo el jueves.</p>', FALSE, NOW() - INTERVAL 3 DAY);

INSERT INTO voto_publicacion (usuario_id, publicacion_id, tipo) VALUES
 (@col2, @pub_git, 'POSITIVO'), (@rm, @pub_git, 'POSITIVO'), (@col1, @pub_cursos, 'POSITIVO'), (@col3, @pub_cursos, 'POSITIVO');
INSERT INTO voto_respuesta (usuario_id, respuesta_id, tipo) VALUES
 (@col1, @res_git2, 'POSITIVO'), (@col3, @res_git2, 'POSITIVO'), (@col1, @res_git1, 'POSITIVO');

-- ============================================================================
-- 10. CHAT: grupal del proyecto, privado PM-colaborador y privado PM-RM
-- ============================================================================
INSERT INTO conversacion (tipo, proyecto_id, fecha_creacion) VALUES ('GRUPAL', @p_talento, NOW() - INTERVAL 30 DAY);
SET @conv_grupal := LAST_INSERT_ID();
INSERT INTO mensaje (conversacion_id, autor_id, contenido, fecha_hora) VALUES
 (@conv_grupal, @pm,   'Buenos días, equipo. Hoy cerramos el modelo de datos.', NOW() - INTERVAL 3 DAY),
 (@conv_grupal, @col1, 'Listo, ya subí el diagrama a Documentos.', NOW() - INTERVAL 3 DAY + INTERVAL 20 MINUTE),
 (@conv_grupal, @col2, 'Yo avanzo con el dashboard esta semana.', NOW() - INTERVAL 2 DAY);

INSERT INTO conversacion (tipo, proyecto_id, fecha_creacion) VALUES ('PRIVADA', @p_talento, NOW() - INTERVAL 5 DAY);
SET @conv_pm_col := LAST_INSERT_ID();
INSERT INTO conversacion_usuario (conversacion_id, usuario_id) VALUES (@conv_pm_col, @pm), (@conv_pm_col, @col1);
INSERT INTO mensaje (conversacion_id, autor_id, contenido, fecha_hora) VALUES
 (@conv_pm_col, @pm,   '¿Cómo vas con la API de autenticación?', NOW() - INTERVAL 2 DAY),
 (@conv_pm_col, @col1, 'Ya la marqué como lista; queda en revisión.', NOW() - INTERVAL 1 DAY);

INSERT INTO conversacion (tipo, proyecto_id, fecha_creacion) VALUES ('PRIVADA', @p_talento, NOW() - INTERVAL 4 DAY);
SET @conv_pm_rm := LAST_INSERT_ID();
INSERT INTO conversacion_usuario (conversacion_id, usuario_id) VALUES (@conv_pm_rm, @pm), (@conv_pm_rm, @rm);
INSERT INTO mensaje (conversacion_id, autor_id, contenido, fecha_hora) VALUES
 (@conv_pm_rm, @pm, 'Te propuse un perfil cloud para la Plataforma de Talento; ¿lo puedes revisar?', NOW() - INTERVAL 1 DAY),
 (@conv_pm_rm, @rm, 'Sí, lo reviso hoy junto con el presupuesto.', NOW() - INTERVAL 20 HOUR);

-- ============================================================================
-- 11. NOTIFICACIONES SIN LEER (una muestra por rol)
-- ============================================================================
INSERT INTO notificacion (usuario_id, tipo, categoria, titulo, descripcion, entidad, entidad_id, leida, fecha_creacion) VALUES
 (@admin, 'CV_PENDIENTE', 'SISTEMA', 'Pre-registro por revisar',
  'Un colaborador nuevo completó su pre-registro y espera tu revisión.', 'USUARIO', @col4, FALSE, NOW() - INTERVAL 1 DAY),
 (@rm, 'PROYECTO_ASIGNADO_RM', 'PROYECTO', 'Nuevo proyecto asignado',
  CONCAT('Se te asignó el proyecto "', @etq, 'Portal de Proveedores" para su revisión.'), 'PROYECTO', @p_prov, FALSE, NOW() - INTERVAL 1 DAY),
 (@rm, 'ASIGNACION_PROPUESTA', 'ASIGNACION', 'Asignación por aprobar',
  'El PM propuso un colaborador para la Plataforma de Talento.', 'ASIGNACION', @a_rm_pend, FALSE, NOW() - INTERVAL 1 DAY),
 (@rm, 'CERTIFICADO_PENDIENTE', 'HABILIDAD', 'Certificado por revisar',
  'Un colaborador subió un certificado de Docker.', 'CERTIFICADO', @cert_pend, FALSE, NOW() - INTERVAL 2 DAY),
 (@rm, 'EDUCACION_PENDIENTE', 'HABILIDAD', 'Formación por revisar',
  'Un colaborador registró un diplomado.', 'EDUCACION', @edu_pend, FALSE, NOW() - INTERVAL 2 DAY),
 (@rm, 'EVIDENCIA_CURSO_PENDIENTE', 'CURSO', 'Evidencia de curso por revisar',
  'Un colaborador subió la evidencia de React con Hooks.', 'COLABORADOR_CURSO', @cc_evid, FALSE, NOW() - INTERVAL 1 DAY),
 (@pm, 'POSTULACION', 'ASIGNACION', 'Nueva postulación',
  'Un colaborador postuló a la App Móvil de Ventas.', 'ASIGNACION', @a_pm_pend, FALSE, NOW() - INTERVAL 6 HOUR),
 (@pm, 'ACTIVIDAD_LISTA', 'ACTIVIDAD', 'Actividad lista para revisar',
  'API de autenticación quedó en revisión.', 'PROYECTO', @p_talento, FALSE, NOW() - INTERVAL 1 DAY),
 (@col1, 'NUEVA_ACTIVIDAD', 'ACTIVIDAD', 'Nueva actividad asignada',
  'Endpoint de reportes en la Plataforma de Talento.', 'ASIGNACION', @a_col1, FALSE, NOW() - INTERVAL 2 DAY),
 (@col1, 'FORO_RESPUESTA', 'SISTEMA', 'Respondieron tu publicación',
  'Hay respuestas nuevas en "¿Cómo nombran sus ramas en Git?".', 'FORO', @f_com, FALSE, NOW() - INTERVAL 2 DAY),
 (@col2, 'EDUCACION_RECHAZADA', 'HABILIDAD', 'Formación rechazada',
  'El documento está ilegible; vuelve a subirlo.', 'EDUCACION', NULL, FALSE, NOW() - INTERVAL 20 DAY),
 (@col3, 'CURSO_ASIGNADO', 'CURSO', 'Curso asignado',
  'El RM te asignó Scrum Fundamentals.', 'COLABORADOR_CURSO', NULL, FALSE, NOW() - INTERVAL 3 DAY);

-- ============================================================================
-- 12. SESIONES ABIERTAS DEL GRUPO (Spring Session; la tabla la crea la app al arrancar)
--     Si la base nunca arrancó con la app, este paso falla y se puede ignorar.
-- ============================================================================
DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME LIKE @pat;

-- Resumen
SELECT u.correo, r.nombre AS rol, u.activo, u.registro_estado
FROM usuario u JOIN rol r ON r.id = u.rol_id
WHERE u.correo LIKE @pat ORDER BY u.id;
