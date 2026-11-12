package com.pucp.skillb_ia.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;


@RestControllerAdvice(assignableTypes = {PmChatRestController.class, ColChatRestController.class, RmChatRestController.class, ColAsistenteRestController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ChatRestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatRestExceptionHandler.class);

    private static final String MENSAJE_GENERICO =
            "No pudimos completar la acción. Inténtalo de nuevo en unos segundos.";


    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> errorNegocio(RuntimeException ex) {
        String mensaje = (ex.getMessage() == null || ex.getMessage().isBlank()) ? MENSAJE_GENERICO : ex.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", mensaje));
    }

    @ExceptionHandler({SecurityException.class, AccessDeniedException.class})
    public ResponseEntity<Map<String, String>> sinPermiso(Exception ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "No tienes permiso para usar este chat."));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> peticionInvalida(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "No pudimos entender la solicitud. Recarga la página e inténtalo otra vez."));
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> errorInesperado(Exception ex) {
        log.error("Error inesperado en la API del chat", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", MENSAJE_GENERICO));
    }
}