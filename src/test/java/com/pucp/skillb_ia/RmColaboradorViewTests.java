package com.pucp.skillb_ia;

import com.pucp.skillb_ia.dto.RmColaboradorDetalle;
import com.pucp.skillb_ia.dto.RmColaboradorResumen;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.CategoriaHabilidad;
import com.pucp.skillb_ia.model.ColaboradorHabilidad;
import com.pucp.skillb_ia.model.ConfiguracionSistema;
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
import com.pucp.skillb_ia.repository.ConfiguracionSistemaRepository;
import com.pucp.skillb_ia.repository.HabilidadRepository;
import com.pucp.skillb_ia.model.enums.NivelExperiencia;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.rm.RmColaboradorConsultaService;
import com.pucp.skillb_ia.controller.RmViewController;
import com.pucp.skillb_ia.service.EvaluacionService;
import com.pucp.skillb_ia.service.rm.*;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ConcurrentModel;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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

    // ===== TASK-031: estados vacíos con el fragmento común (fragments/rm-empty-state) =====

    @Autowired private SpringTemplateEngine templateEngine;

    @Test
    void directorioSinColaboradoresActivosMuestraElEstadoVacioSinAcciones() {
        // El H2 compartido siempre tiene colaboradores: el controlador real recibe un servicio simulado sin datos.
        RmColaboradorConsultaService consulta = mock(RmColaboradorConsultaService.class);
        when(consulta.normalizarFiltrosDirectorio(any(), any(), any(), any()))
                .thenReturn(new RmColaboradorConsultaService.FiltrosColaborador("", "", "", ""));
        when(consulta.listarPaginaDirectorio(any(), any())).thenReturn(new RmColaboradorConsultaService.PaginaColaboradores(
                List.of(), 1, 1, 0, new RmColaboradorConsultaService.ContadoresColaboradores(0, 0, 0, 0)));
        RmViewController controller = new RmViewController(
                mock(RmPerfilService.class), consulta, mock(RmProyectoConsultaService.class),
                mock(RmProyectoRevisionService.class), mock(RmAsignacionService.class),
                mock(RmSolicitudPersonalService.class), mock(RmCertificadoService.class),
                mock(RmEducacionService.class), mock(RmForoConsultaService.class), mock(RmReporteService.class),
                mock(RmReporteExportService.class), mock(RmCursoService.class), mock(RmPresupuestoService.class),
                mock(EvaluacionService.class));
        ConcurrentModel modelo = new ConcurrentModel();
        assertEquals("rm/rm-colaboradores", controller.collaborators(null, null, null, null, null, null, modelo));

        MockHttpServletRequest request = new MockHttpServletRequest(
                webApplicationContext.getServletContext(), "GET", "/rm/colaboradores");
        var intercambio = JakartaServletWebApplication.buildApplication(webApplicationContext.getServletContext())
                .buildExchange(request, new MockHttpServletResponse());
        String html = templateEngine.process("rm/rm-colaboradores",
                new WebContext(intercambio, Locale.forLanguageTag("es"), modelo.asMap()));

        assertEquals(1, contar(html, "class=\"empty-state\""));
        assertTrue(html.contains("<span class=\"empty-state-icon\" aria-hidden=\"true\">"));
        assertTrue(html.contains("<div class=\"empty-state-title\">No hay colaboradores activos registrados</div>"));
        assertTrue(html.contains("Los colaboradores activos aparecerán en este directorio."));
        assertFalse(html.contains("noFilterResults"));
        assertFalse(html.contains("empty-state-action"), "El RM no registra colaboradores");
    }

    @Test
    void directorioSinResultadosFiltradosOfreceLimpiarFiltros() throws Exception {
        String html = mockMvc.perform(get("/rm/colaboradores")
                        .param("busqueda", "sin-coincidencias-t031").param("carga", "max"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalRegistros", 0L))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(html.contains("id=\"noFilterResults\""));
        assertEquals(1, contar(html, "class=\"empty-state\""));
        assertTrue(html.contains("<div class=\"empty-state-title\">Sin resultados para los filtros</div>"));
        assertTrue(html.contains("No se encontraron colaboradores con los filtros seleccionados."));
        // Mismo destino que el botón "Limpiar filtros" del formulario.
        assertTrue(html.contains("<a class=\"btn btn-outline-primary btn-sm\" href=\"/rm/colaboradores\">Limpiar filtros</a>"));
        assertFalse(html.contains("No hay colaboradores activos registrados"));
        assertTrue(html.contains("value=\"sin-coincidencias-t031\""));
    }

    @Test
    void perfilSinRegistrosUsaElComponenteEnSusCincoEstadosVacios() throws Exception {
        Usuario vacio = usuarioRepository.findByCorreo("colaborador.vacio.t031@skillbridge.test").orElseGet(() -> {
            Usuario nuevo = new Usuario();
            nuevo.setCorreo("colaborador.vacio.t031@skillbridge.test");
            nuevo.setNombre("Vacío");
            nuevo.setApellido("T031");
            nuevo.setCargo(cargoDePrueba("Backend Developer"));
            nuevo.setRol(rolRepository.findByNombre("COLABORADOR").orElseThrow());
            nuevo.setNivelExperiencia(NivelExperiencia.JUNIOR);
            nuevo.setHorasDisponibles(BigDecimal.valueOf(40));
            nuevo.setAniosExperiencia(BigDecimal.ZERO);
            return nuevo;
        });
        vacio.setActivo(true);
        vacio = usuarioRepository.save(vacio);
        loteCreado.add(vacio);

        String html = mockMvc.perform(get("/rm/colaboradores/perfil").param("id", vacio.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertEquals(5, contar(html, "class=\"empty-state\""));
        assertEquals(5, contar(html, "<span class=\"empty-state-icon\" aria-hidden=\"true\">"));
        for (String[] estado : new String[][] {
                {"Sin habilidades registradas", "Este colaborador todavía no registró habilidades."},
                {"Sin asignaciones activas", "Este colaborador no tiene asignaciones activas."},
                {"Sin experiencia profesional", "No hay experiencia profesional registrada."},
                {"Sin evaluaciones", "El colaborador aún no tiene evaluaciones registradas."},
                {"Sin estudios registrados", "No hay estudios registrados."}}) {
            assertTrue(html.contains("<div class=\"empty-state-title\">" + estado[0] + "</div>"), estado[0]);
            assertTrue(html.contains("<div class=\"empty-state-text\">" + estado[1] + "</div>"), estado[1]);
        }
        assertFalse(html.contains("empty-state-action"), "Secciones de solo lectura");
        // Sin asignaciones activas no se pinta la tabla; desde TASK-050 no hay paginación ni su script.
        assertFalse(html.contains("asignacion-row"));
        assertFalse(html.contains("id=\"paginationInfo\""));
        assertFalse(html.contains("rm-perfil-colaborador.js"));
        assertTrue(html.contains("href=\"/rm/colaboradores/asignaciones?id=" + vacio.getId() + "\">Ver historial</a>"));
    }

    // Distribución del perfil: tarjetas alineadas, sin columnas apiladas que se desborden ni scroll horizontal.
    @Test
    void perfilDistribuyeLasTarjetasSinDesbordesNiMargenesSobrantes() throws Exception {
        String html = mockMvc.perform(get("/rm/colaboradores/perfil").param("id", colaboradorId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8).replaceAll("\\s+", " ");

        assertFalse(html.contains("h-100 mt-4"), "Información general y Evaluaciones ya no se desplazan con mt-4");
        int experiencia = html.indexOf(">Experiencia profesional<");
        int educacion = html.indexOf(">Educación<");
        int evaluaciones = html.indexOf(">Historial de Desempeño (Evaluaciones)<");
        assertTrue(experiencia > 0 && experiencia < educacion && educacion < evaluaciones);
        // Experiencia y Educación comparten fila en dos columnas; Evaluaciones ocupa todo el ancho debajo.
        String fila = html.substring(html.lastIndexOf("<div class=\"row row-cards mb-4\">", experiencia), evaluaciones);
        assertEquals(2, contar(fila, "<div class=\"col-lg-6\"> <div class=\"card section-card h-100\">"));
        assertTrue(html.contains("<div class=\"card section-card mb-4\"> <div class=\"card-header section-header\"> "
                + "<div> <h3 class=\"card-title mb-1\">Historial de Desempeño (Evaluaciones)</h3>"));

        String topbar = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/resources/static/css/rm-css/rm-topbar.css"), StandardCharsets.UTF_8);
        assertFalse(topbar.contains("50vw"), "50vw incluye la barra de desplazamiento y provoca scroll horizontal");
    }

    // ===== TASK-050: "Asignaciones activas" del perfil sin paginación (lista acotada, renderizada en el servidor) =====

    @Autowired private ConfiguracionSistemaRepository configuracionRepository;

    private static final String CLAVE_MAX = "MAX_ASIGNACIONES_POR_COLABORADOR";

    /**
     * Con el límite por defecto (3) y con un límite configurado mayor (15) el perfil pinta todas las activas
     * en el orden del repositorio; 12 activas superan el antiguo recorte de 10 filas del JavaScript.
     */
    @ParameterizedTest(name = "[{index}] {0} activas, límite {1}")
    @CsvSource(delimiter = '|', value = {"3 | ", "12 | 15"})
    void perfilMuestraTodasLasAsignacionesActivasEnElOrdenDelRepositorio(int cantidad, String limite)
            throws Exception {
        ConfiguracionSistema configuracion = configuracionRepository.findByClave(CLAVE_MAX).orElse(null);
        String valorAnterior = configuracion == null ? null : configuracion.getValor();
        try {
            if (limite != null) {
                if (configuracion == null) {
                    configuracion = new ConfiguracionSistema();
                    configuracion.setClave(CLAVE_MAX);
                }
                configuracion.setValor(limite);
                configuracion = configuracionRepository.save(configuracion);
            }
            Usuario colaborador = colaboradorDeTask050();
            // Nombres en orden inverso al de creación: el orden esperado lo fija el repositorio, no el alfabeto.
            for (int i = cantidad; i >= 1; i--) {
                crearAsignacionT050(colaborador, String.format("T050 Activa %02d", i), EstadoAsignacion.ACTIVA);
            }
            for (EstadoAsignacion otro : List.of(EstadoAsignacion.PENDIENTE, EstadoAsignacion.RECHAZADA,
                    EstadoAsignacion.FINALIZADA)) {
                crearAsignacionT050(colaborador, "T050 No activa " + otro.name(), otro);
            }

            String html = mockMvc.perform(get("/rm/colaboradores/perfil").param("id", colaborador.getId().toString()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

            List<String> esperados = asignacionRepository.findByColaboradorAndEstado(colaborador, EstadoAsignacion.ACTIVA)
                    .stream()
                    .map(asignacion -> proyectoRepository.findById(asignacion.getProyecto().getId()).orElseThrow().getNombre())
                    .toList();
            List<String> filas = proyectosDeLasFilas(html);
            assertEquals(cantidad, esperados.size());
            assertEquals(esperados, filas, "Mismo orden que findByColaboradorAndEstado");
            assertTrue(filas.stream().allMatch(nombre -> nombre.startsWith("T050 Activa ")));
            // (El modal de propuesta sí lista esos proyectos: están ACTIVO.)
            assertFalse(tarjetaDeAsignacionesActivas(html).contains("T050 No activa"),
                    "Pendientes, rechazadas y finalizadas no se muestran");
            assertEquals(cantidad, contar(html, "<td><span class=\"badge bg-green-lt\">Activa</span></td>"));
            // Sin JavaScript: todas las filas visibles desde el servidor, sin controles de página.
            assertFalse(html.contains("asignacion-row d-none"));
            assertSinPaginacionEnJs(html);
            if (limite != null) {
                assertTrue(html.contains(cantidad + " / " + limite), "El resumen usa el límite configurado");
            }
        } finally {
            if (limite != null && configuracion != null) {
                if (valorAnterior == null) {
                    configuracionRepository.delete(configuracion);
                } else {
                    configuracion.setValor(valorAnterior);
                    configuracionRepository.save(configuracion);
                }
            }
        }
    }

    @Test
    void perfilConservaHistorialModalYScriptsSinElScriptDePaginacion() throws Exception {
        Usuario colaborador = colaboradorDeTask050();
        crearAsignacionT050(colaborador, "T050 Activa única", EstadoAsignacion.ACTIVA);

        String html = mockMvc.perform(get("/rm/colaboradores/perfil").param("id", colaborador.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertEquals(List.of("T050 Activa única"), proyectosDeLasFilas(html));
        assertSinPaginacionEnJs(html);
        assertFalse(html.contains("Sin asignaciones activas"));
        assertTrue(html.contains("href=\"/rm/colaboradores/asignaciones?id=" + colaborador.getId() + "\">Ver historial</a>"));
        // Modal compartido de propuesta (TASK-034) y scripts que siguen teniendo responsabilidad.
        assertTrue(html.contains("id=\"proyectosModal\""));
        assertTrue(html.contains("id=\"propuestaModal\""));
        assertTrue(html.contains("id=\"propuestaContexto\""));
        for (String script : List.of("rm-nivel-general.js", "rm-propuesta-asignacion.js", "rm-navigation.js")) {
            assertTrue(html.contains("src=\"/js/rm-js/" + script + "\""), script);
        }
    }

    @Test
    void elScriptDePaginacionDelPerfilFueEliminadoYNoTieneReferencias() throws Exception {
        java.nio.file.Path recursos = java.nio.file.Path.of("src/main/resources");
        assertFalse(java.nio.file.Files.exists(recursos.resolve("static/js/rm-js/rm-perfil-colaborador.js")));
        try (var archivos = java.nio.file.Files.walk(recursos)) {
            List<java.nio.file.Path> referencias = archivos
                    .filter(java.nio.file.Files::isRegularFile)
                    .filter(ruta -> ruta.toString().endsWith(".html") || ruta.toString().endsWith(".js"))
                    .filter(ruta -> {
                        try {
                            // ISO-8859-1 lee cualquier archivo; el nombre buscado es ASCII.
                            return java.nio.file.Files.readString(ruta, StandardCharsets.ISO_8859_1)
                                    .contains("rm-perfil-colaborador.js");
                        } catch (java.io.IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    })
                    .toList();
            assertEquals(List.of(), referencias);
        }
    }

    private static void assertSinPaginacionEnJs(String html) {
        assertFalse(html.contains("id=\"paginationInfo\""));
        assertFalse(html.contains("id=\"pagination\""));
        assertFalse(html.contains("pagination-wrap"));
        assertFalse(html.contains("page-link"));
        assertFalse(html.contains("rm-perfil-colaborador.js"));
        // La tarjeta no tiene enlaces vacíos (el único href="#" de la página es la campana de la barra superior).
        String tarjeta = tarjetaDeAsignacionesActivas(html);
        assertFalse(tarjeta.contains("href=\"#\""));
        assertEquals(1, contar(tarjeta, "href=\""), "Solo el enlace \"Ver historial\"");
    }

    private static String tarjetaDeAsignacionesActivas(String html) {
        int inicio = html.indexOf(">Asignaciones activas<");
        return html.substring(inicio, html.indexOf(">Experiencia profesional<", inicio));
    }

    // Nombres de proyecto de las filas de "Asignaciones activas", en el orden del HTML.
    private static List<String> proyectosDeLasFilas(String html) {
        Matcher matcher = Pattern.compile(
                "<tr class=\"asignacion-row\">\\s*<td class=\"fw-semibold\">([^<]*)</td>").matcher(html);
        List<String> nombres = new ArrayList<>();
        while (matcher.find()) nombres.add(matcher.group(1));
        return nombres;
    }

    private Usuario colaboradorDeTask050() {
        Usuario colaborador = usuarioRepository.findByCorreo("colaborador.t050@skillbridge.test").orElseGet(() -> {
            Usuario nuevo = new Usuario();
            nuevo.setCorreo("colaborador.t050@skillbridge.test");
            nuevo.setNombre("Perfil");
            nuevo.setApellido("T050");
            nuevo.setCargo(cargoDePrueba("Backend Developer"));
            nuevo.setRol(rolRepository.findByNombre("COLABORADOR").orElseThrow());
            nuevo.setNivelExperiencia(NivelExperiencia.JUNIOR);
            nuevo.setHorasDisponibles(BigDecimal.valueOf(40));
            nuevo.setAniosExperiencia(BigDecimal.ZERO);
            return nuevo;
        });
        colaborador.setActivo(true);
        colaborador = usuarioRepository.save(colaborador);
        loteCreado.add(colaborador);
        return colaborador;
    }

    // Cada asignación en su propio proyecto (los borra @AfterEach).
    private void crearAsignacionT050(Usuario colaborador, String nombreProyecto, EstadoAsignacion estado) {
        Proyecto proyecto = crearProyectoDeCarga(0);
        proyecto.setNombre(nombreProyecto);
        proyecto = proyectoRepository.save(proyecto);
        Asignacion asignacion = new Asignacion();
        asignacion.setProyecto(proyecto);
        asignacion.setColaborador(colaborador);
        asignacion.setHorasSemanales(new BigDecimal("2"));
        asignacion.setOrigen(OrigenAsignacion.PROPUESTA_RM);
        asignacion.setEstado(estado);
        asignacion.setAprobadoPorPm(true);
        asignacion.setAprobadoPorRm(true);
        asignacionesCreadas.add(asignacionRepository.save(asignacion).getId());
    }

    private static int contar(String texto, String fragmento) {
        return texto.split(Pattern.quote(fragmento), -1).length - 1;
    }
}
