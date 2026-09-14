-- ============================================================================
-- SkillBridge AI - Migracion: modelo de solicitudes de personal
-- Ejecutar UNA VEZ sobre una BD existente creada antes de este cambio.
-- Las instalaciones nuevas solo necesitan ejecutar skillbridge_db_v4.sql.
-- ============================================================================

USE skillbridge_db;

CREATE TABLE IF NOT EXISTS solicitud_personal (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    proyecto_id              BIGINT NOT NULL,
    cantidad_colaboradores   INT NOT NULL,
    perfiles_requeridos      VARCHAR(1000) NULL,
    mensaje_pm               VARCHAR(1000) NULL,
    estado                   VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    rm_responsable_id        BIGINT NULL,
    fecha_solicitud          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_inicio_atencion    DATETIME NULL,
    fecha_atencion           DATETIME NULL,

    CONSTRAINT fk_solpersonal_proyecto
        FOREIGN KEY (proyecto_id) REFERENCES proyecto(id),
    CONSTRAINT fk_solpersonal_rm
        FOREIGN KEY (rm_responsable_id) REFERENCES usuario(id),
    CONSTRAINT chk_solpersonal_cantidad
        CHECK (cantidad_colaboradores > 0),
    CONSTRAINT chk_solpersonal_estado
        CHECK (estado IN ('PENDIENTE','EN_ATENCION','ATENDIDA','CANCELADA')),

    INDEX idx_solpersonal_estado_fecha (estado, fecha_solicitud),
    INDEX idx_solpersonal_proyecto (proyecto_id)
) ENGINE=InnoDB;

SELECT 'Migracion solicitud_personal aplicada correctamente.' AS resultado;
