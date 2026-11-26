package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmReporteView;
import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Cargo;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.ActividadRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.service.rm.RmReporteService.FiltrosHoras;
import com.pucp.skillb_ia.service.rm.RmReporteService.FiltrosRecursos;
import com.pucp.skillb_ia.service.rm.RmReporteService.PaginaHoras;
import com.pucp.skillb_ia.service.rm.RmReporteService.PaginaRecursos;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.colaborador;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.proyecto;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.rm;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unitarias de RmReporteService (TASK-087): filtros, periodos, búsqueda, paginación y armado del reporte.
// El RM 1 ve sus proyectos y los que no tienen responsable; el alcance real se simula con la misma regla.
@ExtendWith(MockitoExtension.class)
class RmReporteUnitTests {

    private static final Long RM_ID = 1L;

    @Mock
    private ProyectoRepository proyectoRepository;
    @Mock
    private ActividadRepository actividadRepository;
    @Mock
    private RmAlcanceService rmAlcanceService;

    @InjectMocks
    private RmReporteService service;

    private static Proyecto proyectoReporte(Long id, String nombre, EstadoProyecto estado,
                                            Prioridad prioridad, String presupuesto) {
        Proyecto proyecto = proyecto(id);
        proyecto.setNombre(nombre);
        proyecto.setEstado(estado);
        proyecto.setPrioridad(prioridad);
        proyecto.setPresupuesto(presupuesto == null ? null : new BigDecimal(presupuesto));
        return proyecto;
    }

    private static Usuario colaboradorConCargo(Long id, String nombre, String apellido, String cargo) {
        Usuario usuario = colaborador(id);
        usuario.setNombre(nombre);
        usuario.setApellido(apellido);
        if (cargo != null) {
            Cargo entidad = new Cargo();
            entidad.setNombre(cargo);
            usuario.setCargo(entidad);
        }
        return usuario;
    }

    private static Actividad actividad(Proyecto proyecto, Usuario colaborador, String horas) {
        Actividad actividad = new Actividad();
        actividad.setProyecto(proyecto);
        actividad.setColaborador(colaborador);
        actividad.setHorasEstimadas(horas == null ? null : new BigDecimal(horas));
        actividad.setEstado(EstadoActividad.COMPLETADA);
        return actividad;
    }

    // Simula el repositorio y el alcance: el RM 1 ve los proyectos sin responsable o propios.
    private void simular(List<Proyecto> proyectos, List<Actividad> actividades) {
        when(rmAlcanceService.idRmActual()).thenReturn(RM_ID);
        when(rmAlcanceService.puedeVer(any(), any())).thenAnswer(invocacion -> {
            Proyecto proyecto = invocacion.getArgument(0);
            return proyecto.getRmRevisor() == null || proyecto.getRmRevisor().getId().equals(RM_ID);
        });
        when(proyectoRepository.findAllConPmOrderByFechaCreacionDesc()).thenReturn(proyectos);
        when(actividadRepository.findByEstadoAndFechaEntregaBetweenConDetalle(any(), any(), any()))
                .thenReturn(actividades);
    }

    // Sin proyectos visibles: el alcance no llega a consultarse por proyecto.
    private void sinProyectos() {
        when(rmAlcanceService.idRmActual()).thenReturn(RM_ID);
        when(proyectoRepository.findAllConPmOrderByFechaCreacionDesc()).thenReturn(List.of());
        when(actividadRepository.findByEstadoAndFechaEntregaBetweenConDetalle(any(), any(), any()))
                .thenReturn(List.of());
    }

    private static RmReporteView reporteVacio(YearMonth periodo, List<RmReporteView.ColaboradorReporte> colaboradores) {
        return new RmReporteView(periodo, "texto", null, "", null, null, List.of(), List.of(), colaboradores);
    }

    private static RmReporteView.ColaboradorReporte fila(Long id, String nombre, String cargo, String horas) {
        return new RmReporteView.ColaboradorReporte(id, nombre, cargo, new BigDecimal(horas), 1, List.of());
    }

    @Nested
    class NormalizarFiltrosHoras {

        @Test
        void recortaLaBusquedaYConservaProyectoYColaborador() {
            FiltrosHoras filtros = service.normalizarFiltrosHoras("2026-03", 5L, "  ana  ", 9L);
            assertEquals(new FiltrosHoras("2026-03", 5L, "ana", 9L), filtros);
        }

        @Test
        void periodoInvalidoUsaElMesActualYBusquedaNulaQuedaVacia() {
            FiltrosHoras filtros = service.normalizarFiltrosHoras("2026-13", null, null, null);
            assertEquals(YearMonth.now().toString(), filtros.periodo());
            assertEquals("", filtros.busqueda());
        }
    }

    @Nested
    class NormalizarFiltrosRecursos {

        @Test
        void filtrosValidosSeConservan() {
            FiltrosRecursos filtros = service.normalizarFiltrosRecursos(
                    "2025-01", "2025-03", 4L, "ACTIVO", "ALTA");
            assertEquals(new FiltrosRecursos("2025-01", "2025-03", 4L, "ACTIVO", "ALTA"), filtros);
        }

        @Test
        void estadoYPrioridadInvalidosOVaciosQuedanEnTodos() {
            FiltrosRecursos filtros = service.normalizarFiltrosRecursos("2025-01", null, null, "OTRO", " ");
            assertNull(filtros.estado());
            assertNull(filtros.prioridad());
            assertNull(filtros.hasta());
        }

        @Test
        void hastaAnteriorAlInicioOIgualEsUnSoloMes() {
            assertNull(service.normalizarFiltrosRecursos("2025-05", "2025-02", null, null, null).hasta());
            assertNull(service.normalizarFiltrosRecursos("2025-05", "2025-05", null, null, null).hasta());
            assertNull(service.normalizarFiltrosRecursos("2025-05", "mayo", null, null, null).hasta());
        }

        @Test
        void rangoMayorADoceMesesSeRecorta() {
            FiltrosRecursos filtros = service.normalizarFiltrosRecursos("2023-01", "2024-06", null, null, null);
            assertEquals("2023-12", filtros.hasta());
        }

        @Test
        void hastaFuturoSeRecortaAlMesActual() {
            YearMonth actual = YearMonth.now();
            FiltrosRecursos filtros = service.normalizarFiltrosRecursos(
                    actual.minusMonths(2).toString(), actual.plusMonths(3).toString(), null, null, null);
            assertEquals(actual.toString(), filtros.hasta());
        }
    }

    @Nested
    class ComponerPeriodo {

        @Test
        void uneAnioYMesRecortados() {
            assertEquals("2026-09", service.componerPeriodo(" 2026 ", "09 ", "2020-01"));
        }

        @Test
        void valoresInvalidosOFaltantesUsanElPeriodo() {
            assertEquals("2020-01", service.componerPeriodo("2026", "13", "2020-01"));
            assertEquals("2020-01", service.componerPeriodo("2026", "00", "2020-01"));
            assertEquals("2020-01", service.componerPeriodo("2026", "9", "2020-01"));
            assertEquals("2020-01", service.componerPeriodo("26", "09", "2020-01"));
            assertEquals("2020-01", service.componerPeriodo(null, "09", "2020-01"));
            assertEquals("2020-01", service.componerPeriodo("2026", null, "2020-01"));
        }

        @Test
        void bordesDeMesValidos() {
            assertEquals("2026-01", service.componerPeriodo("2026", "01", null));
            assertEquals("2026-12", service.componerPeriodo("2026", "12", null));
        }
    }

    @Nested
    class ListasDelFiltro {

        @Test
        void listarPeriodosSinSeleccionDaLosUltimosDoceMeses() {
            List<RmReporteView.Opcion> periodos = service.listarPeriodos(null);
            assertEquals(12, periodos.size());
            assertEquals(YearMonth.now().toString(), periodos.get(0).getValor());
            assertEquals(YearMonth.now().minusMonths(11).toString(), periodos.get(11).getValor());
        }

        @Test
        void listarPeriodosAgregaUnaSeleccionAntiguaAlFinal() {
            List<RmReporteView.Opcion> periodos = service.listarPeriodos("2020-01");
            assertEquals(13, periodos.size());
            assertEquals("2020-01", periodos.get(12).getValor());
            assertEquals("Enero 2020", periodos.get(12).getTexto());
        }

        @Test
        void listarAniosIncluyeLosDelReporteEnOrdenDescendente() {
            int actual = YearMonth.now().getYear();
            RmReporteView reporte = reporteVacio(YearMonth.of(2015, 3), List.of());
            List<Integer> anios = service.listarAnios(reporte);
            assertEquals(List.of(actual, actual - 1, actual - 2, actual - 3, actual - 4, 2015), anios);
        }

        @Test
        void listarAniosSinRepetirElAnioActual() {
            RmReporteView reporte = reporteVacio(YearMonth.now(), List.of());
            assertEquals(5, service.listarAnios(reporte).size());
        }

        @Test
        void listarMesesDaDoceMesesConMayuscula() {
            List<RmReporteView.Opcion> meses = service.listarMeses();
            assertEquals(12, meses.size());
            assertEquals("01", meses.get(0).getValor());
            assertEquals("Enero", meses.get(0).getTexto());
            assertEquals("12", meses.get(11).getValor());
            assertEquals("Diciembre", meses.get(11).getTexto());
        }

        @Test
        void listarEstadosYPrioridadesConTextoLegible() {
            assertTrue(service.listarEstados().stream()
                    .anyMatch(o -> o.getValor().equals("EN_REVISION") && o.getTexto().equals("En revision")));
            assertEquals(Prioridad.values().length, service.listarPrioridades().size());
        }
    }

    @Nested
    class FiltrarColaboradores {

        private final RmReporteView reporte = reporteVacio(YearMonth.of(2026, 1), List.of(
                fila(1L, "José Pérez", "Desarrollador", "10"),
                fila(2L, "Ana Ruiz", "Analista QA", "5")));

        @Test
        void busquedaVaciaDevuelveTodos() {
            assertEquals(2, service.filtrarColaboradores(reporte, "  ").size());
            assertEquals(2, service.filtrarColaboradores(reporte, null).size());
        }

        @Test
        void ignoraTildesYMayusculas() {
            List<RmReporteView.ColaboradorReporte> resultado = service.filtrarColaboradores(reporte, " JOSE perez ");
            assertEquals(1, resultado.size());
            assertEquals(1L, resultado.get(0).getId());
        }

        @Test
        void buscaTambienPorCargo() {
            List<RmReporteView.ColaboradorReporte> resultado = service.filtrarColaboradores(reporte, "qa");
            assertEquals(List.of(2L), resultado.stream().map(RmReporteView.ColaboradorReporte::getId).toList());
        }

        @Test
        void sinCoincidenciasDevuelveListaVacia() {
            assertTrue(service.filtrarColaboradores(reporte, "zzz").isEmpty());
        }
    }

    @Nested
    class Generar {

        private Proyecto alfa;
        private Proyecto beta;
        private Proyecto ajeno;
        private Usuario ana;
        private Usuario bruno;
        private Usuario sinNombre;

        private void datos() {
            alfa = proyectoReporte(10L, "Alfa", EstadoProyecto.ACTIVO, Prioridad.ALTA, "5000");
            beta = proyectoReporte(20L, "beta", EstadoProyecto.FINALIZADO, Prioridad.BAJA, null);
            ajeno = proyectoReporte(30L, "Ajeno", EstadoProyecto.ACTIVO, Prioridad.ALTA, "9000");
            ajeno.setRmRevisor(rm(99L));
            ana = colaboradorConCargo(100L, "Ana", "Ruiz", "Analista");
            bruno = colaboradorConCargo(200L, "Bruno", "Soto", null);
            sinNombre = colaboradorConCargo(300L, " ", null, "Dev");
            simular(List.of(alfa, beta, ajeno), List.of(
                    actividad(alfa, ana, "6"),
                    actividad(alfa, ana, "2"),
                    actividad(alfa, bruno, "4"),
                    actividad(beta, bruno, "6"),
                    actividad(beta, sinNombre, "0"),
                    actividad(ajeno, ana, "50")));
        }

        @Test
        void soloIncluyeProyectosDelAlcanceDelRm() {
            datos();
            RmReporteView reporte = service.generar("2026-01", null, null);
            assertEquals(List.of("Alfa", "beta"),
                    reporte.getProyectos().stream().map(RmReporteView.ProyectoReporte::getNombre).toList());
            assertEquals(new BigDecimal("18"), reporte.getHorasTotales());
            assertEquals(new BigDecimal("5000"), reporte.getPresupuestoTotal());
        }

        @Test
        void calculaHorasColaboradoresPorcentajeYClases() {
            datos();
            RmReporteView reporte = service.generar("2026-01", null, null);
            RmReporteView.ProyectoReporte filaAlfa = reporte.getProyectos().get(0);
            assertEquals(new BigDecimal("12"), filaAlfa.getHoras());
            assertEquals(2, filaAlfa.getColaboradores());
            assertEquals(100, filaAlfa.getPorcentajeHoras());
            assertEquals("Activo", filaAlfa.getEstadoTexto());
            assertEquals("bg-green-lt", filaAlfa.getEstadoClase());
            assertEquals("bg-red-lt text-red", filaAlfa.getPrioridadClase());

            RmReporteView.ProyectoReporte filaBeta = reporte.getProyectos().get(1);
            assertEquals(50, filaBeta.getPorcentajeHoras());
            assertEquals(BigDecimal.ZERO, filaBeta.getPresupuesto());
            assertEquals("bg-blue-lt", filaBeta.getEstadoClase());
            assertEquals("bg-blue-lt text-blue", filaBeta.getPrioridadClase());
        }

        @Test
        void detallesAgrupadosYOrdenadosPorColaboradorYProyecto() {
            datos();
            RmReporteView reporte = service.generar("2026-01", null, null);
            List<RmReporteView.DetalleColaborador> detalles = reporte.getDetalles();
            assertEquals(List.of("Ana Ruiz|Alfa", "Bruno Soto|Alfa", "Bruno Soto|beta", "usuario300@prueba.local|beta"),
                    detalles.stream().map(d -> d.getColaborador() + "|" + d.getProyecto()).toList());
            assertEquals(new BigDecimal("8"), detalles.get(0).getHoras());
            assertEquals(2, detalles.get(0).getTareas());
            assertEquals("Cargo sin registrar", detalles.get(1).getCargo());
            assertEquals(3, reporte.getColaboradoresInvolucrados());
        }

        @Test
        void colaboradoresOrdenadosPorHorasDescendentesConDesglose() {
            datos();
            RmReporteView reporte = service.generar("2026-01", null, null);
            List<RmReporteView.ColaboradorReporte> colaboradores = reporte.getColaboradores();
            assertEquals(List.of(200L, 100L, 300L),
                    colaboradores.stream().map(RmReporteView.ColaboradorReporte::getId).toList());
            assertEquals(new BigDecimal("10"), colaboradores.get(0).getHoras());
            assertEquals(2, colaboradores.get(0).getTareas());
            assertEquals(2, colaboradores.get(0).getCantidadProyectos());
        }

        @Test
        void filtraPorProyectoYGuardaSuNombre() {
            datos();
            RmReporteView reporte = service.generar("2026-01", 20L, "");
            assertEquals(1, reporte.getTotalProyectos());
            assertEquals("beta", reporte.getProyectoSeleccionadoNombre());
            assertEquals(List.of(200L, 300L), reporte.getDetalles().stream()
                    .map(RmReporteView.DetalleColaborador::getColaboradorId).toList());
        }

        @Test
        void proyectoFueraDelAlcanceNoTieneNombreNiFilas() {
            datos();
            RmReporteView reporte = service.generar("2026-01", 30L, null);
            assertNull(reporte.getProyectoSeleccionadoNombre());
            assertTrue(reporte.getProyectos().isEmpty());
            assertTrue(reporte.getDetalles().isEmpty());
        }

        @Test
        void filtraPorEstadoYPrioridad() {
            datos();
            RmReporteView porEstado = service.generar("2026-01", null, null, "FINALIZADO", null);
            assertEquals(List.of(20L), porEstado.getProyectos().stream().map(RmReporteView.ProyectoReporte::getId).toList());
            assertEquals("FINALIZADO", porEstado.getEstadoSeleccionado());
            assertEquals("Finalizado", porEstado.getEstadoFiltroTexto());

            RmReporteView porPrioridad = service.generar("2026-01", null, null, null, "ALTA");
            assertEquals(List.of(10L), porPrioridad.getProyectos().stream().map(RmReporteView.ProyectoReporte::getId).toList());
            assertEquals("Alta", porPrioridad.getPrioridadFiltroTexto());
        }

        @Test
        void filtrosInvalidosSeTratanComoTodos() {
            datos();
            RmReporteView reporte = service.generar("2026-01", null, null, "X", "Y");
            assertEquals(2, reporte.getTotalProyectos());
            assertEquals("", reporte.getEstadoSeleccionado());
            assertEquals("", reporte.getPrioridadSeleccionada());
        }

        @Test
        void sinHorasElPorcentajeEsCero() {
            Proyecto vacio = proyectoReporte(10L, "Vacío", EstadoProyecto.EN_ESPERA, Prioridad.MEDIA, "100");
            simular(List.of(vacio), List.of());
            RmReporteView reporte = service.generar("2026-01", null, null);
            assertEquals(0, reporte.getProyectos().get(0).getPorcentajeHoras());
            assertEquals("bg-orange-lt", reporte.getProyectos().get(0).getEstadoClase());
            assertEquals("bg-yellow-lt text-yellow", reporte.getProyectos().get(0).getPrioridadClase());
            assertTrue(reporte.getColaboradores().isEmpty());
        }

        @Test
        void unSoloMesConsultaDelPrimerDiaAlPrimerDiaDelMesSiguiente() {
            sinProyectos();
            RmReporteView reporte = service.generar("2026-02", null, null);
            verify(actividadRepository).findByEstadoAndFechaEntregaBetweenConDetalle(EstadoActividad.COMPLETADA,
                    LocalDateTime.of(2026, 2, 1, 0, 0), LocalDateTime.of(2026, 3, 1, 0, 0));
            assertEquals("Febrero 2026", reporte.getPeriodoTexto());
        }

        @Test
        void rangoConsultaTodosLosMesesYUneLosTextos() {
            sinProyectos();
            RmReporteView reporte = service.generar("2025-11", "2026-01", null, null, null);
            verify(actividadRepository).findByEstadoAndFechaEntregaBetweenConDetalle(EstadoActividad.COMPLETADA,
                    LocalDateTime.of(2025, 11, 1, 0, 0), LocalDateTime.of(2026, 2, 1, 0, 0));
            assertTrue(reporte.esRango());
            assertEquals("Noviembre 2025 – Enero 2026", reporte.getPeriodoTexto());
        }

        @Test
        void sinRmEnSesionNoSeFiltraPorAlcance() {
            Proyecto ajenoSinFiltro = proyectoReporte(30L, "Ajeno", EstadoProyecto.ACTIVO, Prioridad.ALTA, "1");
            ajenoSinFiltro.setRmRevisor(rm(99L));
            when(rmAlcanceService.idRmActual()).thenReturn(null);
            when(rmAlcanceService.puedeVer(any(), any())).thenReturn(true);
            when(proyectoRepository.findAllConPmOrderByFechaCreacionDesc()).thenReturn(List.of(ajenoSinFiltro));
            when(actividadRepository.findByEstadoAndFechaEntregaBetweenConDetalle(any(), any(), any()))
                    .thenReturn(List.of());
            assertEquals(1, service.generar("2026-01", null, null).getTotalProyectos());
            assertEquals(1, service.listarProyectos().size());
        }
    }

    @Nested
    class ListarPaginaRecursos {

        private void doceProyectos() {
            List<Proyecto> proyectos = new ArrayList<>();
            IntStream.rangeClosed(1, 12).forEach(i -> proyectos.add(
                    proyectoReporte((long) i, "P" + i, EstadoProyecto.ACTIVO, Prioridad.MEDIA, "10")));
            simular(proyectos, List.of());
        }

        private FiltrosRecursos filtros() {
            return new FiltrosRecursos("2026-01", null, null, null, null);
        }

        @Test
        void segundaPaginaTieneLosProyectosRestantes() {
            doceProyectos();
            PaginaRecursos pagina = service.listarPaginaRecursos(filtros(), "2", null);
            assertEquals(2, pagina.proyectos().filas().size());
            assertEquals(2, pagina.proyectos().paginaActual());
            assertEquals(2, pagina.proyectos().totalPaginas());
            assertEquals(12, pagina.proyectos().totalRegistros());
            assertEquals(12, pagina.reporte().getTotalProyectos());
        }

        @Test
        void paginaInvalidaCeroONegativaEsLaPrimera() {
            doceProyectos();
            assertEquals(1, service.listarPaginaRecursos(filtros(), "abc", null).proyectos().paginaActual());
            assertEquals(1, service.listarPaginaRecursos(filtros(), "0", null).proyectos().paginaActual());
            assertEquals(1, service.listarPaginaRecursos(filtros(), "-3", null).proyectos().paginaActual());
            assertEquals(10, service.listarPaginaRecursos(filtros(), " ", null).proyectos().filas().size());
        }

        @Test
        void paginaExcesivaEsLaUltima() {
            doceProyectos();
            assertEquals(2, service.listarPaginaRecursos(filtros(), "7", null).proyectos().paginaActual());
            assertEquals(2, service.listarPaginaRecursos(filtros(), "99999999999999", null).proyectos().paginaActual());
        }

        @Test
        void listaVaciaTieneUnaPaginaSinFilas() {
            doceProyectos();
            PaginaRecursos pagina = service.listarPaginaRecursos(filtros(), "1", "5");
            assertEquals(1, pagina.colaboradores().totalPaginas());
            assertEquals(1, pagina.colaboradores().paginaActual());
            assertTrue(pagina.colaboradores().filas().isEmpty());
        }
    }

    @Nested
    class ListarPaginaHoras {

        @Test
        void filtraPorBusquedaAntesDePaginar() {
            Proyecto alfa = proyectoReporte(10L, "Alfa", EstadoProyecto.ACTIVO, Prioridad.ALTA, "100");
            List<Actividad> actividades = new ArrayList<>();
            IntStream.rangeClosed(1, 12).forEach(i -> actividades.add(actividad(alfa,
                    colaboradorConCargo((long) i, "Persona" + i, "Prueba", i <= 11 ? "Dev" : "QA"), String.valueOf(i))));
            simular(List.of(alfa), actividades);

            PaginaHoras todas = service.listarPaginaHoras(new FiltrosHoras("2026-01", null, "", null), "2");
            assertEquals(12, todas.colaboradores().size());
            assertEquals(2, todas.pagina().filas().size());

            PaginaHoras qa = service.listarPaginaHoras(new FiltrosHoras("2026-01", null, "qa", null), "2");
            assertEquals(1, qa.colaboradores().size());
            assertEquals(1, qa.pagina().paginaActual());
            assertEquals(12L, qa.pagina().filas().get(0).getId());
            assertEquals(12, qa.reporte().getColaboradores().size());
        }
    }
}
