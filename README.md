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

## Pruebas

Las pruebas activan automáticamente el perfil `test`, que usa una base H2 en
memoria y no se conecta a MySQL ni modifica la base de desarrollo.

```powershell
.\mvnw.cmd test
```
