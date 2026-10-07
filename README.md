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

El proyecto **no usa scripts de migración**: `skillbridge_db_v4.sql` es la única
fuente del esquema. Cada cambio de esquema se hace solo en ese archivo y la base
se vuelve a crear desde él: en la nube, en cada despliegue; en local, después de
cada cambio de esquema. Recrear la base borra sus datos, así que después se
vuelven a cargar los datos demo o de prueba. Los antiguos `migracion_*.sql` se
eliminaron el 2026-10-07 porque su contenido ya estaba en v4.

El script incluye, entre otros, el catálogo de cargos con tarifas por nivel
(`cargo`), las solicitudes de personal, la evidencia de cursos
(`colaborador_curso.evidencia_url`, `fecha_evidencia` y los estados
`EVIDENCIA_PENDIENTE` y `NO_COMPLETADO`), la unicidad de asignaciones abiertas
(`estado_abierto` y `uq_asignacion_abierta`, requiere MySQL 5.7 o superior), el
nivel aprobado de cada certificado, la tabla `evaluacion`, el CV y el
pre-registro del colaborador (`usuario.cv_*`, `registro_estado`,
`motivo_rechazo`) y la baja lógica de actividades y experiencia profesional
(`activo`).

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
requiere cambios en la base de datos.

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
esa vía, al cálculo informativo del bono. Antes de iniciar esta versión, recrear la
base con el `skillbridge_db_v4.sql` actual.

## Pruebas

Las pruebas activan automáticamente el perfil `test`, que usa una base H2 en
memoria y no se conecta a MySQL ni modifica la base de desarrollo.

```powershell
.\mvnw.cmd test
```
