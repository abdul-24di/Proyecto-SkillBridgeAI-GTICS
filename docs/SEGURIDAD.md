# Seguridad y control de acceso — SkillBridge AI

Estado al **4 de noviembre de 2026** (rama `feature/cruds`). Resume cómo está protegida la aplicación, qué se cambió en la revisión de seguridad, qué tiene que tener en cuenta el equipo al programar y qué queda pendiente.

---

## 1. Cómo está protegida la aplicación (resumen)

| Tema | Cómo funciona | Dónde está |
|---|---|---|
| Inicio de sesión | `formLogin` de Spring Security con un `UserDetailsService` propio sobre JPA; contraseñas con BCrypt. Al entrar, cada rol va a su panel. | `config/SecurityConfig.java`, `security/UsuarioDetails*`, `security/RoleRedirectSuccessHandler.java` |
| Sesiones | Spring Session JDBC (tablas `SPRING_SESSION*`, cookie `SESSION`) y "Recordarme". | `application.properties` |
| Acceso por rol | `/admin/**`, `/pm/**`, `/rm/**` y `/colaborador/**` exigen su rol; el resto (salvo login, activación y recuperación) exige sesión. | `SecurityConfig.java` |
| Acceso denegado | Página 403 propia y registro del intento en la auditoría. | `security/RegistroAccesoDenegadoHandler.java`, `templates/error/403.html` |
| CSRF | Activado: todo POST debe traer el token de la sesión. | `SecurityConfig.java`, `static/js/csrf.js` |
| Sesión activa | Con sesión abierta, `/login` lleva al panel: no se puede iniciar otra sesión encima. | `security/SesionActivaLoginFilter.java` |
| Activación y recuperación | Tokens de un solo uso: enlace de activación (48 h) y código de 6 dígitos (15 min, 5 intentos). | `service/AuthService.java`, `controller/AuthViewController.java` |
| Auditoría | Acciones críticas con usuario, fecha, detalle e IP; vista con filtros y exportación CSV/Excel. | `service/AuditoriaService.java`, `/admin/auditoria` |
| Archivos subidos | `/uploads/**` exige sesión; todo archivo se revisa por su contenido real, no solo por el tipo que declara el navegador. | `SecurityConfig.java`, `service/ArchivoAlmacenamientoService.java` |
| Fuerza bruta | Tras 5 contraseñas incorrectas, la cuenta se bloquea 15 minutos. | `security/UsuarioDetailsService.java`, `security/RegistroLoginFallidoHandler.java` |
| Exceso de peticiones | Límite por IP y por minuto; al pasarse responde 429. | `security/LimiteSolicitudesFilter.java` |
| XSS | Thymeleaf escapa todo con `th:text`; el HTML de los foros (`th:utext`) se limpia con Jsoup antes de guardarse. | `security/HtmlSeguro.java` |
| Errores | Páginas propias (403, 429, 4xx, 5xx), sin detalles técnicos. | `templates/error/`, `controller/GlobalExceptionHandler.java` |

---

## 2. Cambios de la revisión de seguridad (21-oct-2026)

### 2.1 Protección CSRF
- Se quitó `csrf.disable()`. Cada POST debe incluir el token de la sesión; si no lo trae, responde **403**.
- Los formularios con `th:action` reciben el token **solos** (Thymeleaf agrega el campo oculto `_csrf`).
- Formularios cuyo `action` pone el JavaScript (`col-detalle-proyecto.html`, modales de `rm-solicitudes-cursos.html`): llevan el campo oculto a mano.
- Peticiones `fetch` (notificaciones, chats del colaborador y PM, imágenes del foro del PM) y formularios creados con JS (cancelar proyecto del PM): usan `static/js/csrf.js`, que lee el token de los `<meta name="_csrf">` de cada topbar.
- **Cerrar sesión ahora es POST** (botón "Salir" de los 4 topbars, página 403 y "Registro en revisión"). `GET /logout` ya no existe.
- Si el token falla (p. ej. la sesión expiró con un formulario abierto), la página 403 dice "El formulario ya no es válido" y la auditoría lo registra como token CSRF inválido, no como rol equivocado.

### 2.2 No se puede iniciar sesión encima de otra (observación de la profesora)
- Antes: con una sesión abierta se podía volver a `/login` y entrar con otra cuenta; la nueva reemplazaba a la anterior sin aviso.
- Ahora: con sesión activa, `GET` y `POST` de `/` y `/login` redirigen al panel del rol actual. Para cambiar de cuenta hay que cerrar sesión.
- Nota: dos pestañas del mismo navegador siempre comparten la sesión (así funciona cualquier web); para probar dos roles a la vez se usa una ventana de incógnito u otro navegador.

### 2.3 Acceso denegado auditado
- Un usuario con sesión que entra a una ruta de otro rol ve una página 403 propia ("No tienes permiso…" con botón "Volver a mi panel" según su rol) en vez del error crudo.
- El intento queda en la auditoría como **"Acceso denegado"** con el usuario, la ruta y la IP.

### 2.4 IP en la auditoría
- `AuditoriaService` toma la IP de la petición en curso, así que todas las llamadas existentes la guardan sin cambiarlas.
- Columna **IP** en `/admin/auditoria` y en las exportaciones CSV y Excel. Las acciones automáticas (sin petición web) quedan sin IP.
- En local se ve `0:0:0:0:0:0:0:1` (localhost en IPv6). Si en EC2 se pone nginx u otro proxy delante, agregar `server.forward-headers-strategy=native` para registrar la IP real del cliente.

### 2.5 Recuperación de contraseña
- **Hueco corregido:** antes, `POST /nueva-contrasena` aceptaba el código de 6 dígitos sin comprobar el correo, así que se podía saltar el paso de verificación y probar códigos contra cualquier cuenta. Ahora el código verificado se guarda en la **sesión del servidor** (ya no viaja en la URL ni en el formulario).
- **Tope de 5 intentos:** al quinto código incorrecto se anula el código y hay que pedir uno nuevo. Los fallos quedan en la auditoría ("Código de recuperación incorrecto") y sirven de contador (sin cambios en la BD).
- **Pedir un código nuevo anula los anteriores.**
- **Cambiar la contraseña cierra las sesiones abiertas** de esa cuenta en otros equipos.
- Solo cuentas **activadas** y activas pueden recuperar contraseña: una cuenta invitada que nunca se activó no puede usar la recuperación para saltarse el pre-registro.

### 2.6 Activación de cuenta
- **Reenviar la invitación invalida el enlace anterior:** solo sirve el último enlace enviado; el viejo muestra "El enlace ya no es válido".

### 2.7 Gestión de usuarios
- **No se puede dejar el sistema sin Administradores:** el servidor rechaza quitarle el rol al último Administrador activo, aunque se intente con una petición hecha a mano.

### 2.8 Archivos subidos
- `/uploads/**` ya no es público: sin sesión, CV, certificados, fotos y evidencias redirigen al login.

### 2.9 Base de datos
- `BDs_SQL/seed_habilidades.sql`: carga las 14 habilidades base (Java, Python, JavaScript, Spring Boot, React, Angular, MySQL, PostgreSQL, MongoDB, AWS, GCP, Docker, Git, Scrum) en una BD existente. `skillbridge_db_v4.sql` ya las trae para instalaciones nuevas.
- `BDs_SQL/migracion_motivo_cierre.sql`: agrega `proyecto.motivo_cierre` y el motivo `PROYECTO_FINALIZADO` (cambios del cierre de proyectos del RM) en una BD existente.
- Ambos scripts no borran datos y se pueden ejecutar más de una vez.

### 2.10 Pruebas
- Suite completa: **738 tests, 0 fallos**. Nuevos tests para cada punto anterior (en `AdminPerfilYCatalogosTests` y `PreRegistroColaboradorTests`); varios pasan por el filtro de seguridad real (CSRF, acceso denegado). Para los más importantes se comprobó que el test falla si se quita el arreglo.
- Además se probó todo en la aplicación real con los 4 roles.
- Se ajustaron 3 tests del RM al CSRF sin quitarles lo que verifican (`RmNavegacionTests`, `RmForoTests`, `RmEducacionTests`).

---

## 2B. Preparación para el test de seguridad (4-nov-2026)

### Disponibilidad (que no tumben la página)
- **Límite por IP** (`LimiteSolicitudesFilter`): 300 peticiones por minuto en general y 20 envíos por minuto en los formularios públicos (login, recuperar, activar), que son los más caros. Va antes de leer la sesión en la BD, así que una IP que inunda el sitio casi no consume recursos. Los archivos estáticos no cuentan. Se ajusta con `app.limite.peticiones-por-minuto` y `app.limite.formularios-por-minuto`.
- **Tomcat:** corta conexiones lentas a los 10 s (ataque *slowloris*), limita lo que lee de una subida rechazada (antes era ilimitado) y baja el tamaño máximo de un formulario de texto a 5 MB.
- **Rendimiento:** el caché de Thymeleaf queda activo y deja de imprimirse cada SQL. Hikari responde error en 5 s en vez de dejar hilos colgados 30 s.
- **Correos:** máximo uno por minuto a la misma cuenta en la recuperación. La carga masiva admite hasta 200 filas.

### Accesos
- **Bloqueo de cuenta:** 5 contraseñas incorrectas en 15 minutos la bloquean, aunque luego se acierte. Spring lo revisa antes que BCrypt, así que el ataque tampoco gasta CPU. Se reinicia con un login correcto o al recuperar la contraseña, y se usa la auditoría como contador (acción `LOGIN_FALLIDO`), sin cambiar la BD.
- **Desactivar o cambiar el rol de un usuario cierra sus sesiones abiertas.** Antes seguía entrando con la sesión que ya tenía.
- **Recordarme:** la clave ya no está escrita en el código (`REMEMBER_ME_KEY`).
- **Contraseña del PM:** usa la misma política que el resto (8 caracteres, mayúscula, número y símbolo).

### Datos maliciosos
- **XSS guardado en foros:** antes se podía publicar `<script>` y se ejecutaba en el navegador de quien leyera, incluido el Admin. Ahora `PublicacionForo` y `RespuestaForo` limpian el HTML al asignarlo, sin importar qué servicio lo guarde. Se conservan negritas, listas, enlaces e imágenes.
- **Archivos falsos:** un `.exe` enviado como `application/pdf` ya no pasa. `ArchivoAlmacenamientoService.guardar()`, por donde pasan todas las subidas, compara los primeros bytes con la firma real de PDF, PNG, JPG, GIF y WEBP.
- **Correo y nombre:** ya no aceptan etiquetas ni caracteres peligrosos.
- **Redirecciones:** el `Referer` y los valores escritos por el usuario ya no se concatenan en un `redirect:`. Antes un `Referer` manipulado podía llevar a otro sitio.
- **Cabeceras HTTP:** `Content-Security-Policy` básica y `Referrer-Policy`, además de las que Spring agrega por defecto.

### Errores
- Páginas propias para 404, 4xx, 429 y 5xx.
- El PM, el RM y el pre-registro ya no muestran mensajes técnicos: todos usan `GlobalExceptionHandler.mensajeSeguro`.
- Corregidos dos errores 500: un dato inválido en el perfil del PM y una subida sin archivo al foro del PM.

### Pre-registro
- **Los archivos ya no se pierden:** cada archivo válido se guarda en una carpeta temporal aunque otros campos tengan errores (`PreRegistroArchivosService`). En la sesión solo van sus datos, y el formulario envía solo un id, que se busca en la sesión de quien subió el archivo. Al terminar se borran; los abandonados, a las 24 horas.
- **Errores exactos:** una lista arriba indica el paso y el campo ("Paso 3 · Certificado 2 · Nivel: Selecciona tu nivel"), y cada enlace lleva a ese campo.
- **Por pasos:** el colaborador ve 4 pasos (`preregistro-pasos.js`), pero es un solo formulario y `@Valid` sigue siendo la validación real. Sin JavaScript funciona igual. Los demás roles conservan su formulario corto.
- **Revisión al elegir el archivo:** formato y tamaño se avisan en el momento.
- Con un enlace inválido no se procesa ni se guarda nada.

### Cómo se probó
- **767 tests, 0 fallos**, 13 de ellos nuevos en `SeguridadTests`.
- Ataque a la app real sobre una **copia** de la BD: 730 GET y 224 POST con valores basura en las rutas de los 4 roles. El único error 500 que apareció (perfil del PM) se corrigió y el POST se repitió limpio. También inundación con 600 peticiones en paralelo, fuerza bruta, subida de 70 MB (rechazada en 17 ms), XSS real en un foro y 14 intentos de crear usuarios inválidos.

### Qué NO protege la aplicación
El filtro corta a una IP que abusa, pero un ataque distribuido (muchas IPs) o que sature la red se frena en la infraestructura: *Security Group* de AWS, un balanceador o AWS WAF. Además, si la app queda detrás de nginx o un balanceador, todos los usuarios llegarían con la misma IP y compartirían el límite: en ese caso **hay que** activar `SERVER_FORWARD_HEADERS_STRATEGY=native`.

---

## 3. Reglas para el equipo al programar

1. **Todo formulario POST con `th:action="@{...}"`.** Así recibe el token CSRF automáticamente. Si el `action` lo pone el JavaScript, agregar dentro del form:
   ```html
   <input type="hidden" th:name="${_csrf?.parameterName}" th:value="${_csrf?.token}">
   ```
2. **Todo `fetch` que haga POST debe mandar el token:**
   ```js
   fetch(url, { method: 'POST', headers: csrfHeaders() })
   // con otras cabeceras: headers: csrfHeaders({'Content-Type': 'application/x-www-form-urlencoded'})
   ```
   Para un `<form>` creado con JS: `agregarCsrfAFormulario(form)` antes de `form.submit()`. `csrf.js` se carga desde los topbars.
3. **"Cerrar sesión" es un formulario POST**, nunca `<a href="/logout">`:
   ```html
   <form th:action="@{/logout}" method="post"><button type="submit">Salir</button></form>
   ```
4. **Si un POST da 403** al probar, casi siempre es que le falta el token CSRF.
5. **Si tienen la BD creada de antes**, ejecutar `BDs_SQL/migracion_motivo_cierre.sql` (sin él salen errores 500 por "Unknown column motivo_cierre") y, opcionalmente, `BDs_SQL/seed_habilidades.sql`.
6. **Credenciales** (correo SMTP, AWS) solo como variables de entorno; nunca en `application.properties` ni en el repositorio.
7. **Para ver cambios de HTML sin reiniciar,** definir `THYMELEAF_CACHE=false` en IntelliJ. Para ver los SQL: `SHOW_SQL=true`. Por defecto ambos están apagados, porque así debe correr en la nube.
8. **Mensajes de error:** en un `catch (Exception e)`, usar `mensajeSeguro(e)`, nunca `e.getMessage()`.
9. **En los tests,** un archivo de prueba debe parecer real: `"%PDF-1.4 prueba".getBytes()`. Con bytes cualquiera, ahora se rechaza.
10. **Ventana del límite:** si probando se recibe "Demasiadas solicitudes", basta esperar un minuto.

---

## 4. Pendientes

### Seguridad
- **Archivos por dueño:** cualquier usuario con sesión puede abrir un archivo de `/uploads` si conoce su URL (los nombres llevan UUID, difíciles de adivinar). Falta servirlos por un controlador que valide que sea el dueño, el Admin o el RM.
- **Archivos en S3:** en modo nube los archivos se sirven desde la URL del bucket y la regla de `/uploads` no los protege. Requiere bucket privado y URLs prefirmadas.
- **Datos en la sesión:** la sesión guarda el `Usuario` completo (incluye hash de contraseña y sueldo). Conviene reducirlo a id, correo, nombre, foto y rol.
- **Privacidad salarial:** el RM recibe el sueldo base de cada colaborador en el HTML de "Proponer asignación" (`data-sueldo`) para estimar costos. Decidir con el equipo si el RM puede verlo; si no, calcular el costo en el servidor y enviar solo el total.
- **Proxy en EC2:** si se usa nginx o un balanceador, configurar `SERVER_FORWARD_HEADERS_STRATEGY=native` (ver 2.4 y 2B).
- **HTTPS:** con HTTP las contraseñas viajan sin cifrar. Al tenerlo, activar además la cookie `Secure` (`SERVER_SERVLET_SESSION_COOKIE_SECURE=true`).
- **Bloqueo y enumeración:** el mensaje de cuenta bloqueada deja deducir que ese correo existe. Se prefirió avisar al usuario.
- `spring-security-test` no está en el `pom.xml`; los tests de seguridad usan el filtro real armado a mano. Agregarlo simplificaría esas pruebas.
- `JdbcUserDetailsManager` (visto en clase) no se usa: se mantiene el `UserDetailsService` propio sobre JPA.

### Funcionalidad del Administrador
- **`ConfiguracionService.getValor(clave)`:** no existe; cada módulo lee `ConfiguracionSistemaRepository` directo. Crear el servicio y, avisando a las compañeras, migrar RM y Colaborador a él.
- **Desactivar una habilidad en uso:** hoy no verifica si la usan colaboradores o proyectos. Mostrar en el modal cuántos colaboradores y proyectos la usan antes de confirmar.
- **Foto en la lista de usuarios** (`/admin/usuarios`): hoy solo muestra iniciales.
- **Detalle del pre-registro** (`/admin/experiencia/{id}`): mostrar los certificados y la formación del colaborador (solo lectura, "los valida el RM") y renombrar el formulario a "Complementar experiencia (opcional)".
- **Snapshot de nómina mensual:** la entidad `NominaMensual` existe pero nada la usa; el bono se calcula al vuelo. Falta el cierre mensual que guarde el snapshot y definir quién lo dispara.
- **Tests de los cálculos del bono** (meta 160 h, horas extra, tope `TOPE_HORAS_EXTRA_BONO`, strikes).

### Detalles menores
- Nombres de prototipo (Abraham Ramirez, Sofía Alarcón, Abdon Vallejo, iniciales AR/SA/AV) como texto de ejemplo en varias plantillas.
- Teléfono del RM y PM acepta 7–20 dígitos; Admin, pre-registro y colaborador exigen 9.
- PM: mensajes sin tildes en `PmProyectoForm`.
- Si falla el servidor de correo, la pantalla igual dice que se envió la invitación; el fallo solo queda en el log. Se puede reenviar desde la lista de usuarios.
- Aviso "próximamente" en el Asistente IA del PM (`pm-asistente-ia.html`). El del colaborador y la pestaña Chat de su detalle de proyecto ya lo tienen; los chats del colaborador y del PM funcionan.
- En la BD de demo, Java, Spring Boot y AWS existen dos veces (`Técnico` y `[DEMO] Tecnologia`) porque `datos_demo_rm_proyectos.sql` crea su propia categoría.
