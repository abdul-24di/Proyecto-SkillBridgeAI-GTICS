import sys
import re

file_path = 'src/main/resources/templates/pm/pm-crear-proyecto.html'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Make form multipart for file upload
content = content.replace('<form method="POST" th:action="@{/pm/proyectos/crear}">', '<form method="POST" th:action="@{/pm/proyectos/crear}" enctype="multipart/form-data">')

# Modify the cart inputs to include hours
cart_html = """
    <!-- Controles para agregar habilidad (como carrito) -->
    <div class="row g-2 mb-2 align-items-end">
      <div class="col-sm-3">
        <label class="form-label small text-secondary mb-1">Habilidad</label>
        <select id="habSelect" class="form-select">
           <option value="">Seleccione...</option>
           <option th:each="hab : ${habilidades}" th:value="${hab.id}" th:text="${hab.nombre}"></option>
        </select>
      </div>
      <div class="col-sm-3">
        <label class="form-label small text-secondary mb-1">Nivel</label>
        <select id="nivelSelect" class="form-select">
           <option value="BASICO">Básico</option>
           <option value="INTERMEDIO" selected>Intermedio</option>
           <option value="AVANZADO">Avanzado</option>
        </select>
      </div>
      <div class="col-sm-2">
        <label class="form-label small text-secondary mb-1">Cant.</label>
        <input type="number" id="cantSelect" class="form-control" value="1" min="1">
      </div>
      <div class="col-sm-2">
        <label class="form-label small text-secondary mb-1">Horas/sem</label>
        <input type="number" id="horasSelect" class="form-control" value="16" min="1" max="40">
      </div>
      <div class="col-sm-2">
        <button type="button" class="btn btn-primary w-100" onclick="addSkillToCart()">Añadir</button>
      </div>
    </div>
"""

# Find the old cart inputs
old_cart_regex = r'<!-- Controles para agregar habilidad \(como carrito\) -->.*?<div class="col-sm-2">\s*<button type="button" class="btn btn-primary w-100" onclick="addSkillToCart\(\)">A\xf1adir</button>\s*</div>\s*</div>'
content = re.sub(old_cart_regex, cart_html, content, flags=re.DOTALL)
# Also try with the regular 'Añadir' encoding
old_cart_regex2 = r'<!-- Controles para agregar habilidad \(como carrito\) -->.*?<div class="col-sm-2">\s*<button type="button" class="btn btn-primary w-100" onclick="addSkillToCart\(\)">Añadir</button>\s*</div>\s*</div>'
content = re.sub(old_cart_regex2, cart_html, content, flags=re.DOTALL)


# Update the JavaScript to include hours
js_old = """    function addSkillToCart() {
        const habSelect = document.getElementById('habSelect');
        const nivelSelect = document.getElementById('nivelSelect');
        const cantSelect = document.getElementById('cantSelect');"""

js_new = """    function addSkillToCart() {
        const habSelect = document.getElementById('habSelect');
        const nivelSelect = document.getElementById('nivelSelect');
        const cantSelect = document.getElementById('cantSelect');
        const horasSelect = document.getElementById('horasSelect');"""

content = content.replace(js_old, js_new)

js_old_2 = """        const habText = habSelect.options[habSelect.selectedIndex].text;
        const nivel = nivelSelect.value;
        const cant = cantSelect.value;"""

js_new_2 = """        const habText = habSelect.options[habSelect.selectedIndex].text;
        const nivel = nivelSelect.value;
        const cant = cantSelect.value;
        const horas = horasSelect.value;"""

content = content.replace(js_old_2, js_new_2)

js_old_badge = """                <span class="badge bg-secondary-lt ms-2">${cant} persona(s)</span>
            </div>
            <button type="button" class="btn-close\""""

js_new_badge = """                <span class="badge bg-secondary-lt ms-2">${cant} persona(s)</span>
                <span class="badge bg-purple-lt ms-2">${horas} hrs/sem</span>
            </div>
            <button type="button" class="btn-close\""""

content = content.replace(js_old_badge, js_new_badge)

js_old_hidden = """            <input type="hidden" name="nivelesRequeridos" value="${nivel}">
            <input type="hidden" name="cantidadesPersonas" value="${cant}">
        `;"""

js_new_hidden = """            <input type="hidden" name="nivelesRequeridos" value="${nivel}">
            <input type="hidden" name="cantidadesPersonas" value="${cant}">
            <input type="hidden" name="horasSemanalesHab" value="${horas}">
        `;"""

content = content.replace(js_old_hidden, js_new_hidden)

# Remove the old horasSemanalesRequeridas input globally and add the File upload
old_context = """<div class="mb-3"><label class="form-label">Horas semanales esperadas por colaborador</label><input type="number" name="horasSemanalesRequeridas" min="1" max="40" class="form-control" value="16"><div class="form-hint">Horas propuestas de asignaci\xf3n; no son disponibilidad ni carga.</div></div>"""
new_context = """
<div class="mb-3">
  <label class="form-label">Documento de Contexto / Requisitos (Opcional)</label>
  <input type="file" name="documentoProyecto" class="form-control">
  <div class="form-hint">Sube un archivo (PDF, DOCX) con más detalle del proyecto para el RM.</div>
</div>
"""
content = content.replace(old_context, new_context)

old_context2 = """<div class="mb-3"><label class="form-label">Horas semanales esperadas por colaborador</label><input type="number" name="horasSemanalesRequeridas" min="1" max="40" class="form-control" value="16"><div class="form-hint">Horas propuestas de asignación; no son disponibilidad ni carga.</div></div>"""
content = content.replace(old_context2, new_context)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

