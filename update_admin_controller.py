import sys
import re

file_path = 'src/main/java/com/pucp/skillb_ia/controller/AdminViewController.java'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Add import
content = content.replace(
    'import com.pucp.skillb_ia.service.admin.AdminCargoService;',
    'import com.pucp.skillb_ia.service.admin.AdminCargoService;\nimport com.pucp.skillb_ia.service.AdminCursoService;\nimport com.pucp.skillb_ia.model.Curso;'
)

# Add service field
content = content.replace(
    'private final AdminPerfilService adminPerfilService;',
    'private final AdminPerfilService adminPerfilService;\n    private final AdminCursoService adminCursoService;'
)

# Modify constructor signature
content = content.replace(
    'AdminPerfilService adminPerfilService,\n                                AdminCargoService adminCargoService)',
    'AdminPerfilService adminPerfilService,\n                                AdminCargoService adminCargoService,\n                                AdminCursoService adminCursoService)'
)

# Modify constructor assignment
content = content.replace(
    'this.adminPerfilService = adminPerfilService;\n    }',
    'this.adminPerfilService = adminPerfilService;\n        this.adminCursoService = adminCursoService;\n    }'
)

# Append new endpoints at the end of the class, before the last '}'
endpoints = """
    // ================== CURSOS ==================
    @GetMapping({"/cursos", "/admin-cursos.html"})
    public String cursos(Model model) {
        model.addAttribute("cursos", adminCursoService.listarTodos());
        model.addAttribute("modalidades", com.pucp.skillb_ia.model.enums.ModalidadCurso.values());
        return "admin/admin-cursos";
    }

    @PostMapping("/cursos/crear")
    public String crearCurso(@ModelAttribute Curso curso,
                             @AuthenticationPrincipal UsuarioDetails principal,
                             RedirectAttributes ra) {
        try {
            adminCursoService.crear(curso, principal.getUsuario());
            ra.addFlashAttribute("mensajeOk", "Se creó el curso exitosamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/cursos";
    }

    @PostMapping("/cursos/editar")
    public String editarCurso(@RequestParam("cursoId") Long cursoId,
                              @ModelAttribute Curso curso,
                              @AuthenticationPrincipal UsuarioDetails principal,
                              RedirectAttributes ra) {
        try {
            adminCursoService.editar(cursoId, curso, principal.getUsuario());
            ra.addFlashAttribute("mensajeOk", "Se guardaron los cambios del curso.");
        } catch (Exception e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/cursos";
    }

    @PostMapping("/cursos/alternar")
    public String alternarEstadoCurso(@RequestParam("cursoId") Long cursoId,
                                      @AuthenticationPrincipal UsuarioDetails principal,
                                      RedirectAttributes ra) {
        try {
            adminCursoService.alternarEstado(cursoId, principal.getUsuario());
            ra.addFlashAttribute("mensajeOk", "El estado del curso ha sido actualizado.");
        } catch (Exception e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/admin/cursos";
    }
}
"""

content = re.sub(r'}\s*$', endpoints, content)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
