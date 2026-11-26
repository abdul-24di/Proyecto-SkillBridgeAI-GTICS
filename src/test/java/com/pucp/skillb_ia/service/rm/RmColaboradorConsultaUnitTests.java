package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmColaboradorDetalle;
import com.pucp.skillb_ia.dto.RmColaboradorResumen;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Certificado;
import com.pucp.skillb_ia.model.ColaboradorHabilidad;
import com.pucp.skillb_ia.model.ConfiguracionSistema;
import com.pucp.skillb_ia.model.Educacion;
import com.pucp.skillb_ia.model.ExperienciaProfesional;
import com.pucp.skillb_ia.model.Habilidad;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoCertificado;
import com.pucp.skillb_ia.model.enums.EstadoValidacion;
import com.pucp.skillb_ia.model.enums.NivelDominio;
import com.pucp.skillb_ia.model.enums.NivelExperiencia;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.CertificadoRepository;
import com.pucp.skillb_ia.repository.ColaboradorHabilidadRepository;
import com.pucp.skillb_ia.repository.ConfiguracionSistemaRepository;
import com.pucp.skillb_ia.repository.EducacionRepository;
import com.pucp.skillb_ia.repository.ExperienciaProfesionalRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.rm.RmColaboradorConsultaService.FiltrosCandidato;
import com.pucp.skillb_ia.service.rm.RmColaboradorConsultaService.FiltrosColaborador;
import com.pucp.skillb_ia.service.rm.RmColaboradorConsultaService.PaginaCandidatos;
import com.pucp.skillb_ia.service.rm.RmColaboradorConsultaService.PaginaColaboradores;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.asignacion;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.certificado;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.colaborador;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.colaboradorHabilidad;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.educacion;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.habilidad;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.limpiarSesion;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.proyecto;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.rm;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.sesionComo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unitarias de RmColaboradorConsultaService (TASK-085): filtros del directorio y de candidatos,
// paginación, contadores, costo de candidatos, sueldos y detalle del colaborador.
// El repositorio de usuarios ya devuelve solo los visibles (registro aprobado o NULL, TASK-053).
@ExtendWith(MockitoExtension.class)
class RmColaboradorConsultaUnitTests {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ColaboradorHabilidadRepository colaboradorHabilidadRepository;
    @Mock
    private AsignacionRepository asignacionRepository;
    @Mock
    private CertificadoRepository certificadoRepository;
    @Mock
    private ConfiguracionSistemaRepository configuracionSistemaRepository;
    @Mock
    private ExperienciaProfesionalRepository experienciaProfesionalRepository;
    @Mock
    private EducacionRepository educacionRepository;
    @Mock
    private RmPresupuestoService presupuestoService;
    @Spy
    private RmAlcanceService rmAlcanceService;

    @InjectMocks
    private RmColaboradorConsultaService service;

    private Usuario rm;
    private Usuario otroRm;

    private static final FiltrosColaborador DIRECTORIO_TODOS = new FiltrosColaborador(null, null, null, null);
    private static final FiltrosCandidato CANDIDATOS_TODOS = new FiltrosCandidato(null, "0", null);

    @BeforeEach
    void preparar() {
        rm = rm(1L);
        otroRm = rm(2L);
        sesionComo(rm);
        lenient().when(configuracionSistemaRepository.findByClave(any())).thenReturn(Optional.empty());
        lenient().when(colaboradorHabilidadRepository.findByColaboradorAndActivoTrueAndEstadoValidacion(any(), any()))
                .thenReturn(List.of());
        lenient().when(asignacionRepository.findByColaboradorAndEstado(any(), any())).thenReturn(List.of());
    }

    @AfterEach
    void limpiar() {
        limpiarSesion();
    }

    // Colaborador con horas disponibles y "activas" asignaciones activas.
    private Usuario colaboradorCon(Long id, String horas, int activas) {
        Usuario colaborador = colaborador(id);
        colaborador.setHorasDisponibles(new BigDecimal(horas));
        List<Asignacion> lista = new ArrayList<>();
        for (int i = 0; i < activas; i++) {
            lista.add(asignacion(id * 100 + i, proyecto(id * 100 + i), colaborador, "8", EstadoAsignacion.ACTIVA));
        }
        lenient().when(asignacionRepository.findByColaboradorAndEstado(colaborador, EstadoAsignacion.ACTIVA))
                .thenReturn(lista);
        return colaborador;
    }

    private void conColaboradores(Usuario... colaboradores) {
        when(usuarioRepository.findColaboradoresVisiblesRm()).thenReturn(List.of(colaboradores));
    }

    private void conMaxAsignaciones(String valor) {
        ConfiguracionSistema configuracion = new ConfiguracionSistema();
        configuracion.setClave("MAX_ASIGNACIONES_POR_COLABORADOR");
        configuracion.setValor(valor);
        when(configuracionSistemaRepository.findByClave("MAX_ASIGNACIONES_POR_COLABORADOR"))
                .thenReturn(Optional.of(configuracion));
    }

    private ColaboradorHabilidad habilidadDe(Usuario colaborador, Habilidad habilidad, EstadoValidacion estado) {
        ColaboradorHabilidad item = colaboradorHabilidad(colaborador, habilidad, NivelDominio.values()[0]);
        item.setEstadoValidacion(estado);
        return item;
    }

    private static List<Long> ids(List<RmColaboradorResumen> filas) {
        return filas.stream().map(RmColaboradorResumen::getId).toList();
    }

    @Nested
    class NormalizarFiltrosDirectorio {

        @Test
        void valoresValidosSinDistinguirMayusculas() {
            assertEquals(new FiltrosColaborador("ana", "16", "max", "Semi Senior"),
                    service.normalizarFiltrosDirectorio(" ana ", "16", "MAX", "semi senior"));
        }

        @Test
        void vaciosAllYDesconocidosSonTodos() {
            assertEquals(DIRECTORIO_TODOS, service.normalizarFiltrosDirectorio("", "all", "5", "Experto"));
            assertEquals(DIRECTORIO_TODOS, service.normalizarFiltrosDirectorio(null, null, null, null));
        }

        @Test
        void busquedaSeRecortaACienCaracteres() {
            assertEquals(100, service.normalizarFiltrosDirectorio("b".repeat(120), null, null, null)
                    .busqueda().length());
        }
    }

    @Nested
    class NormalizarFiltrosCandidatos {

        @Test
        void valoresValidos() {
            assertEquals(new FiltrosCandidato("java", "16", "available"),
                    service.normalizarFiltrosCandidatos("java", "16", "AVAILABLE"));
        }

        @Test
        void disponibilidadDesconocidaVuelveACeroYCargaATodas() {
            assertEquals(CANDIDATOS_TODOS, service.normalizarFiltrosCandidatos(null, "12", "all"));
            assertEquals(CANDIDATOS_TODOS, service.normalizarFiltrosCandidatos("  ", null, null));
        }
    }

    @Nested
    class ListarPaginaDirectorio {

        @Test
        void paginaDeSeisEnSeisYPaginaInvalidaSegura() {
            Usuario[] lista = new Usuario[8];
            for (int i = 0; i < 8; i++) {
                lista[i] = colaboradorCon(10L + i, "20", 0);
            }
            conColaboradores(lista);

            PaginaColaboradores segunda = service.listarPaginaDirectorio(DIRECTORIO_TODOS, "2");

            assertEquals(List.of(16L, 17L), ids(segunda.filas()));
            assertEquals(2, segunda.totalPaginas());
            assertEquals(8, segunda.totalRegistros());
            assertEquals(1, service.listarPaginaDirectorio(DIRECTORIO_TODOS, "abc").paginaActual());
            assertEquals(2, service.listarPaginaDirectorio(DIRECTORIO_TODOS, "40").paginaActual());
        }

        @Test
        void filtroDeDisponibilidadCeroEsExactoYElRestoEsMinimo() {
            conColaboradores(colaboradorCon(10L, "0", 0), colaboradorCon(11L, "8", 0), colaboradorCon(12L, "24", 0));

            assertEquals(List.of(10L), ids(service.listarPaginaDirectorio(
                    new FiltrosColaborador(null, "0", null, null), "1").filas()));
            assertEquals(List.of(11L, 12L), ids(service.listarPaginaDirectorio(
                    new FiltrosColaborador(null, "8", null, null), "1").filas()));
        }

        @Test
        void filtroDeCargaExactaYMaxima() {
            conColaboradores(colaboradorCon(10L, "20", 0), colaboradorCon(11L, "20", 1), colaboradorCon(12L, "20", 3));

            assertEquals(List.of(11L), ids(service.listarPaginaDirectorio(
                    new FiltrosColaborador(null, null, "1", null), "1").filas()));
            assertEquals(List.of(12L), ids(service.listarPaginaDirectorio(
                    new FiltrosColaborador(null, null, "max", null), "1").filas()));
        }

        @Test
        void filtroDeNivelYBusquedaSoloPorHabilidadesValidadas() {
            Usuario senior = colaboradorCon(10L, "20", 0);
            senior.setNivelExperiencia(NivelExperiencia.SENIOR);
            Usuario sinNivel = colaboradorCon(11L, "20", 0);
            when(colaboradorHabilidadRepository.findByColaboradorAndActivoTrueAndEstadoValidacion(
                    senior, EstadoValidacion.VALIDADA))
                    .thenReturn(List.of(habilidadDe(senior, habilidad(1L, "Kubernetes"), EstadoValidacion.VALIDADA)));
            conColaboradores(senior, sinNivel);

            assertEquals(List.of(10L), ids(service.listarPaginaDirectorio(
                    new FiltrosColaborador(null, null, null, "Senior"), "1").filas()));
            assertEquals(List.of(10L), ids(service.listarPaginaDirectorio(
                    new FiltrosColaborador("KUBER", null, null, null), "1").filas()));
            assertEquals("Sin definir", service.listarPaginaDirectorio(DIRECTORIO_TODOS, "1").filas().get(1).getNivel());
        }

        @Test
        void contadoresSobreTodosLosVisibles() {
            conColaboradores(colaboradorCon(10L, "16", 0), colaboradorCon(11L, "8", 3), colaboradorCon(12L, "30", 1));

            PaginaColaboradores pagina = service.listarPaginaDirectorio(
                    new FiltrosColaborador("nadie", null, null, null), "1");

            assertEquals(0, pagina.totalRegistros());
            assertEquals(3, pagina.contadores().total());
            assertEquals(2, pagina.contadores().disponibles());
            assertEquals(1, pagina.contadores().sinAsignaciones());
            assertEquals(1, pagina.contadores().cargaMaxima());
        }

        @Test
        void maximoDeAsignacionesConfiguradoOInvalido() {
            conMaxAsignaciones("1");
            conColaboradores(colaboradorCon(10L, "20", 1));
            assertTrue(service.listarPaginaDirectorio(DIRECTORIO_TODOS, "1").filas().get(0).isCargaMaxima());
        }

        @Test
        void maximoInvalidoUsaTresPorDefecto() {
            conMaxAsignaciones("-2");
            conColaboradores(colaboradorCon(10L, "20", 1));
            assertEquals(3, service.listarPaginaDirectorio(DIRECTORIO_TODOS, "1").filas().get(0).getMaxAsignaciones());
        }
    }

    @Nested
    class ListarPaginaCandidatos {

        private Proyecto proyecto;

        @BeforeEach
        void prepararProyecto() {
            proyecto = proyecto(50L);
            lenient().when(presupuestoService.calcularResumen(proyecto)).thenReturn(new ResumenPresupuesto(
                    new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("5000"), 0));
        }

        private CostoAsignacion costo(String total, boolean calculable) {
            return new CostoAsignacion(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                    new BigDecimal(total), calculable);
        }

        @Test
        void filtraPorHorasMinimasYCarga() {
            Usuario libre = colaboradorCon(10L, "24", 0);
            Usuario pocasHoras = colaboradorCon(11L, "4", 0);
            Usuario lleno = colaboradorCon(12L, "30", 3);
            conColaboradores(libre, pocasHoras, lleno);
            lenient().when(presupuestoService.calcularCosto(any(), any(), any())).thenReturn(costo("100", true));

            assertEquals(List.of(10L, 12L), idsCandidatos(service.listarPaginaCandidatos(
                    proyecto, new FiltrosCandidato(null, "16", null), "1")));
            assertEquals(List.of(10L, 11L), idsCandidatos(service.listarPaginaCandidatos(
                    proyecto, new FiltrosCandidato(null, "0", "available"), "1")));
            assertEquals(List.of(12L), idsCandidatos(service.listarPaginaCandidatos(
                    proyecto, new FiltrosCandidato(null, null, "full"), "1")));
        }

        @Test
        void costoYSiCabeEnElPresupuestoDisponible() {
            Usuario cabe = colaboradorCon(10L, "20", 0);
            Usuario noCabe = colaboradorCon(11L, "20", 0);
            Usuario sinSueldo = colaboradorCon(12L, "20", 0);
            sinSueldo.setSueldoBase(null);
            conColaboradores(cabe, noCabe, sinSueldo);
            BigDecimal horas = proyecto.getHorasSemanalesRequeridas();
            when(presupuestoService.calcularCosto(proyecto, cabe, horas)).thenReturn(costo("5000", true));
            when(presupuestoService.calcularCosto(proyecto, noCabe, horas)).thenReturn(costo("5000.01", true));
            when(presupuestoService.calcularCosto(proyecto, sinSueldo, horas)).thenReturn(costo("0", false));

            PaginaCandidatos pagina = service.listarPaginaCandidatos(proyecto, CANDIDATOS_TODOS, "1");

            assertTrue(pagina.filas().get(0).cabeEnPresupuesto(), "igual al disponible sí cabe");
            assertFalse(pagina.filas().get(1).cabeEnPresupuesto());
            assertFalse(pagina.filas().get(2).cabeEnPresupuesto(), "sin costo calculable no cabe");
            assertNull(pagina.filas().get(2).sueldoBase());
        }

        @Test
        void costoSoloParaLasFilasDeLaPagina() {
            Usuario[] lista = new Usuario[7];
            for (int i = 0; i < 7; i++) {
                lista[i] = colaboradorCon(10L + i, "20", 0);
            }
            conColaboradores(lista);
            when(presupuestoService.calcularCosto(any(), any(), any())).thenReturn(costo("1", true));

            PaginaCandidatos pagina = service.listarPaginaCandidatos(proyecto, CANDIDATOS_TODOS, "2");

            assertEquals(List.of(16L), idsCandidatos(pagina));
            assertEquals(7, pagina.totalRegistros());
            verify(presupuestoService, never()).calcularCosto(proyecto, lista[0], proyecto.getHorasSemanalesRequeridas());
        }

        private List<Long> idsCandidatos(PaginaCandidatos pagina) {
            return pagina.filas().stream().map(fila -> fila.resumen().getId()).toList();
        }
    }

    @Nested
    class MapaSueldosBase {

        @Test
        void sinSueldoQuedaEnCero() {
            conColaboradores(colaborador(10L, "3200"), colaborador(11L, null));

            Map<Long, BigDecimal> mapa = service.mapaSueldosBase();

            assertEquals(new BigDecimal("3200"), mapa.get(10L));
            assertEquals(BigDecimal.ZERO, mapa.get(11L));
        }
    }

    @Nested
    class ObtenerDetalle {

        @Test
        void noVisibleLanzaExcepcion() {
            when(usuarioRepository.findColaboradorVisibleRmById(99L)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.obtenerDetalle(99L));
            assertEquals("No se encontró el colaborador solicitado.", ex.getMessage());
        }

        @Test
        void armaPerfilConRevisionesSoloDelRm() {
            Usuario colaborador = colaboradorCon(10L, "20", 1);
            Habilidad habJava = habilidad(1L, "java");
            Habilidad aws = habilidad(2L, "AWS");
            Habilidad sql = habilidad(3L, "SQL");
            when(usuarioRepository.findColaboradorVisibleRmById(10L)).thenReturn(Optional.of(colaborador));
            when(colaboradorHabilidadRepository.findByColaboradorAndActivoTrue(colaborador)).thenReturn(List.of(
                    habilidadDe(colaborador, habJava, EstadoValidacion.PENDIENTE),
                    habilidadDe(colaborador, aws, EstadoValidacion.VALIDADA),
                    habilidadDe(colaborador, sql, EstadoValidacion.PENDIENTE)));
            Certificado propio = certificado(5L, colaborador, habJava, rm);
            Certificado ajeno = certificado(6L, colaborador, sql, otroRm);
            Certificado aprobado = certificado(7L, colaborador, aws, rm);
            aprobado.setEstado(EstadoCertificado.APROBADO);
            when(certificadoRepository.findByColaboradorIdConDetalle(10L)).thenReturn(List.of(propio, ajeno, aprobado));
            ExperienciaProfesional experiencia = new ExperienciaProfesional();
            experiencia.setEmpresa(" ");
            experiencia.setCargo(null);
            when(experienciaProfesionalRepository.findByColaboradorAndActivoTrueOrderByFechaInicioDesc(colaborador))
                    .thenReturn(List.of(experiencia));
            Educacion pendientePropia = educacion(8L, colaborador, rm);
            Educacion pendienteAjena = educacion(9L, colaborador, otroRm);
            Educacion aprobada = educacion(10L, colaborador, rm);
            aprobada.setEstado(EstadoCertificado.APROBADO);
            when(educacionRepository.findByColaboradorAndActivoTrueOrderByFechaInicioDesc(colaborador))
                    .thenReturn(List.of(pendientePropia, pendienteAjena, aprobada));

            RmColaboradorDetalle detalle = service.obtenerDetalle(10L);

            List<RmColaboradorDetalle.HabilidadDetalle> habilidades = detalle.getHabilidades();
            assertEquals(List.of("AWS", "java", "SQL"),
                    habilidades.stream().map(RmColaboradorDetalle.HabilidadDetalle::getNombre).toList(),
                    "orden alfabético sin distinguir mayúsculas");
            assertEquals("Validada", habilidades.get(0).getEstadoValidacion());
            assertNull(habilidades.get(0).getCertificadoPendienteId(), "el aprobado no lleva a revisión");
            assertEquals(5L, habilidades.get(1).getCertificadoPendienteId());
            assertNull(habilidades.get(2).getCertificadoPendienteId(), "el de otro RM no lleva a revisión");
            assertEquals(1, detalle.getAsignaciones().size());
            assertEquals("Activa", detalle.getAsignaciones().get(0).getEstado());
            assertEquals(List.of(8L), detalle.getEducacion().stream()
                    .map(RmColaboradorDetalle.EducacionDetalle::getRevisionId)
                    .filter(java.util.Objects::nonNull).toList());
            assertEquals(1, detalle.getExperiencias().size());
        }

        @Test
        void resumenUsaAlternativasSinNombreNiCargo() {
            Usuario colaborador = colaboradorCon(10L, "20", 0);
            colaborador.setNombre(null);
            colaborador.setApellido(null);
            colaborador.setNivelExperiencia(NivelExperiencia.SEMI_SENIOR);
            when(usuarioRepository.findColaboradorVisibleRmById(10L)).thenReturn(Optional.of(colaborador));
            when(certificadoRepository.countByColaboradorAndEstado(colaborador, EstadoCertificado.APROBADO)).thenReturn(2L);

            RmColaboradorResumen resumen = service.obtenerDetalle(10L).getResumen();

            assertEquals(colaborador.getCorreo(), resumen.getNombreCompleto());
            assertEquals("CO", resumen.getIniciales());
            assertEquals("Cargo sin registrar", resumen.getCargo());
            assertEquals("Semi Senior", resumen.getNivel());
            assertEquals(2L, resumen.getCertificadosAprobados());
        }
    }
}
