package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Foro;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.NivelDominio;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.col.*;
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
    private final ColaboradorForoService colaboradorForoService;
    private final ColaboradorCursoService colaboradorCursoService;
    private final ColaboradorActividadService colaboradorActividadService;

    public ColaboradorViewController(ColaboradorPerfilService colaboradorPerfilService,
                                     ColaboradorProyectoService colaboradorProyectoService,
                                     ColaboradorExplorarService colaboradorExplorarService,
                                     ColaboradorCursoService colaboradorCursoService,
                                     ColChatService colChatService,
                                     ColaboradorForoService colaboradorForoService,
                                     ColaboradorActividadService colaboradorActividadService) {
        this.colaboradorPerfilService = colaboradorPerfilService;
        this.colaboradorProyectoService = colaboradorProyectoService;
        this.colaboradorExplorarService = colaboradorExplorarService;
        this.colaboradorCursoService = colaboradorCursoService;
        this.colChatService = colChatService;
        this.colaboradorForoService = colaboradorForoService;
        this.colaboradorActividadService = colaboradorActividadService;
    }

    @GetMapping({"", "/"})
    public String index() {
        return "redirect:/colaborador/dashboard";
    }

    @GetMapping({"/dashboard", "/col-dashboard.html"})
    public String dashboard(@AuthenticationPrincipal UsuarioDetails principal, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        Usuario colaborador = principal.getUsuario();

        List<Asignacion> misAsignaciones = colaboradorProyectoService.listarMisAsignaciones(colaborador);
        int proyectosActivos = 0;
        int proyectosFinalizados = 0;
        for (Asignacion asignacion : misAsignaciones) {
            if (asignacion.getEstado() == com.pucp.skillb_ia.model.enums.EstadoAsignacion.ACTIVA) {
                proyectosActivos++;
            }
            if (asignacion.getEstado() == com.pucp.skillb_ia.model.enums.EstadoAsignacion.FINALIZADA) {
                proyectosFinalizados++;
            }
        }

        List<com.pucp.skillb_ia.model.Actividad> actividadesPendientes = colaboradorActividadService.listarActividadesPendientes(colaborador);

        model.addAttribute("proyectosActivos", proyectosActivos);
        model.addAttribute("proyectosFinalizados", proyectosFinalizados);
        model.addAttribute("totalProyectos", proyectosActivos + proyectosFinalizados);
        model.addAttribute("actividadesPendientes", actividadesPendientes);
        model.addAttribute("totalActividadesPendientes", actividadesPendientes.size());
        model.addAttribute("porcentajePerfil", colaboradorPerfilService.calcularPorcentajeCompletado(colaborador));
        model.addAttribute("proyectosConAvance", colaboradorActividadService.listarProyectosActivosConAvance(colaborador, misAsignaciones));

        return "col/col-dashboard";
    }

    @GetMapping({"/proyectos", "/col-proyectos.html"})
    public String projects(@AuthenticationPrincipal UsuarioDetails principal, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }

        List<Asignacion> misAsignaciones = colaboradorProyectoService.listarMisAsignaciones(principal.getUsuario());
        model.addAttribute("misAsignaciones", misAsignaciones);
        return "col/col-proyectos";
    }

    @GetMapping({"/proyectos/detalle", "/col-detalle-proyecto.html"})
    public String projectDetail(@AuthenticationPrincipal UsuarioDetails principal,
                                @RequestParam(required = false) Long asignacionId,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";

        if (asignacionId == null) {
            redirectAttributes.addFlashAttribute("mensajeError", "No se indicó qué proyecto quieres ver.");
            return "redirect:/colaborador/proyectos";
        }

        try {
            var detalle = colaboradorProyectoService.obtenerDetalleProyecto(principal.getUsuario(), asignacionId);
            model.addAttribute("proyecto", detalle.getProyecto());
            model.addAttribute("asignacion", detalle.getAsignacion());
            model.addAttribute("integrantes", detalle.getIntegrantes());
            model.addAttribute("actividadesDelProyecto", detalle.getActividadesDelProyecto());
            model.addAttribute("misActividades", detalle.getMisActividades());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
            return "redirect:/colaborador/proyectos";
        }

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
        if (principal == null) {
            return "redirect:/login";
        }
        Usuario colaborador = principal.getUsuario();
        model.addAttribute("colaborador", colaborador);
        model.addAttribute("forosComunidad", colaboradorForoService.listarForosComunidad());
        model.addAttribute("forosMisProyectos", colaboradorForoService.listarForosDeMisProyectos(colaborador));
        model.addAttribute("forosParaPublicar", colaboradorForoService.listarForosParaPublicar(colaborador));
        model.addAttribute("etiquetas", colaboradorForoService.listarEtiquetas());
        return "col/col-foros";
    }

    @GetMapping({"/foros/detalle", "/col-foro-detalle.html"})
    public String foroDetalle(@RequestParam("foroId") Long foroId,
                              @AuthenticationPrincipal UsuarioDetails principal, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        Usuario colaborador = principal.getUsuario();
        try {
            Foro foro = colaboradorForoService.obtenerForo(colaborador, foroId);
            model.addAttribute("colaborador", colaborador);
            model.addAttribute("foro", foro);
            model.addAttribute("publicaciones", colaboradorForoService.listarPublicacionesConRespuestas(colaborador, foroId));
            model.addAttribute("etiquetas", colaboradorForoService.listarEtiquetas());
            model.addAttribute("puedeParticipar", colaboradorForoService.puedeParticipar(colaborador, foro));
            return "col/col-foro-detalle";
        } catch (IllegalArgumentException e) {
            return "redirect:/colaborador/foros";
        }
    }

    @PostMapping("/foro/publicar")
    public String publicarForo(@RequestParam("foroId") Long foroId,
                               @RequestParam("titulo") String titulo,
                               @RequestParam("contenido") String contenido,
                               @RequestParam(value = "etiquetaId", required = false) Long etiquetaId,
                               @AuthenticationPrincipal UsuarioDetails principal,
                               RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorForoService.crearPublicacion(principal.getUsuario(), foroId, titulo, contenido, etiquetaId);
            redirectAttributes.addFlashAttribute("mensajeExito", "Publicación creada correctamente.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/colaborador/foros/detalle?foroId=" + foroId;
    }

    @PostMapping("/foro/publicacion/{publicacionId}/eliminar")
    public String eliminarPublicacionForo(@PathVariable Long publicacionId,
                                          @RequestParam("foroId") Long foroId,
                                          @AuthenticationPrincipal UsuarioDetails principal,
                                          RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorForoService.eliminarPublicacion(principal.getUsuario(), publicacionId);
            redirectAttributes.addFlashAttribute("mensajeExito", "Publicación eliminada.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/colaborador/foros/detalle?foroId=" + foroId;
    }

    @PostMapping("/foro/responder")
    public String responderForo(@RequestParam("publicacionId") Long publicacionId,
                                @RequestParam("foroId") Long foroId,
                                @RequestParam("contenido") String contenido,
                                @AuthenticationPrincipal UsuarioDetails principal,
                                RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorForoService.crearRespuesta(principal.getUsuario(), publicacionId, contenido);
            redirectAttributes.addFlashAttribute("mensajeExito", "Respuesta publicada.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/colaborador/foros/detalle?foroId=" + foroId;
    }

    @PostMapping("/foro/respuesta/{respuestaId}/eliminar")
    public String eliminarRespuestaForo(@PathVariable Long respuestaId,
                                        @RequestParam("foroId") Long foroId,
                                        @AuthenticationPrincipal UsuarioDetails principal,
                                        RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorForoService.eliminarRespuesta(principal.getUsuario(), respuestaId);
            redirectAttributes.addFlashAttribute("mensajeExito", "Respuesta eliminada.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/colaborador/foros/detalle?foroId=" + foroId;
    }

    @PostMapping("/foro/publicacion/{publicacionId}/like")
    public String likePublicacion(@PathVariable Long publicacionId,
                                  @RequestParam("foroId") Long foroId,
                                  @AuthenticationPrincipal UsuarioDetails principal,
                                  RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorForoService.alternarLikePublicacion(principal.getUsuario(), publicacionId);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/colaborador/foros/detalle?foroId=" + foroId;
    }

    @PostMapping("/foro/respuesta/{respuestaId}/like")
    public String likeRespuesta(@PathVariable Long respuestaId,
                                @RequestParam("foroId") Long foroId,
                                @AuthenticationPrincipal UsuarioDetails principal,
                                RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorForoService.alternarLikeRespuesta(principal.getUsuario(), respuestaId);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/colaborador/foros/detalle?foroId=" + foroId;
    }

    @PostMapping("/foro/upload-imagen")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, String> uploadImagenForo(@RequestParam("image") MultipartFile image) {
        try {
            String url = colaboradorForoService.subirImagenForo(image);
            return java.util.Map.of("url", url);
        } catch (IllegalArgumentException e) {
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
        model.addAttribute("misHabilidades", colaboradorPerfilService.listarHabilidades(colaborador));

        model.addAttribute("cursosDisponibles", colaboradorCursoService.listarCursosDisponibles(colaborador));
        model.addAttribute("misSolicitudesCursos", colaboradorCursoService.listarMisSolicitudes(colaborador));
        model.addAttribute("categoriasCurso", colaboradorCursoService.listarCategorias());


        List<Usuario> colaboradoresExplorar = colaboradorExplorarService.listarColaboradores(colaborador);
        model.addAttribute("colaboradoresExplorar", colaboradoresExplorar);
        model.addAttribute("perfilesExplorar", colaboradorExplorarService.obtenerPerfiles(colaboradoresExplorar));

        return "col/col-explorar";
    }

    @PostMapping("/proyectos/solicitar")
    public String solicitarIncorporacion(@AuthenticationPrincipal UsuarioDetails principal,
                                         @RequestParam Long proyectoId,
                                         @RequestParam Long habilidadId,
                                         @RequestParam(required = false) String mensaje,
                                         @RequestParam(required = false) String habilidadesRelevantes,
                                         RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorProyectoService.solicitarIncorporacion(principal.getUsuario(), proyectoId, habilidadId, mensaje, habilidadesRelevantes);
            redirectAttributes.addFlashAttribute("mensajeExito", "Tu solicitud fue enviada. Quedará pendiente de aprobación del PM y del RM.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/colaborador/explorar";
    }

    @PostMapping("/cursos/solicitar")
    public String solicitarCurso(@AuthenticationPrincipal UsuarioDetails principal,
                                 @RequestParam Long cursoId,
                                 @RequestParam(required = false) String justificacion,
                                 RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorCursoService.solicitarInscripcion(principal.getUsuario(), cursoId, justificacion);
            redirectAttributes.addFlashAttribute("mensajeExito", "Tu solicitud de inscripción fue enviada. Quedará pendiente de aprobación del Resource Manager.");
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
        model.addAttribute("certificadosColaborador", colaboradorPerfilService.listarCertificados(colaborador));
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