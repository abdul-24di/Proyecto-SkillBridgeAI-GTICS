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

Si el correo no se puede enviar (variables vacías, contraseña inválida o puerto
SMTP bloqueado), el Administrador ve un aviso en pantalla, queda un registro
"Error al enviar correo" en la auditoría y la causa aparece en el log con el
prefijo `[EMAIL]`. Al arrancar, si falta `MAIL_USERNAME`, el log lo avisa.

En despliegue (por ejemplo Docker en la nube) las variables deben pasarse al
contenedor (`-e MAIL_USERNAME=... -e MAIL_PASSWORD=... -e APP_BASE_URL=http://IP:8080`).
`APP_BASE_URL` debe ser la URL pública de la aplicación para que los enlaces de
activación sean válidos; si se olvida y queda en `localhost`, el enlace se arma
con la dirección con la que el Admin entró al sistema. Estas credenciales nunca deben
guardarse en `application.properties` ni versionarse.

Toda contraseña nueva (activación, recuperación y cambio desde el perfil de
cualquier rol) debe tener al menos 8 caracteres, una mayúscula, un número y un
símbolo. Para activar la cuenta también hay que aceptar la política de
tratamiento de datos. Las contraseñas existentes, incluidas las de los usuarios
demo, siguen siendo válidas para iniciar sesión.

### Asistente IA del colaborador

El asistente (`/colaborador/asistente`) responde con datos internos de SkillBridge
(foros, cursos y proyectos) y, si hay una llave configurada, redacta la respuesta con
un modelo de IA externo. Se configura con variables de entorno:

| Variable | Por defecto | Uso |
|---|---|---|
| `IA_PROVEEDOR` | `GEMINI` | `GEMINI` (Google AI Studio, tiene capa gratuita) o `ANTHROPIC` (Claude, de pago) |
| `IA_API_KEY` | vacía | Llave del proveedor elegido |
| `IA_MODELO` | según el proveedor | Modelo a usar; si se deja vacía se usa el modelo por defecto del proveedor |
| `IA_MAX_TOKENS` | `900` | Largo máximo de cada respuesta |
| `IA_MENSAJES_POR_MINUTO` | `10` | Consultas por colaborador y por minuto |

```powershell
$env:IA_PROVEEDOR = "GEMINI"
$env:IA_API_KEY = "tu_llave_de_api"
```

Si `IA_API_KEY` está vacía, la aplicación arranca igual: el log avisa
"IA_API_KEY está vacía, se usará el modo sin IA" y el asistente responde solo con los
datos internos. En Docker la llave se pasa al contenedor (`-e IA_API_KEY=...`). Igual
que las credenciales del correo, **nunca** se guarda en `application.properties` ni se
versiona.

### DNI de los usuarios

Al activar la cuenta, todos los roles ingresan su DNI (8 dígitos, no se puede repetir
entre usuarios ni editar después). Se consulta en la API de apisperu (consumo de servicios
web de la clase 10.1) con `RestTemplate` y la cabecera `Authorization: Bearer`, y se
comprueba que el nombre escrito coincida con el del documento.

| Variable | Por defecto | Uso |
|---|---|---|
| `DNI_API_KEY` | vacía | Token de apisperu.com |
| `DNI_API_URL` | `https://dniruc.apisperu.com/api/v1/dni` | Endpoint de consulta |
| `DNI_REINTENTO_CRON` | `0 15 * * * *` | Cada cuánto se reintentan los DNI sin verificar |

Solo bloquea el registro que la API diga que el DNI no existe (HTTP 404 o `success=false`
con "No se encontraron resultados"). Si la key falta, la API no responde o rechaza la
consulta por otro motivo (token, plan, límite, mantenimiento), el registro no se bloquea: el
DNI se guarda como **"sin verificar"**, el usuario recibe una notificación con el motivo y
el log lo avisa con el prefijo `[DNI]`. Un DNI sin verificar no se queda así: cada hora
una tarea programada lo vuelve a consultar, y el usuario puede corregirlo o pulsar
"Verificar ahora" en su perfil. Si la consulta lo confirma, queda verificado y ya no se
puede cambiar; si no existe o no corresponde al nombre de la cuenta, se quita y se le
notifica el motivo para que lo registre de nuevo. Las cuentas creadas antes de pedir el
DNI lo registran desde su perfil.
La key, como las demás credenciales, **nunca** se guarda en `application.properties`
ni se versiona.

### Chat en tiempo real

El chat (PM, colaborador y RM) avisa los mensajes nuevos por WebSocket en
`/ws-chat`. No necesita configuración. Si el despliegue pasa por un proxy, este debe
permitir conexiones WebSocket; si no, el chat sigue funcionando y consulta la API cada
cierto tiempo.

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
Para ver los reportes del RM con varios meses, estados y prioridades, ejecutar
luego [`BDs_SQL/datos_demo_rm_reportes.sql`](BDs_SQL/datos_demo_rm_reportes.sql)
(incluye escenarios sugeridos de filtros y exportación).

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

Para obtener además el reporte de cobertura (JaCoCo):

```powershell
.\mvnw.cmd clean verify
```

El reporte queda en `target/site/jacoco/index.html`. Los resultados y la cobertura por
paquete están en [`docs/informe/07-resultados-testeo.md`](docs/informe/07-resultados-testeo.md).

## API REST

Los 19 endpoints REST (chat del PM, del colaborador y del RM, asistente del colaborador y
notificaciones), el WebSocket del chat, sus fichas y su seguridad están en [`docs/informe/04-apis-rest.md`](docs/informe/04-apis-rest.md). La colección de
Postman está en `docs/informe/postman/`. Antes de ejecutarla hay que cambiar `baseUrl` y escribir la
contraseña demo en la variable `password`.


### Resumen de cobertura y bugs corregidos

* **Pruebas automatizadas:** 1,157 pruebas en 51 clases (100 % aprobadas).
* **Cobertura global (JaCoCo):** 78.8 % de líneas cubiertas.

| N° | Módulo | Problema | Corrección |
| :---: | :--- | :--- | :--- |
| 1 | Foros (Administrador) | Eliminar un foro con publicaciones mostraba un error de la base de datos. | Ahora elimina el foro con sus publicaciones, respuestas y me gusta. |
| 2 | API REST | Los errores del chat y de las notificaciones redirigían con código 302. | Responden JSON con el mensaje de error y el código 400 o 409. |
| 3 | Foros del colaborador | Un error de validación llevaba al detalle de otro foro. | Se valida el formulario y se vuelve al mismo modal. |
| 4 | Pantallas | Los modales fallaban con el error `bootstrap is not defined`. | Se usa el objeto de Tabler. |
| 5 | Project Manager | El motivo de cancelación de un proyecto no se enviaba. | Ahora se envía y se muestra. |
| 6 | Reportes del colaborador | La prueba de *Mi desempeño* fallaba por la fecha de creación del usuario. | Se corrigió la prueba para que el colaborador tenga una fecha de creación antigua. |
| 7 | Explorar proyectos (Colaborador) | Al intentar postularse con restricciones o datos inválidos, no se mostraba correctamente el mensaje de error en pantalla. | Se corrigió el manejo de mensajes flash para mostrar el aviso de error claro en la vista de Explorar. |
| 8 | Perfil del colaborador | El campo de teléfono permitía ingresar letras o caracteres no numéricos al actualizar los datos de contacto. | Se unificó la regla de validación para aceptar únicamente caracteres numéricos válidos en el teléfono. |
| 9 | Avatar / Foto de perfil (4 roles) | Al seleccionar una nueva foto de perfil no se mostraba vista previa antes de guardarla. | Se implementó la previsualización inmediata en el avatar y se ubicó el botón "Guardar foto" junto al selector. |

