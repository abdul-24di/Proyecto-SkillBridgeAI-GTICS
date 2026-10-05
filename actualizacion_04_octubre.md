# Actualización de Cambios - 04 de Octubre de 2026

A continuación, se detalla el resumen de todas las nuevas funcionalidades, mejoras y correcciones que se han implementado y subido en la rama `feature/cruds` el día de hoy:

## 1. Foros de la Comunidad (Administrador)
- **Interacción completa para el Admin:** El Administrador ahora tiene las mismas capacidades que un PM dentro de los foros de la comunidad. 
- **Funcionalidades añadidas:**
  - Dar y quitar **Likes** a comentarios y respuestas.
  - **Responder** a publicaciones existentes (se habilitó el modal de respuesta).
  - **Marcar como solución** la respuesta que resuelva el tema del foro.
  - **Eliminar** mensajes si es necesario.
- **Detalles técnicos:** Se reutilizó la lógica de negocio del `PmForoService` en el `AdminViewController` para mantener consistencia, y se actualizó la vista `admin-foro-detalle.html` para incluir los formularios y modales correspondientes.

## 2. Modal "Proponer Asignación" (Resource Manager)
- **Visualización de Requisitos del Proyecto:** Cuando el RM busca candidatos y hace clic en "Proponer", el modal emergente ahora muestra explícitamente el **Trabajo requerido en el proyecto** como referencia.
- **Información detallada:** Se inyectó dinámicamente un bloque que muestra cada habilidad solicitada por el PM, incluyendo:
  - Nombre de la habilidad (ej. React, Java, Liderazgo).
  - Nivel requerido (Básico, Intermedio, Avanzado).
  - Cantidad de personas requeridas para esa habilidad.
  - Horas semanales requeridas para esa tarea específica.

## 3. Corrección Crítica: Horas por Habilidad (Project Manager)
- **Bug Fix en la creación de proyectos:** Se solucionó un error donde el sistema ignoraba las horas específicas por habilidad que ingresaba el PM en el carrito al crear un proyecto, y en su lugar guardaba 20 horas (el valor por defecto) para todas las habilidades.
- **Detalles técnicos:** Se actualizó `PmViewController` y `PmProyectoService` para que reciban e iteren correctamente la lista `horasSemanalesHab` y la persistan en la entidad `ProyectoHabilidadRequerida`.

## 4. Correcciones Generales (Bug Fixes)
- **Pantalla blanca (Error 500) solucionada:** Se corrigió el problema que causaba que la vista de foros del PM fallara al intentar acceder a los foros generales/de comunidad.
- **Pruebas en GitHub Actions:** Se ajustaron los tests (ej. `RmTopbarTests`) para que el entorno de CI compile y pase las validaciones correctamente.

---
**Nota para el equipo:** Por favor, realicen un `git pull origin feature/cruds` y reinicien su servidor local para poder visualizar y probar todos estos cambios. Para probar el modal del RM con las horas correctas, es necesario crear un **proyecto nuevo** como PM.
