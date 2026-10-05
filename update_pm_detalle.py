import sys
import re

# PM DETALLE PROYECTO
file_path = 'src/main/resources/templates/pm/pm-detalle-proyecto.html'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

doc_html = """              <div><div class="info-label">Documento de Contexto</div><div class="info-value">
                <a th:if="${proyecto.proyecto.documentoContextoUrl != null}" th:href="${proyecto.proyecto.documentoContextoUrl}" target="_blank" class="btn btn-sm btn-outline-primary">Ver Documento</a>
                <span th:if="${proyecto.proyecto.documentoContextoUrl == null}" class="text-secondary">No adjunto</span>
              </div></div>"""

content = content.replace(
    'th:text="${proyecto.proyecto.presupuestoSolicitado != null ? \'S/ \' + #numbers.formatDecimal(proyecto.proyecto.presupuestoSolicitado, 1, \'COMMA\', 2, \'POINT\') : \'No solicitado\'}">No solicitado</div></div>\n            </div>',
    'th:text="${proyecto.proyecto.presupuestoSolicitado != null ? \'S/ \' + #numbers.formatDecimal(proyecto.proyecto.presupuestoSolicitado, 1, \'COMMA\', 2, \'POINT\') : \'No solicitado\'}">No solicitado</div></div>\n' + doc_html + '\n            </div>'
)

# And replace `Horas/sem` global if it exists in the top cards
content = re.sub(
    r'<div class="col-sm-6 col-md-3"><div class="card summary-card"><div class="card-body">\s*<div class="summary-label">Horas/sem</div>.*?</div></div></div>',
    '',
    content,
    flags=re.DOTALL
)

# And add `horasSemanales` to the requirements iteration
req_html = """                <div class="requirement-tags">
                  <span class="badge bg-blue-lt" th:text="|Nivel ${req.nivel}|">Nivel</span>
                  <span class="badge bg-purple-lt" th:if="${req.horasSemanales != null}" th:text="|${req.horasSemanales} hrs/sem|">Horas</span>
                </div>"""
content = re.sub(
    r'<div class="requirement-tags"><span class="badge bg-blue-lt" th:text="\|Nivel \$\{req.nivel\}\|">Nivel</span></div>',
    req_html,
    content
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
