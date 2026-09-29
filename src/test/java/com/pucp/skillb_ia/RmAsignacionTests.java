package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.Cargo;
import com.pucp.skillb_ia.model.CategoriaHabilidad;
import com.pucp.skillb_ia.model.ColaboradorHabilidad;
import com.pucp.skillb_ia.model.Habilidad;
import com.pucp.skillb_ia.dto.RmColaboradorResumen;
import com.pucp.skillb_ia.model.enums.EstadoValidacion;
import com.pucp.skillb_ia.model.enums.NivelDominio;
import com.pucp.skillb_ia.repository.CargoRepository;
import com.pucp.skillb_ia.repository.CategoriaHabilidadRepository;
import com.pucp.skillb_ia.repository.ColaboradorHabilidadRepository;
import com.pucp.skillb_ia.repository.HabilidadRepository;
import com.pucp.skillb_ia.repository.LogAuditoriaRepository;
import com.pucp.skillb_ia.model.LogAuditoria;
import com.pucp.skillb_ia.service.rm.RmColaboradorConsultaService;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.MotivoFinalizacion;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.rm.RmAsignacionService;
import com.pucp.skillb_ia.service.pm.PmAsignacionService;
import com.pucp.skillb_ia.service.rm.RmProyectoRevisionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.pucp.skillb_ia.dto.RmAsignacionView;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@ActiveProfiles("test")
class RmAsignacionTests {

    @Autowired private WebApplicationContext context;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CargoRepository cargoRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private RmAsignacionService asignacionService;
    @Autowired private PmAsignacionService pmAsignacionService;
    @Autowired private RmProyectoRevisionService proyectoRevisionService;
    @Autowired private CategoriaHabilidadRepository categoriaRepository;
    @Autowired private HabilidadRepository habilidadRepository;
    @Autowired private ColaboradorHabilidadRepository colaboradorHabilidadRepository;
    @Autowired private RmColaboradorConsultaService consultaService;
    @Autowired private LogAuditoriaRepository logAuditoriaRepository;

    // Habilidades de Carla por estado; nombres únicos para que la coincidencia sea exacta.
    private static final String HAB_VALIDADA = "Java Asig T015";
    private static final String HAB_PENDIENTE = "Go Asig T015";
    private static final String HAB_RECHAZADA = "Rust Asig T015";
    private static final String HAB_INACTIVA = "Cobol Asig T015";

    private MockMvc mockMvc;
    private Usuario rm;
    private Usuario pm;
    private Usuario colaborador;
    private Proyecto proyecto;
    // Búsqueda de candidatos (TASK-029): lote que se desactiva al terminar cada prueba.
    private static final String BUSCAR = "/rm/proyectos/buscar-colaboradores";
    private final List<Usuario> loteCandidatos = new ArrayList<>();

    @BeforeEach
    void prepararDatos() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        asignacionRepository.deleteAll();

        Rol rolRm = obtenerRol("RESOURCE_MANAGER");
        Rol rolPm = obtenerRol("PROJECT_MANAGER");
        Rol rolColaborador = obtenerRol("COLABORADOR");

        rm = obtenerUsuario("rm.asignaciones@skillbridge.test", "Rosa", "RM", rolRm);
        pm = obtenerUsuario("pm.asignaciones@skillbridge.test", "Pedro", "PM", rolPm);
        colaborador = obtenerUsuario(
                "col.asignaciones@skillbridge.test", "Carla", "Colaboradora", rolColaborador);
        colaborador.setCargo(cargoDePrueba("Backend Developer"));
        colaborador.setHorasDisponibles(new BigDecimal("20.00"));
        colaborador.setHorasContratadasSemana(new BigDecimal("40.00"));
        colaborador.setSueldoBase(new BigDecimal("4800.00"));
        usuarioRepository.save(colaborador);
        asignarHabilidad(HAB_VALIDADA, EstadoValidacion.VALIDADA, true);
        asignarHabilidad(HAB_PENDIENTE, EstadoValidacion.PENDIENTE, true);
        asignarHabilidad(HAB_RECHAZADA, EstadoValidacion.RECHAZADA, true);
        asignarHabilidad(HAB_INACTIVA, EstadoValidacion.VALIDADA, false);

        proyecto = new Proyecto();
        proyecto.setNombre("Proyecto asignaciones " + System.nanoTime());
        proyecto.setDescripcion("Proyecto para probar el flujo de asignaciones del RM.");
        proyecto.setEstado(EstadoProyecto.ACTIVO);
        proyecto.setPrioridad(Prioridad.MEDIA);
        proyecto.setJustificacionPrioridad("Validación del flujo de asignaciones.");
        proyecto.setColaboradoresRequeridos(3);
        proyecto.setPm(pm);
        // Presupuesto y fechas necesarios para que RmPresupuestoService pueda
        // calcular costo y validar suficiencia (feature de presupuesto del RM).
        proyecto.setPresupuesto(new BigDecimal("100000.00"));
        proyecto.setFechaInicio(java.time.LocalDate.now());
        proyecto.setFechaFinEstimada(java.time.LocalDate.now().plusMonths(3));
        proyecto = proyectoRepository.save(proyecto);
    }

    // El lote de candidatos no debe aparecer en las demás clases.
    @AfterEach
    void desactivarLoteCandidatos() {
        loteCandidatos.forEach(usuario -> {
            usuario.setActivo(false);
            usuarioRepository.save(usuario);
        });
        loteCandidatos.clear();
    }

    @Test
    void propuestaDelRmQuedaPendienteDelPm() {
        Asignacion asignacion = asignacionService.proponerDesdeRm(
                proyecto.getId(), colaborador.getId(), new BigDecimal("12"),
                "Tiene las habilidades requeridas.", null, null, rm.getId());

        assertEquals(EstadoAsignacion.PENDIENTE, asignacion.getEstado());
        assertTrue(asignacion.isAprobadoPorRm());
        assertFalse(asignacion.isAprobadoPorPm());
        assertEquals(OrigenAsignacion.PROPUESTA_RM, asignacion.getOrigen());
    }

    @Test
    void propuestaDelPmQuedaPendienteDelRm() {
        Asignacion asignacion = pmAsignacionService.proponer(
                proyecto.getId(), colaborador.getId(), new BigDecimal("12"),
                "Tiene experiencia en el dominio.", pm);

        assertEquals(EstadoAsignacion.PENDIENTE, asignacion.getEstado());
        assertTrue(asignacion.isAprobadoPorPm());
        assertFalse(asignacion.isAprobadoPorRm());
        assertEquals(OrigenAsignacion.PROPUESTA_PM, asignacion.getOrigen());
    }

    @Test
    void aprobarPropuestaDelPmActivaLaAsignacion() {
        Asignacion asignacion = guardarPendiente(OrigenAsignacion.PROPUESTA_PM, true, false, "12");

        asignacionService.aprobar(asignacion.getId(), null, rm.getId());

        Asignacion actualizada = asignacionRepository.findById(asignacion.getId()).orElseThrow();
        assertEquals(EstadoAsignacion.ACTIVA, actualizada.getEstado());
        assertTrue(actualizada.isAprobadoPorRm());
        assertNotNull(actualizada.getFechaActivacion());
    }

    @Test
    void solicitudDelColaboradorSiguePendienteSiFaltaElPm() {
        Asignacion asignacion = guardarPendiente(
                OrigenAsignacion.SOLICITADA_COLABORADOR, false, false, "10");

        asignacionService.aprobar(asignacion.getId(), null, rm.getId());

        Asignacion actualizada = asignacionRepository.findById(asignacion.getId()).orElseThrow();
        assertEquals(EstadoAsignacion.PENDIENTE, actualizada.getEstado());
        assertTrue(actualizada.isAprobadoPorRm());
        assertFalse(actualizada.isAprobadoPorPm());
    }

    @Test
    void solicitudDelColaboradorPuedeSerAprobadaPorAmbosYQuedaActiva() {
        Asignacion asignacion = guardarPendiente(
                OrigenAsignacion.SOLICITADA_COLABORADOR, false, false, "10");

        asignacionService.aprobar(asignacion.getId(), null, rm.getId());
        assertEquals(1, pmAsignacionService.listarPendientesPm(proyecto.getId(), pm).size());

        pmAsignacionService.aprobar(asignacion.getId(), pm);

        Asignacion actualizada = asignacionRepository.findById(asignacion.getId()).orElseThrow();
        assertEquals(EstadoAsignacion.ACTIVA, actualizada.getEstado());
        assertTrue(actualizada.isAprobadoPorRm());
        assertTrue(actualizada.isAprobadoPorPm());
        assertNotNull(actualizada.getFechaActivacion());
    }

    @Test
    void presupuestoPuedeAumentarseTrasBloqueoYPermiteAprobar() {
        proyecto.setPresupuesto(new BigDecimal("100.00"));
        proyectoRepository.save(proyecto);
        Asignacion asignacion = guardarPendiente(
                OrigenAsignacion.PROPUESTA_PM, true, false, "12");

        assertThrows(IllegalArgumentException.class,
                () -> asignacionService.aprobar(asignacion.getId(), null, rm.getId()));

        proyectoRevisionService.asignarPresupuesto(
                proyecto.getId(), new BigDecimal("10000.00"), rm.getId());
        asignacionService.aprobar(asignacion.getId(), null, rm.getId());

        Asignacion actualizada = asignacionRepository.findById(asignacion.getId()).orElseThrow();
        assertEquals(EstadoAsignacion.ACTIVA, actualizada.getEstado());
    }

    @Test
    void noPermiteReducirPresupuestoDebajoDelMontoReservado() {
        guardarPendiente(OrigenAsignacion.PROPUESTA_RM, false, true, "12");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> proyectoRevisionService.asignarPresupuesto(
                        proyecto.getId(), new BigDecimal("100.00"), rm.getId()));

        assertTrue(error.getMessage().contains("comprometido o reservado"));
    }

    @Test
    void pmRechazaSoloConMotivoYConservaElHistorial() {
        Asignacion asignacion = guardarPendiente(
                OrigenAsignacion.PROPUESTA_RM, false, true, "8");

        assertThrows(IllegalArgumentException.class,
                () -> pmAsignacionService.rechazar(asignacion.getId(), " ", pm));

        pmAsignacionService.rechazar(asignacion.getId(), "El perfil no cubre la necesidad.", pm);
        Asignacion actualizada = asignacionRepository.findById(asignacion.getId()).orElseThrow();
        assertEquals(EstadoAsignacion.RECHAZADA, actualizada.getEstado());
        assertEquals("El perfil no cubre la necesidad.", actualizada.getMotivoRechazo());
        assertEquals(pm.getId(), actualizada.getRechazadoPor().getId());
    }

    @Test
    void excesoDeDisponibilidadExigeJustificacionPeroPuedeAprobarse() {
        Asignacion asignacion = guardarPendiente(OrigenAsignacion.PROPUESTA_PM, true, false, "30");

        assertThrows(IllegalArgumentException.class,
                () -> asignacionService.aprobar(asignacion.getId(), null, rm.getId()));

        asignacionService.aprobar(
                asignacion.getId(), "Cobertura temporal autorizada por la organización.", rm.getId());
        Asignacion actualizada = asignacionRepository.findById(asignacion.getId()).orElseThrow();
        assertEquals(EstadoAsignacion.ACTIVA, actualizada.getEstado());
        assertTrue(actualizada.getMensajeSolicitud().contains("Justificación de capacidad"));
    }

    @Test
    void rechazoConservaMotivoEHistorial() {
        Asignacion asignacion = guardarPendiente(OrigenAsignacion.PROPUESTA_PM, true, false, "12");

        asignacionService.rechazar(
                asignacion.getId(), "No coincide con las habilidades requeridas.", rm.getId());

        Asignacion actualizada = asignacionRepository.findById(asignacion.getId()).orElseThrow();
        assertEquals(EstadoAsignacion.RECHAZADA, actualizada.getEstado());
        assertEquals("No coincide con las habilidades requeridas.", actualizada.getMotivoRechazo());
        assertEquals(rm.getId(), actualizada.getRechazadoPor().getId());
    }

    @Test
    void rmPuedeFinalizarAsignacionActivaDirectamente() {
        Asignacion asignacion = guardarPendiente(OrigenAsignacion.PROPUESTA_PM, true, false, "12");
        asignacionService.aprobar(asignacion.getId(), null, rm.getId());

        asignacionService.finalizar(
                asignacion.getId(), MotivoFinalizacion.OTRO, "Fin de participación acordado.", null, null, rm.getId());

        Asignacion actualizada = asignacionRepository.findById(asignacion.getId()).orElseThrow();
        assertEquals(EstadoAsignacion.FINALIZADA, actualizada.getEstado());
        assertEquals(MotivoFinalizacion.OTRO, actualizada.getMotivoFinalizacion());
        assertNotNull(actualizada.getFechaFinalizacion());
    }

    @Test
    void renderizaBandejaYDetalleDeRevision() throws Exception {
        Asignacion asignacion = guardarPendiente(
                OrigenAsignacion.SOLICITADA_COLABORADOR, false, false, "12");

        mockMvc.perform(get("/rm/asignaciones"))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-asignaciones"))
                .andExpect(model().attributeExists("asignaciones", "pendientesRm", "activas"));

        mockMvc.perform(get("/rm/asignaciones/revision").param("id", asignacion.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-revision-asignacion"))
                .andExpect(model().attributeExists("asignacion"));

        mockMvc.perform(get("/rm/asignaciones/revision-postulacion")
                        .param("id", asignacion.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-revision-postulacion"))
                .andExpect(model().attributeExists("asignacion"));
    }

    // TASK-027: filtros y paginación de la bandeja en el servidor.

    @Test
    void bandejaPaginaDeDiezEnOrdenDeFechaSolicitudYCuentaSobreElTotal() throws Exception {
        // Se guardan desordenadas para que el orden no dependa del id.
        int[] horasAtras = {5, 0, 11, 3, 8, 1, 10, 2, 7, 4, 9, 6};
        LocalDateTime base = LocalDateTime.now().withNano(0);
        Map<Integer, Long> idPorAntiguedad = new HashMap<>();
        for (int horas : horasAtras) {
            idPorAntiguedad.put(horas, guardarAsignacion(proyecto, colaborador, OrigenAsignacion.PROPUESTA_PM,
                    EstadoAsignacion.PENDIENTE, false, false, base.minusHours(horas)).getId());
        }
        List<Long> esperado = IntStream.range(0, 12).mapToObj(idPorAntiguedad::get).toList();

        MvcResult pagina1 = mockMvc.perform(get("/rm/asignaciones"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("paginaActual", 1))
                .andExpect(model().attribute("totalPaginas", 2))
                .andExpect(model().attribute("totalRegistros", 12L))
                .andExpect(model().attribute("pendientesRm", 12L))
                .andReturn();
        MvcResult pagina2 = mockMvc.perform(get("/rm/asignaciones").param("pagina", "2"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("paginaActual", 2))
                .andExpect(model().attribute("pendientesRm", 12L))
                .andReturn();

        assertEquals(esperado.subList(0, 10), idsDeLaPagina(pagina1));
        assertEquals(esperado.subList(10, 12), idsDeLaPagina(pagina2));
        String html1 = pagina1.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String html2 = pagina2.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(10, contar(html1, "class=\"assignment-row\""));
        assertEquals(2, contar(html2, "class=\"assignment-row\""));
        assertTrue(html1.contains("Mostrando 1-10 de 12 registros"));
        assertTrue(html2.contains("Mostrando 11-12 de 12 registros"));
        // Tarjeta y pestaña con el total (12), no con las filas de la página.
        assertTrue(html2.contains("<div class=\"summary-number\">12</div>"));
        assertTrue(html2.contains("<span class=\"tab-count\">12</span>"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', value = {
            "''                                          | A B C D H",
            "grupo=active                                | E",
            "grupo=history                               | F G",
            "origen=PM                                   | A H",
            "grupo=history&origen=PM                     | F G",
            "origen=RM                                   | B",
            "origen=Colaborador                          | C D",
            "estado=Pendiente RM                         | A D H",
            "estado=Pendiente PM                         | B",
            "estado=Pendiente RM y PM                    | C",
            "grupo=history&estado=Rechazada              | G",
            "grupo=active&estado=Activa                  | E",
            "busqueda=zoila                              | H",
            "busqueda=BETA T027                          | H",
            "proyectoId={otro}                           | H",
            "proyectoId={principal}&grupo=history        | F G",
            "origen=Colaborador&estado=Pendiente RM      | D",
            "busqueda=carla&origen=PM&proyectoId={principal} | A"
    })
    void cadaFiltroGetDevuelveSoloLasAsignacionesQueCorresponden(String consulta, String esperadas)
            throws Exception {
        Map<String, Long> ids = crearBandejaDeEjemplo();
        String consultaFinal = consulta
                .replace("{principal}", proyecto.getId().toString())
                .replace("{otro}", String.valueOf(ids.get("proyectoOtro")));
        // La pestaña por defecto ("pending") solo muestra A, B, C, D y H cuando no se indica grupo.
        MvcResult resultado = mockMvc.perform(conParametros(consultaFinal))
                .andExpect(status().isOk())
                .andReturn();

        List<Long> esperado = Arrays.stream(esperadas.trim().split(" ")).map(ids::get).sorted().toList();
        assertEquals(esperado, idsDeLaPagina(resultado).stream().sorted().toList());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "grupo=desconocido", "grupo=", "origen=Otro", "origen=all", "estado=Activa", "estado=all",
            "estado=Pendiente",
            "estado=", "busqueda=   ", "pagina=abc", "pagina=0", "pagina=-3", "pagina=999",
            "proyectoId=abc", "proyectoId=999999999", "proyectoId="
    })
    void parametrosVaciosOInvalidosVuelvenAlComportamientoGeneral(String consulta) throws Exception {
        Map<String, Long> ids = crearBandejaDeEjemplo();

        MvcResult resultado = mockMvc.perform(conParametros(consulta))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-asignaciones"))
                .andExpect(model().attribute("grupo", "pending"))
                .andExpect(model().attribute("busqueda", nullValue()))
                .andExpect(model().attribute("origen", nullValue()))
                .andExpect(model().attribute("estado", nullValue()))
                .andExpect(model().attribute("proyectoId", nullValue()))
                .andExpect(model().attribute("paginaActual", 1))
                .andReturn();

        List<Long> pendientes = Stream.of("A", "B", "C", "D", "H").map(ids::get).sorted().toList();
        assertEquals(pendientes, idsDeLaPagina(resultado).stream().sorted().toList());
    }

    @Test
    void contadoresUsanElConjuntoCompletoYSoloSeAcotanPorProyecto() throws Exception {
        Map<String, Long> ids = crearBandejaDeEjemplo();

        // Búsqueda, origen, estado y pestaña no cambian los contadores.
        String html = mockMvc.perform(get("/rm/asignaciones").param("grupo", "history")
                        .param("estado", "Rechazada").param("origen", "PM").param("busqueda", "carla"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalRegistros", 1L))
                .andExpect(model().attribute("pendientesRm", 4L))
                .andExpect(model().attribute("pendientesPm", 1L))
                .andExpect(model().attribute("solicitudesColaborador", 2L))
                .andExpect(model().attribute("activas", 1L))
                .andExpect(model().attribute("historial", 2L))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(html.contains("id=\"projectFilterNotice\""));

        // Con proyectoId los contadores corresponden solo a ese proyecto y se indica el filtro.
        String htmlProyecto = mockMvc.perform(get("/rm/asignaciones")
                        .param("proyectoId", ids.get("proyectoOtro").toString()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pendientesRm", 1L))
                .andExpect(model().attribute("pendientesPm", 0L))
                .andExpect(model().attribute("solicitudesColaborador", 0L))
                .andExpect(model().attribute("activas", 0L))
                .andExpect(model().attribute("historial", 0L))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(htmlProyecto.contains("id=\"projectFilterNotice\""));
        assertTrue(htmlProyecto.contains("Proyecto Beta T027"));
    }

    @Test
    void paginacionConservaLosFiltrosYLaVistaFuncionaSinJavaScript() throws Exception {
        LocalDateTime base = LocalDateTime.now().withNano(0);
        for (int i = 0; i < 12; i++) {
            guardarAsignacion(proyecto, colaborador, OrigenAsignacion.SOLICITADA_COLABORADOR,
                    EstadoAsignacion.PENDIENTE, false, false, base.minusHours(i));
        }
        // No coincide con el estado filtrado: no debe contarse en el total filtrado.
        guardarAsignacion(proyecto, colaborador, OrigenAsignacion.SOLICITADA_COLABORADOR,
                EstadoAsignacion.PENDIENTE, true, false, base);
        String proyectoId = proyecto.getId().toString();

        String html = mockMvc.perform(get("/rm/asignaciones")
                        .param("grupo", "pending").param("busqueda", "Carla").param("origen", "Colaborador")
                        .param("estado", "Pendiente RM y PM").param("proyectoId", proyectoId)
                        .param("pagina", "2"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("paginaActual", 2))
                .andExpect(model().attribute("totalRegistros", 12L))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        List<String> enlaces = enlaces(html, "/rm/asignaciones\\?[^\"]*pagina=\\d+");
        assertTrue(enlaces.stream().anyMatch(enlace -> enlace.endsWith("pagina=1")));
        assertTrue(enlaces.stream().anyMatch(enlace -> enlace.endsWith("pagina=2")));
        for (String enlace : enlaces) {
            assertTrue(enlace.contains("grupo=pending"), enlace);
            assertTrue(enlace.contains("busqueda=Carla"), enlace);
            assertTrue(enlace.contains("origen=Colaborador"), enlace);
            assertTrue(enlace.contains("estado=Pendiente RM y PM"), enlace);
            assertTrue(enlace.contains("proyectoId=" + proyectoId), enlace);
        }
        // Las pestañas conservan búsqueda, origen y proyecto, y reinician estado y página.
        List<String> pestanas = enlaces(html, "/rm/asignaciones\\?grupo=history[^\"]*");
        assertEquals(1, pestanas.size());
        assertTrue(pestanas.get(0).contains("busqueda=Carla") && pestanas.get(0).contains("origen=Colaborador")
                && pestanas.get(0).contains("proyectoId=" + proyectoId), pestanas.get(0));
        assertFalse(pestanas.get(0).contains("estado=") || pestanas.get(0).contains("pagina="), pestanas.get(0));

        // Sin JavaScript: formulario GET con los valores activos, filas ya recortadas en el servidor.
        assertFalse(html.contains("rm-asignaciones.js"));
        assertTrue(html.contains("id=\"filtersForm\" method=\"get\""));
        assertTrue(html.contains("name=\"proyectoId\" value=\"" + proyectoId + "\""));
        assertTrue(html.contains("value=\"Carla\""));
        assertTrue(html.contains("<option value=\"Colaborador\" selected=\"selected\">"));
        assertTrue(html.contains("<option value=\"Pendiente RM y PM\" selected=\"selected\">"));
        // "Pendiente" a secas no es un estado alcanzable: no se ofrece como opción.
        assertFalse(html.contains("<option value=\"Pendiente\""));
        assertEquals(2, contar(html, "class=\"assignment-row\""));
        assertEquals(2, contar(html, ">Pendiente RM y PM</span>"));
    }

    @Test
    void presupuestoDesdeRevisionMuestraMensajeTrasLaRedireccion() throws Exception {
        Asignacion asignacion = guardarPendiente(OrigenAsignacion.PROPUESTA_PM, true, false, "12");
        String destino = "/rm/asignaciones/revision?id=" + asignacion.getId();
        autenticarRm();
        try {
            MvcResult exito = mockMvc.perform(post("/rm/proyectos/" + proyecto.getId() + "/presupuesto")
                            .param("presupuesto", "150000")
                            .param("asignacionId", asignacion.getId().toString()))
                    .andExpect(redirectedUrl(destino))
                    .andExpect(flash().attribute("mensajeExito", "Presupuesto guardado correctamente."))
                    .andExpect(flash().attributeCount(1))
                    .andReturn();
            String html = renderizarTrasRedireccion(destino, exito);
            assertTrue(html.contains(
                    "<div class=\"alert alert-success\" role=\"alert\">Presupuesto guardado correctamente.</div>"));
            assertFalse(html.contains("class=\"alert alert-danger\" role=\"alert\""));

            MvcResult error = mockMvc.perform(post("/rm/proyectos/" + proyecto.getId() + "/presupuesto")
                            .param("presupuesto", "-1")
                            .param("asignacionId", asignacion.getId().toString()))
                    .andExpect(redirectedUrl(destino))
                    .andExpect(flash().attribute("mensajeError", "El presupuesto debe ser mayor que cero."))
                    .andExpect(flash().attributeCount(1))
                    .andReturn();
            html = renderizarTrasRedireccion(destino, error);
            assertTrue(html.contains(
                    "<div class=\"alert alert-danger\" role=\"alert\">El presupuesto debe ser mayor que cero.</div>"));
            assertFalse(html.contains("class=\"alert alert-success\" role=\"alert\""));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void presupuestoDesdePostulacionMuestraMensajeTrasLaRedireccion() throws Exception {
        Asignacion asignacion = guardarPendiente(
                OrigenAsignacion.SOLICITADA_COLABORADOR, false, false, "12");
        String destino = "/rm/asignaciones/revision-postulacion?id=" + asignacion.getId();
        autenticarRm();
        try {
            MvcResult exito = mockMvc.perform(post("/rm/proyectos/" + proyecto.getId() + "/presupuesto")
                            .param("presupuesto", "150000")
                            .param("asignacionId", asignacion.getId().toString()))
                    .andExpect(redirectedUrl(destino))
                    .andExpect(flash().attribute("mensajeExito", "Presupuesto guardado correctamente."))
                    .andReturn();
            String html = renderizarTrasRedireccion(destino, exito);
            assertEquals(1, contar(html, "Presupuesto guardado correctamente."));
            assertTrue(html.contains(
                    "<div class=\"alert alert-success\" role=\"alert\">Presupuesto guardado correctamente.</div>"));

            MvcResult error = mockMvc.perform(post("/rm/proyectos/" + proyecto.getId() + "/presupuesto")
                            .param("presupuesto", "-1")
                            .param("asignacionId", asignacion.getId().toString()))
                    .andExpect(redirectedUrl(destino))
                    .andExpect(flash().attribute("mensajeError", "El presupuesto debe ser mayor que cero."))
                    .andReturn();
            html = renderizarTrasRedireccion(destino, error);
            assertTrue(html.contains(
                    "<div class=\"alert alert-danger\" role=\"alert\">El presupuesto debe ser mayor que cero.</div>"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void revisionOmiteMensajesVaciosYEscapaElHtml() throws Exception {
        Asignacion asignacion = guardarPendiente(OrigenAsignacion.PROPUESTA_PM, true, false, "12");

        String html = mockMvc.perform(get("/rm/asignaciones/revision").param("id", asignacion.getId().toString())
                        .flashAttr("mensajeExito", "")
                        .flashAttr("mensajeError", "   "))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(html.contains("class=\"alert alert-success\" role=\"alert\""));
        assertFalse(html.contains("class=\"alert alert-danger\" role=\"alert\""));

        html = mockMvc.perform(get("/rm/asignaciones/revision").param("id", asignacion.getId().toString())
                        .flashAttr("mensajeError", "<b>falló</b>"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("&lt;b&gt;falló&lt;/b&gt;"));
        assertFalse(html.contains("<b>falló</b>"));
    }

    @Test
    void dashboardEnlazaCadaPendienteConSuRevisionEspecifica() throws Exception {
        Asignacion postulacion = guardarPendiente(
                OrigenAsignacion.SOLICITADA_COLABORADOR, false, false, "12");

        mockMvc.perform(get("/rm/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-dashboard"))
                .andExpect(model().attributeExists("accionesPendientes", "totalAccionesPendientes"))
                .andExpect(content().string(containsString(
                        "/rm/asignaciones/revision-postulacion?id=" + postulacion.getId())));
    }

    @Test
    void buscarColaboradoresIncluyeModalesYBloqueaAQuienYaEstaEnElProyecto() throws Exception {
        mockMvc.perform(get("/rm/proyectos/buscar-colaboradores")
                        .param("proyectoId", proyecto.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"propuestaModal\"")))
                .andExpect(content().string(containsString("id=\"perfilModal\"")))
                .andExpect(content().string(containsString("data-origen=\"buscar\"")));

        asignacionService.proponerDesdeRm(proyecto.getId(), colaborador.getId(), new BigDecimal("8"),
                "Primera propuesta.", null, null, rm.getId());
        mockMvc.perform(get("/rm/proyectos/buscar-colaboradores")
                        .param("proyectoId", proyecto.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("colaboradoresConAsignacion",
                        org.hamcrest.Matchers.hasItem(colaborador.getId())));
    }

    @Test
    void perfilModalDevuelveElFragmentoDelColaborador() throws Exception {
        mockMvc.perform(get("/rm/colaboradores/perfil-modal").param("id", colaborador.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Carla")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("<html"))));
    }

    @Test
    void perfilDelColaboradorListaProyectosAsignables() throws Exception {
        mockMvc.perform(get("/rm/colaboradores/perfil").param("id", colaborador.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("proyectosAsignables", "proyectosConAsignacion"))
                .andExpect(content().string(containsString("id=\"proyectosModal\"")))
                .andExpect(content().string(containsString(proyecto.getNombre())));
    }

    @Test
    void propuestaConErrorVuelveALaPantallaDeOrigen() throws Exception {
        UsuarioDetails details = new UsuarioDetails(usuarioRepository.findActivosByRolNombre("RESOURCE_MANAGER")
                .stream().filter(u -> u.getId().equals(rm.getId())).findFirst().orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
        try {
            // Horas inválidas (0) => error de validación.
            mockMvc.perform(post("/rm/asignaciones/proponer")
                            .param("proyectoId", proyecto.getId().toString())
                            .param("colaboradorId", colaborador.getId().toString())
                            .param("horasSemanales", "0")
                            .param("origen", "buscar"))
                    .andExpect(redirectedUrl("/rm/proyectos/buscar-colaboradores?proyectoId=" + proyecto.getId()));
            mockMvc.perform(post("/rm/asignaciones/proponer")
                            .param("proyectoId", proyecto.getId().toString())
                            .param("colaboradorId", colaborador.getId().toString())
                            .param("horasSemanales", "0")
                            .param("origen", "perfil"))
                    .andExpect(redirectedUrl("/rm/colaboradores/perfil?id=" + colaborador.getId()));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void horasSemanalesVaciasOInvalidasVuelvenConMensaje() throws Exception {
        String destino = "/rm/proyectos/proponer-asignacion?proyectoId=" + proyecto.getId()
                + "&colaboradorId=" + colaborador.getId();
        autenticarRm();
        try {
            mockMvc.perform(post("/rm/asignaciones/proponer")
                            .param("proyectoId", proyecto.getId().toString())
                            .param("colaboradorId", colaborador.getId().toString())
                            .param("horasSemanales", ""))
                    .andExpect(redirectedUrl(destino))
                    .andExpect(flash().attribute("mensajeError", "Las horas semanales deben ser mayores que cero."));
            mockMvc.perform(post("/rm/asignaciones/proponer")
                            .param("proyectoId", proyecto.getId().toString())
                            .param("colaboradorId", colaborador.getId().toString())
                            .param("horasSemanales", "ocho"))
                    .andExpect(redirectedUrl(destino))
                    .andExpect(flash().attribute("mensajeError", "Las horas semanales deben ser un número válido."));
            assertEquals(0, asignacionRepository.count());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void nivelDeExperienciaVacioOInvalidoVuelveConMensaje() throws Exception {
        String destino = "/rm/colaboradores/perfil?id=" + colaborador.getId();
        var nivelAnterior = usuarioRepository.findById(colaborador.getId()).orElseThrow().getNivelExperiencia();
        autenticarRm();
        try {
            mockMvc.perform(post("/rm/colaboradores/" + colaborador.getId() + "/nivel-experiencia")
                            .param("nivel", ""))
                    .andExpect(redirectedUrl(destino))
                    .andExpect(flash().attribute("mensajeError", "Selecciona un nivel de experiencia."));
            mockMvc.perform(post("/rm/colaboradores/" + colaborador.getId() + "/nivel-experiencia")
                            .param("nivel", "EXPERTO"))
                    .andExpect(redirectedUrl(destino))
                    .andExpect(flash().attribute("mensajeError", "Selecciona un nivel de experiencia válido."));
            assertEquals(nivelAnterior,
                    usuarioRepository.findById(colaborador.getId()).orElseThrow().getNivelExperiencia());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void motivoDeFinalizacionVacioOInvalidoVuelveConMensaje() throws Exception {
        Asignacion asignacion = guardarPendiente(OrigenAsignacion.PROPUESTA_PM, true, false, "12");
        asignacionService.aprobar(asignacion.getId(), null, rm.getId());
        autenticarRm();
        try {
            MvcResult vacio = mockMvc.perform(post("/rm/asignaciones/" + asignacion.getId() + "/finalizar")
                            .param("motivo", ""))
                    .andExpect(redirectedUrl("/rm/asignaciones"))
                    .andExpect(flash().attribute("mensajeError", "Selecciona un motivo de finalización."))
                    .andReturn();
            assertTrue(renderizarTrasRedireccion("/rm/asignaciones", vacio)
                    .contains("Selecciona un motivo de finalización."));
            mockMvc.perform(post("/rm/asignaciones/" + asignacion.getId() + "/finalizar")
                            .param("motivo", "DESPIDO"))
                    .andExpect(redirectedUrl("/rm/asignaciones"))
                    .andExpect(flash().attribute("mensajeError", "Selecciona un motivo de finalización válido."));
            assertEquals(EstadoAsignacion.ACTIVA,
                    asignacionRepository.findById(asignacion.getId()).orElseThrow().getEstado());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void renderizaPropuestaPendientePmActivaEHistorialDelColaborador() throws Exception {
        mockMvc.perform(get("/rm/proyectos/proponer-asignacion"))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-proponer-asignacion"))
                .andExpect(model().attributeExists("proyectos", "colaboradores"));

        mockMvc.perform(get("/rm/proyectos/buscar-colaboradores")
                        .param("proyectoId", proyecto.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-buscar-colaboradores-proyecto"))
                .andExpect(model().attributeExists("proyecto", "candidatos"));

        Asignacion pendientePm = guardarPendiente(
                OrigenAsignacion.PROPUESTA_RM, false, true, "8");
        mockMvc.perform(get("/rm/asignaciones/pendiente-pm")
                        .param("id", pendientePm.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-detalle-asignacion-pendiente-pm"));

        Asignacion activa = new Asignacion();
        activa.setProyecto(proyecto);
        activa.setColaborador(colaborador);
        activa.setHorasSemanales(new BigDecimal("10"));
        activa.setOrigen(OrigenAsignacion.PROPUESTA_PM);
        activa.setEstado(EstadoAsignacion.ACTIVA);
        activa.setAprobadoPorPm(true);
        activa.setAprobadoPorRm(true);
        activa = asignacionRepository.save(activa);

        mockMvc.perform(get("/rm/asignaciones/activa").param("id", activa.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-detalle-asignacion-activa"));

        mockMvc.perform(get("/rm/colaboradores/asignaciones")
                        .param("id", colaborador.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-asignaciones-colaborador"))
                .andExpect(model().attributeExists("colaborador", "asignaciones"));
    }

    @Test
    void candidatosYPropuestaUsanSoloHabilidadesValidadas() throws Exception {
        // Regla en el servicio: el resumen del candidato solo lleva habilidades validadas.
        RmColaboradorResumen candidato = consultaService.listarPaginaCandidatos(proyecto,
                        consultaService.normalizarFiltrosCandidatos("Carla Colaboradora", null, null), "1")
                .filas().stream()
                .map(RmColaboradorConsultaService.CandidatoConCosto::resumen)
                .filter(resumen -> resumen.getId().equals(colaborador.getId()))
                .findFirst().orElseThrow();
        assertEquals(java.util.List.of(HAB_VALIDADA), candidato.getHabilidades());
        assertFalse(candidato.getTextoBusqueda().contains(HAB_PENDIENTE));
        assertFalse(candidato.getTextoBusqueda().contains(HAB_RECHAZADA));
        // La búsqueda GET por habilidad: busquedaDeCandidatosSoloCoincideConHabilidadesValidadas.

        // Con 6 tarjetas por página se acota por nombre para que la tarjeta de Carla esté visible.
        String tarjetas = mockMvc.perform(get("/rm/proyectos/buscar-colaboradores")
                        .param("proyectoId", proyecto.getId().toString())
                        .param("busqueda", "Carla Colaboradora"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(tarjetas.contains(HAB_VALIDADA));
        assertSinHabilidadesNoValidadas(tarjetas);

        // Pantalla y modal de propuesta: la lista de colaboradores usa el mismo resumen validado.
        String propuesta = mockMvc.perform(get("/rm/proyectos/proponer-asignacion")
                        .param("proyectoId", proyecto.getId().toString())
                        .param("colaboradorId", colaborador.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertSinHabilidadesNoValidadas(propuesta);
    }

    @Test
    void perfilModalDeLaPropuestaMuestraSoloHabilidadesValidadas() throws Exception {
        String modal = mockMvc.perform(get("/rm/colaboradores/perfil-modal")
                        .param("id", colaborador.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(modal.contains(HAB_VALIDADA));
        assertTrue(modal.contains(
                "class=\"badge align-self-start bg-green-lt\" data-estado-habilidad=\"VALIDADA\">Validada<"));
        assertSinHabilidadesNoValidadas(modal);
        assertFalse(modal.contains("data-estado-habilidad=\"PENDIENTE\""));
        assertFalse(modal.contains("data-estado-habilidad=\"RECHAZADA\""));

        // Regla en Java: el detalle conserva todas, pero el modal usa solo la lista validada.
        var detalle = consultaService.obtenerDetalle(colaborador.getId());
        assertEquals(3, detalle.getHabilidades().size());
        assertEquals(java.util.List.of(HAB_VALIDADA), detalle.getHabilidadesValidadas().stream()
                .map(com.pucp.skillb_ia.dto.RmColaboradorDetalle.HabilidadDetalle::getNombre).toList());
    }

    @Test
    void propuestaManipuladaNoIncorporaHabilidadesNoValidadas() throws Exception {
        autenticarRm();
        try {
            // Parámetros ajenos al formulario que intentan colar habilidades no validadas.
            mockMvc.perform(post("/rm/asignaciones/proponer")
                            .param("proyectoId", proyecto.getId().toString())
                            .param("colaboradorId", colaborador.getId().toString())
                            .param("horasSemanales", "8")
                            .param("justificacion", "Propuesta manipulada.")
                            .param("habilidades", HAB_PENDIENTE, HAB_RECHAZADA)
                            .param("habilidad", HAB_PENDIENTE)
                            .param("estadoValidacion", "VALIDADA"))
                    .andExpect(redirectedUrl("/rm/asignaciones"));
        } finally {
            SecurityContextHolder.clearContext();
        }

        Asignacion propuesta = asignacionRepository.findAll().stream()
                .filter(item -> item.getColaborador().getId().equals(colaborador.getId()))
                .findFirst().orElseThrow();
        // Los criterios de la asignación se calculan en el servidor, solo con habilidades validadas.
        assertEquals(java.util.List.of(HAB_VALIDADA), asignacionService.obtener(propuesta.getId()).getHabilidades());

        // La revisión del RM (criterios de asignación) tampoco muestra habilidades no validadas.
        asignacionRepository.delete(propuesta);
        Asignacion pendienteRm = guardarPendiente(OrigenAsignacion.PROPUESTA_PM, true, false, "8");
        String revision = mockMvc.perform(get("/rm/asignaciones/revision")
                        .param("id", pendienteRm.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(revision.contains(HAB_VALIDADA));
        assertSinHabilidadesNoValidadas(revision);

        // La petición no altera el estado de validación guardado.
        assertEquals(EstadoValidacion.PENDIENTE, estadoGuardado(HAB_PENDIENTE));
        assertEquals(EstadoValidacion.RECHAZADA, estadoGuardado(HAB_RECHAZADA));
    }

    // ---- TASK-034: propuesta desde la búsqueda, modal compartido y cupos del proyecto ----

    private static final String PROPONER = "/rm/asignaciones/proponer";
    private static final String MENSAJE_EXITO = "Propuesta enviada al Project Manager correctamente.";
    private static final String MENSAJE_CUPO_OBLIGATORIO =
            "El proyecto ya tiene sus cupos completos; justifica por qué propones otro colaborador.";
    private static final List<String> ACCIONES_PROPUESTA =
            List.of("PROPUESTA_ASIGNACION", "JUSTIFICACION_CUPO_ASIGNACION");

    @Test
    void propuestaExitosaDesdeBuscarVuelveALaMismaBusquedaConFiltrosYPagina() throws Exception {
        autenticarRm();
        try {
            // Los valores se normalizan y se codifican por completo ("+", "&", espacios y tildes).
            mockMvc.perform(propuesta("buscar", "8")
                            .param("busqueda", "  C++ & Díaz  ")
                            .param("disponibilidad", "16")
                            .param("carga", "available")
                            .param("pagina", "2"))
                    .andExpect(redirectedUrl(BUSCAR + "?proyectoId=" + proyecto.getId()
                            + "&busqueda=C%2B%2B%20%26%20D%C3%ADaz&disponibilidad=16&carga=available&pagina=2"))
                    .andExpect(flash().attribute("mensajeExito", MENSAJE_EXITO));
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertEquals(1, asignacionRepository.count());
    }

    @Test
    void propuestaConErrorDesdeBuscarConservaFiltrosYPagina() throws Exception {
        long auditoriasAntes = contarAuditoriasDePropuesta();
        autenticarRm();
        try {
            mockMvc.perform(propuesta("buscar", "0")
                            .param("busqueda", "Carla Colaboradora")
                            .param("disponibilidad", "8")
                            .param("carga", "full")
                            .param("pagina", "3"))
                    .andExpect(redirectedUrl(BUSCAR + "?proyectoId=" + proyecto.getId()
                            + "&busqueda=Carla%20Colaboradora&disponibilidad=8&carga=full&pagina=3"))
                    .andExpect(flash().attribute("mensajeError", "Las horas semanales deben ser mayores que cero."));
            // Valores desconocidos o inválidos no se copian y no se acepta una URL de retorno libre.
            mockMvc.perform(propuesta("buscar", "0")
                            .param("disponibilidad", "999")
                            .param("carga", "<script>")
                            .param("pagina", "-4")
                            .param("volverA", "https://malicioso.example"))
                    .andExpect(redirectedUrl(BUSCAR + "?proyectoId=" + proyecto.getId()));
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertEquals(0, asignacionRepository.count());
        assertEquals(auditoriasAntes, contarAuditoriasDePropuesta());
    }

    @Test
    void trasProponerDesdeBuscarElCandidatoApareceComoYaEnElProyecto() throws Exception {
        MvcResult resultado;
        autenticarRm();
        try {
            resultado = mockMvc.perform(propuesta("buscar", "8")
                            .param("busqueda", "Carla Colaboradora")
                            .param("disponibilidad", "16")
                            .param("pagina", "1"))
                    .andExpect(redirectedUrl(BUSCAR + "?proyectoId=" + proyecto.getId()
                            + "&busqueda=Carla%20Colaboradora&disponibilidad=16"))
                    .andReturn();
        } finally {
            SecurityContextHolder.clearContext();
        }

        String html = mockMvc.perform(get(java.net.URI.create(resultado.getResponse().getRedirectedUrl()))
                        .flashAttrs(resultado.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-buscar-colaboradores-proyecto"))
                .andExpect(model().attribute("busqueda", "Carla Colaboradora"))
                .andExpect(model().attribute("disponibilidad", "16"))
                .andExpect(model().attribute("colaboradoresConAsignacion",
                        org.hamcrest.Matchers.hasItem(colaborador.getId())))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains(MENSAJE_EXITO));
        assertTrue(html.contains("Ya en el proyecto"));
        assertTrue(html.contains("data-bloqueado=\"true\" data-colaborador-id=\"" + colaborador.getId() + "\""));
    }

    @Test
    void porDebajoDelCupoNoExigeJustificacionDeCupo() {
        llenarCupos(1, 1); // 2 de 3
        long antes = contarAuditoriasDePropuesta();

        Asignacion creada = asignacionService.proponerDesdeRm(proyecto.getId(), colaborador.getId(),
                new BigDecimal("8"), "Dentro del cupo.", null, null, rm.getId());

        assertEquals(EstadoAsignacion.PENDIENTE, creada.getEstado());
        // Solo el registro de la propuesta, sin el de justificación de cupo.
        assertEquals(antes + 1, contarAuditoriasDePropuesta());
        assertFalse(auditoriaDe("PROPUESTA_ASIGNACION", creada.getId()).getDetalle().contains("Supera los cupos"));
    }

    @Test
    void conCuposCompletosSinJustificacionValidaRechazaSinCrearNiAuditar() throws Exception {
        llenarCupos(2, 1); // 3 de 3
        long asignacionesAntes = asignacionRepository.count();
        long auditoriasAntes = contarAuditoriasDePropuesta();
        String destino = BUSCAR + "?proyectoId=" + proyecto.getId();
        autenticarRm();
        try {
            mockMvc.perform(propuesta("buscar", "8"))
                    .andExpect(redirectedUrl(destino))
                    .andExpect(flash().attribute("mensajeError", MENSAJE_CUPO_OBLIGATORIO));
            for (String invalida : List.of("", "   ")) {
                mockMvc.perform(propuesta("buscar", "8").param("justificacionCupo", invalida))
                        .andExpect(redirectedUrl(destino))
                        .andExpect(flash().attribute("mensajeError", MENSAJE_CUPO_OBLIGATORIO));
            }
            mockMvc.perform(propuesta("buscar", "8").param("justificacionCupo", "x".repeat(501)))
                    .andExpect(redirectedUrl(destino))
                    .andExpect(flash().attribute("mensajeError",
                            "La justificación de cupo no puede superar 500 caracteres."));
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertEquals(asignacionesAntes, asignacionRepository.count());
        assertEquals(auditoriasAntes, contarAuditoriasDePropuesta());
        assertEquals(3, asignacionService.contarCuposOcupados(proyecto));
    }

    @Test
    void conCuposCompletosYJustificacionValidaCreaLaPropuestaYAuditaElTexto() throws Exception {
        llenarCupos(2, 1); // 3 de 3
        String texto = "El cliente amplió el alcance: " + "a".repeat(470);
        assertEquals(RmAsignacionService.MAX_JUSTIFICACION_CUPO, texto.length());
        autenticarRm();
        try {
            mockMvc.perform(propuesta("buscar", "8").param("justificacionCupo", "  " + texto + "  "))
                    .andExpect(redirectedUrl(BUSCAR + "?proyectoId=" + proyecto.getId()))
                    .andExpect(flash().attribute("mensajeExito", MENSAJE_EXITO));
        } finally {
            SecurityContextHolder.clearContext();
        }

        Asignacion creada = asignacionRepository.findAll().stream()
                .filter(item -> item.getColaborador().getId().equals(colaborador.getId()))
                .findFirst().orElseThrow();
        assertEquals(EstadoAsignacion.PENDIENTE, creada.getEstado());
        LogAuditoria cupo = auditoriaDe("JUSTIFICACION_CUPO_ASIGNACION", creada.getId());
        assertEquals(texto, cupo.getDetalle());
        assertEquals("Cupos ocupados: 3 de 3", cupo.getValorAnterior());
        assertTrue(auditoriaDe("PROPUESTA_ASIGNACION", creada.getId()).getDetalle()
                .endsWith("Supera los cupos del proyecto."));
        // La justificación de cupo no se mezcla con la justificación general de la propuesta.
        assertEquals("Encaja con el proyecto.", creada.getMensajeSolicitud());
        assertEquals(4, asignacionService.contarCuposOcupados(proyecto));
    }

    @Test
    void elConteoDeCuposIncluyeActivasYPendientesPeroNoEstadosCerrados() {
        List<Usuario> otros = otrosColaboradores(5);
        LocalDateTime ahora = LocalDateTime.now();
        guardarAsignacion(proyecto, otros.get(0), OrigenAsignacion.PROPUESTA_PM, EstadoAsignacion.ACTIVA, true, true, ahora);
        guardarAsignacion(proyecto, otros.get(1), OrigenAsignacion.PROPUESTA_PM, EstadoAsignacion.PENDIENTE, false, false, ahora);
        guardarAsignacion(proyecto, otros.get(2), OrigenAsignacion.PROPUESTA_PM, EstadoAsignacion.FINALIZADA, true, true, ahora);
        guardarAsignacion(proyecto, otros.get(3), OrigenAsignacion.PROPUESTA_PM, EstadoAsignacion.RECHAZADA, false, false, ahora);
        guardarAsignacion(proyecto, otros.get(4), OrigenAsignacion.PROPUESTA_PM, EstadoAsignacion.FINALIZADA, true, true, ahora);
        assertEquals(2, asignacionService.contarCuposOcupados(proyecto));

        // 5 registros, pero solo 2 ocupan cupo: con 3 requeridos no se pide justificación de cupo.
        Asignacion creada = asignacionService.proponerDesdeRm(proyecto.getId(), colaborador.getId(),
                new BigDecimal("8"), null, null, null, rm.getId());
        assertNotNull(creada.getId());
        assertEquals(Map.of(proyecto.getId(), 3L),
                asignacionService.cuposOcupadosPorProyecto(List.of(proyecto)));
    }

    @Test
    void peticionManipuladaNoEvitaLaValidacionDeCupos() throws Exception {
        llenarCupos(2, 1); // 3 de 3
        Proyecto cancelado = new Proyecto();
        cancelado.setNombre("Proyecto cancelado T034 " + System.nanoTime());
        cancelado.setDescripcion("Proyecto cerrado para la propuesta manipulada.");
        cancelado.setEstado(EstadoProyecto.CANCELADO);
        cancelado.setPrioridad(Prioridad.MEDIA);
        cancelado.setJustificacionPrioridad("Validación de TASK-034.");
        cancelado.setColaboradoresRequeridos(10);
        cancelado.setPm(pm);
        cancelado.setPresupuesto(new BigDecimal("100000.00"));
        cancelado.setFechaInicio(java.time.LocalDate.now());
        cancelado.setFechaFinEstimada(java.time.LocalDate.now().plusMonths(3));
        cancelado = proyectoRepository.save(cancelado);
        long asignacionesAntes = asignacionRepository.count();
        long auditoriasAntes = contarAuditoriasDePropuesta();
        String destino = BUSCAR + "?proyectoId=" + proyecto.getId();
        autenticarRm();
        try {
            // Conteos falsos del navegador: el servidor cuenta por su cuenta.
            mockMvc.perform(propuesta("buscar", "8")
                            .param("cuposOcupados", "0")
                            .param("proyectoOcupados", "0")
                            .param("colaboradoresRequeridos", "99")
                            .param("proyectoRequeridos", "99")
                            .param("cupoCompleto", "false"))
                    .andExpect(redirectedUrl(destino))
                    .andExpect(flash().attribute("mensajeError", MENSAJE_CUPO_OBLIGATORIO));
            // La justificación de capacidad no reemplaza a la de cupo.
            mockMvc.perform(propuesta("buscar", "8").param("motivoCapacidad", "Capacidad justificada."))
                    .andExpect(redirectedUrl(destino))
                    .andExpect(flash().attribute("mensajeError", MENSAJE_CUPO_OBLIGATORIO));
            // proyectoId oculto cambiado a un proyecto cerrado: se revalida el estado.
            mockMvc.perform(post(PROPONER)
                            .param("proyectoId", cancelado.getId().toString())
                            .param("colaboradorId", colaborador.getId().toString())
                            .param("horasSemanales", "8")
                            .param("justificacionCupo", "Texto válido.")
                            .param("origen", "buscar"))
                    .andExpect(redirectedUrl(BUSCAR + "?proyectoId=" + cancelado.getId()))
                    .andExpect(flash().attribute("mensajeError",
                            "Solo se pueden proponer asignaciones para proyectos activos o en espera."));
            // Un origen desconocido no produce una redirección externa.
            mockMvc.perform(propuesta("https://malicioso.example", "8"))
                    .andExpect(redirectedUrl("/rm/proyectos/proponer-asignacion?proyectoId=" + proyecto.getId()
                            + "&colaboradorId=" + colaborador.getId()))
                    .andExpect(flash().attribute("mensajeError", MENSAJE_CUPO_OBLIGATORIO));
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertEquals(asignacionesAntes, asignacionRepository.count());
        assertEquals(auditoriasAntes, contarAuditoriasDePropuesta());
    }

    @Test
    void botonesDeProponerDeAsignacionesUsanElModalYNoLaPaginaCompleta() throws Exception {
        // Bandeja: sin colaborador conocido, el modal de proyectos abre la búsqueda de candidatos.
        String bandeja = mockMvc.perform(get("/rm/asignaciones"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(bandeja.contains("/rm/proyectos/proponer-asignacion"));
        assertTrue(bandeja.contains("data-bs-toggle=\"modal\" data-bs-target=\"#proyectosModal\">+ Proponer asignación"));
        assertTrue(bandeja.contains("id=\"proyectosModal\""));
        assertTrue(bandeja.contains("data-buscar-url=\"/rm/proyectos/buscar-colaboradores?proyectoId="
                + proyecto.getId() + "\""));
        assertTrue(bandeja.contains("/js/rm-js/rm-propuesta-asignacion.js"));
        // Con el filtro de proyecto, el modal trae solo ese proyecto.
        mockMvc.perform(get("/rm/asignaciones").param("proyectoId", proyecto.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("proyectosAsignables", org.hamcrest.Matchers.hasSize(1)));

        // Asignaciones del colaborador: colaborador precargado y el mismo modal de propuesta.
        String delColaborador = mockMvc.perform(get("/rm/colaboradores/asignaciones")
                        .param("id", colaborador.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(delColaborador.contains("/rm/proyectos/proponer-asignacion"));
        assertTrue(delColaborador.contains("data-bs-toggle=\"modal\" data-bs-target=\"#proyectosModal\">+ Proponer asignación"));
        assertTrue(delColaborador.contains("id=\"proyectosModal\""));
        assertTrue(delColaborador.contains("id=\"propuestaModal\""));
        assertTrue(delColaborador.contains("data-origen=\"colaborador\""));
        assertTrue(delColaborador.contains("data-colaborador-id=\"" + colaborador.getId() + "\""));
        assertTrue(delColaborador.contains(proyecto.getNombre()));
        assertTrue(delColaborador.contains("data-proyecto-requeridos=\"3\" data-proyecto-ocupados=\"0\""));
        assertTrue(delColaborador.contains("name=\"justificacionCupo\""));
        assertFalse(delColaborador.contains("data-buscar-url"));

        // Búsqueda: el contexto lleva cupos y filtros para volver a la misma búsqueda.
        String busqueda = mockMvc.perform(get(BUSCAR)
                        .param("proyectoId", proyecto.getId().toString())
                        .param("busqueda", "Carla Colaboradora")
                        .param("disponibilidad", "8"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(busqueda.contains("data-proyecto-requeridos=\"3\" data-proyecto-ocupados=\"0\""
                + " data-filtro-busqueda=\"Carla Colaboradora\" data-filtro-disponibilidad=\"8\""
                + " data-filtro-pagina=\"1\""));
        assertTrue(busqueda.contains("name=\"busqueda\" data-campo=\"filtroBusqueda\""));
        assertTrue(busqueda.contains("id=\"propuestaJustificacionCupo\" name=\"justificacionCupo\""));
    }

    @Test
    void flujosDesdePerfilYAsignacionesDelColaboradorSiguenFuncionando() throws Exception {
        String asignacionesColaborador = "/rm/colaboradores/asignaciones?id=" + colaborador.getId();
        autenticarRm();
        try {
            // Asignaciones del colaborador: error y éxito vuelven a la misma pantalla.
            mockMvc.perform(propuesta("colaborador", "0"))
                    .andExpect(redirectedUrl(asignacionesColaborador))
                    .andExpect(flash().attribute("mensajeError", "Las horas semanales deben ser mayores que cero."));
            MvcResult exito = mockMvc.perform(propuesta("colaborador", "8"))
                    .andExpect(redirectedUrl(asignacionesColaborador))
                    .andExpect(flash().attribute("mensajeExito", MENSAJE_EXITO))
                    .andReturn();
            String html = renderizarTrasRedireccion(asignacionesColaborador, exito);
            assertTrue(html.contains(MENSAJE_EXITO));
            assertTrue(html.contains(proyecto.getNombre()));

            // Perfil: el error vuelve al perfil y el éxito, como antes, a la bandeja.
            asignacionRepository.deleteAll();
            mockMvc.perform(propuesta("perfil", "0"))
                    .andExpect(redirectedUrl("/rm/colaboradores/perfil?id=" + colaborador.getId()));
            mockMvc.perform(propuesta("perfil", "8"))
                    .andExpect(redirectedUrl("/rm/asignaciones"))
                    .andExpect(flash().attribute("mensajeExito", MENSAJE_EXITO));
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertEquals(1, asignacionRepository.count());

        mockMvc.perform(get("/rm/colaboradores/perfil").param("id", colaborador.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("proyectosAsignables", "proyectosConAsignacion", "cuposOcupados"))
                .andExpect(content().string(containsString("id=\"proyectosModal\"")))
                .andExpect(content().string(containsString("data-origen=\"perfil\"")))
                .andExpect(content().string(containsString("Ya asignado o propuesto")));
    }

    private MockHttpServletRequestBuilder propuesta(String origen, String horas) {
        return post(PROPONER)
                .param("proyectoId", proyecto.getId().toString())
                .param("colaboradorId", colaborador.getId().toString())
                .param("horasSemanales", horas)
                .param("justificacion", "Encaja con el proyecto.")
                .param("origen", origen);
    }

    // Ocupa cupos del proyecto con otros colaboradores: primero las activas y luego las pendientes.
    private void llenarCupos(int activas, int pendientes) {
        List<Usuario> otros = otrosColaboradores(activas + pendientes);
        for (int i = 0; i < otros.size(); i++) {
            boolean activa = i < activas;
            guardarAsignacion(proyecto, otros.get(i), OrigenAsignacion.PROPUESTA_PM,
                    activa ? EstadoAsignacion.ACTIVA : EstadoAsignacion.PENDIENTE,
                    true, activa, LocalDateTime.now());
        }
    }

    // Inactivos para no aparecer en los candidatos de otras pruebas; solo ocupan cupos.
    private List<Usuario> otrosColaboradores(int cantidad) {
        Rol rolColaborador = obtenerRol("COLABORADOR");
        List<Usuario> otros = new ArrayList<>();
        for (int i = 1; i <= cantidad; i++) {
            Usuario usuario = obtenerUsuario("cupo034." + i + "@skillbridge.test", "Cupo034", "C" + i, rolColaborador);
            usuario.setActivo(false);
            otros.add(usuarioRepository.save(usuario));
        }
        return otros;
    }

    private long contarAuditoriasDePropuesta() {
        return logAuditoriaRepository.findAll().stream()
                .filter(log -> ACCIONES_PROPUESTA.contains(log.getAccion()))
                .count();
    }

    private LogAuditoria auditoriaDe(String accion, Long asignacionId) {
        return logAuditoriaRepository.findAll().stream()
                .filter(log -> accion.equals(log.getAccion()) && asignacionId.equals(log.getEntidadId()))
                .findFirst().orElseThrow();
    }

    // ---- TASK-029: filtros GET y paginación de la búsqueda de candidatos en el servidor ----

    @Test
    void candidatosPaginanDeSeisEnSeisYLaVistaFuncionaSinJavaScript() throws Exception {
        crearLoteCandidatos();
        String proyectoId = proyecto.getId().toString();

        MvcResult primera = mockMvc.perform(get(BUSCAR).param("proyectoId", proyectoId).param("busqueda", "cand029"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("paginaActual", 1))
                .andExpect(model().attribute("totalPaginas", 2))
                .andExpect(model().attribute("totalRegistros", 9L))
                .andReturn();
        assertEquals(List.of("K01", "K02", "K03", "K04", "K05", "K06"), codigosCandidatos(primera));
        String html = primera.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("Mostrando 1-6 de 9 colaboradores"));
        assertTrue(html.contains("id=\"candidateFiltersForm\" method=\"get\""));
        assertTrue(html.contains("type=\"hidden\" name=\"proyectoId\" value=\"" + proyectoId + "\""));
        assertFalse(html.contains("rm-buscar-colaboradores-proyecto.js"));
        assertFalse(html.contains("data-search="));
        // El flujo de propuesta (modales) no cambia.
        assertTrue(html.contains("id=\"propuestaModal\"") && html.contains("id=\"perfilModal\""));

        MvcResult segunda = mockMvc.perform(get(BUSCAR).param("proyectoId", proyectoId)
                        .param("busqueda", "cand029").param("pagina", "2"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("paginaActual", 2))
                .andReturn();
        assertEquals(List.of("K07", "K08", "K09"), codigosCandidatos(segunda));
        assertTrue(segunda.getResponse().getContentAsString(StandardCharsets.UTF_8)
                .contains("Mostrando 7-9 de 9 colaboradores"));
    }

    @Test
    void enlacesDeCandidatosConservanProyectoIdYLosFiltros() throws Exception {
        crearLoteCandidatos();
        String proyectoId = proyecto.getId().toString();
        // Puede recibir otra: K01..K05, K07 y K09 (7), dos páginas.
        String html = mockMvc.perform(get(BUSCAR).param("proyectoId", proyectoId).param("busqueda", "cand029")
                        .param("disponibilidad", "0").param("carga", "available"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalRegistros", 7L))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        List<String> enlaces = enlaces(html, "/rm/proyectos/buscar-colaboradores\\?[^\"]*pagina=\\d+");
        assertEquals(4, enlaces.size()); // Anterior, 1, 2, Siguiente
        for (String enlace : enlaces) {
            assertTrue(enlace.contains("proyectoId=" + proyectoId), enlace);
            assertTrue(enlace.contains("busqueda=cand029"), enlace);
            assertTrue(enlace.contains("disponibilidad=0"), enlace);
            assertTrue(enlace.contains("carga=available"), enlace);
        }
        assertTrue(html.contains("value=\"cand029\""));
        assertTrue(html.contains("<option value=\"available\" selected=\"selected\">"));
        // "Limpiar filtros" también conserva el proyecto.
        assertTrue(html.contains("href=\"/rm/proyectos/buscar-colaboradores?proyectoId=" + proyectoId + "\""));

        String siguiente = enlaces.get(enlaces.size() - 1);
        assertTrue(siguiente.endsWith("pagina=2"), siguiente);
        MvcResult segunda = mockMvc.perform(conParametros(BUSCAR, siguiente.substring(siguiente.indexOf('?') + 1)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("paginaActual", 2))
                .andExpect(model().attribute("carga", "available"))
                .andReturn();
        assertEquals(List.of("K09"), codigosCandidatos(segunda));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', value = {
            "busqueda=cand029 | K01,K02,K03,K04,K05,K06,K07,K08,K09",
            "busqueda=  CAND029 k03  | K03",
            "busqueda=cand029&disponibilidad=0 | K01,K02,K03,K04,K05,K06,K07,K08,K09",
            "busqueda=cand029&disponibilidad=8 | K01,K02,K03,K04,K05,K06,K07,K08",
            "busqueda=cand029&disponibilidad=16 | K04,K05,K06,K07,K08",
            "busqueda=cand029&disponibilidad=24 | K06,K07,K08",
            "busqueda=cand029&carga=available | K01,K02,K03,K04,K05,K07,K09",
            "busqueda=cand029&carga=full | K06,K08",
            "busqueda=cand029&disponibilidad=16&carga=available | K04,K05,K07",
            "busqueda=cand029&disponibilidad=24&carga=FULL | K06,K08",
    })
    void filtrosDeCandidatosAplicanElCriterioAnterior(String filtros, String esperados) throws Exception {
        crearLoteCandidatos();
        assertEquals(Arrays.asList(esperados.split(",")), codigosDeTodasLasPaginas(filtros));
    }

    @Test
    void candidatosConservanLasExclusionesActuales() throws Exception {
        List<Usuario> lote = crearLoteCandidatos();
        // Mismo nombre del lote, pero inactivo o con otro rol: no son candidatos.
        Usuario inactivo = obtenerUsuario("cand029.z98@skillbridge.test", "Cand029", "Z98", obtenerRol("COLABORADOR"));
        inactivo.setActivo(false);
        usuarioRepository.save(inactivo);
        Usuario otroRol = obtenerUsuario("cand029.z99@skillbridge.test", "Cand029", "Z99",
                obtenerRol("PROJECT_MANAGER"));
        otroRol.setActivo(true);
        loteCandidatos.add(usuarioRepository.save(otroRol));
        // K01 ya tiene una propuesta pendiente en este proyecto: sigue listado con "Proponer" bloqueado.
        guardarAsignacion(proyecto, lote.get(0), OrigenAsignacion.PROPUESTA_RM, EstadoAsignacion.PENDIENTE,
                false, true, LocalDateTime.now());

        assertEquals(List.of("K01", "K02", "K03", "K04", "K05", "K06", "K07", "K08", "K09"),
                codigosDeTodasLasPaginas("busqueda=cand029"));
        String html = mockMvc.perform(get(BUSCAR).param("proyectoId", proyecto.getId().toString())
                        .param("busqueda", "cand029"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("colaboradoresConAsignacion",
                        org.hamcrest.Matchers.hasItem(lote.get(0).getId())))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(1, contar(html, ">Ya en el proyecto</span>"));
        assertEquals(5, contar(html, "data-abrir-propuesta"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', value = {
            HAB_VALIDADA + " | true",
            HAB_PENDIENTE + " | false",
            HAB_RECHAZADA + " | false",
            HAB_INACTIVA + " | false",
    })
    void busquedaDeCandidatosSoloCoincideConHabilidadesValidadas(String habilidad, boolean encontrado)
            throws Exception {
        MvcResult resultado = mockMvc.perform(get(BUSCAR).param("proyectoId", proyecto.getId().toString())
                        .param("busqueda", habilidad))
                .andExpect(status().isOk())
                .andReturn();
        List<Long> ids = candidatosDeLaPagina(resultado).stream()
                .map(candidato -> candidato.resumen().getId())
                .toList();
        // Solo Carla tiene estas habilidades de prueba.
        assertEquals(encontrado ? List.of(colaborador.getId()) : List.of(), ids);
        if (!encontrado) {
            assertTrue(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8)
                    .contains("No se encontraron colaboradores con esos filtros."));
        }
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', value = {
            "disponibilidad=abc | 1",
            "disponibilidad=12 | 1",
            "disponibilidad= | 1",
            "carga=xyz | 1",
            "carga=all | 1",
            "pagina=abc | 1",
            "pagina=0 | 1",
            "pagina=-2 | 1",
            "pagina=99 | 2",
    })
    void parametrosInvalidosDeCandidatosVuelvenAValoresSeguros(String filtros, int paginaEsperada)
            throws Exception {
        crearLoteCandidatos();
        mockMvc.perform(conParametros(BUSCAR,
                        "proyectoId=" + proyecto.getId() + "&busqueda=cand029&" + filtros))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-buscar-colaboradores-proyecto"))
                .andExpect(model().attribute("totalRegistros", 9L))
                .andExpect(model().attribute("paginaActual", paginaEsperada))
                .andExpect(model().attribute("disponibilidad", "0"))
                .andExpect(model().attribute("carga", nullValue()));
    }

    /**
     * Crea 9 colaboradores "Cand029 K01".."K09" (horas disponibles, asignaciones activas en otros
     * proyectos): K01 8 0 · K02 8 0 · K03 12 1 · K04 16 2 · K05 20 0 · K06 24 máx ·
     * K07 30 0 · K08 24 máx · K09 0 0.
     */
    private List<Usuario> crearLoteCandidatos() {
        int maximo = consultaService.listarColaboradoresActivos().get(0).getMaxAsignaciones();
        Object[][] datos = {
                {"K01", "8", 0}, {"K02", "8", 0}, {"K03", "12", 1}, {"K04", "16", 2}, {"K05", "20", 0},
                {"K06", "24", maximo}, {"K07", "30", 0}, {"K08", "24", maximo}, {"K09", "0", 0},
        };
        List<Proyecto> cargas = new ArrayList<>();
        for (int i = 0; i < maximo; i++) {
            Proyecto carga = new Proyecto();
            carga.setNombre("Carga Cand029 " + i + " " + System.nanoTime());
            carga.setDescripcion("Proyecto para la carga de los candidatos de TASK-029.");
            carga.setEstado(EstadoProyecto.ACTIVO);
            carga.setPrioridad(Prioridad.BAJA);
            carga.setJustificacionPrioridad("Filtros de candidatos.");
            carga.setColaboradoresRequeridos(20);
            carga.setPm(pm);
            cargas.add(proyectoRepository.save(carga));
        }
        Rol rolColaborador = obtenerRol("COLABORADOR");
        List<Usuario> lote = new ArrayList<>();
        for (Object[] fila : datos) {
            String codigo = (String) fila[0];
            Usuario usuario = obtenerUsuario("cand029." + codigo.toLowerCase() + "@skillbridge.test",
                    "Cand029", codigo, rolColaborador);
            usuario.setActivo(true);
            usuario.setHorasDisponibles(new BigDecimal((String) fila[1]));
            usuario = usuarioRepository.save(usuario);
            lote.add(usuario);
            for (int i = 0; i < (int) fila[2]; i++) {
                guardarAsignacion(cargas.get(i), usuario, OrigenAsignacion.PROPUESTA_RM,
                        EstadoAsignacion.ACTIVA, true, true, LocalDateTime.now());
            }
        }
        loteCandidatos.addAll(lote);
        return lote;
    }

    // Recorre todas las páginas de la búsqueda del proyecto y devuelve los códigos en orden.
    private List<String> codigosDeTodasLasPaginas(String filtros) throws Exception {
        List<String> codigos = new ArrayList<>();
        int pagina = 1;
        int totalPaginas;
        do {
            MvcResult resultado = mockMvc.perform(conParametros(BUSCAR,
                            "proyectoId=" + proyecto.getId() + "&" + filtros + "&pagina=" + pagina))
                    .andExpect(status().isOk())
                    .andReturn();
            totalPaginas = (int) resultado.getModelAndView().getModel().get("totalPaginas");
            codigos.addAll(codigosCandidatos(resultado));
            pagina++;
        } while (pagina <= totalPaginas);
        return codigos;
    }

    private List<String> codigosCandidatos(MvcResult resultado) {
        return candidatosDeLaPagina(resultado).stream()
                .map(candidato -> candidato.resumen().getNombreCompleto())
                .map(nombre -> nombre.substring(nombre.lastIndexOf(' ') + 1))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<RmColaboradorConsultaService.CandidatoConCosto> candidatosDeLaPagina(MvcResult resultado) {
        List<RmColaboradorConsultaService.CandidatoConCosto> filas =
                (List<RmColaboradorConsultaService.CandidatoConCosto>) resultado.getModelAndView()
                        .getModel().get("candidatos");
        assertTrue(filas.size() <= RmColaboradorConsultaService.TAMANIO_PAGINA);
        return filas;
    }

    // Igual que conParametros(consulta), para otra ruta.
    private MockHttpServletRequestBuilder conParametros(String ruta, String consulta) {
        MockHttpServletRequestBuilder solicitud = get(ruta);
        for (String par : consulta.split("&")) {
            int igual = par.indexOf('=');
            if (igual > 0) solicitud.param(par.substring(0, igual), par.substring(igual + 1));
        }
        return solicitud;
    }

    private void assertSinHabilidadesNoValidadas(String html) {
        assertFalse(html.contains(HAB_PENDIENTE));
        assertFalse(html.contains(HAB_RECHAZADA));
        assertFalse(html.contains(HAB_INACTIVA));
    }

    private EstadoValidacion estadoGuardado(String nombreHabilidad) {
        Habilidad habilidad = habilidadRepository.findByNombreIgnoreCase(nombreHabilidad).orElseThrow();
        return colaboradorHabilidadRepository.findByColaboradorAndHabilidad(colaborador, habilidad)
                .orElseThrow().getEstadoValidacion();
    }

    private void asignarHabilidad(String nombre, EstadoValidacion estado, boolean activo) {
        CategoriaHabilidad categoria = categoriaRepository.findByNombreIgnoreCase("Asignaciones T015")
                .orElseGet(() -> {
                    CategoriaHabilidad nueva = new CategoriaHabilidad();
                    nueva.setNombre("Asignaciones T015");
                    return categoriaRepository.save(nueva);
                });
        Habilidad habilidad = habilidadRepository.findByNombreIgnoreCase(nombre).orElseGet(() -> {
            Habilidad nueva = new Habilidad();
            nueva.setNombre(nombre);
            nueva.setCategoria(categoria);
            return habilidadRepository.save(nueva);
        });
        ColaboradorHabilidad perfil = colaboradorHabilidadRepository
                .findByColaboradorAndHabilidad(colaborador, habilidad)
                .orElseGet(() -> {
                    ColaboradorHabilidad nueva = new ColaboradorHabilidad();
                    nueva.setColaborador(colaborador);
                    nueva.setHabilidad(habilidad);
                    return nueva;
                });
        perfil.setNivelDominio(NivelDominio.AVANZADO);
        perfil.setEstadoValidacion(estado);
        perfil.setActivo(activo);
        colaboradorHabilidadRepository.save(perfil);
    }

    private void autenticarRm() {
        UsuarioDetails details = new UsuarioDetails(usuarioRepository.findActivosByRolNombre("RESOURCE_MANAGER")
                .stream().filter(u -> u.getId().equals(rm.getId())).findFirst().orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    // Simula la solicitud GET que hace el navegador tras la redirección, con el flash del POST.
    private String renderizarTrasRedireccion(String destino, MvcResult resultadoPost) throws Exception {
        return mockMvc.perform(get(destino).flashAttrs(resultadoPost.getFlashMap()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private int contar(String texto, String fragmento) {
        return texto.split(java.util.regex.Pattern.quote(fragmento), -1).length - 1;
    }

    private Asignacion guardarPendiente(
            OrigenAsignacion origen, boolean aprobadoPm, boolean aprobadoRm, String horas) {
        Asignacion asignacion = new Asignacion();
        asignacion.setProyecto(proyecto);
        asignacion.setColaborador(colaborador);
        asignacion.setHorasSemanales(new BigDecimal(horas));
        asignacion.setOrigen(origen);
        asignacion.setEstado(EstadoAsignacion.PENDIENTE);
        asignacion.setAprobadoPorPm(aprobadoPm);
        asignacion.setAprobadoPorRm(aprobadoRm);
        asignacion.setMensajeSolicitud("Solicitud de prueba.");
        return asignacionRepository.save(asignacion);
    }

    private Asignacion guardarAsignacion(Proyecto destino, Usuario persona, OrigenAsignacion origen,
                                         EstadoAsignacion estado, boolean aprobadoPm, boolean aprobadoRm,
                                         LocalDateTime fechaSolicitud) {
        Asignacion asignacion = new Asignacion();
        asignacion.setProyecto(destino);
        asignacion.setColaborador(persona);
        asignacion.setHorasSemanales(new BigDecimal("8"));
        asignacion.setOrigen(origen);
        asignacion.setEstado(estado);
        asignacion.setAprobadoPorPm(aprobadoPm);
        asignacion.setAprobadoPorRm(aprobadoRm);
        asignacion.setMensajeSolicitud("Solicitud de prueba.");
        asignacion.setFechaSolicitud(fechaSolicitud);
        return asignacionRepository.save(asignacion);
    }

    /**
     * Bandeja de ejemplo (TASK-027). En el proyecto principal, con Carla:
     * A Pendiente RM (PM), B Pendiente PM (RM), C Pendiente RM y PM (Colaborador sin aprobaciones),
     * D Pendiente RM (Colaborador con PM), E Activa, F Finalizada, G Rechazada.
     * En otro proyecto ("Proyecto Beta T027"), con Zoila: H Pendiente RM (PM).
     */
    private Map<String, Long> crearBandejaDeEjemplo() {
        Usuario zoila = obtenerUsuario("zoila.t027@skillbridge.test", "Zoila", "Buscada",
                obtenerRol("COLABORADOR"));
        Proyecto otro = new Proyecto();
        otro.setNombre("Proyecto Beta T027 " + System.nanoTime());
        otro.setDescripcion("Segundo proyecto para los filtros de la bandeja.");
        otro.setEstado(EstadoProyecto.ACTIVO);
        otro.setPrioridad(Prioridad.MEDIA);
        otro.setJustificacionPrioridad("Filtros de asignaciones.");
        otro.setColaboradoresRequeridos(2);
        otro.setPm(pm);
        otro = proyectoRepository.save(otro);

        LocalDateTime base = LocalDateTime.now().withNano(0);
        Map<String, Long> ids = new HashMap<>();
        ids.put("A", guardarAsignacion(proyecto, colaborador, OrigenAsignacion.PROPUESTA_PM,
                EstadoAsignacion.PENDIENTE, false, false, base.minusHours(1)).getId());
        ids.put("B", guardarAsignacion(proyecto, colaborador, OrigenAsignacion.PROPUESTA_RM,
                EstadoAsignacion.PENDIENTE, false, true, base.minusHours(2)).getId());
        ids.put("C", guardarAsignacion(proyecto, colaborador, OrigenAsignacion.SOLICITADA_COLABORADOR,
                EstadoAsignacion.PENDIENTE, false, false, base.minusHours(3)).getId());
        ids.put("D", guardarAsignacion(proyecto, colaborador, OrigenAsignacion.SOLICITADA_COLABORADOR,
                EstadoAsignacion.PENDIENTE, true, false, base.minusHours(4)).getId());
        ids.put("E", guardarAsignacion(proyecto, colaborador, OrigenAsignacion.PROPUESTA_PM,
                EstadoAsignacion.ACTIVA, true, true, base.minusHours(5)).getId());
        ids.put("F", guardarAsignacion(proyecto, colaborador, OrigenAsignacion.PROPUESTA_PM,
                EstadoAsignacion.FINALIZADA, true, true, base.minusHours(6)).getId());
        ids.put("G", guardarAsignacion(proyecto, colaborador, OrigenAsignacion.PROPUESTA_PM,
                EstadoAsignacion.RECHAZADA, false, false, base.minusHours(7)).getId());
        ids.put("H", guardarAsignacion(otro, zoila, OrigenAsignacion.PROPUESTA_PM,
                EstadoAsignacion.PENDIENTE, false, false, base.minusHours(8)).getId());
        ids.put("proyectoOtro", otro.getId());
        return ids;
    }

    // Convierte "a=1&b=2" en parámetros GET (sin codificar) de /rm/asignaciones.
    private MockHttpServletRequestBuilder conParametros(String consulta) {
        MockHttpServletRequestBuilder solicitud = get("/rm/asignaciones");
        if (consulta == null || consulta.isEmpty()) return solicitud;
        for (String par : consulta.split("&")) {
            int igual = par.indexOf('=');
            solicitud.param(par.substring(0, igual), par.substring(igual + 1));
        }
        return solicitud;
    }

    @SuppressWarnings("unchecked")
    private List<Long> idsDeLaPagina(MvcResult resultado) {
        return ((List<RmAsignacionView>) resultado.getModelAndView().getModel().get("asignaciones")).stream()
                .map(item -> item.getAsignacion().getId())
                .toList();
    }

    // Enlaces href que cumplen el patrón, ya sin escapar (&amp;) y decodificados.
    private List<String> enlaces(String html, String patron) {
        Matcher matcher = Pattern.compile("href=\"(" + patron + ")\"").matcher(html);
        List<String> encontrados = new ArrayList<>();
        while (matcher.find()) {
            encontrados.add(URLDecoder.decode(matcher.group(1).replace("&amp;", "&"), StandardCharsets.UTF_8));
        }
        return encontrados;
    }

    private Rol obtenerRol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol rol = new Rol();
            rol.setNombre(nombre);
            return rolRepository.save(rol);
        });
    }

    private Usuario obtenerUsuario(String correo, String nombre, String apellido, Rol rol) {
        return usuarioRepository.findByCorreo(correo).orElseGet(() -> {
            Usuario usuario = new Usuario();
            usuario.setCorreo(correo);
            usuario.setNombre(nombre);
            usuario.setApellido(apellido);
            usuario.setRol(rol);
            return usuarioRepository.save(usuario);
        });
    }

    private Cargo cargoDePrueba(String nombre) {
        return cargoRepository.findByNombre(nombre).orElseGet(() -> cargoRepository.save(
                new Cargo(nombre, new BigDecimal("2000"), new BigDecimal("3000"), new BigDecimal("4000"))));
    }
}
