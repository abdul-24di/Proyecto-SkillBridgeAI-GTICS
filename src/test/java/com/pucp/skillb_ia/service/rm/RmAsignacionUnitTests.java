package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.ConfiguracionSistema;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.MotivoFinalizacion;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ColaboradorHabilidadRepository;
import com.pucp.skillb_ia.repository.ConfiguracionSistemaRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.DisponibilidadService;
import com.pucp.skillb_ia.service.EvaluacionService;
import com.pucp.skillb_ia.service.NotificacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;

import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.asignacion;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.colaborador;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.pm;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.proyecto;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.rm;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Unitarias de RmAsignacionService: métodos que escriben (TASK-082) y consultas y filtros (TASK-083).
// Datos base: RM 1, PM 2, colaborador 10 (40 h disponibles), proyecto 5 ACTIVO con 1 cupo.
@ExtendWith(MockitoExtension.class)
class RmAsignacionUnitTests {

    private static final Long RM_ID = 1L;
    private static final Long COL_ID = 10L;
    private static final Long PROY_ID = 5L;
    private static final Long ASIG_ID = 50L;

    @Mock private AsignacionRepository asignacionRepository;
    @Mock private ProyectoRepository proyectoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ColaboradorHabilidadRepository colaboradorHabilidadRepository;
    @Mock private ConfiguracionSistemaRepository configuracionSistemaRepository;
    @Mock private AuditoriaService auditoriaService;
    @Mock private RmPresupuestoService presupuestoService;
    @Mock private DisponibilidadService disponibilidadService;
    @Mock private NotificacionService notificacionService;
    @Mock private RmResponsableService rmResponsableService;
    @Mock private EvaluacionService evaluacionService;
    @Mock private RmAlcanceService rmAlcanceService;

    @InjectMocks
    private RmAsignacionService service;

    private Usuario rm;
    private Usuario pm;
    private Usuario colaborador;
    private Proyecto proyecto;

    @BeforeEach
    void preparar() {
        rm = rm(RM_ID);
        pm = pm(2L);
        colaborador = colaborador(COL_ID);
        proyecto = proyecto(PROY_ID);
        proyecto.setEstado(EstadoProyecto.ACTIVO);
        proyecto.setPm(pm);
        proyecto.setColaboradoresRequeridos(1);
    }

    private static BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }

    private void conRm() {
        when(usuarioRepository.findById(RM_ID)).thenReturn(Optional.of(rm));
    }

    private void conProyecto() {
        when(proyectoRepository.findById(PROY_ID)).thenReturn(Optional.of(proyecto));
    }

    private void conColaboradorAsignable() {
        when(usuarioRepository.findColaboradorVisibleRmById(COL_ID)).thenReturn(Optional.of(colaborador));
    }

    private void conMaxAsignaciones(String valor) {
        ConfiguracionSistema config = new ConfiguracionSistema();
        config.setClave("MAX_ASIGNACIONES_POR_COLABORADOR");
        config.setValor(valor);
        when(configuracionSistemaRepository.findByClave("MAX_ASIGNACIONES_POR_COLABORADOR"))
                .thenReturn(Optional.of(config));
    }

    private void guardarDevuelveArgumento() {
        when(asignacionRepository.save(any(Asignacion.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void sinGuardarNiAvisar() {
        verify(asignacionRepository, never()).save(any());
        verifyNoInteractions(notificacionService, auditoriaService, disponibilidadService);
    }

    // Asignación PENDIENTE en espera del RM (solicitud del colaborador).
    private Asignacion pendienteDeRm() {
        Asignacion a = asignacion(ASIG_ID, proyecto, colaborador, "10", EstadoAsignacion.PENDIENTE);
        a.setOrigen(OrigenAsignacion.SOLICITADA_COLABORADOR);
        when(asignacionRepository.findByIdConDetalle(ASIG_ID)).thenReturn(Optional.of(a));
        return a;
    }

    @Nested
    class ProponerDesdeRm {

        private Asignacion proponer(String horas, String justificacion, String motivoCapacidad, String justCupo) {
            return service.proponerDesdeRm(PROY_ID, COL_ID, horas == null ? null : bd(horas),
                    justificacion, motivoCapacidad, justCupo, RM_ID);
        }

        @Test
        void rmInvalidoLanza() {
            when(usuarioRepository.findById(RM_ID)).thenReturn(Optional.of(colaborador));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> proponer("10", null, null, null));
            assertEquals("El usuario actual no es un Resource Manager válido.", ex.getMessage());
            sinGuardarNiAvisar();
        }

        @Test
        void proyectoInexistenteLanza() {
            conRm();
            when(proyectoRepository.findById(PROY_ID)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> proponer("10", null, null, null));
            assertEquals("No se encontró el proyecto seleccionado.", ex.getMessage());
        }

        @Test
        void proyectoNoAsignableLanza() {
            conRm();
            proyecto.setEstado(EstadoProyecto.EN_REVISION);
            conProyecto();

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> proponer("10", null, null, null));
            assertEquals("Solo se pueden proponer asignaciones para proyectos activos o en espera.", ex.getMessage());
            sinGuardarNiAvisar();
        }

        @Test
        void rmNoResponsableLanza() {
            conRm();
            conProyecto();
            doThrow(new IllegalArgumentException("No eres el RM responsable."))
                    .when(rmResponsableService).validarResponsable(proyecto, rm);

            assertThrows(IllegalArgumentException.class, () -> proponer("10", null, null, null));
            sinGuardarNiAvisar();
        }

        @Test
        void colaboradorNoVisibleLanza() {
            conRm();
            conProyecto();
            when(usuarioRepository.findColaboradorVisibleRmById(COL_ID)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> proponer("10", null, null, null));
            assertEquals("No se encontró un colaborador activo válido.", ex.getMessage());
        }

        @Test
        void horasCeroOMayoresA168Lanzan() {
            conRm();
            conProyecto();
            conColaboradorAsignable();

            assertEquals("Las horas semanales deben ser mayores que cero.",
                    assertThrows(IllegalArgumentException.class, () -> proponer("0", null, null, null)).getMessage());
            assertEquals("Las horas semanales deben ser mayores que cero.",
                    assertThrows(IllegalArgumentException.class, () -> proponer(null, null, null, null)).getMessage());
            assertEquals("Las horas semanales no pueden superar 168.",
                    assertThrows(IllegalArgumentException.class, () -> proponer("169", null, null, null)).getMessage());
            sinGuardarNiAvisar();
        }

        @Test
        void masHorasQueLasDisponiblesLanza() {
            conRm();
            conProyecto();
            conColaboradorAsignable();

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> proponer("41", null, null, null));
            assertEquals("Las horas propuestas (41 h/sem) superan las horas disponibles del colaborador (40 h/sem).",
                    ex.getMessage());
            verifyNoInteractions(presupuestoService);
            sinGuardarNiAvisar();
        }

        @Test
        void colaboradorSinHorasDisponiblesLanza() {
            conRm();
            conProyecto();
            colaborador.setHorasDisponibles(null);
            conColaboradorAsignable();

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> proponer("1", null, null, null));
            assertTrue(ex.getMessage().contains("(0 h/sem)"));
        }

        @Test
        void presupuestoInsuficienteLanza() {
            conRm();
            conProyecto();
            conColaboradorAsignable();
            doThrow(new IllegalArgumentException("Presupuesto insuficiente."))
                    .when(presupuestoService).validarPresupuestoSuficiente(proyecto, colaborador, bd("10"));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> proponer("10", null, null, null));
            assertEquals("Presupuesto insuficiente.", ex.getMessage());
            sinGuardarNiAvisar();
        }

        @Test
        void colaboradorYaAsignadoLanza() {
            conRm();
            conProyecto();
            conColaboradorAsignable();
            when(asignacionRepository.existsByProyectoAndColaboradorAndEstadoIn(eq(proyecto), eq(colaborador), any()))
                    .thenReturn(true);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> proponer("10", null, null, null));
            assertEquals("El colaborador ya tiene una asignación pendiente o activa en este proyecto.", ex.getMessage());
            sinGuardarNiAvisar();
        }

        @Test
        void maximoDeActivasSinJustificacionLanza() {
            conRm();
            conProyecto();
            conColaboradorAsignable();
            conMaxAsignaciones("2");
            when(asignacionRepository.countByColaboradorAndEstado(colaborador, EstadoAsignacion.ACTIVA)).thenReturn(2L);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> proponer("10", null, "  ", null));
            assertEquals("El colaborador alcanzó el límite de asignaciones activas; debes justificar la excepción.",
                    ex.getMessage());
            sinGuardarNiAvisar();
        }

        @Test
        void maximoDeActivasConJustificacionGuardaMensajeCombinado() {
            conRm();
            conProyecto();
            conColaboradorAsignable();
            // Valor inválido en configuración: se usa el máximo por defecto (3).
            conMaxAsignaciones("abc");
            when(asignacionRepository.countByColaboradorAndEstado(colaborador, EstadoAsignacion.ACTIVA)).thenReturn(3L);
            guardarDevuelveArgumento();

            Asignacion guardada = proponer("10", " Perfil ideal ", " Proyecto prioritario ", null);

            assertEquals("Perfil ideal\nJustificación de capacidad: Proyecto prioritario",
                    guardada.getMensajeSolicitud());
        }

        @Test
        void justificacionMayorA500Lanza() {
            conRm();
            conProyecto();
            conColaboradorAsignable();

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> proponer("10", "x".repeat(501), null, null));
            assertEquals("La justificación no puede superar 500 caracteres.", ex.getMessage());
        }

        @Test
        void justificacionTotalMayorA500Lanza() {
            conRm();
            conProyecto();
            conColaboradorAsignable();
            when(asignacionRepository.countByColaboradorAndEstado(colaborador, EstadoAsignacion.ACTIVA)).thenReturn(3L);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> proponer("10", "x".repeat(400), "y".repeat(200), null));
            assertEquals("La justificación total no puede superar 500 caracteres.", ex.getMessage());
        }

        @Test
        void cuposCompletosSinJustificacionLanza() {
            conRm();
            conProyecto();
            conColaboradorAsignable();
            when(asignacionRepository.countByProyectoAndEstadoIn(eq(proyecto), any())).thenReturn(1L);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> proponer("10", null, null, " "));
            assertEquals("El proyecto ya tiene sus cupos completos; justifica por qué propones otro colaborador.",
                    ex.getMessage());
            sinGuardarNiAvisar();
        }

        @Test
        void justificacionDeCupoMayorA500Lanza() {
            conRm();
            conProyecto();
            conColaboradorAsignable();
            when(asignacionRepository.countByProyectoAndEstadoIn(eq(proyecto), any())).thenReturn(1L);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> proponer("10", null, null, "x".repeat(501)));
            assertEquals("La justificación de cupo no puede superar 500 caracteres.", ex.getMessage());
        }

        @Test
        void cuposCompletosConJustificacionAuditaElCupo() {
            conRm();
            conProyecto();
            conColaboradorAsignable();
            when(asignacionRepository.countByProyectoAndEstadoIn(eq(proyecto), any())).thenReturn(1L);
            guardarDevuelveArgumento();

            proponer("10", null, null, " Hace falta un especialista ");

            verify(auditoriaService).registrar(eq(rm), eq("PROPUESTA_ASIGNACION"), eq("ASIGNACION"), isNull(),
                    contains("Supera los cupos del proyecto."));
            verify(auditoriaService).registrar(rm, "JUSTIFICACION_CUPO_ASIGNACION", "ASIGNACION", null,
                    "Hace falta un especialista", "Cupos ocupados: 1 de 1", null, null);
        }

        @Test
        void exitoGuardaPendienteAprobadaPorRmNotificaAlPmYAudita() {
            conRm();
            conProyecto();
            conColaboradorAsignable();
            guardarDevuelveArgumento();

            Asignacion guardada = proponer("10", "  ", null, "ignorada");

            assertEquals(EstadoAsignacion.PENDIENTE, guardada.getEstado());
            assertEquals(OrigenAsignacion.PROPUESTA_RM, guardada.getOrigen());
            assertTrue(guardada.isAprobadoPorRm());
            assertNotNull(guardada.getFechaAprobacionRm());
            assertEquals(bd("10"), guardada.getHorasSemanales());
            assertEquals(proyecto, guardada.getProyecto());
            assertEquals(colaborador, guardada.getColaborador());
            assertNull(guardada.getMensajeSolicitud());
            verify(presupuestoService).validarPresupuestoSuficiente(proyecto, colaborador, bd("10"));
            verify(notificacionService).crear(eq(pm), eq("ASIGNACION_PENDIENTE_PM"),
                    eq(CategoriaNotificacion.ASIGNACION), eq("Asignación pendiente de tu aprobación"),
                    contains("Usuario10 Prueba"), eq("ASIGNACION"), isNull());
            verify(auditoriaService).registrar(eq(rm), eq("PROPUESTA_ASIGNACION"), eq("ASIGNACION"), isNull(),
                    eq("El RM propuso a Usuario10 Prueba para el proyecto Proyecto5."));
            verify(auditoriaService, never()).registrar(any(), eq("JUSTIFICACION_CUPO_ASIGNACION"), anyString(),
                    any(), anyString(), anyString(), any(), any());
        }
    }

    @Nested
    class Aprobar {

        @Test
        void asignacionInexistenteLanza() {
            conRm();
            when(asignacionRepository.findByIdConDetalle(ASIG_ID)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.aprobar(ASIG_ID, null, RM_ID));
            assertEquals("No se encontró la asignación solicitada.", ex.getMessage());
        }

        @Test
        void soloPendienteSePuedeAprobar() {
            conRm();
            Asignacion a = pendienteDeRm();
            a.setEstado(EstadoAsignacion.ACTIVA);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.aprobar(ASIG_ID, null, RM_ID));
            assertEquals("La asignación ya fue resuelta.", ex.getMessage());
            sinGuardarNiAvisar();
        }

        @Test
        void yaAprobadaPorRmOPropuestaRmLanza() {
            conRm();
            Asignacion a = pendienteDeRm();
            a.setAprobadoPorRm(true);
            assertEquals("Esta asignación no está pendiente de decisión del RM.",
                    assertThrows(IllegalStateException.class, () -> service.aprobar(ASIG_ID, null, RM_ID)).getMessage());

            a.setAprobadoPorRm(false);
            a.setOrigen(OrigenAsignacion.PROPUESTA_RM);
            assertEquals("Esta asignación no está pendiente de decisión del RM.",
                    assertThrows(IllegalStateException.class, () -> service.aprobar(ASIG_ID, null, RM_ID)).getMessage());
            sinGuardarNiAvisar();
        }

        @Test
        void proyectoYaNoAsignableLanza() {
            conRm();
            pendienteDeRm();
            proyecto.setEstado(EstadoProyecto.FINALIZADO);
            conProyecto();

            assertThrows(IllegalStateException.class, () -> service.aprobar(ASIG_ID, null, RM_ID));
            sinGuardarNiAvisar();
        }

        @Test
        void revalidaHorasDisponibles() {
            conRm();
            pendienteDeRm();
            conProyecto();
            colaborador.setHorasDisponibles(bd("5"));

            assertThrows(IllegalArgumentException.class, () -> service.aprobar(ASIG_ID, null, RM_ID));
            verifyNoInteractions(presupuestoService);
            sinGuardarNiAvisar();
        }

        @Test
        void revalidaPresupuesto() {
            conRm();
            pendienteDeRm();
            conProyecto();
            doThrow(new IllegalArgumentException("Presupuesto insuficiente."))
                    .when(presupuestoService).validarPresupuestoSuficiente(proyecto, colaborador, bd("10"));

            assertThrows(IllegalArgumentException.class, () -> service.aprobar(ASIG_ID, null, RM_ID));
            sinGuardarNiAvisar();
        }

        @Test
        void superaMaximoSinMotivoLanza() {
            conRm();
            pendienteDeRm();
            conProyecto();
            when(asignacionRepository.countByColaboradorAndEstado(colaborador, EstadoAsignacion.ACTIVA)).thenReturn(3L);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.aprobar(ASIG_ID, null, RM_ID));
            assertEquals("El colaborador alcanzó el límite de asignaciones activas; debes justificar la excepción.",
                    ex.getMessage());
            sinGuardarNiAvisar();
        }

        @Test
        void superaMaximoConMotivoLoAgregaAlMensaje() {
            conRm();
            Asignacion a = pendienteDeRm();
            a.setMensajeSolicitud("Quiero participar");
            conProyecto();
            when(asignacionRepository.countByColaboradorAndEstado(colaborador, EstadoAsignacion.ACTIVA)).thenReturn(3L);

            service.aprobar(ASIG_ID, "Urgente", RM_ID);

            assertEquals("Quiero participar\nJustificación de capacidad: Urgente", a.getMensajeSolicitud());
            assertTrue(a.isAprobadoPorRm());
        }

        @Test
        void sinAprobacionDelPmQuedaPendienteYAvisaAlPm() {
            conRm();
            Asignacion a = pendienteDeRm();
            conProyecto();

            service.aprobar(ASIG_ID, null, RM_ID);

            assertEquals(EstadoAsignacion.PENDIENTE, a.getEstado());
            assertTrue(a.isAprobadoPorRm());
            assertNotNull(a.getFechaAprobacionRm());
            assertNull(a.getFechaActivacion());
            verify(asignacionRepository).save(a);
            verifyNoInteractions(disponibilidadService);
            verify(notificacionService, never()).crear(eq(colaborador), anyString(), any(), anyString(), anyString(),
                    anyString(), anyLong());
            verify(notificacionService).crear(eq(pm), eq("ASIGNACION_PENDIENTE_PM"), eq(CategoriaNotificacion.ASIGNACION),
                    eq("Asignación pendiente de tu aprobación"), contains("falta tu aprobación"),
                    eq("PROYECTO"), eq(PROY_ID));
            verify(auditoriaService).registrar(rm, "APROBACION_ASIGNACION", "ASIGNACION", ASIG_ID,
                    "El RM aprobó la asignación; continúa pendiente del PM.");
        }

        @Test
        void conAprobacionDelPmQuedaActivaRecalculaYNotifica() {
            conRm();
            Asignacion a = pendienteDeRm();
            a.setAprobadoPorPm(true);
            conProyecto();

            service.aprobar(ASIG_ID, null, RM_ID);

            assertEquals(EstadoAsignacion.ACTIVA, a.getEstado());
            assertNotNull(a.getFechaActivacion());
            verify(asignacionRepository).save(a);
            verify(disponibilidadService).recalcular(colaborador);
            verify(notificacionService).crear(eq(colaborador), eq("ASIGNACION_APROBADA"),
                    eq(CategoriaNotificacion.ASIGNACION), eq("Asignación aprobada"), contains("quedó activa"),
                    eq("ASIGNACION"), eq(ASIG_ID));
            verify(notificacionService).crear(eq(pm), eq("ASIGNACION_ACTIVA"), eq(CategoriaNotificacion.ASIGNACION),
                    eq("Asignación activa"), contains("ya forma parte del equipo"), eq("PROYECTO"), eq(PROY_ID));
            verify(auditoriaService).registrar(rm, "APROBACION_ASIGNACION", "ASIGNACION", ASIG_ID,
                    "El RM aprobó la asignación; quedó activa.");
        }

        @Test
        void conAprobacionDelPmYActivaDuplicadaLanza() {
            conRm();
            Asignacion a = pendienteDeRm();
            a.setAprobadoPorPm(true);
            conProyecto();
            when(asignacionRepository.existsByProyectoAndColaboradorAndEstado(proyecto, colaborador,
                    EstadoAsignacion.ACTIVA)).thenReturn(true);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.aprobar(ASIG_ID, null, RM_ID));
            assertEquals("El colaborador ya tiene una asignación activa en este proyecto.", ex.getMessage());
            sinGuardarNiAvisar();
        }
    }

    @Nested
    class Rechazar {

        @Test
        void motivoObligatorio() {
            conRm();
            pendienteDeRm();

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.rechazar(ASIG_ID, "   ", RM_ID));
            assertEquals("Debes indicar el motivo del rechazo.", ex.getMessage());
            sinGuardarNiAvisar();
        }

        @Test
        void motivoMayorA300Lanza() {
            conRm();
            pendienteDeRm();

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.rechazar(ASIG_ID, "x".repeat(301), RM_ID));
            assertEquals("El texto no puede superar 300 caracteres.", ex.getMessage());
            sinGuardarNiAvisar();
        }

        @Test
        void yaResueltaLanza() {
            conRm();
            pendienteDeRm().setEstado(EstadoAsignacion.RECHAZADA);

            assertThrows(IllegalStateException.class, () -> service.rechazar(ASIG_ID, "Motivo", RM_ID));
            sinGuardarNiAvisar();
        }

        @Test
        void rmNoResponsableLanza() {
            conRm();
            pendienteDeRm();
            doThrow(new IllegalArgumentException("No eres el RM responsable."))
                    .when(rmResponsableService).validarResponsable(proyecto, rm);

            assertThrows(IllegalArgumentException.class, () -> service.rechazar(ASIG_ID, "Motivo", RM_ID));
            sinGuardarNiAvisar();
        }

        @Test
        void exitoRechazaNotificaAAmbosYAudita() {
            conRm();
            Asignacion a = pendienteDeRm();

            service.rechazar(ASIG_ID, "  Sin perfil  ", RM_ID);

            assertEquals(EstadoAsignacion.RECHAZADA, a.getEstado());
            assertEquals("Sin perfil", a.getMotivoRechazo());
            assertEquals(rm, a.getRechazadoPor());
            verify(asignacionRepository).save(a);
            verify(notificacionService).crear(eq(colaborador), eq("ASIGNACION_RECHAZADA"),
                    eq(CategoriaNotificacion.ASIGNACION), eq("Asignación rechazada"), contains("Motivo: Sin perfil"),
                    eq("ASIGNACION"), eq(ASIG_ID));
            verify(notificacionService).crear(eq(pm), eq("ASIGNACION_RECHAZADA_RM"),
                    eq(CategoriaNotificacion.ASIGNACION), eq("Asignación rechazada por el RM"),
                    contains("Usuario10 Prueba"), eq("PROYECTO"), eq(PROY_ID));
            verify(auditoriaService).registrar(rm, "RECHAZO_ASIGNACION", "ASIGNACION", ASIG_ID,
                    "El RM rechazó la asignación. Motivo: Sin perfil");
        }
    }

    @Nested
    class Finalizar {

        private Asignacion activa() {
            Asignacion a = asignacion(ASIG_ID, proyecto, colaborador, "10", EstadoAsignacion.ACTIVA);
            when(asignacionRepository.findByIdConDetalle(ASIG_ID)).thenReturn(Optional.of(a));
            return a;
        }

        @Test
        void soloActivaSePuedeFinalizar() {
            conRm();
            activa().setEstado(EstadoAsignacion.PENDIENTE);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.finalizar(ASIG_ID, MotivoFinalizacion.values()[0], null, null, null, RM_ID));
            assertEquals("Solo se puede finalizar una asignación activa.", ex.getMessage());
            sinGuardarNiAvisar();
            verifyNoInteractions(evaluacionService);
        }

        @Test
        void motivoObligatorio() {
            conRm();
            activa();

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.finalizar(ASIG_ID, null, null, 4, null, RM_ID));
            assertEquals("Selecciona un motivo de finalización.", ex.getMessage());
            sinGuardarNiAvisar();
            verifyNoInteractions(evaluacionService);
        }

        @Test
        void rmNoResponsableLanza() {
            conRm();
            activa();
            doThrow(new IllegalArgumentException("No eres el RM responsable."))
                    .when(rmResponsableService).validarResponsable(proyecto, rm);

            assertThrows(IllegalArgumentException.class,
                    () -> service.finalizar(ASIG_ID, MotivoFinalizacion.values()[0], null, null, null, RM_ID));
            sinGuardarNiAvisar();
        }

        @Test
        void exitoConCalificacionLiberaHorasEvaluaNotificaYAudita() {
            conRm();
            Asignacion a = activa();
            MotivoFinalizacion motivo = MotivoFinalizacion.values()[0];

            service.finalizar(ASIG_ID, motivo, "  " + "o".repeat(300) + "  ", 5, "Buen trabajo", RM_ID);

            assertEquals(EstadoAsignacion.FINALIZADA, a.getEstado());
            assertEquals(motivo, a.getMotivoFinalizacion());
            assertEquals(rm, a.getDesasignadoPor());
            assertNotNull(a.getFechaFinalizacion());
            verify(asignacionRepository).save(a);
            verify(evaluacionService).evaluarAsignacion(ASIG_ID, 5, "Buen trabajo", rm);
            verify(disponibilidadService).recalcular(colaborador);
            verify(notificacionService).crear(eq(colaborador), eq("ASIGNACION_FINALIZADA"),
                    eq(CategoriaNotificacion.ASIGNACION), eq("Te desasignaron de un proyecto"), contains("Proyecto5"),
                    eq("ASIGNACION"), eq(ASIG_ID));
            ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
            verify(auditoriaService).registrar(eq(rm), eq("FINALIZACION_ASIGNACION"), eq("ASIGNACION"), eq(ASIG_ID),
                    detalle.capture());
            assertEquals("El RM finalizó la asignación. Motivo: " + motivo.name() + ". Observación: " + "o".repeat(220),
                    detalle.getValue());
        }

        @Test
        void sinCalificacionNoCreaEvaluacionNiObservacion() {
            conRm();
            activa();
            MotivoFinalizacion motivo = MotivoFinalizacion.values()[0];

            service.finalizar(ASIG_ID, motivo, "  ", null, null, RM_ID);

            verifyNoInteractions(evaluacionService);
            verify(disponibilidadService).recalcular(colaborador);
            verify(auditoriaService).registrar(rm, "FINALIZACION_ASIGNACION", "ASIGNACION", ASIG_ID,
                    "El RM finalizó la asignación. Motivo: " + motivo.name());
        }
    }

    @Nested
    class EsProyectoAsignable {

        @Test
        void soloActivoOEnEspera() {
            assertFalse(service.esProyectoAsignable(null));
            for (EstadoProyecto estado : EstadoProyecto.values()) {
                proyecto.setEstado(estado);
                boolean esperado = estado == EstadoProyecto.ACTIVO || estado == EstadoProyecto.EN_ESPERA;
                assertEquals(esperado, service.esProyectoAsignable(proyecto), estado.name());
            }
        }
    }

    @Nested
    class Cupos {

        @Test
        void contarCuposOcupadosCuentaActivasYPendientes() {
            when(asignacionRepository.countByProyectoAndEstadoIn(proyecto,
                    java.util.EnumSet.of(EstadoAsignacion.ACTIVA, EstadoAsignacion.PENDIENTE))).thenReturn(2L);

            assertEquals(2L, service.contarCuposOcupados(proyecto));
        }

        @Test
        void cuposOcupadosPorProyectoArmaElMapa() {
            Proyecto otro = proyecto(6L);
            when(asignacionRepository.countByProyectoAndEstadoIn(eq(proyecto), any())).thenReturn(1L);
            when(asignacionRepository.countByProyectoAndEstadoIn(eq(otro), any())).thenReturn(0L);

            Map<Long, Long> cupos = service.cuposOcupadosPorProyecto(List.of(proyecto, otro));

            assertEquals(Map.of(PROY_ID, 1L, 6L, 0L), cupos);
        }

        @Test
        void listaVaciaDevuelveMapaVacio() {
            assertTrue(service.cuposOcupadosPorProyecto(List.of()).isEmpty());
        }
    }

    // ---------- Consultas y filtros (TASK-083) ----------

    // Asignación de prueba con origen definido (la vista lo necesita para clasificar).
    private Asignacion fila(Long id, Proyecto p, EstadoAsignacion estado, OrigenAsignacion origen) {
        Asignacion a = asignacion(id, p, colaborador, "10", estado);
        a.setOrigen(origen);
        return a;
    }

    // Todas las asignaciones visibles para el RM de la sesión.
    private void bandeja(List<Asignacion> asignaciones) {
        when(rmAlcanceService.idRmActual()).thenReturn(RM_ID);
        when(asignacionRepository.findAllConDetalleOrderByFechaSolicitudDesc()).thenReturn(asignaciones);
        when(rmAlcanceService.puedeVer(any(), eq(RM_ID))).thenReturn(true);
    }

    private static RmAsignacionService.FiltrosAsignacion filtros(String grupo, String busqueda, String origen,
                                                                 String estado, Long proyectoId) {
        return new RmAsignacionService.FiltrosAsignacion(grupo, busqueda, origen, estado, proyectoId, null);
    }

    private static List<Long> ids(List<com.pucp.skillb_ia.dto.RmAsignacionView> filas) {
        return filas.stream().map(v -> v.getAsignacion().getId()).toList();
    }

    @Nested
    class EstadosDelGrupo {

        @Test
        void gruposValidos() {
            assertEquals(List.of("Pendiente RM", "Pendiente PM", "Pendiente RM y PM"), service.estadosDelGrupo("pending"));
            assertEquals(List.of("Activa"), service.estadosDelGrupo("active"));
            assertEquals(List.of("Finalizada", "Rechazada"), service.estadosDelGrupo("history"));
        }

        @Test
        void grupoDesconocidoUsaPendientes() {
            // Recibe el grupo ya normalizado (normalizarFiltros nunca lo deja nulo).
            assertEquals(service.estadosDelGrupo("pending"), service.estadosDelGrupo("otro"));
        }
    }

    @Nested
    class NormalizarFiltros {

        @Test
        void valoresVaciosOInvalidosQuedanEnTodos() {
            RmAsignacionService.FiltrosAsignacion f = service.normalizarFiltros(null, "   ", "x", "y", null);

            assertEquals("pending", f.grupo());
            assertNull(f.busqueda());
            assertNull(f.origen());
            assertNull(f.estado());
            assertNull(f.proyectoId());
            assertNull(f.proyectoNombre());
            verifyNoInteractions(proyectoRepository);
        }

        @Test
        void grupoConEspaciosYBusquedaRecortada() {
            RmAsignacionService.FiltrosAsignacion f = service.normalizarFiltros(" active ", "  ana  ", "rm", "ACTIVA", "");

            assertEquals("active", f.grupo());
            assertEquals("ana", f.busqueda());
            assertEquals("RM", f.origen());
            assertEquals("Activa", f.estado());
        }

        @Test
        void estadoDeOtroGrupoSeDescarta() {
            assertNull(service.normalizarFiltros("history", null, null, "Activa", null).estado());
            assertEquals("Rechazada", service.normalizarFiltros("history", null, null, " rechazada ", null).estado());
        }

        @Test
        void busquedaMayorA100SeCorta() {
            assertEquals(100, service.normalizarFiltros(null, "a".repeat(150), null, null, null).busqueda().length());
        }

        @Test
        void proyectoNoNumericoOInexistenteSeIgnora() {
            assertNull(service.normalizarFiltros(null, null, null, null, "abc").proyectoId());
            when(proyectoRepository.findById(99L)).thenReturn(Optional.empty());
            assertNull(service.normalizarFiltros(null, null, null, null, "99").proyectoId());
        }

        @Test
        void proyectoExistenteTraeIdYNombre() {
            conProyecto();

            RmAsignacionService.FiltrosAsignacion f = service.normalizarFiltros(null, null, null, null, " 5 ");

            assertEquals(PROY_ID, f.proyectoId());
            assertEquals("Proyecto5", f.proyectoNombre());
        }
    }

    @Nested
    class ListarPagina {

        @Test
        void soloIncluyeAsignacionesDelAlcanceDelRm() {
            Proyecto ajeno = proyecto(6L);
            bandeja(List.of(fila(1L, proyecto, EstadoAsignacion.PENDIENTE, OrigenAsignacion.PROPUESTA_PM),
                    fila(2L, ajeno, EstadoAsignacion.PENDIENTE, OrigenAsignacion.PROPUESTA_PM)));
            when(rmAlcanceService.puedeVer(ajeno, RM_ID)).thenReturn(false);

            RmAsignacionService.PaginaAsignaciones pagina = service.listarPagina(filtros("pending", null, null, null, null), "1");

            assertEquals(1, pagina.totalRegistros());
            assertEquals(List.of(1L), ids(pagina.filas()));
        }

        @Test
        void filtraPorGrupoOrigenYEstado() {
            bandeja(List.of(
                    fila(1L, proyecto, EstadoAsignacion.PENDIENTE, OrigenAsignacion.SOLICITADA_COLABORADOR),
                    fila(2L, proyecto, EstadoAsignacion.PENDIENTE, OrigenAsignacion.PROPUESTA_PM),
                    fila(3L, proyecto, EstadoAsignacion.ACTIVA, OrigenAsignacion.PROPUESTA_PM)));

            assertEquals(2, service.listarPagina(filtros("pending", null, null, null, null), null).totalRegistros());
            assertEquals(List.of(3L), ids(service.listarPagina(filtros("active", null, null, null, null), null).filas()));
            assertEquals(List.of(1L), ids(service.listarPagina(
                    filtros("pending", null, "Colaborador", null, null), null).filas()));
            assertEquals(List.of(2L), ids(service.listarPagina(
                    filtros("pending", null, null, "Pendiente RM", null), null).filas()));
            assertEquals(List.of(1L), ids(service.listarPagina(
                    filtros("pending", null, null, "Pendiente RM y PM", null), null).filas()));
        }

        @Test
        void busquedaIgnoraMayusculasYTildesEnColaboradorYProyecto() {
            colaborador.setNombre("José");
            bandeja(List.of(fila(1L, proyecto, EstadoAsignacion.PENDIENTE, OrigenAsignacion.PROPUESTA_PM)));

            assertEquals(1, service.listarPagina(filtros("pending", "JOSE", null, null, null), null).totalRegistros());
            assertEquals(1, service.listarPagina(filtros("pending", "proyecto5", null, null, null), null).totalRegistros());
            assertEquals(0, service.listarPagina(filtros("pending", "nadie", null, null, null), null).totalRegistros());
        }

        @Test
        void paginacionConTextoNoNumericoYFueraDeRango() {
            List<Asignacion> muchas = new ArrayList<>();
            for (long i = 1; i <= 23; i++) {
                muchas.add(fila(i, proyecto, EstadoAsignacion.PENDIENTE, OrigenAsignacion.PROPUESTA_PM));
            }
            bandeja(muchas);

            RmAsignacionService.PaginaAsignaciones texto = service.listarPagina(filtros("pending", null, null, null, null), "abc");
            assertEquals(1, texto.paginaActual());
            assertEquals(3, texto.totalPaginas());
            assertEquals(10, texto.filas().size());

            RmAsignacionService.PaginaAsignaciones alta = service.listarPagina(filtros("pending", null, null, null, null), " 99 ");
            assertEquals(3, alta.paginaActual());
            assertEquals(List.of(21L, 22L, 23L), ids(alta.filas()));

            assertEquals(1, service.listarPagina(filtros("pending", null, null, null, null), "-4").paginaActual());
        }

        @Test
        void sinResultadosDevuelveUnaPaginaVacia() {
            when(rmAlcanceService.idRmActual()).thenReturn(RM_ID);
            when(asignacionRepository.findAllConDetalleOrderByFechaSolicitudDesc()).thenReturn(List.of());

            RmAsignacionService.PaginaAsignaciones pagina = service.listarPagina(filtros("pending", null, null, null, null), "2");

            assertEquals(1, pagina.paginaActual());
            assertEquals(1, pagina.totalPaginas());
            assertTrue(pagina.filas().isEmpty());
        }

        @Test
        void contadoresUsanElConjuntoSinFiltroDeGrupoPeroSiDeProyecto() {
            Asignacion pendientePm = fila(2L, proyecto, EstadoAsignacion.PENDIENTE, OrigenAsignacion.PROPUESTA_RM);
            pendientePm.setAprobadoPorRm(true);
            bandeja(List.of(
                    fila(1L, proyecto, EstadoAsignacion.PENDIENTE, OrigenAsignacion.SOLICITADA_COLABORADOR),
                    pendientePm,
                    fila(3L, proyecto, EstadoAsignacion.ACTIVA, OrigenAsignacion.PROPUESTA_PM),
                    fila(4L, proyecto, EstadoAsignacion.FINALIZADA, OrigenAsignacion.PROPUESTA_PM),
                    fila(5L, proyecto, EstadoAsignacion.RECHAZADA, OrigenAsignacion.SOLICITADA_COLABORADOR),
                    fila(6L, proyecto(6L), EstadoAsignacion.ACTIVA, OrigenAsignacion.PROPUESTA_PM)));

            RmAsignacionService.PaginaAsignaciones pagina = service.listarPagina(
                    filtros("history", null, null, null, PROY_ID), null);

            assertEquals(List.of(4L, 5L), ids(pagina.filas()));
            assertEquals(new RmAsignacionService.ContadoresAsignaciones(1, 1, 1, 1, 2), pagina.contadores());
        }
    }

    @Nested
    class Obtener {

        @Test
        void inexistenteLanza() {
            when(asignacionRepository.findByIdConDetalle(ASIG_ID)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.obtener(ASIG_ID));
            assertEquals("No se encontró la asignación solicitada.", ex.getMessage());
        }

        @Test
        void fueraDelAlcanceDelRmLanzaElMismoMensaje() {
            Asignacion a = fila(ASIG_ID, proyecto, EstadoAsignacion.ACTIVA, OrigenAsignacion.PROPUESTA_PM);
            when(asignacionRepository.findByIdConDetalle(ASIG_ID)).thenReturn(Optional.of(a));
            when(rmAlcanceService.idRmActual()).thenReturn(RM_ID);
            when(rmAlcanceService.puedeVer(proyecto, RM_ID)).thenReturn(false);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.obtener(ASIG_ID));
            assertEquals("No se encontró la asignación solicitada.", ex.getMessage());
        }

        @Test
        void dentroDelAlcanceArmaLaVistaConMaximoPorDefectoSiLaConfiguracionEsInvalida() {
            Asignacion a = fila(ASIG_ID, proyecto, EstadoAsignacion.ACTIVA, OrigenAsignacion.PROPUESTA_PM);
            when(asignacionRepository.findByIdConDetalle(ASIG_ID)).thenReturn(Optional.of(a));
            when(rmAlcanceService.idRmActual()).thenReturn(RM_ID);
            when(rmAlcanceService.puedeVer(proyecto, RM_ID)).thenReturn(true);
            conMaxAsignaciones("abc");
            when(asignacionRepository.countByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA)).thenReturn(1L);

            var vista = service.obtener(ASIG_ID);

            assertEquals(3, vista.getMaxAsignaciones());
            assertEquals(1, vista.getEquipoActual());
            assertEquals(0, vista.getVacantes());
            assertEquals("Usuario2 Prueba", vista.getProjectManagerNombre());
        }
    }

    @Nested
    class ListarYDashboard {

        @Test
        void listarDevuelveTodasLasDelAlcanceConElMaximoConfigurado() {
            bandeja(List.of(fila(1L, proyecto, EstadoAsignacion.ACTIVA, OrigenAsignacion.PROPUESTA_PM),
                    fila(2L, proyecto, EstadoAsignacion.FINALIZADA, OrigenAsignacion.PROPUESTA_PM)));
            conMaxAsignaciones("5");

            var lista = service.listar();

            assertEquals(List.of(1L, 2L), ids(lista));
            assertEquals(5, lista.get(0).getMaxAsignaciones());
        }

        @Test
        void dashboardSoloProyectosActivosEnEsperaOEnRevision() {
            List<Asignacion> todas = new ArrayList<>();
            long id = 1;
            for (EstadoProyecto estado : EstadoProyecto.values()) {
                Proyecto p = proyecto(100L + id);
                p.setEstado(estado);
                p.setPm(pm);
                todas.add(fila(id++, p, EstadoAsignacion.ACTIVA, OrigenAsignacion.PROPUESTA_PM));
            }
            bandeja(todas);

            Set<EstadoProyecto> estados = new HashSet<>();
            service.listarParaDashboard().forEach(v -> estados.add(v.getAsignacion().getProyecto().getEstado()));

            assertEquals(Set.of(EstadoProyecto.ACTIVO, EstadoProyecto.EN_ESPERA, EstadoProyecto.EN_REVISION), estados);
        }
    }

    @Nested
    class NormalizarFiltrosColaborador {

        @Test
        void estadoSinDistinguirMayusculasEInvalidoEsTodos() {
            assertEquals(EstadoAsignacion.ACTIVA,
                    service.normalizarFiltrosColaborador(null, " activa ", null, List.of()).estado());
            assertNull(service.normalizarFiltrosColaborador(null, "otro", null, List.of()).estado());
        }

        @Test
        void anioSoloSiEstaDisponibleYEsNumerico() {
            List<Integer> anios = List.of(2026, 2025);
            assertEquals(2025, service.normalizarFiltrosColaborador(null, null, " 2025 ", anios).anio());
            assertNull(service.normalizarFiltrosColaborador(null, null, "2020", anios).anio());
            assertNull(service.normalizarFiltrosColaborador(null, null, "abc", anios).anio());
        }

        @Test
        void busquedaRecortadaVaciaYLarga() {
            assertEquals("web", service.normalizarFiltrosColaborador("  web ", null, null, List.of()).busqueda());
            assertNull(service.normalizarFiltrosColaborador("   ", null, null, List.of()).busqueda());
            assertEquals(100, service.normalizarFiltrosColaborador("b".repeat(120), null, null, List.of())
                    .busqueda().length());
        }
    }

    @Nested
    class ListarPaginaPorColaborador {

        private Proyecto proyectoDe(Long id, Integer anio) {
            Proyecto p = proyecto(id);
            p.setPm(pm);
            p.setFechaInicio(anio == null ? null : LocalDate.of(anio, 3, 1));
            return p;
        }

        private void historial(List<Asignacion> asignaciones) {
            when(usuarioRepository.findById(COL_ID)).thenReturn(Optional.of(colaborador));
            when(asignacionRepository.findByColaboradorIdConDetalle(COL_ID)).thenReturn(asignaciones);
        }

        @Test
        void colaboradorInexistenteInactivoOConOtroRolLanza() {
            when(usuarioRepository.findById(COL_ID)).thenReturn(Optional.empty());
            assertThrows(IllegalArgumentException.class,
                    () -> service.listarPaginaPorColaborador(COL_ID, null, null, null, null));

            colaborador.setActivo(false);
            when(usuarioRepository.findById(COL_ID)).thenReturn(Optional.of(colaborador));
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.listarPaginaPorColaborador(COL_ID, null, null, null, null));
            assertEquals("No se encontró un colaborador activo válido.", ex.getMessage());

            when(usuarioRepository.findById(RM_ID)).thenReturn(Optional.of(rm));
            assertThrows(IllegalArgumentException.class,
                    () -> service.listarPaginaPorColaborador(RM_ID, null, null, null, null));
            verify(asignacionRepository, never()).findByColaboradorIdConDetalle(anyLong());
        }

        @Test
        void aniosDisponiblesDistintosDescendentesYSinFechasNulas() {
            historial(List.of(
                    fila(1L, proyectoDe(101L, 2025), EstadoAsignacion.ACTIVA, OrigenAsignacion.PROPUESTA_PM),
                    fila(2L, proyectoDe(102L, 2026), EstadoAsignacion.FINALIZADA, OrigenAsignacion.PROPUESTA_PM),
                    fila(3L, proyectoDe(103L, 2025), EstadoAsignacion.RECHAZADA, OrigenAsignacion.PROPUESTA_PM),
                    fila(4L, proyectoDe(104L, null), EstadoAsignacion.ACTIVA, OrigenAsignacion.PROPUESTA_PM)));

            var pagina = service.listarPaginaPorColaborador(COL_ID, null, null, null, null);

            assertEquals(List.of(2026, 2025), pagina.aniosDisponibles());
            assertEquals(4, pagina.totalRegistros());
            assertEquals(4, pagina.totalHistorial());
        }

        @Test
        void filtraPorAnioEstadoYBusquedaDeProyectoOPm() {
            Proyecto web = proyectoDe(101L, 2025);
            web.setNombre("Portal Web");
            historial(List.of(
                    fila(1L, web, EstadoAsignacion.ACTIVA, OrigenAsignacion.PROPUESTA_PM),
                    fila(2L, proyectoDe(102L, 2026), EstadoAsignacion.ACTIVA, OrigenAsignacion.PROPUESTA_PM),
                    fila(3L, proyectoDe(103L, 2025), EstadoAsignacion.FINALIZADA, OrigenAsignacion.PROPUESTA_PM),
                    fila(4L, proyectoDe(104L, null), EstadoAsignacion.ACTIVA, OrigenAsignacion.PROPUESTA_PM)));

            var porAnio = service.listarPaginaPorColaborador(COL_ID, null, null, "2025", null);
            assertEquals(List.of(1L, 3L), ids(porAnio.filas()));
            assertEquals(2025, porAnio.filtros().anio());

            assertEquals(List.of(3L), ids(service.listarPaginaPorColaborador(COL_ID, null, "finalizada", null, null).filas()));
            assertEquals(List.of(1L), ids(service.listarPaginaPorColaborador(COL_ID, "PORTAL", null, null, null).filas()));

            var porPm = service.listarPaginaPorColaborador(COL_ID, "usuario2", null, null, null);
            assertEquals(4, porPm.totalRegistros());

            var sinCoincidencia = service.listarPaginaPorColaborador(COL_ID, "nada", null, null, null);
            assertEquals(0, sinCoincidencia.totalRegistros());
            assertEquals(4, sinCoincidencia.totalHistorial());
        }

        @Test
        void paginacionConTextoNoNumericoYFueraDeRango() {
            List<Asignacion> muchas = new ArrayList<>();
            for (long i = 1; i <= 12; i++) {
                muchas.add(fila(i, proyectoDe(100L + i, 2026), EstadoAsignacion.FINALIZADA, OrigenAsignacion.PROPUESTA_PM));
            }
            historial(muchas);

            var texto = service.listarPaginaPorColaborador(COL_ID, null, null, null, "x");
            assertEquals(1, texto.paginaActual());
            assertEquals(2, texto.totalPaginas());
            assertEquals(10, texto.filas().size());

            var alta = service.listarPaginaPorColaborador(COL_ID, null, null, null, "50");
            assertEquals(2, alta.paginaActual());
            assertEquals(List.of(11L, 12L), ids(alta.filas()));
        }

        @Test
        void sinHistorialDevuelvePaginaVacia() {
            historial(List.of());

            var pagina = service.listarPaginaPorColaborador(COL_ID, null, null, null, null);

            assertTrue(pagina.filas().isEmpty());
            assertEquals(1, pagina.totalPaginas());
            assertEquals(0, pagina.totalHistorial());
            assertTrue(pagina.aniosDisponibles().isEmpty());
        }
    }

    @Nested
    class Vigentes {

        @Test
        void colaboradoresConAsignacionVigenteSoloPendientesYActivas() {
            Usuario otro = colaborador(11L);
            Usuario cerrado = colaborador(12L);
            when(asignacionRepository.findByProyecto(proyecto)).thenReturn(List.of(
                    asignacion(1L, proyecto, colaborador, "5", EstadoAsignacion.PENDIENTE),
                    asignacion(2L, proyecto, otro, "5", EstadoAsignacion.ACTIVA),
                    asignacion(3L, proyecto, cerrado, "5", EstadoAsignacion.FINALIZADA),
                    asignacion(4L, proyecto, cerrado, "5", EstadoAsignacion.RECHAZADA)));

            assertEquals(Set.of(COL_ID, 11L), service.colaboradoresConAsignacionVigente(proyecto));
        }

        @Test
        void proyectosConAsignacionVigenteSoloPendientesYActivas() {
            when(asignacionRepository.findByColaboradorIdConDetalle(COL_ID)).thenReturn(List.of(
                    asignacion(1L, proyecto(101L), colaborador, "5", EstadoAsignacion.PENDIENTE),
                    asignacion(2L, proyecto(102L), colaborador, "5", EstadoAsignacion.ACTIVA),
                    asignacion(3L, proyecto(103L), colaborador, "5", EstadoAsignacion.FINALIZADA),
                    asignacion(4L, proyecto(104L), colaborador, "5", EstadoAsignacion.RECHAZADA)));

            assertEquals(Set.of(101L, 102L), service.proyectosConAsignacionVigente(COL_ID));
        }

        @Test
        void listarProyectosAsignablesActivosOEnEsperaDelAlcance() {
            List<Proyecto> proyectos = new ArrayList<>();
            long id = 100;
            for (EstadoProyecto estado : EstadoProyecto.values()) {
                Proyecto p = proyecto(id++);
                p.setEstado(estado);
                proyectos.add(p);
            }
            Proyecto ajeno = proyecto(999L);
            ajeno.setEstado(EstadoProyecto.ACTIVO);
            proyectos.add(ajeno);
            when(rmAlcanceService.idRmActual()).thenReturn(RM_ID);
            when(proyectoRepository.findAllConPmOrderByFechaCreacionDesc()).thenReturn(proyectos);
            when(rmAlcanceService.puedeVer(any(), eq(RM_ID))).thenAnswer(i -> i.getArgument(0) != ajeno);

            List<Proyecto> asignables = service.listarProyectosAsignables();

            assertEquals(2, asignables.size());
            assertTrue(asignables.stream().allMatch(p -> p.getEstado() == EstadoProyecto.ACTIVO
                    || p.getEstado() == EstadoProyecto.EN_ESPERA));
            assertFalse(asignables.contains(ajeno));
        }
    }
}
