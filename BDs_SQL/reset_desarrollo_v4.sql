-- =====================================================================
-- SkillBridge AI — Reinicio de desarrollo v4 (uso OPCIONAL)
-- =====================================================================
-- Ejecutar este archivo BORRA TODAS LAS TABLAS Y SUS DATOS.
-- Úsalo solo en tu entorno local cuando quieras empezar desde cero.
-- Después de correrlo, vuelve a ejecutar skillbridge_db_v4.sql.
-- NUNCA lo corras contra una base con datos reales/de producción.
-- =====================================================================

USE skillbridge_db;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS log_auditoria, configuracion_sistema, notificacion,
    mensaje, conversacion_usuario, conversacion, voto_respuesta, voto_publicacion,
    respuesta_foro, publicacion_foro, foro, etiqueta,
    nomina_mensual, penalizacion, colaborador_curso, curso, actividad,
    asignacion, proyecto_habilidad_requerida, proyecto, certificado,
    colaborador_habilidad, habilidad, categoria_habilidad,
    experiencia_profesional, token_usuario, usuario, rol;

SET FOREIGN_KEY_CHECKS = 1;
