package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.dto.PmAsistenteRespuesta;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.pm.PmAsistenteService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pm/api/asistente")
public class PmAsistenteRestController {

    public record MensajeForm(String mensaje, Long proyectoId) {}
    public record ProponerCandidatoForm(Long proyectoId, Long colaboradorId, BigDecimal horasSemanales, String mensaje) {}

    private final PmAsistenteService asistenteService;

    public PmAsistenteRestController(PmAsistenteService asistenteService) {
        this.asistenteService = asistenteService;
    }

    @PostMapping("/mensaje")
    public ResponseEntity<PmAsistenteRespuesta> mensaje(@RequestBody MensajeForm form,
                                                        @AuthenticationPrincipal UsuarioDetails principal,
                                                        HttpSession session) {
        return ResponseEntity.ok(asistenteService.responder(
                principal.getUsuario().getId(), form.proyectoId(), form.mensaje(), session));
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

    @PostMapping("/acciones/proponer")
    public ResponseEntity<Map<String, String>> proponer(@RequestBody ProponerCandidatoForm form,
                                                        @AuthenticationPrincipal UsuarioDetails principal) {
        asistenteService.confirmarPropuestaCandidato(
                principal.getUsuario().getId(),
                form.proyectoId(),
                form.colaboradorId(),
                form.horasSemanales() != null ? form.horasSemanales() : new BigDecimal("10"),
                form.mensaje());
        return ResponseEntity.ok(Map.of("mensaje",
                "Propuesta de asignación enviada. Quedará pendiente de aprobación del Resource Manager."));
    }
}
