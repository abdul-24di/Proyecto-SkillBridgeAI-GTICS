package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.dto.PmMensajeView;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.pm.PmChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pm/api/chat")
public class PmChatRestController {

    private final PmChatService pmChatService;

    public PmChatRestController(PmChatService pmChatService) {
        this.pmChatService = pmChatService;
    }

    @GetMapping("/mensajes")
    public ResponseEntity<List<PmMensajeView>> getMensajes(@RequestParam("proyectoId") Long proyectoId,
                                                           @AuthenticationPrincipal UsuarioDetails principal) {
        Usuario pm = principal.getUsuario();
        List<PmMensajeView> mensajes = pmChatService.obtenerMensajes(proyectoId, pm);
        return ResponseEntity.ok(mensajes);
    }

    @PostMapping("/enviar")
    public ResponseEntity<PmMensajeView> enviarMensaje(@RequestParam("proyectoId") Long proyectoId,
                                                       @RequestParam("contenido") String contenido,
                                                       @AuthenticationPrincipal UsuarioDetails principal) {
        Usuario pm = principal.getUsuario();
        PmMensajeView mensaje = pmChatService.enviarMensaje(proyectoId, contenido, pm);
        return ResponseEntity.ok(mensaje);
    }
}
