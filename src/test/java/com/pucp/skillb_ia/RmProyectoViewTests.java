package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.LogAuditoria;
import com.pucp.skillb_ia.model.Notificacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.repository.ActividadRepository;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.LogAuditoriaRepository;
import com.pucp.skillb_ia.repository.NotificacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

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

    @Test
    void renderizaDetallePorId() throws Exception {
        mockMvc.perform(get("/rm/proyectos/detalle").param("id", proyectoId.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-detalle-proyecto"))
                .andExpect(model().attributeExists("proyecto"));
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
        String html = renderizar("/rm/proyectos/buscar-colaboradores?proyectoId=" + proyectoId);
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

    private static String fecha(java.time.LocalDate valor) {
        return valor.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
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
}
