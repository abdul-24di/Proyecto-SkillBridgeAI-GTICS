package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.model.TokenUsuario;
import com.pucp.skillb_ia.service.AuthService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

// Épica 2 — Autenticación. El login en sí lo maneja Spring Security
// (SecurityConfig.formLogin, sin método en este controller) — acá va todo lo
// demás del flujo: activación de cuenta (A6) y recuperación de contraseña.
@Controller
public class AuthViewController {

    private final AuthService authService;

    public AuthViewController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping({"/", ""})
    public String root() {
        return "redirect:/login";
    }

    @GetMapping({"/login", "/login.html"})
    public String login() {
        return "auth/login";
    }

    // ============================================================
    // ACTIVACIÓN DE CUENTA
    // ============================================================

    @GetMapping({"/activar-cuenta", "/activar-cuenta.html"})
    public String activarCuentaForm(@RequestParam(required = false) String token, Model model) {
        AuthService.ResultadoToken resultado = authService.validarTokenActivacion(token);
        model.addAttribute("estado", resultado.estado());
        model.addAttribute("token", token);
        if (resultado.token() != null) {
            model.addAttribute("correo", resultado.token().getUsuario().getCorreo());
            model.addAttribute("rolNombre", resultado.token().getUsuario().getRol().getNombre());
        }
        return "auth/activar-cuenta";
    }

    @PostMapping("/activar-cuenta")
    public String activarCuentaSubmit(@RequestParam String token,
                                       @RequestParam String nombreCompleto,
                                       @RequestParam String password,
                                       @RequestParam String confirmarPassword) {
        if (nombreCompleto.trim().length() < 3 || password.length() < 6 || !password.equals(confirmarPassword)) {
            return "redirect:/activar-cuenta?token=" + token + "&error";
        }
        try {
            authService.activarCuenta(token, nombreCompleto, password);
        } catch (IllegalStateException e) {
            return "redirect:/activar-cuenta?token=" + token + "&error";
        }
        return "redirect:/login?activado";
    }

    // ============================================================
    // RECUPERACIÓN DE CONTRASEÑA
    // ============================================================

    @GetMapping({"/recuperar", "/recuperar-contrasena.html"})
    public String recuperarForm() {
        return "auth/recuperar-contrasena";
    }

    @PostMapping("/recuperar")
    public String recuperarSubmit(@RequestParam String correo) {
        authService.solicitarRecuperacion(correo);
        // El mensaje/pantalla siguiente es igual exista o no el correo — no se
        // revela si una cuenta existe en el sistema.
        return "redirect:/verificar-codigo?correo=" + correo;
    }

    @GetMapping({"/verificar-codigo", "/verificar-codigo.html"})
    public String verificarCodigoForm(@RequestParam String correo, Model model) {
        model.addAttribute("correo", correo);
        return "auth/verificar-codigo";
    }

    @PostMapping("/verificar-codigo")
    public String verificarCodigoSubmit(@RequestParam String correo, @RequestParam String codigo) {
        Optional<TokenUsuario> token = authService.validarCodigoRecuperacion(correo, codigo);
        if (token.isEmpty()) {
            return "redirect:/verificar-codigo?correo=" + correo + "&error";
        }
        return "redirect:/nueva-contrasena?token=" + token.get().getToken();
    }

    @GetMapping({"/nueva-contrasena", "/nueva-contrasena.html"})
    public String nuevaContrasenaForm(@RequestParam String token, Model model) {
        model.addAttribute("token", token);
        return "auth/nueva-contrasena";
    }

    @PostMapping("/nueva-contrasena")
    public String nuevaContrasenaSubmit(@RequestParam String token,
                                         @RequestParam String password,
                                         @RequestParam String confirmarPassword) {
        if (password.length() < 6 || !password.equals(confirmarPassword)) {
            return "redirect:/nueva-contrasena?token=" + token + "&error";
        }
        try {
            authService.restablecerContrasena(token, password);
        } catch (IllegalStateException e) {
            return "redirect:/nueva-contrasena?token=" + token + "&error";
        }
        return "redirect:/login?recuperado";
    }

    @GetMapping({"/contrasena-actualizada", "/contrasena-actualizada.html"})
    public String contrasenaActualizada() {
        return "auth/contrasena-actualizada";
    }
}
