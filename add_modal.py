import sys
import re

file_path = 'src/main/resources/templates/col/col-perfil.html'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

modal_html = """
    <!-- Modal Agregar Experiencia -->
    <div class="modal fade" id="addExperienciaModal" tabindex="-1">
        <div class="modal-dialog">
            <div class="modal-content">
                <form th:action="@{/colaborador/perfil/experiencia}" method="post">
                    <div class="modal-header">
                        <h5 class="modal-title">Agregar Experiencia Profesional</h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                    </div>
                    <div class="modal-body">
                        <div class="mb-3">
                            <label class="form-label">Cargo <span class="text-danger">*</span></label>
                            <input type="text" class="form-control" name="cargo" required maxlength="100">
                        </div>
                        <div class="mb-3">
                            <label class="form-label">Empresa <span class="text-danger">*</span></label>
                            <input type="text" class="form-control" name="empresa" required maxlength="150">
                        </div>
                        <div class="row">
                            <div class="col-md-6 mb-3">
                                <label class="form-label">Fecha Inicio</label>
                                <input type="date" class="form-control" name="fechaInicio">
                            </div>
                            <div class="col-md-6 mb-3">
                                <label class="form-label">Fecha Fin</label>
                                <input type="date" class="form-control" name="fechaFin" id="expFechaFin">
                            </div>
                        </div>
                        <div class="mb-3 form-check">
                            <input type="checkbox" class="form-check-input" name="actual" id="expActual" onchange="document.getElementById('expFechaFin').disabled = this.checked; if(this.checked) document.getElementById('expFechaFin').value = '';">
                            <label class="form-check-label" for="expActual">Trabajo aquí actualmente</label>
                        </div>
                        <div class="mb-3">
                            <label class="form-label">Descripción <span class="text-danger">*</span></label>
                            <textarea class="form-control" name="descripcion" rows="3" required maxlength="500"></textarea>
                        </div>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancelar</button>
                        <button type="submit" class="btn btn-primary">Guardar</button>
                    </div>
                </form>
            </div>
        </div>
    </div>
"""

# Append the modal before the script tags or closing body tag
content = re.sub(r'(<script)', modal_html + r'\n\1', content, count=1)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
