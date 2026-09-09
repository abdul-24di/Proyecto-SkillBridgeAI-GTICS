# Correcciones pendientes — Revisión de vistas HTML (feature/vistas-html)

Lista de pendientes encontrados al revisar las vistas construidas por el equipo, antes de empezar los CRUDs. Se actualiza a medida que se revisa cada sección.

---

## Recomendaciones para quien revise PM y RM (aún no revisados a fondo)

No se hizo una revisión completa de `templates/pm/` y `templates/rm/` — quedó pendiente para otra persona del equipo. Adelanto acá un checklist basado en los patrones de bugs que ya se repitieron en Auth/Colaborador/Admin, más los puntos específicos de la Épica 4 y las actualizaciones que aplican a estos dos roles. Un vistazo rápido ya mostró señales buenas: `rm-foros.html` dice explícitamente *"El RM puede consultar todos los foros; no puede crear publicaciones, responder ni acceder al chat"* (perfecto, cumple A7/A9), no existe `rm-chat.html` (correcto), y `rm-talent-matching.html` ya usa el texto **"Proponer para el proyecto"** (cumple A12). Buena señal de que el mismo cuidado se aplicó acá.

### Patrones de bugs a repetir el chequeo (ya encontrados en otras secciones)
- [ ] **Correo editable en `pm-perfil.html` y `rm-perfil.html`**: en Colaborador encontramos el campo correo editable en "Ajustes de Cuenta", violando C4 (el correo es de solo lectura, solo el Admin lo cambia). Revisar si el mismo patrón se repite en los perfiles de PM y RM.
- [ ] **Datos mockeados inconsistentes entre pantallas relacionadas**: en Colaborador, "Mis proyectos" y "Foros" tenían listas de proyectos distintas y hardcodeadas por separado. Revisar si pasa lo mismo entre `pm-proyectos.html` ↔ `pm-foros.html` ↔ `pm-chat.html` ↔ `pm-reportes.html`, y entre las vistas equivalentes de RM.
- [ ] **Pestañas/tabs de foro o chat embebidas dentro del detalle de proyecto, hardcodeadas y desconectadas del JS real**: en `col-detalle-proyecto.html` la pestaña Foro era HTML estático sin conectar a la lógica real del foro. Revisar si `pm-detalle-proyecto.html` tiene el mismo problema.

### Específico de Épica 4 — Proyectos y Asignaciones (A4, A18, A20, Bloque B punto 3)
- [ ] **`pm-crear-proyecto.html`**: confirmar que el campo prioridad (Alta/Media/Baja) requiera justificación de texto, y que el proyecto nazca en estado "En revisión". Ya vi que el campo "prioridad" existe ahí y en `pm-detalle-proyecto.html`, falta verificar el flujo completo.
- [ ] **Estados del proyecto**: verificar que se manejen los 5 estados acordados: En revisión → Activo | Rechazado → En espera | Cancelado | Finalizado. Y que el PM pueda cancelar su propio proyecto mientras esté "En revisión", antes de que el RM decida (`rm-revision-proyecto.html`).
- [ ] **Asignaciones — los 3 orígenes de A4**: verificar que exista el flujo de propuesta desde el PM (con botón "Proponer", no "Confirmar" ni "Asignar directo"), y que las pantallas de RM (`rm-proponer-asignacion.html`, `rm-revision-asignacion.html`, `rm-detalle-asignacion-pendiente-pm.html`) muestren la disponibilidad y carga de trabajo del colaborador antes de aprobar.
- [ ] **Bandeja de aprobaciones pendientes para el PM**: A4/A10 piden una bandeja tanto para PM como para RM. RM ya tiene varias vistas de solicitudes (`rm-solicitudes-colaboradores.html`, `rm-detalle-proyecto-solicitudes.html`). Verificar que el PM tenga su equivalente (asignaciones propuestas por el RM, solicitudes de colaboradores pendientes de su aprobación).
- [ ] **A18 — desasignación por PM o RM sin aprobación del otro**: verificar que ambos roles puedan finalizar una asignación activa directamente (revisar `rm-asignaciones-colaborador.html` / `rm-detalle-asignacion-activa.html`, y el equivalente en PM).
- [ ] **A20 — presupuesto exclusivo del RM**: ya confirmé que "presupuesto" solo aparece en templates de RM (`rm-revision-proyecto.html` tiene "Asignar presupuesto") y en un reporte de PM (`pm-reporte-detalle.html`, que debería ser de solo lectura ahí). Verificar que el PM efectivamente no pueda editarlo, solo verlo.

### Reportes (A21/A22) y certificados (A23)
- [ ] **`pm-reportes.html` / `pm-reporte-detalle.html`** (A21) y **`rm-reporte-recursos.html`** (A22): verificar exportación a PDF y Excel (C18). Ojo: A21/A22 dependen de la Épica 9 (Horas y Pagos), que está Tier 3 diferida — si estas pantallas ya muestran datos de "horas cumplidas" sin que exista el sistema de tareas/horas, es trabajo adelantado sobre una dependencia que aún no existe. Avisar al equipo, igual que con Cursos en Colaborador.
- [ ] **`rm-certificados-pendientes.html` / `rm-revision-certificado.html`** (A23): verificar bandeja de certificados pendientes, aprobar/rechazar con motivo, y que el nivel de experiencia (Junior/Senior) sea editable por el RM. A23 es Tier 2 (recortable, no Tier 1) — no es bloqueante pero conviene revisar si está bien hecho ya que existe.

### Tier 3 ya construido (mismo patrón que Cursos en Colaborador)
- [ ] `rm-cursos.html`, `rm-asignar-curso.html`, `rm-solicitudes-cursos.html` (A25/A26/A27) y `rm-horas-colaboradores.html` (Épica 9) ya están construidos a pesar de estar diferidos hasta después del Parcial. No es un error, pero avisar al equipo por si ese tiempo rendía más invertido en Tier 1.

---

## Épica 2 — Autenticación (`templates/auth/`)

- [ ] **Eliminar el link "¿No tienes cuenta? Regístrate aquí"** del login (`auth/login.html`, línea ~109). Apunta a `/registro`, que no debería ser alcanzable desde el login.
- [ ] **Eliminar/reemplazar `auth/registro.html`** — es el formulario viejo de "Crear cuenta" (Nombre+Apellido separados, Teléfono, dropdown "Tipo de usuario"). Contradice la decisión de que el Admin crea la cuenta (solo correo+rol) y el usuario la activa por enlace. Ya existe el reemplazo correcto en `auth/activar-cuenta.html`.
- [ ] **Falta por completo la pantalla "Verificar código"**:
  - No existe `templates/auth/verificar-codigo.html`.
  - No existe ruta en `AuthViewController` para ella.
  - Por esto el flujo de "Recuperar contraseña" no avanza — el botón "Enviar enlace de recuperación" solo muestra un mensaje y se deshabilita, sin redirigir a ningún lado (`auth.js`, handler de `forgotForm`).
  - Falta: crear el HTML (inputs de 6 dígitos, botón verificar, reenviar código), la ruta en el controller, y agregar el `window.location.href` faltante en el handler de `forgotForm` en `auth.js`.

### Lo que SÍ está bien en Auth (no tocar)
- `activar-cuenta.html`: correo readonly, nombre completo unificado, sin dropdown de rol/teléfono/empresa, checkbox de términos, y maneja bien los 3 estados (válido/expirado/usado) vía query param.
- `nueva-contrasena.html` y `contrasena-actualizada.html`: completos y con navegación funcional.

---

## A24 — Sistema de Notificaciones (transversal a todos los roles)

- [ ] **Falta la sección/vista de notificaciones por completo.** El link "Ver todas las notificaciones" dentro del panel de la campanita es un link muerto (`<a href="#">`, en `static/js/notificaciones.js`). No existe ningún `notificaciones.html` en `templates/` ni ruta para ella en ningún `ViewController`.
- [ ] La vista de notificaciones que falta debe tener: **filtros por tipo de evento según el rol** (los eventos por rol ya están definidos en A24 de las actualizaciones) y **filtro por rango de fechas**.
- [ ] El clic en una notificación individual debe redirigir a la sección relevante del sistema (ya funciona parcialmente en el dropdown de la campanita — usar la misma lógica en la vista completa).
- Nota: A24 está en **Tier 3 (diferido hasta después del Parcial)** según la priorización acordada — no es bloqueante para el 40% de CRUDs, pero si el botón "Ver todas" va a quedar visible en el dropdown mientras tanto, debería no ser un link muerto (quitarlo o deshabilitarlo hasta construir la vista).

### Lo que SÍ está bien de notificaciones (no tocar)
- El dropdown de la campanita (dot indicator de no leídas, marcar todas como leídas, marcar individual al hacer clic) ya funciona bien como base — solo falta la vista completa.

---

## Épica 3 — Gestión de Colaboradores (`templates/col/`)

- [ ] **Viola C4**: en `col-perfil.html` (pestaña "Ajustes de Cuenta"), el campo "Correo electrónico" es editable (`<input type="email" name="correo" value="..." required>`, sin `readonly`/`disabled`). Ya resolvimos que el correo debe ser de solo lectura para el colaborador — solo el Admin puede cambiarlo.
- [ ] **Habilidades como texto libre en vez de catálogo del Admin**: en `col-perfil.html`, modal "Agregar habilidad", el campo es `<input type="text" id="skillNameInput" placeholder="Ingresa nombre de habilidad...">`. Según la Épica 5 ("Catálogo de habilidades por categorías"), el propósito es estandarizar las skills de toda la organización y evitar duplicados — este campo debería ser un select/autocomplete que jale del catálogo que gestiona el Admin, no texto libre. El nivel de dominio sí está bien como `<select>`.
- [ ] **`col-foros.html` no coincide con `col-proyectos.html` (mock data inconsistente)**: `col-proyectos.js` tiene 6 proyectos (A, B, C, D, E, G) en `misProyectos`; `col-foros.js` solo tiene 2 (`const projectForums = [...]` con A y C). Son dos arrays hardcodeados por separado — al conectar a la BD real ambos deben leer de la misma fuente (asignaciones activas del colaborador).
- [ ] **Pestaña "Foro" dentro de `col-detalle-proyecto.html` está mal hecha**: es HTML estático hardcodeado (línea ~250, `<div class="forum-post-card">...`), sin conexión a `col-foros.js` ni a su lógica real (votos, respuestas, filtros). Por eso se ve visualmente distinta y más pobre que la página de Foros dedicada. Debería reusar el mismo componente/datos que `col-foros.js`, filtrado por el proyecto actual.

### Vigilar (no bloqueante, pendiente de definir prioridad)
- [ ] **A23 incompleto**: en `col-perfil.js`, un certificado es solo `certificado: true/false`, sin estado Pendiente/Aprobado/Rechazado. Falta ese ciclo de vida cuando se implemente A23 (revisión por el RM).
- [ ] **Scope extra no pedido**: `col-explorar.html` tiene una pestaña "Colaboradores" que permite a un colaborador buscar/ver perfiles de otros colaboradores. No está en ningún requerimiento (la "Consulta de colaboradores" es exclusiva de PM/RM). No rompe nada, pero es trabajo extra no solicitado.
- [ ] **Tier 3 ya construido**: pestaña "Cursos" en `col-explorar.html` corresponde a A25/A26 (Sistema de Cursos), que el equipo decidió posponer hasta después del Parcial. Ya está hecha — avisar al equipo por si ese tiempo rendía más en Tier 1.

### Lo que SÍ está bien en Colaborador (no tocar)
- Las 7 rutas del `ColaboradorViewController` bien mapeadas.
- `col-proyectos.html` ("Mis proyectos"): cumple A2 — solo lectura, filtros por estado, sin botones de acción en las tarjetas (verificado en el JS).
- `col-explorar.html` (pestaña Proyectos): cumple A3 — listado con postulaciones abiertas + modal "Postularme".
- `col-perfil.html`: habilidades, experiencia, educación, e "Historial de proyectos" correctamente marcado como solo lectura.

---

## Épica 5 — Administración del Sistema (`templates/admin/`)

Sección mejor construida hasta ahora — refleja bien las actualizaciones.

- [ ] **Falta el filtro por rango de fechas en `admin-auditoria.html`**. El requerimiento original pide explícitamente: "Se puede filtrar por usuario, tipo de acción y rango de fechas". Ahora mismo solo hay: buscar usuario, filtro por rol, filtro por tipo de acción — falta el selector de fechas (desde/hasta).
- [ ] **Falta el parámetro `NOMBRE_ORGANIZACION` en `admin-configuracion.html`**. Estaba contemplado en el DoR original junto a `MAX_ASIGNACIONES_POR_COLABORADOR`. Menor, pero fácil de agregar siguiendo el mismo patrón de tabla.
- [ ] **`admin-usuarios.html` — falta ejemplo de formato en la carga masiva**: el modal "Carga masiva de usuarios" solo dice "columnas requeridas: correo, rol" en texto, pero no muestra un ejemplo visual de cómo debe verse el CSV/Excel (ni un archivo de plantilla descargable). Agregar una tabla de ejemplo (2-3 filas de muestra) o un link "Descargar plantilla" dentro del modal.
- [ ] **`admin-configuracion.html` — las descripciones exponen jerga interna del proyecto ("Épica 9", "checkpoint del Parcial") en la UI**: esto no debería verlo un Administrador real usando la plataforma. Textos a corregir:
  - Columna Descripción de `TOPE_HORAS_EXTRA_BONO`: *"Máximo de horas extra pagables por mes en el cálculo de bono (Épica 9 — Horas y Pagos)."* → quitar la referencia a "Épica 9".
  - `title` del botón "Editar" deshabilitado: *"Se habilita cuando la Épica 9 entre en desarrollo"*.
  - Nota informativa debajo de la tabla: *"TOPE_HORAS_EXTRA_BONO pertenece a la Épica 9 (Horas y Pagos), diferida hasta después del checkpoint del Parcial..."*.
- [ ] **`admin-configuracion.html` — el botón "Editar" de `TOPE_HORAS_EXTRA_BONO` está deshabilitado, y no debería estarlo**: contradice **A16**, que dice explícitamente que este parámetro es *"editable por el Administrador"*, sin condicionarlo a que la Épica 9 esté activa. Que la Épica 9 (Horas y Pagos) esté diferida no significa que el parámetro de configuración no pueda editarse desde ya — corrección: habilitar el botón "Editar" igual que `MAX_ASIGNACIONES_POR_COLABORADOR`.

### Lo que SÍ está bien en Admin (no tocar)
- `admin-usuarios.html`: modal "Nuevo usuario" solo pide correo+rol (sin nombre) con mensaje explicando que el nombre/password se define al activar; carga masiva CSV/Excel con validaciones de duplicados (archivo + BD)/roles/filas incompletas; botón "Reenviar enlace" para pendientes; solo desactivación lógica (nunca eliminar).
- `admin-habilidades.html`: CRUD completo por categoría, desactivar (no eliminar) con reactivación, columna de colaboradores usándola.
- `admin-configuracion.html`: `MAX_ASIGNACIONES_POR_COLABORADOR` editable y con historial de cambios.
- `admin-auditoria.html`: refleja las actualizaciones — incluye "Activación de cuenta" (C3), "Asignaciones" con aprobar/rechazar (A11), "Certificados", y "Configuración" (C10) como tipos de acción auditados.

---

## Pendiente de revisar
- [ ] Admin (`templates/admin/`)
- [ ] PM (`templates/pm/`)
- [ ] RM (`templates/rm/`)
