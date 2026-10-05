import sys
import re

file_path = 'src/main/resources/templates/admin/admin-cursos.html'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    'th:text="|${#numbers.formatDecimal(curso.horas, 1, 2)} h|"',
    'th:text="${curso.horas != null ? curso.horas + \' h\' : \'0 h\'}"'
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
