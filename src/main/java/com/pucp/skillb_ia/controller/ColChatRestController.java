package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.dto.PmMensajeView;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.col.ColChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/colaborador/api/chat")
public class ColChatRestController {

    private final ColChatService colChatService;

    public ColChatRestController(ColChatService colChatService) {
        this.colChatService = colChatService;
    }

    @GetMapping("/mensajes")
    public ResponseEntity<List<PmMensajeView>> getMensajes(@RequestParam("proyectoId") Long proyectoId,
                                                           @AuthenticationPrincipal UsuarioDetails principal) {
        Usuario colaborador = principal.getUsuario();
        List<PmMensajeView> mensajes = colChatService.obtenerMensajes(proyectoId, colaborador);
        return ResponseEntity.ok(mensajes);
    }

    @PostMapping("/enviar")
    public ResponseEntity<PmMensajeView> enviarMensaje(@RequestParam("proyectoId") Long proyectoId,
                                                       @RequestParam("contenido") String contenido,
                                                       @AuthenticationPrincipal UsuarioDetails principal) {
        Usuario colaborador = principal.getUsuario();
        PmMensajeView mensaje = colChatService.enviarMensaje(proyectoId, contenido, colaborador);
        return ResponseEntity.ok(mensaje);
    }
}
