package com.pucp.skillb_ia;

import com.pucp.skillb_ia.controller.RmViewController;
import com.pucp.skillb_ia.dto.RmAsignacionView;
import com.pucp.skillb_ia.dto.RmCertificadoView;
import com.pucp.skillb_ia.dto.RmProyectoView;
import com.pucp.skillb_ia.dto.RmSolicitudPersonalView;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.ColaboradorCurso;
import com.pucp.skillb_ia.model.Curso;
import com.pucp.skillb_ia.model.Educacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoCertificado;
import com.pucp.skillb_ia.model.enums.EstadoColaboradorCurso;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.model.enums.OrigenCurso;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ColaboradorCursoRepository;
import com.pucp.skillb_ia.repository.CursoRepository;
import com.pucp.skillb_ia.repository.EducacionRepository;
import com.pucp.skillb_ia.repository.NotificacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.rm.*;
import com.pucp.skillb_ia.service.EvaluacionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@ActiveProfiles("test")
class RmDashboardTests {
    @Autowired private WebApplicationContext context;
    @Autowired private EducacionRepository educacionRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private CursoRepository cursoRepository;
    @Autowired private ColaboradorCursoRepository colaboradorCursoRepository;
    @Autowired private NotificacionRepository notificacionRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private RmAsignacionService rmAsignacionService;
    private MockMvc mockMvc;

    @BeforeEach
    void prepararMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void dashboardRenderizaTodasLasSeccionesDinamicas() throws Exception {
        mockMvc.perform(get("/rm/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-dashboard"))
                .andExpect(model().attributeExists(
                        "totalAprobacionesRm", "totalEsperandoPm", "totalPostulaciones",
                        "totalProyectosVacantes", "totalProyectosRevision",
                        "accionesPendientes", "solicitudesRecientes", "proyectosAtencion",
                        "totalSolicitudesAbiertas", "totalCertificadosPendientes",
                        "totalEducacionPendiente", "totalCursosPendientes"));
    }

    @Test
    void dashboardCuentaSoloFormacionesPendientesYEnlazaALaBandeja() throws Exception {
        educacionRepository.deleteAll();
        Usuario colaborador = usuarioRepository.findByCorreo("col.dashboard.edu@skillbridge.test")
                .orElseGet(() -> {
                    Rol rol = rolRepository.findByNombre("COLABORADOR").orElseGet(() -> {
                        Rol nuevo = new Rol(); nuevo.setNombre("COLABORADOR"); return rolRepository.save(nuevo);
                    });
                    Usuario nuevo = new Usuario();
                    nuevo.setCorreo("col.dashboard.edu@skillbridge.test");
                    nuevo.setNombre("Dana");
                    nuevo.setApellido("Dashboard");
                    nuevo.setRol(rol);
                    return usuarioRepository.save(nuevo);
                });
        guardarEducacion(colaborador, EstadoCertificado.PENDIENTE, true);
        guardarEducacion(colaborador, EstadoCertificado.PENDIENTE, true);
        guardarEducacion(colaborador, EstadoCertificado.APROBADO, true);
        guardarEducacion(colaborador, EstadoCertificado.RECHAZADO, true);
        guardarEducacion(colaborador, EstadoCertificado.PENDIENTE, false);

        String html = mockMvc.perform(get("/rm/dashboard"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalEducacionPendiente", 2L))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Matcher tarjeta = Pattern.compile("href=\"([^\"]*)\">\\s*<span class=\"badge bg-yellow-lt mb-2\">Formación académica")
                .matcher(html);
        assertTrue(tarjeta.find(), "No se encontró la tarjeta de formación académica");
        assertEquals("/rm/colaboradores/educacion", tarjeta.group(1));
        assertTrue(html.contains("2 formación(es) pendiente(s)"));

        educacionRepository.deleteAll();
        String sinPendientes = mockMvc.perform(get("/rm/dashboard"))
                .andExpect(model().attribute("totalEducacionPendiente", 0L))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(sinPendientes.contains("formación(es) pendiente(s)"));
    }

    @Test
    void dashboardCuentaSoloSolicitudesDeCursoPendientesYEnlazaALaBandeja() throws Exception {
        notificacionRepository.deleteAll();
        colaboradorCursoRepository.deleteAll();
        Rol rolColaborador = rolRepository.findByNombre("COLABORADOR").orElseGet(() -> {
            Rol nuevo = new Rol(); nuevo.setNombre("COLABORADOR"); return rolRepository.save(nuevo);
        });
        Usuario colaborador = usuarioRepository.findByCorreo("col.dashboard.curso@skillbridge.test")
                .orElseGet(() -> {
                    Usuario nuevo = new Usuario();
                    nuevo.setCorreo("col.dashboard.curso@skillbridge.test");
                    nuevo.setNombre("Diego");
                    nuevo.setApellido("Curso");
                    nuevo.setRol(rolColaborador);
                    return usuarioRepository.save(nuevo);
                });
        Curso curso = new Curso();
        curso.setNombre("Curso dashboard test");
        curso.setHoras(new BigDecimal("10.00"));
        curso.setCreadoPor(colaborador);
        curso = cursoRepository.save(curso);
        guardarInscripcion(colaborador, curso, OrigenCurso.SOLICITUD_COLABORADOR, EstadoColaboradorCurso.SOLICITADO);
        guardarInscripcion(colaborador, curso, OrigenCurso.SOLICITUD_COLABORADOR, EstadoColaboradorCurso.SOLICITADO);
        guardarInscripcion(colaborador, curso, OrigenCurso.SOLICITUD_COLABORADOR, EstadoColaboradorCurso.EN_CURSO);
        guardarInscripcion(colaborador, curso, OrigenCurso.SOLICITUD_COLABORADOR, EstadoColaboradorCurso.RECHAZADO);
        guardarInscripcion(colaborador, curso, OrigenCurso.ASIGNADO_POR_RM, EstadoColaboradorCurso.EN_CURSO);

        String html = mockMvc.perform(get("/rm/dashboard"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalCursosPendientes", 2L))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Matcher tarjeta = Pattern.compile("href=\"([^\"]*)\">\\s*<span class=\"badge bg-info-lt mb-2\">Solicitudes de cursos")
                .matcher(html);
        assertTrue(tarjeta.find(), "No se encontró la tarjeta de solicitudes de cursos");
        assertEquals("/rm/cursos/solicitudes", tarjeta.group(1));
        assertTrue(html.contains("2 solicitud(es) de curso pendiente(s)"));
        assertTrue(Pattern.compile("href=\"/rm/cursos/solicitudes\"\\s+aria-label=\"Ver solicitudes de curso pendientes\"")
                .matcher(html).find(), "No se encontró la tarjeta resumen de solicitudes de curso");
        assertTrue(Pattern.compile("href=\"/rm/colaboradores/certificados\"\\s+aria-label=\"Ver certificados pendientes\"")
                .matcher(html).find(), "No se encontró la tarjeta resumen de certificados");
        assertTrue(html.contains("class=\"card-body attention-scroll\""));

        colaboradorCursoRepository.deleteAll();
        cursoRepository.delete(curso);
        String sinPendientes = mockMvc.perform(get("/rm/dashboard"))
                .andExpect(model().attribute("totalCursosPendientes", 0L))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(sinPendientes.contains("solicitud(es) de curso pendiente(s)"));
    }

    // TASK-033: el dashboard solo toma asignaciones de proyectos ACTIVO, EN_ESPERA y EN_REVISION.
    private static final Set<EstadoProyecto> ESTADOS_DASHBOARD =
            EnumSet.of(EstadoProyecto.ACTIVO, EstadoProyecto.EN_ESPERA, EstadoProyecto.EN_REVISION);
    // Orden intercalado: los proyectos excluidos quedan entre los incluidos por fecha de solicitud.
    private static final List<EstadoProyecto> ESTADOS_ESCENARIO = List.of(
            EstadoProyecto.ACTIVO, EstadoProyecto.CANCELADO, EstadoProyecto.EN_ESPERA,
            EstadoProyecto.FINALIZADO, EstadoProyecto.EN_REVISION, EstadoProyecto.RECHAZADO);

    private enum TipoPendiente { DECISION_RM, ESPERANDO_PM, POSTULACION }

    private record Escenario(List<Asignacion> asignaciones, Map<EstadoProyecto, Proyecto> proyectos) {
        List<Long> idsIncluidos() {
            return asignaciones.stream()
                    .filter(item -> ESTADOS_DASHBOARD.contains(item.getProyecto().getEstado()))
                    .map(Asignacion::getId)
                    .toList();
        }
    }

    private final List<Asignacion> asignacionesCreadas = new ArrayList<>();
    private final List<Proyecto> proyectosCreados = new ArrayList<>();

    // Los proyectos de estas pruebas no deben quedar en las demás clases.
    @AfterEach
    void limpiarProyectosDelDashboard() {
        asignacionRepository.deleteAll(asignacionesCreadas);
        proyectoRepository.deleteAll(proyectosCreados);
        asignacionesCreadas.clear();
        proyectosCreados.clear();
    }

    @ParameterizedTest
    @EnumSource(EstadoProyecto.class)
    void dashboardIncluyeOExcluyeLasAsignacionesSegunElEstadoDelProyecto(EstadoProyecto estado) throws Exception {
        asignacionRepository.deleteAll();
        Proyecto proyecto = guardarProyecto(estado);
        LocalDateTime base = LocalDateTime.now().minusHours(1);
        Asignacion decisionRm = guardarPendiente(proyecto, TipoPendiente.DECISION_RM, base);
        Asignacion esperandoPm = guardarPendiente(proyecto, TipoPendiente.ESPERANDO_PM, base.minusMinutes(1));
        Asignacion postulacion = guardarPendiente(proyecto, TipoPendiente.POSTULACION, base.minusMinutes(2));
        boolean incluido = ESTADOS_DASHBOARD.contains(estado);

        MvcResult resultado = mockMvc.perform(get("/rm/dashboard")).andExpect(status().isOk()).andReturn();
        Map<String, Object> modelo = resultado.getModelAndView().getModel();
        String html = resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertEquals(incluido ? 2L : 0L, modelo.get("totalAprobacionesRm"));
        assertEquals(incluido ? 1L : 0L, modelo.get("totalEsperandoPm"));
        assertEquals(incluido ? 1L : 0L, modelo.get("totalPostulaciones"));
        assertEquals(incluido ? 3 : 0, modelo.get("totalAccionesPendientes"));
        assertEquals(incluido ? List.of(decisionRm.getId(), esperandoPm.getId(), postulacion.getId()) : List.of(),
                idsDeVistas(modelo.get("accionesPendientes")));
        assertEquals(incluido, html.contains("href=\"/rm/asignaciones/revision?id=" + decisionRm.getId() + "\""));
        assertEquals(incluido, html.contains("href=\"/rm/asignaciones/pendiente-pm?id=" + esperandoPm.getId() + "\""));
        assertEquals(incluido, html.contains(
                "href=\"/rm/asignaciones/revision-postulacion?id=" + postulacion.getId() + "\""));
    }

    @Test
    void conteosYTablaResumidaUsanElMismoConjuntoFiltradoYConservanElOrden() throws Exception {
        Escenario escenario = crearEscenarioConTodosLosEstados();
        List<RmAsignacionView> conjunto = rmAsignacionService.listarParaDashboard();
        assertEquals(escenario.idsIncluidos(), idsDeVistas(conjunto));
        assertEquals(ESTADOS_DASHBOARD, conjunto.stream()
                .map(item -> item.getAsignacion().getProyecto().getEstado())
                .collect(Collectors.toSet()));
        List<Long> pendientes = conjunto.stream()
                .filter(item -> item.isRequiereDecisionRm() || item.isPendientePm())
                .map(item -> item.getAsignacion().getId())
                .toList();

        MvcResult resultado = mockMvc.perform(get("/rm/dashboard")).andExpect(status().isOk()).andReturn();
        Map<String, Object> modelo = resultado.getModelAndView().getModel();
        String html = resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertEquals(conjunto.stream().filter(RmAsignacionView::isRequiereDecisionRm).count(),
                modelo.get("totalAprobacionesRm"));
        assertEquals(conjunto.stream().filter(RmAsignacionView::isPendientePm).count(),
                modelo.get("totalEsperandoPm"));
        assertEquals(conjunto.stream().filter(RmAsignacionView::isSolicitudColaborador)
                .filter(RmAsignacionView::isRequiereDecisionRm).count(), modelo.get("totalPostulaciones"));
        assertEquals(6L, modelo.get("totalAprobacionesRm"));
        assertEquals(3L, modelo.get("totalEsperandoPm"));
        assertEquals(3L, modelo.get("totalPostulaciones"));
        assertEquals(pendientes.size(), modelo.get("totalAccionesPendientes"));
        assertEquals(9, modelo.get("totalAccionesPendientes"));
        // Acciones pendientes = aprobaciones RM + esperando PM, sin contradicciones.
        assertEquals((long) (Integer) modelo.get("totalAccionesPendientes"),
                (Long) modelo.get("totalAprobacionesRm") + (Long) modelo.get("totalEsperandoPm"));
        assertEquals(pendientes.subList(0, 4), idsDeVistas(modelo.get("accionesPendientes")));
        assertTrue(html.contains("Mostrando 4 de 9 acciones pendientes"));
    }

    @Test
    void cadaResumenMuestraComoMaximoCincoFilasYConservaSuEnlaceALaListaCompleta() throws Exception {
        crearEscenarioConTodosLosEstados();

        MvcResult resultado = mockMvc.perform(get("/rm/dashboard")).andExpect(status().isOk()).andReturn();
        Map<String, Object> modelo = resultado.getModelAndView().getModel();
        String html = resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);

        for (String resumen : List.of("accionesPendientes", "solicitudesRecientes", "proyectosAtencion")) {
            assertTrue(((List<?>) modelo.get(resumen)).size() <= 5, resumen + " supera 5 filas");
        }
        assertEquals(4, ((List<?>) modelo.get("accionesPendientes")).size());
        assertTrue(tieneEnlaceDeSeccion(html, "/rm/asignaciones"), "Falta el enlace a la bandeja de asignaciones");
        assertTrue(tieneEnlaceDeSeccion(html, "/rm/asignaciones/solicitudes-colaboradores"),
                "Falta el enlace a las solicitudes de personal");
        assertTrue(tieneEnlaceDeSeccion(html, "/rm/proyectos"), "Falta el enlace a los proyectos");
        // Aprobaciones RM, Esperando PM y Postulaciones enlazan a la bandeja completa.
        Matcher tarjetas = Pattern.compile("class=\"stretched-link\" href=\"/rm/asignaciones\"").matcher(html);
        int totalTarjetas = 0;
        while (tarjetas.find()) totalTarjetas++;
        assertEquals(3, totalTarjetas);
    }

    @Test
    void bandejaGeneralSigueIncluyendoAsignacionesDeProyectosCerrados() {
        Escenario escenario = crearEscenarioConTodosLosEstados();

        assertEquals(escenario.asignaciones().stream().map(Asignacion::getId).toList(),
                idsDeVistas(rmAsignacionService.listar()));
        RmAsignacionService.ContadoresAsignaciones contadores = rmAsignacionService.listarPagina(
                rmAsignacionService.normalizarFiltros(null, null, null, null, null), null).contadores();
        assertEquals(12L, contadores.pendientesRm());
        assertEquals(6L, contadores.pendientesPm());
        assertEquals(6L, contadores.solicitudesColaborador());

        Proyecto cancelado = escenario.proyectos().get(EstadoProyecto.CANCELADO);
        RmAsignacionService.PaginaAsignaciones pagina = rmAsignacionService.listarPagina(
                rmAsignacionService.normalizarFiltros(null, null, null, null, cancelado.getId().toString()), null);
        assertEquals(2L, pagina.contadores().pendientesRm());
        assertEquals(1L, pagina.contadores().pendientesPm());
    }

    @Test
    void enRevisionApareceParaSeguimientoPeroSigueSinSerAsignable() {
        asignacionRepository.deleteAll();
        Proyecto enRevision = guardarProyecto(EstadoProyecto.EN_REVISION);
        Asignacion propuestaPm = guardarPendiente(enRevision, TipoPendiente.DECISION_RM, LocalDateTime.now());
        Usuario rm = usuarioDePrueba("rm.dashboard.t033@skillbridge.test", "Rita", "RESOURCE_MANAGER");

        assertTrue(idsDeVistas(rmAsignacionService.listarParaDashboard()).contains(propuestaPm.getId()));
        assertFalse(rmAsignacionService.esProyectoAsignable(enRevision));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> rmAsignacionService.aprobar(propuestaPm.getId(), null, rm.getId()));
        assertEquals("Solo se pueden proponer asignaciones para proyectos activos o en espera.", error.getMessage());
        Asignacion sinCambios = asignacionRepository.findById(propuestaPm.getId()).orElseThrow();
        assertEquals(EstadoAsignacion.PENDIENTE, sinCambios.getEstado());
        assertFalse(sinCambios.isAprobadoPorRm());
    }

    // Un proyecto por estado y, en cada uno, una pendiente de cada tipo (18 en total, 9 incluidas).
    private Escenario crearEscenarioConTodosLosEstados() {
        asignacionRepository.deleteAll();
        Map<EstadoProyecto, Proyecto> proyectos = new EnumMap<>(EstadoProyecto.class);
        ESTADOS_ESCENARIO.forEach(estado -> proyectos.put(estado, guardarProyecto(estado)));
        List<Asignacion> asignaciones = new ArrayList<>();
        LocalDateTime fecha = LocalDateTime.now().minusHours(1);
        for (TipoPendiente tipo : TipoPendiente.values()) {
            for (EstadoProyecto estado : ESTADOS_ESCENARIO) {
                asignaciones.add(guardarPendiente(proyectos.get(estado), tipo, fecha));
                fecha = fecha.minusMinutes(1);
            }
        }
        return new Escenario(asignaciones, proyectos);
    }

    private Proyecto guardarProyecto(EstadoProyecto estado) {
        Proyecto proyecto = new Proyecto();
        proyecto.setNombre("Proyecto dashboard T033 " + estado + " " + System.nanoTime());
        proyecto.setDescripcion("Proyecto para probar el filtro de asignaciones del dashboard.");
        proyecto.setEstado(estado);
        proyecto.setPrioridad(Prioridad.MEDIA);
        proyecto.setJustificacionPrioridad("Prueba del dashboard RM.");
        proyecto.setColaboradoresRequeridos(3);
        proyecto.setPm(usuarioDePrueba("pm.dashboard.t033@skillbridge.test", "Paula", "PROJECT_MANAGER"));
        proyecto.setPresupuesto(new BigDecimal("50000.00"));
        proyecto.setFechaInicio(LocalDate.now());
        proyecto.setFechaFinEstimada(LocalDate.now().plusMonths(3));
        proyecto = proyectoRepository.save(proyecto);
        proyectosCreados.add(proyecto);
        return proyecto;
    }

    // Un colaborador por tipo: la unicidad de asignaciones abiertas es por colaborador y proyecto.
    private Asignacion guardarPendiente(Proyecto proyecto, TipoPendiente tipo, LocalDateTime fechaSolicitud) {
        Asignacion asignacion = new Asignacion();
        asignacion.setProyecto(proyecto);
        asignacion.setColaborador(usuarioDePrueba(
                "col.dashboard.t033." + tipo.name().toLowerCase() + "@skillbridge.test", "Colab", "COLABORADOR"));
        asignacion.setHorasSemanales(new BigDecimal("8"));
        asignacion.setEstado(EstadoAsignacion.PENDIENTE);
        switch (tipo) {
            case DECISION_RM -> {
                asignacion.setOrigen(OrigenAsignacion.PROPUESTA_PM);
                asignacion.setAprobadoPorPm(true);
            }
            case ESPERANDO_PM -> {
                asignacion.setOrigen(OrigenAsignacion.PROPUESTA_RM);
                asignacion.setAprobadoPorRm(true);
            }
            case POSTULACION -> {
                asignacion.setOrigen(OrigenAsignacion.SOLICITADA_COLABORADOR);
                asignacion.setAprobadoPorPm(true);
            }
        }
        asignacion.setMensajeSolicitud("Solicitud de prueba del dashboard.");
        asignacion.setFechaSolicitud(fechaSolicitud);
        asignacion = asignacionRepository.save(asignacion);
        asignacionesCreadas.add(asignacion);
        return asignacion;
    }

    private Usuario usuarioDePrueba(String correo, String nombre, String rolNombre) {
        Rol rol = rolRepository.findByNombre(rolNombre).orElseGet(() -> {
            Rol nuevo = new Rol(); nuevo.setNombre(rolNombre); return rolRepository.save(nuevo);
        });
        return usuarioRepository.findByCorreo(correo).orElseGet(() -> {
            Usuario nuevo = new Usuario();
            nuevo.setCorreo(correo);
            nuevo.setNombre(nombre);
            nuevo.setApellido("Dashboard");
            nuevo.setRol(rol);
            nuevo.setHorasDisponibles(new BigDecimal("40"));
            nuevo.setHorasContratadasSemana(new BigDecimal("40"));
            return usuarioRepository.save(nuevo);
        });
    }

    @SuppressWarnings("unchecked")
    private List<Long> idsDeVistas(Object vistas) {
        return ((List<RmAsignacionView>) vistas).stream().map(item -> item.getAsignacion().getId()).toList();
    }

    private boolean tieneEnlaceDeSeccion(String html, String url) {
        Matcher enlace = Pattern.compile("<a\\b[^>]*>").matcher(html);
        while (enlace.find()) {
            String etiqueta = enlace.group();
            if (etiqueta.contains("section-action") && etiqueta.contains("href=\"" + url + "\"")) return true;
        }
        return false;
    }

    private void guardarInscripcion(Usuario colaborador, Curso curso, OrigenCurso origen,
                                    EstadoColaboradorCurso estado) {
        ColaboradorCurso inscripcion = new ColaboradorCurso();
        inscripcion.setColaborador(colaborador);
        inscripcion.setCurso(curso);
        inscripcion.setOrigen(origen);
        inscripcion.setEstado(estado);
        colaboradorCursoRepository.save(inscripcion);
    }

    private void guardarEducacion(Usuario colaborador, EstadoCertificado estado, boolean activo) {
        Educacion educacion = new Educacion();
        educacion.setColaborador(colaborador);
        educacion.setTitulo("Formación dashboard");
        educacion.setInstitucion("PUCP");
        educacion.setArchivoUrl("/uploads/certificados-educacion/dashboard.pdf");
        educacion.setEstado(estado);
        educacion.setActivo(activo);
        educacionRepository.save(educacion);
    }

    @Test
    void dashboardCalculaMetricasYPriorizaDatosReales() {
        RmPerfilService perfilService = mock(RmPerfilService.class);
        RmColaboradorConsultaService colaboradorService = mock(RmColaboradorConsultaService.class);
        RmProyectoConsultaService proyectoService = mock(RmProyectoConsultaService.class);
        RmProyectoRevisionService revisionService = mock(RmProyectoRevisionService.class);
        RmAsignacionService asignacionService = mock(RmAsignacionService.class);
        RmSolicitudPersonalService solicitudService = mock(RmSolicitudPersonalService.class);
        RmCertificadoService certificadoService = mock(RmCertificadoService.class);
        RmEducacionService educacionService = mock(RmEducacionService.class);
        RmForoConsultaService foroService = mock(RmForoConsultaService.class);
        RmReporteService reporteService = mock(RmReporteService.class);
        RmReporteExportService reporteExportService = mock(RmReporteExportService.class);
        RmCursoService cursoService = mock(RmCursoService.class);
        RmPresupuestoService presupuestoService = mock(RmPresupuestoService.class);
        EvaluacionService evaluacionService = mock(EvaluacionService.class);

        RmAsignacionView postulacion = asignacion(
                OrigenAsignacion.SOLICITADA_COLABORADOR, false, true);
        RmAsignacionView esperandoPm = asignacion(
                OrigenAsignacion.PROPUESTA_RM, true, false);
        when(asignacionService.listarParaDashboard()).thenReturn(List.of(postulacion, esperandoPm));

        RmProyectoView activoConVacantes = proyecto(
                "Proyecto activo", EstadoProyecto.ACTIVO, Prioridad.ALTA, 1, 2, 1);
        RmProyectoView enRevision = proyecto(
                "Proyecto en revisión", EstadoProyecto.EN_REVISION, Prioridad.MEDIA, 0, 0, 0);
        when(proyectoService.listar()).thenReturn(List.of(activoConVacantes, enRevision));

        RmSolicitudPersonalView solicitudAbierta = mock(RmSolicitudPersonalView.class);
        when(solicitudAbierta.isPendiente()).thenReturn(true);
        when(solicitudService.listar()).thenReturn(List.of(solicitudAbierta));
        when(certificadoService.listarPendientes()).thenReturn(List.of(
                mock(RmCertificadoView.class), mock(RmCertificadoView.class)));
        when(educacionService.contarPendientes()).thenReturn(3L);
        when(cursoService.contarSolicitudesPendientes()).thenReturn(4L);

        RmViewController controller = new RmViewController(
                perfilService, colaboradorService, proyectoService, revisionService,
                asignacionService, solicitudService, certificadoService, educacionService, foroService,
                reporteService, reporteExportService, cursoService, presupuestoService, evaluacionService);
        ConcurrentModel model = new ConcurrentModel();

        assertEquals("rm/rm-dashboard", controller.dashboard(model));
        assertEquals(1L, model.getAttribute("totalAprobacionesRm"));
        assertEquals(1L, model.getAttribute("totalEsperandoPm"));
        assertEquals(1L, model.getAttribute("totalPostulaciones"));
        assertEquals(1L, model.getAttribute("totalProyectosVacantes"));
        assertEquals(1L, model.getAttribute("totalProyectosRevision"));
        assertEquals(1L, model.getAttribute("totalSolicitudesAbiertas"));
        assertEquals(2L, model.getAttribute("totalCertificadosPendientes"));
        assertEquals(3L, model.getAttribute("totalEducacionPendiente"));
        assertEquals(4L, model.getAttribute("totalCursosPendientes"));
        assertEquals(2, model.getAttribute("totalAccionesPendientes"));
        assertEquals(2, model.getAttribute("totalProyectosAtencion"));
        assertSame(enRevision, model.getAttribute("proyectoPrioritario"));
    }

    private RmAsignacionView asignacion(OrigenAsignacion origen,
                                         boolean aprobadoRm,
                                         boolean aprobadoPm) {
        Proyecto proyecto = entidadProyecto("Proyecto de asignación", EstadoProyecto.ACTIVO,
                Prioridad.MEDIA, 2);
        Usuario colaborador = new Usuario();
        colaborador.setHorasDisponibles(new BigDecimal("40"));
        colaborador.setHorasContratadasSemana(new BigDecimal("40"));

        Asignacion asignacion = new Asignacion();
        asignacion.setProyecto(proyecto);
        asignacion.setColaborador(colaborador);
        asignacion.setEstado(EstadoAsignacion.PENDIENTE);
        asignacion.setOrigen(origen);
        asignacion.setAprobadoPorRm(aprobadoRm);
        asignacion.setAprobadoPorPm(aprobadoPm);
        asignacion.setHorasSemanales(new BigDecimal("10"));

        return new RmAsignacionView(asignacion, "Colaborador Test", "CT", "PM Test",
                0, 3, BigDecimal.ZERO, 0, 2, List.of());
    }

    private RmProyectoView proyecto(String nombre, EstadoProyecto estado,
                                    Prioridad prioridad, int integrantes,
                                    int vacantes, int pendientesRm) {
        Proyecto proyecto = entidadProyecto(nombre, estado, prioridad, integrantes + vacantes);
        RmPresupuestoService.ResumenPresupuesto resumen = new RmPresupuestoService.ResumenPresupuesto(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0);
        return new RmProyectoView(proyecto, "PM Test", nombreEstado(estado),
                prioridad.name(), integrantes, vacantes, pendientesRm, List.of(), List.of(), resumen);
    }

    private Proyecto entidadProyecto(String nombre, EstadoProyecto estado,
                                     Prioridad prioridad, int requeridos) {
        Proyecto proyecto = new Proyecto();
        proyecto.setNombre(nombre);
        proyecto.setEstado(estado);
        proyecto.setPrioridad(prioridad);
        proyecto.setColaboradoresRequeridos(requeridos);
        proyecto.setFechaCreacion(LocalDateTime.now());
        return proyecto;
    }

    private String nombreEstado(EstadoProyecto estado) {
        return estado.name().toLowerCase().replace('_', ' ');
    }
}
