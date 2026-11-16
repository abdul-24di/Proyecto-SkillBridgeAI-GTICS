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
| 2 | Alcance | ⏳ Pendiente | — | Tabla historia → estado real (revisada contra el código), tabla de roles, límites, exclusiones. **Incluir lo nuevo:** asistente IA del colaborador, chat privado, chat RM–PM y reparto de revisiones a un RM responsable |
| 3 | Arquitectura | ⏳ Pendiente | — | Diagrama general, diagrama de despliegue, diagrama ER desde `skillbridge_db_v4.sql`, capas, flujo de un caso. **Agregar:** API externa de IA (Gemini o Claude), WebSocket `/ws-chat` y la tarea programada `RmRevisionScheduler` |
| 4 | APIs REST | 🟡 Casi | [04-apis-rest.md](04-apis-rest.md) | Fichas de los 19 endpoints y del WebSocket hechas (16/11). Faltan las capturas nuevas de Postman (15 a 40, marcadas `[pendiente]` en cada ficha) |
| 5 | Costos | ⏳ Pendiente (depende del equipo) | — | Horas/semana de cada uno, tarifa supuesta y su fuente, captura de AWS Billing, tipo de EC2, ¿RDS o MySQL en la VM?, **costo de la API de IA (¿qué proveedor se usa en la nube?)**, herramientas, imprevistos 20–30 % + 10 % de ganancia, gráfico |
| 6 | README + JavaDoc | 🟡 Parcial | `README.md` | README: propósito, usuarios demo, bugs corregidos, equipo. **JavaDoc en todos los métodos** (hoy ~114 bloques) |
| 7 | Resultados del testeo | 🟡 Parcial | [07-resultados-testeo.md](07-resultados-testeo.md) | Medido el 16/11 (787 pruebas, 61.1 %). Falta: nueva captura de JaCoCo, correr el Runner con las 63 peticiones, Fichas de casos (7.2/7.3), tabla de bugs (7.5), captura de GitHub Actions, pruebas funcionales manuales |
| 8 | Manual de usuario | ⏳ Pendiente | — | Paso a paso por rol con capturas (hay muchas en `presentacion/capturas`), flujos que cruzan roles, FAQs. Incluir chat del RM y asistente IA |
| 9 | Manual técnico | ⏳ Pendiente | — | Despliegue en EC2 paso a paso, variables, BD, mantenimiento, backups con `mysqldump`. Documentar `IA_PROVEEDOR`, `IA_API_KEY`, `IA_MODELO`, `IA_MAX_TOKENS`, `IA_MENSAJES_POR_MINUTO` (también en el README) |
| 10 | Recomendaciones | ⏳ Pendiente | — | Base: `docs/SEGURIDAD.md`, partes parciales del README, seguridad del asistente IA (límite de mensajes, prompt injection), cobertura baja de PM/Colaborador |
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

### 15/11/2026

**Contexto:** Mili subió el chat privado, el chat RM–PM, el asistente IA del colaborador y el reparto de revisiones a un RM responsable (commits del 11 y 12/11). Con eso fallaban 29 de 783 pruebas.

**Hecho (Fase 1: dejar el código en verde)**
- **RM responsable:** cada RM ve lo suyo y lo que **no tiene responsable**, para que nada quede sin revisión (los datos demo tienen 3 certificados pendientes sin responsable). Arreglo en `RmAlcanceService` + `RmAlcanceTests`.
- **Notificaciones:** decisión del equipo: cada RM recibe solo las notificaciones de lo que le fue asignado. Se actualizaron 5 pruebas.
- **Un solo manejador de errores de la API** (`ApiErrorAdvice`). Se unió con el `ChatRestExceptionHandler` de Mili, con su acuerdo.
- **Topbar del RM:** vuelve el texto "Resource Manager".
- Resultado: **786 pruebas en verde**. Revisado también en el navegador (topbar y bandeja de certificados del RM).

**Pendiente de decidir:** si un proyecto no tiene RM responsable, la solicitud de personal del PM no le llega a ningún RM como notificación (sí aparece en la bandeja de todos).

### 16/11/2026

**Hecho (Fase 2: documentación al día con los cambios de Mili)**
- **Sección 4 rehecha:** 19 endpoints (antes 7) y el WebSocket. Todas las respuestas se probaron en vivo con `curl` sobre los datos demo: casos correctos y de error de cada endpoint, 401 sin sesión, 403 con rol equivocado y el límite de 10 consultas por minuto del asistente.
- **Bug corregido al documentar:** las acciones del asistente sin `cursoId` o sin `proyectoId` respondían 500; ahora responden 400 con un mensaje claro (`ColAsistenteService` + prueba en `ApiRestTests`).
- **Postman:** colección ampliada de 26 a 63 peticiones, en 5 carpetas (se suman el chat del RM y el asistente).
- **Sección 7:** medida de nuevo: **787 pruebas en verde**; la cobertura de líneas bajó a **61.1 %** (antes 65.1 %) porque el código nuevo del asistente y del chat tiene pocas pruebas.
- **README:** variables de la IA (`IA_PROVEEDOR`, `IA_API_KEY`, `IA_MODELO`, `IA_MAX_TOKENS`, `IA_MENSAJES_POR_MINUTO`) y nota del WebSocket.

**Para el equipo**
- La solicitud de proyecto del asistente no se puede probar con éxito usando solo los datos demo: todos los colaboradores demo ya tienen una asignación en el único proyecto con requisitos de habilidad.
- Subir la cobertura: el asistente IA, los foros y las actividades del PM y del colaborador tienen menos del 20 %.

## Próximos pasos (orden acordado)

0. **Capturas pendientes de la Fase 2:** Runner de Postman con las 63 peticiones, capturas 15 a 40 de las fichas y nueva captura de JaCoCo.
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
| 15/11/2026 | Claude Code | "Revisar si todo está ok después del pull y arreglar las pruebas, la topbar y unir los manejadores de errores" | Diagnóstico de los 29 fallos, arreglo en `RmAlcanceService`, pruebas actualizadas, `ApiErrorAdvice` unificado, topbar del RM | El equipo decidió la regla de notificaciones por RM; Mili aprobó unir los manejadores |
| 16/11/2026 | Claude Code | "Avanza con la fase 2" (actualizar la documentación por los cambios del chat y la IA) | Pruebas en vivo de los 19 endpoints, fichas de la sección 4, colección de Postman ampliada, sección 7 medida de nuevo, README de la IA, arreglo del 500 del asistente | `[ ]` |
