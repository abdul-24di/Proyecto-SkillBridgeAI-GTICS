package com.pucp.skillb_ia.service;

/**
 * Se superó el límite de mensajes por minuto de un asistente IA.
 * Hereda de IllegalArgumentException para que las vistas del PM y del RM lo sigan mostrando como un aviso;
 * la API REST del asistente del colaborador lo responde con 429 (Too Many Requests) en ApiErrorAdvice.
 */
public class LimiteMensajesException extends IllegalArgumentException {

    public LimiteMensajesException(String mensaje) {
        super(mensaje);
    }
}
