package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.dto.ChatContactoView;
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

    // Lista de mensajes directos de PM y colaboradores del proyecto
    @GetMapping("/companeros")
    public ResponseEntity<List<ChatContactoView>> getCompaneros(
            @RequestParam(value = "proyectoId", required = false) Long proyectoId,
            @AuthenticationPrincipal UsuarioDetails principal) {
        Usuario colaborador = principal.getUsuario();
        return ResponseEntity.ok(colChatService.listarContactos(proyectoId, colaborador));
    }


    @GetMapping("/mensajes")
    public ResponseEntity<List<PmMensajeView>> getMensajes(
            @RequestParam(value = "proyectoId", required = false) Long proyectoId,
            @RequestParam(value = "usuarioId", required = false) Long usuarioId,
            @AuthenticationPrincipal UsuarioDetails principal) {
        Usuario colaborador = principal.getUsuario();
        List<PmMensajeView> mensajes;

        // Si no se recibe un usuarioId, obtenemos los mensajes del chat grupal
        if (usuarioId == null) {
            mensajes = colChatService.obtenerMensajes(proyectoId, colaborador);
        } else {

            // Si se recibe un usuarioId, obtenemos los mensajes privados con ese usuario
            mensajes = colChatService.obtenerMensajesPrivados(proyectoId, usuarioId, colaborador);
        }
        return ResponseEntity.ok(mensajes);
    }

    @PostMapping("/enviar")
    public ResponseEntity<PmMensajeView> enviarMensaje(
            @RequestParam(value = "proyectoId", required = false) Long proyectoId,
            @RequestParam(value = "usuarioId", required = false) Long usuarioId,
            @RequestParam(value = "contenido", required = false) String contenido,
            @AuthenticationPrincipal UsuarioDetails principal) {
        Usuario colaborador = principal.getUsuario();
        PmMensajeView mensaje;
        if (usuarioId == null) {
            mensaje = colChatService.enviarMensaje(proyectoId, contenido, colaborador);
        } else {
            mensaje = colChatService.enviarMensajePrivado(proyectoId, usuarioId, contenido, colaborador);
        }
        return ResponseEntity.ok(mensaje);
    }


}