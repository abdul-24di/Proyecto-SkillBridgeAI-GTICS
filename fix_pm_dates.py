import sys
import re

file_path = 'src/main/resources/templates/pm/pm-crear-proyecto.html'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    'id="fechaInicio" class="form-control" required th:min="${#temporals.format(#temporals.createNow(), \'yyyy-MM-dd\')}"',
    'id="fechaInicio" class="form-control" required th:min="${#temporals.format(#temporals.createNow(), \'yyyy-MM-dd\')}" onchange="document.getElementById(\'fechaFinEstimada\').min = this.value;"'
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
