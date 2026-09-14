package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.security.UsuarioDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class GlobalModelAdvice {

    @ModelAttribute("usuarioActual")
    public Usuario usuarioActual(@AuthenticationPrincipal UsuarioDetails principal) {
        return principal != null ? principal.getUsuario() : null;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String archivoDemasiadoGrande(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("mensajeError",
                "El archivo supera el máximo permitido de 10MB.");
        return "redirect:/colaborador/perfil";
    }
}
