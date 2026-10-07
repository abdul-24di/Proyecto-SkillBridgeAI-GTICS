package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.ColaboradorCurso;
import com.pucp.skillb_ia.model.Curso;
import com.pucp.skillb_ia.model.LogAuditoria;
import com.pucp.skillb_ia.model.Notificacion;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoColaboradorCurso;
import com.pucp.skillb_ia.model.enums.OrigenCurso;
import com.pucp.skillb_ia.repository.ColaboradorCursoRepository;
import com.pucp.skillb_ia.repository.CursoRepository;
import com.pucp.skillb_ia.repository.LogAuditoriaRepository;
import com.pucp.skillb_ia.repository.NotificacionRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.ArchivoAlmacenamientoService;
import com.pucp.skillb_ia.service.EmailService;
import com.pucp.skillb_ia.service.NotificacionService;
import com.pucp.skillb_ia.service.col.ColaboradorActividadService;
import com.pucp.skillb_ia.service.col.ColaboradorCursoService;
import com.pucp.skillb_ia.service.rm.RmCursoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

// TASK-057: flujo de cursos y evidencias desde el lado del colaborador.
@SpringBootTest
@ActiveProfiles("test")
class ColaboradorCursoEvidenciaTests {
    private static final String URL_SUBIR = "/colaborador/perfil/cursos/{id}/evidencia";

    @Autowired private WebApplicationContext context;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CursoRepository cursoRepository;
    @Autowired private ColaboradorCursoRepository colaboradorCursoRepository;
    @Autowired private NotificacionRepository notificacionRepository;
    @Autowired private LogAuditoriaRepository logAuditoriaRepository;
    @Autowired private ColaboradorCursoService colaboradorCursoService;
    @Autowired private ColaboradorActividadService colaboradorActividadService;
    @Autowired private RmCursoService rmCursoService;
    @Autowired private NotificacionService notificacionService;
    @Autowired private TransactionTemplate transactionTemplate;
    // Simulados: las pruebas no escriben archivos reales ni tocan SMTP.
    @MockitoBean private ArchivoAlmacenamientoService archivoAlmacenamientoService;
    @MockitoBean private EmailService emailService;

    private MockMvc mockMvc;
    private Usuario admin;
    private Usuario rm;
    private Usuario rmRevisor;
    private Usuario colaborador;
    private Usuario otroColaborador;
    private Curso curso;

    @BeforeEach
    void prepararDatos() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        notificacionRepository.deleteAll();
        colaboradorCursoRepository.deleteAll();
        cursoRepository.deleteAll();

        admin = usuario("admin.colevid@skillbridge.test", "Alba", "Admin", rol("ADMIN"));
        rm = usuario("rm.colevid@skillbridge.test", "Rosa", "Mendoza", rol("RESOURCE_MANAGER"));
        rmRevisor = usuario("rm2.colevid@skillbridge.test", "Raúl", "Vega", rol("RESOURCE_MANAGER"));
        colaborador = usuario("carla.colevid@skillbridge.test", "Carla", "Quispe", rol("COLABORADOR"));
        otroColaborador = usuario("mario.colevid@skillbridge.test", "Mario", "Paz", rol("COLABORADOR"));
        curso = curso("Docker esencial evid test", "20.00", null);

        when(archivoAlmacenamientoService.guardar(any(), anyString(), anyString()))
                .thenAnswer(inv -> "/simulado/" + inv.getArgument(1) + "/" + inv.getArgument(2));
    }

    @AfterEach
    void limpiarSesion() {
        SecurityContextHolder.clearContext();
    }

    // 1. Solo el dueño de la inscripción puede subir evidencia (servicio y ruta HTTP).
    @Test
    void soloElDuenoDeLaInscripcionPuedeSubirEvidencia() throws Exception {
        ColaboradorCurso inscripcion = enCurso(colaborador, curso);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> colaboradorCursoService.subirEvidencia(otroColaborador, inscripcion.getId(), pdf()));
        assertEquals("No se encontró esa inscripción a un curso.", error.getMessage());

        autenticar(otroColaborador);
        mockMvc.perform(multipart(URL_SUBIR, inscripcion.getId()).file(pdf()))
                .andExpect(redirectedUrl("/colaborador/perfil"))
                .andExpect(flash().attribute("mensajeError", "No se encontró esa inscripción a un curso."));

        ColaboradorCurso sinCambios = colaboradorCursoRepository.findById(inscripcion.getId()).orElseThrow();
        assertEquals(EstadoColaboradorCurso.EN_CURSO, sinCambios.getEstado());
        assertNull(sinCambios.getEvidenciaUrl());
        assertTrue(auditorias("SUBIR_EVIDENCIA_CURSO", inscripcion.getId()).isEmpty());
        assertEquals(0, notificacionRepository.count());
        verify(archivoAlmacenamientoService, never()).guardar(any(), anyString(), anyString());

        autenticar(colaborador);
        mockMvc.perform(multipart(URL_SUBIR, inscripcion.getId()).file(pdf()))
                .andExpect(redirectedUrl("/colaborador/perfil"))
                .andExpect(flash().attribute("mensajeExito",
                        "Tu evidencia fue enviada. Quedará pendiente de revisión del Resource Manager."));
        assertEquals(EstadoColaboradorCurso.EVIDENCIA_PENDIENTE,
                colaboradorCursoRepository.findById(inscripcion.getId()).orElseThrow().getEstado());
    }

    // 2a. Acepta PDF, JPG y PNG y guarda con la extensión correcta en la carpeta de evidencias.
    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource({"application/pdf, .pdf", "image/jpeg, .jpg", "image/png, .png"})
    void aceptaPdfJpgYPng(String tipo, String extension) {
        ColaboradorCurso inscripcion = enCurso(colaborador, curso);

        colaboradorCursoService.subirEvidencia(colaborador, inscripcion.getId(),
                new MockMultipartFile("evidencia", "constancia" + extension, tipo, new byte[]{1, 2, 3}));

        ColaboradorCurso enviada = colaboradorCursoRepository.findById(inscripcion.getId()).orElseThrow();
        assertEquals(EstadoColaboradorCurso.EVIDENCIA_PENDIENTE, enviada.getEstado());
        assertNotNull(enviada.getFechaEvidencia());
        String prefijo = "/simulado/cursos-evidencia/evidencia-curso-" + colaborador.getId() + "-" + inscripcion.getId() + "-";
        assertTrue(enviada.getEvidenciaUrl().startsWith(prefijo), enviada.getEvidenciaUrl());
        assertTrue(enviada.getEvidenciaUrl().endsWith(extension), enviada.getEvidenciaUrl());
        verify(archivoAlmacenamientoService).guardar(any(), eq("cursos-evidencia"), anyString());
    }

    // 2b. Cualquier otro tipo se rechaza sin guardar el archivo ni cambiar el estado.
    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource({"image/gif", "text/plain", "application/msword", "application/zip", "application/octet-stream"})
    void rechazaOtrosTiposDeArchivo(String tipo) {
        ColaboradorCurso inscripcion = enCurso(colaborador, curso);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> colaboradorCursoService.subirEvidencia(colaborador, inscripcion.getId(),
                        new MockMultipartFile("evidencia", "archivo", tipo, new byte[]{1, 2, 3})));

        assertEquals("La evidencia debe estar en formato PDF, JPG o PNG.", error.getMessage());
        sinEnvio(inscripcion);
    }

    // 3. El máximo es 10 MB: exactamente 10 MB pasa, un byte más se rechaza.
    @Test
    void rechazaArchivosMayoresDe10Mb() {
        int diezMb = 10 * 1024 * 1024;
        ColaboradorCurso grande = enCurso(colaborador, curso);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> colaboradorCursoService.subirEvidencia(colaborador, grande.getId(),
                        new MockMultipartFile("evidencia", "grande.pdf", "application/pdf", new byte[diezMb + 1])));
        assertEquals("La evidencia supera el máximo de 10MB.", error.getMessage());
        sinEnvio(grande);

        colaboradorCursoService.subirEvidencia(colaborador, grande.getId(),
                new MockMultipartFile("evidencia", "limite.pdf", "application/pdf", new byte[diezMb]));
        assertEquals(EstadoColaboradorCurso.EVIDENCIA_PENDIENTE,
                colaboradorCursoRepository.findById(grande.getId()).orElseThrow().getEstado());
    }

    // 4. Primer envío: vencido si fechaFin <= hoy (ayer y hoy se rechazan); mañana o sin fecha fin, se acepta.
    @ParameterizedTest(name = "[{index}] fechaFin = hoy + {0} días → permitido: {1}")
    @CsvSource({"-1, false", "0, false", "1, true", ", true"})
    void primerEnvioSoloAntesDelVencimiento(Integer diasDesdeHoy, boolean permitido) {
        Curso conPlazo = curso("Curso plazo evid test", "12.00",
                diasDesdeHoy == null ? null : LocalDate.now().plusDays(diasDesdeHoy));
        ColaboradorCurso inscripcion = enCurso(colaborador, conPlazo);

        if (permitido) {
            colaboradorCursoService.subirEvidencia(colaborador, inscripcion.getId(), pdf());
            assertEquals(EstadoColaboradorCurso.EVIDENCIA_PENDIENTE,
                    colaboradorCursoRepository.findById(inscripcion.getId()).orElseThrow().getEstado());
        } else {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> colaboradorCursoService.subirEvidencia(colaborador, inscripcion.getId(), pdf()));
            assertEquals("El plazo para subir evidencia de este curso ya venció.", error.getMessage());
            sinEnvio(inscripcion);
        }
    }

    // 5. Tras un rechazo puede reenviar aunque el plazo ya haya vencido; la evidencia rechazada no vence.
    @Test
    void trasUnRechazoPuedeReenviarAunqueElPlazoHayaVencido() {
        Curso conPlazo = curso("Curso reenvio evid test", "12.00", LocalDate.now().plusDays(2));
        ColaboradorCurso inscripcion = enCurso(colaborador, conPlazo);
        colaboradorCursoService.subirEvidencia(colaborador, inscripcion.getId(), pdf());
        rmCursoService.rechazarEvidencia(inscripcion.getId(), "La constancia no muestra tu nombre.", rm.getId());

        conPlazo.setFechaFin(LocalDate.now().minusDays(1));
        cursoRepository.save(conPlazo);
        colaboradorCursoService.listarMisCursos(colaborador);
        assertEquals(EstadoColaboradorCurso.EN_CURSO,
                colaboradorCursoRepository.findById(inscripcion.getId()).orElseThrow().getEstado(),
                "Con una evidencia rechazada conservada no pasa a NO_COMPLETADO");

        colaboradorCursoService.subirEvidencia(colaborador, inscripcion.getId(),
                new MockMultipartFile("evidencia", "nueva.png", "image/png", new byte[]{4, 5, 6}));

        ColaboradorCurso reenviada = colaboradorCursoRepository.findById(inscripcion.getId()).orElseThrow();
        assertEquals(EstadoColaboradorCurso.EVIDENCIA_PENDIENTE, reenviada.getEstado());
        assertTrue(reenviada.getEvidenciaUrl().endsWith(".png"));
        assertEquals(2, auditorias("SUBIR_EVIDENCIA_CURSO", inscripcion.getId()).size());
    }

    // 6. Sin evidencia y vencido (ayer u hoy) pasa una sola vez a NO_COMPLETADO, con una auditoría y una notificación.
    @Test
    void sinEvidenciaPasaUnaSolaVezANoCompletado() {
        ColaboradorCurso venceHoy = enCurso(colaborador, curso("Curso vence hoy evid test", "12.00", LocalDate.now()));
        ColaboradorCurso vencioAyer = enCurso(colaborador,
                curso("Curso vencio ayer evid test", "8.00", LocalDate.now().minusDays(1)));
        ColaboradorCurso vigente = enCurso(colaborador,
                curso("Curso vigente evid test", "8.00", LocalDate.now().plusDays(1)));
        ColaboradorCurso sinFecha = enCurso(colaborador, curso);

        colaboradorCursoService.listarMisCursos(colaborador);
        colaboradorCursoService.listarMisCursos(colaborador);
        colaboradorCursoService.listarCursosDisponibles(colaborador);
        horasDelMes(colaborador);

        for (ColaboradorCurso item : List.of(venceHoy, vencioAyer)) {
            ColaboradorCurso actual = colaboradorCursoRepository.findById(item.getId()).orElseThrow();
            assertEquals(EstadoColaboradorCurso.NO_COMPLETADO, actual.getEstado());
            assertNull(actual.getFechaCompletado());
            List<LogAuditoria> logs = auditorias("CURSO_NO_COMPLETADO", item.getId());
            assertEquals(1, logs.size());
            assertEquals(colaborador.getId(), logs.get(0).getUsuario().getId());
            assertEquals(1, notificaciones(colaborador, "CURSO_NO_COMPLETADO", item.getId()));
        }
        for (ColaboradorCurso item : List.of(vigente, sinFecha)) {
            assertEquals(EstadoColaboradorCurso.EN_CURSO,
                    colaboradorCursoRepository.findById(item.getId()).orElseThrow().getEstado());
            assertTrue(auditorias("CURSO_NO_COMPLETADO", item.getId()).isEmpty());
        }
        assertEquals(2, notificacionRepository.count(), "Solo las dos notificaciones CURSO_NO_COMPLETADO");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> colaboradorCursoService.subirEvidencia(colaborador, venceHoy.getId(), pdf()));
        assertEquals("Solo puedes subir evidencia de un curso que esté \"En curso\".", error.getMessage());
    }

    // 7. NO_COMPLETADO aparece en "Mis cursos" y sus horas no se suman al mes.
    @Test
    void noCompletadoApareceEnMisCursosYNoSumaHoras() {
        ColaboradorCurso vencido = enCurso(colaborador,
                curso("Curso no completado evid test", "30.00", LocalDate.now().minusDays(1)));
        ColaboradorCurso completado = inscripcion(colaborador, curso, EstadoColaboradorCurso.COMPLETADO);
        completado.setFechaCompletado(LocalDateTime.now());
        colaboradorCursoRepository.save(completado);

        List<ColaboradorCurso> misCursos = colaboradorCursoService.listarMisCursos(colaborador);

        assertEquals(List.of(vencido.getId(), completado.getId()).stream().sorted().toList(),
                misCursos.stream().map(ColaboradorCurso::getId).sorted().toList());
        assertEquals(EstadoColaboradorCurso.NO_COMPLETADO, misCursos.stream()
                .filter(item -> item.getId().equals(vencido.getId())).findFirst().orElseThrow().getEstado());
        assertEquals(0, new BigDecimal("20.00").compareTo(horasDelMes(colaborador)),
                "Solo cuentan las 20 h del curso completado, no las 30 h del no completado");
    }

    // 8. Con el tope de horas extra del bono alcanzado no puede solicitar otro curso; una hora antes, sí.
    @Test
    void topeDeHorasExtraDelBonoBloqueaUnaNuevaSolicitud() {
        BigDecimal tope = colaboradorActividadService.obtenerResumenBonoMensual(colaborador).getTopeHorasExtra();
        BigDecimal meta = colaboradorActividadService.obtenerResumenBonoMensual(colaborador).getMetaMensual();
        completadoEsteMes(colaborador, curso("Curso tope evid test", meta.add(tope).toPlainString(), null));
        completadoEsteMes(otroColaborador, curso("Curso casi tope evid test",
                meta.add(tope).subtract(BigDecimal.ONE).toPlainString(), null));
        Curso nuevo = curso("Curso nuevo evid test", "10.00", LocalDate.now().plusDays(10));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> colaboradorCursoService.solicitarInscripcion(colaborador, nuevo.getId(), null));
        assertTrue(error.getMessage().startsWith("No puedes solicitar este curso: ya alcanzaste el máximo de horas"),
                error.getMessage());
        assertFalse(colaboradorCursoRepository.existsByColaboradorAndCursoAndEstadoIn(
                colaborador, nuevo, List.of(EstadoColaboradorCurso.SOLICITADO)));
        assertEquals(0, notificacionRepository.count());

        colaboradorCursoService.solicitarInscripcion(otroColaborador, nuevo.getId(), null);
        assertTrue(colaboradorCursoRepository.existsByColaboradorAndCursoAndEstadoIn(
                otroColaborador, nuevo, List.of(EstadoColaboradorCurso.SOLICITADO)));
    }

    // 9. Un curso caducado o ya COMPLETADO no se puede solicitar.
    @Test
    void cursoCaducadoOCompletadoNoSePuedeSolicitar() {
        Curso caducado = curso("Curso caducado evid test", "10.00", LocalDate.now().minusDays(1));
        IllegalArgumentException errorCaducado = assertThrows(IllegalArgumentException.class,
                () -> colaboradorCursoService.solicitarInscripcion(colaborador, caducado.getId(), null));
        assertEquals("Este curso ya terminó: su fecha de fin ya pasó.", errorCaducado.getMessage());
        assertTrue(colaboradorCursoService.listarCursosDisponibles(colaborador).stream()
                .noneMatch(item -> item.getCurso().getId().equals(caducado.getId())));

        completadoEsteMes(colaborador, curso);
        IllegalArgumentException errorCompletado = assertThrows(IllegalArgumentException.class,
                () -> colaboradorCursoService.solicitarInscripcion(colaborador, curso.getId(), null));
        assertEquals("Ya tienes una solicitud pendiente o una inscripción activa en este curso.",
                errorCompletado.getMessage());
        assertFalse(colaboradorCursoService.listarCursosDisponibles(colaborador).stream()
                .filter(item -> item.getCurso().getId().equals(curso.getId()))
                .findFirst().orElseThrow().isPuedeSolicitar());

        assertEquals(1, colaboradorCursoRepository.findByColaborador(colaborador).size());
        assertEquals(0, notificacionRepository.count());
    }

    // 10. Aprobar la evidencia fija fechaCompletado y suma las horas al mes de esa fecha (no antes, ni a otro mes).
    @Test
    void aprobarEvidenciaFijaFechaCompletadoYSumaLasHorasAlMesDeEsaFecha() {
        ColaboradorCurso anterior = inscripcion(colaborador, curso("Curso mes pasado evid test", "15.00", null),
                EstadoColaboradorCurso.COMPLETADO);
        anterior.setFechaCompletado(LocalDateTime.now().minusMonths(1));
        colaboradorCursoRepository.save(anterior);
        ColaboradorCurso inscripcion = enCurso(colaborador, curso);
        colaboradorCursoService.subirEvidencia(colaborador, inscripcion.getId(), pdf());
        assertEquals(0, BigDecimal.ZERO.compareTo(horasDelMes(colaborador)), "En revisión todavía no suma");

        LocalDateTime antes = LocalDateTime.now().minusSeconds(1);
        rmCursoService.aprobarEvidencia(inscripcion.getId(), rm.getId());

        ColaboradorCurso completada = colaboradorCursoRepository.findById(inscripcion.getId()).orElseThrow();
        assertEquals(EstadoColaboradorCurso.COMPLETADO, completada.getEstado());
        assertNotNull(completada.getFechaCompletado());
        assertFalse(completada.getFechaCompletado().isBefore(antes));
        assertFalse(completada.getFechaCompletado().isAfter(LocalDateTime.now()));
        assertEquals(YearMonth.now(), YearMonth.from(completada.getFechaCompletado()));
        assertEquals(0, new BigDecimal("20.00").compareTo(horasDelMes(colaborador)),
                "Suma las 20 h del curso; el completado el mes pasado no cuenta");
    }

    // 11. TASK-054: revisor, fecha y auditoría de cada revisión siguen correctos en el flujo del colaborador.
    @Test
    void revisorFechaYAuditoriaDeLaEvidenciaSiguenCorrectos() {
        ColaboradorCurso inscripcion = enCurso(colaborador, curso);
        colaboradorCursoService.subirEvidencia(colaborador, inscripcion.getId(), pdf());
        List<LogAuditoria> subidas = auditorias("SUBIR_EVIDENCIA_CURSO", inscripcion.getId());
        assertEquals(1, subidas.size());
        assertEquals(colaborador.getId(), subidas.get(0).getUsuario().getId());

        rmCursoService.rechazarEvidencia(inscripcion.getId(), "Archivo ilegible.", rm.getId());
        ColaboradorCurso rechazada = colaboradorCursoRepository.findByIdConDetalle(inscripcion.getId()).orElseThrow();
        assertEquals(rm.getId(), rechazada.getEvidenciaRevisadaPor().getId());
        assertNotNull(rechazada.getFechaRevisionEvidencia());
        assertEquals(rm.getId(), rechazada.getAsignadoPor().getId(), "asignadoPor no cambia");

        colaboradorCursoService.subirEvidencia(colaborador, inscripcion.getId(), pdf());
        ColaboradorCurso reenviada = colaboradorCursoRepository.findByIdConDetalle(inscripcion.getId()).orElseThrow();
        assertNull(reenviada.getEvidenciaRevisadaPor(), "El reenvío limpia la revisión anterior");
        assertNull(reenviada.getFechaRevisionEvidencia());

        rmCursoService.aprobarEvidencia(inscripcion.getId(), rmRevisor.getId());
        ColaboradorCurso aprobada = colaboradorCursoRepository.findByIdConDetalle(inscripcion.getId()).orElseThrow();
        assertEquals(rmRevisor.getId(), aprobada.getEvidenciaRevisadaPor().getId());
        assertEquals(aprobada.getFechaCompletado(), aprobada.getFechaRevisionEvidencia());
        assertEquals(rm.getId(), aprobada.getAsignadoPor().getId(), "asignadoPor sigue siendo quien aprobó la inscripción");

        List<LogAuditoria> rechazos = auditorias("RECHAZAR_EVIDENCIA_CURSO", inscripcion.getId());
        assertEquals(1, rechazos.size(), "El rechazo anterior queda en la auditoría");
        assertEquals(rm.getId(), rechazos.get(0).getUsuario().getId());
        assertEquals("EVIDENCIA_PENDIENTE", rechazos.get(0).getValorAnterior());
        assertEquals("EN_CURSO", rechazos.get(0).getValorNuevo());
        List<LogAuditoria> aprobaciones = auditorias("APROBAR_EVIDENCIA_CURSO", inscripcion.getId());
        assertEquals(1, aprobaciones.size());
        assertEquals(rmRevisor.getId(), aprobaciones.get(0).getUsuario().getId());
        assertEquals("EVIDENCIA_PENDIENTE", aprobaciones.get(0).getValorAnterior());
        assertEquals("COMPLETADO", aprobaciones.get(0).getValorNuevo());
        assertTrue(aprobaciones.get(0).getDetalle().contains("Carla Quispe"), aprobaciones.get(0).getDetalle());
        assertEquals(2, auditorias("SUBIR_EVIDENCIA_CURSO", inscripcion.getId()).size());
    }

    // 12. TASK-056: la aprobación de la inscripción notifica y envía un solo correo; la evidencia no lo repite.
    @Test
    void notificacionYCorreoDeAprobacionSiguenCorrectos() {
        Curso conFechas = curso("Kubernetes evid test", "16.00", LocalDate.of(2027, 1, 29));
        conFechas.setFechaInicio(LocalDate.of(2027, 1, 4));
        conFechas = cursoRepository.save(conFechas);
        colaboradorCursoService.solicitarInscripcion(colaborador, conFechas.getId(), "Lo necesito.");
        ColaboradorCurso solicitud = colaboradorCursoRepository.findByColaborador(colaborador).get(0);

        rmCursoService.aprobar(solicitud.getId(), "Aporta al proyecto.", rm.getId());

        List<Notificacion> propias = notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(colaborador);
        assertEquals(1, propias.size());
        assertEquals("CURSO_APROBADO", propias.get(0).getTipo());
        assertEquals("Tu solicitud para “Kubernetes evid test” fue aprobada. Inicio: 04/01/2027"
                + " · Fin: 29/01/2027 · Duración: 16 h.", propias.get(0).getDescripcion());
        assertEquals("/colaborador/perfil#mis-cursos", notificacionService.listar(colaborador.getId()).get(0).url());
        verify(emailService).enviarInscripcionAprobada("carla.colevid@skillbridge.test",
                "Kubernetes evid test", "04/01/2027", "29/01/2027", "16 h");

        colaboradorCursoService.subirEvidencia(colaborador, solicitud.getId(), pdf());
        for (Usuario rmActivo : usuarioRepository.findActivosByRolNombre("RESOURCE_MANAGER")) {
            assertEquals(1, notificaciones(rmActivo, "EVIDENCIA_CURSO_PENDIENTE", solicitud.getId()));
        }
        rmCursoService.aprobarEvidencia(solicitud.getId(), rm.getId());

        assertEquals(List.of("CURSO_COMPLETADO", "CURSO_APROBADO"), notificacionRepository
                .findByUsuarioOrderByFechaCreacionDesc(colaborador).stream().map(Notificacion::getTipo).toList());
        verify(emailService, times(1)).enviarInscripcionAprobada(any(), any(), any(), any(), any());
    }

    // En la web, open-in-view mantiene la sesión; aquí la transacción cumple ese papel.
    private BigDecimal horasDelMes(Usuario usuario) {
        return transactionTemplate.execute(estado ->
                colaboradorActividadService.obtenerResumenHoras(usuario).getHorasTrabajadasMes());
    }

    private void sinEnvio(ColaboradorCurso inscripcion) {
        ColaboradorCurso actual = colaboradorCursoRepository.findById(inscripcion.getId()).orElseThrow();
        assertEquals(EstadoColaboradorCurso.EN_CURSO, actual.getEstado());
        assertNull(actual.getEvidenciaUrl());
        assertTrue(auditorias("SUBIR_EVIDENCIA_CURSO", inscripcion.getId()).isEmpty());
        assertEquals(0, notificacionRepository.count());
        verify(archivoAlmacenamientoService, never()).guardar(any(), anyString(), anyString());
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("evidencia", "constancia.pdf", "application/pdf", new byte[]{1, 2, 3});
    }

    private long notificaciones(Usuario usuario, String tipo, Long inscripcionId) {
        return notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(usuario).stream()
                .filter(item -> tipo.equals(item.getTipo()) && inscripcionId.equals(item.getEntidadId()))
                .count();
    }

    private List<LogAuditoria> auditorias(String accion, Long inscripcionId) {
        return logAuditoriaRepository.findAllConUsuario().stream()
                .filter(log -> accion.equals(log.getAccion()) && "COLABORADOR_CURSO".equals(log.getEntidad())
                        && inscripcionId.equals(log.getEntidadId()))
                .toList();
    }

    private void autenticar(Usuario usuario) {
        UsuarioDetails details = new UsuarioDetails(usuarioRepository.findActivosByRolNombre("COLABORADOR")
                .stream().filter(u -> u.getId().equals(usuario.getId())).findFirst().orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    private ColaboradorCurso enCurso(Usuario usuario, Curso cursoInscrito) {
        return inscripcion(usuario, cursoInscrito, EstadoColaboradorCurso.EN_CURSO);
    }

    private void completadoEsteMes(Usuario usuario, Curso cursoCompletado) {
        ColaboradorCurso item = inscripcion(usuario, cursoCompletado, EstadoColaboradorCurso.COMPLETADO);
        item.setFechaCompletado(LocalDateTime.now());
        colaboradorCursoRepository.save(item);
    }

    private ColaboradorCurso inscripcion(Usuario usuario, Curso cursoInscrito, EstadoColaboradorCurso estado) {
        ColaboradorCurso item = new ColaboradorCurso();
        item.setColaborador(usuario);
        item.setCurso(cursoInscrito);
        item.setOrigen(OrigenCurso.SOLICITUD_COLABORADOR);
        item.setEstado(estado);
        item.setAsignadoPor(rm);
        item.setFechaRespuesta(LocalDateTime.now());
        return colaboradorCursoRepository.save(item);
    }

    private Curso curso(String nombre, String horas, LocalDate fechaFin) {
        Curso nuevo = new Curso();
        nuevo.setNombre(nombre);
        nuevo.setDescripcion("Curso de prueba.");
        nuevo.setCategoria("Técnico");
        nuevo.setHoras(new BigDecimal(horas));
        nuevo.setFechaFin(fechaFin);
        nuevo.setActivo(true);
        nuevo.setCreadoPor(admin);
        return cursoRepository.save(nuevo);
    }

    private Rol rol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol nuevo = new Rol();
            nuevo.setNombre(nombre);
            return rolRepository.save(nuevo);
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
}
