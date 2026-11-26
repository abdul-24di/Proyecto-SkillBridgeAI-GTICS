package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DNI de los usuarios: formato, que no se repita y consulta en la API de apisperu (clase 10.1, consumo de
 * servicios web con RestTemplate y Authorization: Bearer). Si la API no está configurada o no responde, el
 * DNI se guarda "sin verificar": una falla externa no debe impedir que alguien active su cuenta. Un DNI sin
 * verificar se puede corregir y se vuelve a consultar (DniVerificacionService); uno verificado ya no cambia.
 */
@Service
public class DniService {

    private static final Logger log = LoggerFactory.getLogger(DniService.class);

    public static final String REGEX = "^[0-9]{8}$";
    public static final String MENSAJE_FORMATO = "El DNI debe tener exactamente 8 dígitos, sin espacios ni letras.";
    public static final String MENSAJE_YA_VERIFICADO = "Tu DNI ya está verificado y no se puede cambiar.";

    /**
     * Resultado posible de una consulta de DNI.
     * <p>
     * Resultado de consultar la API.
     */
    public enum EstadoConsulta { ENCONTRADO, NO_ENCONTRADO, NO_DISPONIBLE }

    /**
     * Respuesta de la consulta de un DNI.
     *
     * @param estado resultado de la consulta
     * @param nombreCompleto nombre que figura en el DNI; {@code null} si no se encontró
     */
    public record Consulta(EstadoConsulta estado, String nombreCompleto) {
    }

    private final UsuarioRepository usuarioRepository;
    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String url;

    /** Crea el componente con sus dependencias, que Spring inyecta por el constructor. */
    public DniService(UsuarioRepository usuarioRepository, RestTemplate restTemplate,
                      @Value("${app.dni.api-key:}") String apiKey,
                      @Value("${app.dni.url:https://dniruc.apisperu.com/api/v1/dni}") String url) {
        this.usuarioRepository = usuarioRepository;
        this.restTemplate = restTemplate;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.url = url == null ? "" : url.trim().replaceAll("/+$", "");
        if (this.apiKey.isEmpty()) {
            log.warn("[DNI] Falta la variable de entorno DNI_API_KEY: los DNI se guardarán sin verificar.");
        }
    }

    /**
     * Valida un DNI antes de registrarlo.
     *
     * @return true si la API confirmó el DNI y el nombre; false si la API no está disponible ("sin verificar")
     * @throws IllegalArgumentException con un mensaje para el usuario si el formato es inválido, el DNI ya está
     *                                  registrado, la API no lo encuentra o el nombre no coincide
     */
    public boolean validar(String dni, String nombreCompleto) {
        return validar(dni, nombreCompleto, null);
    }

    /** Igual que {@link #validar(String, String)}, sin contar como repetido el DNI del propio usuario. */
    public boolean validar(String dni, String nombreCompleto, Long usuarioId) {
        String limpio = validarFormatoYUnico(dni, usuarioId);
        Consulta consulta = consultar(limpio);
        if (consulta.estado() == EstadoConsulta.NO_DISPONIBLE) {
            return false;
        }
        if (consulta.estado() == EstadoConsulta.NO_ENCONTRADO) {
            throw new IllegalArgumentException("No encontramos ese DNI. Revisa que esté bien escrito.");
        }
        if (!nombresCoinciden(nombreCompleto, consulta.nombreCompleto())) {
            throw new IllegalArgumentException(
                    "Tu nombre no coincide con el registrado para ese DNI. Escríbelo como figura en tu documento.");
        }
        return true;
    }

    /**
     * Validación que no consulta la API: 8 dígitos y que ningún otro usuario lo tenga.
     *
     * @return el DNI sin espacios alrededor
     * @throws IllegalArgumentException si el formato es inválido o el DNI ya está registrado
     */
    public String validarFormatoYUnico(String dni) {
        return validarFormatoYUnico(dni, null);
    }

    /** Igual que {@link #validarFormatoYUnico(String)}; si usuarioId no es null, su propio DNI no cuenta como repetido. */
    public String validarFormatoYUnico(String dni, Long usuarioId) {
        String limpio = dni == null ? "" : dni.trim();
        if (!limpio.matches(REGEX)) {
            throw new IllegalArgumentException(MENSAJE_FORMATO);
        }
        boolean repetido = usuarioId == null
                ? usuarioRepository.existsByDni(limpio)
                : usuarioRepository.existsByDniAndIdNot(limpio, usuarioId);
        if (repetido) {
            throw new IllegalArgumentException("Ese DNI ya está registrado en otra cuenta.");
        }
        return limpio;
    }

    /** true si hay token de la API: sin él no tiene sentido reintentar la verificación. */
    public boolean apiConfigurada() {
        return !apiKey.isEmpty();
    }

    /** Guarda el DNI del usuario. Se puede corregir mientras esté "sin verificar"; verificado ya no cambia. */
    @Transactional
    public void registrar(Usuario usuario, String dni, boolean verificado) {
        if (usuario.isDniVerificado() && usuario.getDni() != null && !usuario.getDni().isBlank()) {
            throw new IllegalStateException(MENSAJE_YA_VERIFICADO);
        }
        usuario.setDni(dni.trim());
        usuario.setDniVerificado(verificado);
        usuarioRepository.save(usuario);
    }

    /**
     * Consulta el DNI en apisperu.com: GET {url}/{dni} con Authorization: Bearer {token}.
     * Nunca lanza excepciones: cualquier problema de la API se devuelve como NO_DISPONIBLE.
     */
    @SuppressWarnings("unchecked")
    public Consulta consultar(String dni) {
        if (apiKey.isEmpty()) {
            return new Consulta(EstadoConsulta.NO_DISPONIBLE, null);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        try {
            ResponseEntity<Map> respuesta = restTemplate.exchange(url + "/" + dni, HttpMethod.GET,
                    new HttpEntity<>(headers), Map.class);
            Map<String, Object> cuerpo = (Map<String, Object>) respuesta.getBody();
            if (cuerpo != null && Boolean.FALSE.equals(cuerpo.get("success"))) {
                String mensaje = cuerpo.get("message") instanceof String m ? m : "";
                // Solo "DNI no encontrado" bloquea el registro; cualquier otro rechazo (token, plan, límite,
                // mantenimiento, sin mensaje) es un problema del servicio y el DNI se guarda sin verificar.
                if (esDniNoEncontrado(mensaje)) {
                    return new Consulta(EstadoConsulta.NO_ENCONTRADO, null);
                }
                log.warn("[DNI] La API rechazó la consulta ({}): el DNI se guardará sin verificar.", mensaje);
                return new Consulta(EstadoConsulta.NO_DISPONIBLE, null);
            }
            String nombre = extraerNombreCompleto(cuerpo);
            if (nombre == null || nombre.isBlank()) {
                return new Consulta(EstadoConsulta.NO_ENCONTRADO, null);
            }
            return new Consulta(EstadoConsulta.ENCONTRADO, nombre);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return new Consulta(EstadoConsulta.NO_ENCONTRADO, null);
            }
            log.warn("[DNI] La API respondió {}: el DNI se guardará sin verificar.", e.getStatusCode());
            return new Consulta(EstadoConsulta.NO_DISPONIBLE, null);
        } catch (RestClientException e) {
            log.warn("[DNI] No se pudo consultar la API ({}): el DNI se guardará sin verificar.", e.getClass().getSimpleName());
            return new Consulta(EstadoConsulta.NO_DISPONIBLE, null);
        }
    }

    /** El mensaje de success=false indica que el DNI no existe (sin importar mayúsculas ni tildes). */
    private boolean esDniNoEncontrado(String mensaje) {
        String texto = Normalizer.normalize(mensaje, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return texto.contains("no se encontr") || texto.contains("no encontrado") || texto.contains("not found")
                || texto.contains("no existe");
    }

    /**
     * Saca el nombre completo de la respuesta de apisperu. Acepta los datos en la raíz o dentro de "data", con
     * el nombre completo en un solo campo o separado en nombres y apellidos.
     */
    @SuppressWarnings("unchecked")
    String extraerNombreCompleto(Map<String, Object> respuesta) {
        if (respuesta == null) {
            return null;
        }
        Map<String, Object> datos = respuesta;
        if (respuesta.get("data") instanceof Map<?, ?> interno) {
            datos = (Map<String, Object>) interno;
        }
        for (String campo : List.of("nombre_completo", "nombreCompleto", "full_name", "fullName")) {
            if (datos.get(campo) instanceof String texto && !texto.isBlank()) {
                return texto;
            }
        }
        StringBuilder partes = new StringBuilder();
        for (String campo : List.of("nombres", "nombre", "apellido_paterno", "apellidoPaterno",
                "apellido_materno", "apellidoMaterno")) {
            if (datos.get(campo) instanceof String texto && !texto.isBlank()) {
                partes.append(texto).append(' ');
            }
        }
        String resultado = partes.toString().trim();
        return resultado.isEmpty() ? null : resultado;
    }

    /**
     * El nombre escrito coincide si tiene al menos dos palabras (nombre y apellido) y todas aparecen en el
     * nombre oficial, sin importar mayúsculas, tildes ni el orden. Permite escribir solo un nombre y un
     * apellido ("Luis Paredes" para "PAREDES DIAZ LUIS ABDUL").
     */
    public boolean nombresCoinciden(String escrito, String oficial) {
        List<String> palabrasEscritas = palabras(escrito);
        List<String> palabrasOficiales = palabras(oficial);
        return palabrasEscritas.size() >= 2 && palabrasOficiales.containsAll(palabrasEscritas);
    }

    /**
     * Separa un texto en palabras normalizadas, para comparar nombres.
     *
     * @param texto texto a separar
     * @return palabras del texto
     */
    private List<String> palabras(String texto) {
        if (texto == null) {
            return List.of();
        }
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return Arrays.stream(sinTildes.toUpperCase(Locale.ROOT).split("[^A-Z]+"))
                .filter(p -> !p.isBlank())
                .toList();
    }
}
