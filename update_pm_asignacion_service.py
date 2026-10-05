import sys
import re

file_path = 'src/main/java/com/pucp/skillb_ia/service/pm/PmAsignacionService.java'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Add EvaluacionService import
content = content.replace(
    'import com.pucp.skillb_ia.service.NotificacionService;',
    'import com.pucp.skillb_ia.service.NotificacionService;\nimport com.pucp.skillb_ia.service.EvaluacionService;'
)

content = content.replace(
    'private final NotificacionService notificacionService;',
    'private final NotificacionService notificacionService;\n    private final EvaluacionService evaluacionService;'
)

content = content.replace(
    'DisponibilidadService disponibilidadService,\n                               NotificacionService notificacionService)',
    'DisponibilidadService disponibilidadService,\n                               NotificacionService notificacionService,\n                               EvaluacionService evaluacionService)'
)

content = content.replace(
    'this.notificacionService = notificacionService;\n    }',
    'this.notificacionService = notificacionService;\n        this.evaluacionService = evaluacionService;\n    }'
)

content = content.replace(
    'public void finalizar(Long asignacionId, Usuario pm) {',
    'public void finalizar(Long asignacionId, Integer calificacion, String feedback, Usuario pm) {'
)

evaluation_call = """
        if (calificacion != null) {
            evaluacionService.evaluarAsignacion(asignacion.getId(), calificacion, feedback, pm);
        }
"""
content = content.replace(
    'asignacionRepository.save(asignacion);',
    'asignacionRepository.save(asignacion);\n' + evaluation_call
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
