package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.dto.PmActividadView;
import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoEntrega;
import com.pucp.skillb_ia.repository.ActividadRepository;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.NotificacionService;
import com.pucp.skillb_ia.service.PenalizacionService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pruebas unitarias de {@link PmActividadService} con JUnit 5 y Mockito. */
@ExtendWith(MockitoExtension.class)
class PmActividadUnitTests {

    @Mock
    private ActividadRepository actividadRepository;
    @Mock
    private ProyectoRepository proyectoRepository;
    @Mock
    private AsignacionRepository asignacionRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private PenalizacionService penalizacionService;
    @Mock
    private NotificacionService notificacionService;

    @InjectMocks
    private PmActividadService servicio;

    @Test
    void listarPorProyectoRevisaVencidasYConvierteAVistas() {
        Usuario pm = PmDatosPrueba.pm(1L);
        Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm);
        Actividad act = PmDatosPrueba.actividad(1L, proyecto, PmDatosPrueba.colaborador(2L),
                EstadoActividad.PENDIENTE, LocalDate.of(2026, 4, 10));

        when(proyectoRepository.findById(10L)).thenReturn(Optional.of(proyecto));
        when(actividadRepository.findByProyectoAndActivoTrueOrderByFechaLimiteAsc(proyecto))
                .thenReturn(List.of(act));

        List<PmActividadView> vistas = servicio.listarPorProyecto(10L, pm);

        assertEquals(1, vistas.size());
        verify(penalizacionService).revisarVencidasSinEntregar(List.of(act));
    }

    @Nested
    class Crear {

        @Test
        void validaCamposObligatoriosYRangos() {
            Usuario pm = PmDatosPrueba.pm(1L);
            LocalDate limite = LocalDate.of(2026, 5, 1);

            assertThrows(IllegalArgumentException.class,
                    () -> servicio.crear(null, 2L, "T", "D", new BigDecimal("4"), limite, pm));
            assertThrows(IllegalArgumentException.class,
                    () -> servicio.crear(10L, null, "T", "D", new BigDecimal("4"), limite, pm));
            assertThrows(IllegalArgumentException.class,
                    () -> servicio.crear(10L, 2L, "  ", "D", new BigDecimal("4"), limite, pm));
            assertThrows(IllegalArgumentException.class,
                    () -> servicio.crear(10L, 2L, "T", "D", new BigDecimal("0.2"), limite, pm));
            assertThrows(IllegalArgumentException.class,
                    () -> servicio.crear(10L, 2L, "T", "D", new BigDecimal("1000"), limite, pm));
            assertThrows(IllegalArgumentException.class,
                    () -> servicio.crear(10L, 2L, "T", "D", new BigDecimal("4"), null, pm));
        }

        @Test
        void rechazaColaboradorSinAsignacionActiva() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm);
            Usuario col = PmDatosPrueba.colaborador(2L);

            when(proyectoRepository.findById(10L)).thenReturn(Optional.of(proyecto));
            when(usuarioRepository.findById(2L)).thenReturn(Optional.of(col));
            when(asignacionRepository.existsByProyectoAndColaboradorAndEstado(proyecto, col, EstadoAsignacion.ACTIVA))
                    .thenReturn(false);

            assertThrows(IllegalStateException.class,
                    () -> servicio.crear(10L, 2L, "Diseño API", "Detalle", new BigDecimal("6"),
                            LocalDate.of(2026, 5, 1), pm));
        }

        @Test
        void creaActividadAuditaYNotificaAlColaborador() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm);
            Usuario col = PmDatosPrueba.colaborador(2L);

            when(proyectoRepository.findById(10L)).thenReturn(Optional.of(proyecto));
            when(usuarioRepository.findById(2L)).thenReturn(Optional.of(col));
            when(asignacionRepository.existsByProyectoAndColaboradorAndEstado(proyecto, col, EstadoAsignacion.ACTIVA))
                    .thenReturn(true);
            when(actividadRepository.save(any(Actividad.class))).thenAnswer(inv -> {
                Actividad a = inv.getArgument(0);
                a.setId(55L);
                return a;
            });

            Actividad creada = servicio.crear(10L, 2L, "  Diseño API  ", "  Endpoints REST  ",
                    new BigDecimal("8"), LocalDate.of(2026, 5, 1), pm);

            assertEquals(55L, creada.getId());
            assertEquals("Diseño API", creada.getTitulo());
            assertEquals(EstadoActividad.PENDIENTE, creada.getEstado());
            verify(auditoriaService).registrar(eq(pm), eq("CREAR"), eq("ACTIVIDAD"), eq(55L), anyString());
            verify(notificacionService).crear(eq(col), eq("NUEVA_ACTIVIDAD"), eq(CategoriaNotificacion.ACTIVIDAD),
                    anyString(), anyString(), eq("ACTIVIDAD"), eq(55L));
        }
    }

    @Nested
    class ConfirmarYDevolver {

        @Test
        void confirmarMarcaCompletadaCalculaEntregaTardiaYAplicaStrikeSiCorresponde() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Usuario col = PmDatosPrueba.colaborador(2L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm);
            Actividad act = PmDatosPrueba.actividad(20L, proyecto, col,
                    EstadoActividad.EN_REVISION, LocalDate.of(2026, 4, 1));
            act.setFechaMarcadoRevision(LocalDateTime.of(2026, 4, 5, 12, 0));

            when(actividadRepository.findById(20L)).thenReturn(Optional.of(act));

            servicio.confirmar(20L, true, pm);

            assertEquals(EstadoActividad.COMPLETADA, act.getEstado());
            assertEquals(EstadoEntrega.TARDIA, act.getEstadoEntrega());
            assertNotNull(act.getFechaEntrega());
            verify(penalizacionService).aplicarStrikeManual(eq(act), anyString());
            verify(penalizacionService).verificarYNotificarPorStrikes(col, proyecto);
            verify(notificacionService).crear(eq(col), eq("ACTIVIDAD_CONFIRMADA"),
                    eq(CategoriaNotificacion.ACTIVIDAD), anyString(), anyString(), eq("ACTIVIDAD"), eq(20L));
        }

        @Test
        void confirmarRechazaSiNoEstaEnRevision() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm);
            Actividad act = PmDatosPrueba.actividad(20L, proyecto, PmDatosPrueba.colaborador(2L),
                    EstadoActividad.EN_PROGRESO, LocalDate.of(2026, 4, 1));
            when(actividadRepository.findById(20L)).thenReturn(Optional.of(act));

            assertThrows(IllegalStateException.class, () -> servicio.confirmar(20L, false, pm));
        }

        @Test
        void devolverIncrementaContadorLimpiaRevisionYAplicaStrikeOpcional() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Usuario col = PmDatosPrueba.colaborador(2L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm);
            Actividad act = PmDatosPrueba.actividad(20L, proyecto, col,
                    EstadoActividad.EN_REVISION, LocalDate.of(2026, 4, 1));
            act.setVecesDevuelta(1);
            act.setFechaMarcadoRevision(LocalDateTime.now());

            when(actividadRepository.findById(20L)).thenReturn(Optional.of(act));

            servicio.devolver(20L, "  Faltan pruebas unitarias  ", true, pm);

            assertEquals(EstadoActividad.EN_PROGRESO, act.getEstado());
            assertEquals("Faltan pruebas unitarias", act.getComentarioDevolucion());
            assertEquals(2, act.getVecesDevuelta());
            assertNull(act.getFechaMarcadoRevision());
            verify(penalizacionService).aplicarStrikePorDevolucion(act);
            verify(penalizacionService).verificarYNotificarPorStrikes(col, proyecto);
        }

        @Test
        void devolverExigeComentarioNoVacio() {
            Usuario pm = PmDatosPrueba.pm(1L);

            assertThrows(IllegalArgumentException.class, () -> servicio.devolver(20L, "   ", false, pm));
        }
    }

    @Nested
    class EditarYEliminar {

        @Test
        void editarRechazaActividadCompletadaYActualizaSiEstaPendiente() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm);
            Actividad completada = PmDatosPrueba.actividad(20L, proyecto, PmDatosPrueba.colaborador(2L),
                    EstadoActividad.COMPLETADA, LocalDate.of(2026, 4, 1));
            when(actividadRepository.findById(20L)).thenReturn(Optional.of(completada));

            assertThrows(IllegalStateException.class,
                    () -> servicio.editar(20L, "Nuevo", "Desc", new BigDecimal("5"), LocalDate.of(2026, 5, 1), pm));

            Actividad pendiente = PmDatosPrueba.actividad(21L, proyecto, PmDatosPrueba.colaborador(2L),
                    EstadoActividad.PENDIENTE, LocalDate.of(2026, 4, 1));
            when(actividadRepository.findById(21L)).thenReturn(Optional.of(pendiente));
            when(actividadRepository.save(pendiente)).thenReturn(pendiente);

            Actividad editada = servicio.editar(21L, "Nuevo título", "Desc", new BigDecimal("5"),
                    LocalDate.of(2026, 5, 1), pm);

            assertEquals("Nuevo título", editada.getTitulo());
            verify(auditoriaService).registrar(eq(pm), eq("EDITAR"), eq("ACTIVIDAD"), eq(21L), anyString());
        }

        @Test
        void eliminarDesactivaActividadPendienteYRechazaEnRevisionOAjena() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Usuario otroPm = PmDatosPrueba.pm(9L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm);
            Actividad enRev = PmDatosPrueba.actividad(20L, proyecto, PmDatosPrueba.colaborador(2L),
                    EstadoActividad.EN_REVISION, LocalDate.of(2026, 4, 1));
            when(actividadRepository.findById(20L)).thenReturn(Optional.of(enRev));

            assertThrows(IllegalStateException.class, () -> servicio.eliminar(20L, pm));
            assertThrows(SecurityException.class, () -> servicio.eliminar(20L, otroPm));

            Actividad pendiente = PmDatosPrueba.actividad(21L, proyecto, PmDatosPrueba.colaborador(2L),
                    EstadoActividad.PENDIENTE, LocalDate.of(2026, 4, 1));
            when(actividadRepository.findById(21L)).thenReturn(Optional.of(pendiente));

            servicio.eliminar(21L, pm);

            assertFalse(pendiente.isActivo());
            verify(actividadRepository).save(pendiente);
            verify(auditoriaService).registrar(eq(pm), eq("ELIMINAR"), eq("ACTIVIDAD"), eq(21L), anyString());
            verify(penalizacionService, never()).aplicarStrikeManual(any(), anyString());
        }
    }
}
