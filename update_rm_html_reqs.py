import sys
import re

file_path = 'src/main/resources/templates/rm/rm-revision-proyecto.html'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Add document link to Info Grid
doc_html = """              <div><div class="info-label">Documento de Contexto</div><div class="info-value">
                <a th:if="${proyecto.proyecto.documentoContextoUrl != null}" th:href="${proyecto.proyecto.documentoContextoUrl}" target="_blank" class="btn btn-sm btn-outline-primary">Ver Documento</a>
                <span th:if="${proyecto.proyecto.documentoContextoUrl == null}" class="text-secondary">No adjunto</span>
              </div></div>"""

content = content.replace(
    'th:text="${proyecto.proyecto.presupuestoSolicitado != null ? \'S/ \' + #numbers.formatDecimal(proyecto.proyecto.presupuestoSolicitado, 1, \'COMMA\', 2, \'POINT\') : \'No solicitado\'}">No solicitado</div></div>\n            </div>',
    'th:text="${proyecto.proyecto.presupuestoSolicitado != null ? \'S/ \' + #numbers.formatDecimal(proyecto.proyecto.presupuestoSolicitado, 1, \'COMMA\', 2, \'POINT\') : \'No solicitado\'}">No solicitado</div></div>\n' + doc_html + '\n            </div>'
)

# Replace "Horas requeridas" summary card since it's no longer global
content = re.sub(
    r'<div class="col-sm-6 col-lg-3"><div class="card review-summary-card"><div class="card-body">\s*<div class="review-summary-label">Horas requeridas</div>.*?</div></div></div>',
    '',
    content,
    flags=re.DOTALL
)

# Update requirements list to show horas
req_html = """                <div class="requirement-tags">
                  <span class="badge bg-blue-lt" th:text="|Nivel ${requisito.nivel}|">Nivel</span>
                  <span class="badge bg-purple-lt" th:if="${requisito.horasSemanales != null}" th:text="|${requisito.horasSemanales} hrs/sem|">Horas</span>
                </div>"""

content = re.sub(
    r'<div class="requirement-tags"><span class="badge bg-blue-lt" th:text="\|Nivel \$\{requisito.nivel\}\|">Nivel</span></div>',
    req_html,
    content
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

# Update rm-detalle-proyecto.html as well for the Document Link
file_path2 = 'src/main/resources/templates/rm/rm-detalle-proyecto.html'
with open(file_path2, 'r', encoding='utf-8') as f:
    content2 = f.read()

content2 = content2.replace(
    'th:text="${proyecto.proyecto.presupuestoSolicitado != null ? \'S/ \' + #numbers.formatDecimal(proyecto.proyecto.presupuestoSolicitado, 1, \'COMMA\', 2, \'POINT\') : \'No solicitado\'}">No solicitado</div></div>\n                </div>',
    'th:text="${proyecto.proyecto.presupuestoSolicitado != null ? \'S/ \' + #numbers.formatDecimal(proyecto.proyecto.presupuestoSolicitado, 1, \'COMMA\', 2, \'POINT\') : \'No solicitado\'}">No solicitado</div></div>\n' + doc_html + '\n                </div>'
)

# And remove global hours summary card there if it exists
content2 = re.sub(
    r'<div class="col-sm-6 col-lg-3"><div class="card summary-card"><div class="card-body">\s*<div class="summary-label">Horas/sem</div>.*?</div></div></div>',
    '',
    content2,
    flags=re.DOTALL
)

content2 = re.sub(
    r'<div class="requirement-tags"><span class="badge bg-blue-lt" th:text="\|Nivel \$\{req.nivel\}\|">Nivel</span></div>',
    """                <div class="requirement-tags">
                  <span class="badge bg-blue-lt" th:text="|Nivel ${req.nivel}|">Nivel</span>
                  <span class="badge bg-purple-lt" th:if="${req.horasSemanales != null}" th:text="|${req.horasSemanales} hrs/sem|">Horas</span>
                </div>""",
    content2
)

with open(file_path2, 'w', encoding='utf-8') as f:
    f.write(content2)

