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

/**
 * API JSON del chat RM–PM que consume el JS de {@code rm-chat.html}: mensajes no leídos, historial y envío.
 * La validación (que el proyecto sea del RM, contenido no vacío) la hace {@link RmChatService}; sus
 * excepciones las convierte {@link GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/rm/api/chat")
public class RmChatRestController {

    private final RmChatService rmChatService;

    /** Crea el componente con sus dependencias, que Spring inyecta por el constructor. */
    public RmChatRestController(RmChatService rmChatService) {
        this.rmChatService = rmChatService;
    }

    /**
     * Cuenta los mensajes no leídos del RM por proyecto.
     *
     * @param principal RM con la sesión iniciada
     * @return mapa id de proyecto (como texto) → número de mensajes no leídos
     */
    @GetMapping("/noleidos")
    public ResponseEntity<Map<String, Long>> getNoLeidos(@AuthenticationPrincipal UsuarioDetails principal) {
        Usuario rm = principal.getUsuario();
        return ResponseEntity.ok(rmChatService.obtenerNoLeidos(rm));
    }

    /**
     * Devuelve el historial del chat de un proyecto y lo marca como leído para el RM.
     *
     * @param proyectoId proyecto del chat
     * @param principal RM con la sesión iniciada
     * @return mensajes en orden cronológico
     */
    @GetMapping("/mensajes")
    public ResponseEntity<List<PmMensajeView>> getMensajes(@RequestParam("proyectoId") Long proyectoId,
                                                           @AuthenticationPrincipal UsuarioDetails principal) {
        Usuario rm = principal.getUsuario();
        return ResponseEntity.ok(rmChatService.obtenerMensajes(proyectoId, rm));
    }

    /**
     * Envía un mensaje del RM al chat de un proyecto.
     *
     * @param proyectoId proyecto del chat
     * @param contenido texto del mensaje
     * @param principal RM con la sesión iniciada
     * @return el mensaje guardado
     */
    @PostMapping("/enviar")
    public ResponseEntity<PmMensajeView> enviarMensaje(@RequestParam(value = "proyectoId", required = false) Long proyectoId,
                                                       @RequestParam(value = "contenido", required = false) String contenido,
                                                       @AuthenticationPrincipal UsuarioDetails principal) {
        Usuario rm = principal.getUsuario();
        return ResponseEntity.ok(rmChatService.enviarMensaje(proyectoId, contenido, rm));
    }
}