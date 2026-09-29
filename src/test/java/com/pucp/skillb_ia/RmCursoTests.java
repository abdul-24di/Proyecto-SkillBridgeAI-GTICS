package com.pucp.skillb_ia;

import com.pucp.skillb_ia.dto.RmCursoView;
import com.pucp.skillb_ia.model.ColaboradorCurso;
import com.pucp.skillb_ia.model.Curso;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.Cargo;
import com.pucp.skillb_ia.repository.CargoRepository;
import com.pucp.skillb_ia.model.enums.EstadoColaboradorCurso;
import com.pucp.skillb_ia.model.enums.OrigenCurso;
import com.pucp.skillb_ia.repository.ColaboradorCursoRepository;
import com.pucp.skillb_ia.repository.CursoRepository;
import com.pucp.skillb_ia.repository.NotificacionRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.rm.RmCursoService;
import com.pucp.skillb_ia.service.NotificacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class RmCursoTests {
    @Autowired private WebApplicationContext context;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CargoRepository cargoRepository;
    @Autowired private CursoRepository cursoRepository;
    @Autowired private ColaboradorCursoRepository colaboradorCursoRepository;
    @Autowired private NotificacionRepository notificacionRepository;
    @Autowired private RmCursoService cursoService;
    @Autowired private NotificacionService notificacionService;

    private MockMvc mockMvc;
    private Usuario admin;
    private Usuario rm;
    private Usuario colaboradorUno;
    private Usuario colaboradorDos;
    private Curso spring;
    private Curso aws;
    private Curso inactivo;

    @BeforeEach
    void prepararDatos() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        notificacionRepository.deleteAll();
        colaboradorCursoRepository.deleteAll();
        cursoRepository.deleteAll();

        admin = usuario("admin.cursos@skillbridge.test", "Adriana", "Curso", rol("ADMIN"), null);
        rm = usuario("rm.cursos@skillbridge.test", "Rosa", "Mendoza", rol("RESOURCE_MANAGER"), null);
        colaboradorUno = usuario("ana.cursos@skillbridge.test", "Ana", "Torres", rol("COLABORADOR"), "Backend Developer");
        colaboradorDos = usuario("luis.cursos@skillbridge.test", "Luis", "Ramos", rol("COLABORADOR"), "Cloud Engineer");
        spring = curso("Spring Boot avanzado test", "Técnico", "20.00", true);
        aws = curso("AWS Practitioner test", "Certificación", "40.00", true);
        inactivo = curso("Curso inactivo test", "Técnico", "8.00", false);
    }

    @Test
    void catalogoMuestraSoloActivosYAplicaFiltrosReales() {
        inscripcion(colaboradorUno, spring, OrigenCurso.SOLICITUD_COLABORADOR,
                EstadoColaboradorCurso.SOLICITADO);
        inscripcion(colaboradorDos, aws, OrigenCurso.ASIGNADO_POR_RM,
                EstadoColaboradorCurso.EN_CURSO);

        RmCursoView.Catalogo catalogo = cursoService.obtenerCatalogo(
                cursoService.normalizarFiltrosCatalogo("", "", ""), null);
        RmCursoView.Catalogo filtrado = cursoService.obtenerCatalogo(
                cursoService.normalizarFiltrosCatalogo("spring", "Técnico", "media"), null);

        assertEquals(2, catalogo.getCursosActivos());
        assertEquals(1, catalogo.getSolicitudesPendientes());
        assertEquals(1, catalogo.getInscripcionesActivas());
        assertTrue(catalogo.getCursos().stream().noneMatch(item -> item.getId().equals(inactivo.getId())));
        assertEquals(1, filtrado.getCursos().size());
        assertEquals(spring.getId(), filtrado.getCursos().get(0).getId());
    }

    @Test
    void asignacionDirectaEvitaDuplicadosYNotificaAlColaborador() {
        ColaboradorCurso creada = cursoService.asignarDirectamente(
                colaboradorUno.getId(), spring.getId(),
                "Fortalecer competencias backend.", rm.getId());

        assertEquals(OrigenCurso.ASIGNADO_POR_RM, creada.getOrigen());
        assertEquals(EstadoColaboradorCurso.EN_CURSO, creada.getEstado());
        assertEquals("Fortalecer competencias backend.", creada.getMotivoRespuesta());
        assertEquals(1, notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(colaboradorUno).size());
        Long notificacionId = notificacionService.listar(colaboradorUno.getId()).get(0).id();
        notificacionService.marcarLeida(colaboradorUno.getId(), notificacionId);
        assertTrue(notificacionService.listar(colaboradorUno.getId()).get(0).leida());
        assertThrows(IllegalStateException.class, () -> cursoService.asignarDirectamente(
                colaboradorUno.getId(), spring.getId(), "Intento duplicado.", rm.getId()));
    }

    @Test
    void rmApruebaYRechazaSolicitudesConMotivoYNotificacion() {
        ColaboradorCurso paraAprobar = inscripcion(colaboradorUno, spring,
                OrigenCurso.SOLICITUD_COLABORADOR, EstadoColaboradorCurso.SOLICITADO);
        ColaboradorCurso paraRechazar = inscripcion(colaboradorDos, aws,
                OrigenCurso.SOLICITUD_COLABORADOR, EstadoColaboradorCurso.SOLICITADO);

        cursoService.aprobar(paraAprobar.getId(), "Aporta al proyecto actual.", rm.getId());
        cursoService.rechazar(paraRechazar.getId(), "Primero debe completar el curso básico.", rm.getId());

        ColaboradorCurso aprobada = colaboradorCursoRepository.findById(paraAprobar.getId()).orElseThrow();
        ColaboradorCurso rechazada = colaboradorCursoRepository.findById(paraRechazar.getId()).orElseThrow();
        assertEquals(EstadoColaboradorCurso.EN_CURSO, aprobada.getEstado());
        assertEquals(EstadoColaboradorCurso.RECHAZADO, rechazada.getEstado());
        assertEquals("Primero debe completar el curso básico.", rechazada.getMotivoRespuesta());
        assertEquals(1, notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(colaboradorUno).size());
        assertEquals(1, notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(colaboradorDos).size());
        assertThrows(IllegalStateException.class,
                () -> cursoService.aprobar(paraAprobar.getId(), "Segundo intento.", rm.getId()));
    }

    @Test
    void aprobarYRechazarExigenMotivoEnElServidorYLoRegistran() {
        ColaboradorCurso solicitud = inscripcion(colaboradorUno, spring,
                OrigenCurso.SOLICITUD_COLABORADOR, EstadoColaboradorCurso.SOLICITADO);

        assertThrows(IllegalArgumentException.class,
                () -> cursoService.aprobar(solicitud.getId(), null, rm.getId()));
        assertThrows(IllegalArgumentException.class,
                () -> cursoService.aprobar(solicitud.getId(), "   ", rm.getId()));
        assertThrows(IllegalArgumentException.class,
                () -> cursoService.aprobar(solicitud.getId(), "x".repeat(501), rm.getId()));
        assertThrows(IllegalArgumentException.class,
                () -> cursoService.rechazar(solicitud.getId(), "  ", rm.getId()));
        assertEquals(EstadoColaboradorCurso.SOLICITADO,
                colaboradorCursoRepository.findById(solicitud.getId()).orElseThrow().getEstado());
        assertTrue(notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(colaboradorUno).isEmpty());

        cursoService.aprobar(solicitud.getId(), "  Necesario para el proyecto Cloud.  ", rm.getId());
        ColaboradorCurso aprobada = colaboradorCursoRepository.findById(solicitud.getId()).orElseThrow();
        assertEquals(EstadoColaboradorCurso.EN_CURSO, aprobada.getEstado());
        assertEquals("Necesario para el proyecto Cloud.", aprobada.getMotivoRespuesta());
    }

    @Test
    void bandejaMuestraSoloAprobarYRechazarConModalesDeMotivo() throws Exception {
        ColaboradorCurso solicitud = inscripcion(colaboradorUno, spring,
                OrigenCurso.SOLICITUD_COLABORADOR, EstadoColaboradorCurso.SOLICITADO);

        String html = mockMvc.perform(get("/rm/cursos/solicitudes"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(html.contains("data-bs-target=\"#approveCourseModal\""));
        assertTrue(html.contains("data-bs-target=\"#rejectCourseModal\""));
        assertTrue(html.contains("data-action=\"/rm/cursos/solicitudes/" + solicitud.getId() + "/aprobar\""));
        assertTrue(html.contains("data-action=\"/rm/cursos/solicitudes/" + solicitud.getId() + "/rechazar\""));
        assertFalse(html.contains("placeholder=\"Motivo obligatorio\""), "El motivo ya no se escribe en la fila");
        assertTrue(html.contains("id=\"courseApproveReason\" name=\"motivo\""));
        assertTrue(html.contains("id=\"courseRejectReason\" name=\"motivo\""));
        assertTrue(html.contains("class=\"btn btn-success js-confirmar\" disabled"));
        assertTrue(html.contains("class=\"btn btn-danger js-confirmar\" disabled"));
    }

    @Test
    void noPermiteNuevasInscripcionesEnCursosOColaboradoresInactivos() {
        ColaboradorCurso solicitud = inscripcion(colaboradorUno, inactivo,
                OrigenCurso.SOLICITUD_COLABORADOR, EstadoColaboradorCurso.SOLICITADO);
        assertThrows(IllegalArgumentException.class,
                () -> cursoService.aprobar(solicitud.getId(), "Capacitación técnica.", rm.getId()));
        assertThrows(IllegalArgumentException.class, () -> cursoService.asignarDirectamente(
                colaboradorUno.getId(), inactivo.getId(), "Capacitación técnica.", rm.getId()));

        ColaboradorCurso otra = inscripcion(colaboradorDos, spring,
                OrigenCurso.SOLICITUD_COLABORADOR, EstadoColaboradorCurso.SOLICITADO);
        colaboradorDos.setActivo(false);
        usuarioRepository.save(colaboradorDos);
        assertThrows(IllegalArgumentException.class,
                () -> cursoService.aprobar(otra.getId(), "Capacitación técnica.", rm.getId()));
        assertEquals(EstadoColaboradorCurso.SOLICITADO,
                colaboradorCursoRepository.findById(otra.getId()).orElseThrow().getEstado());
        assertTrue(notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(colaboradorDos).isEmpty());
    }

    @Test
    void filtroTodosIncluyeHistorialSinCambiarElPredeterminado() throws Exception {
        inscripcion(colaboradorUno, spring, OrigenCurso.SOLICITUD_COLABORADOR,
                EstadoColaboradorCurso.SOLICITADO);
        inscripcion(colaboradorDos, aws, OrigenCurso.SOLICITUD_COLABORADOR,
                EstadoColaboradorCurso.RECHAZADO);

        mockMvc.perform(get("/rm/cursos/solicitudes"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("estadoSeleccionado", "SOLICITADO"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Luis Ramos"))));
        mockMvc.perform(get("/rm/cursos/solicitudes").param("estado", ""))
                .andExpect(status().isOk())
                .andExpect(model().attribute("estadoSeleccionado", ""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Luis Ramos")));
    }

    @Test
    void renderizaCatalogoBandejaYFormularioSinDatosSimulados() throws Exception {
        inscripcion(colaboradorUno, spring, OrigenCurso.SOLICITUD_COLABORADOR,
                EstadoColaboradorCurso.SOLICITADO);

        mockMvc.perform(get("/rm/cursos"))
                .andExpect(status().isOk()).andExpect(view().name("rm/rm-cursos"))
                .andExpect(model().attributeExists("catalogo"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Spring Boot avanzado test")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Kubernetes para entornos productivos"))));

        mockMvc.perform(get("/rm/cursos/solicitudes"))
                .andExpect(status().isOk()).andExpect(view().name("rm/rm-solicitudes-cursos"))
                .andExpect(model().attributeExists("bandeja"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ana Torres")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Motivo de la aprobación")));

        mockMvc.perform(get("/rm/cursos/asignar").param("curso", spring.getId().toString()))
                .andExpect(status().isOk()).andExpect(view().name("rm/rm-asignar-curso"))
                .andExpect(model().attributeExists("colaboradoresCurso", "cursosActivos"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Confirmar inscripción")));
    }

    // --- TASK-030: paginación en el servidor del catálogo (6) y de la bandeja (10) ---

    @Test
    void catalogoPaginaDeSeisPorNombreConCategoriasEIndicadoresSobreElTotal() {
        for (int i = 1; i <= 6; i++) curso("Curso 0" + i + " test", "Técnico", "10.00", true);
        inscripcion(colaboradorUno, spring, OrigenCurso.SOLICITUD_COLABORADOR, EstadoColaboradorCurso.SOLICITADO);
        inscripcion(colaboradorDos, aws, OrigenCurso.ASIGNADO_POR_RM, EstadoColaboradorCurso.EN_CURSO);
        var sinFiltros = cursoService.normalizarFiltrosCatalogo(null, null, null);

        RmCursoView.Catalogo pagina1 = cursoService.obtenerCatalogo(sinFiltros, "1");
        RmCursoView.Catalogo pagina2 = cursoService.obtenerCatalogo(sinFiltros, "2");

        assertEquals(6, pagina1.getCursos().size());
        assertEquals(2, pagina2.getCursos().size());
        assertEquals(2, pagina2.getPaginaActual());
        assertEquals(2, pagina2.getTotalPaginas());
        assertEquals(8, pagina2.getTotalRegistros());
        List<String> nombres = new ArrayList<>();
        pagina1.getCursos().forEach(item -> nombres.add(item.getNombre()));
        pagina2.getCursos().forEach(item -> nombres.add(item.getNombre()));
        assertEquals(nombres.stream().sorted().toList(), nombres, "Orden por nombre entre páginas");
        assertEquals("AWS Practitioner test", nombres.get(0));
        assertEquals("Spring Boot avanzado test", nombres.get(7));
        // Indicadores y categorías sobre el total, no sobre la página ni el filtro.
        RmCursoView.Catalogo filtrado = cursoService.obtenerCatalogo(
                cursoService.normalizarFiltrosCatalogo("curso 0", "técnico", "CORTA"), "2");
        for (RmCursoView.Catalogo catalogo : List.of(pagina2, filtrado)) {
            assertEquals(8, catalogo.getCursosActivos());
            assertEquals(1, catalogo.getSolicitudesPendientes());
            assertEquals(1, catalogo.getInscripcionesActivas());
            assertEquals(1, catalogo.getAsignadosPorRmEsteMes());
            assertEquals(List.of("Certificación", "Técnico"), catalogo.getCategorias());
        }
        assertEquals(6, filtrado.getTotalRegistros());
        assertEquals(1, filtrado.getPaginaActual(), "Una página mayor que el total vuelve a la última");
        // Categoría o duración desconocidas equivalen a "Todas".
        var invalidos = cursoService.normalizarFiltrosCatalogo("  ", "Inexistente", "eterna");
        assertEquals(new RmCursoService.FiltrosCatalogo(null, null, null), invalidos);
    }

    @Test
    void catalogoEnlacesDePaginacionConservanFiltrosSinJavascript() throws Exception {
        for (int i = 1; i <= 6; i++) curso("Java 0" + i + " test", "Técnico", "20.00", true);

        MvcResult resultado = mockMvc.perform(get("/rm/cursos")
                        .param("busqueda", "test").param("categoria", "Técnico")
                        .param("duracion", "media").param("pagina", "2"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("categoriaSeleccionada", "Técnico"))
                .andExpect(model().attribute("duracionSeleccionada", "media"))
                .andReturn();
        String html = resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        RmCursoView.Catalogo catalogo = (RmCursoView.Catalogo) resultado.getModelAndView().getModel().get("catalogo");

        assertEquals(List.of("Spring Boot avanzado test"),
                catalogo.getCursos().stream().map(RmCursoView.CursoItem::getNombre).toList());
        assertTrue(html.contains("Spring Boot avanzado test"));
        assertFalse(html.contains("Java 01 test"), "La vista recibe solo la página actual");
        assertTrue(html.contains("Mostrando 7-7 de 7 cursos"));
        String filtros = "/rm/cursos?busqueda=test&amp;categoria=T%C3%A9cnico&amp;duracion=media&amp;pagina=";
        assertTrue(html.contains("href=\"" + filtros + "1\">Anterior"));
        assertTrue(html.contains("href=\"" + filtros + "1\">1"));
        assertTrue(html.contains("href=\"" + filtros + "2\">2"));
        assertTrue(html.contains("href=\"" + filtros + "3\">Siguiente"));
        assertFalse(bloquePaginacion(html).contains("href=\"#\""), "Enlaces reales, sin JavaScript");
        assertFalse(html.contains("rm-cursos.js"), "El catálogo no necesita JavaScript para paginar");
    }

    @Test
    void bandejaPaginaDeDiezPorFechaDescendenteConIndicadoresSobreElTotal() {
        prepararBandejaPaginada();
        var pendientes = cursoService.normalizarFiltrosBandeja(null, null, null);

        RmCursoView.Bandeja pagina1 = cursoService.obtenerBandeja(pendientes, "1");
        RmCursoView.Bandeja pagina2 = cursoService.obtenerBandeja(pendientes, "2");

        assertEquals(10, pagina1.getInscripciones().size());
        assertEquals(2, pagina2.getInscripciones().size());
        assertEquals(2, pagina2.getTotalPaginas());
        assertEquals(12, pagina2.getTotalRegistros());
        List<LocalDateTime> fechas = new ArrayList<>();
        pagina1.getInscripciones().forEach(item -> fechas.add(item.getFechaSolicitud()));
        pagina2.getInscripciones().forEach(item -> fechas.add(item.getFechaSolicitud()));
        assertEquals(fechas.stream().sorted(Comparator.reverseOrder()).toList(), fechas,
                "Orden por fecha de solicitud descendente entre páginas");
        // Los cuatro indicadores no dependen de filtros ni página.
        RmCursoView.Bandeja filtrada = cursoService.obtenerBandeja(
                cursoService.normalizarFiltrosBandeja("luis", "RECHAZADO", "SOLICITUD_COLABORADOR"), "1");
        for (RmCursoView.Bandeja bandeja : List.of(pagina2, filtrada)) {
            assertEquals(12, bandeja.getPendientes());
            assertEquals(1, bandeja.getAprobadasEsteMes());
            assertEquals(1, bandeja.getRechazadasEsteMes());
            assertEquals(2, bandeja.getEnCurso());
        }
    }

    @Test
    void bandejaFiltraPorBusquedaEstadoYOrigenAntesDePaginar() {
        prepararBandejaPaginada();

        assertEquals(12, totalBandeja(null, null, null), "Sin estado: solo pendientes");
        assertEquals(15, totalBandeja(null, "", null), "Estado vacío: Todos");
        assertEquals(15, totalBandeja(null, "XYZ", "otro"), "Valores desconocidos: Todos");
        assertEquals(2, totalBandeja(null, "EN_CURSO", ""));
        assertEquals(1, totalBandeja(null, "", "ASIGNADO_POR_RM"));
        assertEquals(3, totalBandeja("LUIS", "", ""));
        assertEquals(1, totalBandeja("luis", "RECHAZADO", "SOLICITUD_COLABORADOR"));
        assertEquals(12, totalBandeja("ana", "SOLICITADO", "SOLICITUD_COLABORADOR"));
    }

    @Test
    void bandejaEnlacesConservanFiltrosYMantienenModalesSinPaginarEnJavascript() throws Exception {
        List<ColaboradorCurso> pendientes = prepararBandejaPaginada();

        MvcResult resultado = mockMvc.perform(get("/rm/cursos/solicitudes")
                        .param("busqueda", "ana").param("estado", "")
                        .param("origen", "SOLICITUD_COLABORADOR").param("pagina", "2"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("estadoSeleccionado", ""))
                .andExpect(model().attribute("origenSeleccionado", "SOLICITUD_COLABORADOR"))
                .andReturn();
        String html = resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        RmCursoView.Bandeja bandeja = (RmCursoView.Bandeja) resultado.getModelAndView().getModel().get("bandeja");

        assertEquals(2, bandeja.getInscripciones().size(), "La vista recibe solo la página actual");
        assertEquals(2, html.split("class=\"solicitud-row\"", -1).length - 1);
        assertTrue(html.contains("Mostrando 11-12 de 12 solicitudes"));
        String filtros = "/rm/cursos/solicitudes?busqueda=ana&amp;estado=&amp;origen=SOLICITUD_COLABORADOR&amp;pagina=";
        assertTrue(html.contains("href=\"" + filtros + "1\">Anterior"));
        assertTrue(html.contains("href=\"" + filtros + "2\">2"));
        assertTrue(html.contains("href=\"" + filtros + "3\">Siguiente"));
        assertFalse(bloquePaginacion(html).contains("href=\"#\""), "Enlaces reales, sin JavaScript");
        // Las dos solicitudes más antiguas quedan en la página 2 con sus acciones y modales.
        ColaboradorCurso masAntigua = pendientes.get(pendientes.size() - 1);
        assertTrue(html.contains("data-action=\"/rm/cursos/solicitudes/" + masAntigua.getId() + "/aprobar\""));
        assertTrue(html.contains("data-action=\"/rm/cursos/solicitudes/" + masAntigua.getId() + "/rechazar\""));
        assertTrue(html.contains("id=\"approveCourseModal\""));
        assertTrue(html.contains("id=\"rejectCourseModal\""));
        assertTrue(html.contains("/js/rm-js/rm-solicitudes-cursos.js"));

        String js = new ClassPathResource("static/js/rm-js/rm-solicitudes-cursos.js")
                .getContentAsString(StandardCharsets.UTF_8);
        assertTrue(js.contains("course-decision-modal"), "El JS conserva los modales");
        assertFalse(js.contains("pageSize") || js.contains("solicitud-row") || js.contains("pagination"),
                "El JS ya no pagina la tabla");
    }

    @Test
    void paginaVaciaNegativaCeroOExcesivaNoGeneraErrorTecnico() throws Exception {
        for (int i = 1; i <= 6; i++) curso("Curso 0" + i + " test", "Técnico", "10.00", true);
        prepararBandejaPaginada();

        for (String pagina : List.of("", "abc", "-3", "0", "999")) {
            int esperada = "999".equals(pagina) ? 2 : 1;
            MvcResult catalogo = mockMvc.perform(get("/rm/cursos").param("pagina", pagina))
                    .andExpect(status().isOk()).andReturn();
            MvcResult bandeja = mockMvc.perform(get("/rm/cursos/solicitudes").param("pagina", pagina))
                    .andExpect(status().isOk()).andReturn();
            assertEquals(esperada, ((RmCursoView.Catalogo) catalogo.getModelAndView().getModel()
                    .get("catalogo")).getPaginaActual(), "Catálogo, pagina=" + pagina);
            assertEquals(esperada, ((RmCursoView.Bandeja) bandeja.getModelAndView().getModel()
                    .get("bandeja")).getPaginaActual(), "Bandeja, pagina=" + pagina);
        }
    }

    /**
     * 12 solicitudes pendientes de Ana (una por hora, de la más reciente a la más antigua) y 3 de Luis:
     * una aprobada, una asignada por el RM y una rechazada. Devuelve las pendientes en orden descendente.
     */
    private List<ColaboradorCurso> prepararBandejaPaginada() {
        LocalDateTime base = LocalDateTime.now().minusDays(1);
        List<ColaboradorCurso> pendientes = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            ColaboradorCurso item = new ColaboradorCurso();
            item.setColaborador(colaboradorUno);
            item.setCurso(i % 2 == 0 ? spring : aws);
            item.setOrigen(OrigenCurso.SOLICITUD_COLABORADOR);
            item.setEstado(EstadoColaboradorCurso.SOLICITADO);
            item.setFechaSolicitud(base.minusHours(i));
            pendientes.add(colaboradorCursoRepository.save(item));
        }
        inscripcion(colaboradorDos, spring, OrigenCurso.SOLICITUD_COLABORADOR, EstadoColaboradorCurso.EN_CURSO);
        inscripcion(colaboradorDos, aws, OrigenCurso.ASIGNADO_POR_RM, EstadoColaboradorCurso.EN_CURSO);
        inscripcion(colaboradorDos, aws, OrigenCurso.SOLICITUD_COLABORADOR, EstadoColaboradorCurso.RECHAZADO);
        return pendientes;
    }

    private String bloquePaginacion(String html) {
        int inicio = html.indexOf("id=\"pagination\"");
        assertTrue(inicio >= 0, "La paginación se renderiza en el servidor");
        return html.substring(inicio, html.indexOf("</ul>", inicio));
    }

    private long totalBandeja(String busqueda, String estado, String origen) {
        return cursoService.obtenerBandeja(
                cursoService.normalizarFiltrosBandeja(busqueda, estado, origen), "2").getTotalRegistros();
    }

    private Curso curso(String nombre, String categoria, String horas, boolean activo) {
        Curso curso = new Curso();
        curso.setNombre(nombre);
        curso.setDescripcion("Descripción del curso de prueba.");
        curso.setCategoria(categoria);
        curso.setHoras(new BigDecimal(horas));
        curso.setActivo(activo);
        curso.setCreadoPor(admin);
        return cursoRepository.save(curso);
    }

    private ColaboradorCurso inscripcion(Usuario colaborador, Curso curso,
                                         OrigenCurso origen, EstadoColaboradorCurso estado) {
        ColaboradorCurso inscripcion = new ColaboradorCurso();
        inscripcion.setColaborador(colaborador);
        inscripcion.setCurso(curso);
        inscripcion.setOrigen(origen);
        inscripcion.setEstado(estado);
        if (origen == OrigenCurso.ASIGNADO_POR_RM || estado != EstadoColaboradorCurso.SOLICITADO) {
            inscripcion.setAsignadoPor(rm);
            inscripcion.setFechaRespuesta(LocalDateTime.now());
        }
        return colaboradorCursoRepository.save(inscripcion);
    }

    private Rol rol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol rol = new Rol();
            rol.setNombre(nombre);
            return rolRepository.save(rol);
        });
    }

    private Usuario usuario(String correo, String nombre, String apellido, Rol rol, String cargo) {
        Usuario usuario = usuarioRepository.findByCorreo(correo).orElseGet(Usuario::new);
        usuario.setCorreo(correo);
        usuario.setNombre(nombre);
        usuario.setApellido(apellido);
        usuario.setRol(rol);
        if (cargo != null) usuario.setCargo(cargoDePrueba(cargo));
        usuario.setActivo(true);
        return usuarioRepository.save(usuario);
    }

    private Cargo cargoDePrueba(String nombre) {
        return cargoRepository.findByNombre(nombre).orElseGet(() -> cargoRepository.save(
                new Cargo(nombre, new BigDecimal("2000"), new BigDecimal("3000"), new BigDecimal("4000"))));
    }
}
