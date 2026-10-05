import re

file_path = 'src/main/resources/templates/pm/pm-crear-proyecto.html'
with open(file_path, 'r', encoding='utf-8') as f:
    html = f.read()

# Fix the Thymeleaf variables that were erased
html = html.replace('<option th:each="hab : " th:value="" th:text=""></option>', '<option th:each="hab : ${habilidades}" th:value="${hab.id}" th:text="${hab.nombre}"></option>')

# Check if addSkillToCart is there, if not, add it before </body>
if 'function addSkillToCart' not in html:
    js_code = """
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
        row.innerHTML = `
            <div>
                <strong>${habText}</strong> 
                <span class="badge bg-blue-lt ms-2">${nivel}</span>
                <span class="badge bg-secondary-lt ms-2">${cant} persona(s)</span>
            </div>
            <button type="button" class="btn-close" aria-label="Eliminar" onclick="removeSkill('${uniqueId}')"></button>
        `;
        cart.appendChild(row);
        
        // 2. Añadir inputs ocultos para el form submit
        const hiddenRow = document.createElement('div');
        hiddenRow.id = 'hidden-' + uniqueId;
        hiddenRow.innerHTML = `
            <input type="hidden" name="habilidadIds" value="${habId}">
            <input type="hidden" name="nivelesRequeridos" value="${nivel}">
            <input type="hidden" name="cantidadesPersonas" value="${cant}">
        `;
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
"""
    html = html.replace('</body>', js_code + '\n</body>')

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(html)
