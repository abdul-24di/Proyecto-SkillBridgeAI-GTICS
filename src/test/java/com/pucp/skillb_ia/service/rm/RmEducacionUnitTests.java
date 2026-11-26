package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmEducacionView;
import com.pucp.skillb_ia.model.Educacion;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoCertificado;
import com.pucp.skillb_ia.repository.EducacionRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.NotificacionService;
import com.pucp.skillb_ia.service.rm.RmEducacionService.FiltrosEducacion;
import com.pucp.skillb_ia.service.rm.RmEducacionService.PaginaEducacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.colaborador;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.educacion;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.pm;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.rm;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Revisión de formación académica por el RM: aprobar/rechazar, alcance, filtros y paginación.
@ExtendWith(MockitoExtension.class)
class RmEducacionUnitTests {

    @Mock
    private EducacionRepository educacionRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private NotificacionService notificacionService;
    @Mock
    private RmAlcanceService rmAlcanceService;

    @InjectMocks
    private RmEducacionService service;

    private Usuario yo;
    private Usuario colab;
    private Educacion edu;

    @BeforeEach
    void preparar() {
        yo = rm(1L);
        colab = colaborador(10L);
        edu = educacion(100L, colab, yo);
    }

    // Deja la formación visible (o no) para el RM actual.
    private void encontrar(Educacion educacion, boolean visible) {
        when(educacionRepository.findByIdConDetalle(educacion.getId())).thenReturn(Optional.of(educacion));
        when(rmAlcanceService.idRmActual()).thenReturn(yo.getId());
        when(rmAlcanceService.puedeVerRevision(educacion.getRmResponsable(), yo.getId())).thenReturn(visible);
    }

    private void soyRm() {
        when(usuarioRepository.findById(yo.getId())).thenReturn(Optional.of(yo));
    }

    private RmEducacionView vista(Educacion educacion) {
        Usuario c = educacion.getColaborador();
        return new RmEducacionView(educacion, c.getNombre() + " " + c.getApellido(), "UP", "Cargo", "Junior", "—", 0);
    }

    private List<RmEducacionView> vistas(int cantidad) {
        List<RmEducacionView> lista = new ArrayList<>();
        for (int i = 1; i <= cantidad; i++) {
            lista.add(vista(educacion((long) i, colab, yo)));
        }
        return lista;
    }

    @Nested
    class Aprobar {

        @Test
        void pendienteSeApruebaConRevisorFechaNotificacionYAuditoria() {
            edu.setMotivoRechazo("viejo");
            soyRm();
            encontrar(edu, true);

            service.aprobar(edu.getId(), yo.getId());

            assertEquals(EstadoCertificado.APROBADO, edu.getEstado());
            assertNull(edu.getMotivoRechazo());
            assertSame(yo, edu.getRevisadoPor());
            assertNotNull(edu.getFechaRevision());
            verify(educacionRepository).save(edu);
            verify(notificacionService).crear(eq(colab), eq("EDUCACION_APROBADA"), eq(CategoriaNotificacion.HABILIDAD),
                    eq("Formación académica aprobada"), contains("fue aprobada"), eq("EDUCACION"), eq(edu.getId()));
            verify(auditoriaService).registrar(eq(yo), eq("APROBACION_EDUCACION"), eq("EDUCACION"), eq(edu.getId()),
                    contains("Se aprobó"));
        }

        @ParameterizedTest
        @EnumSource(value = EstadoCertificado.class, names = "PENDIENTE", mode = EnumSource.Mode.EXCLUDE)
        void yaRevisadaLanza(EstadoCertificado estado) {
            edu.setEstado(estado);
            soyRm();
            encontrar(edu, true);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.aprobar(edu.getId(), yo.getId()));
            assertEquals("Esta formación académica ya fue revisada.", ex.getMessage());
            verify(educacionRepository, never()).save(any());
            verifyNoInteractions(notificacionService, auditoriaService);
        }

        @Test
        void fueraDeAlcanceLanzaComoNoEncontrada() {
            soyRm();
            encontrar(edu, false);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.aprobar(edu.getId(), yo.getId()));
            assertEquals("No se encontró la formación académica solicitada.", ex.getMessage());
            verify(educacionRepository, never()).save(any());
        }

        @Test
        void inexistenteLanza() {
            soyRm();
            when(educacionRepository.findByIdConDetalle(99L)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.aprobar(99L, yo.getId()));
            assertEquals("No se encontró la formación académica solicitada.", ex.getMessage());
        }

        @Test
        void inactivaLanzaComoNoEncontrada() {
            edu.setActivo(false);
            soyRm();
            when(educacionRepository.findByIdConDetalle(edu.getId())).thenReturn(Optional.of(edu));

            assertThrows(IllegalArgumentException.class, () -> service.aprobar(edu.getId(), yo.getId()));
            verify(educacionRepository, never()).save(any());
        }

        @Test
        void rmInexistenteLanza() {
            when(usuarioRepository.findById(5L)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.aprobar(edu.getId(), 5L));
            assertEquals("No se encontró el Resource Manager.", ex.getMessage());
            verifyNoInteractions(educacionRepository);
        }

        @Test
        void usuarioQueNoEsRmLanza() {
            Usuario otro = pm(2L);
            when(usuarioRepository.findById(2L)).thenReturn(Optional.of(otro));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.aprobar(edu.getId(), 2L));
            assertEquals("La operación requiere un Resource Manager.", ex.getMessage());
            verifyNoInteractions(educacionRepository);
        }
    }

    @Nested
    class Rechazar {

        @Test
        void pendienteSeRechazaConMotivoRecortado() {
            soyRm();
            encontrar(edu, true);

            service.rechazar(edu.getId(), "  Documento ilegible  ", yo.getId());

            assertEquals(EstadoCertificado.RECHAZADO, edu.getEstado());
            assertEquals("Documento ilegible", edu.getMotivoRechazo());
            assertSame(yo, edu.getRevisadoPor());
            assertNotNull(edu.getFechaRevision());
            verify(educacionRepository).save(edu);
            verify(notificacionService).crear(eq(colab), eq("EDUCACION_RECHAZADA"), eq(CategoriaNotificacion.HABILIDAD),
                    eq("Formación académica rechazada"), contains("Motivo: Documento ilegible"),
                    eq("EDUCACION"), eq(edu.getId()));
            verify(auditoriaService).registrar(eq(yo), eq("RECHAZO_EDUCACION"), eq("EDUCACION"), eq(edu.getId()),
                    contains("Motivo: Documento ilegible"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void motivoVacioLanza(String motivo) {
            soyRm();
            encontrar(edu, true);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.rechazar(edu.getId(), motivo, yo.getId()));
            assertEquals("Debes indicar el motivo del rechazo.", ex.getMessage());
            assertEquals(EstadoCertificado.PENDIENTE, edu.getEstado());
            verify(educacionRepository, never()).save(any());
            verifyNoInteractions(notificacionService, auditoriaService);
        }

        @Test
        void motivoNuloLanza() {
            soyRm();
            encontrar(edu, true);

            assertThrows(IllegalArgumentException.class, () -> service.rechazar(edu.getId(), null, yo.getId()));
            verify(educacionRepository, never()).save(any());
        }

        @Test
        void motivoDe300SeAceptaYDe301Lanza() {
            soyRm();
            encontrar(edu, true);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.rechazar(edu.getId(), "a".repeat(301), yo.getId()));
            assertEquals("El motivo del rechazo no puede superar los 300 caracteres.", ex.getMessage());

            service.rechazar(edu.getId(), "a".repeat(300), yo.getId());
            assertEquals(300, edu.getMotivoRechazo().length());
        }

        @Test
        void yaRevisadaLanza() {
            edu.setEstado(EstadoCertificado.APROBADO);
            soyRm();
            encontrar(edu, true);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.rechazar(edu.getId(), "motivo", yo.getId()));
            assertEquals("Esta formación académica ya fue revisada.", ex.getMessage());
            verify(educacionRepository, never()).save(any());
        }

        @Test
        void fueraDeAlcanceLanza() {
            soyRm();
            encontrar(edu, false);

            assertThrows(IllegalArgumentException.class,
                    () -> service.rechazar(edu.getId(), "motivo", yo.getId()));
            verifyNoInteractions(notificacionService, auditoriaService);
        }
    }

    @Nested
    class ContarPendientes {

        @Test
        void cuentaSoloLasVisiblesParaElRm() {
            Usuario otroRm = rm(2L);
            Educacion mia = educacion(1L, colab, yo);
            Educacion ajena = educacion(2L, colab, otroRm);
            when(rmAlcanceService.idRmActual()).thenReturn(yo.getId());
            when(educacionRepository.findActivasByEstadoConDetalle(EstadoCertificado.PENDIENTE))
                    .thenReturn(List.of(mia, ajena));
            when(rmAlcanceService.puedeVerRevision(yo, yo.getId())).thenReturn(true);
            when(rmAlcanceService.puedeVerRevision(otroRm, yo.getId())).thenReturn(false);

            assertEquals(1, service.contarPendientes());
        }
    }

    @Nested
    class ObtenerUrlDocumento {

        @Test
        void devuelveLaUrl() {
            encontrar(edu, true);
            assertEquals("/uploads/educacion/100.pdf", service.obtenerUrlDocumento(edu.getId()));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "  "})
        void sinDocumentoLanza(String url) {
            edu.setArchivoUrl(url);
            encontrar(edu, true);
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.obtenerUrlDocumento(edu.getId()));
            assertEquals("Esta formación académica no tiene un documento adjunto.", ex.getMessage());
        }

        @Test
        void documentoNuloLanza() {
            edu.setArchivoUrl(null);
            encontrar(edu, true);
            assertThrows(IllegalStateException.class, () -> service.obtenerUrlDocumento(edu.getId()));
        }
    }

    @Nested
    class NormalizarFiltros {

        private final List<String> instituciones = List.of("PUCP", "UNI");

        @Test
        void valoresNulosQuedanSinFiltro() {
            FiltrosEducacion f = service.normalizarFiltros(null, null, instituciones);
            assertNull(f.busqueda());
            assertNull(f.institucion());
            assertNull(f.estado());
            assertNull(f.colaboradorId());
        }

        @Test
        void busquedaEnBlancoQuedaNula() {
            assertNull(service.normalizarFiltros("   ", null, instituciones).busqueda());
        }

        @Test
        void busquedaSeRecortaYLimitaA100() {
            assertEquals("ana", service.normalizarFiltros("  ana ", null, instituciones).busqueda());
            assertEquals(100, service.normalizarFiltros("x".repeat(150), null, instituciones).busqueda().length());
        }

        @Test
        void institucionValidaSinImportarMayusculasDevuelveLaOpcion() {
            assertEquals("PUCP", service.normalizarFiltros(null, " pucp ", instituciones).institucion());
        }

        @Test
        void institucionInvalidaSeDescarta() {
            assertNull(service.normalizarFiltros(null, "Harvard", instituciones).institucion());
        }
    }

    @Nested
    class NormalizarFiltrosHistorial {

        private List<RmEducacionView> revisadas;

        @BeforeEach
        void preparar() {
            revisadas = List.of(vista(edu));
        }

        @ParameterizedTest
        @ValueSource(strings = {"aprobado", " APROBADO "})
        void aceptaAprobado(String estado) {
            assertEquals(EstadoCertificado.APROBADO,
                    service.normalizarFiltrosHistorial(null, null, estado, null, revisadas).estado());
        }

        @Test
        void aceptaRechazado() {
            assertEquals(EstadoCertificado.RECHAZADO,
                    service.normalizarFiltrosHistorial(null, null, "rechazado", null, revisadas).estado());
        }

        @ParameterizedTest
        @ValueSource(strings = {"PENDIENTE", "otro", ""})
        void otrosEstadosSeDescartan(String estado) {
            assertNull(service.normalizarFiltrosHistorial(null, null, estado, null, revisadas).estado());
        }

        @Test
        void colaboradorDelHistorialSeAcepta() {
            assertEquals(10L, service.normalizarFiltrosHistorial(null, null, null, " 10 ", revisadas).colaboradorId());
        }

        @ParameterizedTest
        @ValueSource(strings = {"99", "abc", ""})
        void colaboradorAjenoOInvalidoSeDescarta(String colaborador) {
            assertNull(service.normalizarFiltrosHistorial(null, null, null, colaborador, revisadas).colaboradorId());
        }

        @Test
        void institucionSeValidaContraElHistorial() {
            FiltrosEducacion f = service.normalizarFiltrosHistorial("x", "institucion100", null, null, revisadas);
            assertEquals("Institucion100", f.institucion());
            assertEquals("x", f.busqueda());
        }
    }

    @Nested
    class PaginarPendientes {

        @Test
        void listaVaciaDaUnaPaginaSinFilas() {
            PaginaEducacion p = service.paginarPendientes(List.of(), new FiltrosEducacion(null, null), "3");
            assertTrue(p.filas().isEmpty());
            assertEquals(1, p.paginaActual());
            assertEquals(1, p.totalPaginas());
            assertEquals(0, p.totalRegistros());
        }

        @Test
        void paginaFueraDeRangoSeAjustaALaUltima() {
            PaginaEducacion p = service.paginarPendientes(vistas(8), new FiltrosEducacion(null, null), "9");
            assertEquals(2, p.paginaActual());
            assertEquals(2, p.totalPaginas());
            assertEquals(2, p.filas().size());
            assertEquals(8, p.totalRegistros());
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-1", "abc"})
        void paginaInvalidaVaALaPrimera(String pagina) {
            PaginaEducacion p = service.paginarPendientes(vistas(8), new FiltrosEducacion(null, null), pagina);
            assertEquals(1, p.paginaActual());
            assertEquals(RmEducacionService.TAMANIO_PAGINA, p.filas().size());
        }

        @Test
        void paginaNulaVaALaPrimera() {
            assertEquals(1, service.paginarPendientes(vistas(8), new FiltrosEducacion(null, null), null).paginaActual());
        }

        @Test
        void busquedaIgnoraTildesYMayusculas() {
            List<RmEducacionView> lista = vistas(3);
            lista.get(1).getEducacion().setTitulo("Ingeniería");
            PaginaEducacion p = service.paginarPendientes(lista, new FiltrosEducacion("INGENIERIA", null), "1");
            assertEquals(1, p.totalRegistros());
            assertSame(lista.get(1), p.filas().get(0));
        }

        @Test
        void filtraPorInstitucion() {
            PaginaEducacion p = service.paginarPendientes(vistas(3), new FiltrosEducacion(null, "Institucion2"), "1");
            assertEquals(1, p.totalRegistros());
            assertEquals(2L, p.filas().get(0).getEducacion().getId());
        }
    }

    @Nested
    class PaginarRevisadas {

        @Test
        void filtraPorEstadoYColaborador() {
            Usuario otroColab = colaborador(20L);
            Educacion aprobada = educacion(1L, colab, yo);
            aprobada.setEstado(EstadoCertificado.APROBADO);
            Educacion rechazada = educacion(2L, colab, yo);
            rechazada.setEstado(EstadoCertificado.RECHAZADO);
            Educacion deOtro = educacion(3L, otroColab, yo);
            deOtro.setEstado(EstadoCertificado.APROBADO);
            List<RmEducacionView> lista = List.of(vista(aprobada), vista(rechazada), vista(deOtro));

            PaginaEducacion porEstado = service.paginarRevisadas(lista,
                    new FiltrosEducacion(null, null, EstadoCertificado.APROBADO, null), "1");
            assertEquals(2, porEstado.totalRegistros());

            PaginaEducacion ambos = service.paginarRevisadas(lista,
                    new FiltrosEducacion(null, null, EstadoCertificado.APROBADO, 10L), "1");
            assertEquals(1, ambos.totalRegistros());
            assertSame(aprobada, ambos.filas().get(0).getEducacion());
        }

        @Test
        void sinCoincidenciasDaPaginaVacia() {
            PaginaEducacion p = service.paginarRevisadas(vistas(2),
                    new FiltrosEducacion("nadie", null, null, null), "2");
            assertTrue(p.filas().isEmpty());
            assertEquals(1, p.paginaActual());
        }
    }
}
