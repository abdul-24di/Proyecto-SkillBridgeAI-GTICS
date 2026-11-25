package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmProyectoView;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.ColaboradorHabilidad;
import com.pucp.skillb_ia.model.ConfiguracionSistema;
import com.pucp.skillb_ia.model.Habilidad;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.ProyectoHabilidadRequerida;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoCertificado;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.EstadoValidacion;
import com.pucp.skillb_ia.model.enums.NivelDominio;
import com.pucp.skillb_ia.model.enums.NivelExperiencia;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.CertificadoRepository;
import com.pucp.skillb_ia.repository.ColaboradorHabilidadRepository;
import com.pucp.skillb_ia.repository.ConfiguracionSistemaRepository;
import com.pucp.skillb_ia.repository.ProyectoHabilidadRequeridaRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.col.ColAsistenteIaClient;
import com.pucp.skillb_ia.service.rm.RmPresupuestoService.CostoAsignacion;
import com.pucp.skillb_ia.service.rm.RmPresupuestoService.ImpactoPresupuesto;
import com.pucp.skillb_ia.service.rm.RmPresupuestoService.ResumenPresupuesto;
import com.pucp.skillb_ia.service.rm.RmTalentMatchingService.DatosCandidatos;
import com.pucp.skillb_ia.service.rm.RmTalentMatchingService.HabilidadFaltante;
import com.pucp.skillb_ia.service.rm.RmTalentMatchingService.RecomendacionCandidato;
import com.pucp.skillb_ia.service.rm.RmTalentMatchingService.ResultadoMatching;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.asignacion;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.colaborador;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.colaboradorHabilidad;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.habilidad;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.proyecto;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.proyectoView;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.requisito;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RmTalentMatchingUnitTests {

    @Mock private RmProyectoConsultaService rmProyectoConsultaService;
    @Mock private ProyectoHabilidadRequeridaRepository habilidadRequeridaRepository;
    @Mock private AsignacionRepository asignacionRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ColaboradorHabilidadRepository colaboradorHabilidadRepository;
    @Mock private CertificadoRepository certificadoRepository;
    @Mock private ConfiguracionSistemaRepository configuracionSistemaRepository;
    @Mock private RmPresupuestoService rmPresupuestoService;
    @Mock private ColAsistenteIaClient iaClient;

    @InjectMocks
    private RmTalentMatchingService servicio;

    private Proyecto proyecto;
    private Habilidad java;
    private Habilidad python;
    private final ResumenPresupuesto resumen =
            new ResumenPresupuesto(new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000"), 0);

    @BeforeEach
    void preparar() {
        proyecto = proyecto(10L);
        proyecto.setEstado(EstadoProyecto.ACTIVO);
        java = habilidad(1L, "Java");
        python = habilidad(2L, "Python");
    }

    private static ImpactoPresupuesto impacto(String costo, boolean suficiente) {
        CostoAsignacion c = new CostoAsignacion(BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN,
                new BigDecimal(costo), true);
        return new ImpactoPresupuesto(c, null, BigDecimal.ZERO, suficiente, BigDecimal.ZERO);
    }

    private static HabilidadFaltante faltante(Habilidad h) {
        return new HabilidadFaltante(h.getId(), h.getNombre(), "Nivel intermedio", 1, 0, 1, new BigDecimal("16"));
    }

    // Datos de candidatos armados a mano: habilidades validadas, asignaciones activas y certificados por colaborador.
    private static DatosCandidatos datos(List<Usuario> colaboradores, Map<Long, List<ColaboradorHabilidad>> habilidades,
                                         Map<Long, Integer> activas, Map<Long, Integer> certificados) {
        return new DatosCandidatos(colaboradores, habilidades, activas, certificados, 3);
    }

    private void prepararProyecto(List<ProyectoHabilidadRequerida> requisitos, List<Asignacion> asignaciones) {
        when(habilidadRequeridaRepository.findByProyecto(proyecto)).thenReturn(requisitos);
        when(asignacionRepository.findByProyecto(proyecto)).thenReturn(asignaciones);
        when(rmPresupuestoService.calcularResumen(proyecto)).thenReturn(resumen);
    }

    @Nested
    class CalcularHabilidadesFaltantes {

        @Test
        void cuentaCubiertosPorHabilidadSolicitadaOPorHabilidadesValidadas() {
            Usuario c1 = colaborador(5L);
            Usuario c2 = colaborador(6L);
            Asignacion conHabilidad = asignacion(1L, proyecto, c1, "20", EstadoAsignacion.ACTIVA);
            conHabilidad.setHabilidadSolicitada(java);
            Asignacion sinHabilidad = asignacion(2L, proyecto, c2, "20", EstadoAsignacion.ACTIVA);
            when(habilidadRequeridaRepository.findByProyecto(proyecto)).thenReturn(List.of(
                    requisito(proyecto, java, NivelDominio.INTERMEDIO, 2),
                    requisito(proyecto, python, null, 1)));
            when(asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA))
                    .thenReturn(List.of(conHabilidad, sinHabilidad));
            when(colaboradorHabilidadRepository.findByColaboradorAndActivoTrueAndEstadoValidacion(c2, EstadoValidacion.VALIDADA))
                    .thenReturn(List.of(colaboradorHabilidad(c2, python, NivelDominio.BASICO)));

            List<HabilidadFaltante> faltantes = servicio.calcularHabilidadesFaltantes(proyecto);

            assertEquals(1, faltantes.size());
            HabilidadFaltante f = faltantes.get(0);
            assertEquals(1L, f.habilidadId());
            assertEquals("Nivel intermedio", f.nivelRequerido());
            assertEquals(2, f.requeridos());
            assertEquals(1, f.cubiertos());
            assertEquals(1, f.faltantes());
        }

        @Test
        void cantidadCeroExigeUnaPersonaYSinHorasUsaLasDelProyecto() {
            proyecto.setHorasSemanalesRequeridas(new BigDecimal("12"));
            ProyectoHabilidadRequerida req = requisito(proyecto, java, null, 0);
            req.setHorasSemanales(null);
            when(habilidadRequeridaRepository.findByProyecto(proyecto)).thenReturn(List.of(req));
            when(asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA)).thenReturn(List.of());

            HabilidadFaltante f = servicio.calcularHabilidadesFaltantes(proyecto).get(0);

            assertEquals(1, f.requeridos());
            assertEquals("Cualquier nivel", f.nivelRequerido());
            assertEquals(new BigDecimal("12"), f.horasSemanales());
        }

        @Test
        void requisitoCubiertoNoAparece() {
            Asignacion a = asignacion(1L, proyecto, colaborador(5L), "20", EstadoAsignacion.ACTIVA);
            a.setHabilidadSolicitada(java);
            when(habilidadRequeridaRepository.findByProyecto(proyecto))
                    .thenReturn(List.of(requisito(proyecto, java, NivelDominio.BASICO, 1)));
            when(asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA)).thenReturn(List.of(a));

            assertTrue(servicio.calcularHabilidadesFaltantes(proyecto).isEmpty());
        }
    }

    @Nested
    class RecomendarParaProyecto {

        @Test
        void proyectoNoDotableNoRecomiendaANadie() {
            proyecto.setEstado(EstadoProyecto.FINALIZADO);

            assertTrue(servicio.recomendarParaProyecto(proyecto, List.of()).isEmpty());
            verifyNoInteractions(usuarioRepository, habilidadRequeridaRepository, rmPresupuestoService);
        }

        @Test
        void calculaPuntajeYNotasDeNivel() {
            Usuario senior = colaborador(5L);
            senior.setNivelExperiencia(NivelExperiencia.SENIOR);
            Usuario junior = colaborador(6L);
            junior.setHorasDisponibles(new BigDecimal("10"));
            prepararProyecto(List.of(requisito(proyecto, java, NivelDominio.INTERMEDIO, 1)), List.of());
            when(rmPresupuestoService.calcularImpacto(eq(proyecto), any(), any(), eq(resumen)))
                    .thenReturn(impacto("500", true));

            List<RecomendacionCandidato> recs = servicio.recomendarParaProyecto(proyecto, List.of(faltante(java)), datos(
                    List.of(junior, senior),
                    Map.of(5L, List.of(colaboradorHabilidad(senior, java, NivelDominio.AVANZADO)),
                            6L, List.of(colaboradorHabilidad(junior, java, NivelDominio.BASICO))),
                    Map.of(6L, 1),
                    Map.of(5L, 5)));

            assertEquals(2, recs.size());
            RecomendacionCandidato primero = recs.get(0);
            assertEquals(5L, primero.colaboradorId());
            // 42 + 22 + 15 + (12 + 6) = 97 de 97.
            assertEquals(100, primero.matchPorcentaje());
            assertEquals("bg-green-lt text-green", primero.matchClase());
            // Horas sugeridas = mínimo entre su disponibilidad y las horas semanales del proyecto.
            assertEquals(0, proyecto.getHorasSemanalesRequeridas().compareTo(primero.horasSugeridas()));
            assertEquals(List.of("Java: tiene avanzado, el proyecto pide intermedio (nivel superior)"), primero.notasNivel());
            assertFalse(primero.explicacionPorIa());
            RecomendacionCandidato segundo = recs.get(1);
            // 26 + 14 + 10 + 5 = 55 de 97.
            assertEquals(57, segundo.matchPorcentaje());
            assertEquals("bg-secondary-lt", segundo.matchClase());
            assertEquals(List.of("Java: tiene básico, el proyecto pide intermedio (nivel inferior)"), segundo.notasNivel());
            assertTrue(segundo.explicacion().contains("Nivel distinto al requerido"));
        }

        @Test
        void descartaCandidatosQueNoPuedenEntrar() {
            Usuario yaEnProyecto = colaborador(5L);
            Usuario sinHoras = colaborador(6L);
            sinHoras.setHorasDisponibles(new BigDecimal("0.5"));
            Usuario sinValidadas = colaborador(7L);
            Usuario enElMaximo = colaborador(8L);
            Usuario sinHabilidadRequerida = colaborador(9L);
            Usuario valido = colaborador(11L);
            prepararProyecto(List.of(requisito(proyecto, java, null, 1)),
                    List.of(asignacion(1L, proyecto, yaEnProyecto, "10", EstadoAsignacion.PENDIENTE)));
            when(rmPresupuestoService.calcularImpacto(eq(proyecto), eq(valido), any(), eq(resumen)))
                    .thenReturn(impacto("500", true));
            Map<Long, List<ColaboradorHabilidad>> habilidades = new HashMap<>();
            for (Usuario u : List.of(yaEnProyecto, sinHoras, enElMaximo, valido)) {
                habilidades.put(u.getId(), List.of(colaboradorHabilidad(u, java, NivelDominio.AVANZADO)));
            }
            habilidades.put(9L, List.of(colaboradorHabilidad(sinHabilidadRequerida, python, NivelDominio.AVANZADO)));

            List<RecomendacionCandidato> recs = servicio.recomendarParaProyecto(proyecto, List.of(), datos(
                    List.of(yaEnProyecto, sinHoras, sinValidadas, enElMaximo, sinHabilidadRequerida, valido),
                    habilidades, Map.of(8L, 3), Map.of()));

            assertEquals(List.of(11L), recs.stream().map(RecomendacionCandidato::colaboradorId).toList());
        }

        @Test
        void empateDePuntajeOrdenaPorPresupuestoYLuegoPorHoras() {
            Usuario noCabe = colaborador(5L);
            Usuario cabeConPocasHoras = colaborador(6L, "3200");
            cabeConPocasHoras.setHorasDisponibles(new BigDecimal("20"));
            Usuario cabeConMasHoras = colaborador(7L);
            prepararProyecto(List.of(), List.of());
            when(rmPresupuestoService.calcularImpacto(eq(proyecto), any(), any(), eq(resumen)))
                    .thenAnswer(inv -> impacto("500", !inv.getArgument(1, Usuario.class).getId().equals(5L)));
            Map<Long, List<ColaboradorHabilidad>> habilidades = new HashMap<>();
            for (Usuario u : List.of(noCabe, cabeConPocasHoras, cabeConMasHoras)) {
                habilidades.put(u.getId(), List.of(colaboradorHabilidad(u, java, NivelDominio.BASICO)));
            }

            List<RecomendacionCandidato> recs = servicio.recomendarParaProyecto(proyecto, List.of(),
                    datos(List.of(noCabe, cabeConPocasHoras, cabeConMasHoras), habilidades, Map.of(), Map.of()));

            assertEquals(List.of(7L, 6L, 5L), recs.stream().map(RecomendacionCandidato::colaboradorId).toList());
            assertEquals(recs.get(0).matchPorcentaje(), recs.get(2).matchPorcentaje());
            assertFalse(recs.get(2).cabeEnPresupuesto());
        }

        @Test
        void devuelveComoMaximoSeisCandidatos() {
            prepararProyecto(List.of(), List.of());
            when(rmPresupuestoService.calcularImpacto(eq(proyecto), any(), any(), eq(resumen)))
                    .thenReturn(impacto("500", true));
            List<Usuario> colaboradores = new ArrayList<>();
            Map<Long, List<ColaboradorHabilidad>> habilidades = new HashMap<>();
            for (long id = 20; id < 28; id++) {
                Usuario u = colaborador(id);
                colaboradores.add(u);
                habilidades.put(id, List.of(colaboradorHabilidad(u, java, NivelDominio.BASICO)));
            }

            assertEquals(6, servicio.recomendarParaProyecto(proyecto, List.of(),
                    datos(colaboradores, habilidades, Map.of(), Map.of())).size());
        }

        @Test
        void maximoDeAsignacionesSaleDeLaConfiguracion() {
            Usuario conUna = colaborador(5L);
            ConfiguracionSistema config = new ConfiguracionSistema();
            config.setValor(" 1 ");
            when(configuracionSistemaRepository.findByClave("MAX_ASIGNACIONES_POR_COLABORADOR")).thenReturn(Optional.of(config));
            when(colaboradorHabilidadRepository.findActivasPorEstadoValidacion(EstadoValidacion.VALIDADA))
                    .thenReturn(List.of(colaboradorHabilidad(conUna, java, NivelDominio.BASICO)));
            when(usuarioRepository.findColaboradoresVisiblesRm()).thenReturn(List.of(conUna));
            when(asignacionRepository.contarPorColaboradorYEstado(EstadoAsignacion.ACTIVA))
                    .thenReturn(List.<Object[]>of(new Object[]{5L, 1L}));
            when(certificadoRepository.contarPorColaboradorYEstado(EstadoCertificado.APROBADO)).thenReturn(List.of());
            prepararProyecto(List.of(), List.of());

            assertTrue(servicio.recomendarParaProyecto(proyecto, List.of()).isEmpty());
        }

        @Test
        void configuracionInvalidaUsaTresAsignaciones() {
            ConfiguracionSistema config = new ConfiguracionSistema();
            config.setValor("abc");
            when(configuracionSistemaRepository.findByClave(anyString())).thenReturn(Optional.of(config));

            assertEquals(3, servicio.cargarDatosCandidatos().maxAsignaciones());
        }
    }

    @Nested
    class ConstruirMatching {

        private Usuario candidato;

        // Proyecto con vacantes y un candidato válido (id 5) sin requisitos.
        private void prepararUnCandidato() {
            candidato = colaborador(5L);
            when(habilidadRequeridaRepository.findByProyecto(proyecto)).thenReturn(List.of());
            when(asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA)).thenReturn(List.of());
            when(asignacionRepository.findByProyecto(proyecto)).thenReturn(List.of());
            when(colaboradorHabilidadRepository.findActivasPorEstadoValidacion(EstadoValidacion.VALIDADA))
                    .thenReturn(List.of(colaboradorHabilidad(candidato, java, NivelDominio.BASICO)));
            when(usuarioRepository.findColaboradoresVisiblesRm()).thenReturn(List.of(candidato));
            when(asignacionRepository.contarPorColaboradorYEstado(EstadoAsignacion.ACTIVA)).thenReturn(List.of());
            when(certificadoRepository.contarPorColaboradorYEstado(EstadoCertificado.APROBADO)).thenReturn(List.of());
            when(rmPresupuestoService.calcularResumen(proyecto)).thenReturn(resumen);
            when(rmPresupuestoService.calcularImpacto(eq(proyecto), eq(candidato), any(), eq(resumen)))
                    .thenReturn(impacto("500", true));
        }

        @Test
        void sinProyectosConVacantesNoSeleccionaNada() {
            Proyecto finalizado = proyecto(11L);
            finalizado.setEstado(EstadoProyecto.FINALIZADO);
            when(rmProyectoConsultaService.listar()).thenReturn(List.of(
                    proyectoView(finalizado, 2), proyectoView(proyecto, 0)));

            ResultadoMatching r = servicio.construirMatching(null);

            assertTrue(r.proyectosConVacantes().isEmpty());
            assertNull(r.proyectoSeleccionado());
            verifyNoInteractions(habilidadRequeridaRepository, iaClient);
        }

        @Test
        void proyectoDeContextoSinVacantesSeMuestraSinRecomendaciones() {
            RmProyectoView contexto = proyectoView(proyecto, 0);
            RmProyectoView otro = proyectoView(proyecto(11L), 1);
            otro.getProyecto().setEstado(EstadoProyecto.EN_ESPERA);
            when(rmProyectoConsultaService.listar()).thenReturn(List.of(otro));
            when(habilidadRequeridaRepository.findByProyecto(proyecto)).thenReturn(List.of());
            when(asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA)).thenReturn(List.of());

            ResultadoMatching r = servicio.construirMatching(contexto);

            assertEquals(List.of(contexto, otro), r.proyectosConVacantes());
            assertSame(contexto, r.proyectoSeleccionado());
            assertTrue(r.recomendaciones().isEmpty());
            verifyNoInteractions(usuarioRepository, iaClient);
        }

        @Test
        void sinIaConfiguradaQuedaLaExplicacionBase() {
            prepararUnCandidato();
            when(rmProyectoConsultaService.listar()).thenReturn(List.of(proyectoView(proyecto, 1)));
            when(iaClient.estaConfigurado()).thenReturn(false);

            RecomendacionCandidato rec = servicio.construirMatching(null).recomendaciones().get(0);

            assertTrue(rec.explicacion().startsWith("Cuenta con habilidades validadas"));
            assertFalse(rec.explicacionPorIa());
            verify(iaClient, never()).generar(any(), any(), any());
        }

        @Test
        void usaLaExplicacionDeLaIaYLaGuardaEnCache() {
            prepararUnCandidato();
            when(rmProyectoConsultaService.listar()).thenReturn(List.of(proyectoView(proyecto, 1)));
            when(iaClient.estaConfigurado()).thenReturn(true);
            when(iaClient.generar(anyString(), eq(List.of()), anyString()))
                    .thenReturn("- 5| Encaja por su Java.\nabc|ignorada\n99|de otro candidato\nsin separador");

            RecomendacionCandidato rec = servicio.construirMatching(null).recomendaciones().get(0);
            RecomendacionCandidato otraVez = servicio.construirMatching(null).recomendaciones().get(0);

            assertEquals("Encaja por su Java.", rec.explicacion());
            assertTrue(rec.explicacionPorIa());
            assertEquals(rec, otraVez);
            verify(iaClient, times(1)).generar(anyString(), eq(List.of()), anyString());
        }

        @Test
        void explicacionMuyLargaSeRecortaA600() {
            prepararUnCandidato();
            when(rmProyectoConsultaService.listar()).thenReturn(List.of(proyectoView(proyecto, 1)));
            when(iaClient.estaConfigurado()).thenReturn(true);
            when(iaClient.generar(anyString(), eq(List.of()), anyString())).thenReturn("5|" + "x".repeat(700));

            assertEquals(600, servicio.construirMatching(null).recomendaciones().get(0).explicacion().length());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "respuesta sin formato"})
        void respuestaVaciaOConErrorDegradaALaBaseSinCache(String respuesta) {
            prepararUnCandidato();
            when(rmProyectoConsultaService.listar()).thenReturn(List.of(proyectoView(proyecto, 1)));
            when(iaClient.estaConfigurado()).thenReturn(true);
            when(iaClient.generar(anyString(), eq(List.of()), anyString())).thenReturn(respuesta);

            RecomendacionCandidato rec = servicio.construirMatching(null).recomendaciones().get(0);
            servicio.construirMatching(null);

            assertFalse(rec.explicacionPorIa());
            assertTrue(rec.explicacion().startsWith("Cuenta con habilidades validadas"));
            // Sin texto útil no se guarda en caché: la siguiente recarga vuelve a intentarlo.
            verify(iaClient, times(2)).generar(anyString(), eq(List.of()), anyString());
        }
    }

    @Nested
    class ConExplicacion {

        @Test
        void soloCambiaElTextoYElOrigen() {
            RecomendacionCandidato base = new RecomendacionCandidato(5L, "Ana Prueba", "AP", "Dev", "Junior", 80,
                    "bg-yellow-lt text-yellow", BigDecimal.TEN, 0, 3, 0, List.of(), List.of(), BigDecimal.TEN,
                    BigDecimal.ONE, true, true, List.of(), "base", false);

            RecomendacionCandidato nueva = base.conExplicacion("de la IA", true);

            assertEquals("de la IA", nueva.explicacion());
            assertTrue(nueva.explicacionPorIa());
            assertEquals(base.matchPorcentaje(), nueva.matchPorcentaje());
            assertEquals("base", base.explicacion());
        }
    }
}
