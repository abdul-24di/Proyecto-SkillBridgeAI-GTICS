package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.dto.NotificacionView;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.NotificacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {
    private final NotificacionService notificacionService;

    public NotificacionController(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @GetMapping
    public ResponseEntity<List<NotificacionView>> listar(
            @AuthenticationPrincipal UsuarioDetails principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(notificacionService.listar(principal.getUsuario().getId()));
    }

    @PostMapping("/{id}/leer")
    public ResponseEntity<Void> marcarLeida(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UsuarioDetails principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        notificacionService.marcarLeida(principal.getUsuario().getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/leer-todas")
    public ResponseEntity<Void> marcarTodasLeidas(
            @AuthenticationPrincipal UsuarioDetails principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        notificacionService.marcarTodasLeidas(principal.getUsuario().getId());
        return ResponseEntity.noContent().build();
    }
}
