package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.CategoriaHabilidad;
import com.pucp.skillb_ia.model.Foro;
import com.pucp.skillb_ia.model.Habilidad;
import com.pucp.skillb_ia.model.LogAuditoria;
import com.pucp.skillb_ia.model.Notificacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.ProyectoHabilidadRequerida;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.NivelDominio;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.TipoForo;
import com.pucp.skillb_ia.repository.ActividadRepository;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.CategoriaHabilidadRepository;
import com.pucp.skillb_ia.repository.ForoRepository;
import com.pucp.skillb_ia.repository.HabilidadRepository;
import com.pucp.skillb_ia.repository.LogAuditoriaRepository;
import com.pucp.skillb_ia.repository.NotificacionRepository;
import com.pucp.skillb_ia.repository.ProyectoHabilidadRequeridaRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.rm.RmProyectoRevisionService;
import com.pucp.skillb_ia.controller.RmViewController;
import com.pucp.skillb_ia.service.EvaluacionService;
import com.pucp.skillb_ia.service.rm.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.context.WebApplicationContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@ActiveProfiles("test")
class RmProyectoViewTests {
    @Autowired private WebApplicationContext context;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private RmProyectoRevisionService revisionService;
    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private LogAuditoriaRepository logAuditoriaRepository;
    @Autowired private ActividadRepository actividadRepository;
    @Autowired private NotificacionRepository notificacionRepository;
    @Autowired private CategoriaHabilidadRepository categoriaHabilidadRepository;
    @Autowired private HabilidadRepository habilidadRepository;
    @Autowired private ProyectoHabilidadRequeridaRepository habilidadRequeridaRepository;
    @Autowired private ForoRepository foroRepository;

    private MockMvc mockMvc;
    private Long proyectoId;
    private Long rmId;
    private Long pmId;
    private UsuarioDetails rmDetails;

    @BeforeEach
    void prepararDatos() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        Rol rolPm = rolRepository.findByNombre("PROJECT_MANAGER").orElseGet(() -> {
            Rol rol = new Rol();
            rol.setNombre("PROJECT_MANAGER");
            return rolRepository.save(rol);
        });
        Usuario pm = usuarioRepository.findByCorreo("pm.proyecto@skillbridge.test").orElseGet(() -> {
            Usuario usuario = new Usuario();
            usuario.setCorreo("pm.proyecto@skillbridge.test");
            usuario.setNombre("Project");
            usuario.setApellido("Manager");
            usuario.setRol(rolPm);
            return usuarioRepository.save(usuario);
        });
        pmId = pm.getId();
        Rol rolRm = rolRepository.findByNombre("RESOURCE_MANAGER").orElseGet(() -> {
            Rol rol = new Rol();
            rol.setNombre("RESOURCE_MANAGER");
            return rolRepository.save(rol);
        });
        Usuario rm = usuarioRepository.findByCorreo("rm.revision@skillbridge.test").orElseGet(() -> {
            Usuario usuario = new Usuario();
            usuario.setCorreo("rm.revision@skillbridge.test");
            usuario.setNombre("Resource");
            usuario.setApellido("Manager");
            usuario.setRol(rolRm);
            return usuarioRepository.save(usuario);
        });
        rmId = rm.getId();
        rm.setRol(rolRm); // el rol cargado es LAZY
        rmDetails = new UsuarioDetails(rm);
        Proyecto proyecto = new Proyecto();
        proyecto.setNombre("Proyecto de prueba RM");
        proyecto.setDescripcion("Proyecto utilizado para validar las vistas de consulta.");
        proyecto.setPrioridad(Prioridad.MEDIA);
        proyecto.setJustificacionPrioridad("Validación de la consulta del Resource Manager.");
        proyecto.setPm(pm);
        proyectoId = proyectoRepository.save(proyecto).getId();
    }

    @Test
    void renderizaListadoDeProyectos() throws Exception {
        mockMvc.perform(get("/rm/proyectos"))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-proyectos"))
                .andExpect(model().attributeExists("proyectos", "totalProyectos"));
    }

    // TASK-032: la vista raíz muestra directamente el encabezado, sin ruta de navegación de un solo elemento.
    @Test
    void listadoNoMuestraRutaDeNavegacionRedundante() throws Exception {
        String html = renderizar("/rm/proyectos").replaceAll("\\s+", " ");

        org.junit.jupiter.api.Assertions.assertFalse(html.contains("breadcrumb-wrap"));
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("Migas de pan"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("<h1 class=\"page-title\"> Proyectos </h1>"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("Consulta el estado de los proyectos"));
    }

    @Test
    void renderizaDetallePorId() throws Exception {
        mockMvc.perform(get("/rm/proyectos/detalle").param("id", proyectoId.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-detalle-proyecto"))
                .andExpect(model().attributeExists("proyecto"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(EstadoProyecto.class)
    void detalleMuestraVerForoDelProyectoEnCualquierEstado(EstadoProyecto estado) throws Exception {
        Proyecto proyecto = proyectoRepository.findById(proyectoId).orElseThrow();
        proyecto.setEstado(estado);
        proyectoRepository.save(proyecto);
        Foro foro = new Foro();
        foro.setProyecto(proyecto);
        foro.setTipo(TipoForo.PROYECTO);
        foro.setEsPublico(false);
        foro.setNombre("Foro del proyecto de prueba RM");
        Long foroId = foroRepository.save(foro).getId();

        String html = mockMvc.perform(get("/rm/proyectos/detalle").param("id", proyectoId.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-detalle-proyecto"))
                .andExpect(model().attribute("foroId", foroId))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String enlace = "href=\"/rm/foros/detalle?id=" + foroId + "\"";
        org.junit.jupiter.api.Assertions.assertEquals(1, html.split(java.util.regex.Pattern.quote(enlace), -1).length - 1);
        org.junit.jupiter.api.Assertions.assertTrue(html.contains(">Ver foro</a>"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("Proyecto de prueba RM"));
        // TASK-023: "Ver foro" no depende de las acciones de asignación.
        boolean asignable = estado == EstadoProyecto.ACTIVO || estado == EstadoProyecto.EN_ESPERA;
        org.junit.jupiter.api.Assertions.assertEquals(asignable, html.contains(">Buscar colaborador</a>"), estado.name());
        org.junit.jupiter.api.Assertions.assertEquals(asignable, html.contains(">Ver asignaciones</a>"), estado.name());
    }

    @Test
    void detalleSinForoNoMuestraVerForo() throws Exception {
        String html = mockMvc.perform(get("/rm/proyectos/detalle").param("id", proyectoId.toString()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("foroId", org.hamcrest.Matchers.nullValue()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        org.junit.jupiter.api.Assertions.assertFalse(html.contains("Ver foro"));
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("projectForumLink"));
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("/rm/foros/detalle"));
    }

    @Test
    void renderizaRevisionPorId() throws Exception {
        mockMvc.perform(get("/rm/proyectos/revision").param("id", proyectoId.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-revision-proyecto"))
                .andExpect(model().attributeExists("proyecto"));
    }

    @Test
    void asignaPresupuestoYAprobarActivaElProyecto() {
        revisionService.asignarPresupuesto(proyectoId, new BigDecimal("45000"), rmId);
        revisionService.aprobar(proyectoId, rmId);

        Proyecto proyecto = proyectoRepository.findById(proyectoId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(EstadoProyecto.ACTIVO, proyecto.getEstado());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("45000.00"), proyecto.getPresupuesto());
        org.junit.jupiter.api.Assertions.assertEquals(rmId, proyecto.getRmRevisor().getId());
    }

    @Test
    void rechazarRegistraEstadoMotivoYRevisor() {
        revisionService.rechazar(proyectoId, "El alcance necesita mayor precisión.", rmId);

        Proyecto proyecto = proyectoRepository.findById(proyectoId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(EstadoProyecto.RECHAZADO, proyecto.getEstado());
        org.junit.jupiter.api.Assertions.assertEquals("El alcance necesita mayor precisión.", proyecto.getMotivoRechazo());
        org.junit.jupiter.api.Assertions.assertEquals(rmId, proyecto.getRmRevisor().getId());
    }

    @Test
    void noPermiteAprobarSinPresupuesto() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> revisionService.aprobar(proyectoId, rmId));
    }

    @Test
    void noPermiteModificarUnProyectoYaRevisado() {
        revisionService.rechazar(proyectoId, "Rechazado en la primera decisión.", rmId);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> revisionService.asignarPresupuesto(proyectoId, new BigDecimal("1000"), rmId));
    }

    @Test
    void permiteActualizarPresupuestoDeProyectoActivo() {
        Proyecto proyecto = proyectoRepository.findById(proyectoId).orElseThrow();
        proyecto.setEstado(EstadoProyecto.ACTIVO);
        proyecto.setPresupuesto(new BigDecimal("10000.00"));
        proyectoRepository.save(proyecto);

        revisionService.asignarPresupuesto(proyectoId, new BigDecimal("12500"), rmId);

        Proyecto actualizado = proyectoRepository.findById(proyectoId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("12500.00"), actualizado.getPresupuesto());
    }

    @Test
    void projectManagerNoPuedeEditarPresupuesto() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> revisionService.asignarPresupuesto(proyectoId, new BigDecimal("10000"), pmId));
    }

    @Test
    void noPermiteEditarPresupuestoDeProyectoFinalizado() {
        Proyecto proyecto = proyectoRepository.findById(proyectoId).orElseThrow();
        proyecto.setEstado(EstadoProyecto.FINALIZADO);
        proyectoRepository.save(proyecto);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> revisionService.asignarPresupuesto(proyectoId, new BigDecimal("10000"), rmId));
    }

    @AfterEach
    void limpiarSesion() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void presupuestoVacioEnRevisionVuelveConMensaje() throws Exception {
        String destino = "/rm/proyectos/revision?id=" + proyectoId;
        verificarPresupuestoRechazado("", destino, "El presupuesto debe ser mayor que cero.");
    }

    @Test
    void presupuestoNoNumericoEnRevisionVuelveConMensaje() throws Exception {
        String destino = "/rm/proyectos/revision?id=" + proyectoId;
        verificarPresupuestoRechazado("abc", destino, "El presupuesto debe ser un monto numérico válido.");
    }

    @Test
    void presupuestoVacioEnDetalleVuelveConMensaje() throws Exception {
        activarProyecto();
        String destino = "/rm/proyectos/detalle?id=" + proyectoId;
        verificarPresupuestoRechazado("", destino, "El presupuesto debe ser mayor que cero.");
        verificarPresupuestoRechazado(null, destino, "El presupuesto debe ser mayor que cero.");
    }

    @Test
    void presupuestoNoNumericoEnDetalleVuelveConMensaje() throws Exception {
        activarProyecto();
        String destino = "/rm/proyectos/detalle?id=" + proyectoId;
        verificarPresupuestoRechazado("12,5.0", destino, "El presupuesto debe ser un monto numérico válido.");
    }

    @Test
    void presupuestoValidoSeGuardaDesdeRevisionYDetalle() throws Exception {
        autenticarRm();
        mockMvc.perform(post("/rm/proyectos/" + proyectoId + "/presupuesto").param("presupuesto", "45000"))
                .andExpect(redirectedUrl("/rm/proyectos/revision?id=" + proyectoId))
                .andExpect(flash().attribute("mensajeExito", "Presupuesto guardado correctamente."));
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("45000.00"),
                proyectoRepository.findById(proyectoId).orElseThrow().getPresupuesto());

        activarProyecto();
        mockMvc.perform(post("/rm/proyectos/" + proyectoId + "/presupuesto").param("presupuesto", "52000.50"))
                .andExpect(redirectedUrl("/rm/proyectos/detalle?id=" + proyectoId))
                .andExpect(flash().attribute("mensajeExito", "Presupuesto guardado correctamente."));
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("52000.50"),
                proyectoRepository.findById(proyectoId).orElseThrow().getPresupuesto());
    }

    private void verificarPresupuestoRechazado(String valor, String destino, String mensaje) throws Exception {
        autenticarRm();
        var peticion = post("/rm/proyectos/" + proyectoId + "/presupuesto");
        if (valor != null) peticion.param("presupuesto", valor);
        MvcResult resultado = mockMvc.perform(peticion)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(destino))
                .andExpect(flash().attribute("mensajeError", mensaje))
                .andReturn();
        String html = mockMvc.perform(get(destino).flashAttrs(resultado.getFlashMap()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertTrue(
                html.contains("<div class=\"alert alert-danger\">" + mensaje + "</div>"));
        org.junit.jupiter.api.Assertions.assertNull(
                proyectoRepository.findById(proyectoId).orElseThrow().getPresupuesto());
    }

    // TASK-044: el monto no se redondea ni se reinterpreta; más de 2 decimales se rechaza.
    private static final String MENSAJE_DECIMALES = "El presupuesto admite como máximo 2 decimales.";
    private static final String PATRON_PRESUPUESTO = "pattern=\"\\d{1,10}(\\.\\d{1,2})?\"";
    private static final String AYUDA_PRESUPUESTO =
            "Ingresa el monto sin separador de miles y usa punto para los decimales.";

    @Test
    void presupuestoConMasDeDosDecimalesSeRechazaSinCambiosNiAuditoria() {
        fijarPresupuesto(EstadoProyecto.EN_REVISION, "5000.00");
        for (String valor : new String[] {"1.000", "12345.675", "1000.004"}) {
            long auditorias = logAuditoriaRepository.count();
            IllegalArgumentException ex = org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> revisionService.asignarPresupuesto(proyectoId, new BigDecimal(valor), rmId), valor);
            org.junit.jupiter.api.Assertions.assertEquals(MENSAJE_DECIMALES, ex.getMessage(), valor);
            org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("5000.00"), presupuestoActual(), valor);
            org.junit.jupiter.api.Assertions.assertEquals(auditorias, logAuditoriaRepository.count(), valor);
        }
    }

    @Test
    void presupuestoConHastaDosDecimalesSeGuardaExacto() {
        String[][] casos = {{"999.99", "999.99"}, {"1000.5", "1000.50"}, {"9999999999.99", "9999999999.99"}};
        for (String[] caso : casos) {
            long auditorias = logAuditoriaRepository.count();
            revisionService.asignarPresupuesto(proyectoId, new BigDecimal(caso[0]), rmId);
            org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal(caso[1]), presupuestoActual(), caso[0]);
            org.junit.jupiter.api.Assertions.assertEquals(auditorias + 1, logAuditoriaRepository.count(), caso[0]);
        }
    }

    @Test
    void presupuestoMantieneLasReglasDeMontoPositivoMaximoYComprometido() {
        for (String valor : new String[] {"0", "0.00", "-1"}) {
            IllegalArgumentException ex = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> revisionService.asignarPresupuesto(proyectoId, new BigDecimal(valor), rmId));
            org.junit.jupiter.api.Assertions.assertEquals("El presupuesto debe ser mayor que cero.", ex.getMessage());
        }
        IllegalArgumentException maximo = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> revisionService.asignarPresupuesto(proyectoId, new BigDecimal("10000000000.00"), rmId));
        org.junit.jupiter.api.Assertions.assertEquals(
                "El presupuesto supera el monto máximo permitido.", maximo.getMessage());

        Proyecto proyecto = fijarPresupuesto(EstadoProyecto.ACTIVO, "100000.00");
        proyecto.setFechaInicio(java.time.LocalDate.now());
        proyecto.setFechaFinEstimada(java.time.LocalDate.now().plusMonths(3));
        proyectoRepository.save(proyecto);
        guardarAsignacion(EstadoAsignacion.ACTIVA, true);
        long auditorias = logAuditoriaRepository.count();
        IllegalArgumentException comprometido = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> revisionService.asignarPresupuesto(proyectoId, new BigDecimal("1.00"), rmId));
        org.junit.jupiter.api.Assertions.assertTrue(comprometido.getMessage().startsWith(
                "El presupuesto no puede ser menor que el monto ya comprometido o reservado"));
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("100000.00"), presupuestoActual());
        org.junit.jupiter.api.Assertions.assertEquals(auditorias, logAuditoriaRepository.count());
    }

    @Test
    void presupuestoConTresDecimalesDesdeRevisionVuelveConMensaje() throws Exception {
        fijarPresupuesto(EstadoProyecto.EN_REVISION, "5000.00");
        String destino = "/rm/proyectos/revision?id=" + proyectoId;
        verificarDecimalesRechazados("1.000", null, destino);
        verificarDecimalesRechazados("12345.675", null, destino);
    }

    @Test
    void presupuestoConTresDecimalesDesdeDetalleVuelveConMensaje() throws Exception {
        fijarPresupuesto(EstadoProyecto.ACTIVO, "5000.00");
        String destino = "/rm/proyectos/detalle?id=" + proyectoId;
        verificarDecimalesRechazados("1.000", null, destino);
        verificarDecimalesRechazados("12345.675", null, destino);
    }

    @Test
    void presupuestoConTresDecimalesDesdeRevisionDeAsignacionVuelveConMensaje() throws Exception {
        Asignacion asignacion = prepararRevisionDeAsignacion();
        String destino = "/rm/asignaciones/revision?id=" + asignacion.getId();
        verificarDecimalesRechazados("1.000", asignacion.getId(), destino);
        verificarDecimalesRechazados("12345.675", asignacion.getId(), destino);
    }

    @Test
    void camposDePresupuestoSonTextoDecimalConPatronYAyuda() throws Exception {
        autenticarRm();
        fijarPresupuesto(EstadoProyecto.EN_REVISION, "5000.00");
        verificarCampoPresupuesto("/rm/proyectos/revision?id=" + proyectoId, "budgetInput");
        fijarPresupuesto(EstadoProyecto.ACTIVO, "5000.00");
        verificarCampoPresupuesto("/rm/proyectos/detalle?id=" + proyectoId, "projectBudget");
        Asignacion asignacion = prepararRevisionDeAsignacion();
        verificarCampoPresupuesto("/rm/asignaciones/revision?id=" + asignacion.getId(), "assignmentProjectBudget");
    }

    // TASK-017: los montos mostrados usan "S/ 12,345.00"; los campos de TASK-044 conservan el decimal sin formato.
    // Costo de referencia: sueldo 5000.00 / 160 = 31.25 por hora; 37.5 h/sem; 70 días = 10 semanas.
    @Test
    void detalleMuestraResumenYCostosConFormatoMonetario() throws Exception {
        autenticarRm();
        prepararProyectoConCosto("12345");
        guardarAsignacion(EstadoAsignacion.ACTIVA, true, "5000.00", "37.5");
        String html = renderizar("/rm/proyectos/detalle?id=" + proyectoId);
        verificarMontos(html, "S/ 12,345.00", "S/ 11,718.75", "S/ 0.00", "S/ 626.25", "S/ 1,171.88");
        verificarSinFormatoAnterior(html, "S/ 12345.00", "S/ 11718.75", "S/ 1171.88");
        verificarCampoSinFormato(html, "projectBudget", "12345.00");
    }

    @Test
    void listadoMuestraDisponibleConFormatoMonetario() throws Exception {
        autenticarRm();
        fijarPresupuesto(EstadoProyecto.ACTIVO, "9999999999.99");
        String html = renderizar("/rm/proyectos");
        verificarMontos(html, "Disponible: S/ 9,999,999,999.99 (0% consumido)");
        verificarSinFormatoAnterior(html, "S/ 9999999999.99");
    }

    @Test
    void revisionDeAsignacionMuestraImpactoConFormatoMonetario() throws Exception {
        autenticarRm();
        prepararProyectoConCosto("1000.5");
        Asignacion asignacion = guardarAsignacion(EstadoAsignacion.PENDIENTE, false, "5000.00", "37.5");
        String html = renderizar("/rm/asignaciones/revision?id=" + asignacion.getId());
        verificarMontos(html, "S/ 1,000.50", "S/ 31.25", "S/ 1,171.88", "S/ 11,718.75", "S/ 0.00",
                "Presupuesto insuficiente: faltan S/ 10,718.25.");
        verificarSinFormatoAnterior(html, "S/ 1000.50", "S/ 1171.88", "S/ 11718.75", "S/ 10718.25");
        verificarCampoSinFormato(html, "assignmentProjectBudget", "1000.50");
    }

    @Test
    void busquedaDeColaboradoresMuestraCostoEstimadoConFormatoMonetario() throws Exception {
        autenticarRm();
        Proyecto proyecto = prepararProyectoConCosto("1234567.89");
        proyecto.setHorasSemanalesRequeridas(new BigDecimal("37.5"));
        proyectoRepository.save(proyecto);
        guardarAsignacion(EstadoAsignacion.PENDIENTE, false, "5000.00", "37.5");
        // Desde TASK-029 la búsqueda pagina de 6 en 6: se acota por nombre para ver la tarjeta.
        String html = renderizar("/rm/proyectos/buscar-colaboradores?proyectoId=" + proyectoId
                + "&busqueda=Colaborador Presupuesto");
        verificarMontos(html, "S/ 11,718.75");
        verificarSinFormatoAnterior(html, "S/ 11718.75");
        // Los data-* los consume el JS: siguen con el decimal sin formato.
        verificarMontos(html, "data-proyecto-presupuesto=\"1234567.89\"");
    }

    // TASK-018: aprobar solo con presupuesto, "Usar presupuesto solicitado" y confirmación de cambios.
    private static final String MENSAJE_SIN_PRESUPUESTO = "Asigna un presupuesto válido antes de aprobar el proyecto.";
    private static final String USAR_SOLICITADO = "Usar presupuesto solicitado";

    @Test
    void revisionSinPresupuestoValidoMuestraAprobarDeshabilitado() throws Exception {
        autenticarRm();
        for (String presupuesto : new String[] {null, "0.00"}) {
            Proyecto proyecto = proyectoRepository.findById(proyectoId).orElseThrow();
            proyecto.setPresupuesto(presupuesto == null ? null : new BigDecimal(presupuesto));
            proyectoRepository.save(proyecto);
            String html = renderizar("/rm/proyectos/revision?id=" + proyectoId);
            verificarMontos(html, "disabled aria-describedby=\"approveProjectBloqueo\"",
                    "Asigna un presupuesto mayor que cero para poder aprobar el proyecto.");
            verificarSinFormatoAnterior(html, "data-bs-target=\"#approveProjectModal\"", "id=\"approveProjectModal\"");
        }
    }

    @Test
    void revisionConPresupuestoHabilitaAprobarSinObservacion() throws Exception {
        autenticarRm();
        fijarPresupuesto(EstadoProyecto.EN_REVISION, "5000.00");
        String html = renderizar("/rm/proyectos/revision?id=" + proyectoId);
        verificarMontos(html, "data-bs-target=\"#approveProjectModal\"", "id=\"approveProjectModal\"",
                "Confirmar aprobación");
        verificarSinFormatoAnterior(html, "approveProjectBloqueo", "name=\"observacion\"",
                "Observación de aprobación", "reviewComment");
    }

    @Test
    void aprobarPorPostSinPresupuestoSeRechazaYSigueEnRevision() throws Exception {
        autenticarRm();
        long auditorias = logAuditoriaRepository.count();
        mockMvc.perform(post("/rm/proyectos/" + proyectoId + "/aprobar"))
                .andExpect(redirectedUrl("/rm/proyectos/revision?id=" + proyectoId))
                .andExpect(flash().attribute("mensajeError", MENSAJE_SIN_PRESUPUESTO));
        Proyecto proyecto = proyectoRepository.findById(proyectoId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(EstadoProyecto.EN_REVISION, proyecto.getEstado());
        org.junit.jupiter.api.Assertions.assertNull(proyecto.getRmRevisor());
        org.junit.jupiter.api.Assertions.assertEquals(auditorias, logAuditoriaRepository.count());
    }

    @Test
    void aprobarPorPostConPresupuestoActivaYAuditaSinObservacion() throws Exception {
        autenticarRm();
        fijarPresupuesto(EstadoProyecto.EN_REVISION, "5000.00");
        mockMvc.perform(post("/rm/proyectos/" + proyectoId + "/aprobar"))
                .andExpect(redirectedUrl("/rm/proyectos/detalle?id=" + proyectoId))
                .andExpect(flash().attribute("mensajeExito", "Proyecto aprobado y activado correctamente."));
        org.junit.jupiter.api.Assertions.assertEquals(EstadoProyecto.ACTIVO,
                proyectoRepository.findById(proyectoId).orElseThrow().getEstado());
        LogAuditoria auditoria = ultimaAuditoria("APROBAR_PROYECTO");
        org.junit.jupiter.api.Assertions.assertEquals("Proyecto aprobado", auditoria.getDetalle());
        org.junit.jupiter.api.Assertions.assertEquals("EN_REVISION", auditoria.getValorAnterior());
        org.junit.jupiter.api.Assertions.assertEquals("ACTIVO", auditoria.getValorNuevo());
    }

    @Test
    void usarPresupuestoSolicitadoSeMuestraConElMontoCrudo() throws Exception {
        autenticarRm();
        fijarSolicitado("45000.50");
        String html = renderizar("/rm/proyectos/revision?id=" + proyectoId);
        String formulario = formularioSolicitado(html);
        verificarMontos(formulario, "<input type=\"hidden\" name=\"presupuesto\" value=\"45000.50\">",
                "data-rm-budget-form", USAR_SOLICITADO);
        verificarSinAnterior(formulario);
        verificarSinFormatoAnterior(formulario, "S/", "45,000.50");
        // El monto mostrado conserva el formato de TASK-017.
        verificarMontos(html, "S/ 45,000.50");
    }

    @Test
    void usarPresupuestoSolicitadoNoApareceSiEsNuloInvalidoOIgualAlAsignado() throws Exception {
        autenticarRm();
        fijarSolicitado(null);
        verificarSinFormatoAnterior(renderizar("/rm/proyectos/revision?id=" + proyectoId), USAR_SOLICITADO);
        for (String invalido : new String[] {"0.00", "-10.00"}) {
            fijarSolicitado(invalido);
            verificarSinFormatoAnterior(renderizar("/rm/proyectos/revision?id=" + proyectoId), USAR_SOLICITADO);
        }
        fijarPresupuesto(EstadoProyecto.EN_REVISION, "45000.00");
        fijarSolicitado("45000");
        verificarSinFormatoAnterior(renderizar("/rm/proyectos/revision?id=" + proyectoId), USAR_SOLICITADO);
    }

    @Test
    void usarPresupuestoSolicitadoGuardaExactoYAudita() throws Exception {
        autenticarRm();
        fijarSolicitado("45000.50");
        String monto = valorOculto(formularioSolicitado(renderizar("/rm/proyectos/revision?id=" + proyectoId)));
        mockMvc.perform(post("/rm/proyectos/" + proyectoId + "/presupuesto").param("presupuesto", monto))
                .andExpect(redirectedUrl("/rm/proyectos/revision?id=" + proyectoId))
                .andExpect(flash().attribute("mensajeExito", "Presupuesto guardado correctamente."));
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("45000.50"), presupuestoActual());
        LogAuditoria asignada = ultimaAuditoria("ASIGNAR_PRESUPUESTO_PROYECTO");
        org.junit.jupiter.api.Assertions.assertNull(asignada.getValorAnterior());
        org.junit.jupiter.api.Assertions.assertEquals("45000.50", asignada.getValorNuevo());

        // Con un presupuesto distinto ya asignado, la acción es un cambio: pasa por la confirmación.
        fijarPresupuesto(EstadoProyecto.EN_REVISION, "30000.00");
        String formulario = formularioSolicitado(renderizar("/rm/proyectos/revision?id=" + proyectoId));
        verificarMontos(formulario, "data-rm-budget-form", "data-presupuesto-anterior=\"30000.00\"");
        mockMvc.perform(post("/rm/proyectos/" + proyectoId + "/presupuesto").param("presupuesto", valorOculto(formulario)))
                .andExpect(flash().attribute("mensajeExito", "Presupuesto guardado correctamente."));
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("45000.50"), presupuestoActual());
        LogAuditoria actualizada = ultimaAuditoria("ACTUALIZAR_PRESUPUESTO_PROYECTO");
        org.junit.jupiter.api.Assertions.assertEquals("30000.00", actualizada.getValorAnterior());
        org.junit.jupiter.api.Assertions.assertEquals("45000.50", actualizada.getValorNuevo());
    }

    @Test
    void presupuestoManipuladoSigueValidadoEnElServidor() throws Exception {
        fijarSolicitado("45000.50");
        String destino = "/rm/proyectos/revision?id=" + proyectoId;
        verificarPresupuestoRechazado("0", destino, "El presupuesto debe ser mayor que cero.");
        verificarPresupuestoRechazado("10000000000.00", destino, "El presupuesto supera el monto máximo permitido.");
        verificarPresupuestoRechazado("S/ 45,000.50", destino, "El presupuesto debe ser un monto numérico válido.");
        verificarPresupuestoRechazado("45000.505", destino, MENSAJE_DECIMALES);
    }

    @Test
    void cambiarPresupuestoExistenteUsaModalDeConfirmacionEnRevisionYDetalle() throws Exception {
        autenticarRm();
        fijarPresupuesto(EstadoProyecto.EN_REVISION, "30000.00");
        verificarConfirmacion(renderizar("/rm/proyectos/revision?id=" + proyectoId), "budgetInput", "30000.00");
        fijarPresupuesto(EstadoProyecto.ACTIVO, "30000.00");
        verificarConfirmacion(renderizar("/rm/proyectos/detalle?id=" + proyectoId), "projectBudget", "30000.00");
    }

    @Test
    void primeraAsignacionNoSeTrataComoCambio() throws Exception {
        autenticarRm();
        verificarConfirmacion(renderizar("/rm/proyectos/revision?id=" + proyectoId), "budgetInput", "");
        activarProyecto();
        String detalle = renderizar("/rm/proyectos/detalle?id=" + proyectoId);
        verificarConfirmacion(detalle, "projectBudget", "");
        verificarMontos(detalle, "Asignar presupuesto");
    }

    // El formulario lleva el valor anterior crudo; el modal muestra ambos valores y cancelar no envía nada.
    private void verificarConfirmacion(String html, String idCampo, String anterior) {
        int apertura = html.lastIndexOf("<form", html.indexOf("id=\"" + idCampo + "\""));
        String formulario = html.substring(apertura, html.indexOf('>', apertura) + 1);
        verificarMontos(formulario, "data-rm-budget-form");
        if (anterior.isEmpty()) verificarSinAnterior(formulario);
        else verificarMontos(formulario, "data-presupuesto-anterior=\"" + anterior + "\"");
        verificarSinFormatoAnterior(formulario, "S/", ",");
        int inicio = html.indexOf("id=\"budgetChangeModal\"");
        org.junit.jupiter.api.Assertions.assertTrue(inicio >= 0, "budgetChangeModal");
        String modal = html.substring(inicio, html.indexOf("<script", inicio));
        verificarMontos(modal, "Presupuesto anterior", "data-budget-anterior", "Presupuesto nuevo", "data-budget-nuevo",
                "<button type=\"button\" class=\"btn btn-primary\" data-budget-confirmar>Confirmar cambio</button>",
                "<button type=\"button\" class=\"btn btn-outline-secondary\" data-bs-dismiss=\"modal\">Cancelar</button>");
        verificarSinFormatoAnterior(modal, "<form", "type=\"submit\"");
        verificarMontos(html, "/js/rm-js/rm-presupuesto-confirmacion.js");
    }

    // Sin presupuesto asignado Thymeleaf omite el atributo: el JS lo trata como primera asignación.
    private void verificarSinAnterior(String formulario) {
        org.junit.jupiter.api.Assertions.assertFalse(
                formulario.matches("(?s).*data-presupuesto-anterior=\"\\d.*"), formulario);
    }

    private void fijarSolicitado(String solicitado) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId).orElseThrow();
        proyecto.setPresupuestoSolicitado(solicitado == null ? null : new BigDecimal(solicitado));
        proyectoRepository.save(proyecto);
    }

    private String formularioSolicitado(String html) {
        int inicio = html.indexOf("id=\"useRequestedBudgetForm\"");
        org.junit.jupiter.api.Assertions.assertTrue(inicio >= 0, "useRequestedBudgetForm");
        return html.substring(html.lastIndexOf("<form", inicio), html.indexOf("</form>", inicio));
    }

    private String valorOculto(String formulario) {
        String marca = "name=\"presupuesto\" value=\"";
        int inicio = formulario.indexOf(marca) + marca.length();
        return formulario.substring(inicio, formulario.indexOf('"', inicio));
    }

    private LogAuditoria ultimaAuditoria(String accion) {
        return logAuditoriaRepository.findAll().stream()
                .filter(log -> accion.equals(log.getAccion()) && proyectoId.equals(log.getEntidadId()))
                .max(java.util.Comparator.comparing(LogAuditoria::getId))
                .orElseThrow();
    }

    private Proyecto prepararProyectoConCosto(String presupuesto) {
        Proyecto proyecto = fijarPresupuesto(EstadoProyecto.ACTIVO, presupuesto);
        proyecto.setFechaInicio(java.time.LocalDate.now());
        proyecto.setFechaFinEstimada(java.time.LocalDate.now().plusDays(70));
        return proyectoRepository.save(proyecto);
    }

    private String renderizar(String url) throws Exception {
        return mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private void verificarMontos(String html, String... esperados) {
        for (String esperado : esperados) {
            org.junit.jupiter.api.Assertions.assertTrue(html.contains(esperado), esperado);
        }
    }

    private void verificarSinFormatoAnterior(String html, String... anteriores) {
        for (String anterior : anteriores) {
            org.junit.jupiter.api.Assertions.assertFalse(html.contains(anterior), anterior);
        }
    }

    // El campo de TASK-044 envía el decimal crudo: sin comas, sin "S/" y con su validación.
    private void verificarCampoSinFormato(String html, String idCampo, String valor) {
        int inicio = html.indexOf("id=\"" + idCampo + "\"");
        org.junit.jupiter.api.Assertions.assertTrue(inicio >= 0, idCampo);
        String campo = html.substring(html.lastIndexOf("<input", inicio), html.indexOf('>', inicio) + 1);
        org.junit.jupiter.api.Assertions.assertTrue(campo.contains("value=\"" + valor + "\""), campo);
        org.junit.jupiter.api.Assertions.assertFalse(valor.contains(","), valor);
        org.junit.jupiter.api.Assertions.assertFalse(campo.contains("S/"), campo);
        org.junit.jupiter.api.Assertions.assertTrue(campo.contains("type=\"text\""), campo);
        org.junit.jupiter.api.Assertions.assertTrue(campo.contains("inputmode=\"decimal\""), campo);
        org.junit.jupiter.api.Assertions.assertTrue(campo.contains(PATRON_PRESUPUESTO), campo);
        org.junit.jupiter.api.Assertions.assertTrue(campo.contains("required"), campo);
        org.junit.jupiter.api.Assertions.assertTrue(
                html.contains("<div id=\"" + idCampo + "Ayuda\" class=\"form-hint\">" + AYUDA_PRESUPUESTO + "</div>"), idCampo);
    }

    private void verificarDecimalesRechazados(String valor, Long asignacionId, String destino) throws Exception {
        autenticarRm();
        BigDecimal anterior = presupuestoActual();
        long auditorias = logAuditoriaRepository.count();
        var peticion = post("/rm/proyectos/" + proyectoId + "/presupuesto").param("presupuesto", valor);
        if (asignacionId != null) peticion.param("asignacionId", asignacionId.toString());
        mockMvc.perform(peticion)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(destino))
                .andExpect(flash().attribute("mensajeError", MENSAJE_DECIMALES));
        org.junit.jupiter.api.Assertions.assertEquals(anterior, presupuestoActual(), valor);
        org.junit.jupiter.api.Assertions.assertEquals(auditorias, logAuditoriaRepository.count(), valor);
    }

    private void verificarCampoPresupuesto(String url, String idCampo) throws Exception {
        String html = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        int inicio = html.indexOf("id=\"" + idCampo + "\"");
        org.junit.jupiter.api.Assertions.assertTrue(inicio >= 0, url);
        int apertura = html.lastIndexOf("<input", inicio);
        String campo = html.substring(apertura, html.indexOf('>', inicio) + 1);
        org.junit.jupiter.api.Assertions.assertTrue(campo.contains("name=\"presupuesto\""), campo);
        org.junit.jupiter.api.Assertions.assertFalse(campo.contains("type=\"number\""), campo);
        org.junit.jupiter.api.Assertions.assertFalse(campo.contains("step="), campo);
        org.junit.jupiter.api.Assertions.assertTrue(campo.contains("type=\"text\""), campo);
        org.junit.jupiter.api.Assertions.assertTrue(campo.contains("inputmode=\"decimal\""), campo);
        org.junit.jupiter.api.Assertions.assertTrue(campo.contains(PATRON_PRESUPUESTO), campo);
        org.junit.jupiter.api.Assertions.assertTrue(campo.contains("required"), campo);
        org.junit.jupiter.api.Assertions.assertTrue(campo.contains("value=\"5000.00\""), campo);
        org.junit.jupiter.api.Assertions.assertTrue(
                html.contains("<div id=\"" + idCampo + "Ayuda\" class=\"form-hint\">" + AYUDA_PRESUPUESTO + "</div>"), url);
    }

    private Proyecto fijarPresupuesto(EstadoProyecto estado, String presupuesto) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId).orElseThrow();
        proyecto.setEstado(estado);
        proyecto.setPresupuesto(new BigDecimal(presupuesto));
        return proyectoRepository.save(proyecto);
    }

    private BigDecimal presupuestoActual() {
        return proyectoRepository.findById(proyectoId).orElseThrow().getPresupuesto();
    }

    // Presupuesto insuficiente para la propuesta: la revisión de la asignación muestra el formulario.
    private Asignacion prepararRevisionDeAsignacion() {
        Proyecto proyecto = fijarPresupuesto(EstadoProyecto.ACTIVO, "5000.00");
        proyecto.setFechaInicio(java.time.LocalDate.now());
        proyecto.setFechaFinEstimada(java.time.LocalDate.now().plusMonths(3));
        proyectoRepository.save(proyecto);
        return guardarAsignacion(EstadoAsignacion.PENDIENTE, false);
    }

    private Asignacion guardarAsignacion(EstadoAsignacion estado, boolean aprobadoRm) {
        return guardarAsignacion(estado, aprobadoRm, "4800.00", "40");
    }

    private Asignacion guardarAsignacion(EstadoAsignacion estado, boolean aprobadoRm, String sueldo, String horas) {
        Rol rolColaborador = rolRepository.findByNombre("COLABORADOR").orElseGet(() -> {
            Rol rol = new Rol();
            rol.setNombre("COLABORADOR");
            return rolRepository.save(rol);
        });
        Usuario colaborador = usuarioRepository.findByCorreo("col.presupuesto@skillbridge.test").orElseGet(() -> {
            Usuario usuario = new Usuario();
            usuario.setCorreo("col.presupuesto@skillbridge.test");
            usuario.setNombre("Colaborador");
            usuario.setApellido("Presupuesto");
            usuario.setRol(rolColaborador);
            return usuario;
        });
        colaborador.setSueldoBase(new BigDecimal(sueldo));
        colaborador.setHorasDisponibles(new BigDecimal("20.00"));
        colaborador.setHorasContratadasSemana(new BigDecimal("40.00"));
        colaborador = usuarioRepository.save(colaborador);

        Asignacion asignacion = new Asignacion();
        asignacion.setProyecto(proyectoRepository.findById(proyectoId).orElseThrow());
        asignacion.setColaborador(colaborador);
        asignacion.setHorasSemanales(new BigDecimal(horas));
        asignacion.setOrigen(OrigenAsignacion.PROPUESTA_PM);
        asignacion.setEstado(estado);
        asignacion.setAprobadoPorPm(true);
        asignacion.setAprobadoPorRm(aprobadoRm);
        asignacion.setMensajeSolicitud("Solicitud de prueba.");
        return asignacionRepository.save(asignacion);
    }

    // TASK-021: cambio de fechas del proyecto por el RM.
    private static final String AUDITORIA_FECHAS = "ACTUALIZAR_FECHAS_PROYECTO";
    private static final String NOTIFICACION_FECHAS = "PROYECTO_FECHAS_ACTUALIZADAS";
    private static final String EXITO_FECHAS = "Fechas del proyecto actualizadas correctamente.";
    private static final String MENSAJE_FIN_POSTERIOR = "La fecha de fin debe ser posterior a la fecha de inicio.";
    private static final java.time.LocalDate HOY = java.time.LocalDate.now();

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = EstadoProyecto.class, names = {"EN_REVISION", "ACTIVO", "EN_ESPERA"})
    void cambioDeFechasValidoGuardaAuditaYNotificaEnEstadosPermitidos(EstadoProyecto estado) {
        fijarFechas(estado, HOY, HOY.plusDays(70));

        revisionService.cambiarFechas(proyectoId, HOY.plusDays(7), HOY.plusDays(90), rmId);

        Proyecto proyecto = proyectoRepository.findById(proyectoId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(HOY.plusDays(7), proyecto.getFechaInicio());
        org.junit.jupiter.api.Assertions.assertEquals(HOY.plusDays(90), proyecto.getFechaFinEstimada());
        org.junit.jupiter.api.Assertions.assertEquals(estado, proyecto.getEstado());
        LogAuditoria auditoria = ultimaAuditoria(AUDITORIA_FECHAS);
        org.junit.jupiter.api.Assertions.assertEquals(rmId, auditoria.getUsuario().getId());
        org.junit.jupiter.api.Assertions.assertEquals(
                "Inicio: " + fecha(HOY) + " | Fin: " + fecha(HOY.plusDays(70)), auditoria.getValorAnterior());
        org.junit.jupiter.api.Assertions.assertEquals(
                "Inicio: " + fecha(HOY.plusDays(7)) + " | Fin: " + fecha(HOY.plusDays(90)), auditoria.getValorNuevo());
        List<Notificacion> notificaciones = notificacionesDeFechas();
        org.junit.jupiter.api.Assertions.assertEquals(1, notificaciones.size());
        org.junit.jupiter.api.Assertions.assertTrue(notificaciones.get(0).getDescripcion()
                .contains("del " + fecha(HOY.plusDays(7)) + " al " + fecha(HOY.plusDays(90))));
        org.junit.jupiter.api.Assertions.assertEquals(1, auditoriasDeFechas());
    }

    @Test
    void cambioDeFechasSinFechasPreviasAuditaSinDefinir() {
        revisionService.cambiarFechas(proyectoId, HOY, HOY.plusDays(1), rmId);
        org.junit.jupiter.api.Assertions.assertEquals("Inicio: sin definir | Fin: sin definir",
                ultimaAuditoria(AUDITORIA_FECHAS).getValorAnterior());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = EstadoProyecto.class, names = {"RECHAZADO", "CANCELADO", "FINALIZADO"})
    void cambioDeFechasSeRechazaEnEstadosCerrados(EstadoProyecto estado) {
        fijarFechas(estado, HOY, HOY.plusDays(70));
        verificarFechasSinCambios(IllegalStateException.class, HOY.plusDays(1), HOY.plusDays(80), rmId,
                "Las fechas no pueden modificarse en el estado actual del proyecto.");
    }

    @Test
    void cambioDeFechasExigeAmbasFechasYFinPosterior() {
        fijarFechas(EstadoProyecto.ACTIVO, HOY, HOY.plusDays(70));
        verificarFechasSinCambios(IllegalArgumentException.class, HOY.plusDays(5), HOY.plusDays(5), rmId,
                MENSAJE_FIN_POSTERIOR);
        verificarFechasSinCambios(IllegalArgumentException.class, HOY.plusDays(5), HOY.plusDays(4), rmId,
                MENSAJE_FIN_POSTERIOR);
        String obligatorias = "Debes indicar la fecha de inicio y la fecha de fin del proyecto.";
        verificarFechasSinCambios(IllegalArgumentException.class, null, HOY.plusDays(4), rmId, obligatorias);
        verificarFechasSinCambios(IllegalArgumentException.class, HOY, null, rmId, obligatorias);
    }

    @Test
    void fechaDeInicioNuevaNoPuedeSerAnteriorAHoy() {
        String mensaje = "La fecha de inicio no puede ser anterior a la fecha actual.";
        fijarFechas(EstadoProyecto.EN_REVISION, null, null);
        verificarFechasSinCambios(IllegalArgumentException.class, HOY.minusDays(1), HOY.plusDays(30), rmId, mensaje);
        fijarFechas(EstadoProyecto.ACTIVO, HOY.minusDays(30), HOY.plusDays(30));
        verificarFechasSinCambios(IllegalArgumentException.class, HOY.minusDays(10), HOY.plusDays(30), rmId, mensaje);

        // Un proyecto ya iniciado conserva su inicio pasado y solo extiende el fin.
        revisionService.cambiarFechas(proyectoId, HOY.minusDays(30), HOY.plusDays(60), rmId);
        verificarFechas(HOY.minusDays(30), HOY.plusDays(60));
    }

    @Test
    void cambioDeFechasSeRechazaSiUnaActividadQuedaFueraDelRango() {
        fijarFechas(EstadoProyecto.ACTIVO, HOY, HOY.plusDays(70));
        guardarActividad("Diseño de pantallas", HOY.plusDays(30));
        String mensaje = "No se cambiaron las fechas: 1 actividad(es) vencen fuera del nuevo rango, por ejemplo "
                + "\"Diseño de pantallas\" con fecha límite " + fecha(HOY.plusDays(30)) + ".";
        verificarFechasSinCambios(IllegalArgumentException.class, HOY.plusDays(31), HOY.plusDays(90), rmId, mensaje);
        verificarFechasSinCambios(IllegalArgumentException.class, HOY, HOY.plusDays(29), rmId, mensaje);

        // Los extremos del rango son válidos.
        revisionService.cambiarFechas(proyectoId, HOY.plusDays(30), HOY.plusDays(60), rmId);
        revisionService.cambiarFechas(proyectoId, HOY, HOY.plusDays(30), rmId);
        org.junit.jupiter.api.Assertions.assertEquals(HOY.plusDays(30),
                proyectoRepository.findById(proyectoId).orElseThrow().getFechaFinEstimada());
    }

    // Costo de referencia: 31.25 por hora x 37.5 h/sem = 1171.875 por semana; 70 días = 11 718.75; 84 días = 14 062.50.
    @Test
    void cambioDeFechasSeRechazaSiComprometidoOReservadoSuperanElPresupuesto() {
        prepararProyectoConCosto("12345");
        Asignacion asignacion = guardarAsignacion(EstadoAsignacion.ACTIVA, true, "5000.00", "37.5");
        String mensaje = "No se cambiaron las fechas: con el nuevo rango, el costo comprometido y reservado "
                + "(S/ 14062.50) supera el presupuesto del proyecto (S/ 12345.00).";
        verificarFechasSinCambios(IllegalArgumentException.class, HOY, HOY.plusDays(84), rmId, mensaje);

        asignacion.setEstado(EstadoAsignacion.PENDIENTE);
        asignacionRepository.save(asignacion); // reservado: pendiente ya aprobada por el RM
        verificarFechasSinCambios(IllegalArgumentException.class, HOY, HOY.plusDays(84), rmId, mensaje);

        revisionService.cambiarFechas(proyectoId, HOY, HOY.plusDays(56), rmId);
        org.junit.jupiter.api.Assertions.assertEquals(HOY.plusDays(56),
                proyectoRepository.findById(proyectoId).orElseThrow().getFechaFinEstimada());
    }

    @Test
    void usuarioQueNoEsRmNoPuedeCambiarFechas() throws Exception {
        fijarFechas(EstadoProyecto.ACTIVO, HOY, HOY.plusDays(70));
        verificarFechasSinCambios(IllegalArgumentException.class, HOY.plusDays(1), HOY.plusDays(80), pmId,
                "El usuario autenticado no es un Resource Manager activo.");

        Usuario pm = usuarioRepository.findById(pmId).orElseThrow();
        pm.setRol(rolRepository.findByNombre("PROJECT_MANAGER").orElseThrow()); // el rol cargado es LAZY
        UsuarioDetails pmDetails = new UsuarioDetails(pm);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(pmDetails, null, pmDetails.getAuthorities()));
        mockMvc.perform(post("/rm/proyectos/" + proyectoId + "/fechas")
                        .param("fechaInicio", HOY.plusDays(1).toString())
                        .param("fechaFin", HOY.plusDays(80).toString()))
                .andExpect(redirectedUrl("/rm/proyectos/detalle?id=" + proyectoId))
                .andExpect(flash().attribute("mensajeError", "El usuario autenticado no es un Resource Manager activo."));
        verificarFechas(HOY, HOY.plusDays(70));
        org.junit.jupiter.api.Assertions.assertEquals(0, auditoriasDeFechas());
        org.junit.jupiter.api.Assertions.assertTrue(notificacionesDeFechas().isEmpty());
    }

    @Test
    void formularioDeFechasSoloApareceEnEstadosPermitidosConLasFechasActuales() throws Exception {
        autenticarRm();
        for (EstadoProyecto estado : EstadoProyecto.values()) {
            fijarFechas(estado, HOY, HOY.plusDays(70));
            String html = renderizar("/rm/proyectos/detalle?id=" + proyectoId);
            boolean permitido = estado == EstadoProyecto.EN_REVISION || estado == EstadoProyecto.ACTIVO
                    || estado == EstadoProyecto.EN_ESPERA;
            org.junit.jupiter.api.Assertions.assertEquals(permitido, html.contains("id=\"projectDatesForm\""), estado.name());
            if (!permitido) continue;
            int inicio = html.indexOf("id=\"projectDatesForm\"");
            String formulario = html.substring(html.lastIndexOf("<form", inicio), html.indexOf("</form>", inicio));
            verificarMontos(formulario, "/rm/proyectos/" + proyectoId + "/fechas",
                    "name=\"fechaInicio\"", "value=\"" + HOY + "\"", "name=\"fechaFin\"", "value=\"" + HOY.plusDays(70) + "\"");
            // Convive con el modal de presupuesto de TASK-018 sin que su JS lo intercepte.
            verificarSinFormatoAnterior(formulario, "data-rm-budget-form");
            verificarMontos(html, "id=\"budgetChangeModal\"", "/js/rm-js/rm-presupuesto-confirmacion.js");
        }
        // Mínimos del calendario: inicio desde hoy y fin desde el día siguiente al inicio.
        fijarFechas(EstadoProyecto.ACTIVO, HOY.plusDays(5), HOY.plusDays(70));
        verificarMontos(formularioDeFechas(), "id=\"projectStartDate\"", "min=\"" + HOY + "\"",
                "min=\"" + HOY.plusDays(6) + "\"", "/js/rm-js/rm-proyecto-fechas.js");
        // Si el inicio actual ya pasó, se permite conservarlo (el servidor rechaza otro día pasado).
        fijarFechas(EstadoProyecto.ACTIVO, HOY.minusDays(30), HOY.plusDays(70));
        verificarMontos(formularioDeFechas(), "min=\"" + HOY.minusDays(30) + "\"", "min=\"" + HOY.minusDays(29) + "\"");
        fijarFechas(EstadoProyecto.EN_REVISION, null, null);
        verificarMontos(formularioDeFechas(), "min=\"" + HOY + "\"", "min=\"" + HOY.plusDays(1) + "\"");

        verificarMontos(formularioDeFechas(), "name=\"origen\" value=\"detalle\"");

        // En revisión el mismo fragmento aparece también en la revisión, con sus mínimos y su script.
        fijarFechas(EstadoProyecto.EN_REVISION, HOY.plusDays(5), HOY.plusDays(70));
        String revision = renderizar("/rm/proyectos/revision?id=" + proyectoId);
        int inicio = revision.indexOf("id=\"projectDatesForm\"");
        org.junit.jupiter.api.Assertions.assertTrue(inicio >= 0, "revisión sin formulario de fechas");
        org.junit.jupiter.api.Assertions.assertEquals(inicio, revision.lastIndexOf("id=\"projectDatesForm\""));
        String formulario = revision.substring(revision.lastIndexOf("<form", inicio), revision.indexOf("</form>", inicio));
        verificarMontos(formulario, "/rm/proyectos/" + proyectoId + "/fechas", "name=\"origen\" value=\"revision\"",
                "value=\"" + HOY.plusDays(5) + "\"", "min=\"" + HOY + "\"", "min=\"" + HOY.plusDays(6) + "\"");
        verificarSinFormatoAnterior(formulario, "data-rm-budget-form");
        verificarMontos(revision, "id=\"budgetChangeModal\"", "/js/rm-js/rm-proyecto-fechas.js");
    }

    @Test
    void postDeFechasDesdeLaRevisionVuelveALaRevisionConMensaje() throws Exception {
        autenticarRm();
        fijarFechas(EstadoProyecto.EN_REVISION, HOY, HOY.plusDays(70));
        String destino = "/rm/proyectos/revision?id=" + proyectoId;

        MvcResult error = mockMvc.perform(post("/rm/proyectos/" + proyectoId + "/fechas").param("origen", "revision")
                        .param("fechaInicio", HOY.minusDays(1).toString())
                        .param("fechaFin", HOY.plusDays(30).toString()))
                .andExpect(redirectedUrl(destino))
                .andExpect(flash().attribute("mensajeError", "La fecha de inicio no puede ser anterior a la fecha actual."))
                .andReturn();
        verificarMontos(mockMvc.perform(get(destino).flashAttrs(error.getFlashMap()))
                        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8),
                "<div class=\"alert alert-danger\">La fecha de inicio no puede ser anterior a la fecha actual.</div>");
        verificarFechas(HOY, HOY.plusDays(70));

        MvcResult exito = mockMvc.perform(post("/rm/proyectos/" + proyectoId + "/fechas").param("origen", "revision")
                        .param("fechaInicio", HOY.plusDays(3).toString())
                        .param("fechaFin", HOY.plusDays(40).toString()))
                .andExpect(redirectedUrl(destino))
                .andExpect(flash().attribute("mensajeExito", EXITO_FECHAS))
                .andReturn();
        verificarMontos(mockMvc.perform(get(destino).flashAttrs(exito.getFlashMap()))
                        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8),
                "<div class=\"alert alert-success\">" + EXITO_FECHAS + "</div>", fecha(HOY.plusDays(40)));
        verificarFechas(HOY.plusDays(3), HOY.plusDays(40));
        org.junit.jupiter.api.Assertions.assertEquals(EstadoProyecto.EN_REVISION,
                proyectoRepository.findById(proyectoId).orElseThrow().getEstado());
        org.junit.jupiter.api.Assertions.assertEquals(1, auditoriasDeFechas());
        org.junit.jupiter.api.Assertions.assertEquals(1, notificacionesDeFechas().size());
    }

    @Test
    void postDeFechasVuelveAlDetalleConMensaje() throws Exception {
        autenticarRm();
        fijarFechas(EstadoProyecto.ACTIVO, HOY, HOY.plusDays(70));
        String destino = "/rm/proyectos/detalle?id=" + proyectoId;

        MvcResult error = mockMvc.perform(post("/rm/proyectos/" + proyectoId + "/fechas")
                        .param("fechaInicio", HOY.plusDays(10).toString())
                        .param("fechaFin", HOY.plusDays(10).toString()))
                .andExpect(redirectedUrl(destino))
                .andExpect(flash().attribute("mensajeError", MENSAJE_FIN_POSTERIOR))
                .andReturn();
        verificarMontos(mockMvc.perform(get(destino).flashAttrs(error.getFlashMap()))
                        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8),
                "<div class=\"alert alert-danger\">" + MENSAJE_FIN_POSTERIOR + "</div>");
        mockMvc.perform(post("/rm/proyectos/" + proyectoId + "/fechas")
                        .param("fechaInicio", "01/10/2026").param("fechaFin", ""))
                .andExpect(redirectedUrl(destino))
                .andExpect(flash().attribute("mensajeError", "Las fechas deben tener el formato AAAA-MM-DD."));
        verificarFechas(HOY, HOY.plusDays(70));

        MvcResult exito = mockMvc.perform(post("/rm/proyectos/" + proyectoId + "/fechas")
                        .param("fechaInicio", HOY.plusDays(10).toString())
                        .param("fechaFin", HOY.plusDays(100).toString()))
                .andExpect(redirectedUrl(destino))
                .andExpect(flash().attribute("mensajeExito", EXITO_FECHAS))
                .andReturn();
        verificarMontos(mockMvc.perform(get(destino).flashAttrs(exito.getFlashMap()))
                        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8),
                "<div class=\"alert alert-success\">" + EXITO_FECHAS + "</div>", fecha(HOY.plusDays(100)));
        verificarFechas(HOY.plusDays(10), HOY.plusDays(100));
        org.junit.jupiter.api.Assertions.assertEquals(1, auditoriasDeFechas());
        org.junit.jupiter.api.Assertions.assertEquals(1, notificacionesDeFechas().size());
    }

    private String formularioDeFechas() throws Exception {
        String html = renderizar("/rm/proyectos/detalle?id=" + proyectoId);
        int inicio = html.indexOf("id=\"projectDatesForm\"");
        return html.substring(html.lastIndexOf("<form", inicio), html.indexOf("</form>", inicio))
                + html.substring(html.lastIndexOf("<script"));
    }

    // Un cambio inválido no guarda, no audita ni notifica.
    private void verificarFechasSinCambios(Class<? extends RuntimeException> tipo, java.time.LocalDate inicio,
                                           java.time.LocalDate fin, Long usuarioId, String mensaje) {
        Proyecto antes = proyectoRepository.findById(proyectoId).orElseThrow();
        long auditorias = auditoriasDeFechas();
        int notificaciones = notificacionesDeFechas().size();
        RuntimeException ex = org.junit.jupiter.api.Assertions.assertThrows(tipo,
                () -> revisionService.cambiarFechas(proyectoId, inicio, fin, usuarioId));
        org.junit.jupiter.api.Assertions.assertEquals(mensaje, ex.getMessage());
        verificarFechas(antes.getFechaInicio(), antes.getFechaFinEstimada());
        org.junit.jupiter.api.Assertions.assertEquals(auditorias, auditoriasDeFechas());
        org.junit.jupiter.api.Assertions.assertEquals(notificaciones, notificacionesDeFechas().size());
    }

    private void verificarFechas(java.time.LocalDate inicio, java.time.LocalDate fin) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(inicio, proyecto.getFechaInicio());
        org.junit.jupiter.api.Assertions.assertEquals(fin, proyecto.getFechaFinEstimada());
    }

    private void fijarFechas(EstadoProyecto estado, java.time.LocalDate inicio, java.time.LocalDate fin) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId).orElseThrow();
        proyecto.setEstado(estado);
        proyecto.setFechaInicio(inicio);
        proyecto.setFechaFinEstimada(fin);
        proyectoRepository.save(proyecto);
    }

    private void guardarActividad(String titulo, java.time.LocalDate fechaLimite) {
        Usuario pm = usuarioRepository.findById(pmId).orElseThrow();
        Actividad actividad = new Actividad();
        actividad.setProyecto(proyectoRepository.findById(proyectoId).orElseThrow());
        actividad.setColaborador(pm);
        actividad.setCreadoPor(pm);
        actividad.setTitulo(titulo);
        actividad.setHorasEstimadas(new BigDecimal("8.00"));
        actividad.setFechaLimite(fechaLimite);
        actividadRepository.save(actividad);
    }

    private long auditoriasDeFechas() {
        return logAuditoriaRepository.findAll().stream()
                .filter(log -> AUDITORIA_FECHAS.equals(log.getAccion()) && proyectoId.equals(log.getEntidadId()))
                .count();
    }

    private List<Notificacion> notificacionesDeFechas() {
        return notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(
                        usuarioRepository.findById(pmId).orElseThrow()).stream()
                .filter(n -> NOTIFICACION_FECHAS.equals(n.getTipo()) && proyectoId.equals(n.getEntidadId()))
                .toList();
    }

    // TASK-024: en EN_REVISION el detalle oculta el equipo y muestra los datos ingresados por el PM.
    private static final String[] BLOQUES_DE_EQUIPO = {
            "<div class=\"info-label\">Equipo activo</div>", "<div class=\"info-label\">Vacantes</div>",
            "<div class=\"info-label\">Pendientes del RM</div>", "Equipo del proyecto", "team-card"};

    @Test
    void detalleEnRevisionOcultaEquipoYMuestraLosDatosDelPm() throws Exception {
        autenticarRm();
        Proyecto proyecto = completarDatosDelPm();
        guardarRequisito("Java T024", NivelDominio.AVANZADO, 3);
        String html = renderizar("/rm/proyectos/detalle?id=" + proyectoId);

        verificarSinFormatoAnterior(html, BLOQUES_DE_EQUIPO);
        org.junit.jupiter.api.Assertions.assertEquals(fecha(proyecto.getFechaCreacion().toLocalDate()), valorDe(html, "Fecha de creación"));
        org.junit.jupiter.api.Assertions.assertEquals(fecha(HOY.plusDays(5)), valorDe(html, "Fecha de inicio"));
        org.junit.jupiter.api.Assertions.assertEquals(fecha(HOY.plusDays(70)), valorDe(html, "Fin estimado"));
        org.junit.jupiter.api.Assertions.assertEquals("Alta", valorDe(html, "Prioridad"));
        org.junit.jupiter.api.Assertions.assertEquals("4", valorDe(html, "Colaboradores requeridos"));
        org.junit.jupiter.api.Assertions.assertEquals("30.00 h", valorDe(html, "Horas semanales requeridas"));
        org.junit.jupiter.api.Assertions.assertEquals("S/ 15,000.50", textoDeId(html, "presupuestoSolicitadoRevision"));
        org.junit.jupiter.api.Assertions.assertEquals("Sin asignar", textoDeId(html, "presupuestoAsignadoRevision"));
        verificarMontos(html, "Justificación de prioridad:", "Validación de la consulta del Resource Manager.",
                "Justificación de presupuesto solicitado:", "Licencias y horas de consultoría.",
                "Java T024", "Nivel Avanzado", "3 persona(s)", "<div class=\"col-12\">");
        // TASK-021: el formulario de fechas sigue en el detalle en revisión.
        verificarMontos(html, "id=\"projectDatesForm\"", "name=\"origen\" value=\"detalle\"");
    }

    @Test
    void detalleEnRevisionMuestraSolicitadoYAsignadoPorSeparado() throws Exception {
        autenticarRm();
        completarDatosDelPm();
        fijarPresupuesto(EstadoProyecto.EN_REVISION, "12000");
        String html = renderizar("/rm/proyectos/detalle?id=" + proyectoId);

        org.junit.jupiter.api.Assertions.assertEquals("S/ 15,000.50", textoDeId(html, "presupuestoSolicitadoRevision"));
        org.junit.jupiter.api.Assertions.assertEquals("S/ 12,000.00", textoDeId(html, "presupuestoAsignadoRevision"));
        verificarSinFormatoAnterior(html, BLOQUES_DE_EQUIPO);
    }

    @Test
    void detalleActivoConservaTarjetaYMetricasDeEquipo() throws Exception {
        autenticarRm();
        prepararProyectoConCosto("12345");
        guardarAsignacion(EstadoAsignacion.ACTIVA, true, "5000.00", "37.5");
        String html = renderizar("/rm/proyectos/detalle?id=" + proyectoId);

        verificarMontos(html, BLOQUES_DE_EQUIPO);
        verificarMontos(html, "Colaborador Presupuesto", "<div class=\"col-lg-5\">", "Presupuesto asignado", "S/ 12,345.00");
        verificarSinFormatoAnterior(html, "presupuestoSolicitadoRevision", "presupuestoAsignadoRevision");
    }

    @Test
    void revisionNoMuestraVerDetalleYConservaSusAcciones() throws Exception {
        autenticarRm();
        completarDatosDelPm();
        fijarPresupuesto(EstadoProyecto.EN_REVISION, "12000");
        String html = renderizar("/rm/proyectos/revision?id=" + proyectoId);

        verificarSinFormatoAnterior(html, "Ver detalle", "/rm/proyectos/detalle?id=");
        verificarMontos(html, "Guardar presupuesto", "Usar presupuesto solicitado", "Aprobar proyecto",
                "id=\"budgetChangeModal\"", "id=\"projectDatesForm\"", "name=\"origen\" value=\"revision\"");
    }

    @Test
    void datosOpcionalesAusentesNoMuestranNull() throws Exception {
        autenticarRm();
        Proyecto proyecto = proyectoRepository.findById(proyectoId).orElseThrow();
        proyecto.setDescripcion(null);
        proyectoRepository.save(proyecto);

        String detalle = renderizar("/rm/proyectos/detalle?id=" + proyectoId);
        org.junit.jupiter.api.Assertions.assertEquals("Sin definir", valorDe(detalle, "Fecha de inicio"));
        org.junit.jupiter.api.Assertions.assertEquals("Sin definir", valorDe(detalle, "Fin estimado"));
        org.junit.jupiter.api.Assertions.assertEquals("No solicitado", textoDeId(detalle, "presupuestoSolicitadoRevision"));
        org.junit.jupiter.api.Assertions.assertEquals("Sin asignar", textoDeId(detalle, "presupuestoAsignadoRevision"));
        verificarMontos(detalle, "Sin descripción registrada.", "Justificación de presupuesto solicitado:",
                "Sin justificación registrada.", "No se registraron habilidades requeridas.");
        verificarSinNull(detalle);
        verificarSinNull(renderizar("/rm/proyectos/revision?id=" + proyectoId));
    }

    private Proyecto completarDatosDelPm() {
        Proyecto proyecto = proyectoRepository.findById(proyectoId).orElseThrow();
        proyecto.setFechaInicio(HOY.plusDays(5));
        proyecto.setFechaFinEstimada(HOY.plusDays(70));
        proyecto.setPrioridad(Prioridad.ALTA);
        proyecto.setPresupuestoSolicitado(new BigDecimal("15000.50"));
        proyecto.setJustificacionPresupuesto("Licencias y horas de consultoría.");
        proyecto.setColaboradoresRequeridos(4);
        proyecto.setHorasSemanalesRequeridas(new BigDecimal("30.00"));
        return proyectoRepository.save(proyecto);
    }

    private void guardarRequisito(String nombre, NivelDominio nivel, int cantidad) {
        CategoriaHabilidad categoria = categoriaHabilidadRepository.findByNombreIgnoreCase("Proyectos T024")
                .orElseGet(() -> {
                    CategoriaHabilidad nueva = new CategoriaHabilidad();
                    nueva.setNombre("Proyectos T024");
                    return categoriaHabilidadRepository.save(nueva);
                });
        Habilidad habilidad = habilidadRepository.findByNombreIgnoreCase(nombre).orElseGet(() -> {
            Habilidad nueva = new Habilidad();
            nueva.setNombre(nombre);
            nueva.setCategoria(categoria);
            return habilidadRepository.save(nueva);
        });
        ProyectoHabilidadRequerida requisito = new ProyectoHabilidadRequerida();
        requisito.setProyecto(proyectoRepository.findById(proyectoId).orElseThrow());
        requisito.setHabilidad(habilidad);
        requisito.setNivelRequerido(nivel);
        requisito.setCantidadPersonas(cantidad);
        habilidadRequeridaRepository.save(requisito);
    }

    // Texto del info-value que sigue a la etiqueta indicada.
    private static String valorDe(String html, String etiqueta) {
        int etiquetaInicio = html.indexOf("<div class=\"info-label\">" + etiqueta + "</div>");
        org.junit.jupiter.api.Assertions.assertTrue(etiquetaInicio >= 0, etiqueta);
        int valor = html.indexOf("class=\"info-value\"", etiquetaInicio);
        return html.substring(html.indexOf('>', valor) + 1, html.indexOf('<', valor)).trim();
    }

    private static String textoDeId(String html, String id) {
        int inicio = html.indexOf("id=\"" + id + "\"");
        org.junit.jupiter.api.Assertions.assertTrue(inicio >= 0, id);
        return html.substring(html.indexOf('>', inicio) + 1, html.indexOf('<', inicio)).trim();
    }

    // Revisa el texto visible (sin etiquetas ni scripts) para no depender de atributos.
    private static void verificarSinNull(String html) {
        String texto = html.replaceAll("(?s)<script.*?</script>", " ").replaceAll("(?s)<[^>]*>", " ");
        org.junit.jupiter.api.Assertions.assertFalse(java.util.regex.Pattern.compile("\\bnull\\b").matcher(texto).find(), texto);
    }

    private static String fecha(java.time.LocalDate valor) {
        return valor.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    // TASK-023: acciones del detalle según el estado y búsqueda directa de colaboradores.
    private static final String MENSAJE_NO_ASIGNABLE =
            "Solo se pueden buscar colaboradores para proyectos activos o en espera.";

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = EstadoProyecto.class, names = {"ACTIVO", "EN_ESPERA"})
    void detalleMuestraBuscarYVerAsignacionesEnEstadosAsignables(EstadoProyecto estado) throws Exception {
        fijarEstado(estado);
        String html = mockMvc.perform(get("/rm/proyectos/detalle").param("id", proyectoId.toString()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("proyectoAsignable", true))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        org.junit.jupiter.api.Assertions.assertTrue(html.contains(">Buscar colaborador</a>"), estado.name());
        org.junit.jupiter.api.Assertions.assertTrue(html.contains(">Ver asignaciones</a>"), estado.name());
        org.junit.jupiter.api.Assertions.assertEquals(1, contar(html,
                "href=\"/rm/proyectos/buscar-colaboradores?proyectoId=" + proyectoId + "\""));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = EstadoProyecto.class,
            names = {"EN_REVISION", "RECHAZADO", "CANCELADO", "FINALIZADO"})
    void detalleNoMuestraBuscarNiVerAsignacionesEnEstadosNoAsignables(EstadoProyecto estado) throws Exception {
        fijarEstado(estado);
        String html = mockMvc.perform(get("/rm/proyectos/detalle").param("id", proyectoId.toString()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("proyectoAsignable", false))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        org.junit.jupiter.api.Assertions.assertFalse(html.contains("Buscar colaborador"), estado.name());
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("Ver asignaciones"), estado.name());
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("projectSearchLink"), estado.name());
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("projectAssignmentsLink"), estado.name());
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("/rm/proyectos/buscar-colaboradores"), estado.name());
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("/rm/asignaciones?proyectoId="), estado.name());
    }

    @Test
    void verAsignacionesEnviaElProyectoIdDelDetalleYFiltraLaBandeja() throws Exception {
        fijarEstado(EstadoProyecto.ACTIVO);
        Proyecto otro = new Proyecto();
        otro.setNombre("Otro proyecto RM");
        otro.setPrioridad(Prioridad.BAJA);
        otro.setJustificacionPrioridad("Segundo proyecto para comparar enlaces.");
        otro.setEstado(EstadoProyecto.ACTIVO);
        otro.setPm(usuarioRepository.findById(pmId).orElseThrow());
        Long otroId = proyectoRepository.save(otro).getId();

        String html = renderizar("/rm/proyectos/detalle?id=" + proyectoId);
        String enlace = "href=\"/rm/asignaciones?proyectoId=" + proyectoId + "\"";
        org.junit.jupiter.api.Assertions.assertEquals(1, contar(html, enlace));
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("/rm/asignaciones?proyectoId=" + otroId + "\""));
        int inicio = html.indexOf("id=\"projectAssignmentsLink\"");
        org.junit.jupiter.api.Assertions.assertTrue(inicio >= 0);
        org.junit.jupiter.api.Assertions.assertTrue(
                html.substring(inicio, html.indexOf('>', inicio)).contains(enlace));

        // El enlace usa el filtro por proyecto de TASK-027.
        mockMvc.perform(get("/rm/asignaciones").param("proyectoId", proyectoId.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-asignaciones"))
                .andExpect(model().attribute("proyectoId", proyectoId))
                .andExpect(model().attribute("proyectoNombre", "Proyecto de prueba RM"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = EstadoProyecto.class, names = {"ACTIVO", "EN_ESPERA"})
    void busquedaDirectaFuncionaEnEstadosAsignables(EstadoProyecto estado) throws Exception {
        autenticarRm();
        fijarEstado(estado);
        mockMvc.perform(get("/rm/proyectos/buscar-colaboradores").param("proyectoId", proyectoId.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-buscar-colaboradores-proyecto"))
                .andExpect(model().attributeExists("proyecto", "candidatos"))
                .andExpect(flash().attributeCount(0));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = EstadoProyecto.class,
            names = {"EN_REVISION", "RECHAZADO", "CANCELADO", "FINALIZADO"})
    void busquedaDirectaEnEstadoNoAsignableRedirigeAlDetalleConMensaje(EstadoProyecto estado) throws Exception {
        autenticarRm();
        fijarEstado(estado);
        MvcResult resultado = mockMvc.perform(get("/rm/proyectos/buscar-colaboradores")
                        .param("proyectoId", proyectoId.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rm/proyectos/detalle?id=" + proyectoId))
                .andExpect(model().attributeDoesNotExist("candidatos"))
                .andReturn();
        String mensaje = (String) resultado.getFlashMap().get("mensajeError");
        org.junit.jupiter.api.Assertions.assertNotNull(mensaje, estado.name());
        org.junit.jupiter.api.Assertions.assertTrue(mensaje.startsWith(MENSAJE_NO_ASIGNABLE), mensaje);
        verificarSinNull(mensaje);

        // El detalle al que vuelve muestra el mensaje.
        String html = mockMvc.perform(get("/rm/proyectos/detalle").param("id", proyectoId.toString())
                        .flashAttrs(resultado.getFlashMap()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertTrue(html.contains(MENSAJE_NO_ASIGNABLE));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"abc", "12x", "-", "999999999", "1.5"})
    void busquedaDirectaConProyectoInvalidoOInexistenteVuelveAlListado(String valor) throws Exception {
        autenticarRm();
        mockMvc.perform(get("/rm/proyectos/buscar-colaboradores").param("proyectoId", valor))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rm/proyectos?noEncontrado=true"));
    }

    @Test
    void busquedaDirectaSinProyectoVuelveAlListado() throws Exception {
        autenticarRm();
        mockMvc.perform(get("/rm/proyectos/buscar-colaboradores"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rm/proyectos"));
        mockMvc.perform(get("/rm/proyectos/buscar-colaboradores").param("proyectoId", "  "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rm/proyectos"));
    }

    @Test
    void ocultarAccionesYBloquearBusquedaNoModificaAsignaciones() throws Exception {
        autenticarRm();
        fijarEstado(EstadoProyecto.ACTIVO);
        guardarAsignacion(EstadoAsignacion.PENDIENTE, false);
        guardarAsignacion(EstadoAsignacion.ACTIVA, true);
        List<String> antes = estadoDeAsignaciones();
        long auditoriasAntes = logAuditoriaRepository.count();
        long notificacionesAntes = notificacionRepository.count();

        for (EstadoProyecto estado : EstadoProyecto.values()) {
            fijarEstado(estado);
            renderizar("/rm/proyectos/detalle?id=" + proyectoId);
            mockMvc.perform(get("/rm/proyectos/buscar-colaboradores").param("proyectoId", proyectoId.toString()));
            org.junit.jupiter.api.Assertions.assertEquals(estado,
                    proyectoRepository.findById(proyectoId).orElseThrow().getEstado());
        }
        mockMvc.perform(get("/rm/proyectos/buscar-colaboradores").param("proyectoId", "abc"));

        org.junit.jupiter.api.Assertions.assertEquals(antes, estadoDeAsignaciones());
        org.junit.jupiter.api.Assertions.assertEquals(auditoriasAntes, logAuditoriaRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(notificacionesAntes, notificacionRepository.count());
    }

    private void fijarEstado(EstadoProyecto estado) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId).orElseThrow();
        proyecto.setEstado(estado);
        proyectoRepository.save(proyecto);
    }

    private List<String> estadoDeAsignaciones() {
        return asignacionRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(Asignacion::getId))
                .map(a -> a.getId() + ":" + a.getEstado() + ":" + a.isAprobadoPorPm() + ":"
                        + a.isAprobadoPorRm() + ":" + a.getHorasSemanales())
                .toList();
    }

    private static int contar(String html, String texto) {
        return html.split(java.util.regex.Pattern.quote(texto), -1).length - 1;
    }

    private void activarProyecto() {
        Proyecto proyecto = proyectoRepository.findById(proyectoId).orElseThrow();
        proyecto.setEstado(EstadoProyecto.ACTIVO);
        proyectoRepository.save(proyecto);
    }

    private void autenticarRm() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(rmDetails, null, rmDetails.getAuthorities()));
    }

    // TASK-028: filtros GET y paginación del listado /rm/proyectos en el servidor.
    // Lote de 10 proyectos con un token único; "Análisis {token} NN", NN = 01..10 (10 es el más reciente).
    // {estado, prioridad, colaboradores requeridos (= vacantes, sin equipo activo)}
    private static final String[][] LOTE = {
            {"ACTIVO", "ALTA", "2"}, {"ACTIVO", "ALTA", "2"}, {"ACTIVO", "ALTA", "2"}, {"ACTIVO", "ALTA", "2"},
            {"ACTIVO", "ALTA", "2"}, {"ACTIVO", "ALTA", "2"}, {"ACTIVO", "ALTA", "2"},
            {"EN_ESPERA", "BAJA", "0"}, {"EN_REVISION", "MEDIA", "0"}, {"FINALIZADO", "MEDIA", "3"}};

    @Autowired private com.pucp.skillb_ia.service.rm.RmProyectoConsultaService rmProyectoConsultaService;
    private final List<Long> loteIds = new java.util.ArrayList<>();
    private Long lotePmId;

    @AfterEach
    void borrarLote() {
        loteIds.forEach(proyectoRepository::deleteById);
        loteIds.clear();
        if (lotePmId != null) usuarioRepository.deleteById(lotePmId);
        lotePmId = null;
    }

    /** Crea el lote con fechas de creación antiguas (no desplaza a los demás proyectos) y devuelve el token. */
    private String crearLote() {
        String token = "lote" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Usuario pm = new Usuario();
        pm.setCorreo(token + "@skillbridge.test");
        pm.setNombre("Gestora");
        pm.setApellido(token + "pm");
        pm.setRol(rolRepository.findByNombre("PROJECT_MANAGER").orElseThrow());
        pm = usuarioRepository.save(pm);
        lotePmId = pm.getId();
        for (int i = 0; i < LOTE.length; i++) {
            Proyecto proyecto = new Proyecto();
            proyecto.setNombre(String.format("Análisis %s %02d", token, i + 1));
            proyecto.setEstado(EstadoProyecto.valueOf(LOTE[i][0]));
            proyecto.setPrioridad(Prioridad.valueOf(LOTE[i][1]));
            proyecto.setJustificacionPrioridad("Lote de TASK-028.");
            proyecto.setColaboradoresRequeridos(Integer.parseInt(LOTE[i][2]));
            proyecto.setFechaCreacion(java.time.LocalDateTime.of(2001, 1, 1, 0, 0).plusDays(i));
            proyecto.setPm(pm);
            loteIds.add(proyectoRepository.save(proyecto).getId());
        }
        return token;
    }

    private MvcResult listarProyectos(String consulta) throws Exception {
        return mockMvc.perform(get(java.net.URI.create("/rm/proyectos?" + consulta)))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-proyectos"))
                .andReturn();
    }

    @SuppressWarnings("unchecked")
    private static List<com.pucp.skillb_ia.dto.RmProyectoView> filas(MvcResult resultado) {
        return (List<com.pucp.skillb_ia.dto.RmProyectoView>) resultado.getModelAndView().getModel().get("proyectos");
    }

    private static Object atributo(MvcResult resultado, String nombre) {
        return resultado.getModelAndView().getModel().get(nombre);
    }

    /** Sufijos NN de las filas del lote, en el orden renderizado. */
    private static List<String> numeros(MvcResult resultado) {
        return filas(resultado).stream()
                .map(item -> item.getProyecto().getNombre())
                .map(nombre -> nombre.substring(nombre.length() - 2))
                .toList();
    }

    private static String html(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void listadoPaginaDeSeisEnSeisConservandoElOrdenPorFechaDeCreacion() throws Exception {
        String token = crearLote();

        MvcResult pagina1 = listarProyectos("busqueda=" + token);
        org.junit.jupiter.api.Assertions.assertEquals(List.of("10", "09", "08", "07", "06", "05"), numeros(pagina1));
        org.junit.jupiter.api.Assertions.assertEquals(1, atributo(pagina1, "paginaActual"));
        org.junit.jupiter.api.Assertions.assertEquals(2, atributo(pagina1, "totalPaginas"));
        org.junit.jupiter.api.Assertions.assertEquals(10L, atributo(pagina1, "totalRegistros"));
        org.junit.jupiter.api.Assertions.assertEquals(6, contar(html(pagina1), "project-item"));
        org.junit.jupiter.api.Assertions.assertTrue(html(pagina1).contains("Mostrando 1-6 de 10 proyectos"));
        // Los filtros sin valor viajan vacíos y el servidor los normaliza a "Todos".
        org.junit.jupiter.api.Assertions.assertTrue(html(pagina1).contains("href=\"/rm/proyectos?busqueda=" + token
                + "&amp;estado=&amp;prioridad=&amp;vacantes=&amp;pagina=2\""));

        MvcResult pagina2 = listarProyectos("busqueda=" + token + "&pagina=2");
        org.junit.jupiter.api.Assertions.assertEquals(List.of("04", "03", "02", "01"), numeros(pagina2));
        org.junit.jupiter.api.Assertions.assertEquals(2, atributo(pagina2, "paginaActual"));
        org.junit.jupiter.api.Assertions.assertEquals(4, contar(html(pagina2), "project-item"));
        org.junit.jupiter.api.Assertions.assertTrue(html(pagina2).contains("Mostrando 7-10 de 10 proyectos"));

        // Sin filtros, el orden es el mismo de listar() (fecha de creación descendente) y la página tiene 6.
        MvcResult sinFiltros = listarProyectos("");
        List<Long> esperados = rmProyectoConsultaService.listar().stream()
                .limit(6).map(item -> item.getProyecto().getId()).toList();
        org.junit.jupiter.api.Assertions.assertEquals(esperados,
                filas(sinFiltros).stream().map(item -> item.getProyecto().getId()).toList());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "estado,    ACTIVO",
            "estado,    FINALIZADO",
            "estado,    EN_REVISION",
            "prioridad, ALTA",
            "prioridad, BAJA",
            "vacantes,  with",
            "vacantes,  full"})
    void cadaFiltroGetPorSeparadoAplicaElCriterioDelListado(String parametro, String valor) throws Exception {
        crearLote();
        java.util.function.Predicate<com.pucp.skillb_ia.dto.RmProyectoView> criterio = switch (parametro) {
            case "estado" -> item -> item.getProyecto().getEstado().name().equals(valor);
            case "prioridad" -> item -> item.getProyecto().getPrioridad().name().equals(valor);
            default -> item -> "with".equals(valor) ? item.getVacantes() > 0 : item.getVacantes() == 0;
        };
        List<Long> esperados = rmProyectoConsultaService.listar().stream()
                .filter(criterio).map(item -> item.getProyecto().getId()).toList();

        MvcResult resultado = listarProyectos(parametro + "=" + valor);

        org.junit.jupiter.api.Assertions.assertEquals(valor, atributo(resultado, parametro));
        org.junit.jupiter.api.Assertions.assertEquals((long) esperados.size(), atributo(resultado, "totalRegistros"));
        org.junit.jupiter.api.Assertions.assertEquals(esperados.stream().limit(6).toList(),
                filas(resultado).stream().map(item -> item.getProyecto().getId()).toList());
        org.junit.jupiter.api.Assertions.assertTrue(html(resultado)
                .contains("value=\"" + valor + "\" selected=\"selected\""), valor);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', value = {
            // "with" cuenta vacantes > 0 en cualquier estado (FINALIZADO incluido), como el JS anterior.
            "estado=ACTIVO&prioridad=ALTA&vacantes=with | 07,06,05,04,03,02",
            "estado=FINALIZADO&vacantes=with            | 10",
            "vacantes=full                              | 09,08",
            "prioridad=MEDIA&estado=EN_REVISION         | 09",
            "estado=ACTIVO&prioridad=BAJA               | ''"})
    void combinacionDeFiltrosGet(String consulta, String esperados) throws Exception {
        String token = crearLote();
        MvcResult resultado = listarProyectos("busqueda=" + token + "&" + consulta);
        List<String> lista = esperados.isEmpty() ? List.of() : List.of(esperados.split(","));
        org.junit.jupiter.api.Assertions.assertEquals(lista, numeros(resultado));
        org.junit.jupiter.api.Assertions.assertEquals(lista.isEmpty(),
                html(resultado).contains("No se encontraron proyectos con los filtros seleccionados."));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "{t}", "{T}", "ANÁLISIS {T}", "análisis {t}", "  Análisis {t}  ", "GESTORA {T}PM", "gestora {t}pm"})
    void busquedaIgnoraMayusculasSobreNombreYProjectManager(String patron) throws Exception {
        String token = crearLote();
        String busqueda = patron.replace("{t}", token).replace("{T}", token.toUpperCase(java.util.Locale.ROOT));
        MvcResult resultado = listarProyectos("busqueda=" + java.net.URLEncoder.encode(busqueda, StandardCharsets.UTF_8).replace("+", "%20"));
        org.junit.jupiter.api.Assertions.assertEquals(10L, atributo(resultado, "totalRegistros"), busqueda);
        org.junit.jupiter.api.Assertions.assertEquals(busqueda.trim(), atributo(resultado, "busqueda"));
    }

    @Test
    void busquedaConservaLasTildesComoAntes() throws Exception {
        // El JS anterior solo pasaba a minúsculas (toLocaleLowerCase("es")); "analisis" no encuentra "Análisis".
        String token = crearLote();
        MvcResult resultado = listarProyectos("busqueda=analisis%20" + token);
        org.junit.jupiter.api.Assertions.assertEquals(0L, atributo(resultado, "totalRegistros"));
        org.junit.jupiter.api.Assertions.assertTrue(
                html(resultado).contains("No se encontraron proyectos con los filtros seleccionados."));
    }

    @Test
    void enlacesDePaginacionConservanTodosLosFiltros() throws Exception {
        String token = crearLote();
        String html = html(listarProyectos("busqueda=" + token + "&estado=ACTIVO&prioridad=ALTA&vacantes=with"));

        List<String> enlaces = java.util.regex.Pattern.compile("href=\"(/rm/proyectos\\?[^\"]*pagina=[^\"]*)\"")
                .matcher(html).results().map(m -> m.group(1).replace("&amp;", "&")).toList();
        org.junit.jupiter.api.Assertions.assertEquals(4, enlaces.size(), enlaces.toString()); // Anterior, 1, 2, Siguiente
        for (String enlace : enlaces) {
            org.junit.jupiter.api.Assertions.assertTrue(enlace.contains("busqueda=" + token), enlace);
            org.junit.jupiter.api.Assertions.assertTrue(enlace.contains("estado=ACTIVO"), enlace);
            org.junit.jupiter.api.Assertions.assertTrue(enlace.contains("prioridad=ALTA"), enlace);
            org.junit.jupiter.api.Assertions.assertTrue(enlace.contains("vacantes=with"), enlace);
        }
        String siguiente = enlaces.get(enlaces.size() - 1);
        org.junit.jupiter.api.Assertions.assertTrue(siguiente.endsWith("pagina=2"), siguiente);

        MvcResult pagina2 = listarProyectos(siguiente.substring(siguiente.indexOf('?') + 1));
        org.junit.jupiter.api.Assertions.assertEquals(List.of("01"), numeros(pagina2));
        org.junit.jupiter.api.Assertions.assertEquals("ACTIVO", atributo(pagina2, "estado"));
        org.junit.jupiter.api.Assertions.assertEquals("with", atributo(pagina2, "vacantes"));
    }

    @Test
    void contadoresSeCalculanSobreTodosLosProyectosYNoSobreLaPagina() throws Exception {
        String token = crearLote();
        List<com.pucp.skillb_ia.dto.RmProyectoView> todos = rmProyectoConsultaService.listar();
        long total = proyectoRepository.count();
        long enRevision = proyectoRepository.findAll().stream()
                .filter(p -> p.getEstado() == EstadoProyecto.EN_REVISION).count();
        long conVacantes = todos.stream().filter(com.pucp.skillb_ia.dto.RmProyectoView::isConVacantesParaDotacion).count();
        long pendientesRm = todos.stream().mapToLong(com.pucp.skillb_ia.dto.RmProyectoView::getPendientesRm).sum();

        for (String consulta : List.of("", "busqueda=" + token + "&pagina=2", "busqueda=" + token + "&vacantes=full")) {
            MvcResult resultado = listarProyectos(consulta);
            org.junit.jupiter.api.Assertions.assertEquals(total, atributo(resultado, "totalProyectos"), consulta);
            org.junit.jupiter.api.Assertions.assertEquals(enRevision, atributo(resultado, "totalEnRevision"), consulta);
            org.junit.jupiter.api.Assertions.assertEquals(conVacantes, atributo(resultado, "totalConVacantes"), consulta);
            org.junit.jupiter.api.Assertions.assertEquals(pendientesRm, atributo(resultado, "totalPendientesRm"), consulta);
            org.junit.jupiter.api.Assertions.assertTrue(filas(resultado).size() <= 6);
            org.junit.jupiter.api.Assertions.assertTrue(html(resultado).contains("id=\"metricTotal\">" + total + "</div>"));
        }
        org.junit.jupiter.api.Assertions.assertTrue(total > 10);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', value = {
            "pagina=abc       | 1", "pagina=0         | 1", "pagina=-3        | 1", "pagina=          | 1",
            "pagina=1.5       | 1", "pagina=999       | 2",
            "estado=XYZ       | 1", "estado=          | 1", "estado=all       | 1",
            "prioridad=URGENTE| 1", "prioridad=       | 1", "prioridad=all    | 1",
            "vacantes=otro    | 1", "vacantes=        | 1", "vacantes=all     | 1"})
    void parametrosVaciosOInvalidosVuelvenAValoresSeguros(String consulta, int paginaEsperada) throws Exception {
        String token = crearLote();
        MvcResult resultado = listarProyectos("busqueda=" + token + "&" + consulta);
        org.junit.jupiter.api.Assertions.assertEquals(paginaEsperada, atributo(resultado, "paginaActual"));
        org.junit.jupiter.api.Assertions.assertEquals(10L, atributo(resultado, "totalRegistros"));
        org.junit.jupiter.api.Assertions.assertNull(atributo(resultado, "estado"));
        org.junit.jupiter.api.Assertions.assertNull(atributo(resultado, "prioridad"));
        org.junit.jupiter.api.Assertions.assertNull(atributo(resultado, "vacantes"));
    }

    @Test
    void busquedaEnBlancoMuestraTodosLosProyectos() throws Exception {
        MvcResult resultado = listarProyectos("busqueda=%20%20%20");
        org.junit.jupiter.api.Assertions.assertNull(atributo(resultado, "busqueda"));
        org.junit.jupiter.api.Assertions.assertEquals(atributo(resultado, "totalProyectos"),
                atributo(resultado, "totalRegistros"));
    }

    @Test
    void listadoFuncionaSinJavaScript() throws Exception {
        String token = crearLote();
        String html = html(listarProyectos("busqueda=" + token + "&estado=ACTIVO"));

        org.junit.jupiter.api.Assertions.assertTrue(html.contains(
                "<form id=\"filtersForm\" method=\"get\" class=\"card-body border-bottom\" action=\"/rm/proyectos\">")
                || html.contains("<form id=\"filtersForm\" method=\"get\" action=\"/rm/proyectos\""), "formulario GET");
        for (String campo : List.of("busqueda", "estado", "prioridad", "vacantes")) {
            org.junit.jupiter.api.Assertions.assertTrue(html.contains("name=\"" + campo + "\""), campo);
        }
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("value=\"" + token + "\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("value=\"ACTIVO\" selected=\"selected\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("type=\"submit\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("id=\"clearFilters\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("href=\"/rm/proyectos\">"));
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("rm-proyectos.js"));
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("data-search="));
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("href=\"#\" class=\"page-link\""));
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("noProjectResults"));
        // Solo se renderizan los 6 proyectos de la página, visibles sin que JS los muestre u oculte.
        org.junit.jupiter.api.Assertions.assertEquals(6, contar(html, "class=\"col-lg-6 project-item\""));
        org.junit.jupiter.api.Assertions.assertEquals(0, contar(html, "project-item d-none"));
    }

    // ===== TASK-031: estados vacíos con el fragmento común (fragments/rm-empty-state) =====

    @Autowired private SpringTemplateEngine templateEngine;

    @Test
    void listadoSinProyectosRegistradosMuestraElEstadoVacioSinAcciones() {
        // El H2 compartido siempre tiene proyectos: el controlador real recibe un servicio simulado sin datos.
        RmProyectoConsultaService consulta = mock(RmProyectoConsultaService.class);
        when(consulta.normalizarFiltros(any(), any(), any(), any()))
                .thenReturn(new RmProyectoConsultaService.FiltrosProyecto("", "", "", ""));
        when(consulta.listarPagina(any(), any())).thenReturn(new RmProyectoConsultaService.PaginaProyectos(
                List.of(), 1, 1, 0, new RmProyectoConsultaService.ContadoresProyectos(0, 0, 0, 0)));
        ConcurrentModel modelo = new ConcurrentModel();
        org.junit.jupiter.api.Assertions.assertEquals("rm/rm-proyectos",
                controladorCon(consulta).projects(null, null, null, null, null, null, modelo));
        String html = renderizarVista("/rm/proyectos", "rm/rm-proyectos", modelo);

        org.junit.jupiter.api.Assertions.assertEquals(1, contar(html, "class=\"empty-state\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("<span class=\"empty-state-icon\" aria-hidden=\"true\">"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("<div class=\"empty-state-title\">No hay proyectos registrados</div>"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains(
                "<div class=\"empty-state-text\">Los proyectos que registren los Project Managers aparecerán aquí.</div>"));
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("noProjectResults"));
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("empty-state-action"), "El RM no crea proyectos");
    }

    @Test
    void listadoSinResultadosFiltradosOfreceLimpiarFiltros() throws Exception {
        String html = renderizar("/rm/proyectos?busqueda=sin-coincidencias-t031&estado=ACTIVO");

        org.junit.jupiter.api.Assertions.assertTrue(html.contains("id=\"noProjectResults\""));
        org.junit.jupiter.api.Assertions.assertEquals(1, contar(html, "class=\"empty-state\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("<div class=\"empty-state-title\">Sin resultados para los filtros</div>"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("No se encontraron proyectos con los filtros seleccionados."));
        // Mismo destino que el botón "Limpiar filtros" del formulario.
        org.junit.jupiter.api.Assertions.assertTrue(html.contains(
                "<a class=\"btn btn-outline-primary btn-sm\" href=\"/rm/proyectos\">Limpiar filtros</a>"));
        org.junit.jupiter.api.Assertions.assertFalse(html.contains("No hay proyectos registrados"));
        // Filtros y paginación se conservan.
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("value=\"sin-coincidencias-t031\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("value=\"ACTIVO\" selected=\"selected\""));
    }

    @Test
    void detalleYRevisionUsanElEstadoVacioComunSinCambiarSusCondiciones() throws Exception {
        String revision = renderizar("/rm/proyectos/revision?id=" + proyectoId);
        org.junit.jupiter.api.Assertions.assertEquals(1, contar(revision, "class=\"empty-state\""));
        org.junit.jupiter.api.Assertions.assertTrue(revision.contains("<div class=\"empty-state-title\">Sin habilidades requeridas</div>"));
        org.junit.jupiter.api.Assertions.assertTrue(revision.contains("No hay habilidades requeridas registradas."));

        activarProyecto();
        String detalle = renderizar("/rm/proyectos/detalle?id=" + proyectoId);
        org.junit.jupiter.api.Assertions.assertEquals(2, contar(detalle, "class=\"empty-state\""));
        org.junit.jupiter.api.Assertions.assertTrue(detalle.contains("<div class=\"empty-state-title\">Sin integrantes activos</div>"));
        org.junit.jupiter.api.Assertions.assertTrue(detalle.contains("El proyecto todavía no tiene integrantes activos."));
        org.junit.jupiter.api.Assertions.assertTrue(detalle.contains("<div class=\"empty-state-title\">Sin habilidades requeridas</div>"));
        org.junit.jupiter.api.Assertions.assertTrue(detalle.contains("No se registraron habilidades requeridas."));
        org.junit.jupiter.api.Assertions.assertFalse(detalle.contains("empty-state-action"));

        // Con un requisito, la condición deja de mostrar ese estado vacío en ambas vistas.
        guardarRequisito("Requisito T031", NivelDominio.INTERMEDIO, 1);
        String detalleConRequisito = renderizar("/rm/proyectos/detalle?id=" + proyectoId);
        org.junit.jupiter.api.Assertions.assertEquals(1, contar(detalleConRequisito, "class=\"empty-state\""));
        org.junit.jupiter.api.Assertions.assertFalse(detalleConRequisito.contains("No se registraron habilidades requeridas."));
        fijarEstado(EstadoProyecto.EN_REVISION);
        org.junit.jupiter.api.Assertions.assertEquals(0,
                contar(renderizar("/rm/proyectos/revision?id=" + proyectoId), "class=\"empty-state\""));
    }

    private RmViewController controladorCon(RmProyectoConsultaService consulta) {
        return new RmViewController(
                mock(RmPerfilService.class), mock(RmColaboradorConsultaService.class), consulta,
                mock(RmProyectoRevisionService.class), mock(RmAsignacionService.class),
                mock(RmSolicitudPersonalService.class), mock(RmCertificadoService.class),
                mock(RmEducacionService.class), mock(RmForoConsultaService.class), mock(RmReporteService.class),
                mock(RmReporteExportService.class), mock(RmCursoService.class), mock(RmPresupuestoService.class),
                mock(EvaluacionService.class));
    }

    private String renderizarVista(String ruta, String plantilla, ConcurrentModel modelo) {
        MockHttpServletRequest request = new MockHttpServletRequest(context.getServletContext(), "GET", ruta);
        var intercambio = JakartaServletWebApplication.buildApplication(context.getServletContext())
                .buildExchange(request, new MockHttpServletResponse());
        return templateEngine.process(plantilla, new WebContext(intercambio, Locale.forLanguageTag("es"), modelo.asMap()));
    }
}
