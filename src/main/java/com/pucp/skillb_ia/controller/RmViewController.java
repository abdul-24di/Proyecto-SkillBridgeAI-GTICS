package com.pucp.skillb_ia.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.dto.RmColaboradorDetalle;
import com.pucp.skillb_ia.dto.RmColaboradorResumen;
import com.pucp.skillb_ia.dto.RmAsignacionView;
import com.pucp.skillb_ia.dto.RmProyectoView;
import com.pucp.skillb_ia.model.enums.MotivoFinalizacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.rm.RmColaboradorConsultaService;
import com.pucp.skillb_ia.service.rm.RmAsignacionService;
import com.pucp.skillb_ia.service.rm.RmPerfilService;
import com.pucp.skillb_ia.service.rm.RmProyectoConsultaService;
import com.pucp.skillb_ia.service.rm.RmProyectoRevisionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/rm")
public class RmViewController {

    private final RmPerfilService rmPerfilService;
    private final RmColaboradorConsultaService rmColaboradorConsultaService;
    private final RmProyectoConsultaService rmProyectoConsultaService;
    private final RmProyectoRevisionService rmProyectoRevisionService;
    private final RmAsignacionService rmAsignacionService;

    public RmViewController(
            RmPerfilService rmPerfilService,
            RmColaboradorConsultaService rmColaboradorConsultaService,
            RmProyectoConsultaService rmProyectoConsultaService,
            RmProyectoRevisionService rmProyectoRevisionService,
            RmAsignacionService rmAsignacionService) {
        this.rmPerfilService = rmPerfilService;
        this.rmColaboradorConsultaService = rmColaboradorConsultaService;
        this.rmProyectoConsultaService = rmProyectoConsultaService;
        this.rmProyectoRevisionService = rmProyectoRevisionService;
        this.rmAsignacionService = rmAsignacionService;
    }

    @GetMapping({"", "/"})
    public String index() {
        return "redirect:/rm/dashboard";
    }

    @GetMapping({"/dashboard", "/rm-dashboard.html"})
    public String dashboard(Model model) {
        List<RmAsignacionView> pendientes = rmAsignacionService.listar().stream()
                .filter(item -> "pending".equals(item.getGrupo()))
                .toList();
        model.addAttribute("accionesPendientes", pendientes.stream().limit(4).toList());
        model.addAttribute("totalAccionesPendientes", pendientes.size());
        return "rm/rm-dashboard";
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
            model.addAttribute("proyecto", rmProyectoConsultaService.obtener(proyectoId));
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
            @RequestParam("presupuesto") BigDecimal presupuesto,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            EstadoProyecto estado = rmProyectoRevisionService.asignarPresupuesto(
                    proyectoId, presupuesto, principal.getUsuario().getId());
            redirectAttributes.addFlashAttribute("mensajeExito", "Presupuesto guardado correctamente.");
            return estado == EstadoProyecto.EN_REVISION
                    ? "redirect:/rm/proyectos/revision?id=" + proyectoId
                    : "redirect:/rm/proyectos/detalle?id=" + proyectoId;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
            return "redirect:/rm/proyectos/detalle?id=" + proyectoId;
        }
    }

    @PostMapping("/proyectos/{id}/aprobar")
    public String approveProject(
            @PathVariable("id") Long proyectoId,
            @RequestParam(name = "observacion", required = false) String observacion,
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
    public String projectDetailWithRequests() {
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
            model.addAttribute("colaboradores", rmColaboradorConsultaService.listarColaboradoresActivos());
        } catch (IllegalArgumentException ex) {
            return "redirect:/rm/proyectos?noEncontrado=true";
        }
        return "rm/rm-buscar-colaboradores-proyecto";
    }

    @GetMapping({"/proyectos/proponer-asignacion", "/rm-proponer-asignacion.html"})
    public String proposeAssignment(
            @RequestParam(name = "proyectoId", required = false) Long proyectoId,
            @RequestParam(name = "colaboradorId", required = false) Long colaboradorId,
            Model model) {
        model.addAttribute("proyectos", rmAsignacionService.listarProyectosAsignables());
        model.addAttribute("colaboradores", rmColaboradorConsultaService.listarColaboradoresActivos());
        model.addAttribute("proyectoSeleccionadoId", proyectoId);
        model.addAttribute("colaboradorSeleccionadoId", colaboradorId);
        return "rm/rm-proponer-asignacion";
    }

    @PostMapping("/asignaciones/proponer")
    public String createAssignmentProposal(
            @RequestParam("proyectoId") Long proyectoId,
            @RequestParam("colaboradorId") Long colaboradorId,
            @RequestParam("horasSemanales") BigDecimal horasSemanales,
            @RequestParam(name = "justificacion", required = false) String justificacion,
            @RequestParam(name = "motivoCapacidad", required = false) String motivoCapacidad,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            rmAsignacionService.proponerDesdeRm(
                    proyectoId, colaboradorId, horasSemanales,
                    justificacion, motivoCapacidad, principal.getUsuario().getId());
            redirectAttributes.addFlashAttribute(
                    "mensajeExito", "Propuesta enviada al Project Manager correctamente.");
            return "redirect:/rm/asignaciones";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
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
    public String pendingCertificates() {
        return "rm/rm-certificados-pendientes";
    }

    @GetMapping({"/colaboradores/certificados/revision", "/rm-revision-certificado.html"})
    public String certificateReview() {
        return "rm/rm-revision-certificado";
    }

    @GetMapping({"/colaboradores/historial-validaciones", "/rm-historial-validaciones.html"})
    public String validationHistory() {
        return "rm/rm-historial-validaciones";
    }

    @GetMapping({"/cursos", "/rm-cursos.html"})
    public String courses() {
        return "rm/rm-cursos";
    }

    @GetMapping({"/cursos/solicitudes", "/rm-solicitudes-cursos.html"})
    public String courseRequests() {
        return "rm/rm-solicitudes-cursos";
    }

    @GetMapping({"/cursos/asignar", "/rm-asignar-curso.html"})
    public String assignCourse() {
        return "rm/rm-asignar-curso";
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
            @RequestParam("motivo") MotivoFinalizacion motivo,
            @RequestParam(name = "observacion", required = false) String observacion,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            rmAsignacionService.finalizar(
                    asignacionId, motivo, observacion, principal.getUsuario().getId());
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
    public String collaboratorRequests() {
        return "rm/rm-solicitudes-colaboradores";
    }

    @GetMapping({"/asignaciones/solicitudes-colaboradores/detalle", "/rm-detalle-solicitud-colaboradores.html"})
    public String collaboratorRequestDetail() {
        return "rm/rm-detalle-solicitud-colaboradores";
    }

    @GetMapping({"/talent-matching", "/rm-talent-matching.html"})
    public String talentMatching() {
        return "rm/rm-talent-matching";
    }

    @GetMapping({"/foros", "/rm-foros.html"})
    public String forums() {
        return "rm/rm-foros";
    }

    @GetMapping({"/foros/detalle", "/rm-foro-detalle.html"})
    public String forumDetail() {
        return "rm/rm-foro-detalle";
    }

    @GetMapping({"/reportes/recursos", "/rm-reporte-recursos.html"})
    public String resourceReport() {
        return "rm/rm-reporte-recursos";
    }

    @GetMapping({"/reportes/horas-colaboradores", "/rm-horas-colaboradores.html"})
    public String collaboratorHours() {
        return "rm/rm-horas-colaboradores";
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
}
