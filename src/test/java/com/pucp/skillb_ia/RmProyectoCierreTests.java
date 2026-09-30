package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.LogAuditoria;
import com.pucp.skillb_ia.model.Notificacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.MotivoFinalizacion;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.ActividadRepository;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.LogAuditoriaRepository;
import com.pucp.skillb_ia.repository.NotificacionRepository;
import com.pucp.skillb_ia.repository.PenalizacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.CierreAsignacionesService;
import com.pucp.skillb_ia.service.NotificacionService;
import com.pucp.skillb_ia.service.rm.RmProyectoRevisionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

// Cancelar y finalizar un proyecto ACTIVO o EN_ESPERA desde la zona de riesgo del detalle del RM.
@SpringBootTest
@ActiveProfiles("test")
class RmProyectoCierreTests {

    private static final String MOTIVO = "El cliente retiró el financiamiento.";

    @Autowired private WebApplicationContext context;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private ActividadRepository actividadRepository;
    @Autowired private NotificacionRepository notificacionRepository;
    @Autowired private LogAuditoriaRepository logAuditoriaRepository;
    @Autowired private PenalizacionRepository penalizacionRepository;
    @Autowired private RmProyectoRevisionService service;
    // Espía para simular un fallo a mitad del cierre (prueba de rollback).
    @MockitoSpyBean private NotificacionService notificacionService;

    private MockMvc mockMvc;
    private Usuario rm;
    private Usuario pm;
    private Usuario colActivo;
    private Usuario colPendiente;
    private Proyecto proyecto;
    private Asignacion activa;
    private Asignacion pendiente;

    @BeforeEach
    void prepararDatos() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        rm = obtenerUsuario("rm.cierre@skillbridge.test", "Rita", "RM", obtenerRol("RESOURCE_MANAGER"));
        pm = obtenerUsuario("pm.cierre@skillbridge.test", "Pablo", "PM", obtenerRol("PROJECT_MANAGER"));
        colActivo = colaborador("activo.cierre@skillbridge.test", "Ana");
        colPendiente = colaborador("pendiente.cierre@skillbridge.test", "Beto");

        proyecto = proyecto(EstadoProyecto.ACTIVO);
        activa = asignacion(proyecto, colActivo, EstadoAsignacion.ACTIVA, "10");
        pendiente = asignacion(proyecto, colPendiente, EstadoAsignacion.PENDIENTE, "8");
        colActivo.setHorasDisponibles(new BigDecimal("30.00"));
        colActivo = usuarioRepository.save(colActivo);
    }

    // ── servicio: cancelar ───────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(value = EstadoProyecto.class, names = {"ACTIVO", "EN_ESPERA"})
    void rmCancelaProyectoCerrableYCierraSusAsignaciones(EstadoProyecto estado) {
        cambiarEstado(estado);

        CierreAsignacionesService.Resultado resultado =
                service.cancelar(proyecto.getId(), MOTIVO, proyecto.getNombre(), rm.getId());

        assertEquals(1, resultado.pendientesRechazadas());
        assertEquals(1, resultado.activasFinalizadas());
        assertEquals(EstadoProyecto.CANCELADO, estadoActual());
        Asignacion finalizada = asignacionRepository.findById(activa.getId()).orElseThrow();
        assertEquals(EstadoAsignacion.FINALIZADA, finalizada.getEstado());
        assertEquals(MotivoFinalizacion.PROYECTO_CANCELADO, finalizada.getMotivoFinalizacion());
        assertEquals(rm.getId(), finalizada.getDesasignadoPor().getId());
        assertNotNull(finalizada.getFechaFinalizacion());
        Asignacion rechazada = asignacionRepository.findById(pendiente.getId()).orElseThrow();
        assertEquals(EstadoAsignacion.RECHAZADA, rechazada.getEstado());
        assertEquals("Proyecto cancelado", rechazada.getMotivoRechazo());
        assertEquals(rm.getId(), rechazada.getRechazadoPor().getId());
        // 40 contratadas, sin otras activas.
        assertEquals(0, new BigDecimal("40").compareTo(horasDisponibles(colActivo)));

        Notificacion alPm = unicaNotificacionDeProyecto(pm, "PROYECTO_CANCELADO");
        assertTrue(alPm.getDescripcion().contains(MOTIVO));
        assertEquals(1, notificacionesDeAsignacion(colActivo, activa.getId()).size());
        assertEquals(1, notificacionesDeAsignacion(colPendiente, pendiente.getId()).size());
        // El RM que cierra no recibe notificación del proyecto.
        assertTrue(notificacionesDeProyecto(rm).isEmpty());

        LogAuditoria log = unicaAuditoria("CANCELAR_PROYECTO");
        assertEquals(rm.getId(), log.getUsuario().getId());
        assertEquals(estado.name(), log.getValorAnterior());
        assertEquals("CANCELADO", log.getValorNuevo());
        assertTrue(log.getDetalle().contains("Asignaciones pendientes rechazadas: 1"), log.getDetalle());
        assertTrue(log.getDetalle().contains("Asignaciones activas finalizadas: 1"), log.getDetalle());
        assertTrue(log.getDetalle().contains(MOTIVO), log.getDetalle());
    }

    @Test
    void cancelarNoCreaPenalizacionesNiBorraAsignaciones() {
        int antes = penalizacionRepository.findByColaborador(colActivo).size();
        int filas = asignacionRepository.findByProyecto(proyecto).size();

        service.cancelar(proyecto.getId(), MOTIVO, proyecto.getNombre(), rm.getId());

        assertEquals(antes, penalizacionRepository.findByColaborador(colActivo).size());
        assertEquals(filas, asignacionRepository.findByProyecto(proyecto).size());
    }

    // ── servicio: finalizar ──────────────────────────────────────────────────

    @Test
    void rmFinalizaProyectoConActividadesCompletadas() {
        actividad(EstadoActividad.COMPLETADA);

        CierreAsignacionesService.Resultado resultado =
                service.finalizar(proyecto.getId(), MOTIVO, proyecto.getNombre(), rm.getId());

        assertEquals(1, resultado.pendientesRechazadas());
        assertEquals(1, resultado.activasFinalizadas());
        assertEquals(EstadoProyecto.FINALIZADO, estadoActual());
        Asignacion finalizada = asignacionRepository.findById(activa.getId()).orElseThrow();
        assertEquals(EstadoAsignacion.FINALIZADA, finalizada.getEstado());
        assertEquals(MotivoFinalizacion.OTRO, finalizada.getMotivoFinalizacion());
        assertEquals("Proyecto finalizado",
                asignacionRepository.findById(pendiente.getId()).orElseThrow().getMotivoRechazo());
        assertEquals(0, new BigDecimal("40").compareTo(horasDisponibles(colActivo)));
        assertTrue(unicaNotificacionDeProyecto(pm, "PROYECTO_FINALIZADO").getDescripcion().contains(MOTIVO));
        LogAuditoria log = unicaAuditoria("FINALIZAR_PROYECTO");
        assertEquals("FINALIZADO", log.getValorNuevo());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoActividad.class, names = {"PENDIENTE", "EN_PROGRESO", "EN_REVISION"})
    void finalizarSeBloqueaConActividadesSinCompletar(EstadoActividad estadoActividad) {
        actividad(estadoActividad);
        actividad(EstadoActividad.COMPLETADA);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.finalizar(proyecto.getId(), MOTIVO, proyecto.getNombre(), rm.getId()));

        assertTrue(ex.getMessage().contains("1 actividad(es)"), ex.getMessage());
        assertSinCambios();
    }

    // ── servicio: validaciones comunes ───────────────────────────────────────

    @ParameterizedTest
    @EnumSource(value = EstadoProyecto.class, names = {"EN_REVISION", "RECHAZADO", "CANCELADO", "FINALIZADO"})
    void soloSeCierranProyectosActivosOEnEspera(EstadoProyecto estado) {
        cambiarEstado(estado);

        assertThrows(IllegalStateException.class,
                () -> service.cancelar(proyecto.getId(), MOTIVO, proyecto.getNombre(), rm.getId()));
        assertThrows(IllegalStateException.class,
                () -> service.finalizar(proyecto.getId(), MOTIVO, proyecto.getNombre(), rm.getId()));
        assertEquals(estado, estadoActual());
        assertEquals(EstadoAsignacion.ACTIVA, asignacionRepository.findById(activa.getId()).orElseThrow().getEstado());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void motivoObligatorio(String motivo) {
        assertThrows(IllegalArgumentException.class,
                () -> service.cancelar(proyecto.getId(), motivo, proyecto.getNombre(), rm.getId()));
        assertThrows(IllegalArgumentException.class,
                () -> service.cancelar(proyecto.getId(), null, proyecto.getNombre(), rm.getId()));
        assertThrows(IllegalArgumentException.class,
                () -> service.finalizar(proyecto.getId(), motivo, proyecto.getNombre(), rm.getId()));
        assertSinCambios();
    }

    @Test
    void motivoDeHasta500Caracteres() {
        assertThrows(IllegalArgumentException.class,
                () -> service.cancelar(proyecto.getId(), "a".repeat(501), proyecto.getNombre(), rm.getId()));
        assertSinCambios();

        service.cancelar(proyecto.getId(), "a".repeat(500), proyecto.getNombre(), rm.getId());
        assertEquals(EstadoProyecto.CANCELADO, estadoActual());
        // La descripción de la notificación se recorta a 400 (columna) y la auditoría a 500.
        assertTrue(unicaNotificacionDeProyecto(pm, "PROYECTO_CANCELADO").getDescripcion().length() <= 400);
        assertTrue(unicaAuditoria("CANCELAR_PROYECTO").getDetalle().length() <= 500);
    }

    @Test
    void exigeElNombreExactoDelProyecto() {
        for (String confirmacion : new String[]{null, "", "otro proyecto", proyecto.getNombre().toUpperCase()}) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.cancelar(proyecto.getId(), MOTIVO, confirmacion, rm.getId()));
            assertThrows(IllegalArgumentException.class,
                    () -> service.finalizar(proyecto.getId(), MOTIVO, confirmacion, rm.getId()));
        }
        assertSinCambios();
        // Los espacios alrededor se ignoran.
        service.cancelar(proyecto.getId(), MOTIVO, "  " + proyecto.getNombre() + " ", rm.getId());
        assertEquals(EstadoProyecto.CANCELADO, estadoActual());
    }

    @Test
    void soloUnRmActivoPuedeCerrar() {
        assertThrows(IllegalArgumentException.class,
                () -> service.cancelar(proyecto.getId(), MOTIVO, proyecto.getNombre(), pm.getId()));
        assertThrows(IllegalArgumentException.class,
                () -> service.finalizar(proyecto.getId(), MOTIVO, proyecto.getNombre(), null));
        assertSinCambios();
    }

    @Test
    void siFallaUnaOperacionSeRevierteTodo() {
        doThrow(new IllegalStateException("Fallo simulado al notificar."))
                .when(notificacionService).crear(any(), eq("PROYECTO_CANCELADO"), any(),
                        anyString(), anyString(), anyString(), any());

        assertThrows(IllegalStateException.class,
                () -> service.cancelar(proyecto.getId(), MOTIVO, proyecto.getNombre(), rm.getId()));

        assertSinCambios();
        assertEquals(0, new BigDecimal("30").compareTo(horasDisponibles(colActivo)));
        assertTrue(notificacionesDeAsignacion(colActivo, activa.getId()).isEmpty());
    }

    // ── controlador y vista ──────────────────────────────────────────────────

    @Test
    void postCancelarYFinalizarVuelvenAlDetalleConMensaje() throws Exception {
        String detalle = "/rm/proyectos/detalle?id=" + proyecto.getId();
        autenticarRm();
        try {
            mockMvc.perform(post("/rm/proyectos/" + proyecto.getId() + "/cancelar")
                            .param("motivo", MOTIVO).param("confirmacionNombre", "nombre equivocado"))
                    .andExpect(redirectedUrl(detalle))
                    .andExpect(flash().attribute("mensajeError",
                            "Para confirmar, escribe el nombre exacto del proyecto. No se realizó ningún cambio."));
            assertSinCambios();

            mockMvc.perform(post("/rm/proyectos/" + proyecto.getId() + "/cancelar")
                            .param("motivo", MOTIVO).param("confirmacionNombre", proyecto.getNombre()))
                    .andExpect(redirectedUrl(detalle))
                    .andExpect(flash().attribute("mensajeExito", "Proyecto cancelado. Se rechazaron 1 asignaciones"
                            + " pendientes y se finalizaron 1 asignaciones activas."));

            Proyecto otro = proyecto(EstadoProyecto.EN_ESPERA);
            mockMvc.perform(post("/rm/proyectos/" + otro.getId() + "/finalizar")
                            .param("motivo", MOTIVO).param("confirmacionNombre", otro.getNombre()))
                    .andExpect(redirectedUrl("/rm/proyectos/detalle?id=" + otro.getId()))
                    .andExpect(flash().attribute("mensajeExito", "Proyecto finalizado. Se rechazaron 0 asignaciones"
                            + " pendientes y se finalizaron 0 asignaciones activas."));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProyecto.class, names = {"ACTIVO", "EN_ESPERA"})
    void detalleMuestraLaZonaDeRiesgoEnEstadosCerrables(EstadoProyecto estado) throws Exception {
        cambiarEstado(estado);
        mockMvc.perform(get("/rm/proyectos/detalle").param("id", proyecto.getId().toString()))
                .andExpect(content().string(containsString("id=\"projectDangerZone\"")))
                .andExpect(content().string(containsString("Zona de riesgo")))
                .andExpect(content().string(containsString("action=\"/rm/proyectos/" + proyecto.getId() + "/cancelar\"")))
                .andExpect(content().string(containsString("action=\"/rm/proyectos/" + proyecto.getId() + "/finalizar\"")))
                .andExpect(content().string(containsString("name=\"confirmacionNombre\"")))
                .andExpect(content().string(containsString("data-nombre=\"" + proyecto.getNombre() + "\"")))
                .andExpect(content().string(containsString("rm-proyecto-cierre.js")))
                .andExpect(content().string(not(containsString("id=\"finishProjectBlocked\""))))
                .andExpect(content().string(not(containsString("disabled=\"disabled\">Finalizar proyecto</button>"))));
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProyecto.class, names = {"EN_REVISION", "RECHAZADO", "CANCELADO", "FINALIZADO"})
    void detalleSinZonaDeRiesgoEnOtrosEstados(EstadoProyecto estado) throws Exception {
        cambiarEstado(estado);
        mockMvc.perform(get("/rm/proyectos/detalle").param("id", proyecto.getId().toString()))
                .andExpect(content().string(not(containsString("projectDangerZone"))))
                .andExpect(content().string(not(containsString("/cancelar\""))))
                .andExpect(content().string(not(containsString("/finalizar\""))))
                .andExpect(content().string(not(containsString("rm-proyecto-cierre.js"))));
    }

    @Test
    void detalleDeshabilitaFinalizarConActividadesAbiertas() throws Exception {
        actividad(EstadoActividad.EN_PROGRESO);
        mockMvc.perform(get("/rm/proyectos/detalle").param("id", proyecto.getId().toString()))
                .andExpect(content().string(containsString("id=\"finishProjectBlocked\"")))
                .andExpect(content().string(containsString("No disponible: 1 actividad(es)")))
                .andExpect(content().string(containsString("disabled=\"disabled\">Finalizar proyecto</button>")));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void assertSinCambios() {
        assertNotEquals(EstadoProyecto.CANCELADO, estadoActual());
        assertNotEquals(EstadoProyecto.FINALIZADO, estadoActual());
        assertEquals(EstadoAsignacion.ACTIVA, asignacionRepository.findById(activa.getId()).orElseThrow().getEstado());
        assertEquals(EstadoAsignacion.PENDIENTE,
                asignacionRepository.findById(pendiente.getId()).orElseThrow().getEstado());
        assertTrue(auditorias("CANCELAR_PROYECTO").isEmpty());
        assertTrue(auditorias("FINALIZAR_PROYECTO").isEmpty());
        assertTrue(notificacionesDeProyecto(pm).isEmpty());
    }

    private EstadoProyecto estadoActual() {
        return proyectoRepository.findById(proyecto.getId()).orElseThrow().getEstado();
    }

    private void cambiarEstado(EstadoProyecto estado) {
        proyecto.setEstado(estado);
        proyecto = proyectoRepository.save(proyecto);
    }

    private BigDecimal horasDisponibles(Usuario usuario) {
        return usuarioRepository.findById(usuario.getId()).orElseThrow().getHorasDisponibles();
    }

    private List<Notificacion> notificacionesDeProyecto(Usuario usuario) {
        return notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(usuario).stream()
                .filter(n -> "PROYECTO".equals(n.getEntidad()) && proyecto.getId().equals(n.getEntidadId()))
                .toList();
    }

    private Notificacion unicaNotificacionDeProyecto(Usuario usuario, String tipo) {
        List<Notificacion> lista = notificacionesDeProyecto(usuario);
        assertEquals(1, lista.size());
        assertEquals(tipo, lista.get(0).getTipo());
        return lista.get(0);
    }

    private List<Notificacion> notificacionesDeAsignacion(Usuario usuario, Long asignacionId) {
        return notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(usuario).stream()
                .filter(n -> "ASIGNACION".equals(n.getEntidad()) && asignacionId.equals(n.getEntidadId()))
                .toList();
    }

    private List<LogAuditoria> auditorias(String accion) {
        return logAuditoriaRepository.findAll().stream()
                .filter(l -> accion.equals(l.getAccion()) && proyecto.getId().equals(l.getEntidadId()))
                .toList();
    }

    private LogAuditoria unicaAuditoria(String accion) {
        List<LogAuditoria> lista = auditorias(accion);
        assertEquals(1, lista.size());
        return lista.get(0);
    }

    private void autenticarRm() {
        UsuarioDetails details = new UsuarioDetails(usuarioRepository.findActivosByRolNombre("RESOURCE_MANAGER")
                .stream().filter(u -> u.getId().equals(rm.getId())).findFirst().orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    private Proyecto proyecto(EstadoProyecto estado) {
        Proyecto nuevo = new Proyecto();
        nuevo.setNombre("Proyecto cierre " + System.nanoTime());
        nuevo.setDescripcion("Proyecto para probar el cierre desde el RM.");
        nuevo.setEstado(estado);
        nuevo.setPrioridad(Prioridad.MEDIA);
        nuevo.setJustificacionPrioridad("Prueba de cierre.");
        nuevo.setColaboradoresRequeridos(3);
        nuevo.setPm(pm);
        nuevo.setPresupuesto(new BigDecimal("50000.00"));
        nuevo.setFechaInicio(LocalDate.now());
        nuevo.setFechaFinEstimada(LocalDate.now().plusMonths(2));
        return proyectoRepository.save(nuevo);
    }

    private Asignacion asignacion(Proyecto destino, Usuario persona, EstadoAsignacion estado, String horas) {
        Asignacion nueva = new Asignacion();
        nueva.setProyecto(destino);
        nueva.setColaborador(persona);
        nueva.setHorasSemanales(new BigDecimal(horas));
        nueva.setOrigen(OrigenAsignacion.PROPUESTA_RM);
        nueva.setEstado(estado);
        nueva.setAprobadoPorRm(true);
        nueva.setAprobadoPorPm(estado != EstadoAsignacion.PENDIENTE);
        if (estado == EstadoAsignacion.ACTIVA) nueva.setFechaActivacion(LocalDateTime.now());
        return asignacionRepository.save(nueva);
    }

    private void actividad(EstadoActividad estado) {
        Actividad actividad = new Actividad();
        actividad.setProyecto(proyecto);
        actividad.setColaborador(colActivo);
        actividad.setCreadoPor(pm);
        actividad.setTitulo("Actividad " + estado);
        actividad.setHorasEstimadas(new BigDecimal("4.00"));
        actividad.setFechaLimite(LocalDate.now().plusDays(7));
        actividad.setEstado(estado);
        actividadRepository.save(actividad);
    }

    // Correo único por prueba: cada colaborador empieza sin asignaciones de pruebas anteriores.
    private Usuario colaborador(String correo, String nombre) {
        Usuario usuario = obtenerUsuario(System.nanoTime() + "." + correo, nombre, "Colaborador",
                obtenerRol("COLABORADOR"));
        usuario.setHorasContratadasSemana(new BigDecimal("40.00"));
        usuario.setHorasDisponibles(new BigDecimal("40.00"));
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
