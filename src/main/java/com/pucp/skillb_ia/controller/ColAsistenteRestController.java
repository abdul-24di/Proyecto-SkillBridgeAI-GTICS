package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.dto.ColAsistenteRespuesta;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.col.ColAsistenteService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/colaborador/api/asistente")
public class ColAsistenteRestController {

    public record MensajeForm(String mensaje) {}
    public record SolicitarCursoForm(Long cursoId, String justificacion) {}
    public record SolicitarProyectoForm(Long proyectoId, Long habilidadId, String mensaje) {}

    private final ColAsistenteService asistenteService;

    public ColAsistenteRestController(ColAsistenteService asistenteService) {
        this.asistenteService = asistenteService;
    }

    @PostMapping("/mensaje")
    public ResponseEntity<ColAsistenteRespuesta> mensaje(@RequestBody MensajeForm form,
                                                         @AuthenticationPrincipal UsuarioDetails principal,
                                                         HttpSession session) {
        return ResponseEntity.ok(asistenteService.responder(principal.getUsuario().getId(), form.mensaje(), session));
    }

    @GetMapping("/historial")
    public ResponseEntity<List<String>> historial(HttpSession session) {
        return ResponseEntity.ok(asistenteService.obtenerHistorial(session));
    }

    @PostMapping("/historial/limpiar")
    public ResponseEntity<Map<String, String>> limpiar(HttpSession session) {
        asistenteService.limpiarHistorial(session);
        return ResponseEntity.ok(Map.of("mensaje", "Conversación reiniciada."));
    }

    @PostMapping("/acciones/solicitar-curso")
    public ResponseEntity<Map<String, String>> solicitarCurso(@RequestBody SolicitarCursoForm form,
                                                              @AuthenticationPrincipal UsuarioDetails principal) {
        asistenteService.confirmarSolicitudCurso(principal.getUsuario().getId(), form.cursoId(), form.justificacion());
        return ResponseEntity.ok(Map.of("mensaje",
                "Tu solicitud de inscripción fue enviada. Quedará pendiente de aprobación del Resource Manager."));
    }

    @PostMapping("/acciones/solicitar-proyecto")
    public ResponseEntity<Map<String, String>> solicitarProyecto(@RequestBody SolicitarProyectoForm form,
                                                                 @AuthenticationPrincipal UsuarioDetails principal) {
        asistenteService.confirmarSolicitudProyecto(principal.getUsuario().getId(), form.proyectoId(),
                form.habilidadId(), form.mensaje());
        return ResponseEntity.ok(Map.of("mensaje",
                "Tu solicitud fue enviada. Quedará pendiente de aprobación del PM y del RM."));
    }
}