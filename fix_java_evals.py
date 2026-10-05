import sys
import re

# FIX PM ASIGNACION SERVICE
file_path = 'src/main/java/com/pucp/skillb_ia/service/pm/PmAsignacionService.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

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
        asignacionRepository.save(asignacion);
        if (calificacion != null) {
            evaluacionService.evaluarAsignacion(asignacion.getId(), calificacion, feedback, pm);
        }
"""
content = content.replace(
    '        asignacion.setMotivoFinalizacion(\n                com.pucp.skillb_ia.model.enums.MotivoFinalizacion.OTRO);\n\n        asignacionRepository.save(asignacion);',
    '        asignacion.setMotivoFinalizacion(\n                com.pucp.skillb_ia.model.enums.MotivoFinalizacion.OTRO);\n' + evaluation_call
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

# FIX COLABORADOR VIEW CONTROLLER
file_path2 = 'src/main/java/com/pucp/skillb_ia/controller/ColaboradorViewController.java'
with open(file_path2, 'r', encoding='utf-8') as f:
    content2 = f.read()

content2 = content2.replace(
    'import com.pucp.skillb_ia.service.ColaboradorPerfilService;',
    'import com.pucp.skillb_ia.service.ColaboradorPerfilService;\nimport com.pucp.skillb_ia.service.EvaluacionService;'
)

content2 = content2.replace(
    'private final ColaboradorPerfilService colaboradorPerfilService;',
    'private final ColaboradorPerfilService colaboradorPerfilService;\n    private final EvaluacionService evaluacionService;'
)

content2 = content2.replace(
    'ColaboradorForoService colaboradorForoService,\n                                      ColaboradorPerfilService colaboradorPerfilService)',
    'ColaboradorForoService colaboradorForoService,\n                                      ColaboradorPerfilService colaboradorPerfilService,\n                                      EvaluacionService evaluacionService)'
)

content2 = content2.replace(
    'this.colaboradorPerfilService = colaboradorPerfilService;\n    }',
    'this.colaboradorPerfilService = colaboradorPerfilService;\n        this.evaluacionService = evaluacionService;\n    }'
)

content2 = content2.replace(
    'model.addAttribute("experiencia", new com.pucp.skillb_ia.model.ExperienciaProfesional());',
    'model.addAttribute("experiencia", new com.pucp.skillb_ia.model.ExperienciaProfesional());\n        model.addAttribute("evaluaciones", evaluacionService.obtenerEvaluacionesPorColaborador(principal.getUsuario().getId()));'
)

with open(file_path2, 'w', encoding='utf-8') as f:
    f.write(content2)

