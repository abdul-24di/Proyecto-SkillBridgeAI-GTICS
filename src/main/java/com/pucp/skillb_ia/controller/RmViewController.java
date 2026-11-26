package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.service.CierreAsignacionesService;
import com.pucp.skillb_ia.service.EvaluacionService;
import com.pucp.skillb_ia.service.rm.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.dto.RmColaboradorDetalle;
import com.pucp.skillb_ia.dto.RmAsignacionView;
import com.pucp.skillb_ia.dto.RmNavegacionView;
import com.pucp.skillb_ia.dto.RmProyectoView;
import com.pucp.skillb_ia.dto.RmSolicitudPersonalView;
import com.pucp.skillb_ia.dto.RmReporteView;
import com.pucp.skillb_ia.dto.RmCursoView;
import com.pucp.skillb_ia.dto.RmEducacionView;
import com.pucp.skillb_ia.model.enums.EstadoCertificado;
import com.pucp.skillb_ia.model.enums.MotivoFinalizacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.NivelDominio;
import com.pucp.skillb_ia.model.enums.NivelExperiencia;
import com.pucp.skillb_ia.security.UsuarioDetails;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import static com.pucp.skillb_ia.controller.GlobalExceptionHandler.mensajeSeguro;

/**
 * Vistas y formularios del Resource Manager bajo {@code /rm}: dashboard, revisión y cierre de proyectos,
 * directorio y perfil de colaboradores, validación de certificados y formación académica, cursos,
 * bandeja de asignaciones, solicitudes de personal del PM, Talent Matching con su asistente IA, foros
 * (lectura), reportes y perfil propio. Los foros con escritura están en {@link RmForoController} y el
 * chat en {@link RmChatViewController}.
 *
 * <p>Solo enlaza parámetros, llama a los servicios de {@code service/rm} y elige la vista o el
 * {@code redirect:}. Los formularios siguen el patrón PRG: las {@link IllegalArgumentException} e
 * {@link IllegalStateException} de los servicios se muestran como flash {@code mensajeError} y el éxito
 * como {@code mensajeExito}. Las rutas de vuelta se arman con listas cerradas de orígenes y con datos del
 * propio registro, nunca con una URL enviada por el navegador.</p>
 */
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
    private final RmEducacionService rmEducacionService;
    private final RmForoConsultaService rmForoConsultaService;
    private final RmReporteService rmReporteService;
    private final RmReporteExportService rmReporteExportService;
    private final RmCursoService rmCursoService;
    private final RmPresupuestoService rmPresupuestoService;
    private final EvaluacionService evaluacionService;
    private final RmResponsableService rmResponsableService;
    private final RmTalentMatchingService rmTalentMatchingService;
    private final RmAsistenteService rmAsistenteService;
    private final RmAsistenteContextoService rmAsistenteContextoService;

    /**
     * Crea el controlador sin Talent Matching ni asistente IA (quedan en {@code null}). Lo usan las pruebas
     * que no necesitan esas pantallas.
     *
     * @param rmPerfilService perfil del RM
     * @param rmColaboradorConsultaService consultas de colaboradores
     * @param rmProyectoConsultaService consultas de proyectos
     * @param rmProyectoRevisionService revisión, fechas, presupuesto y cierre de proyectos
     * @param rmAsignacionService bandeja y decisiones de asignaciones
     * @param rmSolicitudPersonalService solicitudes de personal del PM
     * @param rmCertificadoService validación de certificados
     * @param rmEducacionService validación de formación académica
     * @param rmForoConsultaService lectura de foros
     * @param rmReporteService reportes
     * @param rmReporteExportService exportación de reportes a Excel y PDF
     * @param rmCursoService cursos y sus solicitudes
     * @param rmPresupuestoService impacto y costo de asignaciones
     * @param rmResponsableService RM responsable de cada proyecto
     * @param evaluacionService evaluaciones de los colaboradores
     */
    public RmViewController(
            RmPerfilService rmPerfilService,
            RmColaboradorConsultaService rmColaboradorConsultaService,
            RmProyectoConsultaService rmProyectoConsultaService,
            RmProyectoRevisionService rmProyectoRevisionService,
            RmAsignacionService rmAsignacionService,
            RmSolicitudPersonalService rmSolicitudPersonalService,
            RmCertificadoService rmCertificadoService,
            RmEducacionService rmEducacionService,
            RmForoConsultaService rmForoConsultaService,
            RmReporteService rmReporteService,
            RmReporteExportService rmReporteExportService,
            RmCursoService rmCursoService,
            RmPresupuestoService rmPresupuestoService,
            RmResponsableService rmResponsableService,
            EvaluacionService evaluacionService) {
        this(rmPerfilService, rmColaboradorConsultaService, rmProyectoConsultaService,
                rmProyectoRevisionService, rmAsignacionService, rmSolicitudPersonalService,
                rmCertificadoService, rmEducacionService, rmForoConsultaService,
                rmReporteService, rmReporteExportService, rmCursoService,
                rmPresupuestoService, rmResponsableService, evaluacionService,
                null, null, null);
    }

    /**
     * Crea el controlador con todas sus dependencias; es el constructor que usa Spring.
     *
     * @param rmPerfilService perfil del RM
     * @param rmColaboradorConsultaService consultas de colaboradores
     * @param rmProyectoConsultaService consultas de proyectos
     * @param rmProyectoRevisionService revisión, fechas, presupuesto y cierre de proyectos
     * @param rmAsignacionService bandeja y decisiones de asignaciones
     * @param rmSolicitudPersonalService solicitudes de personal del PM
     * @param rmCertificadoService validación de certificados
     * @param rmEducacionService validación de formación académica
     * @param rmForoConsultaService lectura de foros
     * @param rmReporteService reportes
     * @param rmReporteExportService exportación de reportes a Excel y PDF
     * @param rmCursoService cursos y sus solicitudes
     * @param rmPresupuestoService impacto y costo de asignaciones
     * @param rmResponsableService RM responsable de cada proyecto
     * @param evaluacionService evaluaciones de los colaboradores
     * @param rmTalentMatchingService recomendaciones de Talent Matching
     * @param rmAsistenteService conversación con el asistente IA del RM
     * @param rmAsistenteContextoService indicadores y consultas rápidas del asistente
     */
    @org.springframework.beans.factory.annotation.Autowired
    public RmViewController(
            RmPerfilService rmPerfilService,
            RmColaboradorConsultaService rmColaboradorConsultaService,
            RmProyectoConsultaService rmProyectoConsultaService,
            RmProyectoRevisionService rmProyectoRevisionService,
            RmAsignacionService rmAsignacionService,
            RmSolicitudPersonalService rmSolicitudPersonalService,
            RmCertificadoService rmCertificadoService,
            RmEducacionService rmEducacionService,
            RmForoConsultaService rmForoConsultaService,
            RmReporteService rmReporteService,
            RmReporteExportService rmReporteExportService,
            RmCursoService rmCursoService,
            RmPresupuestoService rmPresupuestoService,
            RmResponsableService rmResponsableService,
            EvaluacionService evaluacionService,
            RmTalentMatchingService rmTalentMatchingService,
            RmAsistenteService rmAsistenteService,
            RmAsistenteContextoService rmAsistenteContextoService) {
        this.rmPerfilService = rmPerfilService;
        this.rmColaboradorConsultaService = rmColaboradorConsultaService;
        this.rmProyectoConsultaService = rmProyectoConsultaService;
        this.rmProyectoRevisionService = rmProyectoRevisionService;
        this.rmAsignacionService = rmAsignacionService;
        this.rmSolicitudPersonalService = rmSolicitudPersonalService;
        this.rmCertificadoService = rmCertificadoService;
        this.rmEducacionService = rmEducacionService;
        this.rmForoConsultaService = rmForoConsultaService;
        this.rmReporteService = rmReporteService;
        this.rmReporteExportService = rmReporteExportService;
        this.rmCursoService = rmCursoService;
        this.rmPresupuestoService = rmPresupuestoService;
        this.evaluacionService = evaluacionService;
        this.rmResponsableService = rmResponsableService;
        this.rmTalentMatchingService = rmTalentMatchingService;
        this.rmAsistenteService = rmAsistenteService;
        this.rmAsistenteContextoService = rmAsistenteContextoService;
    }

    // Disponible en el modelo de todas las páginas de este controlador (topbar).
    // Se lee de la BD para que la foto y el nombre no dependan de la copia guardada al iniciar sesión.
    /**
     * RM de la barra superior de todas las páginas de este controlador.
     *
     * @param principal usuario con la sesión iniciada
     * @return el RM leído de la BD, o {@code null} si no hay sesión
     */
    @org.springframework.web.bind.annotation.ModelAttribute("rm")
    public Usuario rm(@AuthenticationPrincipal UsuarioDetails principal) {
        return principal != null ? rmPerfilService.obtenerPerfil(principal.getUsuario().getId()) : null;
    }

    /**
     * Entrada del módulo.
     *
     * @return redirección al dashboard
     */
    @GetMapping({"", "/"})
    public String index() {
        return "redirect:/rm/dashboard";
    }

    /**
     * Dashboard del RM. Antes de contar, marca como vencidos los cursos sin evidencia fuera de plazo. Muestra
     * las decisiones pendientes (del RM o esperando al PM), las solicitudes de personal abiertas, los
     * pendientes de validación (certificados, formación y cursos) y los proyectos que requieren atención:
     * en revisión, con vacantes o con pendientes del RM, ordenados por un puntaje que pesa pendientes,
     * vacantes, revisión y prioridad.
     *
     * @param model modelo de la vista
     * @return la vista {@code rm/rm-dashboard}
     */
    @GetMapping({"/dashboard", "/rm-dashboard.html"})
    public String dashboard(Model model) {
        rmCursoService.marcarCursosSinEvidenciaVencidos();
        List<RmAsignacionView> asignaciones = rmAsignacionService.listarParaDashboard();
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
        long educacionPendiente = rmEducacionService.contarPendientes();
        long cursosPendientes = rmCursoService.contarSolicitudesPendientes();

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
        model.addAttribute("totalEducacionPendiente", educacionPendiente);
        model.addAttribute("totalCursosPendientes", cursosPendientes);
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

    /**
     * Lista paginada de proyectos con filtros normalizados por el servicio y los contadores del encabezado.
     *
     * @param noEncontrado {@code true} si se llega desde un detalle cuyo proyecto no existe (muestra un aviso)
     * @param busqueda texto buscado
     * @param estado filtro de estado del proyecto
     * @param prioridad filtro de prioridad
     * @param vacantes filtro por proyectos con o sin vacantes
     * @param pagina número de página; vacío o inválido abre la primera
     * @param principal RM con la sesión iniciada; marca los proyectos de los que es responsable
     * @param model modelo de la vista
     * @return la vista {@code rm/rm-proyectos}
     */
    @GetMapping({"/proyectos", "/rm-proyectos.html"})
    public String projects(
            @RequestParam(name = "noEncontrado", required = false) Boolean noEncontrado,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String prioridad,
            @RequestParam(required = false) String vacantes,
            @RequestParam(required = false) String pagina,
            @AuthenticationPrincipal UsuarioDetails principal,
            Model model) {
        var filtros = rmProyectoConsultaService.normalizarFiltros(busqueda, estado, prioridad, vacantes);
        Long rmId = principal == null ? null : principal.getUsuario().getId();
        var paginaProyectos = rmProyectoConsultaService.listarPagina(filtros, pagina, rmId);

        var contadores = paginaProyectos.contadores();
        model.addAttribute("proyectos", paginaProyectos.filas());
        model.addAttribute("paginaActual", paginaProyectos.paginaActual());
        model.addAttribute("totalPaginas", paginaProyectos.totalPaginas());
        model.addAttribute("totalRegistros", paginaProyectos.totalRegistros());
        model.addAttribute("tamanioPagina", RmProyectoConsultaService.TAMANIO_PAGINA);
        model.addAttribute("busqueda", filtros.busqueda());
        model.addAttribute("estado", filtros.estado());
        model.addAttribute("prioridad", filtros.prioridad());
        model.addAttribute("vacantes", filtros.vacantes());
        model.addAttribute("totalProyectos", contadores.totalProyectos());
        model.addAttribute("totalConVacantes", contadores.conVacantes());
        model.addAttribute("totalPendientesRm", contadores.pendientesRm());
        model.addAttribute("totalEnRevision", contadores.enRevision());
        model.addAttribute("proyectoNoEncontrado", Boolean.TRUE.equals(noEncontrado));
        model.addAttribute("diasUrgencia", rmResponsableService.diasUrgenciaInicio());

        return "rm/rm-proyectos";
    }

    /**
     * Detalle de un proyecto: resumen de presupuesto, RM responsable y si el usuario lo es, si el proyecto es
     * gestionable, motivo de urgencia, foro asociado, si acepta asignaciones y, para la zona de riesgo, si se
     * puede cancelar o finalizar (solo en {@code ACTIVO} o {@code EN_ESPERA}) y cuántas actividades siguen abiertas.
     *
     * @param proyectoId proyecto a mostrar; sin él vuelve a la lista
     * @param principal RM con la sesión iniciada
     * @param model modelo de la vista
     * @return la vista {@code rm/rm-detalle-proyecto}, o la lista con {@code noEncontrado=true} si no existe
     */
    @GetMapping({"/proyectos/detalle", "/rm-detalle-proyecto.html"})
    public String projectDetail(
            @RequestParam(name = "id", required = false) Long proyectoId,
            @AuthenticationPrincipal UsuarioDetails principal,
            Model model) {
        if (proyectoId == null) return "redirect:/rm/proyectos";
        try {
            RmProyectoView proyecto = rmProyectoConsultaService.obtener(proyectoId);
            model.addAttribute("proyecto", proyecto);
            model.addAttribute("resumen", proyecto.getResumenPresupuesto());

            // Responsable de revisión y urgencia
            com.pucp.skillb_ia.model.Proyecto entidad = proyecto.getProyecto();
            boolean esResponsable = principal != null && entidad.getRmRevisor() != null
                    && entidad.getRmRevisor().getId().equals(principal.getUsuario().getId());
            model.addAttribute("responsableNombre", entidad.getRmRevisor() == null ? null
                    : entidad.getRmRevisor().getNombre() + " " + entidad.getRmRevisor().getApellido());
            model.addAttribute("esResponsable", esResponsable);
            model.addAttribute("proyectoGestionable", rmResponsableService.esGestionable(entidad));
            model.addAttribute("motivoUrgencia", rmResponsableService.motivoUrgencia(entidad));
            model.addAttribute("foroId", rmForoConsultaService
                    .buscarIdPorProyecto(proyecto.getProyecto()).orElse(null));
            model.addAttribute("proyectoAsignable",
                    rmAsignacionService.esProyectoAsignable(proyecto.getProyecto()));
            // Zona de riesgo: cancelar o finalizar solo en ACTIVO o EN_ESPERA.
            boolean cerrable = RmProyectoRevisionService.esProyectoCerrable(proyecto.getProyecto());
            model.addAttribute("proyectoCerrable", cerrable);
            model.addAttribute("actividadesAbiertas",
                    cerrable ? rmProyectoRevisionService.contarActividadesAbiertas(proyecto.getProyecto()) : 0L);
        } catch (IllegalArgumentException ex) {
            return "redirect:/rm/proyectos?noEncontrado=true";
        }
        return "rm/rm-detalle-proyecto";
    }

    /**
     * Pantalla de revisión de un proyecto enviado por el PM. Solo se abre si el proyecto está {@code EN_REVISION};
     * en otro estado redirige a su detalle.
     *
     * @param proyectoId proyecto a revisar; sin él vuelve a la lista
     * @param model modelo de la vista
     * @return la vista {@code rm/rm-revision-proyecto}, el detalle o la lista con {@code noEncontrado=true}
     */
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

    /**
     * Guarda el presupuesto de un proyecto. Las reglas (monto válido, estado y permisos) las aplica
     * {@link RmProyectoRevisionService#asignarPresupuesto}. Si se envió desde el detalle de una asignación de
     * ese mismo proyecto, vuelve a ella; si no, a la revisión (proyecto {@code EN_REVISION}) o al detalle.
     *
     * @param proyectoId proyecto
     * @param presupuestoTexto monto escrito; vacío se pasa como {@code null} al servicio
     * @param asignacionId asignación desde la que se abrió el formulario (opcional)
     * @param origen origen de navegación de esa asignación, para conservarlo al volver
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual; se usa para volver a guardar el contexto de seguridad en la sesión
     * @return redirección a la asignación, a la revisión o al detalle del proyecto
     */
    @PostMapping("/proyectos/{id}/presupuesto")
    public String assignProjectBudget(
            @PathVariable("id") Long proyectoId,
            @RequestParam(name = "presupuesto", required = false) String presupuestoTexto,
            @RequestParam(name = "asignacionId", required = false) Long asignacionId,
            @RequestParam(name = "origen", required = false) String origen,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            BigDecimal presupuesto = convertirDecimal(
                    presupuestoTexto, "El presupuesto debe ser un monto numérico válido.");
            EstadoProyecto estado = rmProyectoRevisionService.asignarPresupuesto(
                    proyectoId, presupuesto, principal.getUsuario().getId());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "Presupuesto guardado correctamente.");
            String redireccionAsignacion = redireccionAsignacionDelProyecto(proyectoId, asignacionId, origen);
            if (redireccionAsignacion != null) return redireccionAsignacion;
            return redireccionPresupuestoProyecto(proyectoId, estado);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
            String redireccionAsignacion = redireccionAsignacionDelProyecto(proyectoId, asignacionId, origen);
            if (redireccionAsignacion != null) return redireccionAsignacion;
            return redireccionPresupuestoProyecto(proyectoId, null);
        }
    }

    // TASK-021: el formulario está en el detalle y en la revisión; éxito y errores vuelven a la vista de origen
    // (si el proyecto ya no está en revisión, GET /proyectos/revision redirige al detalle).
    /**
     * Cambia las fechas de inicio y fin de un proyecto; las reglas las aplica
     * {@link RmProyectoRevisionService#cambiarFechas}. Una fecha que no tenga el formato AAAA-MM-DD se rechaza
     * con un mensaje de error.
     *
     * @param proyectoId proyecto
     * @param fechaInicioTexto fecha de inicio (AAAA-MM-DD)
     * @param fechaFinTexto fecha de fin (AAAA-MM-DD)
     * @param origen {@code revision} para volver a la revisión; otro valor vuelve al detalle
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual; se usa para volver a guardar el contexto de seguridad en la sesión
     * @return redirección a la revisión o al detalle del proyecto
     */
    @PostMapping("/proyectos/{id}/fechas")
    public String changeProjectDates(
            @PathVariable("id") Long proyectoId,
            @RequestParam(name = "fechaInicio", required = false) String fechaInicioTexto,
            @RequestParam(name = "fechaFin", required = false) String fechaFinTexto,
            @RequestParam(name = "origen", required = false) String origen,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes, HttpServletRequest request) {
        String destino = "revision".equals(origen)
                ? "redirect:/rm/proyectos/revision?id=" + proyectoId
                : "redirect:/rm/proyectos/detalle?id=" + proyectoId;
        if (principal == null) return "redirect:/login";
        try {
            rmProyectoRevisionService.cambiarFechas(proyectoId,
                    convertirFecha(fechaInicioTexto), convertirFecha(fechaFinTexto), principal.getUsuario().getId());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "Fechas del proyecto actualizadas correctamente.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
        }
        return destino;
    }

    // Vacío => null (el servicio exige ambas fechas); mal formado => error de negocio.
    private static LocalDate convertirFecha(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Las fechas deben tener el formato AAAA-MM-DD.");
        }
    }

    private String redireccionAsignacionDelProyecto(Long proyectoId, Long asignacionId, String origen) {
        if (asignacionId == null) return null;
        try {
            RmAsignacionView asignacion = rmAsignacionService.obtener(asignacionId);
            if (!asignacion.getAsignacion().getProyecto().getId().equals(proyectoId)) return null;
            return redirectDetalleAsignacion(asignacion, origen);
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

    /**
     * Aprueba un proyecto en revisión y lo activa ({@link RmProyectoRevisionService#aprobar}). Un error de datos
     * ({@link IllegalArgumentException}) vuelve a la revisión; un error de estado
     * ({@link IllegalStateException}, por ejemplo si ya no está en revisión) vuelve al detalle.
     *
     * @param proyectoId proyecto
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual; se usa para volver a guardar el contexto de seguridad en la sesión
     * @return redirección al detalle o a la revisión del proyecto
     */
    @PostMapping("/proyectos/{id}/aprobar")
    public String approveProject(
            @PathVariable("id") Long proyectoId,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            rmProyectoRevisionService.aprobar(proyectoId, principal.getUsuario().getId());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
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

    /**
     * Rechaza un proyecto en revisión con un motivo ({@link RmProyectoRevisionService#rechazar}). Un error de
     * datos (por ejemplo, motivo vacío) vuelve a la revisión; un error de estado vuelve al detalle.
     *
     * @param proyectoId proyecto
     * @param motivo motivo del rechazo, que se comunica al PM
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual; se usa para volver a guardar el contexto de seguridad en la sesión
     * @return redirección al detalle o a la revisión del proyecto
     */
    @PostMapping("/proyectos/{id}/rechazar")
    public String rejectProject(
            @PathVariable("id") Long proyectoId,
            @RequestParam("motivo") String motivo,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            rmProyectoRevisionService.rechazar(
                    proyectoId, motivo, principal.getUsuario().getId());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
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

    /**
     * Convierte al RM en el responsable del proyecto ({@link RmResponsableService#tomar}).
     *
     * @param proyectoId proyecto
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @return redirección al detalle del proyecto
     */
    @PostMapping("/proyectos/{id}/tomar")
    public String takeProject(
            @PathVariable("id") Long proyectoId,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            rmResponsableService.tomar(proyectoId, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeExito", "Ahora eres el Resource Manager responsable de este proyecto.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
        }
        return "redirect:/rm/proyectos/detalle?id=" + proyectoId;
    }

    /**
     * Libera un proyecto del que el RM es responsable; vuelve a la bolsa de proyectos sin asignar
     * ({@link RmResponsableService#liberar}).
     *
     * @param proyectoId proyecto
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @return redirección al detalle del proyecto
     */
    @PostMapping("/proyectos/{id}/liberar")
    public String releaseProject(
            @PathVariable("id") Long proyectoId,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            rmResponsableService.liberar(proyectoId, principal.getUsuario());
            redirectAttributes.addFlashAttribute("mensajeExito", "Liberaste el proyecto. Vuelve a la bolsa de proyectos sin asignar.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
        }
        return "redirect:/rm/proyectos/detalle?id=" + proyectoId;
    }


    // Zona de riesgo del detalle: éxito y error vuelven al detalle del proyecto.
    /**
     * Cancela un proyecto {@code ACTIVO} o {@code EN_ESPERA} y cierra sus asignaciones
     * ({@link RmProyectoRevisionService#cancelar}). El servicio exige el motivo y que se escriba el nombre del
     * proyecto como confirmación. El mensaje de éxito resume las asignaciones cerradas.
     *
     * @param proyectoId proyecto
     * @param motivo motivo de la cancelación
     * @param confirmacionNombre nombre del proyecto escrito por el RM para confirmar
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual; se usa para volver a guardar el contexto de seguridad en la sesión
     * @return redirección al detalle del proyecto
     */
    @PostMapping("/proyectos/{id}/cancelar")
    public String cancelProject(
            @PathVariable("id") Long proyectoId,
            @RequestParam(name = "motivo", required = false) String motivo,
            @RequestParam(name = "confirmacionNombre", required = false) String confirmacionNombre,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            var resultado = rmProyectoRevisionService.cancelar(
                    proyectoId, motivo, confirmacionNombre, principal.getUsuario().getId());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito",
                    CierreAsignacionesService.mensaje("cancelado", resultado));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
        }
        return "redirect:/rm/proyectos/detalle?id=" + proyectoId;
    }

    /**
     * Finaliza un proyecto {@code ACTIVO} o {@code EN_ESPERA} y cierra sus asignaciones
     * ({@link RmProyectoRevisionService#finalizar}), con las mismas exigencias que la cancelación.
     *
     * @param proyectoId proyecto
     * @param motivo motivo de la finalización
     * @param confirmacionNombre nombre del proyecto escrito por el RM para confirmar
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual; se usa para volver a guardar el contexto de seguridad en la sesión
     * @return redirección al detalle del proyecto
     */
    @PostMapping("/proyectos/{id}/finalizar")
    public String finishProject(
            @PathVariable("id") Long proyectoId,
            @RequestParam(name = "motivo", required = false) String motivo,
            @RequestParam(name = "confirmacionNombre", required = false) String confirmacionNombre,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            var resultado = rmProyectoRevisionService.finalizar(
                    proyectoId, motivo, confirmacionNombre, principal.getUsuario().getId());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito",
                    CierreAsignacionesService.mensaje("finalizado", resultado));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
        }
        return "redirect:/rm/proyectos/detalle?id=" + proyectoId;
    }

    /**
     * Proyecto visto desde una solicitud de personal del PM.
     *
     * @param solicitudId solicitud de personal; sin ella vuelve a la bandeja de solicitudes
     * @param model modelo de la vista: la solicitud y su proyecto
     * @return la vista {@code rm/rm-detalle-proyecto-solicitudes}, o la bandeja con {@code noEncontrada=true}
     */
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

    /**
     * Búsqueda paginada de candidatos para un proyecto. Solo se permite en proyectos asignables (activos o en
     * espera); en otro estado vuelve al detalle con un mensaje. Marca a los colaboradores que ya tienen una
     * asignación vigente en el proyecto e incluye los cupos ocupados.
     *
     * @param proyectoIdTexto id del proyecto; vacío vuelve a la lista y no numérico la abre con {@code noEncontrado=true}
     * @param busqueda texto buscado
     * @param disponibilidad filtro de disponibilidad
     * @param carga filtro de carga de trabajo
     * @param pagina número de página; vacío o inválido abre la primera
     * @param model modelo de la vista
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual (no se usa)
     * @return la vista {@code rm/rm-buscar-colaboradores-proyecto} o una redirección
     */
    @GetMapping({"/proyectos/buscar-colaboradores", "/rm-buscar-colaboradores-proyecto.html"})
    public String searchProjectCollaborators(
            @RequestParam(name = "proyectoId", required = false) String proyectoIdTexto,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) String disponibilidad,
            @RequestParam(required = false) String carga,
            @RequestParam(required = false) String pagina,
            Model model,
            RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (proyectoIdTexto == null || proyectoIdTexto.isBlank()) return "redirect:/rm/proyectos";
        Long proyectoId;
        try {
            proyectoId = Long.valueOf(proyectoIdTexto.trim());
        } catch (NumberFormatException ex) {
            return "redirect:/rm/proyectos?noEncontrado=true";
        }
        try {
            RmProyectoView proyecto = rmProyectoConsultaService.obtener(proyectoId);
            if (!rmAsignacionService.esProyectoAsignable(proyecto.getProyecto())) {
                redirectAttributes.addFlashAttribute("mensajeError",
                        "Solo se pueden buscar colaboradores para proyectos activos o en espera. Este proyecto está "
                                + proyecto.getEstadoTexto().toLowerCase(Locale.ROOT) + ".");
                return "redirect:/rm/proyectos/detalle?id=" + proyectoId;
            }
            var filtros = rmColaboradorConsultaService.normalizarFiltrosCandidatos(busqueda, disponibilidad, carga);
            var paginaCandidatos = rmColaboradorConsultaService.listarPaginaCandidatos(
                    proyecto.getProyecto(), filtros, pagina);
            model.addAttribute("proyecto", proyecto);
            model.addAttribute("candidatos", paginaCandidatos.filas());
            model.addAttribute("paginaActual", paginaCandidatos.paginaActual());
            model.addAttribute("totalPaginas", paginaCandidatos.totalPaginas());
            model.addAttribute("totalRegistros", paginaCandidatos.totalRegistros());
            model.addAttribute("tamanioPagina", RmColaboradorConsultaService.TAMANIO_PAGINA);
            model.addAttribute("busqueda", filtros.busqueda());
            model.addAttribute("disponibilidad", filtros.disponibilidad());
            model.addAttribute("carga", filtros.carga());
            model.addAttribute("colaboradoresConAsignacion",
                    rmAsignacionService.colaboradoresConAsignacionVigente(proyecto.getProyecto()));
            model.addAttribute("cuposOcupados", rmAsignacionService.contarCuposOcupados(proyecto.getProyecto()));
        } catch (IllegalArgumentException ex) {
            return "redirect:/rm/proyectos?noEncontrado=true";
        }
        return "rm/rm-buscar-colaboradores-proyecto";
    }

    // Contenido del modal "Ver perfil" en Buscar colaboradores (se carga por AJAX).
    /**
     * Fragmento con el perfil de un colaborador y su sueldo base. Si no existe, el fragmento muestra el error.
     *
     * @param colaboradorId colaborador
     * @param model modelo del fragmento
     * @return el fragmento {@code fragments/rm-asignacion-modales :: perfilColaborador}
     */
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

    /**
     * Propone una asignación al PM desde los modales del RM ({@link RmAsignacionService#proponerDesdeRm}). El
     * servicio valida horas, capacidad del colaborador y cupos; los motivos se piden cuando se supera la
     * capacidad o el cupo. Vuelve a la pantalla de origen: la misma búsqueda con sus filtros ({@code buscar}),
     * el perfil del colaborador (solo en error, {@code perfil}), sus asignaciones ({@code colaborador}) o Talent
     * Matching del proyecto ({@code talent}); sin origen reconocido, a la bandeja.
     *
     * @param proyectoId proyecto
     * @param colaboradorId colaborador propuesto
     * @param horasSemanalesTexto horas semanales; un valor no numérico se rechaza
     * @param justificacion justificación de la propuesta
     * @param motivoCapacidad motivo para superar la capacidad semanal del colaborador
     * @param justificacionCupo justificación para superar los cupos del proyecto
     * @param origen pantalla desde la que se abrió el modal
     * @param busqueda filtro de la búsqueda de candidatos (origen {@code buscar})
     * @param disponibilidad filtro de disponibilidad de la búsqueda (origen {@code buscar})
     * @param carga filtro de carga de la búsqueda (origen {@code buscar})
     * @param pagina página de la búsqueda (origen {@code buscar})
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual (no se usa)
     * @return redirección a la pantalla de origen
     */
    @PostMapping("/asignaciones/proponer")
    public String createAssignmentProposal(
            @RequestParam("proyectoId") Long proyectoId,
            @RequestParam("colaboradorId") Long colaboradorId,
            @RequestParam(name = "horasSemanales", required = false) String horasSemanalesTexto,
            @RequestParam(name = "justificacion", required = false) String justificacion,
            @RequestParam(name = "motivoCapacidad", required = false) String motivoCapacidad,
            @RequestParam(name = "justificacionCupo", required = false) String justificacionCupo,
            @RequestParam(name = "origen", required = false) String origen,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) String disponibilidad,
            @RequestParam(required = false) String carga,
            @RequestParam(required = false) String pagina,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        // Orígenes cerrados del modal (TASK-034): buscar, perfil, colaborador (sus asignaciones)
        // y talent (Talent Matching y su asistente: vuelve al proyecto de la propuesta).
        // Con "buscar", éxito y error vuelven a la misma búsqueda (filtros y página).
        try {
            BigDecimal horasSemanales = convertirDecimal(
                    horasSemanalesTexto, "Las horas semanales deben ser un número válido.");
            rmAsignacionService.proponerDesdeRm(
                    proyectoId, colaboradorId, horasSemanales,
                    justificacion, motivoCapacidad, justificacionCupo, principal.getUsuario().getId());
            redirectAttributes.addFlashAttribute(
                    "mensajeExito", "Propuesta enviada al Project Manager correctamente.");
            if ("buscar".equals(origen)) {
                return "redirect:" + urlBusquedaCandidatos(proyectoId, busqueda, disponibilidad, carga, pagina);
            }
            if ("colaborador".equals(origen)) {
                return "redirect:/rm/colaboradores/asignaciones?id=" + colaboradorId;
            }
            if ("talent".equals(origen)) {
                return "redirect:/rm/talent-matching?proyectoId=" + proyectoId;
            }
            return "redirect:/rm/asignaciones";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
            // Si la propuesta vino de un modal, se vuelve a la pantalla de origen.
            if ("buscar".equals(origen)) {
                return "redirect:" + urlBusquedaCandidatos(proyectoId, busqueda, disponibilidad, carga, pagina);
            }
            if ("perfil".equals(origen)) {
                return "redirect:/rm/colaboradores/perfil?id=" + colaboradorId;
            }
            if ("colaborador".equals(origen)) {
                return "redirect:/rm/colaboradores/asignaciones?id=" + colaboradorId;
            }
            if ("talent".equals(origen)) {
                return "redirect:/rm/talent-matching?proyectoId=" + proyectoId;
            }
            // Sin origen reconocido: la bandeja muestra el error (ya no hay página completa de propuesta).
            return "redirect:/rm/asignaciones";
        }
    }

    // Vuelta a la búsqueda de candidatos: solo parámetros conocidos, normalizados y codificados
    // (nunca una URL enviada por el navegador). Los valores por defecto no se agregan.
    private String urlBusquedaCandidatos(
            Long proyectoId, String busqueda, String disponibilidad, String carga, String pagina) {
        var filtros = rmColaboradorConsultaService.normalizarFiltrosCandidatos(busqueda, disponibilidad, carga);
        java.util.Map<String, Object> valores = new java.util.LinkedHashMap<>();
        valores.put("proyectoId", proyectoId);
        if (filtros.busqueda() != null) valores.put("busqueda", filtros.busqueda());
        if (!"0".equals(filtros.disponibilidad())) valores.put("disponibilidad", filtros.disponibilidad());
        if (filtros.carga() != null) valores.put("carga", filtros.carga());
        try {
            int numeroPagina = Integer.parseInt(pagina == null ? "" : pagina.trim());
            if (numeroPagina > 1) valores.put("pagina", numeroPagina);
        } catch (NumberFormatException ignorado) {
            // Página vacía o inválida: la búsqueda abre la primera.
        }
        // Cada valor va como variable de URI: se codifica completo (incluidos "+", "&" y tildes).
        UriComponentsBuilder url = UriComponentsBuilder.fromPath("/rm/proyectos/buscar-colaboradores");
        valores.keySet().forEach(nombre -> url.queryParam(nombre, "{" + nombre + "}"));
        return url.encode().buildAndExpand(valores).toUriString();
    }

    // Proyectos del modal "Selecciona un proyecto" (perfil, asignaciones del colaborador y bandeja)
    // con sus cupos ocupados; si se indica un proyecto asignable, el modal lista solo ese.
    private void agregarProyectosAsignables(Model model, Long proyectoContextoId) {
        List<RmProyectoView> proyectos = rmProyectoConsultaService.listar().stream()
                .filter(p -> rmAsignacionService.esProyectoAsignable(p.getProyecto()))
                .toList();
        if (proyectoContextoId != null && proyectos.stream()
                .anyMatch(p -> p.getProyecto().getId().equals(proyectoContextoId))) {
            proyectos = proyectos.stream()
                    .filter(p -> p.getProyecto().getId().equals(proyectoContextoId))
                    .toList();
        }
        model.addAttribute("proyectosAsignables", proyectos);
        model.addAttribute("cuposOcupados", rmAsignacionService.cuposOcupadosPorProyecto(
                proyectos.stream().map(RmProyectoView::getProyecto).toList()));
    }

    /**
     * Directorio paginado de colaboradores con filtros y contadores (total, disponibles, sin asignaciones y
     * con carga máxima).
     *
     * @param noEncontrado {@code true} si se llega desde un perfil inexistente (muestra un aviso)
     * @param busqueda texto buscado
     * @param disponibilidad filtro de disponibilidad
     * @param carga filtro de carga de trabajo
     * @param nivel filtro de nivel de experiencia
     * @param pagina número de página; vacío o inválido abre la primera
     * @param model modelo de la vista
     * @return la vista {@code rm/rm-colaboradores}
     */
    @GetMapping({"/colaboradores", "/rm-colaboradores.html"})
    public String collaborators(
            @RequestParam(name = "noEncontrado", required = false) Boolean noEncontrado,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) String disponibilidad,
            @RequestParam(required = false) String carga,
            @RequestParam(required = false) String nivel,
            @RequestParam(required = false) String pagina,
            Model model) {
        var filtros = rmColaboradorConsultaService.normalizarFiltrosDirectorio(busqueda, disponibilidad, carga, nivel);
        var paginaColaboradores = rmColaboradorConsultaService.listarPaginaDirectorio(filtros, pagina);
        var contadores = paginaColaboradores.contadores();

        model.addAttribute("colaboradores", paginaColaboradores.filas());
        model.addAttribute("paginaActual", paginaColaboradores.paginaActual());
        model.addAttribute("totalPaginas", paginaColaboradores.totalPaginas());
        model.addAttribute("totalRegistros", paginaColaboradores.totalRegistros());
        model.addAttribute("tamanioPagina", RmColaboradorConsultaService.TAMANIO_PAGINA);
        model.addAttribute("busqueda", filtros.busqueda());
        model.addAttribute("disponibilidad", filtros.disponibilidad());
        model.addAttribute("carga", filtros.carga());
        model.addAttribute("nivel", filtros.nivel());
        model.addAttribute("totalColaboradores", contadores.total());
        model.addAttribute("totalDisponibles", contadores.disponibles());
        model.addAttribute("totalSinAsignaciones", contadores.sinAsignaciones());
        model.addAttribute("totalCargaMaxima", contadores.cargaMaxima());
        model.addAttribute("colaboradorNoEncontrado", Boolean.TRUE.equals(noEncontrado));

        return "rm/rm-colaboradores";
    }

    /**
     * Perfil de un colaborador con sus evaluaciones y los datos de los modales «Proponer asignación»
     * (proyectos asignables, proyectos donde ya tiene asignación vigente y sueldo base) y «Asignar curso».
     *
     * @param colaboradorId colaborador; sin él vuelve al directorio
     * @param model modelo de la vista
     * @return la vista {@code rm/rm-perfil-colaborador}, o el directorio con {@code noEncontrado=true}
     */
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
            agregarProyectosAsignables(model, null);
            model.addAttribute("proyectosConAsignacion",
                    rmAsignacionService.proyectosConAsignacionVigente(colaboradorId));
            model.addAttribute("sueldoColaborador",
                    rmColaboradorConsultaService.mapaSueldosBase().get(colaboradorId));
            model.addAttribute("evaluaciones",
                    evaluacionService.obtenerEvaluacionesPorColaborador(colaboradorId));
            agregarModalAsignarCurso(model);
        } catch (IllegalArgumentException ex) {
            return "redirect:/rm/colaboradores?noEncontrado=true";
        }

        return "rm/rm-perfil-colaborador";
    }

    /**
     * Historial paginado de asignaciones de un colaborador, filtrado en el servidor. Las métricas del
     * encabezado no dependen de los filtros. Incluye los datos del modal «Proponer asignación».
     *
     * @param colaboradorId colaborador; sin él vuelve al directorio
     * @param busqueda texto buscado
     * @param estado filtro de estado de la asignación
     * @param anio filtro de año
     * @param pagina número de página; vacío o inválido abre la primera
     * @param model modelo de la vista
     * @return la vista {@code rm/rm-asignaciones-colaborador}, o el directorio con {@code noEncontrado=true}
     */
    @GetMapping({"/colaboradores/asignaciones", "/rm-asignaciones-colaborador.html"})
    public String collaboratorAssignments(
            @RequestParam(name = "id", required = false) Long colaboradorId,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String anio,
            @RequestParam(required = false) String pagina,
            Model model) {
        if (colaboradorId == null) return "redirect:/rm/colaboradores";
        try {
            model.addAttribute("colaborador", rmColaboradorConsultaService.obtenerDetalle(colaboradorId));
            // Filtros y paginación en el servidor (TASK-051); las métricas del encabezado
            // salen de "colaborador" y no dependen de los filtros ni de la página.
            var paginaAsignaciones = rmAsignacionService.listarPaginaPorColaborador(
                    colaboradorId, busqueda, estado, anio, pagina);
            var filtros = paginaAsignaciones.filtros();
            model.addAttribute("asignaciones", paginaAsignaciones.filas());
            model.addAttribute("paginaActual", paginaAsignaciones.paginaActual());
            model.addAttribute("totalPaginas", paginaAsignaciones.totalPaginas());
            model.addAttribute("totalRegistros", paginaAsignaciones.totalRegistros());
            model.addAttribute("totalHistorial", paginaAsignaciones.totalHistorial());
            model.addAttribute("tamanioPagina", RmAsignacionService.TAMANIO_PAGINA);
            model.addAttribute("aniosDisponibles", paginaAsignaciones.aniosDisponibles());
            model.addAttribute("busqueda", filtros.busqueda());
            model.addAttribute("estado", filtros.estado() == null ? null : filtros.estado().name());
            model.addAttribute("anio", filtros.anio());
            // Modal "Proponer asignación" (TASK-034): el colaborador ya es conocido.
            agregarProyectosAsignables(model, null);
            model.addAttribute("proyectosConAsignacion",
                    rmAsignacionService.proyectosConAsignacionVigente(colaboradorId));
            model.addAttribute("sueldoColaborador",
                    rmColaboradorConsultaService.mapaSueldosBase().get(colaboradorId));
        } catch (IllegalArgumentException ex) {
            return "redirect:/rm/colaboradores?noEncontrado=true";
        }
        return "rm/rm-asignaciones-colaborador";
    }

    /**
     * Bandeja paginada de certificados pendientes de validación, con el pendiente más antiguo y cuántos
     * superan {@link RmCertificadoService#DIAS_PENDIENTE_ATRASADO} días.
     *
     * @param busqueda texto buscado
     * @param habilidad filtro de habilidad; solo se acepta una de las habilidades presentes
     * @param pagina número de página; vacío o inválido abre la primera
     * @param model modelo de la vista
     * @return la vista {@code rm/rm-certificados-pendientes}
     */
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
        model.addAttribute("diasPendienteMasAntiguo", rmCertificadoService.diasPendienteMasAntiguo(certificados));
        model.addAttribute("pendientesAtrasados", rmCertificadoService.contarPendientesAtrasados(certificados));
        model.addAttribute("diasAtrasado", RmCertificadoService.DIAS_PENDIENTE_ATRASADO);
        return "rm/rm-certificados-pendientes";
    }

    /**
     * Pantalla de revisión de un certificado.
     *
     * @param certificadoId certificado; sin él o si no existe vuelve a la bandeja
     * @param origen {@code perfil} si se abrió desde el perfil del colaborador (al decidir se vuelve a él)
     * @param model modelo de la vista
     * @return la vista {@code rm/rm-revision-certificado} o la bandeja de certificados
     */
    @GetMapping({"/colaboradores/certificados/revision", "/rm-revision-certificado.html"})
    public String certificateReview(
            @RequestParam(name = "id", required = false) Long certificadoId,
            @RequestParam(required = false) String origen,
            Model model) {
        if (certificadoId == null) return "redirect:/rm/colaboradores/certificados";
        try {
            model.addAttribute("certificado", rmCertificadoService.obtener(certificadoId));
            model.addAttribute("desdePerfil", ORIGEN_PERFIL.equals(origen));
        } catch (IllegalArgumentException ex) {
            return "redirect:/rm/colaboradores/certificados";
        }
        return "rm/rm-revision-certificado";
    }

    /**
     * Historial paginado de certificados de un colaborador con totales por estado (aprobados, rechazados y
     * pendientes); los totales no dependen de los filtros.
     *
     * @param colaboradorId colaborador; sin él vuelve al directorio
     * @param busqueda texto buscado
     * @param estado filtro de estado del certificado
     * @param habilidad filtro de habilidad
     * @param pagina número de página; vacío o inválido abre la primera
     * @param model modelo de la vista
     * @return la vista {@code rm/rm-historial-validaciones}, o el directorio con {@code noEncontrado=true}
     */
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

    /**
     * Aprueba un certificado y actualiza el perfil del colaborador con los niveles elegidos
     * ({@link RmCertificadoService#aprobar}). Con éxito vuelve a la bandeja o al perfil; con error, a la revisión.
     *
     * @param certificadoId certificado
     * @param nivelHabilidad nivel de dominio de la habilidad certificada
     * @param nivelGeneral nivel de experiencia general del colaborador
     * @param origen {@code perfil} para volver al perfil del colaborador
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual; se usa para volver a guardar el contexto de seguridad en la sesión
     * @return redirección a la bandeja, al perfil o a la revisión
     */
    @PostMapping("/colaboradores/certificados/{id}/aprobar")
    public String approveCertificate(
            @PathVariable("id") Long certificadoId,
            @RequestParam(name = "nivelHabilidad", required = false) NivelDominio nivelHabilidad,
            @RequestParam(name = "nivelGeneral", required = false) NivelExperiencia nivelGeneral,
            @RequestParam(required = false) String origen,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        boolean desdePerfil = ORIGEN_PERFIL.equals(origen);
        try {
            Long colaboradorId = colaboradorDelCertificado(certificadoId);
            rmCertificadoService.aprobar(certificadoId, nivelHabilidad, nivelGeneral,
                    principal.getUsuario().getId());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "Certificado aprobado y perfil actualizado.");
            return destinoTrasRevision(desdePerfil, colaboradorId, "/rm/colaboradores/certificados");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
            return revisionConOrigen("/rm/colaboradores/certificados/revision?id=" + certificadoId, desdePerfil);
        }
    }

    /**
     * Rechaza un certificado y guarda el motivo ({@link RmCertificadoService#rechazar}). Con éxito vuelve a la
     * bandeja o al perfil; con error, a la revisión.
     *
     * @param certificadoId certificado
     * @param motivo motivo del rechazo
     * @param origen {@code perfil} para volver al perfil del colaborador
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual; se usa para volver a guardar el contexto de seguridad en la sesión
     * @return redirección a la bandeja, al perfil o a la revisión
     */
    @PostMapping("/colaboradores/certificados/{id}/rechazar")
    public String rejectCertificate(
            @PathVariable("id") Long certificadoId,
            @RequestParam("motivo") String motivo,
            @RequestParam(required = false) String origen,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        boolean desdePerfil = ORIGEN_PERFIL.equals(origen);
        try {
            Long colaboradorId = colaboradorDelCertificado(certificadoId);
            rmCertificadoService.rechazar(certificadoId, motivo, principal.getUsuario().getId());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "Certificado rechazado; el motivo quedó guardado.");
            return destinoTrasRevision(desdePerfil, colaboradorId, "/rm/colaboradores/certificados");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
            return revisionConOrigen("/rm/colaboradores/certificados/revision?id=" + certificadoId, desdePerfil);
        }
    }

    // Revisión abierta desde el perfil del colaborador (TASK-053): al decidir se vuelve a ese perfil.
    private static final String ORIGEN_PERFIL = "perfil";

    private Long colaboradorDelCertificado(Long certificadoId) {
        return rmCertificadoService.obtener(certificadoId).getCertificado().getColaborador().getId();
    }

    private Long colaboradorDeLaEducacion(Long educacionId) {
        return rmEducacionService.obtener(educacionId).getEducacion().getColaborador().getId();
    }

    // El colaborador sale del registro revisado, nunca de un parámetro del formulario.
    private static String destinoTrasRevision(boolean desdePerfil, Long colaboradorId, String bandeja) {
        return "redirect:" + (desdePerfil ? "/rm/colaboradores/perfil?id=" + colaboradorId : bandeja);
    }

    private static String revisionConOrigen(String revision, boolean desdePerfil) {
        return "redirect:" + revision + (desdePerfil ? "&origen=" + ORIGEN_PERFIL : "");
    }

    /**
     * Bandeja paginada de formaciones académicas pendientes de validación.
     *
     * @param busqueda texto buscado
     * @param institucion filtro de institución; solo se acepta una de las presentes
     * @param pagina número de página; vacío o inválido abre la primera
     * @param model modelo de la vista
     * @return la vista {@code rm/rm-educacion-pendiente}
     */
    @GetMapping("/colaboradores/educacion")
    public String pendingEducation(
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) String institucion,
            @RequestParam(required = false) String pagina,
            Model model) {
        var formaciones = rmEducacionService.listarPendientes();
        var instituciones = rmEducacionService.institucionesDisponibles(formaciones);
        var filtros = rmEducacionService.normalizarFiltros(busqueda, institucion, instituciones);
        var paginaEducacion = rmEducacionService.paginarPendientes(formaciones, filtros, pagina);
        model.addAttribute("formaciones", paginaEducacion.filas());
        model.addAttribute("paginaActual", paginaEducacion.paginaActual());
        model.addAttribute("totalPaginas", paginaEducacion.totalPaginas());
        model.addAttribute("totalRegistros", paginaEducacion.totalRegistros());
        model.addAttribute("tamanioPagina", RmEducacionService.TAMANIO_PAGINA);
        model.addAttribute("instituciones", instituciones);
        model.addAttribute("busqueda", filtros.busqueda());
        model.addAttribute("institucion", filtros.institucion());
        model.addAttribute("totalPendientes", formaciones.size());
        model.addAttribute("totalColaboradores", formaciones.stream()
                .map(item -> item.getEducacion().getColaborador().getId()).distinct().count());
        return "rm/rm-educacion-pendiente";
    }

    // Historial de formaciones ya revisadas (TASK-061): filtros y paginación en el servidor.
    /**
     * Historial paginado de formaciones ya revisadas, con totales de aprobadas y rechazadas.
     *
     * @param busqueda texto buscado
     * @param institucion filtro de institución
     * @param estado filtro de estado ({@code APROBADO} o {@code RECHAZADO})
     * @param colaborador id del colaborador para filtrar; se muestra su nombre
     * @param pagina número de página; vacío o inválido abre la primera
     * @param model modelo de la vista
     * @return la vista {@code rm/rm-educacion-historial}
     */
    @GetMapping("/colaboradores/educacion/historial")
    public String educationHistory(
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) String institucion,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String colaborador,
            @RequestParam(required = false) String pagina,
            Model model) {
        var revisadas = rmEducacionService.listarRevisadas();
        var filtros = rmEducacionService.normalizarFiltrosHistorial(busqueda, institucion, estado, colaborador, revisadas);
        var paginaEducacion = rmEducacionService.paginarRevisadas(revisadas, filtros, pagina);
        model.addAttribute("formaciones", paginaEducacion.filas());
        model.addAttribute("paginaActual", paginaEducacion.paginaActual());
        model.addAttribute("totalPaginas", paginaEducacion.totalPaginas());
        model.addAttribute("totalRegistros", paginaEducacion.totalRegistros());
        model.addAttribute("tamanioPagina", RmEducacionService.TAMANIO_PAGINA);
        model.addAttribute("instituciones", rmEducacionService.institucionesDisponibles(revisadas));
        model.addAttribute("busqueda", filtros.busqueda());
        model.addAttribute("institucion", filtros.institucion());
        model.addAttribute("estado", filtros.estado() == null ? null : filtros.estado().name());
        model.addAttribute("colaborador", filtros.colaboradorId());
        model.addAttribute("colaboradorNombre", filtros.colaboradorId() == null ? null : revisadas.stream()
                .filter(item -> filtros.colaboradorId().equals(item.getEducacion().getColaborador().getId()))
                .map(RmEducacionView::getColaboradorNombre).findFirst().orElse(null));
        model.addAttribute("totalAprobadas", revisadas.stream()
                .filter(item -> item.getEducacion().getEstado() == EstadoCertificado.APROBADO).count());
        model.addAttribute("totalRechazadas", revisadas.stream()
                .filter(item -> item.getEducacion().getEstado() == EstadoCertificado.RECHAZADO).count());
        return "rm/rm-educacion-historial";
    }

    /**
     * Pantalla de revisión de una formación académica.
     *
     * @param educacionId formación; sin ella vuelve a la bandeja
     * @param origen {@code perfil} si se abrió desde el perfil del colaborador
     * @param model modelo de la vista
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual (no se usa)
     * @return la vista {@code rm/rm-revision-educacion}, o la bandeja con el error si no existe
     */
    @GetMapping("/colaboradores/educacion/revision")
    public String educationReview(
            @RequestParam(name = "id", required = false) Long educacionId,
            @RequestParam(required = false) String origen,
            Model model,
            RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (educacionId == null) return "redirect:/rm/colaboradores/educacion";
        try {
            model.addAttribute("formacion", rmEducacionService.obtener(educacionId));
            model.addAttribute("desdePerfil", ORIGEN_PERFIL.equals(origen));
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
            return "redirect:/rm/colaboradores/educacion";
        }
        return "rm/rm-revision-educacion";
    }

    // Redirige a la URL que generó ArchivoAlmacenamientoService: /uploads/... en local o la URL pública en S3.
    /**
     * Abre el documento adjunto de una formación. Si la formación no existe vuelve a la bandeja; si no tiene
     * documento ({@link IllegalStateException}), a su revisión.
     *
     * @param educacionId formación
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual (no se usa)
     * @return redirección al documento, a la bandeja o a la revisión
     */
    @GetMapping("/colaboradores/educacion/{id}/documento")
    public String educationDocument(
            @PathVariable("id") Long educacionId,
            RedirectAttributes redirectAttributes, HttpServletRequest request) {
        try {
            return "redirect:" + rmEducacionService.obtenerUrlDocumento(educacionId);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
            return "redirect:/rm/colaboradores/educacion";
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
            return "redirect:/rm/colaboradores/educacion/revision?id=" + educacionId;
        }
    }

    /**
     * Aprueba una formación académica ({@link RmEducacionService#aprobar}). Con éxito vuelve a la bandeja o al
     * perfil; con error, a la revisión.
     *
     * @param educacionId formación
     * @param origen {@code perfil} para volver al perfil del colaborador
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual; se usa para volver a guardar el contexto de seguridad en la sesión
     * @return redirección a la bandeja, al perfil o a la revisión
     */
    @PostMapping("/colaboradores/educacion/{id}/aprobar")
    public String approveEducation(
            @PathVariable("id") Long educacionId,
            @RequestParam(required = false) String origen,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        boolean desdePerfil = ORIGEN_PERFIL.equals(origen);
        try {
            Long colaboradorId = colaboradorDeLaEducacion(educacionId);
            rmEducacionService.aprobar(educacionId, principal.getUsuario().getId());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "Formación académica aprobada.");
            return destinoTrasRevision(desdePerfil, colaboradorId, "/rm/colaboradores/educacion");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
            return revisionConOrigen("/rm/colaboradores/educacion/revision?id=" + educacionId, desdePerfil);
        }
    }

    /**
     * Rechaza una formación académica y guarda el motivo ({@link RmEducacionService#rechazar}). Con éxito vuelve
     * a la bandeja o al perfil; con error, a la revisión.
     *
     * @param educacionId formación
     * @param motivo motivo del rechazo
     * @param origen {@code perfil} para volver al perfil del colaborador
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual; se usa para volver a guardar el contexto de seguridad en la sesión
     * @return redirección a la bandeja, al perfil o a la revisión
     */
    @PostMapping("/colaboradores/educacion/{id}/rechazar")
    public String rejectEducation(
            @PathVariable("id") Long educacionId,
            @RequestParam(name = "motivo", required = false) String motivo,
            @RequestParam(required = false) String origen,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        boolean desdePerfil = ORIGEN_PERFIL.equals(origen);
        try {
            Long colaboradorId = colaboradorDeLaEducacion(educacionId);
            rmEducacionService.rechazar(educacionId, motivo, principal.getUsuario().getId());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito",
                    "Formación académica rechazada; el motivo quedó guardado.");
            return destinoTrasRevision(desdePerfil, colaboradorId, "/rm/colaboradores/educacion");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
            return revisionConOrigen("/rm/colaboradores/educacion/revision?id=" + educacionId, desdePerfil);
        }
    }

    /**
     * Cambia el nivel de experiencia general de un colaborador
     * ({@link RmCertificadoService#actualizarNivelExperiencia}). Un valor que no sea un
     * {@link NivelExperiencia} se rechaza.
     *
     * @param colaboradorId colaborador
     * @param nivelTexto nombre del nivel
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual; se usa para volver a guardar el contexto de seguridad en la sesión
     * @return redirección al perfil del colaborador
     */
    @PostMapping("/colaboradores/{id}/nivel-experiencia")
    public String updateCollaboratorExperienceLevel(
            @PathVariable("id") Long colaboradorId,
            @RequestParam(name = "nivel", required = false) String nivelTexto,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            NivelExperiencia nivel = convertirEnum(
                    NivelExperiencia.class, nivelTexto, "Selecciona un nivel de experiencia válido.");
            rmCertificadoService.actualizarNivelExperiencia(
                    colaboradorId, nivel, principal.getUsuario().getId());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "Nivel de experiencia actualizado.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
        }
        return "redirect:/rm/colaboradores/perfil?id=" + colaboradorId;
    }

    /**
     * Catálogo paginado de cursos con filtros, más los datos del modal «Asignar curso».
     *
     * @param busqueda texto buscado
     * @param categoria filtro de categoría
     * @param duracion filtro de duración
     * @param pagina número de página; vacío o inválido abre la primera
     * @param model modelo de la vista
     * @return la vista {@code rm/rm-cursos}
     */
    @GetMapping({"/cursos", "/rm-cursos.html"})
    public String courses(
            @RequestParam(name = "busqueda", required = false) String busqueda,
            @RequestParam(name = "categoria", required = false) String categoria,
            @RequestParam(name = "duracion", required = false) String duracion,
            @RequestParam(name = "pagina", required = false) String pagina,
            Model model) {
        var filtros = rmCursoService.normalizarFiltrosCatalogo(busqueda, categoria, duracion);
        model.addAttribute("catalogo", rmCursoService.obtenerCatalogo(filtros, pagina));
        model.addAttribute("tamanioPagina", RmCursoService.TAMANIO_PAGINA_CATALOGO);
        model.addAttribute("busqueda", filtros.busqueda() == null ? "" : filtros.busqueda());
        model.addAttribute("categoriaSeleccionada", filtros.categoria() == null ? "" : filtros.categoria());
        model.addAttribute("duracionSeleccionada", filtros.duracion() == null ? "" : filtros.duracion());
        agregarModalAsignarCurso(model);
        return "rm/rm-cursos";
    }

    /**
     * Bandeja paginada de solicitudes de cursos y evidencias. Antes de listar, marca como vencidos los cursos
     * sin evidencia fuera de plazo. Sin el parámetro {@code estado} muestra las pendientes; {@code estado=}
     * vacío muestra todas.
     *
     * @param busqueda texto buscado
     * @param estado filtro de estado de la inscripción
     * @param origen filtro de origen (solicitada por el colaborador o asignada por el RM)
     * @param pagina número de página; vacío o inválido abre la primera
     * @param model modelo de la vista
     * @return la vista {@code rm/rm-solicitudes-cursos}
     */
    @GetMapping({"/cursos/solicitudes", "/rm-solicitudes-cursos.html"})
    public String courseRequests(
            @RequestParam(name = "busqueda", required = false) String busqueda,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "origen", required = false) String origen,
            @RequestParam(name = "pagina", required = false) String pagina,
            Model model) {
        rmCursoService.marcarCursosSinEvidenciaVencidos();
        var filtros = rmCursoService.normalizarFiltrosBandeja(busqueda, estado, origen);
        model.addAttribute("bandeja", rmCursoService.obtenerBandeja(filtros, pagina));
        model.addAttribute("tamanioPagina", RmCursoService.TAMANIO_PAGINA_BANDEJA);
        model.addAttribute("busqueda", filtros.busqueda() == null ? "" : filtros.busqueda());
        // "Todos" viaja como estado= en los enlaces: sin el parámetro se vuelve a las pendientes.
        model.addAttribute("estadoSeleccionado", filtros.estado() == null ? "" : filtros.estado().name());
        model.addAttribute("origenSeleccionado", filtros.origen() == null ? "" : filtros.origen().name());
        agregarModalAsignarCurso(model);
        return "rm/rm-solicitudes-cursos";
    }

    // Datos del modal "Asignar curso" (fragments/rm-curso-modales :: modalAsignarCurso), TASK-047.
    private void agregarModalAsignarCurso(Model model) {
        model.addAttribute("colaboradoresCurso", rmCursoService.listarColaboradoresParaModal());
        model.addAttribute("cursosModal", rmCursoService.listarCursosParaModal());
    }

    /**
     * Asigna un curso a un colaborador desde el modal «Asignar curso» y lo notifica
     * ({@link RmCursoService#asignarDirectamente}). Exige colaborador y curso. Vuelve a la vista de origen:
     * el perfil del colaborador ({@code perfil}), la bandeja ({@code solicitudes}) o el catálogo.
     *
     * @param colaboradorId colaborador
     * @param cursoId curso
     * @param motivo motivo de la asignación
     * @param origen vista desde la que se abrió el modal
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual; se usa para volver a guardar el contexto de seguridad en la sesión
     * @return redirección a la vista de origen
     */
    @PostMapping("/cursos/asignar")
    public String assignCourseSubmit(
            @RequestParam(name = "colaboradorId", required = false) Long colaboradorId,
            @RequestParam(name = "cursoId", required = false) Long cursoId,
            @RequestParam(name = "motivo", required = false) String motivo,
            @RequestParam(name = "origen", required = false) String origen,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        // Orígenes cerrados: el modal vuelve a la vista desde donde se abrió.
        String retorno = switch (origen == null ? "" : origen) {
            case "perfil" -> colaboradorId != null
                    ? "redirect:/rm/colaboradores/perfil?id=" + colaboradorId : "redirect:/rm/colaboradores";
            case "solicitudes" -> "redirect:/rm/cursos/solicitudes";
            default -> "redirect:/rm/cursos";
        };
        try {
            if (colaboradorId == null || cursoId == null) {
                throw new IllegalArgumentException("Selecciona el colaborador y el curso.");
            }
            rmCursoService.asignarDirectamente(
                    colaboradorId, cursoId, motivo, principal.getUsuario().getId());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito",
                    "Curso asignado correctamente; el colaborador fue notificado.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
        }
        return retorno;
    }

    /**
     * Aprueba la solicitud de curso de un colaborador y lo notifica ({@link RmCursoService#aprobar}).
     *
     * @param inscripcionId inscripción solicitada
     * @param motivo comentario opcional del RM
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual; se usa para volver a guardar el contexto de seguridad en la sesión
     * @return redirección a la bandeja de solicitudes de cursos
     */
    @PostMapping("/cursos/solicitudes/{id}/aprobar")
    public String approveCourseRequest(
            @PathVariable("id") Long inscripcionId,
            @RequestParam(name = "motivo", required = false) String motivo,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            rmCursoService.aprobar(inscripcionId, motivo, principal.getUsuario().getId());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito",
                    "Solicitud aprobada; el colaborador fue notificado.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
        }
        return "redirect:/rm/cursos/solicitudes";
    }

    /**
     * Rechaza la solicitud de curso de un colaborador, registra el motivo y lo notifica
     * ({@link RmCursoService#rechazar}).
     *
     * @param inscripcionId inscripción solicitada
     * @param motivo motivo del rechazo
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual; se usa para volver a guardar el contexto de seguridad en la sesión
     * @return redirección a la bandeja de solicitudes de cursos
     */
    @PostMapping("/cursos/solicitudes/{id}/rechazar")
    public String rejectCourseRequest(
            @PathVariable("id") Long inscripcionId,
            @RequestParam(name = "motivo", required = false) String motivo,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            rmCursoService.rechazar(inscripcionId, motivo, principal.getUsuario().getId());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito",
                    "Solicitud rechazada; el motivo quedó registrado y se notificó al colaborador.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
        }
        return "redirect:/rm/cursos/solicitudes";
    }

    /**
     * Valida la evidencia de un curso: queda completado y se notifica al colaborador
     * ({@link RmCursoService#aprobarEvidencia}).
     *
     * @param inscripcionId inscripción con evidencia pendiente
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual; se usa para volver a guardar el contexto de seguridad en la sesión
     * @return redirección a la bandeja filtrada por {@code EVIDENCIA_PENDIENTE}
     */
    @PostMapping("/cursos/solicitudes/{id}/evidencia/aprobar")
    public String approveCourseEvidence(
            @PathVariable("id") Long inscripcionId,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            rmCursoService.aprobarEvidencia(inscripcionId, principal.getUsuario().getId());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito",
                    "Evidencia validada; el curso quedó marcado como completado y se notificó al colaborador.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
        }
        return "redirect:/rm/cursos/solicitudes?estado=EVIDENCIA_PENDIENTE";
    }

    /**
     * Rechaza la evidencia de un curso; se notifica al colaborador, que puede volver a subirla
     * ({@link RmCursoService#rechazarEvidencia}).
     *
     * @param inscripcionId inscripción con evidencia pendiente
     * @param motivo motivo del rechazo
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual; se usa para volver a guardar el contexto de seguridad en la sesión
     * @return redirección a la bandeja filtrada por {@code EVIDENCIA_PENDIENTE}
     */
    @PostMapping("/cursos/solicitudes/{id}/evidencia/rechazar")
    public String rejectCourseEvidence(
            @PathVariable("id") Long inscripcionId,
            @RequestParam(name = "motivo", required = false) String motivo,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            rmCursoService.rechazarEvidencia(inscripcionId, motivo, principal.getUsuario().getId());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito",
                    "Evidencia rechazada; el colaborador fue notificado y puede volver a subirla.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
        }
        return "redirect:/rm/cursos/solicitudes?estado=EVIDENCIA_PENDIENTE";
    }

    /**
     * Bandeja paginada de asignaciones por grupo (pendientes del RM, esperando al PM, activas, historial…)
     * con filtros normalizados, contadores, el número de solicitudes de personal pendientes y los datos del
     * modal «Proponer asignación». Agrega {@code sufijoBandeja} para que los enlaces a los detalles conserven
     * los filtros y la página.
     *
     * @param grupo grupo de la bandeja
     * @param busqueda texto buscado
     * @param origen filtro de origen de la asignación (propuesta del RM, solicitud del PM, postulación…)
     * @param estado filtro de estado dentro del grupo
     * @param proyectoId proyecto para filtrar; si es asignable, el modal lista solo ese proyecto
     * @param pagina número de página; vacío o inválido abre la primera
     * @param model modelo de la vista
     * @return la vista {@code rm/rm-asignaciones}
     */
    @GetMapping({"/asignaciones", "/rm-asignaciones.html"})
    public String assignments(
            @RequestParam(required = false) String grupo,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) String origen,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String proyectoId,
            @RequestParam(required = false) String pagina,
            Model model) {
        var filtros = rmAsignacionService.normalizarFiltros(grupo, busqueda, origen, estado, proyectoId);
        var paginaAsignaciones = rmAsignacionService.listarPagina(filtros, pagina);
        var contadores = paginaAsignaciones.contadores();
        model.addAttribute("asignaciones", paginaAsignaciones.filas());
        model.addAttribute("paginaActual", paginaAsignaciones.paginaActual());
        model.addAttribute("totalPaginas", paginaAsignaciones.totalPaginas());
        model.addAttribute("totalRegistros", paginaAsignaciones.totalRegistros());
        model.addAttribute("tamanioPagina", RmAsignacionService.TAMANIO_PAGINA);
        model.addAttribute("estadosDisponibles", rmAsignacionService.estadosDelGrupo(filtros.grupo()));
        model.addAttribute("grupo", filtros.grupo());
        model.addAttribute("busqueda", filtros.busqueda());
        model.addAttribute("origen", filtros.origen());
        model.addAttribute("estado", filtros.estado());
        model.addAttribute("proyectoId", filtros.proyectoId());
        model.addAttribute("proyectoNombre", filtros.proyectoNombre());
        // TASK-062: los detalles reciben los filtros y la página para volver a la misma bandeja tras decidir.
        model.addAttribute("sufijoBandeja", sufijoFiltrosBandeja(filtrosBandeja(
                grupo, busqueda, origen, estado, String.valueOf(paginaAsignaciones.paginaActual()))));
        model.addAttribute("pendientesRm", contadores.pendientesRm());
        model.addAttribute("pendientesPm", contadores.pendientesPm());
        model.addAttribute("solicitudesColaborador", contadores.solicitudesColaborador());
        model.addAttribute("activas", contadores.activas());
        model.addAttribute("historial", contadores.historial());
        // TASK-048: acceso a las solicitudes de personal del PM con el número de pendientes.
        model.addAttribute("solicitudesPersonalPendientes", rmSolicitudPersonalService.contarPendientes());
        // "+ Proponer asignación" (TASK-034): elegir proyecto en el modal abre su búsqueda de candidatos.
        agregarProyectosAsignables(model, filtros.proyectoId());
        return "rm/rm-asignaciones";
    }

    /**
     * Revisión de una asignación que espera la decisión del RM, con el impacto en el presupuesto del proyecto.
     * Si la asignación ya no requiere decisión del RM, redirige al detalle que le corresponde.
     *
     * @param asignacionId asignación; sin ella vuelve a la bandeja
     * @param origen vista desde la que se abrió ({@code colaborador}, {@code proyecto} o {@code asignaciones}); otro valor se ignora
     * @param grupo filtro de grupo de la bandeja (se conserva al volver)
     * @param busqueda texto buscado en la bandeja (se conserva al volver)
     * @param origenBandeja filtro «origen» de la bandeja; viaja con otro nombre para no chocar con {@code origen}
     * @param estado filtro de estado de la bandeja (se conserva al volver)
     * @param pagina página de la bandeja (se conserva al volver)
     * @param model modelo de la vista
     * @return la vista {@code rm/rm-revision-asignacion} o una redirección
     */
    @GetMapping({"/asignaciones/revision", "/rm-revision-asignacion.html"})
    public String assignmentReview(
            @RequestParam(name = "id", required = false) Long asignacionId,
            @RequestParam(name = "origen", required = false) String origen,
            @RequestParam(required = false) String grupo,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) String origenBandeja,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String pagina,
            Model model) {
        if (asignacionId == null) return "redirect:/rm/asignaciones";
        var filtrosBandeja = filtrosBandeja(grupo, busqueda, origenBandeja, estado, pagina);
        try {
            RmAsignacionView asignacion = rmAsignacionService.obtener(asignacionId);
            if (!asignacion.isRequiereDecisionRm()) {
                return redirectDetalleAsignacion(asignacion, origen, filtrosBandeja);
            }
            model.addAttribute("asignacion", asignacion);
            model.addAttribute("impacto", rmPresupuestoService.calcularImpacto(
                    asignacion.getAsignacion().getProyecto(), asignacion.getAsignacion().getColaborador(),
                    asignacion.getAsignacion().getHorasSemanales()));
            model.addAttribute("navegacion", navegacionAsignacion(asignacion, origen, filtrosBandeja));
            model.addAttribute("filtrosBandeja", filtrosBandeja);
        } catch (IllegalArgumentException ex) {
            return "redirect:/rm/asignaciones";
        }
        return "rm/rm-revision-asignacion";
    }

    /**
     * Revisión de una postulación enviada por el colaborador, con el impacto en el presupuesto. Si ya no
     * requiere decisión del RM, redirige al detalle que le corresponde.
     *
     * @param asignacionId asignación; sin ella vuelve a la bandeja
     * @param origen vista desde la que se abrió ({@code colaborador}, {@code proyecto} o {@code asignaciones}); otro valor se ignora
     * @param grupo filtro de grupo de la bandeja (se conserva al volver)
     * @param busqueda texto buscado en la bandeja (se conserva al volver)
     * @param origenBandeja filtro «origen» de la bandeja; viaja con otro nombre para no chocar con {@code origen}
     * @param estado filtro de estado de la bandeja (se conserva al volver)
     * @param pagina página de la bandeja (se conserva al volver)
     * @param model modelo de la vista
     * @return la vista {@code rm/rm-revision-postulacion} o una redirección
     */
    @GetMapping({"/asignaciones/revision-postulacion", "/rm-revision-postulacion.html"})
    public String applicationReview(
            @RequestParam(name = "id", required = false) Long asignacionId,
            @RequestParam(name = "origen", required = false) String origen,
            @RequestParam(required = false) String grupo,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) String origenBandeja,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String pagina,
            Model model) {
        if (asignacionId == null) return "redirect:/rm/asignaciones";
        var filtrosBandeja = filtrosBandeja(grupo, busqueda, origenBandeja, estado, pagina);
        try {
            RmAsignacionView asignacion = rmAsignacionService.obtener(asignacionId);
            if (!asignacion.isRequiereDecisionRm()) {
                return redirectDetalleAsignacion(asignacion, origen, filtrosBandeja);
            }
            model.addAttribute("asignacion", asignacion);
            model.addAttribute("impacto", rmPresupuestoService.calcularImpacto(
                    asignacion.getAsignacion().getProyecto(), asignacion.getAsignacion().getColaborador(),
                    asignacion.getAsignacion().getHorasSemanales()));
            model.addAttribute("navegacion", navegacionAsignacion(asignacion, origen, filtrosBandeja));
            model.addAttribute("filtrosBandeja", filtrosBandeja);
        } catch (IllegalArgumentException ex) {
            return "redirect:/rm/asignaciones";
        }
        return "rm/rm-revision-postulacion";
    }

    /**
     * Detalle de solo lectura de una asignación que espera la respuesta del PM. Si ya no está en ese estado,
     * redirige al detalle que le corresponde.
     *
     * @param asignacionId asignación; sin ella vuelve a la bandeja
     * @param origen vista desde la que se abrió ({@code colaborador}, {@code proyecto} o {@code asignaciones}); otro valor se ignora
     * @param grupo filtro de grupo de la bandeja (se conserva al volver)
     * @param busqueda texto buscado en la bandeja (se conserva al volver)
     * @param origenBandeja filtro «origen» de la bandeja; viaja con otro nombre para no chocar con {@code origen}
     * @param estado filtro de estado de la bandeja (se conserva al volver)
     * @param pagina página de la bandeja (se conserva al volver)
     * @param model modelo de la vista
     * @return la vista {@code rm/rm-detalle-asignacion-pendiente-pm} o una redirección
     */
    @GetMapping({"/asignaciones/pendiente-pm", "/rm-detalle-asignacion-pendiente-pm.html"})
    public String pendingPmAssignment(
            @RequestParam(name = "id", required = false) Long asignacionId,
            @RequestParam(name = "origen", required = false) String origen,
            @RequestParam(required = false) String grupo,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) String origenBandeja,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String pagina,
            Model model) {
        if (asignacionId == null) return "redirect:/rm/asignaciones";
        var filtrosBandeja = filtrosBandeja(grupo, busqueda, origenBandeja, estado, pagina);
        try {
            RmAsignacionView asignacion = rmAsignacionService.obtener(asignacionId);
            if (!asignacion.isPendientePm()) return redirectDetalleAsignacion(asignacion, origen, filtrosBandeja);
            model.addAttribute("asignacion", asignacion);
            model.addAttribute("navegacion", navegacionAsignacion(asignacion, origen, filtrosBandeja));
            model.addAttribute("filtrosBandeja", filtrosBandeja);
        } catch (IllegalArgumentException ex) {
            return "redirect:/rm/asignaciones";
        }
        return "rm/rm-detalle-asignacion-pendiente-pm";
    }

    /**
     * Detalle de una asignación activa o cerrada, con sus indicadores de costo. Si aún espera una decisión del
     * RM o del PM, redirige al detalle que le corresponde.
     *
     * @param asignacionId asignación; sin ella vuelve a la bandeja
     * @param origen vista desde la que se abrió ({@code colaborador}, {@code proyecto} o {@code asignaciones}); otro valor se ignora
     * @param grupo filtro de grupo de la bandeja (se conserva al volver)
     * @param busqueda texto buscado en la bandeja (se conserva al volver)
     * @param origenBandeja filtro «origen» de la bandeja; viaja con otro nombre para no chocar con {@code origen}
     * @param estado filtro de estado de la bandeja (se conserva al volver)
     * @param pagina página de la bandeja (se conserva al volver)
     * @param model modelo de la vista
     * @return la vista {@code rm/rm-detalle-asignacion-activa} o una redirección
     */
    @GetMapping({"/asignaciones/activa", "/rm-detalle-asignacion-activa.html"})
    public String activeAssignment(
            @RequestParam(name = "id", required = false) Long asignacionId,
            @RequestParam(name = "origen", required = false) String origen,
            @RequestParam(required = false) String grupo,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) String origenBandeja,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String pagina,
            Model model) {
        if (asignacionId == null) return "redirect:/rm/asignaciones";
        var filtrosBandeja = filtrosBandeja(grupo, busqueda, origenBandeja, estado, pagina);
        try {
            RmAsignacionView asignacion = rmAsignacionService.obtener(asignacionId);
            if (asignacion.isRequiereDecisionRm() || asignacion.isPendientePm()) {
                return redirectDetalleAsignacion(asignacion, origen, filtrosBandeja);
            }
            model.addAttribute("asignacion", asignacion);
            // TASK-057: indicadores de costo con la fórmula única de RmPresupuestoService.
            model.addAttribute("costo", rmPresupuestoService.calcularCosto(
                    asignacion.getAsignacion().getProyecto(), asignacion.getAsignacion().getColaborador(),
                    asignacion.getAsignacion().getHorasSemanales()));
            model.addAttribute("navegacion", navegacionAsignacion(asignacion, origen, filtrosBandeja));
            model.addAttribute("filtrosBandeja", filtrosBandeja);
        } catch (IllegalArgumentException ex) {
            return "redirect:/rm/asignaciones";
        }
        return "rm/rm-detalle-asignacion-activa";
    }

    // TASK-026: orígenes cerrados de los detalles y revisiones de asignación (propios de estos cuatro GET;
    // la propuesta de TASK-034 tiene su propia lista). Cualquier otro valor usa la ruta canónica /rm/asignaciones.
    private static final Set<String> ORIGENES_DETALLE_ASIGNACION = Set.of("colaborador", "proyecto", "asignaciones");

    private static String origenDetalleAsignacion(String origen) {
        return origen != null && ORIGENES_DETALLE_ASIGNACION.contains(origen) ? origen : null;
    }

    // Migas y regreso armados con el colaborador y el proyecto de la propia asignación, no con datos del navegador.
    private RmNavegacionView navegacionAsignacion(
            RmAsignacionView asignacion, String origen, Map<String, Object> filtrosBandeja) {
        String origenValido = origenDetalleAsignacion(origen);
        String actual = "Asignación #" + asignacion.getAsignacion().getId();
        if ("colaborador".equals(origenValido)) {
            Long colaboradorId = asignacion.getAsignacion().getColaborador().getId();
            String asignacionesColaborador = "/rm/colaboradores/asignaciones?id=" + colaboradorId;
            return new RmNavegacionView(origenValido, List.of(
                    new RmNavegacionView.Miga("Colaboradores", "/rm/colaboradores"),
                    new RmNavegacionView.Miga(asignacion.getColaboradorNombre(),
                            "/rm/colaboradores/perfil?id=" + colaboradorId),
                    new RmNavegacionView.Miga("Asignaciones", asignacionesColaborador)),
                    actual, asignacionesColaborador, "Volver a las asignaciones del colaborador");
        }
        if ("proyecto".equals(origenValido)) {
            var proyecto = asignacion.getAsignacion().getProyecto();
            String asignacionesProyecto = urlBandejaAsignaciones(proyecto.getId(), filtrosBandeja);
            return new RmNavegacionView(origenValido, List.of(
                    new RmNavegacionView.Miga("Proyectos", "/rm/proyectos"),
                    new RmNavegacionView.Miga(proyecto.getNombre(), "/rm/proyectos/detalle?id=" + proyecto.getId()),
                    new RmNavegacionView.Miga("Asignaciones", asignacionesProyecto)),
                    actual, asignacionesProyecto, "Volver a las asignaciones del proyecto");
        }
        String bandeja = urlBandejaAsignaciones(null, filtrosBandeja);
        return new RmNavegacionView(origenValido,
                List.of(new RmNavegacionView.Miga("Asignaciones", bandeja)),
                actual, bandeja, "Volver a asignaciones");
    }

    /**
     * Aprueba una asignación pendiente del RM ({@link RmAsignacionService#aprobar}). Si se supera la capacidad
     * semanal del colaborador, el servicio exige el motivo. Vuelve a la vista desde la que se abrió.
     *
     * @param asignacionId asignación
     * @param motivoCapacidad motivo para superar la capacidad semanal del colaborador
     * @param origen vista desde la que se abrió ({@code colaborador}, {@code proyecto} o {@code asignaciones}); otro valor se ignora
     * @param grupo filtro de grupo de la bandeja (se conserva al volver)
     * @param busqueda texto buscado en la bandeja (se conserva al volver)
     * @param origenBandeja filtro «origen» de la bandeja; viaja con otro nombre para no chocar con {@code origen}
     * @param estado filtro de estado de la bandeja (se conserva al volver)
     * @param pagina página de la bandeja (se conserva al volver)
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual; se usa para volver a guardar el contexto de seguridad en la sesión
     * @return redirección a la vista de origen con sus filtros
     */
    @PostMapping("/asignaciones/{id}/aprobar")
    public String approveAssignment(
            @PathVariable("id") Long asignacionId,
            @RequestParam(name = "motivoCapacidad", required = false) String motivoCapacidad,
            @RequestParam(name = "origen", required = false) String origen,
            @RequestParam(required = false) String grupo,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) String origenBandeja,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String pagina,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            rmAsignacionService.aprobar(asignacionId, motivoCapacidad, principal.getUsuario().getId());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "Asignación aprobada correctamente.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
        }
        return "redirect:" + destinoTrasDecision(
                asignacionId, origen, filtrosBandeja(grupo, busqueda, origenBandeja, estado, pagina));
    }

    /**
     * Rechaza una asignación pendiente del RM con un motivo ({@link RmAsignacionService#rechazar}). Vuelve a la
     * vista desde la que se abrió.
     *
     * @param asignacionId asignación
     * @param motivo motivo del rechazo
     * @param origen vista desde la que se abrió ({@code colaborador}, {@code proyecto} o {@code asignaciones}); otro valor se ignora
     * @param grupo filtro de grupo de la bandeja (se conserva al volver)
     * @param busqueda texto buscado en la bandeja (se conserva al volver)
     * @param origenBandeja filtro «origen» de la bandeja; viaja con otro nombre para no chocar con {@code origen}
     * @param estado filtro de estado de la bandeja (se conserva al volver)
     * @param pagina página de la bandeja (se conserva al volver)
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual; se usa para volver a guardar el contexto de seguridad en la sesión
     * @return redirección a la vista de origen con sus filtros
     */
    @PostMapping("/asignaciones/{id}/rechazar")
    public String rejectAssignment(
            @PathVariable("id") Long asignacionId,
            @RequestParam("motivo") String motivo,
            @RequestParam(name = "origen", required = false) String origen,
            @RequestParam(required = false) String grupo,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) String origenBandeja,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String pagina,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            rmAsignacionService.rechazar(asignacionId, motivo, principal.getUsuario().getId());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "Asignación rechazada correctamente.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
        }
        return "redirect:" + destinoTrasDecision(
                asignacionId, origen, filtrosBandeja(grupo, busqueda, origenBandeja, estado, pagina));
    }

    /**
     * Finaliza una asignación activa con motivo, observación interna, calificación y retroalimentación
     * ({@link RmAsignacionService#finalizar}).
     * Vuelve a la vista desde la que se abrió.
     *
     * @param asignacionId asignación
     * @param motivoTexto nombre del motivo de finalización
     * @param observacion observación del cierre
     * @param calificacion calificación del colaborador
     * @param feedback retroalimentación para el colaborador
     * @param origen vista desde la que se abrió ({@code colaborador}, {@code proyecto} o {@code asignaciones}); otro valor se ignora
     * @param grupo filtro de grupo de la bandeja (se conserva al volver)
     * @param busqueda texto buscado en la bandeja (se conserva al volver)
     * @param origenBandeja filtro «origen» de la bandeja; viaja con otro nombre para no chocar con {@code origen}
     * @param estado filtro de estado de la bandeja (se conserva al volver)
     * @param pagina página de la bandeja (se conserva al volver)
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual; se usa para volver a guardar el contexto de seguridad en la sesión
     * @return redirección a la vista de origen con sus filtros
     */
    @PostMapping("/asignaciones/{id}/finalizar")
    public String finishAssignment(
            @PathVariable("id") Long asignacionId,
            @RequestParam(name = "motivo", required = false) String motivoTexto,
            @RequestParam(name = "observacion", required = false) String observacion,
            @RequestParam(name = "calificacion", required = false) Integer calificacion,
            @RequestParam(name = "feedback", required = false) String feedback,
            @RequestParam(name = "origen", required = false) String origen,
            @RequestParam(required = false) String grupo,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) String origenBandeja,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String pagina,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            MotivoFinalizacion motivo = convertirEnum(
                    MotivoFinalizacion.class, motivoTexto, "Selecciona un motivo de finalización válido.");
            if (motivo == null) {
                throw new IllegalArgumentException("Selecciona un motivo de finalización.");
            }
            if (calificacion == null || feedback == null || feedback.isBlank()
                    || observacion == null || observacion.isBlank()) {
                throw new IllegalArgumentException("Completa la calificación, el feedback y la observación interna.");
            }
            rmAsignacionService.finalizar(
                    asignacionId, motivo, observacion, calificacion, feedback, principal.getUsuario().getId());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "Asignación finalizada correctamente.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
        }
        return "redirect:" + destinoTrasDecision(
                asignacionId, origen, filtrosBandeja(grupo, busqueda, origenBandeja, estado, pagina));
    }

    // Conserva el origen solo si pertenece a la lista cerrada de los detalles (TASK-026).
    private String redirectDetalleAsignacion(RmAsignacionView asignacion, String origen) {
        return redirectDetalleAsignacion(asignacion, origen, Map.of());
    }

    // TASK-062: también conserva los filtros de la bandeja ya normalizados.
    private String redirectDetalleAsignacion(
            RmAsignacionView asignacion, String origen, Map<String, Object> filtrosBandeja) {
        String ruta;
        if (asignacion.isRequiereDecisionRm()) {
            ruta = asignacion.isSolicitudColaborador()
                    ? "/rm/asignaciones/revision-postulacion" : "/rm/asignaciones/revision";
        } else if (asignacion.isPendientePm()) {
            ruta = "/rm/asignaciones/pendiente-pm";
        } else {
            ruta = "/rm/asignaciones/activa";
        }
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("id", asignacion.getAsignacion().getId());
        String origenValido = origenDetalleAsignacion(origen);
        if (origenValido != null) valores.put("origen", origenValido);
        valores.putAll(filtrosBandeja);
        return "redirect:" + construirUrl(ruta, valores);
    }

    // TASK-062: filtros de la bandeja que viajan por los detalles y las decisiones. Solo esta lista cerrada,
    // normalizada por el servicio; los valores por defecto no se agregan. El filtro "origen" de la bandeja
    // viaja como "origenBandeja" para no chocar con el origen de navegación (TASK-026).
    private Map<String, Object> filtrosBandeja(
            String grupo, String busqueda, String origenBandeja, String estado, String pagina) {
        var filtros = rmAsignacionService.normalizarFiltros(grupo, busqueda, origenBandeja, estado, null);
        Map<String, Object> valores = new LinkedHashMap<>();
        if (!RmAsignacionService.GRUPO_POR_DEFECTO.equals(filtros.grupo())) valores.put("grupo", filtros.grupo());
        if (filtros.busqueda() != null) valores.put("busqueda", filtros.busqueda());
        if (filtros.origen() != null) valores.put("origenBandeja", filtros.origen());
        if (filtros.estado() != null) valores.put("estado", filtros.estado());
        try {
            int numeroPagina = Integer.parseInt(pagina == null ? "" : pagina.trim());
            if (numeroPagina > 1) valores.put("pagina", numeroPagina);
        } catch (NumberFormatException ignorado) {
            // Página vacía o inválida: la bandeja abre la primera.
        }
        return valores;
    }

    // Bandeja con sus filtros; el proyecto sale siempre de la asignación, nunca del navegador.
    private String urlBandejaAsignaciones(Long proyectoId, Map<String, Object> filtrosBandeja) {
        Map<String, Object> valores = new LinkedHashMap<>();
        if (proyectoId != null) valores.put("proyectoId", proyectoId);
        filtrosBandeja.forEach((nombre, valor) ->
                valores.put("origenBandeja".equals(nombre) ? "origen" : nombre, valor));
        return construirUrl("/rm/asignaciones", valores);
    }

    // Destino tras aprobar, rechazar o finalizar (TASK-062): la vista desde la que se abrió la asignación.
    private String destinoTrasDecision(Long asignacionId, String origen, Map<String, Object> filtrosBandeja) {
        String origenValido = origenDetalleAsignacion(origen);
        if ("colaborador".equals(origenValido) || "proyecto".equals(origenValido)) {
            try {
                var asignacion = rmAsignacionService.obtener(asignacionId).getAsignacion();
                if ("colaborador".equals(origenValido)) {
                    return "/rm/colaboradores/asignaciones?id=" + asignacion.getColaborador().getId();
                }
                return urlBandejaAsignaciones(asignacion.getProyecto().getId(), filtrosBandeja);
            } catch (IllegalArgumentException ex) {
                return "/rm/asignaciones";
            }
        }
        return urlBandejaAsignaciones(null, filtrosBandeja);
    }

    // Parámetros de los filtros como "&clave=valor…" (codificados) para los enlaces de la bandeja a sus detalles.
    private static String sufijoFiltrosBandeja(Map<String, Object> filtrosBandeja) {
        if (filtrosBandeja.isEmpty()) return "";
        String url = construirUrl("/", filtrosBandeja);
        return "&" + url.substring(url.indexOf('?') + 1);
    }

    // Cada valor va como variable de URI: se codifica completo (incluidos "+", "&" y tildes).
    private static String construirUrl(String ruta, Map<String, Object> valores) {
        UriComponentsBuilder url = UriComponentsBuilder.fromPath(ruta);
        valores.keySet().forEach(nombre -> url.queryParam(nombre, "{" + nombre + "}"));
        return url.encode().buildAndExpand(valores).toUriString();
    }

    /**
     * Bandeja paginada de solicitudes de personal enviadas por los PM, con contadores por estado y el total de
     * colaboradores solicitados.
     *
     * @param noEncontrada {@code true} si se llega desde una solicitud inexistente (muestra un aviso)
     * @param busqueda texto buscado
     * @param estado filtro de estado de la solicitud
     * @param prioridad filtro de prioridad
     * @param pagina número de página; vacío o inválido abre la primera
     * @param model modelo de la vista
     * @return la vista {@code rm/rm-solicitudes-colaboradores}
     */
    @GetMapping({"/asignaciones/solicitudes-colaboradores", "/rm-solicitudes-colaboradores.html"})
    public String collaboratorRequests(
            @RequestParam(name = "noEncontrada", required = false) Boolean noEncontrada,
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String prioridad,
            @RequestParam(required = false) String pagina,
            Model model) {
        var filtros = rmSolicitudPersonalService.normalizarFiltros(busqueda, estado, prioridad);
        var paginaSolicitudes = rmSolicitudPersonalService.listarPagina(filtros, pagina);
        var contadores = paginaSolicitudes.contadores();
        model.addAttribute("solicitudes", paginaSolicitudes.filas());
        model.addAttribute("paginaActual", paginaSolicitudes.paginaActual());
        model.addAttribute("totalPaginas", paginaSolicitudes.totalPaginas());
        model.addAttribute("totalRegistros", paginaSolicitudes.totalRegistros());
        model.addAttribute("tamanioPagina", RmSolicitudPersonalService.TAMANIO_PAGINA);
        model.addAttribute("busqueda", filtros.busqueda());
        model.addAttribute("estado", filtros.estado());
        model.addAttribute("prioridad", filtros.prioridad());
        model.addAttribute("pendientes", contadores.pendientes());
        model.addAttribute("enAtencion", contadores.enAtencion());
        model.addAttribute("atendidas", contadores.atendidas());
        model.addAttribute("totalSolicitados", contadores.totalSolicitados());
        model.addAttribute("solicitudNoEncontrada", Boolean.TRUE.equals(noEncontrada));
        return "rm/rm-solicitudes-colaboradores";
    }

    /**
     * Detalle de una solicitud de personal del PM.
     *
     * @param solicitudId solicitud; sin ella vuelve a la bandeja
     * @param model modelo de la vista
     * @return la vista {@code rm/rm-detalle-solicitud-colaboradores}, o la bandeja con {@code noEncontrada=true}
     */
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

    /**
     * Pasa una solicitud de personal a «En atención» ({@link RmSolicitudPersonalService#iniciarAtencion}) y abre
     * la búsqueda de candidatos de su proyecto. Con error vuelve al detalle de la solicitud.
     *
     * @param solicitudId solicitud
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual; se usa para volver a guardar el contexto de seguridad en la sesión
     * @return redirección a la búsqueda de candidatos o al detalle de la solicitud
     */
    @PostMapping("/asignaciones/solicitudes-colaboradores/{id}/iniciar")
    public String startCollaboratorRequest(
            @PathVariable("id") Long solicitudId,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            rmSolicitudPersonalService.iniciarAtencion(solicitudId, principal.getUsuario().getId());
            RmSolicitudPersonalView solicitud = rmSolicitudPersonalService.obtener(solicitudId);
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "La solicitud pasó a En atención.");
            return "redirect:/rm/proyectos/buscar-colaboradores?proyectoId="
                    + solicitud.getSolicitud().getProyecto().getId();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
            return "redirect:/rm/asignaciones/solicitudes-colaboradores/detalle?id=" + solicitudId;
        }
    }

    /**
     * Marca una solicitud de personal como atendida ({@link RmSolicitudPersonalService#marcarAtendida}).
     *
     * @param solicitudId solicitud
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual; se usa para volver a guardar el contexto de seguridad en la sesión
     * @return redirección al detalle de la solicitud
     */
    @PostMapping("/asignaciones/solicitudes-colaboradores/{id}/atendida")
    public String finishCollaboratorRequest(
            @PathVariable("id") Long solicitudId,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            rmSolicitudPersonalService.marcarAtendida(solicitudId, principal.getUsuario().getId());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "Solicitud marcada como atendida.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
        }
        return "redirect:/rm/asignaciones/solicitudes-colaboradores/detalle?id=" + solicitudId;
    }

    /**
     * Talent Matching y asistente IA del RM: proyectos con vacantes, habilidades faltantes, recomendaciones,
     * indicadores y conversación guardada en la sesión. Un {@code proyectoId} vacío, mal formado o inexistente
     * se ignora y se muestra la vista general.
     *
     * @param proyectoIdTexto proyecto de contexto (opcional)
     * @param session sesión HTTP donde se guarda la conversación con el asistente
     * @param model modelo de la vista
     * @return la vista {@code rm/rm-talent-matching}
     */
    @GetMapping({"/talent-matching", "/rm-talent-matching.html", "/asistente"})
    public String talentMatching(
            @RequestParam(name = "proyectoId", required = false) String proyectoIdTexto,
            jakarta.servlet.http.HttpSession session,
            Model model) {
        // TASK-026: la ruta muestra el proyecto solo si proyectoId es un proyecto existente;
        // vacío, mal formado o inexistente => ruta canónica "Talent Matching" (sin inventar un proyecto).
        RmProyectoView proyectoContexto = null;
        try {
            if (proyectoIdTexto != null && !proyectoIdTexto.isBlank()) {
                proyectoContexto = rmProyectoConsultaService.obtener(Long.valueOf(proyectoIdTexto.trim()));
            }
        } catch (IllegalArgumentException ignorado) {
            // NumberFormatException también es IllegalArgumentException.
        }
        cargarVistaTalentMatchingYAsistente(proyectoContexto, session, model);
        return "rm/rm-talent-matching";
    }

    /**
     * Envía una pregunta al asistente IA del RM ({@link RmAsistenteService#responder}); la respuesta se guarda en
     * la sesión. Con errores de validación vuelve a mostrar la vista sin redirigir; con éxito o error de negocio
     * redirige a la sección del asistente.
     *
     * @param form mensaje y proyecto de contexto
     * @param result errores de validación del formulario
     * @param principal RM con la sesión iniciada
     * @param session sesión HTTP donde se guarda la conversación
     * @param model modelo de la vista (si hay errores de validación)
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @return la vista {@code rm/rm-talent-matching} o la redirección a la sección del asistente
     */
    @PostMapping("/talent-matching/asistente/mensaje")
    public String enviarMensajeAsistenteRm(
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.ModelAttribute("rmAsistenteMensajeForm")
            com.pucp.skillb_ia.dto.RmAsistenteMensajeForm form,
            org.springframework.validation.BindingResult result,
            @AuthenticationPrincipal UsuarioDetails principal,
            jakarta.servlet.http.HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {
        RmProyectoView proyectoContexto = null;
        if (form.getProyectoId() != null) {
            try {
                proyectoContexto = rmProyectoConsultaService.obtener(form.getProyectoId());
            } catch (IllegalArgumentException ignorado) {
                proyectoContexto = null;
            }
        }
        if (result.hasErrors()) {
            cargarVistaTalentMatchingYAsistente(proyectoContexto, session, model);
            return "rm/rm-talent-matching";
        }
        try {
            Long rmId = principal != null ? principal.getUsuario().getId() : null;
            rmAsistenteService.responder(rmId, form.getProyectoId(), form.getMensaje(), session);
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
        }
        return proyectoContexto != null
                ? "redirect:/rm/talent-matching?proyectoId=" + proyectoContexto.getProyecto().getId() + "#rmAsistenteSeccion"
                : "redirect:/rm/talent-matching#rmAsistenteSeccion";
    }

    /**
     * Borra la conversación con el asistente IA guardada en la sesión.
     *
     * @param proyectoId proyecto de contexto al que se vuelve (opcional)
     * @param session sesión HTTP donde se guarda la conversación
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @return redirección a la sección del asistente
     */
    @PostMapping("/talent-matching/asistente/limpiar")
    public String limpiarAsistenteRm(
            @RequestParam(name = "proyectoId", required = false) Long proyectoId,
            jakarta.servlet.http.HttpSession session,
            RedirectAttributes redirectAttributes) {
        rmAsistenteService.limpiarHistorial(session);
        redirectAttributes.addFlashAttribute("mensajeExito", "Conversación con el Asistente IA del RM reiniciada.");
        return proyectoId != null
                ? "redirect:/rm/talent-matching?proyectoId=" + proyectoId + "#rmAsistenteSeccion"
                : "redirect:/rm/talent-matching#rmAsistenteSeccion";
    }

    /**
     * Propone al PM un candidato recomendado por Talent Matching
     * ({@link RmAsistenteService#proponerDesdeTalentMatching}), con las mismas reglas que la propuesta manual.
     *
     * @param proyectoId proyecto
     * @param colaboradorId colaborador propuesto
     * @param horasSemanalesTexto horas semanales; un valor no numérico se rechaza
     * @param justificacion justificación de la propuesta
     * @param motivoCapacidad motivo para superar la capacidad semanal del colaborador
     * @param justificacionCupo justificación para superar los cupos del proyecto
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @return redirección a Talent Matching del proyecto
     */
    @PostMapping("/talent-matching/proponer")
    public String proponerDesdeTalentMatching(
            @RequestParam("proyectoId") Long proyectoId,
            @RequestParam("colaboradorId") Long colaboradorId,
            @RequestParam(name = "horasSemanales", required = false) String horasSemanalesTexto,
            @RequestParam(name = "justificacion", required = false) String justificacion,
            @RequestParam(name = "motivoCapacidad", required = false) String motivoCapacidad,
            @RequestParam(name = "justificacionCupo", required = false) String justificacionCupo,
            @AuthenticationPrincipal UsuarioDetails principal,
            RedirectAttributes redirectAttributes) {
        if (principal == null) return "redirect:/login";
        try {
            BigDecimal horasSemanales = convertirDecimal(
                    horasSemanalesTexto, "Las horas semanales deben ser un número válido.");
            rmAsistenteService.proponerDesdeTalentMatching(
                    principal.getUsuario().getId(), proyectoId, colaboradorId,
                    horasSemanales, justificacion, motivoCapacidad, justificacionCupo);
            redirectAttributes.addFlashAttribute("mensajeExito",
                    "Propuesta enviada al Project Manager correctamente desde Talent Matching.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("mensajeError", ex.getMessage());
        }
        return "redirect:/rm/talent-matching?proyectoId=" + proyectoId;
    }

    private void cargarVistaTalentMatchingYAsistente(RmProyectoView proyectoContexto,
                                                     jakarta.servlet.http.HttpSession session,
                                                     Model model) {
        var matching = rmTalentMatchingService.construirMatching(proyectoContexto);
        Long idSeleccionado = matching.proyectoSeleccionado() != null
                ? matching.proyectoSeleccionado().getProyecto().getId()
                : null;
        model.addAttribute("proyectoContexto", proyectoContexto);
        model.addAttribute("matching", matching);
        model.addAttribute("proyectosConVacantes", matching.proyectosConVacantes());
        model.addAttribute("proyectoSeleccionado", matching.proyectoSeleccionado());
        model.addAttribute("habilidadesFaltantes", matching.habilidadesFaltantes());
        model.addAttribute("recomendaciones", matching.recomendaciones());
        model.addAttribute("kpisAsistente", rmAsistenteContextoService.obtenerKpis());
        model.addAttribute("consultasRapidas", rmAsistenteContextoService.listarConsultasRapidas(idSeleccionado));
        var turnos = rmAsistenteService.obtenerTurnos(session);
        model.addAttribute("turnosAsistente", turnos);

        // Modal "Proponer asignación": datos del proyecto seleccionado y de los proyectos
        // de los candidatos del chat, más los sueldos base para el cálculo del costo.
        java.util.Map<Long, RmProyectoView> proyectosPropuesta = new java.util.HashMap<>();
        if (matching.proyectoSeleccionado() != null) {
            proyectosPropuesta.put(idSeleccionado, matching.proyectoSeleccionado());
        }
        turnos.stream()
                .filter(t -> t.respuesta() != null && t.respuesta().candidatos() != null)
                .flatMap(t -> t.respuesta().candidatos().stream())
                .map(com.pucp.skillb_ia.dto.RmAsistenteRespuesta.CandidatoTarjetaRm::proyectoId)
                .filter(id -> id != null && !proyectosPropuesta.containsKey(id))
                .distinct()
                .forEach(id -> {
                    try {
                        proyectosPropuesta.put(id, rmProyectoConsultaService.obtener(id));
                    } catch (IllegalArgumentException ignorado) {
                        // Proyecto fuera de alcance o eliminado: su tarjeta no abre el modal.
                    }
                });
        model.addAttribute("proyectosPropuesta", proyectosPropuesta);
        model.addAttribute("cuposPropuesta", rmAsignacionService.cuposOcupadosPorProyecto(
                proyectosPropuesta.values().stream().map(RmProyectoView::getProyecto).toList()));
        model.addAttribute("sueldosPorColaborador", rmColaboradorConsultaService.mapaSueldosBase());
        if (!model.containsAttribute("rmAsistenteMensajeForm")) {
            com.pucp.skillb_ia.dto.RmAsistenteMensajeForm form = new com.pucp.skillb_ia.dto.RmAsistenteMensajeForm();
            form.setProyectoId(idSeleccionado);
            model.addAttribute("rmAsistenteMensajeForm", form);
        }
    }

    /**
     * Lista paginada de foros con filtros e indicadores (foros, activos, publicaciones y última actividad).
     *
     * @param noEncontrado {@code true} si se llega desde un foro inexistente (muestra un aviso)
     * @param busqueda texto buscado
     * @param estado filtro de estado del foro
     * @param actividad filtro de actividad reciente
     * @param pagina número de página; vacío o inválido abre la primera
     * @param model modelo de la vista
     * @return la vista {@code rm/rm-foros}
     */
    @GetMapping({"/foros", "/rm-foros.html"})
    public String forums(@RequestParam(name = "noEncontrado", required = false) Boolean noEncontrado,
                         @RequestParam(required = false) String busqueda,
                         @RequestParam(required = false) String estado,
                         @RequestParam(required = false) String actividad,
                         @RequestParam(required = false) String pagina,
                         Model model) {
        var filtros = rmForoConsultaService.normalizarFiltros(busqueda, estado, actividad);
        var paginaForos = rmForoConsultaService.listarPagina(filtros, pagina);
        var indicadores = paginaForos.indicadores();
        model.addAttribute("foros", paginaForos.foros());
        model.addAttribute("paginaActual", paginaForos.paginaActual());
        model.addAttribute("totalPaginas", paginaForos.totalPaginas());
        model.addAttribute("totalRegistros", paginaForos.totalRegistros());
        model.addAttribute("tamanioPagina", RmForoConsultaService.TAMANIO_PAGINA);
        model.addAttribute("busqueda", filtros.busqueda());
        model.addAttribute("estado", filtros.estado());
        model.addAttribute("actividad", filtros.actividad());
        model.addAttribute("totalForos", indicadores.totalForos());
        model.addAttribute("totalActivos", indicadores.totalActivos());
        model.addAttribute("totalPublicaciones", indicadores.totalPublicaciones());
        model.addAttribute("ultimaActividad", indicadores.ultimaActividad());
        model.addAttribute("foroNoEncontrado", Boolean.TRUE.equals(noEncontrado));
        return "rm/rm-foros";
    }

    /**
     * Detalle de un foro con sus publicaciones; prepara el formulario de nueva publicación que procesa
     * {@link RmForoController}.
     *
     * @param id foro
     * @param ordenar {@code votos} para ordenar por votos; cualquier otro valor ordena por fecha
     * @param model modelo de la vista
     * @return la vista {@code rm/rm-foro-detalle}, o la lista con {@code noEncontrado=true}
     */
    @GetMapping({"/foros/detalle", "/rm-foro-detalle.html"})
    public String forumDetail(@RequestParam Long id,
                              @RequestParam(defaultValue = "fecha") String ordenar,
                              Model model) {
        try {
            model.addAttribute("foro", rmForoConsultaService.obtener(id, ordenar));
            model.addAttribute("ordenActual", "votos".equalsIgnoreCase(ordenar) ? "votos" : "fecha");
            if (!model.containsAttribute("pmForoPublicacionForm")) {
                model.addAttribute("pmForoPublicacionForm", new com.pucp.skillb_ia.dto.pm.PmForoPublicacionForm());
            }
            return "rm/rm-foro-detalle";
        } catch (IllegalArgumentException e) {
            return "redirect:/rm/foros?noEncontrado=true";
        }
    }

    /**
     * Reporte de recursos por rango de meses, con tablas paginadas de proyectos y colaboradores y las opciones
     * del modal de exportación. El rango puede llegar como año y mes por separado (formulario) o como
     * {@code periodo}/{@code hasta} (enlaces de página); su largo máximo es
     * {@link RmReporteService#MAXIMO_MESES_RANGO} meses.
     *
     * @param periodo mes inicial (AAAA-MM)
     * @param proyectoTexto proyecto para filtrar; vacío o no numérico equivale a «Todos»
     * @param estado filtro de estado del proyecto
     * @param hasta mes final (AAAA-MM)
     * @param prioridad filtro de prioridad
     * @param anioDesde año inicial del formulario
     * @param mesDesde mes inicial del formulario
     * @param anioHasta año final del formulario
     * @param mesHasta mes final del formulario
     * @param paginaProyectos página de la tabla de proyectos
     * @param paginaColaboradores página de la tabla de colaboradores
     * @param model modelo de la vista
     * @return la vista {@code rm/rm-reporte-recursos}
     */
    @GetMapping({"/reportes/recursos", "/rm-reporte-recursos.html"})
    public String resourceReport(
            @RequestParam(name = "periodo", required = false) String periodo,
            @RequestParam(name = "proyecto", required = false) String proyectoTexto,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "hasta", required = false) String hasta,
            @RequestParam(name = "prioridad", required = false) String prioridad,
            @RequestParam(name = "anioDesde", required = false) String anioDesde,
            @RequestParam(name = "mesDesde", required = false) String mesDesde,
            @RequestParam(name = "anioHasta", required = false) String anioHasta,
            @RequestParam(name = "mesHasta", required = false) String mesHasta,
            @RequestParam(name = "paginaProyectos", required = false) String paginaProyectos,
            @RequestParam(name = "paginaColaboradores", required = false) String paginaColaboradores,
            Model model) {
        // El formulario envía año y mes por separado; los enlaces de página, "periodo" y "hasta".
        var filtros = rmReporteService.normalizarFiltrosRecursos(
                rmReporteService.componerPeriodo(anioDesde, mesDesde, periodo),
                rmReporteService.componerPeriodo(anioHasta, mesHasta, hasta),
                idReporte(proyectoTexto), estado, prioridad);
        var paginaRecursos = rmReporteService.listarPaginaRecursos(filtros, paginaProyectos, paginaColaboradores);
        RmReporteView reporte = paginaRecursos.reporte();
        cargarModeloReporte(model, reporte);
        model.addAttribute("paginaProyectos", paginaRecursos.proyectos());
        model.addAttribute("paginaColaboradores", paginaRecursos.colaboradores());
        model.addAttribute("tamanioPagina", RmReporteService.TAMANIO_PAGINA);
        model.addAttribute("archivoExportacion", rmReporteExportService.nombreArchivo(reporte));
        model.addAttribute("columnasProyecto", RmReporteExportService.COLUMNAS_PROYECTO);
        model.addAttribute("columnasDetalle", RmReporteExportService.COLUMNAS_DETALLE);
        model.addAttribute("metricasResumen", RmReporteExportService.METRICAS);
        model.addAttribute("aniosFiltro", rmReporteService.listarAnios(reporte));
        model.addAttribute("mesesFiltro", rmReporteService.listarMeses());
        model.addAttribute("maximoMesesRango", RmReporteService.MAXIMO_MESES_RANGO);
        model.addAttribute("mesActual", java.time.YearMonth.now().toString());
        return "rm/rm-reporte-recursos";
    }

    /**
     * Reporte de horas por colaborador con su desglose. Los indicadores (total de horas, cuántos están por
     * debajo o en la referencia) se calculan sobre todo el conjunto filtrado, no sobre la página visible. Si el
     * colaborador pedido no está en el resultado, se selecciona el primero.
     *
     * @param periodo mes del reporte (AAAA-MM)
     * @param proyectoId proyecto para filtrar
     * @param busqueda texto buscado
     * @param colaboradorId colaborador cuyo desglose se muestra
     * @param pagina número de página; vacío o inválido abre la primera
     * @param model modelo de la vista
     * @return la vista {@code rm/rm-horas-colaboradores}
     */
    @GetMapping({"/reportes/horas-colaboradores", "/rm-horas-colaboradores.html"})
    public String collaboratorHours(
            @RequestParam(name = "periodo", required = false) String periodo,
            @RequestParam(name = "proyecto", required = false) Long proyectoId,
            @RequestParam(name = "busqueda", required = false) String busqueda,
            @RequestParam(name = "colaborador", required = false) Long colaboradorId,
            @RequestParam(name = "pagina", required = false) String pagina,
            Model model) {
        var filtros = rmReporteService.normalizarFiltrosHoras(periodo, proyectoId, busqueda, colaboradorId);
        var paginaHoras = rmReporteService.listarPaginaHoras(filtros, pagina);
        RmReporteView reporte = paginaHoras.reporte();
        // Conjunto completo filtrado: indicadores y desglose no dependen de la página visible.
        List<RmReporteView.ColaboradorReporte> colaboradores = paginaHoras.colaboradores();
        RmReporteView.ColaboradorReporte seleccionado = colaboradores.stream()
                .filter(item -> colaboradorId != null && item.getId().equals(colaboradorId))
                .findFirst()
                .orElseGet(() -> colaboradores.stream().findFirst().orElse(null));
        cargarModeloReporte(model, reporte);
        model.addAttribute("colaboradores", paginaHoras.pagina().filas());
        model.addAttribute("paginaActual", paginaHoras.pagina().paginaActual());
        model.addAttribute("totalPaginas", paginaHoras.pagina().totalPaginas());
        model.addAttribute("totalRegistros", paginaHoras.pagina().totalRegistros());
        model.addAttribute("tamanioPagina", RmReporteService.TAMANIO_PAGINA);
        model.addAttribute("colaboradorSeleccionado", seleccionado);
        model.addAttribute("busqueda", filtros.busqueda());
        model.addAttribute("colaboradorFiltro", filtros.colaborador());
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

    // Exporta las horas con los mismos filtros de la pantalla (todas las filas, no solo la página visible).
    /**
     * Descarga el reporte de horas con los mismos filtros de la pantalla y todas las filas.
     *
     * @param periodo mes del reporte (AAAA-MM)
     * @param proyectoId proyecto para filtrar
     * @param busqueda texto buscado
     * @param formato {@code pdf} para PDF; cualquier otro valor genera Excel (.xlsx)
     * @return el archivo como adjunto
     */
    @GetMapping("/reportes/horas-colaboradores/exportar")
    public ResponseEntity<byte[]> exportarHorasColaboradores(
            @RequestParam(name = "periodo", required = false) String periodo,
            @RequestParam(name = "proyecto", required = false) Long proyectoId,
            @RequestParam(name = "busqueda", required = false) String busqueda,
            @RequestParam(name = "formato", defaultValue = "excel") String formato) {
        var filtros = rmReporteService.normalizarFiltrosHoras(periodo, proyectoId, busqueda, null);
        var paginaHoras = rmReporteService.listarPaginaHoras(filtros, null);
        RmReporteView reporte = paginaHoras.reporte();
        String nombre = rmReporteExportService.nombreArchivoHoras(reporte);
        if ("pdf".equalsIgnoreCase(formato)) {
            return archivo(rmReporteExportService.crearPdfHoras(reporte, paginaHoras.colaboradores()),
                    nombre + ".pdf", "application/pdf");
        }
        return archivo(rmReporteExportService.crearExcelHoras(reporte, paginaHoras.colaboradores()),
                nombre + ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }
    /**
     * Descarga el reporte de recursos en Excel o PDF. En modo personalizado se eligen las hojas y columnas;
     * {@link RmReporteExportService.OpcionesExcel#normalizar} descarta los valores fuera de las listas cerradas.
     *
     * @param periodo mes inicial (AAAA-MM)
     * @param proyectoTexto proyecto para filtrar; vacío o no numérico equivale a «Todos»
     * @param estado filtro de estado del proyecto
     * @param hasta mes final (AAAA-MM)
     * @param prioridad filtro de prioridad
     * @param personalizado {@code true} para usar las hojas y columnas elegidas
     * @param resumen {@code true} para exportar solo el resumen
     * @param formato {@code pdf} para PDF; cualquier otro valor genera Excel (.xlsx)
     * @param hojas hojas elegidas (modo personalizado)
     * @param colProyecto índices de columnas de la hoja de proyectos (modo personalizado)
     * @param colDetalle índices de columnas de la hoja de detalle (modo personalizado)
     * @return el archivo como adjunto
     */
    @GetMapping("/reportes/recursos/excel")
    public ResponseEntity<byte[]> exportarReporteExcel(
            @RequestParam(name = "periodo", required = false) String periodo,
            @RequestParam(name = "proyecto", required = false) String proyectoTexto,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "hasta", required = false) String hasta,
            @RequestParam(name = "prioridad", required = false) String prioridad,
            @RequestParam(name = "personalizado", defaultValue = "false") boolean personalizado,
            @RequestParam(name = "resumen", defaultValue = "false") boolean resumen,
            @RequestParam(name = "formato", defaultValue = "excel") String formato,
            @RequestParam(name = "hojas", required = false) List<String> hojas,
            @RequestParam(name = "colProyecto", required = false) List<Integer> colProyecto,
            @RequestParam(name = "colDetalle", required = false) List<Integer> colDetalle) {
        var opciones = RmReporteExportService.OpcionesExcel.normalizar(
                personalizado, resumen, hojas, colProyecto, colDetalle);
        RmReporteView reporte = rmReporteService.generar(periodo, hasta, idReporte(proyectoTexto), estado, prioridad);
        if ("pdf".equalsIgnoreCase(formato)) {
            return archivo(rmReporteExportService.crearPdf(reporte, opciones),
                    rmReporteExportService.nombreArchivo(reporte) + ".pdf",
                    "application/pdf");
        }
        return archivo(rmReporteExportService.crearExcel(reporte, opciones),
                rmReporteExportService.nombreArchivo(reporte) + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    /** Proyecto del reporte: vacío o no numérico se trata como "Todos", igual que un estado inválido. */
    private Long idReporte(String proyectoTexto) {
        if (proyectoTexto == null || !proyectoTexto.trim().matches("\\d{1,18}")) return null;
        return Long.valueOf(proyectoTexto.trim());
    }

    private void cargarModeloReporte(Model model, RmReporteView reporte) {
        model.addAttribute("reporte", reporte);
        model.addAttribute("periodos", rmReporteService.listarPeriodos(reporte.getPeriodoValor()));
        model.addAttribute("proyectosFiltro", rmReporteService.listarProyectos());
        model.addAttribute("estadosFiltro", rmReporteService.listarEstados());
        model.addAttribute("prioridadesFiltro", rmReporteService.listarPrioridades());
    }

    private ResponseEntity<byte[]> archivo(byte[] contenido, String nombre, String contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(nombre, StandardCharsets.UTF_8).build());
        headers.setContentLength(contenido.length);
        return ResponseEntity.ok().headers(headers).body(contenido);
    }

    /**
     * Perfil propio del RM. Redirige al login si no hay sesión o si el usuario no tiene el rol
     * {@code RESOURCE_MANAGER}.
     *
     * @param principal usuario con la sesión iniciada
     * @param model modelo de la vista
     * @return la vista {@code rm/rm-perfil} o la redirección al login
     */
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

    /**
     * Actualiza el teléfono del RM ({@link RmPerfilService#actualizarTelefono}). Con error conserva lo escrito
     * en el flash {@code telefonoIngresado}; el mensaje pasa por {@code mensajeSeguro}.
     *
     * @param telefono teléfono nuevo
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual; se usa para volver a guardar el contexto de seguridad en la sesión
     * @return redirección al perfil
     */
    @PostMapping("/perfil/telefono")
    public String actualizarTelefonoRm(@RequestParam(value = "telefono", required = false) String telefono,
                                        @AuthenticationPrincipal UsuarioDetails principal,
                                        RedirectAttributes redirectAttributes, HttpServletRequest request) {
        try {
            rmPerfilService.actualizarTelefono(telefono, principal.getUsuario());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "Teléfono actualizado correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", mensajeSeguro(e));
            redirectAttributes.addFlashAttribute("telefonoIngresado", telefono);
        }
        return "redirect:/rm/perfil";
    }

    /**
     * Cambia la foto de perfil del RM ({@link RmPerfilService#actualizarFoto}).
     *
     * @param foto imagen subida
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual; se usa para volver a guardar el contexto de seguridad en la sesión
     * @return redirección al perfil
     */
    @PostMapping("/perfil/foto")
    public String actualizarFotoRm(@RequestParam("foto") org.springframework.web.multipart.MultipartFile foto,
                                    @AuthenticationPrincipal UsuarioDetails principal,
                                    RedirectAttributes redirectAttributes, HttpServletRequest request) {
        try {
            rmPerfilService.actualizarFoto(foto, principal.getUsuario());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "Foto de perfil actualizada.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return "redirect:/rm/perfil";
    }

    /**
     * Cambia la contraseña del RM ({@link RmPerfilService#actualizarPassword}). Antes comprueba que la nueva y
     * su confirmación coincidan.
     *
     * @param passwordActual contraseña actual
     * @param passwordNueva contraseña nueva
     * @param passwordConfirm confirmación de la contraseña nueva
     * @param principal RM con la sesión iniciada
     * @param redirectAttributes mensajes flash que se muestran tras redirigir
     * @param request petición actual; se usa para volver a guardar el contexto de seguridad en la sesión
     * @return redirección al perfil
     */
    @PostMapping("/perfil/password")
    public String actualizarPasswordRm(@RequestParam("passwordActual") String passwordActual,
                                        @RequestParam("passwordNueva") String passwordNueva,
                                        @RequestParam("passwordConfirm") String passwordConfirm,
                                        @AuthenticationPrincipal UsuarioDetails principal,
                                        RedirectAttributes redirectAttributes, HttpServletRequest request) {
        try {
            if (!passwordNueva.equals(passwordConfirm)) {
                throw new IllegalArgumentException("Las contraseñas nuevas no coinciden.");
            }
            rmPerfilService.actualizarPassword(passwordActual, passwordNueva, principal.getUsuario());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "Contraseña actualizada correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensajeError", mensajeSeguro(e));
        }
        return "redirect:/rm/perfil";
    }
}
