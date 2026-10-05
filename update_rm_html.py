import sys
import re

file_path = 'src/main/resources/templates/rm/rm-detalle-asignacion-activa.html'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

eval_html = """
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
"""

content = content.replace(
    '<label for="observacion" class="form-label mt-3">Observacin</label>',
    eval_html + '<label for="observacion" class="form-label mt-3">Observacin Interna (RM)</label>'
)
# Re-replace encoding issue if needed
content = content.replace(
    '<label for="observacion" class="form-label mt-3">Observación</label>',
    eval_html + '<label for="observacion" class="form-label mt-3">Observación Interna (RM)</label>'
)
content = content.replace(
    '<label for="observacion" class="form-label mt-3">Observaci\u00f3n</label>',
    eval_html + '<label for="observacion" class="form-label mt-3">Observación Interna (RM)</label>'
)


with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
