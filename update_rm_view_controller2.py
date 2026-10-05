import sys
import re

file_path = 'src/main/java/com/pucp/skillb_ia/controller/RmViewController.java'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Add import if not present
if 'import com.pucp.skillb_ia.service.EvaluacionService;' not in content:
    content = content.replace(
        'import com.pucp.skillb_ia.service.UsuarioService;',
        'import com.pucp.skillb_ia.service.UsuarioService;\nimport com.pucp.skillb_ia.service.EvaluacionService;'
    )

if 'private final EvaluacionService evaluacionService;' not in content:
    content = content.replace(
        'private final UsuarioService usuarioService;',
        'private final UsuarioService usuarioService;\n    private final EvaluacionService evaluacionService;'
    )
    
    content = content.replace(
        'RmForoService rmForoService,\n                              UsuarioService usuarioService)',
        'RmForoService rmForoService,\n                              UsuarioService usuarioService,\n                              EvaluacionService evaluacionService)'
    )
    
    content = content.replace(
        'this.usuarioService = usuarioService;\n    }',
        'this.usuarioService = usuarioService;\n        this.evaluacionService = evaluacionService;\n    }'
    )

content = content.replace(
    'model.addAttribute("sueldoColaborador", rmColaboradorConsultaService.mapaSueldosBase().get(colaboradorId));\n        } catch (IllegalArgumentException ex) {',
    'model.addAttribute("sueldoColaborador", rmColaboradorConsultaService.mapaSueldosBase().get(colaboradorId));\n            model.addAttribute("evaluaciones", evaluacionService.obtenerEvaluacionesPorColaborador(colaboradorId));\n        } catch (IllegalArgumentException ex) {'
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
