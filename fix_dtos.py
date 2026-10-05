import sys

# PM PROYECTO SERVICE
file_path = 'src/main/java/com/pucp/skillb_ia/service/pm/PmProyectoService.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    'h.getCantidadPersonas()))',
    'h.getCantidadPersonas(),\n                          h.getHorasSemanales()))'
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

# RM PROYECTO CONSULTA SERVICE
file_path2 = 'src/main/java/com/pucp/skillb_ia/service/rm/RmProyectoConsultaService.java'
with open(file_path2, 'r', encoding='utf-8') as f:
    content2 = f.read()

content2 = content2.replace(
    'r.getCantidadPersonas()))',
    'r.getCantidadPersonas(),\n                          r.getHorasSemanales()))'
)

with open(file_path2, 'w', encoding='utf-8') as f:
    f.write(content2)
