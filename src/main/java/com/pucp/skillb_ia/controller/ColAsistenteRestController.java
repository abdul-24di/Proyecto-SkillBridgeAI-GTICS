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


/**
 * API REST del asistente de IA del colaborador (rutas bajo {@code /colaborador/api/asistente}).
 * <p>
 * La pantalla del asistente llama a estos endpoints desde JavaScript. Hay dos grupos:
 * <ul>
 *   <li><b>Conversación</b>: enviar un mensaje, leer el historial y reiniciarlo. El historial
 *       vive en la sesión HTTP, no en la base de datos.</li>
 *   <li><b>Acciones confirmadas</b>: solicitar un curso o un proyecto. El asistente solo
 *       <i>propone</i> estas acciones; se ejecutan únicamente cuando el colaborador las confirma
 *       con un clic, y pasan por las mismas validaciones que el flujo normal.</li>
 * </ul>
 *
 * @see ColAsistenteService
 */
@RestController
@RequestMapping("/colaborador/api/asistente")
public class ColAsistenteRestController {

    /**
     * Cuerpo de la petición de {@code /mensaje}.
     *
     * @param mensaje texto que escribió el colaborador
     */
    public record MensajeForm(String mensaje) {}


    /**
     * Cuerpo de la petición de {@code /acciones/solicitar-curso}.
     *
     * @param cursoId curso al que se quiere inscribir
     * @param justificacion motivo de la solicitud (opcional)
     */
    public record SolicitarCursoForm(Long cursoId, String justificacion) {}


    /**
     * Cuerpo de la petición de {@code /acciones/solicitar-proyecto}.
     *
     * @param proyectoId proyecto al que quiere unirse
     * @param habilidadId perfil (habilidad) al que postula
     * @param mensaje mensaje para los revisores (opcional)
     */
    public record SolicitarProyectoForm(Long proyectoId, Long habilidadId, String mensaje) {}

    private final ColAsistenteService asistenteService;

    public ColAsistenteRestController(ColAsistenteService asistenteService) {
        this.asistenteService = asistenteService;
    }

    /**
     * Envía una consulta al asistente y devuelve su respuesta ({@code POST /colaborador/api/asistente/mensaje}).
     * La respuesta trae el texto, tarjetas de foros, cursos y proyectos, y acciones propuestas.
     *
     * @param form cuerpo con el mensaje
     * @param principal usuario autenticado
     * @param session sesión HTTP donde se guarda el historial de la conversación
     * @return la respuesta del asistente
     * @see ColAsistenteService#responder
     */
    @PostMapping("/mensaje")
    public ResponseEntity<ColAsistenteRespuesta> mensaje(@RequestBody MensajeForm form,
                                                         @AuthenticationPrincipal UsuarioDetails principal,
                                                         HttpSession session) {
        return ResponseEntity.ok(asistenteService.responder(principal.getUsuario().getId(), form.mensaje(), session));
    }

    /**
     * Devuelve el historial de la conversación guardado en la sesión
     * ({@code GET /colaborador/api/asistente/historial}).
     *
     * @param session sesión HTTP
     * @return mensajes alternados: consulta del colaborador, respuesta del asistente
     */
    @GetMapping("/historial")
    public ResponseEntity<List<String>> historial(HttpSession session) {
        return ResponseEntity.ok(asistenteService.obtenerHistorial(session));
    }

    /**
     * Reinicia la conversación borrando el historial de la sesión
     * ({@code POST /colaborador/api/asistente/historial/limpiar}).
     *
     * @param session sesión HTTP
     * @return un mensaje de confirmación
     */
    @PostMapping("/historial/limpiar")
    public ResponseEntity<Map<String, String>> limpiar(HttpSession session) {
        asistenteService.limpiarHistorial(session);
        return ResponseEntity.ok(Map.of("mensaje", "Conversación reiniciada."));
    }

    /**
     * Confirma la solicitud de inscripción a un curso que propuso el asistente
     * ({@code POST /colaborador/api/asistente/acciones/solicitar-curso}). La solicitud queda
     * pendiente de aprobación del Resource Manager.
     *
     * @param form curso y justificación
     * @param principal usuario autenticado
     * @return un mensaje de confirmación
     * @see ColAsistenteService#confirmarSolicitudCurso
     */
    @PostMapping("/acciones/solicitar-curso")
    public ResponseEntity<Map<String, String>> solicitarCurso(@RequestBody SolicitarCursoForm form,
                                                              @AuthenticationPrincipal UsuarioDetails principal) {
        asistenteService.confirmarSolicitudCurso(principal.getUsuario().getId(), form.cursoId(), form.justificacion());
        return ResponseEntity.ok(Map.of("mensaje",
                "Tu solicitud de inscripción fue enviada. Quedará pendiente de aprobación del Resource Manager."));
    }

    /**
     * Confirma la solicitud de incorporación a un proyecto que propuso el asistente
     * ({@code POST /colaborador/api/asistente/acciones/solicitar-proyecto}). La asignación queda
     * pendiente de aprobación del PM y del RM.
     *
     * @param form proyecto, perfil y mensaje
     * @param principal usuario autenticado
     * @return un mensaje de confirmación
     * @see ColAsistenteService#confirmarSolicitudProyecto
     */
    @PostMapping("/acciones/solicitar-proyecto")
    public ResponseEntity<Map<String, String>> solicitarProyecto(@RequestBody SolicitarProyectoForm form,
                                                                 @AuthenticationPrincipal UsuarioDetails principal) {
        asistenteService.confirmarSolicitudProyecto(principal.getUsuario().getId(), form.proyectoId(),
                form.habilidadId(), form.mensaje());
        return ResponseEntity.ok(Map.of("mensaje",
                "Tu solicitud fue enviada. Quedará pendiente de aprobación del PM y del RM."));
    }
}