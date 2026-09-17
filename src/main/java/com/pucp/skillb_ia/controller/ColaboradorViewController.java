package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.NivelDominio;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.col.ColaboradorExplorarService;
import com.pucp.skillb_ia.service.col.ColaboradorPerfilService;
import com.pucp.skillb_ia.service.col.ColaboradorProyectoService;
import com.pucp.skillb_ia.service.col.ColChatService;
import com.pucp.skillb_ia.service.col.ColForoService;
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

import java.util.List;

@Controller
@RequestMapping("/colaborador")
public class ColaboradorViewController {

    private final ColaboradorPerfilService colaboradorPerfilService;
    private final ColaboradorProyectoService colaboradorProyectoService;
    private final ColaboradorExplorarService colaboradorExplorarService;
    private final ColChatService colChatService;
    private final ColForoService colForoService;

    public ColaboradorViewController(ColaboradorPerfilService colaboradorPerfilService,
                                     ColaboradorProyectoService colaboradorProyectoService,
                                     ColaboradorExplorarService colaboradorExplorarService,
                                     ColChatService colChatService,
                                     ColForoService colForoService) {
        this.colaboradorPerfilService = colaboradorPerfilService;
        this.colaboradorProyectoService = colaboradorProyectoService;
        this.colaboradorExplorarService = colaboradorExplorarService;
        this.colChatService = colChatService;
        this.colForoService = colForoService;
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

    @GetMapping({"/chat", "/col-chat.html"})
    public String chat(@RequestParam(value = "proyectoId", required = false) Long proyectoId,
                       @AuthenticationPrincipal UsuarioDetails principal, 
                       Model model) {
        Usuario colaborador = principal.getUsuario();
        model.addAttribute("colaborador", colaborador);
        model.addAttribute("proyectos", colChatService.listarProyectosActivos(colaborador));
        model.addAttribute("proyectoIdSeleccionado", proyectoId);
        return "col/col-chat";
    }

    @GetMapping({"/foros", "/col-foros.html"})
    public String foros(@AuthenticationPrincipal UsuarioDetails principal, Model model) {
        Usuario colab = principal.getUsuario();
        model.addAttribute("colaborador", colab);
        model.addAttribute("foros", colForoService.listarForos(colab));
        return "col/col-foros";
    }

    @GetMapping({"/foros/detalle", "/col-foro-detalle.html"})
    public String foroDetalle(@RequestParam("proyectoId") Long proyectoId,
                              @AuthenticationPrincipal UsuarioDetails principal, Model model) {
        Usuario colab = principal.getUsuario();
        model.addAttribute("colaborador", colab);
        model.addAttribute("publicaciones", colForoService.obtenerDetalleForo(proyectoId, colab));
        model.addAttribute("proyectoIdSeleccionado", proyectoId);
        
        // También necesitamos el proyecto actual para mostrar el nombre.
        // Lo sacamos de la lista de asignaciones activas:
        colaboradorProyectoService.listarMisAsignaciones(colab).stream()
            .filter(a -> a.getProyecto().getId().equals(proyectoId))
            .findFirst()
            .ifPresent(a -> model.addAttribute("proyecto", a.getProyecto()));
            
        return "col/col-foro-detalle";
    }

    @PostMapping("/foro/publicar")
    public String publicarForo(@RequestParam("proyectoId") Long proyectoId,
                               @RequestParam("titulo") String titulo,
                               @RequestParam("contenido") String contenido,
                               @AuthenticationPrincipal UsuarioDetails principal,
                               RedirectAttributes ra) {
        try {
            colForoService.publicar(proyectoId, titulo, contenido, principal.getUsuario());
            ra.addFlashAttribute("success", "Publicación creada exitosamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/colaborador/foros/detalle?proyectoId=" + proyectoId;
    }

    @PostMapping("/foro/responder")
    public String responderForo(@RequestParam("publicacionId") Long publicacionId,
                                @RequestParam("proyectoId") Long proyectoId,
                                @RequestParam("contenido") String contenido,
                                @AuthenticationPrincipal UsuarioDetails principal,
                                RedirectAttributes ra) {
        try {
            colForoService.responder(publicacionId, contenido, principal.getUsuario());
            ra.addFlashAttribute("success", "Respuesta publicada.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/colaborador/foros/detalle?proyectoId=" + proyectoId;
    }

    @PostMapping("/foro/upload-imagen")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, String> uploadImagenForo(@RequestParam("image") MultipartFile image) {
        try {
            String url = colForoService.subirImagen(image);
            return java.util.Map.of("url", url);
        } catch (Exception e) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, e.getMessage());
        }
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
        model.addAttribute("proyectos", colaboradorProyectoService.listarTodosLosProyectos(colaborador));
        model.addAttribute("misSolicitudes", colaboradorProyectoService.listarMisSolicitudes(colaborador));

        List<Usuario> colaboradoresExplorar = colaboradorExplorarService.listarColaboradores(colaborador);
        model.addAttribute("colaboradoresExplorar", colaboradoresExplorar);
        model.addAttribute("perfilesExplorar", colaboradorExplorarService.obtenerPerfiles(colaboradoresExplorar));

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
        model.addAttribute("nivelesDominio", NivelDominio.values());
        model.addAttribute("categoriasHabilidad", colaboradorPerfilService.listarCategoriasHabilidad());
        model.addAttribute("educacionColaborador", colaboradorPerfilService.listarEducacion(colaborador));
        model.addAttribute("porcentajeCompletado", colaboradorPerfilService.calcularPorcentajeCompletado(colaborador));
        model.addAttribute("pendientesCompletar", colaboradorPerfilService.listarPendientesCompletar(colaborador));
        model.addAttribute("experienciaProfesional", colaboradorPerfilService.listarExperienciaProfesional(colaborador));
        model.addAttribute("historialProyectos", colaboradorProyectoService.listarHistorialProyectos(colaborador));

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

    @PostMapping("/perfil/foto/eliminar")
    public String eliminarFoto(@AuthenticationPrincipal UsuarioDetails principal,
                               RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorPerfilService.eliminarFoto(principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeExito", "Tu foto de perfil se eliminó correctamente.");
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
                                   @RequestParam(required = false) String habilidadId,
                                   @RequestParam(required = false) String nuevaHabilidadNombre,
                                   @RequestParam(required = false) String categoriaId,
                                   @RequestParam(required = false) String nivel,
                                   @RequestParam(value = "certificado", required = false) MultipartFile certificado,
                                   RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorPerfilService.agregarHabilidad(
                    principal.getUsuario(),
                    parseIdOpcional(habilidadId),
                    nuevaHabilidadNombre,
                    parseIdOpcional(categoriaId),
                    parseNivel(nivel),
                    certificado);
            redirectAttributes.addFlashAttribute("mensajeExito",
                    "Habilidad agregada. Quedará pendiente de revisión del Resource Manager.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/colaborador/perfil";
    }


    private Long parseIdOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(valor.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El valor enviado no es válido.");
        }
    }

    private NivelDominio parseNivel(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Selecciona un nivel.");
        }
        try {
            return NivelDominio.valueOf(valor.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El nivel seleccionado no es válido.");
        }
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
                                   @RequestParam("certificado") MultipartFile certificado,
                                   RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorPerfilService.agregarEducacion(
                    principal.getUsuario(), institucion, titulo, fechaInicio, fechaFin, actual, certificado);
            redirectAttributes.addFlashAttribute("mensajeExito", "Formación académica agregada. Quedará pendiente de revisión.");
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