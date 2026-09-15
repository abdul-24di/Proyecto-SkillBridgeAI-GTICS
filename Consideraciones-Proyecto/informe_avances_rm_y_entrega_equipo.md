# Informe de avances y entrega al equipo — SkillBridge AI

Fecha de elaboración: 15 de septiembre de 2026.

## 1. Resumen y alcance

El trabajo se concentró en conectar las vistas del Resource Manager (RM) a la base de datos y desarrollar sus operaciones reales. Se avanzó en consulta de colaboradores y proyectos, revisión de proyectos, presupuesto, asignaciones, solicitudes de personal, certificados, foros de solo lectura, dashboard, reportes y cursos.

También se preparó la configuración local, se incorporaron pruebas aisladas y se agregaron datos SQL de demostración. Algunas integraciones del colaborador se utilizan para alimentar los flujos del RM, especialmente postulaciones y certificados.

Esto no significa que todo el proyecto ni los cuatro roles estén terminados. Las pantallas del PM y del administrador requieren sus propias conexiones al backend. El perfil del RM actualmente consulta datos reales, pero no guarda las modificaciones que aparecen en su formulario.

Este informe describe el estado comprobado en el código actual y los resultados de la última ejecución de pruebas disponible. No certifica el funcionamiento de una instancia desplegada en la nube.

## 2. Preparación técnica y organización

- La conexión a MySQL utiliza variables de entorno, sin guardar su contraseña en `application.properties`.
- En ejecución normal, `spring.jpa.hibernate.ddl-auto=none`: Hibernate no crea ni actualiza automáticamente las tablas. Los cambios de estructura se aplican mediante SQL.
- Se configuró el perfil `test` con H2 en memoria y `create-drop`. H2 es una base temporal exclusivamente para las pruebas; no reemplaza el MySQL del proyecto ni modifica la BD local o de nube.
- `.gitignore` excluye `target/`, configuraciones de los IDE y archivos locales de secretos. En la revisión actual, `target/` no contiene archivos rastreados por Git. Agregar una carpeta al `.gitignore` no elimina archivos que ya estuvieran versionados.
- Los cambios de despliegue y las migraciones están documentados en el README.

La estructura mantiene Spring MVC, Thymeleaf y Spring Data JPA:

| Carpeta dentro de `src/main/java/com/pucp/skillb_ia/` | Responsabilidad |
| --- | --- |
| `model/` | Entidades JPA que representan tablas y relaciones. Cumple la función de la carpeta `entity/` usada en los laboratorios; el nombre de la carpeta no cambia su naturaleza. |
| `model/enums/` | Estados y valores limitados del dominio. |
| `repository/` | Consultas y persistencia mediante Spring Data JPA, incluidos métodos derivados y consultas JPQL. |
| `service/rm/` | Reglas de negocio y transacciones del RM. |
| `dto/` | Datos preparados para las vistas, resúmenes y exportaciones. Un DTO no es otra tabla. |
| `controller/` | Rutas HTTP, parámetros, formularios, redirecciones y mensajes. |
| `config/` y `security/` | Configuración y soporte de autenticación. |

Las vistas están en `src/main/resources/templates/rm/`; los estilos y scripts correspondientes están en `src/main/resources/static/`. No se cambió `model/` a `entity/`.

La exportación Excel incorpora Apache POI (`poi-ooxml` 5.2.5). El PDF se genera desde `RmReporteExportService`. Estas utilidades y las pruebas son complementos técnicos; no sustituyen el uso de entidades, repositorios, controladores y relaciones JPA. Este informe no constituye una nueva comparación diapositiva por diapositiva con las clases del curso.

## 3. Funcionalidades desarrolladas

### 3.1. Perfil del RM

La ruta `/rm/perfil` recupera el usuario autenticado desde la BD y comprueba que tenga rol RM. Muestra sus datos reales.

Nombres, apellidos y correo están bloqueados para edición, respetando la regla acordada de que los cambios de identidad corresponden al administrador.

Limitación actual: `RmPerfilService` solo implementa consulta. No hay una ruta POST para guardar teléfono, foto o contraseña del RM. El formulario previene el envío y el teléfono está deshabilitado; el botón «Guardar cambios» no representa una actualización persistente. Implementar el guardado de esos campos queda pendiente, sin habilitar nombres ni apellidos.

### 3.2. Consulta de colaboradores

Se conectaron el directorio, el perfil seleccionado y la consulta de asignaciones a datos reales. Se consulta información de colaboradores activos, habilidades, educación, experiencia, certificados y carga de asignaciones.

Los resúmenes muestran disponibilidad y carga; el límite de asignaciones se obtiene de la configuración del sistema, con valor de respaldo si no está configurado. La información presentada al RM no incluye remuneraciones.

Archivos principales: `RmColaboradorConsultaService`, `RmColaboradorResumen`, `RmColaboradorDetalle` y las vistas `rm-colaboradores.html`, `rm-perfil-colaborador.html` y `rm-asignaciones-colaborador.html`.

### 3.3. Proyectos y revisión

Se conectaron listado, detalle y revisión por identificador real. Los datos de equipo, vacantes, habilidades requeridas y pendientes provienen de la BD.

El RM puede aprobar un proyecto en `EN_REVISION`, pasando a `ACTIVO`, o rechazarlo con un motivo obligatorio. Se conserva el revisor y se registra auditoría. Las reglas impiden resolver nuevamente un proyecto que ya no esté en revisión.

Archivos principales: `RmProyectoConsultaService`, `RmProyectoRevisionService` y `RmProyectoView`.

Esto cubre la consulta y decisión del RM, no el CRUD completo de creación y administración del proyecto desde el PM.

### 3.4. Presupuesto del proyecto

El RM puede asignar o actualizar presupuesto en proyectos `EN_REVISION`, `ACTIVO` o `EN_ESPERA`. El importe debe ser mayor que cero, se normaliza a dos decimales y se valida el máximo admitido por el modelo.

No se permite aprobar un proyecto sin presupuesto válido ni modificarlo mediante esta operación cuando el proyecto está en un estado no editable, como `FINALIZADO`. Se registra auditoría de asignación o actualización. El servicio verifica que quien realiza la operación sea un RM activo; el PM no puede usarlo para editar el presupuesto.

El presupuesto mostrado es un monto asignado. No debe interpretarse como una contabilidad completa de gastos, pagos o saldo ejecutado.

### 3.5. Asignaciones

Se implementaron consulta, detalle, propuesta desde RM, aprobación, rechazo y finalización de asignaciones activas.

Se distinguen tres orígenes: propuesta del PM, propuesta del RM y solicitud del colaborador. Una propuesta del RM queda pendiente de aprobación del PM; cuando una asignación recibe ambas aprobaciones, puede pasar a `ACTIVA`.

Se validan duplicados pendientes o activos para el mismo colaborador y proyecto, horas semanales y capacidad. Si se supera la disponibilidad o el límite de asignaciones, se exige justificación de la excepción. Los rechazos requieren motivo y la finalización conserva motivo, responsable y fecha. Estas operaciones registran auditoría.

Archivos principales: `RmAsignacionService`, `RmAsignacionView` y las rutas `/rm/asignaciones/...`.

La lógica del RM no significa que la interfaz de aprobación del PM ya esté conectada. Esa integración es necesaria para completar desde las pantallas el ciclo de las propuestas originadas en RM.

### 3.6. Solicitudes de personal y postulaciones

Se separaron dos conceptos que no deben confundirse:

- `solicitud_personal`: el PM pide una cantidad de colaboradores y perfiles para su proyecto. No identifica por sí misma a una persona asignada.
- `asignacion` de origen `SOLICITUD_COLABORADOR`: una persona postula a un proyecto concreto y entra al flujo de aprobaciones.

El nuevo modelo de solicitudes de personal incluye proyecto, cantidad, perfiles, mensaje, estado, RM responsable y fechas. El flujo implementado permite `PENDIENTE → EN_ATENCION → ATENDIDA`; el modelo también admite `CANCELADA`. Se impide crear dos solicitudes abiertas para el mismo proyecto.

Existe un método de servicio para crear la solicitud desde el PM, validando que sea el responsable del proyecto. La interfaz y ruta de creación del PM siguen pendientes de conexión.

Las postulaciones del colaborador cuentan con servicio y POST real de solicitud de incorporación. Se consultan como asignaciones y aparecen en los indicadores y pendientes del dashboard del RM. No deben insertarse en `solicitud_personal`.

Archivos principales: `SolicitudPersonal`, `EstadoSolicitudPersonal`, `SolicitudPersonalRepository`, `RmSolicitudPersonalService`, `RmSolicitudPersonalView` y `ColaboradorProyectoService`.

### 3.7. Validación de certificados

Se conectaron bandeja de pendientes, revisión y historial por colaborador. El RM puede aprobar o rechazar con motivo, conservando responsable y fecha. Se impide revisar nuevamente un certificado ya resuelto.

Al aprobar se valida la habilidad asociada y se puede actualizar su nivel de dominio y el nivel general de experiencia del colaborador. También existe una operación específica para actualizar el nivel general. Si un certificado se rechaza, se tiene en cuenta si ya existe otro aprobado de la misma habilidad para no invalidarla indebidamente. Se registra auditoría.

La carga desde el colaborador utiliza almacenamiento local configurable y acepta PDF, JPG o PNG, hasta 10 MB. Se ajustaron los límites multipart y la configuración de consumo de cargas rechazadas de Tomcat para permitir una respuesta controlada y evitar el restablecimiento de conexión observado con ciertas cargas.

Archivos principales: `RmCertificadoService`, `RmCertificadoView`, `ColaboradorPerfilService` y `application.properties`.

### 3.8. Foros de solo lectura

El RM consulta foros generales y de proyecto, incluidos los privados, sin necesitar una asignación al proyecto. Se muestran publicaciones, autores, respuestas, solución aceptada, etiquetas, votos y última actividad desde la BD.

El detalle permite ordenar publicaciones por fecha o votos. No se habilitaron operaciones del RM para publicar, responder, votar o acceder al chat: su alcance es de consulta.

Archivos principales: `RmForoConsultaService`, `RmForoView`, `rm-foros.html` y `rm-foro-detalle.html`.

### 3.9. Dashboard

Se reemplazaron los datos estáticos por resúmenes de proyectos, asignaciones, solicitudes de personal y certificados.

Incluye aprobaciones pendientes del RM, espera del PM, postulaciones de colaboradores, proyectos con vacantes, proyectos en revisión, solicitudes de personal abiertas y certificados pendientes. Presenta listas limitadas de acciones, solicitudes recientes y proyectos que requieren atención, con enlaces hacia sus detalles.

La prioridad visual se calcula considerando revisión, pendientes, vacantes y prioridad del proyecto. No es una recomendación de inteligencia artificial.

### 3.10. Reportes

Se implementaron el reporte de recursos y la consulta de horas por colaborador, con filtros de periodo, proyecto y estado, además de búsqueda de colaboradores en la vista de horas. Se generan exportaciones reales a Excel y PDF desde los datos del reporte.

Regla importante: las «horas trabajadas» son la suma de `actividad.horas_estimadas` para actividades `COMPLETADA` cuya `fecha_entrega` pertenece al mes seleccionado. No son horas registradas por un reloj ni una hoja de asistencia. No se suman actividades pendientes o en revisión.

Las vistas incluyen presupuesto asignado, horas y desgloses. No exponen sueldo base, bonos ni pagos mensuales y no implementan nómina. Los presupuestos reflejan el valor actual de los proyectos, no un historial mensual de modificaciones presupuestarias.

Archivos principales: `RmReporteService`, `RmReporteExportService`, `RmReporteView`, `rm-reporte-recursos.html` y `rm-horas-colaboradores.html`. Se retiraron scripts que alimentaban estas pantallas con información ficticia.

### 3.11. Cursos del RM y notificaciones

Se conectaron las tres vistas del RM: catálogo, solicitudes e inscripción directa.

- Catálogo de cursos activos con búsqueda y filtros de categoría y duración.
- Bandeja con filtros de estado y origen, incluyendo historial y motivos guardados. Por defecto muestra pendientes; «Todos» incluye los demás estados.
- Aprobación de solicitudes, que pasan a `EN_CURSO`, y rechazo con motivo obligatorio.
- Asignación directa a colaboradores activos, con justificación y estado inicial `EN_CURSO`.
- Validación de cursos activos y de duplicados pendientes o activos.
- Notificaciones persistidas al colaborador por aprobación, rechazo o asignación.

Se conectó la campana compartida a `/api/notificaciones`, con consulta de las últimas 20 notificaciones y marcado individual o total como leídas. Las operaciones de marcado comprueban que la notificación pertenezca al usuario. No se implementó una página completa con filtros para todas las notificaciones ni avisos de todos los eventos del proyecto.

Archivos principales: `RmCursoService`, `RmCursoView`, `NotificacionService`, `NotificacionController`, `NotificacionView` y `notificaciones.js`.

Pendiente fuera del RM: mantenimiento del catálogo por el administrador, solicitud de cursos desde la vista del colaborador y gestión de su finalización. La pestaña de cursos del colaborador todavía utiliza datos simulados. La notificación muestra el resultado en la campana, sin enlazar a esa pestaña simulada.

## 4. Base de datos: instrucciones para el equipo

### Instalación nueva

Ejecutar `BDs_SQL/skillbridge_db_v4.sql` actualizado. Ya contiene la tabla `solicitud_personal` y el campo `colaborador_curso.motivo_respuesta`; no necesita las dos migraciones posteriores.

No ejecutar el esquema completo encima de una base existente como si fuera una migración.

### Base existente, local o en la nube

Respaldar la base y comprobar si ya tiene cada cambio. Aplicar antes de iniciar la nueva versión:

| Archivo en `BDs_SQL/` | Cambio |
| --- | --- |
| `migracion_solicitud_personal.sql` | Crea `solicitud_personal` si no existe, con sus relaciones, índices y validaciones. No transforma ni elimina las asignaciones existentes. |
| `migracion_cursos.sql` | Agrega `motivo_respuesta VARCHAR(500) NULL` a `colaborador_curso` si falta. Conserva los registros anteriores. |

Son cambios aditivos, no un borrado de la BD. Sin embargo, omitirlos puede provocar errores al consultar o guardar desde el nuevo código. Que una migración sea aditiva no elimina la necesidad de respaldar y verificar la estructura existente.

Los scripts seleccionan explícitamente `skillbridge_db`. Si el entorno utiliza otro nombre, el responsable del despliegue debe ajustar y revisar esa selección antes de ejecutarlos.

### Datos de demostración

`BDs_SQL/datos_demo_rm_proyectos.sql` permite probar proyectos, presupuesto, asignaciones, solicitudes de personal, certificados, foros, actividades de reportes y cursos. Ejecutarlo después del esquema o de las migraciones necesarias.

El usuario RM de demostración es `demo.rm@skillbridge.local`, contraseña `abc123`. También se crean cuentas de administrador, PM y colaboradores de demostración. Estas credenciales son exclusivamente de desarrollo.

Reejecutar este archivo restaura distintos datos de demostración, incluidos estados de proyectos e inscripciones de cursos. Las decisiones realizadas sobre esos datos durante una prueba pueden reiniciarse. No es una carga de datos para producción.

Los datos de certificados no sustituyen la carga de un archivo real: para comprobar descarga o visualización, subir un certificado desde el colaborador.

### Precaución con el reinicio

`BDs_SQL/reset_desarrollo_v4.sql` es destructivo y borra tablas y datos. No utilizarlo en la nube ni en una base con datos que deban conservarse. Además, su lista actual de borrado no incluye `solicitud_personal`; debe revisarse antes de intentar un reinicio completo sobre el esquema actualizado.

## 5. Indicaciones para quien despliega

1. Obtener el código actualizado y utilizar un JDK compatible con Java 25, declarado en `pom.xml`.
2. Respaldar la BD existente y aplicar solo las migraciones que correspondan.
3. Configurar `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` para el MySQL correcto. No usar credenciales de ejemplo ni subir secretos al repositorio.
4. Configurar `UPLOAD_DIR` como directorio escribible y persistente. Las fotos y certificados pueden perderse si quedan en el almacenamiento temporal de una instancia.
5. Reconstruir el proyecto mediante Maven, incluyendo la dependencia Apache POI. Por ejemplo: `./mvnw clean package` en Linux o `.\mvnw.cmd clean package` en Windows.
6. Desplegar y reiniciar la aplicación. Verificar conexión, rutas del RM, archivos subidos y descargas de reportes.
7. No activar el perfil `test` para la aplicación desplegada: utiliza una BD temporal H2.
8. No ejecutar datos demo ni scripts de reinicio sobre producción.

La configuración normal no aplica automáticamente las migraciones. La responsabilidad de actualizar la BD de nube permanece en el compañero encargado del despliegue.

## 6. Pruebas y límites de la verificación

La última ejecución completa registrada, del 14 de septiembre de 2026, terminó con 46 pruebas, 0 fallos y 0 errores:

| Grupo | Pruebas |
| --- | ---: |
| Asignaciones | 9 |
| Consulta de colaboradores | 2 |
| Certificados | 6 |
| Cursos | 6 |
| Dashboard | 2 |
| Foros | 3 |
| Proyectos y presupuesto | 10 |
| Reportes | 3 |
| Solicitudes de personal | 4 |
| Inicio del contexto de la aplicación | 1 |
| Total | 46 |

Cubren reglas de negocio, renderizado de vistas, estados, duplicados y archivos exportados, según cada grupo. Para repetirlas en Windows:

```powershell
.\mvnw.cmd test
```

Las pruebas usan H2 y no afectan MySQL. No equivalen a ejecutar las migraciones en MySQL ni a probar en un navegador todos los flujos del PM, administrador o nube. En la elaboración de este informe se revisaron el código y los resultados disponibles; no se volvió a ejecutar la suite ni se modificó una base de datos.

## 7. Pendientes y riesgos que no deben perderse

- Completar restricciones globales de acceso por rol. `SecurityConfig` mantiene `anyRequest().permitAll()` para facilitar el desarrollo. Hay comprobaciones en distintas operaciones, pero no sustituyen una política global de seguridad.
- Reactivar CSRF después de integrar tokens en todos los formularios y peticiones de escritura. Actualmente está deshabilitado. No debe considerarse una configuración final de producción.
- Revisar la clave fija de «recordar sesión» y gestionar su configuración apropiadamente para el despliegue.
- Completar el guardado del perfil del RM; los controles visibles no garantizan persistencia.
- Conectar las operaciones del PM necesarias para creación de proyectos, solicitudes de personal y aprobación de propuestas del RM.
- Conectar el catálogo de cursos del administrador y las solicitudes y seguimiento de cursos del colaborador.
- Completar Talent Matching/IA: la ruta del RM todavía renderiza una vista sin servicio real de recomendaciones.
- Ampliar notificaciones si se requiere el alcance transversal completo, con página de historial, filtros y más tipos de eventos.
- Configurar correo real. `EmailService` todavía escribe enlaces y códigos en consola; no envía correos mediante SMTP.
- Verificar migraciones, carga de archivos y flujos integrados sobre MySQL y en el entorno desplegado antes de afirmar que no existen regresiones.

## 8. Guía breve de revisión manual

Con una base de desarrollo actualizada y datos de prueba:

1. Iniciar sesión como RM y comprobar que el perfil consulta al usuario correcto y que nombres y apellidos no se pueden editar.
2. Consultar colaboradores y revisar que sus detalles correspondan al identificador seleccionado.
3. Abrir un proyecto en revisión, asignar presupuesto y aprobarlo. Usar otro para comprobar rechazo con motivo.
4. Proponer una asignación desde RM y comprobar que espera al PM. Revisar aprobación y rechazo de solicitudes pendientes del RM y finalizar una asignación activa.
5. Verificar que las postulaciones reales del colaborador aparezcan como asignaciones y en los pendientes del dashboard, no como solicitudes generales de personal.
6. Recorrer los estados de una solicitud de personal de demostración.
7. Subir un certificado real desde un colaborador, revisarlo como RM y comprobar su historial y la validación de la habilidad.
8. Abrir foros generales y privados como RM; comprobar que solo se ofrecen acciones de lectura.
9. Comparar indicadores del dashboard con sus bandejas de origen.
10. Filtrar reportes por mes y descargar Excel/PDF; comprobar que solo sumen actividades completadas del periodo.
11. En Cursos, aprobar y rechazar solicitudes, asignar directamente, intentar un duplicado y comprobar las notificaciones desde la cuenta del colaborador.

## 9. Documentación de referencia

La configuración y migraciones también están resumidas en `README.md`. Este informe complementa el plan histórico `Consideraciones-Proyecto/informe_revision_y_plan_cruds.md`: sus apartados que indicaban que todo RM estaba pendiente ya no describen el avance actual.

Para la entrega al equipo, compartir este informe junto con el código y los SQL actualizados. No basta con compartir las plantillas HTML: los servicios, DTO, repositorios, dependencias y estructura de BD forman parte del funcionamiento implementado.
