package com.pucp.skillb_ia;

import com.pucp.skillb_ia.dto.NotificacionView;
import com.pucp.skillb_ia.dto.RmCursoView;
import com.pucp.skillb_ia.model.ColaboradorCurso;
import com.pucp.skillb_ia.model.Curso;
import com.pucp.skillb_ia.model.LogAuditoria;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.Cargo;
import com.pucp.skillb_ia.repository.CargoRepository;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoColaboradorCurso;
import com.pucp.skillb_ia.model.enums.OrigenCurso;
import com.pucp.skillb_ia.repository.ColaboradorCursoRepository;
import com.pucp.skillb_ia.repository.CursoRepository;
import com.pucp.skillb_ia.repository.LogAuditoriaRepository;
import com.pucp.skillb_ia.repository.NotificacionRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.rm.RmCursoService;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.NotificacionService;
import com.pucp.skillb_ia.service.col.ColaboradorCursoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    @Autowired private ColaboradorCursoService colaboradorCursoService;
    @Autowired private LogAuditoriaRepository logAuditoriaRepository;
    // TASK-054: permite simular un fallo de la auditoría para comprobar que la decisión se revierte.
    @MockitoSpyBean private AuditoriaService auditoriaService;

    private MockMvc mockMvc;
    private Usuario admin;
    private Usuario rm;
    private Usuario colaboradorUno;
    private Usuario colaboradorDos;
    private Usuario rmInscripcion;
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

    @AfterEach
    void limpiarSesion() {
        SecurityContextHolder.clearContext();
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

    // TASK-032: todas las tarjetas numéricas del catálogo (4) y de la bandeja (5) tienen un icono decorativo.
    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', value = {
            "/rm/cursos | Cursos activos;Solicitudes pendientes;Inscripciones activas;Asignados por RM",
            "/rm/cursos/solicitudes | Solicitudes pendientes;Evidencias por revisar;Aprobadas este mes;"
                    + "Rechazadas este mes;En curso"})
    void tarjetasNumericasTienenIconoDecorativo(String url, String etiquetas) throws Exception {
        String html = mockMvc.perform(get(url)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Matcher tarjeta = Pattern.compile("<div class=\"card metric-card\">"
                + "<div class=\"card-body d-flex align-items-center gap-3\">\\s*"
                + "<div class=\"metric-icon bg-(\\w+)-lt text-\\1\"><svg [^>]*aria-hidden=\"true\"[^>]*>"
                + "(?:(?!</svg>).)*</svg></div>\\s*"
                + "<div><div class=\"metric-label\">([^<]+)</div>", Pattern.DOTALL).matcher(html);
        List<String> conIcono = new ArrayList<>();
        while (tarjeta.find()) conIcono.add(tarjeta.group(2));

        List<String> esperadas = List.of(etiquetas.split(";"));
        assertEquals(esperadas, conIcono);
        assertEquals(esperadas.size(), ocurrencias(html, "class=\"card metric-card\""), "Ninguna tarjeta queda sin icono");
        assertEquals(esperadas.size(), ocurrencias(html, "<div class=\"metric-icon bg-"));
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

    // --- TASK-049: revisión de la evidencia de finalización desde la bandeja ---

    private static final String URL_EVIDENCIAS = "/rm/cursos/solicitudes?estado=EVIDENCIA_PENDIENTE";

    @Test
    void filtroEvidenciaPendienteMuestraSoloEvidenciasConSuOpcionSeleccionada() throws Exception {
        ColaboradorCurso evidencia = evidenciaPendiente(colaboradorUno, spring, "ana.pdf", null);
        inscripcion(colaboradorDos, aws, OrigenCurso.SOLICITUD_COLABORADOR, EstadoColaboradorCurso.SOLICITADO);
        inscripcion(colaboradorDos, spring, OrigenCurso.ASIGNADO_POR_RM, EstadoColaboradorCurso.EN_CURSO);

        MvcResult resultado = mockMvc.perform(get("/rm/cursos/solicitudes").param("estado", "EVIDENCIA_PENDIENTE"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("estadoSeleccionado", "EVIDENCIA_PENDIENTE"))
                .andReturn();
        String html = resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        RmCursoView.Bandeja bandeja = (RmCursoView.Bandeja) resultado.getModelAndView().getModel().get("bandeja");

        assertEquals(List.of(evidencia.getId()),
                bandeja.getInscripciones().stream().map(RmCursoView.InscripcionItem::getId).toList());
        assertTrue(html.contains("<option value=\"EVIDENCIA_PENDIENTE\" selected=\"selected\">Evidencia en revisión</option>"));
        assertFalse(html.contains("Luis Ramos"), "Solo las evidencias en revisión");
        assertEquals(1, totalBandeja(null, "EVIDENCIA_PENDIENTE", null));
        assertEquals(1, totalBandeja(null, null, null), "Sin estado sigue mostrando solo SOLICITADO");
    }

    @Test
    void paginacionDeEvidenciasConservaElFiltroYEntregaSoloLaPaginaActual() throws Exception {
        LocalDateTime base = LocalDateTime.now().minusDays(1);
        for (int i = 0; i < 12; i++) {
            evidenciaPendiente(colaboradorUno, i % 2 == 0 ? spring : aws, "ev" + i + ".pdf", base.minusHours(i));
        }
        inscripcion(colaboradorDos, aws, OrigenCurso.SOLICITUD_COLABORADOR, EstadoColaboradorCurso.SOLICITADO);

        MvcResult resultado = mockMvc.perform(get("/rm/cursos/solicitudes")
                        .param("estado", "EVIDENCIA_PENDIENTE").param("pagina", "2"))
                .andExpect(status().isOk())
                .andReturn();
        String html = resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        RmCursoView.Bandeja bandeja = (RmCursoView.Bandeja) resultado.getModelAndView().getModel().get("bandeja");

        assertEquals(2, bandeja.getInscripciones().size(), "La vista recibe solo la página actual");
        assertEquals(2, bandeja.getPaginaActual());
        assertEquals(12, bandeja.getTotalRegistros());
        assertEquals(2, html.split("class=\"solicitud-row\"", -1).length - 1);
        assertTrue(html.contains("Mostrando 11-12 de 12 solicitudes"));
        String paginacion = bloquePaginacion(html);
        String filtro = "estado=EVIDENCIA_PENDIENTE&amp;origen=&amp;pagina=";
        assertTrue(paginacion.contains(filtro + "1\">Anterior"));
        assertTrue(paginacion.contains(filtro + "1\">1"));
        assertTrue(paginacion.contains(filtro + "2\">2"));
        assertTrue(paginacion.contains(filtro + "3\">Siguiente"));
        assertFalse(paginacion.contains("estado=&amp;"), "Ningún enlace pierde el filtro");
    }

    @Test
    void indicadorEvidenciasPorRevisarSeCalculaSobreTodasLasInscripciones() throws Exception {
        prepararBandejaPaginada();
        for (int i = 0; i < 3; i++) evidenciaPendiente(colaboradorDos, spring, "luis" + i + ".pdf", null);
        inscripcion(colaboradorUno, aws, OrigenCurso.SOLICITUD_COLABORADOR, EstadoColaboradorCurso.COMPLETADO);

        List<RmCursoView.Bandeja> vistas = List.of(
                cursoService.obtenerBandeja(cursoService.normalizarFiltrosBandeja(null, null, null), "2"),
                cursoService.obtenerBandeja(cursoService.normalizarFiltrosBandeja(
                        "luis", "RECHAZADO", "SOLICITUD_COLABORADOR"), "1"),
                cursoService.obtenerBandeja(cursoService.normalizarFiltrosBandeja(
                        "sin coincidencias", "EVIDENCIA_PENDIENTE", "ASIGNADO_POR_RM"), "9"));
        for (RmCursoView.Bandeja bandeja : vistas) {
            assertEquals(3, bandeja.getEvidenciasPorRevisar());
            assertEquals(12, bandeja.getPendientes(), "Los indicadores existentes no cambian");
            assertEquals(2, bandeja.getEnCurso());
        }

        mockMvc.perform(get("/rm/cursos/solicitudes").param("estado", "RECHAZADO").param("pagina", "3"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Evidencias por revisar")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"evidenciasPorRevisar\" class=\"metric-number\">3<")))
                // TASK-032: el icono se agrega sin cambiar la etiqueta, el id ni el valor del indicador.
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<div><div class=\"metric-label\">Evidencias por revisar</div>"
                                + "<div id=\"evidenciasPorRevisar\" class=\"metric-number\">3</div></div>")));
    }

    @Test
    void filaConEvidenciaEnlazaElArchivoDeFormaSeguraYLasDemasConservanVerPerfil() throws Exception {
        ColaboradorCurso evidencia = evidenciaPendiente(colaboradorUno, spring, "constancia-ana.pdf", null);
        inscripcion(colaboradorDos, aws, OrigenCurso.ASIGNADO_POR_RM, EstadoColaboradorCurso.EN_CURSO);

        String html = mockMvc.perform(get("/rm/cursos/solicitudes").param("estado", ""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertEquals(1, ocurrencias(html, ">Ver evidencia</a>"));
        String enlace = etiquetaAnterior(html, ">Ver evidencia</a>", "<a");
        assertTrue(enlace.contains("showEvidenciaModal") && enlace.contains(evidencia.getEvidenciaUrl()));
        assertTrue(html.contains("data-action=\"/rm/cursos/solicitudes/" + evidencia.getId() + "/evidencia/aprobar\""));
        assertTrue(html.contains("data-action=\"/rm/cursos/solicitudes/" + evidencia.getId() + "/evidencia/rechazar\""));
        assertTrue(html.contains(">Validar evidencia</button>"));
        assertTrue(html.contains(">Rechazar evidencia</button>"));
        assertEquals(1, ocurrencias(html, ">Ver perfil</a>"), "Solo la fila sin decisión pendiente");
        assertTrue(html.contains("/rm/colaboradores/perfil?id=" + colaboradorDos.getId()));
        assertFalse(html.contains("/rm/colaboradores/perfil?id=" + colaboradorUno.getId()));
    }

    @Test
    void modalesDeEvidenciaValidanSinMotivoYRechazanConMotivoObligatorio() throws Exception {
        evidenciaPendiente(colaboradorUno, spring, "ana.pdf", null);

        String html = mockMvc.perform(get("/rm/cursos/solicitudes").param("estado", "EVIDENCIA_PENDIENTE"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String aprobar = bloqueModal(html, "approveEvidenceModal");
        assertTrue(aprobar.contains("course-decision-modal"));
        assertFalse(aprobar.contains("name=\"motivo\""), "La validación no pide motivo");
        assertFalse(aprobar.contains("js-motivo"));
        assertTrue(aprobar.contains("class=\"btn btn-success js-confirmar\">Confirmar validación</button>"),
                "Confirmar está habilitado desde el inicio");
        String rechazar = bloqueModal(html, "rejectEvidenceModal");
        assertTrue(rechazar.contains("id=\"evidenceRejectReason\" name=\"motivo\" maxlength=\"500\""));
        assertTrue(rechazar.contains("js-motivo\" required"));
        assertTrue(rechazar.contains("class=\"btn btn-danger js-confirmar\" disabled"));
        assertTrue(rechazar.contains("podrá reenviar la evidencia"));

        String js = new ClassPathResource("static/js/rm-js/rm-solicitudes-cursos.js")
                .getContentAsString(StandardCharsets.UTF_8);
        assertTrue(js.contains("motivo !== null && motivo.value.trim() === \"\""),
                "El motivo se exige solo en los modales que tienen el campo");
        assertFalse(js.contains("pagination") || js.contains("solicitud-row") || js.contains("estado")
                || js.contains("busqueda"), "Filtros y paginación siguen en el servidor");
    }

    @Test
    void validarEvidenciaCompletaElCursoYUnaSegundaDecisionSeRechaza() throws Exception {
        ColaboradorCurso evidencia = evidenciaPendiente(colaboradorUno, spring, "ana.pdf", null);
        autenticarRm();

        mockMvc.perform(post("/rm/cursos/solicitudes/{id}/evidencia/aprobar", evidencia.getId()))
                .andExpect(redirectedUrl(URL_EVIDENCIAS))
                .andExpect(flash().attribute("mensajeExito",
                        "Evidencia validada; el curso quedó marcado como completado y se notificó al colaborador."));

        ColaboradorCurso completada = colaboradorCursoRepository.findById(evidencia.getId()).orElseThrow();
        assertEquals(EstadoColaboradorCurso.COMPLETADO, completada.getEstado());
        assertNotNull(completada.getFechaCompletado());
        var notificaciones = notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(colaboradorUno);
        assertEquals(1, notificaciones.size());
        assertEquals("CURSO_COMPLETADO", notificaciones.get(0).getTipo());

        String yaDecidida = "Esta inscripción no tiene una evidencia pendiente de revisión.";
        mockMvc.perform(post("/rm/cursos/solicitudes/{id}/evidencia/aprobar", evidencia.getId()))
                .andExpect(redirectedUrl(URL_EVIDENCIAS))
                .andExpect(flash().attribute("mensajeError", yaDecidida));
        mockMvc.perform(post("/rm/cursos/solicitudes/{id}/evidencia/rechazar", evidencia.getId())
                        .param("motivo", "Llegó tarde."))
                .andExpect(redirectedUrl(URL_EVIDENCIAS))
                .andExpect(flash().attribute("mensajeError", yaDecidida));
        assertEquals(EstadoColaboradorCurso.COMPLETADO,
                colaboradorCursoRepository.findById(evidencia.getId()).orElseThrow().getEstado());
        assertEquals(1, notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(colaboradorUno).size());
    }

    @Test
    void rechazarEvidenciaExigeMotivoYPermiteReenviarla() throws Exception {
        ColaboradorCurso evidencia = evidenciaPendiente(colaboradorUno, spring, "ana.pdf", null);
        autenticarRm();

        String sinMotivo = "El motivo del rechazo de la evidencia es obligatorio.";
        mockMvc.perform(post("/rm/cursos/solicitudes/{id}/evidencia/rechazar", evidencia.getId()))
                .andExpect(redirectedUrl(URL_EVIDENCIAS))
                .andExpect(flash().attribute("mensajeError", sinMotivo));
        mockMvc.perform(post("/rm/cursos/solicitudes/{id}/evidencia/rechazar", evidencia.getId())
                        .param("motivo", "   "))
                .andExpect(redirectedUrl(URL_EVIDENCIAS))
                .andExpect(flash().attribute("mensajeError", sinMotivo));
        mockMvc.perform(post("/rm/cursos/solicitudes/{id}/evidencia/rechazar", evidencia.getId())
                        .param("motivo", "x".repeat(501)))
                .andExpect(redirectedUrl(URL_EVIDENCIAS))
                .andExpect(flash().attribute("mensajeError", "El motivo no puede superar 500 caracteres."));
        assertEquals(EstadoColaboradorCurso.EVIDENCIA_PENDIENTE,
                colaboradorCursoRepository.findById(evidencia.getId()).orElseThrow().getEstado());
        assertTrue(notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(colaboradorUno).isEmpty());

        mockMvc.perform(post("/rm/cursos/solicitudes/{id}/evidencia/rechazar", evidencia.getId())
                        .param("motivo", "  La constancia no muestra tu nombre.  "))
                .andExpect(redirectedUrl(URL_EVIDENCIAS))
                .andExpect(flash().attribute("mensajeExito",
                        "Evidencia rechazada; el colaborador fue notificado y puede volver a subirla."));

        ColaboradorCurso rechazada = colaboradorCursoRepository.findById(evidencia.getId()).orElseThrow();
        assertEquals(EstadoColaboradorCurso.EN_CURSO, rechazada.getEstado(), "Vuelve a En curso para reenviarla");
        assertEquals("La constancia no muestra tu nombre.", rechazada.getMotivoRespuesta());
        var notificaciones = notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(colaboradorUno);
        assertEquals(1, notificaciones.size());
        assertEquals("EVIDENCIA_CURSO_RECHAZADA", notificaciones.get(0).getTipo());

        mockMvc.perform(post("/rm/cursos/solicitudes/{id}/evidencia/aprobar", evidencia.getId()))
                .andExpect(redirectedUrl(URL_EVIDENCIAS))
                .andExpect(flash().attribute("mensajeError",
                        "Esta inscripción no tiene una evidencia pendiente de revisión."));
    }

    // TASK-054: revisor, fecha de revisión y auditoría de la decisión sobre la evidencia.
    @Test
    void aprobarEvidenciaGuardaRevisorYFechaCompletaElCursoYAuditaLasHoras() throws Exception {
        ColaboradorCurso evidencia = evidenciaAprobadaPorOtroRm();
        autenticarRm();
        LocalDateTime antes = LocalDateTime.now().minusSeconds(1);

        mockMvc.perform(post("/rm/cursos/solicitudes/{id}/evidencia/aprobar", evidencia.getId()))
                .andExpect(redirectedUrl(URL_EVIDENCIAS))
                .andExpect(flash().attributeExists("mensajeExito"));

        ColaboradorCurso completada = colaboradorCursoRepository.findByIdConDetalle(evidencia.getId()).orElseThrow();
        assertEquals(EstadoColaboradorCurso.COMPLETADO, completada.getEstado());
        assertEquals(rm.getId(), completada.getEvidenciaRevisadaPor().getId(), "El revisor es el RM autenticado");
        assertNotNull(completada.getFechaRevisionEvidencia());
        assertFalse(completada.getFechaRevisionEvidencia().isBefore(antes));
        assertEquals(completada.getFechaCompletado(), completada.getFechaRevisionEvidencia());
        assertEquals(rmInscripcion.getId(), completada.getAsignadoPor().getId(),
                "asignadoPor sigue siendo el RM que aprobó la inscripción");
        assertEquals("CURSO_COMPLETADO",
                notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(colaboradorUno).get(0).getTipo());

        List<LogAuditoria> auditorias = auditorias("APROBAR_EVIDENCIA_CURSO", evidencia.getId());
        assertEquals(1, auditorias.size());
        LogAuditoria log = auditorias.get(0);
        assertEquals(rm.getId(), log.getUsuario().getId());
        assertEquals("COLABORADOR_CURSO", log.getEntidad());
        assertTrue(log.getDetalle().contains("Spring Boot avanzado test"), log.getDetalle());
        assertTrue(log.getDetalle().contains("Ana Torres"), log.getDetalle());
        assertTrue(log.getDetalle().contains("Horas confirmadas: 20.00 h, sumadas a "
                + YearMonth.from(completada.getFechaCompletado())), log.getDetalle());
        assertEquals("EVIDENCIA_PENDIENTE", log.getValorAnterior());
        assertEquals("COMPLETADO", log.getValorNuevo());
        assertTrue(auditorias("RECHAZAR_EVIDENCIA_CURSO", evidencia.getId()).isEmpty());
    }

    @Test
    void rechazarEvidenciaGuardaRevisorFechaMotivoYAuditoriaSinTocarAsignadoPor() throws Exception {
        ColaboradorCurso evidencia = evidenciaAprobadaPorOtroRm();
        autenticarRm();

        mockMvc.perform(post("/rm/cursos/solicitudes/{id}/evidencia/rechazar", evidencia.getId())
                        .param("motivo", "La constancia no muestra tu nombre."))
                .andExpect(redirectedUrl(URL_EVIDENCIAS))
                .andExpect(flash().attributeExists("mensajeExito"));

        ColaboradorCurso rechazada = colaboradorCursoRepository.findByIdConDetalle(evidencia.getId()).orElseThrow();
        assertEquals(EstadoColaboradorCurso.EN_CURSO, rechazada.getEstado());
        assertEquals("La constancia no muestra tu nombre.", rechazada.getMotivoRespuesta());
        assertEquals(rm.getId(), rechazada.getEvidenciaRevisadaPor().getId());
        assertNotNull(rechazada.getFechaRevisionEvidencia());
        assertNull(rechazada.getFechaCompletado());
        assertEquals(rmInscripcion.getId(), rechazada.getAsignadoPor().getId());
        assertNotNull(rechazada.getEvidenciaUrl(), "La evidencia rechazada se conserva");

        List<LogAuditoria> auditorias = auditorias("RECHAZAR_EVIDENCIA_CURSO", evidencia.getId());
        assertEquals(1, auditorias.size());
        LogAuditoria log = auditorias.get(0);
        assertEquals(rm.getId(), log.getUsuario().getId());
        assertEquals("COLABORADOR_CURSO", log.getEntidad());
        assertTrue(log.getDetalle().contains("Spring Boot avanzado test"), log.getDetalle());
        assertTrue(log.getDetalle().contains("Ana Torres"), log.getDetalle());
        assertTrue(log.getDetalle().endsWith("Motivo: La constancia no muestra tu nombre."), log.getDetalle());
        assertEquals("EN_CURSO", log.getValorNuevo());
    }

    @Test
    void rechazoConMotivoMaximoSeAuditaSinSuperarLaColumna() {
        ColaboradorCurso evidencia = evidenciaPendiente(colaboradorUno, spring, "ana.pdf", null);

        cursoService.rechazarEvidencia(evidencia.getId(), "m".repeat(500), rm.getId());

        assertEquals("m".repeat(500),
                colaboradorCursoRepository.findById(evidencia.getId()).orElseThrow().getMotivoRespuesta());
        String detalle = auditorias("RECHAZAR_EVIDENCIA_CURSO", evidencia.getId()).get(0).getDetalle();
        assertEquals(500, detalle.length());
        assertTrue(detalle.endsWith("..."));
    }

    @Test
    void reenviarEvidenciaRechazadaLimpiaLaRevisionAnterior() throws Exception {
        ColaboradorCurso evidencia = evidenciaAprobadaPorOtroRm();
        cursoService.rechazarEvidencia(evidencia.getId(), "Archivo ilegible.", rm.getId());

        colaboradorCursoService.subirEvidencia(colaboradorUno, evidencia.getId(),
                new MockMultipartFile("evidencia", "constancia.pdf", "application/pdf", new byte[]{1, 2, 3}));

        ColaboradorCurso reenviada = colaboradorCursoRepository.findByIdConDetalle(evidencia.getId()).orElseThrow();
        assertEquals(EstadoColaboradorCurso.EVIDENCIA_PENDIENTE, reenviada.getEstado());
        assertNull(reenviada.getEvidenciaRevisadaPor());
        assertNull(reenviada.getFechaRevisionEvidencia());
        assertEquals(rmInscripcion.getId(), reenviada.getAsignadoPor().getId());
        assertEquals(1, auditorias("RECHAZAR_EVIDENCIA_CURSO", evidencia.getId()).size(),
                "El rechazo anterior queda en la auditoría");

        String html = mockMvc.perform(get("/rm/cursos/solicitudes").param("estado", "EVIDENCIA_PENDIENTE"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(html.contains("Evidencia revisada por"), "Vuelve a revisión sin revisor");
    }

    @Test
    void segundaDecisionOEstadoInvalidoNoCambiaDatosNiAudita() {
        ColaboradorCurso evidencia = evidenciaPendiente(colaboradorUno, spring, "ana.pdf", null);
        cursoService.aprobarEvidencia(evidencia.getId(), rm.getId());
        ColaboradorCurso decidida = colaboradorCursoRepository.findByIdConDetalle(evidencia.getId()).orElseThrow();

        assertThrows(IllegalStateException.class,
                () -> cursoService.aprobarEvidencia(evidencia.getId(), rm.getId()));
        assertThrows(IllegalStateException.class,
                () -> cursoService.rechazarEvidencia(evidencia.getId(), "Tarde.", rm.getId()));

        ColaboradorCurso despues = colaboradorCursoRepository.findByIdConDetalle(evidencia.getId()).orElseThrow();
        assertEquals(EstadoColaboradorCurso.COMPLETADO, despues.getEstado());
        assertEquals(decidida.getFechaRevisionEvidencia(), despues.getFechaRevisionEvidencia());
        assertEquals(decidida.getFechaCompletado(), despues.getFechaCompletado());
        assertNull(despues.getMotivoRespuesta());
        assertEquals(1, auditorias("APROBAR_EVIDENCIA_CURSO", evidencia.getId()).size());
        assertTrue(auditorias("RECHAZAR_EVIDENCIA_CURSO", evidencia.getId()).isEmpty());
        assertEquals(1, notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(colaboradorUno).size());

        for (EstadoColaboradorCurso estado : List.of(EstadoColaboradorCurso.SOLICITADO,
                EstadoColaboradorCurso.EN_CURSO, EstadoColaboradorCurso.RECHAZADO,
                EstadoColaboradorCurso.NO_COMPLETADO)) {
            ColaboradorCurso otra = inscripcion(colaboradorDos, aws, OrigenCurso.SOLICITUD_COLABORADOR, estado);
            assertThrows(IllegalStateException.class,
                    () -> cursoService.aprobarEvidencia(otra.getId(), rm.getId()), estado.name());
            assertThrows(IllegalStateException.class,
                    () -> cursoService.rechazarEvidencia(otra.getId(), "Motivo.", rm.getId()), estado.name());
            ColaboradorCurso sinCambios = colaboradorCursoRepository.findById(otra.getId()).orElseThrow();
            assertEquals(estado, sinCambios.getEstado());
            assertNull(sinCambios.getEvidenciaRevisadaPor());
            assertNull(sinCambios.getFechaRevisionEvidencia());
            assertTrue(auditorias("APROBAR_EVIDENCIA_CURSO", otra.getId()).isEmpty());
            assertTrue(auditorias("RECHAZAR_EVIDENCIA_CURSO", otra.getId()).isEmpty());
        }

        ColaboradorCurso sinMotivo = evidenciaPendiente(colaboradorDos, spring, "luis.pdf", null);
        assertThrows(IllegalArgumentException.class,
                () -> cursoService.rechazarEvidencia(sinMotivo.getId(), "  ", rm.getId()));
        assertNull(colaboradorCursoRepository.findById(sinMotivo.getId()).orElseThrow().getFechaRevisionEvidencia());
        assertTrue(auditorias("RECHAZAR_EVIDENCIA_CURSO", sinMotivo.getId()).isEmpty());
        assertTrue(notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(colaboradorDos).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"APROBAR_EVIDENCIA_CURSO", "RECHAZAR_EVIDENCIA_CURSO"})
    void unFalloDuranteLaDecisionRevierteTodosLosCambios(String accion) {
        ColaboradorCurso evidencia = evidenciaAprobadaPorOtroRm();
        doThrow(new IllegalStateException("Fallo simulado de auditoría")).when(auditoriaService)
                .registrar(any(), eq(accion), any(), any(), any(), any(), any(), any());

        assertThrows(IllegalStateException.class, () -> {
            if (accion.startsWith("APROBAR")) cursoService.aprobarEvidencia(evidencia.getId(), rm.getId());
            else cursoService.rechazarEvidencia(evidencia.getId(), "Archivo ilegible.", rm.getId());
        });

        ColaboradorCurso sinCambios = colaboradorCursoRepository.findByIdConDetalle(evidencia.getId()).orElseThrow();
        assertEquals(EstadoColaboradorCurso.EVIDENCIA_PENDIENTE, sinCambios.getEstado());
        assertNull(sinCambios.getEvidenciaRevisadaPor());
        assertNull(sinCambios.getFechaRevisionEvidencia());
        assertNull(sinCambios.getFechaCompletado());
        assertNull(sinCambios.getMotivoRespuesta());
        assertEquals(rmInscripcion.getId(), sinCambios.getAsignadoPor().getId());
        assertTrue(notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(colaboradorUno).isEmpty());
        assertTrue(auditorias(accion, evidencia.getId()).isEmpty());
    }

    @Test
    void bandejaMuestraRevisorYFechaDeLaEvidenciaRevisada() throws Exception {
        ColaboradorCurso aprobada = evidenciaPendiente(colaboradorUno, spring, "ana.pdf", null);
        ColaboradorCurso rechazada = evidenciaPendiente(colaboradorDos, aws, "luis.pdf", null);
        ColaboradorCurso pendiente = evidenciaPendiente(colaboradorDos, spring, "luis2.pdf", null);
        cursoService.aprobarEvidencia(aprobada.getId(), rm.getId());
        cursoService.rechazarEvidencia(rechazada.getId(), "Falta la fecha.", rm.getId());
        LocalDateTime fecha = colaboradorCursoRepository.findById(aprobada.getId()).orElseThrow()
                .getFechaRevisionEvidencia();

        RmCursoView.Bandeja bandeja = cursoService.obtenerBandeja(
                cursoService.normalizarFiltrosBandeja("", "", ""), null);
        RmCursoView.InscripcionItem itemAprobada = bandeja.getInscripciones().stream()
                .filter(item -> item.getId().equals(aprobada.getId())).findFirst().orElseThrow();
        assertEquals("Rosa Mendoza", itemAprobada.getEvidenciaRevisadaPor());
        assertEquals(fecha, itemAprobada.getFechaRevisionEvidencia());
        assertNull(bandeja.getInscripciones().stream().filter(item -> item.getId().equals(pendiente.getId()))
                .findFirst().orElseThrow().getEvidenciaRevisadaPor());

        String html = mockMvc.perform(get("/rm/cursos/solicitudes").param("estado", ""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(2, html.split("Evidencia revisada por Rosa Mendoza", -1).length - 1,
                "Solo las evidencias revisadas muestran revisor");
        assertTrue(html.contains("Evidencia revisada por Rosa Mendoza · "
                + String.format("%02d", fecha.getDayOfMonth())), "Muestra la fecha de revisión");
        assertTrue(html.contains(String.format("%d %02d:%02d<", fecha.getYear(), fecha.getHour(), fecha.getMinute())),
                "Muestra la hora de revisión");
    }

    @Test
    void notificacionDeEvidenciaAbreLaBandejaFiltradaYLasDemasConservanSuEnlace() {
        ColaboradorCurso evidencia = evidenciaPendiente(colaboradorUno, spring, "ana.pdf", null);
        notificacionService.crear(rm, "EVIDENCIA_CURSO_PENDIENTE", CategoriaNotificacion.CURSO,
                "Evidencia de curso pendiente de revisión", "Ana subió evidencia.",
                "COLABORADOR_CURSO", evidencia.getId());
        notificacionService.crear(rm, "CURSO_SOLICITADO", CategoriaNotificacion.CURSO,
                "Nueva solicitud de curso", "Ana solicitó un curso.", "COLABORADOR_CURSO", evidencia.getId());

        List<NotificacionView> vistas = notificacionService.listar(rm.getId());
        assertEquals(URL_EVIDENCIAS, urlPorTitulo(vistas, "Evidencia de curso pendiente de revisión"));
        assertEquals("/rm/cursos/solicitudes", urlPorTitulo(vistas, "Nueva solicitud de curso"));
    }

    @Test
    void solicitudesSolicitadoConservanSuFlujoSinAccionesDeEvidencia() throws Exception {
        ColaboradorCurso paraAprobar = inscripcion(colaboradorUno, spring,
                OrigenCurso.SOLICITUD_COLABORADOR, EstadoColaboradorCurso.SOLICITADO);
        ColaboradorCurso paraRechazar = inscripcion(colaboradorDos, aws,
                OrigenCurso.SOLICITUD_COLABORADOR, EstadoColaboradorCurso.SOLICITADO);
        ColaboradorCurso evidencia = evidenciaPendiente(colaboradorDos, spring, "luis.pdf", null);

        String html = mockMvc.perform(get("/rm/cursos/solicitudes"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("estadoSeleccionado", "SOLICITADO"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("data-action=\"/rm/cursos/solicitudes/" + paraAprobar.getId() + "/aprobar\""));
        assertFalse(html.contains("/evidencia/aprobar\""), "La vista predeterminada no incluye evidencias");
        assertFalse(html.contains(">Ver evidencia</a>"));
        assertTrue(bloqueModal(html, "approveCourseModal").contains("class=\"btn btn-success js-confirmar\" disabled"),
                "La aprobación de solicitudes sigue exigiendo motivo");

        autenticarRm();
        mockMvc.perform(post("/rm/cursos/solicitudes/{id}/aprobar", paraAprobar.getId())
                        .param("motivo", "Aporta al proyecto actual."))
                .andExpect(redirectedUrl("/rm/cursos/solicitudes"))
                .andExpect(flash().attribute("mensajeExito", "Solicitud aprobada; el colaborador fue notificado."));
        mockMvc.perform(post("/rm/cursos/solicitudes/{id}/rechazar", paraRechazar.getId()))
                .andExpect(redirectedUrl("/rm/cursos/solicitudes"))
                .andExpect(flash().attributeExists("mensajeError"));
        mockMvc.perform(post("/rm/cursos/solicitudes/{id}/rechazar", paraRechazar.getId())
                        .param("motivo", "Primero el curso básico."))
                .andExpect(redirectedUrl("/rm/cursos/solicitudes"))
                .andExpect(flash().attributeExists("mensajeExito"));

        assertEquals(EstadoColaboradorCurso.EN_CURSO,
                colaboradorCursoRepository.findById(paraAprobar.getId()).orElseThrow().getEstado());
        assertEquals(EstadoColaboradorCurso.RECHAZADO,
                colaboradorCursoRepository.findById(paraRechazar.getId()).orElseThrow().getEstado());
        assertEquals(EstadoColaboradorCurso.EVIDENCIA_PENDIENTE,
                colaboradorCursoRepository.findById(evidencia.getId()).orElseThrow().getEstado());
    }

    private ColaboradorCurso evidenciaPendiente(Usuario colaborador, Curso curso, String archivo,
                                                LocalDateTime fechaSolicitud) {
        ColaboradorCurso item = inscripcion(colaborador, curso, OrigenCurso.SOLICITUD_COLABORADOR,
                EstadoColaboradorCurso.EVIDENCIA_PENDIENTE);
        item.setEvidenciaUrl("/uploads/cursos-evidencia/" + archivo);
        item.setFechaEvidencia(LocalDateTime.now());
        if (fechaSolicitud != null) item.setFechaSolicitud(fechaSolicitud);
        return colaboradorCursoRepository.save(item);
    }

    // Evidencia de una inscripción aprobada por otro RM (ya inactivo), distinto del que revisa la evidencia.
    private ColaboradorCurso evidenciaAprobadaPorOtroRm() {
        rmInscripcion = usuario("rm.inscripcion.cursos@skillbridge.test", "Iris", "Salas",
                rol("RESOURCE_MANAGER"), null);
        rmInscripcion.setActivo(false);
        rmInscripcion = usuarioRepository.save(rmInscripcion);
        ColaboradorCurso item = evidenciaPendiente(colaboradorUno, spring, "ana.pdf", null);
        item.setAsignadoPor(rmInscripcion);
        return colaboradorCursoRepository.save(item);
    }

    private List<LogAuditoria> auditorias(String accion, Long inscripcionId) {
        return logAuditoriaRepository.findAllConUsuario().stream()
                .filter(log -> accion.equals(log.getAccion()) && "COLABORADOR_CURSO".equals(log.getEntidad())
                        && inscripcionId.equals(log.getEntidadId()))
                .toList();
    }

    private void autenticarRm() {
        UsuarioDetails details = new UsuarioDetails(usuarioRepository.findActivosByRolNombre("RESOURCE_MANAGER")
                .stream().filter(u -> u.getId().equals(rm.getId())).findFirst().orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    private String bloqueModal(String html, String id) {
        int posicion = html.indexOf("id=\"" + id + "\"");
        assertTrue(posicion >= 0, "Existe el modal " + id);
        int inicio = html.lastIndexOf("<div", posicion);
        return html.substring(inicio, html.indexOf("</form>", posicion));
    }

    private String etiquetaAnterior(String html, String texto, String apertura) {
        int posicion = html.indexOf(texto);
        assertTrue(posicion >= 0, "Existe " + texto);
        return html.substring(html.lastIndexOf(apertura, posicion), posicion);
    }

    // TASK-031: catálogo y bandeja sin resultados usan el estado vacío común; indicadores y filtros se conservan.
    @Test
    void catalogoYBandejaSinResultadosUsanElEstadoVacioComun() throws Exception {
        String catalogo = mockMvc.perform(get("/rm/cursos").param("busqueda", "sin-coincidencias-t031"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(1, ocurrencias(catalogo, "class=\"empty-state\""));
        assertTrue(catalogo.contains("<div class=\"card\"><div class=\"empty-state\">"), catalogo);
        assertTrue(catalogo.contains("<span class=\"empty-state-icon\" aria-hidden=\"true\">"));
        assertTrue(catalogo.contains("<div class=\"empty-state-title\">Sin cursos</div>"));
        assertTrue(catalogo.contains("No se encontraron cursos activos con estos filtros."));
        assertFalse(catalogo.contains("card-body text-center text-secondary py-5"));
        assertTrue(catalogo.contains("value=\"sin-coincidencias-t031\""));
        assertEquals(4, ocurrencias(catalogo, "class=\"card metric-card\""));

        String bandeja = mockMvc.perform(get("/rm/cursos/solicitudes").param("busqueda", "sin-coincidencias-t031"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(1, ocurrencias(bandeja, "class=\"empty-state\""));
        assertTrue(bandeja.contains("<tr><td colspan=\"7\" class=\"empty-state-cell\"><div class=\"empty-state\">"), bandeja);
        assertTrue(bandeja.contains("<div class=\"empty-state-title\">Sin inscripciones</div>"));
        assertTrue(bandeja.contains("No se encontraron inscripciones con estos filtros."));
        assertTrue(bandeja.contains("value=\"sin-coincidencias-t031\""));
        assertEquals(5, ocurrencias(bandeja, "class=\"card metric-card\""));
        assertTrue(bandeja.contains("id=\"evidenciasPorRevisar\""));
        assertFalse(catalogo.contains("empty-state-action") || bandeja.contains("empty-state-action"));
    }

    private int ocurrencias(String html, String texto) {
        return html.split(java.util.regex.Pattern.quote(texto), -1).length - 1;
    }

    private String urlPorTitulo(List<NotificacionView> vistas, String titulo) {
        return vistas.stream().filter(item -> titulo.equals(item.titulo()))
                .findFirst().orElseThrow().url();
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
