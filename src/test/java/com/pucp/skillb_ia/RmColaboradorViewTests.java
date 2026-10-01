package com.pucp.skillb_ia;

import com.pucp.skillb_ia.dto.RmColaboradorDetalle;
import com.pucp.skillb_ia.dto.RmColaboradorResumen;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.CategoriaHabilidad;
import com.pucp.skillb_ia.model.ColaboradorHabilidad;
import com.pucp.skillb_ia.model.Habilidad;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.Cargo;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.EstadoValidacion;
import com.pucp.skillb_ia.model.enums.NivelDominio;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.CargoRepository;
import com.pucp.skillb_ia.repository.CategoriaHabilidadRepository;
import com.pucp.skillb_ia.repository.ColaboradorHabilidadRepository;
import com.pucp.skillb_ia.repository.HabilidadRepository;
import com.pucp.skillb_ia.model.enums.NivelExperiencia;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.rm.RmColaboradorConsultaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@ActiveProfiles("test")
class RmColaboradorViewTests {

    // Nombres únicos para que la única coincidencia posible sea la propia habilidad.
    private static final String VALIDADA = "Java Validada T015";
    private static final String PENDIENTE = "Go Pendiente T015";
    private static final String RECHAZADA = "Rust Rechazada T015";
    private static final String INACTIVA = "Cobol Inactiva T015";
    // Lote de TASK-029: nombre único; el apellido (C01..C12) fija el orden y sirve de código.
    private static final String LOTE = "Lote029";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired private CargoRepository cargoRepository;
    @Autowired private CategoriaHabilidadRepository categoriaRepository;
    @Autowired private HabilidadRepository habilidadRepository;
    @Autowired private ColaboradorHabilidadRepository colaboradorHabilidadRepository;
    @Autowired private RmColaboradorConsultaService consultaService;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private AsignacionRepository asignacionRepository;

    private MockMvc mockMvc;
    private Long colaboradorId;
    private final List<Usuario> loteCreado = new ArrayList<>();
    private final List<Long> asignacionesCreadas = new ArrayList<>();
    private final List<Long> proyectosCreados = new ArrayList<>();

    @BeforeEach
    void prepararDatos() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        Usuario colaborador = usuarioRepository.findByCorreo("colaborador.vista@skillbridge.test")
                .orElseGet(() -> {
                    Rol rol = rolRepository.findByNombre("COLABORADOR")
                            .orElseGet(() -> {
                                Rol nuevoRol = new Rol();
                                nuevoRol.setNombre("COLABORADOR");
                                return rolRepository.save(nuevoRol);
                            });

                    Usuario nuevoColaborador = new Usuario();
                    nuevoColaborador.setCorreo("colaborador.vista@skillbridge.test");
                    nuevoColaborador.setNombre("María");
                    nuevoColaborador.setApellido("Prueba");
                    nuevoColaborador.setCargo(cargoDePrueba("Backend Developer"));
                    nuevoColaborador.setRol(rol);
                    nuevoColaborador.setActivo(true);
                    nuevoColaborador.setNivelExperiencia(NivelExperiencia.SEMI_SENIOR);
                    nuevoColaborador.setHorasDisponibles(BigDecimal.valueOf(16));
                    nuevoColaborador.setAniosExperiencia(BigDecimal.valueOf(3));
                    return usuarioRepository.save(nuevoColaborador);
                });

        colaboradorId = colaborador.getId();

        asignarHabilidad(colaborador, VALIDADA, EstadoValidacion.VALIDADA, true);
        asignarHabilidad(colaborador, PENDIENTE, EstadoValidacion.PENDIENTE, true);
        asignarHabilidad(colaborador, RECHAZADA, EstadoValidacion.RECHAZADA, true);
        asignarHabilidad(colaborador, INACTIVA, EstadoValidacion.VALIDADA, false);
    }

    // El lote no debe alterar a las demás clases: se borran sus asignaciones y proyectos y se desactiva.
    @AfterEach
    void limpiarLote() {
        asignacionRepository.deleteAllById(asignacionesCreadas);
        proyectoRepository.deleteAllById(proyectosCreados);
        loteCreado.forEach(usuario -> {
            usuario.setActivo(false);
            usuarioRepository.save(usuario);
        });
        asignacionesCreadas.clear();
        proyectosCreados.clear();
        loteCreado.clear();
    }

    @Test
    void renderizaDirectorioConDatosReales() throws Exception {
        mockMvc.perform(get("/rm/colaboradores"))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-colaboradores"))
                .andExpect(model().attributeExists("colaboradores", "totalColaboradores"));
    }

    // TASK-032: la vista raíz muestra directamente el encabezado, sin ruta de navegación de un solo elemento.
    @Test
    void directorioNoMuestraRutaDeNavegacionRedundante() throws Exception {
        String html = mockMvc.perform(get("/rm/colaboradores"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8).replaceAll("\\s+", " ");

        assertFalse(html.contains("breadcrumb-wrap"));
        assertFalse(html.contains("Migas de pan"));
        assertTrue(html.contains("<h1 class=\"page-title\"> Colaboradores </h1>"));
        assertTrue(html.contains("Consulta habilidades, nivel de experiencia"));
    }

    @Test
    void renderizaPerfilDelColaboradorSeleccionado() throws Exception {
        mockMvc.perform(get("/rm/colaboradores/perfil").param("id", colaboradorId.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-perfil-colaborador"))
                .andExpect(model().attributeExists("colaborador"));
    }

    @Test
    void resumenYTextoDeBusquedaUsanSoloHabilidadesValidadas() {
        RmColaboradorResumen resumen = consultaService.listarColaboradoresActivos().stream()
                .filter(item -> item.getId().equals(colaboradorId))
                .findFirst().orElseThrow();

        assertEquals(List.of(VALIDADA), resumen.getHabilidades());
        assertTrue(resumen.getTextoBusqueda().contains(VALIDADA));
        assertFalse(resumen.getTextoBusqueda().contains(PENDIENTE));
        assertFalse(resumen.getTextoBusqueda().contains(RECHAZADA));
        assertFalse(resumen.getTextoBusqueda().contains(INACTIVA));
        // La búsqueda GET sobre este texto: busquedaPorHabilidadSoloCoincideConHabilidadesValidadas.
    }

    @Test
    void directorioMuestraSoloHabilidadesValidadasEnLasTarjetas() throws Exception {
        // Con 6 tarjetas por página se acota por nombre para que la tarjeta esté en la página visible.
        String html = mockMvc.perform(get("/rm/colaboradores").param("busqueda", "María Prueba"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(html.contains(VALIDADA));
        assertFalse(html.contains(PENDIENTE));
        assertFalse(html.contains(RECHAZADA));
        assertFalse(html.contains(INACTIVA));
    }

    @Test
    void perfilCompletoConservaTodasLasHabilidadesActivasConSuEstado() throws Exception {
        RmColaboradorDetalle detalle = consultaService.obtenerDetalle(colaboradorId);
        Map<String, String> estados = detalle.getHabilidades().stream()
                .collect(Collectors.toMap(RmColaboradorDetalle.HabilidadDetalle::getNombre,
                        RmColaboradorDetalle.HabilidadDetalle::getEstadoCodigo));
        assertEquals(Map.of(VALIDADA, "VALIDADA", PENDIENTE, "PENDIENTE", RECHAZADA, "RECHAZADA"), estados);
        // El resumen del mismo detalle (cabecera y propuesta) sigue siendo solo validado.
        assertEquals(List.of(VALIDADA), detalle.getResumen().getHabilidades());

        String html = mockMvc.perform(get("/rm/colaboradores/perfil").param("id", colaboradorId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(html.contains(VALIDADA));
        assertTrue(html.contains(PENDIENTE));
        assertTrue(html.contains(RECHAZADA));
        assertFalse(html.contains(INACTIVA));
        assertInsigniaDeEstado(html);
    }

    // Cada estado tiene su propia insignia (color y texto).
    static void assertInsigniaDeEstado(String html) {
        assertTrue(html.contains("class=\"badge bg-green-lt\" data-estado-habilidad=\"VALIDADA\">Validada<"));
        assertTrue(html.contains("class=\"badge bg-yellow-lt\" data-estado-habilidad=\"PENDIENTE\">Pendiente<"));
        assertTrue(html.contains("class=\"badge bg-red-lt\" data-estado-habilidad=\"RECHAZADA\">Rechazada<"));
    }

    // ---- TASK-029: filtros GET y paginación del directorio en el servidor ----

    @Test
    void directorioPaginaDeSeisEnSeisConservandoElOrden() throws Exception {
        crearLote();

        MvcResult primera = mockMvc.perform(get("/rm/colaboradores").param("busqueda", "lote029"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("paginaActual", 1))
                .andExpect(model().attribute("totalPaginas", 2))
                .andExpect(model().attribute("totalRegistros", 12L))
                .andReturn();
        assertEquals(List.of("C01", "C02", "C03", "C04", "C05", "C06"), codigosDeLaPagina(primera));
        assertTrue(primera.getResponse().getContentAsString(StandardCharsets.UTF_8)
                .contains("Mostrando 1-6 de 12 colaboradores"));

        MvcResult segunda = mockMvc.perform(get("/rm/colaboradores")
                        .param("busqueda", "lote029").param("pagina", "2"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("paginaActual", 2))
                .andReturn();
        assertEquals(List.of("C07", "C08", "C09", "C10", "C11", "C12"), codigosDeLaPagina(segunda));
        assertTrue(segunda.getResponse().getContentAsString(StandardCharsets.UTF_8)
                .contains("Mostrando 7-12 de 12 colaboradores"));

        // Sin filtros: la plantilla recibe solo las 6 primeras, en el orden de siempre (nombre y apellido).
        MvcResult sinFiltros = mockMvc.perform(get("/rm/colaboradores")).andExpect(status().isOk()).andReturn();
        List<Long> esperados = consultaService.listarColaboradoresActivos().stream()
                .limit(6).map(RmColaboradorResumen::getId).toList();
        assertEquals(esperados, filasDeLaPagina(sinFiltros).stream().map(RmColaboradorResumen::getId).toList());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', value = {
            "disponibilidad=24 | C08,C10,C11",
            "disponibilidad=16 | C06,C07,C08,C09,C10,C11",
            "disponibilidad=8 | C02,C03,C04,C05,C06,C07,C08,C09,C10,C11,C12",
            "disponibilidad=0 | C01",
            "carga=0 | C01,C02,C03,C04,C05,C06,C07,C08,C12",
            "carga=1 | C09",
            "carga=2 | C10",
            "carga=max | C11",
            "nivel=Junior | C01,C02,C03,C04,C05,C06,C07,C08",
            "nivel=Semi Senior | C09",
            "nivel=Senior | C10,C11",
            "disponibilidad=16&carga=0&nivel=Junior | C06,C07,C08",
            "disponibilidad=24&carga=max&nivel=senior | C11",
            "disponibilidad=8&carga=2&nivel=Junior | ",
    })
    void cadaFiltroDelDirectorioYSuCombinacionAplicanElCriterioAnterior(String filtros, String esperados)
            throws Exception {
        crearLote();
        List<String> esperadosLista = esperados == null ? List.of() : Arrays.asList(esperados.split(","));
        assertEquals(esperadosLista, codigosDeTodasLasPaginas("busqueda=lote029&" + filtros));
    }

    @Test
    void busquedaIgnoraMayusculasYCoincideConNombre() throws Exception {
        crearLote();
        assertEquals(List.of("C05"), codigosDeTodasLasPaginas("busqueda=  LOTE029 c05  "));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', value = {
            VALIDADA + " | true",
            PENDIENTE + " | false",
            RECHAZADA + " | false",
            INACTIVA + " | false",
    })
    void busquedaPorHabilidadSoloCoincideConHabilidadesValidadas(String habilidad, boolean encontrado)
            throws Exception {
        MvcResult resultado = mockMvc.perform(get("/rm/colaboradores").param("busqueda", habilidad))
                .andExpect(status().isOk())
                .andReturn();
        List<Long> ids = filasDeLaPagina(resultado).stream().map(RmColaboradorResumen::getId).toList();
        assertEquals(encontrado, ids.contains(colaboradorId));
        // Ninguna otra persona tiene estas habilidades de prueba: la validada es la única coincidencia.
        assertEquals(encontrado ? List.of(colaboradorId) : List.of(), ids);
        if (!encontrado) {
            assertTrue(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8)
                    .contains("No se encontraron colaboradores con los filtros seleccionados."));
        }
    }

    @Test
    void enlacesDePaginacionConservanTodosLosFiltros() throws Exception {
        crearLote();
        // Junior, 0 asignaciones y al menos 8 h: C02..C08 (7), dos páginas.
        String html = mockMvc.perform(get("/rm/colaboradores").param("busqueda", "lote029")
                        .param("disponibilidad", "8").param("carga", "0").param("nivel", "Junior"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalRegistros", 7L))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        List<String> enlaces = enlaces(html, "/rm/colaboradores\\?[^\"]*pagina=\\d+");
        assertEquals(4, enlaces.size()); // Anterior, 1, 2, Siguiente
        for (String enlace : enlaces) {
            assertTrue(enlace.contains("busqueda=lote029"), enlace);
            assertTrue(enlace.contains("disponibilidad=8"), enlace);
            assertTrue(enlace.contains("carga=0"), enlace);
            assertTrue(enlace.contains("nivel=Junior"), enlace);
        }
        // El formulario GET conserva los valores activos.
        assertTrue(html.contains("value=\"lote029\""));
        assertTrue(html.contains("<option value=\"8\" selected=\"selected\">"));
        assertTrue(html.contains("<option value=\"0\" selected=\"selected\">"));
        assertTrue(html.contains("<option value=\"Junior\" selected=\"selected\">"));

        // Seguir "Siguiente" lleva a la página 2 con los mismos filtros.
        String siguiente = enlaces.get(enlaces.size() - 1);
        assertTrue(siguiente.endsWith("pagina=2"), siguiente);
        MvcResult segunda = mockMvc.perform(conParametros(siguiente.substring(0, siguiente.indexOf('?')),
                        siguiente.substring(siguiente.indexOf('?') + 1)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("paginaActual", 2))
                .andExpect(model().attribute("nivel", "Junior"))
                .andReturn();
        assertEquals(List.of("C08"), codigosDeLaPagina(segunda));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', value = {
            "disponibilidad=abc | 1",
            "disponibilidad=12 | 1",
            "disponibilidad=all | 1",
            "carga=5 | 1",
            "carga= | 1",
            "nivel=Master | 1",
            "nivel=Sin definir | 1",
            "pagina=abc | 1",
            "pagina=0 | 1",
            "pagina=-3 | 1",
            "pagina=1.5 | 1",
            "pagina=99 | 2",
    })
    void parametrosVaciosOInvalidosVuelvenAValoresSeguros(String filtros, int paginaEsperada) throws Exception {
        crearLote();
        mockMvc.perform(conParametros("/rm/colaboradores", "busqueda=lote029&" + filtros))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-colaboradores"))
                .andExpect(model().attribute("totalRegistros", 12L))
                .andExpect(model().attribute("paginaActual", paginaEsperada))
                .andExpect(model().attribute("disponibilidad", org.hamcrest.Matchers.nullValue()))
                .andExpect(model().attribute("carga", org.hamcrest.Matchers.nullValue()))
                .andExpect(model().attribute("nivel", org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void contadoresUsanTodosLosColaboradoresYLaVistaFuncionaSinJavaScript() throws Exception {
        crearLote();
        MvcResult sinFiltros = mockMvc.perform(get("/rm/colaboradores")).andExpect(status().isOk()).andReturn();
        MvcResult filtrado = mockMvc.perform(get("/rm/colaboradores")
                        .param("busqueda", "lote029").param("carga", "max"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalRegistros", 1L))
                .andReturn();
        for (String contador : List.of("totalColaboradores", "totalDisponibles",
                "totalSinAsignaciones", "totalCargaMaxima")) {
            assertEquals(sinFiltros.getModelAndView().getModel().get(contador),
                    filtrado.getModelAndView().getModel().get(contador), contador);
        }
        assertEquals((long) consultaService.listarColaboradoresActivos().size(),
                sinFiltros.getModelAndView().getModel().get("totalColaboradores"));

        String html = filtrado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(html.contains("rm-colaboradores.js"));
        assertFalse(html.contains("data-search="));
        assertTrue(html.contains("id=\"filtersForm\" method=\"get\""));
        assertTrue(html.contains("Mostrando 1-1 de 1 colaboradores"));
        assertFalse(html.contains("id=\"pagination\""));
    }

    /**
     * Crea 12 colaboradores "Lote029 C01".."C12" (el apellido fija el orden):
     * C01 0 h Junior 0 · C02 8 h Junior 0 · C03 8 h Junior 0 · C04 10 h Junior 0 · C05 12 h Junior 0 ·
     * C06 16 h Junior 0 · C07 20 h Junior 0 · C08 24 h Junior 0 · C09 16 h Semi Senior 1 ·
     * C10 24 h Senior 2 · C11 30 h Senior carga máxima · C12 12 h sin nivel 0.
     */
    private void crearLote() {
        Rol rol = rolRepository.findByNombre("COLABORADOR").orElseThrow();
        int maximo = consultaService.listarColaboradoresActivos().get(0).getMaxAsignaciones();
        Object[][] datos = {
                {"C01", "0", NivelExperiencia.JUNIOR, 0},
                {"C02", "8", NivelExperiencia.JUNIOR, 0},
                {"C03", "8", NivelExperiencia.JUNIOR, 0},
                {"C04", "10", NivelExperiencia.JUNIOR, 0},
                {"C05", "12", NivelExperiencia.JUNIOR, 0},
                {"C06", "16", NivelExperiencia.JUNIOR, 0},
                {"C07", "20", NivelExperiencia.JUNIOR, 0},
                {"C08", "24", NivelExperiencia.JUNIOR, 0},
                {"C09", "16", NivelExperiencia.SEMI_SENIOR, 1},
                {"C10", "24", NivelExperiencia.SENIOR, 2},
                {"C11", "30", NivelExperiencia.SENIOR, maximo},
                {"C12", "12", null, 0},
        };
        List<Proyecto> proyectos = new ArrayList<>();
        for (int i = 0; i < maximo; i++) proyectos.add(crearProyectoDeCarga(i));

        for (Object[] fila : datos) {
            String codigo = (String) fila[0];
            String correo = "lote029." + codigo.toLowerCase() + "@skillbridge.test";
            Usuario usuario = usuarioRepository.findByCorreo(correo).orElseGet(Usuario::new);
            usuario.setCorreo(correo);
            usuario.setNombre(LOTE);
            usuario.setApellido(codigo);
            usuario.setRol(rol);
            usuario.setActivo(true);
            usuario.setCargo(cargoDePrueba("Backend Developer"));
            usuario.setHorasDisponibles(new BigDecimal((String) fila[1]));
            usuario.setNivelExperiencia((NivelExperiencia) fila[2]);
            usuario = usuarioRepository.save(usuario);
            loteCreado.add(usuario);
            for (int i = 0; i < (int) fila[3]; i++) {
                Asignacion activa = new Asignacion();
                activa.setProyecto(proyectos.get(i));
                activa.setColaborador(usuario);
                activa.setHorasSemanales(new BigDecimal("4"));
                activa.setOrigen(OrigenAsignacion.PROPUESTA_RM);
                activa.setEstado(EstadoAsignacion.ACTIVA);
                activa.setAprobadoPorPm(true);
                activa.setAprobadoPorRm(true);
                asignacionesCreadas.add(asignacionRepository.save(activa).getId());
            }
        }
    }

    private Proyecto crearProyectoDeCarga(int indice) {
        Rol rolPm = rolRepository.findByNombre("PROJECT_MANAGER").orElseGet(() -> {
            Rol nuevo = new Rol();
            nuevo.setNombre("PROJECT_MANAGER");
            return rolRepository.save(nuevo);
        });
        Usuario pm = usuarioRepository.findByCorreo("pm.lote029@skillbridge.test").orElseGet(() -> {
            Usuario nuevo = new Usuario();
            nuevo.setCorreo("pm.lote029@skillbridge.test");
            nuevo.setNombre("Pablo");
            nuevo.setApellido("Lote");
            nuevo.setRol(rolPm);
            nuevo.setActivo(true);
            return usuarioRepository.save(nuevo);
        });
        Proyecto proyecto = new Proyecto();
        proyecto.setNombre("Carga Lote029 " + indice + " " + System.nanoTime());
        proyecto.setDescripcion("Proyecto para la carga del lote de TASK-029.");
        proyecto.setEstado(EstadoProyecto.ACTIVO);
        proyecto.setPrioridad(Prioridad.MEDIA);
        proyecto.setJustificacionPrioridad("Filtros del directorio.");
        proyecto.setColaboradoresRequeridos(20);
        proyecto.setPm(pm);
        proyecto = proyectoRepository.save(proyecto);
        proyectosCreados.add(proyecto.getId());
        return proyecto;
    }

    // Recorre todas las páginas y devuelve los códigos del lote en orden.
    private List<String> codigosDeTodasLasPaginas(String consulta) throws Exception {
        List<String> codigos = new ArrayList<>();
        int pagina = 1;
        int totalPaginas;
        do {
            MvcResult resultado = mockMvc.perform(conParametros("/rm/colaboradores", consulta + "&pagina=" + pagina))
                    .andExpect(status().isOk())
                    .andReturn();
            totalPaginas = (int) resultado.getModelAndView().getModel().get("totalPaginas");
            codigos.addAll(codigosDeLaPagina(resultado));
            pagina++;
        } while (pagina <= totalPaginas);
        return codigos;
    }

    private List<String> codigosDeLaPagina(MvcResult resultado) {
        return filasDeLaPagina(resultado).stream()
                .map(RmColaboradorResumen::getNombreCompleto)
                .map(nombre -> nombre.substring(nombre.lastIndexOf(' ') + 1))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<RmColaboradorResumen> filasDeLaPagina(MvcResult resultado) {
        List<RmColaboradorResumen> filas =
                (List<RmColaboradorResumen>) resultado.getModelAndView().getModel().get("colaboradores");
        assertTrue(filas.size() <= RmColaboradorConsultaService.TAMANIO_PAGINA);
        return filas;
    }

    // Convierte "a=1&b=2" en parámetros GET (sin codificar).
    private MockHttpServletRequestBuilder conParametros(String ruta, String consulta) {
        MockHttpServletRequestBuilder solicitud = get(ruta);
        for (String par : consulta.split("&")) {
            int igual = par.indexOf('=');
            if (igual > 0) solicitud.param(par.substring(0, igual), par.substring(igual + 1));
        }
        return solicitud;
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

    private void asignarHabilidad(Usuario colaborador, String nombre, EstadoValidacion estado, boolean activo) {
        CategoriaHabilidad categoria = categoriaRepository.findByNombreIgnoreCase("Habilidades T015")
                .orElseGet(() -> {
                    CategoriaHabilidad nueva = new CategoriaHabilidad();
                    nueva.setNombre("Habilidades T015");
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
        perfil.setNivelDominio(NivelDominio.INTERMEDIO);
        perfil.setEstadoValidacion(estado);
        perfil.setActivo(activo);
        colaboradorHabilidadRepository.save(perfil);
    }

    private Cargo cargoDePrueba(String nombre) {
        return cargoRepository.findByNombre(nombre).orElseGet(() -> cargoRepository.save(
                new Cargo(nombre, new BigDecimal("2000"), new BigDecimal("3000"), new BigDecimal("4000"))));
    }
}
