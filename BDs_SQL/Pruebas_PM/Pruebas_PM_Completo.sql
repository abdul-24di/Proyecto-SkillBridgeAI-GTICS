SET SQL_SAFE_UPDATES = 0;

-- 1. Asegurar que haya usuarios extra para probar asignaciones
INSERT IGNORE INTO usuario (correo, password_hash, nombre, apellido, rol_id, activo, cargo_id, horas_disponibles) VALUES
('colab1.pm@skillbridge.com', '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom', 'Laura', 'Gomez', (SELECT id FROM rol WHERE nombre='COLABORADOR' LIMIT 1), 1, (SELECT id FROM cargo WHERE nombre='Frontend Developer'), 40),
('colab2.pm@skillbridge.com', '$2a$10$Oxug4hl7T.T7x8vUmeUfEu9g04cLzSg31v1G1zQlWEw3pLNib8Xom', 'Diego', 'Perez', (SELECT id FROM rol WHERE nombre='COLABORADOR' LIMIT 1), 1, (SELECT id FROM cargo WHERE nombre='Backend Developer'), 40);

-- 2. Proyectos del PM principal (pm@skillbridge.com) en varios estados
-- EN_REVISION (esperando al RM)
INSERT IGNORE INTO proyecto (nombre, descripcion, estado, prioridad, justificacion_prioridad, colaboradores_requeridos, horas_semanales_requeridas, pm_id, fecha_creacion)
VALUES ('Sistema de Facturación v2', 'Renovación completa del sistema de facturación.', 'EN_REVISION', 'ALTA', 'Es crítico para finanzas', 4, 80, (SELECT id FROM usuario WHERE correo='pm@skillbridge.com' LIMIT 1), NOW());

-- EN_ESPERA (RM asignó presupuesto, buscando gente)
INSERT IGNORE INTO proyecto (nombre, descripcion, estado, prioridad, justificacion_prioridad, colaboradores_requeridos, horas_semanales_requeridas, presupuesto, pm_id, fecha_creacion)
VALUES ('Campaña Marketing 2027', 'Plataforma para la nueva campaña.', 'EN_ESPERA', 'MEDIA', 'Importante para el próximo año', 2, 40, 15000.00, (SELECT id FROM usuario WHERE correo='pm@skillbridge.com' LIMIT 1), NOW());

-- FINALIZADO (Ya terminó)
INSERT IGNORE INTO proyecto (nombre, descripcion, estado, prioridad, justificacion_prioridad, colaboradores_requeridos, horas_semanales_requeridas, presupuesto, pm_id, fecha_creacion)
VALUES ('Migración de Correos', 'Migrar servidores antiguos a Exchange.', 'FINALIZADO', 'BAJA', 'Completado con éxito', 1, 10, 5000.00, (SELECT id FROM usuario WHERE correo='pm@skillbridge.com' LIMIT 1), NOW());

-- 3. Asignaciones (Para probar pestaña de asignaciones)
-- Solicitud de colaborador PENDIENTE de que el PM la acepte
INSERT IGNORE INTO asignacion (proyecto_id, colaborador_id, horas_semanales, origen, mensaje_solicitud, estado, aprobado_por_pm, aprobado_por_rm)
VALUES (
    (SELECT id FROM proyecto WHERE nombre='Portal de Clientes' LIMIT 1),
    (SELECT id FROM usuario WHERE correo='colab1.pm@skillbridge.com' LIMIT 1),
    10, 'SOLICITUD_COLABORADOR', 'Me interesa mucho trabajar en este proyecto de facturación.', 'PENDIENTE_PM', FALSE, FALSE
);

-- Colaborador activo en el proyecto
INSERT IGNORE INTO asignacion (proyecto_id, colaborador_id, horas_semanales, origen, estado, aprobado_por_pm, aprobado_por_rm)
VALUES (
    (SELECT id FROM proyecto WHERE nombre='Portal de Clientes' LIMIT 1),
    (SELECT id FROM usuario WHERE correo='colab2.pm@skillbridge.com' LIMIT 1),
    20, 'PROPUESTA_PM', 'ACTIVA', TRUE, TRUE
);

-- 4. Actividades en Portal de Clientes
-- EN_REVISION (Lista para que el PM confirme o devuelva)
INSERT IGNORE INTO actividad (proyecto_id, colaborador_id, titulo, descripcion, horas_estimadas, fecha_limite, estado, creado_por, comentario_colaborador)
VALUES (
    (SELECT id FROM proyecto WHERE nombre='Portal de Clientes' LIMIT 1),
    (SELECT id FROM usuario WHERE correo='colab2.pm@skillbridge.com' LIMIT 1),
    'Diseñar mockups del dashboard',
    'Crear los diseños de alta fidelidad en Figma.',
    10.00,
    NOW() + INTERVAL 2 DAY,
    'EN_REVISION',
    (SELECT id FROM usuario WHERE correo='pm@skillbridge.com' LIMIT 1),
    'Ya terminé los diseños, por favor revisarlos.'
);

-- PENDIENTE (Para probar Eliminar y Editar)
INSERT IGNORE INTO actividad (proyecto_id, colaborador_id, titulo, descripcion, horas_estimadas, fecha_limite, estado, creado_por)
VALUES (
    (SELECT id FROM proyecto WHERE nombre='Portal de Clientes' LIMIT 1),
    (SELECT id FROM usuario WHERE correo='colab2.pm@skillbridge.com' LIMIT 1),
    'Configurar base de datos en AWS',
    'Crear el clúster de RDS y configurar los security groups.',
    8.00,
    NOW() + INTERVAL 7 DAY,
    'PENDIENTE',
    (SELECT id FROM usuario WHERE correo='pm@skillbridge.com' LIMIT 1)
);

-- EN_PROGRESO
INSERT IGNORE INTO actividad (proyecto_id, colaborador_id, titulo, descripcion, horas_estimadas, fecha_limite, estado, creado_por)
VALUES (
    (SELECT id FROM proyecto WHERE nombre='Portal de Clientes' LIMIT 1),
    (SELECT id FROM usuario WHERE correo='colab2.pm@skillbridge.com' LIMIT 1),
    'Programar el login',
    'Usar Spring Security para el inicio de sesión.',
    15.00,
    NOW() + INTERVAL 5 DAY,
    'EN_PROGRESO',
    (SELECT id FROM usuario WHERE correo='pm@skillbridge.com' LIMIT 1)
);

SET SQL_SAFE_UPDATES = 1;
