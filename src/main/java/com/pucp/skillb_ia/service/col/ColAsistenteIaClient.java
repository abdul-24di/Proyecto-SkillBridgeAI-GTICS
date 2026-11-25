package com.pucp.skillb_ia.service.col;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Cliente HTTP del proveedor de IA. Es el <b>único</b> punto del sistema que se comunica con la IA.
 * <p>
 * Soporta dos proveedores, elegidos con la propiedad {@code app.ia.proveedor}:
 * <ul>
 *   <li>{@code ANTHROPIC} (Claude, de pago), el valor por defecto;</li>
 *   <li>{@code GEMINI} (Google AI Studio, con capa gratuita).</li>
 * </ul>
 * Si no hay clave de API ({@code app.ia.api-key}), {@link #estaConfigurado} es falso y el
 * asistente funciona en modo sin IA. Ante cualquier falla devuelve {@code null} en vez de
 * lanzar excepciones, para que el asistente pueda degradarse sin romper la pantalla.
 */
@Component
public class ColAsistenteIaClient {

    private static final Logger log = LoggerFactory.getLogger(ColAsistenteIaClient.class);


    /**
     * Cliente HTTP que se inicializa únicamente cuando se necesita utilizar.
     * Esto evita que un problema durante su creación impida que la aplicación
     * o las pruebas se ejecuten correctamente. */

    private volatile RestClient restClient;
    private final boolean esGemini;
    private final String url;
    private final String apiKey;
    private final String modelo;
    private final int maxTokens;

    /**
     * Controla que no se realicen más de 5 peticiones al mismo tiempo.
     * Las demás esperan hasta que se libere un espacio.
     */
    private final Semaphore cupo = new Semaphore(5);



    /**
     * Configura el cliente desde las propiedades de la aplicación.
     * <p>
     * Si no se indica modelo, usa {@code claude-haiku-5-5} (Anthropic) o
     * {@code gemini-3.5-flash-lite} (Gemini). Si no se indica URL, usa la oficial de cada proveedor.
     *
     * @param proveedor {@code ANTHROPIC} (por defecto) o {@code GEMINI}
     * @param apiKey clave de API; vacía desactiva la IA
     * @param modelo modelo a usar; vacío usa el predeterminado del proveedor
     * @param urlManual URL del servicio; vacía usa la oficial
     * @param maxTokens máximo de tokens de la respuesta (900 por defecto)
     */
    public ColAsistenteIaClient(@Value("${app.ia.proveedor:ANTHROPIC}") String proveedor,
                                @Value("${app.ia.api-key:}") String apiKey,
                                @Value("${app.ia.modelo:}") String modelo,
                                @Value("${app.ia.url:}") String urlManual,
                                @Value("${app.ia.max-tokens:900}") int maxTokens) {
        this.esGemini = "GEMINI".equalsIgnoreCase(proveedor == null ? "" : proveedor.trim());
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        String modeloElegido = modelo == null ? "" : modelo.trim();
        if (modeloElegido.isEmpty()) {
            modeloElegido = esGemini ? "gemini-3.5-flash-lite" : "claude-haiku-5-5";
        }
        this.modelo = modeloElegido;
        this.maxTokens = maxTokens;

        String urlElegida = urlManual == null ? "" : urlManual.trim();
        if (urlElegida.isEmpty()) {
            urlElegida = esGemini
                    ? "https://generativelanguage.googleapis.com/v1beta/models/" + this.modelo + ":generateContent"
                    : "https://api.anthropic.com/v1/messages";
        }
        this.url = urlElegida;

        if (this.apiKey.isEmpty()) {
            log.warn("Asistente IA: IA_API_KEY está vacía, se usará el modo sin IA.");
        } else {
            log.info("Asistente IA: proveedor={}, modelo={}", esGemini ? "GEMINI" : "ANTHROPIC", this.modelo);
        }

    }

    /**
     * Devuelve el cliente HTTP, creándolo la primera vez de forma segura entre hilos (patrón
     * <i>double-checked locking</i>). Tiene 5 segundos de espera para conectar y 45 para leer la respuesta.
     *
     * @return el cliente HTTP compartido
     */
    private RestClient restClient() {

        // Comprueba si ya existe un cliente HTTP creado anteriormente.
        RestClient cliente = restClient;

        // Si el cliente todavía no existe, es necesario crearlo.
        if (cliente == null) {

            // Evita que varios hilos creen el cliente HTTP al mismo tiempo.
            synchronized (this) {

                // Vuelve a comprobar si otro hilo ya creó el cliente mientras esperábamos.
                cliente = restClient;

                // Si todavía no existe, comienza su creación.
                if (cliente == null) {


                    // Crea el cliente HTTP de Java y establece 5 segundos como tiempo máximo para conectar.
                    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

                    // Crea una fábrica que permite utilizar el cliente HTTP de Java desde Spring.
                    JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);

                    // Establece 45 segundos como tiempo máximo de espera para leer la respuesta.
                    fabrica.setReadTimeout(Duration.ofSeconds(45));

                    // Construye el cliente RestClient utilizando la fábrica configurada.
                    cliente = RestClient.builder().requestFactory(fabrica).build();

                    // Guarda el cliente creado para reutilizarlo en futuras llamadas.
                    restClient = cliente;
                }
            }
        }

        // Devuelve el cliente HTTP para que pueda realizar peticiones a otros
        return cliente;
    }

    /**
     * @return {@code true} si hay una clave de API configurada; si no, el asistente usa el modo sin IA
     */
    public boolean estaConfigurado() {
        return !apiKey.isEmpty();
    }


    /**
     * Pide una respuesta a la IA.
     * <p>
     * Usa un semáforo de 5 cupos y espera hasta 3 segundos por uno; si no lo consigue, devuelve
     * {@code null}. Cualquier error (respuesta HTTP de error, falla de red o del cliente) se
     * registra en el log y también devuelve {@code null}. El cupo siempre se libera al terminar.
     *
     * @param system instrucciones de sistema (rol, reglas y datos del colaborador)
     * @param historial conversación previa; las posiciones pares son del usuario y las impares de la IA
     * @param mensaje consulta actual, ya encapsulada por seguridad
     * @return el texto de la IA, o {@code null} si no se pudo obtener
     */
    public String generar(String system, List<String> historial, String mensaje) {
        boolean adquirido = false;
        try {
            adquirido = cupo.tryAcquire(3, TimeUnit.SECONDS);
            if (!adquirido) {
                log.warn("Asistente IA: demasiadas llamadas simultáneas");
                return null;
            }
            if (esGemini) {
                return llamarGemini(system, historial, mensaje);
            }
            return llamarAnthropic(system, historial, mensaje);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;

        } catch (RestClientResponseException e) {
            log.warn("Asistente IA: el proveedor respondió {} con el modelo '{}'. Detalle: {}",
                    e.getStatusCode().value(), modelo, e.getResponseBodyAsString());
            return null;

        } catch (RestClientException e) {
            log.warn("Asistente IA: falló la llamada al proveedor: {}", e.getMessage());
            return null;

        } catch (java.io.UncheckedIOException e) {
            log.warn("Asistente IA: no se pudo crear el cliente HTTP: {}", e.getMessage());
            return null;
        } finally {
            if (adquirido) {
                cupo.release();
            }
        }
    }



    /**
     * Llama a la API de Anthropic ({@code /v1/messages}). Envía el historial como mensajes
     * alternados {@code user}/{@code assistant}, temperatura 0.2 (respuestas poco variables) y el
     * límite de tokens configurado. La clave va en el encabezado {@code x-api-key}.
     *
     * @param system instrucciones de sistema
     * @param historial conversación previa
     * @param mensaje consulta actual
     * @return el texto de la respuesta, o {@code null} si vino vacía
     */
    private String llamarAnthropic(String system, List<String> historial, String mensaje) {
        List<Map<String, Object>> mensajes = new ArrayList<>();
        for (int i = 0; i < historial.size(); i++) {
            Map<String, Object> m = new HashMap<>();
            m.put("role", i % 2 == 0 ? "user" : "assistant");
            m.put("content", historial.get(i));
            mensajes.add(m);
        }
        Map<String, Object> actual = new HashMap<>();
        actual.put("role", "user");
        actual.put("content", mensaje);
        mensajes.add(actual);

        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("model", modelo);
        cuerpo.put("max_tokens", maxTokens);
        cuerpo.put("temperature", 0.2);
        cuerpo.put("system", system);
        cuerpo.put("messages", mensajes);

        Map<String, Object> respuesta = restClient().post()
                .uri(url)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        return extraerTextoAnthropic(respuesta);
    }

    /**
     * Extrae el texto de la respuesta de Anthropic, uniendo los bloques de tipo {@code text}.
     *
     * @param respuesta cuerpo JSON ya convertido a mapa
     * @return el texto, o {@code null} si no hay
     */
    private String extraerTextoAnthropic(Map<String, Object> respuesta) {
        if (respuesta == null) {
            return null;
        }
        StringBuilder texto = new StringBuilder();

        Object contenido = respuesta.get("content");

        if (contenido instanceof List<?> bloques) {

            for (Object bloque : bloques) {

                if (bloque instanceof Map<?, ?> mapa
                        && "text".equals(mapa.get("type"))
                        && mapa.get("text") instanceof String t) {

                    texto.append(t);

                }

            }

        }

        return texto.length() == 0 ? null : texto.toString();

    }



    /**
     * Arma un turno de conversación con el formato de Gemini ({@code role} + {@code parts}).
     *
     * @param rol {@code user} o {@code model}
     * @param texto contenido del turno
     * @return el turno listo para enviar
     */
    private Map<String, Object> turnoGemini(String rol, String texto) {
        Map<String, Object> parte = new HashMap<>();

        parte.put("text", texto);

        List<Object> partes = new ArrayList<>();

        partes.add(parte);

        Map<String, Object> turno = new HashMap<>();

        turno.put("role", rol);

        turno.put("parts", partes);

        return turno;
    }

    /**
     * Llama a la API de Gemini ({@code generateContent}). Equivale a {@link #llamarAnthropic}
     * pero con el formato de Gemini: las instrucciones van en {@code systemInstruction}, los turnos
     * alternan {@code user}/{@code model}, el máximo de tokens es el mayor entre el configurado y
     * 2048, la temperatura es 0.2 y se activan filtros de seguridad (acoso, odio, contenido sexual
     * y peligroso) que bloquean desde nivel medio. La clave va en {@code x-goog-api-key}.
     *
     * @param system instrucciones de sistema
     * @param historial conversación previa
     * @param mensaje consulta actual
     * @return el texto de la respuesta, o {@code null} si vino vacía
     */
    private String llamarGemini(String system, List<String> historial, String mensaje) {
        List<Map<String, Object>> contenidos = new ArrayList<>();
        for (int i = 0; i < historial.size(); i++) {
            contenidos.add(turnoGemini(i % 2 == 0 ? "user" : "model", historial.get(i)));
        }
        contenidos.add(turnoGemini("user", mensaje));

        Map<String, Object> parteSistema = new HashMap<>();
        parteSistema.put("text", system);
        List<Object> partesSistema = new ArrayList<>();
        partesSistema.add(parteSistema);
        Map<String, Object> instruccion = new HashMap<>();
        instruccion.put("parts", partesSistema);


        Map<String, Object> generacion = new HashMap<>();
        generacion.put("maxOutputTokens", Math.max(maxTokens, 2048));
        generacion.put("temperature", 0.2);

        List<Map<String, String>> safetySettings = List.of(
                Map.of("category", "HARM_CATEGORY_HARASSMENT", "threshold", "BLOCK_MEDIUM_AND_ABOVE"),
                Map.of("category", "HARM_CATEGORY_HATE_SPEECH", "threshold", "BLOCK_MEDIUM_AND_ABOVE"),
                Map.of("category", "HARM_CATEGORY_SEXUALLY_EXPLICIT", "threshold", "BLOCK_MEDIUM_AND_ABOVE"),
                Map.of("category", "HARM_CATEGORY_DANGEROUS_CONTENT", "threshold", "BLOCK_MEDIUM_AND_ABOVE")
        );

        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("systemInstruction", instruccion);
        cuerpo.put("contents", contenidos);
        cuerpo.put("generationConfig", generacion);
        cuerpo.put("safetySettings", safetySettings);

        Map<String, Object> respuesta = restClient().post()
                .uri(url)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        return extraerTextoGemini(respuesta);
    }


    /**
     * Extrae el texto de la respuesta de Gemini: toma el primer candidato y une sus partes de
     * texto. Si no hay texto (por ejemplo, la respuesta fue bloqueada por los filtros), lo registra
     * en el log.
     *
     * @param respuesta cuerpo JSON ya convertido a mapa
     * @return el texto, o {@code null} si no hay
     */
    private String extraerTextoGemini(Map<String, Object> respuesta) {
        if (respuesta == null) {
            return null;
        }
        StringBuilder texto = new StringBuilder();
        if (respuesta.get("candidates") instanceof List<?> candidatos && !candidatos.isEmpty()
                && candidatos.get(0) instanceof Map<?, ?> candidato
                && candidato.get("content") instanceof Map<?, ?> contenido
                && contenido.get("parts") instanceof List<?> partes) {
            for (Object parte : partes) {
                if (parte instanceof Map<?, ?> mapa && mapa.get("text") instanceof String t) {
                    texto.append(t);
                }
            }
        }

        if (texto.length() == 0) {
            log.warn("Asistente IA: Gemini respondió sin texto. Respuesta: {}", respuesta);
        }

        return texto.length() == 0 ? null : texto.toString();
    }
}