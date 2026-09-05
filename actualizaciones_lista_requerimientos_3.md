# Actualizaciones a la Lista de Requerimientos — SkillBridge AI

Documento consolidado con todo lo conversado hasta ahora. Organizado en 3 bloques:
**A) Listo para aplicar** (ya acordado, con texto exacto) · **B) Pendiente de decidir** (el equipo debe resolverlo) · **C) Dudas nuevas / a replantear** (encontradas al revisar todo junto, sin decisión aún).

---
---

# BLOQUE A — LISTO PARA APLICAR

## A1. Épica de Gestión de Colaboradores — Historia de Gestión del perfil

**Reemplazar la Descripción:**
> Como colaborador, quiero **actualizar** mi información personal y profesional dentro de mi perfil, para mantenerla vigente dentro de la plataforma.

*Motivo: el nombre y la contraseña ya no se completan aquí — se establecen durante la Historia de Activación de cuenta (A6). Esta historia es solo para actualizar el perfil una vez la cuenta ya está activa.*

---

## A2. NUEVA Historia — Consulta de proyectos y asignaciones del colaborador

**Descripción:**
> Como colaborador, quiero consultar los proyectos en los que participo y el estado de mis asignaciones, para conocer en qué proyectos estoy trabajando y hacer seguimiento a las solicitudes que me involucran.

**Criterios de Aceptación:**
- El colaborador puede ver la lista de proyectos en los que tiene una asignación activa.
- El colaborador puede ver el estado de sus asignaciones (en aprobación, activa, finalizada, rechazada).
- El colaborador puede consultar su historial completo de participación en proyectos anteriores.
- La vista es de solo lectura: el colaborador no puede aprobar, rechazar ni modificar ninguna asignación.

**DoR:** Los estados posibles de una asignación están definidos (ver A4) · La información a mostrar por asignación está identificada (proyecto, estado, fechas) · La historia ha sido estimada.

**DoD:** El colaborador ve sus asignaciones activas y su estado actual · Puede consultar su historial de proyectos finalizados · No existe ningún control de acción visible · Se probó con colaboradores en distintos estados de asignación.

**Estimación:** 3 puntos — vista de consulta simple, reutiliza el modelo de A4.

**Subtareas:**
- Crear ruta de consulta "mis proyectos y asignaciones".
- Implementar vista de asignaciones activas con su estado.
- Implementar vista de historial de participación.
- Validar que la vista sea de solo lectura.

---

## A3. NUEVA Historia — Colaborador solicita incorporarse a un proyecto

**Descripción:**
> Como colaborador, quiero explorar los proyectos disponibles en la organización y solicitar incorporarme a uno que me interese, para participar en proyectos alineados con mis habilidades sin depender únicamente de que un PM o RM me contacte.

**Criterios de Aceptación:**
- El colaborador puede ver un listado de proyectos activos que aún requieren colaboradores (actuales menor al número requerido).
- Cada proyecto listado muestra sus tecnologías y habilidades requeridas.
- El colaborador puede enviar una solicitud para incorporarse a un proyecto específico.
- La solicitud queda registrada como una asignación pendiente, que requiere la aprobación tanto del PM del proyecto como del RM (ver A4).
- El colaborador puede ver el estado de las solicitudes que ha enviado.
- El sistema impide una nueva solicitud al mismo proyecto si ya existe una pendiente o una asignación activa en él.

**DoR:** La Historia de Gestión de información del proyecto está completada · La Historia de Gestión de asignaciones (A4) está completada · Se definió cómo se calcula si un proyecto "aún requiere colaboradores" · La historia ha sido estimada.

**DoD:** El colaborador ve el listado de proyectos con cupos disponibles · Puede enviar una solicitud · La solicitud genera una asignación pendiente de doble aprobación (PM y RM) · Se impiden solicitudes duplicadas · El colaborador ve el estado de sus solicitudes enviadas.

**Estimación:** 5 puntos.

**Subtareas:**
- Calcular cupos disponibles por proyecto (actuales vs. requeridos).
- Crear vista de proyectos disponibles.
- Implementar envío de solicitud de incorporación.
- Validar solicitudes duplicadas.
- Implementar vista de "mis solicitudes enviadas".

---

## A4. REEMPLAZAR COMPLETA — Historia de Gestión de asignaciones de colaboradores

**Descripción:**
> Como Project Manager, Resource Manager o colaborador, quiero que toda asignación de un colaborador a un proyecto sea aprobada tanto por el Project Manager del proyecto como por el Resource Manager, para garantizar que el colaborador es apto para el proyecto y que no se supera su disponibilidad, sin depender de la decisión unilateral de una sola persona.

**Criterios de Aceptación:**
- Una asignación puede originarse de tres formas: el PM la propone, el RM la propone, o el colaborador solicita incorporarse.
- Si la propuesta la inicia el PM, requiere la aprobación del RM.
- Si la propuesta la inicia el RM, requiere la aprobación del PM del proyecto.
- Si la solicitud la inicia el colaborador, requiere la aprobación de **ambos** (PM y RM).
- El colaborador no puede aceptar ni rechazar una asignación; solo consultarla (ver A2).
- Antes de aprobar, el sistema muestra al RM la disponibilidad y carga de trabajo actual del colaborador.
- El RM puede aprobar o rechazar una asignación que supere el límite definido por la organización, dejando registrado el motivo.
- Una asignación queda "Activa" solo cuando se cumplen todas las aprobaciones requeridas según su origen.
- Si cualquiera de los roles requeridos la rechaza, queda "Rechazada" y no se activa.
- Una asignación activa puede finalizarse sin eliminar el historial del colaborador.
- El sistema impide más de una asignación activa del mismo colaborador al mismo proyecto.

**DoR:** Los tres orígenes están definidos · Las reglas de aprobación por origen están acordadas · El límite de asignaciones ya existe y es consultable · La bandeja de solicitudes pendientes por rol está definida · La historia ha sido estimada.

**DoD:** Los tres orígenes están implementados · El flujo de aprobación funciona en cada caso · El RM ve disponibilidad/carga antes de aprobar · Solo pasa a "Activa" con todas las aprobaciones · El rechazo de cualquier rol requerido la deja "Rechazada" · Se probaron los tres flujos · El historial es consultable según permisos.

**Estimación:** 13 puntos (sube de 8) — tres orígenes + doble aprobación condicional.

**Subtareas:**
- Implementar modelo de Asignación con estado y origen.
- Implementar flujo de propuesta desde el PM.
- Implementar flujo de propuesta desde el RM.
- Implementar flujo de solicitud desde el colaborador.
- Implementar bandeja de aprobaciones pendientes.
- Implementar validación de límite al momento de aprobar.
- Implementar consulta de historial de asignaciones.
- Validar el funcionamiento integral de los tres flujos.

---

## A5. ACTUALIZAR — Épica 5, Historia de Gestión de usuarios (campos + creación masiva)

**Reemplazar criterio existente:**
> ~~Se puede crear un nuevo usuario asignándole nombre, correo y rol.~~

Por:
> Se puede crear un nuevo usuario asignándole únicamente **correo y rol**; el nombre y la contraseña los define el propio usuario al activar su cuenta (ver A6).

**Agregar a Criterios:**
- Creación masiva por CSV/Excel con columnas **correo y rol** (sin nombre).
- Vista previa de los usuarios detectados antes de confirmar la carga.
- Validación del archivo (correos duplicados **dentro del archivo y contra usuarios ya existentes**, roles inválidos, filas incompletas), mostrando errores antes de crear.
- Al crear un usuario (individual o masivo), el sistema envía automáticamente el correo de activación.
- El Admin puede reenviar el enlace de activación si expiró.

**Agregar a DoR:** Formato del archivo (correo, rol) definido y documentado · La Historia de Activación de cuenta (A6) está completada.

**Agregar a DoD:** La carga masiva crea usuarios válidos solo con correo y rol · Errores de validación visibles antes de confirmar · Cada usuario creado recibe su correo de activación.

**Agregar Subtareas:**
- Implementar carga y parseo del archivo CSV/Excel.
- Implementar vista previa antes de confirmar.
- Implementar validación de filas (duplicados, roles, campos faltantes).
- Enlazar creación de usuario con el envío del correo de activación.
- Implementar botón de reenvío del enlace desde el panel de usuarios.

**Estimación:** sube de 8 a 13 puntos.

---

## A6. NUEVA Historia — Épica 2 (Autenticación), Activación de cuenta

**Descripción:**
> Como usuario recién creado por el Administrador, quiero activar mi cuenta a través de un enlace enviado a mi correo, para establecer mi nombre y contraseña y poder acceder a la plataforma.

**Criterios de Aceptación:**
- Al crear un usuario, el sistema envía automáticamente un correo con un enlace único de activación.
- El enlace tiene vigencia limitada.
- Al ingresar al enlace, el usuario establece su nombre completo y su contraseña.
- La contraseña cumple con las políticas de seguridad establecidas.
- Completada la activación, el usuario puede iniciar sesión.
- El enlace no puede reutilizarse una vez completado o expirado.
- Si expira, el Administrador puede reenviarlo desde el panel de usuarios (ver A5).

**DoR:** Mecanismo de generación/envío del enlace definido · Vigencia del enlace definida · Políticas de contraseña ya definidas · La historia ha sido estimada.

**DoD:** El correo de activación se envía al crear un usuario · El formulario de activación captura nombre y contraseña · El enlace expira y no se reutiliza · El usuario activado inicia sesión correctamente · El Admin puede reenviarlo · Se probaron los 3 escenarios (éxito, expirado, ya usado).

**Estimación:** 8 puntos.

**Subtareas:**
- Definir flujo de activación de cuenta.
- Implementar generación y envío del correo de activación.
- Implementar formulario de activación (nombre + contraseña).
- Validar vigencia y uso único del enlace.
- Implementar reenvío del enlace desde el panel de Admin.
- Validar el flujo completo.

---

## A7. ACTUALIZAR — Historia de Gestión de publicaciones del foro

**Agregar a Criterios:** Votos positivos/negativos por publicación (un voto por usuario, modificable/retirable) · Ordenar por más votadas o por fecha · El RM accede de lectura a **todos** los foros de la organización, sin necesidad de asignación activa · Colaboradores y PM solo acceden a los foros de sus proyectos con asignación activa.

**Agregar a DoR/DoD/Subtareas:** mecanismo de votación definido · sistema de votos funcional · regla de acceso por rol implementada y probada.

**Estimación:** sube de 8 a 13 puntos.

---

## A8. ACTUALIZAR — Historia de Gestión de respuestas y soluciones del foro

**Agregar:** votación en respuestas (igual que publicaciones) · ordenar respuestas por más votadas dentro de una publicación.

**Estimación:** sube de 8 a 10 puntos.

---

## A9. ACTUALIZAR — Historia de Gestión del chat del proyecto

**Aclarar:** "miembros del proyecto" = colaboradores con asignación activa + el PM del proyecto. **El RM NO tiene acceso al chat**, a diferencia del foro (A7).

---

## A10. ACTUALIZAR — Épica 1, Historia de Mockups en Miro

- Mockup de asignación → renombrar a "propuesta de asignación", con botón "Proponer" (no "confirmar").
- Agregar mockup de "Bandeja de aprobaciones pendientes" (PM y RM).
- Agregar subtarea nueva: mockups del colaborador — "Mis proyectos y asignaciones" (solo lectura) y "Proyectos disponibles" (solicitar incorporarme).
- Mockup del foro: agregar controles de voto (upvote/downvote) y ordenar por más votadas.
- **(Nuevo, ver C5 abajo)** — falta agregar mockup de la pantalla de activación de cuenta y el correo de activación.

---

## A11. ACTUALIZAR — Épica 5, Historia de Log de auditoría

**Agregar a acciones auditadas:** propuesta, aprobación o rechazo de asignaciones de colaboradores.
**(Nuevo, ver C3 abajo)** — evaluar agregar también "activación de cuenta completada".

---

## A12. ACTUALIZAR — Épica 8, Historia de AI Talent Matching

- Botón "Asignar al proyecto" → **"Proponer para el proyecto"** (en Criterios, DoD y en la subtarea correspondiente).
- Agregar criterio/DoD: la propuesta queda pendiente de la aprobación del otro rol; la IA nunca asigna directamente.

---

## A13. CORREGIR — Typo pre-existente en la Descripción de la Épica 8

> ~~Como solución de debra integrar Inteligencia Artificial...~~ → **Como solución, se deberá integrar Inteligencia Artificial...**

---

## A14. ACTUALIZAR — Épica 6, Historias 6.1 y 6.2 (Dashboards)

**Ambas historias, agregar:** contador de "asignaciones pendientes de mi aprobación" (6.1 = solo de sus proyectos; 6.2 = de toda la organización) + subtarea de implementación.
**Estimación:** se mantiene o sube 1-2 puntos como máximo en cada una.

---
---

# BLOQUE A (continuación) — NUEVO: Sistema de Horas y Pagos → ÉPICA 9

*No encaja dentro de las 8 épicas actuales — introduce una entidad nueva (Tarea) y un dominio nuevo (cálculo de sueldo). Se propone como Épica 9.*

## A15. NUEVA ÉPICA 9 — Gestión de Horas y Pagos

**Descripción:**
> Actualmente, el sistema no cuenta con un mecanismo para registrar las tareas puntuales que un PM asigna a un colaborador dentro de un proyecto, ni para calcular su remuneración mensual en base a las horas trabajadas. Sin este módulo, no es posible determinar de forma objetiva cuánto se le debe pagar a cada colaborador ni distinguir cuándo un déficit de horas es responsabilidad de la gestión del proyecto y no del colaborador.
>
> Esta épica permite que los PM asignen tareas con horas estimadas y fecha límite dentro de sus proyectos, consolida las horas trabajadas por cada colaborador a nivel mensual, y calcula automáticamente su sueldo (base fijo + bono por horas extra entregadas a tiempo, con tope configurable por el Administrador).

---

### Historia 9.1 — Gestión de tareas del proyecto

**Descripción:**
> Como Project Manager, quiero asignar tareas puntuales a los colaboradores de mi proyecto indicando horas estimadas y fecha límite, para registrar el trabajo que deben realizar y que sus horas se contabilicen correctamente.

**Criterios de Aceptación:**
- Solo el PM del proyecto asigna tareas (el RM no asigna tareas, solo colaboradores a proyectos).
- Una tarea solo puede asignarse a un colaborador con una asignación **activa** en ese proyecto.
- Cada tarea registra: descripción, horas estimadas y fecha límite de entrega.
- El conteo de horas de la tarea inicia en el momento en que se asigna, no cuando se entrega.
- El **colaborador** marca la tarea como **"Listo para revisar"** cuando considera que terminó su trabajo.
- El **PM** revisa una tarea marcada como lista y puede **confirmarla** (queda "Entregada a tiempo" o "Entregada tardía", según la fecha límite) o **devolverla** con un comentario (vuelve a estado "En revisión" para que el colaborador la retome).
- La fecha en que el PM confirma la entrega es la que determina si fue a tiempo o tardía.
- Las tareas quedan asociadas al proyecto y al colaborador correspondiente.

**DoR:** La Historia de Gestión de asignaciones (A4) está completada — una tarea depende de que el colaborador tenga asignación activa · Los campos de una tarea están definidos · La historia ha sido estimada.

**DoD:** El PM crea una tarea con horas estimadas y fecha límite · Solo se asigna a colaboradores con asignación activa · El colaborador puede marcar una tarea como "Listo para revisar" · El PM puede confirmar (a tiempo/tardía) o devolver la tarea con comentario · Se probaron los flujos de creación, marcado, confirmación y devolución.

**Estimación:** 10 puntos (sube de 8) — entidad nueva con validación de asignación activa, control de fechas, y el flujo de 2 pasos colaborador→PM (ver C11).

**Subtareas:**
- Implementar entidad Tarea (proyecto, colaborador, horasEstimadas, fechaLimite, fechaEntrega, estado, creadoPor).
- Implementar creación de tarea, validando asignación activa del colaborador en el proyecto.
- Implementar marcado de "Listo para revisar" por parte del colaborador.
- Implementar confirmación de entrega (a tiempo/tardía) o devolución con comentario por parte del PM.
- Implementar consulta de tareas por proyecto.

---

### Historia 9.2 — Registro y consolidado de horas mensuales

**Descripción:**
> Como colaborador, PM, RM o Administrador, quiero consultar el total de horas trabajadas por un colaborador, desglosado por proyecto y consolidado a nivel mensual, para conocer su carga real de trabajo.

**Criterios de Aceptación:**
- Las horas de una tarea se acreditan al total mensual del colaborador **independientemente de si la entrega fue a tiempo o tardía**.
- El total mensual es la suma de las horas estimadas de todas las tareas asignadas ese mes, sin importar el proyecto.
- Se puede consultar el desglose de horas por proyecto y el consolidado mensual total.
- El colaborador puede ver su propio consolidado de horas.

**DoR:** La Historia 9.1 está completada · La historia ha sido estimada.

**DoD:** El total mensual se calcula sumando correctamente todas las tareas del mes · Se muestra el desglose por proyecto · El colaborador consulta su propio consolidado.

**Estimación:** 5 puntos.

**Subtareas:**
- Implementar cálculo del consolidado mensual de horas por colaborador.
- Implementar desglose de horas por proyecto dentro del mes.
- Implementar vista de consulta (colaborador ve lo suyo; PM/RM/Admin según corresponda).

---

### Historia 9.3 — Cálculo de sueldo mensual y bono por horas extra

**Descripción:**
> Como Administrador, quiero que el sistema calcule automáticamente el sueldo mensual de cada colaborador (sueldo base + bono por horas extra entregadas a tiempo), para remunerar el trabajo realizado sin penalizar los déficits de horas.

**Criterios de Aceptación:**
- Cada colaborador tiene una meta mensual fija de 160 horas y un sueldo base fijo, pagado siempre completo, **sin descuentos por déficit**.
- Si supera las 160 horas mensuales con tareas entregadas a tiempo, el excedente se paga como bono a la misma tarifa de su hora normal.
- Solo las horas de tareas entregadas **a tiempo** cuentan para el bono; las tardías suman al total de 160 horas pero no generan bono.
- El bono está topeado a un máximo de horas extra pagables por mes, **configurable por el Administrador** (20 por defecto — ver A16).
- Fórmula: `valor_hora = sueldo_base / 160` · `horas_extra = max(0, horas_a_tiempo_del_mes - 160)` · `horas_extra_pagables = min(horas_extra, tope_bono)` · `bono = horas_extra_pagables × valor_hora` · `pago_total = sueldo_base + bono`.

**DoR:** La Historia 9.2 está completada · El campo `sueldo_base` existe en el perfil del colaborador (ver C7) · El parámetro de tope de horas extra está configurado (A16) · La historia ha sido estimada.

**DoD:** El sistema calcula correctamente el pago total para los 3 casos de ejemplo documentados por el equipo (exceso dentro del tope, exceso por encima del tope, déficit) · El bono respeta el tope configurado · Las tareas tardías no generan bono pero sí cuentan para las 160 horas.

**Estimación:** 8 puntos — lógica de cálculo con varias condiciones y dependencia de configuración.

**Subtareas:**
- Implementar cálculo de `valor_hora` y `horas_extra` según la fórmula.
- Implementar el tope de horas extra pagables, leyendo el parámetro configurable.
- Implementar el cálculo del pago total mensual por colaborador.
- Validar contra los 3 casos de ejemplo documentados (S/ 2,400 base → casos de 180h, 195h y 130h).

---

## A16. ACTUALIZAR — Épica 5, Historia de Configuración de parámetros globales

**Agregar un parámetro nuevo:**
- `TOPE_HORAS_EXTRA_BONO` — máximo de horas extra pagables por mes (default: 20), editable por el Administrador.

**Agregar a Subtareas:** incluir `TOPE_HORAS_EXTRA_BONO` en el seed inicial de configuración, junto a `MAX_ASIGNACIONES_POR_COLABORADOR`.

---
---

## A17. CONFIRMACIÓN — Bonos con límite definido por el Admin

Ya cubierto por A16 (`TOPE_HORAS_EXTRA_BONO`, editable por el Administrador). Sin acción adicional — solo se confirma que la Épica 9 ya contempla esto correctamente.

---

## A18. ACTUALIZAR — Épica 4, Historia de Gestión de asignaciones (desasignar colaborador)

**Agregar a Criterios de Aceptación:**
- El PM **o** el RM pueden finalizar (desasignar) la asignación activa de un colaborador de un proyecto directamente, sin necesidad de aprobación del otro rol.

**Agregar a DoD:** Se verificó que tanto el PM como el RM pueden finalizar una asignación directamente, cada uno de forma independiente.

**Agregar Subtarea:** Implementar finalización/desasignación de una asignación activa, disponible tanto para el PM como para el RM.

*(Actualizado según C16 — se había redactado inicialmente como exclusivo del PM; el equipo confirmó que también aplica al RM.)*

---

## A19. ACTUALIZAR — Épica 5, Historia de Gestión de usuarios (Admin asigna sueldo)

**Agregar a Criterios de Aceptación:**
- El Administrador puede asignar y actualizar el **sueldo base** de cada colaborador.

**Agregar a DoD:** El campo sueldo base es editable únicamente por el Administrador.

---

## A20. NUEVA Historia — Épica 4, Asignación de presupuesto al proyecto (RM)

**Descripción:**
> Como Resource Manager, quiero asignar y actualizar el presupuesto de cada proyecto, para controlar el gasto financiero de la organización entre los distintos proyectos.

**Criterios de Aceptación:**
- Solo el RM asigna o edita el presupuesto de un proyecto — el PM no tiene permiso de edición sobre este campo.
- El proyecto almacena su presupuesto asignado.
- El RM puede consultar el presupuesto de todos los proyectos de la organización.

**DoR:** La Historia de Gestión de información del proyecto está completada · La historia ha sido estimada.

**DoD:** El RM puede asignar/editar el presupuesto de un proyecto · Se verificó que el PM no puede editarlo · El RM consulta el presupuesto de todos los proyectos.

**Estimación:** 5 puntos.

**Subtareas:**
- Agregar campo `presupuesto` a la entidad Proyecto.
- Implementar edición exclusiva del RM sobre este campo.
- Validar que el PM no tenga permiso de edición.

---

## A21. NUEVA Historia — Épica 6, Reporte de horas cumplidas por proyecto (PM)

**Descripción:**
> Como Project Manager, quiero generar un reporte de las horas cumplidas por cada colaborador de mi proyecto, para evaluar su desempeño y planificar mejor el trabajo futuro.

**Criterios de Aceptación:**
- El reporte muestra, por colaborador del proyecto, las horas cumplidas (de tareas entregadas) en un rango de fechas seleccionable.
- Se puede filtrar por colaborador o por mes.

**DoR:** La Épica 9 (Historias 9.1 y 9.2) está completada · La historia ha sido estimada.

**DoD:** El reporte muestra correctamente las horas por colaborador y periodo · Se probaron los filtros.

**Estimación:** 5 puntos.

**Subtareas:**
- Implementar consulta de horas por proyecto y colaborador.
- Implementar filtro por rango de fechas.
- Implementar la vista del reporte.

---

## A22. NUEVA Historia — Épica 6, Reporte de presupuesto y horas por proyecto (RM)

**Descripción:**
> Como Resource Manager, quiero generar un reporte del presupuesto asignado a cada proyecto y las horas cumplidas por sus colaboradores, para evaluar el uso de recursos de la organización entre proyectos.

**Criterios de Aceptación:**
- El reporte muestra, por proyecto, el presupuesto asignado (A20) y el total de horas cumplidas por sus colaboradores en un periodo.
- El RM puede comparar entre distintos proyectos.

**DoR:** La Historia A20 (presupuesto) y la Épica 9 están completadas · La historia ha sido estimada.

**DoD:** El reporte muestra presupuesto y horas correctamente por proyecto.

**Estimación:** 5 puntos.

**Subtareas:**
- Implementar consulta de presupuesto por proyecto.
- Implementar consulta de horas totales por proyecto.
- Implementar la vista combinada del reporte.

---

## A23. NUEVA Historia — Épica 3, Revisión de certificados y nivel de experiencia (RM)

*Esto reemplaza la nota anterior de "por ahora los certificados se suben sin validación" — ahora sí tienen dueño y flujo definidos.*

**Descripción:**
> Como Resource Manager, quiero revisar los certificados que suben los colaboradores y actualizar su nivel de habilidad y su nivel de experiencia general (Junior/Senior), para mantener el perfil profesional del colaborador validado y confiable.

**Criterios de Aceptación:**
- El colaborador sube un certificado asociado a una habilidad de su perfil (ya definido en la Historia de habilidades y experiencia).
- El certificado queda en estado "Pendiente de revisión" hasta que el RM lo evalúe.
- El RM tiene una bandeja de certificados pendientes de revisión.
- El RM puede **aprobar** el certificado (validando la habilidad, opcionalmente subiendo su nivel de dominio) o **rechazarlo** indicando un motivo.
- El RM puede asignar o actualizar el **nivel de experiencia general** del colaborador (Junior/Senior).
- El colaborador puede ver el estado de sus certificados (pendiente, aprobado, rechazado).

**DoR:** La Historia de Gestión de habilidades y experiencia está completada · Los niveles de experiencia (Junior/Senior) están definidos · La historia ha sido estimada.

**DoD:** El colaborador sube certificados asociados a una habilidad · El RM ve la bandeja de certificados pendientes · El RM aprueba/rechaza certificados · El nivel de experiencia es editable por el RM · Se probaron los flujos de aprobación y rechazo.

**Estimación:** 8 puntos — bandeja de revisión + flujo de aprobación/rechazo + nuevo campo de clasificación.

**Subtareas:**
- Agregar estado (pendiente/aprobado/rechazado) a los certificados subidos por el colaborador.
- Implementar bandeja de certificados pendientes para el RM.
- Implementar aprobación/rechazo de certificados con motivo.
- Agregar campo "nivel de experiencia" (Junior/Senior) al perfil del colaborador, editable por el RM.
- Implementar vista de estado de certificados para el colaborador.

---
---

## A24. NUEVA Historia — Sistema de Notificaciones por Rol (Épica 5)

**Descripción:**
> Como usuario de la plataforma (cualquier rol), quiero recibir notificaciones relevantes para mis responsabilidades y poder filtrarlas o marcarlas como leídas, para estar al tanto de las acciones que requieren mi atención sin revisar manualmente cada sección.

**Criterios de Aceptación:**

Eventos que generan notificación por rol:

*Para el Project Manager:*
- Una asignación propuesta por el RM sobre uno de sus proyectos está pendiente de su aprobación.
- Un colaborador ha solicitado incorporarse a uno de sus proyectos (pendiente de su aprobación).
- El RM aprueba o rechaza uno de sus proyectos enviados a revisión.
- Una tarea que el PM asignó ha sido marcada como "Listo para revisar" por el colaborador.

*Para el Resource Manager:*
- Una asignación propuesta por un PM está pendiente de su aprobación.
- Un colaborador ha solicitado incorporarse a un proyecto (pendiente de su aprobación).
- Un colaborador ha subido un nuevo certificado pendiente de revisión.

*Para el Colaborador:*
- Una solicitud de incorporación a un proyecto fue aprobada o rechazada.
- Una tarea asignada fue confirmada (a tiempo) o devuelta con comentario por el PM.
- Un certificado fue aprobado o rechazado por el RM.
- Ha sido inscrito en un curso por el RM.

*Para el Administrador:*
- No se definen notificaciones automáticas por ahora; el Admin monitorea el log de auditoría.

*Comportamiento general:*
- El ícono de campanita en el navbar muestra el número de notificaciones no leídas.
- El usuario puede marcar una notificación como leída individualmente o marcar todas como leídas.
- El usuario puede filtrar las notificaciones por tipo según su rol.
- Hacer clic en una notificación redirige directamente a la sección relevante del sistema.

**DoR:** Lista completa de eventos por rol definida · Tabla `notificacion` existe en el schema · Las historias generadoras de notificaciones están completadas o en desarrollo avanzado · Historia estimada.

**DoD:** Los eventos definidos generan notificaciones para los roles correctos · El contador de no leídas funciona · Los filtros por tipo funcionan por rol · El clic redirige a la sección correcta · Se probaron los eventos de cada rol.

**Estimación:** 8 puntos

**Subtareas:**
- Implementar el servicio de creación de notificaciones (método reutilizable: `notificacionService.crear(usuarioId, tipo, mensaje, enlace)`).
- Integrar el servicio en los flujos de asignaciones (propuesta, aprobación, rechazo).
- Integrar el servicio en el flujo de certificados (subida, aprobación, rechazo).
- Integrar el servicio en el flujo de tareas (marcado como listo, confirmación, devolución).
- Integrar el servicio en el flujo de proyectos (aprobación/rechazo por RM, solicitud de incorporación de colaborador).
- Implementar el contador de no leídas en el navbar.
- Implementar la vista del centro de notificaciones con filtros por tipo y opción de marcar como leída.
- Implementar el clic en notificación → redirección a la sección relevante.

---

## A25. NUEVA Historia — Gestión del catálogo de cursos y capacitaciones (Admin, Épica 5)

**Descripción:**
> Como Administrador, quiero registrar, editar y desactivar cursos y capacitaciones disponibles en la organización, para que el Resource Manager pueda asignarlos a colaboradores y los colaboradores puedan solicitarlos.

**Criterios de Aceptación:**
- El Administrador puede crear un curso indicando: nombre, descripción, duración en horas y categoría (técnico, habilidades blandas, certificación, etc.).
- El Administrador puede editar la información de un curso existente.
- El Administrador puede desactivar un curso; el curso deja de aparecer como disponible para nuevas inscripciones pero no se elimina.
- Los cursos activos son visibles para el RM y los colaboradores.

**DoR:** Los campos de un curso están definidos · Tablas `curso` y `colaborador_curso` existen en el schema · Historia estimada.

**DoD:** El Admin puede crear, editar y desactivar cursos · Los cursos desactivados no aparecen para inscripción · Se probaron creación, edición y desactivación.

**Estimación:** 5 puntos

**Subtareas:**
- Implementar la entidad Curso (nombre, descripción, duración, categoría, activo).
- Implementar el CRUD de cursos en el panel de Administrador.
- Implementar la vista del catálogo de cursos activos (accesible para RM y Colaboradores).

---

## A26. NUEVA Historia — Colaborador solicita inscripción a un curso (Épica 5)

**Descripción:**
> Como colaborador, quiero explorar el catálogo de cursos disponibles y solicitar inscribirme en uno que me interese, para desarrollar mis habilidades profesionales dentro de la organización.

**Criterios de Aceptación:**
- El colaborador puede ver el catálogo de cursos activos con nombre, descripción, duración y categoría.
- El colaborador puede enviar una solicitud de inscripción a cualquier curso activo.
- La solicitud queda en estado "Pendiente" hasta que el RM la gestione.
- El colaborador puede ver el estado de sus solicitudes: Pendiente, Aprobada o Rechazada.
- El sistema impide enviar una solicitud duplicada al mismo curso si ya hay una pendiente o una inscripción activa.

**DoR:** La Historia A25 está completada · Historia estimada.

**DoD:** El colaborador puede solicitar inscripción a un curso · No hay solicitudes duplicadas · El colaborador ve el estado actualizado · Se probó el flujo completo.

**Estimación:** 3 puntos

**Subtareas:**
- Implementar la vista del catálogo de cursos activos para el colaborador.
- Implementar el envío de solicitud de inscripción.
- Implementar la validación de solicitudes duplicadas.
- Implementar la vista de "Mis solicitudes de curso" con estado actualizado.

---

## A27. NUEVA Historia — RM gestiona inscripciones a cursos (Épica 5)

**Descripción:**
> Como Resource Manager, quiero asignar cursos directamente a colaboradores que no están cumpliendo sus horas de trabajo, y gestionar las solicitudes de inscripción enviadas por los colaboradores, para apoyar su desarrollo y mejorar su rendimiento.

**Criterios de Aceptación:**
- El RM puede ver todas las solicitudes de inscripción a cursos pendientes de revisión de la organización.
- El RM puede aprobar o rechazar una solicitud, indicando motivo si rechaza.
- El RM puede asignar directamente un curso a un colaborador sin que el colaborador lo haya solicitado (por ejemplo, cuando no está cumpliendo sus horas de trabajo).
- El colaborador recibe una notificación cuando el RM le asigna o inscribe en un curso (ver A24).

**DoR:** Las Historias A25 y A26 están completadas · La Historia A24 (Notificaciones) está en desarrollo o completada · Historia estimada.

**DoD:** El RM ve y gestiona solicitudes pendientes · El RM puede inscribir directamente a un colaborador · El colaborador recibe notificación · Se probaron los tres flujos: aprobación, rechazo e inscripción directa.

**Estimación:** 5 puntos

**Subtareas:**
- Implementar la bandeja de solicitudes de inscripción pendientes para el RM.
- Implementar la aprobación de solicitud con notificación al colaborador.
- Implementar el rechazo de solicitud con motivo y notificación al colaborador.
- Implementar la inscripción directa de un colaborador a un curso por parte del RM.

---
---

# BLOQUE B — DECISIONES TOMADAS



1. **Historia "Gestión de disponibilidad" (Épica 3):** falta definir si se mide en horas/semana, %, o estados con nombre.
✅ RESUELTO: ES POR HORAS (horas/semana).

2. **Etiquetas RF08 (Administración) y RF09 (Reportes y Dashboard):** trazabilidad frente a los RF01-07 de la profesora. Opcional.
⏳ SIGUE PENDIENTE — es el único punto del Bloque B que no se resolvió en esta ronda. No bloquea nada (es solo documentación), pero quedó sin decisión.

3. **Campo "prioridad" (Alta/Media/Baja) en Proyecto:** para que el RM arbitre con datos reales. Opcional.
✅ RESUELTO: SE AGREGA EL CAMPO DE PRIORIDAD PARA LOS PROYECTOS. El PM marca la prioridad (Alta/Media/Baja) y justifica al crear el proyecto. El RM revisa y confirma/rechaza el proyecto. El PM puede cancelar su proyecto mientras está en estado "En revisión" antes de que el RM decida.
Estados del proyecto: En revisión → Activo | Rechazado → En espera | Cancelado | Finalizado.

4. **Historia formal de "Notificaciones del sistema":** hoy solo existe como vista de UI en el mockup del Admin, no como historia con criterios propios.
✅ RESUELTO: Se crea historia formal con criterios propios. Todos los roles tienen notificaciones con filtros según su rol. Ver A24.

5. **Seguimiento a la responsabilidad de PM/RM cuando un colaborador no llega a sus 160 horas:** si debe generar algún reporte o alerta, y para quién.
✅ RESUELTO: El RM puede incluir al colaborador en otro proyecto como apoyo, o asignarle cursos de capacitación. Los cursos los registra el Administrador. El colaborador también puede solicitar inscribirse a cursos. Ver A25, A26 y A27.

6. **Si el tope de horas extra (bono) es único por colaborador, o puede variar según proyecto o rol.**
✅ RESUELTO: El tope de horas extra pagables es POR COLABORADOR (no varía por proyecto ni por rol).

7. **Cómo se maneja un colaborador que trabaja solo parte del mes.**
✅ RESUELTO: Si es removido a mitad de mes, se le contabilizan las horas trabajadas hasta ese momento. Se aplica penalización según el motivo: si fue por mal desempeño, se penaliza; si fue porque el proyecto se canceló, no se penaliza. *(El mecanismo exacto de cálculo de la penalización sigue abierto — ver tabla `penalizacion` en la BD, campo `monto` sin fórmula definida todavía.)*

---
---

# BLOQUE C — DUDAS RESUELTAS

*Todas las preguntas de este bloque han sido respondidas por el equipo. Se registra la decisión tomada para cada una.*

**C1. Dependencia circular entre A5 (Gestión de usuarios) y A6 (Activación de cuenta).**
✅ RESUELTO: Ambas historias se desarrollan en paralelo y de forma coordinada — no existe un bloqueo estricto entre ellas. El equipo que desarrolle A6 debe coordinar con el equipo que desarrolle A5 el contrato del endpoint de activación (qué datos espera recibir) antes de comenzar a programar.
División de responsabilidades: A6 es dueña de toda la lógica de activación y reenvío de enlace (genera el token, lo invalida al usarse, genera uno nuevo al reenviar). A5 solo implementa el botón en la UI del Admin que llama al endpoint de reenvío.

**C2. "Reenviar el enlace de activación" está duplicado en dos historias.**
✅ RESUELTO: La Historia A6 (Activación de cuenta) es dueña de la lógica completa de reenvío (generar nuevo token, invalidar el anterior). La Historia A5 (Gestión de usuarios) solo implementa el botón en la interfaz del Admin que dispara esa lógica. No se programa dos veces.

**C3. ¿La activación de cuenta debería quedar en el log de auditoría (Historia 5.4)?**
✅ RESUELTO: Sí. Se agrega "activación de cuenta completada" como una acción auditada en la Historia 5.4. Esto permite al Admin saber quién todavía no ha activado su invitación.

**C4. ¿El colaborador puede editar su propio correo desde su perfil?**
✅ RESUELTO: No. El correo es de solo lectura para el colaborador. Solo el Administrador puede cambiarlo, ya que es el identificador de login y receptor de notificaciones del sistema.

**C5. Falta el mockup de la pantalla de activación de cuenta.**
✅ RESUELTO: Se agrega como subtarea dentro de la Historia A10 (Mockups, Épica 1). Lo realiza el integrante que tenga disponibilidad al momento de ejecutar los mockups.

**C6. Validación de duplicados en la carga masiva (A5): ¿contra qué se compara?**
✅ CONFIRMADO: La validación es doble — se detectan correos duplicados dentro del archivo cargado Y se cruzan contra los correos de usuarios ya existentes en la base de datos. Ambas validaciones son obligatorias.

**C7. ¿Quién puede ver el sueldo base y el pago mensual de un colaborador?**
✅ RESUELTO: Solo el propio colaborador y el Administrador. Ni el PM ni el RM tienen acceso al sueldo base ni al pago total calculado. Esta regla debe implementarse como restricción de acceso explícita en la Historia 9.3.

**C8. ¿El sistema de Horas y Pagos aplica solo a Colaboradores?**
✅ RESUELTO: Sí, aplica exclusivamente al rol Colaborador. PM, RM y Administrador no participan en el sistema de tareas ni en el cálculo de nómina.

**C9. Tres conceptos de ocupación del colaborador — no confundirlos.**
✅ ACLARADO (no es un cambio, es una advertencia de diseño para el equipo):
- **Disponibilidad** (Épica 3): horas/semana que el colaborador declara tener libre. Dato autodeclarado.
- **Carga** (Épica 4/5/6): número de asignaciones activas sobre el límite configurado. Dato calculado automáticamente.
- **Horas trabajadas** (Épica 9): suma de horas estimadas de tareas asignadas en el mes. Dato para el cálculo de nómina.
Son tres métricas distintas que no se mezclan entre sí en el modelo de datos.

**C10. Auditoría de cambios de configuración del sistema — nunca estuvo incluida.**
✅ RESUELTO: Sí se audita. Se agrega "cambio en parámetros de configuración del sistema" (incluyendo MAX_ASIGNACIONES_POR_COLABORADOR y TOPE_HORAS_EXTRA_BONO) como acción auditada en la Historia 5.4.

**C11. ¿Quién marca una tarea como "entregada"?**
✅ RESUELTO: Flujo en dos pasos:
1. El **colaborador** marca la tarea como "Listo para revisar" cuando considera que terminó.
2. El **PM** revisa y puede **confirmar** la entrega (queda como "Entregada a tiempo" o "Entregada tardía") o **devolver** la tarea con un comentario (vuelve a estado "En revisión").
La fecha de confirmación del PM es la que determina si fue a tiempo o tardía. Esto sube la estimación de H9.1 de 8 a **10 puntos**.

**C12. ¿Qué pasa con las horas de una tarea si la asignación se cancela antes de la entrega?**
✅ RESUELTO: Las horas ya contabilizadas se **mantienen**. Si una tarea fue asignada, el tiempo comprometido en ese mes ya fue registrado y no se anula retroactivamente, independientemente de lo que ocurra con la asignación posterior.

**C13. ¿Las horas se controlan por tareas (estimadas) o por registro libre de tiempo (cronómetro)?**
✅ CONFIRMADO: Las horas se controlan a través de las **tareas con horas estimadas** asignadas por el PM. No existe un cronómetro ni registro libre de tiempo. Las horas estimadas de la tarea son las que se contabilizan.

**C14. ¿El nivel Junior/Senior del colaborador afecta el sueldo u otro cálculo automático?**
✅ RESUELTO: Es **solo informativo**. No afecta el valor_hora, el sueldo_base ni ningún cálculo automático de la Épica 9. Sirve únicamente para filtros de búsqueda del RM y para el AI Talent Matching.

**C15. ¿El presupuesto del proyecto se valida contra los pagos reales de la Épica 9?**
✅ RESUELTO: Por ahora es **solo informativo**. El RM declara un presupuesto que queda registrado en el proyecto, pero el sistema no lo valida automáticamente contra los pagos calculados en la Épica 9. Esta validación puede agregarse en una fase futura.

**C16. ¿Quién puede desasignar directamente a un colaborador de un proyecto?**
✅ RESUELTO: Tanto el **PM como el RM** pueden desasignar directamente a un colaborador sin necesidad de aprobación del otro rol. El historial de participación del colaborador se conserva en ambos casos.

**C17. Si el RM rechaza un certificado, ¿qué pasa con él?**
✅ RESUELTO: El certificado rechazado **no se elimina**. Queda visible en el perfil del colaborador con el estado "Rechazado" y el motivo indicado por el RM. El colaborador puede ver el motivo para corregirlo. Si quiere volver a intentarlo, debe subir un certificado nuevo.

**C18. ¿Los reportes de horas y presupuesto (A21/A22) son exportables?**
✅ RESUELTO: Sí. Los reportes pueden exportarse a **PDF y Excel**. Esto sube la estimación de A21 y A22 de 5 a **8 puntos** cada uno.

---
---

# PRIORIZACIÓN — qué es imprescindible y qué es recortable

*El backlog total pasó de ~150 a ~350 puntos. Con 4 personas y el Parcial (semana 9, ~12 de octubre) exigiendo "todos los CRUDs completos + sesión + security", conviene tener clarísimo qué NO puede faltar y qué se puede simplificar o dejar para el final sin que el proyecto se vea incompleto.*

## 🔴 TIER 1 — Imprescindible (esto define si el proyecto funciona o no)

- **Épica 1** completa (setup, ramas, BD, despliegue, mockups).
- **Épica 2** completa, incluyendo A6 (Activación de cuenta) — sin esto nadie entra al sistema.
- **Épica 3**: perfil, habilidades y experiencia, disponibilidad, consulta de colaboradores. (A23 — revisión de certificados por el RM — **no** es Tier 1, ver abajo.)
- **Épica 4** completa: proyectos, A4 (asignaciones con doble aprobación), A2, A3, A18, A20 (presupuesto). Es el corazón del sistema — RF03/RF04 de la profesora.
- **Épica 5** básica: gestión de usuarios (crear individual, cambiar rol, desactivar), catálogo de habilidades, configuración, log de auditoría.
- **Épica 7** básica: publicaciones, respuestas, marcar solución, chat — sin votos todavía.
- **Épica 8** básica: Talent Matching funcionando con explicación.

## 🟡 TIER 2 — Importante, pero recortable si falta tiempo

- **A5 — creación masiva de usuarios (CSV)**: la creación individual ya cubre la necesidad real; el CSV es una comodidad, no un bloqueador.
- **A7/A8 — votos en el foro**: el foro funciona perfecto sin upvote/downvote. Es una mejora de UX, no una funcionalidad crítica.
- **A14 — contador de aprobaciones pendientes en dashboards**: la bandeja de aprobaciones (dentro de A4) ya resuelve lo esencial; el contador en el dashboard es un plus visual.
- **A21/A22 — exportar reportes a PDF/Excel**: mostrarlos en pantalla ya cumple; exportar es una capa extra de esfuerzo (librerías, formato) que se puede dejar para el final.
- **A23 — revisión de certificados por el RM**: sin esto, el colaborador simplemente sube el certificado y ya — sigue siendo funcional, solo que sin validación. Bájenlo de prioridad sin culpa.

## 🟢 TIER 3 — DIFERIDO por acuerdo del equipo (no cancelado, solo pospuesto)

**Decisión tomada por el equipo:** Épica 9 (Horas y Pagos), Cursos (A25-A27) y Notificaciones (A24) quedan **fuera del compromiso hasta el Parcial**. No se eliminan del backlog — se retoman después si el tiempo lo permite.

**Checkpoint de revisión: justo después del Parcial (~semana del 12-19 de octubre).**
En ese momento, con el Tier 1 ya completo y evaluado, el equipo decide con datos reales (no con optimismo) si hay margen para retomar alguna de estas 3 piezas antes de fin de ciclo. Si en ese checkpoint el Tier 1 no está sólido al 100%, estas 3 quedan descartadas para el semestre sin necesidad de discutirlo de nuevo — la regla ya está tomada de antemano.

- **Épica 9 completa (Horas y Pagos)** — la pieza más grande y la más ajena a lo que pidió la profesora (RF01-07 no la mencionan). Si se retoma, considerar una versión reducida primero (solo registrar horas de tareas, sin calcular sueldo/bono automático) antes de ir por la versión completa con fórmulas y topes.
- **A25/A26/A27 — Sistema de Cursos**: depende de que la Épica 9 exista (es una de sus vías de remediación). Si Épica 9 no se retoma, esto tampoco se retoma.
- **A24 — Sistema de Notificaciones**: toca casi todos los demás módulos para integrarse. De las 3, es la que más sentido tiene retomar primero si sobra algo de tiempo, porque no depende de la Épica 9 y es la mejora de experiencia más visible en una demo.

**Regla práctica:** si en cualquier momento antes del checkpoint el equipo siente que va atrasado contra el cronograma (Entregables 3/4/5), no se espera al checkpoint — se recorta Tier 3 de inmediato. El checkpoint es la fecha *máxima* para decidir, no la única oportunidad.

---
---

# Resumen de cambios de estimación (Story Points)

| Historia | Antes | Ahora | Motivo del cambio |
|---|---|---|---|
| Gestión de asignaciones de colaboradores (A4) | 8 | **13** | Triple origen + doble aprobación condicional |
| Gestión de publicaciones del foro (A7) | 8 | **13** | Votos + ordenamiento + control de acceso por rol |
| Gestión de respuestas y soluciones del foro (A8) | 8 | **10** | Votos en respuestas + ordenamiento |
| Gestión de usuarios (A5) | 8 | **13** | Carga masiva + activación + sueldo_base |
| Consulta de proyectos y asignaciones del colaborador (A2, nueva) | — | **3** | Vista de solo lectura |
| Colaborador solicita incorporarse a un proyecto (A3, nueva) | — | **5** | Flujo de solicitud + validaciones |
| Activación de cuenta (A6, nueva) | — | **8** | Token + correo + formulario + expiración |
| Dashboards PM/RM — contador aprobaciones pendientes (A14) | 8 c/u | **8-10 c/u** | Contador de aprobaciones pendientes |
| **Épica 9 — Gestión de tareas (9.1)** | — | **10** | Flujo 2 pasos: colaborador marca → PM valida (C11) |
| **Épica 9 — Consolidado de horas (9.2)** | — | **5** | Cálculo mensual + desglose |
| **Épica 9 — Cálculo de sueldo y bono (9.3)** | — | **8** | Fórmula con tope + snapshot nómina |
| **Asignación de presupuesto al proyecto (A20, nueva)** | — | **5** | Campo presupuesto + edición solo RM |
| **Reporte de horas por proyecto — PM (A21, nueva)** | — | **8** | Vista + filtros + exportación PDF/Excel (C18) |
| **Reporte de presupuesto y horas — RM (A22, nueva)** | — | **8** | Vista + filtros + exportación PDF/Excel (C18) |
| **Revisión de certificados y nivel de experiencia (A23, nueva)** | — | **8** | Bandeja RM + aprobación/rechazo + nivel Junior/Senior |
| **Sistema de Notificaciones (A24, nueva)** | — | **8** | Servicio central + integración con todos los flujos |
| **Gestión del catálogo de cursos — Admin (A25, nueva)** | — | **5** | CRUD de cursos |
| **Colaborador solicita inscripción a curso (A26, nueva)** | — | **3** | Catálogo + solicitud + estado |
| **RM gestiona inscripciones a cursos (A27, nueva)** | — | **5** | Bandeja + aprobación/rechazo + asignación directa |

**Incremento neto total desde el documento original: +125 puntos aproximadamente.**
- Épica 9 (H9.1, H9.2, H9.3): +21 SP
- Nuevas historias A2, A3, A6, A20, A21, A22, A23, A24, A25, A26, A27: +64 SP
- Subidas de estimación en historias existentes (A4, A5, A7, A8, A9.1, A21, A22): +40 SP

**Total estimado del backlog completo: ~350 SP**
