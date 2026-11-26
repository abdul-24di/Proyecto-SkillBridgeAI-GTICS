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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Errores de las vistas: guarda un mensaje para el usuario y vuelve a la página anterior. */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Pattern RUTA_MARCAR_LISTO =
            Pattern.compile("^/colaborador/proyectos/actividades/(\\d+)/marcar-listo$");
    private static final String RUTA_SUBIR_DOCUMENTO = "/colaborador/proyectos/documentos/subir";

    /**
     * Avisa que el archivo no se pudo recibir y, si se conoce la pantalla, reabre su ventana.
     *
     * @param request petición HTTP
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @return redirección a la página anterior
     */
    @ExceptionHandler(MultipartException.class)
    public String subidaRechazada(HttpServletRequest request, RedirectAttributes redirectAttributes) {
        String mensaje = "No se pudo recibir el archivo: es demasiado grande o no se pudo leer.";
        String ruta = request.getRequestURI();
        Matcher actividad = RUTA_MARCAR_LISTO.matcher(ruta);
        if (actividad.matches()) {
            redirectAttributes.addFlashAttribute("errorEntrega", mensaje + " El máximo permitido es 10 MB.");
            redirectAttributes.addFlashAttribute("abrirModalActividadId", Long.valueOf(actividad.group(1)));
        } else if (RUTA_SUBIR_DOCUMENTO.equals(ruta)) {
            redirectAttributes.addFlashAttribute("errorDocumento", mensaje + " El máximo permitido es 20 MB.");
            redirectAttributes.addFlashAttribute("abrirModalSubirDocumento", true);
        } else {
            redirectAttributes.addFlashAttribute("mensajeError", mensaje);
            redirectAttributes.addFlashAttribute("error", mensaje);
        }
        return "redirect:" + paginaAnterior(request, "/");
    }

    /**
     * Muestra el mensaje de una regla de negocio.
     * <p>
     * Error de negocio: recurso no encontrado o acceso denegado
     *
     * @param ex excepción capturada
     * @param request petición HTTP
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @return redirección a la página anterior
     */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public String errorNegocio(Exception ex, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", ex.getMessage());
        redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
        return "redirect:" + paginaAnterior(request, "/");
    }

    /**
     * Avisa que el usuario no tiene permiso sobre el recurso.
     * <p>
     * Intento de acceder a un recurso que no le pertenece
     *
     * @param request petición HTTP
     * @param redirectAttributes mensajes que se muestran después de redirigir
     * @return redirección a la página anterior
     */
    @ExceptionHandler(SecurityException.class)
    public String errorSeguridad(HttpServletRequest request, RedirectAttributes redirectAttributes) {
        String mensaje = "No tienes permiso para realizar esta acción.";
        redirectAttributes.addFlashAttribute("error", mensaje);
        redirectAttributes.addFlashAttribute("mensajeError", mensaje);
        return "redirect:" + paginaAnterior(request, "/");
    }

    /**
     * Devuelve el mensaje de la excepción solo si está escrito para el usuario.
     * <p>
     * Los mensajes de negocio (IllegalArgumentException / IllegalStateException) están escritos para el usuario;
     * cualquier otro error (p. ej. de la base de datos) no debe filtrar detalles técnicos.
     *
     * @param e excepción capturada
     * @return mensaje para mostrar
     */
    public static String mensajeSeguro(Exception e) {
        if (e instanceof IllegalArgumentException || e instanceof IllegalStateException
                || e instanceof SecurityException) {
            return e.getMessage();
        }
        return "No se pudo completar la operación. Revisa los datos e inténtalo de nuevo.";
    }

    /**
     * Obtiene la ruta de la página desde la que se envió la petición.
     * <p>
     * El Referer lo envía el cliente y puede traer cualquier cosa: solo se usa su ruta, para no redirigir nunca a
     * otro sitio. Si no se puede leer, se vuelve a la página por defecto.
     *
     * @param request petición HTTP
     * @param porDefecto página a la que se vuelve si no se conoce la anterior
     * @return ruta interna, o la página por defecto
     */
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
