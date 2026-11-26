package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmProyectoView;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ProyectoHabilidadRequeridaRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.service.rm.RmProyectoConsultaService.FiltrosProyecto;
import com.pucp.skillb_ia.service.rm.RmProyectoConsultaService.PaginaProyectos;
import com.pucp.skillb_ia.service.rm.RmPresupuestoService.CostoAsignacion;
import com.pucp.skillb_ia.service.rm.RmPresupuestoService.ResumenPresupuesto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.asignacion;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.colaborador;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.limpiarSesion;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.pm;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.proyecto;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.rm;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.sesionComo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

// Unitarias de RmProyectoConsultaService (TASK-085): filtros, paginación, contadores y alcance del RM.
// RmAlcanceService es real (@Spy) y lee la sesión que arma RmDatosPrueba.sesionComo.
@ExtendWith(MockitoExtension.class)
class RmProyectoConsultaUnitTests {

    @Mock
    private ProyectoRepository proyectoRepository;
    @Mock
    private AsignacionRepository asignacionRepository;
    @Mock
    private ProyectoHabilidadRequeridaRepository habilidadRequeridaRepository;
    @Mock
    private RmPresupuestoService presupuestoService;
    @Spy
    private RmAlcanceService rmAlcanceService;

    @InjectMocks
    private RmProyectoConsultaService service;

    private Usuario rm;
    private Usuario otroRm;
    private Usuario pm;

    private static final ResumenPresupuesto RESUMEN = new ResumenPresupuesto(
            new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000"), 0);

    @BeforeEach
    void preparar() {
        rm = rm(1L);
        otroRm = rm(2L);
        pm = pm(3L);
        sesionComo(rm);
        lenient().when(asignacionRepository.findByProyecto(any())).thenReturn(List.of());
        lenient().when(habilidadRequeridaRepository.findByProyecto(any())).thenReturn(List.of());
        lenient().when(presupuestoService.calcularResumen(any())).thenReturn(RESUMEN);
        lenient().when(presupuestoService.calcularCosto(any(), any(), any())).thenReturn(new CostoAsignacion(
                new BigDecimal("20"), BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, true));
    }

    @AfterEach
    void limpiar() {
        limpiarSesion();
    }

    // Proyecto del PM con estado, prioridad, vacantes requeridas y RM responsable (null = sin responsable).
    private Proyecto proyectoDe(Long id, EstadoProyecto estado, Prioridad prioridad, int requeridos, Usuario responsable) {
        Proyecto proyecto = proyecto(id);
        proyecto.setPm(pm);
        proyecto.setEstado(estado);
        proyecto.setPrioridad(prioridad);
        proyecto.setColaboradoresRequeridos(requeridos);
        proyecto.setRmRevisor(responsable);
        return proyecto;
    }

    private Proyecto proyectoDe(Long id) {
        return proyectoDe(id, EstadoProyecto.ACTIVO, Prioridad.MEDIA, 1, rm);
    }

    private void conProyectos(Proyecto... proyectos) {
        when(proyectoRepository.findAllConPmOrderByFechaCreacionDesc()).thenReturn(List.of(proyectos));
    }

    private static final FiltrosProyecto SIN_FILTROS = new FiltrosProyecto(null, null, null, null);

    @Nested
    class NormalizarFiltros {

        @Test
        void valoresValidosSinDistinguirMayusculas() {
            FiltrosProyecto filtros = service.normalizarFiltros("  web  ", "activo", "alta", "WITH");

            assertEquals(new FiltrosProyecto("web", "ACTIVO", "ALTA", "with"), filtros);
        }

        @Test
        void vaciosAllYDesconocidosSonTodos() {
            assertEquals(SIN_FILTROS, service.normalizarFiltros("   ", "all", "", "otro"));
            assertEquals(SIN_FILTROS, service.normalizarFiltros(null, null, null, null));
        }

        @Test
        void busquedaSeRecortaACienCaracteres() {
            FiltrosProyecto filtros = service.normalizarFiltros("a".repeat(150), null, null, null);

            assertEquals(100, filtros.busqueda().length());
        }
    }

    @Nested
    class Listar {

        @Test
        void soloProyectosPropiosOSinResponsable() {
            Proyecto propio = proyectoDe(1L);
            Proyecto ajeno = proyectoDe(2L, EstadoProyecto.ACTIVO, Prioridad.MEDIA, 1, otroRm);
            Proyecto libre = proyectoDe(3L, EstadoProyecto.EN_REVISION, Prioridad.MEDIA, 1, null);
            conProyectos(propio, ajeno, libre);

            List<Long> ids = service.listar().stream().map(v -> v.getProyecto().getId()).toList();

            assertEquals(List.of(1L, 3L), ids);
        }

        @Test
        void sinSesionDeRmNoFiltra() {
            limpiarSesion();
            conProyectos(proyectoDe(1L), proyectoDe(2L, EstadoProyecto.ACTIVO, Prioridad.MEDIA, 1, otroRm));

            assertEquals(2, service.listar().size());
        }

        @Test
        void calculaVacantesYPendientesDelRm() {
            Proyecto proyecto = proyectoDe(1L, EstadoProyecto.ACTIVO, Prioridad.ALTA, 3, rm);
            Asignacion activa = asignacion(1L, proyecto, colaborador(10L), "10", EstadoAsignacion.ACTIVA);
            Asignacion propuestaPm = asignacion(2L, proyecto, colaborador(11L), "10", EstadoAsignacion.PENDIENTE);
            propuestaPm.setOrigen(OrigenAsignacion.PROPUESTA_PM);
            Asignacion solicitada = asignacion(3L, proyecto, colaborador(12L), "10", EstadoAsignacion.PENDIENTE);
            solicitada.setOrigen(OrigenAsignacion.SOLICITADA_COLABORADOR);
            Asignacion yaAprobada = asignacion(4L, proyecto, colaborador(13L), "10", EstadoAsignacion.PENDIENTE);
            yaAprobada.setOrigen(OrigenAsignacion.PROPUESTA_PM);
            yaAprobada.setAprobadoPorRm(true);
            conProyectos(proyecto);
            when(asignacionRepository.findByProyecto(proyecto))
                    .thenReturn(List.of(activa, propuestaPm, solicitada, yaAprobada));

            RmProyectoView vista = service.listar().get(0);

            assertEquals(1, vista.getIntegrantesActivos());
            assertEquals(2, vista.getVacantes());
            assertEquals(2, vista.getPendientesRm());
            assertEquals(1, vista.getEquipo().size());
            assertTrue(vista.getPropuestas().isEmpty(), "el listado no arma propuestas");
            assertEquals("Activo", vista.getEstadoTexto());
            assertEquals("Alta", vista.getPrioridadTexto());
        }

        @Test
        void vacantesNoBajanDeCero() {
            Proyecto proyecto = proyectoDe(1L, EstadoProyecto.ACTIVO, Prioridad.MEDIA, 1, rm);
            conProyectos(proyecto);
            when(asignacionRepository.findByProyecto(proyecto)).thenReturn(List.of(
                    asignacion(1L, proyecto, colaborador(10L), "10", EstadoAsignacion.ACTIVA),
                    asignacion(2L, proyecto, colaborador(11L), "10", EstadoAsignacion.ACTIVA)));

            assertEquals(0, service.listar().get(0).getVacantes());
        }
    }

    @Nested
    class ListarPagina {

        private Proyecto[] proyectos(int cantidad) {
            List<Proyecto> lista = new ArrayList<>();
            for (long i = 1; i <= cantidad; i++) {
                lista.add(proyectoDe(i));
            }
            return lista.toArray(new Proyecto[0]);
        }

        @Test
        void paginaDeSeisEnSeisConservandoElOrden() {
            conProyectos(proyectos(8));

            PaginaProyectos primera = service.listarPagina(SIN_FILTROS, "1");
            PaginaProyectos segunda = service.listarPagina(SIN_FILTROS, "2");

            assertEquals(6, primera.filas().size());
            assertEquals(1L, primera.filas().get(0).getProyecto().getId());
            assertEquals(List.of(7L, 8L), segunda.filas().stream().map(v -> v.getProyecto().getId()).toList());
            assertEquals(2, segunda.totalPaginas());
            assertEquals(8, segunda.totalRegistros());
        }

        @Test
        void paginaInvalidaUsaUnValorSeguro() {
            conProyectos(proyectos(8));

            assertEquals(1, service.listarPagina(SIN_FILTROS, "abc").paginaActual());
            assertEquals(1, service.listarPagina(SIN_FILTROS, "-3").paginaActual());
            assertEquals(1, service.listarPagina(SIN_FILTROS, null).paginaActual());
            assertEquals(2, service.listarPagina(SIN_FILTROS, "99").paginaActual());
        }

        @Test
        void sinResultadosDevuelveUnaPaginaVacia() {
            conProyectos();

            PaginaProyectos pagina = service.listarPagina(SIN_FILTROS, "5");

            assertEquals(1, pagina.paginaActual());
            assertEquals(1, pagina.totalPaginas());
            assertTrue(pagina.filas().isEmpty());
        }

        @Test
        void filtrosDeBusquedaEstadoPrioridadYVacantes() {
            Proyecto web = proyectoDe(1L, EstadoProyecto.ACTIVO, Prioridad.ALTA, 2, rm);
            web.setNombre("Portal Web");
            Proyecto datos = proyectoDe(2L, EstadoProyecto.EN_REVISION, Prioridad.BAJA, 0, rm);
            datos.setNombre("Analítica de datos");
            conProyectos(web, datos);

            assertEquals(List.of(1L), ids(service.listarPagina(new FiltrosProyecto("PORTAL", null, null, null), "1")));
            assertEquals(List.of(2L), ids(service.listarPagina(new FiltrosProyecto("analítica", null, null, null), "1")));
            assertEquals(List.of(2L), ids(service.listarPagina(new FiltrosProyecto(null, "EN_REVISION", null, null), "1")));
            assertEquals(List.of(1L), ids(service.listarPagina(new FiltrosProyecto(null, null, "ALTA", null), "1")));
            assertEquals(List.of(1L), ids(service.listarPagina(new FiltrosProyecto(null, null, null, "with"), "1")));
            assertEquals(List.of(2L), ids(service.listarPagina(new FiltrosProyecto(null, null, null, "full"), "1")));
            assertEquals(List.of(), ids(service.listarPagina(new FiltrosProyecto("portal", "EN_REVISION", null, null), "1")));
        }

        @Test
        void contadoresSobreTodosLosProyectosSinFiltros() {
            Proyecto conVacantes = proyectoDe(1L, EstadoProyecto.ACTIVO, Prioridad.MEDIA, 2, rm);
            Proyecto enRevision = proyectoDe(2L, EstadoProyecto.EN_REVISION, Prioridad.MEDIA, 0, rm);
            Proyecto finalizado = proyectoDe(3L, EstadoProyecto.FINALIZADO, Prioridad.MEDIA, 2, rm);
            Asignacion pendiente = asignacion(1L, conVacantes, colaborador(10L), "10", EstadoAsignacion.PENDIENTE);
            pendiente.setOrigen(OrigenAsignacion.PROPUESTA_PM);
            conProyectos(conVacantes, enRevision, finalizado);
            when(asignacionRepository.findByProyecto(conVacantes)).thenReturn(List.of(pendiente));

            PaginaProyectos pagina = service.listarPagina(new FiltrosProyecto(null, "CANCELADO", null, null), "1");

            assertEquals(0, pagina.totalRegistros());
            assertEquals(3, pagina.contadores().totalProyectos());
            assertEquals(1, pagina.contadores().conVacantes(), "un FINALIZADO no cuenta como vacante para dotar");
            assertEquals(1, pagina.contadores().pendientesRm());
            assertEquals(1, pagina.contadores().enRevision());
        }

        @Test
        void proyectosDeOtroRmNoAparecen() {
            conProyectos(proyectoDe(1L), proyectoDe(2L, EstadoProyecto.ACTIVO, Prioridad.MEDIA, 1, otroRm));

            PaginaProyectos pagina = service.listarPagina(SIN_FILTROS, "1", rm.getId());

            assertEquals(List.of(1L), ids(pagina));
            assertEquals(1, pagina.contadores().totalProyectos());
        }

        private List<Long> ids(PaginaProyectos pagina) {
            return pagina.filas().stream().map(v -> v.getProyecto().getId()).toList();
        }
    }

    @Nested
    class Obtener {

        @Test
        void inexistenteLanzaExcepcion() {
            when(proyectoRepository.findById(99L)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.obtener(99L));
            assertEquals("No se encontró el proyecto solicitado.", ex.getMessage());
        }

        @Test
        void proyectoDeOtroRmSeTrataComoInexistente() {
            Proyecto ajeno = proyectoDe(2L, EstadoProyecto.ACTIVO, Prioridad.MEDIA, 1, otroRm);
            when(proyectoRepository.findById(2L)).thenReturn(Optional.of(ajeno));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.obtener(2L));
            assertEquals("No se encontró el proyecto solicitado.", ex.getMessage());
        }

        @Test
        void detalleOrdenaPropuestasYParticipantes() {
            Proyecto proyecto = proyectoDe(1L, EstadoProyecto.ACTIVO, Prioridad.MEDIA, 3, null);
            LocalDateTime base = LocalDateTime.of(2026, 2, 1, 9, 0);
            Asignacion antigua = asignacion(1L, proyecto, colaborador(10L), "10", EstadoAsignacion.ACTIVA);
            antigua.setOrigen(OrigenAsignacion.PROPUESTA_PM);
            antigua.setFechaSolicitud(base);
            antigua.setFechaActivacion(base.plusDays(5));
            Asignacion reciente = asignacion(2L, proyecto, colaborador(11L), "10", EstadoAsignacion.FINALIZADA);
            reciente.setOrigen(OrigenAsignacion.PROPUESTA_PM);
            reciente.setFechaSolicitud(base.plusDays(3));
            reciente.setFechaActivacion(base.plusDays(1));
            Asignacion pendiente = asignacion(3L, proyecto, colaborador(12L), "10", EstadoAsignacion.PENDIENTE);
            pendiente.setOrigen(OrigenAsignacion.PROPUESTA_PM);
            pendiente.setFechaSolicitud(null);
            when(proyectoRepository.findById(1L)).thenReturn(Optional.of(proyecto));
            when(asignacionRepository.findByProyecto(proyecto)).thenReturn(List.of(antigua, reciente, pendiente));

            RmProyectoView vista = service.obtener(1L);

            assertEquals(List.of(2L, 1L, 3L), vista.getPropuestas().stream()
                    .map(RmProyectoView.AsignacionProyecto::asignacionId).toList(),
                    "más recientes primero y sin fecha al final");
            assertEquals(List.of(2L, 1L), vista.getParticipantes().stream()
                    .map(RmProyectoView.AsignacionProyecto::asignacionId).toList(),
                    "solo activadas, por fecha de activación");
            assertEquals("Usuario3 Prueba", vista.getPmNombre());
        }

        @Test
        void sinRequisitosNiNombreUsaAlternativas() {
            Proyecto proyecto = proyectoDe(1L);
            pm.setNombre(null);
            pm.setApellido(" ");
            when(proyectoRepository.findById(1L)).thenReturn(Optional.of(proyecto));

            RmProyectoView vista = service.obtener(1L);

            assertEquals(pm.getCorreo(), vista.getPmNombre());
            assertTrue(vista.getRequisitos().isEmpty());
        }
    }
}
