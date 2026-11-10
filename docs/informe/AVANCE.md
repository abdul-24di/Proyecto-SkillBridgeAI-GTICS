# Avance de la documentación final

> Seguimiento del informe final del curso (Entregable 12). Se actualiza en cada sesión de trabajo.
> **Entrega:** 10/dic/2026 en Paideia, antes del laboratorio (fuera de hora = −5 puntos). **Presentación:** 14/dic/2026.

## Cómo se trabaja

- **Guía maestra:** `Plantilla_Documentacion_SkillBridge.md`, con las 10 secciones de la profesora y los anexos A–E. La tiene Abdul; no está en el repo.
- **Formato del Word final:** `documento_deGuia.docx`. Se toma solo el formato: portada PUCP, secciones numeradas, tablas y "Figura N.".
- **Referencias externas** (TFG UPM, UNI, PetClinic, USIL): **solo guían la forma**. No se copia texto. Se citan en el Anexo E. No se suben al repo.
- **Borradores:** un `.md` por sección en esta carpeta (`docs/informe/`). Al final se pasan al Word.
- **Contenido real:** todo sale del código, la BD, los commits y pruebas ejecutadas de verdad. Lo que depende del equipo se marca `[pendiente: ...]`.
- **Seguridad:** nada de contraseñas ni llaves en los documentos. La colección de Postman deja `password` vacío.

## Estado por sección

| # | Sección | Estado | Archivo | Qué falta |
|---|---|---|---|---|
| — | Portada | ⏳ Pendiente | — | Nombre del grupo, jefe de práctica, códigos de los 4 integrantes |
| 1 | Resumen ejecutivo | ⏳ Pendiente | — | Se escribe al final |
| 2 | Alcance | ⏳ Pendiente | — | Tabla historia → estado real (revisada contra el código), tabla de roles, límites, exclusiones (la IA no está implementada) |
| 3 | Arquitectura | ⏳ Pendiente | — | Diagrama general, diagrama de despliegue, diagrama ER desde `skillbridge_db_v4.sql`, capas, flujo de un caso |
| 4 | APIs REST | ✅ Hecho | [04-apis-rest.md](04-apis-rest.md) | — |
| 5 | Costos | ⏳ Pendiente (depende del equipo) | — | Horas/semana de cada uno, tarifa supuesta y su fuente, captura de AWS Billing, tipo de EC2, ¿RDS o MySQL en la VM?, herramientas, imprevistos 20–30 % + 10 % de ganancia, gráfico |
| 6 | README + JavaDoc | 🟡 Parcial | `README.md` | README: propósito, usuarios demo, bugs corregidos, equipo. **JavaDoc en todos los métodos** (hoy ~114 bloques) |
| 7 | Resultados del testeo | 🟡 Parcial | [07-resultados-testeo.md](07-resultados-testeo.md) | Fichas de casos (7.2/7.3), tabla de bugs (7.5), captura de GitHub Actions, pruebas funcionales manuales |
| 8 | Manual de usuario | ⏳ Pendiente | — | Paso a paso por rol con capturas (hay muchas en `presentacion/capturas`), flujos que cruzan roles, FAQs |
| 9 | Manual técnico | ⏳ Pendiente | — | Despliegue en EC2 paso a paso, variables, BD, mantenimiento, backups con `mysqldump` |
| 10 | Recomendaciones | ⏳ Pendiente | — | Base: `docs/SEGURIDAD.md`, partes parciales del README, IA no implementada, cobertura baja de PM/Colaborador |
| A | Jira | ⏳ Pendiente (equipo) | — | Capturas del tablero; resumen de épicas |
| B | GitHub | 🟡 Casi | — | Captura de Contributors (los 4 tienen commits) |
| C | Uso de IA | ⏳ Pendiente | — | Tabla de prompts (ver "Registro de uso de IA" abajo) |
| D | Miro | ⏳ Pendiente (equipo) | — | Enlace y capturas |
| E | Referencias | ⏳ Pendiente | — | Al final |

## Registro de sesiones

### 10/11/2026

**Hecho**
- Diagnóstico de qué existe y qué falta para cada sección del informe.
- **Cobertura:** plugin JaCoCo en `pom.xml`. `mvnw clean verify` → 782 pruebas en verde, **65.1 % de líneas**. Captura en `capturas/jacoco.png`.
- **API REST corregida:** los errores ahora son JSON con 400/409 (antes redirigían con 302). Sin sesión responde 401. El chat del colaborador tiene las mismas reglas que el del PM. Clase nueva `ApiErrorAdvice` + 8 pruebas en `ApiRestTests`.
- **Sección 4 completa:** tabla de endpoints, seguridad, 7 fichas en el formato de la clase 9.2 con sus capturas.
- **Postman:** colección `postman/SkillBridge_API.postman_collection.json` con 26 peticiones. Runner: **32/32 tests aprobados**. 16 capturas en `capturas/`.
- **Sección 7 parcial:** tipos de prueba, pruebas por módulo, cobertura por paquete y resultados de Postman.
- README: cómo sacar la cobertura y enlace a la API. CHANGELOG actualizado.

**Datos útiles**
- Para repetir las pruebas de Postman se necesita la app en `localhost:8080` con los datos demo (`skillbridge_db_v4.sql` + `datos_demo_rm_proyectos.sql`). Los usuarios demo y su contraseña están en `BDs_SQL/datos_demo_rm_proyectos.sql`.
- Las clases con menos cobertura son los foros y actividades de PM y Colaborador (menos del 10 %).

## Próximos pasos (orden acordado)

1. **JavaDoc** en todos los métodos, por paquetes, sin cambiar la lógica.
2. **Alcance** (2) y **Arquitectura** (3) con diagramas.
3. **Manual técnico** (9) y **Manual de usuario** (8).
4. **Recomendaciones** (10).
5. **Costos** (5), cuando el equipo pase los datos.
6. Resumen ejecutivo, anexos, limpieza del repo y checklist final.

## Limpieza antes del .zip (no se ha hecho todavía)

Archivos versionados que no deberían ir en la entrega, para decidir en equipo:
- Archivos sueltos: `cookies.txt`, `cookies_test.txt`, `c_rm.txt`, `c_test.txt`, `crm.txt`, `response_pm.html`, `output_cursos.txt`, `ColaboradorViewController.tmp`.
- Carpeta `uploads/` con archivos de usuarios (ya está en `.gitignore`, pero hay archivos que se subieron antes).
- Carpeta `Clases/` con las diapositivas del curso.

## Registro de uso de IA (para el Anexo C)

| Fecha | Herramienta | Pedido | Uso | Qué revisó o cambió el equipo |
|---|---|---|---|---|
| 10/11/2026 | Claude Code | "Leer la plantilla y decir qué tenemos y qué falta para la documentación" | Diagnóstico por sección | `[ ]` |
| 10/11/2026 | Claude Code | "Agregar JaCoCo y sacar la cobertura real" | Plugin en `pom.xml`, ejecución de `mvnw verify`, sección 7.4 | `[ ]` |
| 10/11/2026 | Claude Code | "Documentar la API REST, armar la colección de Postman y corregir la API" | Fichas de la sección 4, colección, `ApiErrorAdvice`, 401 en la API, `ApiRestTests` | El equipo ejecutó la colección y tomó las capturas |
