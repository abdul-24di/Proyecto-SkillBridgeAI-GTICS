import sys
import re

file_path = 'src/main/java/com/pucp/skillb_ia/controller/RmViewController.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    'RmCursoService rmCursoService,\n            RmPresupuestoService rmPresupuestoService)',
    'RmCursoService rmCursoService,\n            RmPresupuestoService rmPresupuestoService,\n            EvaluacionService evaluacionService)'
)

content = content.replace(
    'this.rmPresupuestoService = rmPresupuestoService;\n    }',
    'this.rmPresupuestoService = rmPresupuestoService;\n        this.evaluacionService = evaluacionService;\n    }'
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
