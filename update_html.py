import sys
import re

file_path = 'src/main/resources/templates/col/col-perfil.html'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = re.sub(
    r'Aún no tienes experiencia profesional registrada\. Esta información la ingresa el Administrador\.',
    r'Aún no tienes experiencia profesional registrada. Registra tus trabajos anteriores aquí.',
    content
)

content = re.sub(
    r'(<h3 class="card-title mb-1">Experiencia profesional</h3>\s*<div class="section-description">Historial laboral fuera de SkillBridge AI\.</div>\s*</div>)',
    r'\1\n                                <button type="button" class="btn btn-sm btn-light" data-bs-toggle="modal" data-bs-target="#addExperienciaModal">+ Agregar experiencia</button>',
    content
)

content = re.sub(
    r'(<div class="timeline-entry-desc" th:text="\$\{exp\.descripcion\}">Descripción</div>\s*</div>)',
    r'<div class="timeline-entry-desc" th:text="${exp.descripcion}">Descripción</div>\n                                    <form th:action="@{/colaborador/perfil/experiencia/{id}/eliminar(id=${exp.id})}" method="post" class="position-absolute" style="top: 15px; right: 20px;" onsubmit="return confirm(\'¿Estás seguro de eliminar esta experiencia profesional?\');">\n                                        <button type="submit" class="btn btn-sm btn-outline-danger border-0 p-1" title="Eliminar experiencia">\n                                            <i class="bi bi-trash"></i>\n                                        </button>\n                                    </form>\n                                </div>',
    content
)

content = re.sub(
    r'(<div th:each="exp : \$\{experienciaProfesional\}" class="timeline-entry)"',
    r'\1 position-relative"',
    content
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
