USE skillbridge_db;

-- =====================================================================
-- COLABORADORES ADICIONALES (contraseña para todos: abc123, igual que Carlos)
-- =====================================================================

INSERT IGNORE INTO usuario (correo, password_hash, nombre, apellido, rol_id, activo, cargo, descripcion, horas_disponibles, anios_experiencia, nivel_experiencia, fecha_contratacion) VALUES
('maria.lopez@skillbridge.com', '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom', 'María', 'López', (SELECT id FROM rol WHERE nombre='COLABORADOR' LIMIT 1), 1, 'UX/UI Designer', 'Diseñadora UX/UI enfocada en investigación de usuarios y prototipado rápido.', 15.00, 3.0, 'SEMI_SENIOR', '2023-03-01'),
('mallory.hulme@skillbridge.com', '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom', 'Mallory', 'Hulme', (SELECT id FROM rol WHERE nombre='COLABORADOR' LIMIT 1), 1, 'Frontend Developer', 'Desarrolladora frontend enfocada en interfaces accesibles y de alto rendimiento.', 40.00, 2.0, 'JUNIOR', '2024-06-15'),
('dunn.slane@skillbridge.com', '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom', 'Dunn', 'Slane', (SELECT id FROM rol WHERE nombre='COLABORADOR' LIMIT 1), 1, 'UI/UX Designer', 'Diseñador UI/UX enfocado en sistemas de diseño y research.', 0.00, 4.0, 'SENIOR', '2022-01-10');

-- Completamos el perfil de Carlos (col@), que en tu seed original solo tenía cargo y horas
UPDATE usuario
SET descripcion = 'Backend developer especializado en Spring Boot y bases de datos relacionales.',
    anios_experiencia = 5.0,
    nivel_experiencia = 'SENIOR',
    fecha_contratacion = '2021-05-01'
WHERE correo = 'col@skillbridge.com';


-- =====================================================================
-- CATÁLOGO DE HABILIDADES 
-- =====================================================================

INSERT IGNORE INTO categoria_habilidad (nombre, descripcion, activa) VALUES
('Backend', 'Lenguajes y frameworks del lado del servidor', 1),
('Frontend', 'Lenguajes y frameworks del lado del cliente', 1),
('Base de Datos', 'Motores y herramientas de bases de datos', 1),
('DevOps & Cloud', 'Infraestructura, contenedores y nube', 1),
('Diseño', 'Diseño de producto e interfaces', 1),
('Habilidades Blandas', 'Comunicación, liderazgo y trabajo en equipo', 1);

INSERT IGNORE INTO habilidad (nombre, categoria_id, activa) VALUES
('Java', (SELECT id FROM categoria_habilidad WHERE nombre='Backend' LIMIT 1), 1),
('Spring Boot', (SELECT id FROM categoria_habilidad WHERE nombre='Backend' LIMIT 1), 1),
('Node.js', (SELECT id FROM categoria_habilidad WHERE nombre='Backend' LIMIT 1), 1),
('React', (SELECT id FROM categoria_habilidad WHERE nombre='Frontend' LIMIT 1), 1),
('JavaScript', (SELECT id FROM categoria_habilidad WHERE nombre='Frontend' LIMIT 1), 1),
('CSS', (SELECT id FROM categoria_habilidad WHERE nombre='Frontend' LIMIT 1), 1),
('MySQL', (SELECT id FROM categoria_habilidad WHERE nombre='Base de Datos' LIMIT 1), 1),
('PostgreSQL', (SELECT id FROM categoria_habilidad WHERE nombre='Base de Datos' LIMIT 1), 1),
('Docker', (SELECT id FROM categoria_habilidad WHERE nombre='DevOps & Cloud' LIMIT 1), 1),
('AWS', (SELECT id FROM categoria_habilidad WHERE nombre='DevOps & Cloud' LIMIT 1), 1),
('Figma', (SELECT id FROM categoria_habilidad WHERE nombre='Diseño' LIMIT 1), 1),
('UX Research', (SELECT id FROM categoria_habilidad WHERE nombre='Diseño' LIMIT 1), 1),
('Comunicación Efectiva', (SELECT id FROM categoria_habilidad WHERE nombre='Habilidades Blandas' LIMIT 1), 1);


-- =====================================================================
-- HABILIDADES DE CADA COLABORADOR (mezcla de estados a propósito:
-- VALIDADA para que aparezcan en Explorar, PENDIENTE y RECHAZADA para
-- probar los badges y el botón "Volver a subir certificado")
-- =====================================================================

INSERT IGNORE INTO colaborador_habilidad (colaborador_id, habilidad_id, nivel_dominio, estado_validacion, activo) VALUES
((SELECT id FROM usuario WHERE correo='col@skillbridge.com' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='Java' LIMIT 1), 'AVANZADO', 'VALIDADA', 1),
((SELECT id FROM usuario WHERE correo='col@skillbridge.com' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='Spring Boot' LIMIT 1), 'AVANZADO', 'VALIDADA', 1),
((SELECT id FROM usuario WHERE correo='col@skillbridge.com' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='MySQL' LIMIT 1), 'INTERMEDIO', 'PENDIENTE', 1),
((SELECT id FROM usuario WHERE correo='col@skillbridge.com' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='Docker' LIMIT 1), 'BASICO', 'RECHAZADA', 1),

((SELECT id FROM usuario WHERE correo='maria.lopez@skillbridge.com' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='Figma' LIMIT 1), 'AVANZADO', 'VALIDADA', 1),
((SELECT id FROM usuario WHERE correo='maria.lopez@skillbridge.com' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='UX Research' LIMIT 1), 'INTERMEDIO', 'VALIDADA', 1),

((SELECT id FROM usuario WHERE correo='mallory.hulme@skillbridge.com' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='React' LIMIT 1), 'AVANZADO', 'VALIDADA', 1),
((SELECT id FROM usuario WHERE correo='mallory.hulme@skillbridge.com' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='JavaScript' LIMIT 1), 'AVANZADO', 'VALIDADA', 1),
((SELECT id FROM usuario WHERE correo='mallory.hulme@skillbridge.com' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='CSS' LIMIT 1), 'INTERMEDIO', 'PENDIENTE', 1),

((SELECT id FROM usuario WHERE correo='dunn.slane@skillbridge.com' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='Figma' LIMIT 1), 'AVANZADO', 'VALIDADA', 1),
((SELECT id FROM usuario WHERE correo='dunn.slane@skillbridge.com' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='Comunicación Efectiva' LIMIT 1), 'INTERMEDIO', 'VALIDADA', 1);


-- =====================================================================
-- CERTIFICADOS (uno por cada fila de arriba, con el estado que le corresponde)
-- =====================================================================

INSERT IGNORE INTO certificado (colaborador_id, habilidad_id, archivo_url, estado, motivo_rechazo, revisado_por, fecha_revision) VALUES
((SELECT id FROM usuario WHERE correo='col@skillbridge.com' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='Java' LIMIT 1), '/uploads/certificados/demo-java.pdf', 'APROBADO', NULL, (SELECT id FROM usuario WHERE correo='rm@skillbridge.com' LIMIT 1), NOW()),
((SELECT id FROM usuario WHERE correo='col@skillbridge.com' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='Spring Boot' LIMIT 1), '/uploads/certificados/demo-spring.pdf', 'APROBADO', NULL, (SELECT id FROM usuario WHERE correo='rm@skillbridge.com' LIMIT 1), NOW()),
((SELECT id FROM usuario WHERE correo='col@skillbridge.com' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='MySQL' LIMIT 1), '/uploads/certificados/demo-mysql.pdf', 'PENDIENTE', NULL, NULL, NULL),
((SELECT id FROM usuario WHERE correo='col@skillbridge.com' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='Docker' LIMIT 1), '/uploads/certificados/demo-docker.pdf', 'RECHAZADO', 'El certificado no acredita el nivel declarado.', (SELECT id FROM usuario WHERE correo='rm@skillbridge.com' LIMIT 1), NOW()),

((SELECT id FROM usuario WHERE correo='maria.lopez@skillbridge.com' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='Figma' LIMIT 1), '/uploads/certificados/demo-figma.pdf', 'APROBADO', NULL, (SELECT id FROM usuario WHERE correo='rm@skillbridge.com' LIMIT 1), NOW()),
((SELECT id FROM usuario WHERE correo='maria.lopez@skillbridge.com' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='UX Research' LIMIT 1), '/uploads/certificados/demo-uxresearch.pdf', 'APROBADO', NULL, (SELECT id FROM usuario WHERE correo='rm@skillbridge.com' LIMIT 1), NOW()),

((SELECT id FROM usuario WHERE correo='mallory.hulme@skillbridge.com' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='React' LIMIT 1), '/uploads/certificados/demo-react.pdf', 'APROBADO', NULL, (SELECT id FROM usuario WHERE correo='rm@skillbridge.com' LIMIT 1), NOW()),
((SELECT id FROM usuario WHERE correo='mallory.hulme@skillbridge.com' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='JavaScript' LIMIT 1), '/uploads/certificados/demo-js.pdf', 'APROBADO', NULL, (SELECT id FROM usuario WHERE correo='rm@skillbridge.com' LIMIT 1), NOW()),
((SELECT id FROM usuario WHERE correo='mallory.hulme@skillbridge.com' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='CSS' LIMIT 1), '/uploads/certificados/demo-css.pdf', 'PENDIENTE', NULL, NULL, NULL),

((SELECT id FROM usuario WHERE correo='dunn.slane@skillbridge.com' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='Figma' LIMIT 1), '/uploads/certificados/demo-figma2.pdf', 'APROBADO', NULL, (SELECT id FROM usuario WHERE correo='rm@skillbridge.com' LIMIT 1), NOW()),
((SELECT id FROM usuario WHERE correo='dunn.slane@skillbridge.com' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='Comunicación Efectiva' LIMIT 1), '/uploads/certificados/demo-comm.pdf', 'APROBADO', NULL, (SELECT id FROM usuario WHERE correo='rm@skillbridge.com' LIMIT 1), NOW());


-- =====================================================================
-- EDUCACIÓN (ya con archivo_url, como lo dejamos)
-- =====================================================================

INSERT IGNORE INTO educacion (colaborador_id, institucion, titulo, archivo_url, fecha_inicio, fecha_fin, actual, estado) VALUES
((SELECT id FROM usuario WHERE correo='col@skillbridge.com' LIMIT 1), 'Universidad Nacional de Ingeniería', 'Ingeniería de Sistemas', '/uploads/certificados-educacion/demo-carlos.pdf', '2016-03-01', '2021-12-15', 0, 'PENDIENTE'),
((SELECT id FROM usuario WHERE correo='maria.lopez@skillbridge.com' LIMIT 1), 'Universidad Z', 'Diseño Gráfico', '/uploads/certificados-educacion/demo-maria.pdf', '2017-03-01', '2022-12-15', 0, 'PENDIENTE'),
((SELECT id FROM usuario WHERE correo='mallory.hulme@skillbridge.com' LIMIT 1), 'Universidad Y', 'Ingeniería de Sistemas', '/uploads/certificados-educacion/demo-mallory.pdf', '2020-03-01', NULL, 1, 'PENDIENTE'),
((SELECT id FROM usuario WHERE correo='dunn.slane@skillbridge.com' LIMIT 1), 'Universidad Z', 'Diseño Gráfico', '/uploads/certificados-educacion/demo-dunn.pdf', '2015-03-01', '2019-12-15', 0, 'PENDIENTE');


-- =====================================================================
-- EXPERIENCIA PROFESIONAL
-- =====================================================================

INSERT IGNORE INTO experiencia_profesional (colaborador_id, empresa, cargo, descripcion, fecha_inicio, fecha_fin, actual) VALUES
((SELECT id FROM usuario WHERE correo='col@skillbridge.com' LIMIT 1), 'Tech Solutions SAC', 'Backend Developer', 'Desarrollo e integración de APIs REST con Spring Boot y MySQL.', '2019-01-01', '2021-04-30', 0),
((SELECT id FROM usuario WHERE correo='maria.lopez@skillbridge.com' LIMIT 1), 'Estudio Creativo', 'UX/UI Designer', 'Diseño de flujos y research con usuarios finales.', '2021-01-01', NULL, 1),
((SELECT id FROM usuario WHERE correo='mallory.hulme@skillbridge.com' LIMIT 1), 'Web Studio', 'Frontend Developer', 'Construcción de interfaces con React y TypeScript.', '2022-06-01', NULL, 1),
((SELECT id FROM usuario WHERE correo='dunn.slane@skillbridge.com' LIMIT 1), 'Design Co.', 'UI/UX Designer', 'Diseño de sistemas de componentes y research de usuarios.', '2018-01-01', '2021-12-31', 0);


-- =====================================================================
-- PROYECTOS (uno por cada estado, para probar los filtros de Explorar)
-- =====================================================================

INSERT IGNORE INTO proyecto (nombre, descripcion, fecha_inicio, fecha_fin_estimada, estado, prioridad, justificacion_prioridad, presupuesto, colaboradores_requeridos, horas_semanales_requeridas, pm_id, rm_revisor_id) VALUES
('Portal de Clientes', 'Plataforma web para que los clientes gestionen sus pedidos y facturación en línea.', '2026-08-01', '2026-12-15', 'ACTIVO', 'ALTA', 'Cliente estratégico con contrato multianual.', 45000.00, 3, 20, (SELECT id FROM usuario WHERE correo='pm@skillbridge.com' LIMIT 1), (SELECT id FROM usuario WHERE correo='rm@skillbridge.com' LIMIT 1)),
('App de Delivery Interno', 'Aplicación móvil para coordinar entregas entre almacenes de la empresa.', '2026-09-01', '2027-02-28', 'ACTIVO', 'MEDIA', 'Mejora la eficiencia logística interna.', 30000.00, 2, 20, (SELECT id FROM usuario WHERE correo='pm@skillbridge.com' LIMIT 1), (SELECT id FROM usuario WHERE correo='rm@skillbridge.com' LIMIT 1)),
('Rediseño de Marca', 'Actualización de la identidad visual y sistema de diseño de la organización.', NULL, NULL, 'EN_REVISION', 'BAJA', 'Iniciativa de marketing sin fecha comprometida todavía.', NULL, 1, 15, (SELECT id FROM usuario WHERE correo='pm@skillbridge.com' LIMIT 1), NULL),
('Migración a la Nube', 'Migración de la infraestructura on-premise a AWS.', '2026-05-01', '2026-08-30', 'EN_ESPERA', 'MEDIA', 'Pausado por priorización de presupuesto.', 60000.00, 2, 20, (SELECT id FROM usuario WHERE correo='pm@skillbridge.com' LIMIT 1), (SELECT id FROM usuario WHERE correo='rm@skillbridge.com' LIMIT 1)),
('Sitio Web Corporativo v1', 'Primera versión del sitio web institucional.', '2025-02-01', '2025-06-30', 'FINALIZADO', 'MEDIA', 'Proyecto ya entregado y en producción.', 20000.00, 2, 20, (SELECT id FROM usuario WHERE correo='pm@skillbridge.com' LIMIT 1), (SELECT id FROM usuario WHERE correo='rm@skillbridge.com' LIMIT 1)),
('Sistema de Inventario Legacy', 'Reemplazo del sistema de inventario antiguo.', '2025-09-01', '2025-11-01', 'CANCELADO', 'BAJA', 'Se canceló por cambio de prioridades del área.', NULL, 2, 20, (SELECT id FROM usuario WHERE correo='pm@skillbridge.com' LIMIT 1), (SELECT id FROM usuario WHERE correo='rm@skillbridge.com' LIMIT 1));


-- =====================================================================
-- HABILIDADES REQUERIDAS POR PROYECTO
-- =====================================================================

INSERT IGNORE INTO proyecto_habilidad_requerida (proyecto_id, habilidad_id, nivel_requerido, cantidad_personas) VALUES
((SELECT id FROM proyecto WHERE nombre='Portal de Clientes' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='Java' LIMIT 1), 'AVANZADO', 1),
((SELECT id FROM proyecto WHERE nombre='Portal de Clientes' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='Spring Boot' LIMIT 1), 'AVANZADO', 1),
((SELECT id FROM proyecto WHERE nombre='Portal de Clientes' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='MySQL' LIMIT 1), 'INTERMEDIO', 1),

((SELECT id FROM proyecto WHERE nombre='App de Delivery Interno' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='React' LIMIT 1), 'AVANZADO', 1),
((SELECT id FROM proyecto WHERE nombre='App de Delivery Interno' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='JavaScript' LIMIT 1), 'INTERMEDIO', 1),

((SELECT id FROM proyecto WHERE nombre='Rediseño de Marca' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='Figma' LIMIT 1), 'AVANZADO', 1),

((SELECT id FROM proyecto WHERE nombre='Migración a la Nube' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='Docker' LIMIT 1), 'AVANZADO', 1),
((SELECT id FROM proyecto WHERE nombre='Migración a la Nube' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='AWS' LIMIT 1), 'AVANZADO', 1),

((SELECT id FROM proyecto WHERE nombre='Sitio Web Corporativo v1' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='React' LIMIT 1), 'INTERMEDIO', 1),
((SELECT id FROM proyecto WHERE nombre='Sitio Web Corporativo v1' LIMIT 1), (SELECT id FROM habilidad WHERE nombre='CSS' LIMIT 1), 'BASICO', 1);


-- =====================================================================
-- ASIGNACIONES (para probar "Mis solicitudes", "Proyectos destacados"
-- del perfil público, y el badge "Ya te postulaste")
-- =====================================================================

-- Carlos activo en Portal de Clientes, María activa en App de Delivery
INSERT IGNORE INTO asignacion (proyecto_id, colaborador_id, horas_semanales, origen, estado, aprobado_por_pm, aprobado_por_rm, fecha_aprobacion_pm, fecha_aprobacion_rm, fecha_activacion) VALUES
((SELECT id FROM proyecto WHERE nombre='Portal de Clientes' LIMIT 1), (SELECT id FROM usuario WHERE correo='col@skillbridge.com' LIMIT 1), 20, 'PROPUESTA_PM', 'ACTIVA', 1, 1, NOW(), NOW(), NOW()),
((SELECT id FROM proyecto WHERE nombre='App de Delivery Interno' LIMIT 1), (SELECT id FROM usuario WHERE correo='maria.lopez@skillbridge.com' LIMIT 1), 20, 'PROPUESTA_PM', 'ACTIVA', 1, 1, NOW(), NOW(), NOW());

-- Mallory con una solicitud pendiente en Portal de Clientes
INSERT IGNORE INTO asignacion (proyecto_id, colaborador_id, horas_semanales, origen, mensaje_solicitud, estado) VALUES
((SELECT id FROM proyecto WHERE nombre='Portal de Clientes' LIMIT 1), (SELECT id FROM usuario WHERE correo='mallory.hulme@skillbridge.com' LIMIT 1), 20, 'SOLICITADA_COLABORADOR', 'Me interesa este proyecto porque puedo aportar en el frontend.', 'PENDIENTE');

-- Carlos con un proyecto finalizado en su historial
INSERT IGNORE INTO asignacion (proyecto_id, colaborador_id, horas_semanales, origen, estado, aprobado_por_pm, aprobado_por_rm, fecha_aprobacion_pm, fecha_aprobacion_rm, fecha_activacion, fecha_finalizacion, motivo_finalizacion) VALUES
((SELECT id FROM proyecto WHERE nombre='Sitio Web Corporativo v1' LIMIT 1), (SELECT id FROM usuario WHERE correo='col@skillbridge.com' LIMIT 1), 20, 'PROPUESTA_PM', 'FINALIZADA', 1, 1, '2025-02-05', '2025-02-05', '2025-02-05', '2025-06-30', 'OTRO');

-- Dunn queda sin ninguna asignación a propósito, para probar el estado "sin proyectos destacados"
-- =====================================================================
-- CASOS DE PRUEBA PARA EL MÓDULO DE FOROS
-- Usuario principal de pruebas: col@skillbridge.com
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0) Usuario adicional: un "otro colaborador" sin relación con col@skillbridge.com
--    Sirve para probar que col NO puede editar/eliminar contenido ajeno,
--    y para ser dueño de proyectos donde col no tiene asignación.
-- ---------------------------------------------------------------------
INSERT IGNORE INTO usuario (correo, password_hash, nombre, apellido, rol_id, activo, cargo, horas_disponibles)
SELECT 'colaborador2@skillbridge.com',
       '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom',
       'Ana', 'Torres',
       (SELECT id FROM rol WHERE nombre = 'COLABORADOR' LIMIT 1),
       1, 'Frontend Developer', 40
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE correo = 'colaborador2@skillbridge.com' LIMIT 1);

-- ---------------------------------------------------------------------
-- 1) Etiquetas adicionales
-- ---------------------------------------------------------------------
INSERT IGNORE INTO etiqueta (nombre) SELECT 'Anuncio' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM etiqueta WHERE nombre = 'Anuncio' LIMIT 1);
INSERT IGNORE INTO etiqueta (nombre) SELECT 'Bug' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM etiqueta WHERE nombre = 'Bug' LIMIT 1);

-- ---------------------------------------------------------------------
-- 2) SEGUNDO FORO GENERAL DE COMUNIDAD
--    CASO: la pestaña "Comunidad" debe mostrar más de un foro.
-- ---------------------------------------------------------------------
INSERT IGNORE INTO foro (proyecto_id, tipo, es_publico, nombre, fecha_creacion)
SELECT NULL, 'GENERAL', TRUE, 'Anuncios Oficiales', CURRENT_TIMESTAMP
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM foro WHERE nombre = 'Anuncios Oficiales' LIMIT 1);

-- ---------------------------------------------------------------------
-- 3) PROYECTOS DE PRUEBA (todos con PM = pm@skillbridge.com)
-- ---------------------------------------------------------------------

-- 3.1 Proyecto donde col SÍ tiene asignación ACTIVA, foro PRIVADO (ya lo tenías)
--     -> Aparece en "Mis proyectos", NO en "Comunidad", col puede publicar/responder/eliminar lo suyo.

-- 3.2 Proyecto donde col SÍ tiene asignación ACTIVA, foro COMPARTIDO a comunidad
--     -> Aparece en "Mis proyectos" Y en "Comunidad", en ambos casos col puede participar (es miembro).
INSERT IGNORE INTO proyecto (nombre, descripcion, estado, prioridad, justificacion_prioridad,
                       colaboradores_requeridos, horas_semanales_requeridas, pm_id, fecha_creacion)
SELECT '[DEMO] Proyecto Compartido', 'Proyecto cuyo foro el PM decidió compartir con la comunidad',
       'ACTIVO', 'ALTA', 'Proyecto de alta visibilidad', 2, 20,
       (SELECT id FROM usuario WHERE correo = 'pm@skillbridge.com' LIMIT 1), CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM proyecto WHERE nombre = '[DEMO] Proyecto Compartido' LIMIT 1);

-- 3.3 Proyecto AJENO (col NO tiene asignación), foro COMPARTIDO a comunidad
--     -> Aparece SOLO en "Comunidad", de SOLO LECTURA para col (no ve botones de publicar/responder/eliminar).
INSERT IGNORE INTO proyecto (nombre, descripcion, estado, prioridad, justificacion_prioridad,
                       colaboradores_requeridos, horas_semanales_requeridas, pm_id, fecha_creacion)
SELECT '[DEMO] Proyecto Ajeno Publico', 'Proyecto de otro equipo, foro visible para toda la comunidad',
       'ACTIVO', 'MEDIA', 'Visibilidad para toda la org', 1, 20,
       (SELECT id FROM usuario WHERE correo = 'pm@skillbridge.com' LIMIT 1), CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM proyecto WHERE nombre = '[DEMO] Proyecto Ajeno Publico' LIMIT 1);

-- 3.4 Proyecto AJENO (col NO tiene asignación), foro PRIVADO
--     -> NO debe aparecer en ninguna pestaña para col, y si intenta entrar por URL con el foroId
--        directo, el servicio debe rechazarlo ("no tienes acceso").
INSERT IGNORE INTO proyecto (nombre, descripcion, estado, prioridad, justificacion_prioridad,
                       colaboradores_requeridos, horas_semanales_requeridas, pm_id, fecha_creacion)
SELECT '[DEMO] Proyecto Ajeno Privado', 'Proyecto de otro equipo, foro interno y privado',
       'ACTIVO', 'BAJA', 'Proyecto interno', 1, 20,
       (SELECT id FROM usuario WHERE correo = 'pm@skillbridge.com' LIMIT 1), CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM proyecto WHERE nombre = '[DEMO] Proyecto Ajeno Privado' LIMIT 1);

-- 3.5 Proyecto donde col tuvo una asignación pero ya está FINALIZADA
--     -> NO debe aparecer en "Mis proyectos" (ya no es un miembro activo).
INSERT IGNORE INTO proyecto (nombre, descripcion, estado, prioridad, justificacion_prioridad,
                       colaboradores_requeridos, horas_semanales_requeridas, pm_id, fecha_creacion)
SELECT '[DEMO] Proyecto Finalizado', 'Proyecto donde col ya no participa',
       'FINALIZADO', 'MEDIA', 'Proyecto cerrado', 1, 20,
       (SELECT id FROM usuario WHERE correo = 'pm@skillbridge.com' LIMIT 1), CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM proyecto WHERE nombre = '[DEMO] Proyecto Finalizado' LIMIT 1);

-- 3.6 Proyecto donde col tiene una asignación PENDIENTE (aún sin aprobar)
--     -> Tampoco debe aparecer en "Mis proyectos" (todavía no es ACTIVA).
INSERT IGNORE INTO proyecto (nombre, descripcion, estado, prioridad, justificacion_prioridad,
                       colaboradores_requeridos, horas_semanales_requeridas, pm_id, fecha_creacion)
SELECT '[DEMO] Proyecto Pendiente', 'Proyecto donde la asignación de col aún no fue aprobada',
       'ACTIVO', 'MEDIA', 'Proyecto en formación de equipo', 2, 20,
       (SELECT id FROM usuario WHERE correo = 'pm@skillbridge.com' LIMIT 1), CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM proyecto WHERE nombre = '[DEMO] Proyecto Pendiente' LIMIT 1);

-- ---------------------------------------------------------------------
-- 4) FOROS de cada proyecto nuevo
-- ---------------------------------------------------------------------
INSERT IGNORE INTO foro (proyecto_id, tipo, es_publico, nombre, fecha_creacion)
SELECT (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Compartido' LIMIT 1),
       'PROYECTO', TRUE, 'Foro — [DEMO] Proyecto Compartido', CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM foro WHERE nombre = 'Foro — [DEMO] Proyecto Compartido' LIMIT 1);

INSERT IGNORE INTO foro (proyecto_id, tipo, es_publico, nombre, fecha_creacion)
SELECT (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Ajeno Publico' LIMIT 1),
       'PROYECTO', TRUE, 'Foro — [DEMO] Proyecto Ajeno Publico', CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM foro WHERE nombre = 'Foro — [DEMO] Proyecto Ajeno Publico' LIMIT 1);

INSERT IGNORE INTO foro (proyecto_id, tipo, es_publico, nombre, fecha_creacion)
SELECT (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Ajeno Privado' LIMIT 1),
       'PROYECTO', FALSE, 'Foro — [DEMO] Proyecto Ajeno Privado', CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM foro WHERE nombre = 'Foro — [DEMO] Proyecto Ajeno Privado' LIMIT 1);

INSERT IGNORE INTO foro (proyecto_id, tipo, es_publico, nombre, fecha_creacion)
SELECT (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Finalizado' LIMIT 1),
       'PROYECTO', FALSE, 'Foro — [DEMO] Proyecto Finalizado', CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM foro WHERE nombre = 'Foro — [DEMO] Proyecto Finalizado' LIMIT 1);

INSERT IGNORE INTO foro (proyecto_id, tipo, es_publico, nombre, fecha_creacion)
SELECT (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Pendiente' LIMIT 1),
       'PROYECTO', FALSE, 'Foro — [DEMO] Proyecto Pendiente', CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM foro WHERE nombre = 'Foro — [DEMO] Proyecto Pendiente' LIMIT 1);

-- ---------------------------------------------------------------------
-- 5) ASIGNACIONES de col@skillbridge.com en cada proyecto nuevo
-- ---------------------------------------------------------------------

-- col ACTIVA en Proyecto Compartido
INSERT IGNORE INTO asignacion (proyecto_id, colaborador_id, horas_semanales, origen, estado,
                         aprobado_por_pm, aprobado_por_rm, fecha_activacion, fecha_solicitud)
SELECT (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Compartido' LIMIT 1),
       (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com' LIMIT 1),
       15, 'PROPUESTA_PM', 'ACTIVA', TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM asignacion
    WHERE proyecto_id = (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Compartido' LIMIT 1)
      AND colaborador_id = (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com' LIMIT 1));

-- col FINALIZADA en Proyecto Finalizado (ya no cuenta como activa)
INSERT IGNORE INTO asignacion (proyecto_id, colaborador_id, horas_semanales, origen, estado,
                         aprobado_por_pm, aprobado_por_rm, fecha_activacion, fecha_finalizacion,
                         motivo_finalizacion, fecha_solicitud)
SELECT (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Finalizado' LIMIT 1),
       (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com' LIMIT 1),
       10, 'PROPUESTA_PM', 'FINALIZADA', TRUE, TRUE,
       CURRENT_TIMESTAMP - INTERVAL 60 DAY, CURRENT_TIMESTAMP - INTERVAL 5 DAY,
       'PROYECTO_CANCELADO', CURRENT_TIMESTAMP - INTERVAL 60 DAY
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM asignacion
    WHERE proyecto_id = (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Finalizado' LIMIT 1)
      AND colaborador_id = (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com' LIMIT 1));

-- col PENDIENTE en Proyecto Pendiente (aún sin aprobación completa)
INSERT IGNORE INTO asignacion (proyecto_id, colaborador_id, horas_semanales, origen, estado,
                         aprobado_por_pm, aprobado_por_rm, fecha_solicitud)
SELECT (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Pendiente' LIMIT 1),
       (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com' LIMIT 1),
       10, 'SOLICITADA_COLABORADOR', 'PENDIENTE', TRUE, FALSE, CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM asignacion
    WHERE proyecto_id = (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Pendiente' LIMIT 1)
      AND colaborador_id = (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com' LIMIT 1));

-- colaborador2 ACTIVA en los dos proyectos "ajenos" (para que tengan contenido y dueño)
INSERT IGNORE INTO asignacion (proyecto_id, colaborador_id, horas_semanales, origen, estado,
                         aprobado_por_pm, aprobado_por_rm, fecha_activacion, fecha_solicitud)
SELECT (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Ajeno Publico' LIMIT 1),
       (SELECT id FROM usuario WHERE correo = 'colaborador2@skillbridge.com' LIMIT 1),
       20, 'PROPUESTA_PM', 'ACTIVA', TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM asignacion
    WHERE proyecto_id = (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Ajeno Publico' LIMIT 1)
      AND colaborador_id = (SELECT id FROM usuario WHERE correo = 'colaborador2@skillbridge.com' LIMIT 1));

INSERT IGNORE INTO asignacion (proyecto_id, colaborador_id, horas_semanales, origen, estado,
                         aprobado_por_pm, aprobado_por_rm, fecha_activacion, fecha_solicitud)
SELECT (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Ajeno Privado' LIMIT 1),
       (SELECT id FROM usuario WHERE correo = 'colaborador2@skillbridge.com' LIMIT 1),
       20, 'PROPUESTA_PM', 'ACTIVA', TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM asignacion
    WHERE proyecto_id = (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Ajeno Privado' LIMIT 1)
      AND colaborador_id = (SELECT id FROM usuario WHERE correo = 'colaborador2@skillbridge.com' LIMIT 1));

-- ---------------------------------------------------------------------
-- 6) PUBLICACIONES adicionales
-- ---------------------------------------------------------------------

-- Más contenido en "Comunidad SkillBridge" (para probar el buscador con varios resultados)
INSERT IGNORE INTO publicacion_foro (foro_id, autor_id, etiqueta_id, titulo, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM foro WHERE nombre = 'Comunidad SkillBridge' LIMIT 1),
       (SELECT id FROM usuario WHERE correo = 'admin@skillbridge.com' LIMIT 1),
       (SELECT id FROM etiqueta WHERE nombre = 'Anuncio' LIMIT 1),
       'Mantenimiento programado este fin de semana',
       '<p>El sistema estará en mantenimiento el sábado de 10pm a 12am.</p>',
       CURRENT_TIMESTAMP - INTERVAL 2 DAY, TRUE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM publicacion_foro WHERE titulo = 'Mantenimiento programado este fin de semana' LIMIT 1);

INSERT IGNORE INTO publicacion_foro (foro_id, autor_id, etiqueta_id, titulo, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM foro WHERE nombre = 'Comunidad SkillBridge' LIMIT 1),
       (SELECT id FROM usuario WHERE correo = 'colaborador2@skillbridge.com' LIMIT 1),
       NULL,
       'Recomendaciones de librerías para testing',
       '<p>¿Qué usan para tests de integración en Spring Boot, Testcontainers o H2?</p>',
       CURRENT_TIMESTAMP - INTERVAL 1 DAY, TRUE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM publicacion_foro WHERE titulo = 'Recomendaciones de librerías para testing' LIMIT 1);

-- CASO: publicación eliminada lógicamente (activo = FALSE) -> NO debe aparecer en el listado
INSERT IGNORE INTO publicacion_foro (foro_id, autor_id, etiqueta_id, titulo, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM foro WHERE nombre = 'Comunidad SkillBridge' LIMIT 1),
       (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com' LIMIT 1),
       NULL,
       'Publicación de prueba (debe estar oculta)',
       '<p>Si ves este texto en la lista de publicaciones, el borrado lógico no está filtrando bien.</p>',
       CURRENT_TIMESTAMP - INTERVAL 3 DAY, FALSE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM publicacion_foro WHERE titulo = 'Publicación de prueba (debe estar oculta LIMIT 1)');

-- Publicación del PM en el foro general (Anuncios Oficiales)
INSERT IGNORE INTO publicacion_foro (foro_id, autor_id, etiqueta_id, titulo, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM foro WHERE nombre = 'Anuncios Oficiales' LIMIT 1),
       (SELECT id FROM usuario WHERE correo = 'pm@skillbridge.com' LIMIT 1),
       (SELECT id FROM etiqueta WHERE nombre = 'Anuncio' LIMIT 1),
       'Nueva política de code review',
       '<p>A partir de este sprint, todo PR requiere al menos 2 aprobaciones.</p>',
       CURRENT_TIMESTAMP, TRUE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM publicacion_foro WHERE titulo = 'Nueva política de code review' LIMIT 1);

-- Publicación de col en su proyecto privado, sin respuestas todavía (estado vacío)
INSERT IGNORE INTO publicacion_foro (foro_id, autor_id, etiqueta_id, titulo, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM foro WHERE nombre = 'Foro — [DEMO] Proyecto Foro' LIMIT 1),
       (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com' LIMIT 1),
       (SELECT id FROM etiqueta WHERE nombre = 'Bug' LIMIT 1),
       'Bug en el login con Google',
       '<p>Al iniciar sesión con Google a veces no redirige bien al dashboard. ¿Alguien más lo vio?</p>',
       CURRENT_TIMESTAMP, TRUE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM publicacion_foro WHERE titulo = 'Bug en el login con Google' LIMIT 1);

-- Dos publicaciones en el proyecto COMPARTIDO (col es miembro, así que puede escribir aquí,
-- se vea desde "Mis proyectos" o desde "Comunidad")
INSERT IGNORE INTO publicacion_foro (foro_id, autor_id, etiqueta_id, titulo, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM foro WHERE nombre = 'Foro — [DEMO] Proyecto Compartido' LIMIT 1),
       (SELECT id FROM usuario WHERE correo = 'pm@skillbridge.com' LIMIT 1),
       (SELECT id FROM etiqueta WHERE nombre = 'Anuncio' LIMIT 1),
       'Bienvenidos al foro abierto del proyecto',
       '<p>Este foro es visible para toda la organización, pero solo el equipo puede publicar.</p>',
       CURRENT_TIMESTAMP - INTERVAL 4 DAY, TRUE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM publicacion_foro WHERE titulo = 'Bienvenidos al foro abierto del proyecto' LIMIT 1);

INSERT IGNORE INTO publicacion_foro (foro_id, autor_id, etiqueta_id, titulo, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM foro WHERE nombre = 'Foro — [DEMO] Proyecto Compartido' LIMIT 1),
       (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com' LIMIT 1),
       NULL,
       'Avance de la sprint actual',
       '<p>Ya terminé mis tareas del sprint, quedan pendientes las de QA.</p>',
       CURRENT_TIMESTAMP - INTERVAL 1 DAY, TRUE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM publicacion_foro WHERE titulo = 'Avance de la sprint actual' LIMIT 1);

-- CASO: publicación en un proyecto AJENO PÚBLICO -> col debe poder LEERLA pero no
-- ver botones de Responder/Eliminar/Like habilitados para escribir (solo lectura)
INSERT IGNORE INTO publicacion_foro (foro_id, autor_id, etiqueta_id, titulo, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM foro WHERE nombre = 'Foro — [DEMO] Proyecto Ajeno Publico' LIMIT 1),
       (SELECT id FROM usuario WHERE correo = 'colaborador2@skillbridge.com' LIMIT 1),
       NULL,
       'Demo pública de nuestro proyecto',
       '<p>Compartimos este avance con toda la comunidad para recibir feedback.</p>',
       CURRENT_TIMESTAMP, TRUE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM publicacion_foro WHERE titulo = 'Demo pública de nuestro proyecto' LIMIT 1);

-- CASO: publicación en un proyecto AJENO PRIVADO -> col NO debe poder verla ni acceder al foro
INSERT IGNORE INTO publicacion_foro (foro_id, autor_id, etiqueta_id, titulo, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM foro WHERE nombre = 'Foro — [DEMO] Proyecto Ajeno Privado' LIMIT 1),
       (SELECT id FROM usuario WHERE correo = 'colaborador2@skillbridge.com' LIMIT 1),
       NULL,
       'Discusión interna del equipo',
       '<p>Este contenido es privado del equipo, col no debería poder verlo.</p>',
       CURRENT_TIMESTAMP, TRUE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM publicacion_foro WHERE titulo = 'Discusión interna del equipo' LIMIT 1);

-- ---------------------------------------------------------------------
-- 7) RESPUESTAS adicionales (incluye una eliminada lógicamente)
-- ---------------------------------------------------------------------

INSERT IGNORE INTO respuesta_foro (publicacion_id, autor_id, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM publicacion_foro WHERE titulo = 'Recomendaciones de librerías para testing' LIMIT 1),
       (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com' LIMIT 1),
       '<p>Yo uso Testcontainers, se siente más real que H2 en memoria.</p>',
       CURRENT_TIMESTAMP, TRUE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM respuesta_foro WHERE contenido LIKE '%se siente más real que H2%' LIMIT 1);

INSERT IGNORE INTO respuesta_foro (publicacion_id, autor_id, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM publicacion_foro WHERE titulo = 'Recomendaciones de librerías para testing' LIMIT 1),
       (SELECT id FROM usuario WHERE correo = 'rm@skillbridge.com' LIMIT 1),
       '<p>Depende del proyecto, para cosas rápidas H2 basta.</p>',
       CURRENT_TIMESTAMP, TRUE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM respuesta_foro WHERE contenido LIKE '%para cosas rápidas H2 basta%' LIMIT 1);

-- CASO: respuesta eliminada lógicamente -> no debe listarse ni contar en "X respuestas"
INSERT IGNORE INTO respuesta_foro (publicacion_id, autor_id, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM publicacion_foro WHERE titulo = 'Recomendaciones de librerías para testing' LIMIT 1),
       (SELECT id FROM usuario WHERE correo = 'admin@skillbridge.com' LIMIT 1),
       '<p>Respuesta de prueba (debe estar oculta).</p>',
       CURRENT_TIMESTAMP, FALSE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM respuesta_foro WHERE contenido LIKE '%Respuesta de prueba (debe estar oculta LIMIT 1)%');

-- Respuesta del PM a la publicación del bug de col
INSERT IGNORE INTO respuesta_foro (publicacion_id, autor_id, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM publicacion_foro WHERE titulo = 'Bug en el login con Google' LIMIT 1),
       (SELECT id FROM usuario WHERE correo = 'pm@skillbridge.com' LIMIT 1),
       '<p>Sí, ya lo reporté. Al parecer es el redirect_uri mal configurado.</p>',
       CURRENT_TIMESTAMP, TRUE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM respuesta_foro WHERE contenido LIKE '%redirect_uri mal configurado%' LIMIT 1);

-- Respuesta a la demo pública (colaborador2 respondiendo su propia publicación)
INSERT IGNORE INTO respuesta_foro (publicacion_id, autor_id, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM publicacion_foro WHERE titulo = 'Demo pública de nuestro proyecto' LIMIT 1),
       (SELECT id FROM usuario WHERE correo = 'colaborador2@skillbridge.com' LIMIT 1),
       '<p>Gracias por el feedback a quienes ya comentaron.</p>',
       CURRENT_TIMESTAMP, TRUE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM respuesta_foro WHERE contenido LIKE '%Gracias por el feedback a quienes%' LIMIT 1);

-- ---------------------------------------------------------------------
-- 8) LIKES (voto_publicacion / voto_respuesta) — para probar contador y toggle
-- ---------------------------------------------------------------------

-- col ya le dio like a esta publicación -> al cargar la página debe verse el corazón "activado"
INSERT IGNORE INTO voto_publicacion (usuario_id, publicacion_id, tipo)
SELECT (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com' LIMIT 1),
       (SELECT id FROM publicacion_foro WHERE titulo = '¿Cómo configuran su entorno de Spring Boot?' LIMIT 1),
       'POSITIVO'
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM voto_publicacion
    WHERE usuario_id = (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com' LIMIT 1)
      AND publicacion_id = (SELECT id FROM publicacion_foro WHERE titulo = '¿Cómo configuran su entorno de Spring Boot?' LIMIT 1));

-- Otros usuarios también le dieron like a la misma publicación -> contador debe ser > 1
INSERT IGNORE INTO voto_publicacion (usuario_id, publicacion_id, tipo)
SELECT (SELECT id FROM usuario WHERE correo = 'admin@skillbridge.com' LIMIT 1),
       (SELECT id FROM publicacion_foro WHERE titulo = '¿Cómo configuran su entorno de Spring Boot?' LIMIT 1),
       'POSITIVO'
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM voto_publicacion
    WHERE usuario_id = (SELECT id FROM usuario WHERE correo = 'admin@skillbridge.com' LIMIT 1)
      AND publicacion_id = (SELECT id FROM publicacion_foro WHERE titulo = '¿Cómo configuran su entorno de Spring Boot?' LIMIT 1));

INSERT IGNORE INTO voto_publicacion (usuario_id, publicacion_id, tipo)
SELECT (SELECT id FROM usuario WHERE correo = 'rm@skillbridge.com' LIMIT 1),
       (SELECT id FROM publicacion_foro WHERE titulo = '¿Cómo configuran su entorno de Spring Boot?' LIMIT 1),
       'POSITIVO'
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM voto_publicacion
    WHERE usuario_id = (SELECT id FROM usuario WHERE correo = 'rm@skillbridge.com' LIMIT 1)
      AND publicacion_id = (SELECT id FROM publicacion_foro WHERE titulo = '¿Cómo configuran su entorno de Spring Boot?' LIMIT 1));

-- Esta publicación tiene likes de OTROS pero col todavía NO le dio like -> corazón debe verse "apagado"
INSERT IGNORE INTO voto_publicacion (usuario_id, publicacion_id, tipo)
SELECT (SELECT id FROM usuario WHERE correo = 'pm@skillbridge.com' LIMIT 1),
       (SELECT id FROM publicacion_foro WHERE titulo = 'Nueva política de code review' LIMIT 1),
       'POSITIVO'
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM voto_publicacion
    WHERE usuario_id = (SELECT id FROM usuario WHERE correo = 'pm@skillbridge.com' LIMIT 1)
      AND publicacion_id = (SELECT id FROM publicacion_foro WHERE titulo = 'Nueva política de code review' LIMIT 1));

-- Like en una respuesta (para probar el corazón dentro de las respuestas también)
INSERT IGNORE INTO voto_respuesta (usuario_id, respuesta_id, tipo)
SELECT (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com' LIMIT 1),
       (SELECT id FROM respuesta_foro WHERE contenido LIKE '%para cosas rápidas H2 basta%' LIMIT 1),
       'POSITIVO'
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM voto_respuesta
    WHERE usuario_id = (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com' LIMIT 1)
      AND respuesta_id = (SELECT id FROM respuesta_foro WHERE contenido LIKE '%para cosas rápidas H2 basta%' LIMIT 1));

-- =====================================================================
-- VERIFICACIÓN RÁPIDA (opcional, solo para revisar que todo cargó bien)
-- =====================================================================
SELECT p.nombre AS proyecto, a.estado, u.correo
FROM asignacion a
JOIN proyecto p ON p.id = a.proyecto_id
JOIN usuario u ON u.id = a.colaborador_id
WHERE u.correo IN ('col@skillbridge.com', 'colaborador2@skillbridge.com')
ORDER BY u.correo, p.nombre;

SELECT f.nombre AS foro, f.tipo, f.es_publico, p.nombre AS proyecto
FROM foro f
LEFT JOIN proyecto p ON p.id = f.proyecto_id
ORDER BY f.tipo, f.nombre;
INSERT IGNORE INTO actividad (proyecto_id, colaborador_id, titulo, descripcion, horas_estimadas, fecha_limite, estado, creado_por)
VALUES
(
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Compartido' LIMIT 1),
    (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com' LIMIT 1),
    'Diseñar el módulo de login',
    'Crear la pantalla de inicio de sesión con validación de credenciales y mensajes de error.',
    8.00,
    '2026-09-30',
    'PENDIENTE',
    (SELECT pm_id FROM proyecto WHERE nombre = '[DEMO] Proyecto Compartido' LIMIT 1)
),
(
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Compartido' LIMIT 1),
    (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com' LIMIT 1),
    'Conectar endpoint de autenticación',
    'Integrar el frontend con el endpoint /auth/login del backend.',
    5.50,
    '2026-09-25',
    'EN_PROGRESO',
    (SELECT pm_id FROM proyecto WHERE nombre = '[DEMO] Proyecto Compartido' LIMIT 1)
),
(
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Compartido' LIMIT 1),
    (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com' LIMIT 1),
    'Redactar casos de prueba',
    'Documentar los casos de prueba manuales para el flujo de login.',
    3.00,
    '2026-09-10',
    'COMPLETADA',
    (SELECT pm_id FROM proyecto WHERE nombre = '[DEMO] Proyecto Compartido' LIMIT 1)
);

-- Para que la "Redactar casos de prueba" ya se vea como completada a tiempo
UPDATE actividad
SET estado_entrega = 'A_TIEMPO', fecha_entrega = '2026-09-09 15:00:00'
WHERE titulo = 'Redactar casos de prueba';
-- ============================================================
-- Datos adicionales: foro/documento en proyecto finalizado 
-- y actividades para probarevidencia + strikes
-- ============================================================
USE skillbridge_db;

-- Borramos sus strikes de prueba anteriores
DELETE FROM penalizacion
WHERE colaborador_id = (SELECT id FROM usuario WHERE correo = 'demo.colaborador2@skillbridge.local' LIMIT 1)
  AND proyecto_id = (SELECT id FROM proyecto WHERE nombre = '[DEMO] Migracion Cloud' LIMIT 1);

-- Lo regresamos a ACTIVA (por si quedó FINALIZADA por bajo desempeño)
UPDATE asignacion
SET estado = 'ACTIVA', motivo_finalizacion = NULL, fecha_finalizacion = NULL
WHERE proyecto_id = (SELECT id FROM proyecto WHERE nombre = '[DEMO] Migracion Cloud' LIMIT 1)
  AND colaborador_id = (SELECT id FROM usuario WHERE correo = 'demo.colaborador2@skillbridge.local' LIMIT 1)
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
INSERT IGNORE INTO actividad (proyecto_id, colaborador_id, titulo, descripcion, horas_estimadas,
                       fecha_limite, estado, creado_por)
SELECT
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Migracion Cloud' LIMIT 1),
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador3@skillbridge.local' LIMIT 1),
    '[DEMO] Probar flujo de notificaciones',
    'Actividad creada para probar el sistema de notificaciones end-to-end.',
    4.00,
    DATE_ADD(CURRENT_DATE, INTERVAL 7 DAY),
    'PENDIENTE',
    (SELECT id FROM usuario WHERE correo = 'demo.pm@skillbridge.local' LIMIT 1)
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM actividad WHERE titulo = '[DEMO] Probar flujo de notificaciones' LIMIT 1);
-- //-----------------------------------



-- 1) Foro y documento en el proyecto YA FINALIZADO, para comprobar que ahora
--    sí se pueden ver (solo lectura) tras el fix.
INSERT IGNORE INTO foro (proyecto_id, tipo, es_publico, nombre, fecha_creacion)
SELECT (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Finalizado' LIMIT 1),
       'PROYECTO', FALSE, '[DEMO] Foro Proyecto Finalizado', CURRENT_TIMESTAMP - INTERVAL 100 DAY
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM foro WHERE nombre = '[DEMO] Foro Proyecto Finalizado' LIMIT 1);

INSERT IGNORE INTO documento (proyecto_id, subido_por_id, nombre, categoria, archivo_url, activo, fecha_creacion)
SELECT
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Finalizado' LIMIT 1),
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador3@skillbridge.local' LIMIT 1),
    '[DEMO] Acta de cierre.pdf', 'PDF', '/uploads/documentos/demo-cierre.pdf', TRUE, CURRENT_TIMESTAMP - INTERVAL 31 DAY
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM documento WHERE nombre = '[DEMO] Acta de cierre.pdf' LIMIT 1);

-- 2) demo.colaborador1 (ACTIVA en [DEMO] Migracion Cloud):

-- 2a) Actividad pendiente sin vencer -> clic -> "Marcar listo" con evidencia
INSERT IGNORE INTO actividad (proyecto_id, colaborador_id, titulo, descripcion, horas_estimadas,
                       fecha_limite, estado, creado_por)
SELECT
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Migracion Cloud' LIMIT 1),
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador1@skillbridge.local' LIMIT 1),
    '[DEMO] Documentar plan de rollback',
    'Redactar el procedimiento de reversión en caso de fallo en la migración.',
    6.00,
    DATE_ADD(CURRENT_DATE, INTERVAL 5 DAY),
    'PENDIENTE',
    (SELECT id FROM usuario WHERE correo = 'demo.pm@skillbridge.local' LIMIT 1)
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM actividad WHERE titulo = '[DEMO] Documentar plan de rollback' LIMIT 1);

-- 2b) Ya marcada "lista para revisar" con evidencia -> aparece en la bandeja del PM
INSERT IGNORE INTO actividad (proyecto_id, colaborador_id, titulo, descripcion, horas_estimadas,
                       fecha_limite, fecha_marcado_revision, evidencia_url, comentario_colaborador,
                       estado, creado_por)
SELECT
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Migracion Cloud' LIMIT 1),
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador1@skillbridge.local' LIMIT 1),
    '[DEMO] Migrar base de datos de staging',
    'Migrar la base de datos del ambiente de staging al nuevo proveedor cloud.',
    12.00,
    DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY),
    CURRENT_TIMESTAMP - INTERVAL 1 DAY,
    '/uploads/evidencias-actividades/demo-migracion-staging.pdf',
    'Migración completada, adjunto el log sin errores.',
    'EN_REVISION',
    (SELECT id FROM usuario WHERE correo = 'demo.pm@skillbridge.local' LIMIT 1)
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM actividad WHERE titulo = '[DEMO] Migrar base de datos de staging' LIMIT 1);

-- 3) demo.colaborador2 (ACTIVA en [DEMO] Migracion Cloud): ya tiene 2 strikes
--    previos + 1 actividad vencida sin entregar -> al ver su proyecto o sus
--    actividades, debe aplicarse el 3er strike y expulsarlo automáticamente.

INSERT IGNORE INTO actividad (proyecto_id, colaborador_id, titulo, descripcion, horas_estimadas,
                       fecha_limite, estado, creado_por)
SELECT
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Migracion Cloud' LIMIT 1),
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador2@skillbridge.local' LIMIT 1),
    '[DEMO] Configurar balanceador de carga',
    'Configurar el balanceador de carga para el nuevo cluster productivo.',
    10.00,
    DATE_SUB(CURRENT_DATE, INTERVAL 3 DAY),
    'PENDIENTE',
    (SELECT id FROM usuario WHERE correo = 'demo.pm@skillbridge.local' LIMIT 1)
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM actividad WHERE titulo = '[DEMO] Configurar balanceador de carga' LIMIT 1);

INSERT IGNORE INTO penalizacion (colaborador_id, proyecto_id, tipo, motivo, monto, fecha)
SELECT
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador2@skillbridge.local' LIMIT 1),
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Migracion Cloud' LIMIT 1),
    'ACTIVIDAD_TARDIA',
    '[DEMO] Strike de prueba #1: entregó una actividad fuera de plazo.',
    1.00, CURRENT_TIMESTAMP - INTERVAL 10 DAY
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM penalizacion WHERE motivo = '[DEMO] Strike de prueba #1: entregó una actividad fuera de plazo.' LIMIT 1);

INSERT IGNORE INTO penalizacion (colaborador_id, proyecto_id, tipo, motivo, monto, fecha)
SELECT
    (SELECT id FROM usuario WHERE correo = 'demo.colaborador2@skillbridge.local' LIMIT 1),
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Migracion Cloud' LIMIT 1),
    'ACTIVIDAD_TARDIA',
    '[DEMO] Strike de prueba #2: el PM devolvió una actividad mal hecha.',
    1.00, CURRENT_TIMESTAMP - INTERVAL 5 DAY
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM penalizacion WHERE motivo = '[DEMO] Strike de prueba #2: el PM devolvió una actividad mal hecha.' LIMIT 1);
-- ============================================================================
-- SkillBridge AI - Datos de prueba para "Explorar > Cursos"
-- Requiere haber corrido antes: skillbridge_db_v4.sql
--                                
-- Usuario de prueba para iniciar sesión como colaborador:
--   correo: col@skillbridge.com / contraseña: abc123
-- ============================================================================

USE skillbridge_db;

-- ---------------------------------------------------------------------------
-- 1) CATÁLOGO DE CURSOS (los "crea" el Administrador)
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO curso (nombre, descripcion, categoria, horas, activo, creado_por) VALUES
('Spring Security Avanzado',
 'Aprende a proteger APIs REST con autenticación y autorización robustas, JWT y roles.',
 'Técnico', 12, TRUE,
 (SELECT id FROM usuario WHERE correo = 'admin@skillbridge.com' LIMIT 1)),

('Comunicación Efectiva en Equipos Ágiles',
 'Mejora la comunicación dentro de equipos multidisciplinarios y ágiles, feedback y manejo de conflictos.',
 'Habilidades blandas', 6, TRUE,
 (SELECT id FROM usuario WHERE correo = 'admin@skillbridge.com' LIMIT 1)),

('AWS Certified Developer — Preparación',
 'Preparación guiada para la certificación AWS enfocada en desarrolladores: Lambda, S3, DynamoDB.',
 'Certificación', 20, TRUE,
 (SELECT id FROM usuario WHERE correo = 'admin@skillbridge.com' LIMIT 1)),

('React Avanzado',
 'Patrones avanzados, rendimiento y testing en aplicaciones React modernas.',
 'Técnico', 10, TRUE,
 (SELECT id FROM usuario WHERE correo = 'admin@skillbridge.com' LIMIT 1)),

('Curso Descontinuado (no debe aparecer)',
 'Este curso está desactivado y no debería verse en el catálogo del colaborador.',
 'Técnico', 8, FALSE,
 (SELECT id FROM usuario WHERE correo = 'admin@skillbridge.com' LIMIT 1));

-- ---------------------------------------------------------------------------
-- 2) SOLICITUDES / INSCRIPCIONES DE EJEMPLO para el colaborador de prueba
--    (col@skillbridge.com) — cubre los distintos estados y badges:
--    - Spring Security Avanzado        -> sin ninguna solicitud (botón "Solicitar inscripción")
--    - Comunicación Efectiva           -> SOLICITADO   (badge "Solicitud pendiente", sin botón)
--    - AWS Certified Developer         -> EN_CURSO     (badge "Inscrito", sin botón)
--    - React Avanzado                  -> RECHAZADO    (badge "Solicitud rechazada" + botón "Volver a solicitar")
-- ---------------------------------------------------------------------------

-- Pendiente de revisión por el RM (con justificación del colaborador)
INSERT IGNORE INTO colaborador_curso (colaborador_id, curso_id, origen, estado, justificacion_colaborador, fecha_solicitud)
VALUES (
    (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com' LIMIT 1),
    (SELECT id FROM curso WHERE nombre = 'Comunicación Efectiva en Equipos Ágiles' LIMIT 1),
    'SOLICITUD_COLABORADOR', 'SOLICITADO',
    'Quiero mejorar cómo doy feedback a mi equipo, últimamente hemos tenido roces por malos entendidos.',
    NOW() - INTERVAL 2 DAY
);

-- Ya aprobada por el RM (en curso)
INSERT IGNORE INTO colaborador_curso (colaborador_id, curso_id, origen, estado, asignado_por, justificacion_colaborador, fecha_solicitud, fecha_respuesta)
VALUES (
    (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com' LIMIT 1),
    (SELECT id FROM curso WHERE nombre = 'AWS Certified Developer — Preparación' LIMIT 1),
    'SOLICITUD_COLABORADOR', 'EN_CURSO',
    (SELECT id FROM usuario WHERE correo = 'rm@skillbridge.com' LIMIT 1),
    'Me gustaría certificarme para apoyar mejor al proyecto que usa infraestructura AWS.',
    NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 8 DAY
);

-- Rechazada por el RM (para probar el botón "Volver a solicitar")
INSERT IGNORE INTO colaborador_curso (colaborador_id, curso_id, origen, estado, asignado_por, justificacion_colaborador, motivo_respuesta, fecha_solicitud, fecha_respuesta)
VALUES (
    (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com' LIMIT 1),
    (SELECT id FROM curso WHERE nombre = 'React Avanzado' LIMIT 1),
    'SOLICITUD_COLABORADOR', 'RECHAZADO',
    (SELECT id FROM usuario WHERE correo = 'rm@skillbridge.com' LIMIT 1),
    'No estoy usando React en mi proyecto actual, prefiero enfocar el presupuesto de capacitación en otra habilidad.',
    'Por ahora no aplica a tu proyecto asignado. Podemos revisarlo si cambias de proyecto.',
    NOW() - INTERVAL 15 DAY, NOW() - INTERVAL 13 DAY
);

-- Inscripción asignada DIRECTAMENTE por el RM (sin que el colaborador la pidiera)
-- Sirve para comprobar que en "Mis solicitudes de curso" NO aparezca
-- (porque el origen es ASIGNADO_POR_RM, no SOLICITUD_COLABORADOR),
-- pero sí debería bloquear el botón "Solicitar" de ese curso si estuviera EN_CURSO.
-- (Se deja comentado porque usa "Spring Security Avanzado", que en el caso base
--  queda libre para probar el flujo de solicitud desde cero. Descomenta si
--  quieres probar este escenario en vez de dejarlo libre.)
-- INSERT IGNORE INTO colaborador_curso (colaborador_id, curso_id, origen, estado, asignado_por, fecha_solicitud, fecha_respuesta)
-- VALUES (
--     (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com' LIMIT 1),
--     (SELECT id FROM curso WHERE nombre = 'Spring Security Avanzado' LIMIT 1),
--     'ASIGNADO_POR_RM', 'EN_CURSO',
--     (SELECT id FROM usuario WHERE correo = 'rm@skillbridge.com' LIMIT 1),
--     NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 5 DAY
-- );
