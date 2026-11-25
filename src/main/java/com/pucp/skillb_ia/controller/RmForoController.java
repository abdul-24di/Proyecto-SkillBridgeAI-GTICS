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

    public RmForoController(PmForoService pmForoService) {
        this.pmForoService = pmForoService;
    }

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

    @PostMapping("/publicacion/{id}/like")
    public String likePublicacion(@PathVariable Long id, @RequestParam(required = false) Long foroId,
                                  @AuthenticationPrincipal UsuarioDetails principal, RedirectAttributes ra) {
        return ejecutar(() -> pmForoService.alternarLikePublicacion(principal.getUsuario(), id), null, foroId, ra);
    }

    @PostMapping("/respuesta/{id}/like")
    public String likeRespuesta(@PathVariable Long id, @RequestParam(required = false) Long foroId,
                                @AuthenticationPrincipal UsuarioDetails principal, RedirectAttributes ra) {
        return ejecutar(() -> pmForoService.alternarLikeRespuesta(principal.getUsuario(), id), null, foroId, ra);
    }

    @PostMapping("/respuesta/{id}/marcar-solucion")
    public String marcarSolucion(@PathVariable Long id, @RequestParam(required = false) Long foroId,
                                 @AuthenticationPrincipal UsuarioDetails principal, RedirectAttributes ra) {
        return ejecutar(() -> pmForoService.alternarSolucion(principal.getUsuario(), id), null, foroId, ra);
    }

    @PostMapping("/publicacion/{id}/eliminar")
    public String eliminarPublicacion(@PathVariable Long id, @RequestParam(required = false) Long foroId,
                                      @AuthenticationPrincipal UsuarioDetails principal, RedirectAttributes ra) {
        return ejecutar(() -> pmForoService.eliminarPublicacion(principal.getUsuario(), id),
                "Publicación eliminada.", foroId, ra);
    }

    @PostMapping("/respuesta/{id}/eliminar")
    public String eliminarRespuesta(@PathVariable Long id, @RequestParam(required = false) Long foroId,
                                    @AuthenticationPrincipal UsuarioDetails principal, RedirectAttributes ra) {
        return ejecutar(() -> pmForoService.eliminarRespuesta(principal.getUsuario(), id),
                "Respuesta eliminada.", foroId, ra);
    }

    @PostMapping("/upload-imagen")
    @ResponseBody
    public Map<String, String> subirImagen(@RequestParam("image") MultipartFile image) {
        try {
            return Map.of("url", pmForoService.subirImagen(image));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensajeSeguro(e));
        }
    }

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

    private static String detalle(Long foroId) {
        return foroId == null ? "redirect:/rm/foros" : "redirect:/rm/foros/detalle?id=" + foroId;
    }
}
