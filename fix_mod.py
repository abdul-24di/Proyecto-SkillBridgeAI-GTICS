import sys
import re

file_path = 'src/main/resources/templates/admin/admin-cursos.html'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('th:each="mod : ${modalidades}"', 'th:each="m : ${modalidades}"')
content = content.replace('th:value="${mod}"', 'th:value="${m}"')
content = content.replace('th:text="${mod}"', 'th:text="${m}"')
content = content.replace('th:selected="${mod == curso.modalidad}"', 'th:selected="${m == curso.modalidad}"')

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
