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


@Controller
@RequestMapping("/rm")
public class RmChatViewController {

    private final RmChatService rmChatService;
    private final RmPerfilService rmPerfilService;

    public RmChatViewController(RmChatService rmChatService, RmPerfilService rmPerfilService) {
        this.rmChatService = rmChatService;
        this.rmPerfilService = rmPerfilService;
    }


    @ModelAttribute("rm")
    public Usuario rm(@AuthenticationPrincipal UsuarioDetails principal) {
        return principal != null ? rmPerfilService.obtenerPerfil(principal.getUsuario().getId()) : null;
    }

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