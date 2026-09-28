SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS skillbridge_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE skillbridge_db;



-- =====================================================================
-- 1. ROL 
-- =====================================================================

CREATE TABLE rol (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre  VARCHAR(30) NOT NULL UNIQUE
) ENGINE=InnoDB;

INSERT INTO rol (nombre) VALUES
    ('ADMINISTRADOR'),
    ('PROJECT_MANAGER'),
    ('RESOURCE_MANAGER'),
    ('COLABORADOR');

-- =====================================================================
-- 1.5 CARGO (catálogo del Admin + matriz salarial por nivel)
-- usuario.sueldo_base = tarifa del cargo según usuario.nivel_experiencia.
-- =====================================================================
CREATE TABLE cargo (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre              VARCHAR(100) NOT NULL UNIQUE,
    sueldo_junior       DECIMAL(10,2) NULL,
    sueldo_semi_senior  DECIMAL(10,2) NULL,
    sueldo_senior       DECIMAL(10,2) NULL,
    activo              BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

INSERT INTO cargo (nombre, sueldo_junior, sueldo_semi_senior, sueldo_senior) VALUES
    ('Backend Developer', 2000.00, 3500.00, 5000.00),
    ('Frontend Developer', 1800.00, 3200.00, 4800.00),
    ('UX Designer', 1900.00, 3300.00, 4900.00),
    ('DevOps Engineer', 2500.00, 4000.00, 6000.00);

-- =====================================================================
-- 2. USUARIO
-- =====================================================================


CREATE TABLE usuario (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    correo                      VARCHAR(150)  NOT NULL UNIQUE,
    password_hash               VARCHAR(255)  NULL,
    nombre                      VARCHAR(150)  NULL,
    apellido                    VARCHAR(150)  NULL,
    telefono                    VARCHAR(20)   NULL,
    foto_url                    VARCHAR(500)  NULL,
	descripcion                 VARCHAR(500)  NULL,
    rol_id                      BIGINT        NOT NULL,
    activo                      BOOLEAN       NOT NULL DEFAULT TRUE,
    fecha_creacion              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    
    cargo_id                    BIGINT        NULL,
    horas_contratadas_semana    DECIMAL(5,2)  NULL,
    horas_disponibles           DECIMAL(5,2)  NULL,
    anios_experiencia           DECIMAL(4,1)  NULL,
    sueldo_base                 DECIMAL(10,2) NULL,
    nivel_experiencia           VARCHAR(20)   NULL,
    fecha_contratacion          DATE          NULL,

    CONSTRAINT fk_usuario_rol
        FOREIGN KEY (rol_id) REFERENCES rol(id),

    CONSTRAINT fk_usuario_cargo
        FOREIGN KEY (cargo_id) REFERENCES cargo(id),

    CONSTRAINT chk_usuario_nivel
        CHECK (
            nivel_experiencia IS NULL OR
            nivel_experiencia IN ('JUNIOR','SEMI_SENIOR','SENIOR')
        ),

    CONSTRAINT chk_usuario_horas_disp
        CHECK (horas_disponibles IS NULL OR horas_disponibles >= 0),

    CONSTRAINT chk_usuario_anios_exp
        CHECK (anios_experiencia IS NULL OR anios_experiencia >= 0)
) ENGINE=InnoDB;


-- =====================================================================
-- 3. TOKEN DE USUARIO (unifica activación + recuperación)
-- =====================================================================

-- Contraseña: 123456!

CREATE TABLE token_usuario (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id          BIGINT       NOT NULL,
    tipo                VARCHAR(20)  NOT NULL, -- 'ACTIVACION' | 'RECUPERACION'
    token               VARCHAR(255) NOT NULL UNIQUE,
    fecha_expiracion    DATETIME     NOT NULL,
    usado               BOOLEAN      NOT NULL DEFAULT FALSE,
    fecha_creacion      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_token_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario(id),

    CONSTRAINT chk_token_tipo
        CHECK (tipo IN ('ACTIVACION','RECUPERACION'))
) ENGINE=InnoDB;


-- =====================================================================
-- 4. EXPERIENCIA PROFESIONAL (historial de otras empresas/proyectos)
-- =====================================================================

CREATE TABLE experiencia_profesional (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    colaborador_id  BIGINT       NOT NULL,
    empresa         VARCHAR(150) NULL,
    cargo           VARCHAR(100) NULL,
    descripcion     VARCHAR(500) NOT NULL,
    fecha_inicio    DATE NULL,
    fecha_fin       DATE NULL,
    actual          BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_experiencia_colaborador
        FOREIGN KEY (colaborador_id) REFERENCES usuario(id)
) ENGINE=InnoDB;


-- =====================================================================
-- 5. HABILIDADES
-- =====================================================================

CREATE TABLE categoria_habilidad (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre      VARCHAR(80) NOT NULL UNIQUE,
    descripcion VARCHAR(300) NULL,
    activa      BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

-- Categorías fijas del catálogo (igual que `rol`, no se crean desde la UI del
-- Admin — solo se gestionan las habilidades dentro de ellas).
INSERT INTO categoria_habilidad (nombre, descripcion) VALUES
    ('Técnico', 'Lenguajes, frameworks y tecnologías'),
    ('Habilidades blandas', 'Comunicación, liderazgo, trabajo en equipo'),
    ('Certificación', 'Certificaciones profesionales'),
    ('Herramientas', 'Herramientas y software de apoyo');

CREATE TABLE habilidad (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre          VARCHAR(100) NOT NULL,
    categoria_id    BIGINT NOT NULL,
    activa          BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_habilidad_categoria
        FOREIGN KEY (categoria_id) REFERENCES categoria_habilidad(id),

    CONSTRAINT uq_habilidad_nombre_categoria
        UNIQUE (nombre, categoria_id)
) ENGINE=InnoDB;


-- =====================================================================
-- 6. HABILIDADES DEL COLABORADOR
-- =====================================================================

CREATE TABLE colaborador_habilidad (
    colaborador_id      BIGINT NOT NULL,
    habilidad_id        BIGINT NOT NULL,
    nivel_dominio       VARCHAR(20) NOT NULL,
    estado_validacion   VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    activo              BOOLEAN NOT NULL DEFAULT TRUE,

    PRIMARY KEY (colaborador_id, habilidad_id),

    CONSTRAINT fk_colhab_colaborador
        FOREIGN KEY (colaborador_id) REFERENCES usuario(id),

    CONSTRAINT fk_colhab_habilidad
        FOREIGN KEY (habilidad_id) REFERENCES habilidad(id),

    CONSTRAINT chk_colhab_nivel
        CHECK (nivel_dominio IN ('BASICO','INTERMEDIO','AVANZADO')),

    CONSTRAINT chk_colhab_validacion
        CHECK (estado_validacion IN ('PENDIENTE','VALIDADA','RECHAZADA'))
) ENGINE=InnoDB;


-- =====================================================================
-- 7. CERTIFICADOS
-- =====================================================================


CREATE TABLE certificado (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    colaborador_id  BIGINT       NOT NULL,
    habilidad_id    BIGINT       NOT NULL,
    archivo_url     VARCHAR(500) NOT NULL,
    estado          VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    motivo_rechazo  VARCHAR(300) NULL,
    revisado_por    BIGINT       NULL,
    fecha_subida    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_revision  DATETIME     NULL,

    CONSTRAINT fk_certificado_colaborador
        FOREIGN KEY (colaborador_id) REFERENCES usuario(id),

    CONSTRAINT fk_certificado_habilidad
        FOREIGN KEY (habilidad_id) REFERENCES habilidad(id),

    CONSTRAINT fk_certificado_revisor
        FOREIGN KEY (revisado_por) REFERENCES usuario(id),

    CONSTRAINT chk_certificado_estado
        CHECK (estado IN ('PENDIENTE','APROBADO','RECHAZADO'))
) ENGINE=InnoDB;


-- =====================================================================
-- 8. PROYECTOS
-- =====================================================================

CREATE TABLE proyecto (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre                      VARCHAR(150)  NOT NULL,
    descripcion                 VARCHAR(1000) NULL,
    fecha_inicio                DATE NULL,
    fecha_fin_estimada          DATE NULL,
    estado                      VARCHAR(20)   NOT NULL DEFAULT 'EN_REVISION',
    prioridad                   VARCHAR(10)   NOT NULL,
    justificacion_prioridad     VARCHAR(500)  NOT NULL,
    motivo_rechazo              VARCHAR(500)  NULL,
    presupuesto_solicitado      DECIMAL(12,2) NULL,
    justificacion_presupuesto   VARCHAR(500)  NULL,
    presupuesto                 DECIMAL(12,2) NULL,
    colaboradores_requeridos    INT NOT NULL DEFAULT 1,
    horas_semanales_requeridas  DECIMAL(5,2) NOT NULL DEFAULT 20,
    pm_id                       BIGINT NOT NULL,
    rm_revisor_id               BIGINT NULL,
    fecha_creacion              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_proyecto_pm FOREIGN KEY (pm_id) REFERENCES usuario(id),
    CONSTRAINT fk_proyecto_rm FOREIGN KEY (rm_revisor_id) REFERENCES usuario(id),

    CONSTRAINT chk_proyecto_estado
        CHECK (estado IN ('EN_REVISION','RECHAZADO','ACTIVO','EN_ESPERA','CANCELADO','FINALIZADO')),

    CONSTRAINT chk_proyecto_prioridad
        CHECK (prioridad IN ('ALTA','MEDIA','BAJA')),

    CONSTRAINT chk_proyecto_colaboradores
        CHECK (colaboradores_requeridos > 0)
) ENGINE=InnoDB;


-- =====================================================================
-- 9. HABILIDADES REQUERIDAS POR PROYECTO
-- =====================================================================

CREATE TABLE proyecto_habilidad_requerida (
    proyecto_id         BIGINT NOT NULL,
    habilidad_id        BIGINT NOT NULL,
    nivel_requerido     VARCHAR(20) NULL,
    cantidad_personas   INT NOT NULL DEFAULT 1,

    PRIMARY KEY (proyecto_id, habilidad_id),

    CONSTRAINT fk_projhab_proyecto FOREIGN KEY (proyecto_id) REFERENCES proyecto(id),
    CONSTRAINT fk_projhab_habilidad FOREIGN KEY (habilidad_id) REFERENCES habilidad(id),

    CONSTRAINT chk_projhab_nivel
        CHECK (nivel_requerido IS NULL OR nivel_requerido IN ('BASICO','INTERMEDIO','AVANZADO')),

    CONSTRAINT chk_projhab_cantidad
        CHECK (cantidad_personas > 0)
) ENGINE=InnoDB;


-- =====================================================================
-- 10. ASIGNACIONES / POSTULACIONES
-- =====================================================================

CREATE TABLE asignacion (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    proyecto_id         BIGINT NOT NULL,
    colaborador_id      BIGINT NOT NULL,
    horas_semanales     DECIMAL(5,2) NOT NULL,
    habilidades_relevantes VARCHAR(500) NULL,
    origen              VARCHAR(30) NOT NULL,
    mensaje_solicitud   VARCHAR(500) NULL,
    habilidad_solicitada_id BIGINT NULL,
    estado              VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE',
    aprobado_por_pm     BOOLEAN NOT NULL DEFAULT FALSE,
    aprobado_por_rm     BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_aprobacion_pm DATETIME NULL,
    fecha_aprobacion_rm DATETIME NULL,
    rechazado_por       BIGINT NULL,
    desasignado_por     BIGINT NULL,
    motivo_rechazo      VARCHAR(300) NULL,
    motivo_finalizacion VARCHAR(30) NULL,
    fecha_solicitud     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_activacion    DATETIME NULL,
    fecha_finalizacion  DATETIME NULL,

    CONSTRAINT fk_asignacion_proyecto FOREIGN KEY (proyecto_id) REFERENCES proyecto(id),
    CONSTRAINT fk_asignacion_colaborador FOREIGN KEY (colaborador_id) REFERENCES usuario(id),
    CONSTRAINT fk_asignacion_rechazado_por FOREIGN KEY (rechazado_por) REFERENCES usuario(id),
    CONSTRAINT fk_asignacion_desasignado_por FOREIGN KEY (desasignado_por) REFERENCES usuario(id),
    CONSTRAINT fk_asignacion_habilidad_solicitada FOREIGN KEY (habilidad_solicitada_id) REFERENCES habilidad(id),

    CONSTRAINT chk_asignacion_origen
        CHECK (origen IN ('PROPUESTA_PM','PROPUESTA_RM','SOLICITADA_COLABORADOR')),

    CONSTRAINT chk_asignacion_estado
        CHECK (estado IN ('PENDIENTE','ACTIVA','RECHAZADA','FINALIZADA')),

    CONSTRAINT chk_asignacion_horas
        CHECK (horas_semanales > 0),

    CONSTRAINT chk_asignacion_finalizacion
        CHECK (motivo_finalizacion IS NULL OR motivo_finalizacion IN ('BAJO_DESEMPENO','PROYECTO_CANCELADO','OTRO')),

    CONSTRAINT uq_asignacion_proyecto_colaborador_estado
        UNIQUE (proyecto_id, colaborador_id, estado)
) ENGINE=InnoDB;


-- =====================================================================
-- 11. ACTIVIDADES 
-- =====================================================================

CREATE TABLE actividad (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    proyecto_id             BIGINT NOT NULL,
    colaborador_id          BIGINT NOT NULL,
    titulo                  VARCHAR(200) NOT NULL,
    descripcion             VARCHAR(500) NULL,
    horas_estimadas         DECIMAL(6,2) NOT NULL,
    fecha_asignacion        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_limite            DATE NOT NULL,
    fecha_marcado_revision  DATETIME NULL,
    fecha_entrega           DATETIME NULL,
    estado_entrega          VARCHAR(10) NULL,
    veces_devuelta          INT NOT NULL DEFAULT 0,
    comentario_devolucion   VARCHAR(300) NULL,
    evidencia_url           VARCHAR(500) NULL,
    comentario_colaborador  VARCHAR(300) NULL,
    estado                  VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    creado_por              BIGINT NOT NULL,

    CONSTRAINT fk_actividad_proyecto FOREIGN KEY (proyecto_id) REFERENCES proyecto(id),
    CONSTRAINT fk_actividad_colaborador FOREIGN KEY (colaborador_id) REFERENCES usuario(id),
    CONSTRAINT fk_actividad_creador FOREIGN KEY (creado_por) REFERENCES usuario(id),

    CONSTRAINT chk_actividad_horas CHECK (horas_estimadas > 0),

    CONSTRAINT chk_actividad_estado
        CHECK (estado IN ('PENDIENTE','EN_PROGRESO','EN_REVISION','COMPLETADA')),

    CONSTRAINT chk_actividad_entrega
        CHECK (estado_entrega IS NULL OR estado_entrega IN ('A_TIEMPO','TARDIA'))
) ENGINE=InnoDB;


-- =====================================================================
-- 12. CURSOS
-- =====================================================================

CREATE TABLE curso (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre          VARCHAR(150) NOT NULL,
    descripcion     VARCHAR(500) NULL,
    categoria       VARCHAR(50) NULL,
    modalidad       VARCHAR(20) NULL,
    autodidacta     BOOLEAN NOT NULL DEFAULT FALSE,
    dias            VARCHAR(100) NULL,
    fecha_inicio    DATE NULL,
    fecha_fin       DATE NULL,
    lugar 			VARCHAR(300) NULL,
    institucion 	VARCHAR(150) NULL,
    horas           DECIMAL(6,2) NOT NULL,
    activo          BOOLEAN NOT NULL DEFAULT TRUE,
    creado_por      BIGINT NOT NULL,
    fecha_creacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_curso_creador FOREIGN KEY (creado_por) REFERENCES usuario(id),
    CONSTRAINT chk_curso_horas CHECK (horas > 0),
    CONSTRAINT chk_curso_modalidad
        CHECK (modalidad IS NULL OR modalidad IN ('VIRTUAL','PRESENCIAL','HIBRIDO'))
) ENGINE=InnoDB;


-- =====================================================================
-- 13. SOLICITUDES / ASIGNACIONES DE CURSOS
-- =====================================================================

CREATE TABLE colaborador_curso (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    colaborador_id      BIGINT NOT NULL,
    curso_id            BIGINT NOT NULL,
    origen              VARCHAR(25) NOT NULL,
    estado              VARCHAR(20) NOT NULL DEFAULT 'SOLICITADO',
    asignado_por        BIGINT NULL,
    motivo_respuesta            VARCHAR(500) NULL,
    justificacion_colaborador   VARCHAR(500) NULL,
    fecha_solicitud     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_respuesta     DATETIME NULL,
    fecha_completado    DATETIME NULL,

    CONSTRAINT fk_colcurso_colaborador FOREIGN KEY (colaborador_id) REFERENCES usuario(id),
    CONSTRAINT fk_colcurso_curso FOREIGN KEY (curso_id) REFERENCES curso(id),
    CONSTRAINT fk_colcurso_asignador FOREIGN KEY (asignado_por) REFERENCES usuario(id),

    CONSTRAINT chk_colcurso_origen
        CHECK (origen IN ('SOLICITUD_COLABORADOR','ASIGNADO_POR_RM')),

    CONSTRAINT chk_colcurso_estado
        CHECK (estado IN ('SOLICITADO','EN_CURSO','COMPLETADO','RECHAZADO'))
) ENGINE=InnoDB;


-- =====================================================================
-- 14. PENALIZACIONES
-- =====================================================================

CREATE TABLE penalizacion (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    colaborador_id  BIGINT NOT NULL,
    proyecto_id     BIGINT NULL,
    actividad_id    BIGINT NULL,
    tipo            VARCHAR(30) NOT NULL,
    motivo          VARCHAR(500) NOT NULL,
    monto           DECIMAL(10,2) NOT NULL,
    fecha           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_penalizacion_colaborador FOREIGN KEY (colaborador_id) REFERENCES usuario(id),
    CONSTRAINT fk_penalizacion_proyecto FOREIGN KEY (proyecto_id) REFERENCES proyecto(id),
    CONSTRAINT fk_penalizacion_actividad FOREIGN KEY (actividad_id) REFERENCES actividad(id),

    CONSTRAINT chk_penalizacion_tipo
        CHECK (tipo IN ('ACTIVIDAD_TARDIA','SALIDA_PROYECTO','OTRO')),

    CONSTRAINT chk_penalizacion_monto CHECK (monto > 0)
) ENGINE=InnoDB;


-- =====================================================================
-- 15. NÓMINA MENSUAL
-- =====================================================================

CREATE TABLE nomina_mensual (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    colaborador_id          BIGINT NOT NULL,
    anio                    INT NOT NULL,
    mes                     INT NOT NULL,
    horas_contratadas       DECIMAL(7,2) NOT NULL,
    horas_actividades       DECIMAL(7,2) NOT NULL DEFAULT 0,
    horas_capacitacion      DECIMAL(7,2) NOT NULL DEFAULT 0,
    horas_cumplidas         DECIMAL(7,2) NOT NULL DEFAULT 0,
    horas_faltantes         DECIMAL(7,2) NOT NULL DEFAULT 0,
    horas_extra_pagadas     DECIMAL(6,2) NOT NULL DEFAULT 0,
    sueldo_base_aplicado    DECIMAL(10,2) NOT NULL,
    bono                    DECIMAL(10,2) NOT NULL DEFAULT 0,
    penalizacion            DECIMAL(10,2) NOT NULL DEFAULT 0,
    pago_total              DECIMAL(10,2) NOT NULL,
    fecha_calculo           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_nomina_colaborador FOREIGN KEY (colaborador_id) REFERENCES usuario(id),
    CONSTRAINT uq_nomina_periodo UNIQUE (colaborador_id, anio, mes),
    CONSTRAINT chk_nomina_mes CHECK (mes BETWEEN 1 AND 12)
) ENGINE=InnoDB;


-- =====================================================================
-- 16. ETIQUETAS 
-- =====================================================================

CREATE TABLE etiqueta (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre  VARCHAR(50) NOT NULL UNIQUE
) ENGINE=InnoDB;


-- =====================================================================
-- 17. FOROS
-- =====================================================================


CREATE TABLE foro (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    proyecto_id     BIGINT NULL,
    tipo            VARCHAR(15) NOT NULL,
    es_publico      BOOLEAN NOT NULL DEFAULT TRUE,
    nombre          VARCHAR(150) NOT NULL,
    fecha_creacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_foro_proyecto FOREIGN KEY (proyecto_id) REFERENCES proyecto(id),
    CONSTRAINT chk_foro_tipo CHECK (tipo IN ('GENERAL','PROYECTO'))
) ENGINE=InnoDB;


-- =====================================================================
-- 18. PUBLICACIONES DEL FORO
-- =====================================================================


CREATE TABLE publicacion_foro (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    foro_id         BIGINT NOT NULL,
    autor_id        BIGINT NOT NULL,
    etiqueta_id     BIGINT NULL,
    titulo          VARCHAR(200) NOT NULL,
    contenido       TEXT NOT NULL,
    activo          BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_publicacion_foro FOREIGN KEY (foro_id) REFERENCES foro(id),
    CONSTRAINT fk_publicacion_autor FOREIGN KEY (autor_id) REFERENCES usuario(id),
    CONSTRAINT fk_publicacion_etiqueta FOREIGN KEY (etiqueta_id) REFERENCES etiqueta(id)
) ENGINE=InnoDB;


-- =====================================================================
-- 19. RESPUESTAS DEL FORO
-- =====================================================================

CREATE TABLE respuesta_foro (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    publicacion_id  BIGINT NOT NULL,
    autor_id        BIGINT NOT NULL,
    contenido       TEXT NOT NULL,
    es_solucion     BOOLEAN NOT NULL DEFAULT FALSE,
    activo          BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_respuesta_publicacion FOREIGN KEY (publicacion_id) REFERENCES publicacion_foro(id),
    CONSTRAINT fk_respuesta_autor FOREIGN KEY (autor_id) REFERENCES usuario(id)
) ENGINE=InnoDB;


-- =====================================================================
-- 20. VOTOS DE PUBLICACIONES
-- =====================================================================

CREATE TABLE voto_publicacion (
    usuario_id      BIGINT NOT NULL,
    publicacion_id  BIGINT NOT NULL,
    tipo            VARCHAR(10) NOT NULL,

    PRIMARY KEY (usuario_id, publicacion_id),

    CONSTRAINT fk_votopub_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT fk_votopub_publicacion FOREIGN KEY (publicacion_id) REFERENCES publicacion_foro(id),
    CONSTRAINT chk_votopub_tipo CHECK (tipo IN ('POSITIVO','NEGATIVO'))
) ENGINE=InnoDB;


-- =====================================================================
-- 21. VOTOS DE RESPUESTAS
-- =====================================================================

CREATE TABLE voto_respuesta (
    usuario_id      BIGINT NOT NULL,
    respuesta_id    BIGINT NOT NULL,
    tipo            VARCHAR(10) NOT NULL,

    PRIMARY KEY (usuario_id, respuesta_id),

    CONSTRAINT fk_votoresp_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT fk_votoresp_respuesta FOREIGN KEY (respuesta_id) REFERENCES respuesta_foro(id),
    CONSTRAINT chk_votoresp_tipo CHECK (tipo IN ('POSITIVO','NEGATIVO'))
) ENGINE=InnoDB;


-- =====================================================================
-- 22. CONVERSACIONES / CHAT
-- =====================================================================

CREATE TABLE conversacion (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tipo            VARCHAR(15) NOT NULL,
    proyecto_id     BIGINT NOT NULL,
    fecha_creacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_conversacion_proyecto FOREIGN KEY (proyecto_id) REFERENCES proyecto(id),
    CONSTRAINT chk_conversacion_tipo CHECK (tipo IN ('GRUPAL','PRIVADA'))
) ENGINE=InnoDB;

CREATE TABLE conversacion_usuario (
    conversacion_id BIGINT NOT NULL,
    usuario_id      BIGINT NOT NULL,

    PRIMARY KEY (conversacion_id, usuario_id),

    CONSTRAINT fk_convusuario_conversacion FOREIGN KEY (conversacion_id) REFERENCES conversacion(id),
    CONSTRAINT fk_convusuario_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

CREATE TABLE mensaje (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversacion_id BIGINT NOT NULL,
    autor_id        BIGINT NOT NULL,
    contenido       TEXT NOT NULL,
    fecha_hora      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_mensaje_conversacion FOREIGN KEY (conversacion_id) REFERENCES conversacion(id),
    CONSTRAINT fk_mensaje_autor FOREIGN KEY (autor_id) REFERENCES usuario(id)
) ENGINE=InnoDB;


-- =====================================================================
-- 23. NOTIFICACIONES
-- =====================================================================

CREATE TABLE notificacion (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id      BIGINT NOT NULL,
    tipo            VARCHAR(50) NOT NULL,
    categoria       VARCHAR(30) NOT NULL,
    titulo          VARCHAR(150) NOT NULL,
    descripcion     VARCHAR(400) NULL,
    entidad         VARCHAR(60) NULL,
    entidad_id      BIGINT NULL,
    leida           BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_creacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_notificacion_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),

    CONSTRAINT chk_notificacion_categoria
        CHECK (categoria IN ('PROYECTO','ASIGNACION','ACTIVIDAD','CURSO','HABILIDAD','MENSAJE','HORAS','SISTEMA'))
) ENGINE=InnoDB;


-- =====================================================================
-- 24. CONFIGURACIÓN DEL SISTEMA
-- =====================================================================

CREATE TABLE configuracion_sistema (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    clave           VARCHAR(60) NOT NULL UNIQUE,
    valor           VARCHAR(255) NOT NULL,
    descripcion     VARCHAR(300) NULL
) ENGINE=InnoDB;

INSERT INTO configuracion_sistema (clave, valor, descripcion) VALUES
    ('MAX_ASIGNACIONES_POR_COLABORADOR', '3', 'Límite de asignaciones activas simultáneas por colaborador'),
    ('TOPE_HORAS_EXTRA_BONO', '20', 'Máximo de horas extra pagables como bono por mes'),
    ('NOMBRE_ORGANIZACION', 'SkillBridge AI', 'Nombre visible de la organización');


-- =====================================================================
-- 25A. DOCUMENTOS DEL PROYECTO
-- =====================================================================

CREATE TABLE documento (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    proyecto_id     BIGINT NOT NULL,
    subido_por_id   BIGINT NOT NULL,
    nombre          VARCHAR(200) NOT NULL,
    categoria       VARCHAR(20) NOT NULL,
    archivo_url     VARCHAR(500) NOT NULL,
    activo          BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_documento_proyecto FOREIGN KEY (proyecto_id) REFERENCES proyecto(id),
    CONSTRAINT fk_documento_usuario FOREIGN KEY (subido_por_id) REFERENCES usuario(id)
) ENGINE=InnoDB;


-- =====================================================================
-- 25B. EDUCACIÓN DEL COLABORADOR
-- =====================================================================

CREATE TABLE educacion (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    colaborador_id  BIGINT NOT NULL,
    institucion     VARCHAR(150) NOT NULL,
    titulo          VARCHAR(150) NOT NULL,
    archivo_url     VARCHAR(500) NULL,
    fecha_inicio    DATE NULL,
    fecha_fin       DATE NULL,
    actual          BOOLEAN NOT NULL DEFAULT FALSE,
    estado          VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    motivo_rechazo  VARCHAR(300) NULL,
    revisado_por    BIGINT NULL,
    activo          BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_revision  DATETIME NULL,

    CONSTRAINT fk_educacion_colaborador FOREIGN KEY (colaborador_id) REFERENCES usuario(id),
    CONSTRAINT fk_educacion_revisor FOREIGN KEY (revisado_por) REFERENCES usuario(id)
) ENGINE=InnoDB;


-- =====================================================================
-- 25C. SOLICITUDES DE PERSONAL (PM -> RM)
-- Antes vivía en BDs_SQL/migracion_solicitud_personal.sql como migración
-- aparte para BDs ya creadas; una instalación nueva la recibe aquí directo.
-- =====================================================================

CREATE TABLE solicitud_personal (
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

    CONSTRAINT fk_solpersonal_proyecto FOREIGN KEY (proyecto_id) REFERENCES proyecto(id),
    CONSTRAINT fk_solpersonal_rm FOREIGN KEY (rm_responsable_id) REFERENCES usuario(id),
    CONSTRAINT chk_solpersonal_cantidad CHECK (cantidad_colaboradores > 0),
    CONSTRAINT chk_solpersonal_estado CHECK (estado IN ('PENDIENTE','EN_ATENCION','ATENDIDA','CANCELADA')),

    INDEX idx_solpersonal_estado_fecha (estado, fecha_solicitud),
    INDEX idx_solpersonal_proyecto (proyecto_id)
) ENGINE=InnoDB;


-- =====================================================================
-- 25. AUDITORÍA
-- =====================================================================

CREATE TABLE log_auditoria (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id      BIGINT NULL,
    accion          VARCHAR(60) NOT NULL,
    entidad         VARCHAR(60) NOT NULL,
    entidad_id      BIGINT NULL,
    detalle         VARCHAR(500) NULL,
    valor_anterior  VARCHAR(255) NULL,
    valor_nuevo     VARCHAR(255) NULL,
    fecha_hora      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip              VARCHAR(45) NULL,

    CONSTRAINT fk_log_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;
-- 1. Tabla de evaluaciones
CREATE TABLE IF NOT EXISTS evaluacion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    colaborador_id BIGINT NOT NULL,
    evaluador_id BIGINT NOT NULL,
    asignacion_id BIGINT,
    calificacion INT NOT NULL,
    comentarios VARCHAR(1000),
    fecha_creacion DATETIME NOT NULL,
    CONSTRAINT fk_eval_colaborador FOREIGN KEY (colaborador_id) REFERENCES usuario(id),
    CONSTRAINT fk_eval_evaluador FOREIGN KEY (evaluador_id) REFERENCES usuario(id),
    CONSTRAINT fk_eval_asignacion FOREIGN KEY (asignacion_id) REFERENCES asignacion(id)
);

-- 2. Columna de horas por habilidad
-- 2. Columna de horas por habilidad
ALTER TABLE proyecto_habilidad_requerida 
    ADD COLUMN horas_semanales DECIMAL(5,2);

-- 3. Columna de documento de contexto en proyecto
ALTER TABLE proyecto 
    ADD COLUMN documento_contexto_url VARCHAR(500);
-- =====================================================================
-- DATOS DE PRUEBA: USUARIOS (contraseña para todos: abc123)
-- Hash generado con BCrypt $ (compatible con Spring Security Java)
-- =====================================================================


INSERT INTO usuario (correo, password_hash, nombre, apellido, rol_id, activo, cargo_id, horas_disponibles) VALUES
  ('admin@skillbridge.com',  '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom', 'Admin',   'Sistema',  1, 1, NULL, NULL),
  ('rm@skillbridge.com',     '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom', 'Ricardo', 'Mendez',   3, 1, NULL, NULL),
  ('pm@skillbridge.com',     '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom', 'Pedro',   'Martinez', 2, 1, NULL, NULL),
  ('col@skillbridge.com',    '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom', 'Carlos',  'Lopez',    4, 1, (SELECT id FROM cargo WHERE nombre = 'Backend Developer'), 40);



