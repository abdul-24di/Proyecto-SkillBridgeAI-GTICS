package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.dto.ChatContactoView;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.rm.RmChatService;
import com.pucp.skillb_ia.service.rm.RmPerfilService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Pantalla del chat del RM con el Project Manager de cada proyecto ({@code /rm/chat}). Solo arma la vista;
 * los mensajes se leen y envían por AJAX con {@link RmChatRestController}. Los proyectos que se listan los
 * decide {@link RmChatService}.
 */
@Controller
@RequestMapping("/rm")
public class RmChatViewController {

    private final RmChatService rmChatService;
    private final RmPerfilService rmPerfilService;

    /** Crea el componente con sus dependencias, que Spring inyecta por el constructor. */
    public RmChatViewController(RmChatService rmChatService, RmPerfilService rmPerfilService) {
        this.rmChatService = rmChatService;
        this.rmPerfilService = rmPerfilService;
    }

    /**
     * RM de la barra superior, leído de la BD para que el nombre y la foto estén al día.
     *
     * @param principal usuario con la sesión iniciada
     * @return el RM, o {@code null} si no hay sesión
     */
    @ModelAttribute("rm")
    public Usuario rm(@AuthenticationPrincipal UsuarioDetails principal) {
        return principal != null ? rmPerfilService.obtenerPerfil(principal.getUsuario().getId()) : null;
    }

    /**
     * Muestra el chat de un proyecto del RM. Si el proyecto pedido no está entre los del RM (o no se indicó)
     * y el RM tiene proyectos, redirige al primero; sin proyectos, muestra la vista vacía.
     *
     * @param proyectoId proyecto cuyo chat se abre (opcional)
     * @param principal RM con la sesión iniciada
     * @param model modelo de la vista: proyectos, proyecto seleccionado y contacto del PM
     * @return la vista {@code rm/rm-chat} o la redirección al primer proyecto
     */
    @GetMapping({"/chat", "/rm-chat.html"})
    public String chat(@RequestParam(value = "proyectoId", required = false) Long proyectoId,
                       @AuthenticationPrincipal UsuarioDetails principal,
                       Model model) {
        Usuario rm = principal.getUsuario();
        List<Proyecto> proyectos = rmChatService.listarProyectosDelRm(rm);

        Proyecto seleccionado = null;
        for (Proyecto p : proyectos) {
            if (p.getId().equals(proyectoId)) {
                seleccionado = p;
                break;
            }
        }

        if (seleccionado == null && !proyectos.isEmpty()) {
            return "redirect:/rm/chat?proyectoId=" + proyectos.get(0).getId();
        }

        model.addAttribute("proyectos", proyectos);
        model.addAttribute("proyectoSeleccionado", seleccionado);
        model.addAttribute("pmContacto",
                seleccionado != null ? new ChatContactoView(seleccionado.getPm(), "Project Manager") : null);
        return "rm/rm-chat";
    }
}