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
import java.util.Map;

/**
 * API REST del chat del colaborador (rutas bajo {@code /colaborador/api/chat}).
 * <p>
 * La pantalla del chat llama a estos endpoints desde JavaScript para listar contactos, leer y
 * enviar mensajes y consultar no leídos. Los permisos los valida {@link ColChatService}: si el
 * colaborador no pertenece al proyecto, el service lanza un error que traduce el manejador
 * global de excepciones.
 * <p>
 * Convención común: si no se envía {@code usuarioId}, la operación es sobre el chat
 * <b>grupal</b>; si se envía, es sobre el chat <b>privado</b> con ese usuario.
 */
@RestController
@RequestMapping("/colaborador/api/chat")
public class ColChatRestController {

    private final ColChatService colChatService;

    /**
     * @param colChatService service con la lógica y los permisos del chat
     */
    public ColChatRestController(ColChatService colChatService) {
        this.colChatService = colChatService;
    }

    /**
     * Lista las personas con quienes puede chatear en privado ({@code GET /colaborador/api/chat/companeros}):
     * el PM y los demás colaboradores activos del proyecto.
     *
     * @param proyectoId proyecto del chat
     * @param principal usuario autenticado
     * @return contactos disponibles
     * @see ColChatService#listarContactos
     */
    @GetMapping("/companeros")
    public ResponseEntity<List<ChatContactoView>> getCompaneros(
            @RequestParam(value = "proyectoId", required = false) Long proyectoId,
            @AuthenticationPrincipal UsuarioDetails principal) {
        Usuario colaborador = principal.getUsuario();
        return ResponseEntity.ok(colChatService.listarContactos(proyectoId, colaborador));
    }

    /**
     * Cuenta los mensajes no leídos por chat ({@code GET /colaborador/api/chat/noleidos}).
     *
     * @param proyectoId proyecto del chat
     * @param principal usuario autenticado
     * @return cantidad de mensajes no leídos por chat
     * @see ColChatService#obtenerNoLeidos
     */
    @GetMapping("/noleidos")
    public ResponseEntity<Map<String, Long>> getNoLeidos(
            @RequestParam(value = "proyectoId", required = false) Long proyectoId,
            @AuthenticationPrincipal UsuarioDetails principal) {
        Usuario colaborador = principal.getUsuario();
        return ResponseEntity.ok(colChatService.obtenerNoLeidos(proyectoId, colaborador));
    }


    /**
     * Obtiene los mensajes de un chat ({@code GET /colaborador/api/chat/mensajes}). Sin
     * {@code usuarioId} devuelve el chat grupal; con {@code usuarioId}, el privado con ese usuario.
     * Leerlos los marca como leídos.
     *
     * @param proyectoId proyecto del chat
     * @param usuarioId otro participante del chat privado (opcional)
     * @param principal usuario autenticado
     * @return mensajes de la conversación
     * @see ColChatService#obtenerMensajes
     * @see ColChatService#obtenerMensajesPrivados
     */
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

    /**
     * Envía un mensaje ({@code POST /colaborador/api/chat/enviar}). Sin {@code usuarioId} va al chat
     * grupal; con {@code usuarioId}, al privado con ese usuario. El texto no puede estar vacío ni
     * superar 1000 caracteres.
     *
     * @param proyectoId proyecto del chat
     * @param usuarioId destinatario del mensaje privado (opcional)
     * @param contenido texto del mensaje
     * @param principal usuario autenticado
     * @return el mensaje guardado
     * @see ColChatService#enviarMensaje
     * @see ColChatService#enviarMensajePrivado
     */
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