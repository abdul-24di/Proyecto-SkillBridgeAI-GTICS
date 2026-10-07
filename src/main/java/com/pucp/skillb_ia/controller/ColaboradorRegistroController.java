package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.col.ColaboradorPerfilService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Pantalla "Registro en revisión" del pre-registro (ver RegistroPendienteInterceptor): muestra el
// estado y deja subir un CV nuevo, p. ej. cuando el Admin rechazó el anterior.
@Controller
@RequestMapping("/colaborador/registro")
public class ColaboradorRegistroController {

    private final UsuarioRepository usuarioRepository;
    private final ColaboradorPerfilService colaboradorPerfilService;

    public ColaboradorRegistroController(UsuarioRepository usuarioRepository,
                                         ColaboradorPerfilService colaboradorPerfilService) {
        this.usuarioRepository = usuarioRepository;
        this.colaboradorPerfilService = colaboradorPerfilService;
    }

    @GetMapping
    public String registro(@AuthenticationPrincipal UsuarioDetails principal, Model model) {
        model.addAttribute("usuario", recargar(principal));
        return "col/col-registro";
    }

    @PostMapping("/cv")
    public String subirCv(@RequestParam("cv") MultipartFile cv,
                          @AuthenticationPrincipal UsuarioDetails principal,
                          RedirectAttributes ra) {
        try {
            colaboradorPerfilService.actualizarCv(recargar(principal), cv);
            ra.addFlashAttribute("mensajeOk", "Tu CV se envió. El Administrador lo revisará pronto.");
        } catch (Exception e) {
            ra.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/colaborador/registro";
    }

    private Usuario recargar(UsuarioDetails principal) {
        return usuarioRepository.findById(principal.getUsuario().getId()).orElseThrow();
    }
}
