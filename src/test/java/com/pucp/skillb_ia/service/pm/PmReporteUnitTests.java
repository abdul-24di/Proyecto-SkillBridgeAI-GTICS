package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.dto.PmReporteConsolidadoView;
import com.pucp.skillb_ia.dto.PmReporteView;
import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoEntrega;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.ActividadRepository;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.service.PenalizacionService;
import com.pucp.skillb_ia.service.pm.PmReporteService.Avance;
import com.pucp.skillb_ia.service.pm.PmReporteService.FiltrosConsolidado;
import com.pucp.skillb_ia.service.pm.PmReporteService.PaginaConsolidado;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** Pruebas unitarias de {@link PmReporteService} con JUnit 5 y Mockito. */
@ExtendWith(MockitoExtension.class)
class PmReporteUnitTests {

    @Mock
    private ProyectoRepository proyectoRepository;
    @Mock
    private ActividadRepository actividadRepository;
    @Mock
    private AsignacionRepository asignacionRepository;
    @Mock
    private PenalizacionService penalizacionService;

    @InjectMocks
    private PmReporteService servicio;

    @Nested
    class FiltrosYUtilidades {

        @Test
        void normalizarFiltrosConsolidadoAjustaFechasYValidaEnums() {
            FiltrosConsolidado f = servicio.normalizarFiltrosConsolidado(
                    "2025-01", "2025-03", false, 10L, "activo", "ALTA");

            assertEquals("2025-01", f.periodo());
            assertEquals("2025-03", f.hasta());
            assertEquals("ACTIVO", f.estado());
            assertEquals("ALTA", f.prioridad());

            FiltrosConsolidado invalido = servicio.normalizarFiltrosConsolidado(
                    "2025-05", "2025-02", false, null, "NO_EXISTE", "INVALIDA");
            assertNull(invalido.hasta());
            assertNull(invalido.estado());
            assertNull(invalido.prioridad());
        }

        @Test
        void componerPeriodoYListadosDeOpcionesFuncionan() {
            assertEquals("2025-09", servicio.componerPeriodo("2025", "09", "2024-01"));
            assertEquals("2024-01", servicio.componerPeriodo("2025", "13", "2024-01"));
            assertEquals(12, servicio.listarMeses().size());
            assertEquals(EstadoProyecto.values().length, servicio.listarEstados().size());
            assertEquals(Prioridad.values().length, servicio.listarPrioridades().size());
        }
    }

    @Test
    void generarConsolidadoYListarPaginaCalculanMetricasCorrectamente() {
        Usuario pm = PmDatosPrueba.pm(1L);
        Usuario col = PmDatosPrueba.colaborador(2L);
        Proyecto p1 = PmDatosPrueba.proyecto(10L, pm, EstadoProyecto.ACTIVO);
        p1.setPrioridad(Prioridad.ALTA);
        Proyecto p2 = PmDatosPrueba.proyecto(11L, pm, EstadoProyecto.CANCELADO);

        Asignacion asig = PmDatosPrueba.asignacion(1L, p1, col, "25", EstadoAsignacion.ACTIVA);
        Actividad comp = PmDatosPrueba.actividad(1L, p1, col, EstadoActividad.COMPLETADA, LocalDate.of(2025, 2, 10));
        comp.setEstadoEntrega(EstadoEntrega.A_TIEMPO);
        comp.setHorasEstimadas(new BigDecimal("12"));
        Actividad pend = PmDatosPrueba.actividad(2L, p1, col, EstadoActividad.PENDIENTE, LocalDate.of(2025, 2, 15));
        pend.setHorasEstimadas(new BigDecimal("8"));

        when(proyectoRepository.findByPmOrderByFechaCreacionDesc(pm)).thenReturn(List.of(p1, p2));
        when(actividadRepository.findByProyectoAndActivoTrue(p1)).thenReturn(List.of(comp, pend));
        when(asignacionRepository.findByProyectoAndEstado(p1, EstadoAsignacion.ACTIVA)).thenReturn(List.of(asig));
        when(penalizacionService.contarStrikes(col, p1)).thenReturn(1);

        FiltrosConsolidado filtros = new FiltrosConsolidado("2025-02", null, false, null, "ACTIVO", "ALTA");
        PaginaConsolidado pagina = servicio.listarPaginaConsolidado(pm, filtros, "1", "1");
        PmReporteConsolidadoView rep = pagina.reporte();

        assertEquals(1, rep.getTotalProyectos());
        assertEquals(2, rep.getTotalActividades());
        assertEquals(1, rep.getActividadesCompletadas());
        assertEquals(100, rep.getPuntualidadGlobal());
        assertEquals(new BigDecimal("12"), rep.getHorasCompletadasTotales());
        assertEquals(1, pagina.proyectos().filas().size());
        assertEquals(1, pagina.colaboradores().filas().size());
        assertEquals(1, pagina.colaboradores().filas().get(0).getStrikes());
    }

    @Nested
    class ReporteIndividualYAvance {

        @Test
        void obtenerCalculaEstadosYRechazaProyectoAjeno() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Usuario otro = PmDatosPrueba.pm(2L);
            Usuario col = PmDatosPrueba.colaborador(3L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm);

            Actividad a1 = PmDatosPrueba.actividad(1L, proyecto, col, EstadoActividad.COMPLETADA, LocalDate.of(2026, 3, 1));
            Actividad a2 = PmDatosPrueba.actividad(2L, proyecto, col, EstadoActividad.EN_REVISION, LocalDate.of(2026, 3, 5));
            Asignacion asig = PmDatosPrueba.asignacion(1L, proyecto, col, "20", EstadoAsignacion.ACTIVA);

            when(proyectoRepository.findById(10L)).thenReturn(Optional.of(proyecto));
            when(actividadRepository.findByProyectoAndActivoTrue(proyecto)).thenReturn(List.of(a1, a2));
            when(asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA)).thenReturn(List.of(asig));

            PmReporteView vista = servicio.obtener(10L, pm);

            assertEquals(2, vista.getTotalActividades());
            assertEquals(1, vista.getActividadesCompletadas());
            assertEquals(1, vista.getActividadesEnRevision());
            assertEquals(new BigDecimal("16"), vista.getHorasTotalesEstimadas());
            assertEquals(1, vista.getColaboradores().size());

            assertThrows(SecurityException.class, () -> servicio.obtener(10L, otro));
        }

        @Test
        void avanceFiltraPorMesYGeneraExcelNoVacio() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Usuario col = PmDatosPrueba.colaborador(3L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm);
            Asignacion asig = PmDatosPrueba.asignacion(1L, proyecto, col, "20", EstadoAsignacion.ACTIVA);

            Actividad enMarzo = PmDatosPrueba.actividad(1L, proyecto, col, EstadoActividad.COMPLETADA, LocalDate.of(2026, 3, 10));
            enMarzo.setEstadoEntrega(EstadoEntrega.A_TIEMPO);
            Actividad enAbril = PmDatosPrueba.actividad(2L, proyecto, col, EstadoActividad.COMPLETADA, LocalDate.of(2026, 4, 10));
            enAbril.setEstadoEntrega(EstadoEntrega.TARDIA);

            when(proyectoRepository.findById(10L)).thenReturn(Optional.of(proyecto));
            when(actividadRepository.findByProyectoAndActivoTrue(proyecto)).thenReturn(List.of(enMarzo, enAbril));
            when(asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA)).thenReturn(List.of(asig));
            when(penalizacionService.contarStrikes(col, proyecto)).thenReturn(0);

            Avance avance = servicio.avance(10L, pm, YearMonth.of(2026, 3));

            assertEquals(1, avance.total());
            assertEquals(100, avance.puntualidad());
            byte[] excel = servicio.excelAvance("Proyecto 10", avance);
            assertTrue(excel.length > 0);
        }
    }
}
