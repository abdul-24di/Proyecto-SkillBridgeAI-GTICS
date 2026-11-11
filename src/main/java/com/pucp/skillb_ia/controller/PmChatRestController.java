package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.dto.ChatContactoView;
import com.pucp.skillb_ia.dto.PmMensajeView;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.pm.PmChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/pm/api/chat")
public class PmChatRestController {

    private final PmChatService pmChatService;

    public PmChatRestController(PmChatService pmChatService) {
        this.pmChatService = pmChatService;
    }

    // Colaboradores activos del proyecto
    @GetMapping("/companeros")
    public ResponseEntity<List<ChatContactoView>> getCompaneros(@RequestParam("proyectoId") Long proyectoId,
                                                                @AuthenticationPrincipal UsuarioDetails principal) {
        Usuario pm = principal.getUsuario();
        return ResponseEntity.ok(pmChatService.listarContactos(proyectoId, pm));
    }


    @GetMapping("/noleidos")
    public ResponseEntity<Map<String, Long>> getNoLeidos(@RequestParam("proyectoId") Long proyectoId,
                                                         @AuthenticationPrincipal UsuarioDetails principal) {
        Usuario pm = principal.getUsuario();
        return ResponseEntity.ok(pmChatService.obtenerNoLeidos(proyectoId, pm));
    }


    @GetMapping("/mensajes")
    public ResponseEntity<List<PmMensajeView>> getMensajes(@RequestParam("proyectoId") Long proyectoId,
                                                           @RequestParam(value = "usuarioId", required = false) Long usuarioId,
                                                           @AuthenticationPrincipal UsuarioDetails principal) {
        Usuario pm = principal.getUsuario();
        List<PmMensajeView> mensajes;
        if (usuarioId == null) {
            mensajes = pmChatService.obtenerMensajes(proyectoId, pm);
        } else {
            mensajes = pmChatService.obtenerMensajesPrivados(proyectoId, usuarioId, pm);
        }
        return ResponseEntity.ok(mensajes);
    }

    @PostMapping("/enviar")
    public ResponseEntity<PmMensajeView> enviarMensaje(@RequestParam(value = "proyectoId", required = false) Long proyectoId,
                                                       @RequestParam(value = "usuarioId", required = false) Long usuarioId,
                                                       @RequestParam(value = "contenido", required = false) String contenido,
                                                       @AuthenticationPrincipal UsuarioDetails principal) {
        Usuario pm = principal.getUsuario();
        PmMensajeView mensaje;
        if (usuarioId == null) {
            mensaje = pmChatService.enviarMensaje(proyectoId, contenido, pm);
        } else {
            mensaje = pmChatService.enviarMensajePrivado(proyectoId, usuarioId, contenido, pm);
        }
        return ResponseEntity.ok(mensaje);
    }
}