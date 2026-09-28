# CHANGELOG - SkillBridge AI
## Sesión 28 de Septiembre 2026

---

## 1. CRUD Catálogo de Cursos (Administrador)

### Archivos nuevos
- `src/main/java/com/pucp/skillb_ia/service/AdminCursoService.java` — Lógica para listar, crear, editar y activar/desactivar cursos con validación de fechas.
- `src/main/resources/templates/admin/admin-cursos.html` — Vista completa con tabla, modal de creación y modal de edición.
- `src/main/java/com/pucp/skillb_ia/repository/CursoRepository.java` — Añadido `findAllByOrderByNombreAsc()`.

### Archivos modificados
- `src/main/java/com/pucp/skillb_ia/controller/AdminViewController.java` — Rutas GET/POST para `/admin/cursos`, `/admin/cursos/crear`, `/admin/cursos/editar`, `/admin/cursos/alternar`.
- `src/main/resources/templates/fragments/admin-topbar.html` — Agregado enlace a la sección Cursos.

### Bug resuelto
- Thymeleaf no permite usar `mod` como variable de iteración (es una palabra reservada matemática). Se renombró a `m` en el `th:each` del selector de modalidades, corrigiendo la pantalla en blanco al acceder a Cursos.

---

## 2. Sistema de Evaluaciones / Feedback de Desempeño

### Archivos nuevos
- `src/main/java/com/pucp/skillb_ia/model/Evaluacion.java` — Nueva entidad JPA con campos: `colaborador`, `evaluador`, `asignacion`, `calificacion` (1-5), `comentarios`, `fechaCreacion`.
- `src/main/java/com/pucp/skillb_ia/repository/EvaluacionRepository.java` — Repositorio JPA para persistir evaluaciones.
- `src/main/java/com/pucp/skillb_ia/service/EvaluacionService.java` — Métodos `evaluarAsignacion(...)` y `obtenerEvaluacionesPorColaborador(Long colaboradorId)`.

### Archivos modificados
**Backend:**
- `src/main/java/com/pucp/skillb_ia/service/rm/RmAsignacionService.java` — Inyectado `EvaluacionService`. Método `finalizar()` ahora recibe `calificacion` e `feedback`.
- `src/main/java/com/pucp/skillb_ia/service/pm/PmAsignacionService.java` — Inyectado `EvaluacionService`. Método `finalizar()` ahora recibe `calificacion` e `feedback`.
- `src/main/java/com/pucp/skillb_ia/controller/RmViewController.java` — Endpoint `POST /asignaciones/{id}/finalizar` acepta `calificacion` y `feedback`.
- `src/main/java/com/pucp/skillb_ia/controller/PmViewController.java` — Endpoint `POST /asignaciones/{id}/finalizar` acepta `calificacion` y `feedback`.
- `src/main/java/com/pucp/skillb_ia/controller/ColaboradorViewController.java` — Inyectado `EvaluacionService`, se expone `evaluaciones` al modelo del perfil.

**Frontend:**
- `src/main/resources/templates/rm/rm-detalle-asignacion-activa.html` — Formulario de finalización ampliado con selector de estrellas (1-5) y textarea de feedback.
- `src/main/resources/templates/pm/pm-asignaciones-proyecto.html` — Botón "Finalizar" abre un modal con selector de estrellas y feedback antes de confirmar.
- `src/main/resources/templates/rm/rm-perfil-colaborador.html` — Nueva tarjeta "Historial de Desempeño" que muestra todas las evaluaciones del colaborador.
- `src/main/resources/templates/col/col-perfil.html` — Nueva sección "Feedback y Evaluaciones" visible para el colaborador en su propio perfil.

### Script SQL requerido
```sql
CREATE TABLE evaluacion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    colaborador_id BIGINT NOT NULL,
    evaluador_id BIGINT NOT NULL,
    asignacion_id BIGINT,
    calificacion INT NOT NULL,
    comentarios VARCHAR(1000),
    fecha_creacion DATETIME NOT NULL,
    CONSTRAINT fk_eval_colaborador FOREIGN KEY (colaborador_id) REFERENCES usuario(id),
    CONSTRAINT fk_eval_evaluador FOREIGN KEY (evaluador_id) REFERENCES usuario(id),
    CONSTRAINT fk_eval_asignacion FOREIGN KEY (asignacion_id) REFERENCES asignacion(id)
);
```

---

## 3. Horas Semanales por Habilidad en Creación de Proyecto (PM)

### Descripción
Anteriormente había un único campo global de "Horas semanales esperadas por colaborador". Ahora cada habilidad requerida tiene sus propias horas semanales específicas.

### Archivos modificados
**Modelo / DTO:**
- `src/main/java/com/pucp/skillb_ia/model/ProyectoHabilidadRequerida.java` — Añadido campo `horasSemanales (DECIMAL 5,2)`.
- `src/main/java/com/pucp/skillb_ia/dto/PmProyectoView.java` — `RequisitoHabilidad` incluye `horasSemanales`.
- `src/main/java/com/pucp/skillb_ia/dto/RmProyectoView.java` — `RequisitoTalento` incluye `horasSemanales`.

**Servicio:**
- `src/main/java/com/pucp/skillb_ia/service/pm/PmProyectoService.java` — Método `crear()` acepta `List<BigDecimal> horasSemanalesHab` y `MultipartFile documentoProyecto`. Al construir la vista, se propaga `horasSemanales` al DTO.
- `src/main/java/com/pucp/skillb_ia/service/rm/RmProyectoConsultaService.java` — Propaga `horasSemanales` al `RequisitoTalento`.

**Controlador:**
- `src/main/java/com/pucp/skillb_ia/controller/PmViewController.java` — Endpoint `POST /proyectos/crear` acepta `horasSemanalesHab` y `documentoProyecto`.

**Frontend:**
- `src/main/resources/templates/pm/pm-crear-proyecto.html` — Carrito de habilidades requeridas incluye columna "Horas/sem" por habilidad. Se eliminó el campo global de horas. Se añadió `enctype="multipart/form-data"` para soportar archivos.
- `src/main/resources/templates/rm/rm-revision-proyecto.html` — Cada requerimiento de habilidad muestra badge con horas/sem. Se añadió enlace para ver documento de contexto.
- `src/main/resources/templates/rm/rm-detalle-proyecto.html` — Idem anterior.

### Script SQL requerido
```sql
ALTER TABLE proyecto_habilidad_requerida ADD COLUMN horas_semanales DECIMAL(5,2);
```

---

## 4. Documento de Contexto de Proyecto (PM → RM)

### Descripción
El PM puede adjuntar un archivo (PDF, DOCX, etc.) al crear un proyecto para darle más contexto al RM sobre los requisitos del proyecto.

### Archivos modificados
**Modelo:**
- `src/main/java/com/pucp/skillb_ia/model/Proyecto.java` — Añadido campo `documentoContextoUrl (VARCHAR 500)`.

**Servicio:**
- `src/main/java/com/pucp/skillb_ia/service/pm/PmProyectoService.java` — Si se sube un archivo, lo guarda en S3 (o almacenamiento configurado) y guarda la URL en `Proyecto.documentoContextoUrl`.

**Frontend:**
- `src/main/resources/templates/pm/pm-crear-proyecto.html` — Input `type="file"` en la sección "Contexto para Resource Manager".
- `src/main/resources/templates/rm/rm-revision-proyecto.html` — Botón "Ver Documento" visible si hay URL adjunta.
- `src/main/resources/templates/rm/rm-detalle-proyecto.html` — Idem anterior.

### Script SQL requerido
```sql
ALTER TABLE proyecto ADD COLUMN documento_contexto_url VARCHAR(500);
```

---

## Resumen de Scripts SQL necesarios

```sql
-- Ejecutar en la base de datos (local o AWS) antes de arrancar la app

-- 1. Nueva tabla de evaluaciones
CREATE TABLE evaluacion (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    colaborador_id BIGINT NOT NULL,
    evaluador_id BIGINT NOT NULL,
    asignacion_id BIGINT,
    calificacion INT NOT NULL,
    comentarios VARCHAR(1000),
    fecha_creacion DATETIME NOT NULL,
    CONSTRAINT fk_eval_colaborador FOREIGN KEY (colaborador_id) REFERENCES usuario(id),
    CONSTRAINT fk_eval_evaluador FOREIGN KEY (evaluador_id) REFERENCES usuario(id),
    CONSTRAINT fk_eval_asignacion FOREIGN KEY (asignacion_id) REFERENCES asignacion(id)
);

-- 2. Horas semanales por habilidad requerida
ALTER TABLE proyecto_habilidad_requerida ADD COLUMN horas_semanales DECIMAL(5,2);

-- 3. URL de documento de contexto del proyecto
ALTER TABLE proyecto ADD COLUMN documento_contexto_url VARCHAR(500);
```
