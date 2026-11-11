package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.util.Paginador;
import org.springframework.data.domain.Page;
import java.util.List;
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
import com.pucp.skillb_ia.service.pm.PmSolicitudPersonalService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.validation.Valid;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ModelAttribute;
import com.pucp.skillb_ia.dto.pm.*;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.stream.Collectors;

import static com.pucp.skillb_ia.controller.GlobalExceptionHandler.mensajeSeguro;

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
    private final PmSolicitudPersonalService pmSolicitudPersonalService;

    public PmViewController(PmProyectoService pmProyectoService,
                            PmActividadService pmActividadService,
                            PmAsignacionService pmAsignacionService,
                            PmForoService pmForoService,
                            PmReporteService pmReporteService,
                            PmPerfilService pmPerfilService,
                            PmChatService pmChatService,
                            HabilidadRepository habilidadRepository,
                            UsuarioRepository usuarioRepository,
                            com.pucp.skillb_ia.service.pm.PmDashboardService pmDashboardService,
                            PmSolicitudPersonalService pmSolicitudPersonalService) {
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
        this.pmSolicitudPersonalService = pmSolicitudPersonalService;
    }

    // Disponible en el modelo de todas las páginas de este controlador (topbar).
    @ModelAttribute("pm")
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
    public String proyectos(@AuthenticationPrincipal UsuarioDetails principal,
                            @RequestParam(required = false) String busqueda,
                            @RequestParam(required = false) String estado,
                            @RequestParam(required = false) String prioridad,
                            @RequestParam(required = false) Integer pagina,
                            Model model) {
        Usuario pm = principal.getUsuario();
        // Filtros y paginación en el servidor (antes en pm-proyectos.js), 6 tarjetas por página.
        List<com.pucp.skillb_ia.dto.PmProyectoView> todos = pmProyectoService.listar(pm);
        List<com.pucp.skillb_ia.dto.PmProyectoView> filtrados = todos.stream()
                .filter(p -> Paginador.contiene(p.getProyecto().getNombre() + " " +
                        (p.getProyecto().getDescripcion() != null ? p.getProyecto().getDescripcion() : "") + " " +
                        p.getRequisitos().stream().map(r -> r.getHabilidad()).toList(), busqueda))
                .filter(p -> Paginador.coincide(p.getEstadoTexto(), estado))
                .filter(p -> Paginador.coincide(p.getProyecto().getPrioridad() != null ? p.getProyecto().getPrioridad().toString() : "", prioridad))
                .toList();
        Page<com.pucp.skillb_ia.dto.PmProyectoView> paginaProyectos = Paginador.paginar(filtrados, pagina, 6);
        model.addAttribute("paginaProyectos", paginaProyectos);
        model.addAttribute("proyectos", paginaProyectos.getContent());
        model.addAttribute("totalProyectos", todos.size());
        model.addAttribute("busqueda", busqueda);
        model.addAttribute("estadoFiltro", estado);
        model.addAttribute("prioridadFiltro", prioridad);
        model.addAttribute("pm", pm);
        return "pm/pm-proyectos";
    }

    @GetMapping({"/proyectos/detalle", "/pm-detalle-proyecto.html"})
    public String detalleProyecto(@RequestParam("id") Long proyectoId,
                                  @AuthenticationPrincipal UsuarioDetails principal,
                                  Model model) {
        Usuario pm = principal.getUsuario();
        var proyecto = pmProyectoService.obtener(proyectoId, pm);
        model.addAttribute("proyecto", proyecto);
        model.addAttribute("pm", pm);
        // Habilidades del proyecto con sus cupos libres para el modal de solicitud de personal.
        model.addAttribute("cuposSolicitud", pmSolicitudPersonalService.listarCupos(proyecto.getProyecto()));
        // Tras un error, el flash trae los valores ingresados en el modal de solicitud de personal.
        if (!model.containsAttribute("pmSolicitudPersonalForm")) {
            model.addAttribute("pmSolicitudPersonalForm", new PmSolicitudPersonalForm());
        }
        return "pm/pm-detalle-proyecto";
    }

    // TASK-058: el PM pide personal al RM, con desglose por habilidad del proyecto.
    // Las reglas están en PmSolicitudPersonalService y RmSolicitudPersonalService.crearDesdePm.
    @PostMapping("/proyectos/{id}/solicitudes-personal")
    public String solicitarPersonal(@PathVariable("id") Long proyectoId,
                                    @Valid @ModelAttribute("pmSolicitudPersonalForm") PmSolicitudPersonalForm form,
                                    BindingResult result,
                                    @AuthenticationPrincipal UsuarioDetails principal,
                                    RedirectAttributes ra) {
        String destino = "redirect:/pm/proyectos/detalle?id=" + proyectoId;
        if (result.hasErrors()) {
            return reabrirSolicitudPersonal(destino, mensajesSolicitudPersonal(result), form, ra);
        }
        try {
            pmSolicitudPersonalService.solicitar(proyectoId, form.getCantidad(), form.getCantidadesPorHabilidad(),
                    form.getPerfiles(), form.getMensaje(), principal.getUsuario());
            ra.addFlashAttribute("success", "Solicitud de personal enviada. Los Resource Managers fueron notificados.");
        } catch (IllegalArgumentException e) {
            // Datos del formulario (desglose, perfiles): se corrigen en el mismo modal.
            return reabrirSolicitudPersonal(destino, e.getMessage(), form, ra);
        } catch (IllegalStateException e) {
            // Estado del proyecto o solicitud abierta: no se corrige en el modal.
            ra.addFlashAttribute("error", mensajeSeguro(e));
        } catch (SecurityException e) {
            ra.addFlashAttribute("error", mensajeSeguro(e));
            return "redirect:/pm/proyectos";
        }
        return destino;
    }

    private static String reabrirSolicitudPersonal(String destino, String mensaje, PmSolicitudPersonalForm form,
                                                   RedirectAttributes ra) {
        ra.addFlashAttribute("error", mensaje);
        ra.addFlashAttribute("pmSolicitudPersonalForm", form);
        ra.addFlashAttribute("abrirSolicitudPersonal", true);
        // El fragmento reabre el modal también sin JavaScript (CSS :target).
        return destino + "#requestStaffModal";
    }

    private static String mensajesSolicitudPersonal(BindingResult result) {
        return result.getFieldErrors().stream()
                .sorted(java.util.Comparator.comparingInt(error -> ordenCampoSolicitud(error.getField())))
                .map(error -> {
                    if (!error.isBindingFailure()) return error.getDefaultMessage();
                    return error.getField().startsWith("cantidadesPorHabilidad")
                            ? "La cantidad por habilidad debe ser un número entero mayor o igual que cero."
                            : "La cantidad debe ser un número entero mayor que cero.";
                })
                .distinct()
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private static int ordenCampoSolicitud(String campo) {
        if (campo.equals("cantidad")) return 0;
        if (campo.startsWith("cantidadesPorHabilidad")) return 1;
        return campo.equals("perfiles") ? 2 : 3;
    }

    @GetMapping({"/proyectos/crear", "/pm-crear-proyecto.html"})
    public String crearProyectoForm(@AuthenticationPrincipal UsuarioDetails principal, Model model) {
        if (!model.containsAttribute("pmProyectoForm")) {
            model.addAttribute("pmProyectoForm", new PmProyectoForm());
        }
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
        if (form.getFechaInicio() != null && form.getFechaFinEstimada() != null
                && form.getFechaFinEstimada().isBefore(form.getFechaInicio())) {
            result.rejectValue("fechaFinEstimada", "fechaFinEstimada.anterior",
                    "La fecha de fin estimada no puede ser anterior a la fecha de inicio");
        }
        if (form.getCantidadesPersonas() != null) {
            for (Integer cant : form.getCantidadesPersonas()) {
                if (cant == null || cant < 1) {
                    result.rejectValue("habilidadIds", "cantidadesPersonas.invalida",
                            "La cantidad de personas por habilidad debe ser de al menos 1");
                    break;
                }
            }
        }
        if (form.getHorasSemanalesHab() != null) {
            for (BigDecimal horas : form.getHorasSemanalesHab()) {
                if (horas == null || horas.compareTo(new BigDecimal("0.5")) < 0 || horas.compareTo(new BigDecimal("168")) > 0) {
                    result.rejectValue("habilidadIds", "horasSemanalesHab.invalida",
                            "Las horas semanales por habilidad deben estar entre 0.5 y 168");
                    break;
                }
            }
        }
        if (result.hasErrors()) {
            model.addAttribute("habilidades", habilidadRepository.findByActivaTrue());
            model.addAttribute("pm", principal.getUsuario());
            return "pm/pm-crear-proyecto";
        }
        try {
            com.pucp.skillb_ia.model.Proyecto nuevo = pmProyectoService.crear(
                    form.getNombre(), form.getDescripcion(), form.getFechaInicio(), form.getFechaFinEstimada(),
                    form.getPrioridad(), form.getJustificacionPrioridad(), form.getPresupuestoSolicitado(), form.getJustificacionPresupuesto(),
                    form.getColaboradoresRequeridos() != null ? form.getColaboradoresRequeridos() : 1, null,
                    form.getHabilidadIds(), form.getNivelesRequeridos(), form.getCantidadesPersonas(), form.getHorasSemanalesHab(),
                    principal.getUsuario());
            ra.addFlashAttribute("success", "Proyecto creado exitosamente. Está pendiente de revisión por el RM.");
            return "redirect:/pm/proyectos/detalle?id=" + nuevo.getId();
        } catch (Exception e) {
            model.addAttribute("error", mensajeSeguro(e));
            model.addAttribute("habilidades", habilidadRepository.findByActivaTrue());
            model.addAttribute("pm", principal.getUsuario());
            return "pm/pm-crear-proyecto";
        }
    }

    @PostMapping("/proyectos/{id}/cancelar")
    public String cancelarProyecto(@PathVariable("id") Long proyectoId,
                                   @RequestParam(value = "motivo", required = false) String motivo,
                                   @AuthenticationPrincipal UsuarioDetails principal,
                                   RedirectAttributes ra, HttpServletRequest request) {
        try {
            if (motivo != null && motivo.trim().length() > 500) {
                throw new IllegalArgumentException("El motivo de cancelación no puede superar los 500 caracteres.");
            }
            var resultado = pmProyectoService.cancelar(proyectoId, principal.getUsuario());
            ra.addFlashAttribute("success", CierreAsignacionesService.mensaje("cancelado", resultado));
        } catch (Exception e) {
            ra.addFlashAttribute("error", mensajeSeguro(e));
        }
        return "redirect:/pm/proyectos";
    }

    @PostMapping("/proyectos/cancelar")
    public String cancelarProyectoDesdeDetalle(@RequestParam("proyectoId") Long proyectoId,
                                               @RequestParam(value = "motivo", required = false) String motivo,
                                               @AuthenticationPrincipal UsuarioDetails principal,
                                               RedirectAttributes ra, HttpServletRequest request) {
        return cancelarProyecto(proyectoId, motivo, principal, ra, request);
    }

    // ═══════════════════════════════════════════════════════════
    // ASIGNACIONES
    // ═══════════════════════════════════════════════════════════

    @GetMapping({"/proyectos/asignaciones", "/pm-asignaciones-proyecto.html"})
    public String asignaciones(@RequestParam("proyectoId") Long proyectoId,
                               @RequestParam(required = false) Integer paginaActivas,
                               @RequestParam(required = false) Integer paginaPendientes,
                               @RequestParam(required = false) Integer paginaTramite,
                               @RequestParam(required = false) Integer paginaHistorial,
                               @AuthenticationPrincipal UsuarioDetails principal,
                               Model model) {
        Usuario pm = principal.getUsuario();
        var todas = pmAsignacionService.listarPorProyecto(proyectoId, pm);
        var pendientes = pmAsignacionService.listarPendientesPm(proyectoId, pm);
        // La lista completa se usa para los modales de "Finalizar"; las tablas se paginan en el servidor (10 por página).
        model.addAttribute("asignaciones", todas);
        var activas = todas.stream().filter(a -> "Activa".equals(a.getEstadoTexto())).toList();
        var tramite = todas.stream().filter(a -> "Pendiente".equals(a.getEstadoTexto())).toList();
        var historial = todas.stream().filter(a -> "Rechazada".equals(a.getEstadoTexto()) || "Finalizada".equals(a.getEstadoTexto())).toList();
        // Si un rechazo volvió con error, se abre la página donde está esa propuesta para poder mostrar su modal.
        Object rechazarId = model.getAttribute("abrirModalRechazarId");
        if (rechazarId != null) {
            for (int i = 0; i < pendientes.size(); i++) {
                if (rechazarId.equals(pendientes.get(i).getAsignacion().getId())) {
                    paginaPendientes = i / Paginador.TAMANIO_PAGINA + 1;
                }
            }
        }
        model.addAttribute("paginaActivas", Paginador.paginar(activas, paginaActivas));
        model.addAttribute("paginaPendientes", Paginador.paginar(pendientes, paginaPendientes));
        model.addAttribute("paginaTramite", Paginador.paginar(tramite, paginaTramite));
        model.addAttribute("paginaHistorial", Paginador.paginar(historial, paginaHistorial));
        model.addAttribute("proyecto", pmProyectoService.obtener(proyectoId, pm));
        // Colaboradores disponibles para proponer (rol COLABORADOR activos)
        model.addAttribute("colaboradoresDisponibles",
                usuarioRepository.findActivosByRolNombre("COLABORADOR"));
        if (!model.containsAttribute("pmProponerAsignacionForm")) {
            PmProponerAsignacionForm proponerForm = new PmProponerAsignacionForm();
            proponerForm.setProyectoId(proyectoId);
            model.addAttribute("pmProponerAsignacionForm", proponerForm);
        }
        model.addAttribute("pm", pm);
        return "pm/pm-asignaciones-proyecto";
    }

    @PostMapping("/asignaciones/proponer")
    public String proponerAsignacion(
            @Valid @ModelAttribute("pmProponerAsignacionForm") PmProponerAsignacionForm form,
            BindingResult result,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes ra, HttpServletRequest request) {
        Long proyectoId = form.getProyectoId();
        if (proyectoId == null) {
            ra.addFlashAttribute("error", "El proyecto es obligatorio.");
            return "redirect:/pm/proyectos";
        }
        if (result.hasErrors()) {
            ra.addFlashAttribute("error", unirErrores(result, "Revisa los datos de la propuesta."));
            ra.addFlashAttribute("pmProponerAsignacionForm", form);
            ra.addFlashAttribute("org.springframework.validation.BindingResult.pmProponerAsignacionForm", result);
            ra.addFlashAttribute("abrirModalProponer", true);
            return "redirect:/pm/proyectos/asignaciones?proyectoId=" + proyectoId;
        }
        try {
            pmAsignacionService.proponer(proyectoId, form.getColaboradorId(), form.getHorasSemanales(),
                    form.getMensajeSolicitud(), principal.getUsuario());
            ra.addFlashAttribute("success", "Propuesta enviada al RM para aprobación.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", mensajeSeguro(e));
            ra.addFlashAttribute("pmProponerAsignacionForm", form);
            ra.addFlashAttribute("abrirModalProponer", true);
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
            ra.addFlashAttribute("error", mensajeSeguro(e));
        }
        return "redirect:/pm/proyectos/asignaciones?proyectoId=" + proyectoId;
    }

    @PostMapping("/asignaciones/{id}/rechazar")
    public String rechazarAsignacion(@PathVariable("id") Long asignacionId,
                                     @Valid @ModelAttribute("pmRechazarAsignacionForm") PmRechazarAsignacionForm form,
                                     BindingResult result,
                                     @AuthenticationPrincipal UsuarioDetails principal,
                                     RedirectAttributes ra, HttpServletRequest request) {
        Long proyectoId = form.getProyectoId();
        if (proyectoId == null) {
            ra.addFlashAttribute("error", "El proyecto es obligatorio.");
            return "redirect:/pm/proyectos";
        }
        if (result.hasErrors()) {
            ra.addFlashAttribute("error", unirErrores(result, "Debes indicar el motivo del rechazo."));
            ra.addFlashAttribute("abrirModalRechazarId", asignacionId);
            return "redirect:/pm/proyectos/asignaciones?proyectoId=" + proyectoId;
        }
        try {
            pmAsignacionService.rechazar(asignacionId, form.getMotivo(), principal.getUsuario());
            ra.addFlashAttribute("success", "Asignación rechazada.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", mensajeSeguro(e));
        }
        return "redirect:/pm/proyectos/asignaciones?proyectoId=" + proyectoId;
    }

    @PostMapping("/asignaciones/{id}/finalizar")
    public String finalizarAsignacion(@PathVariable("id") Long asignacionId,
                                      @Valid @ModelAttribute("pmFinalizarAsignacionForm") PmFinalizarAsignacionForm form,
                                      BindingResult result,
                                      @AuthenticationPrincipal UsuarioDetails principal,
                                      RedirectAttributes ra, HttpServletRequest request) {
        Long proyectoId = form.getProyectoId();
        if (proyectoId == null) {
            ra.addFlashAttribute("error", "El proyecto es obligatorio.");
            return "redirect:/pm/proyectos";
        }
        if (result.hasErrors()) {
            ra.addFlashAttribute("error", unirErrores(result, "Revisa los datos para finalizar la asignación."));
            ra.addFlashAttribute("abrirModalFinalizarId", asignacionId);
            return "redirect:/pm/proyectos/asignaciones?proyectoId=" + proyectoId;
        }
        try {
            pmAsignacionService.finalizar(asignacionId, form.getCalificacion(), form.getFeedback(), principal.getUsuario());
            ra.addFlashAttribute("success", "Asignación finalizada.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", mensajeSeguro(e));
        }
        return "redirect:/pm/proyectos/asignaciones?proyectoId=" + proyectoId;
    }

    // ═══════════════════════════════════════════════════════════
    // ACTIVIDADES
    // ═══════════════════════════════════════════════════════════

    @GetMapping({"/actividades", "/pm-actividades.html"})
    public String actividades(@RequestParam("proyectoId") Long proyectoId,
                              @RequestParam(required = false) Integer pagina,
                              @AuthenticationPrincipal UsuarioDetails principal,
                              Model model) {
        Usuario pm = principal.getUsuario();
        var todas = pmActividadService.listarPorProyecto(proyectoId, pm);
        // Si un modal de editar/detalle volvió con error, se abre la página donde está esa actividad.
        Object idConError = model.getAttribute("abrirModalEditarActividadId") != null
                ? model.getAttribute("abrirModalEditarActividadId") : model.getAttribute("abrirModalDetalleActividadId");
        if (idConError != null) {
            for (int i = 0; i < todas.size(); i++) {
                if (idConError.equals(todas.get(i).getActividad().getId())) {
                    pagina = i / Paginador.TAMANIO_PAGINA + 1;
                }
            }
        }
        // Paginación en el servidor (antes pm-actividades.js), 10 por página.
        var paginaActividades = Paginador.paginar(todas, pagina);
        model.addAttribute("paginaActividades", paginaActividades);
        model.addAttribute("actividades", paginaActividades.getContent());
        model.addAttribute("proyecto", pmProyectoService.obtener(proyectoId, pm));
        // Colaboradores activos del proyecto para asignar actividades
        model.addAttribute("colaboradoresActivos",
                pmAsignacionService.listarPorProyecto(proyectoId, pm)
                        .stream()
                        .filter(a -> "Activa".equals(a.getEstadoTexto()))
                        .toList());
        if (!model.containsAttribute("pmActividadForm")) {
            PmActividadForm actForm = new PmActividadForm();
            actForm.setProyectoId(proyectoId);
            model.addAttribute("pmActividadForm", actForm);
        }
        model.addAttribute("pm", pm);
        return "pm/pm-actividades";
    }

    @PostMapping("/actividades/crear")
    public String crearActividad(
            @Valid @ModelAttribute("pmActividadForm") PmActividadForm form,
            BindingResult result,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes ra, HttpServletRequest request) {
        Long proyectoId = form.getProyectoId();
        if (proyectoId == null) {
            ra.addFlashAttribute("error", "El proyecto es obligatorio.");
            return "redirect:/pm/proyectos";
        }
        if (result.hasErrors()) {
            ra.addFlashAttribute("error", unirErrores(result, "Revisa los campos de la nueva actividad."));
            ra.addFlashAttribute("pmActividadForm", form);
            ra.addFlashAttribute("org.springframework.validation.BindingResult.pmActividadForm", result);
            ra.addFlashAttribute("abrirModalNuevaActividad", true);
            return "redirect:/pm/actividades?proyectoId=" + proyectoId;
        }
        try {
            pmActividadService.crear(proyectoId, form.getColaboradorId(), form.getTitulo(), form.getDescripcion(),
                    form.getHorasEstimadas(), form.getFechaLimite(), principal.getUsuario());
            ra.addFlashAttribute("success", "Actividad creada correctamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", mensajeSeguro(e));
            ra.addFlashAttribute("pmActividadForm", form);
            ra.addFlashAttribute("abrirModalNuevaActividad", true);
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
            ra.addFlashAttribute("error", mensajeSeguro(e));
        }
        return "redirect:/pm/actividades?proyectoId=" + proyectoId;
    }

    @PostMapping("/actividades/{id}/devolver")
    public String devolverActividad(@PathVariable("id") Long actividadId,
                                    @Valid @ModelAttribute("pmDevolverActividadForm") PmDevolverActividadForm form,
                                    BindingResult result,
                                    @AuthenticationPrincipal UsuarioDetails principal,
                                    RedirectAttributes ra, HttpServletRequest request) {
        Long proyectoId = form.getProyectoId();
        if (proyectoId == null) {
            ra.addFlashAttribute("error", "El proyecto es obligatorio.");
            return "redirect:/pm/proyectos";
        }
        if (result.hasErrors()) {
            ra.addFlashAttribute("error", unirErrores(result, "Debes escribir un comentario para devolver la actividad."));
            ra.addFlashAttribute("abrirModalDetalleActividadId", actividadId);
            return "redirect:/pm/actividades?proyectoId=" + proyectoId;
        }
        try {
            pmActividadService.devolver(actividadId, form.getComentario(), form.isAplicarStrike(), principal.getUsuario());
            ra.addFlashAttribute("success", "Actividad devuelta al colaborador.");
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(getClass()).error("Error al devolver actividad {}", actividadId, e);
            ra.addFlashAttribute("error", mensajeSeguro(e));
        }
        return "redirect:/pm/actividades?proyectoId=" + proyectoId;
    }

    @PostMapping("/actividades/{id}/editar")
    public String editarActividad(@PathVariable("id") Long actividadId,
                                  @Valid @ModelAttribute("pmEditarActividadForm") PmEditarActividadForm form,
                                  BindingResult result,
                                  @AuthenticationPrincipal UsuarioDetails principal,
                                  RedirectAttributes ra, HttpServletRequest request) {
        Long proyectoId = form.getProyectoId();
        if (proyectoId == null) {
            ra.addFlashAttribute("error", "El proyecto es obligatorio.");
            return "redirect:/pm/proyectos";
        }
        if (result.hasErrors()) {
            ra.addFlashAttribute("error", unirErrores(result, "Revisa los campos de la actividad."));
            ra.addFlashAttribute("abrirModalEditarActividadId", actividadId);
            return "redirect:/pm/actividades?proyectoId=" + proyectoId;
        }
        try {
            pmActividadService.editar(actividadId, form.getTitulo(), form.getDescripcion(),
                    form.getHorasEstimadas(), form.getFechaLimite(), principal.getUsuario());
            ra.addFlashAttribute("success", "Actividad editada correctamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", mensajeSeguro(e));
            ra.addFlashAttribute("abrirModalEditarActividadId", actividadId);
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
            ra.addFlashAttribute("error", mensajeSeguro(e));
        }
        return "redirect:/pm/actividades?proyectoId=" + proyectoId;
    }

    // ═══════════════════════════════════════════════════════════
    // FOROS
    // ═══════════════════════════════════════════════════════════

    @GetMapping({"/foros", "/pm-foros.html"})
    public String foros(@AuthenticationPrincipal UsuarioDetails principal,
                        @RequestParam(required = false) String tab,
                        @RequestParam(required = false) Integer paginaComunidad,
                        @RequestParam(required = false) Integer paginaMios,
                        Model model) {
        Usuario pm = principal.getUsuario();
        // Paginación en el servidor (antes pm-foros.js), 6 foros por pestaña.
        var pagComunidad = Paginador.paginar(pmForoService.listarForosComunidad(), paginaComunidad, 6);
        var pagMios = Paginador.paginar(pmForoService.listarForosPm(pm), paginaMios, 6);
        model.addAttribute("paginaComunidad", pagComunidad);
        model.addAttribute("paginaMios", pagMios);
        model.addAttribute("forosComunidad", pagComunidad.getContent());
        model.addAttribute("foros", pagMios.getContent());
        model.addAttribute("tab", "mios".equals(tab) ? "mios" : "comunidad");
        model.addAttribute("pm", pm);
        return "pm/pm-foros";
    }

    @GetMapping({"/foro/detalle", "/pm-foro-detalle.html"})
    public String foroDetalle(@RequestParam(value = "proyectoId", required = false) Long proyectoId,
                              @RequestParam(value = "foroId", required = false) Long foroId,
                              @AuthenticationPrincipal UsuarioDetails principal,
                              Model model) {
        Usuario pm = principal.getUsuario();
        if (!model.containsAttribute("pmForoPublicacionForm")) {
            PmForoPublicacionForm pubForm = new PmForoPublicacionForm();
            pubForm.setProyectoId(proyectoId);
            pubForm.setForoId(foroId);
            model.addAttribute("pmForoPublicacionForm", pubForm);
        }
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
    public String publicarForo(@Valid @ModelAttribute("pmForoPublicacionForm") PmForoPublicacionForm form,
                               BindingResult result,
                               @AuthenticationPrincipal UsuarioDetails principal,
                               RedirectAttributes ra, HttpServletRequest request) {
        Long proyectoId = form.getProyectoId();
        Long foroId = form.getForoId();
        if (!result.hasFieldErrors("contenido") && PmForoService.contenidoForoVacio(form.getContenido())) {
            result.rejectValue("contenido", "contenido.vacio", "El contenido de la publicación es obligatorio.");
        }
        String redirectUrl = foroId != null
                ? "redirect:/pm/foro/detalle?foroId=" + foroId
                : (proyectoId != null ? "redirect:/pm/foro/detalle?proyectoId=" + proyectoId : "redirect:/pm/foros");
        if (result.hasErrors()) {
            ra.addFlashAttribute("error", unirErrores(result, "Revisa los campos de la publicación."));
            ra.addFlashAttribute("pmForoPublicacionForm", form);
            ra.addFlashAttribute("org.springframework.validation.BindingResult.pmForoPublicacionForm", result);
            ra.addFlashAttribute("abrirModalNuevaPublicacion", true);
            return redirectUrl;
        }
        try {
            if (foroId != null) {
                pmForoService.publicarEnForo(foroId, form.getTitulo(), form.getContenido(), principal.getUsuario());
                ra.addFlashAttribute("success", "Publicación creada exitosamente.");
                return "redirect:/pm/foro/detalle?foroId=" + foroId;
            } else if (proyectoId != null) {
                pmForoService.publicar(proyectoId, form.getTitulo(), form.getContenido(), principal.getUsuario());
                ra.addFlashAttribute("success", "Publicación creada exitosamente.");
                return "redirect:/pm/foro/detalle?proyectoId=" + proyectoId;
            }
        } catch (Exception e) {
            ra.addFlashAttribute("error", mensajeSeguro(e));
            ra.addFlashAttribute("pmForoPublicacionForm", form);
            ra.addFlashAttribute("abrirModalNuevaPublicacion", true);
        }
        return redirectUrl;
    }

    @PostMapping("/foro/responder")
    public String responderForo(@Valid @ModelAttribute("pmForoRespuestaForm") PmForoRespuestaForm form,
                                BindingResult result,
                                @AuthenticationPrincipal UsuarioDetails principal,
                                RedirectAttributes ra, HttpServletRequest request) {
        Long proyectoId = form.getProyectoId();
        Long foroId = form.getForoId();
        if (!result.hasFieldErrors("contenido") && PmForoService.contenidoForoVacio(form.getContenido())) {
            result.rejectValue("contenido", "contenido.vacio", "El contenido de la respuesta no puede estar vacío.");
        }
        String redirectUrl = foroId != null
                ? "redirect:/pm/foro/detalle?foroId=" + foroId
                : "redirect:/pm/foro/detalle?proyectoId=" + proyectoId;
        if (result.hasErrors()) {
            ra.addFlashAttribute("error", unirErrores(result, "El contenido de la respuesta no puede estar vacío."));
            ra.addFlashAttribute("abrirModalResponderId", form.getPublicacionId());
            return redirectUrl;
        }
        try {
            pmForoService.responder(form.getPublicacionId(), form.getContenido(), principal.getUsuario());
            ra.addFlashAttribute("success", "Respuesta publicada.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", mensajeSeguro(e));
            ra.addFlashAttribute("abrirModalResponderId", form.getPublicacionId());
        }
        return redirectUrl;
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
            ra.addFlashAttribute("error", mensajeSeguro(e));
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
            ra.addFlashAttribute("error", mensajeSeguro(e));
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
            ra.addFlashAttribute("error", mensajeSeguro(e));
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
            ra.addFlashAttribute("error", mensajeSeguro(e));
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
            ra.addFlashAttribute("error", mensajeSeguro(e));
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
                org.springframework.http.HttpStatus.BAD_REQUEST, mensajeSeguro(e));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // REPORTES
    // ═══════════════════════════════════════════════════════════

    @GetMapping({"/reportes", "/pm-reportes.html"})
    public String reportes(@AuthenticationPrincipal UsuarioDetails principal,
                           @RequestParam(required = false) Integer pagina,
                           Model model) {
        Usuario pm = principal.getUsuario();
        // Paginación en el servidor (antes pm-reportes.js), 6 tarjetas por página.
        var paginaReportes = Paginador.paginar(pmProyectoService.listar(pm), pagina, 6);
        model.addAttribute("paginaReportes", paginaReportes);
        model.addAttribute("proyectos", paginaReportes.getContent());
        model.addAttribute("pm", pm);
        return "pm/pm-reportes";
    }

    @GetMapping({"/reportes/detalle", "/pm-reporte-detalle.html"})
    public String reporteDetalle(@RequestParam("proyectoId") Long proyectoId,
                                 @RequestParam(required = false) Integer pagina,
                                 @AuthenticationPrincipal UsuarioDetails principal,
                                 Model model) {
        Usuario pm = principal.getUsuario();
        var reporte = pmReporteService.obtener(proyectoId, pm);
        model.addAttribute("reporte", reporte);
        // La tabla de métricas se pagina en el servidor (antes pm-reporte-detalle.js), 10 por página.
        model.addAttribute("paginaColaboradores", Paginador.paginar(reporte.getColaboradores(), pagina));
        model.addAttribute("pm", pm);
        return "pm/pm-reporte-detalle";
    }

    // ═══════════════════════════════════════════════════════════
    // PERFIL
    // ═══════════════════════════════════════════════════════════

    @GetMapping({"/perfil", "/pm-perfil.html"})
    public String perfil(@AuthenticationPrincipal UsuarioDetails principal, Model model) {
        cargarVistaPerfil(principal, model);
        return "pm/pm-perfil";
    }

    // La vista tiene dos formularios: si uno vuelve con errores, el otro también debe estar en el modelo.
    private void cargarVistaPerfil(UsuarioDetails principal, Model model) {
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
    }

    @PostMapping("/perfil/datos")
    public String actualizarDatos(
            @Valid @ModelAttribute("pmPerfilDatosForm") PmPerfilDatosForm form, BindingResult result,
            @AuthenticationPrincipal UsuarioDetails principal, Model model,
            RedirectAttributes ra, HttpServletRequest request) {
        if (result.hasErrors()) {
            cargarVistaPerfil(principal, model);
            return "pm/pm-perfil";
        }
        try {
            pmPerfilService.actualizarDatos(form.getNombre(), form.getApellido(), form.getCargo(), form.getTelefono(), principal.getUsuario());
            ra.addFlashAttribute("success", "Datos actualizados correctamente.");
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
        } catch (Exception e) {
            ra.addFlashAttribute("error", mensajeSeguro(e));
        }
        return "redirect:/pm/perfil";
    }

    @PostMapping("/perfil/foto")
    public String actualizarFoto(@RequestParam(value = "foto", required = false) MultipartFile foto,
                                 @AuthenticationPrincipal UsuarioDetails principal,
                                 RedirectAttributes ra, HttpServletRequest request) {
        try {
            pmPerfilService.actualizarFoto(foto, principal.getUsuario());
            ra.addFlashAttribute("success", "Foto de perfil actualizada.");
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
        } catch (Exception e) {
            ra.addFlashAttribute("error", mensajeSeguro(e));
        }
        return "redirect:/pm/perfil";
    }

    @PostMapping("/perfil/password")
    public String actualizarPassword(
            @Valid @ModelAttribute("pmPerfilPasswordForm") PmPerfilPasswordForm form, BindingResult result,
            @AuthenticationPrincipal UsuarioDetails principal, Model model,
            RedirectAttributes ra, HttpServletRequest request) {
        if (form.getPasswordNueva() != null && form.getPasswordConfirm() != null
                && !form.getPasswordNueva().isBlank() && !form.getPasswordConfirm().isBlank()
                && !form.getPasswordNueva().equals(form.getPasswordConfirm())) {
            result.rejectValue("passwordConfirm", "passwordConfirm.noCoincide",
                    "Las contraseñas nuevas no coinciden.");
        }
        if (result.hasErrors()) {
            cargarVistaPerfil(principal, model);
            return "pm/pm-perfil";
        }
        try {
            pmPerfilService.actualizarPassword(form.getPasswordActual(), form.getPasswordNueva(), principal.getUsuario());
            ra.addFlashAttribute("success", "Contraseña actualizada correctamente.");
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
        } catch (Exception e) {
            ra.addFlashAttribute("error", mensajeSeguro(e));
        }
        return "redirect:/pm/perfil";
    }

    // ═══════════════════════════════════════════════════════════
    // OTRAS VISTAS
    // ═══════════════════════════════════════════════════════════

    @GetMapping({"/chat", "/pm-chat.html"})
    public String chat(@RequestParam(value = "proyectoId", required = false) Long proyectoId,
                       @RequestParam(value = "usuarioId", required = false) Long usuarioId,
                       @AuthenticationPrincipal UsuarioDetails principal,
                       Model model) {
        Usuario pm = principal.getUsuario();
        model.addAttribute("pm", pm);
        model.addAttribute("proyectos", pmChatService.listarProyectosDelPm(pm));
        model.addAttribute("proyectoIdSeleccionado", proyectoId);
        model.addAttribute("usuarioIdSeleccionado", usuarioId);

        return "pm/pm-chat";
    }

    @GetMapping({"/asistente", "/pm-asistente-ia.html"})
    public String asistente(@AuthenticationPrincipal UsuarioDetails principal, Model model) {
        model.addAttribute("pm", principal.getUsuario());
        return "pm/pm-asistente-ia";
    }

    private static String unirErrores(BindingResult result, String porDefecto) {
        String unidos = result.getFieldErrors().stream()
                .map(error -> error.isBindingFailure()
                        ? "El valor ingresado en '" + error.getField() + "' no tiene un formato válido."
                        : error.getDefaultMessage())
                .filter(msg -> msg != null && !msg.isBlank())
                .distinct()
                .collect(Collectors.joining(" "));
        return unidos.isBlank() ? porDefecto : unidos;
    }
}
