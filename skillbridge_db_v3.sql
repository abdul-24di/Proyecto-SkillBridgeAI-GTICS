
-- =====================================================================
-- SkillBridge AI — Base de Datos
-- GTICS 2026-2 · TEL137
-- =====================================================================
--
-- PRINCIPALES REGLAS DEL SISTEMA
--
-- 1. El PM crea el proyecto.
-- 2. El RM revisa y aprueba/rechaza el proyecto.
-- 3. Un colaborador puede solicitar unirse a un proyecto.
-- 4. Una asignación requiere aprobación tanto del PM como del RM.
-- 5. Si cualquiera de los dos rechaza, el colaborador NO se une.
-- 6. Cada proyecto tiene un chat grupal para TODOS sus integrantes.
-- 7. Los integrantes también pueden crear chats privados entre
--    miembros del MISMO proyecto.
-- 8. Un colaborador puede solicitar un curso.
-- 9. El RM decide si aprueba o rechaza la solicitud del curso.
-- 10. Las horas cumplidas provienen de actividades y cursos completados.
-- 11. Las notificaciones se almacenan en una sola tabla y se clasifican
--     mediante el campo `tipo`.
-- 12. El sistema evita borrar información de negocio físicamente.
--
-- =====================================================================

CREATE DATABASE IF NOT EXISTS skillbridge_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE skillbridge_db;

SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================================
-- NOTA
-- =====================================================================
-- Este script asume una base de datos NUEVA (no incluye DROP TABLE).
-- Si necesitas reiniciar todo desde cero durante desarrollo, usa el
-- archivo aparte `reset_desarrollo.sql` — nunca se incluye un DROP
-- aquí para no borrar datos por accidente en un ambiente con
-- información real cargada.
-- =====================================================================

SET FOREIGN_KEY_CHECKS = 1;
-- =====================================================================
-- 1. USUARIOS Y AUTENTICACIÓN
-- =====================================================================

CREATE TABLE usuario (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    correo              VARCHAR(150) NOT NULL UNIQUE,
    password_hash       VARCHAR(255) NULL,
    nombre              VARCHAR(150) NULL,
    apellido            VARCHAR(150) NULL,
    telefono            VARCHAR(20) NULL,
    foto_url            VARCHAR(500) NULL,

    rol                 VARCHAR(30) NOT NULL,

    activo              BOOLEAN NOT NULL DEFAULT TRUE,

    fecha_creacion      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_usuario_rol
        CHECK (
            rol IN (
                'ADMINISTRADOR',
                'PROJECT_MANAGER',
                'RESOURCE_MANAGER',
                'COLABORADOR'
            )
        )
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- Activación de cuenta
-- ---------------------------------------------------------------------

CREATE TABLE token_activacion (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id          BIGINT NOT NULL,
    token               VARCHAR(255) NOT NULL UNIQUE,
    fecha_expiracion    DATETIME NOT NULL,
    usado               BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_creacion      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_token_activacion_usuario
        FOREIGN KEY (usuario_id)
        REFERENCES usuario(id)
) ENGINE=InnoDB;


-- ---------------------------------------------------------------------
-- Recuperación de contraseña
-- ---------------------------------------------------------------------

CREATE TABLE token_recuperacion_password (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id          BIGINT NOT NULL,
    token               VARCHAR(255) NOT NULL UNIQUE,
    fecha_expiracion    DATETIME NOT NULL,
    usado               BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_creacion      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_token_recuperacion_usuario
        FOREIGN KEY (usuario_id)
        REFERENCES usuario(id)
) ENGINE=InnoDB;


-- =====================================================================
-- 2. PERFIL DEL COLABORADOR
-- =====================================================================

CREATE TABLE colaborador (
    usuario_id                  BIGINT PRIMARY KEY,

    cargo                       VARCHAR(100) NULL,

    -- Horas que el colaborador tiene contratadas por semana.
    horas_contratadas_semana    DECIMAL(5,2) NULL,

    sueldo_base                 DECIMAL(10,2) NULL,

    nivel_experiencia           VARCHAR(20) NULL,

    fecha_contratacion          DATE NULL,

    estado                      VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',

    CONSTRAINT fk_colaborador_usuario
        FOREIGN KEY (usuario_id)
        REFERENCES usuario(id),

    CONSTRAINT chk_colaborador_nivel
        CHECK (
            nivel_experiencia IS NULL OR
            nivel_experiencia IN (
                'JUNIOR',
                'SEMI_SENIOR',
                'SENIOR'
            )
        ),

    CONSTRAINT chk_colaborador_estado
        CHECK (
            estado IN (
                'ACTIVO',
                'INACTIVO'
            )
        )
) ENGINE=InnoDB;


-- =====================================================================
-- 3. EXPERIENCIA PROFESIONAL
-- =====================================================================

CREATE TABLE experiencia_profesional (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,

    colaborador_id      BIGINT NOT NULL,

    empresa             VARCHAR(150) NULL,
    cargo               VARCHAR(100) NULL,
    descripcion         VARCHAR(500) NOT NULL,

    fecha_inicio        DATE NULL,
    fecha_fin           DATE NULL,

    actual              BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_experiencia_colaborador
        FOREIGN KEY (colaborador_id)
        REFERENCES colaborador(usuario_id)
) ENGINE=InnoDB;


-- =====================================================================
-- 4. DISPONIBILIDAD
-- =====================================================================
--
-- Representa cuántas horas semanales tiene disponible el colaborador
-- para proyectos durante un determinado periodo.
-- =====================================================================

CREATE TABLE disponibilidad (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,

    colaborador_id      BIGINT NOT NULL,

    horas_disponibles   DECIMAL(5,2) NOT NULL,

    fecha_inicio        DATE NOT NULL,
    fecha_fin           DATE NULL,

    activo              BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_disponibilidad_colaborador
        FOREIGN KEY (colaborador_id)
        REFERENCES colaborador(usuario_id),

    CONSTRAINT chk_disponibilidad_horas
        CHECK (horas_disponibles > 0)
) ENGINE=InnoDB;


-- =====================================================================
-- 5. HABILIDADES
-- =====================================================================

CREATE TABLE categoria_habilidad (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,

    nombre              VARCHAR(80) NOT NULL UNIQUE,

    descripcion         VARCHAR(300) NULL
) ENGINE=InnoDB;


CREATE TABLE habilidad (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,

    nombre              VARCHAR(100) NOT NULL,

    categoria_id        BIGINT NOT NULL,

    activa              BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_habilidad_categoria
        FOREIGN KEY (categoria_id)
        REFERENCES categoria_habilidad(id),

    CONSTRAINT uq_habilidad_nombre_categoria
        UNIQUE (nombre, categoria_id)
) ENGINE=InnoDB;


-- =====================================================================
-- 6. HABILIDADES DEL COLABORADOR
-- =====================================================================
--
-- El colaborador declara:
--   - habilidad
--   - nivel de dominio
--
-- Posteriormente puede presentar un certificado para validarla.
-- El RM revisa el certificado.
-- =====================================================================

CREATE TABLE colaborador_habilidad (
    colaborador_id      BIGINT NOT NULL,

    habilidad_id        BIGINT NOT NULL,

    nivel_dominio       VARCHAR(20) NOT NULL,

    estado_validacion   VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',

    PRIMARY KEY (colaborador_id, habilidad_id),

    CONSTRAINT fk_colhab_colaborador
        FOREIGN KEY (colaborador_id)
        REFERENCES colaborador(usuario_id),

    CONSTRAINT fk_colhab_habilidad
        FOREIGN KEY (habilidad_id)
        REFERENCES habilidad(id),

    CONSTRAINT chk_colhab_nivel
        CHECK (
            nivel_dominio IN (
                'BASICO',
                'INTERMEDIO',
                'AVANZADO'
            )
        ),

    CONSTRAINT chk_colhab_validacion
        CHECK (
            estado_validacion IN (
                'PENDIENTE',
                'VALIDADA'
            )
        )
) ENGINE=InnoDB;


-- =====================================================================
-- 7. CERTIFICADOS
-- =====================================================================
--
-- Un certificado siempre está asociado a una habilidad que el
-- colaborador ya declaró.
-- =====================================================================

CREATE TABLE certificado (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,

    colaborador_id      BIGINT NOT NULL,
    habilidad_id        BIGINT NOT NULL,

    archivo_url         VARCHAR(500) NOT NULL,

    estado              VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',

    motivo_rechazo      VARCHAR(300) NULL,

    revisado_por        BIGINT NULL,

    fecha_subida        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_revision      DATETIME NULL,

    CONSTRAINT fk_certificado_colaborador_habilidad
        FOREIGN KEY (colaborador_id, habilidad_id)
        REFERENCES colaborador_habilidad(
            colaborador_id,
            habilidad_id
        ),

    CONSTRAINT fk_certificado_revisor
        FOREIGN KEY (revisado_por)
        REFERENCES usuario(id),

    CONSTRAINT chk_certificado_estado
        CHECK (
            estado IN (
                'PENDIENTE',
                'APROBADO',
                'RECHAZADO'
            )
        )
) ENGINE=InnoDB;


-- =====================================================================
-- 8. PROYECTOS
-- =====================================================================
--
-- Flujo:
--
-- PM crea
--     ↓
-- EN_REVISION
--     ↓
-- RM revisa
--     ↓
-- ACTIVO / RECHAZADO
--
-- Un proyecto activo puede pasar a:
--   EN_ESPERA
--   FINALIZADO
--   CANCELADO
-- =====================================================================

CREATE TABLE proyecto (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,

    nombre                  VARCHAR(150) NOT NULL,

    descripcion             VARCHAR(1000) NULL,

    fecha_inicio            DATE NULL,

    fecha_fin_estimada      DATE NULL,

    estado                  VARCHAR(20) NOT NULL DEFAULT 'EN_REVISION',

    prioridad               VARCHAR(10) NOT NULL,

    justificacion_prioridad VARCHAR(500) NOT NULL,

    motivo_rechazo          VARCHAR(500) NULL,

    presupuesto_solicitado    DECIMAL(12,2) NULL,
    justificacion_presupuesto VARCHAR(500) NULL,
    presupuesto               DECIMAL(12,2) NULL,

    colaboradores_requeridos INT NOT NULL DEFAULT 1,

    pm_id                   BIGINT NOT NULL,

    rm_revisor_id           BIGINT NULL,

    fecha_creacion          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_proyecto_pm
        FOREIGN KEY (pm_id)
        REFERENCES usuario(id),

    CONSTRAINT fk_proyecto_rm
        FOREIGN KEY (rm_revisor_id)
        REFERENCES usuario(id),

    CONSTRAINT chk_proyecto_estado
        CHECK (
            estado IN (
                'EN_REVISION',
                'RECHAZADO',
                'ACTIVO',
                'EN_ESPERA',
                'CANCELADO',
                'FINALIZADO'
            )
        ),

    CONSTRAINT chk_proyecto_prioridad
        CHECK (
            prioridad IN (
                'ALTA',
                'MEDIA',
                'BAJA'
            )
        ),

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

    CONSTRAINT fk_projhab_proyecto
        FOREIGN KEY (proyecto_id)
        REFERENCES proyecto(id),

    CONSTRAINT fk_projhab_habilidad
        FOREIGN KEY (habilidad_id)
        REFERENCES habilidad(id),

    CONSTRAINT chk_projhab_nivel
        CHECK (
            nivel_requerido IS NULL OR
            nivel_requerido IN (
                'BASICO',
                'INTERMEDIO',
                'AVANZADO'
            )
        ),

    CONSTRAINT chk_projhab_cantidad
        CHECK (cantidad_personas > 0)
) ENGINE=InnoDB;


-- =====================================================================
-- 10. ASIGNACIONES / POSTULACIONES
-- =====================================================================
--
-- FLUJO 1: COLABORADOR SOLICITA UNIRSE
--
-- colaborador
--     ↓
-- SOLICITADA_COLABORADOR
--     ↓
-- PM + RM reciben notificación
--     ↓
-- ambos aprueban
--     ↓
-- ACTIVA
--
-- Si PM o RM rechaza:
--     ↓
-- RECHAZADA
--
-- La misma tabla también permite que el PM proponga directamente
-- a un colaborador.
--
-- IMPORTANTE:
-- aprobado_por_pm = TRUE
-- Y
-- aprobado_por_rm = TRUE
-- son necesarios para activar la asignación.
-- =====================================================================

CREATE TABLE asignacion (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,

    proyecto_id         BIGINT NOT NULL,

    colaborador_id      BIGINT NOT NULL,

    horas_semanales     DECIMAL(5,2) NOT NULL,

    origen              VARCHAR(30) NOT NULL, -- 'PROPUESTA_PM' | 'PROPUESTA_RM' | 'SOLICITADA_COLABORADOR'

    estado              VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE',

    aprobado_por_pm     BOOLEAN NOT NULL DEFAULT FALSE,

    aprobado_por_rm     BOOLEAN NOT NULL DEFAULT FALSE,

    fecha_aprobacion_pm DATETIME NULL,

    fecha_aprobacion_rm DATETIME NULL,

    rechazado_por       BIGINT NULL,

    motivo_rechazo      VARCHAR(300) NULL,

    motivo_finalizacion VARCHAR(30) NULL,

    fecha_solicitud     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    fecha_activacion    DATETIME NULL,

    fecha_finalizacion  DATETIME NULL,

    CONSTRAINT fk_asignacion_proyecto
        FOREIGN KEY (proyecto_id)
        REFERENCES proyecto(id),

    CONSTRAINT fk_asignacion_colaborador
        FOREIGN KEY (colaborador_id)
        REFERENCES colaborador(usuario_id),

    CONSTRAINT fk_asignacion_rechazado_por
        FOREIGN KEY (rechazado_por)
        REFERENCES usuario(id),

    CONSTRAINT chk_asignacion_origen
        CHECK (
            origen IN ('PROPUESTA_PM','PROPUESTA_RM','SOLICITADA_COLABORADOR')
        ),

    CONSTRAINT chk_asignacion_estado
        CHECK (
            estado IN (
                'PENDIENTE',
                'ACTIVA',
                'RECHAZADA',
                'FINALIZADA'
            )
        ),

    CONSTRAINT chk_asignacion_horas
        CHECK (horas_semanales > 0),

    CONSTRAINT chk_asignacion_finalizacion
        CHECK (
            motivo_finalizacion IS NULL OR
            motivo_finalizacion IN (
                'BAJO_DESEMPENO',
                'PROYECTO_CANCELADO',
                'OTRO'
            )
        ),

    CONSTRAINT uq_asignacion_proyecto_colaborador_estado
        UNIQUE (
            proyecto_id,
            colaborador_id,
            estado
        )
) ENGINE=InnoDB;


-- =====================================================================
-- 11. ACTIVIDADES
-- =====================================================================
--
-- El PM asigna actividades a colaboradores con asignación ACTIVA.
--
-- Flujo:
--
-- PENDIENTE
--    ↓
-- EN_PROGRESO
--    ↓
-- EN_REVISION
--    ↓
-- COMPLETADA
--
-- Las horas_estimadas cuentan como horas cumplidas únicamente cuando
-- la actividad está COMPLETADA.
-- =====================================================================

CREATE TABLE actividad (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,

    proyecto_id         BIGINT NOT NULL,

    colaborador_id      BIGINT NOT NULL,

    titulo              VARCHAR(200) NOT NULL,

    descripcion         VARCHAR(500) NULL,

    horas_estimadas     DECIMAL(6,2) NOT NULL,

    fecha_asignacion    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    fecha_limite        DATE NOT NULL,

    fecha_entrega       DATETIME NULL,

    estado              VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',

    creado_por          BIGINT NOT NULL,

    CONSTRAINT fk_actividad_proyecto
        FOREIGN KEY (proyecto_id)
        REFERENCES proyecto(id),

    CONSTRAINT fk_actividad_colaborador
        FOREIGN KEY (colaborador_id)
        REFERENCES colaborador(usuario_id),

    CONSTRAINT fk_actividad_creador
        FOREIGN KEY (creado_por)
        REFERENCES usuario(id),

    CONSTRAINT chk_actividad_horas
        CHECK (horas_estimadas > 0),

    CONSTRAINT chk_actividad_estado
        CHECK (
            estado IN (
                'PENDIENTE',
                'EN_PROGRESO',
                'EN_REVISION',
                'COMPLETADA'
            )
        )
) ENGINE=InnoDB;


-- =====================================================================
-- 12. CURSOS
-- =====================================================================
--
-- El Administrador crea los cursos.
-- Cada curso tiene una cantidad de horas.
-- =====================================================================

CREATE TABLE curso (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,

    nombre          VARCHAR(150) NOT NULL,

    descripcion     VARCHAR(500) NULL,

    horas           DECIMAL(6,2) NOT NULL,

    activo          BOOLEAN NOT NULL DEFAULT TRUE,

    creado_por      BIGINT NOT NULL,

    fecha_creacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_curso_creador
        FOREIGN KEY (creado_por)
        REFERENCES usuario(id),

    CONSTRAINT chk_curso_horas
        CHECK (horas > 0)
) ENGINE=InnoDB;


-- =====================================================================
-- 13. SOLICITUDES / ASIGNACIONES DE CURSOS
-- =====================================================================
--
-- CASO 1:
-- Colaborador encuentra un curso
--     ↓
-- SOLICITUD_PROPIA
--     ↓
-- RM recibe notificación
--     ↓
-- RM aprueba/rechaza
--
-- CASO 2:
-- RM asigna directamente un curso
--     ↓
-- ASIGNADO_POR_RM
--
-- Un curso solo suma horas cuando está COMPLETADO.
-- =====================================================================

CREATE TABLE colaborador_curso (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,

    colaborador_id      BIGINT NOT NULL,

    curso_id            BIGINT NOT NULL,

    origen              VARCHAR(25) NOT NULL,

    estado              VARCHAR(20) NOT NULL DEFAULT 'SOLICITADO',

    asignado_por        BIGINT NULL,

    fecha_solicitud     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    fecha_respuesta     DATETIME NULL,

    fecha_completado    DATETIME NULL,

    CONSTRAINT fk_colcurso_colaborador
        FOREIGN KEY (colaborador_id)
        REFERENCES colaborador(usuario_id),

    CONSTRAINT fk_colcurso_curso
        FOREIGN KEY (curso_id)
        REFERENCES curso(id),

    CONSTRAINT fk_colcurso_asignador
        FOREIGN KEY (asignado_por)
        REFERENCES usuario(id),

    CONSTRAINT chk_colcurso_origen
        CHECK (
            origen IN (
                'SOLICITUD_PROPIA',
                'ASIGNADO_POR_RM'
            )
        ),

    CONSTRAINT chk_colcurso_estado
        CHECK (
            estado IN (
                'SOLICITADO',
                'EN_CURSO',
                'COMPLETADO',
                'RECHAZADO'
            )
        )
) ENGINE=InnoDB;


-- =====================================================================
-- 14. PENALIZACIONES
-- =====================================================================
--
-- Permite registrar descuentos por:
--   - actividades tardías
--   - salida de proyecto
--   - otros motivos
-- =====================================================================

CREATE TABLE penalizacion (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,

    colaborador_id      BIGINT NOT NULL,

    proyecto_id         BIGINT NULL,

    actividad_id        BIGINT NULL,

    tipo                VARCHAR(30) NOT NULL,

    motivo              VARCHAR(500) NOT NULL,

    monto               DECIMAL(10,2) NOT NULL,

    fecha               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_penalizacion_colaborador
        FOREIGN KEY (colaborador_id)
        REFERENCES colaborador(usuario_id),

    CONSTRAINT fk_penalizacion_proyecto
        FOREIGN KEY (proyecto_id)
        REFERENCES proyecto(id),

    CONSTRAINT fk_penalizacion_actividad
        FOREIGN KEY (actividad_id)
        REFERENCES actividad(id),

    CONSTRAINT chk_penalizacion_tipo
        CHECK (
            tipo IN (
                'ACTIVIDAD_TARDIA',
                'SALIDA_PROYECTO',
                'OTRO'
            )
        ),

    CONSTRAINT chk_penalizacion_monto
        CHECK (monto > 0)
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

    sueldo_base_aplicado    DECIMAL(10,2) NOT NULL,

    bono                    DECIMAL(10,2) NOT NULL DEFAULT 0,

    penalizacion            DECIMAL(10,2) NOT NULL DEFAULT 0,

    pago_total              DECIMAL(10,2) NOT NULL,

    fecha_calculo           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_nomina_colaborador
        FOREIGN KEY (colaborador_id)
        REFERENCES colaborador(usuario_id),

    CONSTRAINT uq_nomina_periodo
        UNIQUE (colaborador_id, anio, mes),

    CONSTRAINT chk_nomina_mes
        CHECK (mes BETWEEN 1 AND 12)
) ENGINE=InnoDB;


-- =====================================================================
-- 16. FOROS
-- =====================================================================
--
-- Hay dos tipos:
--
-- GENERAL
--   proyecto_id = NULL
--
-- PROYECTO
--   proyecto_id = ID del proyecto
--
-- La visibilidad puede ser:
--   PUBLICO
--   PRIVADO
--
-- Las reglas de acceso se validan en Spring.
-- =====================================================================

CREATE TABLE foro (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,

    proyecto_id         BIGINT NULL,

    tipo                VARCHAR(15) NOT NULL,

    visibilidad         VARCHAR(15) NOT NULL DEFAULT 'PUBLICO',

    nombre              VARCHAR(150) NOT NULL,

    fecha_creacion      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_foro_proyecto
        FOREIGN KEY (proyecto_id)
        REFERENCES proyecto(id),

    CONSTRAINT chk_foro_tipo
        CHECK (
            tipo IN (
                'GENERAL',
                'PROYECTO'
            )
        ),

    CONSTRAINT chk_foro_visibilidad
        CHECK (
            visibilidad IN (
                'PUBLICO',
                'PRIVADO'
            )
        )
) ENGINE=InnoDB;


-- =====================================================================
-- 17. PUBLICACIONES DEL FORO
-- =====================================================================

CREATE TABLE publicacion_foro (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,

    foro_id         BIGINT NOT NULL,

    autor_id        BIGINT NOT NULL,

    titulo          VARCHAR(200) NOT NULL,

    contenido       TEXT NOT NULL,

    fecha_creacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_publicacion_foro
        FOREIGN KEY (foro_id)
        REFERENCES foro(id),

    CONSTRAINT fk_publicacion_autor
        FOREIGN KEY (autor_id)
        REFERENCES usuario(id)
) ENGINE=InnoDB;


-- =====================================================================
-- 18. ETIQUETAS
-- =====================================================================

CREATE TABLE etiqueta (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,

    nombre  VARCHAR(50) NOT NULL UNIQUE
) ENGINE=InnoDB;


CREATE TABLE publicacion_etiqueta (
    publicacion_id  BIGINT NOT NULL,

    etiqueta_id     BIGINT NOT NULL,

    PRIMARY KEY (
        publicacion_id,
        etiqueta_id
    ),

    CONSTRAINT fk_pubetiqueta_publicacion
        FOREIGN KEY (publicacion_id)
        REFERENCES publicacion_foro(id),

    CONSTRAINT fk_pubetiqueta_etiqueta
        FOREIGN KEY (etiqueta_id)
        REFERENCES etiqueta(id)
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

    fecha_creacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_respuesta_publicacion
        FOREIGN KEY (publicacion_id)
        REFERENCES publicacion_foro(id),

    CONSTRAINT fk_respuesta_autor
        FOREIGN KEY (autor_id)
        REFERENCES usuario(id)
) ENGINE=InnoDB;


-- =====================================================================
-- 20. VOTOS DE PUBLICACIONES
-- =====================================================================

CREATE TABLE voto_publicacion (
    usuario_id      BIGINT NOT NULL,

    publicacion_id  BIGINT NOT NULL,

    tipo            VARCHAR(10) NOT NULL,

    PRIMARY KEY (
        usuario_id,
        publicacion_id
    ),

    CONSTRAINT fk_votopub_usuario
        FOREIGN KEY (usuario_id)
        REFERENCES usuario(id),

    CONSTRAINT fk_votopub_publicacion
        FOREIGN KEY (publicacion_id)
        REFERENCES publicacion_foro(id),

    CONSTRAINT chk_votopub_tipo
        CHECK (
            tipo IN (
                'POSITIVO',
                'NEGATIVO'
            )
        )
) ENGINE=InnoDB;


-- =====================================================================
-- 21. VOTOS DE RESPUESTAS
-- =====================================================================

CREATE TABLE voto_respuesta (
    usuario_id      BIGINT NOT NULL,

    respuesta_id    BIGINT NOT NULL,

    tipo            VARCHAR(10) NOT NULL,

    PRIMARY KEY (
        usuario_id,
        respuesta_id
    ),

    CONSTRAINT fk_votoresp_usuario
        FOREIGN KEY (usuario_id)
        REFERENCES usuario(id),

    CONSTRAINT fk_votoresp_respuesta
        FOREIGN KEY (respuesta_id)
        REFERENCES respuesta_foro(id),

    CONSTRAINT chk_votoresp_tipo
        CHECK (
            tipo IN (
                'POSITIVO',
                'NEGATIVO'
            )
        )
) ENGINE=InnoDB;


-- =====================================================================
-- 22. CONVERSACIONES / CHAT
-- =====================================================================
--
-- IMPORTANTE:
--
-- Tanto el chat GRUPAL como el PRIVADO pertenecen a un proyecto.
--
-- GRUPAL:
--   proyecto_id = X
--   participan todos los integrantes.
--
-- PRIVADA:
--   proyecto_id = X
--   participan solamente dos integrantes del proyecto.
--
-- Por lo tanto NO existe un chat privado "fuera" de un proyecto.
-- =====================================================================

CREATE TABLE conversacion (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,

    tipo                VARCHAR(15) NOT NULL,

    proyecto_id         BIGINT NOT NULL,

    fecha_creacion      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_conversacion_proyecto
        FOREIGN KEY (proyecto_id)
        REFERENCES proyecto(id),

    CONSTRAINT chk_conversacion_tipo
        CHECK (
            tipo IN (
                'GRUPAL',
                'PRIVADA'
            )
        )
) ENGINE=InnoDB;


-- =====================================================================
-- 23. PARTICIPANTES DE CONVERSACIÓN
-- =====================================================================

CREATE TABLE conversacion_usuario (
    conversacion_id     BIGINT NOT NULL,

    usuario_id          BIGINT NOT NULL,

    PRIMARY KEY (
        conversacion_id,
        usuario_id
    ),

    CONSTRAINT fk_conversacion_usuario_conversacion
        FOREIGN KEY (conversacion_id)
        REFERENCES conversacion(id),

    CONSTRAINT fk_conversacion_usuario_usuario
        FOREIGN KEY (usuario_id)
        REFERENCES usuario(id)
) ENGINE=InnoDB;


-- =====================================================================
-- 24. MENSAJES
-- =====================================================================

CREATE TABLE mensaje (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,

    conversacion_id     BIGINT NOT NULL,

    autor_id            BIGINT NOT NULL,

    contenido           TEXT NOT NULL,

    fecha_hora          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_mensaje_conversacion
        FOREIGN KEY (conversacion_id)
        REFERENCES conversacion(id),

    CONSTRAINT fk_mensaje_autor
        FOREIGN KEY (autor_id)
        REFERENCES usuario(id)
) ENGINE=InnoDB;


-- =====================================================================
-- 25. NOTIFICACIONES
-- =====================================================================
--
-- Una sola tabla para TODOS los roles.
--
-- El campo `tipo` permite filtrar:
--
--   PROYECTOS
--   ASIGNACIONES
--   ACTIVIDADES
--   CURSOS
--   HABILIDADES
--   MENSAJES
--   HORAS
--   SISTEMA
--
-- El destinatario está determinado por usuario_id.
--
-- Ejemplos:
--
-- RM:
--   NUEVO_PROYECTO
--   POSTULACION_PROYECTO
--   CERTIFICADO_SUBIDO
--   CURSO_SOLICITADO
--   HORAS_INSUFICIENTES
--   ACTIVIDAD_RETRASADA
--
-- PM:
--   PROYECTO_APROBADO
--   PROYECTO_RECHAZADO
--   POSTULACION_PROYECTO
--   ASIGNACION_APROBADA_RM
--   ACTIVIDAD_EN_REVISION
--   NUEVO_MENSAJE
--
-- COLABORADOR:
--   ASIGNACION_APROBADA
--   ASIGNACION_RECHAZADA
--   CURSO_APROBADO
--   CURSO_RECHAZADO
--   CERTIFICADO_APROBADO
--   CERTIFICADO_RECHAZADO
--   NUEVO_MENSAJE
--
-- ADMIN:
--   NOTIFICACIONES_ADMINISTRATIVAS
-- =====================================================================

CREATE TABLE notificacion (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,

    usuario_id          BIGINT NOT NULL,

    tipo                VARCHAR(50) NOT NULL,

    categoria           VARCHAR(30) NOT NULL,

    titulo              VARCHAR(150) NOT NULL,

    descripcion         VARCHAR(400) NULL,

    entidad             VARCHAR(60) NULL,

    entidad_id          BIGINT NULL,

    leida               BOOLEAN NOT NULL DEFAULT FALSE,

    fecha_creacion      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_notificacion_usuario
        FOREIGN KEY (usuario_id)
        REFERENCES usuario(id),

    CONSTRAINT chk_notificacion_categoria
        CHECK (
            categoria IN (
                'PROYECTO',
                'ASIGNACION',
                'ACTIVIDAD',
                'CURSO',
                'HABILIDAD',
                'MENSAJE',
                'HORAS',
                'SISTEMA'
            )
        )
) ENGINE=InnoDB;


-- =====================================================================
-- 26. CONFIGURACIÓN DEL SISTEMA
-- =====================================================================

CREATE TABLE configuracion_sistema (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,

    clave           VARCHAR(60) NOT NULL UNIQUE,

    valor           VARCHAR(255) NOT NULL,

    descripcion     VARCHAR(300) NULL
) ENGINE=InnoDB;


-- =====================================================================
-- 27. AUDITORÍA
-- =====================================================================

CREATE TABLE log_auditoria (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,

    usuario_id      BIGINT NULL,

    accion          VARCHAR(60) NOT NULL,

    entidad         VARCHAR(60) NOT NULL,

    entidad_id      BIGINT NULL,

    detalle         VARCHAR(500) NULL,

    fecha_hora      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    ip              VARCHAR(45) NULL,

    CONSTRAINT fk_log_usuario
        FOREIGN KEY (usuario_id)
        REFERENCES usuario(id)
) ENGINE=InnoDB;


