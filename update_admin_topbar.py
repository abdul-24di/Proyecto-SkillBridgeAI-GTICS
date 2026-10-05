import sys
import re

file_path = 'src/main/resources/templates/fragments/admin-topbar.html'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    '<a href="../admin/admin-habilidades.html"\n         th:classappend="${activeSection == \'habilidades\'} ? \'active\' : \'\'" th:href="@{/admin/habilidades}">Habilidades</a>',
    '<a href="../admin/admin-habilidades.html"\n         th:classappend="${activeSection == \'habilidades\'} ? \'active\' : \'\'" th:href="@{/admin/habilidades}">Habilidades</a>\n      <a href="../admin/admin-cursos.html"\n         th:classappend="${activeSection == \'cursos\'} ? \'active\' : \'\'" th:href="@{/admin/cursos}">Cursos</a>'
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
