import sys
import re

file_path = 'src/main/java/com/pucp/skillb_ia/service/rm/RmAsignacionService.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    'NotificacionService notificacionService) {',
    'NotificacionService notificacionService,\n            EvaluacionService evaluacionService) {'
)

content = content.replace(
    'this.notificacionService = notificacionService;\n    }',
    'this.notificacionService = notificacionService;\n        this.evaluacionService = evaluacionService;\n    }'
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
