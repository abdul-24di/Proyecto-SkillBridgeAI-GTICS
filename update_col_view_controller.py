import sys
import re

file_path = 'src/main/java/com/pucp/skillb_ia/controller/ColaboradorViewController.java'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Add import
if 'import com.pucp.skillb_ia.service.EvaluacionService;' not in content:
    content = content.replace(
        'import com.pucp.skillb_ia.service.ColaboradorPerfilService;',
        'import com.pucp.skillb_ia.service.ColaboradorPerfilService;\nimport com.pucp.skillb_ia.service.EvaluacionService;'
    )

if 'private final EvaluacionService evaluacionService;' not in content:
    content = content.replace(
        'private final ColaboradorPerfilService colaboradorPerfilService;',
        'private final ColaboradorPerfilService colaboradorPerfilService;\n    private final EvaluacionService evaluacionService;'
    )
    
    content = content.replace(
        'ColaboradorForoService colaboradorForoService,\n                                      ColaboradorPerfilService colaboradorPerfilService)',
        'ColaboradorForoService colaboradorForoService,\n                                      ColaboradorPerfilService colaboradorPerfilService,\n                                      EvaluacionService evaluacionService)'
    )
    
    content = content.replace(
        'this.colaboradorPerfilService = colaboradorPerfilService;\n    }',
        'this.colaboradorPerfilService = colaboradorPerfilService;\n        this.evaluacionService = evaluacionService;\n    }'
    )

# Add to profile GET method
content = content.replace(
    'model.addAttribute("experiencia", new com.pucp.skillb_ia.model.ExperienciaProfesional());',
    'model.addAttribute("experiencia", new com.pucp.skillb_ia.model.ExperienciaProfesional());\n        model.addAttribute("evaluaciones", evaluacionService.obtenerEvaluacionesPorColaborador(principal.getUsuario().getId()));'
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
