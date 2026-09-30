package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.Cargo;
import com.pucp.skillb_ia.repository.CargoRepository;
import com.pucp.skillb_ia.dto.RmForoView;
import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.*;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.service.rm.RmForoConsultaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.math.BigDecimal;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class RmForoTests {
    @Autowired private WebApplicationContext context;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CargoRepository cargoRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private ForoRepository foroRepository;
    @Autowired private EtiquetaRepository etiquetaRepository;
    @Autowired private PublicacionForoRepository publicacionRepository;
    @Autowired private RespuestaForoRepository respuestaRepository;
    @Autowired private VotoPublicacionRepository votoPublicacionRepository;
    @Autowired private VotoRespuestaRepository votoRespuestaRepository;
    @Autowired private RmForoConsultaService foroService;

    private MockMvc mockMvc;
    private Usuario pm;
    private Usuario colaborador;
    private Usuario votante;
    private Proyecto proyecto;
    private Etiqueta etiqueta;
    private final List<Proyecto> proyectosTemporales = new ArrayList<>();

    @BeforeEach
    void prepararDatos() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        borrarForos();

        pm = usuario("pm.foro@skillbridge.test", "Paula", "Mora", rol("PROJECT_MANAGER"));
        colaborador = usuario("col.foro@skillbridge.test", "Carlos", "Foro", rol("COLABORADOR"));
        colaborador.setCargo(cargoDePrueba("Backend Developer"));
        colaborador = usuarioRepository.save(colaborador);
        votante = usuario("votante.foro@skillbridge.test", "Valeria", "Voto", rol("COLABORADOR"));

        proyecto = proyectoRepository.findAll().stream()
                .filter(item -> "Foro Test".equals(item.getNombre()))
                .findFirst().orElseGet(Proyecto::new);
        proyecto.setNombre("Foro Test");
        proyecto.setDescripcion("Proyecto utilizado para probar la consulta de foros.");
        proyecto.setEstado(EstadoProyecto.ACTIVO);
        proyecto.setPrioridad(Prioridad.MEDIA);
        proyecto.setJustificacionPrioridad("Validación automática.");
        proyecto.setColaboradoresRequeridos(2);
        proyecto.setPm(pm);
        proyecto = proyectoRepository.save(proyecto);

        etiqueta = etiquetaRepository.findAll().stream()
                .filter(item -> "Pregunta Test".equals(item.getNombre()))
                .findFirst().orElseGet(() -> {
                    Etiqueta nueva = new Etiqueta();
                    nueva.setNombre("Pregunta Test");
                    return etiquetaRepository.save(nueva);
                });
    }

    @AfterEach
    void limpiarProyectosTemporales() {
        borrarForos();
        proyectoRepository.deleteAll(proyectosTemporales);
        proyectosTemporales.clear();
    }

    @Test
    void rmPuedeConsultarForosPublicosYPrivadosSinAsignacion() {
        Foro publico = foro("Foro público test", true, proyecto);
        Foro privado = foro("Foro privado test", false, proyecto);

        List<RmForoView> foros = foroService.listar();

        assertTrue(foros.stream().anyMatch(item -> item.getId().equals(publico.getId())));
        assertTrue(foros.stream().anyMatch(item -> item.getId().equals(privado.getId())));
    }

    @Test
    void detalleIncluyeRespuestasSolucionVotosYOrdenamiento() {
        Foro foro = foro("Foro con actividad test", false, proyecto);
        PublicacionForo antigua = publicacion(foro, colaborador, "Publicación más votada",
                LocalDateTime.now().minusDays(2));
        PublicacionForo reciente = publicacion(foro, pm, "Publicación más reciente",
                LocalDateTime.now().minusHours(2));
        RespuestaForo respuesta = respuesta(antigua, pm, true,
                LocalDateTime.now().minusDays(1));
        voto(antigua, colaborador, TipoVoto.POSITIVO);
        voto(antigua, votante, TipoVoto.POSITIVO);
        votoRespuesta(respuesta, votante, TipoVoto.POSITIVO);

        RmForoView porVotos = foroService.obtener(foro.getId(), "votos");
        RmForoView porFecha = foroService.obtener(foro.getId(), "fecha");

        assertEquals("Publicación más votada", porVotos.getPublicaciones().get(0).getTitulo());
        assertEquals("Publicación más reciente", porFecha.getPublicaciones().get(0).getTitulo());
        RmForoView.Publicacion publicacion = porVotos.getPublicaciones().get(0);
        assertEquals(2, publicacion.getPuntaje());
        assertEquals(1, publicacion.getRespuestas().size());
        assertTrue(publicacion.getRespuestas().get(0).isSolucion());
        assertEquals(1, publicacion.getRespuestas().get(0).getPuntaje());
        assertEquals(2, porVotos.getParticipantes());
    }

    @Test
    void renderizaListadoYDetalleDeSoloLectura() throws Exception {
        Foro foro = foro("Foro render test", false, proyecto);
        publicacion(foro, colaborador, "Tema visible", LocalDateTime.now());

        mockMvc.perform(get("/rm/foros"))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-foros"))
                .andExpect(model().attributeExists(
                        "foros", "totalForos", "totalActivos", "totalPublicaciones"));

        mockMvc.perform(get("/rm/foros/detalle")
                        .param("id", foro.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-foro-detalle"))
                .andExpect(model().attributeExists("foro", "ordenActual"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("method=\"post\""))));
    }

    // TASK-039: filtros y paginación del listado en el servidor.

    @Test
    void paginaUnoYDosConMasDeCuatroForosEIndicadoresSobreElTotal() throws Exception {
        List<Foro> lote = loteDesordenado("Foro lote");
        publicacion(lote.get(0), colaborador, "Tema del lote", LocalDateTime.now().minusMinutes(5));

        MvcResult pagina1 = listado("pagina", "1");
        assertEquals(4, forosDe(pagina1).size());
        assertEquals(1, modelo(pagina1, "paginaActual"));
        assertEquals(2, modelo(pagina1, "totalPaginas"));
        assertEquals(6L, modelo(pagina1, "totalRegistros"));
        assertTrue(html(pagina1).contains("Mostrando 1-4 de 6 foros"));

        MvcResult pagina2 = listado("pagina", "2");
        assertEquals(2, forosDe(pagina2).size());
        assertEquals(2, modelo(pagina2, "paginaActual"));
        assertTrue(html(pagina2).contains("Mostrando 5-6 de 6 foros"));

        // Los indicadores no dependen de los filtros ni de la página.
        for (MvcResult resultado : List.of(pagina1, pagina2, listado("estado", "GENERAL"),
                listado("busqueda", "no existe", "pagina", "3"))) {
            assertEquals(6, modelo(resultado, "totalForos"));
            assertEquals(6L, modelo(resultado, "totalActivos"));
            assertEquals(1, modelo(resultado, "totalPublicaciones"));
            assertNotNull(modelo(resultado, "ultimaActividad"));
        }
    }

    @Test
    void paginacionConservaElOrdenDelRepositorio() throws Exception {
        loteDesordenado("Foro orden");

        List<Long> paginados = new ArrayList<>();
        forosDe(listado("pagina", "1")).forEach(foro -> paginados.add(foro.getId()));
        forosDe(listado("pagina", "2")).forEach(foro -> paginados.add(foro.getId()));

        List<Long> delServicio = foroService.listar().stream().map(RmForoView::getId).toList();
        List<Long> porFechaDescendente = foroRepository.findAll().stream()
                .sorted(Comparator.comparing(Foro::getFechaCreacion).reversed())
                .map(Foro::getId).toList();
        assertEquals(delServicio, paginados);
        assertEquals(porFechaDescendente, paginados);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ANALISIS|Foro Análisis de Datos",
            "análisis|Foro Análisis de Datos",
            "AnÁlIsIs DE datos|Foro Análisis de Datos",
            "'   analisis   '|Foro Análisis de Datos",
            "LOGISTICA|Foro Logística",
            "MIGRACIÓN|Foro Logística",
            "migracion de servidores|Foro Logística",
            "pregunta TEST|Foro Logística"
    })
    void busquedaIgnoraMayusculasYTildes(String busqueda, String esperado) throws Exception {
        foro("Foro Análisis de Datos", true, proyecto);
        Foro logistica = foro("Foro Logística", true, proyecto);
        publicacion(logistica, colaborador, "Migración de servidores", LocalDateTime.now());
        foro("Foro Otro Tema", true, proyecto);

        MvcResult resultado = listado("busqueda", busqueda);

        assertEquals(List.of(esperado), nombres(forosDe(resultado)));
        assertEquals(busqueda.trim(), modelo(resultado, "busqueda"));
    }

    @Test
    void busquedaIncluyeProyectoYProjectManager() throws Exception {
        Proyecto otro = proyectoTemporal(EstadoProyecto.ACTIVO);
        foro("Foro A", true, proyecto);
        foro("Foro B", true, otro);
        foro("Foro C", true, null);

        assertEquals(Set.of("Foro A", "Foro B"), Set.copyOf(nombres(forosDe(listado("busqueda", "PAULA MORA")))));
        assertEquals(List.of("Foro B"), nombres(forosDe(listado("busqueda", otro.getNombre().toUpperCase()))));
        assertEquals(List.of("Foro C"), nombres(forosDe(listado("busqueda", "comunidad GENERAL"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GENERAL", "EN_REVISION", "ACTIVO", "EN_ESPERA", "RECHAZADO", "CANCELADO", "FINALIZADO"})
    void estadoFiltraGeneralYEstadosDelProyecto(String estado) throws Exception {
        foro("Foro estado GENERAL", true, null);
        for (EstadoProyecto estadoProyecto : EstadoProyecto.values()) {
            foro("Foro estado " + estadoProyecto.name(), true, proyectoTemporal(estadoProyecto));
        }

        MvcResult resultado = listado("estado", estado);

        List<RmForoView> foros = forosDe(resultado);
        assertEquals(List.of("Foro estado " + estado), nombres(foros));
        assertEquals(estado, foros.get(0).getProyectoEstadoCodigo());
        assertEquals(estado, modelo(resultado, "estado"));
        assertEquals(1L, modelo(resultado, "totalRegistros"));
        assertTrue(html(resultado).contains("value=\"" + estado + "\" selected=\"selected\""));
    }

    @ParameterizedTest
    @CsvSource({
            "recent, Foro reciente por publicación;Foro reciente por respuesta;Foro nuevo sin publicaciones",
            "older, Foro antiguo con publicación;Foro antiguo sin publicaciones"
    })
    void actividadSeparaLosUltimosSieteDias(String actividad, String esperados) throws Exception {
        LocalDateTime ahora = LocalDateTime.now();
        Foro porPublicacion = foro("Foro reciente por publicación", true, proyecto, ahora.minusDays(30));
        publicacion(porPublicacion, colaborador, "Tema reciente", ahora.minusDays(2));
        Foro porRespuesta = foro("Foro reciente por respuesta", true, proyecto, ahora.minusDays(30));
        PublicacionForo antigua = publicacion(porRespuesta, colaborador, "Tema antiguo", ahora.minusDays(20));
        respuesta(antigua, pm, false, ahora.minusDays(1));
        foro("Foro nuevo sin publicaciones", true, proyecto, ahora.minusDays(6));
        Foro antiguo = foro("Foro antiguo con publicación", true, proyecto, ahora.minusDays(40));
        publicacion(antiguo, colaborador, "Tema viejo", ahora.minusDays(10));
        foro("Foro antiguo sin publicaciones", true, proyecto, ahora.minusDays(8));

        MvcResult resultado = listado("actividad", actividad);

        List<RmForoView> foros = forosDe(resultado);
        assertEquals(Set.of(esperados.split(";")), Set.copyOf(nombres(foros)));
        foros.forEach(foro -> assertEquals("recent".equals(actividad), foro.isActividadReciente()));
        assertEquals(actividad, modelo(resultado, "actividad"));
        assertTrue(html(resultado).contains("value=\"" + actividad + "\" selected=\"selected\""));
    }

    @Test
    void combinacionDeFiltros() throws Exception {
        LocalDateTime ahora = LocalDateTime.now();
        foro("Foro Análisis reciente", true, proyecto, ahora.minusDays(1));
        foro("Foro Análisis antiguo", true, proyecto, ahora.minusDays(20));
        foro("Foro Análisis en espera", true, proyectoTemporal(EstadoProyecto.EN_ESPERA), ahora.minusDays(1));
        foro("Foro Análisis general", true, null, ahora.minusDays(1));
        foro("Foro Otro reciente", true, proyecto, ahora.minusDays(1));

        MvcResult resultado = listado("busqueda", "ANALISIS", "estado", "ACTIVO", "actividad", "recent");

        assertEquals(List.of("Foro Análisis reciente"), nombres(forosDe(resultado)));
        assertEquals(1L, modelo(resultado, "totalRegistros"));
        String html = html(resultado);
        assertTrue(html.contains("value=\"ANALISIS\""));
        assertTrue(html.contains("value=\"ACTIVO\" selected=\"selected\""));
        assertTrue(html.contains("value=\"recent\" selected=\"selected\""));
    }

    @Test
    void enlacesDePaginacionConservanLosFiltros() throws Exception {
        loteDesordenado("Foro enlace");
        foro("Foro ajeno", true, null);
        String filtros = "busqueda=enlace&estado=ACTIVO&actividad=recent";

        String pagina1 = html(listado("busqueda", "enlace", "estado", "ACTIVO", "actividad", "recent"));
        assertEquals("/rm/foros?" + filtros + "&pagina=2", enlace(pagina1, "Siguiente"));
        assertEquals("/rm/foros?" + filtros + "&pagina=2", enlace(pagina1, "2"));
        assertEquals("/rm/foros?" + filtros + "&pagina=1", enlace(pagina1, "1"));
        assertTrue(pagina1.contains("Mostrando 1-4 de 6 foros"));

        MvcResult segunda = mockMvc.perform(get(enlace(pagina1, "Siguiente")))
                .andExpect(status().isOk()).andReturn();
        assertEquals(2, forosDe(segunda).size());
        assertEquals(2, modelo(segunda, "paginaActual"));
        String pagina2 = html(segunda);
        assertTrue(pagina2.contains("Mostrando 5-6 de 6 foros"));
        assertEquals("/rm/foros?" + filtros + "&pagina=1", enlace(pagina2, "Anterior"));
        assertTrue(pagina2.contains("value=\"enlace\""));
        assertTrue(pagina2.contains("value=\"ACTIVO\" selected=\"selected\""));
        assertTrue(pagina2.contains("value=\"recent\" selected=\"selected\""));
    }

    @ParameterizedTest
    @CsvSource({"0, 1", "-3, 1", "'', 1", "abc, 1", "1.5, 1", "999, 2", "2, 2"})
    void paginaInvalidaUsaUnValorSeguro(String pagina, int esperada) throws Exception {
        loteDesordenado("Foro página");

        MvcResult resultado = listado("pagina", pagina);

        assertEquals(esperada, modelo(resultado, "paginaActual"));
        assertEquals(esperada == 1 ? 4 : 2, forosDe(resultado).size());
    }

    @ParameterizedTest
    @CsvSource({
            "FOO, BAR", "'', ''", "all, all", "'   ', '   '", "PROYECTO, 7dias"
    })
    void filtrosInvalidosUsanTodos(String estado, String actividad) throws Exception {
        foro("Foro válido 1", true, proyecto);
        foro("Foro válido 2", true, null, LocalDateTime.now().minusDays(30));

        MvcResult resultado = listado("estado", estado, "actividad", actividad, "busqueda", "   ");

        assertEquals(2L, modelo(resultado, "totalRegistros"));
        assertNull(modelo(resultado, "estado"));
        assertNull(modelo(resultado, "actividad"));
        assertNull(modelo(resultado, "busqueda"));
    }

    @Test
    void filtrosSinDistinguirMayusculasSeNormalizan() {
        RmForoConsultaService.FiltrosForo filtros = foroService.normalizarFiltros(" x ", "activo", "RECENT");
        assertEquals(new RmForoConsultaService.FiltrosForo("x", "ACTIVO", "recent"), filtros);
        assertEquals(100, foroService.normalizarFiltros("a".repeat(150), null, null).busqueda().length());
    }

    @Test
    void listadoFuncionaSinJavaScript() throws Exception {
        loteDesordenado("Foro sin JS");

        String html = html(listado());

        assertTrue(html.contains("<form id=\"forumFiltersForm\" method=\"get\" action=\"/rm/foros\""));
        assertTrue(html.contains("name=\"busqueda\""));
        assertTrue(html.contains("name=\"estado\""));
        assertTrue(html.contains("name=\"actividad\""));
        assertTrue(html.contains("type=\"submit\""));
        assertTrue(Pattern.compile("<a id=\"clearForumFilters\"[^>]*href=\"/rm/foros\"").matcher(html).find());
        assertFalse(html.contains("rm-foros.js"));
        assertFalse(html.contains("data-search"));
        assertFalse(html.contains("class=\"page-link\" href=\"#\""));
        assertEquals(4, Pattern.compile("class=\"forum-row forum-data-row\"").matcher(html).results().count());
        assertFalse(html.contains("forum-data-row d-none"));
        assertTrue(html.contains("id=\"forumEmptyFiltered\"") && html.contains("forum-empty d-none"));
    }

    @Test
    void estadosVaciosSeResuelvenEnElServidor() throws Exception {
        String sinForos = html(listado());
        assertTrue(sinForos.contains("No hay foros registrados en la organización."));

        foro("Foro existente", true, proyecto);
        String sinResultados = html(listado("busqueda", "inexistente"));
        assertFalse(sinResultados.contains("No hay foros registrados en la organización."));
        assertTrue(Pattern.compile("class=\"forum-empty\" id=\"forumEmptyFiltered\"").matcher(sinResultados).find());
        assertTrue(sinResultados.contains("Mostrando 0-0 de 0 foros"));
    }

    @Test
    void detalleYAccesoDesdeProyectoSiguenFuncionando() throws Exception {
        Foro foro = foro("Foro del proyecto test", false, proyecto);
        publicacion(foro, colaborador, "Primera publicación", LocalDateTime.now().minusDays(1));

        String listadoHtml = html(listado("busqueda", "proyecto test"));
        assertTrue(listadoHtml.contains("href=\"/rm/foros/detalle?id=" + foro.getId() + "\""));

        mockMvc.perform(get("/rm/foros/detalle").param("id", foro.getId().toString())
                        .param("ordenar", "votos"))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-foro-detalle"))
                .andExpect(model().attribute("ordenActual", "votos"));
        mockMvc.perform(get("/rm/foros/detalle").param("id", "999999"))
                .andExpect(redirectedUrl("/rm/foros?noEncontrado=true"));

        mockMvc.perform(get("/rm/proyectos/detalle").param("id", proyecto.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("foroId", foro.getId()))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"/rm/foros/detalle?id=" + foro.getId() + "\"")));
    }

    private List<Foro> loteDesordenado(String prefijo) {
        // Se crean en un orden distinto al de la fecha para no confundir fecha con id.
        int[] horas = {3, 1, 5, 2, 6, 4};
        List<Foro> lote = new ArrayList<>();
        for (int hora : horas) {
            lote.add(foro(prefijo + " " + hora, true, proyecto, LocalDateTime.now().minusHours(hora)));
        }
        return lote;
    }

    private MvcResult listado(String... parametros) throws Exception {
        MockHttpServletRequestBuilder peticion = get("/rm/foros");
        for (int i = 0; i < parametros.length; i += 2) {
            peticion.param(parametros[i], parametros[i + 1]);
        }
        return mockMvc.perform(peticion)
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-foros"))
                .andReturn();
    }

    @SuppressWarnings("unchecked")
    private List<RmForoView> forosDe(MvcResult resultado) {
        return (List<RmForoView>) modelo(resultado, "foros");
    }

    private Object modelo(MvcResult resultado, String clave) {
        return resultado.getModelAndView().getModel().get(clave);
    }

    private String html(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private List<String> nombres(List<RmForoView> foros) {
        return foros.stream().map(RmForoView::getNombre).toList();
    }

    private String enlace(String html, String texto) {
        Matcher matcher = Pattern.compile("<a class=\"page-link\"\\s+href=\"([^\"]*)\"\\s*>"
                + Pattern.quote(texto) + "</a>").matcher(html);
        assertTrue(matcher.find(), "No se encontró el enlace " + texto);
        return matcher.group(1).replace("&amp;", "&");
    }

    private Proyecto proyectoTemporal(EstadoProyecto estado) {
        Proyecto nuevo = new Proyecto();
        nuevo.setNombre("Foro Temp " + estado.name() + " " + UUID.randomUUID().toString().substring(0, 8));
        nuevo.setDescripcion("Proyecto temporal para filtrar foros.");
        nuevo.setEstado(estado);
        nuevo.setPrioridad(Prioridad.BAJA);
        nuevo.setJustificacionPrioridad("Validación automática.");
        nuevo.setColaboradoresRequeridos(1);
        nuevo.setPm(pm);
        nuevo = proyectoRepository.save(nuevo);
        proyectosTemporales.add(nuevo);
        return nuevo;
    }

    private void borrarForos() {
        votoRespuestaRepository.deleteAll();
        votoPublicacionRepository.deleteAll();
        respuestaRepository.deleteAll();
        publicacionRepository.deleteAll();
        foroRepository.deleteAll();
    }

    private Foro foro(String nombre, boolean publico, Proyecto proyecto) {
        return foro(nombre, publico, proyecto, null);
    }

    private Foro foro(String nombre, boolean publico, Proyecto proyecto, LocalDateTime fechaCreacion) {
        Foro foro = new Foro();
        foro.setNombre(nombre);
        foro.setTipo(proyecto == null ? TipoForo.GENERAL : TipoForo.PROYECTO);
        foro.setEsPublico(publico);
        foro.setProyecto(proyecto);
        foro.setFechaCreacion(fechaCreacion);
        return foroRepository.save(foro);
    }

    private PublicacionForo publicacion(Foro foro, Usuario autor, String titulo,
                                        LocalDateTime fecha) {
        PublicacionForo publicacion = new PublicacionForo();
        publicacion.setForo(foro);
        publicacion.setAutor(autor);
        publicacion.setEtiqueta(etiqueta);
        publicacion.setTitulo(titulo);
        publicacion.setContenido("Contenido de prueba del foro.");
        publicacion.setFechaCreacion(fecha);
        return publicacionRepository.save(publicacion);
    }

    private RespuestaForo respuesta(PublicacionForo publicacion, Usuario autor,
                                     boolean solucion, LocalDateTime fecha) {
        RespuestaForo respuesta = new RespuestaForo();
        respuesta.setPublicacion(publicacion);
        respuesta.setAutor(autor);
        respuesta.setContenido("Respuesta de prueba.");
        respuesta.setEsSolucion(solucion);
        respuesta.setFechaCreacion(fecha);
        return respuestaRepository.save(respuesta);
    }

    private void voto(PublicacionForo publicacion, Usuario usuario, TipoVoto tipo) {
        VotoPublicacion voto = new VotoPublicacion();
        voto.setId(new VotoPublicacionId(usuario.getId(), publicacion.getId()));
        voto.setUsuario(usuario);
        voto.setPublicacion(publicacion);
        voto.setTipo(tipo);
        votoPublicacionRepository.save(voto);
    }

    private void votoRespuesta(RespuestaForo respuesta, Usuario usuario, TipoVoto tipo) {
        VotoRespuesta voto = new VotoRespuesta();
        voto.setId(new VotoRespuestaId(usuario.getId(), respuesta.getId()));
        voto.setUsuario(usuario);
        voto.setRespuesta(respuesta);
        voto.setTipo(tipo);
        votoRespuestaRepository.save(voto);
    }

    private Rol rol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol rol = new Rol();
            rol.setNombre(nombre);
            return rolRepository.save(rol);
        });
    }

    private Usuario usuario(String correo, String nombre, String apellido, Rol rol) {
        Usuario usuario = usuarioRepository.findByCorreo(correo).orElseGet(Usuario::new);
        usuario.setCorreo(correo);
        usuario.setNombre(nombre);
        usuario.setApellido(apellido);
        usuario.setRol(rol);
        usuario.setActivo(true);
        return usuarioRepository.save(usuario);
    }

    private Cargo cargoDePrueba(String nombre) {
        return cargoRepository.findByNombre(nombre).orElseGet(() -> cargoRepository.save(
                new Cargo(nombre, new BigDecimal("2000"), new BigDecimal("3000"), new BigDecimal("4000"))));
    }
}
