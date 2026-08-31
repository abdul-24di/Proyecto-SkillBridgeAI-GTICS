# Consideraciones sobre la Base de Datos — SkillBridge AI

Acompaña a `schema.sql`. El script **ya fue probado contra un servidor MariaDB real** (no es teórico): se crearon las 25 tablas, se insertaron datos de ejemplo, y se verificó que las reglas de negocio (constraints) rechazan lo que deben rechazar. Detalle de las pruebas al final de este documento.

---

## 1. Resumen — 25 tablas, organizadas por épica

| Épica | Tablas |
|---|---|
| 2 — Autenticación | `usuario`, `token_activacion`, `token_recuperacion_password` |
| 3 — Colaboradores | `colaborador`, `categoria_habilidad`, `habilidad`, `colaborador_habilidad`, `experiencia_profesional`, `certificado` |
| 4 — Proyectos y Asignación | `proyecto`, `proyecto_habilidad_requerida`, `asignacion` |
| 5 — Administración | `configuracion_sistema`, `log_auditoria` |
| 7 — Foros y Chat | `etiqueta`, `publicacion_foro`, `publicacion_etiqueta`, `respuesta_foro`, `voto_publicacion`, `voto_respuesta`, `mensaje_chat` |
| 9 — Horas y Pagos | `tarea`, `nomina_mensual` |
| Cursos (nuevo) | `curso`, `colaborador_curso` |
| Notificaciones (nuevo) | `notificacion` |

No incluí tablas para Épica 1 (infraestructura), 6 (dashboards/reportes — son consultas sobre las tablas existentes, no necesitan tabla propia) ni 8 (IA — el matching y el chatbot son llamadas a una API externa en el momento, no requieren persistencia propia).

---

## 2. Por qué VARCHAR y no ENUM nativo de MySQL (importante para escalabilidad)

Campos como `rol`, `estado`, `prioridad`, `origen` están declarados como `VARCHAR` + un `CHECK` de validación, **no como el tipo `ENUM` nativo de MySQL**. Es intencional:

- Si usan `ENUM('ACTIVO','INACTIVO')` y luego necesitan agregar un valor nuevo (por ejemplo, si deciden agregar un estado "En pausa por vacaciones" a las asignaciones), tienen que hacer un `ALTER TABLE` — una migración de esquema.
- Con `VARCHAR` + `CHECK`, agregar un valor nuevo es modificar la restricción `CHECK` (un ALTER también, pero mucho más simple) y en JPA solo es agregar una constante al `enum` de Java — nada en los datos existentes se ve afectado.
- En Spring Data JPA, esto se mapea con `@Enumerated(EnumType.STRING)`, que es exactamente el patrón que enseña la mayoría de cursos de Spring Boot.

**Ya viene pensado para lo que aún está pendiente de decidir**: si más adelante agregan un estado nuevo a `proyecto` o `asignacion`, no rompen nada del modelo ya construido.

---

## 3. La tabla `colaborador` está separada de `usuario`

`usuario` tiene lo mínimo para autenticación (correo, password, nombre, rol). `colaborador` es una extensión 1:1 (mismo `id` que `usuario`) solo para quienes tienen ese rol, con los campos que **no aplican a PM/RM/Admin**: `disponibilidad_horas`, `sueldo_base`, `nivel_experiencia`.

Esto evita llenar la tabla `usuario` de columnas que quedarían `NULL` para el 75% de los roles, y aísla `sueldo_base` — un dato sensible (ver duda C7 de la lista de requerimientos) — en una tabla que pueden proteger con una consulta/permiso distinto si más adelante formalizan quién puede verla.

**Ojo:** en tu capa de aplicación (Spring Security), esto significa que un `Usuario` con rol `COLABORADOR` siempre debe tener una fila correspondiente en `colaborador` — conviene crearla en el mismo momento en que el Admin da de alta la cuenta (Historia 5.1), no dejarlo para después.

---

## 4. `proyecto_habilidad_requerida` — tecnologías y habilidades comparten catálogo

La Historia de "Gestión de información del proyecto" pide registrar tanto "tecnologías utilizadas" como "habilidades requeridas". En vez de crear dos catálogos separados, ambos se modelan contra la **misma tabla `habilidad`** (que ya tiene categorías como Frameworks, Cloud, Lenguajes — las tecnologías caen naturalmente ahí).

Si más adelante el equipo decide que "tecnología" y "habilidad requerida" deben ser conceptualmente distintas (por ejemplo, para que el AI Talent Matching las pese diferente), es un cambio de una sola columna (`proyecto_habilidad_requerida.tipo VARCHAR`), no una tabla nueva.

---

## 5. `nomina_mensual` es un snapshot, no un cálculo en vivo

Podría haberse evitado esta tabla y calcular el sueldo "al vuelo" cada vez que alguien lo consulta (sumando tareas del mes contra la fórmula). **No lo hice así a propósito:** si el Admin cambia el `sueldo_base` de un colaborador o el `TOPE_HORAS_EXTRA_BONO` el próximo mes, un cálculo en vivo alteraría retroactivamente los pagos de meses ya cerrados — algo que ninguna empresa real aceptaría.

`nomina_mensual` guarda una "foto" del cálculo de cada mes (`sueldo_base_aplicado`, no solo una referencia al colaborador), así los pagos pasados quedan fijos para siempre, sin importar qué cambie después en la configuración.

Incluye también un campo `penalizacion` (nullable) para el caso de colaboradores de medio mes que mencionaste — está ahí como espacio reservado, pero el mecanismo exacto (cuánto se penaliza, cómo se calcula) sigue siendo la duda C-nueva más importante de toda la Épica 9.

---

## 6. Los votos del foro están en 2 tablas, no 1

Podría haberse hecho una sola tabla `voto` con columnas `publicacion_id` y `respuesta_id`, ambas nullable (una se usa, la otra queda vacía según el caso). Preferí separarlas en `voto_publicacion` y `voto_respuesta`: con columnas nulas "opcionales" es más fácil que un bug inserte una fila sin ninguna de las dos, o con ambas — separar las tablas hace que esa inconsistencia sea imposible por diseño, no solo por disciplina del programador.

---

## 7. Cosas que ya están en el schema pero dependen de decisiones aún pendientes

| Campo/Tabla | Qué se asumió | Dónde está la duda |
|---|---|---|
| `colaborador.disponibilidad_horas` | Horas por semana (confirmaste "ES POR HORAS") | Falta confirmar si es semanal o de otro periodo |
| `asignacion.motivo_finalizacion` | 3 valores: bajo desempeño, proyecto cancelado, otro | El mecanismo de "penalización" en sí no está definido |
| `certificado.habilidad_id` | Nullable, por si un certificado no aplica a una sola habilidad | — |
| `notificacion` | Una tabla genérica para los 4 roles con `tipo` como texto libre | Falta la lista completa de qué eventos generan notificación |
| `proyecto.estado` | Los 6 estados que definiste: En revisión, Rechazado, Activo, En espera, Cancelado, Finalizado | — ya está resuelto, solo lo marco porque es reciente |

---

## 8. Antes de usarlo en el proyecto real (Historia 1.3)

- Con `spring.jpa.hibernate.ddl-auto=update`, Spring Boot va a generar las tablas automáticamente a partir de tus clases `@Entity` — **no vas a ejecutar este `schema.sql` directamente en el proyecto**. Este script sirve como:
  1. Referencia para que todo el equipo diseñe sus entidades JPA de forma consistente (mismos nombres de columna, mismos tipos).
  2. Base para el script de datos de prueba (`seed.sql`) que pide la Historia 1.3 — pueden partir de los `INSERT` que ya probé en las pruebas (ver abajo) y expandirlos.
- Los nombres de tabla y columna están en snake_case (`fecha_creacion`, `colaborador_id`) — con la configuración por defecto de Hibernate, tus entidades en camelCase (`fechaCreacion`, `colaboradorId`) se traducen automáticamente a este formato, así que no hace falta ningún `@Column(name=...)` explícito la mayoría de las veces.

---

## 9. Pruebas realizadas (evidencia de que el script funciona)

Corrí `schema.sql` contra un servidor MariaDB real (no una simulación) y validé:

1. ✅ Las 25 tablas se crean sin errores — 38 llaves foráneas en total.
2. ✅ Un `UPDATE` que intenta poner `proyecto.estado = 'INVENTADO'` es **rechazado** por el `CHECK` (`chk_proyecto_estado`) — los estados inválidos no pueden colarse.
3. ✅ Flujo completo: RM aprueba un proyecto (`EN_REVISION` → `ACTIVO`) y le asigna presupuesto en la misma operación (A20).
4. ✅ Se crea una asignación activa, se le asigna una tarea con horas, y esas horas quedan correctamente vinculadas al `sueldo_base` del colaborador para el cálculo de nómina.
5. ✅ Intentar insertar una **segunda asignación activa** del mismo colaborador al mismo proyecto es **rechazado** (`uq_asignacion_activa`) — la regla de "no más de una asignación activa" está garantizada por la base de datos, no solo por el código Java.
