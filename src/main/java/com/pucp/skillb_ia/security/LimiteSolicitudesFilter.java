package com.pucp.skillb_ia.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Frena a quien inunda el sitio de peticiones: cuenta por IP lo que hace en cada minuto y, al pasarse del
// límite, responde 429 sin llegar a la base de datos. Los formularios públicos (login, recuperar contraseña,
// activar cuenta) tienen un límite más bajo porque cada envío es caro: BCrypt, correos y archivos.
public class LimiteSolicitudesFilter extends OncePerRequestFilter {

    private static final long VENTANA_MS = 60_000;
    private static final int MAX_IPS_EN_MEMORIA = 20_000;
    private static final Set<String> FORMULARIOS_PUBLICOS =
            Set.of("/login", "/recuperar", "/verificar-codigo", "/nueva-contrasena", "/activar-cuenta");
    private static final List<String> ARCHIVOS_ESTATICOS =
            List.of("/css/", "/js/", "/img/", "/tabler/", "/documentos/", "/plantillas/", "/favicon.ico");

    private static class Contador {
        long inicio = System.currentTimeMillis();
        int peticiones;
        int formularios;
    }

    private final Map<String, Contador> contadores = new ConcurrentHashMap<>();
    private final int maxPorMinuto;
    private final int maxFormulariosPorMinuto;

    public LimiteSolicitudesFilter(int maxPorMinuto, int maxFormulariosPorMinuto) {
        this.maxPorMinuto = maxPorMinuto;
        this.maxFormulariosPorMinuto = maxFormulariosPorMinuto;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return ARCHIVOS_ESTATICOS.stream().anyMatch(request.getRequestURI()::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (superaElLimite(request)) {
            response.setHeader("Retry-After", "60");
            response.sendError(429);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean superaElLimite(HttpServletRequest request) {
        long ahora = System.currentTimeMillis();
        if (contadores.size() > MAX_IPS_EN_MEMORIA) {
            contadores.values().removeIf(c -> ahora - c.inicio > VENTANA_MS);
        }
        boolean esFormulario = "POST".equals(request.getMethod())
                && FORMULARIOS_PUBLICOS.contains(request.getRequestURI());

        Contador contador = contadores.compute(request.getRemoteAddr(), (ip, actual) -> {
            Contador c = actual == null || ahora - actual.inicio > VENTANA_MS ? new Contador() : actual;
            c.peticiones++;
            if (esFormulario) {
                c.formularios++;
            }
            return c;
        });
        return contador.peticiones > maxPorMinuto || contador.formularios > maxFormulariosPorMinuto;
    }
}
