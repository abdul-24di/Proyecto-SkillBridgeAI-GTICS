# Consideraciones y decisiones pendientes — Base de Datos v4

Acompaña a `skillbridge_db_v4.sql` (28 tablas, probado contra MariaDB real). Este documento tiene 2 partes: **cosas que deben tener en cuenta al programar** (no son errores, son consecuencias de las decisiones ya tomadas) y **decisiones que aún faltan cerrar**.

---

## PARTE 1 — Consideraciones a tener en cuenta

### 1. La BD ya no impide por sí sola que un PM/RM/Admin termine en una tabla de "colaborador"

Antes, cuando `colaborador` era una tabla separada, era estructuralmente imposible que un PM apareciera como `colaborador_id` en `asignacion`, `certificado`, `nomina_mensual`, etc. — esa tabla solo tenía filas para gente con rol Colaborador.

Ahora que todo vive en `usuario`, la base de datos **ya no puede garantizar eso por sí sola**. Nada impide, a nivel de SQL, insertar el `id` de Carlos (PM) en `asignacion.colaborador_id`.

**Esto significa que la validación "este usuario debe tener rol COLABORADOR" ahora es responsabilidad exclusiva del código Java** (en el `service`, antes de guardar). Es el precio de la simplificación que pidió el profesor — no es un error, pero todos en el equipo deben saberlo para no asumir que "si compila, está bien".

*Si quieren una red de seguridad extra a nivel de base de datos (no obligatorio), se puede agregar un `TRIGGER` que valide el rol antes de insertar — se los puedo armar si lo prefieren, pero normalmente para un proyecto de curso basta con la validación en el service de Spring.*

### 2. `usuario` va a tener muchas columnas en `NULL` para PM, RM y Admin

Es esperado y correcto: `cargo`, `horas_contratadas_semana`, `horas_disponibles`, `anios_experiencia`, `sueldo_base`, `nivel_experiencia` y `fecha_contratacion` solo tienen sentido para Colaboradores. Para los otros 3 roles, estas columnas quedan vacías — no es un dato faltante, es esperado por diseño.

### 3. `rol` tiene exactamente 4 filas fijas — no se gestiona como un catálogo dinámico

A diferencia de `habilidad` o `curso` (que el Admin puede crear libremente), `rol` no tiene una historia de "crear nuevo rol" en ningún lado del sistema. Las 4 filas ya vienen insertadas en el script. Si en algún momento agregaran un quinto rol, no bastaría con insertarlo en la tabla — habría que revisar todo el código que asume solo 4 roles (permisos, validaciones, etc.).

### 4. El "seed" de datos de prueba que pide la Historia 1.3 debe actualizarse

Si ya tenían un `seed.sql` con usuarios de prueba basado en la estructura anterior (con tabla `colaborador` separada y `rol` como texto), hay que rehacerlo contra esta nueva estructura. Puedo armarlo si lo necesitan.

---

## PARTE 2 — Decisiones que faltan cerrar

### ⚠️ A. Relación publicación↔etiqueta — confirmar con el profesor

Interpreté "de uno a muchos" + "simplificar" como: **una publicación tiene una sola etiqueta** (`publicacion_foro.etiqueta_id`, FK directa a `etiqueta`). Como la persona que me pasó la observación no recordaba el detalle exacto, esto es una interpretación mía, no una certeza. Antes de darlo por definitivo, confirmen con el profesor si:
- Es correcto tal como quedó (1 publicación → 1 etiqueta), o
- En realidad se refería a otra cosa (por ejemplo, que cada *etiqueta* solo pueda usarse en un *foro* específico, no en cualquiera).

### B. Trigger de validación de rol (mencionado en la Parte 1, punto 1)

¿Lo agregamos como capa extra de seguridad en la BD, o queda solo como validación en el código Java? Ninguna opción es incorrecta — es una decisión de cuánto quieren "blindar" la base de datos versus mantenerla simple.

### C. Pendientes que ya venían de antes y siguen abiertos (no relacionados a esta ronda del profesor, pero siguen sin resolver)

- Etiquetas de trazabilidad RF08/RF09 — opcional, sigue sin decidirse.
- Mecanismo exacto de cálculo de la `penalizacion` (tabla ya existe con `monto`, pero la fórmula de cuánto penalizar según el motivo no está definida).

---

## Resumen de la sesión de correcciones

| Acción | Detalle |
|---|---|
| Tablas eliminadas | `colaborador`, `disponibilidad`, `token_recuperacion_password`, `publicacion_etiqueta` |
| Tablas nuevas | `rol`, `token_usuario` |
| Total de tablas | 31 → **28** |
| Validado | Sí, contra MariaDB real: creación limpia, rol como FK (rechaza valores inválidos), certificado con 2 relaciones a usuario, foro con visibilidad booleana, publicación con etiqueta directa, ciclo reset→recreación completo |
