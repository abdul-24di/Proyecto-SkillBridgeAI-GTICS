# 🎯 Plan de Acción: Alcanzar el 90% de los CRUDs

He analizado todo el código fuente actual del proyecto (Controladores, Servicios, Repositorios y HTMLs) para identificar exactamente qué operaciones CRUD nos faltan para cumplir con el entregable del **90%** de esta semana. 

La buena noticia es que tus compañeros han avanzado muchísimo (ya tenemos CRUDs completos de Usuarios, Habilidades, Categorías y Matriz Salarial/Cargos). Sin embargo, he detectado **3 CRUDs principales** que aún no existen o están incompletos:

---

## 1. CRUD de Experiencia Profesional (Colaborador) 🔴 [URGENTE]
**Estado actual:** El modelo y la base de datos existen (`ExperienciaProfesional.java`), pero no hay endpoints para guardarla. La vista `col-perfil.html` tiene un mensaje estático que dice: *"Esta información la ingresa el Administrador"*, pero el Administrador **tampoco tiene** cómo ingresarla. 
**Lo que falta:**
- [ ] Crear métodos en `ColaboradorPerfilService` para guardar, editar y eliminar experiencia.
- [ ] Crear los `@PostMapping` en `ColaboradorViewController`.
- [ ] Construir los modales en `col-perfil.html` para que el propio Colaborador (o el RM) pueda registrar su historial laboral.

## 2. CRUD del Catálogo de Cursos (Resource Manager / Admin) 🟡 [PENDIENTE]
**Estado actual:** El RM puede asignar cursos a los colaboradores, y los colaboradores pueden solicitar cursos. ¡Pero nadie puede crear cursos nuevos! El sistema asume que los cursos aparecen por arte de magia en la BD.
**Lo que falta:**
- [ ] Agregar botones de "Nuevo Curso", "Editar" y "Eliminar" en el catálogo de cursos.
- [ ] Crear los endpoints en el controlador y servicio respectivo.

## 3. CRUD de Evaluaciones / Feedback (Project Manager / RM) 🔵 [RECOMENDADO]
**Estado actual:** Un RM puede finalizar una asignación de proyecto. Sin embargo, no existe un lugar donde se califique el desempeño del colaborador al finalizar (Nota del 1 al 5, comentarios de feedback). Esto es vital para "SkillBridge" porque debería alimentar el algoritmo de *talent matching*.
**Lo que falta:**
- [ ] Implementar un modal de "Evaluación de Desempeño" cuando el RM finalice la asignación.

---

### Siguiente paso
Te sugiero empezar por el **CRUD de Experiencia Profesional (Punto 1)**, ya que es la única parte del Perfil del Colaborador que aún está en blanco y es un CRUD estándar y rápido de hacer. 

¿Quieres que empecemos a codificar el CRUD de Experiencia Profesional ahora mismo?
