# Avances y Funcionalidades Implementadas (PM y Colaborador)

Este documento resume todas las funcionalidades, validaciones y correcciones implementadas recientemente en la rama eature/cruds para los roles de **Project Manager (PM)** y **Colaborador**, así como mejoras a nivel global.

## 1. Mejoras Globales y Arquitectura
- **Manejo Global de Errores (500 a Alertas amigables):** Se implementó un GlobalExceptionHandler que captura excepciones de negocio (IllegalArgumentException, IllegalStateException) y de seguridad. En lugar de mostrar la pantalla de error genérica de Spring (Whitelabel Error Page), redirige al usuario a la página anterior mostrando una alerta limpia en la interfaz usando RedirectAttributes.

## 2. Project Manager (PM)
- **Dashboard Modernizado:** Se rediseñaron las tarjetas de métricas superiores del Dashboard. Ahora utilizan íconos SVG de Tabler y calculan sus valores en tiempo real usando consultas Thymeleaf (ej. conteo exacto de proyectos "Activos" y "En revisión").
- **Creación de Proyectos - Validaciones:**
  - **Fechas:** Validación robusta (tanto visual en JS como estricta en Backend) para evitar que la "Fecha de fin estimada" sea menor a la "Fecha de inicio".
  - **Valores Numéricos:** Se bloquearon números negativos en los campos de Presupuesto, Horas e integrantes, estableciendo mínimos lógicos.
  - **Presupuesto (UX):** El campo de presupuesto ahora cuenta con formato automático. Al escribir, se añaden separadores de miles y el símbolo de moneda para facilitar la lectura.
- **Creación de Proyectos - Requerimientos de Talento (Multi-habilidad):**
  - Se rediseñó la vista para permitir la selección de **múltiples habilidades** de forma dinámica (estilo "carrito de compras"), definiendo nivel y cantidad por cada una.
  - Se agregó una caja de texto libre para **"Otras habilidades"** no listadas en el catálogo. Lo ingresado aquí es interceptado por el backend e incrustado automáticamente en la descripción del proyecto para el Resource Manager.
- **Gestión de Actividades (CRUD Completo):**
  - Se implementó la lógica en el backend y los modales en el frontend para **Editar** y **Eliminar** actividades.
  - Reglas de negocio estrictas: No se pueden editar actividades ya completadas, y no se pueden eliminar actividades que ya están bajo revisión.

## 3. Colaborador
- **Foros y Comunidad (Modo Lectura vs Escritura):**
  - Se blindó la lógica del foro (ColaboradorForoService.java) para soportar foros de proyectos públicos. 
  - Si un PM hace público el foro de su proyecto, este aparecerá en la pestaña "Comunidad" para todos los colaboradores de la empresa.
  - **Modo Lectura:** Si un colaborador accede al foro de un proyecto en el que no tiene una asignación ACTIVA, la interfaz oculta automáticamente los botones de "Nueva publicación" y "Responder", mostrando un aviso de "Solo lectura". El backend bloquea cualquier petición POST forzada.

## 4. Scripts de Base de Datos (Testing)
Para facilitar las pruebas sin colapsar las bases de datos locales:
- Se unificaron los múltiples scripts de prueba del Colaborador en un solo archivo robusto (Pruebas_Colaborador_Unificado.sql).
- Se creó un script dedicado para probar todos los flujos del PM (Pruebas_PM_Completo.sql).
- **Resiliencia SQL:** Ambos scripts cuentan ahora con INSERT IGNORE, subconsultas protegidas con LIMIT 1, desactivación del Modo Seguro (SQL_SAFE_UPDATES = 0) y limpieza de codificación UTF-8 BOM, lo que garantiza que puedan ser ejecutados múltiples veces en MySQL Workbench sin lanzar errores de sintaxis ni de llaves duplicadas.
