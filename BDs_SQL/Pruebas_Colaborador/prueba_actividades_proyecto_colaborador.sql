INSERT INTO actividad (proyecto_id, colaborador_id, titulo, descripcion, horas_estimadas, fecha_limite, estado, creado_por)
VALUES
(
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Compartido'),
    (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com'),
    'Diseñar el módulo de login',
    'Crear la pantalla de inicio de sesión con validación de credenciales y mensajes de error.',
    8.00,
    '2026-09-30',
    'PENDIENTE',
    (SELECT pm_id FROM proyecto WHERE nombre = '[DEMO] Proyecto Compartido')
),
(
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Compartido'),
    (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com'),
    'Conectar endpoint de autenticación',
    'Integrar el frontend con el endpoint /auth/login del backend.',
    5.50,
    '2026-09-25',
    'EN_PROGRESO',
    (SELECT pm_id FROM proyecto WHERE nombre = '[DEMO] Proyecto Compartido')
),
(
    (SELECT id FROM proyecto WHERE nombre = '[DEMO] Proyecto Compartido'),
    (SELECT id FROM usuario WHERE correo = 'col@skillbridge.com'),
    'Redactar casos de prueba',
    'Documentar los casos de prueba manuales para el flujo de login.',
    3.00,
    '2026-09-10',
    'COMPLETADA',
    (SELECT pm_id FROM proyecto WHERE nombre = '[DEMO] Proyecto Compartido')
);

-- Para que la "Redactar casos de prueba" ya se vea como completada a tiempo
UPDATE actividad
SET estado_entrega = 'A_TIEMPO', fecha_entrega = '2026-09-09 15:00:00'
WHERE titulo = 'Redactar casos de prueba';