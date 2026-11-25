package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.repository.ActividadRepository;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.service.PenalizacionService;
import com.pucp.skillb_ia.service.pm.PmDashboardService.StrikeColaborador;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** Pruebas unitarias de {@link PmDashboardService} con JUnit 5 y Mockito. */
@ExtendWith(MockitoExtension.class)
class PmDashboardUnitTests {

    @Mock
    private ProyectoRepository proyectoRepository;
    @Mock
    private ActividadRepository actividadRepository;
    @Mock
    private AsignacionRepository asignacionRepository;
    @Mock
    private PenalizacionService penalizacionService;

    @InjectMocks
    private PmDashboardService servicio;

    @Test
    void contadoresDeleganEnRepositoriosConLosEstadosCorrectos() {
        Usuario pm = PmDatosPrueba.pm(1L);
        Proyecto p1 = PmDatosPrueba.proyecto(10L, pm);
        Proyecto p2 = PmDatosPrueba.proyecto(11L, pm);
        when(proyectoRepository.findByPmAndEstado(pm, EstadoProyecto.ACTIVO)).thenReturn(List.of(p1, p2));
        when(actividadRepository.countByPmAndEstado(pm, EstadoActividad.EN_REVISION)).thenReturn(4L);
        when(asignacionRepository.countColaboradoresUnicosActivosPorPm(pm, EstadoAsignacion.ACTIVA)).thenReturn(6L);
        when(asignacionRepository.countPendientesRmByPm(pm, EstadoAsignacion.PENDIENTE, OrigenAsignacion.PROPUESTA_PM))
                .thenReturn(2L);

        assertEquals(2L, servicio.getCountProyectosActivos(pm));
        assertEquals(4L, servicio.getCountActividadesPendientes(pm));
        assertEquals(6L, servicio.getCountColaboradoresEquipo(pm));
        assertEquals(2L, servicio.getCountAsignacionesEnRevision(pm));
    }

    @Test
    void getActividadesPendientesDevuelveListaEnRevision() {
        Usuario pm = PmDatosPrueba.pm(1L);
        Proyecto p = PmDatosPrueba.proyecto(10L, pm);
        Actividad act = PmDatosPrueba.actividad(1L, p, PmDatosPrueba.colaborador(2L),
                EstadoActividad.EN_REVISION, LocalDate.of(2026, 3, 1));
        when(actividadRepository.findByPmAndEstadoConDetalle(pm, EstadoActividad.EN_REVISION))
                .thenReturn(List.of(act));

        assertEquals(List.of(act), servicio.getActividadesPendientes(pm));
    }

    @Test
    void getStrikesEquipoOrdenaDeMayorAMenorYDescartaSinStrikes() {
        Usuario pm = PmDatosPrueba.pm(1L);
        Proyecto activo = PmDatosPrueba.proyecto(10L, pm, EstadoProyecto.ACTIVO);
        Proyecto espera = PmDatosPrueba.proyecto(11L, pm, EstadoProyecto.EN_ESPERA);

        Usuario ana = PmDatosPrueba.colaborador(2L);
        ana.setNombre("Ana");
        ana.setApellido("López");
        Usuario bruno = PmDatosPrueba.colaborador(3L);
        bruno.setNombre("Bruno");
        bruno.setApellido("Díaz");
        Usuario carla = PmDatosPrueba.colaborador(4L);
        carla.setNombre("Carla");
        carla.setApellido("Ruiz");

        Asignacion a1 = PmDatosPrueba.asignacion(1L, activo, ana, "20", EstadoAsignacion.ACTIVA);
        Asignacion a2 = PmDatosPrueba.asignacion(2L, activo, bruno, "20", EstadoAsignacion.ACTIVA);
        Asignacion a3 = PmDatosPrueba.asignacion(3L, espera, carla, "20", EstadoAsignacion.ACTIVA);

        when(proyectoRepository.findByPmAndEstado(pm, EstadoProyecto.ACTIVO)).thenReturn(List.of(activo));
        when(proyectoRepository.findByPmAndEstado(pm, EstadoProyecto.EN_ESPERA)).thenReturn(List.of(espera));
        when(asignacionRepository.findByProyectoAndEstado(activo, EstadoAsignacion.ACTIVA)).thenReturn(List.of(a1, a2));
        when(asignacionRepository.findByProyectoAndEstado(espera, EstadoAsignacion.ACTIVA)).thenReturn(List.of(a3));

        when(penalizacionService.contarStrikes(ana, activo)).thenReturn(1);
        when(penalizacionService.contarStrikes(bruno, activo)).thenReturn(0);
        when(penalizacionService.contarStrikes(carla, espera)).thenReturn(3);

        List<StrikeColaborador> resultado = servicio.getStrikesEquipo(pm);

        assertEquals(2, resultado.size());
        assertEquals("Carla Ruiz", resultado.get(0).colaboradorNombre());
        assertEquals("CR", resultado.get(0).iniciales());
        assertTrue(resultado.get(0).enLimite());
        assertEquals(100, resultado.get(0).porcentaje());

        assertEquals("Ana López", resultado.get(1).colaboradorNombre());
        assertEquals("AL", resultado.get(1).iniciales());
        assertFalse(resultado.get(1).enLimite());
        assertEquals(33, resultado.get(1).porcentaje());
    }

    @Test
    void getProyectosQueNecesitanAtencionLimitaACinco() {
        Usuario pm = PmDatosPrueba.pm(1L);
        List<Proyecto> siete = List.of(
                PmDatosPrueba.proyecto(1L, pm), PmDatosPrueba.proyecto(2L, pm),
                PmDatosPrueba.proyecto(3L, pm), PmDatosPrueba.proyecto(4L, pm),
                PmDatosPrueba.proyecto(5L, pm), PmDatosPrueba.proyecto(6L, pm),
                PmDatosPrueba.proyecto(7L, pm));
        when(proyectoRepository.findByPmAndEstado(pm, EstadoProyecto.ACTIVO)).thenReturn(siete);

        List<Proyecto> resultado = servicio.getProyectosQueNecesitanAtencion(pm);

        assertEquals(5, resultado.size());
    }
}
