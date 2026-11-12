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

// Único punto que habla con el proveedor de IA. Soporta dos proveedores, elegidos con app.ia.proveedor:
// ANTHROPIC (Claude, de pago)  |  GEMINI (Google AI Studio, tiene capa gratuita)
@Component
public class ColAsistenteIaClient {

    private static final Logger log = LoggerFactory.getLogger(ColAsistenteIaClient.class);

    private final RestClient restClient;
    private final boolean esGemini;
    private final String url;
    private final String apiKey;
    private final String modelo;
    private final int maxTokens;
    // Limita llamadas simultáneas para no agotar el pool de conexiones ni el cupo del proveedor.
    private final Semaphore cupo = new Semaphore(5);

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

        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(Duration.ofSeconds(45));
        this.restClient = RestClient.builder().requestFactory(fabrica).build();

        if (this.apiKey.isEmpty()) {
            log.warn("Asistente IA: IA_API_KEY está vacía, se usará el modo sin IA.");
        } else {
            log.info("Asistente IA: proveedor={}, modelo={}", esGemini ? "GEMINI" : "ANTHROPIC", this.modelo);
        }

    }

    public boolean estaConfigurado() {
        return !apiKey.isEmpty();
    }


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
        } finally {
            if (adquirido) {
                cupo.release();
            }
        }
    }

    // ANTHROPIC

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
        cuerpo.put("system", system);
        cuerpo.put("messages", mensajes);

        Map<String, Object> respuesta = restClient.post()
                .uri(url)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        return extraerTextoAnthropic(respuesta);
    }

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


    // GEMINI

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

        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("systemInstruction", instruccion);
        cuerpo.put("contents", contenidos);
        cuerpo.put("generationConfig", generacion);

        Map<String, Object> respuesta = restClient.post()
                .uri(url)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        return extraerTextoGemini(respuesta);
    }

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