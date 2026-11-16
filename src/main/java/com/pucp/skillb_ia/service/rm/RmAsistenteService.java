package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmAsistenteRespuesta;
import com.pucp.skillb_ia.dto.RmAsistenteRespuesta.CandidatoTarjetaRm;
import com.pucp.skillb_ia.dto.RmAsistenteRespuesta.ProyectoTarjetaRm;
import com.pucp.skillb_ia.dto.RmAsistenteRespuesta.TurnoConversacionRm;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.service.col.ColAsistenteIaClient;
import com.pucp.skillb_ia.service.rm.RmAsistenteContextoService.ContextoRm;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RmAsistenteService {

    private static final int MAX_MENSAJE = 1000;
    private static final int MAX_HISTORIAL = 12;
    private static final int MAX_TURNOS_SESION = 8;
    private static final String ATTR_HISTORIAL = "RM_ASISTENTE_HISTORIAL";
    private static final String ATTR_TURNOS = "RM_ASISTENTE_TURNOS";

    private static final Pattern MARCADOR = Pattern.compile(
            "\\[\\[(PROY|CAND):(\\d{1,9}(?::\\d{1,9})?)\\]\\]");

    private final RmAsistenteContextoService contextoService;
    private final ColAsistenteIaClient iaClient;
    private final RmAsignacionService rmAsignacionService;
    private final int maxPorMinuto;
    private final Map<Long, ArrayDeque<Long>> envios = new ConcurrentHashMap<>();

    public RmAsistenteService(RmAsistenteContextoService contextoService,
                              ColAsistenteIaClient iaClient,
                              RmAsignacionService rmAsignacionService,
                              @Value("${app.ia.mensajes-por-minuto:10}") int maxPorMinuto) {
        this.contextoService = contextoService;
        this.iaClient = iaClient;
        this.rmAsignacionService = rmAsignacionService;
        this.maxPorMinuto = maxPorMinuto;
    }

    public RmAsistenteRespuesta responder(Long rmId, Long proyectoContextoId, String mensajeCrudo, HttpSession session) {
        String mensaje = mensajeCrudo == null ? "" : mensajeCrudo.trim();
        if (mensaje.isEmpty()) {
            throw new IllegalArgumentException("Escribe tu consulta para el asistente del RM.");
        }
        if (mensaje.length() > MAX_MENSAJE) {
            throw new IllegalArgumentException("Tu mensaje es muy largo (máximo " + MAX_MENSAJE + " caracteres).");
        }
        if (rmId != null) {
            validarRitmo(rmId);
        }

        ContextoRm contexto = contextoService.construir(proyectoContextoId, mensaje);
        List<String> historial = leerHistorial(session);

        String textoIa = null;
        boolean iaActiva = false;
        if (iaClient.estaConfigurado()) {
            textoIa = iaClient.generar(promptSistema(contexto), historial, mensaje);
            iaActiva = textoIa != null && !textoIa.isBlank();
        }
        if (!iaActiva) {
            textoIa = contexto.resumenAnaliticoLocal();
        }

        RmAsistenteRespuesta respuesta = procesar(textoIa, contexto, iaActiva);

        historial.add(mensaje);
        historial.add(respuesta.respuesta());
        while (historial.size() > MAX_HISTORIAL) {
            historial.remove(0);
            historial.remove(0);
        }
        session.setAttribute(ATTR_HISTORIAL, new ArrayList<>(historial));

        List<TurnoConversacionRm> turnos = obtenerTurnos(session);
        turnos.add(new TurnoConversacionRm(mensaje, respuesta));
        while (turnos.size() > MAX_TURNOS_SESION) {
            turnos.remove(0);
        }
        session.setAttribute(ATTR_TURNOS, new ArrayList<>(turnos));

        return respuesta;
    }

    @SuppressWarnings("unchecked")
    public List<TurnoConversacionRm> obtenerTurnos(HttpSession session) {
        Object guardado = session.getAttribute(ATTR_TURNOS);
        if (guardado instanceof List<?>) {
            return new ArrayList<>((List<TurnoConversacionRm>) guardado);
        }
        return new ArrayList<>();
    }

    public void limpiarHistorial(HttpSession session) {
        session.removeAttribute(ATTR_HISTORIAL);
        session.removeAttribute(ATTR_TURNOS);
    }

    public Asignacion proponerDesdeTalentMatching(Long rmId, Long proyectoId, Long colaboradorId,
                                                  BigDecimal horasSemanales, String justificacion,
                                                  String motivoCapacidad, String justificacionCupo) {
        return rmAsignacionService.proponerDesdeRm(
                proyectoId, colaboradorId, horasSemanales, justificacion, motivoCapacidad, justificacionCupo, rmId);
    }

    private RmAsistenteRespuesta procesar(String textoIa, ContextoRm ctx, boolean iaDisponible) {
        Set<Long> proyectosUsados = new LinkedHashSet<>();
        Set<String> candidatosUsados = new LinkedHashSet<>();

        StringBuilder sb = new StringBuilder();
        int ultimo = 0;
        Matcher m = MARCADOR.matcher(textoIa);
        while (m.find()) {
            sb.append(textoIa, ultimo, m.start());
            ultimo = m.end();
            String tipo = m.group(1);
            String clave = m.group(2);

            if ("PROY".equals(tipo)) {
                Long id = Long.valueOf(clave.split(":")[0]);
                if (ctx.proyectos().containsKey(id)) {
                    proyectosUsados.add(id);
                    sb.append("**").append(ctx.proyectos().get(id).nombre()).append("**");
                }
            } else if ("CAND".equals(tipo)) {
                if (ctx.candidatos().containsKey(clave)) {
                    candidatosUsados.add(clave);
                    sb.append("**").append(ctx.candidatos().get(clave).nombre()).append("**");
                }
            }
        }
        sb.append(textoIa.substring(ultimo));
        String textoLimpio = sb.toString().trim();

        if (ctx.proyectoEnfocado() != null && ctx.proyectos().containsKey(ctx.proyectoEnfocado().getProyecto().getId())) {
            proyectosUsados.add(ctx.proyectoEnfocado().getProyecto().getId());
        }

        List<ProyectoTarjetaRm> proyectos = new ArrayList<>();
        for (Long id : proyectosUsados) {
            proyectos.add(ctx.proyectos().get(id));
        }

        List<CandidatoTarjetaRm> candidatos = new ArrayList<>();
        for (String clave : candidatosUsados) {
            candidatos.add(ctx.candidatos().get(clave));
        }

        boolean usoInternos = !proyectos.isEmpty() || !candidatos.isEmpty()
                || textoLimpio.contains("Según SkillBridge");

        return new RmAsistenteRespuesta(
                textoLimpio,
                RmAsistenteRespuesta.estructurarTexto(textoLimpio),
                usoInternos,
                iaDisponible,
                proyectos,
                candidatos
        );
    }

    private void validarRitmo(Long rmId) {
        long ahora = System.currentTimeMillis();
        ArrayDeque<Long> cola = envios.computeIfAbsent(rmId, k -> new ArrayDeque<>());
        synchronized (cola) {
            while (!cola.isEmpty() && ahora - cola.peekFirst() > 60_000) {
                cola.pollFirst();
            }
            if (cola.size() >= maxPorMinuto) {
                throw new IllegalArgumentException("Enviaste muchos mensajes seguidos. Espera un momento e inténtalo de nuevo.");
            }
            cola.addLast(ahora);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> leerHistorial(HttpSession session) {
        Object guardado = session.getAttribute(ATTR_HISTORIAL);
        if (guardado instanceof List<?>) {
            return new ArrayList<>((List<String>) guardado);
        }
        return new ArrayList<>();
    }

    private String promptSistema(ContextoRm ctx) {
        return """
                Eres el Asistente SkillBridge IA especializado para el Resource Manager (RM).
                Responde siempre en español, de forma ejecutiva, clara, breve y accionable (máximo ~260 palabras; usa viñetas con "- ").
                Fecha actual: %s.

                REGLAS OBLIGATORIAS DEL ASISTENTE RM:
                1. Usa exclusivamente la información del bloque DATOS DE SKILLBRIDGE (RM). Nunca inventes proyectos, colaboradores, presupuestos ni validaciones.
                2. El RM SÍ gestiona presupuestos, costos estimados por asignación, validaciones de habilidades/certificados/educación y solicitudes de personal de los PMs.
                3. Para mencionar un proyecto usa su marcador [[PROY:id]]. Para mencionar un candidato recomendado usa [[CAND:colaboradorId:proyectoId]]. El sistema convertirá cada marcador en una tarjeta interactiva.
                4. Las recomendaciones de Talent Matching NUNCA asignan directamente: aclara siempre que el RM propone al candidato y la propuesta queda pendiente de aprobación del Project Manager (PM).
                5. Separa el origen de tu respuesta usando "**Según SkillBridge (Resource Manager)**" para los datos reales del bloque y "**Recomendación de dotación (IA)**" para tus sugerencias.

                === DATOS DE SKILLBRIDGE (RM) ===
                %s
                === FIN DE DATOS ===
                """.formatted(LocalDate.now(), ctx.textoPrompt());
    }
}
