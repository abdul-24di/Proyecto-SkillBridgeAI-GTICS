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

## Base de datos y despliegue

Una instalación nueva debe ejecutar [`BDs_SQL/skillbridge_db_v4.sql`](BDs_SQL/skillbridge_db_v4.sql).
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

El cargo del colaborador ahora es un catálogo (`cargo`) con tarifas por nivel
(Junior / Semi-Senior / Senior) que administra el Admin en
*Habilidades → Cargos y Matriz Salarial*. El `sueldo_base` se calcula con la
tarifa del cargo según el nivel del colaborador y se recalcula al cambiar las
tarifas, el cargo o el nivel. Una base creada antes de este cambio (con
`usuario.cargo` como texto) debe ejecutar una sola vez
[`BDs_SQL/migracion_cargos.sql`](BDs_SQL/migracion_cargos.sql); los textos de
cargo existentes se conservan como cargos sin tarifa.

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

Este alcance corresponde al RM: el mantenimiento del catálogo por el
administrador y el envío de nuevas solicitudes desde la vista del colaborador
todavía deben conectarse a la base de datos. Los datos de demostración permiten
probar la revisión del RM mientras se implementan esos módulos. Antes de iniciar
esta versión en una base existente, ejecutar `migracion_cursos.sql`; no borra
registros y solo agrega `colaborador_curso.motivo_respuesta`.

## Pruebas

Las pruebas activan automáticamente el perfil `test`, que usa una base H2 en
memoria y no se conecta a MySQL ni modifica la base de desarrollo.

```powershell
.\mvnw.cmd test
```
