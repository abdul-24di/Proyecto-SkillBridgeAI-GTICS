package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmCertificadoView;
import com.pucp.skillb_ia.model.Cargo;
import com.pucp.skillb_ia.model.Certificado;
import com.pucp.skillb_ia.model.ColaboradorHabilidad;
import com.pucp.skillb_ia.model.Habilidad;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoCertificado;
import com.pucp.skillb_ia.model.enums.EstadoValidacion;
import com.pucp.skillb_ia.model.enums.NivelDominio;
import com.pucp.skillb_ia.model.enums.NivelExperiencia;
import com.pucp.skillb_ia.repository.CertificadoRepository;
import com.pucp.skillb_ia.repository.ColaboradorHabilidadRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.NotificacionService;
import com.pucp.skillb_ia.service.rm.RmCertificadoService.FiltrosCertificados;
import com.pucp.skillb_ia.service.rm.RmCertificadoService.PaginaCertificados;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.certificado;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.colaborador;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.colaboradorHabilidad;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.habilidad;
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

// Revisión de certificados por el RM: aprobar/rechazar, nivel general, alcance, contadores, filtros y paginación.
@ExtendWith(MockitoExtension.class)
class RmCertificadoUnitTests {

    @Mock
    private CertificadoRepository certificadoRepository;
    @Mock
    private ColaboradorHabilidadRepository colaboradorHabilidadRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private NotificacionService notificacionService;
    @Mock
    private RmAlcanceService rmAlcanceService;

    @InjectMocks
    private RmCertificadoService service;

    private Usuario yo;
    private Usuario colab;
    private Habilidad java;
    private Certificado cert;
    private ColaboradorHabilidad perfil;

    @BeforeEach
    void preparar() {
        yo = rm(1L);
        colab = colaborador(10L);
        java = habilidad(5L, "Java");
        cert = certificado(100L, colab, java, yo);
        perfil = colaboradorHabilidad(colab, java, NivelDominio.BASICO);
    }

    private void soyRm() {
        when(usuarioRepository.findById(yo.getId())).thenReturn(Optional.of(yo));
    }

    // Deja el certificado visible (o no) para el RM actual.
    private void encontrar(Certificado certificado, boolean visible) {
        when(certificadoRepository.findByIdConDetalle(certificado.getId())).thenReturn(Optional.of(certificado));
        when(rmAlcanceService.idRmActual()).thenReturn(yo.getId());
        when(rmAlcanceService.puedeVerRevision(certificado.getRmResponsable(), yo.getId())).thenReturn(visible);
    }

    private void conPerfil(ColaboradorHabilidad item) {
        when(colaboradorHabilidadRepository.findByColaboradorAndHabilidad(colab, java)).thenReturn(Optional.ofNullable(item));
    }

    private RmCertificadoView vista(Certificado certificado) {
        Usuario c = certificado.getColaborador();
        return new RmCertificadoView(certificado, c.getNombre() + " " + c.getApellido(), "UP", "Cargo",
                "Basico", "Junior", "—", 0, List.of());
    }

    private List<RmCertificadoView> vistas(int cantidad) {
        List<RmCertificadoView> lista = new ArrayList<>();
        for (int i = 1; i <= cantidad; i++) {
            lista.add(vista(certificado((long) i, colab, java, yo)));
        }
        return lista;
    }

    private RmCertificadoView subidoHaceDias(long dias) {
        Certificado c = certificado(dias + 1000, colab, java, yo);
        c.setFechaSubida(LocalDateTime.now().minusDays(dias));
        return vista(c);
    }

    @Nested
    class Aprobar {

        @Test
        void pendienteSeApruebaValidaHabilidadNotificaYAudita() {
            cert.setMotivoRechazo("viejo");
            soyRm();
            encontrar(cert, true);
            conPerfil(perfil);

            service.aprobar(cert.getId(), NivelDominio.AVANZADO, null, yo.getId());

            assertEquals(NivelDominio.AVANZADO, perfil.getNivelDominio());
            assertEquals(EstadoValidacion.VALIDADA, perfil.getEstadoValidacion());
            assertTrue(perfil.isActivo());
            verify(colaboradorHabilidadRepository).save(perfil);
            assertEquals(EstadoCertificado.APROBADO, cert.getEstado());
            assertEquals(NivelDominio.AVANZADO, cert.getNivelAprobado());
            assertNull(cert.getMotivoRechazo());
            assertSame(yo, cert.getRevisadoPor());
            assertNotNull(cert.getFechaRevision());
            verify(certificadoRepository).save(cert);
            verify(notificacionService).crear(eq(colab), eq("CERTIFICADO_APROBADO"), eq(CategoriaNotificacion.HABILIDAD),
                    eq("Certificado aprobado"), contains("Java"), eq("CERTIFICADO"), eq(cert.getId()));
            verify(auditoriaService).registrar(eq(yo), eq("APROBACION_CERTIFICADO"), eq("CERTIFICADO"),
                    eq(cert.getId()), contains("Java"));
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        void sinNivelDeHabilidadMantieneElVigente() {
            perfil.setNivelDominio(NivelDominio.INTERMEDIO);
            soyRm();
            encontrar(cert, true);
            conPerfil(perfil);

            service.aprobar(cert.getId(), null, null, yo.getId());

            assertEquals(NivelDominio.INTERMEDIO, perfil.getNivelDominio());
            assertEquals(NivelDominio.INTERMEDIO, cert.getNivelAprobado());
        }

        @Test
        void subirNivelGeneralGuardaRecalculaSueldoYAudita() {
            Cargo cargo = new Cargo();
            cargo.setSueldoSemiSenior(new BigDecimal("5000"));
            colab.setCargo(cargo);
            colab.setNivelExperiencia(NivelExperiencia.JUNIOR);
            soyRm();
            encontrar(cert, true);
            conPerfil(perfil);

            service.aprobar(cert.getId(), null, NivelExperiencia.SEMI_SENIOR, yo.getId());

            assertEquals(NivelExperiencia.SEMI_SENIOR, colab.getNivelExperiencia());
            assertEquals(0, new BigDecimal("5000").compareTo(colab.getSueldoBase()));
            verify(usuarioRepository).save(colab);
            verify(auditoriaService).registrar(eq(yo), eq("ACTUALIZACION_NIVEL_EXPERIENCIA"), eq("USUARIO"),
                    eq(colab.getId()), anyString(), eq("JUNIOR"), eq("SEMI_SENIOR"), any());
            assertEquals(EstadoCertificado.APROBADO, cert.getEstado());
        }

        @Test
        void bajarNivelGeneralLanzaYNoApruebaNada() {
            colab.setNivelExperiencia(NivelExperiencia.SENIOR);
            soyRm();
            encontrar(cert, true);
            conPerfil(perfil);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.aprobar(cert.getId(), NivelDominio.AVANZADO, NivelExperiencia.JUNIOR, yo.getId()));

            assertTrue(ex.getMessage().startsWith("No se puede bajar el nivel general de experiencia de Senior a Junior"));
            assertEquals(EstadoCertificado.PENDIENTE, cert.getEstado());
            assertEquals(NivelDominio.BASICO, perfil.getNivelDominio());
            verify(certificadoRepository, never()).save(any());
            verify(colaboradorHabilidadRepository, never()).save(any());
            verifyNoInteractions(notificacionService, auditoriaService);
        }

        @Test
        void habilidadQueYaNoEstaEnElPerfilLanza() {
            perfil.setActivo(false);
            soyRm();
            encontrar(cert, true);
            conPerfil(perfil);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.aprobar(cert.getId(), null, null, yo.getId()));
            assertEquals("La habilidad asociada ya no forma parte del perfil del colaborador.", ex.getMessage());
            verify(certificadoRepository, never()).save(any());
        }

        @Test
        void habilidadInexistenteEnElPerfilLanza() {
            soyRm();
            encontrar(cert, true);
            conPerfil(null);

            assertThrows(IllegalStateException.class, () -> service.aprobar(cert.getId(), null, null, yo.getId()));
            verify(certificadoRepository, never()).save(any());
        }

        @Test
        void yaRevisadoLanza() {
            cert.setEstado(EstadoCertificado.RECHAZADO);
            soyRm();
            encontrar(cert, true);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.aprobar(cert.getId(), null, null, yo.getId()));
            assertEquals("Este certificado ya fue revisado.", ex.getMessage());
            verify(certificadoRepository, never()).save(any());
        }

        @Test
        void certificadoDeOtroRmNoSeEncuentra() {
            soyRm();
            encontrar(cert, false);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.aprobar(cert.getId(), null, null, yo.getId()));
            assertEquals("No se encontró el certificado solicitado.", ex.getMessage());
            verify(certificadoRepository, never()).save(any());
            verifyNoInteractions(notificacionService, auditoriaService);
        }

        @Test
        void certificadoInexistenteLanza() {
            soyRm();
            when(certificadoRepository.findByIdConDetalle(999L)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.aprobar(999L, null, null, yo.getId()));
            assertEquals("No se encontró el certificado solicitado.", ex.getMessage());
        }

        @Test
        void usuarioQueNoEsRmLanza() {
            Usuario otro = pm(2L);
            when(usuarioRepository.findById(2L)).thenReturn(Optional.of(otro));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.aprobar(cert.getId(), null, null, 2L));
            assertEquals("La operación requiere un Resource Manager.", ex.getMessage());
            verifyNoInteractions(certificadoRepository);
        }

        @Test
        void rmInexistenteLanza() {
            when(usuarioRepository.findById(anyLong())).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.aprobar(cert.getId(), null, null, 77L));
            assertEquals("No se encontró el Resource Manager.", ex.getMessage());
        }
    }

    @Nested
    class Rechazar {

        @Test
        void pendienteSeRechazaConMotivoNotificacionYAuditoria() {
            soyRm();
            encontrar(cert, true);
            conPerfil(perfil);

            service.rechazar(cert.getId(), "  Documento ilegible  ", yo.getId());

            assertEquals(EstadoCertificado.RECHAZADO, cert.getEstado());
            assertEquals("Documento ilegible", cert.getMotivoRechazo());
            assertSame(yo, cert.getRevisadoPor());
            assertNotNull(cert.getFechaRevision());
            verify(certificadoRepository).save(cert);
            assertEquals(EstadoValidacion.RECHAZADA, perfil.getEstadoValidacion());
            verify(notificacionService).crear(eq(colab), eq("CERTIFICADO_RECHAZADO"), eq(CategoriaNotificacion.HABILIDAD),
                    eq("Certificado rechazado"), contains("Motivo: Documento ilegible"), eq("CERTIFICADO"), eq(cert.getId()));
            verify(auditoriaService).registrar(eq(yo), eq("RECHAZO_CERTIFICADO"), eq("CERTIFICADO"),
                    eq(cert.getId()), contains("Documento ilegible"));
        }

        @Test
        void conOtroCertificadoAprobadoLaHabilidadSigueValidada() {
            perfil.setEstadoValidacion(EstadoValidacion.VALIDADA);
            soyRm();
            encontrar(cert, true);
            when(certificadoRepository.countByColaboradorAndHabilidadAndEstado(colab, java, EstadoCertificado.APROBADO))
                    .thenReturn(1L);

            service.rechazar(cert.getId(), "Vencido", yo.getId());

            assertEquals(EstadoValidacion.VALIDADA, perfil.getEstadoValidacion());
            verify(colaboradorHabilidadRepository, never()).findByColaboradorAndHabilidad(any(), any());
        }

        @Test
        void motivoLargoSeRecortaA300() {
            soyRm();
            encontrar(cert, true);
            conPerfil(perfil);

            service.rechazar(cert.getId(), "x".repeat(350), yo.getId());

            assertEquals(300, cert.getMotivoRechazo().length());
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void motivoVacioLanzaYNoGuarda(String motivo) {
            soyRm();
            encontrar(cert, true);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.rechazar(cert.getId(), motivo, yo.getId()));
            assertEquals("Debes indicar el motivo del rechazo.", ex.getMessage());
            assertEquals(EstadoCertificado.PENDIENTE, cert.getEstado());
            verify(certificadoRepository, never()).save(any());
            verifyNoInteractions(notificacionService, auditoriaService);
        }

        @Test
        void motivoNuloLanza() {
            soyRm();
            encontrar(cert, true);

            assertThrows(IllegalArgumentException.class, () -> service.rechazar(cert.getId(), null, yo.getId()));
            verify(certificadoRepository, never()).save(any());
        }

        @Test
        void yaRevisadoLanza() {
            cert.setEstado(EstadoCertificado.APROBADO);
            soyRm();
            encontrar(cert, true);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.rechazar(cert.getId(), "Motivo", yo.getId()));
            assertEquals("Este certificado ya fue revisado.", ex.getMessage());
        }

        @Test
        void certificadoDeOtroRmNoSeRevisa() {
            soyRm();
            encontrar(cert, false);

            assertThrows(IllegalArgumentException.class, () -> service.rechazar(cert.getId(), "Motivo", yo.getId()));
            verify(certificadoRepository, never()).save(any());
            verifyNoInteractions(notificacionService, auditoriaService);
        }
    }

    @Nested
    class ActualizarNivelExperiencia {

        private void colaboradorExiste() {
            when(usuarioRepository.findById(colab.getId())).thenReturn(Optional.of(colab));
        }

        @Test
        void subeElNivelGuardaYAudita() {
            colab.setNivelExperiencia(NivelExperiencia.JUNIOR);
            soyRm();
            colaboradorExiste();

            service.actualizarNivelExperiencia(colab.getId(), NivelExperiencia.SENIOR, yo.getId());

            assertEquals(NivelExperiencia.SENIOR, colab.getNivelExperiencia());
            verify(usuarioRepository).save(colab);
            verify(auditoriaService).registrar(eq(yo), eq("ACTUALIZACION_NIVEL_EXPERIENCIA"), eq("USUARIO"),
                    eq(colab.getId()), anyString(), eq("JUNIOR"), eq("SENIOR"), any());
        }

        @Test
        void sinNivelPrevioSePuedeAsignarJunior() {
            colab.setNivelExperiencia(null);
            soyRm();
            colaboradorExiste();

            service.actualizarNivelExperiencia(colab.getId(), NivelExperiencia.JUNIOR, yo.getId());

            assertEquals(NivelExperiencia.JUNIOR, colab.getNivelExperiencia());
            verify(auditoriaService).registrar(eq(yo), anyString(), anyString(), anyLong(), anyString(),
                    eq(null), eq("JUNIOR"), any());
        }

        @Test
        void mantenerNoGuardaNiAudita() {
            colab.setNivelExperiencia(NivelExperiencia.SEMI_SENIOR);
            soyRm();
            colaboradorExiste();

            service.actualizarNivelExperiencia(colab.getId(), NivelExperiencia.SEMI_SENIOR, yo.getId());

            verify(usuarioRepository, never()).save(any());
            verifyNoInteractions(auditoriaService);
        }

        @Test
        void bajarLanzaSinModificar() {
            colab.setNivelExperiencia(NivelExperiencia.SEMI_SENIOR);
            soyRm();
            colaboradorExiste();

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.actualizarNivelExperiencia(colab.getId(), NivelExperiencia.JUNIOR, yo.getId()));
            assertEquals("No se puede bajar el nivel general de experiencia de Semi Senior a Junior. "
                    + "Solo se permite mantenerlo o subirlo.", ex.getMessage());
            assertEquals(NivelExperiencia.SEMI_SENIOR, colab.getNivelExperiencia());
            verify(usuarioRepository, never()).save(any());
            verifyNoInteractions(auditoriaService);
        }

        @Test
        void nivelNuloLanza() {
            soyRm();
            colaboradorExiste();

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.actualizarNivelExperiencia(colab.getId(), null, yo.getId()));
            assertEquals("Selecciona un nivel de experiencia.", ex.getMessage());
        }

        @Test
        void colaboradorInactivoNoSeEncuentra() {
            colab.setActivo(false);
            soyRm();
            colaboradorExiste();

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.actualizarNivelExperiencia(colab.getId(), NivelExperiencia.SENIOR, yo.getId()));
            assertEquals("No se encontró el colaborador solicitado.", ex.getMessage());
        }

        @Test
        void usuarioQueNoEsColaboradorNoSeEncuentra() {
            Usuario otroPm = pm(20L);
            soyRm();
            when(usuarioRepository.findById(20L)).thenReturn(Optional.of(otroPm));

            assertThrows(IllegalArgumentException.class,
                    () -> service.actualizarNivelExperiencia(20L, NivelExperiencia.SENIOR, yo.getId()));
        }
    }

    @Nested
    class ListarPendientes {

        @Test
        void soloIncluyeLosVisiblesParaElRm() {
            Certificado ajeno = certificado(101L, colab, java, rm(2L));
            when(rmAlcanceService.idRmActual()).thenReturn(yo.getId());
            when(certificadoRepository.findByEstadoConDetalle(EstadoCertificado.PENDIENTE)).thenReturn(List.of(cert, ajeno));
            when(rmAlcanceService.puedeVerRevision(yo, yo.getId())).thenReturn(true);
            when(rmAlcanceService.puedeVerRevision(ajeno.getRmResponsable(), yo.getId())).thenReturn(false);

            List<RmCertificadoView> resultado = service.listarPendientes();

            assertEquals(1, resultado.size());
            assertSame(cert, resultado.get(0).getCertificado());
        }
    }

    @Nested
    class ListarHistorial {

        @Test
        void colaboradorInexistenteLanza() {
            when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.listarHistorial(99L));
            assertEquals("No se encontró el colaborador solicitado.", ex.getMessage());
        }

        @Test
        void filtraPorAlcanceDelRm() {
            Certificado ajeno = certificado(101L, colab, java, rm(2L));
            when(usuarioRepository.findById(colab.getId())).thenReturn(Optional.of(colab));
            when(rmAlcanceService.idRmActual()).thenReturn(yo.getId());
            when(certificadoRepository.findByColaboradorIdConDetalle(colab.getId())).thenReturn(List.of(cert, ajeno));
            when(rmAlcanceService.puedeVerRevision(yo, yo.getId())).thenReturn(true);
            when(rmAlcanceService.puedeVerRevision(ajeno.getRmResponsable(), yo.getId())).thenReturn(false);

            List<RmCertificadoView> resultado = service.listarHistorial(colab.getId());

            assertEquals(1, resultado.size());
            assertSame(cert, resultado.get(0).getCertificado());
        }
    }

    @Nested
    class Obtener {

        @Test
        void deOtroRmNoSeEncuentra() {
            encontrar(cert, false);
            assertThrows(IllegalArgumentException.class, () -> service.obtener(cert.getId()));
        }

        @Test
        void visibleDevuelveLaVistaConNivelDeHabilidad() {
            encontrar(cert, true);
            conPerfil(perfil);

            RmCertificadoView vista = service.obtener(cert.getId());

            assertSame(cert, vista.getCertificado());
        }
    }

    @Nested
    class DiasPendienteMasAntiguo {

        @Test
        void sinPendientesEsCero() {
            assertEquals(0, service.diasPendienteMasAntiguo(List.of()));
        }

        @Test
        void tomaElSubidoPrimero() {
            assertEquals(10, service.diasPendienteMasAntiguo(List.of(subidoHaceDias(3), subidoHaceDias(10), subidoHaceDias(0))));
        }

        @Test
        void ignoraFechasNulas() {
            RmCertificadoView sinFecha = vista(certificado(50L, colab, java, yo));
            sinFecha.getCertificado().setFechaSubida(null);
            assertEquals(2, service.diasPendienteMasAntiguo(List.of(sinFecha, subidoHaceDias(2))));
        }

        @Test
        void fechaFuturaNoDaNegativo() {
            RmCertificadoView futuro = vista(certificado(51L, colab, java, yo));
            futuro.getCertificado().setFechaSubida(LocalDateTime.now().plusDays(3));
            assertEquals(0, service.diasPendienteMasAntiguo(List.of(futuro)));
        }
    }

    @Nested
    class ContarPendientesAtrasados {

        @Test
        void cuentaSoloLosDeMasDeSieteDias() {
            List<RmCertificadoView> pendientes = List.of(subidoHaceDias(0), subidoHaceDias(7),
                    subidoHaceDias(8), subidoHaceDias(30));
            assertEquals(2, service.contarPendientesAtrasados(pendientes));
        }

        @Test
        void ignoraFechasNulas() {
            RmCertificadoView sinFecha = vista(certificado(50L, colab, java, yo));
            sinFecha.getCertificado().setFechaSubida(null);
            assertEquals(0, service.contarPendientesAtrasados(List.of(sinFecha)));
        }
    }

    @Nested
    class HabilidadesDisponibles {

        @Test
        void distintasYOrdenadasSinDistinguirMayusculas() {
            List<RmCertificadoView> lista = List.of(
                    vista(certificado(1L, colab, habilidad(1L, "sql"), yo)),
                    vista(certificado(2L, colab, habilidad(2L, "Java"), yo)),
                    vista(certificado(3L, colab, habilidad(3L, "sql"), yo)));
            assertEquals(List.of("Java", "sql"), service.habilidadesDisponibles(lista));
        }
    }

    @Nested
    class NormalizarFiltros {

        @Test
        void recortaBusquedaYAceptaOpcionesValidasSinMayusculas() {
            FiltrosCertificados f = service.normalizarFiltros("  ana  ", " java ", "aprobado", List.of("Java", "SQL"));
            assertEquals("ana", f.busqueda());
            assertEquals("Java", f.habilidad());
            assertEquals(EstadoCertificado.APROBADO, f.estado());
        }

        @Test
        void descartaValoresInvalidosYVacios() {
            FiltrosCertificados f = service.normalizarFiltros("   ", "Cobol", "BORRADO", List.of("Java"));
            assertNull(f.busqueda());
            assertNull(f.habilidad());
            assertNull(f.estado());
        }

        @Test
        void nulosQuedanSinFiltro() {
            FiltrosCertificados f = service.normalizarFiltros(null, null, null, List.of("Java"));
            assertNull(f.busqueda());
            assertNull(f.habilidad());
            assertNull(f.estado());
        }

        @Test
        void busquedaLargaSeRecortaA100() {
            FiltrosCertificados f = service.normalizarFiltros("a".repeat(150), null, null, List.of());
            assertEquals(100, f.busqueda().length());
        }
    }

    @Nested
    class PaginarPendientes {

        private final FiltrosCertificados sinFiltros = new FiltrosCertificados(null, null, null);

        @Test
        void seisPorPagina() {
            PaginaCertificados p = service.paginarPendientes(vistas(13), sinFiltros, "2");
            assertEquals(6, p.filas().size());
            assertEquals(2, p.paginaActual());
            assertEquals(3, p.totalPaginas());
            assertEquals(13, p.totalRegistros());
        }

        @ParameterizedTest
        @ValueSource(strings = {"abc", "0", "-3"})
        void paginaInvalidaVaALaPrimera(String pagina) {
            assertEquals(1, service.paginarPendientes(vistas(8), sinFiltros, pagina).paginaActual());
        }

        @Test
        void paginaFueraDeRangoVaALaUltima() {
            PaginaCertificados p = service.paginarPendientes(vistas(8), sinFiltros, "50");
            assertEquals(2, p.paginaActual());
            assertEquals(2, p.filas().size());
        }

        @Test
        void listaVaciaTieneUnaPaginaSinFilas() {
            PaginaCertificados p = service.paginarPendientes(List.of(), sinFiltros, null);
            assertEquals(1, p.totalPaginas());
            assertTrue(p.filas().isEmpty());
        }

        @Test
        void busquedaIgnoraTildesYMayusculas() {
            Usuario jose = colaborador(11L);
            jose.setNombre("José");
            RmCertificadoView deJose = vista(certificado(200L, jose, java, yo));
            PaginaCertificados p = service.paginarPendientes(List.of(deJose, vista(cert)),
                    new FiltrosCertificados("JOSE", null, null), "1");
            assertEquals(1, p.totalRegistros());
            assertSame(deJose, p.filas().get(0));
        }

        @Test
        void filtraPorHabilidad() {
            RmCertificadoView sql = vista(certificado(201L, colab, habilidad(6L, "SQL"), yo));
            PaginaCertificados p = service.paginarPendientes(List.of(sql, vista(cert)),
                    new FiltrosCertificados(null, "SQL", null), "1");
            assertEquals(1, p.totalRegistros());
            assertSame(sql, p.filas().get(0));
        }
    }

    @Nested
    class PaginarHistorial {

        @Test
        void filtraPorEstado() {
            Certificado aprobado = certificado(300L, colab, java, yo);
            aprobado.setEstado(EstadoCertificado.APROBADO);
            PaginaCertificados p = service.paginarHistorial(List.of(vista(cert), vista(aprobado)),
                    new FiltrosCertificados(null, null, EstadoCertificado.APROBADO), "1");
            assertEquals(1, p.totalRegistros());
            assertSame(aprobado, p.filas().get(0).getCertificado());
        }

        @Test
        void buscaPorNombreDeArchivoOHabilidad() {
            PaginaCertificados porArchivo = service.paginarHistorial(List.of(vista(cert)),
                    new FiltrosCertificados("cert100.pdf", null, null), "1");
            PaginaCertificados porHabilidad = service.paginarHistorial(List.of(vista(cert)),
                    new FiltrosCertificados("java", null, null), "1");
            PaginaCertificados sinCoincidencia = service.paginarHistorial(List.of(vista(cert)),
                    new FiltrosCertificados("Usuario10", null, null), "1");
            assertEquals(1, porArchivo.totalRegistros());
            assertEquals(1, porHabilidad.totalRegistros());
            assertEquals(0, sinCoincidencia.totalRegistros());
        }
    }
}
