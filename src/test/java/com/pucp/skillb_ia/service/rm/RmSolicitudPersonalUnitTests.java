package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmProyectoView;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.SolicitudPersonal;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.EstadoSolicitudPersonal;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.SolicitudPersonalRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.NotificacionService;
import com.pucp.skillb_ia.service.rm.RmSolicitudPersonalService.FiltrosSolicitud;
import com.pucp.skillb_ia.service.rm.RmSolicitudPersonalService.PaginaSolicitudes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.pm;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.proyecto;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.rm;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.solicitudPersonal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// TASK-084: RmSolicitudPersonalService con sus 8 dependencias simuladas.
@ExtendWith(MockitoExtension.class)
class RmSolicitudPersonalUnitTests {

    @Mock private SolicitudPersonalRepository solicitudRepository;
    @Mock private ProyectoRepository proyectoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private RmProyectoConsultaService proyectoConsultaService;
    @Mock private AuditoriaService auditoriaService;
    @Mock private RmResponsableService rmResponsableService;
    @Mock private NotificacionService notificacionService;
    @Mock private RmAlcanceService rmAlcanceService;

    @InjectMocks
    private RmSolicitudPersonalService service;

    private Usuario rm;
    private Usuario pm;
    private Proyecto proyecto;

    @BeforeEach
    void preparar() {
        rm = rm(1L);
        pm = pm(2L);
        proyecto = proyecto(10L);
        proyecto.setPm(pm);
        proyecto.setEstado(EstadoProyecto.ACTIVO);
    }

    private void conRm() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(rm));
    }

    // Simula findByIdConDetalle y el alcance del RM actual.
    private SolicitudPersonal conSolicitud(EstadoSolicitudPersonal estado) {
        SolicitudPersonal solicitud = solicitudPersonal(50L, proyecto, estado);
        when(solicitudRepository.findByIdConDetalle(50L)).thenReturn(Optional.of(solicitud));
        when(rmAlcanceService.idRmActual()).thenReturn(1L);
        when(rmAlcanceService.puedeVer(proyecto, 1L)).thenReturn(true);
        return solicitud;
    }

    @Nested
    class CrearDesdePm {

        private void conDatos() {
            when(proyectoRepository.findById(10L)).thenReturn(Optional.of(proyecto));
            when(usuarioRepository.findById(2L)).thenReturn(Optional.of(pm));
        }

        @Test
        void creaPendienteAuditaYNotificaAlRm() {
            conDatos();
            when(solicitudRepository.existsByProyectoAndEstadoIn(eq(proyecto), any())).thenReturn(false);
            when(solicitudRepository.save(any(SolicitudPersonal.class))).thenAnswer(inv -> {
                SolicitudPersonal s = inv.getArgument(0);
                s.setId(77L);
                return s;
            });

            SolicitudPersonal guardada = service.crearDesdePm(10L, 3, "  Java  ", "   ", 2L);

            assertEquals(77L, guardada.getId());
            assertSame(proyecto, guardada.getProyecto());
            assertEquals(3, guardada.getCantidadColaboradores());
            assertEquals("Java", guardada.getPerfilesRequeridos());
            assertNull(guardada.getMensajePm());
            assertEquals(EstadoSolicitudPersonal.PENDIENTE, guardada.getEstado());
            assertNull(guardada.getRmResponsable());
            verify(auditoriaService).registrar(eq(pm), eq("CREACION_SOLICITUD_PERSONAL"), eq("SOLICITUD_PERSONAL"),
                    eq(77L), eq("El PM solicitó 3 colaborador(es) para Proyecto10."));
            verify(notificacionService).crearParaRmDelProyecto(eq(proyecto), eq("SOLICITUD_PERSONAL_PENDIENTE"), any(),
                    anyString(), anyString(), eq("SOLICITUD_PERSONAL"), eq(77L));
        }

        @Test
        void textosLargosSeRecortanA1000() {
            conDatos();
            when(solicitudRepository.existsByProyectoAndEstadoIn(eq(proyecto), any())).thenReturn(false);
            when(solicitudRepository.save(any(SolicitudPersonal.class))).thenAnswer(inv -> inv.getArgument(0));

            SolicitudPersonal guardada = service.crearDesdePm(10L, 1, "p".repeat(1200), "m".repeat(1001), 2L);

            assertEquals(1000, guardada.getPerfilesRequeridos().length());
            assertEquals(1000, guardada.getMensajePm().length());
        }

        @Test
        void proyectoInexistenteLanza() {
            when(proyectoRepository.findById(10L)).thenReturn(Optional.empty());
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.crearDesdePm(10L, 1, null, null, 2L));
            assertEquals("No se encontró el proyecto solicitado.", ex.getMessage());
        }

        @Test
        void pmInexistenteLanza() {
            when(proyectoRepository.findById(10L)).thenReturn(Optional.of(proyecto));
            when(usuarioRepository.findById(2L)).thenReturn(Optional.empty());
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.crearDesdePm(10L, 1, null, null, 2L));
            assertEquals("No se encontró el Project Manager.", ex.getMessage());
        }

        @Test
        void otroPmLanza() {
            Usuario otroPm = pm(3L);
            when(proyectoRepository.findById(10L)).thenReturn(Optional.of(proyecto));
            when(usuarioRepository.findById(3L)).thenReturn(Optional.of(otroPm));
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.crearDesdePm(10L, 1, null, null, 3L));
            assertEquals("Solo el Project Manager responsable puede crear la solicitud.", ex.getMessage());
            verify(solicitudRepository, never()).save(any());
        }

        @Test
        void usuarioQueNoEsPmLanza() {
            when(proyectoRepository.findById(10L)).thenReturn(Optional.of(proyecto));
            when(usuarioRepository.findById(1L)).thenReturn(Optional.of(rm));
            assertThrows(IllegalStateException.class, () -> service.crearDesdePm(10L, 1, null, null, 1L));
        }

        @Test
        void proyectoNoActivoLanza() {
            proyecto.setEstado(EstadoProyecto.EN_REVISION);
            conDatos();
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.crearDesdePm(10L, 1, null, null, 2L));
            assertEquals("El proyecto debe estar activo o en espera para solicitar personal.", ex.getMessage());
        }

        @Test
        void cantidadCeroLanza() {
            proyecto.setEstado(EstadoProyecto.EN_ESPERA);
            conDatos();
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.crearDesdePm(10L, 0, null, null, 2L));
            assertEquals("La cantidad solicitada debe ser mayor que cero.", ex.getMessage());
        }

        @Test
        void solicitudAbiertaExistenteLanza() {
            conDatos();
            when(solicitudRepository.existsByProyectoAndEstadoIn(eq(proyecto), any())).thenReturn(true);
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.crearDesdePm(10L, 1, null, null, 2L));
            assertEquals("El proyecto ya tiene una solicitud de personal abierta.", ex.getMessage());
            verify(solicitudRepository, never()).save(any());
            verifyNoInteractions(auditoriaService, notificacionService);
        }
    }

    @Nested
    class IniciarAtencion {

        @Test
        void pasaAEnAtencionNotificaYAudita() {
            conRm();
            SolicitudPersonal solicitud = conSolicitud(EstadoSolicitudPersonal.PENDIENTE);

            service.iniciarAtencion(50L, 1L);

            assertEquals(EstadoSolicitudPersonal.EN_ATENCION, solicitud.getEstado());
            assertSame(rm, solicitud.getRmResponsable());
            assertNotNull(solicitud.getFechaInicioAtencion());
            verify(rmResponsableService).validarResponsable(proyecto, rm);
            verify(solicitudRepository).save(solicitud);
            verify(notificacionService).crear(eq(pm), eq("SOLICITUD_PERSONAL_EN_ATENCION"), any(), anyString(),
                    anyString(), eq("SOLICITUD_PERSONAL"), eq(50L));
            verify(auditoriaService).registrar(eq(rm), eq("INICIO_ATENCION_SOLICITUD"), eq("SOLICITUD_PERSONAL"),
                    eq(50L), anyString());
        }

        @Test
        void noPendienteLanza() {
            conRm();
            conSolicitud(EstadoSolicitudPersonal.EN_ATENCION);
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.iniciarAtencion(50L, 1L));
            assertEquals("Solo una solicitud pendiente puede pasar a atención.", ex.getMessage());
            verify(solicitudRepository, never()).save(any());
            verifyNoInteractions(notificacionService, auditoriaService);
        }

        @Test
        void rmNoResponsableLanza() {
            conRm();
            SolicitudPersonal solicitud = conSolicitud(EstadoSolicitudPersonal.PENDIENTE);
            doThrow(new IllegalStateException("Otro RM")).when(rmResponsableService).validarResponsable(proyecto, rm);
            assertThrows(IllegalStateException.class, () -> service.iniciarAtencion(50L, 1L));
            assertEquals(EstadoSolicitudPersonal.PENDIENTE, solicitud.getEstado());
            verify(solicitudRepository, never()).save(any());
        }

        @Test
        void fueraDelAlcanceLanzaComoNoEncontrada() {
            conRm();
            when(solicitudRepository.findByIdConDetalle(50L))
                    .thenReturn(Optional.of(solicitudPersonal(50L, proyecto, EstadoSolicitudPersonal.PENDIENTE)));
            when(rmAlcanceService.idRmActual()).thenReturn(1L);
            when(rmAlcanceService.puedeVer(proyecto, 1L)).thenReturn(false);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.iniciarAtencion(50L, 1L));
            assertEquals("No se encontró la solicitud de personal.", ex.getMessage());
        }

        @Test
        void solicitudInexistenteLanza() {
            conRm();
            when(solicitudRepository.findByIdConDetalle(50L)).thenReturn(Optional.empty());
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.iniciarAtencion(50L, 1L));
            assertEquals("No se encontró la solicitud de personal.", ex.getMessage());
        }

        @Test
        void usuarioNoRmLanza() {
            when(usuarioRepository.findById(2L)).thenReturn(Optional.of(pm));
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.iniciarAtencion(50L, 2L));
            assertEquals("La operación requiere un Resource Manager.", ex.getMessage());
            verifyNoInteractions(solicitudRepository);
        }

        @Test
        void rmInexistenteLanza() {
            when(usuarioRepository.findById(1L)).thenReturn(Optional.empty());
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.iniciarAtencion(50L, 1L));
            assertEquals("No se encontró el Resource Manager.", ex.getMessage());
        }
    }

    @Nested
    class MarcarAtendida {

        @Test
        void pasaAAtendidaNotificaYAudita() {
            conRm();
            SolicitudPersonal solicitud = conSolicitud(EstadoSolicitudPersonal.EN_ATENCION);

            service.marcarAtendida(50L, 1L);

            assertEquals(EstadoSolicitudPersonal.ATENDIDA, solicitud.getEstado());
            assertSame(rm, solicitud.getRmResponsable());
            assertNotNull(solicitud.getFechaAtencion());
            verify(solicitudRepository).save(solicitud);
            verify(notificacionService).crear(eq(pm), eq("SOLICITUD_PERSONAL_ATENDIDA"), any(), anyString(),
                    anyString(), eq("SOLICITUD_PERSONAL"), eq(50L));
            verify(auditoriaService).registrar(eq(rm), eq("CIERRE_SOLICITUD_PERSONAL"), eq("SOLICITUD_PERSONAL"),
                    eq(50L), anyString());
        }

        @Test
        void pendienteNoPuedeMarcarseAtendida() {
            conRm();
            conSolicitud(EstadoSolicitudPersonal.PENDIENTE);
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.marcarAtendida(50L, 1L));
            assertEquals("Solo una solicitud en atención puede marcarse como atendida.", ex.getMessage());
            verify(solicitudRepository, never()).save(any());
            verifyNoInteractions(notificacionService, auditoriaService);
        }

        @Test
        void yaAtendidaLanza() {
            conRm();
            conSolicitud(EstadoSolicitudPersonal.ATENDIDA);
            assertThrows(IllegalStateException.class, () -> service.marcarAtendida(50L, 1L));
        }
    }

    @Nested
    class NormalizarFiltros {

        @Test
        void vaciosYDesconocidosSonTodos() {
            FiltrosSolicitud filtros = service.normalizarFiltros("   ", "all", "URGENTISIMA");
            assertNull(filtros.busqueda());
            assertNull(filtros.estado());
            assertNull(filtros.prioridad());
            assertEquals(new FiltrosSolicitud(null, null, null), service.normalizarFiltros(null, null, null));
        }

        @Test
        void aceptaOpcionesSinDistinguirMayusculas() {
            String prioridad = Prioridad.values()[0].name();
            FiltrosSolicitud filtros = service.normalizarFiltros(" java ", " en_atencion ", prioridad.toLowerCase());
            assertEquals("java", filtros.busqueda());
            assertEquals("EN_ATENCION", filtros.estado());
            assertEquals(prioridad, filtros.prioridad());
        }

        @Test
        void busquedaSeRecortaA100() {
            assertEquals(100, service.normalizarFiltros("b".repeat(150), null, null).busqueda().length());
        }
    }

    @Nested
    class ListarPaginaYContar {

        private final List<SolicitudPersonal> todas = new ArrayList<>();

        // n solicitudes del proyecto propio; la última es de un proyecto fuera del alcance.
        private void conSolicitudes(EstadoSolicitudPersonal... estados) {
            long id = 1;
            for (EstadoSolicitudPersonal estado : estados) {
                todas.add(solicitudPersonal(id++, proyecto, estado));
            }
            Proyecto ajeno = proyecto(99L);
            todas.add(solicitudPersonal(id, ajeno, EstadoSolicitudPersonal.PENDIENTE));
            when(solicitudRepository.findAllConDetalleOrderByFechaSolicitudDesc()).thenReturn(todas);
            when(rmAlcanceService.idRmActual()).thenReturn(1L);
            when(rmAlcanceService.puedeVer(proyecto, 1L)).thenReturn(true);
            when(rmAlcanceService.puedeVer(ajeno, 1L)).thenReturn(false);
        }

        private void conVistaDeProyecto() {
            RmProyectoView vista = mock(RmProyectoView.class);
            when(vista.getRequisitos()).thenReturn(List.of());
            when(proyectoConsultaService.obtener(10L)).thenReturn(vista);
        }

        @Test
        void contarPendientesSoloDelAlcance() {
            conSolicitudes(EstadoSolicitudPersonal.PENDIENTE, EstadoSolicitudPersonal.PENDIENTE,
                    EstadoSolicitudPersonal.ATENDIDA);
            assertEquals(2L, service.contarPendientes());
        }

        @Test
        void paginaDeSeisYContadoresSobreTodas() {
            conSolicitudes(EstadoSolicitudPersonal.PENDIENTE, EstadoSolicitudPersonal.PENDIENTE,
                    EstadoSolicitudPersonal.PENDIENTE, EstadoSolicitudPersonal.EN_ATENCION,
                    EstadoSolicitudPersonal.EN_ATENCION, EstadoSolicitudPersonal.ATENDIDA,
                    EstadoSolicitudPersonal.ATENDIDA);
            conVistaDeProyecto();

            PaginaSolicitudes pagina = service.listarPagina(new FiltrosSolicitud(null, null, null), "2");

            assertEquals(2, pagina.paginaActual());
            assertEquals(2, pagina.totalPaginas());
            assertEquals(7, pagina.totalRegistros());
            assertEquals(1, pagina.filas().size());
            assertEquals(7L, pagina.filas().get(0).getSolicitud().getId());
            assertEquals(3, pagina.contadores().pendientes());
            assertEquals(2, pagina.contadores().enAtencion());
            assertEquals(2, pagina.contadores().atendidas());
            assertEquals(14, pagina.contadores().totalSolicitados());
        }

        @Test
        void paginaInvalidaOFueraDeRangoSeAjusta() {
            conSolicitudes(EstadoSolicitudPersonal.PENDIENTE);
            conVistaDeProyecto();
            FiltrosSolicitud sinFiltros = new FiltrosSolicitud(null, null, null);

            assertEquals(1, service.listarPagina(sinFiltros, "abc").paginaActual());
            assertEquals(1, service.listarPagina(sinFiltros, "99").paginaActual());
            assertEquals(1, service.listarPagina(sinFiltros, "-3").paginaActual());
            assertEquals(1, service.listarPagina(sinFiltros, null).paginaActual());
        }

        @Test
        void filtraPorEstadoYPrioridadContadoresSinFiltro() {
            conSolicitudes(EstadoSolicitudPersonal.PENDIENTE, EstadoSolicitudPersonal.ATENDIDA);
            Prioridad prioridad = Prioridad.values()[0];
            proyecto.setPrioridad(prioridad);
            conVistaDeProyecto();

            PaginaSolicitudes pagina = service.listarPagina(
                    new FiltrosSolicitud(null, "ATENDIDA", prioridad.name()), "1");

            assertEquals(1, pagina.totalRegistros());
            assertEquals(EstadoSolicitudPersonal.ATENDIDA, pagina.filas().get(0).getSolicitud().getEstado());
            assertEquals(1, pagina.contadores().pendientes());
        }

        @Test
        void proyectoSinPrioridadNoPasaFiltroDePrioridad() {
            conSolicitudes(EstadoSolicitudPersonal.PENDIENTE);
            PaginaSolicitudes pagina = service.listarPagina(
                    new FiltrosSolicitud(null, null, Prioridad.values()[0].name()), "1");
            assertEquals(0, pagina.totalRegistros());
            assertEquals(1, pagina.totalPaginas());
            assertEquals(List.of(), pagina.filas());
            verifyNoInteractions(proyectoConsultaService);
        }

        @Test
        void busquedaPorNombreDeProyectoOPmSinMayusculas() {
            conSolicitudes(EstadoSolicitudPersonal.PENDIENTE);
            conVistaDeProyecto();

            assertEquals(1, service.listarPagina(new FiltrosSolicitud("proyecto10", null, null), "1").totalRegistros());
            assertEquals(1, service.listarPagina(new FiltrosSolicitud("USUARIO2", null, null), "1").totalRegistros());
            assertEquals(0, service.listarPagina(new FiltrosSolicitud("inexistente", null, null), "1").totalRegistros());
        }
    }
}
