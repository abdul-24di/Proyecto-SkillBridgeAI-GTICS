import sys
import re

file_path = 'src/main/resources/templates/pm/pm-asignaciones-proyecto.html'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Replace the form with a button that triggers a modal
form_regex = re.compile(r'<form method="POST" th:action="@\{/pm/asignaciones/\{id\}/finalizar\(id=\$\{asig\.asignacion\.id\}\)}">\s*<input type="hidden" name="proyectoId" th:value="\$\{proyecto\.proyecto\.id\}" />\s*<button type="submit" class="btn btn-outline-danger btn-sm"[^>]*>Finalizar</button>\s*</form>')

content = form_regex.sub(
    '<button type="button" class="btn btn-outline-danger btn-sm" data-bs-toggle="modal" th:data-bs-target="|#modalFinalizarAsig${asig.asignacion.id}|">Finalizar</button>',
    content
)

# Append the modal loop at the end before </body>
modal_html = """
<!-- Modals Finalizar Asignación -->
<th:block th:each="asig : ${colaboradores}">
    <div class="modal fade" th:id="|modalFinalizarAsig${asig.asignacion.id}|" tabindex="-1" aria-hidden="true">
        <div class="modal-dialog">
            <div class="modal-content">
                <form method="POST" th:action="@{/pm/asignaciones/{id}/finalizar(id=${asig.asignacion.id})}">
                    <input type="hidden" name="proyectoId" th:value="${proyecto.proyecto.id}" />
                    <div class="modal-header">
                        <h5 class="modal-title">Finalizar Asignación</h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                    </div>
                    <div class="modal-body">
                        <p>¿Estás seguro que deseas finalizar la participación de <strong th:text="${asig.asignacion.colaborador.nombre + ' ' + asig.asignacion.colaborador.apellido}"></strong> en este proyecto?</p>
                        
                        <label class="form-label mt-3">Calificación (Estrellas) <span class="text-secondary">(Opcional)</span></label>
                        <select name="calificacion" class="form-select mb-2">
                            <option value="">Sin calificar</option>
                            <option value="5">5 - Excelente</option>
                            <option value="4">4 - Bueno</option>
                            <option value="3">3 - Regular</option>
                            <option value="2">2 - Deficiente</option>
                            <option value="1">1 - Muy deficiente</option>
                        </select>
                        <label class="form-label mt-2">Feedback de desempeño <span class="text-secondary">(Opcional)</span></label>
                        <textarea name="feedback" maxlength="500" rows="3" class="form-control" placeholder="Escribe un comentario sobre el desempeño del colaborador..."></textarea>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-link link-secondary" data-bs-dismiss="modal">Cancelar</button>
                        <button type="submit" class="btn btn-danger">Confirmar finalización</button>
                    </div>
                </form>
            </div>
        </div>
    </div>
</th:block>
"""

content = content.replace('</body>', modal_html + '\n</body>')

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
