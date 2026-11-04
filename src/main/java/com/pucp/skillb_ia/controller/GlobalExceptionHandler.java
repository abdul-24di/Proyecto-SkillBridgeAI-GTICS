package com.pucp.skillb_ia.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

@ControllerAdvice
public class GlobalExceptionHandler {

    // Archivo demasiado grande (evidencia, documento, foto, etc.)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String archivoDemasiadoGrande(HttpServletRequest request, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("mensajeError", "El archivo que intentaste subir es demasiado grande.");
        return "redirect:" + paginaAnterior(request, "/");
    }

    // Petición de subida mal formada (p. ej. un POST sin archivo enviado a mano): es un error del cliente.
    @ExceptionHandler(MultipartException.class)
    public void subidaInvalida(HttpServletResponse response) throws IOException {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
    }

    // Error de negocio: recurso no encontrado o acceso denegado
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public String errorNegocio(Exception ex, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", ex.getMessage());
        return "redirect:" + paginaAnterior(request, "/");
    }

    // Intento de acceder a un recurso que no le pertenece
    @ExceptionHandler(SecurityException.class)
    public String errorSeguridad(HttpServletRequest request, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", "No tienes permiso para realizar esta acción.");
        return "redirect:" + paginaAnterior(request, "/");
    }

    // Los mensajes de negocio (IllegalArgumentException / IllegalStateException) están escritos para el
    // usuario; cualquier otro error (p. ej. de la base de datos) no debe filtrar detalles técnicos.
    public static String mensajeSeguro(Exception e) {
        if (e instanceof IllegalArgumentException || e instanceof IllegalStateException
                || e instanceof SecurityException) {
            return e.getMessage();
        }
        return "No se pudo completar la operación. Revisa los datos e inténtalo de nuevo.";
    }

    // El Referer lo envía el cliente y puede traer cualquier cosa: solo se usa su ruta, para no redirigir
    // nunca a otro sitio. Si no se puede leer, se vuelve a la página por defecto.
    public static String paginaAnterior(HttpServletRequest request, String porDefecto) {
        String referer = request.getHeader("Referer");
        if (referer == null) {
            return porDefecto;
        }
        try {
            URI uri = new URI(referer);
            String ruta = uri.getRawPath();
            if (ruta == null || !ruta.startsWith("/") || ruta.startsWith("//")) {
                return porDefecto;
            }
            return uri.getRawQuery() == null ? ruta : ruta + "?" + uri.getRawQuery();
        } catch (URISyntaxException e) {
            return porDefecto;
        }
    }
}
