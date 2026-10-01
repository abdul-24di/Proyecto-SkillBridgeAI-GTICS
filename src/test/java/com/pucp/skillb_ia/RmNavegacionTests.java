package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Cargo;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.SolicitudPersonal;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.CargoRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.SolicitudPersonalRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.rm.RmSolicitudPersonalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Navegación de las vistas secundarias del RM (TASK-026): rutas canónicas, orígenes cerrados
 * por endpoint y enlaces de regreso armados en el servidor con los identificadores de las entidades.
 */
@SpringBootTest
@ActiveProfiles("test")
class RmNavegacionTests {

    private static final String BANDEJA = "/rm/asignaciones";
    private static final List<String> DETALLES = List.of(
            "/rm/asignaciones/activa", "/rm/asignaciones/pendiente-pm",
            "/rm/asignaciones/revision", "/rm/asignaciones/revision-postulacion");

    @Autowired private WebApplicationContext context;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CargoRepository cargoRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private SolicitudPersonalRepository solicitudRepository;
    @Autowired private RmSolicitudPersonalService solicitudService;

    private MockMvc mockMvc;
    private Usuario rm;
    private Usuario pm;
    private Usuario colaborador;
    private Usuario otroColaborador;
    private Proyecto proyecto;
    // Una asignación por vista de detalle: activa, pendiente del PM, revisión (PM) y postulación del colaborador.
    private Asignacion activa;
    private Asignacion pendientePm;
    private Asignacion revision;
    private Asignacion postulacion;
    private final List<Asignacion> creadas = new ArrayList<>();
    private final List<SolicitudPersonal> solicitudes = new ArrayList<>();

    @BeforeEach
    void prepararDatos() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        rm = obtenerUsuario("rm.asignaciones@skillbridge.test", "Rosa", "RM", obtenerRol("RESOURCE_MANAGER"));
        pm = obtenerUsuario("pm.asignaciones@skillbridge.test", "Pedro", "PM", obtenerRol("PROJECT_MANAGER"));
        colaborador = prepararColaborador("col.navegacion@skillbridge.test", "Nora", "Navegación");
        otroColaborador = prepararColaborador("col.navegacion.otro@skillbridge.test", "Otto", "Ajeno");

        proyecto = new Proyecto();
        proyecto.setNombre("Proyecto navegación " + System.nanoTime());
        proyecto.setDescripcion("Proyecto para probar la navegación del RM.");
        proyecto.setEstado(EstadoProyecto.ACTIVO);
        proyecto.setPrioridad(Prioridad.MEDIA);
        proyecto.setJustificacionPrioridad("Validación de rutas de navegación.");
        proyecto.setColaboradoresRequeridos(5);
        proyecto.setPm(pm);
        proyecto.setPresupuesto(new BigDecimal("100000.00"));
        proyecto.setFechaInicio(LocalDate.now());
        proyecto.setFechaFinEstimada(LocalDate.now().plusMonths(3));
        proyecto = proyectoRepository.save(proyecto);

        activa = guardar(colaborador, OrigenAsignacion.PROPUESTA_RM, EstadoAsignacion.ACTIVA, true, true);
        pendientePm = guardar(colaborador, OrigenAsignacion.PROPUESTA_RM, EstadoAsignacion.PENDIENTE, false, true);
        revision = guardar(colaborador, OrigenAsignacion.PROPUESTA_PM, EstadoAsignacion.PENDIENTE, true, false);
        postulacion = guardar(colaborador, OrigenAsignacion.SOLICITADA_COLABORADOR,
                EstadoAsignacion.PENDIENTE, false, false);
    }

    // Los datos de esta clase no deben aparecer en las demás (listados, candidatos, contadores).
    @AfterEach
    void limpiarDatos() {
        SecurityContextHolder.clearContext();
        asignacionRepository.deleteAll(creadas);
        creadas.clear();
        solicitudRepository.deleteAll(solicitudes);
        solicitudes.clear();
        for (Usuario usuario : List.of(colaborador, otroColaborador)) {
            usuario.setActivo(false);
            usuarioRepository.save(usuario);
        }
    }

    @Test
    void flujoColaboradoresPerfilAsignacionesVerVuelveAlColaboradorCorrecto() throws Exception {
        String asignacionesColaborador = "/rm/colaboradores/asignaciones?id=" + colaborador.getId();

        String perfil = html("/rm/colaboradores/perfil?id=" + colaborador.getId());
        assertTrue(perfil.contains("href=\"" + asignacionesColaborador + "\""));

        String listado = html(asignacionesColaborador);
        assertEquals(List.of(
                        "/rm/asignaciones/activa?id=" + activa.getId() + "&origen=colaborador",
                        "/rm/asignaciones/pendiente-pm?id=" + pendientePm.getId() + "&origen=colaborador",
                        "/rm/asignaciones/revision?id=" + revision.getId() + "&origen=colaborador",
                        "/rm/asignaciones/revision-postulacion?id=" + postulacion.getId() + "&origen=colaborador")
                        .stream().sorted().toList(),
                enlaces(listado, "/rm/asignaciones/[a-z-]+\\?id=\\d+[^\"]*").stream().sorted().toList());

        for (String destino : enlaces(listado, "/rm/asignaciones/[a-z-]+\\?id=\\d+[^\"]*")) {
            String detalle = html(destino);
            assertEquals(asignacionesColaborador, volver(detalle), destino);
            String migas = migas(detalle);
            assertTrue(migas.contains("href=\"/rm/colaboradores\">Colaboradores</a>"), destino);
            assertTrue(migas.contains("href=\"/rm/colaboradores/perfil?id=" + colaborador.getId()
                    + "\">Nora Navegación</a>"), destino);
            assertTrue(migas.contains("href=\"" + asignacionesColaborador + "\">Asignaciones</a>"), destino);
        }
    }

    @Test
    void migasDeAsignacionesDelColaboradorIncluyenElNombreReal() throws Exception {
        String migas = migas(html("/rm/colaboradores/asignaciones?id=" + colaborador.getId()));

        assertTrue(migas.contains("href=\"/rm/colaboradores\">Colaboradores</a>"));
        assertTrue(migas.contains("href=\"/rm/colaboradores/perfil?id=" + colaborador.getId()
                + "\">Nora Navegación</a>"));
        assertTrue(migas.endsWith("<span class=\"text-secondary\">Asignaciones</span>"));
        assertFalse(migas.contains("Otto"));
    }

    @Test
    void sinOrigenLosDetallesVuelvenALaBandeja() throws Exception {
        for (Asignacion asignacion : List.of(activa, pendientePm, revision, postulacion)) {
            String detalle = html(rutaDe(asignacion) + "?id=" + asignacion.getId());
            assertEquals(BANDEJA, volver(detalle));
            assertEquals("<a class=\"breadcrumb-link\" href=\"/rm/asignaciones\">Asignaciones</a>"
                    + "<span class=\"breadcrumb-separator\">›</span>"
                    + "<span class=\"text-secondary\">Asignación #" + asignacion.getId() + "</span>",
                    migas(detalle).replaceAll(">\\s+<", "><"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "COLABORADOR", "colaborador ", "buscar", "perfil", "detalle",
            "https://malicioso.example", "//malicioso.example", "/rm/colaboradores/asignaciones?id=1"})
    void origenInvalidoUsaLaRutaCanonica(String origen) throws Exception {
        for (Asignacion asignacion : List.of(activa, pendientePm, revision, postulacion)) {
            String detalle = html(rutaDe(asignacion) + "?id=" + asignacion.getId() + "&origen=" + codificar(origen));
            assertEquals(BANDEJA, volver(detalle), origen);
            assertFalse(detalle.contains("malicioso"), origen);
            assertFalse(migas(detalle).contains("/rm/colaboradores"), origen);
        }
    }

    @Test
    void origenProyectoYAsignacionesSiguenSuRutaValida() throws Exception {
        String asignacionesProyecto = "/rm/asignaciones?proyectoId=" + proyecto.getId();

        // La bandeja filtrada por proyecto marca origen=proyecto; sin filtro, origen=asignaciones.
        List<String> desdeProyecto = enlaces(html(asignacionesProyecto), "/rm/asignaciones/[a-z-]+\\?id=\\d+[^\"]*");
        assertFalse(desdeProyecto.isEmpty());
        assertTrue(desdeProyecto.stream().allMatch(url -> url.endsWith("&origen=proyecto")), desdeProyecto.toString());
        List<String> desdeBandeja = enlaces(html(BANDEJA + "?grupo=all"), "/rm/asignaciones/[a-z-]+\\?id=\\d+[^\"]*");
        assertFalse(desdeBandeja.isEmpty());
        assertTrue(desdeBandeja.stream().allMatch(url -> url.endsWith("&origen=asignaciones")), desdeBandeja.toString());

        for (Asignacion asignacion : List.of(activa, pendientePm, revision, postulacion)) {
            String base = rutaDe(asignacion) + "?id=" + asignacion.getId();
            String conProyecto = html(base + "&origen=proyecto");
            assertEquals(asignacionesProyecto, volver(conProyecto));
            String migas = migas(conProyecto);
            assertTrue(migas.contains("href=\"/rm/proyectos\">Proyectos</a>"));
            assertTrue(migas.contains("href=\"/rm/proyectos/detalle?id=" + proyecto.getId() + "\">"
                    + proyecto.getNombre() + "</a>"));
            assertTrue(migas.contains("href=\"" + asignacionesProyecto + "\">Asignaciones</a>"));

            assertEquals(BANDEJA, volver(html(base + "&origen=asignaciones")));
        }
    }

    @Test
    void elColaboradorSeObtieneDeLaAsignacionYNoDelNavegador() throws Exception {
        String detalle = html("/rm/asignaciones/activa?id=" + activa.getId() + "&origen=colaborador"
                + "&colaboradorId=" + otroColaborador.getId() + "&proyectoId=999999");

        assertEquals("/rm/colaboradores/asignaciones?id=" + colaborador.getId(), volver(detalle));
        assertTrue(migas(detalle).contains("/rm/colaboradores/perfil?id=" + colaborador.getId() + "\""));
        assertFalse(detalle.contains("Otto"));
    }

    @Test
    void redireccionPorEstadoConservaSoloOrigenesValidos() throws Exception {
        // Abrir una asignación con la ruta de otro estado redirige a la vista correcta sin perder el origen.
        mockMvc.perform(get("/rm/asignaciones/activa").param("id", pendientePm.getId().toString())
                        .param("origen", "colaborador"))
                .andExpect(redirectedUrl("/rm/asignaciones/pendiente-pm?id=" + pendientePm.getId()
                        + "&origen=colaborador"));
        mockMvc.perform(get("/rm/asignaciones/activa").param("id", postulacion.getId().toString())
                        .param("origen", "proyecto"))
                .andExpect(redirectedUrl("/rm/asignaciones/revision-postulacion?id=" + postulacion.getId()
                        + "&origen=proyecto"));
        mockMvc.perform(get("/rm/asignaciones/pendiente-pm").param("id", activa.getId().toString())
                        .param("origen", "https://malicioso.example"))
                .andExpect(redirectedUrl("/rm/asignaciones/activa?id=" + activa.getId()));
    }

    @Test
    void revisionDePostulacionTieneMigasYEnlaceDeRegreso() throws Exception {
        String directa = html("/rm/asignaciones/revision-postulacion?id=" + postulacion.getId());
        assertTrue(migas(directa).contains("href=\"/rm/asignaciones\">Asignaciones</a>"));
        assertTrue(migas(directa).contains("Asignación #" + postulacion.getId()));
        assertEquals(BANDEJA, volver(directa));
        assertFalse(directa.contains("history.back"));

        // Con presupuesto insuficiente aparece el formulario de presupuesto de la revisión.
        proyecto.setPresupuesto(new BigDecimal("1.00"));
        proyectoRepository.save(proyecto);
        String desdeColaborador = html("/rm/asignaciones/revision-postulacion?id=" + postulacion.getId()
                + "&origen=colaborador");
        assertEquals("/rm/colaboradores/asignaciones?id=" + colaborador.getId(), volver(desdeColaborador));
        // El formulario de presupuesto conserva el origen cerrado para volver a la misma revisión.
        assertTrue(desdeColaborador.contains("name=\"origen\" value=\"colaborador\""));
    }

    @Test
    void presupuestoDesdeLaRevisionVuelveConElOrigenValidoYNuncaAUnaUrlExterna() throws Exception {
        autenticarRm();
        mockMvc.perform(post("/rm/proyectos/{id}/presupuesto", proyecto.getId())
                        .param("presupuesto", "no-es-monto")
                        .param("asignacionId", revision.getId().toString())
                        .param("origen", "colaborador"))
                .andExpect(redirectedUrl("/rm/asignaciones/revision?id=" + revision.getId() + "&origen=colaborador"));
        mockMvc.perform(post("/rm/proyectos/{id}/presupuesto", proyecto.getId())
                        .param("presupuesto", "no-es-monto")
                        .param("asignacionId", revision.getId().toString())
                        .param("origen", "https://malicioso.example"))
                .andExpect(redirectedUrl("/rm/asignaciones/revision?id=" + revision.getId()));
    }

    @Test
    void proyectoDeLaSolicitudDePersonalReflejaSuFlujoReal() throws Exception {
        SolicitudPersonal solicitud = solicitudService.crearDesdePm(
                proyecto.getId(), 2, "Backend", "Completar el equipo.", pm.getId());
        solicitudes.add(solicitud);
        String codigo = String.format("SOL-%03d", solicitud.getId());
        String detalleSolicitud = "/rm/asignaciones/solicitudes-colaboradores/detalle?id=" + solicitud.getId();

        // Detalle de la solicitud → "Ver proyecto".
        String proyectoSolicitud = "/rm/proyectos/detalle-solicitudes?solicitudId=" + solicitud.getId();
        assertTrue(html(detalleSolicitud).contains("href=\"" + proyectoSolicitud + "\""));

        String migas = migas(html(proyectoSolicitud));
        assertTrue(migas.contains("href=\"/rm/asignaciones\">Asignaciones</a>"));
        assertTrue(migas.contains("href=\"/rm/asignaciones/solicitudes-colaboradores\">Solicitudes</a>"));
        assertTrue(migas.contains("href=\"" + detalleSolicitud + "\">" + codigo + "</a>"));
        assertTrue(migas.contains(proyecto.getNombre()));
        assertFalse(migas.contains("href=\"/rm/proyectos\""));

        // Navegación directa: sin solicitud, o con una inexistente o mal formada, va a la lista de solicitudes.
        mockMvc.perform(get("/rm/proyectos/detalle-solicitudes"))
                .andExpect(redirectedUrl("/rm/asignaciones/solicitudes-colaboradores"));
        mockMvc.perform(get("/rm/proyectos/detalle-solicitudes").param("solicitudId", "999999999"))
                .andExpect(redirectedUrl("/rm/asignaciones/solicitudes-colaboradores?noEncontrada=true"));
    }

    // TASK-032: sin proyecto válido es una vista raíz; no se muestra la ruta de un solo elemento.
    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "999999999", "https://malicioso.example"})
    void talentMatchingSinContextoValidoEsVistaRaizSinRutaDeNavegacion(String proyectoId) throws Exception {
        for (String pagina : List.of(
                html("/rm/talent-matching?proyectoId=" + codificar(proyectoId)), html("/rm/talent-matching"))) {
            assertFalse(pagina.contains("Clínica AI"));
            assertFalse(pagina.contains("malicioso"));
            assertFalse(pagina.contains("breadcrumb-wrap"), "Sin proyecto no hay ruta de navegación.");
            assertTrue(normalizar(pagina).contains("<h1 class=\"page-title mb-0\">Talent Matching</h1>"));
        }
    }

    @Test
    void talentMatchingConProyectoConservaLaRutaContextual() throws Exception {
        String pagina = html("/rm/talent-matching?proyectoId=" + proyecto.getId());

        assertFalse(pagina.contains("Clínica AI"));
        assertEquals("<a class=\"breadcrumb-link\" href=\"/rm/talent-matching\">Talent Matching</a>"
                        + "<span class=\"breadcrumb-separator\">›</span>"
                        + "<span class=\"text-secondary\">" + proyecto.getNombre() + "</span>",
                normalizar(migas(pagina)));
        assertFalse(html("/rm/talent-matching").contains("Clínica AI"));
    }

    // TASK-032: las vistas secundarias conservan su ruta de varios elementos (TASK-026 sin cambios).
    @Test
    void vistasSecundariasConservanSusRutasDeNavegacion() throws Exception {
        SolicitudPersonal solicitud = solicitudService.crearDesdePm(
                proyecto.getId(), 2, "Backend", "Completar el equipo.", pm.getId());
        solicitudes.add(solicitud);
        List<String> secundarias = new ArrayList<>(List.of(
                "/rm/colaboradores/asignaciones?id=" + colaborador.getId(),
                "/rm/proyectos/detalle-solicitudes?solicitudId=" + solicitud.getId(),
                "/rm/talent-matching?proyectoId=" + proyecto.getId()));
        for (Asignacion asignacion : List.of(activa, pendientePm, revision, postulacion)) {
            secundarias.add(rutaDe(asignacion) + "?id=" + asignacion.getId());
            secundarias.add(rutaDe(asignacion) + "?id=" + asignacion.getId() + "&origen=colaborador");
        }
        for (String url : secundarias) {
            String migas = migas(html(url));
            assertTrue(migas.contains("class=\"breadcrumb-link\""), url);
            assertTrue(migas.contains("<span class=\"breadcrumb-separator\">›</span>"), url);
        }
    }

    // TASK-050: el perfil ya no pagina "Asignaciones activas" en JS; migas, enlaces y scripts restantes no cambian.
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void perfilConservaSuNavegacionSinPaginacionEnJs(boolean conAsignaciones) throws Exception {
        Usuario persona = conAsignaciones ? colaborador : otroColaborador;
        String perfil = html("/rm/colaboradores/perfil?id=" + persona.getId());

        String migas = migas(perfil);
        assertTrue(migas.contains("href=\"/rm/colaboradores\">Colaboradores</a>"));
        assertTrue(migas.endsWith("<span class=\"text-secondary\">" + persona.getNombre() + " "
                + persona.getApellido() + "</span>"));
        String historial = "/rm/colaboradores/asignaciones?id=" + persona.getId();
        assertTrue(perfil.contains("href=\"" + historial + "\">Ver historial</a>"));
        assertEquals(List.of(historial, historial), enlaces(perfil, "/rm/colaboradores/asignaciones\\?[^\"]*"),
                "\"Ver asignaciones\" y \"Ver historial\" apuntan al mismo colaborador");

        // Solo la activa (no las tres pendientes); sin colaborador activo, el estado vacío común de TASK-031.
        assertEquals(conAsignaciones ? 1 : 0, perfil.split("<tr class=\"asignacion-row\">", -1).length - 1);
        assertEquals(!conAsignaciones, perfil.contains("<div class=\"empty-state-title\">Sin asignaciones activas</div>"));

        assertFalse(perfil.contains("id=\"paginationInfo\""));
        assertFalse(perfil.contains("id=\"pagination\""));
        assertFalse(perfil.contains("rm-perfil-colaborador.js"));
        for (String script : List.of("rm-nivel-general.js", "rm-propuesta-asignacion.js", "rm-navigation.js")) {
            assertTrue(perfil.contains("src=\"/js/rm-js/" + script + "\""), script);
        }
        assertTrue(perfil.contains("id=\"propuestaContexto\" class=\"d-none\" data-origen=\"perfil\""));
    }

    // TASK-051: filtros y paginación en el servidor sin cambiar la navegación de TASK-026 ni el modal de TASK-034.
    @ParameterizedTest
    @ValueSource(strings = {"", "&estado=all&anio=all&pagina=1", "&busqueda=PEDRO&pagina=abc", "&estado=desconocido&pagina=-2"})
    void asignacionesDelColaboradorConservanNavegacionYPropuestaConFiltros(String filtros) throws Exception {
        String asignacionesColaborador = "/rm/colaboradores/asignaciones?id=" + colaborador.getId();
        String listado = html(asignacionesColaborador + filtros);

        // Los 4 detalles conservan solo origen=colaborador (el contrato de TASK-026 no admite filtros).
        assertEquals(List.of(
                        "/rm/asignaciones/activa?id=" + activa.getId() + "&origen=colaborador",
                        "/rm/asignaciones/pendiente-pm?id=" + pendientePm.getId() + "&origen=colaborador",
                        "/rm/asignaciones/revision?id=" + revision.getId() + "&origen=colaborador",
                        "/rm/asignaciones/revision-postulacion?id=" + postulacion.getId() + "&origen=colaborador")
                        .stream().sorted().toList(),
                enlaces(listado, "/rm/asignaciones/[a-z-]+\\?id=\\d+[^\"]*").stream().sorted().toList());

        String migas = migas(listado);
        assertTrue(migas.contains("href=\"/rm/colaboradores\">Colaboradores</a>"));
        assertTrue(migas.contains("href=\"/rm/colaboradores/perfil?id=" + colaborador.getId() + "\">Nora Navegación</a>"));
        assertTrue(migas.endsWith("<span class=\"text-secondary\">Asignaciones</span>"));

        assertTrue(listado.contains("data-bs-toggle=\"modal\" data-bs-target=\"#proyectosModal\">+ Proponer asignación"));
        assertTrue(listado.contains("id=\"propuestaContexto\" class=\"d-none\" data-origen=\"colaborador\""));
        assertTrue(listado.contains("id=\"propuestaModal\""));
        // Topbar compartido, Tabler, modal de propuesta y el JS visual de la vista; sin scripts en línea.
        assertEquals(List.of("/js/notificaciones.js", "/tabler/js/tabler.min.js", "/js/rm-js/rm-propuesta-asignacion.js",
                "/js/rm-js/rm-asignaciones-colaborador.js"), scripts(listado));
        // Sin enlaces vacíos en el contenido de la vista (el topbar compartido queda fuera).
        assertFalse(listado.substring(listado.indexOf("<nav class=\"breadcrumb-wrap\""), listado.indexOf("</main>"))
                .contains("href=\"#\""));
        assertTrue(listado.contains("<form id=\"assignmentFiltersForm\" method=\"get\" action=\"/rm/colaboradores/asignaciones\""));
    }

    @Test
    void conFiltroDeEstadoLaPostulacionPendienteSigueAbriendoSuRevisionYVuelveSinFiltros() throws Exception {
        String asignacionesColaborador = "/rm/colaboradores/asignaciones?id=" + colaborador.getId();
        String listado = html(asignacionesColaborador + "&estado=PENDIENTE");

        List<String> detalles = enlaces(listado, "/rm/asignaciones/[a-z-]+\\?id=\\d+[^\"]*");
        assertEquals(3, detalles.size(), detalles.toString());
        assertTrue(detalles.contains("/rm/asignaciones/revision-postulacion?id=" + postulacion.getId() + "&origen=colaborador"));
        for (String destino : detalles) {
            assertEquals(asignacionesColaborador, volver(html(destino)), destino);
        }
    }

    @Test
    void elJsDeAsignacionesDelColaboradorEsSoloVisualYSoloLoCargaSuVista() throws Exception {
        // El mock se eliminó; el archivo nuevo (solo visual) todavía no tiene código.
        String js = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/resources/static/js/rm-js/rm-asignaciones-colaborador.js"));
        String codigo = js.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "").trim();
        assertEquals("", codigo);
        for (String mock : List.of("collaboratorAssignments", "Clínica AI", "Juan Pérez", "pageSize", "currentPage")) {
            assertFalse(js.contains(mock), mock);
        }
        try (var archivos = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/resources"))) {
            List<java.nio.file.Path> referencias = archivos
                    .filter(java.nio.file.Files::isRegularFile)
                    .filter(ruta -> ruta.toString().matches(".*\\.(html|js|css)$"))
                    .filter(ruta -> {
                        try {
                            return new String(java.nio.file.Files.readAllBytes(ruta), StandardCharsets.ISO_8859_1)
                                    .contains("rm-asignaciones-colaborador.js");
                        } catch (java.io.IOException ex) {
                            throw new java.io.UncheckedIOException(ex);
                        }
                    })
                    .toList();
            assertEquals(List.of(java.nio.file.Path.of(
                    "src/main/resources/templates/rm/rm-asignaciones-colaborador.html")), referencias);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "&busqueda=x&estado=ACTIVA&anio=2026&pagina=2"})
    void colaboradorInexistenteConservaLaRedireccionSegura(String filtros) throws Exception {
        mockMvc.perform(get("/rm/colaboradores/asignaciones?id=999999999" + filtros))
                .andExpect(redirectedUrl("/rm/colaboradores?noEncontrado=true"));
        mockMvc.perform(get("/rm/colaboradores/asignaciones?id=" + otroColaborador.getId() + filtros))
                .andExpect(status().isOk());
        mockMvc.perform(get("/rm/colaboradores/asignaciones" + (filtros.isEmpty() ? "" : "?" + filtros.substring(1))))
                .andExpect(redirectedUrl("/rm/colaboradores"));
    }

    @Test
    void origenColaboradorConservaSuSignificadoEnLaPropuestaDeTask034() throws Exception {
        // Los tres orígenes de la propuesta siguen volviendo a su pantalla (error de horas: no crea nada).
        autenticarRm();
        Map<String, String> destinos = Map.of(
                "colaborador", "/rm/colaboradores/asignaciones?id=" + otroColaborador.getId(),
                "perfil", "/rm/colaboradores/perfil?id=" + otroColaborador.getId(),
                "buscar", "/rm/proyectos/buscar-colaboradores?proyectoId=" + proyecto.getId());
        for (var destino : destinos.entrySet()) {
            mockMvc.perform(post("/rm/asignaciones/proponer")
                            .param("proyectoId", proyecto.getId().toString())
                            .param("colaboradorId", otroColaborador.getId().toString())
                            .param("horasSemanales", "0")
                            .param("justificacion", "Prueba de navegación.")
                            .param("origen", destino.getKey()))
                    .andExpect(redirectedUrl(destino.getValue()));
        }
        assertTrue(asignacionRepository.findAll().stream()
                .noneMatch(a -> a.getColaborador().getId().equals(otroColaborador.getId())));
    }

    // --- utilidades ---

    private String html(String url) throws Exception {
        return mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String migas(String html) {
        Matcher matcher = Pattern.compile("<nav class=\"breadcrumb-wrap\"[^>]*>(.*?)</nav>", Pattern.DOTALL).matcher(html);
        assertTrue(matcher.find(), "La vista no tiene ruta de navegación.");
        return matcher.group(1).trim();
    }

    // Quita los espacios entre etiquetas y dentro de ellas para comparar el marcado.
    private static String normalizar(String html) {
        return html.replaceAll("\\s+", " ").replace("> ", ">").replace(" <", "<").trim();
    }

    private String volver(String html) {
        Matcher matcher = Pattern.compile(
                "<a class=\"text-secondary text-decoration-none small\" href=\"([^\"]*)\">← [^<]+</a>").matcher(html);
        assertTrue(matcher.find(), "La vista no tiene enlace de regreso.");
        String destino = matcher.group(1).replace("&amp;", "&");
        assertFalse(matcher.find(), "La vista tiene más de un enlace de regreso.");
        return destino;
    }

    private List<String> enlaces(String html, String patron) {
        Matcher matcher = Pattern.compile("href=\"(" + patron + ")\"").matcher(html);
        List<String> encontrados = new ArrayList<>();
        while (matcher.find()) encontrados.add(matcher.group(1).replace("&amp;", "&"));
        return encontrados;
    }

    private List<String> scripts(String html) {
        Matcher matcher = Pattern.compile("<script\\b([^>]*)>").matcher(html);
        List<String> encontrados = new ArrayList<>();
        while (matcher.find()) {
            Matcher src = Pattern.compile("src=\"([^\"]*)\"").matcher(matcher.group(1));
            encontrados.add(src.find() ? src.group(1) : "(en línea)");
        }
        return encontrados;
    }

    private static String codificar(String valor) {
        return java.net.URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    private String rutaDe(Asignacion asignacion) {
        if (asignacion == activa) return DETALLES.get(0);
        if (asignacion == pendientePm) return DETALLES.get(1);
        if (asignacion == revision) return DETALLES.get(2);
        return DETALLES.get(3);
    }

    private Asignacion guardar(Usuario persona, OrigenAsignacion origen, EstadoAsignacion estado,
                               boolean aprobadoPm, boolean aprobadoRm) {
        Asignacion asignacion = new Asignacion();
        asignacion.setProyecto(proyecto);
        asignacion.setColaborador(persona);
        asignacion.setHorasSemanales(new BigDecimal("8"));
        asignacion.setOrigen(origen);
        asignacion.setEstado(estado);
        asignacion.setAprobadoPorPm(aprobadoPm);
        asignacion.setAprobadoPorRm(aprobadoRm);
        asignacion.setMensajeSolicitud("Solicitud de prueba.");
        asignacion = asignacionRepository.save(asignacion);
        creadas.add(asignacion);
        return asignacion;
    }

    private void autenticarRm() {
        UsuarioDetails details = new UsuarioDetails(usuarioRepository.findActivosByRolNombre("RESOURCE_MANAGER")
                .stream().filter(u -> u.getId().equals(rm.getId())).findFirst().orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    private Usuario prepararColaborador(String correo, String nombre, String apellido) {
        Usuario usuario = obtenerUsuario(correo, nombre, apellido, obtenerRol("COLABORADOR"));
        usuario.setActivo(true);
        usuario.setCargo(cargoRepository.findByNombre("Backend Developer").orElseGet(() -> cargoRepository.save(
                new Cargo("Backend Developer", new BigDecimal("2000"), new BigDecimal("3000"), new BigDecimal("4000")))));
        usuario.setHorasDisponibles(new BigDecimal("20.00"));
        usuario.setHorasContratadasSemana(new BigDecimal("40.00"));
        usuario.setSueldoBase(new BigDecimal("4800.00"));
        return usuarioRepository.save(usuario);
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
