package com.pucp.skillb_ia.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.dto.RmColaboradorDetalle;
import com.pucp.skillb_ia.dto.RmColaboradorResumen;
import com.pucp.skillb_ia.dto.RmProyectoView;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.rm.RmColaboradorConsultaService;
import com.pucp.skillb_ia.service.rm.RmPerfilService;
import com.pucp.skillb_ia.service.rm.RmProyectoConsultaService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/rm")
public class RmViewController {

    private final RmPerfilService rmPerfilService;
    private final RmColaboradorConsultaService rmColaboradorConsultaService;
    private final RmProyectoConsultaService rmProyectoConsultaService;

    public RmViewController(
            RmPerfilService rmPerfilService,
            RmColaboradorConsultaService rmColaboradorConsultaService,
            RmProyectoConsultaService rmProyectoConsultaService) {
        this.rmPerfilService = rmPerfilService;
        this.rmColaboradorConsultaService = rmColaboradorConsultaService;
        this.rmProyectoConsultaService = rmProyectoConsultaService;
    }

    @GetMapping({"", "/"})
    public String index() {
        return "redirect:/rm/dashboard";
    }

    @GetMapping({"/dashboard", "/rm-dashboard.html"})
    public String dashboard() {
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
    public String projectReview() {
        return "rm/rm-revision-proyecto";
    }

    @GetMapping({"/proyectos/detalle-solicitudes", "/rm-detalle-proyecto-solicitudes.html"})
    public String projectDetailWithRequests() {
        return "rm/rm-detalle-proyecto-solicitudes";
    }

    @GetMapping({"/proyectos/buscar-colaboradores", "/rm-buscar-colaboradores-proyecto.html"})
    public String searchProjectCollaborators() {
        return "rm/rm-buscar-colaboradores-proyecto";
    }

    @GetMapping({"/proyectos/proponer-asignacion", "/rm-proponer-asignacion.html"})
    public String proposeAssignment() {
        return "rm/rm-proponer-asignacion";
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
    public String collaboratorAssignments() {
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
    public String assignments() {
        return "rm/rm-asignaciones";
    }

    @GetMapping({"/asignaciones/revision", "/rm-revision-asignacion.html"})
    public String assignmentReview() {
        return "rm/rm-revision-asignacion";
    }

    @GetMapping({"/asignaciones/revision-postulacion", "/rm-revision-postulacion.html"})
    public String applicationReview() {
        return "rm/rm-revision-postulacion";
    }

    @GetMapping({"/asignaciones/pendiente-pm", "/rm-detalle-asignacion-pendiente-pm.html"})
    public String pendingPmAssignment() {
        return "rm/rm-detalle-asignacion-pendiente-pm";
    }

    @GetMapping({"/asignaciones/activa", "/rm-detalle-asignacion-activa.html"})
    public String activeAssignment() {
        return "rm/rm-detalle-asignacion-activa";
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
