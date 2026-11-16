package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.DniService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Registro del DNI desde el perfil, para las cuentas creadas antes de que se pidiera al activar la cuenta.
// Sirve para cualquier rol con sesión (lo protege anyRequest().authenticated() en SecurityConfig) y solo funciona
// una vez: si el usuario ya tiene DNI, no se cambia.
@Controller
public class PerfilDniController {

    private final DniService dniService;
    private final UsuarioRepository usuarioRepository;

    public PerfilDniController(DniService dniService, UsuarioRepository usuarioRepository) {
        this.dniService = dniService;
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping("/perfil/dni")
    public String registrarDni(@RequestParam(value = "dni", required = false) String dni,
                               @AuthenticationPrincipal UsuarioDetails principal,
                               HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        String volver = "redirect:" + GlobalExceptionHandler.paginaAnterior(request, "/") + "#dni";

        // Se lee de la base de datos (no de la sesión) para no registrar dos veces si abrió el perfil en dos pestañas.
        Usuario usuario = usuarioRepository.findById(principal.getUsuario().getId()).orElse(null);
        if (usuario == null) return "redirect:/login";
        if (usuario.getDni() != null && !usuario.getDni().isBlank()) {
            redirectAttributes.addFlashAttribute("dniError", "Tu DNI ya está registrado y no se puede cambiar.");
            return volver;
        }

        try {
            boolean verificado = dniService.validar(dni, usuario.getNombre() + " " + usuario.getApellido());
            dniService.registrar(usuario, dni, verificado);
            // Los perfiles del Admin y del PM muestran la copia del usuario guardada en la sesión: se actualiza y se
            // vuelve a guardar (mismo patrón que usa RmViewController al aprobar un proyecto).
            principal.getUsuario().setDni(usuario.getDni());
            principal.getUsuario().setDniVerificado(verificado);
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("dniGuardado", verificado
                    ? "DNI registrado y verificado."
                    : "DNI registrado. Quedó como \"sin verificar\" porque la consulta no estaba disponible.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("dniError", e.getMessage());
            redirectAttributes.addFlashAttribute("dniIngresado", dni);
        }
        return volver;
    }
}
