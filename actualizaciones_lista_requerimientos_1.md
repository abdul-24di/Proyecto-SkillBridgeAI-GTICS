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
- El PM puede marcar una tarea como entregada, indicando si fue a tiempo o tardía (comparando contra la fecha límite).
- Las tareas quedan asociadas al proyecto y al colaborador correspondiente.

**DoR:** La Historia de Gestión de asignaciones (A4) está completada — una tarea depende de que el colaborador tenga asignación activa · Los campos de una tarea están definidos · La historia ha sido estimada.

**DoD:** El PM crea una tarea con horas estimadas y fecha límite · Solo se asigna a colaboradores con asignación activa · Se puede marcar como entregada (a tiempo/tardía) · Se probaron los flujos de creación y entrega.

**Estimación:** 8 puntos — entidad nueva con validación de asignación activa y control de fechas.

**Subtareas:**
- Implementar entidad Tarea (proyecto, colaborador, horasEstimadas, fechaLimite, fechaEntrega, estado).
- Implementar creación de tarea, validando asignación activa del colaborador en el proyecto.
- Implementar marcado de entrega (a tiempo / tardía).
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
- El PM puede finalizar (desasignar) la asignación activa de un colaborador de su proyecto directamente, sin necesidad de aprobación del RM.

**Agregar a DoD:** Se verificó que el PM puede finalizar una asignación de su proyecto directamente.

**Agregar Subtarea:** Implementar finalización/desasignación de una asignación activa por parte del PM.

*(Ver duda C16 — falta confirmar si el RM también debería poder hacerlo, o si queda exclusivo del PM.)*

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

# BLOQUE B — PENDIENTE DE DECIDIR (el equipo debe resolverlo)

1. **Historia "Gestión de disponibilidad" (Épica 3):** falta definir si se mide en horas/semana, %, o estados con nombre.
ES POR HORAS
2. **Etiquetas RF08 (Administración) y RF09 (Reportes y Dashboard):** trazabilidad frente a los RF01-07 de la profesora. Opcional.

3. **Campo "prioridad" (Alta/Media/Baja) en Proyecto:** para que el RM arbitre con datos reales. Opcional.
SE AGREGARA EL CAMPO DE PRIORIDAD PARA LOS PROYECTOS, el PM tendra una opcion en que nivel estara el proyecto de prioridad y el motivo. Luego el RM sera el que lo revise y confirmar/rechazar. Tambien el PM puede cancelar su proyecto antes de que el RM pueda tomar una decision.
APARTE:
Proyecto tendra una tabla de estados: En revisión, Rechazado, Activo, En espera, Cancelado, Finalizado.

4. **Historia formal de "Notificaciones del sistema":** hoy solo existe como vista de UI en el mockup del Admin, no como historia con criterios propios.
Todos los roles tienen que tener sus notificaciones, y que las notificaciones tengan sus filtros dependiendo de cada rol

5. **Seguimiento a la responsabilidad de PM/RM cuando un colaborador no llega a sus 160 horas:** si debe generar algún reporte o alerta, y para quién. *(pendiente propio del documento de Horas y Pagos)*
Puede entrar como apoyo a otros proyectos. Y sobre los cursos/certificados, CURSOS Y CAPACITACIONES CREADAS POR ADMIN. AUN POR CONFIRMAR
6. **Si el tope de horas extra (bono) es único por colaborador, o puede variar según proyecto o rol.** *(pendiente propio del documento de Horas y Pagos)*
POR COLABORADOR
7. **Cómo se maneja un colaborador que trabaja solo parte del mes** (ingresa o sale a mitad de mes) — la meta de 160 horas no debería aplicarse completa en ese caso. *(pendiente propio del documento de Horas y Pagos)*
SI LO BOTAN A MITAD DE MES SE LE CONTABILIZA LAS HORAS TRABAJADAS, PERO SEGUN EL MOTIVO SE LE PENALIZARA (POR EJEMPLO, SI FUE POR MAL DESEMPEÑO SE LE PENALIZARA Y SI FUE PORQUE EL PROYECTO SE CANCELO NO SE LE PENALIZARA)

---
COSAS DEFINIDAS
PM: En su solicitud de proyecto, el PM debe marcar la prioridad (Alta/Media/Baja) y justificar para que el RM lo evalue.
En caso de que el colaborador no se esté cumpliendo las horas de trabajo, el RM cuenta con las opciones de incluir al colaborador en otro proyectos como apoyo o asginarle cursos.
El colaborador también puede solicitar de participar en cursos.
El colaborador y RM deben contar con una sección que les peermita revisar los cursos disponibles.
Ela dminsitrador se encarga de registrar los cursos
---

# BLOQUE C — DUDAS NUEVAS / A REPLANTEAR

*Encontradas al revisar todo el flujo en conjunto. No son errores del equipo — son consecuencias de ir cerrando decisiones por partes. Ninguna bloquea el Entregable 1, pero conviene que las vean antes de programar.*

**C1. Dependencia circular entre A5 (Gestión de usuarios) y A6 (Activación de cuenta).**
Puse en el DoR de A5 que "A6 debe estar completada", pero A6 depende de que exista un usuario creado (A5) para poder activarlo. En la práctica no es un problema real — solo significa que Mili (dueña de A6) y yo (A5) tenemos que coordinar y desarrollarlas casi en paralelo — pero como está redactado ahora mismo parece una dependencia imposible de resolver en el papel. Recomiendo aclarar esto en ambas historias con una nota tipo "se desarrollan de forma coordinada", no como bloqueo estricto.

**C2. "Reenviar el enlace de activación" está duplicado en dos historias.**
Aparece como subtarea en A5 (Gestión de usuarios) y como criterio/DoD en A6 (Activación de cuenta). Deberían decidir quién es el dueño real: mi propuesta es que A6 dueña la lógica (generar un nuevo enlace, invalidar el anterior) y A5 solo tiene el botón en la UI que la dispara — pero convendría que ustedes lo dejen explícito para que no se programe dos veces por separado.

**C3. ¿La activación de cuenta debería quedar en el log de auditoría (Historia 5.4)?**
Ahora mismo audito "creación de usuario" pero no el momento en que ese usuario efectivamente activa su cuenta. Podría ser útil para que el Admin sepa quién todavía no ha activado su invitación. Sugerencia: agregar "activación de cuenta completada" a la lista de acciones auditadas.

**C4. ¿El colaborador puede editar su propio correo desde su perfil (Historia de perfil, A1)?**
No quedó explícito. Mi recomendación: que el correo sea de solo lectura para el colaborador (solo el Admin puede cambiarlo), ya que es el identificador de login y de las notificaciones — cambiarlo libremente podría causar problemas de acceso. Falta que el equipo lo confirme.

**C5. Falta el mockup de la pantalla de activación y del correo de activación.**
La Historia A10 (mockups) no incluye una pantalla para "el usuario pone su nombre y contraseña tras hacer clic en el link" ni el diseño del correo que se envía. Es una pantalla nueva que nadie tiene asignada todavía dentro de la Historia de Mockups — probablemente le toque a Mili por ser parte de Épica 2, pero hay que decidirlo.

**C6. Validación de duplicados en la carga masiva (A5): ¿contra qué se compara?**
Ya lo dejé más claro en el texto de A5 (duplicados dentro del archivo Y contra usuarios ya existentes), pero quiero que quede explícito que lo confirmen — es fácil programar solo la validación "dentro del archivo" y olvidar cruzarlo contra la base de datos real.

**C7. Confidencialidad del sueldo — ¿quién puede ver `sueldo_base` y `pago_total` de un colaborador?**
El documento de Horas y Pagos no lo especifica. Mi recomendación: **solo el propio colaborador y el Administrador** — ni el PM ni el RM deberían verlo, ya que gestionan asignaciones y tareas, no compensación. Falta que el equipo lo confirme y se agregue como regla de acceso explícita en la Historia 9.3.

**C8. ¿El sistema de Horas y Pagos aplica solo a Colaboradores, o también a PM/RM/Admin?**
El documento siempre dice "colaborador". Asumí que es exclusivo de ese rol — si un PM o RM también recibiera bono por horas trabajadas, el modelo cambiaría bastante (¿quién les asigna tareas a ellos?). Recomiendo confirmarlo explícitamente con el equipo.

**C9. Tres conceptos distintos de "qué tan ocupado está" un colaborador — no confundirlos.**
Ahora coexisten: **disponibilidad** (Épica 3, autodeclarada, pendiente B1), **carga** (asignaciones activas / límite, Épica 4-5-6) y **horas trabajadas** (Épica 9, para el sueldo). No hace falta unificarlos — cada uno responde a una pregunta distinta — pero si definen "disponibilidad" en horas/semana (B1), dejen claro que es una cifra distinta de las horas de tareas de la Épica 9, para que no se mezclen sin querer en el modelo de datos.

**C10. Auditoría de cambios de configuración (Historia 5.4) — nunca estuvo incluida.**
Repasando el log de auditoría completo, noté que "cambios en los parámetros de configuración del sistema" **nunca formó parte** de las acciones auditadas — ni el límite de asignaciones original, ni ahora el nuevo tope de horas extra (A16). Cambiar ese tope afecta directamente cuánto se le paga a la gente, así que sugiero agregar esta categoría al log de auditoría, más allá del sistema de horas.

**C11. ¿Quién marca una tarea como "entregada" — el PM, o el colaborador la marca y el PM solo valida?**
El documento no lo aclara. En la Historia 9.1 asumí que el PM la marca directamente, pero en la práctica suele ser el colaborador quien sabe cuándo terminó. Vale la pena que el equipo lo confirme, porque cambia de quién es la acción en la interfaz.

**C12. ¿Qué pasa con las horas ya contabilizadas de una tarea si la asignación del colaborador al proyecto se rechaza o finaliza antes de que la tarea se entregue?**
Caso borde: un PM asigna una tarea, y luego la asignación al proyecto se da de baja por algún motivo. ¿La tarea y sus horas ya contabilizadas se mantienen o se anulan? No está definido en ninguno de los dos documentos.

**C13. "Tiempo controlado por actividades" — interpreté que las horas se controlan a través de las tareas (Historia 9.1), no con un registro libre de tiempo.**
Confirmen si es correcto, o si en realidad quieren algo más granular, como que el colaborador marque inicio/fin de cada actividad (un cronómetro real), en vez de solo horas estimadas por tarea.

**C14. Nivel de experiencia (Junior/Senior) — ¿lo asigna el RM (como lo integré, junto con la revisión de certificados), o se autodeclara el colaborador?**
Y algo más importante: ¿afecta algo automático del sistema (por ejemplo el valor_hora o el sueldo_base en la Épica 9), o es solo informativo / para filtrar candidatos en el AI Talent Matching?

**C15. Presupuesto de proyecto (A20) — ¿se valida contra los pagos reales generados por los colaboradores asignados a ese proyecto (Épica 9), o es un número puramente informativo que el RM declara sin ningún control automático?**
Si more adelante quieren que el sistema avise cuando un proyecto se pasa de presupuesto, esa validación no está contemplada todavía.

**C16. Desasignar colaborador (A18) — ¿requiere aprobación del RM, igual que asignar, o el PM puede hacerlo directamente?**
Lo dejé como acción unilateral del PM (el riesgo de "liberar" a alguien es distinto al de "consumir" un recurso escaso), pero conviene que el equipo lo confirme explícitamente.

**C17. Revisión de certificados (A23) — si el RM rechaza un certificado, ¿qué pasa con él?**
¿Se elimina, o queda visible con la marca "rechazado"? ¿Se notifica al colaborador (conectaría con la Historia de Notificaciones, todavía pendiente en el Bloque B)?

**C18. Reportes de horas y presupuesto (A21/A22) — ¿deben poder exportarse (PDF/Excel), o basta con verlos en pantalla como los dashboards ya existentes (6.1/6.2)?**
La palabra "reporte" a veces implica un documento descargable, distinto de un dashboard interactivo — vale la pena que lo aclaren para no subestimar el esfuerzo de estas 2 historias.

---

# Resumen de cambios de estimación (Story Points)

| Historia | Antes | Ahora |
|---|---|---|
| Gestión de asignaciones de colaboradores (A4) | 8 | 13 |
| Gestión de publicaciones del foro (A7) | 8 | 13 |
| Gestión de respuestas y soluciones del foro (A8) | 8 | 10 |
| Gestión de usuarios (A5) | 8 | 13 |
| Consulta de proyectos y asignaciones (A2, nueva) | — | 3 |
| Colaborador solicita incorporarse (A3, nueva) | — | 5 |
| Activación de cuenta (A6, nueva) | — | 8 |
| Dashboards PM/RM (A14) | 8 c/u | 8-10 c/u |
| **Épica 9 — Gestión de tareas (9.1)** | — | 8 |
| **Épica 9 — Consolidado de horas (9.2)** | — | 5 |
| **Épica 9 — Cálculo de sueldo y bono (9.3)** | — | 8 |
| **Asignación de presupuesto al proyecto (A20, nueva)** | — | 5 |
| **Reporte de horas por proyecto — PM (A21, nueva)** | — | 5 |
| **Reporte de presupuesto y horas — RM (A22, nueva)** | — | 5 |
| **Revisión de certificados y nivel de experiencia (A23, nueva)** | — | 8 |

**Incremento neto aproximado: +81 puntos** al backlog total. La Épica 9 + las historias A20-A23 agregan **44 puntos** entre todas — es un bloque de trabajo grande, probablemente valga la pena tratarlo como su propia fase dentro del cronograma, no meterlo de golpe en un solo entregable.
