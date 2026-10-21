# Guion de demostración — Resource Manager (≤ 5 min)

Objetivo: mostrar en 5 minutos todas las vistas del rol **Resource Manager (RM)** y las operaciones CRUD que ofrece cada una.

> **Aclaración para el jurado:** el RM **no crea proyectos ni usuarios** (eso lo hacen el PM y el Administrador). Sus "CRUD" son: **crear** propuestas de asignación y asignaciones de cursos, **consultar** todo, **actualizar** presupuesto, fechas, estados y niveles, y **dar de baja lógica** (rechazar, finalizar, cancelar). En el sistema nada se borra físicamente: todo queda auditado.

---

## 0. Preparación (antes de grabar o presentar)

- [ ] Ejecutar `BDs_SQL/datos_demo_rm_proyectos.sql`. Si ya se ejecutó, volver a correrlo restaura los proyectos `[DEMO]` a su estado inicial.
- [ ] Levantar la app (`.\mvnw.cmd spring-boot:run`) y entrar con el usuario **demo.rm@skillbridge.local** (la contraseña de desarrollo está en el script, no se dice en voz alta).
- [ ] Navegador al 90 % de zoom, sin otras pestañas, sin barra de favoritos.
- [ ] Tener a mano una imagen JPG/PNG < 2 MB para la foto de perfil.
- [ ] Ensayar una vez con cronómetro. Si se pasa del tiempo, saltar los bloques marcados como **(opcional)**.

Datos de demostración que se usan:

| Elemento | Nombre | Estado |
|---|---|---|
| Proyecto para revisar | `[DEMO] Portal de Atencion` | `EN_REVISION` |
| Proyecto con equipo | `[DEMO] Migracion Cloud` | `ACTIVO` |
| Proyecto en espera | `[DEMO] Analitica Comercial` | `EN_ESPERA` |
| Proyectos cerrados | `[DEMO] Proyecto Finalizado` / `[DEMO] Proyecto Rechazado` | solo lectura |
| Cursos | `[DEMO] AWS Cloud Practitioner`, `[DEMO] Spring Boot avanzado`… | catálogo activo |

---

## 1. Mapa CRUD del RM (para tener presente)

| Módulo (menú) | Crear | Leer | Actualizar | Baja lógica |
|---|---|---|---|---|
| Proyectos | — | Listado, filtros, detalle, revisión | Presupuesto, fechas, aprobar | Rechazar, finalizar, cancelar |
| Asignaciones | Proponer asignación | Bandeja, revisión, detalle | Aprobar | Rechazar, finalizar |
| Solicitudes de personal | — | Bandeja y detalle | Iniciar atención, marcar atendida | — |
| Colaboradores | — | Directorio, perfil, historial | Nivel de experiencia | — |
| Certificados | — | Bandeja y revisión | Aprobar (con nivel) | Rechazar con motivo |
| Formación académica | — | Bandeja y documento | Aprobar | Rechazar con motivo |
| Cursos | Asignar curso a colaborador | Catálogo y solicitudes | Aprobar solicitud y evidencia | Rechazar solicitud y evidencia |
| Foros | — | Solo lectura | — | — |
| Reportes | Exportar Excel / PDF | Recursos y horas | — | — |
| Mi perfil | — | Datos propios | Teléfono, foto, contraseña | — |

---

## 2. Guion minuto a minuto

Formato de cada bloque: **⏱ tiempo · Pantalla · Hacer · Decir**.

### Bloque 1 — Ingreso y Dashboard · ⏱ 0:00 – 0:25

**Pantalla:** `/login` → `/rm/dashboard`

**Hacer:**
1. Iniciar sesión con el usuario RM (ya escrito, solo pulsar "Ingresar").
2. En el dashboard, señalar con el cursor: tarjetas superiores (aprobaciones pendientes, esperando PM, postulaciones), la segunda fila (certificados y solicitudes de curso) y el panel **"Requieren atención"**.

**Decir:**
> "Al ingresar como Resource Manager el sistema redirige a su dashboard. Aquí ve de un vistazo todo lo que tiene pendiente: asignaciones por aprobar, proyectos en revisión, certificados, formación académica y solicitudes de cursos. Cada tarjeta lleva directo a su bandeja."

---

### Bloque 2 — Proyectos (R + U + baja lógica) · ⏱ 0:25 – 1:25

**Pantalla:** menú **Proyectos** → `/rm/proyectos`

**Hacer:**
1. **Leer/filtrar:** escribir "DEMO" en la búsqueda, filtrar por estado `En revisión` → "Filtrar". Mostrar la paginación y "Limpiar filtros".
2. Abrir **`[DEMO] Portal de Atencion`** (en revisión) → vista de revisión.
3. **Actualizar presupuesto:** pulsar "Usar presupuesto solicitado" (o escribir un monto) → guardar. Señalar el resumen total / comprometido / reservado / disponible.
4. **Actualizar fechas:** cambiar la fecha de fin en el formulario de fechas → guardar. Mostrar el mensaje verde.
5. **Aprobar:** "Aprobar proyecto" → confirmar en el modal. El proyecto pasa a `ACTIVO`.
   - Mencionar sin hacerlo: *"Sin presupuesto el botón de aprobar aparece deshabilitado; también puedo rechazarlo con un motivo."*
6. Volver al listado y abrir **`[DEMO] Migracion Cloud`** (activo): mostrar equipo, costo por integrante, y al final las tarjetas **"Finalizar proyecto"** y **"Cancelar proyecto"**. Abrir el modal de cancelar y **cerrarlo sin confirmar**.

**Decir:**
> "El RM no crea proyectos, los revisa. Puede filtrar por nombre, estado, prioridad y vacantes. En la revisión asigna el presupuesto, ajusta las fechas y aprueba o rechaza. Una vez activo, puede finalizarlo o cancelarlo: es una baja lógica que rechaza las asignaciones pendientes y finaliza las activas, liberando las horas de los colaboradores."

---

### Bloque 3 — Asignaciones (C + R + U + baja lógica) · ⏱ 1:25 – 2:25

**Pantalla:** detalle de `[DEMO] Migracion Cloud` → "Buscar colaborador" → `/rm/proyectos/buscar-colaboradores`

**Hacer:**
1. **Crear:** en la búsqueda de candidatos, filtrar por una habilidad. Mostrar el costo estimado de cada candidato. Pulsar "Ver perfil" (modal) y cerrarlo.
2. Pulsar **"Proponer"** en un candidato → en el modal señalar los requisitos del proyecto, escribir horas semanales (p. ej. `20`) → enviar. Mensaje de éxito.
3. Ir al menú **Asignaciones** → `/rm/asignaciones`. **Leer:** usar los filtros de grupo, origen y estado. Señalar la propuesta recién creada con estado "Pendiente PM".
4. **Actualizar:** abrir una asignación "Pendiente RM" → revisión → **"Aprobar asignación"**. Mostrar que el presupuesto disponible se descuenta.
5. **Baja lógica:** abrir otra pendiente → **"Rechazar asignación"** con un motivo corto. *(Opcional: abrir una asignación activa y mostrar el botón "Finalizar" con su motivo, sin confirmar.)*

**Decir:**
> "Este es el 'crear' del RM: propone asignaciones. El sistema solo muestra habilidades validadas y calcula el costo con el sueldo base. Si los cupos están completos, exige una justificación que queda auditada. En la bandeja aprueba, rechaza o finaliza; al aprobar se valida que el presupuesto alcance."

---

### Bloque 4 — Solicitudes de personal (R + U) · ⏱ 2:25 – 2:45

**Pantalla:** Dashboard → enlace "Solicitudes abiertas" → `/rm/asignaciones/solicitudes-colaboradores`

**Hacer:**
1. Mostrar la bandeja con sus estados. Abrir el detalle de una solicitud abierta.
2. Pulsar **"Iniciar atención y buscar"**: lleva a la búsqueda de colaboradores ya filtrada.
   *(Opcional: volver y "Marcar como atendida".)*

**Decir:**
> "Cuando el PM pide personal sin indicar a quién, la solicitud llega aquí. El RM la toma, busca candidatos y, al terminar, la marca como atendida."

---

### Bloque 5 — Colaboradores, certificados y formación (R + U) · ⏱ 2:45 – 3:35

**Pantalla:** menú **Colaboradores** → `/rm/colaboradores`

**Hacer:**
1. **Leer:** filtrar el directorio por nombre o habilidad. Abrir un perfil: habilidades con insignia por estado y asignaciones activas.
2. **Actualizar nivel de experiencia:** en el perfil, subir el nivel general → "Guardar nivel" → "Confirmar nivel general". Mencionar: *"Solo se mantiene o sube, nunca baja."*
3. Volver al directorio → botón **"Certificados pendientes"** → abrir uno → **Revisión**:
   - Mostrar el documento, elegir el nivel de dominio aprobado → **"Aprobar certificado"**.
   - *(Mencionar: "Rechazar certificado" abre un modal con motivo obligatorio.)*
4. Directorio → botón **"Formación académica pendiente"** → abrir una pendiente → ver documento → **"Rechazar"** con motivo (o aprobar).
5. *(Opcional)* Mostrar el **Historial de validaciones** desde el perfil.

**Decir:**
> "El RM valida lo que el colaborador declara. Al aprobar un certificado fija el nivel de dominio de la habilidad y puede subir el nivel general. La formación académica se aprueba o rechaza con motivo, y el colaborador recibe una notificación en ambos casos."

---

### Bloque 6 — Cursos (C + R + U + rechazo) · ⏱ 3:35 – 4:20

**Pantalla:** menú **Cursos** → `/rm/cursos`

**Hacer:**
1. **Leer:** catálogo de cursos con filtros (categoría, búsqueda).
2. **Crear:** botón **"Asignar curso"** → elegir colaborador y `[DEMO] Spring Boot avanzado` → guardar. Mensaje de éxito.
3. Botón **"Solicitudes pendientes"** → `/rm/cursos/solicitudes`:
   - Una solicitud **pendiente** → **"Aprobar"** (se envía notificación y correo con fechas y horas).
   - Una **evidencia pendiente** → "Ver evidencia" → **"Validar evidencia"** (el curso queda completado) o **"Rechazar evidencia"** con motivo.

**Decir:**
> "Los colaboradores solicitan cursos y el RM los aprueba, o los asigna directamente. Al terminar, el colaborador sube su evidencia y el RM la revisa: queda registrado quién la revisó y cuándo. Los cursos que vencen sin evidencia se marcan como no completados automáticamente."

---

### Bloque 7 — Foros y reportes (R + exportación) · ⏱ 4:20 – 4:45

**Hacer:**
1. Menú **Foros** → abrir `[DEMO] Foro Migracion Cloud`: publicaciones, respuestas y solución marcada. *"Es de solo lectura para el RM."*
2. Menú **Reportes** → `/rm/reportes/recursos`: tabla de proyectos y de colaboradores. Botón **"Exportar"** → descargar **Excel** (y mencionar PDF).
3. Enlace **"Horas por colaborador"** → mostrar la tabla paginada.

**Decir:**
> "El RM consulta los foros de toda la organización para entender el avance, y genera reportes de recursos y horas, exportables a Excel y PDF."

---

### Bloque 8 — Mi perfil y cierre · ⏱ 4:45 – 5:00

**Hacer:**
1. Clic en el avatar (arriba a la derecha) → `/rm/perfil`.
2. **Actualizar** teléfono → guardar. *(Opcional: subir la foto y ver que cambia en la barra superior.)*

**Decir (cierre):**
> "Por último, el RM gestiona su propio perfil: teléfono, foto y contraseña. En resumen, el Resource Manager controla el presupuesto, las personas y su formación, y todas sus acciones quedan validadas en el servidor, notificadas y auditadas. Gracias."

---

## 3. Qué evitar durante la demo

- **Talent Matching** es una maqueta: no abrirlo (o mencionarlo solo como trabajo futuro).
- No confirmar "Cancelar proyecto" ni "Finalizar proyecto" en vivo: tarda en explicarse y deja el proyecto cerrado. Abrir el modal basta.
- No mostrar la consola, `application.properties` ni la contraseña del usuario demo.
- Si un formulario muestra un **mensaje rojo**, aprovecharlo: *"El servidor valida la regla de negocio y muestra el motivo."*

## 4. Plan B

| Problema | Qué hacer |
|---|---|
| Ya se aprobó `[DEMO] Portal de Atencion` en un ensayo | Volver a ejecutar `datos_demo_rm_proyectos.sql` (restaura los proyectos `[DEMO]`). |
| No hay candidatos en la búsqueda | Limpiar filtros; los colaboradores con pre-registro sin aprobar no aparecen. |
| Falla el correo (SMTP) | La notificación interna se crea igual; seguir con la demo. |
| Se acaba el tiempo | Saltar los pasos **(opcional)** y el bloque 4; el bloque 7 puede reducirse a mostrar el Excel. |
