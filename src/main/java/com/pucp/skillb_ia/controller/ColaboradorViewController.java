package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.NivelDominio;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.col.ColaboradorPerfilService;
import com.pucp.skillb_ia.service.col.ColaboradorProyectoService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/colaborador")
public class ColaboradorViewController {

    private final ColaboradorPerfilService colaboradorPerfilService;
    private final ColaboradorProyectoService colaboradorProyectoService;

    public ColaboradorViewController(ColaboradorPerfilService colaboradorPerfilService,
                                     ColaboradorProyectoService colaboradorProyectoService) {
        this.colaboradorPerfilService = colaboradorPerfilService;
        this.colaboradorProyectoService = colaboradorProyectoService;
    }

    @GetMapping({"", "/"})
    public String index() {
        return "redirect:/colaborador/dashboard";
    }

    @GetMapping({"/dashboard", "/col-dashboard.html"})
    public String dashboard() {
        return "col/col-dashboard";
    }

    @GetMapping({"/proyectos", "/col-proyectos.html"})
    public String projects(@AuthenticationPrincipal UsuarioDetails principal, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        model.addAttribute("misAsignaciones", colaboradorProyectoService.listarMisAsignaciones(principal.getUsuario()));
        return "col/col-proyectos";
    }

    @GetMapping({"/proyectos/detalle", "/col-detalle-proyecto.html"})
    public String projectDetail() {
        return "col/col-detalle-proyecto";
    }

    @GetMapping({"/foros", "/col-foros.html"})
    public String forums() {
        return "col/col-foros";
    }

    @GetMapping({"/asistente", "/col-asistente.html"})
    public String assistant() {
        return "col/col-asistente";
    }

    @GetMapping({"/explorar", "/col-explorar.html"})
    public String explore(@AuthenticationPrincipal UsuarioDetails principal, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        Usuario colaborador = principal.getUsuario();
        model.addAttribute("proyectosDisponibles", colaboradorProyectoService.listarProyectosDisponibles(colaborador));
        model.addAttribute("misSolicitudes", colaboradorProyectoService.listarMisSolicitudes(colaborador));
        return "col/col-explorar";
    }

    @PostMapping("/proyectos/solicitar")
    public String solicitarIncorporacion(@AuthenticationPrincipal UsuarioDetails principal,
                                         @RequestParam Long proyectoId,
                                         @RequestParam(required = false) String mensaje,
                                         RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorProyectoService.solicitarIncorporacion(principal.getUsuario(), proyectoId, mensaje);
            redirectAttributes.addFlashAttribute("mensajeExito", "Tu solicitud fue enviada. Quedará pendiente de aprobación del PM y del RM.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/colaborador/explorar";
    }

    // ============================================================
    // PERFIL
    // ============================================================

    @GetMapping({"/perfil", "/col-perfil.html"})
    public String profile(@AuthenticationPrincipal UsuarioDetails principal, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        Usuario colaborador = principal.getUsuario();

        model.addAttribute("colaborador", colaborador);
        model.addAttribute("habilidadesColaborador", colaboradorPerfilService.listarHabilidades(colaborador));
        model.addAttribute("habilidadesDisponibles", colaboradorPerfilService.listarHabilidadesDisponibles(colaborador));
        model.addAttribute("certificadosColaborador", colaboradorPerfilService.listarCertificados(colaborador));
        model.addAttribute("nivelesDominio", NivelDominio.values());
        model.addAttribute("educacionColaborador", colaboradorPerfilService.listarEducacion(colaborador));
        model.addAttribute("porcentajeCompletado", colaboradorPerfilService.calcularPorcentajeCompletado(colaborador));
        model.addAttribute("pendientesCompletar", colaboradorPerfilService.listarPendientesCompletar(colaborador));

        return "col/col-perfil";
    }

    @PostMapping("/perfil/sobre-mi")
    public String actualizarSobreMi(@AuthenticationPrincipal UsuarioDetails principal,
                                    @RequestParam String descripcion,
                                    RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorPerfilService.actualizarSobreMi(principal.getUsuario(), descripcion);
            redirectAttributes.addFlashAttribute("mensajeExito", "Tu descripción se actualizó correctamente.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/colaborador/perfil";
    }

    @PostMapping("/perfil/foto")
    public String actualizarFoto(@AuthenticationPrincipal UsuarioDetails principal,
                                 @RequestParam("foto") MultipartFile foto,
                                 RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorPerfilService.actualizarFoto(principal.getUsuario(), foto);
            redirectAttributes.addFlashAttribute("mensajeExito", "Tu foto de perfil se actualizó correctamente.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/colaborador/perfil?tab=cuenta";
    }

    @PostMapping("/perfil/password")
    public String cambiarPassword(@AuthenticationPrincipal UsuarioDetails principal,
                                  @RequestParam String passwordActual,
                                  @RequestParam String passwordNueva,
                                  @RequestParam String passwordConfirmar,
                                  RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorPerfilService.cambiarPassword(principal.getUsuario(), passwordActual, passwordNueva, passwordConfirmar);
            redirectAttributes.addFlashAttribute("mensajeExito", "Tu contraseña se actualizó correctamente.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/colaborador/perfil?tab=cuenta";
    }

    @PostMapping("/perfil/habilidades")
    public String agregarHabilidad(@AuthenticationPrincipal UsuarioDetails principal,
                                   @RequestParam Long habilidadId,
                                   @RequestParam NivelDominio nivel,
                                   RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorPerfilService.agregarHabilidad(principal.getUsuario(), habilidadId, nivel);
            redirectAttributes.addFlashAttribute("mensajeExito", "Habilidad agregada a tu perfil.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/colaborador/perfil";
    }

    @PostMapping("/perfil/habilidades/{habilidadId}/eliminar")
    public String eliminarHabilidad(@AuthenticationPrincipal UsuarioDetails principal,
                                    @PathVariable Long habilidadId,
                                    RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        colaboradorPerfilService.eliminarHabilidad(principal.getUsuario(), habilidadId);
        redirectAttributes.addFlashAttribute("mensajeExito", "Habilidad eliminada de tu perfil.");
        return "redirect:/colaborador/perfil";
    }

    @PostMapping("/perfil/certificados")
    public String subirCertificado(@AuthenticationPrincipal UsuarioDetails principal,
                                   @RequestParam Long habilidadId,
                                   @RequestParam("certificado") MultipartFile certificado,
                                   RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorPerfilService.subirCertificado(
                    principal.getUsuario(), habilidadId, certificado);
            redirectAttributes.addFlashAttribute("mensajeExito",
                    "Certificado enviado. El Resource Manager podrá revisarlo desde su bandeja.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/colaborador/perfil";
    }

    @PostMapping("/perfil/educacion")
    public String agregarEducacion(@AuthenticationPrincipal UsuarioDetails principal,
                                   @RequestParam String institucion,
                                   @RequestParam String titulo,
                                   @RequestParam(required = false) java.time.LocalDate fechaInicio,
                                   @RequestParam(required = false) java.time.LocalDate fechaFin,
                                   @RequestParam(required = false, defaultValue = "false") boolean actual,
                                   RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorPerfilService.agregarEducacion(principal.getUsuario(), institucion, titulo, fechaInicio, fechaFin, actual);
            redirectAttributes.addFlashAttribute("mensajeExito", "Formación académica agregada. Quedará pendiente de revisión del Administrador.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/colaborador/perfil";
    }

    @PostMapping("/perfil/educacion/{educacionId}/eliminar")
    public String eliminarEducacion(@AuthenticationPrincipal UsuarioDetails principal,
                                    @PathVariable Long educacionId,
                                    RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorPerfilService.eliminarEducacion(principal.getUsuario(), educacionId);
            redirectAttributes.addFlashAttribute("mensajeExito", "Formación académica eliminada de tu perfil.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/colaborador/perfil";
    }
}
