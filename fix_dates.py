import sys
import re

file_path = 'src/main/resources/templates/col/col-perfil.html'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Education Modal
content = content.replace(
    '<input type="date" name="fechaInicio" id="eduStartInput" class="form-control">',
    '<input type="date" name="fechaInicio" id="eduStartInput" class="form-control" onchange="document.getElementById(\'eduEndInput\').min = this.value;">'
)

# Experience Modal
content = content.replace(
    '<input type="date" class="form-control" name="fechaInicio">',
    '<input type="date" class="form-control" name="fechaInicio" id="expFechaInicio" onchange="document.getElementById(\'expFechaFin\').min = this.value;">'
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
