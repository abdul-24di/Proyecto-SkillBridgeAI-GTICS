package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.MotivoFinalizacion;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.DisponibilidadService;
import com.pucp.skillb_ia.service.EvaluacionService;
import com.pucp.skillb_ia.service.NotificacionService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pruebas unitarias de {@link PmAsignacionService} con JUnit 5 y Mockito. */
@ExtendWith(MockitoExtension.class)
class PmAsignacionUnitTests {

    @Mock
    private AsignacionRepository asignacionRepository;
    @Mock
    private ProyectoRepository proyectoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private DisponibilidadService disponibilidadService;
    @Mock
    private NotificacionService notificacionService;
    @Mock
    private EvaluacionService evaluacionService;

    @InjectMocks
    private PmAsignacionService servicio;

    @Nested
    class Proponer {

        @Test
        void proponeColaboradorValidoYNotificaAlRm() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Usuario col = PmDatosPrueba.colaborador(2L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm, EstadoProyecto.ACTIVO);

            when(proyectoRepository.findById(10L)).thenReturn(Optional.of(proyecto));
            when(usuarioRepository.findById(2L)).thenReturn(Optional.of(col));
            when(asignacionRepository.existsByProyectoAndColaboradorAndEstadoIn(
                    proyecto, col, EnumSet.of(EstadoAsignacion.ACTIVA, EstadoAsignacion.PENDIENTE)))
                    .thenReturn(false);
            when(asignacionRepository.save(any(Asignacion.class))).thenAnswer(inv -> {
                Asignacion a = inv.getArgument(0);
                a.setId(77L);
                return a;
            });

            Asignacion creada = servicio.proponer(10L, 2L, new BigDecimal("20"), "  Perfil backend  ", pm);

            assertEquals(77L, creada.getId());
            assertEquals(EstadoAsignacion.PENDIENTE, creada.getEstado());
            assertEquals(OrigenAsignacion.PROPUESTA_PM, creada.getOrigen());
            assertTrue(creada.isAprobadoPorPm());
            assertFalse(creada.isAprobadoPorRm());
            assertEquals("Perfil backend", creada.getMensajeSolicitud());
            verify(notificacionService).crearParaRmDelProyecto(eq(proyecto), eq("ASIGNACION_PENDIENTE_RM"),
                    eq(CategoriaNotificacion.ASIGNACION), anyString(), anyString(), eq("ASIGNACION"), eq(77L));
        }

        @Test
        void rechazaProyectoNoAsignableHorasInvalidasODuplicado() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Usuario col = PmDatosPrueba.colaborador(2L);
            Proyecto finalizado = PmDatosPrueba.proyecto(10L, pm, EstadoProyecto.FINALIZADO);

            when(proyectoRepository.findById(10L)).thenReturn(Optional.of(finalizado));
            when(usuarioRepository.findById(2L)).thenReturn(Optional.of(col));

            assertThrows(IllegalStateException.class,
                    () -> servicio.proponer(10L, 2L, new BigDecimal("20"), null, pm));

            Proyecto activo = PmDatosPrueba.proyecto(11L, pm, EstadoProyecto.ACTIVO);
            when(proyectoRepository.findById(11L)).thenReturn(Optional.of(activo));

            assertThrows(IllegalArgumentException.class,
                    () -> servicio.proponer(11L, 2L, BigDecimal.ZERO, null, pm));
            assertThrows(IllegalArgumentException.class,
                    () -> servicio.proponer(11L, 2L, new BigDecimal("169"), null, pm));

            when(asignacionRepository.existsByProyectoAndColaboradorAndEstadoIn(
                    activo, col, EnumSet.of(EstadoAsignacion.ACTIVA, EstadoAsignacion.PENDIENTE)))
                    .thenReturn(true);
            assertThrows(IllegalStateException.class,
                    () -> servicio.proponer(11L, 2L, new BigDecimal("20"), null, pm));
        }
    }

    @Nested
    class AprobarYRechazar {

        @Test
        void aprobarActivaAsignacionCuandoRmYaAproboYRecalculaDisponibilidad() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Usuario col = PmDatosPrueba.colaborador(2L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm, EstadoProyecto.ACTIVO);
            Asignacion a = PmDatosPrueba.asignacion(50L, proyecto, col, "20", EstadoAsignacion.PENDIENTE);
            a.setAprobadoPorRm(true);
            a.setAprobadoPorPm(false);

            when(asignacionRepository.findById(50L)).thenReturn(Optional.of(a));
            when(asignacionRepository.existsByProyectoAndColaboradorAndEstado(proyecto, col, EstadoAsignacion.ACTIVA))
                    .thenReturn(false);

            servicio.aprobar(50L, pm);

            assertTrue(a.isAprobadoPorPm());
            assertEquals(EstadoAsignacion.ACTIVA, a.getEstado());
            assertNotNull(a.getFechaActivacion());
            verify(disponibilidadService).recalcular(col);
            verify(notificacionService).crear(eq(col), eq("ASIGNACION_APROBADA"),
                    eq(CategoriaNotificacion.ASIGNACION), anyString(), anyString(), eq("ASIGNACION"), eq(50L));
            verify(auditoriaService).registrar(eq(pm), eq("APROBAR"), eq("ASIGNACION"), eq(50L), anyString());
        }

        @Test
        void aprobarRechazaPropuestaDelPropioPm() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm, EstadoProyecto.ACTIVO);
            Asignacion a = PmDatosPrueba.asignacion(50L, proyecto, PmDatosPrueba.colaborador(2L),
                    "20", EstadoAsignacion.PENDIENTE);
            a.setOrigen(OrigenAsignacion.PROPUESTA_PM);
            a.setAprobadoPorPm(true);

            when(asignacionRepository.findById(50L)).thenReturn(Optional.of(a));

            assertThrows(IllegalStateException.class, () -> servicio.aprobar(50L, pm));
        }

        @Test
        void rechazarExigeMotivoYNotificaAlColaborador() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Usuario col = PmDatosPrueba.colaborador(2L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm, EstadoProyecto.ACTIVO);
            Asignacion a = PmDatosPrueba.asignacion(50L, proyecto, col, "20", EstadoAsignacion.PENDIENTE);

            when(asignacionRepository.findById(50L)).thenReturn(Optional.of(a));

            assertThrows(IllegalArgumentException.class, () -> servicio.rechazar(50L, "   ", pm));

            servicio.rechazar(50L, "  Perfil distinto al requerido  ", pm);

            assertEquals(EstadoAsignacion.RECHAZADA, a.getEstado());
            assertEquals("Perfil distinto al requerido", a.getMotivoRechazo());
            assertEquals(pm, a.getRechazadoPor());
            verify(notificacionService).crear(eq(col), eq("ASIGNACION_RECHAZADA"),
                    eq(CategoriaNotificacion.ASIGNACION), anyString(), anyString(), eq("ASIGNACION"), eq(50L));
        }
    }

    @Nested
    class Finalizar {

        @Test
        void finalizarDesactivaEvaluaYRecalculaDisponibilidad() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Usuario col = PmDatosPrueba.colaborador(2L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm, EstadoProyecto.ACTIVO);
            Asignacion a = PmDatosPrueba.asignacion(50L, proyecto, col, "20", EstadoAsignacion.ACTIVA);

            when(asignacionRepository.findById(50L)).thenReturn(Optional.of(a));

            servicio.finalizar(50L, 5, "Excelente trabajo", pm);

            assertEquals(EstadoAsignacion.FINALIZADA, a.getEstado());
            assertEquals(MotivoFinalizacion.OTRO, a.getMotivoFinalizacion());
            verify(evaluacionService).evaluarAsignacion(50L, 5, "Excelente trabajo", pm);
            verify(disponibilidadService).recalcular(col);
            verify(notificacionService).crear(eq(col), eq("ASIGNACION_FINALIZADA"),
                    eq(CategoriaNotificacion.ASIGNACION), anyString(), anyString(), eq("ASIGNACION"), eq(50L));
        }

        @Test
        void finalizarSinCalificacionNoLlamaEvaluacionYRechazaNoActiva() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm, EstadoProyecto.ACTIVO);
            Asignacion pendiente = PmDatosPrueba.asignacion(51L, proyecto, PmDatosPrueba.colaborador(2L),
                    "20", EstadoAsignacion.PENDIENTE);
            when(asignacionRepository.findById(51L)).thenReturn(Optional.of(pendiente));

            assertThrows(IllegalStateException.class, () -> servicio.finalizar(51L, null, null, pm));

            Asignacion activa = PmDatosPrueba.asignacion(52L, proyecto, PmDatosPrueba.colaborador(2L),
                    "20", EstadoAsignacion.ACTIVA);
            when(asignacionRepository.findById(52L)).thenReturn(Optional.of(activa));

            servicio.finalizar(52L, null, null, pm);

            verify(evaluacionService, never()).evaluarAsignacion(any(), any(), any(), any());
        }
    }
}
