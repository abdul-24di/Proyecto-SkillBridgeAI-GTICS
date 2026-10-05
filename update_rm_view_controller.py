import sys
import re

file_path = 'src/main/java/com/pucp/skillb_ia/controller/RmViewController.java'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    '@RequestParam(name = "observacion", required = false) String observacion,\n            @AuthenticationPrincipal UsuarioDetails principal,',
    '@RequestParam(name = "observacion", required = false) String observacion,\n            @RequestParam(name = "calificacion", required = false) Integer calificacion,\n            @RequestParam(name = "feedback", required = false) String feedback,\n            @AuthenticationPrincipal UsuarioDetails principal,'
)

content = content.replace(
    'rmAsignacionService.finalizar(\n                    asignacionId, motivo, observacion, principal.getUsuario().getId());',
    'rmAsignacionService.finalizar(\n                    asignacionId, motivo, observacion, calificacion, feedback, principal.getUsuario().getId());'
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
