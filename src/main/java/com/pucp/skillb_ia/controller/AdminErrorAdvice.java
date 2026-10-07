package com.pucp.skillb_ia.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Solo para las pantallas del Admin: un valor que no se puede convertir (texto en un campo numérico o de
// fecha) o un campo obligatorio ausente vuelve a la pantalla con un mensaje claro, en vez de la página de
// error 400 en blanco. Los demás roles conservan su comportamiento actual.
@ControllerAdvice(assignableTypes = AdminViewController.class)
public class AdminErrorAdvice {

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public String datoInvalido(Exception ex, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        String mensaje;
        if (ex instanceof MethodArgumentTypeMismatchException tipo) {
            mensaje = "El valor ingresado en el campo \"" + tipo.getName() + "\" no es válido.";
        } else {
            mensaje = "Falta completar el campo \""
                    + ((MissingServletRequestParameterException) ex).getParameterName() + "\".";
        }
        redirectAttributes.addFlashAttribute("mensajeError", mensaje);
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/admin/dashboard");
    }
}
