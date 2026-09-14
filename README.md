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

Las fotos y los certificados que suben los colaboradores se guardan en la
carpeta indicada por `UPLOAD_DIR`. En la nube esta variable debe apuntar a un
directorio escribible y persistente; si se usa el almacenamiento temporal de
la instancia, los archivos podrían perderse al reiniciar o volver a desplegar.

Para cargar datos de demostración del RM, incluidas solicitudes de personal y
certificados en diferentes estados, se puede ejecutar después
[`BDs_SQL/datos_demo_rm_proyectos.sql`](BDs_SQL/datos_demo_rm_proyectos.sql).

## Pruebas

Las pruebas activan automáticamente el perfil `test`, que usa una base H2 en
memoria y no se conecta a MySQL ni modifica la base de desarrollo.

```powershell
.\mvnw.cmd test
```
