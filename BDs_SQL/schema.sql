-- =====================================================================
-- SkillBridge AI — Esquema de Base de Datos
-- GTICS 2026-2 · TEL137
-- =====================================================================
-- Convenciones de diseño (explicadas con más detalle en el .md adjunto):
--  - snake_case, tablas en singular.
--  - Los campos de "estado" y "rol" son VARCHAR, no ENUM nativo de MySQL,
--    para poder agregar valores nuevos sin ALTER TABLE (ver considera-
--    ciones_base_datos.md, punto 2).
--  - Nada se borra físicamente: todo lo que el negocio pide "desactivar"
--    tiene una columna `activo` en vez de un DELETE.
--  - Todas las fechas de auditoría (`fecha_creacion`) son automáticas.
-- =====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS skillbridge_db
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE skillbridge_db;

-- =====================================================================
-- ÉPICA 2 — AUTENTICACIÓN Y AUTORIZACIÓN
-- =====================================================================

-- Historia 5.1 (creación) + Historia de Activación de cuenta.
-- `nombre` y `password_hash` quedan NULL hasta que el usuario activa su cuenta.
CREATE TABLE usuario (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    correo              VARCHAR(150)  NOT NULL UNIQUE,
    password_hash       VARCHAR(255)  NULL,
    nombre              VARCHAR(150)  NULL,
    rol                 VARCHAR(30)   NOT NULL, -- 'ADMINISTRADOR' | 'PROJECT_MANAGER' | 'RESOURCE_MANAGER' | 'COLABORADOR'
    activo              BOOLEAN       NOT NULL DEFAULT TRUE,
    fecha_creacion      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_usuario_rol CHECK (rol IN ('ADMINISTRADOR','PROJECT_MANAGER','RESOURCE_MANAGER','COLABORADOR'))
) ENGINE=InnoDB;

-- Historia de Activación de cuenta (A6).
CREATE TABLE token_activacion (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id          BIGINT        NOT NULL,
    token               VARCHAR(255)  NOT NULL UNIQUE,
    fecha_expiracion    DATETIME      NOT NULL,
    usado               BOOLEAN       NOT NULL DEFAULT FALSE,
    fecha_creacion      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_token_activacion_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

-- Historia de Recuperación de contraseña (ya existía en Épica 2).
CREATE TABLE token_recuperacion_password (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id          BIGINT        NOT NULL,
    token               VARCHAR(255)  NOT NULL UNIQUE,
    fecha_expiracion    DATETIME      NOT NULL,
    usado               BOOLEAN       NOT NULL DEFAULT FALSE,
    fecha_creacion      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_token_recuperacion_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

-- =====================================================================
-- ÉPICA 3 — GESTIÓN DE COLABORADORES
-- =====================================================================

-- Extiende a `usuario` (1:1) solo para quienes tienen rol COLABORADOR.
-- Separado de `usuario` para no llenar de columnas nulas a PM/RM/Admin,
-- y porque `sueldo_base` es un dato sensible (ver C7) que conviene aislar.
CREATE TABLE colaborador (
    usuario_id              BIGINT PRIMARY KEY,
    cargo                   VARCHAR(100)  NULL,
    disponibilidad_horas    DECIMAL(5,2)  NULL,        -- B1: "ES POR HORAS" (horas/semana disponibles)
    sueldo_base             DECIMAL(10,2) NULL,         -- A19: solo lo asigna el Administrador
    nivel_experiencia       VARCHAR(20)   NULL,         -- A23: 'JUNIOR' | 'SENIOR', lo asigna el RM
    CONSTRAINT fk_colaborador_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT chk_colaborador_nivel CHECK (nivel_experiencia IS NULL OR nivel_experiencia IN ('JUNIOR','SENIOR'))
) ENGINE=InnoDB;

-- Historia 5.2 — catálogo administrado por el Admin.
CREATE TABLE categoria_habilidad (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre  VARCHAR(80) NOT NULL UNIQUE
) ENGINE=InnoDB;

CREATE TABLE habilidad (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre          VARCHAR(100) NOT NULL,
    categoria_id    BIGINT       NOT NULL,
    activa          BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_habilidad_categoria FOREIGN KEY (categoria_id) REFERENCES categoria_habilidad(id),
    CONSTRAINT uq_habilidad_nombre_categoria UNIQUE (nombre, categoria_id)
) ENGINE=InnoDB;

-- Historia de Gestión de habilidades y experiencia (colaborador).
CREATE TABLE colaborador_habilidad (
    colaborador_id  BIGINT      NOT NULL,
    habilidad_id    BIGINT      NOT NULL,
    nivel_dominio   VARCHAR(20) NOT NULL, -- 'BASICO' | 'INTERMEDIO' | 'AVANZADO'
    PRIMARY KEY (colaborador_id, habilidad_id),
    CONSTRAINT fk_colhab_colaborador FOREIGN KEY (colaborador_id) REFERENCES colaborador(usuario_id),
    CONSTRAINT fk_colhab_habilidad   FOREIGN KEY (habilidad_id)   REFERENCES habilidad(id)
) ENGINE=InnoDB;

CREATE TABLE experiencia_profesional (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    colaborador_id  BIGINT       NOT NULL,
    descripcion     VARCHAR(500) NOT NULL,
    fecha_inicio    DATE         NULL,
    fecha_fin       DATE         NULL,
    CONSTRAINT fk_experiencia_colaborador FOREIGN KEY (colaborador_id) REFERENCES colaborador(usuario_id)
) ENGINE=InnoDB;

-- Historia A23 — el RM revisa certificados y actualiza habilidades/nivel.
CREATE TABLE certificado (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    colaborador_id      BIGINT       NOT NULL,
    habilidad_id        BIGINT       NULL,             -- a qué habilidad refuerza (puede ser general)
    archivo_url         VARCHAR(500) NOT NULL,
    estado              VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE', -- PENDIENTE | APROBADO | RECHAZADO
    motivo_rechazo      VARCHAR(300) NULL,
    revisado_por        BIGINT       NULL,             -- usuario_id del RM que revisó
    fecha_subida        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_revision      DATETIME     NULL,
    CONSTRAINT fk_certificado_colaborador FOREIGN KEY (colaborador_id) REFERENCES colaborador(usuario_id),
    CONSTRAINT fk_certificado_habilidad   FOREIGN KEY (habilidad_id)   REFERENCES habilidad(id),
    CONSTRAINT fk_certificado_revisor     FOREIGN KEY (revisado_por)   REFERENCES usuario(id),
    CONSTRAINT chk_certificado_estado CHECK (estado IN ('PENDIENTE','APROBADO','RECHAZADO'))
) ENGINE=InnoDB;

-- =====================================================================
-- ÉPICA 4 — PROYECTOS Y ASIGNACIÓN
-- =====================================================================

-- Estados y flujo de prioridad definidos por el equipo (ver B3):
-- EN_REVISION -> ACTIVO | RECHAZADO   (por decisión del RM)
-- ACTIVO -> EN_ESPERA -> ACTIVO
-- ACTIVO | EN_ESPERA -> FINALIZADO
-- EN_REVISION -> CANCELADO (el propio PM puede cancelar antes de que el RM decida)
CREATE TABLE proyecto (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre                  VARCHAR(150)  NOT NULL,
    descripcion             VARCHAR(1000) NULL,
    fecha_inicio            DATE          NULL,
    fecha_fin_estimada      DATE          NULL,
    estado                  VARCHAR(20)   NOT NULL DEFAULT 'EN_REVISION',
    prioridad               VARCHAR(10)   NOT NULL, -- 'ALTA' | 'MEDIA' | 'BAJA'
    justificacion_prioridad VARCHAR(500)  NOT NULL,
    motivo_rechazo          VARCHAR(500)  NULL,
    presupuesto             DECIMAL(12,2) NULL,      -- A20: solo lo asigna el RM, tras aprobar
    colaboradores_requeridos INT          NOT NULL DEFAULT 1,
    pm_id                   BIGINT        NOT NULL,
    rm_revisor_id           BIGINT        NULL,       -- quién aprobó/rechazó el proyecto
    fecha_creacion          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_proyecto_pm  FOREIGN KEY (pm_id) REFERENCES usuario(id),
    CONSTRAINT fk_proyecto_rm  FOREIGN KEY (rm_revisor_id) REFERENCES usuario(id),
    CONSTRAINT chk_proyecto_estado CHECK (estado IN ('EN_REVISION','RECHAZADO','ACTIVO','EN_ESPERA','CANCELADO','FINALIZADO')),
    CONSTRAINT chk_proyecto_prioridad CHECK (prioridad IN ('ALTA','MEDIA','BAJA'))
) ENGINE=InnoDB;

-- "Tecnologías" y "habilidades requeridas" del proyecto comparten el
-- mismo catálogo de `habilidad` (ver considera_base_datos.md, punto 4).
CREATE TABLE proyecto_habilidad_requerida (
    proyecto_id     BIGINT NOT NULL,
    habilidad_id    BIGINT NOT NULL,
    PRIMARY KEY (proyecto_id, habilidad_id),
    CONSTRAINT fk_projhab_proyecto  FOREIGN KEY (proyecto_id)  REFERENCES proyecto(id),
    CONSTRAINT fk_projhab_habilidad FOREIGN KEY (habilidad_id) REFERENCES habilidad(id)
) ENGINE=InnoDB;

-- Historia A4 (reescrita): flujo de triple origen + doble aprobación condicional.
CREATE TABLE asignacion (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    proyecto_id         BIGINT      NOT NULL,
    colaborador_id      BIGINT      NOT NULL,
    origen              VARCHAR(25) NOT NULL, -- 'PROPUESTA_PM' | 'PROPUESTA_RM' | 'SOLICITADA_COLABORADOR'
    estado              VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE', -- PENDIENTE | ACTIVA | RECHAZADA | FINALIZADA
    aprobado_por_pm     BOOLEAN     NOT NULL DEFAULT FALSE,
    aprobado_por_rm     BOOLEAN     NOT NULL DEFAULT FALSE,
    motivo_rechazo      VARCHAR(300) NULL,
    motivo_finalizacion VARCHAR(30) NULL,     -- B7: 'BAJO_DESEMPENO' | 'PROYECTO_CANCELADO' | 'OTRO' (para la posible penalización)
    fecha_solicitud     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_activacion    DATETIME    NULL,
    fecha_finalizacion  DATETIME    NULL,
    CONSTRAINT fk_asignacion_proyecto    FOREIGN KEY (proyecto_id)    REFERENCES proyecto(id),
    CONSTRAINT fk_asignacion_colaborador FOREIGN KEY (colaborador_id) REFERENCES colaborador(usuario_id),
    CONSTRAINT chk_asignacion_origen CHECK (origen IN ('PROPUESTA_PM','PROPUESTA_RM','SOLICITADA_COLABORADOR')),
    CONSTRAINT chk_asignacion_estado CHECK (estado IN ('PENDIENTE','ACTIVA','RECHAZADA','FINALIZADA')),
    -- Un colaborador no puede tener 2 asignaciones ACTIVAS al mismo proyecto a la vez:
    CONSTRAINT uq_asignacion_activa UNIQUE (proyecto_id, colaborador_id, estado)
) ENGINE=InnoDB;

-- =====================================================================
-- ÉPICA 9 — GESTIÓN DE HORAS Y PAGOS
-- =====================================================================

-- Historia 9.1 — solo el PM asigna tareas, y solo a colaboradores con
-- asignación ACTIVA en ese proyecto (se valida en la capa de servicio).
CREATE TABLE tarea (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    proyecto_id         BIGINT       NOT NULL,
    colaborador_id      BIGINT       NOT NULL,
    descripcion         VARCHAR(300) NOT NULL,
    horas_estimadas     DECIMAL(6,2) NOT NULL,
    fecha_limite        DATE         NOT NULL,
    fecha_entrega       DATETIME     NULL,
    estado_entrega      VARCHAR(15)  NOT NULL DEFAULT 'PENDIENTE', -- PENDIENTE | A_TIEMPO | TARDIA
    creado_por          BIGINT       NOT NULL, -- usuario_id del PM
    fecha_creacion      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tarea_proyecto    FOREIGN KEY (proyecto_id)    REFERENCES proyecto(id),
    CONSTRAINT fk_tarea_colaborador FOREIGN KEY (colaborador_id) REFERENCES colaborador(usuario_id),
    CONSTRAINT fk_tarea_creador     FOREIGN KEY (creado_por)     REFERENCES usuario(id),
    CONSTRAINT chk_tarea_estado CHECK (estado_entrega IN ('PENDIENTE','A_TIEMPO','TARDIA'))
) ENGINE=InnoDB;

-- Historia 9.3 — snapshot mensual del cálculo de pago.
-- Se GUARDA (no se recalcula al vuelo) para que un cambio futuro en el
-- sueldo_base o en el tope de bono no altere los pagos ya calculados de
-- meses anteriores. Ver considera_base_datos.md, punto 5.
CREATE TABLE nomina_mensual (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    colaborador_id          BIGINT       NOT NULL,
    anio                    INT          NOT NULL,
    mes                     INT          NOT NULL, -- 1-12
    horas_totales           DECIMAL(7,2) NOT NULL, -- a tiempo + tardías
    horas_a_tiempo          DECIMAL(7,2) NOT NULL,
    horas_extra_pagadas     DECIMAL(6,2) NOT NULL DEFAULT 0,
    sueldo_base_aplicado    DECIMAL(10,2) NOT NULL,
    bono                    DECIMAL(10,2) NOT NULL DEFAULT 0,
    penalizacion            DECIMAL(10,2) NULL,     -- B7: mecanismo exacto aún sin definir
    pago_total              DECIMAL(10,2) NOT NULL,
    fecha_calculo           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_nomina_colaborador FOREIGN KEY (colaborador_id) REFERENCES colaborador(usuario_id),
    CONSTRAINT uq_nomina_periodo UNIQUE (colaborador_id, anio, mes)
) ENGINE=InnoDB;

-- =====================================================================
-- CURSOS Y CAPACITACIONES (definido en la sesión de hoy)
-- =====================================================================

-- El Admin registra los cursos.
CREATE TABLE curso (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre          VARCHAR(150)  NOT NULL,
    descripcion     VARCHAR(500)  NULL,
    activo          BOOLEAN       NOT NULL DEFAULT TRUE,
    creado_por      BIGINT        NOT NULL, -- usuario_id del Admin
    fecha_creacion  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_curso_creador FOREIGN KEY (creado_por) REFERENCES usuario(id)
) ENGINE=InnoDB;

-- Un mismo registro sirve tanto para "colaborador solicita" como para
-- "RM asigna" — se distingue por la columna `origen`.
CREATE TABLE colaborador_curso (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    colaborador_id      BIGINT      NOT NULL,
    curso_id            BIGINT      NOT NULL,
    origen              VARCHAR(20) NOT NULL, -- 'SOLICITUD_PROPIA' | 'ASIGNADO_POR_RM'
    estado              VARCHAR(20) NOT NULL DEFAULT 'SOLICITADO', -- SOLICITADO | EN_CURSO | COMPLETADO | RECHAZADO
    asignado_por        BIGINT      NULL,     -- usuario_id del RM, si el origen fue ASIGNADO_POR_RM
    fecha_solicitud     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_completado    DATETIME    NULL,
    CONSTRAINT fk_colcurso_colaborador FOREIGN KEY (colaborador_id) REFERENCES colaborador(usuario_id),
    CONSTRAINT fk_colcurso_curso       FOREIGN KEY (curso_id)       REFERENCES curso(id),
    CONSTRAINT fk_colcurso_asignador   FOREIGN KEY (asignado_por)   REFERENCES usuario(id),
    CONSTRAINT chk_colcurso_origen CHECK (origen IN ('SOLICITUD_PROPIA','ASIGNADO_POR_RM')),
    CONSTRAINT chk_colcurso_estado CHECK (estado IN ('SOLICITADO','EN_CURSO','COMPLETADO','RECHAZADO'))
) ENGINE=InnoDB;

-- =====================================================================
-- ÉPICA 5 — ADMINISTRACIÓN DEL SISTEMA
-- =====================================================================

-- Historia 5.3 — clave/valor genérico, escalable sin ALTER TABLE.
CREATE TABLE configuracion_sistema (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    clave           VARCHAR(60)   NOT NULL UNIQUE,
    valor           VARCHAR(255)  NOT NULL,
    descripcion     VARCHAR(300)  NULL
) ENGINE=InnoDB;

-- Historia 5.4 — incluye ahora asignaciones (A11) y, sugerido, cambios
-- de configuración (C10). El "usuario_id" puede ser NULL si la acción
-- la origina el propio sistema (ej. un job automático).
CREATE TABLE log_auditoria (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id      BIGINT       NULL,
    accion          VARCHAR(60)  NOT NULL,
    entidad         VARCHAR(60)  NOT NULL,
    entidad_id      BIGINT       NULL,
    detalle         VARCHAR(500) NULL,
    fecha_hora      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip              VARCHAR(45)  NULL,
    CONSTRAINT fk_log_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

-- =====================================================================
-- ÉPICA 7 — COMUNICACIÓN: FOROS Y CHAT
-- =====================================================================

CREATE TABLE etiqueta (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre  VARCHAR(50) NOT NULL UNIQUE
) ENGINE=InnoDB;

CREATE TABLE publicacion_foro (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    proyecto_id     BIGINT        NOT NULL,
    autor_id        BIGINT        NOT NULL,
    titulo          VARCHAR(200)  NOT NULL,
    contenido       TEXT          NOT NULL,
    fecha_creacion  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_publicacion_proyecto FOREIGN KEY (proyecto_id) REFERENCES proyecto(id),
    CONSTRAINT fk_publicacion_autor    FOREIGN KEY (autor_id)    REFERENCES usuario(id)
) ENGINE=InnoDB;

CREATE TABLE publicacion_etiqueta (
    publicacion_id  BIGINT NOT NULL,
    etiqueta_id     BIGINT NOT NULL,
    PRIMARY KEY (publicacion_id, etiqueta_id),
    CONSTRAINT fk_publetiq_publicacion FOREIGN KEY (publicacion_id) REFERENCES publicacion_foro(id),
    CONSTRAINT fk_publetiq_etiqueta    FOREIGN KEY (etiqueta_id)    REFERENCES etiqueta(id)
) ENGINE=InnoDB;

CREATE TABLE respuesta_foro (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    publicacion_id  BIGINT       NOT NULL,
    autor_id        BIGINT       NOT NULL,
    contenido       TEXT         NOT NULL,
    es_solucion     BOOLEAN      NOT NULL DEFAULT FALSE,
    fecha_creacion  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_respuesta_publicacion FOREIGN KEY (publicacion_id) REFERENCES publicacion_foro(id),
    CONSTRAINT fk_respuesta_autor       FOREIGN KEY (autor_id)       REFERENCES usuario(id)
) ENGINE=InnoDB;

-- A7/A8: un voto por usuario, separado en 2 tablas para no usar FKs
-- nulas polimórficas (ver considera_base_datos.md, punto 6).
CREATE TABLE voto_publicacion (
    usuario_id      BIGINT      NOT NULL,
    publicacion_id  BIGINT      NOT NULL,
    tipo            VARCHAR(10) NOT NULL, -- 'POSITIVO' | 'NEGATIVO'
    PRIMARY KEY (usuario_id, publicacion_id),
    CONSTRAINT fk_votopub_usuario     FOREIGN KEY (usuario_id)     REFERENCES usuario(id),
    CONSTRAINT fk_votopub_publicacion FOREIGN KEY (publicacion_id) REFERENCES publicacion_foro(id),
    CONSTRAINT chk_votopub_tipo CHECK (tipo IN ('POSITIVO','NEGATIVO'))
) ENGINE=InnoDB;

CREATE TABLE voto_respuesta (
    usuario_id      BIGINT      NOT NULL,
    respuesta_id    BIGINT      NOT NULL,
    tipo            VARCHAR(10) NOT NULL,
    PRIMARY KEY (usuario_id, respuesta_id),
    CONSTRAINT fk_votoresp_usuario   FOREIGN KEY (usuario_id)   REFERENCES usuario(id),
    CONSTRAINT fk_votoresp_respuesta FOREIGN KEY (respuesta_id) REFERENCES respuesta_foro(id),
    CONSTRAINT chk_votoresp_tipo CHECK (tipo IN ('POSITIVO','NEGATIVO'))
) ENGINE=InnoDB;

-- A9: el chat NO incluye al RM (a diferencia del foro) — se filtra en
-- la capa de servicio comprobando la asignación activa + PM del proyecto.
CREATE TABLE mensaje_chat (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    proyecto_id     BIGINT    NOT NULL,
    autor_id        BIGINT    NOT NULL,
    contenido       TEXT      NOT NULL,
    fecha_hora      DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mensaje_proyecto FOREIGN KEY (proyecto_id) REFERENCES proyecto(id),
    CONSTRAINT fk_mensaje_autor    FOREIGN KEY (autor_id)    REFERENCES usuario(id)
) ENGINE=InnoDB;

-- =====================================================================
-- NOTIFICACIONES (definido en la sesión de hoy — para TODOS los roles)
-- =====================================================================

CREATE TABLE notificacion (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id          BIGINT       NOT NULL, -- destinatario, cualquier rol
    tipo                VARCHAR(40)  NOT NULL, -- ej. 'ASIGNACION_PENDIENTE', 'CERTIFICADO_SUBIDO', 'PROYECTO_RECHAZADO'...
    titulo              VARCHAR(150) NOT NULL,
    descripcion         VARCHAR(400) NULL,
    entidad             VARCHAR(60)  NULL,     -- a qué tabla/entidad refiere (para armar el link)
    entidad_id          BIGINT       NULL,
    leida               BOOLEAN      NOT NULL DEFAULT FALSE,
    fecha_creacion      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notificacion_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

SET FOREIGN_KEY_CHECKS = 1;
