import sys
import re

file_path = 'src/main/java/com/pucp/skillb_ia/controller/PmViewController.java'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    'public String finalizarAsignacion(@PathVariable("id") Long asignacionId,\n                                      @RequestParam("proyectoId") Long proyectoId,\n                                      @AuthenticationPrincipal UsuarioDetails principal,\n                                      RedirectAttributes ra) {',
    'public String finalizarAsignacion(@PathVariable("id") Long asignacionId,\n                                      @RequestParam("proyectoId") Long proyectoId,\n                                      @RequestParam(name = "calificacion", required = false) Integer calificacion,\n                                      @RequestParam(name = "feedback", required = false) String feedback,\n                                      @AuthenticationPrincipal UsuarioDetails principal,\n                                      RedirectAttributes ra) {'
)

content = content.replace(
    'pmAsignacionService.finalizar(asignacionId, principal.getUsuario());',
    'pmAsignacionService.finalizar(asignacionId, calificacion, feedback, principal.getUsuario());'
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
