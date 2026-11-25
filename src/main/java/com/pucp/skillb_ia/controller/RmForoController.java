package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.dto.pm.PmForoPublicacionForm;
import com.pucp.skillb_ia.dto.pm.PmForoRespuestaForm;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.pm.PmForoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.stream.Collectors;

import static com.pucp.skillb_ia.controller.GlobalExceptionHandler.mensajeSeguro;

/**
 * Participación del RM en los foros del Admin: en los de comunidad publica, responde y reacciona; en los de
 * anuncios solo reacciona. Las reglas las valida {@link PmForoService}; los foros de proyecto siguen siendo de
 * lectura para el RM (las vistas están en RmViewController).
 */
@Controller
@RequestMapping("/rm/foros")
public class RmForoController {

    private final PmForoService pmForoService;

    /** Crea el componente con sus dependencias, que Spring inyecta por el constructor. */
    public RmForoController(PmForoService pmForoService) {
        this.pmForoService = pmForoService;
    }

    /**
     * Publica en un foro de comunidad. Con errores vuelve al modal y conserva lo escrito.
     *
     * @param form datos del formulario
     * @param result errores de validación del formulario
     * @param principal usuario con la sesión iniciada
     * @param ra mensajes que se muestran después de redirigir
     * @return redirección al detalle del foro
     */
    @PostMapping("/publicar")
    public String publicar(@Valid @ModelAttribute("pmForoPublicacionForm") PmForoPublicacionForm form,
                           BindingResult result,
                           @AuthenticationPrincipal UsuarioDetails principal,
                           RedirectAttributes ra) {
        if (form.getForoId() == null) {
            return "redirect:/rm/foros";
        }
        // Un editor con solo espacios o etiquetas vacías (<p><br></p>) no es contenido.
        if (!result.hasFieldErrors("contenido") && PmForoService.contenidoForoVacio(form.getContenido())) {
            result.rejectValue("contenido", "contenido.vacio", "El contenido de la publicación es obligatorio.");
        }
        if (!result.hasErrors()) {
            try {
                pmForoService.publicarEnForo(form.getForoId(), form.getTitulo(), form.getContenido(),
                        principal.getUsuario());
                ra.addFlashAttribute("success", "Publicación creada exitosamente.");
                return detalle(form.getForoId());
            } catch (Exception e) {
                ra.addFlashAttribute("error", mensajeSeguro(e));
            }
        } else {
            ra.addFlashAttribute("error", "Revisa los campos marcados para publicar.");
        }
        ra.addFlashAttribute("pmForoPublicacionForm", form);
        ra.addFlashAttribute("org.springframework.validation.BindingResult.pmForoPublicacionForm", result);
        ra.addFlashAttribute("abrirModalNuevaPublicacion", true);
        return detalle(form.getForoId());
    }

    /**
     * Responde una publicación de un foro de comunidad.
     *
     * @param form datos del formulario
     * @param result errores de validación del formulario
     * @param principal usuario con la sesión iniciada
     * @param ra mensajes que se muestran después de redirigir
     * @return redirección al detalle del foro
     */
    @PostMapping("/responder")
    public String responder(@Valid @ModelAttribute("pmForoRespuestaForm") PmForoRespuestaForm form,
                            BindingResult result,
                            @AuthenticationPrincipal UsuarioDetails principal,
                            RedirectAttributes ra) {
        if (!result.hasFieldErrors("contenido") && PmForoService.contenidoForoVacio(form.getContenido())) {
            result.rejectValue("contenido", "contenido.vacio", "El contenido de la respuesta no puede estar vacío.");
        }
        if (result.hasErrors()) {
            ra.addFlashAttribute("error", result.getFieldErrors().stream()
                    .map(error -> error.isBindingFailure() ? "Los datos enviados no son válidos." : error.getDefaultMessage())
                    .distinct().collect(Collectors.joining(" ")));
            ra.addFlashAttribute("abrirModalResponderId", form.getPublicacionId());
            return detalle(form.getForoId());
        }
        try {
            pmForoService.responder(form.getPublicacionId(), form.getContenido(), principal.getUsuario());
            ra.addFlashAttribute("success", "Respuesta publicada.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", mensajeSeguro(e));
            ra.addFlashAttribute("abrirModalResponderId", form.getPublicacionId());
        }
        return detalle(form.getForoId());
    }

    /**
     * Da o quita "me gusta" a una publicación.
     *
     * @param id id de la publicación o respuesta
     * @param foroId id del foro
     * @param principal usuario con la sesión iniciada
     * @param ra mensajes que se muestran después de redirigir
     * @return redirección al detalle del foro
     */
    @PostMapping("/publicacion/{id}/like")
    public String likePublicacion(@PathVariable Long id, @RequestParam(required = false) Long foroId,
                                  @AuthenticationPrincipal UsuarioDetails principal, RedirectAttributes ra) {
        return ejecutar(() -> pmForoService.alternarLikePublicacion(principal.getUsuario(), id), null, foroId, ra);
    }

    /**
     * Da o quita "me gusta" a una respuesta.
     *
     * @param id id de la publicación o respuesta
     * @param foroId id del foro
     * @param principal usuario con la sesión iniciada
     * @param ra mensajes que se muestran después de redirigir
     * @return redirección al detalle del foro
     */
    @PostMapping("/respuesta/{id}/like")
    public String likeRespuesta(@PathVariable Long id, @RequestParam(required = false) Long foroId,
                                @AuthenticationPrincipal UsuarioDetails principal, RedirectAttributes ra) {
        return ejecutar(() -> pmForoService.alternarLikeRespuesta(principal.getUsuario(), id), null, foroId, ra);
    }

    /**
     * Marca o desmarca la solución de una publicación propia.
     *
     * @param id id de la publicación o respuesta
     * @param foroId id del foro
     * @param principal usuario con la sesión iniciada
     * @param ra mensajes que se muestran después de redirigir
     * @return redirección al detalle del foro
     */
    @PostMapping("/respuesta/{id}/marcar-solucion")
    public String marcarSolucion(@PathVariable Long id, @RequestParam(required = false) Long foroId,
                                 @AuthenticationPrincipal UsuarioDetails principal, RedirectAttributes ra) {
        return ejecutar(() -> pmForoService.alternarSolucion(principal.getUsuario(), id), null, foroId, ra);
    }

    /**
     * Elimina una publicación propia.
     *
     * @param id id de la publicación o respuesta
     * @param foroId id del foro
     * @param principal usuario con la sesión iniciada
     * @param ra mensajes que se muestran después de redirigir
     * @return redirección al detalle del foro
     */
    @PostMapping("/publicacion/{id}/eliminar")
    public String eliminarPublicacion(@PathVariable Long id, @RequestParam(required = false) Long foroId,
                                      @AuthenticationPrincipal UsuarioDetails principal, RedirectAttributes ra) {
        return ejecutar(() -> pmForoService.eliminarPublicacion(principal.getUsuario(), id),
                "Publicación eliminada.", foroId, ra);
    }

    /**
     * Elimina una respuesta propia.
     *
     * @param id id de la publicación o respuesta
     * @param foroId id del foro
     * @param principal usuario con la sesión iniciada
     * @param ra mensajes que se muestran después de redirigir
     * @return redirección al detalle del foro
     */
    @PostMapping("/respuesta/{id}/eliminar")
    public String eliminarRespuesta(@PathVariable Long id, @RequestParam(required = false) Long foroId,
                                    @AuthenticationPrincipal UsuarioDetails principal, RedirectAttributes ra) {
        return ejecutar(() -> pmForoService.eliminarRespuesta(principal.getUsuario(), id),
                "Respuesta eliminada.", foroId, ra);
    }

    /**
     * Sube una imagen del editor.
     *
     * @param image imagen que se insertará en el contenido
     * @return JSON con la {@code url} de la imagen
     * @throws ResponseStatusException (400) si el archivo no es válido
     */
    @PostMapping("/upload-imagen")
    @ResponseBody
    public Map<String, String> subirImagen(@RequestParam("image") MultipartFile image) {
        try {
            return Map.of("url", pmForoService.subirImagen(image));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensajeSeguro(e));
        }
    }

    /**
     * Ejecuta una acción del foro y deja el mensaje de éxito o de error.
     *
     * @param accion operación a ejecutar
     * @param mensajeExito mensaje si sale bien; {@code null} para no mostrar ninguno
     * @param foroId id del foro
     * @param ra mensajes que se muestran después de redirigir
     * @return redirección al detalle del foro
     */
    private String ejecutar(Runnable accion, String mensajeExito, Long foroId, RedirectAttributes ra) {
        try {
            accion.run();
            if (mensajeExito != null) {
                ra.addFlashAttribute("success", mensajeExito);
            }
        } catch (Exception e) {
            ra.addFlashAttribute("error", mensajeSeguro(e));
        }
        return detalle(foroId);
    }

    /**
     * Arma la redirección al detalle de un foro, o a la lista si no se conoce el foro.
     *
     * @param foroId id del foro
     * @return ruta de redirección
     */
    private static String detalle(Long foroId) {
        return foroId == null ? "redirect:/rm/foros" : "redirect:/rm/foros/detalle?id=" + foroId;
    }
}
