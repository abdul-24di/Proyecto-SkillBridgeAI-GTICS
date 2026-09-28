package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.service.EvaluacionService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.dto.RmColaboradorDetalle;
import com.pucp.skillb_ia.dto.RmColaboradorResumen;
import com.pucp.skillb_ia.dto.RmAsignacionView;
import com.pucp.skillb_ia.dto.RmProyectoView;
import com.pucp.skillb_ia.dto.RmSolicitudPersonalView;
import com.pucp.skillb_ia.dto.RmForoView;
import com.pucp.skillb_ia.dto.RmReporteView;
import com.pucp.skillb_ia.dto.RmCursoView;
import com.pucp.skillb_ia.model.enums.MotivoFinalizacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.NivelDominio;
import com.pucp.skillb_ia.model.enums.NivelExperiencia;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.rm.RmColaboradorConsultaService;
import com.pucp.skillb_ia.service.rm.RmAsignacionService;
import com.pucp.skillb_ia.service.rm.RmPerfilService;
import com.pucp.skillb_ia.service.rm.RmProyectoConsultaService;
import com.pucp.skillb_ia.service.rm.RmProyectoRevisionService;
import com.pucp.skillb_ia.service.rm.RmSolicitudPersonalService;
import com.pucp.skillb_ia.service.rm.RmCertificadoService;
import com.pucp.skillb_ia.service.rm.RmForoConsultaService;
import com.pucp.skillb_ia.service.rm.RmReporteExportService;
import com.pucp.skillb_ia.service.rm.RmReporteService;
import com.pucp.skillb_ia.service.rm.RmCursoService;
import com.pucp.skillb_ia.service.rm.RmPresupuestoService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

@Controller
@RequestMapping("/rm")
public class RmViewController {

    private final RmPerfilService rmPerfilService;
    private final RmColaboradorConsultaService rmColaboradorConsultaService;
    private final RmProyectoConsultaService rmProyectoConsultaService;
    private final RmProyectoRevisionService rmProyectoRevisionService;
    private final RmAsignacionService rmAsignacionService;
    private final RmSolicitudPersonalService rmSolicitudPersonalService;
    private final RmCertificadoService rmCertificadoService;
    private final RmForoConsultaService rmForoConsultaService;
    private final RmReporteService rmReporteService;
    private final RmReporteExportService rmReporteExportService;
    private final RmCursoService rmCursoService;
    private final RmPresupuestoService rmPresupuestoService;
    private final EvaluacionService evaluacionService;

    public RmViewController(
            RmPerfilService rmPerfilService,
            RmColaboradorConsultaService rmColaboradorConsultaService,
            RmProyectoConsultaService rmProyectoConsultaService,
            RmProyectoRevisionService rmProyectoRevisionService,
            RmAsignacionService rmAsignacionService,
            RmSolicitudPersonalService rmSolicitudPersonalService,
            RmCertificadoService rmCertificadoService,
            RmForoConsultaService rmForoConsultaService,
            RmReporteService rmReporteService,
            RmReporteExportService rmReporteExportService,
            RmCursoService rmCursoService,
            RmPresupuestoService rmPresupuestoService,
            EvaluacionService evaluacionService) {
        this.rmPerfilService = rmPerfilService;
        this.rmColaboradorConsultaService = rmColaboradorConsultaService;
        this.rmProyectoConsultaService = rmProyectoConsultaService;
        this.rmProyectoRevisionService = rmProyectoRevisionService;
        this.rmAsignacionService = rmAsignacionService;
        this.rmSolicitudPersonalService = rmSolicitudPersonalService;
        this.rmCertificadoService = rmCertificadoService;
        this.rmForoConsultaService = rmForoConsultaService;
        this.rmReporteService = rmReporteService;
        this.rmReporteExportService = rmReporteExportService;
        this.rmCursoService = rmCursoService;
        this.rmPresupuestoService = rmPresupuestoService;
        this.evaluacionService = evaluacionService;
    }

    // Disponible en el modelo de todas las páginas de este controlador (topbar).
    // Se lee de la BD para que la foto y el nombre no dependan de la copia guardada al iniciar sesión.
    @org.springframework.web.bind.annotation.ModelAttribute("rm")
    public Usuario rm(@AuthenticationPrincipal UsuarioDetails principal) {
        return principal != null ? rmPerfilService.obtenerPerfil(principal.getUsuario().getId()) : null;
    }

    @GetMapping({"", "/"})
    public String index() {
        return "redirect:/rm/dashboard";
    }

    @GetMapping({"/dashboard", "/rm-dashboard.html"})
    public String dashboard(Model model) {
        List<RmAsignacionView> asignaciones = rmAsignacionService.listar();
        List<RmAsignacionView> pendientes = asignaciones.stream()
                .filter(item -> item.isRequiereDecisionRm() || item.isPendientePm())
                .toList();
        List<RmProyectoView> proyectos = rmProyectoConsultaService.listar();
        List<RmProyectoView> proyectosAtencion = proyectos.stream()
                .filter(this::requiereAtencionDashboard)
                .sorted(Comparator.comparingInt(this::puntajeAtencionDashboard).reversed()
                        .thenComparing(proyecto -> proyecto.getProyecto().getFechaCreacion(),
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<RmSolicitudPersonalView> solicitudes = rmSolicitudPersonalService.listar();
        long solicitudesAbiertas = solicitudes.stream()
                .filter(item -> item.isPendiente() || item.isEnAtencion())
                .count();
        long certificadosPendientes = rmCertificadoService.listarPendientes().size();

        model.addAttribute("totalAprobacionesRm", asignaciones.stream()
                .filter(RmAsignacionView::isRequiereDecisionRm).count());
        model.addAttribute("totalEsperandoPm", asignaciones.stream()
                .filter(RmAsignacionView::isPendientePm).count());
        model.addAttribute("totalPostulaciones", asignaciones.stream()
                .filter(RmAsignacionView::isSolicitudColaborador)
                .filter(RmAsignacionView::isRequiereDecisionRm).count());
        model.addAttribute("totalProyectosVacantes", proyectos.stream()
                .filter(RmProyectoView::isConVacantesParaDotacion).count());
        model.addAttribute("totalProyectosRevision", proyectos.stream()
                .filter(item -> item.getProyecto().getEstado() == EstadoProyecto.EN_REVISION).count());

        model.addAttribute("accionesPendientes", pendientes.stream().limit(4).toList());
        model.addAttribute("totalAccionesPendientes", pendientes.size());
        model.addAttribute("solicitudesRecientes", solicitudes.stream().limit(3).toList());
        model.addAttribute("totalSolicitudesAbiertas", solicitudesAbiertas);
        model.addAttribute("totalCertificadosPendientes", certificadosPendientes);
        model.addAttribute("proyectosAtencion", proyectosAtencion.stream().limit(5).toList());
        model.addAttribute("totalProyectosAtencion", proyectosAtencion.size());
        model.addAttribute("proyectoPrioritario",
                proyectosAtencion.stream().findFirst().orElse(null));
        return "rm/rm-dashboard";
    }

    private boolean requiereAtencionDashboard(RmProyectoView proyecto) {
        return proyecto.getProyecto().getEstado() == EstadoProyecto.EN_REVISION
                || proyecto.isConVacantesParaDotacion()
                || proyecto.getPendientesRm() > 0;
    }

    private int puntajeAtencionDashboard(RmProyectoView proyecto) {
        int puntaje = proyecto.getPendientesRm() * 10 + proyecto.getVacantes() * 3;
        if (proyecto.getProyecto().getEstado() == EstadoProyecto.EN_REVISION) puntaje += 30;
        return switch (proyecto.getProyecto().getPrioridad()) {
            case ALTA -> puntaje + 20;
            case MEDIA -> puntaje + 10;
            case BAJA -> puntaje;
        };
    }

    @GetMapping({"/proyectos", "/rm-proyectos.html"})
    public String projects(
            @RequestParam(name = "noEncontrado", required = false) Boolean noEncontrado,
            Model model) {
        List<RmProyectoView> proyectos = rmProyectoConsultaService.listar();
        model.addAttribute("proyectos", proyectos);
        model.addAttribute("totalProyectos", proyectos.size());
        model.addAttribute("totalConVacantes", proyectos.stream()
                .filter(RmProyectoView::isConVacantesParaDotacion)
                .count());
        model.addAttribute("totalPendientesRm", proyectos.stream()
                .mapToInt(RmProyectoView::getPendientesRm)
                .sum());
        model.addAttribute("totalEnRevision", proyectos.stream()
                .filter(proyecto -> proyecto.getProyecto().getEstado().name().equals("EN_REVISION"))
                .count());
        model.addAttribute("proyectoNoEncontrado", Boolean.TRUE.equals(noEncontrado));
        return "rm/rm-proyectos";
    }

    @GetMapping({"/proyectos/detalle", "/rm-detalle-proyecto.html"})
    public String projectDetail(
            @RequestParam(name = "id", required = false) Long proyectoId,
            Model model) {
        if (proyectoId == null) return "redirect:/rm/proyectos";
        try {
            RmProyectoView proyecto = rmProyectoConsultaService.obtener(proyectoId);
            model.addAttribute("proyecto", proyecto);
            model.addAttribute("resumen", proyecto.getResumenPresupuesto());
        } catch (IllegalArgumentException ex) {
            return "redirect:/rm/proyectos?noEncontrado=true";
        }
        return "rm/rm-detalle-proyecto";
    }

    @GetMapping({"/proyectos/revision", "/rm-revision-proyecto.html"})
    public String projectReview(
            @RequestParam(name = "id", required = false) Long proyectoId,
            Model model) {
        if (proyectoId == null) return "redirect:/rm/proyectos";
        try {
            RmProyectoView proyecto = rmProyectoConsultaService.obtener(proyectoId);
            if (!proyecto.getProyecto().getEstado().name().equals("EN_REVISION")) {
                return "redirect:/rm/proyectos/detalle?id=" + proyectoId;
            }
            model.addAttribute("proyecto", proyecto);
        } catch (IllegalArgumentException ex) {
            return "redirect:/rm/proyectos?noEncontrado=true";
        }
        return "rm/rm-revision-proyecto";
    }

    @PostMapping("/proyectos/{id}/presupuesto")
    public String assignProjectBudget(
            @PathVariable("id") Long proyectoId,
            @RequestParam(name = "presupuesto", required = false) String presupuestoTexto,
            @RequestParam(name = "asignacionId", required = false) Long asignacionId,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            BigDecimal presupuesto = convertirDecimal(
                    presupuestoTexto, "El presupuesto debe ser un monto numérico válido.");
            EstadoProyecto estado = rmProyectoRevisionService.asignarPresupuesto(
                    proyectoId, presupuesto, principal.getUsuario().getId());
            redirectAttributes.addFlashAttribute("mensajeExito", "Presupuesto guardado correctamente.");
            String redireccionAsignacion = redireccionAsignacionDelProyecto(proyectoId, asignacionId);
            if (redireccionAsignacion != null) return redireccionAsignacion;
            return redireccionPresupuestoProyecto(proyectoId, estado);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
            String redireccionAsignacion = redireccionAsignacionDelProyecto(proyectoId, asignacionId);
            if (redireccionAsignacion != null) return redireccionAsignacion;
            return redireccionPresupuestoProyecto(proyectoId, null);
        }
    }

    private String redireccionAsignacionDelProyecto(Long proyectoId, Long asignacionId) {
        if (asignacionId == null) return null;
        try {
            RmAsignacionView asignacion = rmAsignacionService.obtener(asignacionId);
            if (!asignacion.getAsignacion().getProyecto().getId().equals(proyectoId)) return null;
            return redirectDetalleAsignacion(asignacion);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // Vacío => null (el servicio aplica su validación); mal formado => error de negocio.
    private static BigDecimal convertirDecimal(String valor, String mensajeFormato) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return new BigDecimal(valor.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(mensajeFormato);
        }
    }

    private static <E extends Enum<E>> E convertirEnum(Class<E> tipo, String valor, String mensajeFormato) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return Enum.valueOf(tipo, valor.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(mensajeFormato);
        }
    }

    private String redireccionPresupuestoProyecto(Long proyectoId, EstadoProyecto estadoConocido) {
        EstadoProyecto estado = estadoConocido;
        if (estado == null) {
            try {
                estado = rmProyectoConsultaService.obtener(proyectoId).getProyecto().getEstado();
            } catch (IllegalArgumentException ex) {
                return "redirect:/rm/proyectos";
            }
        }
        return estado == EstadoProyecto.EN_REVISION
                ? "redirect:/rm/proyectos/revision?id=" + proyectoId
                : "redirect:/rm/proyectos/detalle?id=" + proyectoId;
    }

    @PostMapping("/proyectos/{id}/aprobar")
    public String approveProject(
            @PathVariable("id") Long proyectoId,
            @RequestParam(name = "observacion", required = false) String observacion,
            @RequestParam(name = "calificacion", required = false) Integer calificacion,
            @RequestParam(name = "feedback", required = false) String feedback,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            rmProyectoRevisionService.aprobar(
                    proyectoId, observacion, principal.getUsuario().getId());
            redirectAttributes.addFlashAttribute("mensajeExito", "Proyecto aprobado y activado correctamente.");
            return "redirect:/rm/proyectos/detalle?id=" + proyectoId;
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
            return "redirect:/rm/proyectos/revision?id=" + proyectoId;
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
            return "redirect:/rm/proyectos/detalle?id=" + proyectoId;
        }
    }

    @PostMapping("/proyectos/{id}/rechazar")
    public String rejectProject(
            @PathVariable("id") Long proyectoId,
            @RequestParam("motivo") String motivo,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            rmProyectoRevisionService.rechazar(
                    proyectoId, motivo, principal.getUsuario().getId());
            redirectAttributes.addFlashAttribute("mensajeExito", "Proyecto rechazado correctamente.");
            return "redirect:/rm/proyectos/detalle?id=" + proyectoId;
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
            return "redirect:/rm/proyectos/revision?id=" + proyectoId;
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
            return "redirect:/rm/proyectos/detalle?id=" + proyectoId;
        }
    }

    @GetMapping({"/proyectos/detalle-solicitudes", "/rm-detalle-proyecto-solicitudes.html"})
    public String projectDetailWithRequests(
            @RequestParam(name = "solicitudId", required = false) Long solicitudId,
            Model model) {
        if (solicitudId == null) {
            return "redirect:/rm/asignaciones/solicitudes-colaboradores";
        }
        try {
            RmSolicitudPersonalView solicitud = rmSolicitudPersonalService.obtener(solicitudId);
            model.addAttribute("solicitud", solicitud);
            model.addAttribute("proyecto", solicitud.getProyecto());
        } catch (IllegalArgumentException ex) {
            return "redirect:/rm/asignaciones/solicitudes-colaboradores?noEncontrada=true";
        }
        return "rm/rm-detalle-proyecto-solicitudes";
    }

    @GetMapping({"/proyectos/buscar-colaboradores", "/rm-buscar-colaboradores-proyecto.html"})
    public String searchProjectCollaborators(
            @RequestParam(name = "proyectoId", required = false) Long proyectoId,
            Model model) {
        if (proyectoId == null) return "redirect:/rm/proyectos";
        try {
            RmProyectoView proyecto = rmProyectoConsultaService.obtener(proyectoId);
            if (proyecto.getProyecto().getEstado() != EstadoProyecto.ACTIVO
                    && proyecto.getProyecto().getEstado() != EstadoProyecto.EN_ESPERA) {
                return "redirect:/rm/proyectos/detalle?id=" + proyectoId;
            }
            model.addAttribute("proyecto", proyecto);
            model.addAttribute("candidatos",
                    rmColaboradorConsultaService.listarCandidatosParaProyecto(proyecto.getProyecto()));
            model.addAttribute("colaboradoresConAsignacion",
                    rmAsignacionService.colaboradoresConAsignacionVigente(proyecto.getProyecto()));
        } catch (IllegalArgumentException ex) {
            return "redirect:/rm/proyectos?noEncontrado=true";
        }
        return "rm/rm-buscar-colaboradores-proyecto";
    }

    // Contenido del modal "Ver perfil" en Buscar colaboradores (se carga por AJAX).
    @GetMapping("/colaboradores/perfil-modal")
    public String collaboratorProfileModal(@RequestParam(name = "id") Long colaboradorId, Model model) {
        try {
            model.addAttribute("colaborador", rmColaboradorConsultaService.obtenerDetalle(colaboradorId));
            model.addAttribute("sueldoColaborador",
                    rmColaboradorConsultaService.mapaSueldosBase().get(colaboradorId));
        } catch (IllegalArgumentException ex) {
            model.addAttribute("perfilError", ex.getMessage());
        }
        return "fragments/rm-asignacion-modales :: perfilColaborador";
    }

    @GetMapping({"/proyectos/proponer-asignacion", "/rm-proponer-asignacion.html"})
    public String proposeAssignment(
            @RequestParam(name = "proyectoId", required = false) Long proyectoId,
            @RequestParam(name = "colaboradorId", required = false) Long colaboradorId,
            Model model) {
        List<com.pucp.skillb_ia.model.Proyecto> proyectos = rmAsignacionService.listarProyectosAsignables();
        java.util.Map<Long, BigDecimal> disponiblePorProyecto = proyectos.stream()
                .collect(java.util.stream.Collectors.toMap(com.pucp.skillb_ia.model.Proyecto::getId,
                        p -> rmPresupuestoService.calcularResumen(p).disponible()));

        model.addAttribute("proyectos", proyectos);
        model.addAttribute("colaboradores", rmColaboradorConsultaService.listarColaboradoresActivos());
        model.addAttribute("sueldosPorColaborador", rmColaboradorConsultaService.mapaSueldosBase());
        model.addAttribute("disponiblePorProyecto", disponiblePorProyecto);
        model.addAttribute("proyectoSeleccionadoId", proyectoId);
        model.addAttribute("colaboradorSeleccionadoId", colaboradorId);
        return "rm/rm-proponer-asignacion";
    }

    @PostMapping("/asignaciones/proponer")
    public String createAssignmentProposal(
            @RequestParam("proyectoId") Long proyectoId,
            @RequestParam("colaboradorId") Long colaboradorId,
            @RequestParam(name = "horasSemanales", required = false) String horasSemanalesTexto,
            @RequestParam(name = "justificacion", required = false) String justificacion,
            @RequestParam(name = "motivoCapacidad", required = false) String motivoCapacidad,
            @RequestParam(name = "origen", required = false) String origen,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            BigDecimal horasSemanales = convertirDecimal(
                    horasSemanalesTexto, "Las horas semanales deben ser un número válido.");
            rmAsignacionService.proponerDesdeRm(
                    proyectoId, colaboradorId, horasSemanales,
                    justificacion, motivoCapacidad, principal.getUsuario().getId());
            redirectAttributes.addFlashAttribute(
                    "mensajeExito", "Propuesta enviada al Project Manager correctamente.");
            return "redirect:/rm/asignaciones";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
            // Si la propuesta vino de un modal, se vuelve a la pantalla de origen.
            if ("buscar".equals(origen)) {
                return "redirect:/rm/proyectos/buscar-colaboradores?proyectoId=" + proyectoId;
            }
            if ("perfil".equals(origen)) {
                return "redirect:/rm/colaboradores/perfil?id=" + colaboradorId;
            }
            return "redirect:/rm/proyectos/proponer-asignacion?proyectoId=" + proyectoId
                    + "&colaboradorId=" + colaboradorId;
        }
    }

    @GetMapping({"/colaboradores", "/rm-colaboradores.html"})
    public String collaborators(
            @RequestParam(name = "noEncontrado", required = false) Boolean noEncontrado,
            Model model) {
        List<RmColaboradorResumen> colaboradores =
                rmColaboradorConsultaService.listarColaboradoresActivos();

        model.addAttribute("colaboradores", colaboradores);
        model.addAttribute("totalColaboradores", colaboradores.size());
        model.addAttribute("totalDisponibles", colaboradores.stream()
                .filter(colaborador -> colaborador.getHorasDisponibles()
                        .compareTo(BigDecimal.valueOf(16)) >= 0)
                .count());
        model.addAttribute("totalSinAsignaciones", colaboradores.stream()
                .filter(colaborador -> colaborador.getAsignacionesActivas() == 0)
                .count());
        model.addAttribute("totalCargaMaxima", colaboradores.stream()
                .filter(RmColaboradorResumen::isCargaMaxima)
                .count());
        model.addAttribute("colaboradorNoEncontrado", Boolean.TRUE.equals(noEncontrado));

        return "rm/rm-colaboradores";
    }

    @GetMapping({"/colaboradores/perfil", "/rm-perfil-colaborador.html"})
    public String collaboratorProfile(
            @RequestParam(name = "id", required = false) Long colaboradorId,
            Model model) {
        if (colaboradorId == null) {
            return "redirect:/rm/colaboradores";
        }

        try {
            RmColaboradorDetalle colaborador =
                    rmColaboradorConsultaService.obtenerDetalle(colaboradorId);
            model.addAttribute("colaborador", colaborador);
            // Datos para el modal "Proponer asignación": proyectos que aceptan
            // colaboradores (activos o en espera) y el sueldo para estimar el costo.
            model.addAttribute("proyectosAsignables", rmProyectoConsultaService.listar().stream()
                    .filter(p -> p.getProyecto().getEstado() == EstadoProyecto.ACTIVO
                            || p.getProyecto().getEstado() == EstadoProyecto.EN_ESPERA)
                    .toList());
            model.addAttribute("proyectosConAsignacion",
                    rmAsignacionService.proyectosConAsignacionVigente(colaboradorId));
            model.addAttribute("sueldoColaborador",
                    rmColaboradorConsultaService.mapaSueldosBase().get(colaboradorId));
        } catch (IllegalArgumentException ex) {
            return "redirect:/rm/colaboradores?noEncontrado=true";
        }

        return "rm/rm-perfil-colaborador";
    }

    @GetMapping({"/colaboradores/asignaciones", "/rm-asignaciones-colaborador.html"})
    public String collaboratorAssignments(
            @RequestParam(name = "id", required = false) Long colaboradorId,
            Model model) {
        if (colaboradorId == null) return "redirect:/rm/colaboradores";
        try {
            model.addAttribute("colaborador", rmColaboradorConsultaService.obtenerDetalle(colaboradorId));
            model.addAttribute("asignaciones", rmAsignacionService.listarPorColaborador(colaboradorId));
        } catch (IllegalArgumentException ex) {
            return "redirect:/rm/colaboradores?noEncontrado=true";
        }
        return "rm/rm-asignaciones-colaborador";
    }

    @GetMapping({"/colaboradores/certificados", "/rm-certificados-pendientes.html"})
    public String pendingCertificates(
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) String habilidad,
            @RequestParam(required = false) String pagina,
            Model model) {
        var certificados = rmCertificadoService.listarPendientes();
        var habilidades = rmCertificadoService.habilidadesDisponibles(certificados);
        var filtros = rmCertificadoService.normalizarFiltros(busqueda, habilidad, null, habilidades);
        agregarPaginaCertificados(model, rmCertificadoService.paginarPendientes(certificados, filtros, pagina),
                filtros, habilidades);
        model.addAttribute("totalPendientes", certificados.size());
        model.addAttribute("totalColaboradores", certificados.stream()
                .map(item -> item.getCertificado().getColaborador().getId()).distinct().count());
        model.addAttribute("totalHabilidades", certificados.stream()
                .map(item -> item.getCertificado().getHabilidad().getId()).distinct().count());
        model.addAttribute("revisadosHoy", rmCertificadoService.contarRevisadosHoy());
        return "rm/rm-certificados-pendientes";
    }

    @GetMapping({"/colaboradores/certificados/revision", "/rm-revision-certificado.html"})
    public String certificateReview(
            @RequestParam(name = "id", required = false) Long certificadoId,
            Model model) {
        if (certificadoId == null) return "redirect:/rm/colaboradores/certificados";
        try {
            model.addAttribute("certificado", rmCertificadoService.obtener(certificadoId));
        } catch (IllegalArgumentException ex) {
            return "redirect:/rm/colaboradores/certificados";
        }
        return "rm/rm-revision-certificado";
    }

    @GetMapping({"/colaboradores/historial-validaciones", "/rm-historial-validaciones.html"})
    public String validationHistory(
            @RequestParam(name = "id", required = false) Long colaboradorId,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String habilidad,
            @RequestParam(required = false) String pagina,
            Model model) {
        if (colaboradorId == null) return "redirect:/rm/colaboradores";
        try {
            var colaborador = rmColaboradorConsultaService.obtenerDetalle(colaboradorId);
            var certificados = rmCertificadoService.listarHistorial(colaboradorId);
            var habilidades = rmCertificadoService.habilidadesDisponibles(certificados);
            var filtros = rmCertificadoService.normalizarFiltros(busqueda, habilidad, estado, habilidades);
            model.addAttribute("colaborador", colaborador);
            agregarPaginaCertificados(model, rmCertificadoService.paginarHistorial(certificados, filtros, pagina),
                    filtros, habilidades);
            model.addAttribute("estado", filtros.estado() == null ? null : filtros.estado().name());
            model.addAttribute("totalDocumentos", certificados.size());
            model.addAttribute("aprobados", certificados.stream()
                    .filter(item -> item.getCertificado().getEstado().name().equals("APROBADO")).count());
            model.addAttribute("rechazados", certificados.stream()
                    .filter(item -> item.getCertificado().getEstado().name().equals("RECHAZADO")).count());
            model.addAttribute("pendientes", certificados.stream().filter(item -> item.isPendiente()).count());
        } catch (IllegalArgumentException ex) {
            return "redirect:/rm/colaboradores?noEncontrado=true";
        }
        return "rm/rm-historial-validaciones";
    }

    private void agregarPaginaCertificados(Model model, RmCertificadoService.PaginaCertificados pagina,
                                           RmCertificadoService.FiltrosCertificados filtros,
                                           List<String> habilidades) {
        model.addAttribute("certificados", pagina.filas());
        model.addAttribute("paginaActual", pagina.paginaActual());
        model.addAttribute("totalPaginas", pagina.totalPaginas());
        model.addAttribute("totalRegistros", pagina.totalRegistros());
        model.addAttribute("tamanioPagina", RmCertificadoService.TAMANIO_PAGINA);
        model.addAttribute("habilidades", habilidades);
        model.addAttribute("busqueda", filtros.busqueda());
        model.addAttribute("habilidad", filtros.habilidad());
    }

    @PostMapping("/colaboradores/certificados/{id}/aprobar")
    public String approveCertificate(
            @PathVariable("id") Long certificadoId,
            @RequestParam(name = "nivelHabilidad", required = false) NivelDominio nivelHabilidad,
            @RequestParam(name = "nivelGeneral", required = false) NivelExperiencia nivelGeneral,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            rmCertificadoService.aprobar(certificadoId, nivelHabilidad, nivelGeneral,
                    principal.getUsuario().getId());
            redirectAttributes.addFlashAttribute("mensajeExito", "Certificado aprobado y perfil actualizado.");
            return "redirect:/rm/colaboradores/certificados";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
            return "redirect:/rm/colaboradores/certificados/revision?id=" + certificadoId;
        }
    }

    @PostMapping("/colaboradores/certificados/{id}/rechazar")
    public String rejectCertificate(
            @PathVariable("id") Long certificadoId,
            @RequestParam("motivo") String motivo,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            rmCertificadoService.rechazar(certificadoId, motivo, principal.getUsuario().getId());
            redirectAttributes.addFlashAttribute("mensajeExito", "Certificado rechazado; el motivo quedó guardado.");
            return "redirect:/rm/colaboradores/certificados";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
            return "redirect:/rm/colaboradores/certificados/revision?id=" + certificadoId;
        }
    }

    @PostMapping("/colaboradores/{id}/nivel-experiencia")
    public String updateCollaboratorExperienceLevel(
            @PathVariable("id") Long colaboradorId,
            @RequestParam(name = "nivel", required = false) String nivelTexto,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            NivelExperiencia nivel = convertirEnum(
                    NivelExperiencia.class, nivelTexto, "Selecciona un nivel de experiencia válido.");
            rmCertificadoService.actualizarNivelExperiencia(
                    colaboradorId, nivel, principal.getUsuario().getId());
            redirectAttributes.addFlashAttribute("mensajeExito", "Nivel de experiencia actualizado.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
        }
        return "redirect:/rm/colaboradores/perfil?id=" + colaboradorId;
    }

    @GetMapping({"/cursos", "/rm-cursos.html"})
    public String courses(
            @RequestParam(name = "busqueda", required = false) String busqueda,
            @RequestParam(name = "categoria", required = false) String categoria,
            @RequestParam(name = "duracion", required = false) String duracion,
            Model model) {
        model.addAttribute("catalogo", rmCursoService.obtenerCatalogo(busqueda, categoria, duracion));
        model.addAttribute("busqueda", busqueda == null ? "" : busqueda);
        model.addAttribute("categoriaSeleccionada", categoria == null ? "" : categoria);
        model.addAttribute("duracionSeleccionada", duracion == null ? "" : duracion);
        return "rm/rm-cursos";
    }

    @GetMapping({"/cursos/solicitudes", "/rm-solicitudes-cursos.html"})
    public String courseRequests(
            @RequestParam(name = "busqueda", required = false) String busqueda,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "origen", required = false) String origen,
            Model model) {
        String filtroEstado = estado == null ? "SOLICITADO" : estado;
        model.addAttribute("bandeja", rmCursoService.obtenerBandeja(busqueda, filtroEstado, origen));
        model.addAttribute("busqueda", busqueda == null ? "" : busqueda);
        model.addAttribute("estadoSeleccionado", filtroEstado);
        model.addAttribute("origenSeleccionado", origen == null ? "" : origen);
        return "rm/rm-solicitudes-cursos";
    }

    @GetMapping({"/cursos/asignar", "/rm-asignar-curso.html"})
    public String assignCourse(
            @RequestParam(name = "colaborador", required = false) Long colaboradorId,
            @RequestParam(name = "curso", required = false) Long cursoId,
            Model model) {
        model.addAttribute("colaboradoresCurso", rmCursoService.listarColaboradoresActivos());
        model.addAttribute("cursosActivos", rmCursoService.listarCursosActivos());
        model.addAttribute("colaboradorSeleccionado", colaboradorId);
        model.addAttribute("cursoSeleccionado", cursoId);
        return "rm/rm-asignar-curso";
    }

    @PostMapping("/cursos/asignar")
    public String assignCourseSubmit(
            @RequestParam("colaboradorId") Long colaboradorId,
            @RequestParam("cursoId") Long cursoId,
            @RequestParam("motivo") String motivo,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            rmCursoService.asignarDirectamente(
                    colaboradorId, cursoId, motivo, principal.getUsuario().getId());
            redirectAttributes.addFlashAttribute("mensajeExito",
                    "Curso asignado correctamente; el colaborador fue notificado.");
            return "redirect:/rm/cursos/solicitudes?estado=EN_CURSO";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
            return "redirect:/rm/cursos/asignar?colaborador=" + colaboradorId + "&curso=" + cursoId;
        }
    }

    @PostMapping("/cursos/solicitudes/{id}/aprobar")
    public String approveCourseRequest(
            @PathVariable("id") Long inscripcionId,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            rmCursoService.aprobar(inscripcionId, principal.getUsuario().getId());
            redirectAttributes.addFlashAttribute("mensajeExito",
                    "Solicitud aprobada; el colaborador fue notificado.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
        }
        return "redirect:/rm/cursos/solicitudes";
    }

    @PostMapping("/cursos/solicitudes/{id}/rechazar")
    public String rejectCourseRequest(
            @PathVariable("id") Long inscripcionId,
            @RequestParam("motivo") String motivo,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            rmCursoService.rechazar(inscripcionId, motivo, principal.getUsuario().getId());
            redirectAttributes.addFlashAttribute("mensajeExito",
                    "Solicitud rechazada; el motivo quedó registrado y se notificó al colaborador.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
        }
        return "redirect:/rm/cursos/solicitudes";
    }

    @GetMapping({"/asignaciones", "/rm-asignaciones.html"})
    public String assignments(Model model) {
        List<RmAsignacionView> asignaciones = rmAsignacionService.listar();
        model.addAttribute("asignaciones", asignaciones);
        model.addAttribute("pendientesRm", asignaciones.stream()
                .filter(RmAsignacionView::isRequiereDecisionRm).count());
        model.addAttribute("pendientesPm", asignaciones.stream()
                .filter(RmAsignacionView::isPendientePm).count());
        model.addAttribute("solicitudesColaborador", asignaciones.stream()
                .filter(RmAsignacionView::isSolicitudColaborador)
                .filter(item -> item.getAsignacion().getEstado().name().equals("PENDIENTE"))
                .count());
        model.addAttribute("activas", asignaciones.stream()
                .filter(RmAsignacionView::isActiva).count());
        model.addAttribute("historial", asignaciones.stream()
                .filter(item -> "history".equals(item.getGrupo())).count());
        return "rm/rm-asignaciones";
    }

    @GetMapping({"/asignaciones/revision", "/rm-revision-asignacion.html"})
    public String assignmentReview(
            @RequestParam(name = "id", required = false) Long asignacionId,
            Model model) {
        if (asignacionId == null) return "redirect:/rm/asignaciones";
        try {
            RmAsignacionView asignacion = rmAsignacionService.obtener(asignacionId);
            if (!asignacion.isRequiereDecisionRm()) return redirectDetalleAsignacion(asignacion);
            model.addAttribute("asignacion", asignacion);
            model.addAttribute("impacto", rmPresupuestoService.calcularImpacto(
                    asignacion.getAsignacion().getProyecto(), asignacion.getAsignacion().getColaborador(),
                    asignacion.getAsignacion().getHorasSemanales()));
        } catch (IllegalArgumentException ex) {
            return "redirect:/rm/asignaciones";
        }
        return "rm/rm-revision-asignacion";
    }

    @GetMapping({"/asignaciones/revision-postulacion", "/rm-revision-postulacion.html"})
    public String applicationReview(
            @RequestParam(name = "id", required = false) Long asignacionId,
            Model model) {
        if (asignacionId == null) return "redirect:/rm/asignaciones";
        try {
            RmAsignacionView asignacion = rmAsignacionService.obtener(asignacionId);
            if (!asignacion.isRequiereDecisionRm()) return redirectDetalleAsignacion(asignacion);
            model.addAttribute("asignacion", asignacion);
            model.addAttribute("impacto", rmPresupuestoService.calcularImpacto(
                    asignacion.getAsignacion().getProyecto(), asignacion.getAsignacion().getColaborador(),
                    asignacion.getAsignacion().getHorasSemanales()));
        } catch (IllegalArgumentException ex) {
            return "redirect:/rm/asignaciones";
        }
        return "rm/rm-revision-postulacion";
    }

    @GetMapping({"/asignaciones/pendiente-pm", "/rm-detalle-asignacion-pendiente-pm.html"})
    public String pendingPmAssignment(
            @RequestParam(name = "id", required = false) Long asignacionId,
            Model model) {
        if (asignacionId == null) return "redirect:/rm/asignaciones";
        try {
            RmAsignacionView asignacion = rmAsignacionService.obtener(asignacionId);
            if (!asignacion.isPendientePm()) return redirectDetalleAsignacion(asignacion);
            model.addAttribute("asignacion", asignacion);
        } catch (IllegalArgumentException ex) {
            return "redirect:/rm/asignaciones";
        }
        return "rm/rm-detalle-asignacion-pendiente-pm";
    }

    @GetMapping({"/asignaciones/activa", "/rm-detalle-asignacion-activa.html"})
    public String activeAssignment(
            @RequestParam(name = "id", required = false) Long asignacionId,
            Model model) {
        if (asignacionId == null) return "redirect:/rm/asignaciones";
        try {
            RmAsignacionView asignacion = rmAsignacionService.obtener(asignacionId);
            if (asignacion.isRequiereDecisionRm() || asignacion.isPendientePm()) {
                return redirectDetalleAsignacion(asignacion);
            }
            model.addAttribute("asignacion", asignacion);
        } catch (IllegalArgumentException ex) {
            return "redirect:/rm/asignaciones";
        }
        return "rm/rm-detalle-asignacion-activa";
    }

    @PostMapping("/asignaciones/{id}/aprobar")
    public String approveAssignment(
            @PathVariable("id") Long asignacionId,
            @RequestParam(name = "motivoCapacidad", required = false) String motivoCapacidad,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            rmAsignacionService.aprobar(asignacionId, motivoCapacidad, principal.getUsuario().getId());
            redirectAttributes.addFlashAttribute("mensajeExito", "Asignación aprobada correctamente.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
        }
        return "redirect:/rm/asignaciones";
    }

    @PostMapping("/asignaciones/{id}/rechazar")
    public String rejectAssignment(
            @PathVariable("id") Long asignacionId,
            @RequestParam("motivo") String motivo,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            rmAsignacionService.rechazar(asignacionId, motivo, principal.getUsuario().getId());
            redirectAttributes.addFlashAttribute("mensajeExito", "Asignación rechazada correctamente.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
        }
        return "redirect:/rm/asignaciones";
    }

    @PostMapping("/asignaciones/{id}/finalizar")
    public String finishAssignment(
            @PathVariable("id") Long asignacionId,
            @RequestParam(name = "motivo", required = false) String motivoTexto,
            @RequestParam(name = "observacion", required = false) String observacion,
            @RequestParam(name = "calificacion", required = false) Integer calificacion,
            @RequestParam(name = "feedback", required = false) String feedback,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            MotivoFinalizacion motivo = convertirEnum(
                    MotivoFinalizacion.class, motivoTexto, "Selecciona un motivo de finalización válido.");
            rmAsignacionService.finalizar(
                    asignacionId, motivo, observacion, calificacion, feedback, principal.getUsuario().getId());
            redirectAttributes.addFlashAttribute("mensajeExito", "Asignación finalizada correctamente.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
        }
        return "redirect:/rm/asignaciones";
    }

    private String redirectDetalleAsignacion(RmAsignacionView asignacion) {
        Long id = asignacion.getAsignacion().getId();
        if (asignacion.isRequiereDecisionRm()) {
            return asignacion.isSolicitudColaborador()
                    ? "redirect:/rm/asignaciones/revision-postulacion?id=" + id
                    : "redirect:/rm/asignaciones/revision?id=" + id;
        }
        if (asignacion.isPendientePm()) {
            return "redirect:/rm/asignaciones/pendiente-pm?id=" + id;
        }
        return "redirect:/rm/asignaciones/activa?id=" + id;
    }

    @GetMapping({"/asignaciones/solicitudes-colaboradores", "/rm-solicitudes-colaboradores.html"})
    public String collaboratorRequests(
            @RequestParam(name = "noEncontrada", required = false) Boolean noEncontrada,
            Model model) {
        List<RmSolicitudPersonalView> solicitudes = rmSolicitudPersonalService.listar();
        model.addAttribute("solicitudes", solicitudes);
        model.addAttribute("pendientes", solicitudes.stream().filter(RmSolicitudPersonalView::isPendiente).count());
        model.addAttribute("enAtencion", solicitudes.stream().filter(RmSolicitudPersonalView::isEnAtencion).count());
        model.addAttribute("atendidas", solicitudes.stream()
                .filter(item -> item.getSolicitud().getEstado().name().equals("ATENDIDA")).count());
        model.addAttribute("totalSolicitados", solicitudes.stream()
                .mapToInt(item -> item.getSolicitud().getCantidadColaboradores()).sum());
        model.addAttribute("solicitudNoEncontrada", Boolean.TRUE.equals(noEncontrada));
        return "rm/rm-solicitudes-colaboradores";
    }

    @GetMapping({"/asignaciones/solicitudes-colaboradores/detalle", "/rm-detalle-solicitud-colaboradores.html"})
    public String collaboratorRequestDetail(
            @RequestParam(name = "id", required = false) Long solicitudId,
            Model model) {
        if (solicitudId == null) {
            return "redirect:/rm/asignaciones/solicitudes-colaboradores";
        }
        try {
            model.addAttribute("solicitud", rmSolicitudPersonalService.obtener(solicitudId));
        } catch (IllegalArgumentException ex) {
            return "redirect:/rm/asignaciones/solicitudes-colaboradores?noEncontrada=true";
        }
        return "rm/rm-detalle-solicitud-colaboradores";
    }

    @PostMapping("/asignaciones/solicitudes-colaboradores/{id}/iniciar")
    public String startCollaboratorRequest(
            @PathVariable("id") Long solicitudId,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            rmSolicitudPersonalService.iniciarAtencion(solicitudId, principal.getUsuario().getId());
            RmSolicitudPersonalView solicitud = rmSolicitudPersonalService.obtener(solicitudId);
            redirectAttributes.addFlashAttribute("mensajeExito", "La solicitud pasó a En atención.");
            return "redirect:/rm/proyectos/buscar-colaboradores?proyectoId="
                    + solicitud.getSolicitud().getProyecto().getId();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
            return "redirect:/rm/asignaciones/solicitudes-colaboradores/detalle?id=" + solicitudId;
        }
    }

    @PostMapping("/asignaciones/solicitudes-colaboradores/{id}/atendida")
    public String finishCollaboratorRequest(
            @PathVariable("id") Long solicitudId,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            rmSolicitudPersonalService.marcarAtendida(solicitudId, principal.getUsuario().getId());
            redirectAttributes.addFlashAttribute("mensajeExito", "Solicitud marcada como atendida.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
        }
        return "redirect:/rm/asignaciones/solicitudes-colaboradores/detalle?id=" + solicitudId;
    }

    @GetMapping({"/talent-matching", "/rm-talent-matching.html"})
    public String talentMatching() {
        return "rm/rm-talent-matching";
    }

    @GetMapping({"/foros", "/rm-foros.html"})
    public String forums(@RequestParam(name = "noEncontrado", required = false) Boolean noEncontrado,
                         Model model) {
        List<RmForoView> foros = rmForoConsultaService.listar();
        model.addAttribute("foros", foros);
        model.addAttribute("totalForos", foros.size());
        model.addAttribute("totalActivos", foros.stream()
                .filter(foro -> "ACTIVO".equals(foro.getProyectoEstadoCodigo()))
                .count());
        model.addAttribute("totalPublicaciones", foros.stream()
                .mapToInt(RmForoView::getTotalPublicaciones).sum());
        model.addAttribute("ultimaActividad", foros.stream()
                .map(RmForoView::getUltimaActividad)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null));
        model.addAttribute("foroNoEncontrado", Boolean.TRUE.equals(noEncontrado));
        return "rm/rm-foros";
    }

    @GetMapping({"/foros/detalle", "/rm-foro-detalle.html"})
    public String forumDetail(@RequestParam Long id,
                              @RequestParam(defaultValue = "fecha") String ordenar,
                              Model model) {
        try {
            model.addAttribute("foro", rmForoConsultaService.obtener(id, ordenar));
            model.addAttribute("ordenActual", "votos".equalsIgnoreCase(ordenar) ? "votos" : "fecha");
            return "rm/rm-foro-detalle";
        } catch (IllegalArgumentException e) {
            return "redirect:/rm/foros?noEncontrado=true";
        }
    }

    @GetMapping({"/reportes/recursos", "/rm-reporte-recursos.html"})
    public String resourceReport(
            @RequestParam(name = "periodo", required = false) String periodo,
            @RequestParam(name = "proyecto", required = false) Long proyectoId,
            @RequestParam(name = "estado", required = false) String estado,
            Model model) {
        RmReporteView reporte = rmReporteService.generar(periodo, proyectoId, estado);
        cargarModeloReporte(model, reporte);
        return "rm/rm-reporte-recursos";
    }

    @GetMapping({"/reportes/horas-colaboradores", "/rm-horas-colaboradores.html"})
    public String collaboratorHours(
            @RequestParam(name = "periodo", required = false) String periodo,
            @RequestParam(name = "proyecto", required = false) Long proyectoId,
            @RequestParam(name = "busqueda", required = false) String busqueda,
            @RequestParam(name = "colaborador", required = false) Long colaboradorId,
            Model model) {
        RmReporteView reporte = rmReporteService.generar(periodo, proyectoId, null);
        List<RmReporteView.ColaboradorReporte> colaboradores =
                rmReporteService.filtrarColaboradores(reporte, busqueda);
        RmReporteView.ColaboradorReporte seleccionado = colaboradores.stream()
                .filter(item -> colaboradorId != null && item.getId().equals(colaboradorId))
                .findFirst()
                .orElseGet(() -> colaboradores.stream().findFirst().orElse(null));
        cargarModeloReporte(model, reporte);
        model.addAttribute("colaboradores", colaboradores);
        model.addAttribute("colaboradorSeleccionado", seleccionado);
        model.addAttribute("busqueda", busqueda == null ? "" : busqueda);
        model.addAttribute("totalColaboradoresHoras", colaboradores.size());
        model.addAttribute("totalHorasColaboradores", colaboradores.stream()
                .map(RmReporteView.ColaboradorReporte::getHoras)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        model.addAttribute("debajoReferencia", colaboradores.stream()
                .filter(item -> !item.isAlcanzaReferencia()).count());
        model.addAttribute("enReferencia", colaboradores.stream()
                .filter(RmReporteView.ColaboradorReporte::isAlcanzaReferencia).count());
        return "rm/rm-horas-colaboradores";
    }

    @GetMapping("/reportes/recursos/excel")
    public ResponseEntity<byte[]> exportarReporteExcel(
            @RequestParam(name = "periodo", required = false) String periodo,
            @RequestParam(name = "proyecto", required = false) Long proyectoId,
            @RequestParam(name = "estado", required = false) String estado) {
        RmReporteView reporte = rmReporteService.generar(periodo, proyectoId, estado);
        return archivo(rmReporteExportService.crearExcel(reporte),
                "reporte-recursos-" + reporte.getPeriodoValor() + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @GetMapping("/reportes/recursos/pdf")
    public ResponseEntity<byte[]> exportarReportePdf(
            @RequestParam(name = "periodo", required = false) String periodo,
            @RequestParam(name = "proyecto", required = false) Long proyectoId,
            @RequestParam(name = "estado", required = false) String estado) {
        RmReporteView reporte = rmReporteService.generar(periodo, proyectoId, estado);
        return archivo(rmReporteExportService.crearPdf(reporte),
                "reporte-recursos-" + reporte.getPeriodoValor() + ".pdf",
                MediaType.APPLICATION_PDF_VALUE);
    }

    private void cargarModeloReporte(Model model, RmReporteView reporte) {
        model.addAttribute("reporte", reporte);
        model.addAttribute("periodos", rmReporteService.listarPeriodos(reporte.getPeriodoValor()));
        model.addAttribute("proyectosFiltro", rmReporteService.listarProyectos());
        model.addAttribute("estadosFiltro", rmReporteService.listarEstados());
    }

    private ResponseEntity<byte[]> archivo(byte[] contenido, String nombre, String contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(nombre, StandardCharsets.UTF_8).build());
        headers.setContentLength(contenido.length);
        return ResponseEntity.ok().headers(headers).body(contenido);
    }

    @GetMapping("/perfil")
    public String perfil(
            @AuthenticationPrincipal UsuarioDetails principal,
            Model model) {

        if (principal == null){
            return "redirect:/login";
        }

        boolean esResourceManager = principal.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_RESOURCE_MANAGER"));

        if (!esResourceManager){
            return "redirect:/login";
        }

        Usuario rm = rmPerfilService.obtenerPerfil(
                principal.getUsuario().getId());

        model.addAttribute("rm", rm);

        return "rm/rm-perfil";

    }

    @PostMapping("/perfil/telefono")
    public String actualizarTelefonoRm(@RequestParam(value = "telefono", required = false) String telefono,
                                        @AuthenticationPrincipal UsuarioDetails principal,
                                        RedirectAttributes redirectAttributes) {
        try {
            rmPerfilService.actualizarTelefono(telefono, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeExito", "Teléfono actualizado correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
            redirectAttributes.addFlashAttribute("telefonoIngresado", telefono);
        }
        return "redirect:/rm/perfil";
    }

    @PostMapping("/perfil/foto")
    public String actualizarFotoRm(@RequestParam("foto") org.springframework.web.multipart.MultipartFile foto,
                                    @AuthenticationPrincipal UsuarioDetails principal,
                                    RedirectAttributes redirectAttributes) {
        try {
            rmPerfilService.actualizarFoto(foto, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeExito", "Foto de perfil actualizada.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/rm/perfil";
    }

    @PostMapping("/perfil/password")
    public String actualizarPasswordRm(@RequestParam("passwordActual") String passwordActual,
                                        @RequestParam("passwordNueva") String passwordNueva,
                                        @RequestParam("passwordConfirm") String passwordConfirm,
                                        @AuthenticationPrincipal UsuarioDetails principal,
                                        RedirectAttributes redirectAttributes) {
        try {
            if (!passwordNueva.equals(passwordConfirm)) {
                throw new IllegalArgumentException("Las contraseñas nuevas no coinciden.");
            }
            rmPerfilService.actualizarPassword(passwordActual, passwordNueva, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeExito", "Contraseña actualizada correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/rm/perfil";
    }
}
