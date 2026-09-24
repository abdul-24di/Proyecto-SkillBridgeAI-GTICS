-- =====================================================================
-- SkillBridge AI — Migración: catálogo de cargos (usuario.cargo -> usuario.cargo_id)
-- =====================================================================
-- Ejecutar UNA SOLA VEZ en una base creada con una versión anterior de
-- skillbridge_db_v4.sql (donde usuario.cargo era VARCHAR). Una instalación
-- nueva con el skillbridge_db_v4.sql actual NO necesita esta migración.
--
-- Qué hace:
--   1. Crea la tabla cargo y carga los cargos base con sus tarifas.
--   2. Registra como cargo (sin tarifas) cualquier texto de cargo que ya
--      tuvieran los usuarios, para no perder información.
--   3. Agrega usuario.cargo_id, lo llena según el texto anterior y elimina
--      la columna vieja usuario.cargo.
-- Las tarifas de los cargos creados en el paso 2 se completan luego desde
-- Admin > Habilidades > Cargos y Matriz Salarial.
-- =====================================================================

USE skillbridge_db;

CREATE TABLE IF NOT EXISTS cargo (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre              VARCHAR(100) NOT NULL UNIQUE,
    sueldo_junior       DECIMAL(10,2) NULL,
    sueldo_semi_senior  DECIMAL(10,2) NULL,
    sueldo_senior       DECIMAL(10,2) NULL,
    activo              BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

INSERT IGNORE INTO cargo (nombre, sueldo_junior, sueldo_semi_senior, sueldo_senior) VALUES
    ('Backend Developer', 2000.00, 3500.00, 5000.00),
    ('Frontend Developer', 1800.00, 3200.00, 4800.00),
    ('UX Designer', 1900.00, 3300.00, 4900.00),
    ('DevOps Engineer', 2500.00, 4000.00, 6000.00);

INSERT IGNORE INTO cargo (nombre)
SELECT DISTINCT TRIM(cargo)
FROM usuario
WHERE cargo IS NOT NULL AND TRIM(cargo) <> '';

ALTER TABLE usuario ADD COLUMN cargo_id BIGINT NULL AFTER fecha_creacion;

SET SQL_SAFE_UPDATES = 0;
UPDATE usuario u
JOIN cargo c ON c.nombre = TRIM(u.cargo)
SET u.cargo_id = c.id;
SET SQL_SAFE_UPDATES = 1;

ALTER TABLE usuario
    DROP COLUMN cargo,
    ADD CONSTRAINT fk_usuario_cargo FOREIGN KEY (cargo_id) REFERENCES cargo(id);
