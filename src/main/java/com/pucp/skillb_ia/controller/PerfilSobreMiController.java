package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.PerfilSobreMiService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * "Sobre mí" en el perfil del Administrador, el PM y el RM. Cada ruta cuelga de /admin, /pm o /rm, así que
 * SecurityConfig ya exige el rol correspondiente.
 */
@Controller
public class PerfilSobreMiController {

    private final PerfilSobreMiService perfilSobreMiService;

    /** Crea el componente con sus dependencias, que Spring inyecta por el constructor. */
    public PerfilSobreMiController(PerfilSobreMiService perfilSobreMiService) {
        this.perfilSobreMiService = perfilSobreMiService;
    }

    /**
     * Guarda la descripción del perfil y actualiza la copia del usuario en la sesión.
     *
     * @param area área del rol: {@code admin}, {@code pm} o {@code rm}
     * @param descripcion descripción; puede ser {@code null} o vacía
     * @param principal usuario con la sesión iniciada
     * @param request petición HTTP
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @return redirección a la sección "Sobre mí" del perfil
     */
    @PostMapping("/{area:admin|pm|rm}/perfil/sobre-mi")
    public String actualizar(@PathVariable String area,
                             @RequestParam(value = "descripcion", required = false) String descripcion,
                             @AuthenticationPrincipal UsuarioDetails principal,
                             HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            String guardada = perfilSobreMiService.actualizar(principal.getUsuario().getId(), descripcion);
            // Los perfiles del Admin y del PM muestran la copia del usuario guardada en la sesión.
            principal.getUsuario().setDescripcion(guardada);
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("sobreMiGuardado", "Tu descripción se actualizó correctamente.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("sobreMiError", e.getMessage());
            redirectAttributes.addFlashAttribute("sobreMiIngresado", descripcion);
        }
        return "redirect:/" + area + "/perfil#sobre-mi";
    }
}
