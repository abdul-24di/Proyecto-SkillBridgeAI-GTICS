package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.LogAuditoria;
import com.pucp.skillb_ia.model.Notificacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.MotivoFinalizacion;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.model.enums.Prioridad;
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
import com.pucp.skillb_ia.service.pm.PmProyectoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.containsString;
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

// TASK-041: al cancelar un proyecto se cierran sus asignaciones abiertas en la misma transacción.
@SpringBootTest
@ActiveProfiles("test")
class PmProyectoCancelacionTests {

    @Autowired private WebApplicationContext context;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private NotificacionRepository notificacionRepository;
    @Autowired private LogAuditoriaRepository logAuditoriaRepository;
    @Autowired private PenalizacionRepository penalizacionRepository;
    @Autowired private PmProyectoService pmProyectoService;
    @Autowired private JdbcTemplate jdbcTemplate;
    // Espía para simular un fallo a mitad de la cancelación (prueba de rollback).
    @MockitoSpyBean private NotificacionService notificacionService;

    private MockMvc mockMvc;
    private Usuario pm;
    private Usuario rm;
    private Usuario colActivo;      // ACTIVA de 10 h en el proyecto y ACTIVA de 5 h en otro
    private Usuario colPendiente;   // PENDIENTE en el proyecto
    private Proyecto proyecto;
    private Proyecto otroProyecto;
    private Asignacion activa;
    private Asignacion pendiente;
    private Asignacion activaOtroProyecto;

    @BeforeEach
    void prepararDatos() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        pm = obtenerUsuario("pm.cancelacion@skillbridge.test", "Paula", "PM", obtenerRol("PROJECT_MANAGER"));
        rm = obtenerUsuario("rm.cancelacion@skillbridge.test", "Raul", "RM", obtenerRol("RESOURCE_MANAGER"));
        Rol rolColaborador = obtenerRol("COLABORADOR");
        colActivo = colaborador("col.activo.cancelacion@skillbridge.test", "Ana", rolColaborador);
        colPendiente = colaborador("col.pendiente.cancelacion@skillbridge.test", "Beto", rolColaborador);

        proyecto = proyecto(EstadoProyecto.EN_ESPERA);
        otroProyecto = proyecto(EstadoProyecto.ACTIVO);

        activa = asignacion(proyecto, colActivo, EstadoAsignacion.ACTIVA, "10");
        pendiente = asignacion(proyecto, colPendiente, EstadoAsignacion.PENDIENTE, "8");
        activaOtroProyecto = asignacion(otroProyecto, colActivo, EstadoAsignacion.ACTIVA, "5");
        // Disponibilidad inicial coherente: 40 - (10 + 5) = 25 h.
        colActivo.setHorasDisponibles(new BigDecimal("25.00"));
        colActivo = usuarioRepository.save(colActivo);
    }

    @Test
    void pendientesQuedanRechazadasConMotivoYFecha() {
        LocalDateTime antes = LocalDateTime.now().minusSeconds(1);

        pmProyectoService.cancelar(proyecto.getId(), pm);

        Asignacion rechazada = asignacionRepository.findById(pendiente.getId()).orElseThrow();
        assertEquals(EstadoAsignacion.RECHAZADA, rechazada.getEstado());
        assertEquals("Proyecto cancelado", rechazada.getMotivoRechazo());
        assertEquals(pm.getId(), rechazada.getRechazadoPor().getId());
        assertNotNull(rechazada.getFechaFinalizacion(), "Debe registrar la fecha de la decisión.");
        assertTrue(rechazada.getFechaFinalizacion().isAfter(antes));
        assertNull(rechazada.getMotivoFinalizacion());
        assertEquals(EstadoProyecto.CANCELADO,
                proyectoRepository.findById(proyecto.getId()).orElseThrow().getEstado());
    }

    @Test
    void activasQuedanFinalizadasConMotivoFechaYResponsable() {
        LocalDateTime antes = LocalDateTime.now().minusSeconds(1);

        pmProyectoService.cancelar(proyecto.getId(), pm);

        Asignacion finalizada = asignacionRepository.findById(activa.getId()).orElseThrow();
        assertEquals(EstadoAsignacion.FINALIZADA, finalizada.getEstado());
        assertEquals(MotivoFinalizacion.PROYECTO_CANCELADO, finalizada.getMotivoFinalizacion());
        assertNotNull(finalizada.getFechaFinalizacion());
        assertTrue(finalizada.getFechaFinalizacion().isAfter(antes));
        assertEquals(pm.getId(), finalizada.getDesasignadoPor().getId());
        // La activa de otro proyecto no se toca.
        assertEquals(EstadoAsignacion.ACTIVA,
                asignacionRepository.findById(activaOtroProyecto.getId()).orElseThrow().getEstado());
    }

    @Test
    void recalculaDisponibilidadYLiberaHoras() {
        pmProyectoService.cancelar(proyecto.getId(), pm);

        // Solo queda comprometida la activa de 5 h del otro proyecto: 40 - 5 = 35.
        Usuario actualizado = usuarioRepository.findById(colActivo.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("35").compareTo(actualizado.getHorasDisponibles()));
        // La pendiente no comprometía horas: 40 - 0 = 40.
        Usuario conPendiente = usuarioRepository.findById(colPendiente.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("40").compareTo(conPendiente.getHorasDisponibles()));
    }

    @Test
    void noCreaStrikesNiPenalizaciones() {
        int antesActivo = penalizacionRepository.findByColaborador(colActivo).size();
        int antesPendiente = penalizacionRepository.findByColaborador(colPendiente).size();

        pmProyectoService.cancelar(proyecto.getId(), pm);

        assertEquals(antesActivo, penalizacionRepository.findByColaborador(colActivo).size());
        assertEquals(antesPendiente, penalizacionRepository.findByColaborador(colPendiente).size());
    }

    @Test
    void cadaColaboradorAfectadoRecibeSuNotificacion() {
        pmProyectoService.cancelar(proyecto.getId(), pm);

        List<Notificacion> delActivo = notificacionesDe(colActivo, activa.getId());
        assertEquals(1, delActivo.size());
        assertEquals("ASIGNACION_FINALIZADA", delActivo.get(0).getTipo());
        assertTrue(delActivo.get(0).getDescripcion().contains(proyecto.getNombre()));

        List<Notificacion> delPendiente = notificacionesDe(colPendiente, pendiente.getId());
        assertEquals(1, delPendiente.size());
        assertEquals("ASIGNACION_RECHAZADA", delPendiente.get(0).getTipo());
        assertTrue(delPendiente.get(0).getDescripcion().contains("Proyecto cancelado"));

        // La asignación del otro proyecto no genera notificación.
        assertTrue(notificacionesDe(colActivo, activaOtroProyecto.getId()).isEmpty());
    }

    @Test
    void pmQueCancelaNoRecibeNotificacionNiLosRm() {
        int antesPm = notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(pm).size();
        int antesRm = notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(rm).size();

        pmProyectoService.cancelar(proyecto.getId(), pm);

        assertEquals(antesPm, notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(pm).size());
        assertEquals(antesRm, notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(rm).size());
    }

    @Test
    void auditoriaRegistraAmbosConteos() {
        pmProyectoService.cancelar(proyecto.getId(), pm);

        List<LogAuditoria> logs = auditoriasCancelacion(proyecto);
        assertEquals(1, logs.size());
        LogAuditoria log = logs.get(0);
        assertEquals(pm.getId(), log.getUsuario().getId());
        assertTrue(log.getDetalle().contains("Asignaciones pendientes rechazadas: 1"), log.getDetalle());
        assertTrue(log.getDetalle().contains("Asignaciones activas finalizadas: 1"), log.getDetalle());
    }

    @Test
    void resultadoYMensajeContienenLosConteos() throws Exception {
        // Una segunda pendiente para que X e Y sean distintos.
        Usuario tercero = colaborador("col.tercero.cancelacion@skillbridge.test", "Ciro",
                obtenerRol("COLABORADOR"));
        asignacion(proyecto, tercero, EstadoAsignacion.PENDIENTE, "6");
        String esperado = "Proyecto cancelado. Se rechazaron 2 asignaciones pendientes"
                + " y se finalizaron 1 asignaciones activas.";

        autenticar(pm);
        try {
            mockMvc.perform(post("/pm/proyectos/" + proyecto.getId() + "/cancelar"))
                    .andExpect(redirectedUrl("/pm/proyectos"))
                    .andExpect(flash().attribute("success", esperado));
            // El listado muestra el mensaje.
            mockMvc.perform(get("/pm/proyectos").flashAttr("success", esperado))
                    .andExpect(content().string(containsString(esperado)));
        } finally {
            SecurityContextHolder.clearContext();
        }

        Proyecto vacio = proyecto(EstadoProyecto.EN_REVISION);
        CierreAsignacionesService.Resultado sinAsignaciones = pmProyectoService.cancelar(vacio.getId(), pm);
        assertEquals(0, sinAsignaciones.pendientesRechazadas());
        assertEquals(0, sinAsignaciones.activasFinalizadas());
    }

    @Test
    void noSeBorraNingunaAsignacion() {
        Asignacion historica = asignacion(proyecto, colPendiente, EstadoAsignacion.RECHAZADA, "4");
        Set<Long> antes = idsDe(proyecto);

        pmProyectoService.cancelar(proyecto.getId(), pm);

        assertEquals(antes, idsDe(proyecto));
        assertEquals(EstadoAsignacion.RECHAZADA,
                asignacionRepository.findById(historica.getId()).orElseThrow().getEstado());
    }

    @Test
    void conservaEstadosPermitidosYPropietario() {
        Usuario otroPm = obtenerUsuario("pm2.cancelacion@skillbridge.test", "Pia", "PM",
                obtenerRol("PROJECT_MANAGER"));
        assertThrows(SecurityException.class, () -> pmProyectoService.cancelar(proyecto.getId(), otroPm));
        // ACTIVO no es un estado cancelable para el PM.
        assertThrows(IllegalStateException.class, () -> pmProyectoService.cancelar(otroProyecto.getId(), pm));

        // Nada cambió en ninguno de los dos intentos.
        assertEquals(EstadoAsignacion.ACTIVA, asignacionRepository.findById(activa.getId()).orElseThrow().getEstado());
        assertEquals(EstadoAsignacion.ACTIVA,
                asignacionRepository.findById(activaOtroProyecto.getId()).orElseThrow().getEstado());
        assertEquals(EstadoProyecto.EN_ESPERA, proyectoRepository.findById(proyecto.getId()).orElseThrow().getEstado());
    }

    @Test
    void siFallaUnaOperacionSeRevierteTodaLaCancelacion() {
        int notificacionesAntes = notificacionesDe(colPendiente, pendiente.getId()).size();
        doThrow(new IllegalStateException("Fallo simulado al notificar."))
                .when(notificacionService).crear(any(), eq("ASIGNACION_FINALIZADA"), any(),
                        anyString(), anyString(), anyString(), any());

        assertThrows(IllegalStateException.class, () -> pmProyectoService.cancelar(proyecto.getId(), pm));

        assertEquals(EstadoProyecto.EN_ESPERA, proyectoRepository.findById(proyecto.getId()).orElseThrow().getEstado());
        Asignacion sigueActiva = asignacionRepository.findById(activa.getId()).orElseThrow();
        assertEquals(EstadoAsignacion.ACTIVA, sigueActiva.getEstado());
        assertNull(sigueActiva.getMotivoFinalizacion());
        assertNull(sigueActiva.getFechaFinalizacion());
        Asignacion siguePendiente = asignacionRepository.findById(pendiente.getId()).orElseThrow();
        assertEquals(EstadoAsignacion.PENDIENTE, siguePendiente.getEstado());
        assertNull(siguePendiente.getMotivoRechazo());
        assertEquals(0, new BigDecimal("25").compareTo(
                usuarioRepository.findById(colActivo.getId()).orElseThrow().getHorasDisponibles()));
        assertEquals(notificacionesAntes, notificacionesDe(colPendiente, pendiente.getId()).size());
        assertTrue(auditoriasCancelacion(proyecto).isEmpty());
    }

    // Riesgo UNIQUE (TASK-047): con uq_asignacion_abierta instalada en H2, cerrar una asignación
    // cuando ya existe una RECHAZADA/FINALIZADA del mismo colaborador y proyecto no debe fallar.
    @Test
    void riesgoUniquePendienteConRechazadaPreviaDelMismoColaborador() {
        Asignacion rechazadaPrevia = asignacion(proyecto, colPendiente, EstadoAsignacion.RECHAZADA, "4");
        conRestriccionDeAsignacionAbierta(() -> {
            CierreAsignacionesService.Resultado resultado = pmProyectoService.cancelar(proyecto.getId(), pm);

            assertEquals(1, resultado.pendientesRechazadas());
            assertEquals(EstadoAsignacion.RECHAZADA,
                    asignacionRepository.findById(pendiente.getId()).orElseThrow().getEstado());
            assertEquals(EstadoAsignacion.RECHAZADA,
                    asignacionRepository.findById(rechazadaPrevia.getId()).orElseThrow().getEstado());
        });
    }

    @Test
    void riesgoUniqueActivaConFinalizadaPreviaDelMismoColaborador() {
        Asignacion finalizadaPrevia = asignacion(proyecto, colActivo, EstadoAsignacion.FINALIZADA, "4");
        conRestriccionDeAsignacionAbierta(() -> {
            CierreAsignacionesService.Resultado resultado = pmProyectoService.cancelar(proyecto.getId(), pm);

            assertEquals(1, resultado.activasFinalizadas());
            assertEquals(EstadoAsignacion.FINALIZADA,
                    asignacionRepository.findById(activa.getId()).orElseThrow().getEstado());
            assertEquals(EstadoAsignacion.FINALIZADA,
                    asignacionRepository.findById(finalizadaPrevia.getId()).orElseThrow().getEstado());
        });
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    // El perfil test genera el esquema desde las entidades (sin la restricción): se instala la de
    // skillbridge_db_v4.sql (sin STORED, que H2 no admite) solo durante la prueba.
    private void conRestriccionDeAsignacionAbierta(Runnable prueba) {
        jdbcTemplate.execute("ALTER TABLE asignacion ADD COLUMN estado_abierto VARCHAR(30) GENERATED ALWAYS AS "
                + "(CASE WHEN estado IN ('PENDIENTE','ACTIVA') THEN estado END)");
        jdbcTemplate.execute("ALTER TABLE asignacion ADD CONSTRAINT uq_asignacion_abierta "
                + "UNIQUE (proyecto_id, colaborador_id, estado_abierto)");
        try {
            // Control: la restricción está activa (no admite dos PENDIENTE iguales).
            assertThrows(DataIntegrityViolationException.class,
                    () -> asignacion(proyecto, colPendiente, EstadoAsignacion.PENDIENTE, "2"));
            prueba.run();
        } finally {
            jdbcTemplate.execute("ALTER TABLE asignacion DROP CONSTRAINT uq_asignacion_abierta");
            jdbcTemplate.execute("ALTER TABLE asignacion DROP COLUMN estado_abierto");
        }
    }

    private List<Notificacion> notificacionesDe(Usuario usuario, Long asignacionId) {
        return notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(usuario).stream()
                .filter(n -> "ASIGNACION".equals(n.getEntidad()) && asignacionId.equals(n.getEntidadId()))
                .toList();
    }

    private List<LogAuditoria> auditoriasCancelacion(Proyecto destino) {
        return logAuditoriaRepository.findAll().stream()
                .filter(l -> "CANCELAR".equals(l.getAccion()) && "PROYECTO".equals(l.getEntidad())
                        && destino.getId().equals(l.getEntidadId()))
                .toList();
    }

    private Set<Long> idsDe(Proyecto destino) {
        return asignacionRepository.findByProyecto(destino).stream()
                .map(Asignacion::getId).collect(Collectors.toSet());
    }

    private void autenticar(Usuario usuario) {
        // findActivosByRolNombre trae el rol cargado, necesario para las autoridades.
        UsuarioDetails details = new UsuarioDetails(usuarioRepository.findActivosByRolNombre("PROJECT_MANAGER")
                .stream().filter(u -> u.getId().equals(usuario.getId())).findFirst().orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    private Proyecto proyecto(EstadoProyecto estado) {
        Proyecto nuevo = new Proyecto();
        nuevo.setNombre("Proyecto cancelacion " + System.nanoTime());
        nuevo.setDescripcion("Proyecto para probar la cancelación del PM.");
        nuevo.setEstado(estado);
        nuevo.setPrioridad(Prioridad.MEDIA);
        nuevo.setJustificacionPrioridad("Prueba de cancelación.");
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

    // Correo único por prueba: cada colaborador empieza sin asignaciones de pruebas anteriores.
    private Usuario colaborador(String correo, String nombre, Rol rol) {
        Usuario usuario = obtenerUsuario(System.nanoTime() + "." + correo, nombre, "Colaborador", rol);
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
