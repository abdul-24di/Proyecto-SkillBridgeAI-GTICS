import sys
import re

file_path = 'src/main/resources/templates/pm/pm-asignaciones-proyecto.html'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Replace the direct form submission button with a modal trigger
old_button_html = """              <form method="POST" th:action="@{/pm/asignaciones/{id}/finalizar(id=${asig.asignacion.id})}">
                <input type="hidden" name="proyectoId" th:value="${proyecto.id}">
                <button type="submit" class="btn btn-outline-danger btn-sm" onclick="return confirm('Finalizar asignacin?');">Finalizar</button>
              </form>"""

# Wait, the exact match is slightly different depending on what I have
content = re.sub(
    r'<form method="POST" th:action="@\{/pm/asignaciones/\{id\}/finalizar\(id=\$\{asig\.asignacion\.id\}\)}">\s*<input type="hidden" name="proyectoId" th:value="\$\{proyecto\.id\}">\s*<button type="submit" class="btn btn-outline-danger btn-sm" onclick="return confirm\([^)]+\);">Finalizar</button>\s*</form>',
    '<button type="button" class="btn btn-outline-danger btn-sm" data-bs-toggle="modal" th:data-bs-target="\'#modalFinalizarAsig\' + ${asig.asignacion.id}">Finalizar</button>',
    content
)

# Wait, if that regex doesn't match exactly, let's just do it directly.
