package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.security.UsuarioDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Datos disponibles en todas las vistas. */
@ControllerAdvice
public class GlobalModelAdvice {

    /**
     * Deja al usuario de la sesión en el modelo, para la barra superior.
     *
     * @param principal usuario con la sesión iniciada
     * @return el usuario o {@code null} si no hay sesión
     */
    @ModelAttribute("usuarioActual")
    public Usuario usuarioActual(@AuthenticationPrincipal UsuarioDetails principal) {
        return principal != null ? principal.getUsuario() : null;
    }

    /**
     * Avisa que el archivo supera el tamaño máximo permitido.
     *
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @return redirección al perfil del colaborador
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String archivoDemasiadoGrande(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("mensajeError",
                "El archivo supera el máximo permitido de 10MB.");
        return "redirect:/colaborador/perfil";
    }
}
