# Resumen de Actualizaciones (PM y Colaborador)

Este documento resume todas las actualizaciones e implementaciones funcionales realizadas recientemente en el proyecto SkillBridge AI.

Última verificación: **30 de septiembre de 2026**, commit `42b57fc`.

## 1. Chat Funcional (PM y Colaboradores)
Se ha reemplazado la maqueta de chat (Javascript simulado) por un sistema de chat real, impulsado por AJAX para soportar actualizaciones en tiempo real (Polling).

*   **Para el PM (`pm-chat.html`):** Ahora el PM puede ver todos los proyectos que gestiona. Al hacer clic en un proyecto, puede conversar grupalmente con todos los miembros del equipo.
*   **Para el Colaborador (`col-chat.html`):** Se creó la interfaz de chat que antes no existía. El colaborador ahora solo visualiza y chatea en los proyectos en los que tiene una asignación en estado `ACTIVA`.
*   **Backend (`PmChatService` / `ColChatService`):** Lógica que recupera conversaciones y mensajes reales de la base de datos, validando accesos según el rol.
*   **REST Controllers (`PmChatRestController` / `ColChatRestController`):** Proporcionan los endpoints `/api/chat/mensajes` y `/api/chat/enviar` para que el frontend mantenga el flujo de mensajería asíncrono sin recargar la página.

## 2. Foro Dinámico y Editor Enriquecido (PM y Colaboradores)
Se ha implementado un foro funcional que soporta formato enriquecido e imágenes, útil para ser un centro de preguntas y respuestas (Q&A).

*   **Editor Quill JS (`col-foro-quill.js` / `pm-foro-quill.js`):** Integración completa de editor de texto para dar formato (negrita, cursiva, listas, títulos) y la capacidad de insertar múltiples imágenes directamente.
*   **Subida de Imágenes:** Endpoint backend `POST /foro/upload-imagen` que guarda físicamente las imágenes en el directorio `uploads/foro/` y retorna la URL pública.
*   **Para el Colaborador:**
    *   Se eliminó el código Javascript simulado (`foro-data.js`).
    *   Se implementaron las vistas reales en Thymeleaf (`col-foros.html` y `col-foro-detalle.html`).
    *   El colaborador puede publicar temas nuevos o responder si pertenece al proyecto (validado en base de datos).
*   **Cambio en BD:** Para soportar el HTML que produce Quill JS, los campos `contenido` en `PublicacionForo` y `RespuestaForo` ahora se consideran `LONGTEXT`. *(Recuerda realizar este `ALTER TABLE` manualmente si no lo has hecho).*

## 3. Reportes Dinámicos (PM)
La sección de reportes pasó de ser visualizaciones estáticas a mostrar datos matemáticos calculados de la base de datos.

*   **Dashboard de Reportes (`pm-reportes.html`):**
    *   Iteración dinámica sobre los proyectos reales gestionados por el PM.
    *   Barras de progreso que reflejan exactamente el porcentaje de "Actividades Completadas" versus el total de actividades.
    *   Estados actualizados (Ej. *En curso*, *En espera*, *Completado*) que adaptan su color dinámicamente.
*   **Detalle de Reporte (`pm-reporte-detalle.html`):**
    *   Muestra el presupuesto real (aprobado o solicitado).
    *   Muestra la cantidad exacta de integrantes activos versus vacantes requeridas.
    *   Lista de colaboradores con su "Rendimiento" calculado como porcentaje de cuántas de sus tareas asignadas han completado.

## 4. Limpieza de Errores y Excepciones
*   Se solucionó el error **Whitelabel Error (500)** en la vista "Gestionar Asignaciones" causado por un campo SpEL incorrecto (`fechaAsignacion` en lugar de `fechaActivacion`).
*   Se corrigieron validaciones de vistas donde se crasheaba si un proyecto todavía no poseía un "Foro" o "Chat" creado en la base de datos (ahora se instancian on-demand o manejan el `null` suavemente).

## 5. Solución de Pruebas Unitarias y Seguridad
*   **Pruebas Unitarias (GitHub Actions):** Se arregló el error de compilación en el pipeline CI que impedía el build. Faltaba inyectar `certificadosColaborador` en el endpoint de perfil del Colaborador (`ColaboradorViewController.java`).
*   **Seguridad por Roles (`SecurityConfig.java`):** Se sustituyó el `permitAll()` global por restricciones estrictas de URL por rol:
    *   `/admin/**` → solo `ADMINISTRADOR`
    *   `/pm/**` → solo `PROJECT_MANAGER`
    *   `/rm/**` → solo `RESOURCE_MANAGER`
    *   `/colaborador/**` → solo `COLABORADOR`
*   Se agregaron las rutas de recursos estáticos de Tabler (`/tabler/**`, `/documentos/**`, `/plantillas/**`) a la lista de acceso público para evitar que el CSS del login se rompa.

## 6. Sincronización Base de Datos y Corrección de Login (17 Sep 2026)
*   **Error 403 Forbidden al hacer Login:** Los nombres de roles en `SecurityConfig.java` estaban abreviados (`PM`, `RM`, `ADMIN`) pero la base de datos los guarda completos (`PROJECT_MANAGER`, `RESOURCE_MANAGER`, `ADMINISTRADOR`). Se corrigieron para que coincidan.
*   **Error SQL `Select Habilidad;`:** Se eliminó una línea SQL inválida que fue escrita por error en `skillbridge_db_v4.sql` antes de la definición de la tabla `habilidad`, la cual interrumpía toda ejecución del script.
*   **Columnas faltantes en la BD (Error 500 al hacer Login):** El modelo Java (`Proyecto.java`, `Asignacion.java`, `Actividad.java`) tenía campos que no existían en el script SQL. Se corrigieron agregando las columnas al script y usando `ddl-auto=update` temporalmente para que Hibernate las creara automáticamente en la BD local:
    *   `proyecto.horas_semanales_requeridas`
    *   `asignacion.habilidades_relevantes`
    *   `asignacion.habilidad_solicitada_id`
    *   `actividad.evidencia_url`
    *   `actividad.comentario_colaborador`
*   **Despliegue en AWS EC2:** Se configuraron las credenciales de base de datos como variables de entorno en el servidor (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) para conectar Spring Boot a AWS RDS de forma segura sin exponer credenciales en el código.
*   **Puerto 8080 en AWS:** Se habilitó la regla de entrada en el Security Group de EC2 para el puerto `8080` (TCP, `0.0.0.0/0`).

## 7. Finalización de Cursos con Evidencia (30 Sep 2026)

La inscripción de un colaborador ahora puede avanzar de `EN_CURSO` a `EVIDENCIA_PENDIENTE` y, después de la revisión del RM, a `COMPLETADO`.

*   **Carga del colaborador:** Desde su perfil puede adjuntar un PDF, JPG o PNG de hasta 10 MB para una inscripción propia en estado `EN_CURSO`. La carga usa `ArchivoAlmacenamientoService`, se audita y notifica a los RM activos.
*   **Perfil:** La sección de cursos muestra inscripciones en curso, pendientes de evidencia y completadas, junto con el archivo, el estado y el motivo de rechazo cuando corresponda.
*   **Decisión del RM:** Existen endpoints para aprobar o rechazar. Aprobar fija `fechaCompletado`; rechazar exige motivo, devuelve la inscripción a `EN_CURSO` y permite reemplazar el archivo sin un máximo de intentos implementado.
*   **Horas:** Al aprobar la evidencia, las horas del curso se agregan al cálculo mensual de horas trabajadas y cuentan para la meta y el bono informativo. No reducen `horasDisponibles`.
*   **Pendientes de interfaz y trazabilidad:** La bandeja `rm-solicitudes-cursos.html` todavía no presenta el filtro `EVIDENCIA_PENDIENTE`, el enlace al archivo ni los botones de decisión. También faltan revisor, fecha y auditoría de aprobación/rechazo.
*   **Pendiente de base de datos:** `skillbridge_db_v4.sql` ya contiene los campos y el nuevo estado, pero falta una migración idempotente para bases existentes. `migracion_cursos.sql` no cubre `evidencia_url`, `fecha_evidencia`, el ancho de `estado` ni su `CHECK`.
*   **Script de instalación con error:** El mismo commit llevó `horas_semanales` y `documento_contexto_url` a los `CREATE TABLE`, pero dejó al final los `ALTER TABLE ... ADD COLUMN` de esas columnas. Una instalación nueva falla con `Duplicate column name` antes de cargar los datos de prueba (TASK-048).
*   **Cobertura:** La suite completa continúa en verde (454 pruebas), pero no hay pruebas específicas para este flujo.

## 8. Correo Real para Activación y Recuperación (30 Sep 2026)

*   **SMTP:** `EmailService` envía mediante Gmail cuando existen `MAIL_USERNAME` y `MAIL_PASSWORD`; si faltan, conserva el fallback a registro en el log.
*   **Enlaces absolutos:** `APP_BASE_URL` define la URL base usada en los mensajes.
*   **Seguridad:** Las rutas reales de activación y recuperación quedaron públicas en `SecurityConfig`, mientras las áreas por rol permanecen protegidas.
*   **Configuración administrativa:** El Admin puede crear parámetros desde `/admin/configuracion/crear`.
*   **Cobertura pendiente:** No hay pruebas automatizadas del envío SMTP ni de las rutas públicas de Auth.

## 9. Validaciones del Catálogo de Cursos del Admin (30 Sep 2026)

*   **Datos obligatorios:** La categoría siempre es obligatoria; las fechas de inicio y fin también, salvo en cursos autodidactas (el formulario las libera al marcar la casilla). Se valida en `AdminCursoService` y en `admin-cursos.html`.
*   **Fechas:** La fecha de fin no puede ser anterior a la de inicio y, al crear, la de inicio no puede ser anterior a hoy. Al editar se conserva la fecha original de un curso ya iniciado.
*   **Pendiente:** Editar aún no actualiza `lugar` ni `institucion`, y no hay pruebas del CRUD (TASK-005).

## 10. Política de Contraseñas Unificada (30 Sep 2026)

*   **Regla única:** Mínimo 8 caracteres, con una mayúscula, un número y un símbolo, en la activación, la recuperación y el cambio de contraseña de Admin, RM, PM y Colaborador. Antes, salvo el Colaborador, bastaban 6 caracteres.
*   **Activación:** Además exige aceptar la política de tratamiento de datos (`aceptaPolitica`), validada en el servidor.
*   **Formularios de Auth:** Muestran el error debajo de cada campo con estilos propios (`auth.js`, `auth.css`) y comprueban que la confirmación coincida.
*   **Compatibilidad:** Las contraseñas existentes y las de los usuarios demo siguen funcionando.
*   **Cobertura:** Suite completa en verde (454 pruebas) tras el cambio, sin pruebas específicas de la regla.

