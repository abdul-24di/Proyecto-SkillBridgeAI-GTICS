# Informe de equipo — Revisión de vistas HTML y plan hacia el 40% de CRUDs

Revisión de las vistas HTML construidas en `feature/vistas-html`, comparadas contra la lista de requerimientos original y `actualizaciones_lista_requerimientos_3.md`. Se revisaron a fondo **Auth, Colaborador y Admin — las correcciones de las 3 ya están aplicadas y verificadas en el navegador**. **PM y RM quedan pendientes** — al final de este documento hay un checklist guía para quien los revise.

Enfoque acordado: **corregir-y-construir por épica**, no "corregir todo primero, luego construir todo". Cada quien corrige su parte de las vistas justo antes de escribir el backend que la alimenta, para no bloquear al resto del equipo.

## Estado actual (dónde quedamos)

**Hecho y verificado — Fases 0 a 3** (ver detalle completo en la sección 2):
- Correcciones de HTML de Auth, Colaborador y Admin.
- Fundamento de backend: 28 entidades JPA + 28 repositories + Spring Security + `AuditoriaService`.
- Auth real de punta a punta: login contra BD con redirección por rol, activación de cuenta con token, recuperación de contraseña con código, logout — todo probado en el navegador contra la BD real.

**Siguiente al retomar: Fase 4** — conectar Usuarios/Habilidades/Configuración/Auditoría del Admin al backend real (el modal "Nuevo usuario" ya tiene el método `AuthService.invitarUsuario(...)` esperándolo). Ver "Próximos pasos inmediatos" al final de la sección 2.

**Pendiente sin tocar todavía**: revisión de vistas PM/RM (checklist en la sección 3), y todo lo que dependa de eso (Fase 6 — Épica 4, la pieza más grande).

---

## 1. Plan de correcciones HTML

### Épica 2 — Autenticación (`templates/auth/`) — ✅ CORREGIDO

- [x] **Pantalla "Verificar código" creada**: `templates/auth/verificar-codigo.html` (6 inputs con auto-avance y soporte de pegar código), ruta agregada en `AuthViewController`, y `auth.js` actualizado para redirigir correctamente en todo el flujo (`recuperar` → `verificar-codigo?email=...` → `nueva-contrasena` → `contrasena-actualizada`). Verificado end-to-end en el navegador, incluyendo el caso de código incorrecto.
- [x] **`auth/registro.html` eliminado**, junto con su ruta `/registro` en el controller.
- [x] **Link "¿No tienes cuenta? Regístrate aquí" eliminado** del login.

**Ya está bien (no tocar)**: `activar-cuenta.html` (correo readonly, nombre unificado, sin dropdown de rol/teléfono/empresa, maneja los 3 estados válido/expirado/usado), `nueva-contrasena.html` y `contrasena-actualizada.html` (completos y con navegación funcional).

---

### Épica 3 — Gestión de Colaboradores (`templates/col/`) — ✅ CORREGIDO

- [x] **C4 corregida**: el campo "Correo electrónico" en `col-perfil.html` ahora es `readonly disabled`, con nota explicando que solo el Admin puede cambiarlo.
- [x] **Habilidades ahora vienen del catálogo**: el campo pasó de `<input type="text">` a un `<select>` poblado desde un catálogo mock en `col-perfil.js` (excluye las que el colaborador ya tiene agregadas).
- [x] **Datos de proyectos unificados**: se extrajeron `projectForums`, `posts` y las funciones de render del foro a un archivo compartido `col-js/foro-data.js`, usado tanto por `col-foros.js` como por el detalle de proyecto. `projectForums` ahora tiene los mismos 6 proyectos que `misProyectos` en `col-proyectos.js`.
- [x] **Tab "Foro" del detalle de proyecto corregido — causa raíz encontrada**: el `<link>` apuntaba a `col-foro.css` (sin "s"), un archivo que **no existe** — el real es `col-foros.css`. Por eso no tenía ningún estilo aplicado. Se corrigió el link y además se reemplazó el HTML estático por un render real usando `posts` filtrados por el proyecto actual y el mismo componente `renderPost()` que usa la página de Foros — mismo look, mismos datos. El clic en una publicación o un voto redirige a la página de Foros completa (no se duplicó todo el subsistema de respuestas/votos ahí).
- [x] **Bug adicional encontrado y corregido al verificar**: `col-perfil.js` tenía un bloque muerto (líneas 200-234) que hacía `document.getElementById("editProfileModal").addEventListener(...)` sobre un modal que **ya no existe** en el HTML (fue reemplazado por el tab "Ajustes de Cuenta"). Esto lanzaba un error no capturado que **detenía la ejecución de todo el script después de ese punto** — por eso el fix del catálogo de habilidades tampoco funcionaba al probarlo. Se limpió el bloque huérfano y se conectó el input de foto directo al tab de Ajustes de Cuenta sin necesitar el modal. Se verificó que no queden más IDs referenciados en JS que no existan en el HTML.

**Vigilar (no bloqueante, sin prioridad urgente)**:
- Un certificado es solo `certificado: true/false` en `col-perfil.js`, sin estado Pendiente/Aprobado/Rechazado — falta ese ciclo de vida para cuando se implemente A23.
- `col-explorar.html` tiene una pestaña "Colaboradores" (buscar/ver perfiles de otros colaboradores) que no está en ningún requerimiento — es exclusivo de PM/RM. No rompe nada, pero es trabajo extra no solicitado.
- La pestaña "Cursos" en Explorar (A25/A26) ya está construida a pesar de ser Tier 3 diferido — avisar al equipo por si ese tiempo rendía más en Tier 1.

**Ya está bien (no tocar)**: las 7 rutas de `ColaboradorViewController`; "Mis proyectos" cumple A2 (solo lectura, sin botones de acción); "Explorar" (proyectos) cumple A3 (postulaciones + modal "Postularme"); perfil con habilidades/experiencia/educación/historial correctamente de solo lectura.

---

### A24 — Sistema de Notificaciones (transversal a los 4 roles)

- [ ] **Falta la sección/vista de notificaciones por completo.** El link "Ver todas las notificaciones" del dropdown de la campanita es un link muerto (`<a href="#">`, en `static/js/notificaciones.js`). No existe ningún `notificaciones.html` ni ruta.
- [ ] La vista que falta debe tener: filtros por tipo de evento según el rol, y filtro por rango de fechas.
- [ ] El clic en una notificación individual debe redirigir a la sección relevante (ya funciona parcialmente en el dropdown — reusar la misma lógica).
- Nota: A24 es **Tier 3 (diferido hasta después del Parcial)** — no bloquea el 40% de CRUDs, pero si el botón "Ver todas" queda visible mientras tanto, no debería ser un link muerto (quitarlo o deshabilitarlo).

**Ya está bien (no tocar)**: el dropdown de la campanita (contador de no leídas, marcar todas/individual como leída) ya funciona bien como base.

---

### Épica 5 — Administración del Sistema (`templates/admin/`) — ✅ CORREGIDO

Sección mejor construida desde el inicio — refleja bien las actualizaciones. Todas las correcciones eran menores.

- [x] **Botón "Editar" de `TOPE_HORAS_EXTRA_BONO` habilitado**, con el mismo comportamiento que `MAX_ASIGNACIONES_POR_COLABORADOR` (conecta al modal genérico `#editParamModal` vía `data-param-*`).
- [x] **Jerga interna eliminada**: se reescribió la descripción de `TOPE_HORAS_EXTRA_BONO`, el `title` del botón y la nota informativa completa (que se quitó, ya no aplica) — ya no mencionan "Épica 9" ni "checkpoint del Parcial".
- [x] **Filtro por rango de fechas agregado en `admin-auditoria.html`**: dos inputs `type="date"` (Desde/Hasta) junto a los filtros existentes.
- [x] **Ejemplo de formato agregado en la carga masiva**: tabla con 3 filas de muestra dentro del modal, más un link real "Descargar plantilla .csv" que apunta a un archivo `.csv` de ejemplo agregado en `static/plantillas/usuarios-plantilla.csv`.
- [x] **Parámetro `NOMBRE_ORGANIZACION` agregado** a `admin-configuracion.html`, siguiendo el mismo patrón de fila que los demás parámetros. Se cambió el input del modal de editar parámetro de `type="number"` a `type="text"` para que funcione tanto con parámetros numéricos como de texto.

**Ya está bien (no tocar)**: Usuarios (modal solo correo+rol, carga masiva con validaciones, "Reenviar enlace", solo desactivación lógica); Habilidades (CRUD por categoría, desactivar/reactivar); Auditoría (incluye Activación de cuenta [C3], Asignaciones aprobar/rechazar [A11], Certificados y Configuración [C10] como tipos auditados).

---

## 2. Plan de CRUDs — fases hacia el 40%

Orden propuesto, a validar en equipo. Cada fase corrige su parte de las vistas justo antes de escribir el backend correspondiente.

| Fase | Qué incluye | Estado |
|---|---|---|
| **0** | Setup Spring Boot + Thymeleaf conectado a `skillbridge_db_v4.sql`, vistas HTML de las 4 áreas construidas | ✅ Hecho |
| **1** | Corregir las correcciones de Auth, Colaborador y Admin de este informe (Auth primero — es estructural) | ✅ Hecho y verificado en el navegador |
| **2** | Fundamento de backend: entidades JPA + repositories alineados al schema v4 (28 tablas — `rol`, `usuario`, `token_usuario`, `proyecto`, `asignacion`, `habilidad`, `certificado`, `foro`, `notificacion`, `log_auditoria`, etc.), Spring Security, `AuditoriaService` reutilizable | ✅ Hecho y verificado |
| **3** | Épica 2 real: login contra BD, sesión, activación de cuenta con token de un solo uso, recuperación de contraseña con código | ✅ Hecho y verificado |
| **4** | Épica 5 básica: usuarios (individual + carga masiva), catálogo de habilidades, configuración de parámetros, log de auditoría conectado de verdad | ▶️ Siguiente |
| **5** | Épica 3: perfil del colaborador, habilidades (desde el catálogo del Admin), experiencia, disponibilidad, consulta de colaboradores para PM/RM | Por hacer |
| **6** | Épica 4 — la pieza más grande: info y estado del proyecto, triple origen de asignación con doble aprobación (A4), solicitudes del colaborador (A2/A3), desasignación (A18), presupuesto (A20). Depende de que se corrijan las vistas de PM/RM primero | Por hacer |
| **7** | Foro/Chat básico (sin votos) + AI Talent Matching con explicación — cierre del alcance Tier 1 | Por hacer |

### Qué se construyó en la Fase 2

- **28 entidades JPA** en `model/`, una por cada tabla de `skillbridge_db_v4.sql`, con nombres de columna (`@Column(name=...)`) transcritos 1:1 del schema. Incluye 5 claves compuestas (`@EmbeddedId`) para las tablas N:M: `colaborador_habilidad`, `proyecto_habilidad_requerida`, `voto_publicacion`, `voto_respuesta`, `conversacion_usuario`.
- **17 enums** en `model/enums/` que mapean cada `CHECK` constraint del schema (`EstadoProyecto`, `OrigenAsignacion`, `EstadoAsignacion`, `Prioridad`, `NivelDominio`, etc.), con comentarios que citan la historia/decisión de las actualizaciones a la que corresponden (A4, A6, A16, A18, A20, C7, C11, C14, etc.).
- **28 repositories** en `repository/`, con métodos de búsqueda solo donde hay una necesidad concreta ya identificada (p. ej. `AsignacionRepository.findByColaboradorAndEstado` para la carga de trabajo antes de aprobar en A4; `ForoRepository.findByEsPublicoTrue` para la regla de A7/A9 del RM).
- **`AuditoriaService`**, con un método `registrar(...)` reutilizable, listo para llamarse desde cualquier flujo (asignaciones, certificados, usuarios, configuración).
- **Spring Security agregado** al `pom.xml`, con un `PasswordEncoder` (BCrypt) ya definido para cuando la Fase 3 implemente la activación de cuenta (A6) y el login real. La config queda permisiva por ahora (`permitAll`) para no romper las vistas ya construidas.
- **Verificado**: compiló sin errores (`mvn compile` con BUILD SUCCESS), la app arrancó correctamente contra la BD real, y los conteos de columnas de las 28 tablas en MySQL coinciden exactamente con las entidades.
- Las tablas de la Épica 9 (Horas y Pagos: `actividad`, `curso`, `colaborador_curso`, `penalizacion`, `nomina_mensual`) y Cursos ya están modeladas (porque ya existen en el schema), pero su service/controller se deja para cuando el equipo decida retomar esa épica — siguen Tier 3 diferidas.

### Qué se construyó en la Fase 3

- **`AuthService`** — dueño único de toda la lógica de tokens (C1/C2): `invitarUsuario`, `reenviarActivacion`, `activarCuenta`, `solicitarRecuperacion`, `validarCodigoRecuperacion`, `restablecerContrasena`. Tokens de activación válidos 48h (coincide con el texto ya existente en la vista); código de recuperación de 6 dígitos válido 15 min.
- **`EmailService`** — stub que loguea a consola (no hay SMTP configurado todavía); interfaz lista para cambiar la implementación sin tocar el resto del código cuando se agregue un servidor de correo real.
- **Login real con Spring Security**: `UsuarioDetailsService` + `UsuarioDetails` (el correo es el username, C4), `RoleRedirectSuccessHandler` (redirige a `/admin/dashboard`, `/pm/proyectos`, `/rm/dashboard` o `/colaborador/dashboard` según el rol), soporte de "Recordar sesión" y logout. El formulario de login ya usaba los nombres de campo por defecto de Spring Security, así que se integró directo sin JS.
- **Los 3 estados del enlace de activación** (válido / expirado-inválido / ya usado) y los 3 pasos de recuperación (código → nueva contraseña → confirmación) quedaron con controllers y templates reales, reemplazando toda la lógica falsa en JS.
- **Bug real encontrado y corregido durante la verificación**: `LazyInitializationException` al leer el rol del usuario después del login (la relación `usuario.rol` es `LAZY` y se intentaba leer fuera de la sesión de Hibernate). Se corrigió calculando las autoridades una sola vez dentro de una transacción (`UsuarioDetailsService` con `@Transactional`).
- **Verificado end-to-end en el navegador y con la BD real**: activación con token válido/expirado/usado, login con contraseña correcta/incorrecta, recuperación completa con código real generado y consultado en MySQL, cambio de contraseña confirmado con un segundo login, logout, y las 5 entradas correspondientes en `log_auditoria` (activación, logins, recuperación).

### Archivos nuevos de backend (Fases 2 y 3)

```
src/main/java/com/pucp/skillb_ia/
├── model/                          28 entidades JPA (una por tabla del schema v4)
│   ├── enums/                      17 enums (mapean los CHECK constraints)
│   └── *Id.java                    5 claves compuestas (@Embeddable)
├── repository/                     28 repositories (Spring Data JPA)
├── service/
│   ├── AuditoriaService.java       registrar(...) reutilizable — log_auditoria
│   ├── AuthService.java            toda la lógica de activación/recuperación (C1/C2)
│   └── EmailService.java           stub que loguea a consola (sin SMTP todavía)
├── security/
│   ├── UsuarioDetails.java         adapta Usuario a Spring Security
│   ├── UsuarioDetailsService.java  carga el usuario por correo (username)
│   └── RoleRedirectSuccessHandler.java   redirige al dashboard según el rol
└── config/
    └── SecurityConfig.java         login/logout/remember-me + PasswordEncoder
```

Además: `pom.xml` con `spring-boot-starter-security` agregado.

### Próximos pasos inmediatos
1. ~~Corregir Auth, Colaborador y Admin~~ — ✅ hecho y verificado.
2. ~~Fase 2: entidades + repositories + Security~~ — ✅ hecho y verificado.
3. ~~Fase 3: Auth real (login, activación, recuperación)~~ — ✅ hecho y verificado.
4. Asignar la revisión de PM/RM usando el checklist de la sección 3 (sigue pendiente).
5. Arrancar la Fase 4 (Épica 5 básica: usuarios, habilidades, configuración, auditoría conectados de verdad) — es lo que sigue en el plan. Ahí se conecta el modal "Nuevo usuario" del Admin a `AuthService.invitarUsuario(...)`, que ya existe y está probado.

---

## 3. Indicaciones para quien revise PM y RM

No se hizo una revisión completa de `templates/pm/` y `templates/rm/`. Checklist basado en los patrones de bugs repetidos en Auth/Colaborador/Admin, más los puntos específicos de la Épica 4. Un vistazo rápido ya mostró señales buenas: `rm-foros.html` dice explícitamente *"El RM puede consultar todos los foros; no puede crear publicaciones, responder ni acceder al chat"* (cumple A7/A9), no existe `rm-chat.html` (correcto), y `rm-talent-matching.html` ya usa el texto **"Proponer para el proyecto"** (cumple A12).

### Patrones de bugs a repetir el chequeo
- [ ] **Correo editable en `pm-perfil.html` y `rm-perfil.html`**: mismo bug que encontramos en Colaborador (viola C4). Revisar si se repite.
- [ ] **Datos mockeados inconsistentes entre pantallas relacionadas**: revisar si `pm-proyectos.html` ↔ `pm-foros.html` ↔ `pm-chat.html` ↔ `pm-reportes.html` (y sus equivalentes RM) tienen listas de proyectos distintas hardcodeadas por separado, como pasó en Colaborador.
- [ ] **Tabs de foro/chat embebidas en el detalle de proyecto, hardcodeadas**: revisar si `pm-detalle-proyecto.html` tiene el mismo problema que `col-detalle-proyecto.html` (HTML estático desconectado del JS real).

### Específico de Épica 4 (A4, A18, A20, Bloque B punto 3)
- [ ] **`pm-crear-proyecto.html`**: confirmar que el campo prioridad (Alta/Media/Baja) requiera justificación de texto, y que el proyecto nazca en estado "En revisión".
- [ ] **Estados del proyecto**: verificar los 5 estados acordados — En revisión → Activo | Rechazado → En espera | Cancelado | Finalizado. Y que el PM pueda cancelar su proyecto mientras esté "En revisión", antes de que el RM decida.
- [ ] **Los 3 orígenes de asignación (A4)**: verificar el flujo de propuesta desde el PM (botón "Proponer", no "Confirmar"/"Asignar directo"), y que las vistas de RM (`rm-proponer-asignacion.html`, `rm-revision-asignacion.html`, `rm-detalle-asignacion-pendiente-pm.html`) muestren disponibilidad y carga de trabajo del colaborador antes de aprobar.
- [ ] **Bandeja de aprobaciones pendientes para el PM**: RM ya tiene varias (`rm-solicitudes-colaboradores.html`, `rm-detalle-proyecto-solicitudes.html`). Verificar que el PM tenga su equivalente.
- [ ] **A18 — desasignación por PM o RM sin aprobación del otro**: verificar que ambos roles puedan finalizar una asignación activa directamente.
- [ ] **A20 — presupuesto exclusivo del RM**: "presupuesto" solo debería aparecer editable en templates de RM (`rm-revision-proyecto.html` ya tiene "Asignar presupuesto"). Verificar que en PM (`pm-reporte-detalle.html`) sea solo de lectura.

### Reportes (A21/A22) y certificados (A23)
- [ ] `pm-reportes.html` / `pm-reporte-detalle.html` (A21) y `rm-reporte-recursos.html` (A22): verificar exportación a PDF y Excel (C18). Ojo: dependen de la Épica 9 (Tier 3 diferida) — si ya muestran "horas cumplidas" sin que exista el sistema de tareas/horas, es trabajo adelantado sobre una dependencia inexistente aún.
- [ ] `rm-certificados-pendientes.html` / `rm-revision-certificado.html` (A23): verificar bandeja de pendientes, aprobar/rechazar con motivo, y nivel de experiencia (Junior/Senior) editable por el RM.

### Tier 3 ya construido
- [ ] `rm-cursos.html`, `rm-asignar-curso.html`, `rm-solicitudes-cursos.html` (A25/A26/A27) y `rm-horas-colaboradores.html` (Épica 9) ya están construidos a pesar de estar diferidos hasta después del Parcial. No es un error, pero avisar al equipo por si ese tiempo rendía más en Tier 1.
