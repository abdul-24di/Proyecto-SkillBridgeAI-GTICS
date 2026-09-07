package com.pucp.skillb_ia.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthViewController {

    @GetMapping({"/", ""})
    public String root() {
        return "redirect:/login";
    }

    @GetMapping({"/login", "/login.html"})
    public String login() {
        return "auth/login";
    }

    @GetMapping({"/registro", "/registro.html"})
    public String registro() {
        return "auth/registro";
    }

    @GetMapping({"/activar-cuenta", "/activar-cuenta.html"})
    public String activarCuenta() {
        return "auth/activar-cuenta";
    }

    @GetMapping({"/recuperar", "/recuperar-contrasena.html"})
    public String recuperar() {
        return "auth/recuperar-contrasena";
    }

    @GetMapping({"/nueva-contrasena", "/nueva-contrasena.html"})
    public String nuevaContrasena() {
        return "auth/nueva-contrasena";
    }

    @GetMapping({"/contrasena-actualizada", "/contrasena-actualizada.html"})
    public String contrasenaActualizada() {
        return "auth/contrasena-actualizada";
    }
}