package com.pucp.skillb_ia.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite de mensajes por minuto compartido por los asistentes IA (Colaborador, PM y RM).
 * TASK-074: un solo cupo por usuario; compute() hace atómico el conteo y el barrido retira
 * a quien no envía hace más de un minuto, así el mapa no crece sin límite.
 */
@Service
public class AsistenteRitmoService {

    private static final long VENTANA_MS = 60_000;

    private final int maxPorMinuto;
    private final Map<Long, ArrayDeque<Long>> envios = new ConcurrentHashMap<>();
    private volatile long ultimoBarrido = System.currentTimeMillis();

    public AsistenteRitmoService(@Value("${app.ia.mensajes-por-minuto:10}") int maxPorMinuto) {
        this.maxPorMinuto = maxPorMinuto;
    }

    public void validar(Long usuarioId) {
        validar(usuarioId, System.currentTimeMillis());
    }

    void validar(Long usuarioId, long ahora) {
        barrerColasAntiguas(ahora);
        boolean[] excedido = {false};
        envios.compute(usuarioId, (k, cola) -> {
            ArrayDeque<Long> actual = cola != null ? cola : new ArrayDeque<>();
            while (!actual.isEmpty() && ahora - actual.peekFirst() > VENTANA_MS) {
                actual.pollFirst();
            }
            if (actual.size() >= maxPorMinuto) {
                excedido[0] = true;
            } else {
                actual.addLast(ahora);
            }
            return actual;
        });
        if (excedido[0]) {
            throw new IllegalArgumentException("Enviaste muchos mensajes seguidos. Espera un momento e inténtalo de nuevo.");
        }
    }

    int usuariosRegistrados() {
        return envios.size();
    }

    private void barrerColasAntiguas(long ahora) {
        if (ahora - ultimoBarrido < VENTANA_MS) {
            return;
        }
        ultimoBarrido = ahora;
        for (Long id : envios.keySet()) {
            envios.computeIfPresent(id, (k, cola) ->
                    cola.isEmpty() || ahora - cola.peekLast() > VENTANA_MS ? null : cola);
        }
    }
}
