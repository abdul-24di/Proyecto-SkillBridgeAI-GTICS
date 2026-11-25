package com.pucp.skillb_ia.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/**
 * Errores de toda la API REST: chat del PM, del colaborador y del RM, asistente del colaborador y
 * notificaciones. Estos controladores devuelven JSON, así que sus errores también son JSON
 * {@code {"error": "mensaje"}} con su código HTTP. Sin esta clase los atendía GlobalExceptionHandler, que está pensado
 * para las vistas: guarda el mensaje en un flash y redirige (302).
 * Va primero (HIGHEST_PRECEDENCE) para ganar sobre GlobalExceptionHandler solo en estos controladores.
 * Une el antiguo ChatRestExceptionHandler (error inesperado con log) y los mensajes por parámetro.
 */
@RestControllerAdvice(assignableTypes = {PmChatRestController.class, ColChatRestController.class,
        RmChatRestController.class, ColAsistenteRestController.class, NotificacionController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiErrorAdvice {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorAdvice.class);

    private static final String MENSAJE_GENERICO =
            "No pudimos completar la acción. Inténtalo de nuevo en unos segundos.";

    /**
     * Responde ante datos inválidos.
     * <p>
     * Datos inválidos o recurso que no existe o no le pertenece al usuario (mensaje escrito para el usuario).
     *
     * @param ex excepción capturada
     * @return respuesta 400
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> datoInvalido(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, mensajeOGenerico(ex));
    }

    /**
     * Responde cuando se supera el límite del asistente.
     * <p>
     * Demasiados mensajes seguidos al asistente IA. Es más específico que IllegalArgumentException, así que
     * Spring usa este manejador y no el de 400.
     *
     * @param ex excepción capturada
     * @return respuesta 429
     */
    @ExceptionHandler(com.pucp.skillb_ia.service.LimiteMensajesException.class)
    public ResponseEntity<Map<String, String>> demasiadosMensajes(com.pucp.skillb_ia.service.LimiteMensajesException ex) {
        return error(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    /**
     * Responde cuando la operación no se puede realizar en el estado actual.
     * <p>
     * La operación no se puede hacer en el estado actual (por ejemplo, una solicitud que ya existe).
     *
     * @param ex excepción capturada
     * @return respuesta 409
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> estadoInvalido(IllegalStateException ex) {
        return error(HttpStatus.CONFLICT, mensajeOGenerico(ex));
    }

    /**
     * Responde cuando el usuario no tiene permiso.
     *
     * @return respuesta 403
     */
    @ExceptionHandler({SecurityException.class, AccessDeniedException.class})
    public ResponseEntity<Map<String, String>> sinPermiso() {
        return error(HttpStatus.FORBIDDEN, "No tienes permiso para realizar esta acción.");
    }

    /**
     * Responde cuando falta un parámetro obligatorio.
     * <p>
     * Falta un parámetro obligatorio, por ejemplo proyectoId.
     *
     * @param ex excepción capturada
     * @return respuesta 400
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> faltaParametro(MissingServletRequestParameterException ex) {
        return error(HttpStatus.BAD_REQUEST, "Falta el parámetro \"" + ex.getParameterName() + "\".");
    }

    /**
     * Responde cuando un parámetro tiene un tipo incorrecto.
     * <p>
     * Un parámetro con un tipo que no corresponde, por ejemplo texto donde va un id numérico.
     *
     * @param ex excepción capturada
     * @return respuesta 400
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> tipoInvalido(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "El valor del parámetro \"" + ex.getName() + "\" no es válido.");
    }

    /**
     * Responde cuando el cuerpo no es un JSON válido.
     * <p>
     * El asistente recibe JSON ({@literal @}RequestBody): cuerpo vacío o mal formado.
     *
     * @return respuesta 400
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> cuerpoInvalido() {
        return error(HttpStatus.BAD_REQUEST, "Debes enviar los datos en formato JSON.");
    }

    /**
     * Responde ante cualquier otro error.
     * <p>
     * Cualquier otro error: se registra en el log y el usuario recibe un mensaje sin detalles técnicos.
     *
     * @param ex excepción capturada
     * @return respuesta 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> errorInesperado(Exception ex) {
        log.error("Error inesperado en la API REST", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, MENSAJE_GENERICO);
    }

    /**
     * Devuelve el mensaje de la excepción o uno genérico si no tiene.
     *
     * @param ex excepción capturada
     * @return mensaje para mostrar
     */
    private String mensajeOGenerico(RuntimeException ex) {
        return (ex.getMessage() == null || ex.getMessage().isBlank()) ? MENSAJE_GENERICO : ex.getMessage();
    }

    /**
     * Arma la respuesta JSON {@code {"error": "mensaje"}}.
     *
     * @param estado código HTTP
     * @param mensaje mensaje para el usuario
     * @return respuesta con el código indicado
     */
    private ResponseEntity<Map<String, String>> error(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(Map.of("error", mensaje));
    }
}
