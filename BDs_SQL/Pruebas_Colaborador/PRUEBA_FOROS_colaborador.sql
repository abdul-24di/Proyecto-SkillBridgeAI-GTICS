-- =====================================================================
-- CASOS DE PRUEBA PARA EL MÓDULO DE FOROS
-- Usuario principal de pruebas: col@skillbridge.com
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0) Usuario adicional: un "otro colaborador" sin relación con col@skillbridge.com
--    Sirve para probar que col NO puede editar/eliminar contenido ajeno,
--    y para ser dueño de proyectos donde col no tiene asignación.
-- ---------------------------------------------------------------------
INSERT INTO usuario (correo, password_hash, nombre, apellido, rol_id, activo, cargo, horas_disponibles)
SELECT 'colaborador2@skillbridge.com',
       '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom',
       'Ana', 'Torres',
       (SELECT id FROM rol WHERE nombre = 'COLABORADOR'),
       1, 'Frontend Developer', 40
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE correo = 'colaborador2@skillbridge.com');

-- ---------------------------------------------------------------------
-- 1) Etiquetas adicionales
-- ---------------------------------------------------------------------
INSERT INTO etiqueta (nombre) SELECT 'Anuncio' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM etiqueta WHERE nombre = 'Anuncio');
INSERT INTO etiqueta (nombre) SELECT 'Bug' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM etiqueta WHERE nombre = 'Bug');

-- ---------------------------------------------------------------------
-- 2) SEGUNDO FORO GENERAL DE COMUNIDAD
--    CASO: la pestaña "Comunidad" debe mostrar más de un foro.
-- ---------------------------------------------------------------------
INSERT INTO foro (proyecto_id, tipo, es_publico, nombre, fecha_creacion)
SELECT NULL, 'GENERAL', TRUE, 'Anuncios Oficiales', CURRENT_TIMESTAMP
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM foro WHERE nombre = 'Anuncios Oficiales');

-- ---------------------------------------------------------------------
-- 3) PROYECTOS DE PRUEBA (todos con PM = pm@skillbridge.com)
-- ---------------------------------------------------------------------

-- 3.1 Proyecto donde col SÍ tiene asignación ACTIVA, foro PRIVADO (ya lo tenías)
--     -> Aparece en "Mis proyectos", NO en "Comunidad", col puede publicar/responder/eliminar lo suyo.

-- 3.2 Proyecto donde col SÍ tiene asignación ACTIVA, foro COMPARTIDO a comunidad
--     -> Aparece en "Mis proyectos" Y en "Comunidad", en ambos casos col puede participar (es miembro).
INSERT INTO proyecto (nombre, descripcion, estado, prioridad, justificacion_prioridad,
                       colaboradores_requeridos, horas_semanales_requeridas, pm_id, fecha_creacion)
SELECT '[DEMO] Proyecto Compartido', 'Proyecto cuyo foro el PM decidió compartir con la comunidad',
       'ACTIVO', 'ALTA', 'Proyecto de alta visibilidad', 2, 20,
       (SELECT id FROM usuario WHERE correo = 'pm@skillbridge.com'), CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM proyecto WHERE nombre = '[DEMO] Proyecto Compartido');

-- 3.3 Proyecto AJENO (col NO tiene asignación), foro COMPARTIDO a comunidad
--     -> Aparece SOLO en "Comunidad", de SOLO LECTURA para col (no ve botones de publicar/responder/eliminar).
INSERT INTO proyecto (nombre, descripcion, estado, prioridad, justificacion_prioridad,
                       colaboradores_requeridos, horas_semanales_requeridas, pm_id, fecha_creacion)
SELECT '[DEMO] Proyecto Ajeno Publico', 'Proyecto de otro equipo, foro visible para toda la comunidad',
       'ACTIVO', 'MEDIA', 'Visibilidad para toda la org', 1, 20,
       (SELECT id FROM usuario WHERE correo = 'pm@skillbridge.com'), CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM proyecto WHERE nombre = '[DEMO] Proyecto Ajeno Publico');

-- 3.4 Proyecto AJENO (col NO tiene asignación), foro PRIVADO
--     -> NO debe aparecer en ninguna pestaña para col, y si intenta entrar por URL con el foroId
--        directo, el servicio debe rechazarlo ("no tienes acceso").
INSERT INTO proyecto (nombre, descripcion, estado, prioridad, justificacion_prioridad,
                       colaboradores_requeridos, horas_semanales_requeridas, pm_id, fecha_creacion)
SELECT '[DEMO] Proyecto Ajeno Privado', 'Proyecto de otro equipo, foro interno y privado',
       'ACTIVO', 'BAJA', 'Proyecto interno', 1, 20,
       (SELECT id FROM usuario WHERE correo = 'pm@skillbridge.com'), CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM proyecto WHERE nombre = '[DEMO] Proyecto Ajeno Privado');

-- 3.5 Proyecto donde col tuvo una asignación pero ya está FINALIZADA
--     -> NO debe aparecer en "Mis proyectos" (ya no es un miembro activo).
INSERT INTO proyecto (nombre, descripcion, estado, prioridad, justificacion_prioridad,
                       colaboradores_requeridos, horas_semanales_requeridas, pm_id, fecha_creacion)
SELECT '[DEMO] Proyecto Finalizado', 'Proyecto donde col ya no participa',
       'FINALIZADO', 'MEDIA', 'Proyecto cerrado', 1, 20,
       (SELECT id FROM usuario WHERE correo = 'pm@skillbridge.com'), CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM proyecto WHERE nombre = '[DEMO] Proyecto Finalizado');

-- 3.6 Proyecto donde col tiene una asignación PENDIENTE (aún sin aprobar)
--     -> Tampoco debe aparecer en "Mis proyectos" (todavía no es ACTIVA).
INSERT INTO proyecto (nombre, descripcion, estado, prioridad, justificacion_prioridad,
                       colaboradores_requeridos, horas_semanales_requeridas, pm_id, fecha_creacion)
SELECT '[DEMO] Proyecto Pendiente', 'Proyecto donde la asignación de col aún no fue aprobada',
       'ACTIVO', 'MEDIA', 'Proyecto en formación de equipo', 2, 20,
       (SELECT id FROM usuario WHERE correo = 'pm@skillbridge.com'), CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM proyecto WHERE nombre = '[DEMO] Proyecto Pendiente');

-- ---------------------------------------------------------------------
-- 4) FOROS de cada proyecto nuevo
-- ---------------------------------------------------------------------
INSERT INTO foro (proyecto_id, tipo, es_publico, nombre, fecha_creacion)
SELECT (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Compartido'),
       'PROYECTO', TRUE, 'Foro — [DEMO] Proyecto Compartido', CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM foro WHERE nombre = 'Foro — [DEMO] Proyecto Compartido');

INSERT INTO foro (proyecto_id, tipo, es_publico, nombre, fecha_creacion)
SELECT (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Ajeno Publico'),
       'PROYECTO', TRUE, 'Foro — [DEMO] Proyecto Ajeno Publico', CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM foro WHERE nombre = 'Foro — [DEMO] Proyecto Ajeno Publico');

INSERT INTO foro (proyecto_id, tipo, es_publico, nombre, fecha_creacion)
SELECT (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Ajeno Privado'),
       'PROYECTO', FALSE, 'Foro — [DEMO] Proyecto Ajeno Privado', CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM foro WHERE nombre = 'Foro — [DEMO] Proyecto Ajeno Privado');

INSERT INTO foro (proyecto_id, tipo, es_publico, nombre, fecha_creacion)
SELECT (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Finalizado'),
       'PROYECTO', FALSE, 'Foro — [DEMO] Proyecto Finalizado', CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM foro WHERE nombre = 'Foro — [DEMO] Proyecto Finalizado');

INSERT INTO foro (proyecto_id, tipo, es_publico, nombre, fecha_creacion)
SELECT (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Pendiente'),
       'PROYECTO', FALSE, 'Foro — [DEMO] Proyecto Pendiente', CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM foro WHERE nombre = 'Foro — [DEMO] Proyecto Pendiente');

-- ---------------------------------------------------------------------
-- 5) ASIGNACIONES de col@skillbridge.com en cada proyecto nuevo
-- ---------------------------------------------------------------------

-- col ACTIVA en Proyecto Compartido
INSERT INTO asignacion (proyecto_id, colaborador_id, horas_semanales, origen, estado,
                         aprobado_por_pm, aprobado_por_rm, fecha_activacion, fecha_solicitud)
SELECT (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Compartido'),
       (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com'),
       15, 'PROPUESTA_PM', 'ACTIVA', TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM asignacion
    WHERE proyecto_id = (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Compartido')
      AND colaborador_id = (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com'));

-- col FINALIZADA en Proyecto Finalizado (ya no cuenta como activa)
INSERT INTO asignacion (proyecto_id, colaborador_id, horas_semanales, origen, estado,
                         aprobado_por_pm, aprobado_por_rm, fecha_activacion, fecha_finalizacion,
                         motivo_finalizacion, fecha_solicitud)
SELECT (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Finalizado'),
       (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com'),
       10, 'PROPUESTA_PM', 'FINALIZADA', TRUE, TRUE,
       CURRENT_TIMESTAMP - INTERVAL 60 DAY, CURRENT_TIMESTAMP - INTERVAL 5 DAY,
       'PROYECTO_CANCELADO', CURRENT_TIMESTAMP - INTERVAL 60 DAY
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM asignacion
    WHERE proyecto_id = (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Finalizado')
      AND colaborador_id = (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com'));

-- col PENDIENTE en Proyecto Pendiente (aún sin aprobación completa)
INSERT INTO asignacion (proyecto_id, colaborador_id, horas_semanales, origen, estado,
                         aprobado_por_pm, aprobado_por_rm, fecha_solicitud)
SELECT (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Pendiente'),
       (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com'),
       10, 'SOLICITADA_COLABORADOR', 'PENDIENTE', TRUE, FALSE, CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM asignacion
    WHERE proyecto_id = (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Pendiente')
      AND colaborador_id = (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com'));

-- colaborador2 ACTIVA en los dos proyectos "ajenos" (para que tengan contenido y dueño)
INSERT INTO asignacion (proyecto_id, colaborador_id, horas_semanales, origen, estado,
                         aprobado_por_pm, aprobado_por_rm, fecha_activacion, fecha_solicitud)
SELECT (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Ajeno Publico'),
       (SELECT id FROM usuario WHERE correo = 'colaborador2@skillbridge.com'),
       20, 'PROPUESTA_PM', 'ACTIVA', TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM asignacion
    WHERE proyecto_id = (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Ajeno Publico')
      AND colaborador_id = (SELECT id FROM usuario WHERE correo = 'colaborador2@skillbridge.com'));

INSERT INTO asignacion (proyecto_id, colaborador_id, horas_semanales, origen, estado,
                         aprobado_por_pm, aprobado_por_rm, fecha_activacion, fecha_solicitud)
SELECT (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Ajeno Privado'),
       (SELECT id FROM usuario WHERE correo = 'colaborador2@skillbridge.com'),
       20, 'PROPUESTA_PM', 'ACTIVA', TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM asignacion
    WHERE proyecto_id = (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Ajeno Privado')
      AND colaborador_id = (SELECT id FROM usuario WHERE correo = 'colaborador2@skillbridge.com'));

-- ---------------------------------------------------------------------
-- 6) PUBLICACIONES adicionales
-- ---------------------------------------------------------------------

-- Más contenido en "Comunidad SkillBridge" (para probar el buscador con varios resultados)
INSERT INTO publicacion_foro (foro_id, autor_id, etiqueta_id, titulo, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM foro WHERE nombre = 'Comunidad SkillBridge'),
       (SELECT id FROM usuario WHERE correo = 'admin@skillbridge.com'),
       (SELECT id FROM etiqueta WHERE nombre = 'Anuncio'),
       'Mantenimiento programado este fin de semana',
       '<p>El sistema estará en mantenimiento el sábado de 10pm a 12am.</p>',
       CURRENT_TIMESTAMP - INTERVAL 2 DAY, TRUE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM publicacion_foro WHERE titulo = 'Mantenimiento programado este fin de semana');

INSERT INTO publicacion_foro (foro_id, autor_id, etiqueta_id, titulo, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM foro WHERE nombre = 'Comunidad SkillBridge'),
       (SELECT id FROM usuario WHERE correo = 'colaborador2@skillbridge.com'),
       NULL,
       'Recomendaciones de librerías para testing',
       '<p>¿Qué usan para tests de integración en Spring Boot, Testcontainers o H2?</p>',
       CURRENT_TIMESTAMP - INTERVAL 1 DAY, TRUE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM publicacion_foro WHERE titulo = 'Recomendaciones de librerías para testing');

-- CASO: publicación eliminada lógicamente (activo = FALSE) -> NO debe aparecer en el listado
INSERT INTO publicacion_foro (foro_id, autor_id, etiqueta_id, titulo, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM foro WHERE nombre = 'Comunidad SkillBridge'),
       (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com'),
       NULL,
       'Publicación de prueba (debe estar oculta)',
       '<p>Si ves este texto en la lista de publicaciones, el borrado lógico no está filtrando bien.</p>',
       CURRENT_TIMESTAMP - INTERVAL 3 DAY, FALSE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM publicacion_foro WHERE titulo = 'Publicación de prueba (debe estar oculta)');

-- Publicación del PM en el foro general (Anuncios Oficiales)
INSERT INTO publicacion_foro (foro_id, autor_id, etiqueta_id, titulo, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM foro WHERE nombre = 'Anuncios Oficiales'),
       (SELECT id FROM usuario WHERE correo = 'pm@skillbridge.com'),
       (SELECT id FROM etiqueta WHERE nombre = 'Anuncio'),
       'Nueva política de code review',
       '<p>A partir de este sprint, todo PR requiere al menos 2 aprobaciones.</p>',
       CURRENT_TIMESTAMP, TRUE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM publicacion_foro WHERE titulo = 'Nueva política de code review');

-- Publicación de col en su proyecto privado, sin respuestas todavía (estado vacío)
INSERT INTO publicacion_foro (foro_id, autor_id, etiqueta_id, titulo, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM foro WHERE nombre = 'Foro — [DEMO] Proyecto Foro'),
       (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com'),
       (SELECT id FROM etiqueta WHERE nombre = 'Bug'),
       'Bug en el login con Google',
       '<p>Al iniciar sesión con Google a veces no redirige bien al dashboard. ¿Alguien más lo vio?</p>',
       CURRENT_TIMESTAMP, TRUE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM publicacion_foro WHERE titulo = 'Bug en el login con Google');

-- Dos publicaciones en el proyecto COMPARTIDO (col es miembro, así que puede escribir aquí,
-- se vea desde "Mis proyectos" o desde "Comunidad")
INSERT INTO publicacion_foro (foro_id, autor_id, etiqueta_id, titulo, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM foro WHERE nombre = 'Foro — [DEMO] Proyecto Compartido'),
       (SELECT id FROM usuario WHERE correo = 'pm@skillbridge.com'),
       (SELECT id FROM etiqueta WHERE nombre = 'Anuncio'),
       'Bienvenidos al foro abierto del proyecto',
       '<p>Este foro es visible para toda la organización, pero solo el equipo puede publicar.</p>',
       CURRENT_TIMESTAMP - INTERVAL 4 DAY, TRUE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM publicacion_foro WHERE titulo = 'Bienvenidos al foro abierto del proyecto');

INSERT INTO publicacion_foro (foro_id, autor_id, etiqueta_id, titulo, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM foro WHERE nombre = 'Foro — [DEMO] Proyecto Compartido'),
       (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com'),
       NULL,
       'Avance de la sprint actual',
       '<p>Ya terminé mis tareas del sprint, quedan pendientes las de QA.</p>',
       CURRENT_TIMESTAMP - INTERVAL 1 DAY, TRUE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM publicacion_foro WHERE titulo = 'Avance de la sprint actual');

-- CASO: publicación en un proyecto AJENO PÚBLICO -> col debe poder LEERLA pero no
-- ver botones de Responder/Eliminar/Like habilitados para escribir (solo lectura)
INSERT INTO publicacion_foro (foro_id, autor_id, etiqueta_id, titulo, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM foro WHERE nombre = 'Foro — [DEMO] Proyecto Ajeno Publico'),
       (SELECT id FROM usuario WHERE correo = 'colaborador2@skillbridge.com'),
       NULL,
       'Demo pública de nuestro proyecto',
       '<p>Compartimos este avance con toda la comunidad para recibir feedback.</p>',
       CURRENT_TIMESTAMP, TRUE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM publicacion_foro WHERE titulo = 'Demo pública de nuestro proyecto');

-- CASO: publicación en un proyecto AJENO PRIVADO -> col NO debe poder verla ni acceder al foro
INSERT INTO publicacion_foro (foro_id, autor_id, etiqueta_id, titulo, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM foro WHERE nombre = 'Foro — [DEMO] Proyecto Ajeno Privado'),
       (SELECT id FROM usuario WHERE correo = 'colaborador2@skillbridge.com'),
       NULL,
       'Discusión interna del equipo',
       '<p>Este contenido es privado del equipo, col no debería poder verlo.</p>',
       CURRENT_TIMESTAMP, TRUE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM publicacion_foro WHERE titulo = 'Discusión interna del equipo');

-- ---------------------------------------------------------------------
-- 7) RESPUESTAS adicionales (incluye una eliminada lógicamente)
-- ---------------------------------------------------------------------

INSERT INTO respuesta_foro (publicacion_id, autor_id, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM publicacion_foro WHERE titulo = 'Recomendaciones de librerías para testing'),
       (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com'),
       '<p>Yo uso Testcontainers, se siente más real que H2 en memoria.</p>',
       CURRENT_TIMESTAMP, TRUE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM respuesta_foro WHERE contenido LIKE '%se siente más real que H2%');

INSERT INTO respuesta_foro (publicacion_id, autor_id, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM publicacion_foro WHERE titulo = 'Recomendaciones de librerías para testing'),
       (SELECT id FROM usuario WHERE correo = 'rm@skillbridge.com'),
       '<p>Depende del proyecto, para cosas rápidas H2 basta.</p>',
       CURRENT_TIMESTAMP, TRUE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM respuesta_foro WHERE contenido LIKE '%para cosas rápidas H2 basta%');

-- CASO: respuesta eliminada lógicamente -> no debe listarse ni contar en "X respuestas"
INSERT INTO respuesta_foro (publicacion_id, autor_id, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM publicacion_foro WHERE titulo = 'Recomendaciones de librerías para testing'),
       (SELECT id FROM usuario WHERE correo = 'admin@skillbridge.com'),
       '<p>Respuesta de prueba (debe estar oculta).</p>',
       CURRENT_TIMESTAMP, FALSE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM respuesta_foro WHERE contenido LIKE '%Respuesta de prueba (debe estar oculta)%');

-- Respuesta del PM a la publicación del bug de col
INSERT INTO respuesta_foro (publicacion_id, autor_id, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM publicacion_foro WHERE titulo = 'Bug en el login con Google'),
       (SELECT id FROM usuario WHERE correo = 'pm@skillbridge.com'),
       '<p>Sí, ya lo reporté. Al parecer es el redirect_uri mal configurado.</p>',
       CURRENT_TIMESTAMP, TRUE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM respuesta_foro WHERE contenido LIKE '%redirect_uri mal configurado%');

-- Respuesta a la demo pública (colaborador2 respondiendo su propia publicación)
INSERT INTO respuesta_foro (publicacion_id, autor_id, contenido, fecha_creacion, activo)
SELECT (SELECT id FROM publicacion_foro WHERE titulo = 'Demo pública de nuestro proyecto'),
       (SELECT id FROM usuario WHERE correo = 'colaborador2@skillbridge.com'),
       '<p>Gracias por el feedback a quienes ya comentaron.</p>',
       CURRENT_TIMESTAMP, TRUE
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM respuesta_foro WHERE contenido LIKE '%Gracias por el feedback a quienes%');

-- ---------------------------------------------------------------------
-- 8) LIKES (voto_publicacion / voto_respuesta) — para probar contador y toggle
-- ---------------------------------------------------------------------

-- col ya le dio like a esta publicación -> al cargar la página debe verse el corazón "activado"
INSERT INTO voto_publicacion (usuario_id, publicacion_id, tipo)
SELECT (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com'),
       (SELECT id FROM publicacion_foro WHERE titulo = '¿Cómo configuran su entorno de Spring Boot?'),
       'POSITIVO'
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM voto_publicacion
    WHERE usuario_id = (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com')
      AND publicacion_id = (SELECT id FROM publicacion_foro WHERE titulo = '¿Cómo configuran su entorno de Spring Boot?'));

-- Otros usuarios también le dieron like a la misma publicación -> contador debe ser > 1
INSERT INTO voto_publicacion (usuario_id, publicacion_id, tipo)
SELECT (SELECT id FROM usuario WHERE correo = 'admin@skillbridge.com'),
       (SELECT id FROM publicacion_foro WHERE titulo = '¿Cómo configuran su entorno de Spring Boot?'),
       'POSITIVO'
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM voto_publicacion
    WHERE usuario_id = (SELECT id FROM usuario WHERE correo = 'admin@skillbridge.com')
      AND publicacion_id = (SELECT id FROM publicacion_foro WHERE titulo = '¿Cómo configuran su entorno de Spring Boot?'));

INSERT INTO voto_publicacion (usuario_id, publicacion_id, tipo)
SELECT (SELECT id FROM usuario WHERE correo = 'rm@skillbridge.com'),
       (SELECT id FROM publicacion_foro WHERE titulo = '¿Cómo configuran su entorno de Spring Boot?'),
       'POSITIVO'
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM voto_publicacion
    WHERE usuario_id = (SELECT id FROM usuario WHERE correo = 'rm@skillbridge.com')
      AND publicacion_id = (SELECT id FROM publicacion_foro WHERE titulo = '¿Cómo configuran su entorno de Spring Boot?'));

-- Esta publicación tiene likes de OTROS pero col todavía NO le dio like -> corazón debe verse "apagado"
INSERT INTO voto_publicacion (usuario_id, publicacion_id, tipo)
SELECT (SELECT id FROM usuario WHERE correo = 'pm@skillbridge.com'),
       (SELECT id FROM publicacion_foro WHERE titulo = 'Nueva política de code review'),
       'POSITIVO'
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM voto_publicacion
    WHERE usuario_id = (SELECT id FROM usuario WHERE correo = 'pm@skillbridge.com')
      AND publicacion_id = (SELECT id FROM publicacion_foro WHERE titulo = 'Nueva política de code review'));

-- Like en una respuesta (para probar el corazón dentro de las respuestas también)
INSERT INTO voto_respuesta (usuario_id, respuesta_id, tipo)
SELECT (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com'),
       (SELECT id FROM respuesta_foro WHERE contenido LIKE '%para cosas rápidas H2 basta%'),
       'POSITIVO'
FROM DUAL WHERE NOT EXISTS (
    SELECT 1 FROM voto_respuesta
    WHERE usuario_id = (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com')
      AND respuesta_id = (SELECT id FROM respuesta_foro WHERE contenido LIKE '%para cosas rápidas H2 basta%'));

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