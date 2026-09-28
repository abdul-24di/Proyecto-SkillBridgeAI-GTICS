package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.Cargo;
import com.pucp.skillb_ia.repository.CargoRepository;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
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

    private MockMvc mockMvc;
    private Usuario rm;
    private Usuario pm;
    private Usuario colaborador;
    private Proyecto proyecto;

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

    @Test
    void propuestaDelRmQuedaPendienteDelPm() {
        Asignacion asignacion = asignacionService.proponerDesdeRm(
                proyecto.getId(), colaborador.getId(), new BigDecimal("12"),
                "Tiene las habilidades requeridas.", null, rm.getId());

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
                "Primera propuesta.", null, rm.getId());
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
