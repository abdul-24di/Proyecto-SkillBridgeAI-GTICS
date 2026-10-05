import sys
import re

file_path = 'src/main/java/com/pucp/skillb_ia/service/rm/RmAsignacionService.java'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Add import
content = content.replace(
    'import com.pucp.skillb_ia.service.NotificacionService;',
    'import com.pucp.skillb_ia.service.NotificacionService;\nimport com.pucp.skillb_ia.service.EvaluacionService;'
)

# Add EvaluacionService dependency
content = content.replace(
    'private final NotificacionService notificacionService;',
    'private final NotificacionService notificacionService;\n    private final EvaluacionService evaluacionService;'
)

content = content.replace(
    'DisponibilidadService disponibilidadService,\n                              NotificacionService notificacionService)',
    'DisponibilidadService disponibilidadService,\n                              NotificacionService notificacionService,\n                              EvaluacionService evaluacionService)'
)

content = content.replace(
    'this.notificacionService = notificacionService;\n    }',
    'this.notificacionService = notificacionService;\n        this.evaluacionService = evaluacionService;\n    }'
)

# Update finalizar method signature
content = content.replace(
    'public void finalizar(\n            Long asignacionId,\n            MotivoFinalizacion motivo,\n            String observacion,\n            Long rmId) {',
    'public void finalizar(\n            Long asignacionId,\n            MotivoFinalizacion motivo,\n            String observacion,\n            Integer calificacion,\n            String feedback,\n            Long rmId) {'
)

# Add evaluacionService call
evaluation_call = """
        // Si se provee una calificación, registramos la evaluación
        if (calificacion != null) {
            evaluacionService.evaluarAsignacion(asignacion.getId(), calificacion, feedback, rm);
        }
"""
content = content.replace(
    'asignacion.setFechaFinalizacion(LocalDateTime.now());\n        asignacionRepository.save(asignacion);',
    'asignacion.setFechaFinalizacion(LocalDateTime.now());\n        asignacionRepository.save(asignacion);\n' + evaluation_call
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
