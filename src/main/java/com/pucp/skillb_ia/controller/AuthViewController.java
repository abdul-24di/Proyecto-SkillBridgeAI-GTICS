package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.dto.auth.ActivarCuentaForm;
import com.pucp.skillb_ia.dto.auth.CertificadoPreRegistroForm;
import com.pucp.skillb_ia.dto.auth.EstudioPreRegistroForm;
import com.pucp.skillb_ia.dto.auth.ExperienciaForm;
import com.pucp.skillb_ia.dto.auth.NuevaContrasenaForm;
import com.pucp.skillb_ia.model.TokenUsuario;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.service.AuthService;
import com.pucp.skillb_ia.service.col.ColaboradorPerfilService;
import com.pucp.skillb_ia.service.col.PreRegistroColaboradorService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

// Épica 2 — Autenticación. El login en sí lo maneja Spring Security
// (SecurityConfig.formLogin, sin método en este controller) — acá va todo lo
// demás del flujo: activación de cuenta (A6) y recuperación de contraseña.
// Los formularios de activación y nueva contraseña validan con Bean Validation
// (@Valid + BindingResult), igual que el resto del sistema.
@Controller
public class AuthViewController {

    private final AuthService authService;
    private final PreRegistroColaboradorService preRegistroColaboradorService;
    private final ColaboradorPerfilService colaboradorPerfilService;

    public AuthViewController(AuthService authService,
                              PreRegistroColaboradorService preRegistroColaboradorService,
                              ColaboradorPerfilService colaboradorPerfilService) {
        this.authService = authService;
        this.preRegistroColaboradorService = preRegistroColaboradorService;
        this.colaboradorPerfilService = colaboradorPerfilService;
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
    // ACTIVACIÓN DE CUENTA (y pre-registro del colaborador)
    // ============================================================

    @GetMapping({"/activar-cuenta", "/activar-cuenta.html"})
    public String activarCuentaForm(@RequestParam(required = false) String token, Model model) {
        ActivarCuentaForm form = new ActivarCuentaForm();
        form.setToken(token);
        agregarFilasEnBlanco(form);
        model.addAttribute("activarCuentaForm", form);
        cargarDatosActivacion(token, model);
        return "auth/activar-cuenta";
    }

    @PostMapping("/activar-cuenta")
    public String activarCuentaSubmit(@Valid @ModelAttribute("activarCuentaForm") ActivarCuentaForm form,
                                       BindingResult result, Model model) {
        // Pre-registro: CV, experiencia, certificados y formación solo se piden a los colaboradores.
        AuthService.ResultadoToken resultado = authService.validarTokenActivacion(form.getToken());
        boolean esColaborador = resultado.token() != null
                && "COLABORADOR".equals(resultado.token().getUsuario().getRol().getNombre());
        if (esColaborador) {
            preRegistroColaboradorService.validar(form, result);
        }
        if (result.hasErrors()) {
            cargarDatosActivacion(form.getToken(), model);
            agregarFilasEnBlanco(form);
            return "auth/activar-cuenta";
        }
        try {
            Usuario usuario = authService.activarCuenta(form.getToken(), form.getNombreCompleto(),
                    form.getTelefono(), form.getDescripcion(), form.getPassword());
            if (esColaborador) {
                preRegistroColaboradorService.guardar(usuario, form);
            }
        } catch (IllegalStateException e) {
            // El enlace expiró o ya se usó entre que se abrió el form y se envió:
            // el GET vuelve a mostrar el estado correcto.
            return "redirect:/activar-cuenta?token=" + form.getToken();
        }
        return "redirect:/login?activado";
    }

    // Cada sección dinámica del formulario necesita al menos una fila para mostrarse.
    private void agregarFilasEnBlanco(ActivarCuentaForm form) {
        if (form.getExperiencias().isEmpty()) form.getExperiencias().add(new ExperienciaForm());
        if (form.getCertificados().isEmpty()) form.getCertificados().add(new CertificadoPreRegistroForm());
        if (form.getEstudios().isEmpty()) form.getEstudios().add(new EstudioPreRegistroForm());
    }

    private void cargarDatosActivacion(String token, Model model) {
        AuthService.ResultadoToken resultado = authService.validarTokenActivacion(token);
        model.addAttribute("estado", resultado.estado());
        model.addAttribute("token", token);
        if (resultado.token() != null) {
            model.addAttribute("correo", resultado.token().getUsuario().getCorreo());
            model.addAttribute("rolNombre", resultado.token().getUsuario().getRol().getNombre());
        }
        // Catálogo para el selector de habilidades de los certificados.
        model.addAttribute("habilidadesCatalogo", colaboradorPerfilService.listarHabilidadesActivas());
        model.addAttribute("niveles", com.pucp.skillb_ia.model.enums.NivelDominio.values());
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
        NuevaContrasenaForm form = new NuevaContrasenaForm();
        form.setToken(token);
        model.addAttribute("nuevaContrasenaForm", form);
        return "auth/nueva-contrasena";
    }

    @PostMapping("/nueva-contrasena")
    public String nuevaContrasenaSubmit(@Valid @ModelAttribute("nuevaContrasenaForm") NuevaContrasenaForm form,
                                         BindingResult result) {
        if (result.hasErrors()) {
            return "auth/nueva-contrasena";
        }
        try {
            authService.restablecerContrasena(form.getToken(), form.getPassword());
        } catch (IllegalStateException e) {
            return "redirect:/nueva-contrasena?token=" + form.getToken() + "&error";
        }
        return "redirect:/login?recuperado";
    }

    @GetMapping({"/contrasena-actualizada", "/contrasena-actualizada.html"})
    public String contrasenaActualizada() {
        return "auth/contrasena-actualizada";
    }
}
