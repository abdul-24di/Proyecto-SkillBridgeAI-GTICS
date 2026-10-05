import re

file_path = 'src/main/resources/templates/pm/pm-crear-proyecto.html'
with open(file_path, 'r', encoding='utf-8') as f:
    html = f.read()

old_presupuesto = r'<div class="col-lg-6"><label class="form-label">Presupuesto solicitado</label><input type="number" step="0.01" min="0" name="presupuestoSolicitado" class="form-control" placeholder="0.00"></div>'
new_presupuesto = r'''<div class="col-lg-6">
    <label class="form-label">Presupuesto solicitado</label>
    <div class="input-group">
        <span class="input-group-text">$</span>
        <input type="text" id="presupuestoVisible" class="form-control" placeholder="0.00">
        <input type="hidden" name="presupuestoSolicitado" id="presupuestoReal">
    </div>
</div>'''

html = html.replace(old_presupuesto, new_presupuesto)

js_code = r'''
<script>
    document.addEventListener("DOMContentLoaded", function() {
        const presVis = document.getElementById('presupuestoVisible');
        const presReal = document.getElementById('presupuestoReal');
        if (presVis && presReal) {
            presVis.addEventListener('blur', function() {
                // Remove spaces and any non-numeric except dot/comma
                let val = this.value.replace(/\s+/g, '').replace(/[^0-9.,]/g, '');
                // Replace comma with dot for JS parsing
                val = val.replace(',', '.');
                
                if (val) {
                    let num = parseFloat(val);
                    if (!isNaN(num)) {
                        presReal.value = num.toFixed(2);
                        // Convert to es-ES (1.200,50) and replace dots with spaces (1 200,50)
                        this.value = num.toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).replace(/\./g, ' ');
                    }
                } else {
                    presReal.value = '';
                    this.value = '';
                }
            });

            presVis.addEventListener('focus', function() {
                if (presReal.value) {
                    this.value = presReal.value.replace('.', ',');
                }
            });
        }
    });
</script>
'''
html = html.replace('</body>', js_code + '\n</body>')

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(html)
