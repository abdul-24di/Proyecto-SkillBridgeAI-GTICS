-- ============================================================================
-- SkillBridge AI - Datos de demostracion para los Reportes del RM
-- Compatible con: BDs_SQL/skillbridge_db_v4.sql
-- Base de datos:   MySQL 8.0.19+
-- ============================================================================
--
-- PROPOSITO
--   Llenar /rm/reportes/recursos (y su Excel) con horas en los ultimos
--   12 meses, en proyectos de todos los estados y prioridades, para probar:
--   * el rango de meses "Desde" / "Hasta" (maximo 12 meses)
--   * los filtros de proyecto, estado y prioridad
--   * el Excel con o sin resumen, con una o dos hojas y con columnas elegidas
--   * la paginacion de las tablas (mas de 10 filas por colaborador y proyecto)
--   * /rm/reportes/horas-colaboradores con varios meses distintos
--
-- REQUISITO
--   Ejecutar antes BDs_SQL/datos_demo_rm_proyectos.sql (usuarios demo,
--   proyectos [DEMO] Migracion Cloud, Analitica Comercial y Proyecto
--   Finalizado). Este archivo agrega 3 proyectos y actividades COMPLETADA.
--
-- FECHAS
--   Las fechas son relativas a CURRENT_DATE: "m" = meses hacia atras
--   (0 = mes actual, 11 = hace 11 meses). Ninguna entrega queda en el futuro.
--
-- SEGURIDAD
--   * No elimina tablas ni registros.
--   * Se puede ejecutar varias veces: cada proyecto y cada actividad se crea
--     solo si no existe (mismo proyecto y mismo titulo).
--   * Las fechas se fijan al ejecutar: si pasan los meses, volver a cargar
--     la base demo para que las horas sigan en los ultimos 12 meses.
--
-- ESCENARIOS SUGERIDOS (usuario demo.rm@skillbridge.local / abc123)
--   1. Mes actual, sin filtros: 5 proyectos con horas (Migracion Cloud, Portal
--      de Atencion, Analitica Comercial, App Movil Ventas y Mesa de Ayuda) y
--      los 4 colaboradores demo; el resto aparece con 0 h.
--   2. Desde hace 11 meses hasta el mes actual: todo el historico (12 meses).
--   3. Rango de 3 meses + Prioridad "Alta": solo [DEMO] Migracion Cloud y
--      [DEMO] Portal de Atencion (este con horas solo del mes actual).
--   4. Estado "Finalizado" o "Cancelado" + un rango de 12 meses: proyectos
--      cerrados con horas solo en meses antiguos.
--   5. Proyecto "[DEMO] App Movil Ventas" + rango de 6 meses: un proyecto con
--      horas en todos esos meses y 4 colaboradores.
--   6. Exportar desmarcando la hoja "Proyectos", o el resumen, o columnas como
--      "Presupuesto asignado" (la nota de presupuesto desaparece con ella).
--   7. Un mes sin horas para un filtro (por ejemplo, Prioridad "Baja" y el mes
--      actual con Estado "Cancelado"): el Excel sale con las secciones vacias.
-- ============================================================================
ROLLBACK;

SET NAMES utf8mb4;
USE skillbridge_db;

START TRANSACTION;

SET @demo_pm_id := (SELECT id FROM usuario WHERE correo = 'demo.pm@skillbridge.local');
SET @demo_rm_id := (SELECT id FROM usuario WHERE correo = 'demo.rm@skillbridge.local');

-- --------------------------------------------------------------------------
-- 1. Proyectos adicionales (otras prioridades y un proyecto cancelado)
-- --------------------------------------------------------------------------
INSERT INTO proyecto (
    nombre, descripcion, fecha_inicio, fecha_fin_estimada, estado, prioridad,
    justificacion_prioridad, presupuesto_solicitado, justificacion_presupuesto,
    presupuesto, colaboradores_requeridos, horas_semanales_requeridas,
    pm_id, rm_revisor_id, fecha_creacion
)
SELECT
    '[DEMO] App Movil Ventas',
    'Aplicacion movil para la fuerza de ventas con catalogo y pedidos sin conexion.',
    DATE_FORMAT(CURRENT_DATE - INTERVAL 7 MONTH, '%Y-%m-01'), CURRENT_DATE + INTERVAL 4 MONTH,
    'ACTIVO', 'MEDIA',
    'Mejora el registro de pedidos en campo, pero no bloquea otras iniciativas.',
    42000.00, 'Desarrollo movil, backend de sincronizacion y pruebas en campo.',
    42000.00, 4, 24.00, @demo_pm_id, @demo_rm_id,
    CURRENT_TIMESTAMP - INTERVAL 8 MONTH
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM proyecto WHERE nombre = '[DEMO] App Movil Ventas');

INSERT INTO proyecto (
    nombre, descripcion, fecha_inicio, fecha_fin_estimada, estado, prioridad,
    justificacion_prioridad, presupuesto_solicitado, justificacion_presupuesto,
    presupuesto, colaboradores_requeridos, horas_semanales_requeridas,
    pm_id, rm_revisor_id, fecha_creacion
)
SELECT
    '[DEMO] Mesa de Ayuda Interna',
    'Mesa de ayuda para incidencias internas con base de conocimiento.',
    DATE_FORMAT(CURRENT_DATE - INTERVAL 11 MONTH, '%Y-%m-01'), CURRENT_DATE + INTERVAL 2 MONTH,
    'ACTIVO', 'BAJA',
    'Ordena la atencion interna; no tiene fecha comprometida con clientes.',
    18000.00, 'Configuracion de la herramienta y documentacion.',
    18000.00, 2, 12.00, @demo_pm_id, @demo_rm_id,
    CURRENT_TIMESTAMP - INTERVAL 12 MONTH
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM proyecto WHERE nombre = '[DEMO] Mesa de Ayuda Interna');

INSERT INTO proyecto (
    nombre, descripcion, fecha_inicio, fecha_fin_estimada, estado, prioridad,
    justificacion_prioridad, presupuesto_solicitado, justificacion_presupuesto,
    presupuesto, colaboradores_requeridos, horas_semanales_requeridas,
    pm_id, rm_revisor_id, motivo_cierre, fecha_creacion
)
SELECT
    '[DEMO] Integracion ERP',
    'Integracion del ERP con el sistema de facturacion. Cancelado por cambio de proveedor.',
    DATE_FORMAT(CURRENT_DATE - INTERVAL 10 MONTH, '%Y-%m-01'), CURRENT_DATE - INTERVAL 4 MONTH,
    'CANCELADO', 'MEDIA',
    'Necesario para unificar la facturacion de las sedes.',
    30000.00, 'Desarrollo de conectores y pruebas de integracion.',
    30000.00, 3, 20.00, @demo_pm_id, @demo_rm_id,
    'La empresa cambio de proveedor de ERP y el alcance dejo de aplicar.',
    CURRENT_TIMESTAMP - INTERVAL 11 MONTH
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM proyecto WHERE nombre = '[DEMO] Integracion ERP');

-- --------------------------------------------------------------------------
-- 2. Actividades COMPLETADA repartidas en los ultimos 12 meses
--    m = meses hacia atras, dia = dia de entrega dentro de ese mes.
-- --------------------------------------------------------------------------
INSERT INTO actividad (proyecto_id, colaborador_id, titulo, descripcion, horas_estimadas,
                       fecha_asignacion, fecha_limite, fecha_marcado_revision, fecha_entrega,
                       estado_entrega, veces_devuelta, estado, creado_por)
SELECT p.id, u.id, d.titulo, 'Actividad de demostracion para el reporte de recursos.', d.horas,
       DATE_FORMAT(CURRENT_DATE - INTERVAL d.m MONTH, '%Y-%m-01'),
       LAST_DAY(CURRENT_DATE - INTERVAL d.m MONTH),
       LEAST(DATE_FORMAT(CURRENT_DATE - INTERVAL d.m MONTH, '%Y-%m-01')
                 + INTERVAL (d.dia - 2) DAY + INTERVAL 17 HOUR, CURRENT_TIMESTAMP - INTERVAL 2 HOUR),
       LEAST(DATE_FORMAT(CURRENT_DATE - INTERVAL d.m MONTH, '%Y-%m-01')
                 + INTERVAL (d.dia - 1) DAY + INTERVAL 10 HOUR, CURRENT_TIMESTAMP - INTERVAL 1 HOUR),
       IF(d.dia > 24, 'TARDIA', 'A_TIEMPO'), 0, 'COMPLETADA', @demo_pm_id
FROM (
    -- Mes actual
    SELECT 0 AS m, 3 AS dia, '[DEMO] Migracion Cloud' AS proyecto, 'demo.colaborador4@skillbridge.local' AS correo, '[DEMO-REP] Pipeline de despliegue' AS titulo, 30.00 AS horas
    UNION ALL SELECT 0, 4,  '[DEMO] App Movil Ventas',      'demo.colaborador1@skillbridge.local', '[DEMO-REP] Sincronizacion de pedidos', 34.50
    UNION ALL SELECT 0, 5,  '[DEMO] App Movil Ventas',      'demo.colaborador3@skillbridge.local', '[DEMO-REP] Metricas de uso movil', 16.00
    UNION ALL SELECT 0, 2,  '[DEMO] Mesa de Ayuda Interna', 'demo.colaborador3@skillbridge.local', '[DEMO-REP] Catalogo de incidencias', 12.00
    -- Hace 1 mes
    UNION ALL SELECT 1, 6,  '[DEMO] Migracion Cloud',       'demo.colaborador1@skillbridge.local', '[DEMO-REP] Adaptacion de servicios Java', 40.00
    UNION ALL SELECT 1, 12, '[DEMO] Migracion Cloud',       'demo.colaborador2@skillbridge.local', '[DEMO-REP] Red y seguridad en AWS', 38.00
    UNION ALL SELECT 1, 20, '[DEMO] App Movil Ventas',      'demo.colaborador1@skillbridge.local', '[DEMO-REP] Pantallas de catalogo', 26.00
    UNION ALL SELECT 1, 25, '[DEMO] App Movil Ventas',      'demo.colaborador4@skillbridge.local', '[DEMO-REP] Publicacion en tiendas', 14.00
    UNION ALL SELECT 1, 15, '[DEMO] Analitica Comercial',   'demo.colaborador3@skillbridge.local', '[DEMO-REP] Limpieza de datos de ventas', 22.00
    -- Hace 2 meses
    UNION ALL SELECT 2, 8,  '[DEMO] Migracion Cloud',       'demo.colaborador2@skillbridge.local', '[DEMO-REP] Plan de migracion por olas', 32.00
    UNION ALL SELECT 2, 14, '[DEMO] App Movil Ventas',      'demo.colaborador1@skillbridge.local', '[DEMO-REP] API de productos', 36.00
    UNION ALL SELECT 2, 18, '[DEMO] App Movil Ventas',      'demo.colaborador2@skillbridge.local', '[DEMO-REP] Notificaciones push', 18.00
    UNION ALL SELECT 2, 22, '[DEMO] Mesa de Ayuda Interna', 'demo.colaborador4@skillbridge.local', '[DEMO-REP] Monitoreo de la mesa de ayuda', 10.00
    -- Hace 3 meses
    UNION ALL SELECT 3, 5,  '[DEMO] Proyecto Finalizado',   'demo.colaborador3@skillbridge.local', '[DEMO-REP] Informe de cierre', 12.00
    UNION ALL SELECT 3, 10, '[DEMO] Migracion Cloud',       'demo.colaborador4@skillbridge.local', '[DEMO-REP] Contenedores base', 28.00
    UNION ALL SELECT 3, 16, '[DEMO] App Movil Ventas',      'demo.colaborador3@skillbridge.local', '[DEMO-REP] Diseno de reportes moviles', 20.00
    UNION ALL SELECT 3, 26, '[DEMO] Analitica Comercial',   'demo.colaborador2@skillbridge.local', '[DEMO-REP] Fuentes de datos externas', 24.00
    -- Hace 4 meses
    UNION ALL SELECT 4, 7,  '[DEMO] Proyecto Finalizado',   'demo.colaborador1@skillbridge.local', '[DEMO-REP] Correccion de errores finales', 18.00
    UNION ALL SELECT 4, 12, '[DEMO] Integracion ERP',       'demo.colaborador1@skillbridge.local', '[DEMO-REP] Conector de facturas', 30.00
    UNION ALL SELECT 4, 19, '[DEMO] App Movil Ventas',      'demo.colaborador4@skillbridge.local', '[DEMO-REP] Entorno de pruebas movil', 12.00
    UNION ALL SELECT 4, 23, '[DEMO] Mesa de Ayuda Interna', 'demo.colaborador3@skillbridge.local', '[DEMO-REP] Base de conocimiento inicial', 9.50
    -- Hace 5 meses
    UNION ALL SELECT 5, 9,  '[DEMO] Proyecto Finalizado',   'demo.colaborador2@skillbridge.local', '[DEMO-REP] Despliegue a produccion', 26.00
    UNION ALL SELECT 5, 15, '[DEMO] Integracion ERP',       'demo.colaborador2@skillbridge.local', '[DEMO-REP] Colas de integracion', 22.00
    UNION ALL SELECT 5, 21, '[DEMO] App Movil Ventas',      'demo.colaborador1@skillbridge.local', '[DEMO-REP] Prototipo de la app', 28.00
    -- Hace 6 meses
    UNION ALL SELECT 6, 4,  '[DEMO] Proyecto Finalizado',   'demo.colaborador1@skillbridge.local', '[DEMO-REP] Modulo de consultas', 34.00
    UNION ALL SELECT 6, 11, '[DEMO] Integracion ERP',       'demo.colaborador3@skillbridge.local', '[DEMO-REP] Mapeo de campos contables', 16.00
    UNION ALL SELECT 6, 17, '[DEMO] App Movil Ventas',      'demo.colaborador3@skillbridge.local', '[DEMO-REP] Levantamiento de requisitos', 14.00
    UNION ALL SELECT 6, 24, '[DEMO] Mesa de Ayuda Interna', 'demo.colaborador2@skillbridge.local', '[DEMO-REP] Instalacion de la herramienta', 8.00
    -- Hace 7 meses
    UNION ALL SELECT 7, 8,  '[DEMO] Proyecto Finalizado',   'demo.colaborador3@skillbridge.local', '[DEMO-REP] Pruebas de regresion', 20.00
    UNION ALL SELECT 7, 14, '[DEMO] Integracion ERP',       'demo.colaborador1@skillbridge.local', '[DEMO-REP] Analisis del ERP', 24.00
    UNION ALL SELECT 7, 27, '[DEMO] Proyecto Finalizado',   'demo.colaborador4@skillbridge.local', '[DEMO-REP] Copias de seguridad', 10.00
    -- Hace 8 meses
    UNION ALL SELECT 8, 6,  '[DEMO] Proyecto Finalizado',   'demo.colaborador1@skillbridge.local', '[DEMO-REP] Servicios de autenticacion', 38.00
    UNION ALL SELECT 8, 13, '[DEMO] Integracion ERP',       'demo.colaborador2@skillbridge.local', '[DEMO-REP] Ambiente de integracion', 18.00
    UNION ALL SELECT 8, 22, '[DEMO] Mesa de Ayuda Interna', 'demo.colaborador3@skillbridge.local', '[DEMO-REP] Flujos de atencion', 11.00
    -- Hace 9 meses
    UNION ALL SELECT 9, 10, '[DEMO] Proyecto Finalizado',   'demo.colaborador2@skillbridge.local', '[DEMO-REP] Infraestructura del proyecto', 30.00
    UNION ALL SELECT 9, 18, '[DEMO] Integracion ERP',       'demo.colaborador3@skillbridge.local', '[DEMO-REP] Inventario de reportes ERP', 12.00
    -- Hace 10 meses
    UNION ALL SELECT 10, 7, '[DEMO] Proyecto Finalizado',   'demo.colaborador1@skillbridge.local', '[DEMO-REP] Arquitectura inicial', 24.00
    UNION ALL SELECT 10, 21,'[DEMO] Mesa de Ayuda Interna', 'demo.colaborador4@skillbridge.local', '[DEMO-REP] Evaluacion de herramientas', 6.00
    -- Hace 11 meses
    UNION ALL SELECT 11, 9, '[DEMO] Mesa de Ayuda Interna', 'demo.colaborador3@skillbridge.local', '[DEMO-REP] Encuesta de necesidades', 8.00
    UNION ALL SELECT 11, 16,'[DEMO] Proyecto Finalizado',   'demo.colaborador3@skillbridge.local', '[DEMO-REP] Analisis de datos historicos', 15.00
) AS d
JOIN proyecto p ON p.nombre = d.proyecto
JOIN usuario u ON u.correo = d.correo
WHERE NOT EXISTS (
    SELECT 1 FROM actividad a WHERE a.proyecto_id = p.id AND a.titulo = d.titulo
);

COMMIT;

-- --------------------------------------------------------------------------
-- 3. Verificacion: horas completadas por mes y proyecto (lo que suma el reporte)
-- --------------------------------------------------------------------------
SELECT DATE_FORMAT(a.fecha_entrega, '%Y-%m') AS mes,
       p.nombre AS proyecto, p.estado, p.prioridad,
       COUNT(*) AS tareas, SUM(a.horas_estimadas) AS horas
FROM actividad a
JOIN proyecto p ON p.id = a.proyecto_id
WHERE a.estado = 'COMPLETADA' AND p.nombre LIKE '[DEMO]%'
GROUP BY mes, p.nombre, p.estado, p.prioridad
ORDER BY mes DESC, p.nombre;
