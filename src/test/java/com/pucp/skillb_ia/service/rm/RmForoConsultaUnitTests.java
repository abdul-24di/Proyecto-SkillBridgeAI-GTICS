package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.ForoPublicacionView;
import com.pucp.skillb_ia.dto.ForoRespuestaView;
import com.pucp.skillb_ia.dto.RmForoView;
import com.pucp.skillb_ia.model.Foro;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ForoRepository;
import com.pucp.skillb_ia.service.ForoDetalleService;
import com.pucp.skillb_ia.service.ForoDetalleService.PermisosForo;
import com.pucp.skillb_ia.service.rm.RmForoConsultaService.FiltrosForo;
import com.pucp.skillb_ia.service.rm.RmForoConsultaService.PaginaForos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.foro;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.limpiarSesion;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.pm;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.proyecto;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.rm;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.sesionComo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unitarias de RmForoConsultaService (TASK-085): filtros, paginación, indicadores, permisos y alcance del RM.
// RmAlcanceService es real (@Spy) y lee la sesión; ForoDetalleService se simula.
@ExtendWith(MockitoExtension.class)
class RmForoConsultaUnitTests {

    @Mock
    private ForoRepository foroRepository;
    @Mock
    private AsignacionRepository asignacionRepository;
    @Spy
    private RmAlcanceService rmAlcanceService;
    @Mock
    private ForoDetalleService foroDetalleService;

    @InjectMocks
    private RmForoConsultaService service;

    private Usuario rm;
    private Usuario otroRm;
    private Usuario pm;

    private static final FiltrosForo SIN_FILTROS = new FiltrosForo(null, null, null);

    @BeforeEach
    void preparar() {
        rm = rm(1L);
        otroRm = rm(2L);
        pm = pm(3L);
        sesionComo(rm);
        lenient().when(foroDetalleService.listar(any(), any(), any(), any())).thenReturn(List.of());
    }

    @AfterEach
    void limpiar() {
        limpiarSesion();
    }

    private Proyecto proyectoDe(Long id, EstadoProyecto estado, Usuario responsable) {
        Proyecto proyecto = proyecto(id);
        proyecto.setPm(pm);
        proyecto.setEstado(estado);
        proyecto.setPrioridad(Prioridad.ALTA);
        proyecto.setRmRevisor(responsable);
        return proyecto;
    }

    private void conForos(Foro... foros) {
        when(foroRepository.findTodosConProyectoYPm()).thenReturn(List.of(foros));
    }

    // Publicación simulada del autor, con su fecha y etiqueta, y las respuestas dadas (autor, fecha).
    private ForoPublicacionView publicacion(Long autorId, LocalDateTime fecha, String etiqueta,
                                            ForoRespuestaView... respuestas) {
        ForoPublicacionView publicacion = mock(ForoPublicacionView.class);
        lenient().when(publicacion.getAutorId()).thenReturn(autorId);
        lenient().when(publicacion.getFechaCreacion()).thenReturn(fecha);
        lenient().when(publicacion.getEtiqueta()).thenReturn(etiqueta);
        lenient().when(publicacion.getTitulo()).thenReturn("Tema " + autorId);
        lenient().when(publicacion.getRespuestas()).thenReturn(List.of(respuestas));
        return publicacion;
    }

    private ForoRespuestaView respuesta(Long autorId, LocalDateTime fecha) {
        ForoRespuestaView respuesta = mock(ForoRespuestaView.class);
        lenient().when(respuesta.getAutorId()).thenReturn(autorId);
        lenient().when(respuesta.getFechaCreacion()).thenReturn(fecha);
        return respuesta;
    }

    @Nested
    class NormalizarFiltros {

        @Test
        void valoresValidosSinDistinguirMayusculas() {
            assertEquals(new FiltrosForo("api", "GENERAL", "recent"),
                    service.normalizarFiltros(" api ", "general", "RECENT"));
            assertEquals(new FiltrosForo(null, "EN_REVISION", "older"),
                    service.normalizarFiltros(null, "en_revision", "older"));
        }

        @Test
        void vaciosAllYDesconocidosSonTodos() {
            assertEquals(SIN_FILTROS, service.normalizarFiltros("  ", "all", "otro"));
            assertEquals(SIN_FILTROS, service.normalizarFiltros(null, null, null));
        }

        @Test
        void busquedaSeRecortaACienCaracteres() {
            assertEquals(100, service.normalizarFiltros("x".repeat(101), null, null).busqueda().length());
        }
    }

    @Nested
    class Listar {

        @Test
        void soloForoGeneralYForosDeProyectosEnAlcance() {
            conForos(foro(1L, null),
                    foro(2L, proyectoDe(10L, EstadoProyecto.ACTIVO, rm)),
                    foro(3L, proyectoDe(11L, EstadoProyecto.ACTIVO, otroRm)),
                    foro(4L, proyectoDe(12L, EstadoProyecto.EN_REVISION, null)));

            assertEquals(List.of(1L, 2L, 4L), service.listar().stream().map(RmForoView::getId).toList());
        }

        @Test
        void foroGeneralPermiteParticiparYDeProyectoEsSoloLectura() {
            Foro general = foro(1L, null);
            Foro deProyecto = foro(2L, proyectoDe(10L, EstadoProyecto.ACTIVO, rm));
            conForos(general, deProyecto);

            List<RmForoView> foros = service.listar();

            assertTrue(foros.get(0).isParticipa());
            assertFalse(foros.get(1).isParticipa());
            verify(foroDetalleService).listar(eq(general), eq(rm), eq(new PermisosForo(true, true, false, false)), eq("fecha"));
            verify(foroDetalleService).listar(eq(deProyecto), isNull(), eq(PermisosForo.SOLO_LECTURA), eq("fecha"));
        }

        @Test
        void foroDeAnunciosSoloPermiteReaccionar() {
            Foro anuncios = foro(1L, null);
            anuncios.setEsPublico(false);
            conForos(anuncios);

            RmForoView vista = service.listar().get(0);

            assertTrue(vista.isAnuncios());
            assertFalse(vista.isParticipa());
            verify(foroDetalleService).listar(eq(anuncios), eq(rm), eq(new PermisosForo(true, false, false, false)), eq("fecha"));
        }

        @Test
        void foroGeneralCuentaParticipantesYUltimaActividad() {
            Foro general = foro(1L, null);
            LocalDateTime base = LocalDateTime.of(2026, 3, 1, 10, 0);
            conForos(general);
            ForoRespuestaView r1 = respuesta(21L, base.plusDays(4));
            ForoRespuestaView r2 = respuesta(20L, base.plusDays(1));
            List<ForoPublicacionView> publicaciones = List.of(
                    publicacion(20L, base, "Java", r1, r2),
                    publicacion(22L, base.plusDays(2), "api"),
                    publicacion(21L, base.plusDays(3), "java"));
            when(foroDetalleService.listar(eq(general), any(), any(), any())).thenReturn(publicaciones);

            RmForoView vista = service.listar().get(0);

            assertEquals(3, vista.getParticipantes());
            assertEquals(3, vista.getIntegrantesActivos(), "en el general, integrantes = participantes");
            assertEquals(base.plusDays(4), vista.getUltimaActividad());
            assertEquals(List.of("api", "Java", "java"), vista.getEtiquetas());
            assertEquals("Comunidad general", vista.getProyectoNombre());
            assertEquals("GENERAL", vista.getProyectoEstadoCodigo());
            assertEquals("Organización", vista.getPmNombre());
        }

        @Test
        void foroDeProyectoSinPublicacionesUsaDatosDelProyecto() {
            Proyecto proyecto = proyectoDe(10L, EstadoProyecto.EN_ESPERA, rm);
            proyecto.setColaboradoresRequeridos(4);
            Foro deProyecto = foro(2L, proyecto);
            conForos(deProyecto);
            when(asignacionRepository.countByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA)).thenReturn(2L);

            RmForoView vista = service.listar().get(0);

            assertEquals(2, vista.getIntegrantesActivos());
            assertEquals(4, vista.getColaboradoresRequeridos());
            assertEquals(deProyecto.getFechaCreacion(), vista.getUltimaActividad());
            assertEquals("En espera", vista.getProyectoEstado());
            assertEquals("bg-orange-lt", vista.getProyectoEstadoClase());
            assertEquals("bg-red-lt text-red", vista.getProyectoPrioridadClase());
            assertEquals("Usuario3 Prueba", vista.getPmNombre());
        }
    }

    @Nested
    class ListarPagina {

        @Test
        void paginaDeCuatroEnCuatroYPaginaInvalidaSegura() {
            List<Foro> foros = new ArrayList<>();
            for (long i = 1; i <= 6; i++) {
                foros.add(foro(i, null));
            }
            conForos(foros.toArray(new Foro[0]));

            PaginaForos segunda = service.listarPagina(SIN_FILTROS, "2");

            assertEquals(List.of(5L, 6L), segunda.foros().stream().map(RmForoView::getId).toList());
            assertEquals(2, segunda.totalPaginas());
            assertEquals(6, segunda.totalRegistros());
            assertEquals(1, service.listarPagina(SIN_FILTROS, "x").paginaActual());
            assertEquals(1, service.listarPagina(SIN_FILTROS, "0").paginaActual());
            assertEquals(2, service.listarPagina(SIN_FILTROS, "50").paginaActual());
        }

        @Test
        void busquedaIgnoraTildesYMayusculas() {
            Foro general = foro(1L, null);
            Foro deProyecto = foro(2L, proyectoDe(10L, EstadoProyecto.ACTIVO, rm));
            deProyecto.setNombre("Migración");
            conForos(general, deProyecto);

            List<Long> ids = service.listarPagina(new FiltrosForo("MIGRACION", null, null), "1")
                    .foros().stream().map(RmForoView::getId).toList();

            assertEquals(List.of(2L), ids);
        }

        @Test
        void filtrosDeEstadoYActividad() {
            Foro general = foro(1L, null);
            Foro activo = foro(2L, proyectoDe(10L, EstadoProyecto.ACTIVO, rm));
            conForos(general, activo);
            List<ForoPublicacionView> recientes = List.of(publicacion(20L, LocalDateTime.now().minusDays(1), null));
            when(foroDetalleService.listar(eq(general), any(), any(), any())).thenReturn(recientes);

            assertEquals(List.of(1L), ids(service.listarPagina(new FiltrosForo(null, "GENERAL", null), "1")));
            assertEquals(List.of(2L), ids(service.listarPagina(new FiltrosForo(null, "ACTIVO", null), "1")));
            assertEquals(List.of(1L), ids(service.listarPagina(new FiltrosForo(null, null, "recent"), "1")));
            assertEquals(List.of(2L), ids(service.listarPagina(new FiltrosForo(null, null, "older"), "1")));
        }

        @Test
        void indicadoresSobreTodosLosForos() {
            Foro general = foro(1L, null);
            Foro activo = foro(2L, proyectoDe(10L, EstadoProyecto.ACTIVO, rm));
            Foro enRevision = foro(3L, proyectoDe(11L, EstadoProyecto.EN_REVISION, rm));
            LocalDateTime fecha = LocalDateTime.of(2026, 4, 1, 8, 0);
            conForos(general, activo, enRevision);
            List<ForoPublicacionView> publicaciones = List.of(
                    publicacion(20L, fecha, null), publicacion(21L, fecha.minusDays(1), null));
            when(foroDetalleService.listar(eq(general), any(), any(), any())).thenReturn(publicaciones);

            PaginaForos pagina = service.listarPagina(new FiltrosForo("nada coincide", null, null), "1");

            assertEquals(0, pagina.totalRegistros());
            assertEquals(3, pagina.indicadores().totalForos());
            assertEquals(1, pagina.indicadores().totalActivos());
            assertEquals(2, pagina.indicadores().totalPublicaciones());
            assertEquals(fecha, pagina.indicadores().ultimaActividad());
        }

        @Test
        void sinForosIndicadoresVacios() {
            conForos();

            PaginaForos pagina = service.listarPagina(SIN_FILTROS, "1");

            assertEquals(0, pagina.indicadores().totalForos());
            assertNull(pagina.indicadores().ultimaActividad());
            assertTrue(pagina.foros().isEmpty());
        }

        private List<Long> ids(PaginaForos pagina) {
            return pagina.foros().stream().map(RmForoView::getId).toList();
        }
    }

    @Nested
    class Obtener {

        @Test
        void inexistenteLanzaExcepcion() {
            when(foroRepository.findByIdConProyectoYPm(9L)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.obtener(9L, null));
            assertEquals("No se encontró el foro solicitado.", ex.getMessage());
        }

        @Test
        void foroDeProyectoDeOtroRmSeTrataComoInexistente() {
            when(foroRepository.findByIdConProyectoYPm(3L))
                    .thenReturn(Optional.of(foro(3L, proyectoDe(11L, EstadoProyecto.ACTIVO, otroRm))));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.obtener(3L, "fecha"));
            assertEquals("No se encontró el foro solicitado.", ex.getMessage());
        }

        @Test
        void normalizaElOrden() {
            Foro general = foro(1L, null);
            when(foroRepository.findByIdConProyectoYPm(1L)).thenReturn(Optional.of(general));

            assertEquals(1L, service.obtener(1L, " VOTOS ").getId());
            verify(foroDetalleService).listar(eq(general), eq(rm), any(), eq("votos"));
        }
    }

    @Nested
    class BuscarIdPorProyecto {

        @Test
        void devuelveElIdOVacio() {
            Proyecto conForo = proyectoDe(10L, EstadoProyecto.ACTIVO, rm);
            Proyecto sinForo = proyectoDe(11L, EstadoProyecto.ACTIVO, rm);
            when(foroRepository.findByProyecto(conForo)).thenReturn(Optional.of(foro(5L, conForo)));
            when(foroRepository.findByProyecto(sinForo)).thenReturn(Optional.empty());

            assertEquals(Optional.of(5L), service.buscarIdPorProyecto(conForo));
            assertEquals(Optional.empty(), service.buscarIdPorProyecto(sinForo));
        }
    }
}
