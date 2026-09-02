-- =====================================================================
-- SkillBridge AI — Reinicio de desarrollo (uso OPCIONAL)
-- =====================================================================
-- Ejecutar este archivo BORRA TODAS LAS TABLAS Y SUS DATOS.
-- Úsalo solo en tu entorno local de desarrollo cuando quieras empezar
-- desde cero. Después de correrlo, vuelve a ejecutar el schema
-- principal (skillbridge_db_v2.sql) para recrear las tablas vacías.
--
-- NUNCA corras esto contra una base con datos reales/de producción.
-- =====================================================================

USE skillbridge_db;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS log_auditoria, configuracion_sistema, notificacion,
    mensaje, conversacion_usuario, conversacion, voto_respuesta, voto_publicacion,
    respuesta_foro, publicacion_etiqueta, etiqueta, publicacion_foro, foro,
    nomina_mensual, penalizacion, colaborador_curso, curso, actividad,
    asignacion, proyecto_habilidad_requerida, proyecto, certificado,
    colaborador_habilidad, habilidad, categoria_habilidad, disponibilidad,
    experiencia_profesional, colaborador, token_recuperacion_password,
    token_activacion, usuario;

SET FOREIGN_KEY_CHECKS = 1;
