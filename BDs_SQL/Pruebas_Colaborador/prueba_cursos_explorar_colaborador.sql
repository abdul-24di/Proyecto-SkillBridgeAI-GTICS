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
INSERT INTO curso (nombre, descripcion, categoria, horas, activo, creado_por) VALUES
('Spring Security Avanzado',
 'Aprende a proteger APIs REST con autenticación y autorización robustas, JWT y roles.',
 'Técnico', 12, TRUE,
 (SELECT id FROM usuario WHERE correo = 'admin@skillbridge.com')),

('Comunicación Efectiva en Equipos Ágiles',
 'Mejora la comunicación dentro de equipos multidisciplinarios y ágiles, feedback y manejo de conflictos.',
 'Habilidades blandas', 6, TRUE,
 (SELECT id FROM usuario WHERE correo = 'admin@skillbridge.com')),

('AWS Certified Developer — Preparación',
 'Preparación guiada para la certificación AWS enfocada en desarrolladores: Lambda, S3, DynamoDB.',
 'Certificación', 20, TRUE,
 (SELECT id FROM usuario WHERE correo = 'admin@skillbridge.com')),

('React Avanzado',
 'Patrones avanzados, rendimiento y testing en aplicaciones React modernas.',
 'Técnico', 10, TRUE,
 (SELECT id FROM usuario WHERE correo = 'admin@skillbridge.com')),

('Curso Descontinuado (no debe aparecer)',
 'Este curso está desactivado y no debería verse en el catálogo del colaborador.',
 'Técnico', 8, FALSE,
 (SELECT id FROM usuario WHERE correo = 'admin@skillbridge.com'));

-- ---------------------------------------------------------------------------
-- 2) SOLICITUDES / INSCRIPCIONES DE EJEMPLO para el colaborador de prueba
--    (col@skillbridge.com) — cubre los distintos estados y badges:
--    - Spring Security Avanzado        -> sin ninguna solicitud (botón "Solicitar inscripción")
--    - Comunicación Efectiva           -> SOLICITADO   (badge "Solicitud pendiente", sin botón)
--    - AWS Certified Developer         -> EN_CURSO     (badge "Inscrito", sin botón)
--    - React Avanzado                  -> RECHAZADO    (badge "Solicitud rechazada" + botón "Volver a solicitar")
-- ---------------------------------------------------------------------------

-- Pendiente de revisión por el RM (con justificación del colaborador)
INSERT INTO colaborador_curso (colaborador_id, curso_id, origen, estado, justificacion_colaborador, fecha_solicitud)
VALUES (
    (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com'),
    (SELECT id FROM curso WHERE nombre = 'Comunicación Efectiva en Equipos Ágiles'),
    'SOLICITUD_COLABORADOR', 'SOLICITADO',
    'Quiero mejorar cómo doy feedback a mi equipo, últimamente hemos tenido roces por malos entendidos.',
    NOW() - INTERVAL 2 DAY
);

-- Ya aprobada por el RM (en curso)
INSERT INTO colaborador_curso (colaborador_id, curso_id, origen, estado, asignado_por, justificacion_colaborador, fecha_solicitud, fecha_respuesta)
VALUES (
    (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com'),
    (SELECT id FROM curso WHERE nombre = 'AWS Certified Developer — Preparación'),
    'SOLICITUD_COLABORADOR', 'EN_CURSO',
    (SELECT id FROM usuario WHERE correo = 'rm@skillbridge.com'),
    'Me gustaría certificarme para apoyar mejor al proyecto que usa infraestructura AWS.',
    NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 8 DAY
);

-- Rechazada por el RM (para probar el botón "Volver a solicitar")
INSERT INTO colaborador_curso (colaborador_id, curso_id, origen, estado, asignado_por, justificacion_colaborador, motivo_respuesta, fecha_solicitud, fecha_respuesta)
VALUES (
    (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com'),
    (SELECT id FROM curso WHERE nombre = 'React Avanzado'),
    'SOLICITUD_COLABORADOR', 'RECHAZADO',
    (SELECT id FROM usuario WHERE correo = 'rm@skillbridge.com'),
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
-- INSERT INTO colaborador_curso (colaborador_id, curso_id, origen, estado, asignado_por, fecha_solicitud, fecha_respuesta)
-- VALUES (
--     (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com'),
--     (SELECT id FROM curso WHERE nombre = 'Spring Security Avanzado'),
--     'ASIGNADO_POR_RM', 'EN_CURSO',
--     (SELECT id FROM usuario WHERE correo = 'rm@skillbridge.com'),
--     NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 5 DAY
-- );