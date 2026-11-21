package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.dto.auth.ActivarCuentaForm;
import com.pucp.skillb_ia.dto.auth.CertificadoPreRegistroForm;
import com.pucp.skillb_ia.dto.auth.ErrorPreRegistro;
import com.pucp.skillb_ia.dto.auth.EstudioPreRegistroForm;
import com.pucp.skillb_ia.dto.auth.ExperienciaForm;
import com.pucp.skillb_ia.dto.auth.NuevaContrasenaForm;
import com.pucp.skillb_ia.model.TokenUsuario;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.service.AuthService;
import com.pucp.skillb_ia.service.DniService;
import com.pucp.skillb_ia.service.DniVerificacionService;
import com.pucp.skillb_ia.service.col.ColaboradorPerfilService;
import com.pucp.skillb_ia.service.col.PreRegistroArchivosService;
import com.pucp.skillb_ia.service.col.PreRegistroColaboradorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
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
    private final PreRegistroArchivosService preRegistroArchivosService;
    private final DniService dniService;
    private final DniVerificacionService dniVerificacionService;

    public AuthViewController(AuthService authService,
                              PreRegistroColaboradorService preRegistroColaboradorService,
                              ColaboradorPerfilService colaboradorPerfilService,
                              PreRegistroArchivosService preRegistroArchivosService,
                              DniService dniService,
                              DniVerificacionService dniVerificacionService) {
        this.dniService = dniService;
        this.dniVerificacionService = dniVerificacionService;
        this.authService = authService;
        this.preRegistroColaboradorService = preRegistroColaboradorService;
        this.colaboradorPerfilService = colaboradorPerfilService;
        this.preRegistroArchivosService = preRegistroArchivosService;
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
    public String activarCuentaForm(@RequestParam(required = false) String token, Model model, HttpSession session,
                                    HttpServletRequest request, HttpServletResponse response,
                                    RedirectAttributes redirectAttributes) {
        // Si en este navegador ya hay alguien con sesión iniciada (p. ej. el Admin que creó la cuenta), se cierra
        // primero. Antes, al terminar la activación el login llevaba al panel de esa otra sesión y parecía que
        // la cuenta nueva tenía otro rol.
        if (cerrarSesionAbierta(request, response)) {
            redirectAttributes.addAttribute("token", token);
            return "redirect:/activar-cuenta";
        }
        ActivarCuentaForm form = new ActivarCuentaForm();
        form.setToken(token);
        agregarFilasEnBlanco(form);
        model.addAttribute("activarCuentaForm", form);
        cargarDatosActivacion(token, model, session);
        return "auth/activar-cuenta";
    }

    @PostMapping("/activar-cuenta")
    public String activarCuentaSubmit(@Valid @ModelAttribute("activarCuentaForm") ActivarCuentaForm form,
                                       BindingResult result, Model model, HttpSession session,
                                       RedirectAttributes redirectAttributes,
                                       HttpServletRequest request, HttpServletResponse response) {
        // Sin un enlace vigente no se procesa nada (ni se guarda ningún archivo): el GET muestra el estado.
        AuthService.ResultadoToken resultado = authService.validarTokenActivacion(form.getToken());
        if (!resultado.esValido()) {
            redirectAttributes.addAttribute("token", form.getToken());
            return "redirect:/activar-cuenta";
        }

        // Pre-registro: CV, experiencia, certificados y formación solo se piden a los colaboradores.
        boolean esColaborador = "COLABORADOR".equals(resultado.token().getUsuario().getRol().getNombre());
        if (esColaborador) {
            preRegistroColaboradorService.validar(form, result, session);
        }
        // DNI: primero que no se repita (no gasta consultas); la API solo se consulta cuando el resto del formulario
        // ya está correcto, para no gastar una consulta en cada intento.
        boolean dniVerificado = false;
        if (!result.hasFieldErrors("dni")) {
            try {
                dniService.validarFormatoYUnico(form.getDni());
                if (!result.hasErrors()) {
                    dniVerificado = dniService.validar(form.getDni(), form.getNombreCompleto());
                }
            } catch (IllegalArgumentException e) {
                result.rejectValue("dni", "dni.invalido", e.getMessage());
            }
        }
        if (result.hasErrors()) {
            List<ErrorPreRegistro> errores = preRegistroColaboradorService.resumirErrores(result);
            model.addAttribute("erroresResumen", errores);
            model.addAttribute("pasoInicial", errores.isEmpty() ? 1 : errores.get(0).paso());
            cargarDatosActivacion(form.getToken(), model, session);
            agregarFilasEnBlanco(form);
            return "auth/activar-cuenta";
        }
        try {
            Usuario usuario = authService.activarCuenta(form.getToken(), form.getNombreCompleto(),
                    form.getTelefono(), form.getDescripcion(), form.getPassword());
            dniService.registrar(usuario, form.getDni(), dniVerificado);
            if (!dniVerificado) {
                dniVerificacionService.notificarSinVerificar(usuario);
            }
            if (esColaborador) {
                List<String> noGuardado = preRegistroColaboradorService.guardar(usuario, form, session);
                if (!noGuardado.isEmpty()) {
                    redirectAttributes.addFlashAttribute("avisoPreRegistro",
                            "Tu cuenta se activó, pero no se pudo guardar: " + String.join(", ", noGuardado)
                                    + ". Al ingresar, vuelve a subirlo; si el problema sigue, avisa al Administrador.");
                }
            }
        } catch (IllegalStateException e) {
            // El enlace expiró o ya se usó entre que se abrió el form y se envió:
            // el GET vuelve a mostrar el estado correcto.
            redirectAttributes.addAttribute("token", form.getToken());
            return "redirect:/activar-cuenta";
        }
        cerrarSesionAbierta(request, response);
        return "redirect:/login?activado";
    }

    // Cierra la sesión de quien esté conectado en este navegador (mismo cierre que hace Spring Security en /logout).
    // Devuelve true si había una sesión iniciada.
    private boolean cerrarSesionAbierta(HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return false;
        }
        new SecurityContextLogoutHandler().logout(request, response, auth);
        return true;
    }

    // Cada sección dinámica del formulario necesita al menos una fila para mostrarse.
    private void agregarFilasEnBlanco(ActivarCuentaForm form) {
        if (form.getExperiencias().isEmpty()) form.getExperiencias().add(new ExperienciaForm());
        if (form.getCertificados().isEmpty()) form.getCertificados().add(new CertificadoPreRegistroForm());
        if (form.getEstudios().isEmpty()) form.getEstudios().add(new EstudioPreRegistroForm());
    }

    private void cargarDatosActivacion(String token, Model model, HttpSession session) {
        AuthService.ResultadoToken resultado = authService.validarTokenActivacion(token);
        model.addAttribute("estado", resultado.estado());
        model.addAttribute("token", token);
        model.addAttribute("archivosGuardados", preRegistroArchivosService.archivos(session));
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
    public String recuperarSubmit(@RequestParam String correo, RedirectAttributes redirectAttributes) {
        authService.solicitarRecuperacion(correo);
        // El mensaje/pantalla siguiente es igual exista o no el correo — no se
        // revela si una cuenta existe en el sistema.
        redirectAttributes.addAttribute("correo", correo);
        return "redirect:/verificar-codigo";
    }

    @GetMapping({"/verificar-codigo", "/verificar-codigo.html"})
    public String verificarCodigoForm(@RequestParam String correo, Model model) {
        model.addAttribute("correo", correo);
        return "auth/verificar-codigo";
    }

    // El código verificado se guarda en la sesión (no en la URL ni en el formulario): /nueva-contrasena solo
    // cambia la contraseña de quien pasó por esta verificación con su correo.
    private static final String RECUPERACION_CORREO = "recuperacionCorreo";
    private static final String RECUPERACION_CODIGO = "recuperacionCodigo";

    @PostMapping("/verificar-codigo")
    public String verificarCodigoSubmit(@RequestParam String correo, @RequestParam String codigo, HttpSession session,
                                        RedirectAttributes redirectAttributes) {
        Optional<TokenUsuario> token = authService.validarCodigoRecuperacion(correo, codigo);
        if (token.isEmpty()) {
            redirectAttributes.addAttribute("correo", correo);
            redirectAttributes.addAttribute("error", "1");
            return "redirect:/verificar-codigo";
        }
        session.setAttribute(RECUPERACION_CORREO, correo);
        session.setAttribute(RECUPERACION_CODIGO, codigo);
        return "redirect:/nueva-contrasena";
    }

    @GetMapping({"/nueva-contrasena", "/nueva-contrasena.html"})
    public String nuevaContrasenaForm(HttpSession session, Model model) {
        if (session.getAttribute(RECUPERACION_CODIGO) == null) {
            return "redirect:/recuperar?vencido";
        }
        model.addAttribute("nuevaContrasenaForm", new NuevaContrasenaForm());
        return "auth/nueva-contrasena";
    }

    @PostMapping("/nueva-contrasena")
    public String nuevaContrasenaSubmit(@Valid @ModelAttribute("nuevaContrasenaForm") NuevaContrasenaForm form,
                                         BindingResult result, HttpSession session) {
        if (result.hasErrors()) {
            return "auth/nueva-contrasena";
        }
        String correo = (String) session.getAttribute(RECUPERACION_CORREO);
        String codigo = (String) session.getAttribute(RECUPERACION_CODIGO);
        session.removeAttribute(RECUPERACION_CORREO);
        session.removeAttribute(RECUPERACION_CODIGO);
        if (codigo == null) {
            return "redirect:/recuperar?vencido";
        }
        try {
            authService.restablecerContrasena(correo, codigo, form.getPassword());
        } catch (IllegalStateException e) {
            return "redirect:/recuperar?vencido";
        }
        return "redirect:/login?recuperado";
    }

    @GetMapping({"/contrasena-actualizada", "/contrasena-actualizada.html"})
    public String contrasenaActualizada() {
        return "auth/contrasena-actualizada";
    }
}
