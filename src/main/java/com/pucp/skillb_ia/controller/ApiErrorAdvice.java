package com.pucp.skillb_ia.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

// Errores de la API REST (chat del PM, chat del colaborador y notificaciones). Estos controladores devuelven
// JSON, así que sus errores también deben ser JSON con su código HTTP. Sin esta clase los atendía
// GlobalExceptionHandler, que está pensado para las vistas: guardaba el mensaje en un flash y redirigía (302).
// Va primero (HIGHEST_PRECEDENCE) para que gane sobre GlobalExceptionHandler solo en estos tres controladores.
@RestControllerAdvice(assignableTypes = {PmChatRestController.class, ColChatRestController.class,
        NotificacionController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiErrorAdvice {

    // Datos inválidos o recurso que no existe o no le pertenece al usuario (mensaje escrito para el usuario).
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> datoInvalido(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // La operación no se puede hacer en el estado actual (por ejemplo, el PM aún no inició el chat).
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> estadoInvalido(IllegalStateException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> sinPermiso() {
        return error(HttpStatus.FORBIDDEN, "No tienes permiso para realizar esta acción.");
    }

    // Falta un parámetro obligatorio, por ejemplo proyectoId.
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> faltaParametro(MissingServletRequestParameterException ex) {
        return error(HttpStatus.BAD_REQUEST, "Falta el parámetro \"" + ex.getParameterName() + "\".");
    }

    // Un parámetro con un tipo que no corresponde, por ejemplo texto donde va un id numérico.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> tipoInvalido(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "El valor del parámetro \"" + ex.getName() + "\" no es válido.");
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(Map.of("error", mensaje));
    }
}
