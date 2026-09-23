USE skillbridge_db;

-- =====================================================================
-- COLABORADORES ADICIONALES (contraseña para todos: abc123, igual que Carlos)
-- =====================================================================

INSERT INTO usuario (correo, password_hash, nombre, apellido, rol_id, activo, cargo, descripcion, horas_disponibles, anios_experiencia, nivel_experiencia, fecha_contratacion) VALUES
('maria.lopez@skillbridge.com', '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom', 'María', 'López', (SELECT id FROM rol WHERE nombre='COLABORADOR'), 1, 'UX/UI Designer', 'Diseñadora UX/UI enfocada en investigación de usuarios y prototipado rápido.', 15.00, 3.0, 'SEMI_SENIOR', '2023-03-01'),
('mallory.hulme@skillbridge.com', '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom', 'Mallory', 'Hulme', (SELECT id FROM rol WHERE nombre='COLABORADOR'), 1, 'Frontend Developer', 'Desarrolladora frontend enfocada en interfaces accesibles y de alto rendimiento.', 40.00, 2.0, 'JUNIOR', '2024-06-15'),
('dunn.slane@skillbridge.com', '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom', 'Dunn', 'Slane', (SELECT id FROM rol WHERE nombre='COLABORADOR'), 1, 'UI/UX Designer', 'Diseñador UI/UX enfocado en sistemas de diseño y research.', 0.00, 4.0, 'SENIOR', '2022-01-10');

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

INSERT INTO categoria_habilidad (nombre, descripcion, activa) VALUES
('Backend', 'Lenguajes y frameworks del lado del servidor', 1),
('Frontend', 'Lenguajes y frameworks del lado del cliente', 1),
('Base de Datos', 'Motores y herramientas de bases de datos', 1),
('DevOps & Cloud', 'Infraestructura, contenedores y nube', 1),
('Diseño', 'Diseño de producto e interfaces', 1),
('Habilidades Blandas', 'Comunicación, liderazgo y trabajo en equipo', 1);

INSERT INTO habilidad (nombre, categoria_id, activa) VALUES
('Java', (SELECT id FROM categoria_habilidad WHERE nombre='Backend'), 1),
('Spring Boot', (SELECT id FROM categoria_habilidad WHERE nombre='Backend'), 1),
('Node.js', (SELECT id FROM categoria_habilidad WHERE nombre='Backend'), 1),
('React', (SELECT id FROM categoria_habilidad WHERE nombre='Frontend'), 1),
('JavaScript', (SELECT id FROM categoria_habilidad WHERE nombre='Frontend'), 1),
('CSS', (SELECT id FROM categoria_habilidad WHERE nombre='Frontend'), 1),
('MySQL', (SELECT id FROM categoria_habilidad WHERE nombre='Base de Datos'), 1),
('PostgreSQL', (SELECT id FROM categoria_habilidad WHERE nombre='Base de Datos'), 1),
('Docker', (SELECT id FROM categoria_habilidad WHERE nombre='DevOps & Cloud'), 1),
('AWS', (SELECT id FROM categoria_habilidad WHERE nombre='DevOps & Cloud'), 1),
('Figma', (SELECT id FROM categoria_habilidad WHERE nombre='Diseño'), 1),
('UX Research', (SELECT id FROM categoria_habilidad WHERE nombre='Diseño'), 1),
('Comunicación Efectiva', (SELECT id FROM categoria_habilidad WHERE nombre='Habilidades Blandas'), 1);


-- =====================================================================
-- HABILIDADES DE CADA COLABORADOR (mezcla de estados a propósito:
-- VALIDADA para que aparezcan en Explorar, PENDIENTE y RECHAZADA para
-- probar los badges y el botón "Volver a subir certificado")
-- =====================================================================

INSERT INTO colaborador_habilidad (colaborador_id, habilidad_id, nivel_dominio, estado_validacion, activo) VALUES
((SELECT id FROM usuario WHERE correo='col@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='Java'), 'AVANZADO', 'VALIDADA', 1),
((SELECT id FROM usuario WHERE correo='col@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='Spring Boot'), 'AVANZADO', 'VALIDADA', 1),
((SELECT id FROM usuario WHERE correo='col@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='MySQL'), 'INTERMEDIO', 'PENDIENTE', 1),
((SELECT id FROM usuario WHERE correo='col@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='Docker'), 'BASICO', 'RECHAZADA', 1),

((SELECT id FROM usuario WHERE correo='maria.lopez@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='Figma'), 'AVANZADO', 'VALIDADA', 1),
((SELECT id FROM usuario WHERE correo='maria.lopez@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='UX Research'), 'INTERMEDIO', 'VALIDADA', 1),

((SELECT id FROM usuario WHERE correo='mallory.hulme@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='React'), 'AVANZADO', 'VALIDADA', 1),
((SELECT id FROM usuario WHERE correo='mallory.hulme@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='JavaScript'), 'AVANZADO', 'VALIDADA', 1),
((SELECT id FROM usuario WHERE correo='mallory.hulme@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='CSS'), 'INTERMEDIO', 'PENDIENTE', 1),

((SELECT id FROM usuario WHERE correo='dunn.slane@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='Figma'), 'AVANZADO', 'VALIDADA', 1),
((SELECT id FROM usuario WHERE correo='dunn.slane@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='Comunicación Efectiva'), 'INTERMEDIO', 'VALIDADA', 1);


-- =====================================================================
-- CERTIFICADOS (uno por cada fila de arriba, con el estado que le corresponde)
-- =====================================================================

INSERT INTO certificado (colaborador_id, habilidad_id, archivo_url, estado, motivo_rechazo, revisado_por, fecha_revision) VALUES
((SELECT id FROM usuario WHERE correo='col@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='Java'), '/uploads/certificados/demo-java.pdf', 'APROBADO', NULL, (SELECT id FROM usuario WHERE correo='rm@skillbridge.com'), NOW()),
((SELECT id FROM usuario WHERE correo='col@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='Spring Boot'), '/uploads/certificados/demo-spring.pdf', 'APROBADO', NULL, (SELECT id FROM usuario WHERE correo='rm@skillbridge.com'), NOW()),
((SELECT id FROM usuario WHERE correo='col@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='MySQL'), '/uploads/certificados/demo-mysql.pdf', 'PENDIENTE', NULL, NULL, NULL),
((SELECT id FROM usuario WHERE correo='col@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='Docker'), '/uploads/certificados/demo-docker.pdf', 'RECHAZADO', 'El certificado no acredita el nivel declarado.', (SELECT id FROM usuario WHERE correo='rm@skillbridge.com'), NOW()),

((SELECT id FROM usuario WHERE correo='maria.lopez@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='Figma'), '/uploads/certificados/demo-figma.pdf', 'APROBADO', NULL, (SELECT id FROM usuario WHERE correo='rm@skillbridge.com'), NOW()),
((SELECT id FROM usuario WHERE correo='maria.lopez@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='UX Research'), '/uploads/certificados/demo-uxresearch.pdf', 'APROBADO', NULL, (SELECT id FROM usuario WHERE correo='rm@skillbridge.com'), NOW()),

((SELECT id FROM usuario WHERE correo='mallory.hulme@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='React'), '/uploads/certificados/demo-react.pdf', 'APROBADO', NULL, (SELECT id FROM usuario WHERE correo='rm@skillbridge.com'), NOW()),
((SELECT id FROM usuario WHERE correo='mallory.hulme@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='JavaScript'), '/uploads/certificados/demo-js.pdf', 'APROBADO', NULL, (SELECT id FROM usuario WHERE correo='rm@skillbridge.com'), NOW()),
((SELECT id FROM usuario WHERE correo='mallory.hulme@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='CSS'), '/uploads/certificados/demo-css.pdf', 'PENDIENTE', NULL, NULL, NULL),

((SELECT id FROM usuario WHERE correo='dunn.slane@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='Figma'), '/uploads/certificados/demo-figma2.pdf', 'APROBADO', NULL, (SELECT id FROM usuario WHERE correo='rm@skillbridge.com'), NOW()),
((SELECT id FROM usuario WHERE correo='dunn.slane@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='Comunicación Efectiva'), '/uploads/certificados/demo-comm.pdf', 'APROBADO', NULL, (SELECT id FROM usuario WHERE correo='rm@skillbridge.com'), NOW());


-- =====================================================================
-- EDUCACIÓN (ya con archivo_url, como lo dejamos)
-- =====================================================================

INSERT INTO educacion (colaborador_id, institucion, titulo, archivo_url, fecha_inicio, fecha_fin, actual, estado) VALUES
((SELECT id FROM usuario WHERE correo='col@skillbridge.com'), 'Universidad Nacional de Ingeniería', 'Ingeniería de Sistemas', '/uploads/certificados-educacion/demo-carlos.pdf', '2016-03-01', '2021-12-15', 0, 'PENDIENTE'),
((SELECT id FROM usuario WHERE correo='maria.lopez@skillbridge.com'), 'Universidad Z', 'Diseño Gráfico', '/uploads/certificados-educacion/demo-maria.pdf', '2017-03-01', '2022-12-15', 0, 'PENDIENTE'),
((SELECT id FROM usuario WHERE correo='mallory.hulme@skillbridge.com'), 'Universidad Y', 'Ingeniería de Sistemas', '/uploads/certificados-educacion/demo-mallory.pdf', '2020-03-01', NULL, 1, 'PENDIENTE'),
((SELECT id FROM usuario WHERE correo='dunn.slane@skillbridge.com'), 'Universidad Z', 'Diseño Gráfico', '/uploads/certificados-educacion/demo-dunn.pdf', '2015-03-01', '2019-12-15', 0, 'PENDIENTE');


-- =====================================================================
-- EXPERIENCIA PROFESIONAL
-- =====================================================================

INSERT INTO experiencia_profesional (colaborador_id, empresa, cargo, descripcion, fecha_inicio, fecha_fin, actual) VALUES
((SELECT id FROM usuario WHERE correo='col@skillbridge.com'), 'Tech Solutions SAC', 'Backend Developer', 'Desarrollo e integración de APIs REST con Spring Boot y MySQL.', '2019-01-01', '2021-04-30', 0),
((SELECT id FROM usuario WHERE correo='maria.lopez@skillbridge.com'), 'Estudio Creativo', 'UX/UI Designer', 'Diseño de flujos y research con usuarios finales.', '2021-01-01', NULL, 1),
((SELECT id FROM usuario WHERE correo='mallory.hulme@skillbridge.com'), 'Web Studio', 'Frontend Developer', 'Construcción de interfaces con React y TypeScript.', '2022-06-01', NULL, 1),
((SELECT id FROM usuario WHERE correo='dunn.slane@skillbridge.com'), 'Design Co.', 'UI/UX Designer', 'Diseño de sistemas de componentes y research de usuarios.', '2018-01-01', '2021-12-31', 0);


-- =====================================================================
-- PROYECTOS (uno por cada estado, para probar los filtros de Explorar)
-- =====================================================================

INSERT INTO proyecto (nombre, descripcion, fecha_inicio, fecha_fin_estimada, estado, prioridad, justificacion_prioridad, presupuesto, colaboradores_requeridos, horas_semanales_requeridas, pm_id, rm_revisor_id) VALUES
('Portal de Clientes', 'Plataforma web para que los clientes gestionen sus pedidos y facturación en línea.', '2026-08-01', '2026-12-15', 'ACTIVO', 'ALTA', 'Cliente estratégico con contrato multianual.', 45000.00, 3, 20, (SELECT id FROM usuario WHERE correo='pm@skillbridge.com'), (SELECT id FROM usuario WHERE correo='rm@skillbridge.com')),
('App de Delivery Interno', 'Aplicación móvil para coordinar entregas entre almacenes de la empresa.', '2026-09-01', '2027-02-28', 'ACTIVO', 'MEDIA', 'Mejora la eficiencia logística interna.', 30000.00, 2, 20, (SELECT id FROM usuario WHERE correo='pm@skillbridge.com'), (SELECT id FROM usuario WHERE correo='rm@skillbridge.com')),
('Rediseño de Marca', 'Actualización de la identidad visual y sistema de diseño de la organización.', NULL, NULL, 'EN_REVISION', 'BAJA', 'Iniciativa de marketing sin fecha comprometida todavía.', NULL, 1, 15, (SELECT id FROM usuario WHERE correo='pm@skillbridge.com'), NULL),
('Migración a la Nube', 'Migración de la infraestructura on-premise a AWS.', '2026-05-01', '2026-08-30', 'EN_ESPERA', 'MEDIA', 'Pausado por priorización de presupuesto.', 60000.00, 2, 20, (SELECT id FROM usuario WHERE correo='pm@skillbridge.com'), (SELECT id FROM usuario WHERE correo='rm@skillbridge.com')),
('Sitio Web Corporativo v1', 'Primera versión del sitio web institucional.', '2025-02-01', '2025-06-30', 'FINALIZADO', 'MEDIA', 'Proyecto ya entregado y en producción.', 20000.00, 2, 20, (SELECT id FROM usuario WHERE correo='pm@skillbridge.com'), (SELECT id FROM usuario WHERE correo='rm@skillbridge.com')),
('Sistema de Inventario Legacy', 'Reemplazo del sistema de inventario antiguo.', '2025-09-01', '2025-11-01', 'CANCELADO', 'BAJA', 'Se canceló por cambio de prioridades del área.', NULL, 2, 20, (SELECT id FROM usuario WHERE correo='pm@skillbridge.com'), (SELECT id FROM usuario WHERE correo='rm@skillbridge.com'));


-- =====================================================================
-- HABILIDADES REQUERIDAS POR PROYECTO
-- =====================================================================

INSERT INTO proyecto_habilidad_requerida (proyecto_id, habilidad_id, nivel_requerido, cantidad_personas) VALUES
((SELECT id FROM proyecto WHERE nombre='Portal de Clientes'), (SELECT id FROM habilidad WHERE nombre='Java'), 'AVANZADO', 1),
((SELECT id FROM proyecto WHERE nombre='Portal de Clientes'), (SELECT id FROM habilidad WHERE nombre='Spring Boot'), 'AVANZADO', 1),
((SELECT id FROM proyecto WHERE nombre='Portal de Clientes'), (SELECT id FROM habilidad WHERE nombre='MySQL'), 'INTERMEDIO', 1),

((SELECT id FROM proyecto WHERE nombre='App de Delivery Interno'), (SELECT id FROM habilidad WHERE nombre='React'), 'AVANZADO', 1),
((SELECT id FROM proyecto WHERE nombre='App de Delivery Interno'), (SELECT id FROM habilidad WHERE nombre='JavaScript'), 'INTERMEDIO', 1),

((SELECT id FROM proyecto WHERE nombre='Rediseño de Marca'), (SELECT id FROM habilidad WHERE nombre='Figma'), 'AVANZADO', 1),

((SELECT id FROM proyecto WHERE nombre='Migración a la Nube'), (SELECT id FROM habilidad WHERE nombre='Docker'), 'AVANZADO', 1),
((SELECT id FROM proyecto WHERE nombre='Migración a la Nube'), (SELECT id FROM habilidad WHERE nombre='AWS'), 'AVANZADO', 1),

((SELECT id FROM proyecto WHERE nombre='Sitio Web Corporativo v1'), (SELECT id FROM habilidad WHERE nombre='React'), 'INTERMEDIO', 1),
((SELECT id FROM proyecto WHERE nombre='Sitio Web Corporativo v1'), (SELECT id FROM habilidad WHERE nombre='CSS'), 'BASICO', 1);


-- =====================================================================
-- ASIGNACIONES (para probar "Mis solicitudes", "Proyectos destacados"
-- del perfil público, y el badge "Ya te postulaste")
-- =====================================================================

-- Carlos activo en Portal de Clientes, María activa en App de Delivery
INSERT INTO asignacion (proyecto_id, colaborador_id, horas_semanales, origen, estado, aprobado_por_pm, aprobado_por_rm, fecha_aprobacion_pm, fecha_aprobacion_rm, fecha_activacion) VALUES
((SELECT id FROM proyecto WHERE nombre='Portal de Clientes'), (SELECT id FROM usuario WHERE correo='col@skillbridge.com'), 20, 'PROPUESTA_PM', 'ACTIVA', 1, 1, NOW(), NOW(), NOW()),
((SELECT id FROM proyecto WHERE nombre='App de Delivery Interno'), (SELECT id FROM usuario WHERE correo='maria.lopez@skillbridge.com'), 20, 'PROPUESTA_PM', 'ACTIVA', 1, 1, NOW(), NOW(), NOW());

-- Mallory con una solicitud pendiente en Portal de Clientes
INSERT INTO asignacion (proyecto_id, colaborador_id, horas_semanales, origen, mensaje_solicitud, estado) VALUES
((SELECT id FROM proyecto WHERE nombre='Portal de Clientes'), (SELECT id FROM usuario WHERE correo='mallory.hulme@skillbridge.com'), 20, 'SOLICITADA_COLABORADOR', 'Me interesa este proyecto porque puedo aportar en el frontend.', 'PENDIENTE');

-- Carlos con un proyecto finalizado en su historial
INSERT INTO asignacion (proyecto_id, colaborador_id, horas_semanales, origen, estado, aprobado_por_pm, aprobado_por_rm, fecha_aprobacion_pm, fecha_aprobacion_rm, fecha_activacion, fecha_finalizacion, motivo_finalizacion) VALUES
((SELECT id FROM proyecto WHERE nombre='Sitio Web Corporativo v1'), (SELECT id FROM usuario WHERE correo='col@skillbridge.com'), 20, 'PROPUESTA_PM', 'FINALIZADA', 1, 1, '2025-02-05', '2025-02-05', '2025-02-05', '2025-06-30', 'OTRO');

-- Dunn queda sin ninguna asignación a propósito, para probar el estado "sin proyectos destacados"