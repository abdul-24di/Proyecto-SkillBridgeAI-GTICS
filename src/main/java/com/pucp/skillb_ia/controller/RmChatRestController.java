package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.dto.PmMensajeView;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.rm.RmChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rm/api/chat")
public class RmChatRestController {

    private final RmChatService rmChatService;

    public RmChatRestController(RmChatService rmChatService) {
        this.rmChatService = rmChatService;
    }


    @GetMapping("/noleidos")
    public ResponseEntity<Map<String, Long>> getNoLeidos(@AuthenticationPrincipal UsuarioDetails principal) {
        Usuario rm = principal.getUsuario();
        return ResponseEntity.ok(rmChatService.obtenerNoLeidos(rm));
    }


    @GetMapping("/mensajes")
    public ResponseEntity<List<PmMensajeView>> getMensajes(@RequestParam("proyectoId") Long proyectoId,
                                                           @AuthenticationPrincipal UsuarioDetails principal) {
        Usuario rm = principal.getUsuario();
        return ResponseEntity.ok(rmChatService.obtenerMensajes(proyectoId, rm));
    }

    @PostMapping("/enviar")
    public ResponseEntity<PmMensajeView> enviarMensaje(@RequestParam(value = "proyectoId", required = false) Long proyectoId,
                                                       @RequestParam(value = "contenido", required = false) String contenido,
                                                       @AuthenticationPrincipal UsuarioDetails principal) {
        Usuario rm = principal.getUsuario();
        return ResponseEntity.ok(rmChatService.enviarMensaje(proyectoId, contenido, rm));
    }
}