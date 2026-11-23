package com.pucp.skillb_ia.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Defensa en profundidad contra Prompt Injection (directo e indirecto) y fuga de
 * instrucciones del sistema para todos los asistentes IA de SkillBridge (Colaborador, PM y RM).
 */
@Service
public class AsistenteSeguridadService {

    private static final Logger log = LoggerFactory.getLogger(AsistenteSeguridadService.class);

    public static final String MENSAJE_BLOQUEO_INJECTION =
            "No puedo procesar esa solicitud porque intenta alterar mis instrucciones o acceder a configuración interna. "
                    + "Puedo ayudarte únicamente con consultas sobre tus datos y gestión dentro de SkillBridge.";

    public static final String MENSAJE_FUGA_BLOQUEADA =
            "No puedo mostrar instrucciones internas ni estructura técnica del asistente. "
                    + "Por favor reformula tu consulta sobre proyectos, cursos, habilidades o gestión en SkillBridge.";

    private record ReglaInjection(String categoria, Pattern patron) {}

    // Caracteres invisibles Unicode (zero-width, bidi override, BOM) y controles no imprimibles.
    private static final Pattern CARACTERES_INVISIBLES = Pattern.compile("[\\p{Cf}\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");
    private static final Pattern ESPACIOS_MULTIPLES = Pattern.compile("\\s+");
    private static final Pattern MARCADOR_GENERICO = Pattern.compile("\\[\\[[^\\]]{1,120}\\]\\]");
    private static final Pattern ETIQUETA_DATO_FILTRADA = Pattern.compile(
            "</?\\s*(?:dato_usuario|extracto_foro|consulta_usuario)\\s*>", Pattern.CASE_INSENSITIVE);

    private static final List<ReglaInjection> REGLAS_INJECTION = List.of(
            // 1. Anulación / omisión de instrucciones previas o del sistema (ES / EN)
            new ReglaInjection("OVERRIDE_INSTRUCCIONES", Pattern.compile(
                    "(?:ignora|ignorar|olvida|olvidar|descarta|descartar|anula|anular|omite|omitir|saltate|salta|"
                            + "desobedece|desobedecer|deja\\s+de\\s+seguir|rompe|elimina|borra)"
                            + "\\s+(?:por\\s+completo\\s+)?(?:todas?\\s+)?(?:las?\\s+|tus?\\s+|los?\\s+|estas?\\s+|mis\\s+)?"
                            + "(?:instrucciones|reglas|directrices|restricciones|indicaciones|politicas|comandos|ordenes|prompt)"
                            + "(?:\\s+(?:anteriores?|previas?|del\\s+sistema|obligatorias?|actuales?|internas?))?")),
            new ReglaInjection("OVERRIDE_INSTRUCCIONES_EN", Pattern.compile(
                    "(?:ignore|disregard|forget|override|bypass|skip|drop|cancel)"
                            + "\\s+(?:all\\s+)?(?:previous|prior|above|system|your|the|earlier|all)"
                            + "\\s+(?:instructions|rules|prompts?|guidelines|constraints|directives|policies)")),
            new ReglaInjection("NUEVAS_INSTRUCCIONES", Pattern.compile(
                    "(?:a\\s+partir\\s+de\\s+ahora\\s+(?:solo\\s+)?(?:sigue|obedece|haz)|"
                            + "nuevas?\\s+instrucciones\\s+(?:del\\s+sistema|obligatorias)|"
                            + "new\\s+system\\s+instructions|override\\s+system\\s+prompt|"
                            + "system\\s+override)")),

            // 2. Extracción / volcado del prompt del sistema o del bloque de contexto interno (ES / EN)
            new ReglaInjection("EXTRACCION_PROMPT", Pattern.compile(
                    "(?:muestra|muestrame|mostrar|revela|revelame|revelar|imprime|imprimeme|imprimir|dime|dame|"
                            + "copia|copiame|repite|repiteme|transcribe|lista|enumera|devuelve|ensena|ensename|"
                            + "expon|comparte|lee|leeme|vuelca|muestre)"
                            + "\\s+(?:textualmente\\s+|literalmente\\s+|integramente\\s+|todo\\s+|toda\\s+)?"
                            + "(?:tu\\s+|el\\s+|la\\s+|los\\s+|las\\s+)?"
                            + "(?:system\\s+prompt|prompt\\s+(?:del\\s+)?sistema|prompt\\s+inicial|prompt\\s+interno|"
                            + "instrucciones\\s+(?:del\\s+)?sistema|instrucciones\\s+internas|instrucciones\\s+ocultas|"
                            + "reglas\\s+obligatorias|bloque\\s+datos\\s+de\\s+skillbridge|datos\\s+de\\s+skillbridge|"
                            + "configuracion\\s+interna\\s+del\\s+asistente|texto\\s+del\\s+sistema)")),
            new ReglaInjection("EXTRACCION_PROMPT_EN", Pattern.compile(
                    "(?:show|reveal|print|repeat|dump|leak|output|display|expose|tell\\s+me|give\\s+me)"
                            + "\\s+(?:me\\s+)?(?:your\\s+|the\\s+|full\\s+|exact\\s+|verbatim\\s+)?"
                            + "(?:system\\s+prompt|system\\s+instructions|hidden\\s+instructions|initial\\s+prompt|"
                            + "internal\\s+prompt|prompt\\s+template|developer\\s+prompt)")),
            new ReglaInjection("CONSULTA_PROMPT_DIRECTA", Pattern.compile(
                    "(?:cuales?\\s+son\\s+tus\\s+(?:instrucciones\\s+(?:internas|del\\s+sistema|ocultas|secretas)|"
                            + "reglas\\s+obligatorias)|what\\s+are\\s+your\\s+(?:system|hidden|internal)\\s+(?:instructions|rules|prompts?))")),

            // 3. Cambio de rol / jailbreak (DAN, modo desarrollador, sin restricciones)
            new ReglaInjection("CAMBIO_DE_ROL", Pattern.compile(
                    "(?:actua\\s+como|ahora\\s+eres|finge\\s+ser|comportate\\s+como|conviertete\\s+en|adopta\\s+el\\s+rol\\s+de|"
                            + "pretend\\s+(?:to\\s+be|you\\s+are)|act\\s+as|you\\s+are\\s+now|roleplay\\s+as)"
                            + "\\s+(?:un\\s+|una\\s+|el\\s+|la\\s+|a\\s+|an\\s+)?"
                            + "(?:dan\\b|jailbreak|modo\\s+desarrollador|developer\\s+mode|administrador\\s+root|"
                            + "superusuario|hacker|asistente\\s+sin\\s+(?:restricciones|reglas|limites)|"
                            + "ia\\s+sin\\s+(?:restricciones|reglas|limites)|sistema\\s+sin\\s+limites|"
                            + "unrestricted|uncensored|evil|malicious)")),
            new ReglaInjection("JAILBREAK_MODO", Pattern.compile(
                    "\\b(?:jailbreak|modo\\s+dios|god\\s+mode|developer\\s+mode|modo\\s+desarrollador|"
                            + "dan\\s+mode|do\\s+anything\\s+now|unfiltered\\s+mode|sin\\s+censura\\s+ni\\s+reglas)\\b")),

            // 4. Inyección de delimitadores de prompt, tokens de control LLM o marcadores internos falsificados
            new ReglaInjection("DELIMITADOR_SISTEMA", Pattern.compile(
                    "(?:===\\s*(?:fin\\s+de\\s+datos|datos\\s+de\\s+skillbridge|system|instrucciones|reglas)\\s*===|"
                            + "<\\s*\\|?\\s*(?:im_start|im_end|system|endoftext|assistant|prompt)\\s*\\|?\\s*>|"
                            + "(?:^|\\n)\\s*\\[?\\s*(?:system|developer|assistant)\\s*\\]?\\s*:|"
                            + "</?\\s*(?:dato_usuario|extracto_foro|consulta_usuario|system|system_prompt|instructions?)\\b[^>]*>|"
                            + "\\[\\[\\s*(?:accion|curso|proy|pub|cand)\\s*:)"))
    );

    // Patrones que solo aparecen si el modelo filtró partes del system prompt interno.
    private static final List<Pattern> PATRONES_FUGA_SISTEMA = List.of(
            Pattern.compile("===\\s*DATOS DE SKILLBRIDGE", Pattern.CASE_INSENSITIVE),
            Pattern.compile("===\\s*FIN DE DATOS\\s*===", Pattern.CASE_INSENSITIVE),
            Pattern.compile("REGLAS OBLIGATORIAS DEL ASISTENTE", Pattern.CASE_INSENSITIVE),
            Pattern.compile("REGLAS OBLIGATORIAS\\s*\\R\\s*1\\.", Pattern.CASE_INSENSITIVE),
            Pattern.compile("PRIVACIDAD FINANCIERA ESTRICTA:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("GU[IÍ]A DE USO DE SKILLBRIDGE\\s*\\(reglas de la plataforma\\)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("El texto dentro de\\s*<?(extracto_foro|dato_usuario)>?\\s*lo escribieron", Pattern.CASE_INSENSITIVE),
            Pattern.compile("ACCI[OÓ]N DISPONIBLE:\\s*\\[\\[ACCION:", Pattern.CASE_INSENSITIVE)
    );

    public record ResultadoSalida(String texto, boolean fugaDetectada) {}

    /**
     * Limpia caracteres invisibles/de control del mensaje de usuario antes de procesarlo.
     */
    public String limpiarEntradaUsuario(String mensajeCrudo) {
        if (mensajeCrudo == null) {
            return "";
        }
        return CARACTERES_INVISIBLES.matcher(mensajeCrudo).replaceAll("").trim();
    }

    /**
     * Detecta intentos de Prompt Injection directo (ES/EN), incluyendo evasiones con
     * caracteres invisibles Unicode, homóglifos NFKC y tildes.
     */
    public Optional<String> detectarPromptInjection(String mensajeCrudo) {
        if (mensajeCrudo == null || mensajeCrudo.isBlank()) {
            return Optional.empty();
        }
        String normalizado = normalizarParaAnalisis(mensajeCrudo);
        for (ReglaInjection regla : REGLAS_INJECTION) {
            if (regla.patron().matcher(normalizado).find()) {
                return Optional.of(regla.categoria());
            }
        }
        return Optional.empty();
    }

    /**
     * Evalúa el mensaje del usuario y registra una advertencia de seguridad si se detecta un ataque.
     */
    public boolean esIntentoPromptInjection(String rolAsistente, Long usuarioId, String mensajeCrudo) {
        Optional<String> categoria = detectarPromptInjection(mensajeCrudo);
        if (categoria.isPresent()) {
            log.warn("Asistente IA [{}]: intento de prompt injection bloqueado (usuarioId={}, categoria={})",
                    rolAsistente, usuarioId, categoria.get());
            return true;
        }
        return false;
    }

    /**
     * Envuelve el mensaje limpio del usuario para dejar clara al modelo la frontera de entrada no confiable.
     */
    public String encapsularMensajeUsuario(String mensajeLimpio) {
        String seguro = CARACTERES_INVISIBLES.matcher(mensajeLimpio == null ? "" : mensajeLimpio)
                .replaceAll("")
                .replace("</consulta_usuario>", "")
                .replace("<consulta_usuario>", "")
                .trim();
        return "<consulta_usuario>" + seguro + "</consulta_usuario>";
    }

    /**
     * Inspecciona la respuesta del LLM antes de mostrarla o guardarla en historial:
     * bloquea fugas del system prompt y limpia etiquetas delimitadoras filtradas.
     */
    public ResultadoSalida validarSalidaIa(String textoIa, String rolAsistente, Long usuarioId) {
        if (textoIa == null || textoIa.isBlank()) {
            return new ResultadoSalida("", false);
        }
        for (Pattern patron : PATRONES_FUGA_SISTEMA) {
            if (patron.matcher(textoIa).find()) {
                log.warn("Asistente IA [{}]: posible fuga del prompt del sistema bloqueada (usuarioId={})",
                        rolAsistente, usuarioId);
                return new ResultadoSalida(MENSAJE_FUGA_BLOQUEADA, true);
            }
        }
        String limpio = ETIQUETA_DATO_FILTRADA.matcher(textoIa).replaceAll("").trim();
        return new ResultadoSalida(limpio, false);
    }

    /**
     * Elimina cualquier marcador interno huérfano o malformado [[...]] que haya quedado tras el procesamiento.
     */
    public static String limpiarMarcadoresRestantes(String texto) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        return MARCADOR_GENERICO.matcher(texto).replaceAll("").trim();
    }

    /**
     * Sanitiza texto proveniente de la base de datos o escrito por usuarios antes de insertarlo
     * en el contexto del system prompt, neutralizando cierres de etiquetas XML (case-insensitive),
     * delimitadores de sección, saltos de línea, marcadores internos y caracteres invisibles.
     */
    public static String sanitizarDato(String texto) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        String s = CARACTERES_INVISIBLES.matcher(texto).replaceAll("");
        s = s.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
        s = s.replace("[[", "(").replace("]]", ")");
        s = s.replace('<', '(').replace('>', ')');
        s = s.replace("===", "---").replace("##", "--");
        return ESPACIOS_MULTIPLES.matcher(s).replaceAll(" ").trim();
    }

    /**
     * Envuelve un texto de usuario/BD sanitizado en <dato_usuario>...</dato_usuario>.
     */
    public static String envolverDato(String texto) {
        return "<dato_usuario>" + sanitizarDato(texto) + "</dato_usuario>";
    }

    /**
     * Envuelve un extracto de foro sanitizado en <extracto_foro>...</extracto_foro>.
     */
    public static String envolverExtractoForo(String texto) {
        return "<extracto_foro>" + sanitizarDato(texto) + "</extracto_foro>";
    }

    private static String normalizarParaAnalisis(String texto) {
        String sinInvisibles = CARACTERES_INVISIBLES.matcher(texto).replaceAll("");
        String nfkc = Normalizer.normalize(sinInvisibles, Normalizer.Form.NFKC);
        String sinTildes = Normalizer.normalize(nfkc, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT);
    }
}
