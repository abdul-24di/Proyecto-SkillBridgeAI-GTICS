package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.dto.PmCupoHabilidadView;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Habilidad;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.ProyectoHabilidadRequerida;
import com.pucp.skillb_ia.model.SolicitudPersonal;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.NivelDominio;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ProyectoHabilidadRequeridaRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.service.rm.RmSolicitudPersonalService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pruebas unitarias de {@link PmSolicitudPersonalService} con JUnit 5 y Mockito. */
@ExtendWith(MockitoExtension.class)
class PmSolicitudPersonalUnitTests {

    @Mock
    private ProyectoRepository proyectoRepository;
    @Mock
    private ProyectoHabilidadRequeridaRepository habilidadRequeridaRepository;
    @Mock
    private AsignacionRepository asignacionRepository;
    @Mock
    private RmSolicitudPersonalService rmSolicitudPersonalService;

    @InjectMocks
    private PmSolicitudPersonalService servicio;

    @Test
    void listarCuposCalculaOcupadosYOrdenaAlfabeticamente() {
        Usuario pm = PmDatosPrueba.pm(1L);
        Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm);
        Habilidad spring = PmDatosPrueba.habilidad(2L, "Spring Boot");
        Habilidad angular = PmDatosPrueba.habilidad(1L, "Angular");

        Asignacion a = PmDatosPrueba.asignacion(1L, proyecto, PmDatosPrueba.colaborador(5L), "20", EstadoAsignacion.ACTIVA);
        a.setHabilidadSolicitada(spring);

        ProyectoHabilidadRequerida reqSpring = PmDatosPrueba.requisito(proyecto, spring, NivelDominio.AVANZADO, 3);
        ProyectoHabilidadRequerida reqAngular = PmDatosPrueba.requisito(proyecto, angular, null, 2);

        when(asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA)).thenReturn(List.of(a));
        when(habilidadRequeridaRepository.findByProyecto(proyecto)).thenReturn(List.of(reqSpring, reqAngular));

        List<PmCupoHabilidadView> cupos = servicio.listarCupos(proyecto);

        assertEquals(2, cupos.size());
        assertEquals("Angular", cupos.get(0).getHabilidad());
        assertEquals("Cualquier nivel", cupos.get(0).getNivel());
        assertEquals(2, cupos.get(0).getDisponibles());

        assertEquals("Spring Boot", cupos.get(1).getHabilidad());
        assertEquals("Avanzado", cupos.get(1).getNivel());
        assertEquals(1, cupos.get(1).getOcupados());
        assertEquals(2, cupos.get(1).getDisponibles());
    }

    @Nested
    class Solicitar {

        @Test
        void rechazaSiProyectoNoExisteONoPerteneceAlPm() {
            Usuario pm = PmDatosPrueba.pm(1L);
            when(proyectoRepository.findById(99L)).thenReturn(Optional.empty());
            assertThrows(IllegalArgumentException.class,
                    () -> servicio.solicitar(99L, 1, Map.of(), "Perfil", "Msg", pm));

            Proyecto ajeno = PmDatosPrueba.proyecto(10L, PmDatosPrueba.pm(2L));
            when(proyectoRepository.findById(10L)).thenReturn(Optional.of(ajeno));
            assertThrows(SecurityException.class,
                    () -> servicio.solicitar(10L, 1, Map.of(), "Perfil", "Msg", pm));
        }

        @Test
        void rechazaCantidadMayorARequeridaPorElProyecto() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm);
            proyecto.setColaboradoresRequeridos(2);
            when(proyectoRepository.findById(10L)).thenReturn(Optional.of(proyecto));

            assertThrows(IllegalArgumentException.class,
                    () -> servicio.solicitar(10L, 3, Map.of(), "Perfil", "Msg", pm));
        }

        @Test
        void rechazaCantidadNegativaOHabilidadAjenaOExcesoDeCupo() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm);
            proyecto.setColaboradoresRequeridos(5);
            Habilidad java = PmDatosPrueba.habilidad(1L, "Java");
            when(proyectoRepository.findById(10L)).thenReturn(Optional.of(proyecto));
            when(asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA)).thenReturn(List.of());
            when(habilidadRequeridaRepository.findByProyecto(proyecto))
                    .thenReturn(List.of(PmDatosPrueba.requisito(proyecto, java, NivelDominio.INTERMEDIO, 1)));

            assertThrows(IllegalArgumentException.class,
                    () -> servicio.solicitar(10L, 1, Map.of(1L, -1), null, "Msg", pm));
            assertThrows(IllegalArgumentException.class,
                    () -> servicio.solicitar(10L, 1, Map.of(999L, 1), null, "Msg", pm));
            assertThrows(IllegalArgumentException.class,
                    () -> servicio.solicitar(10L, 2, Map.of(1L, 2), null, "Msg", pm));
        }

        @Test
        void exigePerfilesCuandoHayColaboradoresFueraDeHabilidadesDelProyecto() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm);
            proyecto.setColaboradoresRequeridos(4);
            Habilidad java = PmDatosPrueba.habilidad(1L, "Java");
            when(proyectoRepository.findById(10L)).thenReturn(Optional.of(proyecto));
            when(asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA)).thenReturn(List.of());
            when(habilidadRequeridaRepository.findByProyecto(proyecto))
                    .thenReturn(List.of(PmDatosPrueba.requisito(proyecto, java, NivelDominio.BASICO, 2)));

            assertThrows(IllegalArgumentException.class,
                    () -> servicio.solicitar(10L, 2, Map.of(1L, 1), "   ", "Msg", pm));
        }

        @Test
        void armaTextoDePerfilesYDelegaEnRmSolicitudPersonalService() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm);
            proyecto.setColaboradoresRequeridos(4);
            Habilidad java = PmDatosPrueba.habilidad(1L, "Java");
            SolicitudPersonal esperada = new SolicitudPersonal();

            when(proyectoRepository.findById(10L)).thenReturn(Optional.of(proyecto));
            when(asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA)).thenReturn(List.of());
            when(habilidadRequeridaRepository.findByProyecto(proyecto))
                    .thenReturn(List.of(PmDatosPrueba.requisito(proyecto, java, NivelDominio.INTERMEDIO, 2)));
            when(rmSolicitudPersonalService.crearDesdePm(eq(10L), eq(2), anyString(), eq("Urgente"), eq(1L)))
                    .thenReturn(esperada);

            SolicitudPersonal resultado = servicio.solicitar(10L, 2, Map.of(1L, 1), "QA Automation", "Urgente", pm);

            assertSame(esperada, resultado);
            ArgumentCaptor<String> detalleCaptor = ArgumentCaptor.forClass(String.class);
            verify(rmSolicitudPersonalService).crearDesdePm(anyLong(), anyInt(), detalleCaptor.capture(), eq("Urgente"), eq(1L));
            assertTrue(detalleCaptor.getValue().contains("Java · Intermedio: 1 colaborador(es)"));
            assertTrue(detalleCaptor.getValue().contains("Otros perfiles (1):"));
            assertTrue(detalleCaptor.getValue().contains("QA Automation"));
        }
    }
}
