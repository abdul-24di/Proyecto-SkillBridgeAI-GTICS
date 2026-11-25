package com.pucp.skillb_ia.controller;

import com.pucp.skillb_ia.dto.ColDocumentoForm;
import com.pucp.skillb_ia.dto.ColEntregaActividadForm;
import com.pucp.skillb_ia.dto.col.ColForoPublicacionForm;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.pm.PmForoService;
import com.pucp.skillb_ia.util.Periodo;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import com.pucp.skillb_ia.service.EvaluacionService;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Foro;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.NivelDominio;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.col.*;
import com.pucp.skillb_ia.util.Paginador;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Controller MVC de todas las pantallas del rol Colaborador (rutas bajo {@code /colaborador}).
 * <p>
 * Solo recibe la petición, obtiene el usuario autenticado, delega la lógica a los services
 * del paquete {@code service.col} y devuelve la vista Thymeleaf (carpeta {@code templates/col})
 * o una redirección. Pantallas que cubre: dashboard, proyectos y su detalle, foros, chat,
 * explorar, desempeño (con exportación a Excel y PDF), perfil y asistente.
 */
@Controller
@RequestMapping("/colaborador")
public class ColaboradorViewController {

    private final ColaboradorPerfilService colaboradorPerfilService;
    private final EvaluacionService evaluacionService;
    private final ColaboradorProyectoService colaboradorProyectoService;
    private final ColaboradorExplorarService colaboradorExplorarService;
    private final ColChatService colChatService;
    private final ColaboradorForoService colaboradorForoService;
    private final ColaboradorCursoService colaboradorCursoService;
    private final ColaboradorActividadService colaboradorActividadService;
    private final ColaboradorDocumentoService colaboradorDocumentoService;
    private final UsuarioRepository usuarioRepository;
    private final com.pucp.skillb_ia.service.col.ColDesempenoService colDesempenoService;

    /**
     * Inyecta por constructor todos los services y repositorios que usa el controller.
     *
     * @param colaboradorPerfilService perfil: datos, habilidades, certificados, educación
     * @param colaboradorProyectoService asignaciones y proyectos del colaborador
     * @param colaboradorExplorarService proyectos disponibles y perfiles de otros colaboradores
     * @param colaboradorCursoService catálogo, solicitudes y evidencias de cursos
     * @param colChatService chat del proyecto
     * @param colaboradorForoService foros, publicaciones y respuestas
     * @param colaboradorActividadService actividades, horas, bono y penalizaciones
     * @param colaboradorDocumentoService documentos del proyecto
     * @param usuarioRepository acceso a la tabla de usuarios
     * @param evaluacionService evaluaciones del colaborador
     * @param colDesempenoService datos de la pantalla de desempeño
     */
    public ColaboradorViewController(ColaboradorPerfilService colaboradorPerfilService,
                                     ColaboradorProyectoService colaboradorProyectoService,
                                     ColaboradorExplorarService colaboradorExplorarService,
                                     ColaboradorCursoService colaboradorCursoService,
                                     ColChatService colChatService,
                                     ColaboradorForoService colaboradorForoService,
                                     ColaboradorActividadService colaboradorActividadService,
                                     ColaboradorDocumentoService colaboradorDocumentoService,
                                     UsuarioRepository usuarioRepository,
                                     EvaluacionService evaluacionService,
                                     com.pucp.skillb_ia.service.col.ColDesempenoService colDesempenoService) {
        this.colDesempenoService = colDesempenoService;
        this.colaboradorPerfilService = colaboradorPerfilService;
        this.colaboradorProyectoService = colaboradorProyectoService;
        this.colaboradorExplorarService = colaboradorExplorarService;
        this.colaboradorCursoService = colaboradorCursoService;
        this.colChatService = colChatService;
        this.colaboradorForoService = colaboradorForoService;
        this.colaboradorActividadService = colaboradorActividadService;
        this.colaboradorDocumentoService = colaboradorDocumentoService;
        this.evaluacionService = evaluacionService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping({"", "/"})
    public String index() {
        return "redirect:/colaborador/dashboard";
    }

    /**
     * Muestra el dashboard del colaborador ({@code GET /colaborador/dashboard}).
     * <p>
     * Calcula y envía a la vista: proyectos activos y finalizados (contados desde sus
     * asignaciones), actividades pendientes, porcentaje de perfil completado, avance de cada
     * proyecto activo, resumen de horas del mes, penalizaciones, porcentaje y monto de
     * descuento del mes, y resumen del bono por horas extra.
     *
     * @param principal usuario autenticado; si es {@code null} se redirige al login
     * @param model modelo donde se cargan los datos para la vista
     * @return la vista {@code col/col-dashboard} o {@code redirect:/login}
     */
    @GetMapping({"/dashboard", "/col-dashboard.html"})
    public String dashboard(@AuthenticationPrincipal UsuarioDetails principal, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        Usuario colaborador = usuarioRepository.findById(principal.getUsuario().getId()).orElseThrow();

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
        model.addAttribute("resumenHoras", colaboradorActividadService.obtenerResumenHoras(colaborador));
        model.addAttribute("misPenalizaciones", colaboradorActividadService.listarMisPenalizaciones(colaborador));
        model.addAttribute("porcentajeDescuentoMes", colaboradorActividadService.obtenerPorcentajeDescuentoMesActual(colaborador));
        model.addAttribute("montoDescuentoMes", colaboradorActividadService.obtenerMontoDescuentoMesActual(colaborador));
        model.addAttribute("resumenBono", colaboradorActividadService.obtenerResumenBonoMensual(colaborador));

        return "col/col-dashboard";
    }

    /**
     * Lista los proyectos del colaborador con búsqueda, filtro por estado y paginación
     * ({@code GET /colaborador/proyectos}).
     * <p>
     * El filtrado y la paginación se hacen en el servidor, con 6 tarjetas por página.
     * La búsqueda compara contra nombre y descripción del proyecto.
     *
     * @param principal usuario autenticado
     * @param busqueda texto a buscar (opcional)
     * @param estado estado de la asignación a filtrar (opcional; vacío equivale a "Todos")
     * @param pagina número de página (opcional)
     * @param model modelo para la vista
     * @return la vista {@code col/col-proyectos} o {@code redirect:/login}
     */
    @GetMapping({"/proyectos", "/col-proyectos.html"})
    public String projects(@AuthenticationPrincipal UsuarioDetails principal,
                           @RequestParam(required = false) String busqueda,
                           @RequestParam(required = false) String estado,
                           @RequestParam(required = false) Integer pagina,
                           Model model) {
        if (principal == null) {
            return "redirect:/login";
        }

        // Filtro y paginación en el servidor (antes en col-proyectos.js), 6 tarjetas por página.
        List<Asignacion> todas = colaboradorProyectoService.listarMisAsignaciones(principal.getUsuario());
        List<Asignacion> filtradas = todas.stream()
                .filter(a -> Paginador.coincide(a.getEstado().name(), estado))
                .filter(a -> Paginador.contiene(a.getProyecto().getNombre() + " " +
                        (a.getProyecto().getDescripcion() != null ? a.getProyecto().getDescripcion() : ""), busqueda))
                .toList();

        Page<Asignacion> paginaProyectos = Paginador.paginar(filtradas, pagina, 6);
        model.addAttribute("paginaProyectos", paginaProyectos);
        model.addAttribute("misAsignaciones", paginaProyectos.getContent());
        model.addAttribute("totalAsignaciones", todas.size());
        model.addAttribute("busqueda", busqueda);
        model.addAttribute("estadoFiltro", estado == null || estado.isBlank() ? "Todos" : estado);
        return "col/col-proyectos";
    }



    @GetMapping({"/proyectos/detalle", "/col-detalle-proyecto.html"})
    public String projectDetail(@AuthenticationPrincipal UsuarioDetails principal,
                                @RequestParam(required = false) Long asignacionId,
                                @RequestParam(required = false) String categoriaDoc,
                                @RequestParam(required = false) String busquedaDoc,
                                @RequestParam(required = false) String paginaTodas,
                                @RequestParam(required = false) String paginaMias,
                                Model model,
                                RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";

        if (asignacionId == null) {
            redirectAttributes.addFlashAttribute("mensajeError", "No se indicó qué proyecto quieres ver.");
            return "redirect:/colaborador/proyectos";
        }

        try {
            Usuario colaborador = principal.getUsuario();
            model.addAttribute("colaborador", colaborador);
            if (!model.containsAttribute("colEntregaForm")) {
                model.addAttribute("colEntregaForm", new ColEntregaActividadForm());
            }
            if (!model.containsAttribute("colDocumentoForm")) {
                model.addAttribute("colDocumentoForm", new ColDocumentoForm());
            }
            var detalle = colaboradorProyectoService.obtenerDetalleProyecto(colaborador, asignacionId, paginaTodas, paginaMias);
            model.addAttribute("proyecto", detalle.getProyecto());
            model.addAttribute("asignacion", detalle.getAsignacion());
            model.addAttribute("integrantes", detalle.getIntegrantes());
            model.addAttribute("actividadesDelProyecto", detalle.getActividadesDelProyecto());
            model.addAttribute("misActividades", detalle.getMisActividades());
            model.addAttribute("perfilesIntegrantes", detalle.getPerfilesIntegrantes());
            model.addAttribute("misStrikes", detalle.getMisStrikes());
            model.addAttribute("paginaActualTodas", detalle.getPaginaActualTodas());
            model.addAttribute("totalPaginasTodas", detalle.getTotalPaginasTodas());
            model.addAttribute("totalActividadesProyecto", detalle.getTotalActividadesProyecto());
            model.addAttribute("paginaActualMias", detalle.getPaginaActualMias());
            model.addAttribute("totalPaginasMias", detalle.getTotalPaginasMias());
            model.addAttribute("totalMisActividades", detalle.getTotalMisActividades());
            //Pestaña Documentos
            model.addAttribute("documentos", colaboradorDocumentoService.listarDocumentos(
                    colaborador, detalle.getProyecto().getId(), categoriaDoc, busquedaDoc));
            model.addAttribute("filtroCategoriaDoc", categoriaDoc == null ? "" : categoriaDoc);
            model.addAttribute("filtroBusquedaDoc", busquedaDoc == null ? "" : busquedaDoc);

            //Pestaña Foro, solo de un proyecto en especifico, no todos los foros
            Optional<Foro> foroOpt = colaboradorForoService.obtenerForoDeProyecto(detalle.getProyecto());
            if (foroOpt.isPresent()) {
                model.addAttribute("foroDelProyecto", foroOpt.get());
                model.addAttribute("publicacionesForoProyecto",
                        colaboradorForoService.listarPublicacionesConRespuestas(colaborador, foroOpt.get().getId(), "fecha"));
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
            return "redirect:/colaborador/proyectos";
        }

        return "col/col-detalle-proyecto";
    }

    @PostMapping("/proyectos/documentos/subir")
    public String subirDocumentoProyecto(@AuthenticationPrincipal UsuarioDetails principal,
                                         @RequestParam Long proyectoId,
                                         @RequestParam Long asignacionId,
                                         @ModelAttribute("colDocumentoForm") ColDocumentoForm form,
                                         BindingResult result,
                                         RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        String destino = "redirect:/colaborador/proyectos/detalle?asignacionId=" + asignacionId + "&tab=documentos";

        String errorArchivo = colaboradorDocumentoService.validarArchivo(form.getArchivo());
        if (errorArchivo != null) {
            result.addError(new FieldError("colDocumentoForm", "archivo", errorArchivo));
        }
        if (result.hasErrors()) {
            form.setArchivo(null);
            redirectAttributes.addFlashAttribute("colDocumentoForm", form);
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.colDocumentoForm", result);
            redirectAttributes.addFlashAttribute("abrirModalSubirDocumento", true);
            return destino;
        }

        try {
            colaboradorDocumentoService.subirDocumento(principal.getUsuario(), proyectoId, form.getArchivo());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "Documento subido correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorDocumento", GlobalExceptionHandler.mensajeSeguro(e));
            redirectAttributes.addFlashAttribute("abrirModalSubirDocumento", true);
        }
        return destino;
    }

    @PostMapping("/proyectos/documentos/{documentoId}/eliminar")
    public String eliminarDocumentoProyecto(@AuthenticationPrincipal UsuarioDetails principal,
                                            @PathVariable Long documentoId,
                                            @RequestParam Long asignacionId,
                                            RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorDocumentoService.eliminarDocumento(principal.getUsuario(), documentoId);
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "Documento eliminado.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorEliminarDoc", GlobalExceptionHandler.mensajeSeguro(e));
            redirectAttributes.addFlashAttribute("abrirModalEliminarDocId", documentoId);
        }
        return "redirect:/colaborador/proyectos/detalle?asignacionId=" + asignacionId + "&tab=documentos";
    }

    @GetMapping({"/chat", "/col-chat.html"})
    public String chat(@RequestParam(value = "proyectoId", required = false) Long proyectoId,
                       @RequestParam(value = "usuarioId", required = false) Long usuarioId,
                       @AuthenticationPrincipal UsuarioDetails principal,
                       Model model) {
        Usuario colaborador = principal.getUsuario();
        model.addAttribute("colaborador", colaborador);
        model.addAttribute("proyectos", colChatService.listarProyectosActivos(colaborador));
        model.addAttribute("proyectoIdSeleccionado", proyectoId);
        model.addAttribute("usuarioIdSeleccionado", usuarioId);

        return "col/col-chat";
    }




    @GetMapping({"/foros", "/col-foros.html"})
    public String foros(@AuthenticationPrincipal UsuarioDetails principal,
                        @RequestParam(required = false) String busqueda,
                        @RequestParam(required = false) String tab,
                        @RequestParam(required = false) Integer paginaComunidad,
                        @RequestParam(required = false) Integer paginaMios,
                        Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        Usuario colaborador = principal.getUsuario();
        model.addAttribute("colaborador", colaborador);

        // Búsqueda y paginación en el servidor (antes en JS), 6 foros por página en cada pestaña.
        List<Foro> comunidad = colaboradorForoService.listarForosComunidad().stream()
                .filter(f -> Paginador.contiene(f.getNombre() + " " + (f.getProyecto() != null ? f.getProyecto().getNombre() : ""), busqueda))
                .toList();
        List<Foro> mios = colaboradorForoService.listarForosDeMisProyectos(colaborador).stream()
                .filter(f -> Paginador.contiene(f.getNombre() + " " + (f.getProyecto() != null ? f.getProyecto().getNombre() : ""), busqueda))
                .toList();
        Page<Foro> pagComunidad = Paginador.paginar(comunidad, paginaComunidad, 6);
        Page<Foro> pagMios = Paginador.paginar(mios, paginaMios, 6);
        model.addAttribute("paginaComunidad", pagComunidad);
        model.addAttribute("paginaMios", pagMios);
        model.addAttribute("forosComunidad", pagComunidad.getContent());
        model.addAttribute("forosMisProyectos", pagMios.getContent());
        model.addAttribute("busqueda", busqueda);
        model.addAttribute("tab", "mios".equals(tab) ? "mios" : "comunidad");
        model.addAttribute("forosParaPublicar", colaboradorForoService.listarForosParaPublicar(colaborador));
        model.addAttribute("etiquetas", colaboradorForoService.listarEtiquetas());
        if (!model.containsAttribute("colForoPublicacionForm")) {
            model.addAttribute("colForoPublicacionForm", new ColForoPublicacionForm());
        }
        return "col/col-foros";
    }

    @GetMapping({"/foros/detalle", "/col-foro-detalle.html"})
    public String foroDetalle(@RequestParam("foroId") Long foroId,
                              @RequestParam(value = "ordenar", defaultValue = "fecha") String ordenar,
                              @AuthenticationPrincipal UsuarioDetails principal, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        Usuario colaborador = principal.getUsuario();
        try {
            Foro foro = colaboradorForoService.obtenerForo(colaborador, foroId);
            model.addAttribute("colaborador", colaborador);
            model.addAttribute("foro", foro);
            String orden = com.pucp.skillb_ia.service.ForoDetalleService.normalizarOrden(ordenar);
            model.addAttribute("ordenActual", orden);
            model.addAttribute("publicaciones", colaboradorForoService.listarPublicacionesConRespuestas(colaborador, foroId, orden));
            model.addAttribute("etiquetas", colaboradorForoService.listarEtiquetas());
            model.addAttribute("puedeParticipar", colaboradorForoService.puedeParticipar(colaborador, foro));
            if (!model.containsAttribute("colForoPublicacionForm")) {
                model.addAttribute("colForoPublicacionForm", new ColForoPublicacionForm());
            }
            return "col/col-foro-detalle";
        } catch (IllegalArgumentException e) {
            return "redirect:/colaborador/foros";
        }
    }

    // Si hay errores se vuelve a la página desde donde se publicó (el listado de foros o el detalle del
    // foro), con el modal abierto y lo escrito conservado; antes iba siempre al detalle del foro elegido.
    @PostMapping("/foro/publicar")
    public String publicarForo(@Valid @ModelAttribute("colForoPublicacionForm") ColForoPublicacionForm form,
                               BindingResult result,
                               @AuthenticationPrincipal UsuarioDetails principal,
                               RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        // Un editor con solo espacios o etiquetas vacías (<p><br></p>) no es contenido.
        if (!result.hasFieldErrors("contenido") && PmForoService.contenidoForoVacio(form.getContenido())) {
            result.rejectValue("contenido", "contenido.vacio", "El contenido de la publicación es obligatorio.");
        }
        if (!result.hasErrors()) {
            try {
                colaboradorForoService.crearPublicacion(principal.getUsuario(), form.getForoId(), form.getTitulo(),
                        form.getContenido(), form.getEtiquetaId());
                request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
                redirectAttributes.addFlashAttribute("mensajeExito", "Publicación creada correctamente.");
                redirectAttributes.addAttribute("foroId", form.getForoId());
                return "redirect:/colaborador/foros/detalle";
            } catch (IllegalArgumentException e) {
                // Por ejemplo, un foro donde ya no puede participar: el error va al selector del foro.
                result.rejectValue("foroId", "foro.invalido", e.getMessage());
            }
        }
        redirectAttributes.addFlashAttribute("colForoPublicacionForm", form);
        redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.colForoPublicacionForm", result);
        redirectAttributes.addFlashAttribute("abrirModalNuevaPublicacion", true);
        if ("detalle".equals(form.getOrigen()) && form.getForoId() != null) {
            redirectAttributes.addAttribute("foroId", form.getForoId());
            return "redirect:/colaborador/foros/detalle";
        }
        return "redirect:/colaborador/foros";
    }

    @PostMapping("/foro/publicacion/{publicacionId}/eliminar")
    public String eliminarPublicacionForo(@PathVariable Long publicacionId,
                                          @RequestParam("foroId") Long foroId,
                                          @AuthenticationPrincipal UsuarioDetails principal,
                                          RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorForoService.eliminarPublicacion(principal.getUsuario(), publicacionId);
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
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
                                RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorForoService.crearRespuesta(principal.getUsuario(), publicacionId, contenido);
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
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
                                        RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorForoService.eliminarRespuesta(principal.getUsuario(), respuestaId);
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
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
                                  RedirectAttributes redirectAttributes, HttpServletRequest request) {
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
                                RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorForoService.alternarLikeRespuesta(principal.getUsuario(), respuestaId);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/colaborador/foros/detalle?foroId=" + foroId;
    }

    @PostMapping("/foro/respuesta/{respuestaId}/marcar-solucion")
    public String marcarRespuestaComoSolucion(@PathVariable Long respuestaId,
                                              @RequestParam("foroId") Long foroId,
                                              @AuthenticationPrincipal UsuarioDetails principal,
                                              RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorForoService.alternarSolucion(principal.getUsuario(), respuestaId);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/colaborador/foros/detalle?foroId=" + foroId;
    }

    @PostMapping("/foro/upload-imagen")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<java.util.Map<String, String>> uploadImagenForo(
            @RequestParam("image") MultipartFile image) {
        try {
            String url = colaboradorForoService.subirImagenForo(image);
            return org.springframework.http.ResponseEntity.ok(java.util.Map.of("url", url));
        } catch (IllegalArgumentException e) {
            return org.springframework.http.ResponseEntity.badRequest()
                    .body(java.util.Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.internalServerError()
                    .body(java.util.Map.of("error", "No se pudo subir la imagen. Inténtalo de nuevo."));
        }
    }

    @GetMapping({"/asistente", "/col-asistente.html"})
    public String assistant() {
        return "col/col-asistente";
    }

    @GetMapping({"/explorar", "/col-explorar.html"})
    public String explore(@AuthenticationPrincipal UsuarioDetails principal,
                          @RequestParam(required = false) String panel,
                          @RequestParam(required = false) String busqueda,
                          @RequestParam(required = false) String estadoProyecto,
                          @RequestParam(required = false) String postulaciones,
                          @RequestParam(required = false) Integer pagina,
                          @RequestParam(required = false) String busquedaColab,
                          @RequestParam(required = false) String nivel,
                          @RequestParam(required = false) Integer paginaColab,
                          @RequestParam(required = false) String busquedaCurso,
                          @RequestParam(required = false) String categoriaCurso,
                          @RequestParam(required = false) Integer paginaCurso,
                          Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        Usuario colaborador = principal.getUsuario();
        // Pestaña activa y filtros en el servidor (antes los manejaba col-explorar.js); 6 por página.
        String panelActivo = "colaboradores".equals(panel) || "cursos".equals(panel) ? panel : "proyectos";
        model.addAttribute("panel", panelActivo);

        // --- Proyectos ---
        List<com.pucp.skillb_ia.dto.ColProyectoDisponibleView> proyectos = colaboradorProyectoService.listarTodosLosProyectos(colaborador).stream()
                .filter(v -> Paginador.contiene(v.getProyecto().getNombre() + " " +
                        (v.getProyecto().getDescripcion() != null ? v.getProyecto().getDescripcion() : ""), busqueda))
                .filter(v -> Paginador.coincide(v.getProyecto().getEstado().name(), estadoProyecto))
                .filter(v -> Paginador.coincide(v.isPostulacionesAbiertas() ? "abiertas" : "cerradas", postulaciones))
                .toList();
        Page<com.pucp.skillb_ia.dto.ColProyectoDisponibleView> paginaProyectos = Paginador.paginar(proyectos, pagina, 6);
        model.addAttribute("paginaProyectos", paginaProyectos);
        model.addAttribute("proyectos", paginaProyectos.getContent());
        model.addAttribute("busqueda", busqueda);
        model.addAttribute("estadoProyecto", estadoProyecto);
        model.addAttribute("postulaciones", postulaciones);
        model.addAttribute("misSolicitudes", colaboradorProyectoService.listarMisSolicitudes(colaborador));


        // --- Cursos ---
        List<com.pucp.skillb_ia.dto.CursoDisponibleView> cursos = colaboradorCursoService.listarCursosDisponibles(colaborador).stream()
                .filter(v -> Paginador.contiene(v.getCurso().getNombre() + " " +
                        (v.getCurso().getDescripcion() != null ? v.getCurso().getDescripcion() : ""), busquedaCurso))
                .filter(v -> Paginador.coincideSinTildes(v.getCurso().getCategoria(), categoriaCurso))
                .toList();
        Page<com.pucp.skillb_ia.dto.CursoDisponibleView> paginaCursos = Paginador.paginar(cursos, paginaCurso, 6);
        model.addAttribute("paginaCursos", paginaCursos);
        model.addAttribute("cursosDisponibles", paginaCursos.getContent());
        model.addAttribute("busquedaCurso", busquedaCurso);
        model.addAttribute("categoriaCursoFiltro", categoriaCurso);
        model.addAttribute("misSolicitudesCursos", colaboradorCursoService.listarMisSolicitudes(colaborador));
        model.addAttribute("categoriasCurso", colaboradorCursoService.listarCategorias());

        // --- Colaboradores (busca por nombre, cargo o habilidades validadas) ---
        List<Usuario> todosColaboradores = colaboradorExplorarService.listarColaboradores(colaborador);
        var perfiles = colaboradorExplorarService.obtenerPerfiles(todosColaboradores);
        List<Usuario> colaboradoresFiltrados = todosColaboradores.stream()
                .filter(c -> Paginador.contiene(c.getNombre() + " " + c.getApellido() + " " +
                        (c.getCargo() != null ? c.getCargo().getNombre() : "") + " " +
                        (perfiles.get(c.getId()).getResumenHabilidades() != null ? perfiles.get(c.getId()).getResumenHabilidades() : ""), busquedaColab))
                .filter(c -> Paginador.coincide(c.getNivelExperiencia() != null ? c.getNivelExperiencia().name() : "", nivel))
                .toList();
        Page<Usuario> paginaColaboradores = Paginador.paginar(colaboradoresFiltrados, paginaColab, 6);
        model.addAttribute("paginaColaboradores", paginaColaboradores);
        model.addAttribute("colaboradoresExplorar", paginaColaboradores.getContent());
        model.addAttribute("totalColaboradores", todosColaboradores.size());
        model.addAttribute("perfilesExplorar", perfiles);
        model.addAttribute("busquedaColab", busquedaColab);
        model.addAttribute("nivelFiltro", nivel);

        return "col/col-explorar";
    }

    @PostMapping("/proyectos/solicitar")
    public String solicitarIncorporacion(@AuthenticationPrincipal UsuarioDetails principal,
                                         @RequestParam Long proyectoId,
                                         @RequestParam(required = false) Long habilidadId,
                                         @RequestParam(required = false) String mensaje,
                                         RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorProyectoService.solicitarIncorporacion(principal.getUsuario(), proyectoId, habilidadId, mensaje);
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "Tu solicitud fue enviada. Quedará pendiente de aprobación del PM y del RM.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/colaborador/explorar";
    }


    @PostMapping(value = "/proyectos/solicitar-ajax", produces = "text/plain;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<String> solicitarIncorporacionAjax(@AuthenticationPrincipal UsuarioDetails principal,
                                                             @RequestParam Long proyectoId,
                                                             @RequestParam(required = false) Long habilidadId,
                                                             @RequestParam(required = false) String mensaje,
                                                             HttpServletRequest request) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Tu sesión expiró. Vuelve a iniciar sesión.");
        }
        try {
            colaboradorProyectoService.solicitarIncorporacion(principal.getUsuario(), proyectoId, habilidadId, mensaje);
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            return ResponseEntity.ok("Tu solicitud fue enviada. Quedará pendiente de aprobación del PM y del RM.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/cursos/solicitar")
    public String solicitarCurso(@AuthenticationPrincipal UsuarioDetails principal,
                                 @RequestParam Long cursoId,
                                 @RequestParam(required = false) String justificacion,
                                 RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorCursoService.solicitarInscripcion(principal.getUsuario(), cursoId, justificacion);
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "Tu solicitud de inscripción fue enviada. Quedará pendiente de aprobación del Resource Manager.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/colaborador/explorar?panel=cursos";
    }

    // ============================================================
    // PERFIL
    // ============================================================

    // ============================================================
    // MI DESEMPEÑO (reporte del colaborador, por mes)
    // ============================================================
    // El mes pedido no puede ser anterior al mes en que se activó el perfil.
    private YearMonth periodoValido(Usuario colaborador, String mes) {
        YearMonth periodo = Periodo.leer(mes);
        YearMonth primerMes = YearMonth.from(colaborador.getFechaCreacion());
        if (periodo.isBefore(primerMes)) {
            return primerMes;
        }
        return periodo;
    }

    @GetMapping({"/desempeno", "/col-desempeno.html"})
    public String desempeno(@AuthenticationPrincipal UsuarioDetails principal,
                            @RequestParam(required = false) String mes, Model model) {
        if (principal == null) return "redirect:/login";
        Usuario colaborador = usuarioRepository.findById(principal.getUsuario().getId()).orElseThrow();
        java.time.YearMonth periodo = periodoValido(colaborador, mes);
        model.addAttribute("colaborador", colaborador);
        model.addAttribute("mes", periodo.toString());
        model.addAttribute("mesMinimo", java.time.YearMonth.from(colaborador.getFechaCreacion()).toString());
        model.addAttribute("mesMaximo", java.time.YearMonth.now().toString());
        model.addAttribute("desempeno", colDesempenoService.obtener(colaborador, periodo));


        return "col/col-desempeno";
    }

    @GetMapping("/desempeno/excel")
    public org.springframework.http.ResponseEntity<byte[]> desempenoExcel(@AuthenticationPrincipal UsuarioDetails principal,
                                                                          @RequestParam(required = false) String mes) {
        Usuario colaborador = usuarioRepository.findById(principal.getUsuario().getId()).orElseThrow();
        java.time.YearMonth periodo = periodoValido(colaborador, mes);
        byte[] contenido = colDesempenoService.excel(colaborador, colDesempenoService.obtener(colaborador, periodo));
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"mi-desempeno-" + periodo + ".xlsx\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(com.pucp.skillb_ia.util.ReporteExcel.TIPO_CONTENIDO))
                .body(contenido);
    }

    @GetMapping("/desempeno/pdf")
    public org.springframework.http.ResponseEntity<byte[]> desempenoPdf(@AuthenticationPrincipal UsuarioDetails principal,
                                                                        @RequestParam(required = false) String mes) {
        Usuario colaborador = usuarioRepository.findById(principal.getUsuario().getId()).orElseThrow();
        java.time.YearMonth periodo = periodoValido(colaborador, mes);
        byte[] contenido = colDesempenoService.pdf(colaborador, colDesempenoService.obtener(colaborador, periodo));
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"mi-desempeno-" + periodo + ".pdf\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(contenido);
    }

    @GetMapping({"/perfil", "/col-perfil.html"})
    public String profile(@AuthenticationPrincipal UsuarioDetails principal, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        Usuario colaborador = usuarioRepository.findById(principal.getUsuario().getId()).orElseThrow();

        model.addAttribute("colaborador", colaborador);
        model.addAttribute("habilidadesColaborador", colaboradorPerfilService.listarHabilidades(colaborador));
        model.addAttribute("certificadosPorHabilidad", colaboradorPerfilService.mapaCertificadosPorHabilidad(colaborador));
        model.addAttribute("motivosRechazoPorHabilidad", colaboradorPerfilService.mapaMotivosRechazoPorHabilidad(colaborador));
        model.addAttribute("certificadosColaborador", colaboradorPerfilService.listarCertificados(colaborador));
        model.addAttribute("habilidadesDisponibles", colaboradorPerfilService.listarHabilidadesDisponibles(colaborador));
        model.addAttribute("nivelesDominio", NivelDominio.values());
        model.addAttribute("categoriasHabilidad", colaboradorPerfilService.listarCategoriasHabilidad());
        model.addAttribute("educacionColaborador", colaboradorPerfilService.listarEducacion(colaborador));
        model.addAttribute("porcentajeCompletado", colaboradorPerfilService.calcularPorcentajeCompletado(colaborador));
        model.addAttribute("pendientesCompletar", colaboradorPerfilService.listarPendientesCompletar(colaborador));
        model.addAttribute("experienciaProfesional", colaboradorPerfilService.listarExperienciaProfesional(colaborador));
        model.addAttribute("historialProyectos", colaboradorProyectoService.listarHistorialProyectos(colaborador));
        model.addAttribute("misCursosEnCurso", colaboradorCursoService.listarMisCursos(colaborador));
        model.addAttribute("evaluaciones", evaluacionService.obtenerEvaluacionesPorColaborador(colaborador.getId()));

        return "col/col-perfil";

    }

    @PostMapping("/perfil/cursos/{inscripcionId}/evidencia")
    public String subirEvidenciaCurso(@AuthenticationPrincipal UsuarioDetails principal,
                                      @PathVariable Long inscripcionId,
                                      @RequestParam("evidencia") MultipartFile evidencia,
                                      RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorCursoService.subirEvidencia(principal.getUsuario(), inscripcionId, evidencia);
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito",
                    "Tu evidencia fue enviada. Quedará pendiente de revisión del Resource Manager.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/colaborador/perfil";
    }

    @PostMapping("/perfil/sobre-mi")
    public String actualizarSobreMi(@AuthenticationPrincipal UsuarioDetails principal,
                                    @RequestParam String descripcion,
                                    RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorPerfilService.actualizarSobreMi(principal.getUsuario(), descripcion);
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "Tu descripción se actualizó correctamente.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/colaborador/perfil";
    }

    @PostMapping("/perfil/telefono")
    public String actualizarTelefono(@AuthenticationPrincipal UsuarioDetails principal,
                                     @RequestParam String telefono,
                                     RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";

        try {
            colaboradorPerfilService.actualizarTelefono(principal.getUsuario(), telefono);

            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "Tu teléfono se actualizó correctamente.");

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }

        return "redirect:/colaborador/perfil";

    }

    @PostMapping("/perfil/foto")
    public String actualizarFoto(@AuthenticationPrincipal UsuarioDetails principal,
                                 @RequestParam("foto") MultipartFile foto,
                                 RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorPerfilService.actualizarFoto(principal.getUsuario(), foto);
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "Tu foto de perfil se actualizó correctamente.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/colaborador/perfil?tab=cuenta";
    }

    @PostMapping("/perfil/cv")
    public String actualizarCv(@AuthenticationPrincipal UsuarioDetails principal,
                               @RequestParam("cv") MultipartFile cv,
                               RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorPerfilService.actualizarCv(principal.getUsuario(), cv);
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito",
                    "Tu CV se subió correctamente y quedó pendiente de revisión del Administrador.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/colaborador/perfil?tab=cuenta";
    }

    @PostMapping("/perfil/foto/eliminar")
    public String eliminarFoto(@AuthenticationPrincipal UsuarioDetails principal,
                               RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorPerfilService.eliminarFoto(principal.getUsuario());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
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
                                  RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorPerfilService.cambiarPassword(principal.getUsuario(), passwordActual, passwordNueva, passwordConfirmar);
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
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
                                   RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorPerfilService.agregarHabilidad(
                    principal.getUsuario(),
                    parseIdOpcional(habilidadId),
                    nuevaHabilidadNombre,
                    parseIdOpcional(categoriaId),
                    parseNivel(nivel),
                    certificado);
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
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
        return parseNivelOpcional(valor);
    }

    private NivelDominio parseNivelOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
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
                                    RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        colaboradorPerfilService.eliminarHabilidad(principal.getUsuario(), habilidadId);
        request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "Habilidad eliminada de tu perfil.");
        return "redirect:/colaborador/perfil";
    }

    @PostMapping("/perfil/certificados")
    public String subirCertificado(@AuthenticationPrincipal UsuarioDetails principal,
                                   @RequestParam Long habilidadId,
                                   @RequestParam(required = false) String nivel,
                                   @RequestParam("certificado") MultipartFile certificado,
                                   RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorPerfilService.subirCertificado(
                    principal.getUsuario(), habilidadId, parseNivelOpcional(nivel), certificado);
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
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
                                   @RequestParam(required = false) String fechaInicio,
                                   @RequestParam(required = false) String fechaFin,
                                   @RequestParam("certificado") MultipartFile certificado,
                                   RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorPerfilService.agregarEducacion(
                    principal.getUsuario(), institucion, titulo, fechaInicio, fechaFin, certificado);
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "Formación académica agregada. Quedará pendiente de revisión.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/colaborador/perfil";
    }

    @PostMapping("/perfil/educacion/{educacionId}/eliminar")
    public String eliminarEducacion(@AuthenticationPrincipal UsuarioDetails principal,
                                    @PathVariable Long educacionId,
                                    RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        try {
            colaboradorPerfilService.eliminarEducacion(principal.getUsuario(), educacionId);
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "Formación académica eliminada de tu perfil.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensajeError", e.getMessage());
        }
        return "redirect:/colaborador/perfil";
    }

    @PostMapping("/proyectos/actividades/{actividadId}/marcar-listo")
    public String marcarActividadListaParaRevision(@AuthenticationPrincipal UsuarioDetails principal,
                                                   @PathVariable Long actividadId,
                                                   @RequestParam Long asignacionId,
                                                   @Valid @ModelAttribute("colEntregaForm") ColEntregaActividadForm form,
                                                   BindingResult result,
                                                   RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (principal == null) return "redirect:/login";
        String destino = "redirect:/colaborador/proyectos/detalle?asignacionId=" + asignacionId + "&tab=actividades";

        String errorEvidencia = colaboradorProyectoService.validarEvidencia(form.getEvidencia());
        if (errorEvidencia != null) {
            result.addError(new FieldError("colEntregaForm", "evidencia", errorEvidencia));
        }

        if (result.hasErrors()) {
            form.setEvidencia(null);
            redirectAttributes.addFlashAttribute("colEntregaForm", form);
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.colEntregaForm", result);
            redirectAttributes.addFlashAttribute("abrirModalActividadId", actividadId);
            return destino;
        }

        try {
            colaboradorProyectoService.marcarActividadListaParaRevision(
                    principal.getUsuario(), actividadId, form.getEvidencia(), form.getComentario());
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT", SecurityContextHolder.getContext());
            redirectAttributes.addFlashAttribute("mensajeExito", "Actividad marcada como lista para revisión. El PM la revisará pronto.");
        } catch (IllegalArgumentException e) {
            form.setEvidencia(null);
            redirectAttributes.addFlashAttribute("colEntregaForm", form);
            redirectAttributes.addFlashAttribute("errorEntrega", e.getMessage());
            redirectAttributes.addFlashAttribute("abrirModalActividadId", actividadId);
        } catch (Exception e) {
            form.setEvidencia(null);
            redirectAttributes.addFlashAttribute("colEntregaForm", form);
            redirectAttributes.addFlashAttribute("errorEntrega", "No se pudo procesar la evidencia. Verifica el archivo e inténtalo de nuevo.");
            redirectAttributes.addFlashAttribute("abrirModalActividadId", actividadId);
        }
        return destino;
    }


}