package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.dto.PmMensajeView;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.col.ColChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static com.pucp.skillb_ia.controller.GlobalExceptionHandler.mensajeSeguro;

@RestController
@RequestMapping("/colaborador/api/chat")
public class ColChatRestController {

    private final ColChatService colChatService;

    public ColChatRestController(ColChatService colChatService) {
        this.colChatService = colChatService;
    }

    @GetMapping("/mensajes")
    public ResponseEntity<?> getMensajes(@RequestParam(value = "proyectoId", required = false) Long proyectoId,
                                         @AuthenticationPrincipal UsuarioDetails principal) {
        try {
            Usuario colaborador = principal.getUsuario();
            List<PmMensajeView> mensajes = colChatService.obtenerMensajes(proyectoId, colaborador);
            return ResponseEntity.ok(mensajes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", mensajeSeguro(e)));
        }
    }

    @PostMapping("/enviar")
    public ResponseEntity<?> enviarMensaje(@RequestParam(value = "proyectoId", required = false) Long proyectoId,
                                           @RequestParam(value = "contenido", required = false) String contenido,
                                           @AuthenticationPrincipal UsuarioDetails principal) {
        try {
            Usuario colaborador = principal.getUsuario();
            PmMensajeView mensaje = colChatService.enviarMensaje(proyectoId, contenido, colaborador);
            return ResponseEntity.ok(mensaje);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", mensajeSeguro(e)));
        }
    }
}