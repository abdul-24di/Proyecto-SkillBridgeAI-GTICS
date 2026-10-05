import re

file_path = 'src/main/resources/templates/pm/pm-crear-proyecto.html'
with open(file_path, 'r', encoding='utf-8') as f:
    html = f.read()

new_skills_html = r'''
  <div class="mb-3">
    <label class="form-label">Habilidades Requeridas</label>
    
    <!-- Controles para agregar habilidad (como carrito) -->
    <div class="row g-2 mb-2 align-items-end">
      <div class="col-sm-5">
        <label class="form-label small text-secondary mb-1">Habilidad</label>
        <select id="habSelect" class="form-select">
           <option value="">Seleccione...</option>
           <option th:each="hab : " th:value="" th:text=""></option>
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
        <button type="button" class="btn btn-primary w-100" onclick="addSkillToCart()">Añadir</button>
      </div>
    </div>
    
    <!-- Carrito visual de habilidades seleccionadas -->
    <div id="skills-cart" class="d-flex flex-column gap-2 mb-3 mt-3">
      <!-- Aquí se agregarán visualmente las habilidades -->
    </div>
    
    <!-- Contenedor oculto para mandar los arrays al backend -->
    <div id="hidden-inputs-container"></div>
    
  </div>
  <div class="mb-3">
    <label class="form-label">¿Falta alguna habilidad en la lista?</label>
    <textarea name="habilidadesExtra" class="form-control" rows="2" placeholder="Escribe aquí las habilidades que no encontraste (Ej: Rust, GraphQL...)"></textarea>
    <div class="form-hint">Se agregarán automáticamente a la descripción del proyecto.</div>
  </div>
'''

old_pattern = re.compile(r'<div class="mb-3">\s*<label class="form-label">Habilidad Requerida \(Ejemplo\)</label>.*?</select>\s*<div class="form-hint">Selecciona la habilidad principal requerida.</div>\s*</div>', re.DOTALL)
html = old_pattern.sub(new_skills_html, html)

# Solo añado el JS si no está ya
if 'addSkillToCart()' not in html:
    js_code = r'''
    <script>
        function addSkillToCart() {
            const habSelect = document.getElementById('habSelect');
            const nivelSelect = document.getElementById('nivelSelect');
            const cantSelect = document.getElementById('cantSelect');
            
            const habId = habSelect.value;
            const habText = habSelect.options[habSelect.selectedIndex].text;
            const nivel = nivelSelect.value;
            const cant = cantSelect.value;
            
            if (!habId) {
                alert("Por favor selecciona una habilidad primero.");
                return;
            }
            
            const cart = document.getElementById('skills-cart');
            const hiddenContainer = document.getElementById('hidden-inputs-container');
            
            const uniqueId = 'skill-' + Date.now();
            
            // 1. Añadir badge visual
            const row = document.createElement('div');
            row.id = uniqueId;
            row.className = 'd-flex align-items-center justify-content-between p-2 border rounded bg-light';
            row.innerHTML = 
                <div>
                    <strong></strong> 
                    <span class="badge bg-blue-lt ms-2"></span>
                    <span class="badge bg-secondary-lt ms-2"> persona(s)</span>
                </div>
                <button type="button" class="btn-close" aria-label="Eliminar" onclick="removeSkill('')"></button>
            ;
            cart.appendChild(row);
            
            // 2. Añadir inputs ocultos para el form submit
            const hiddenRow = document.createElement('div');
            hiddenRow.id = 'hidden-' + uniqueId;
            hiddenRow.innerHTML = 
                <input type="hidden" name="habilidadIds" value="">
                <input type="hidden" name="nivelesRequeridos" value="">
                <input type="hidden" name="cantidadesPersonas" value="">
            ;
            hiddenContainer.appendChild(hiddenRow);
            
            // 3. Limpiar selects
            habSelect.value = "";
            nivelSelect.value = "INTERMEDIO";
            cantSelect.value = "1";
        }
        
        function removeSkill(id) {
            const visualRow = document.getElementById(id);
            const hiddenRow = document.getElementById('hidden-' + id);
            if (visualRow) visualRow.remove();
            if (hiddenRow) hiddenRow.remove();
        }
    </script>
    '''
    html = re.sub(r'(</script>\s*</body>)', rf'{js_code}\1', html)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(html)
