import sys
import re

file_path = 'src/main/resources/templates/rm/rm-perfil-colaborador.html'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

eval_html = """
          <!-- Evaluaciones / Feedback -->
          <div class="card section-card h-100 mt-4">
            <div class="card-header section-header">
              <div>
                <h3 class="card-title mb-1">Historial de Desempeño (Evaluaciones)</h3>
                <div class="section-description">Calificaciones y feedback recibidos al finalizar proyectos.</div>
              </div>
            </div>
            <div th:if="${!#lists.isEmpty(evaluaciones)}">
              <div class="timeline-item" th:each="evaluacion : ${evaluaciones}">
                <div class="d-flex justify-content-between gap-2 flex-wrap">
                  <div class="fw-semibold">
                    <span th:if="${evaluacion.asignacion != null}" th:text="${evaluacion.asignacion.proyecto.nombre}">Proyecto</span>
                    <span th:if="${evaluacion.asignacion == null}">Actividad general</span>
                    <span class="ms-2 badge" th:classappend="${evaluacion.calificacion >= 4 ? 'bg-success-lt' : (evaluacion.calificacion == 3 ? 'bg-warning-lt' : 'bg-danger-lt')}">
                      <span th:text="${evaluacion.calificacion}">5</span> ⭐
                    </span>
                  </div>
                  <span class="small text-secondary" th:text="${#temporals.format(evaluacion.fechaCreacion, 'dd/MM/yyyy')}">Fecha</span>
                </div>
                <div class="text-secondary small mt-1">
                  Evaluado por: <span th:text="${evaluacion.evaluador.nombre + ' ' + evaluacion.evaluador.apellido + ' (' + evaluacion.evaluador.rol.nombre + ')'}">Evaluador</span>
                </div>
                <div class="small text-dark mt-2 p-2 bg-light rounded" th:text="${evaluacion.comentarios != null and !evaluacion.comentarios.isEmpty() ? evaluacion.comentarios : 'Sin comentarios'}">Comentario</div>
              </div>
            </div>
            <div class="empty-state" th:if="${#lists.isEmpty(evaluaciones)}">
              El colaborador aún no tiene evaluaciones registradas.
            </div>
          </div>
"""

# Insert it before the Educación card or right after Experiencia.
content = content.replace(
    '<div class="card section-card h-100">',
    '<div class="card section-card h-100 mt-4">',
    1 # Just replace the first occurrence if needed, or let's use a more specific regex.
)

# Wait, let's find the closing tag of the Experiencia card.
# The card has "Experiencia profesional". Let's inject it before "Educación" card.
content = content.replace(
    '<h3 class="card-title mb-1">Educacin</h3>',
    '<h3 class="card-title mb-1">Educación</h3>'
)

# It's better to replace just before `<div class="card section-card h-100">` of Educación.
content = re.sub(
    r'(<div class="card section-card h-100">\s*<div class="card-header section-header">\s*<div>\s*<h3 class="card-title mb-1">Educaci)',
    eval_html + r'\1',
    content
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
