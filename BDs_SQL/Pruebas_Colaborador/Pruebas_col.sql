-- =====================================================================
-- 0. ACTUALIZAR A CARLOS (col@skillbridge.com) CON SUELDO Y NIVEL
--    (ya existe, solo le completamos los datos que faltaban)
-- =====================================================================
SET SQL_SAFE_UPDATES = 0;
UPDATE usuario
SET nivel_experiencia = 'SEMI_SENIOR',
    sueldo_base = 3500.00,
    anios_experiencia = 3.5,
    horas_contratadas_semana = 40,
    fecha_contratacion = DATE_SUB(CURDATE(), INTERVAL 2 YEAR),
    descripcion = 'Backend Developer enfocado en Java y Spring Boot, con experiencia en microservicios.'
WHERE correo = 'col@skillbridge.com';

-- =====================================================================
-- 1. MÁS COLABORADORES (para "ver colaboradores" / Explorar / Talent Matching)
-- =====================================================================
INSERT INTO usuario (correo, password_hash, nombre, apellido, rol_id, activo, cargo_id,
                      horas_disponibles, horas_contratadas_semana, anios_experiencia,
                      sueldo_base, nivel_experiencia, fecha_contratacion, descripcion) VALUES
('ana.torres@skillbridge.com', '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom', 'Ana', 'Torres',
 (SELECT id FROM rol WHERE nombre='COLABORADOR'), 1,
 (SELECT id FROM cargo WHERE nombre='Frontend Developer'),
 20, 40, 2.0, 3200.00, 'SEMI_SENIOR', DATE_SUB(CURDATE(), INTERVAL 1 YEAR),
 'Frontend developer especializada en React y diseño de interfaces accesibles.'),

('luis.fernandez@skillbridge.com', '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom', 'Luis', 'Fernandez',
 (SELECT id FROM rol WHERE nombre='COLABORADOR'), 1,
 (SELECT id FROM cargo WHERE nombre='UX Designer'),
 40, 40, 1.0, 1900.00, 'JUNIOR', DATE_SUB(CURDATE(), INTERVAL 6 MONTH),
 'Diseñador UX junior, recién incorporado al equipo.'),

('maria.rodriguez@skillbridge.com', '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom', 'Maria', 'Rodriguez',
 (SELECT id FROM rol WHERE nombre='COLABORADOR'), 1,
 (SELECT id FROM cargo WHERE nombre='DevOps Engineer'),
 0, 40, 5.0, 6000.00, 'SENIOR', DATE_SUB(CURDATE(), INTERVAL 4 YEAR),
 'DevOps senior, actualmente sin horas disponibles por estar en dos proyectos.');

-- =====================================================================
-- 2. HABILIDADES (catálogo)
-- =====================================================================
INSERT INTO habilidad (nombre, categoria_id, activa) VALUES
('Java', (SELECT id FROM categoria_habilidad WHERE nombre='Técnico'), 1),
('Spring Boot', (SELECT id FROM categoria_habilidad WHERE nombre='Técnico'), 1),
('React', (SELECT id FROM categoria_habilidad WHERE nombre='Técnico'), 1),
('AWS', (SELECT id FROM categoria_habilidad WHERE nombre='Técnico'), 1),
('Comunicación efectiva', (SELECT id FROM categoria_habilidad WHERE nombre='Habilidades blandas'), 1),
('Liderazgo', (SELECT id FROM categoria_habilidad WHERE nombre='Habilidades blandas'), 1),
('Scrum Master Certified', (SELECT id FROM categoria_habilidad WHERE nombre='Certificación'), 1),
('Figma', (SELECT id FROM categoria_habilidad WHERE nombre='Herramientas'), 1),
('Jira', (SELECT id FROM categoria_habilidad WHERE nombre='Herramientas'), 1);

-- =====================================================================
-- 3. HABILIDADES DE CADA COLABORADOR
-- =====================================================================
INSERT INTO colaborador_habilidad (colaborador_id, habilidad_id, nivel_dominio, estado_validacion, activo) VALUES
((SELECT id FROM usuario WHERE correo='col@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='Java'), 'AVANZADO', 'VALIDADA', 1),
((SELECT id FROM usuario WHERE correo='col@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='Spring Boot'), 'INTERMEDIO', 'VALIDADA', 1),
((SELECT id FROM usuario WHERE correo='col@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='Comunicación efectiva'), 'BASICO', 'PENDIENTE', 1),

((SELECT id FROM usuario WHERE correo='ana.torres@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='React'), 'INTERMEDIO', 'VALIDADA', 1),
((SELECT id FROM usuario WHERE correo='ana.torres@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='Figma'), 'AVANZADO', 'VALIDADA', 1),

((SELECT id FROM usuario WHERE correo='luis.fernandez@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='Figma'), 'AVANZADO', 'VALIDADA', 1),

((SELECT id FROM usuario WHERE correo='maria.rodriguez@skillbridge.com'), (SELECT id FROM habilidad WHERE nombre='AWS'), 'AVANZADO', 'VALIDADA', 1);

-- Un certificado pendiente de revisión para el RM, ligado a la habilidad pendiente de Carlos
INSERT INTO certificado (colaborador_id, habilidad_id, archivo_url, estado) VALUES
((SELECT id FROM usuario WHERE correo='col@skillbridge.com'),
 (SELECT id FROM habilidad WHERE nombre='Comunicación efectiva'),
 'https://skillbridge-ia-archivos.s3.us-east-1.amazonaws.com/certificados/demo-comunicacion.pdf',
 'PENDIENTE');

-- =====================================================================
-- 4. EXPERIENCIA Y EDUCACIÓN DE CARLOS (las ingresa el Admin)
-- =====================================================================
INSERT INTO experiencia_profesional (colaborador_id, empresa, cargo, descripcion, fecha_inicio, fecha_fin, actual) VALUES
((SELECT id FROM usuario WHERE correo='col@skillbridge.com'),
 'TechCorp SAC', 'Backend Developer Jr.',
 'Desarrollo de APIs REST y mantenimiento de microservicios en Java.',
 DATE_SUB(CURDATE(), INTERVAL 3 YEAR), DATE_SUB(CURDATE(), INTERVAL 2 YEAR), 0);

INSERT INTO educacion (colaborador_id, institucion, titulo, fecha_inicio, fecha_fin, actual, estado, revisado_por) VALUES
((SELECT id FROM usuario WHERE correo='col@skillbridge.com'),
 'Universidad Nacional de Ingeniería', 'Ingeniería de Software',
 DATE_SUB(CURDATE(), INTERVAL 6 YEAR), DATE_SUB(CURDATE(), INTERVAL 1 YEAR), 0,
 'APROBADO', (SELECT id FROM usuario WHERE correo='rm@skillbridge.com')),
((SELECT id FROM usuario WHERE correo='col@skillbridge.com'),
 'Platzi', 'Certificación en Spring Boot',
 DATE_SUB(CURDATE(), INTERVAL 2 MONTH), NULL, 1,
 'PENDIENTE', NULL);

-- =====================================================================
-- 5. PROYECTOS
-- =====================================================================
INSERT INTO proyecto (nombre, descripcion, fecha_inicio, fecha_fin_estimada, estado, prioridad,
                       justificacion_prioridad, presupuesto, colaboradores_requeridos,
                       horas_semanales_requeridas, pm_id, rm_revisor_id) VALUES
('Portal de Clientes',
 'Portal web para que los clientes consulten sus pedidos y facturas.',
 DATE_SUB(CURDATE(), INTERVAL 2 MONTH), DATE_ADD(CURDATE(), INTERVAL 2 MONTH),
 'ACTIVO', 'ALTA', 'Cliente estratégico con entrega comprometida este trimestre.',
 45000.00, 3, 20,
 (SELECT id FROM usuario WHERE correo='pm@skillbridge.com'),
 (SELECT id FROM usuario WHERE correo='rm@skillbridge.com')),

('App Móvil Interna',
 'Aplicación móvil para que los colaboradores registren asistencia desde el campo.',
 DATE_SUB(CURDATE(), INTERVAL 5 DAY), DATE_ADD(CURDATE(), INTERVAL 3 MONTH),
 'ACTIVO', 'MEDIA', 'Mejora interna de procesos, sin fecha límite externa.',
 20000.00, 2, 15,
 (SELECT id FROM usuario WHERE correo='pm@skillbridge.com'),
 (SELECT id FROM usuario WHERE correo='rm@skillbridge.com')),

('Sistema Legacy',
 'Mantenimiento del sistema contable antiguo, ya reemplazado.',
 DATE_SUB(CURDATE(), INTERVAL 1 YEAR), DATE_SUB(CURDATE(), INTERVAL 1 MONTH),
 'FINALIZADO', 'BAJA', 'Proyecto cerrado tras la migración al nuevo sistema.',
 15000.00, 1, 20,
 (SELECT id FROM usuario WHERE correo='pm@skillbridge.com'),
 (SELECT id FROM usuario WHERE correo='rm@skillbridge.com'));

INSERT INTO proyecto_habilidad_requerida (proyecto_id, habilidad_id, nivel_requerido, cantidad_personas, horas_semanales) VALUES
((SELECT id FROM proyecto WHERE nombre='Portal de Clientes'), (SELECT id FROM habilidad WHERE nombre='Java'), 'INTERMEDIO', 2, 20),
((SELECT id FROM proyecto WHERE nombre='Portal de Clientes'), (SELECT id FROM habilidad WHERE nombre='React'), 'INTERMEDIO', 1, 20),
((SELECT id FROM proyecto WHERE nombre='App Móvil Interna'), (SELECT id FROM habilidad WHERE nombre='React'), 'BASICO', 2, 15);

-- =====================================================================
-- 6. ASIGNACIONES
-- =====================================================================

-- Carlos, ACTIVA en Portal de Clientes (su proyecto principal de pruebas)
INSERT INTO asignacion (proyecto_id, colaborador_id, horas_semanales, habilidades_relevantes, origen,
                         estado, aprobado_por_pm, aprobado_por_rm, fecha_aprobacion_pm, fecha_aprobacion_rm,
                         fecha_solicitud, fecha_activacion) VALUES
((SELECT id FROM proyecto WHERE nombre='Portal de Clientes'),
 (SELECT id FROM usuario WHERE correo='col@skillbridge.com'),
 20, 'Java, Spring Boot', 'PROPUESTA_PM',
 'ACTIVA', 1, 1, DATE_SUB(NOW(), INTERVAL 2 MONTH), DATE_SUB(NOW(), INTERVAL 2 MONTH),
 DATE_SUB(NOW(), INTERVAL 2 MONTH), DATE_SUB(NOW(), INTERVAL 2 MONTH));

-- Carlos, FINALIZADA en Sistema Legacy (para su historial de proyectos)
INSERT INTO asignacion (proyecto_id, colaborador_id, horas_semanales, origen, estado,
                         aprobado_por_pm, aprobado_por_rm, motivo_finalizacion,
                         fecha_solicitud, fecha_activacion, fecha_finalizacion) VALUES
((SELECT id FROM proyecto WHERE nombre='Sistema Legacy'),
 (SELECT id FROM usuario WHERE correo='col@skillbridge.com'),
 20, 'PROPUESTA_PM', 'FINALIZADA', 1, 1, 'OTRO',
 DATE_SUB(NOW(), INTERVAL 1 YEAR), DATE_SUB(NOW(), INTERVAL 1 YEAR), DATE_SUB(NOW(), INTERVAL 1 MONTH));

-- Ana solicita unirse a Portal de Clientes (PENDIENTE) -> para que RM/PM prueben aprobar/rechazar
INSERT INTO asignacion (proyecto_id, colaborador_id, horas_semanales, origen, mensaje_solicitud,
                         habilidad_solicitada_id, estado, fecha_solicitud) VALUES
((SELECT id FROM proyecto WHERE nombre='Portal de Clientes'),
 (SELECT id FROM usuario WHERE correo='ana.torres@skillbridge.com'),
 15, 'SOLICITADA_COLABORADOR', 'Me interesa sumarme por el frontend del portal.',
 (SELECT id FROM habilidad WHERE nombre='React'), 'PENDIENTE', DATE_SUB(NOW(), INTERVAL 1 DAY));

-- (Ojo: "App Móvil Interna" queda sin nadie asignado a propósito, para que Carlos
--  pruebe en vivo el flujo de Explorar > Proyectos > Postularme.)

-- =====================================================================
-- 7. ACTIVIDADES DE CARLOS EN "Portal de Clientes"
-- =====================================================================

-- Completadas este mes (suman ~175h -> activan el bono por horas extra)
INSERT INTO actividad (proyecto_id, colaborador_id, titulo, descripcion, horas_estimadas,
                        fecha_limite, fecha_entrega, estado_entrega, estado, creado_por) VALUES
((SELECT id FROM proyecto WHERE nombre='Portal de Clientes'),
 (SELECT id FROM usuario WHERE correo='col@skillbridge.com'),
 'Diseñar esquema de base de datos', 'Modelado de tablas para pedidos y facturas.', 60,
 DATE_SUB(CURDATE(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY), 'A_TIEMPO', 'COMPLETADA',
 (SELECT id FROM usuario WHERE correo='pm@skillbridge.com')),

((SELECT id FROM proyecto WHERE nombre='Portal de Clientes'),
 (SELECT id FROM usuario WHERE correo='col@skillbridge.com'),
 'Implementar API de autenticación', 'Login, JWT y manejo de roles.', 70,
 DATE_SUB(CURDATE(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), 'A_TIEMPO', 'COMPLETADA',
 (SELECT id FROM usuario WHERE correo='pm@skillbridge.com')),

((SELECT id FROM proyecto WHERE nombre='Portal de Clientes'),
 (SELECT id FROM usuario WHERE correo='col@skillbridge.com'),
 'Escribir pruebas unitarias del módulo de pagos', 'Cobertura de los casos críticos de pago.', 45,
 DATE_SUB(CURDATE(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 'A_TIEMPO', 'COMPLETADA',
 (SELECT id FROM usuario WHERE correo='pm@skillbridge.com')),

-- Vencidas y aún "Pendiente" -> al entrar Carlos a su Dashboard, el sistema le aplica
-- los strikes solo (revisarVencidasSinEntregar). 3 strikes este mes = 5% de descuento.
((SELECT id FROM proyecto WHERE nombre='Portal de Clientes'),
 (SELECT id FROM usuario WHERE correo='col@skillbridge.com'),
 'Documentar endpoints de la API', 'Swagger/OpenAPI de los endpoints nuevos.', 10,
 DATE_SUB(CURDATE(), INTERVAL 2 DAY), NULL, NULL, 'PENDIENTE',
 (SELECT id FROM usuario WHERE correo='pm@skillbridge.com')),

((SELECT id FROM proyecto WHERE nombre='Portal de Clientes'),
 (SELECT id FROM usuario WHERE correo='col@skillbridge.com'),
 'Optimizar consultas SQL lentas', 'Revisar índices en las tablas de pedidos.', 8,
 DATE_SUB(CURDATE(), INTERVAL 1 DAY), NULL, NULL, 'PENDIENTE',
 (SELECT id FROM usuario WHERE correo='pm@skillbridge.com')),

((SELECT id FROM proyecto WHERE nombre='Portal de Clientes'),
 (SELECT id FROM usuario WHERE correo='col@skillbridge.com'),
 'Revisar logs de errores en producción', 'Identificar la causa de los 500 reportados.', 6,
 CURDATE(), NULL, NULL, 'PENDIENTE',
 (SELECT id FROM usuario WHERE correo='pm@skillbridge.com')),

-- En progreso: para que Carlos pruebe "marcar listo para revisar"
((SELECT id FROM proyecto WHERE nombre='Portal de Clientes'),
 (SELECT id FROM usuario WHERE correo='col@skillbridge.com'),
 'Preparar demo para el cliente', 'Ambiente de staging listo para mostrar avances.', 12,
 DATE_ADD(CURDATE(), INTERVAL 5 DAY), NULL, NULL, 'EN_PROGRESO',
 (SELECT id FROM usuario WHERE correo='pm@skillbridge.com')),

-- En revisión: para que el PM pruebe "confirmar/devolver"
((SELECT id FROM proyecto WHERE nombre='Portal de Clientes'),
 (SELECT id FROM usuario WHERE correo='col@skillbridge.com'),
 'Actualizar dependencias del proyecto', 'Subir versiones de Spring Boot y librerías.', 8,
 DATE_ADD(CURDATE(), INTERVAL 3 DAY), NULL, NULL, 'EN_REVISION',
 (SELECT id FROM usuario WHERE correo='pm@skillbridge.com'));

UPDATE actividad SET fecha_marcado_revision = NOW()
WHERE titulo = 'Actualizar dependencias del proyecto';

-- =====================================================================
-- 8. CURSOS (catálogo del Admin)
-- =====================================================================
INSERT INTO curso (nombre, descripcion, categoria, modalidad, autodidacta, dias,
                    fecha_inicio, fecha_fin, lugar, institucion, horas, activo, creado_por) VALUES

-- Autodidacta: pide evidencia (flujo completo con RM)
('Fundamentos de AWS', 'Curso introductorio de servicios en la nube de AWS.',
 'Certificación', 'VIRTUAL', 1, NULL, NULL, NULL, NULL, NULL, 15, 1,
 (SELECT id FROM usuario WHERE correo='admin@skillbridge.com')),

-- No autodidacta, YA TERMINADO (fecha_fin en el pasado) -> se autocompleta al entrar
('Scrum Avanzado', 'Taller presencial de metodologías ágiles.',
 'Técnico', 'PRESENCIAL', 0, 'Lunes y Miércoles',
 DATE_SUB(CURDATE(), INTERVAL 20 DAY), DATE_SUB(CURDATE(), INTERVAL 5 DAY),
 'Oficina central, Lima', 'Scrum Perú', 10, 1,
 (SELECT id FROM usuario WHERE correo='admin@skillbridge.com')),

-- No autodidacta, TODAVÍA EN CURSO (fecha_fin futura) -> muestra el aviso de autocompletado
('Liderazgo de Equipos', 'Programa de liderazgo para futuros leads técnicos.',
 'Habilidades blandas', 'VIRTUAL', 0, 'Martes',
 CURDATE(), DATE_ADD(CURDATE(), INTERVAL 30 DAY),
 NULL, 'SkillBridge Academy', 12, 1,
 (SELECT id FROM usuario WHERE correo='admin@skillbridge.com')),

-- Autodidacta, disponible en el catálogo para que Carlos lo explore y solicite en vivo
('Certificación Kubernetes', 'Preparación para la certificación CKA.',
 'Técnico', 'VIRTUAL', 1, NULL, NULL, NULL, NULL, NULL, 25, 1,
 (SELECT id FROM usuario WHERE correo='admin@skillbridge.com')),

-- Inactivo: para confirmar que NO aparece en el catálogo de Explorar
('Curso Descontinuado', 'Ya no se dicta.', 'Técnico', 'VIRTUAL', 1, NULL, NULL, NULL, NULL, NULL, 5, 0,
 (SELECT id FROM usuario WHERE correo='admin@skillbridge.com'));

-- =====================================================================
-- 9. INSCRIPCIONES DE CURSO DE CARLOS
-- =====================================================================

-- Autodidacta con evidencia YA SUBIDA y pendiente de que el RM la revise
INSERT INTO colaborador_curso (colaborador_id, curso_id, origen, estado, asignado_por,
                                motivo_respuesta, fecha_solicitud, fecha_respuesta,
                                evidencia_url, fecha_evidencia) VALUES
((SELECT id FROM usuario WHERE correo='col@skillbridge.com'),
 (SELECT id FROM curso WHERE nombre='Fundamentos de AWS'),
 'SOLICITUD_COLABORADOR', 'EVIDENCIA_PENDIENTE',
 (SELECT id FROM usuario WHERE correo='rm@skillbridge.com'),
 'Aprobado, adelante.',
 DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY),
 'https://skillbridge-ia-archivos.s3.us-east-1.amazonaws.com/certificados/demo-evidencia-aws.pdf',
 DATE_SUB(NOW(), INTERVAL 1 DAY));

-- No autodidacta YA VENCIDO: queda "En curso" a propósito, para que se autocomplete
-- en vivo la primera vez que Carlos entre a su Perfil o Dashboard
INSERT INTO colaborador_curso (colaborador_id, curso_id, origen, estado, asignado_por,
                                motivo_respuesta, fecha_solicitud, fecha_respuesta) VALUES
((SELECT id FROM usuario WHERE correo='col@skillbridge.com'),
 (SELECT id FROM curso WHERE nombre='Scrum Avanzado'),
 'ASIGNADO_POR_RM', 'EN_CURSO',
 (SELECT id FROM usuario WHERE correo='rm@skillbridge.com'),
 'Te asignamos este curso por bajo cumplimiento de horas el mes pasado.',
 DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY));

-- No autodidacta, todavía en curso con fecha fin futura
INSERT INTO colaborador_curso (colaborador_id, curso_id, origen, estado, asignado_por,
                                motivo_respuesta, fecha_solicitud, fecha_respuesta) VALUES
((SELECT id FROM usuario WHERE correo='col@skillbridge.com'),
 (SELECT id FROM curso WHERE nombre='Liderazgo de Equipos'),
 'SOLICITUD_COLABORADOR', 'EN_CURSO',
 (SELECT id FROM usuario WHERE correo='rm@skillbridge.com'),
 'Aprobado.',
 DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY));

-- Solicitud pendiente de que el RM la apruebe/rechace (para probar Explorar > Cursos en vivo
-- y la bandeja del RM con una solicitud real esperando)
INSERT INTO colaborador_curso (colaborador_id, curso_id, origen, estado,
                                justificacion_colaborador, fecha_solicitud) VALUES
((SELECT id FROM usuario WHERE correo='col@skillbridge.com'),
 (SELECT id FROM curso WHERE nombre='Certificación Kubernetes'),
 'SOLICITUD_COLABORADOR', 'SOLICITADO',
 'Quiero certificarme en Kubernetes para apoyar mejor el proyecto actual.',
 DATE_SUB(NOW(), INTERVAL 2 HOUR));

-- Un rechazo histórico de otra colaboradora, para que el RM vea variedad en su bandeja
INSERT INTO colaborador_curso (colaborador_id, curso_id, origen, estado, asignado_por,
                                motivo_respuesta, fecha_solicitud, fecha_respuesta) VALUES
((SELECT id FROM usuario WHERE correo='ana.torres@skillbridge.com'),
 (SELECT id FROM curso WHERE nombre='Fundamentos de AWS'),
 'SOLICITUD_COLABORADOR', 'RECHAZADO',
 (SELECT id FROM usuario WHERE correo='rm@skillbridge.com'),
 'Ya hay cupo lleno este trimestre para este curso.',
 DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_SUB(NOW(), INTERVAL 14 DAY));

-- =====================================================================
-- 10. NOTIFICACIONES DE CARLOS (para probar el clic -> redirección)
-- =====================================================================
INSERT INTO notificacion (usuario_id, tipo, categoria, titulo, descripcion, entidad, entidad_id, leida) VALUES
((SELECT id FROM usuario WHERE correo='col@skillbridge.com'),
 'CURSO_APROBADO', 'CURSO', 'Solicitud de curso aprobada',
 'Tu solicitud para "Fundamentos de AWS" fue aprobada.',
 'COLABORADOR_CURSO',
 (SELECT id FROM colaborador_curso WHERE colaborador_id = (SELECT id FROM usuario WHERE correo='col@skillbridge.com')
    AND curso_id = (SELECT id FROM curso WHERE nombre='Fundamentos de AWS')),
 0);

-- =====================================================================
-- 11. EVALUACIÓN DE DESEMPEÑO (para la tarjeta "Feedback y Evaluaciones")
-- =====================================================================
INSERT INTO evaluacion (colaborador_id, evaluador_id, asignacion_id, calificacion, comentarios, fecha_creacion) VALUES
((SELECT id FROM usuario WHERE correo='col@skillbridge.com'),
 (SELECT id FROM usuario WHERE correo='pm@skillbridge.com'),
 (SELECT id FROM asignacion WHERE proyecto_id = (SELECT id FROM proyecto WHERE nombre='Sistema Legacy')
    AND colaborador_id = (SELECT id FROM usuario WHERE correo='col@skillbridge.com')),
 5, 'Excelente desempeño durante todo el proyecto, cumplió todos los plazos.',
 DATE_SUB(NOW(), INTERVAL 1 MONTH));