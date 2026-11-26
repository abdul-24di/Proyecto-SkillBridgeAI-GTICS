package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.DniVerificacionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Registro, corrección o nueva verificación del DNI desde el perfil. Sirve para cualquier rol con sesión (lo protege
// anyRequest().authenticated() en SecurityConfig). Un DNI "sin verificar" se puede corregir; uno verificado no.
@Controller
public class PerfilDniController {

    private final DniVerificacionService dniVerificacionService;

    public PerfilDniController(DniVerificacionService dniVerificacionService) {
        this.dniVerificacionService = dniVerificacionService;
    }

    @PostMapping("/perfil/dni")
    public String registrarDni(@RequestParam(value = "dni", required = false) String dni,
                               @AuthenticationPrincipal UsuarioDetails principal,
                               HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        String volver = "redirect:" + GlobalExceptionHandler.paginaAnterior(request, "/") + "#dni";

        // El servicio lee el usuario de la base de datos (no de la sesión): si ya está verificado, no se cambia.
        try {
            boolean verificado = dniVerificacionService.registrarDesdePerfil(principal.getUsuario().getId(), dni);
            // Los perfiles del Admin y del PM muestran la copia del usuario guardada en la sesión: se actualiza y se
            // vuelve a guardar (mismo patrón que usa RmViewController al aprobar un proyecto).
            dniVerificacionService.sincronizar(principal.getUsuario());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("dniGuardado", verificado
                    ? "DNI verificado."
                    : "DNI guardado. Quedó como \"sin verificar\" porque la consulta no estaba disponible; "
                    + "lo volveremos a intentar automáticamente.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("dniError", e.getMessage());
            redirectAttributes.addFlashAttribute("dniIngresado", dni);
        }
        return volver;
    }
}
