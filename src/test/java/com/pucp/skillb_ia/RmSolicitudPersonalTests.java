package com.pucp.skillb_ia;

import com.pucp.skillb_ia.dto.RmSolicitudPersonalView;
import com.pucp.skillb_ia.model.CategoriaHabilidad;
import com.pucp.skillb_ia.model.Habilidad;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.ProyectoHabilidadRequerida;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.SolicitudPersonal;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.EstadoSolicitudPersonal;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.CategoriaHabilidadRepository;
import com.pucp.skillb_ia.repository.HabilidadRepository;
import com.pucp.skillb_ia.repository.ProyectoHabilidadRequeridaRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.SolicitudPersonalRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.rm.RmSolicitudPersonalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@ActiveProfiles("test")
class RmSolicitudPersonalTests {

    @Autowired private WebApplicationContext context;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private SolicitudPersonalRepository solicitudRepository;
    @Autowired private RmSolicitudPersonalService solicitudService;
    @Autowired private CategoriaHabilidadRepository categoriaRepository;
    @Autowired private HabilidadRepository habilidadRepository;
    @Autowired private ProyectoHabilidadRequeridaRepository habilidadRequeridaRepository;

    private MockMvc mockMvc;
    private Usuario rm;
    private Usuario pm;
    private Proyecto proyecto;

    @BeforeEach
    void prepararDatos() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        solicitudRepository.deleteAll();
        rm = obtenerUsuario("rm.solicitudes@skillbridge.test", "Rosa", "Mendoza",
                obtenerRol("RESOURCE_MANAGER"));
        pm = obtenerUsuario("pm.solicitudes@skillbridge.test", "Pablo", "Morales",
                obtenerRol("PROJECT_MANAGER"));

        proyecto = new Proyecto();
        proyecto.setNombre("Proyecto solicitudes " + System.nanoTime());
        proyecto.setDescripcion("Proyecto para validar solicitudes de personal.");
        proyecto.setEstado(EstadoProyecto.ACTIVO);
        proyecto.setPrioridad(Prioridad.ALTA);
        proyecto.setJustificacionPrioridad("Debe completar el equipo pronto.");
        proyecto.setColaboradoresRequeridos(3);
        proyecto.setPm(pm);
        proyecto = proyectoRepository.save(proyecto);
    }

    @Test
    void creaSolicitudSeparadaDeUnaAsignacion() {
        SolicitudPersonal solicitud = solicitudService.crearDesdePm(
                proyecto.getId(), 2, "Backend y DevOps", "Priorizar Docker.", pm.getId());

        assertNotNull(solicitud.getId());
        assertEquals(EstadoSolicitudPersonal.PENDIENTE, solicitud.getEstado());
        assertEquals(2, solicitud.getCantidadColaboradores());
        assertNull(solicitud.getRmResponsable());
    }

    @Test
    void recorrePendienteEnAtencionYAtendida() {
        SolicitudPersonal solicitud = crearSolicitud();

        solicitudService.iniciarAtencion(solicitud.getId(), rm.getId());
        SolicitudPersonal iniciada = solicitudRepository.findById(solicitud.getId()).orElseThrow();
        assertEquals(EstadoSolicitudPersonal.EN_ATENCION, iniciada.getEstado());
        assertEquals(rm.getId(), iniciada.getRmResponsable().getId());
        assertNotNull(iniciada.getFechaInicioAtencion());

        solicitudService.marcarAtendida(solicitud.getId(), rm.getId());
        SolicitudPersonal atendida = solicitudRepository.findById(solicitud.getId()).orElseThrow();
        assertEquals(EstadoSolicitudPersonal.ATENDIDA, atendida.getEstado());
        assertNotNull(atendida.getFechaAtencion());
    }

    @Test
    void impideDosSolicitudesAbiertasParaElMismoProyecto() {
        crearSolicitud();
        assertThrows(IllegalStateException.class, () -> solicitudService.crearDesdePm(
                proyecto.getId(), 1, null, null, pm.getId()));
    }

    @Test
    void renderizaListadoDetalleYVistaDeProyecto() throws Exception {
        SolicitudPersonal solicitud = crearSolicitud();

        mockMvc.perform(get("/rm/asignaciones/solicitudes-colaboradores"))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-solicitudes-colaboradores"))
                .andExpect(model().attributeExists("solicitudes", "pendientes", "enAtencion", "atendidas"));

        mockMvc.perform(get("/rm/asignaciones/solicitudes-colaboradores/detalle")
                        .param("id", solicitud.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-detalle-solicitud-colaboradores"))
                .andExpect(model().attributeExists("solicitud"));

        mockMvc.perform(get("/rm/proyectos/detalle-solicitudes")
                        .param("solicitudId", solicitud.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-detalle-proyecto-solicitudes"))
                .andExpect(model().attributeExists("solicitud", "proyecto"));
    }

    @Test
    void iniciarAtencionMuestraElMensajeDeExitoEnLaBusqueda() throws Exception {
        SolicitudPersonal solicitud = crearSolicitud();
        String destino = "/rm/proyectos/buscar-colaboradores?proyectoId=" + proyecto.getId();
        UsuarioDetails details = new UsuarioDetails(usuarioRepository.findActivosByRolNombre("RESOURCE_MANAGER")
                .stream().filter(u -> u.getId().equals(rm.getId())).findFirst().orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
        try {
            MvcResult resultado = mockMvc.perform(
                            post("/rm/asignaciones/solicitudes-colaboradores/" + solicitud.getId() + "/iniciar"))
                    .andExpect(redirectedUrl(destino))
                    .andExpect(flash().attribute("mensajeExito", "La solicitud pasó a En atención."))
                    .andExpect(flash().attributeCount(1))
                    .andReturn();

            // Simula la solicitud GET que hace el navegador tras la redirección, con el flash del POST.
            String html = mockMvc.perform(get(destino).flashAttrs(resultado.getFlashMap()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("rm/rm-buscar-colaboradores-proyecto"))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertTrue(html.contains(
                    "<div class=\"alert alert-success\" role=\"alert\">La solicitud pasó a En atención.</div>"));
            assertEquals(1, html.split("La solicitud pasó a En atención.", -1).length - 1);
            assertFalse(html.contains("class=\"alert alert-danger\" role=\"alert\""));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void busquedaMuestraErrorYOmiteMensajesVacios() throws Exception {
        String destino = "/rm/proyectos/buscar-colaboradores?proyectoId=" + proyecto.getId();

        String html = mockMvc.perform(get(destino).flashAttr("mensajeError", "No se pudo proponer."))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("<div class=\"alert alert-danger\" role=\"alert\">No se pudo proponer.</div>"));
        assertFalse(html.contains("class=\"alert alert-success\" role=\"alert\""));

        html = mockMvc.perform(get(destino).flashAttr("mensajeExito", "").flashAttr("mensajeError", " "))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(html.contains("class=\"alert alert-success\" role=\"alert\""));
        assertFalse(html.contains("class=\"alert alert-danger\" role=\"alert\""));
    }

    // ---- TASK-038: filtros y paginación en el servidor ----

    private static final String LISTADO = "/rm/asignaciones/solicitudes-colaboradores";

    @Test
    void paginaUnoYDosConMasDeSeisSolicitudesOrdenadasPorFechaDescendente() throws Exception {
        // Se crean desordenadas; el listado debe ir de la más reciente a la más antigua.
        int[] horasAtras = {3, 0, 7, 1, 5, 2, 6, 4};
        Map<Integer, Long> idPorHoras = new LinkedHashMap<>();
        for (int horas : horasAtras) {
            SolicitudPersonal solicitud = crearSolicitudDirecta(crearProyecto("Orden " + horas, Prioridad.MEDIA),
                    EstadoSolicitudPersonal.PENDIENTE, horas, 1);
            idPorHoras.put(horas, solicitud.getId());
        }
        List<Long> esperados = new ArrayList<>();
        for (int horas = 0; horas < 8; horas++) esperados.add(idPorHoras.get(horas));

        MvcResult pagina1 = mockMvc.perform(get(LISTADO))
                .andExpect(status().isOk())
                .andExpect(model().attribute("paginaActual", 1))
                .andExpect(model().attribute("totalPaginas", 2))
                .andExpect(model().attribute("totalRegistros", 8L))
                .andReturn();
        MvcResult pagina2 = mockMvc.perform(get(LISTADO).param("pagina", "2"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("paginaActual", 2))
                .andReturn();

        assertEquals(esperados.subList(0, 6), idsDe(pagina1));
        assertEquals(esperados.subList(6, 8), idsDe(pagina2));
        assertTrue(html(pagina1).contains("Mostrando 1-6 de 8 solicitudes"));
        assertTrue(html(pagina2).contains("Mostrando 7-8 de 8 solicitudes"));
    }

    @ParameterizedTest(name = "[{index}] busqueda=''{0}'' estado=''{1}'' prioridad=''{2}'' → {3}")
    @CsvSource({
            "PORTAL,          ,            ,         A C E",
            "pablo MORALES,   ,            ,         A B C D E",
            "kubernetes t038, ,            ,         B",
            "migración,       ,            ,         B",
            "sin coincidencias,,           ,         ''",
            ",                EN_ATENCION, ,         B E",
            ",                cancelada,   ,         D",
            ",                ,            ALTA,     A D E",
            ",                ,            baja,     C",
            "portal,          EN_ATENCION, ALTA,     E",
            "portal,          ,            ALTA,     A E",
            ",                all,         all,      A B C D E",
            ",                XYZ,         URGENTE,  A B C D E"
    })
    void filtraPorBusquedaEstadoYPrioridadEnElServidor(String busqueda, String estado, String prioridad,
                                                       String esperados) throws Exception {
        Map<String, Long> escenario = crearEscenarioFiltros();
        var peticion = get(LISTADO);
        if (busqueda != null) peticion.param("busqueda", busqueda);
        if (estado != null) peticion.param("estado", estado);
        if (prioridad != null) peticion.param("prioridad", prioridad);

        MvcResult resultado = mockMvc.perform(peticion)
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-solicitudes-colaboradores"))
                .andReturn();

        // El escenario tiene fechas descendentes de A a E, así que el orden esperado es el alfabético.
        List<Long> ids = Arrays.stream(esperados.trim().split("\\s+"))
                .filter(etiqueta -> !etiqueta.isBlank())
                .map(escenario::get)
                .toList();
        assertEquals(ids, idsDe(resultado));
    }

    @Test
    void losEnlacesDePaginacionYElFormularioConservanLosFiltros() throws Exception {
        for (int i = 0; i < 8; i++) {
            crearSolicitudDirecta(crearProyecto("Portal Enlaces " + i, Prioridad.ALTA),
                    EstadoSolicitudPersonal.PENDIENTE, i, 1);
        }
        crearSolicitudDirecta(crearProyecto("Otro proyecto", Prioridad.ALTA), EstadoSolicitudPersonal.PENDIENTE, 9, 1);

        String html = html(mockMvc.perform(get(LISTADO)
                        .param("busqueda", "portal")
                        .param("estado", "PENDIENTE")
                        .param("prioridad", "ALTA"))
                .andExpect(model().attribute("totalRegistros", 8L))
                .andExpect(model().attribute("totalPaginas", 2))
                .andReturn());

        String base = "href=\"" + LISTADO + "?busqueda=portal&amp;estado=PENDIENTE&amp;prioridad=ALTA&amp;pagina=";
        assertTrue(html.contains(base + "0\">Anterior</a>"));
        assertTrue(html.contains(base + "1\">1</a>"));
        assertTrue(html.contains(base + "2\">2</a>"));
        assertTrue(html.contains(base + "2\">Siguiente</a>"));
        assertTrue(html.contains("value=\"portal\""));
        assertTrue(html.contains("<option value=\"PENDIENTE\" selected=\"selected\">Pendiente</option>"));
        assertTrue(html.contains("<option value=\"ALTA\" selected=\"selected\">Alta</option>"));

        // En la página 2 "Anterior" vuelve a la 1 con los mismos filtros.
        String html2 = html(mockMvc.perform(get(LISTADO)
                        .param("busqueda", "portal")
                        .param("estado", "PENDIENTE")
                        .param("prioridad", "ALTA")
                        .param("pagina", "2"))
                .andReturn());
        assertTrue(html2.contains(base + "1\">Anterior</a>"));
        assertTrue(html2.contains("Mostrando 7-8 de 8 solicitudes"));
    }

    @Test
    void losIndicadoresSeCalculanSobreTodasLasSolicitudes() throws Exception {
        crearEscenarioFiltros();

        MvcResult resultado = mockMvc.perform(get(LISTADO)
                        .param("estado", "CANCELADA")
                        .param("busqueda", "app")
                        .param("pagina", "3"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalRegistros", 1L))
                .andExpect(model().attribute("pendientes", 1L))
                .andExpect(model().attribute("enAtencion", 2L))
                .andExpect(model().attribute("atendidas", 1L))
                .andExpect(model().attribute("totalSolicitados", 15L))
                .andReturn();
        assertEquals(1, idsDe(resultado).size());
    }

    @ParameterizedTest(name = "[{index}] pagina=''{0}'' → {1}")
    @CsvSource({
            "'',   1",
            "0,    1",
            "-4,   1",
            "abc,  1",
            "' 2 ', 2",
            "99,   2"
    })
    void paginaVaciaCeroNegativaOExcesivaNoProduceErrorTecnico(String pagina, int esperada) throws Exception {
        for (int i = 0; i < 8; i++) {
            crearSolicitudDirecta(crearProyecto("Pagina " + i, Prioridad.BAJA), EstadoSolicitudPersonal.PENDIENTE, i, 1);
        }

        mockMvc.perform(get(LISTADO).param("pagina", pagina))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-solicitudes-colaboradores"))
                .andExpect(model().attribute("paginaActual", esperada))
                .andExpect(model().attribute("totalPaginas", 2));
    }

    @Test
    void sinResultadosLaPaginaExcesivaMuestraElListadoVacio() throws Exception {
        crearEscenarioFiltros();

        String html = html(mockMvc.perform(get(LISTADO).param("busqueda", "no existe").param("pagina", "5"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("paginaActual", 1))
                .andExpect(model().attribute("totalPaginas", 1))
                .andExpect(model().attribute("totalRegistros", 0L))
                .andReturn());
        assertTrue(html.contains("Mostrando 0-0 de 0 solicitudes"));
        assertTrue(html.contains("No se encontraron solicitudes."));
        assertFalse(html.contains("id=\"pagination\""));
    }

    @Test
    void elHtmlFuncionaSinJavaScript() throws Exception {
        for (int i = 0; i < 8; i++) {
            crearSolicitudDirecta(crearProyecto("Sin JS " + i, Prioridad.MEDIA), EstadoSolicitudPersonal.PENDIENTE, i, 1);
        }

        String html = html(mockMvc.perform(get(LISTADO)).andExpect(status().isOk()).andReturn());

        assertTrue(html.contains("<form id=\"filtersForm\" method=\"get\""));
        assertTrue(html.contains("action=\"" + LISTADO + "\""));
        assertTrue(html.contains("name=\"busqueda\""));
        assertTrue(html.contains("name=\"estado\""));
        assertTrue(html.contains("name=\"prioridad\""));
        assertTrue(html.contains("<button type=\"submit\" class=\"btn btn-primary flex-fill\">Filtrar</button>"));
        assertTrue(html.contains("href=\"" + LISTADO + "\">Limpiar</a>"));
        // Solo se envían al navegador las 6 filas de la página, sin datos para filtrar en JS.
        assertEquals(6, html.split("class=\"request-row\"", -1).length - 1);
        assertFalse(html.contains("data-search="));
        assertFalse(html.contains("rm-solicitudes-colaboradores.js"));
        assertFalse(html.contains("class=\"page-link\" href=\"#\""));
        assertFalse(Files.exists(Path.of("src/main/resources/static/js/rm-js/rm-solicitudes-colaboradores.js")));
    }

    @Test
    void lasAccionesDelListadoSiguenFuncionando() throws Exception {
        SolicitudPersonal solicitud = crearSolicitud();

        String html = html(mockMvc.perform(get(LISTADO)).andExpect(status().isOk()).andReturn());
        assertTrue(html.contains("href=\"" + LISTADO + "/detalle?id=" + solicitud.getId() + "\">Atender</a>"));

        // noEncontrada sigue mostrando el aviso, también junto con filtros.
        mockMvc.perform(get(LISTADO + "/detalle").param("id", "999999"))
                .andExpect(redirectedUrl(LISTADO + "?noEncontrada=true"));
        String aviso = html(mockMvc.perform(get(LISTADO).param("noEncontrada", "true").param("estado", "PENDIENTE"))
                .andExpect(status().isOk()).andReturn());
        assertTrue(aviso.contains("La solicitud indicada no existe."));

        UsuarioDetails details = new UsuarioDetails(usuarioRepository.findActivosByRolNombre("RESOURCE_MANAGER")
                .stream().filter(u -> u.getId().equals(rm.getId())).findFirst().orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
        try {
            mockMvc.perform(post(LISTADO + "/" + solicitud.getId() + "/iniciar"))
                    .andExpect(redirectedUrl("/rm/proyectos/buscar-colaboradores?proyectoId=" + proyecto.getId()));
            mockMvc.perform(post(LISTADO + "/" + solicitud.getId() + "/atendida"))
                    .andExpect(redirectedUrl(LISTADO + "/detalle?id=" + solicitud.getId()))
                    .andExpect(flash().attribute("mensajeExito", "Solicitud marcada como atendida."));
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertEquals(EstadoSolicitudPersonal.ATENDIDA,
                solicitudRepository.findById(solicitud.getId()).orElseThrow().getEstado());

        mockMvc.perform(get(LISTADO).param("estado", "ATENDIDA"))
                .andExpect(model().attribute("atendidas", 1L))
                .andExpect(model().attribute("totalRegistros", 1L));
    }

    /** A-E con fechas descendentes (A es la más reciente). B tiene la habilidad "Kubernetes T038". */
    private Map<String, Long> crearEscenarioFiltros() {
        Map<String, Long> ids = new LinkedHashMap<>();
        ids.put("A", crearSolicitudDirecta(crearProyecto("Portal Clientes", Prioridad.ALTA),
                EstadoSolicitudPersonal.PENDIENTE, 1, 2).getId());
        Proyecto nube = crearProyecto("Migración Nube", Prioridad.MEDIA);
        agregarRequisito(nube, "Kubernetes T038");
        ids.put("B", crearSolicitudDirecta(nube, EstadoSolicitudPersonal.EN_ATENCION, 2, 3).getId());
        ids.put("C", crearSolicitudDirecta(crearProyecto("Portal Interno", Prioridad.BAJA),
                EstadoSolicitudPersonal.ATENDIDA, 3, 1).getId());
        ids.put("D", crearSolicitudDirecta(crearProyecto("App Móvil", Prioridad.ALTA),
                EstadoSolicitudPersonal.CANCELADA, 4, 4).getId());
        ids.put("E", crearSolicitudDirecta(crearProyecto("Portal Ventas", Prioridad.ALTA),
                EstadoSolicitudPersonal.EN_ATENCION, 5, 5).getId());
        return ids;
    }

    private Proyecto crearProyecto(String nombre, Prioridad prioridad) {
        Proyecto nuevo = new Proyecto();
        nuevo.setNombre(nombre + " " + System.nanoTime());
        nuevo.setDescripcion("Proyecto para validar filtros de solicitudes.");
        nuevo.setEstado(EstadoProyecto.ACTIVO);
        nuevo.setPrioridad(prioridad);
        nuevo.setJustificacionPrioridad("Prueba de filtros.");
        nuevo.setColaboradoresRequeridos(3);
        nuevo.setPm(pm);
        return proyectoRepository.save(nuevo);
    }

    // TASK-031: la fila vacía usa el fragmento común, conserva id, colspan y la clase d-none cuando hay filas.
    @Test
    void filaVaciaUsaElComponenteComunYConservaSuIdColspanYClase() throws Exception {
        String vacio = html(mockMvc.perform(get(LISTADO).param("busqueda", "sin-coincidencias-t031"))
                .andExpect(status().isOk()).andReturn());
        assertTrue(vacio.contains("<tr id=\"emptyRequests\"><td colspan=\"8\" class=\"empty-state-cell\">"
                + "<div class=\"empty-state\">"), vacio);
        assertTrue(vacio.contains("<div class=\"empty-state-title\">Sin solicitudes</div>"));
        assertTrue(vacio.contains("<div class=\"empty-state-text\">No se encontraron solicitudes.</div>"));
        assertFalse(vacio.contains("empty-state-action"));
        assertTrue(vacio.contains("value=\"sin-coincidencias-t031\""));

        crearSolicitudDirecta(crearProyecto("Con filas T031", Prioridad.MEDIA), EstadoSolicitudPersonal.PENDIENTE, 0, 1);
        String conFilas = html(mockMvc.perform(get(LISTADO)).andExpect(status().isOk()).andReturn());
        assertTrue(conFilas.contains("<tr id=\"emptyRequests\" class=\"d-none\"><td colspan=\"8\" class=\"empty-state-cell\">"),
                conFilas);
    }

    @Test
    void proyectoDeLaSolicitudSinEquipoNiRequisitosUsaElComponenteComun() throws Exception {
        SolicitudPersonal solicitud = crearSolicitudDirecta(
                crearProyecto("Sin equipo T031", Prioridad.BAJA), EstadoSolicitudPersonal.PENDIENTE, 0, 1);

        String html = html(mockMvc.perform(get("/rm/proyectos/detalle-solicitudes")
                        .param("solicitudId", solicitud.getId().toString()))
                .andExpect(status().isOk()).andReturn());

        assertEquals(2, html.split("class=\"empty-state\"", -1).length - 1);
        assertTrue(html.contains("<tr><td colspan=\"5\" class=\"empty-state-cell\"><div class=\"empty-state\">"));
        assertTrue(html.contains("<div class=\"empty-state-title\">Sin asignaciones activas</div>"));
        assertTrue(html.contains("El proyecto todavía no tiene asignaciones activas."));
        assertTrue(html.contains("<div class=\"empty-state-title\">Sin requisitos registrados</div>"));
        assertFalse(html.contains("empty-state-action"));
    }

    private SolicitudPersonal crearSolicitudDirecta(Proyecto destino, EstadoSolicitudPersonal estado,
                                                    int horasAtras, int cantidad) {
        SolicitudPersonal solicitud = new SolicitudPersonal();
        solicitud.setProyecto(destino);
        solicitud.setCantidadColaboradores(cantidad);
        solicitud.setEstado(estado);
        solicitud.setFechaSolicitud(LocalDateTime.now().minusHours(horasAtras));
        return solicitudRepository.save(solicitud);
    }

    private void agregarRequisito(Proyecto destino, String nombreHabilidad) {
        CategoriaHabilidad categoria = categoriaRepository.findByNombreIgnoreCase("Solicitudes T038")
                .orElseGet(() -> {
                    CategoriaHabilidad nueva = new CategoriaHabilidad();
                    nueva.setNombre("Solicitudes T038");
                    return categoriaRepository.save(nueva);
                });
        Habilidad habilidad = habilidadRepository.findByNombreIgnoreCase(nombreHabilidad).orElseGet(() -> {
            Habilidad nueva = new Habilidad();
            nueva.setNombre(nombreHabilidad);
            nueva.setCategoria(categoria);
            return habilidadRepository.save(nueva);
        });
        ProyectoHabilidadRequerida requisito = new ProyectoHabilidadRequerida();
        requisito.setProyecto(destino);
        requisito.setHabilidad(habilidad);
        habilidadRequeridaRepository.save(requisito);
    }

    @SuppressWarnings("unchecked")
    private List<Long> idsDe(MvcResult resultado) {
        List<RmSolicitudPersonalView> filas =
                (List<RmSolicitudPersonalView>) resultado.getModelAndView().getModel().get("solicitudes");
        return filas.stream().map(item -> item.getSolicitud().getId()).toList();
    }

    private String html(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private SolicitudPersonal crearSolicitud() {
        return solicitudService.crearDesdePm(
                proyecto.getId(), 2, "Backend y DevOps", "Priorizar Docker.", pm.getId());
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
}
