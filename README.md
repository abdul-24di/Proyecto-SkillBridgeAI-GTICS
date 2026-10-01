# Proyecto-SkillBridgeAI-GTICS
Plataforma de gestión de talento, proyectos y conocimiento con IA — TEL137 GTICS 2026-2, PUCP

## Configuración local

La aplicación no guarda la contraseña de MySQL en el repositorio. Antes de
iniciarla, configura las variables de entorno de la sesión.

En PowerShell:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/skillbridge_db"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "tu_contrasena_local"
.\mvnw.cmd spring-boot:run
```

`DB_URL` usa `jdbc:mysql://localhost:3306/skillbridge_db` por defecto,
`DB_USERNAME` usa `root` y `UPLOAD_DIR` usa `uploads`. `DB_PASSWORD` queda
vacía si no se define.

### Correo de activación y recuperación

La aplicación usa Gmail SMTP cuando se configuran `MAIL_USERNAME` y
`MAIL_PASSWORD`; si están vacías, `EmailService` conserva el flujo y registra
el mensaje en el log sin intentar enviarlo. `MAIL_PASSWORD` debe ser una
contraseña de aplicación de Google, no la contraseña normal de la cuenta.

```powershell
$env:MAIL_USERNAME = "cuenta@gmail.com"
$env:MAIL_PASSWORD = "contraseña_de_aplicacion"
$env:APP_BASE_URL = "http://localhost:8080"
```

En despliegue, `APP_BASE_URL` debe ser la URL pública de la aplicación para que
los enlaces de activación sean válidos. Estas credenciales nunca deben
guardarse en `application.properties` ni versionarse.

Toda contraseña nueva (activación, recuperación y cambio desde el perfil de
cualquier rol) debe tener al menos 8 caracteres, una mayúscula, un número y un
símbolo. Para activar la cuenta también hay que aceptar la política de
tratamiento de datos. Las contraseñas existentes, incluidas las de los usuarios
demo, siguen siendo válidas para iniciar sesión.

## Base de datos y despliegue

Una instalación nueva debe ejecutar [`BDs_SQL/skillbridge_db_v4.sql`](BDs_SQL/skillbridge_db_v4.sql).

Desde TASK-048, `proyecto_habilidad_requerida.horas_semanales` y
`proyecto.documento_contexto_url` se definen únicamente dentro de sus
`CREATE TABLE`. El script ya no repite esas columnas mediante `ALTER TABLE` y
puede usarse directamente en instalaciones nuevas. Las bases existentes
anteriores a esas columnas siguen las sentencias de migración documentadas en
[`CHANGELOG.md`](CHANGELOG.md).
Si el entorno local o de nube ya tenía la versión anterior de la base, debe
ejecutar una sola vez [`BDs_SQL/migracion_solicitud_personal.sql`](BDs_SQL/migracion_solicitud_personal.sql)
antes de desplegar esta versión. La migración agrega la tabla
`solicitud_personal` sin borrar ni modificar registros existentes.

Para incorporar el flujo de Cursos del RM en una base ya creada con una
versión anterior de `skillbridge_db_v4.sql`, se debe ejecutar una sola vez
[`BDs_SQL/migracion_cursos.sql`](BDs_SQL/migracion_cursos.sql). Esta migración
añade el campo donde se conserva el motivo de rechazo o asignación directa y
puede ejecutarse nuevamente sin duplicarlo. Una instalación nueva que ejecute
el `skillbridge_db_v4.sql` actualizado no necesita esta migración.

El flujo de evidencia de finalización de cursos agregó posteriormente
`colaborador_curso.evidencia_url`, `fecha_evidencia`, el estado
`EVIDENCIA_PENDIENTE` y amplió `estado` a `VARCHAR(25)`. El repositorio todavía
no contiene una migración para esos cambios: en una base existente deben
aplicarse antes de desplegar. La versión actual de `migracion_cursos.sql` **no**
los incluye. El detalle y las limitaciones están en la sesión del 30 de
septiembre de [`CHANGELOG.md`](CHANGELOG.md).

El cargo del colaborador ahora es un catálogo (`cargo`) con tarifas por nivel
(Junior / Semi-Senior / Senior) que administra el Admin en
*Habilidades → Cargos y Matriz Salarial*. El `sueldo_base` se calcula con la
tarifa del cargo según el nivel del colaborador y se recalcula al cambiar las
tarifas, el cargo o el nivel. Una base creada antes de este cambio (con
`usuario.cargo` como texto) debe ejecutar una sola vez
[`BDs_SQL/migracion_cargos.sql`](BDs_SQL/migracion_cargos.sql); los textos de
cargo existentes se conservan como cargos sin tarifa.

La tabla `asignacion` solo impide duplicar asignaciones abiertas: un
colaborador no puede tener dos `PENDIENTE` ni dos `ACTIVA` en el mismo
proyecto, pero sí varias `RECHAZADA` o `FINALIZADA` (historial). Para ello usa
la columna generada `estado_abierto` y la restricción `uq_asignacion_abierta`,
que reemplaza a `uq_asignacion_proyecto_colaborador_estado`. Una base creada
antes de este cambio debe ejecutar
[`BDs_SQL/migracion_asignacion_unicidad_abierta.sql`](BDs_SQL/migracion_asignacion_unicidad_abierta.sql)
(requiere MySQL 5.7 o superior). No borra ni modifica registros y puede
ejecutarse nuevamente sin error. La aplicación no lee esa columna, así que
funciona igual antes y después de migrar.

Cada certificado guarda en `certificado.nivel_aprobado` el nivel de dominio con
el que el RM lo aprobó, y el historial de validaciones muestra ese valor. Una
base creada antes de este cambio debe ejecutar una sola vez, antes de desplegar,
[`BDs_SQL/migracion_nivel_aprobado_certificado.sql`](BDs_SQL/migracion_nivel_aprobado_certificado.sql).
Solo agrega la columna (nula), puede ejecutarse nuevamente sin error y no
rellena los certificados existentes: los aprobados antes de migrar se muestran
como "Sin registro".

`skillbridge_db_v4.sql` incluye además la tabla `evaluacion` (calificación de
1 a 5 y comentarios al finalizar una asignación) y las columnas
`proyecto_habilidad_requerida.horas_semanales` y
`proyecto.documento_contexto_url`. No hay un archivo de migración para estos
cambios: una base creada antes debe ejecutar una sola vez las sentencias de la
sección *Resumen de Scripts SQL necesarios* de [`CHANGELOG.md`](CHANGELOG.md).

Las fotos y los certificados que suben los colaboradores se guardan en la
carpeta indicada por `UPLOAD_DIR`. En la nube esta variable debe apuntar a un
directorio escribible y persistente; si se usa el almacenamiento temporal de
la instancia, los archivos podrían perderse al reiniciar o volver a desplegar.

Para cargar datos de demostración del RM, incluidas solicitudes de personal,
certificados, foros, actividades para reportes y solicitudes de cursos, se
puede ejecutar después
[`BDs_SQL/datos_demo_rm_proyectos.sql`](BDs_SQL/datos_demo_rm_proyectos.sql).

### Reportes del RM

Las horas trabajadas corresponden a la suma de `actividad.horas_estimadas` de
las actividades con estado `COMPLETADA` y fecha de entrega dentro del mes
seleccionado. Las actividades pendientes o en revisión no se contabilizan.
Los reportes no exponen sueldo base, bonos ni pagos mensuales. La exportación
a Excel usa Apache POI, por lo que el despliegue debe volver a construir el
proyecto para descargar la dependencia declarada en `pom.xml`. Esta función no
requiere una migración adicional de la base de datos.

### Cursos del RM

En `/rm/cursos` se consulta el catálogo activo con filtros. Desde la bandeja
se aprueban o rechazan solicitudes (con motivo obligatorio para rechazar), y
desde el formulario se asignan cursos directamente a colaboradores activos.
Se impiden inscripciones pendientes o activas duplicadas. Cada decisión o
asignación genera una notificación real en la campana del colaborador.

Este alcance corresponde al RM. El administrador mantiene el catálogo en
`/admin/cursos` (crear, editar y activar/desactivar); la categoría es
obligatoria y las fechas también, salvo en cursos autodidactas; al crear, la
fecha de inicio no puede ser anterior a hoy. El RM solo ve los cursos
activos. El colaborador solicita cursos desde `/colaborador/explorar`; al ser
aprobados o asignados aparecen en "Mis cursos" de `/colaborador/perfil`. Solo
los cursos autodidactas piden evidencia: el colaborador sube un PDF/JPG/PNG de
hasta 10 MB, la inscripción pasa a `EVIDENCIA_PENDIENTE` y el RM puede
aprobarla (`COMPLETADO`) o rechazarla (`EN_CURSO`, con reenvío permitido). Los
cursos de horario fijo no piden evidencia: pasan solos a `COMPLETADO` cuando
llega su fecha de fin, lo que se comprueba al abrir las vistas de cursos u
horas del colaborador. Las notificaciones de cursos del colaborador abren
`/colaborador/perfil#mis-cursos`. Datos de prueba manual del colaborador:
[`BDs_SQL/Pruebas_Colaborador/Pruebas_col.sql`](BDs_SQL/Pruebas_Colaborador/Pruebas_col.sql).

La integración sigue parcial: la bandeja HTML del RM todavía no presenta el
archivo ni los botones de aprobación/rechazo de evidencia, aunque los endpoints
existen. Tampoco hay alerta automática por fecha final. Las horas de un curso
completado durante el mes se suman a las horas mensuales del colaborador y, por
esa vía, al cálculo informativo del bono. Antes de iniciar esta versión sobre
una base existente, aplicar `migracion_cursos.sql` y además los cambios de
evidencia descritos en `CHANGELOG.md`.

## Pruebas

Las pruebas activan automáticamente el perfil `test`, que usa una base H2 en
memoria y no se conecta a MySQL ni modifica la base de desarrollo.

```powershell
.\mvnw.cmd test
```
