-- ============================================================================
-- SkillBridge AI - Seed del catálogo base de habilidades
--
-- Para una base de datos que YA existe: carga las 14 habilidades base sin borrar
-- nada. Una instalación nueva no lo necesita, porque skillbridge_db_v4.sql ya las
-- incluye.
--
-- Se puede ejecutar más de una vez: no inserta una habilidad si ya existe otra con
-- el mismo nombre en cualquier categoría (misma regla que aplica el Administrador).
-- Requiere las categorías base (Técnico, Herramientas, Habilidades blandas) creadas
-- por skillbridge_db_v4.sql.
-- ============================================================================

USE skillbridge_db;

INSERT INTO habilidad (nombre, categoria_id)
SELECT base.nombre, c.id
FROM (
    SELECT 'Java'        AS nombre, 'Técnico'             AS categoria UNION ALL
    SELECT 'Python',      'Técnico'             UNION ALL
    SELECT 'JavaScript',  'Técnico'             UNION ALL
    SELECT 'Spring Boot', 'Técnico'             UNION ALL
    SELECT 'React',       'Técnico'             UNION ALL
    SELECT 'Angular',     'Técnico'             UNION ALL
    SELECT 'MySQL',       'Técnico'             UNION ALL
    SELECT 'PostgreSQL',  'Técnico'             UNION ALL
    SELECT 'MongoDB',     'Técnico'             UNION ALL
    SELECT 'AWS',         'Técnico'             UNION ALL
    SELECT 'GCP',         'Técnico'             UNION ALL
    SELECT 'Docker',      'Herramientas'        UNION ALL
    SELECT 'Git',         'Herramientas'        UNION ALL
    SELECT 'Scrum',       'Habilidades blandas'
) AS base
JOIN categoria_habilidad c ON c.nombre = base.categoria
WHERE NOT EXISTS (SELECT 1 FROM habilidad h WHERE h.nombre = base.nombre);
