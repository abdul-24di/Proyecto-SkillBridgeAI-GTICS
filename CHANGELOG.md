# CHANGELOG - SkillBridge AI

## Sesión 1 de Octubre 2026

---

## 1. Cursos de horario fijo y evidencia solo para autodidactas (TASK-043 parcial) — commits `996932f`, `3a140f4`, `2bd7b68`

### Descripción
Solo los cursos autodidactas piden evidencia de finalización. Los cursos con horario fijo (no autodidactas) ya no la piden: cuando llega su `fecha_fin`, la inscripción `EN_CURSO` pasa sola a `COMPLETADO`, se registra `fecha_completado`, se audita `COMPLETAR_CURSO_PROGRAMADO` y se notifica `CURSO_COMPLETADO` al colaborador. Sus horas se suman al total mensual igual que las de un curso aprobado por el RM.

No hay tarea programada: la comprobación se ejecuta cuando el colaborador abre Explorar (catálogo), "Mis cursos" del perfil o el resumen de horas del dashboard.

### Archivos modificados
- `src/main/java/com/pucp/skillb_ia/service/col/ColaboradorCursoService.java` — Nuevo `completarCursosProgramadosVencidos`, llamado desde `listarCursosDisponibles` y `listarMisCursos` (ambos dejan de ser `readOnly`). `subirEvidencia` rechaza los cursos no autodidactas.
- `src/main/java/com/pucp/skillb_ia/service/col/ColaboradorActividadService.java` — Inyecta `ColaboradorCursoService` y completa los cursos vencidos antes de calcular `obtenerResumenHoras`.
- `src/main/java/com/pucp/skillb_ia/service/NotificacionService.java` — Las notificaciones con entidad `COLABORADOR_CURSO` del colaborador enlazan a `/colaborador/perfil#mis-cursos` (antes `#`).
- `src/main/java/com/pucp/skillb_ia/service/rm/RmCursoService.java` — La bandeja muestra "Evidencia rechazada" (insignia roja) para una inscripción `EN_CURSO` que conserva una evidencia.
- `src/main/resources/templates/col/col-perfil.html` — Ancla `#mis-cursos`; botón, modal y aviso de rechazo de evidencia solo para autodidactas; aviso con la fecha de autocompletado para los cursos de horario fijo.
- `BDs_SQL/Pruebas_Colaborador/Pruebas_col.sql` — Nuevo script de datos de prueba manual del colaborador (cursos autodidactas y de horario fijo con inscripciones en varios estados, actividades, asignaciones, notificaciones y evaluación). Reemplaza a `Pruebas_Colaborador_Unificado.sql`, que se eliminó.

### Alcance pendiente
- `fecha_completado` es la fecha en que el colaborador abre la vista, no `fecha_fin`: si entra en un mes posterior, las horas se imputan a ese mes. Las vistas del RM no disparan la comprobación.
- Un curso de horario fijo sin `fecha_fin` queda `EN_CURSO` indefinidamente.
- Sin cambios de esquema y sin pruebas nuevas para estos casos.

---

## Sesión 30 de Septiembre 2026

---

## 1. Configuración de Parámetros Globales (Administrador) — crear parámetro nuevo

### Descripción
Antes solo se podían editar los parámetros de `configuracion_sistema` ya existentes (cargados por SQL). Ahora el Admin puede crear parámetros nuevos desde la UI.

### Archivos modificados
- `src/main/java/com/pucp/skillb_ia/service/AdminConfiguracionService.java` — Nuevo método `crear(clave, descripcion, valorInicial, admin)`: normaliza la clave (mayúsculas, sin espacios), valida que no esté vacía ni duplicada, y audita el cambio.
- `src/main/java/com/pucp/skillb_ia/controller/AdminViewController.java` — Nuevo endpoint `POST /admin/configuracion/crear`.
- `src/main/resources/templates/admin/admin-configuracion.html` — Botón "Nuevo parámetro" + modal de creación.

---

## 2. Envío real de correos (activación de cuenta y recuperación de contraseña)

### Descripción
`EmailService` era un stub que solo logueaba a consola (no había SMTP configurado). Ahora envía correos reales por Gmail SMTP, con fallback automático a solo-log si no hay credenciales configuradas (para no romper a quien no las tenga en su máquina).

### Archivos modificados
- `pom.xml` — Agregada dependencia `spring-boot-starter-mail`.
- `src/main/resources/application.properties` — Configuración SMTP (`smtp.gmail.com:587`) vía variables de entorno `MAIL_USERNAME`/`MAIL_PASSWORD` (con default vacío), más `app.mail.from` y `app.base-url` para construir enlaces completos en los correos.
- `src/main/java/com/pucp/skillb_ia/service/EmailService.java` — Reemplazado el logging por envío real vía `JavaMailSender`. El remitente se muestra como "SkillBridge AI" en vez del correo crudo.

### Cómo configurarlo localmente
Cada quien necesita su propia cuenta de Gmail con una "contraseña de aplicación" (Google no permite la contraseña normal por SMTP). Se configura como variables de entorno `MAIL_USERNAME` y `MAIL_PASSWORD` en la configuración de ejecución de IntelliJ — **nunca en `application.properties`**, para no exponer credenciales en el repositorio.

---

## 3. Fix de seguridad: rutas públicas de autenticación faltantes

### Bug encontrado
Al probar el flujo real de activación y recuperación de contraseña (sin sesión iniciada, como le pasaría a cualquier usuario nuevo), Spring Security redirigía todo a `/login` porque `SecurityConfig` tenía una lista de rutas públicas desactualizada (`/auth/**`, `/reset-password`, `/activate`, que no corresponden a ninguna ruta real del proyecto) y le faltaban las rutas reales: `/activar-cuenta`, `/recuperar`, `/verificar-codigo`, `/nueva-contrasena`, `/contrasena-actualizada`, `/login.html`. Esto rompía por completo la activación de cuenta y la recuperación de contraseña para cualquier usuario sin sesión activa.

### Archivos modificados
- `src/main/java/com/pucp/skillb_ia/config/SecurityConfig.java` — Corregida la lista de `permitAll()` con las rutas reales del flujo de autenticación.

### Verificación
Probado de punta a punta sin sesión previa: creación de usuario individual y por carga masiva (CSV), envío real de correo (Gmail e institucional PUCP), activación de cuenta vía enlace, login con la contraseña elegida, y recuperación de contraseña completa (código de 6 dígitos → nueva contraseña → login).

---

## 4. Finalización de cursos con evidencia (implementación parcial de TASK-043)

### Descripción
El colaborador ahora ve en su perfil los cursos `EN_CURSO`, `EVIDENCIA_PENDIENTE` y `COMPLETADO`. Para un curso en progreso puede adjuntar una evidencia PDF, JPG o PNG de hasta 10 MB. La evidencia se guarda mediante `ArchivoAlmacenamientoService`, queda asociada a la inscripción (`ColaboradorCurso`) y se notifica a todos los Resource Managers.

El backend del RM permite aprobar o rechazar la evidencia. Aprobar cambia la inscripción a `COMPLETADO` y registra `fecha_completado`; rechazar la devuelve a `EN_CURSO`, conserva el archivo y el motivo, y permite volver a subir evidencia. Las horas del curso completado se suman al total mensual usado por el dashboard y por el cálculo informativo del bono.

### Archivos modificados

**Modelo y base de datos:**
- `src/main/java/com/pucp/skillb_ia/model/ColaboradorCurso.java` — Nuevos campos `evidenciaUrl` y `fechaEvidencia`.
- `src/main/java/com/pucp/skillb_ia/model/enums/EstadoColaboradorCurso.java` — Nuevo estado `EVIDENCIA_PENDIENTE`.
- `BDs_SQL/skillbridge_db_v4.sql` — `colaborador_curso.estado` pasa a `VARCHAR(25)`, el `CHECK` admite `EVIDENCIA_PENDIENTE` y se agregan `evidencia_url` y `fecha_evidencia`. El mismo commit también movió `proyecto.documento_contexto_url` y `proyecto_habilidad_requerida.horas_semanales` a sus `CREATE TABLE`, pero no retiró los `ALTER TABLE ... ADD COLUMN` del final del script (ver *Alcance pendiente*).
- `src/main/java/com/pucp/skillb_ia/repository/ColaboradorCursoRepository.java` — Nuevo `findByIdAndColaborador`, usado para validar que la inscripción sea del colaborador autenticado.

**Colaborador:**
- `src/main/java/com/pucp/skillb_ia/service/col/ColaboradorCursoService.java` — Lista los cursos del perfil, valida propiedad/estado/tipo/tamaño, almacena la evidencia, audita `SUBIR_EVIDENCIA_CURSO` y notifica a los RM. Una inscripción en `EVIDENCIA_PENDIENTE` también impide volver a solicitar el mismo curso (igual que en la asignación directa del RM).
- `src/main/java/com/pucp/skillb_ia/controller/ColaboradorViewController.java` — Expone `misCursosEnCurso` y añade `POST /colaborador/perfil/cursos/{inscripcionId}/evidencia`.
- `src/main/resources/templates/col/col-perfil.html` — Nueva tarjeta "Mis cursos", estados, motivo de rechazo, enlace al archivo y modal de carga. La experiencia profesional queda de solo lectura y administrada por el Admin.
- `src/main/java/com/pucp/skillb_ia/service/col/ColaboradorActividadService.java` — Suma las horas de cursos completados durante el mes a `horasTrabajadasMes`.

**Resource Manager:**
- `src/main/java/com/pucp/skillb_ia/service/rm/RmCursoService.java` — Soporta `EVIDENCIA_PENDIENTE`, aprobación y rechazo de evidencias, finalización del curso y notificaciones al colaborador.
- `src/main/java/com/pucp/skillb_ia/controller/RmViewController.java` — Nuevos endpoints `POST /rm/cursos/solicitudes/{id}/evidencia/aprobar` y `/rechazar`.
- `src/main/java/com/pucp/skillb_ia/dto/RmCursoView.java` — La fila de inscripción expone la URL y si la evidencia está pendiente.

### Migración requerida para bases existentes

El commit actualizó el esquema de instalaciones nuevas, pero **no agregó una migración idempotente**. Antes de desplegar sobre una base existente se deben añadir las columnas, ampliar `estado` y reemplazar `chk_colcurso_estado`. No basta con la versión actual de `BDs_SQL/migracion_cursos.sql`, porque esa migración solo agrega `motivo_respuesta`.

### Alcance pendiente y verificación
- **Instalación nueva corregida (TASK-048):** `skillbridge_db_v4.sql` conserva `horas_semanales` y `documento_contexto_url` dentro de sus `CREATE TABLE` y ya no intenta agregarlas otra vez con `ALTER TABLE`. Las bases existentes siguen usando las sentencias de la sesión del 28 de septiembre.
- La plantilla `rm-solicitudes-cursos.html` todavía no muestra `EVIDENCIA_PENDIENTE`, el archivo ni botones que invoquen los nuevos endpoints; por UI el RM aún no puede decidir la evidencia.
- No se registran `revisado_por` ni `fecha_revision`, y aprobar/rechazar la evidencia no genera auditoría.
- No existe alerta al llegar la fecha final ni correo de aprobación del curso con fechas y horas.
- Las notificaciones de curso dirigidas al colaborador todavía resuelven a `#`; las de RM abren la bandeja general. *(Actualización 1 de octubre: las del colaborador ya abren `/colaborador/perfil#mis-cursos`.)*
- No se añadieron pruebas específicas del flujo. La suite existente se ejecutó el 30 de septiembre de 2026: **454 pruebas, 0 fallos**.

---

## 5. Validaciones del catálogo de cursos (Admin) — commit `42b57fc`

### Descripción
Crear y editar un curso en `/admin/cursos` ahora exige más datos, validados en el servidor (`AdminCursoService`) y también en el formulario:
- **Categoría obligatoria** al crear y al editar.
- **Fechas de inicio y fin obligatorias**, salvo en los cursos autodidactas, que usan `horas` como duración. En el formulario, marcar "autodidacta" quita el `required` y los asteriscos de las fechas.
- La fecha de fin no puede ser anterior a la de inicio.
- **Al crear**, la fecha de inicio no puede ser anterior a hoy. Al editar no se aplica, para que un curso ya iniciado conserve su fecha original.

### Archivos modificados
- `src/main/java/com/pucp/skillb_ia/service/AdminCursoService.java` — Nuevo `validarCategoriaYFechas`, usado por `crear` y `editar`; validación de fecha de inicio ≥ hoy solo en `crear`.
- `src/main/resources/templates/admin/admin-cursos.html` — Categoría y fechas con `required`, `min` = hoy en la fecha de inicio del modal de creación y script que libera las fechas si el curso es autodidacta.

### Consideraciones
- Un curso existente sin categoría, o no autodidacta y sin fechas, ya no se puede guardar al editarlo hasta completar esos datos.
- `editar` sigue sin actualizar `lugar` ni `institucion`, y `horas` solo se valida en el HTML (`min="0.5"`), no en el servidor.
- Sin pruebas automatizadas del CRUD de cursos (TASK-005).

---

## 6. Política de contraseñas unificada — commit `42b57fc`

### Descripción
Toda contraseña nueva debe tener **al menos 8 caracteres, una mayúscula, un número y un símbolo** (antes bastaban 6 caracteres, salvo en el perfil del Colaborador). La regla se aplica en:
- Activación de cuenta (`POST /activar-cuenta`) y recuperación (`POST /nueva-contrasena`), validada en `AuthViewController` con la expresión `(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9\s]).{8,}`.
- Cambio de contraseña desde el perfil de Admin, RM y PM (`AdminPerfilService`, `RmPerfilService`, `PmPerfilService`, con la misma expresión) y del Colaborador (`ColaboradorPerfilService.validarPassword`, que pasó de 6 a 8 caracteres y ya exigía mayúscula, número y símbolo).

Además, la activación exige **aceptar la política de tratamiento de datos**: el checkbox ahora se envía como `aceptaPolitica` y, si falta, el servidor rechaza la activación.

Las contraseñas que ya existen (incluidas las de los usuarios demo) siguen funcionando: la regla solo se aplica al definir una contraseña nueva.

### Archivos modificados
- `src/main/java/com/pucp/skillb_ia/controller/AuthViewController.java` — Constante `PASSWORD_VALIDA` y parámetro `aceptaPolitica`.
- `src/main/java/com/pucp/skillb_ia/service/{AdminPerfilService,rm/RmPerfilService,pm/PmPerfilService,col/ColaboradorPerfilService}.java` — Nueva regla y mensaje de error.
- `src/main/resources/templates/auth/{activar-cuenta,nueva-contrasena}.html` — `required`, `minlength="8"`, `pattern` y mensajes por campo; los formularios usan `novalidate data-custom-validate`.
- `src/main/resources/static/js/auth-js/auth.js` — Validación propia que muestra el error debajo de cada campo (en lugar del globo del navegador) y verifica que la confirmación coincida.
- `src/main/resources/static/css/auth-css/auth.css` — Estilos de `.auth-field-error` y del campo con error.
- `src/main/resources/templates/{admin/admin-perfil,rm/rm-perfil,pm/pm-perfil,col/col-perfil}.html` — `minlength="8"`, `pattern` y `required` en el cambio de contraseña.

### Consideraciones
- Si el navegador no valida (p. ej. una petición manual), el servidor redirige a `?error` con un mensaje genérico que no indica qué regla falló.
- La aceptación de la política no se guarda ni se audita; solo se exige para activar.
- El Colaborador valida con `Character` (acepta mayúsculas y dígitos no ASCII, como "Ñ"), mientras que Auth y los perfiles de Admin/RM/PM usan la expresión regular (solo `A-Z` y `0-9`). La diferencia solo afecta a contraseñas con caracteres acentuados.
- Sin pruebas automatizadas de la nueva regla. La suite completa se ejecutó después del commit: **454 pruebas, 0 fallos**.

---

## Sesión 28 de Septiembre 2026

---

## 1. CRUD Catálogo de Cursos (Administrador)

### Archivos nuevos
- `src/main/java/com/pucp/skillb_ia/service/AdminCursoService.java` — Lógica para listar, crear, editar y activar/desactivar cursos con validación de fechas.
- `src/main/resources/templates/admin/admin-cursos.html` — Vista completa con tabla, modal de creación y modal de edición.
- `src/main/java/com/pucp/skillb_ia/repository/CursoRepository.java` — Añadido `findAllByOrderByNombreAsc()`.

### Archivos modificados
- `src/main/java/com/pucp/skillb_ia/controller/AdminViewController.java` — Rutas GET/POST para `/admin/cursos`, `/admin/cursos/crear`, `/admin/cursos/editar`, `/admin/cursos/alternar`.
- `src/main/resources/templates/fragments/admin-topbar.html` — Agregado enlace a la sección Cursos.

### Bug resuelto
- Thymeleaf no permite usar `mod` como variable de iteración (es una palabra reservada matemática). Se renombró a `m` en el `th:each` del selector de modalidades, corrigiendo la pantalla en blanco al acceder a Cursos.

---

## 2. Sistema de Evaluaciones / Feedback de Desempeño

### Archivos nuevos
- `src/main/java/com/pucp/skillb_ia/model/Evaluacion.java` — Nueva entidad JPA con campos: `colaborador`, `evaluador`, `asignacion`, `calificacion` (1-5), `comentarios`, `fechaCreacion`.
- `src/main/java/com/pucp/skillb_ia/repository/EvaluacionRepository.java` — Repositorio JPA para persistir evaluaciones.
- `src/main/java/com/pucp/skillb_ia/service/EvaluacionService.java` — Métodos `evaluarAsignacion(...)` y `obtenerEvaluacionesPorColaborador(Long colaboradorId)`.

### Archivos modificados
**Backend:**
- `src/main/java/com/pucp/skillb_ia/service/rm/RmAsignacionService.java` — Inyectado `EvaluacionService`. Método `finalizar()` ahora recibe `calificacion` e `feedback`.
- `src/main/java/com/pucp/skillb_ia/service/pm/PmAsignacionService.java` — Inyectado `EvaluacionService`. Método `finalizar()` ahora recibe `calificacion` e `feedback`.
- `src/main/java/com/pucp/skillb_ia/controller/RmViewController.java` — Endpoint `POST /asignaciones/{id}/finalizar` acepta `calificacion` y `feedback`.
- `src/main/java/com/pucp/skillb_ia/controller/PmViewController.java` — Endpoint `POST /asignaciones/{id}/finalizar` acepta `calificacion` y `feedback`.
- `src/main/java/com/pucp/skillb_ia/controller/ColaboradorViewController.java` — Inyectado `EvaluacionService`, se expone `evaluaciones` al modelo del perfil.

**Frontend:**
- `src/main/resources/templates/rm/rm-detalle-asignacion-activa.html` — Formulario de finalización ampliado con selector de estrellas (1-5) y textarea de feedback.
- `src/main/resources/templates/pm/pm-asignaciones-proyecto.html` — Botón "Finalizar" abre un modal con selector de estrellas y feedback antes de confirmar.
- `src/main/resources/templates/rm/rm-perfil-colaborador.html` — Nueva tarjeta "Historial de Desempeño" que muestra todas las evaluaciones del colaborador.
- `src/main/resources/templates/col/col-perfil.html` — Nueva sección "Feedback y Evaluaciones" visible para el colaborador en su propio perfil.

### Script SQL requerido
```sql
CREATE TABLE evaluacion (
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
```

---

## 3. Horas Semanales por Habilidad en Creación de Proyecto (PM)

### Descripción
Anteriormente había un único campo global de "Horas semanales esperadas por colaborador". Ahora cada habilidad requerida tiene sus propias horas semanales específicas.

### Archivos modificados
**Modelo / DTO:**
- `src/main/java/com/pucp/skillb_ia/model/ProyectoHabilidadRequerida.java` — Añadido campo `horasSemanales (DECIMAL 5,2)`.
- `src/main/java/com/pucp/skillb_ia/dto/PmProyectoView.java` — `RequisitoHabilidad` incluye `horasSemanales`.
- `src/main/java/com/pucp/skillb_ia/dto/RmProyectoView.java` — `RequisitoTalento` incluye `horasSemanales`.

**Servicio:**
- `src/main/java/com/pucp/skillb_ia/service/pm/PmProyectoService.java` — Método `crear()` acepta `List<BigDecimal> horasSemanalesHab` y `MultipartFile documentoProyecto`. Al construir la vista, se propaga `horasSemanales` al DTO.
- `src/main/java/com/pucp/skillb_ia/service/rm/RmProyectoConsultaService.java` — Propaga `horasSemanales` al `RequisitoTalento`.

**Controlador:**
- `src/main/java/com/pucp/skillb_ia/controller/PmViewController.java` — Endpoint `POST /proyectos/crear` acepta `horasSemanalesHab` y `documentoProyecto`.

**Frontend:**
- `src/main/resources/templates/pm/pm-crear-proyecto.html` — Carrito de habilidades requeridas incluye columna "Horas/sem" por habilidad. Se eliminó el campo global de horas. Se añadió `enctype="multipart/form-data"` para soportar archivos.
- `src/main/resources/templates/rm/rm-revision-proyecto.html` — Cada requerimiento de habilidad muestra badge con horas/sem. Se añadió enlace para ver documento de contexto.
- `src/main/resources/templates/rm/rm-detalle-proyecto.html` — Idem anterior.

### Script SQL requerido
```sql
ALTER TABLE proyecto_habilidad_requerida ADD COLUMN horas_semanales DECIMAL(5,2);
```

---

## 4. Documento de Contexto de Proyecto (PM → RM)

### Descripción
El PM puede adjuntar un archivo (PDF, DOCX, etc.) al crear un proyecto para darle más contexto al RM sobre los requisitos del proyecto.

### Archivos modificados
**Modelo:**
- `src/main/java/com/pucp/skillb_ia/model/Proyecto.java` — Añadido campo `documentoContextoUrl (VARCHAR 500)`.

**Servicio:**
- `src/main/java/com/pucp/skillb_ia/service/pm/PmProyectoService.java` — Si se sube un archivo, lo guarda en S3 (o almacenamiento configurado) y guarda la URL en `Proyecto.documentoContextoUrl`.

**Frontend:**
- `src/main/resources/templates/pm/pm-crear-proyecto.html` — Input `type="file"` en la sección "Contexto para Resource Manager".
- `src/main/resources/templates/rm/rm-revision-proyecto.html` — Botón "Ver Documento" visible si hay URL adjunta.
- `src/main/resources/templates/rm/rm-detalle-proyecto.html` — Idem anterior.

### Script SQL requerido
```sql
ALTER TABLE proyecto ADD COLUMN documento_contexto_url VARCHAR(500);
```

---

## Resumen de Scripts SQL necesarios

```sql
-- Ejecutar en la base de datos (local o AWS) antes de arrancar la app

-- 1. Nueva tabla de evaluaciones
CREATE TABLE evaluacion (
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

-- 2. Horas semanales por habilidad requerida
ALTER TABLE proyecto_habilidad_requerida ADD COLUMN horas_semanales DECIMAL(5,2);

-- 3. URL de documento de contexto del proyecto
ALTER TABLE proyecto ADD COLUMN documento_contexto_url VARCHAR(500);
```
