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


/**
 * Controlador REST del chat del Project Manager ({@code /pm/api/chat}): expone contactos del proyecto,
 * conteo de mensajes no leídos, historial y envío de mensajes grupales y privados.
 */
@RestController
@RequestMapping("/pm/api/chat")
public class PmChatRestController {

    private final PmChatService pmChatService;

    /** Crea el controlador con el servicio de chat del PM. */
    public PmChatRestController(PmChatService pmChatService) {
        this.pmChatService = pmChatService;
    }

    /**
     * Lista los contactos disponibles para chatear en un proyecto (RM revisor y colaboradores activos).
     *
     * @param proyectoId identificador del proyecto del PM
     * @param principal usuario autenticado con rol PM
     * @return lista de contactos del proyecto
     */
    @GetMapping("/companeros")
    public ResponseEntity<List<ChatContactoView>> getCompaneros(@RequestParam("proyectoId") Long proyectoId,
                                                                @AuthenticationPrincipal UsuarioDetails principal) {
        Usuario pm = principal.getUsuario();
        return ResponseEntity.ok(pmChatService.listarContactos(proyectoId, pm));
    }

    /**
     * Devuelve el conteo de mensajes no leídos por conversación (chat grupal y chats privados).
     *
     * @param proyectoId identificador del proyecto del PM
     * @param principal usuario autenticado con rol PM
     * @return mapa clave de conversación → cantidad de mensajes sin leer
     */
    @GetMapping("/noleidos")
    public ResponseEntity<Map<String, Long>> getNoLeidos(@RequestParam("proyectoId") Long proyectoId,
                                                         @AuthenticationPrincipal UsuarioDetails principal) {
        Usuario pm = principal.getUsuario();
        return ResponseEntity.ok(pmChatService.obtenerNoLeidos(proyectoId, pm));
    }

    /**
     * Obtiene el historial de mensajes del chat grupal o de un chat privado si se especifica {@code usuarioId}.
     *
     * @param proyectoId identificador del proyecto del PM
     * @param usuarioId identificador opcional del contacto (RM o colaborador activo)
     * @param principal usuario autenticado con rol PM
     * @return lista ordenada de mensajes de la conversación
     */
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

    /**
     * Envía un mensaje en el chat grupal del proyecto o en un chat privado si se indica {@code usuarioId}.
     *
     * @param proyectoId identificador del proyecto del PM
     * @param usuarioId identificador opcional del destinatario privado
     * @param contenido texto del mensaje
     * @param principal usuario autenticado con rol PM
     * @return vista del mensaje recién registrado
     */
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