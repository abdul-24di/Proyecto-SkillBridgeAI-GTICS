package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.model.Habilidad;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.HabilidadRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.CierreAsignacionesService;
import com.pucp.skillb_ia.service.pm.PmActividadService;
import com.pucp.skillb_ia.service.pm.PmAsignacionService;
import com.pucp.skillb_ia.service.pm.PmChatService;
import com.pucp.skillb_ia.service.pm.PmForoService;
import com.pucp.skillb_ia.service.pm.PmPerfilService;
import com.pucp.skillb_ia.service.pm.PmProyectoService;
import com.pucp.skillb_ia.service.pm.PmReporteService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.validation.Valid;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import com.pucp.skillb_ia.dto.pm.*;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/pm")
public class PmViewController {

    private final PmProyectoService pmProyectoService;
    private final PmActividadService pmActividadService;
    private final PmAsignacionService pmAsignacionService;
    private final PmForoService pmForoService;
    private final PmReporteService pmReporteService;
    private final PmPerfilService pmPerfilService;
    private final PmChatService pmChatService;
    private final HabilidadRepository habilidadRepository;
    private final UsuarioRepository usuarioRepository;
    private final com.pucp.skillb_ia.service.pm.PmDashboardService pmDashboardService;

    public PmViewController(PmProyectoService pmProyectoService,
                            PmActividadService pmActividadService,
                            PmAsignacionService pmAsignacionService,
                            PmForoService pmForoService,
                            PmReporteService pmReporteService,
                            PmPerfilService pmPerfilService,
                            PmChatService pmChatService,
                            HabilidadRepository habilidadRepository,
                            UsuarioRepository usuarioRepository,
                            com.pucp.skillb_ia.service.pm.PmDashboardService pmDashboardService) {
        this.pmProyectoService = pmProyectoService;
        this.pmActividadService = pmActividadService;
        this.pmAsignacionService = pmAsignacionService;
        this.pmForoService = pmForoService;
        this.pmReporteService = pmReporteService;
        this.pmPerfilService = pmPerfilService;
        this.pmChatService = pmChatService;
        this.habilidadRepository = habilidadRepository;
        this.usuarioRepository = usuarioRepository;
        this.pmDashboardService = pmDashboardService;
    }

    // Disponible en el modelo de todas las páginas de este controlador (topbar).
    @org.springframework.web.bind.annotation.ModelAttribute("pm")
    public Usuario pm(@AuthenticationPrincipal UsuarioDetails principal) {
        return principal != null ? principal.getUsuario() : null;
    }

    // ═══════════════════════════════════════════════════════════
    // INDEX
    // ═══════════════════════════════════════════════════════════

    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(@AuthenticationPrincipal UsuarioDetails principal, Model model) {
        if (principal == null) return "redirect:/login";
        Usuario pm = principal.getUsuario();
        
        model.addAttribute("totalProyectos", pmDashboardService.getCountProyectosActivos(pm));
        model.addAttribute("totalActividades", pmDashboardService.getCountActividadesPendientes(pm));
        model.addAttribute("totalColaboradores", pmDashboardService.getCountColaboradoresEquipo(pm));
        model.addAttribute("totalAsignaciones", pmDashboardService.getCountAsignacionesEnRevision(pm));
        model.addAttribute("actividadesPendientes", pmDashboardService.getActividadesPendientes(pm));
        model.addAttribute("proyectosAtencion", pmDashboardService.getProyectosQueNecesitanAtencion(pm));
        
        return "pm/pm-dashboard";
    }

    // ═══════════════════════════════════════════════════════════
    // PROYECTOS
    // ═══════════════════════════════════════════════════════════

    @GetMapping({"/proyectos", "/pm-proyectos.html"})
    public String proyectos(@AuthenticationPrincipal UsuarioDetails principal, Model model) {
        Usuario pm = principal.getUsuario();
        model.addAttribute("proyectos", pmProyectoService.listar(pm));
        model.addAttribute("pm", pm);
        return "pm/pm-proyectos";
    }

    @GetMapping({"/proyectos/detalle", "/pm-detalle-proyecto.html"})
    public String detalleProyecto(@RequestParam("id") Long proyectoId,
                                  @AuthenticationPrincipal UsuarioDetails principal,
                                  Model model) {
        Usuario pm = principal.getUsuario();
        model.addAttribute("proyecto", pmProyectoService.obtener(proyectoId, pm));
        model.addAttribute("pm", pm);
        return "pm/pm-detalle-proyecto";
    }

    @GetMapping({"/proyectos/crear", "/pm-crear-proyecto.html"})
    public String crearProyectoForm(@AuthenticationPrincipal UsuarioDetails principal, Model model) {
        model.addAttribute("pmProyectoForm", new PmProyectoForm());
        model.addAttribute("habilidades", habilidadRepository.findByActivaTrue());
        model.addAttribute("pm", principal.getUsuario());
        return "pm/pm-crear-proyecto";
    }

    @PostMapping("/proyectos/crear")
    public String crearProyecto(
            @Valid @ModelAttribute("pmProyectoForm") PmProyectoForm form,
            BindingResult result,
            @AuthenticationPrincipal UsuarioDetails principal,
            Model model,
            RedirectAttributes ra, HttpServletRequest request) {
        if (result.hasErrors()) {
            model.addAttribute("habilidades", habilidadRepository.findByActivaTrue());
            model.addAttribute("pm", principal.getUsuario());
            return "pm/pm-crear-proyecto";
        }
        try {
            com.pucp.skillb_ia.model.Proyecto nuevo = pmProyectoService.crear(
                    form.getNombre(), form.getDescripcion(), form.getFechaInicio(), form.getFechaFinEstimada(),
                    form.getPrioridad(), form.getJustificacionPrioridad(), form.getPresupuestoSolicitado(), form.getJustificacionPresupuesto(),
                    form.getColaboradoresRequeridos(), null,
                    form.getHabilidadIds(), form.getNivelesRequeridos(), form.getCantidadesPersonas(), form.getHorasSemanalesHab(),
                    principal.getUsuario());
            ra.addFlashAttribute("success", "Proyecto creado exitosamente. Está pendiente de revisión por el RM.");
            return "redirect:/pm/proyectos/detalle?id=" + nuevo.getId();
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/pm/proyectos/crear";
        }
    }

    @PostMapping("/proyectos/{id}/cancelar")
    public String cancelarProyecto(@PathVariable("id") Long proyectoId,
                                   @AuthenticationPrincipal UsuarioDetails principal,
                                   RedirectAttributes ra, HttpServletRequest request) {
        try {
            var resultado = pmProyectoService.cancelar(proyectoId, principal.getUsuario());
            ra.addFlashAttribute("success", CierreAsignacionesService.mensaje("cancelado", resultado));
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/pm/proyectos";
    }

    // ═══════════════════════════════════════════════════════════
    // ASIGNACIONES
    // ═══════════════════════════════════════════════════════════

    @GetMapping({"/proyectos/asignaciones", "/pm-asignaciones-proyecto.html"})
    public String asignaciones(@RequestParam("proyectoId") Long proyectoId,
                               @AuthenticationPrincipal UsuarioDetails principal,
                               Model model) {
        Usuario pm = principal.getUsuario();
        model.addAttribute("asignaciones", pmAsignacionService.listarPorProyecto(proyectoId, pm));
        model.addAttribute("pendientesPm", pmAsignacionService.listarPendientesPm(proyectoId, pm));
        model.addAttribute("proyecto", pmProyectoService.obtener(proyectoId, pm));
        // Colaboradores disponibles para proponer (rol COLABORADOR activos)
        model.addAttribute("colaboradoresDisponibles",
                usuarioRepository.findActivosByRolNombre("COLABORADOR"));
        model.addAttribute("pm", pm);
        return "pm/pm-asignaciones-proyecto";
    }

    @PostMapping("/asignaciones/proponer")
    public String proponerAsignacion(
            @RequestParam("proyectoId") Long proyectoId,
            @RequestParam("colaboradorId") Long colaboradorId,
            @RequestParam("horasSemanales") BigDecimal horasSemanales,
            @RequestParam(value = "mensajeSolicitud", required = false) String mensaje,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes ra, HttpServletRequest request) {
        try {
            pmAsignacionService.proponer(proyectoId, colaboradorId, horasSemanales, mensaje,
                    principal.getUsuario());
            ra.addFlashAttribute("success", "Propuesta enviada al RM para aprobación.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/pm/proyectos/asignaciones?proyectoId=" + proyectoId;
    }

    @PostMapping("/asignaciones/{id}/aprobar")
    public String aprobarAsignacion(@PathVariable("id") Long asignacionId,
                                    @RequestParam("proyectoId") Long proyectoId,
                                    @AuthenticationPrincipal UsuarioDetails principal,
                                    RedirectAttributes ra, HttpServletRequest request) {
        try {
            pmAsignacionService.aprobar(asignacionId, principal.getUsuario());
            ra.addFlashAttribute("success", "Asignación aprobada.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/pm/proyectos/asignaciones?proyectoId=" + proyectoId;
    }

    @PostMapping("/asignaciones/{id}/rechazar")
    public String rechazarAsignacion(@PathVariable("id") Long asignacionId,
                                     @RequestParam("proyectoId") Long proyectoId,
                                     @RequestParam(value = "motivo", required = false) String motivo,
                                     @AuthenticationPrincipal UsuarioDetails principal,
                                     RedirectAttributes ra, HttpServletRequest request) {
        try {
            pmAsignacionService.rechazar(asignacionId, motivo, principal.getUsuario());
            ra.addFlashAttribute("success", "Asignación rechazada.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/pm/proyectos/asignaciones?proyectoId=" + proyectoId;
    }

    @PostMapping("/asignaciones/{id}/finalizar")
    public String finalizarAsignacion(@PathVariable("id") Long asignacionId,
                                      @RequestParam("proyectoId") Long proyectoId,
                                      @RequestParam(name = "calificacion", required = false) Integer calificacion,
                                      @RequestParam(name = "feedback", required = false) String feedback,
                                      @AuthenticationPrincipal UsuarioDetails principal,
                                      RedirectAttributes ra, HttpServletRequest request) {
        try {
            pmAsignacionService.finalizar(asignacionId, calificacion, feedback, principal.getUsuario());
            ra.addFlashAttribute("success", "Asignación finalizada.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/pm/proyectos/asignaciones?proyectoId=" + proyectoId;
    }

    // ═══════════════════════════════════════════════════════════
    // ACTIVIDADES
    // ═══════════════════════════════════════════════════════════

    @GetMapping({"/actividades", "/pm-actividades.html"})
    public String actividades(@RequestParam("proyectoId") Long proyectoId,
                              @AuthenticationPrincipal UsuarioDetails principal,
                              Model model) {
        Usuario pm = principal.getUsuario();
        model.addAttribute("actividades", pmActividadService.listarPorProyecto(proyectoId, pm));
        model.addAttribute("proyecto", pmProyectoService.obtener(proyectoId, pm));
        // Colaboradores activos del proyecto para asignar actividades
        model.addAttribute("colaboradoresActivos",
                pmAsignacionService.listarPorProyecto(proyectoId, pm)
                        .stream()
                        .filter(a -> "Activa".equals(a.getEstadoTexto()))
                        .toList());
        model.addAttribute("pm", pm);
        return "pm/pm-actividades";
    }

    @PostMapping("/actividades/crear")
    public String crearActividad(
            @RequestParam("proyectoId") Long proyectoId,
            @RequestParam("colaboradorId") Long colaboradorId,
            @RequestParam("titulo") String titulo,
            @RequestParam(value = "descripcion", required = false) String descripcion,
            @RequestParam("horasEstimadas") BigDecimal horasEstimadas,
            @RequestParam("fechaLimite") String fechaLimiteStr,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes ra, HttpServletRequest request) {
        try {
            LocalDate fechaLimite = LocalDate.parse(fechaLimiteStr);
            pmActividadService.crear(proyectoId, colaboradorId, titulo, descripcion,
                    horasEstimadas, fechaLimite, principal.getUsuario());
            ra.addFlashAttribute("success", "Actividad creada correctamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/pm/actividades?proyectoId=" + proyectoId;
    }

    @PostMapping("/actividades/{id}/confirmar")
    public String confirmarActividad(@PathVariable("id") Long actividadId,
                                     @RequestParam("proyectoId") Long proyectoId,
                                     @RequestParam(value = "aplicarStrike", defaultValue = "false") boolean aplicarStrike,
                                     @AuthenticationPrincipal UsuarioDetails principal,
                                     RedirectAttributes ra, HttpServletRequest request) {
        try {
            pmActividadService.confirmar(actividadId, aplicarStrike, principal.getUsuario());
            ra.addFlashAttribute("success", "Entrega confirmada exitosamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/pm/actividades?proyectoId=" + proyectoId;
    }

    @PostMapping("/actividades/{id}/devolver")
    public String devolverActividad(@PathVariable("id") Long actividadId,
                                    @RequestParam("proyectoId") Long proyectoId,
                                    @RequestParam(value = "comentario", required = false) String comentario,
                                    @RequestParam(value = "aplicarStrike", defaultValue = "false") boolean aplicarStrike,
                                    @AuthenticationPrincipal UsuarioDetails principal,
                                    RedirectAttributes ra, HttpServletRequest request) {
        try {
            if (comentario == null || comentario.isBlank()) {
                throw new IllegalArgumentException("Debes escribir un comentario para devolver la actividad.");
            }
            pmActividadService.devolver(actividadId, comentario, aplicarStrike, principal.getUsuario());
            ra.addFlashAttribute("success", "Actividad devuelta al colaborador.");
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(getClass()).error("Error al devolver actividad {}", actividadId, e);
            ra.addFlashAttribute("error", e.getMessage() != null ? e.getMessage() : "No se pudo devolver la actividad.");
        }
        return "redirect:/pm/actividades?proyectoId=" + proyectoId;
    }

    @PostMapping("/actividades/{id}/editar")
    public String editarActividad(@PathVariable("id") Long actividadId,
                                  @RequestParam("proyectoId") Long proyectoId,
                                  @RequestParam("titulo") String titulo,
                                  @RequestParam(value = "descripcion", required = false) String descripcion,
                                  @RequestParam("horasEstimadas") java.math.BigDecimal horasEstimadas,
                                  @RequestParam("fechaLimite") String fechaLimiteStr,
                                  @AuthenticationPrincipal UsuarioDetails principal,
                                  RedirectAttributes ra, HttpServletRequest request) {
        try {
            java.time.LocalDate fechaLimite = java.time.LocalDate.parse(fechaLimiteStr);
            pmActividadService.editar(actividadId, titulo, descripcion, horasEstimadas, fechaLimite, principal.getUsuario());
            ra.addFlashAttribute("success", "Actividad editada correctamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/pm/actividades?proyectoId=" + proyectoId;
    }

    @PostMapping("/actividades/{id}/eliminar")
    public String eliminarActividad(@PathVariable("id") Long actividadId,
                                    @RequestParam("proyectoId") Long proyectoId,
                                    @AuthenticationPrincipal UsuarioDetails principal,
                                    RedirectAttributes ra, HttpServletRequest request) {
        try {
            pmActividadService.eliminar(actividadId, principal.getUsuario());
            ra.addFlashAttribute("success", "Actividad eliminada correctamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/pm/actividades?proyectoId=" + proyectoId;
    }

    // ═══════════════════════════════════════════════════════════
    // FOROS
    // ═══════════════════════════════════════════════════════════

    @GetMapping({"/foros", "/pm-foros.html"})
    public String foros(@AuthenticationPrincipal UsuarioDetails principal, Model model) {
        Usuario pm = principal.getUsuario();
        model.addAttribute("foros", pmForoService.listarForosPm(pm));
        model.addAttribute("forosComunidad", pmForoService.listarForosComunidad());
        model.addAttribute("pm", pm);
        return "pm/pm-foros";
    }

    @GetMapping({"/foro/detalle", "/pm-foro-detalle.html"})
    public String foroDetalle(@RequestParam(value = "proyectoId", required = false) Long proyectoId,
                              @RequestParam(value = "foroId", required = false) Long foroId,
                              @AuthenticationPrincipal UsuarioDetails principal,
                              Model model) {
        Usuario pm = principal.getUsuario();
        if (foroId != null) {
            model.addAttribute("publicaciones", pmForoService.obtenerDetallePorForo(foroId, pm));
            model.addAttribute("foroBase", pmForoService.obtenerForoBase(foroId));
            model.addAttribute("pm", pm);
            return "pm/pm-foro-detalle";
        } else if (proyectoId != null) {
            model.addAttribute("publicaciones", pmForoService.obtenerDetalle(proyectoId, pm));
            model.addAttribute("proyecto", pmProyectoService.obtener(proyectoId, pm));
            model.addAttribute("pm", pm);
            return "pm/pm-foro-detalle";
        }
        return "redirect:/pm/foros";
    }

    @PostMapping("/foro/publicar")
    public String publicarForo(@RequestParam(value = "proyectoId", required = false) Long proyectoId,
                               @RequestParam(value = "foroId", required = false) Long foroId,
                               @RequestParam("titulo") String titulo,
                               @RequestParam("contenido") String contenido,
                               @AuthenticationPrincipal UsuarioDetails principal,
                               RedirectAttributes ra, HttpServletRequest request) {
        try {
            if (foroId != null) {
                pmForoService.publicarEnForo(foroId, titulo, contenido, principal.getUsuario());
                ra.addFlashAttribute("success", "Publicación creada exitosamente.");
                return "redirect:/pm/foro/detalle?foroId=" + foroId;
            } else if (proyectoId != null) {
                pmForoService.publicar(proyectoId, titulo, contenido, principal.getUsuario());
                ra.addFlashAttribute("success", "Publicación creada exitosamente.");
                return "redirect:/pm/foro/detalle?proyectoId=" + proyectoId;
            }
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/pm/foros";
    }

    @PostMapping("/foro/responder")
    public String responderForo(@RequestParam("publicacionId") Long publicacionId,
                                @RequestParam(value = "proyectoId", required = false) Long proyectoId,
                                @RequestParam(value = "foroId", required = false) Long foroId,
                                @RequestParam("contenido") String contenido,
                                @AuthenticationPrincipal UsuarioDetails principal,
                                RedirectAttributes ra, HttpServletRequest request) {
        try {
            pmForoService.responder(publicacionId, contenido, principal.getUsuario());
            ra.addFlashAttribute("success", "Respuesta publicada.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        if (foroId != null) return "redirect:/pm/foro/detalle?foroId=" + foroId;
        return "redirect:/pm/foro/detalle?proyectoId=" + proyectoId;
    }

    @PostMapping("/foro/respuesta/{id}/marcar-solucion")
    public String marcarSolucionForo(@PathVariable("id") Long respuestaId,
                                     @RequestParam(value = "proyectoId", required = false) Long proyectoId,
                                     @RequestParam(value = "foroId", required = false) Long foroId,
                                     @AuthenticationPrincipal UsuarioDetails principal,
                                     RedirectAttributes ra, HttpServletRequest request) {
        try {
            pmForoService.alternarSolucion(principal.getUsuario(), respuestaId);
            ra.addFlashAttribute("success", "Estado de solución actualizado.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        if (foroId != null) return "redirect:/pm/foro/detalle?foroId=" + foroId;
        return "redirect:/pm/foro/detalle?proyectoId=" + proyectoId;
    }

    @PostMapping("/foro/publicacion/{publicacionId}/like")
    public String likePublicacion(@PathVariable Long publicacionId,
                                  @RequestParam(value = "proyectoId", required = false) Long proyectoId,
                                  @RequestParam(value = "foroId", required = false) Long foroId,
                                  @AuthenticationPrincipal UsuarioDetails principal,
                                  RedirectAttributes ra, HttpServletRequest request) {
        try {
            pmForoService.alternarLikePublicacion(principal.getUsuario(), publicacionId);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        if (foroId != null) return "redirect:/pm/foro/detalle?foroId=" + foroId;
        return "redirect:/pm/foro/detalle?proyectoId=" + proyectoId;
    }

    @PostMapping("/foro/respuesta/{respuestaId}/like")
    public String likeRespuesta(@PathVariable Long respuestaId,
                                @RequestParam(value = "proyectoId", required = false) Long proyectoId,
                                @RequestParam(value = "foroId", required = false) Long foroId,
                                @AuthenticationPrincipal UsuarioDetails principal,
                                RedirectAttributes ra, HttpServletRequest request) {
        try {
            pmForoService.alternarLikeRespuesta(principal.getUsuario(), respuestaId);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        if (foroId != null) return "redirect:/pm/foro/detalle?foroId=" + foroId;
        return "redirect:/pm/foro/detalle?proyectoId=" + proyectoId;
    }

    @PostMapping("/foro/publicacion/{publicacionId}/eliminar")
    public String eliminarPublicacionForo(@PathVariable Long publicacionId,
                                          @RequestParam(value = "proyectoId", required = false) Long proyectoId,
                                          @RequestParam(value = "foroId", required = false) Long foroId,
                                          @AuthenticationPrincipal UsuarioDetails principal,
                                          RedirectAttributes ra, HttpServletRequest request) {
        try {
            pmForoService.eliminarPublicacion(principal.getUsuario(), publicacionId);
            ra.addFlashAttribute("success", "Publicación eliminada.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        if (foroId != null) return "redirect:/pm/foro/detalle?foroId=" + foroId;
        return "redirect:/pm/foro/detalle?proyectoId=" + proyectoId;
    }

    @PostMapping("/foro/respuesta/{respuestaId}/eliminar")
    public String eliminarRespuestaForo(@PathVariable Long respuestaId,
                                        @RequestParam(value = "proyectoId", required = false) Long proyectoId,
                                        @RequestParam(value = "foroId", required = false) Long foroId,
                                        @AuthenticationPrincipal UsuarioDetails principal,
                                        RedirectAttributes ra, HttpServletRequest request) {
        try {
            pmForoService.eliminarRespuesta(principal.getUsuario(), respuestaId);
            ra.addFlashAttribute("success", "Respuesta eliminada.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        if (foroId != null) return "redirect:/pm/foro/detalle?foroId=" + foroId;
        return "redirect:/pm/foro/detalle?proyectoId=" + proyectoId;
    }

    @PostMapping("/foro/upload-imagen")
    @org.springframework.web.bind.annotation.ResponseBody
    public java.util.Map<String, String> uploadImagenForo(@RequestParam("image") org.springframework.web.multipart.MultipartFile image) {
        try {
            String url = pmForoService.subirImagen(image);
            return java.util.Map.of("url", url);
        } catch (Exception e) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    // REPORTES
    // ═══════════════════════════════════════════════════════════

    @GetMapping({"/reportes", "/pm-reportes.html"})
    public String reportes(@AuthenticationPrincipal UsuarioDetails principal, Model model) {
        Usuario pm = principal.getUsuario();
        model.addAttribute("proyectos", pmProyectoService.listar(pm));
        model.addAttribute("pm", pm);
        return "pm/pm-reportes";
    }

    @GetMapping({"/reportes/detalle", "/pm-reporte-detalle.html"})
    public String reporteDetalle(@RequestParam("proyectoId") Long proyectoId,
                                 @AuthenticationPrincipal UsuarioDetails principal,
                                 Model model) {
        Usuario pm = principal.getUsuario();
        model.addAttribute("reporte", pmReporteService.obtener(proyectoId, pm));
        model.addAttribute("pm", pm);
        return "pm/pm-reporte-detalle";
    }

    // ═══════════════════════════════════════════════════════════
    // PERFIL
    // ═══════════════════════════════════════════════════════════

    @GetMapping({"/perfil", "/pm-perfil.html"})
    public String perfil(@AuthenticationPrincipal UsuarioDetails principal, Model model) {
        model.addAttribute("perfil", pmPerfilService.obtener(principal.getUsuario()));
        model.addAttribute("pm", principal.getUsuario());
                if (!model.containsAttribute("pmPerfilDatosForm")) {
            PmPerfilDatosForm form = new PmPerfilDatosForm();
            form.setNombre(principal.getUsuario().getNombre());
            form.setApellido(principal.getUsuario().getApellido());
            form.setTelefono(principal.getUsuario().getTelefono());
            model.addAttribute("pmPerfilDatosForm", form);
        }
        if (!model.containsAttribute("pmPerfilPasswordForm")) model.addAttribute("pmPerfilPasswordForm", new PmPerfilPasswordForm());
        return "pm/pm-perfil";
    }

    @PostMapping("/perfil/datos")
    public String actualizarDatos(
            @Valid @ModelAttribute("pmPerfilDatosForm") PmPerfilDatosForm form, BindingResult result,
            @AuthenticationPrincipal UsuarioDetails principal, Model model,
            RedirectAttributes ra, HttpServletRequest request) {
        if (result.hasErrors()) {
            model.addAttribute("perfil", pmPerfilService.obtener(principal.getUsuario()));
            model.addAttribute("pm", principal.getUsuario());
            return "pm/pm-perfil";
        }
        try {
            pmPerfilService.actualizarDatos(form.getNombre(), form.getApellido(), form.getCargo(), form.getTelefono(), principal.getUsuario());
            ra.addFlashAttribute("success", "Datos actualizados correctamente.");
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/pm/perfil";
    }

    @PostMapping("/perfil/foto")
    public String actualizarFoto(@RequestParam("foto") MultipartFile foto,
                                 @AuthenticationPrincipal UsuarioDetails principal,
                                 RedirectAttributes ra, HttpServletRequest request) {
        try {
            pmPerfilService.actualizarFoto(foto, principal.getUsuario());
            ra.addFlashAttribute("success", "Foto de perfil actualizada.");
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/pm/perfil";
    }

    @PostMapping("/perfil/password")
    public String actualizarPassword(
            @Valid @ModelAttribute("pmPerfilPasswordForm") PmPerfilPasswordForm form, BindingResult result,
            @AuthenticationPrincipal UsuarioDetails principal, Model model,
            RedirectAttributes ra, HttpServletRequest request) {
        if (result.hasErrors()) {
            model.addAttribute("perfil", pmPerfilService.obtener(principal.getUsuario()));
            model.addAttribute("pm", principal.getUsuario());
            return "pm/pm-perfil";
        }
        try {
            if (!form.getPasswordNueva().equals(form.getPasswordConfirm())) {
                throw new IllegalArgumentException("Las contraseñas nuevas no coinciden.");
            }
            pmPerfilService.actualizarPassword(form.getPasswordActual(), form.getPasswordNueva(), principal.getUsuario());
            ra.addFlashAttribute("success", "Contraseña actualizada correctamente.");
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/pm/perfil";
    }

    // ═══════════════════════════════════════════════════════════
    // OTRAS VISTAS (sin lógica específica aún)
    // ═══════════════════════════════════════════════════════════

    @GetMapping({"/chat", "/pm-chat.html"})
    public String chat(@RequestParam(value = "proyectoId", required = false) Long proyectoId,
                       @AuthenticationPrincipal UsuarioDetails principal, 
                       Model model) {
        Usuario pm = principal.getUsuario();
        model.addAttribute("pm", pm);
        model.addAttribute("proyectos", pmChatService.listarProyectosDelPm(pm));
        model.addAttribute("proyectoIdSeleccionado", proyectoId);
        return "pm/pm-chat";
    }

    @GetMapping({"/asistente", "/pm-asistente-ia.html"})
    public String asistente(@AuthenticationPrincipal UsuarioDetails principal, Model model) {
        model.addAttribute("pm", principal.getUsuario());
        return "pm/pm-asistente-ia";
    }
}