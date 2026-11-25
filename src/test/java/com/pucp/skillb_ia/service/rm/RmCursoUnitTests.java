package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmCursoView;
import com.pucp.skillb_ia.model.ColaboradorCurso;
import com.pucp.skillb_ia.model.Curso;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoColaboradorCurso;
import com.pucp.skillb_ia.model.enums.OrigenCurso;
import com.pucp.skillb_ia.repository.ColaboradorCursoRepository;
import com.pucp.skillb_ia.repository.CursoRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.EmailService;
import com.pucp.skillb_ia.service.NotificacionService;
import com.pucp.skillb_ia.service.col.ColaboradorCursoService;
import com.pucp.skillb_ia.service.rm.RmCursoService.FiltroEstadoBandeja;
import com.pucp.skillb_ia.service.rm.RmCursoService.FiltrosBandeja;
import com.pucp.skillb_ia.service.rm.RmCursoService.FiltrosCatalogo;
import com.pucp.skillb_ia.service.rm.RmCursoService.SituacionInscripcion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.colaborador;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.curso;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.inscripcion;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.pm;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.rm;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Cursos del RM: inscripciones (aprobar/rechazar), evidencias, asignación directa, bandeja, catálogo y estados.
@ExtendWith(MockitoExtension.class)
class RmCursoUnitTests {

    @Mock private CursoRepository cursoRepository;
    @Mock private ColaboradorCursoRepository colaboradorCursoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private NotificacionService notificacionService;
    @Mock private EmailService emailService;
    @Mock private AuditoriaService auditoriaService;
    @Mock private ColaboradorCursoService colaboradorCursoService;
    @Mock private RmAlcanceService rmAlcanceService;
    @Mock private RmColaboradorConsultaService rmColaboradorConsultaService;

    @InjectMocks private RmCursoService servicio;

    private Usuario rm;
    private Usuario otroRm;
    private Usuario colaborador;
    private Curso curso;

    @BeforeEach
    void preparar() {
        rm = rm(1L);
        otroRm = rm(2L);
        colaborador = colaborador(10L);
        curso = curso(100L);
    }

    // El RM 1 tiene la sesión; la regla de alcance es la real de RmAlcanceService.
    private void sesionDelRm() {
        when(rmAlcanceService.idRmActual()).thenReturn(1L);
        when(rmAlcanceService.puedeVerRevision(any(), any())).thenCallRealMethod();
    }

    private void rmExiste() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(rm));
    }

    private void guardarDevuelveLoMismo() {
        when(colaboradorCursoRepository.save(any(ColaboradorCurso.class))).thenAnswer(i -> i.getArgument(0));
    }

    private ColaboradorCurso solicitud(Long id, Usuario responsable) {
        return inscripcion(id, colaborador, curso, responsable, OrigenCurso.SOLICITUD_COLABORADOR,
                EstadoColaboradorCurso.SOLICITADO);
    }

    private ColaboradorCurso conEvidencia(Long id, Usuario responsable) {
        ColaboradorCurso item = inscripcion(id, colaborador, curso, responsable, OrigenCurso.SOLICITUD_COLABORADOR,
                EstadoColaboradorCurso.EVIDENCIA_PENDIENTE);
        item.setEvidenciaUrl("/uploads/evidencias/" + id + ".pdf");
        item.setMotivoRespuesta("Aprobado por relevancia");
        return item;
    }

    @Nested
    class Aprobar {

        @Test
        void solicitudPendienteSePoneEnCursoNotificaYEnviaCorreo() {
            rmExiste();
            sesionDelRm();
            guardarDevuelveLoMismo();
            curso.setFechaInicio(LocalDate.of(2027, 3, 1));
            curso.setFechaFin(LocalDate.of(2027, 4, 15));
            curso.setHoras(new BigDecimal("12.50"));
            ColaboradorCurso item = solicitud(5L, rm);
            when(colaboradorCursoRepository.findByIdConDetalle(5L)).thenReturn(Optional.of(item));
            when(usuarioRepository.findById(10L)).thenReturn(Optional.of(colaborador));
            when(cursoRepository.findById(100L)).thenReturn(Optional.of(curso));

            ColaboradorCurso resultado = servicio.aprobar(5L, "  Útil para el proyecto  ", 1L);

            assertEquals(EstadoColaboradorCurso.EN_CURSO, resultado.getEstado());
            assertSame(rm, resultado.getAsignadoPor());
            assertNotNull(resultado.getFechaRespuesta());
            assertEquals("Útil para el proyecto", resultado.getMotivoRespuesta());
            verify(notificacionService).crear(eq(colaborador), eq("CURSO_APROBADO"), eq(CategoriaNotificacion.CURSO),
                    eq("Solicitud de curso aprobada"), contains("Inicio: 01/03/2027 · Fin: 15/04/2027 · Duración: 12.5 h"),
                    eq("COLABORADOR_CURSO"), eq(5L));
            verify(emailService).enviarInscripcionAprobada(colaborador.getCorreo(), "Curso100",
                    "01/03/2027", "15/04/2027", "12.5 h");
        }

        @Test
        void cursoSinFechasNiHorasMuestraPorConfirmar() {
            rmExiste();
            sesionDelRm();
            guardarDevuelveLoMismo();
            curso.setHoras(null);
            when(colaboradorCursoRepository.findByIdConDetalle(5L)).thenReturn(Optional.of(solicitud(5L, rm)));
            when(usuarioRepository.findById(10L)).thenReturn(Optional.of(colaborador));
            when(cursoRepository.findById(100L)).thenReturn(Optional.of(curso));

            servicio.aprobar(5L, "ok", 1L);

            verify(emailService).enviarInscripcionAprobada(colaborador.getCorreo(), "Curso100",
                    "Por confirmar", "Por confirmar", "Por confirmar");
        }

        @Test
        void siElCorreoFallaLaAprobacionSeConserva() {
            rmExiste();
            sesionDelRm();
            guardarDevuelveLoMismo();
            when(colaboradorCursoRepository.findByIdConDetalle(5L)).thenReturn(Optional.of(solicitud(5L, rm)));
            when(usuarioRepository.findById(10L)).thenReturn(Optional.of(colaborador));
            when(cursoRepository.findById(100L)).thenReturn(Optional.of(curso));
            doThrow(new RuntimeException("SMTP caído")).when(emailService)
                    .enviarInscripcionAprobada(anyString(), anyString(), anyString(), anyString(), anyString());

            ColaboradorCurso resultado = servicio.aprobar(5L, "ok", 1L);

            assertEquals(EstadoColaboradorCurso.EN_CURSO, resultado.getEstado());
            verify(notificacionService).crear(any(), eq("CURSO_APROBADO"), any(), anyString(), anyString(),
                    anyString(), anyLong());
        }

        @Test
        void rmInexistenteLanzaSinTocarNada() {
            when(usuarioRepository.findById(1L)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.aprobar(5L, "ok", 1L));

            assertEquals("No se encontró un Resource Manager activo.", ex.getMessage());
            verify(colaboradorCursoRepository, never()).save(any());
            verifyNoInteractions(notificacionService, emailService);
        }

        @Test
        void usuarioConOtroRolOInactivoNoEsRm() {
            when(usuarioRepository.findById(3L)).thenReturn(Optional.of(pm(3L)));
            assertThrows(IllegalArgumentException.class, () -> servicio.aprobar(5L, "ok", 3L));

            rm.setActivo(false);
            rmExiste();
            assertThrows(IllegalArgumentException.class, () -> servicio.aprobar(5L, "ok", 1L));
        }

        @Test
        void solicitudInexistenteLanza() {
            rmExiste();
            when(colaboradorCursoRepository.findByIdConDetalle(5L)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.aprobar(5L, "ok", 1L));

            assertEquals("No se encontró la solicitud de curso.", ex.getMessage());
        }

        @Test
        void solicitudDeOtroRmSeTrataComoInexistente() {
            rmExiste();
            sesionDelRm();
            when(colaboradorCursoRepository.findByIdConDetalle(5L)).thenReturn(Optional.of(solicitud(5L, otroRm)));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.aprobar(5L, "ok", 1L));

            assertEquals("No se encontró la solicitud de curso.", ex.getMessage());
            verify(colaboradorCursoRepository, never()).save(any());
        }

        @Test
        void solicitudYaGestionadaOAsignadaPorRmLanza() {
            rmExiste();
            sesionDelRm();
            ColaboradorCurso enCurso = solicitud(5L, rm);
            enCurso.setEstado(EstadoColaboradorCurso.EN_CURSO);
            ColaboradorCurso asignada = solicitud(6L, rm);
            asignada.setOrigen(OrigenCurso.ASIGNADO_POR_RM);
            when(colaboradorCursoRepository.findByIdConDetalle(5L)).thenReturn(Optional.of(enCurso));
            when(colaboradorCursoRepository.findByIdConDetalle(6L)).thenReturn(Optional.of(asignada));

            for (Long id : List.of(5L, 6L)) {
                IllegalStateException ex = assertThrows(IllegalStateException.class,
                        () -> servicio.aprobar(id, "ok", 1L));
                assertEquals("La solicitud ya fue gestionada o no fue enviada por un colaborador.", ex.getMessage());
            }
            verify(colaboradorCursoRepository, never()).save(any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void motivoVacioLanza(String motivo) {
            rmExiste();
            sesionDelRm();
            when(colaboradorCursoRepository.findByIdConDetalle(5L)).thenReturn(Optional.of(solicitud(5L, rm)));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.aprobar(5L, motivo, 1L));

            assertEquals("El motivo de la aprobación es obligatorio.", ex.getMessage());
            verifyNoInteractions(emailService, notificacionService);
        }

        @Test
        void motivoDeMasDe500CaracteresLanza() {
            rmExiste();
            sesionDelRm();
            when(colaboradorCursoRepository.findByIdConDetalle(5L)).thenReturn(Optional.of(solicitud(5L, rm)));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.aprobar(5L, "a".repeat(501), 1L));

            assertEquals("El motivo no puede superar 500 caracteres.", ex.getMessage());
        }

        @Test
        void colaboradorInactivoLanza() {
            rmExiste();
            sesionDelRm();
            colaborador.setActivo(false);
            when(colaboradorCursoRepository.findByIdConDetalle(5L)).thenReturn(Optional.of(solicitud(5L, rm)));
            when(usuarioRepository.findById(10L)).thenReturn(Optional.of(colaborador));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.aprobar(5L, "ok", 1L));

            assertEquals("No se encontró un colaborador activo.", ex.getMessage());
        }

        @Test
        void cursoInactivoLanza() {
            rmExiste();
            sesionDelRm();
            curso.setActivo(false);
            when(colaboradorCursoRepository.findByIdConDetalle(5L)).thenReturn(Optional.of(solicitud(5L, rm)));
            when(usuarioRepository.findById(10L)).thenReturn(Optional.of(colaborador));
            when(cursoRepository.findById(100L)).thenReturn(Optional.of(curso));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.aprobar(5L, "ok", 1L));

            assertEquals("No se encontró un curso activo.", ex.getMessage());
        }

        @Test
        void colaboradorYaEnCursoLanzaSinCorreo() {
            rmExiste();
            sesionDelRm();
            ColaboradorCurso item = solicitud(5L, rm);
            when(colaboradorCursoRepository.findByIdConDetalle(5L)).thenReturn(Optional.of(item));
            when(usuarioRepository.findById(10L)).thenReturn(Optional.of(colaborador));
            when(cursoRepository.findById(100L)).thenReturn(Optional.of(curso));
            when(colaboradorCursoRepository.existsByColaboradorAndCursoAndEstado(
                    colaborador, curso, EstadoColaboradorCurso.EN_CURSO)).thenReturn(true);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> servicio.aprobar(5L, "ok", 1L));

            assertEquals("El colaborador ya tiene una inscripción activa en este curso.", ex.getMessage());
            assertEquals(EstadoColaboradorCurso.SOLICITADO, item.getEstado());
            verify(colaboradorCursoRepository, never()).save(any());
            verifyNoInteractions(emailService, notificacionService);
        }

        @Test
        void inscripcionSinResponsableLaPuedeAprobarCualquierRm() {
            rmExiste();
            sesionDelRm();
            guardarDevuelveLoMismo();
            when(colaboradorCursoRepository.findByIdConDetalle(5L)).thenReturn(Optional.of(solicitud(5L, null)));
            when(usuarioRepository.findById(10L)).thenReturn(Optional.of(colaborador));
            when(cursoRepository.findById(100L)).thenReturn(Optional.of(curso));

            assertEquals(EstadoColaboradorCurso.EN_CURSO, servicio.aprobar(5L, "ok", 1L).getEstado());
        }
    }

    @Nested
    class Rechazar {

        @Test
        void solicitudPendienteQuedaRechazadaYNotificaSinCorreo() {
            rmExiste();
            sesionDelRm();
            guardarDevuelveLoMismo();
            when(colaboradorCursoRepository.findByIdConDetalle(5L)).thenReturn(Optional.of(solicitud(5L, rm)));

            ColaboradorCurso resultado = servicio.rechazar(5L, " No aplica ", 1L);

            assertEquals(EstadoColaboradorCurso.RECHAZADO, resultado.getEstado());
            assertSame(rm, resultado.getAsignadoPor());
            assertEquals("No aplica", resultado.getMotivoRespuesta());
            assertNotNull(resultado.getFechaRespuesta());
            verify(notificacionService).crear(eq(colaborador), eq("CURSO_RECHAZADO"), eq(CategoriaNotificacion.CURSO),
                    eq("Solicitud de curso rechazada"), eq("Tu solicitud para “Curso100” fue rechazada. Motivo: No aplica"),
                    eq("COLABORADOR_CURSO"), eq(5L));
            verifyNoInteractions(emailService);
        }

        @Test
        void motivoVacioLanzaYNoGuarda() {
            rmExiste();
            sesionDelRm();
            when(colaboradorCursoRepository.findByIdConDetalle(5L)).thenReturn(Optional.of(solicitud(5L, rm)));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.rechazar(5L, null, 1L));

            assertEquals("El motivo del rechazo es obligatorio.", ex.getMessage());
            verify(colaboradorCursoRepository, never()).save(any());
            verifyNoInteractions(notificacionService);
        }

        @Test
        void solicitudYaRechazadaLanza() {
            rmExiste();
            sesionDelRm();
            ColaboradorCurso item = solicitud(5L, rm);
            item.setEstado(EstadoColaboradorCurso.RECHAZADO);
            when(colaboradorCursoRepository.findByIdConDetalle(5L)).thenReturn(Optional.of(item));

            assertThrows(IllegalStateException.class, () -> servicio.rechazar(5L, "x", 1L));
            verify(colaboradorCursoRepository, never()).save(any());
        }
    }

    @Nested
    class AprobarEvidencia {

        @Test
        void evidenciaPendienteCompletaElCursoConRevisorFechaYAuditoria() {
            rmExiste();
            sesionDelRm();
            guardarDevuelveLoMismo();
            ColaboradorCurso item = conEvidencia(5L, rm);
            item.setAsignadoPor(otroRm);
            when(colaboradorCursoRepository.findByIdConDetalle(5L)).thenReturn(Optional.of(item));

            ColaboradorCurso resultado = servicio.aprobarEvidencia(5L, 1L);

            assertEquals(EstadoColaboradorCurso.COMPLETADO, resultado.getEstado());
            assertNotNull(resultado.getFechaCompletado());
            assertEquals(resultado.getFechaCompletado(), resultado.getFechaRevisionEvidencia());
            assertSame(rm, resultado.getEvidenciaRevisadaPor());
            assertSame(otroRm, resultado.getAsignadoPor());
            verify(notificacionService).crear(eq(colaborador), eq("CURSO_COMPLETADO"), eq(CategoriaNotificacion.CURSO),
                    eq("Curso completado"), anyString(), eq("COLABORADOR_CURSO"), eq(5L));
            verify(auditoriaService).registrar(eq(rm), eq("APROBAR_EVIDENCIA_CURSO"), eq("COLABORADOR_CURSO"), eq(5L),
                    contains("Horas confirmadas: 20 h"), eq("EVIDENCIA_PENDIENTE"), eq("COMPLETADO"), eq(null));
        }

        @Test
        void sinEvidenciaPendienteLanzaYNoAudita() {
            rmExiste();
            sesionDelRm();
            ColaboradorCurso item = conEvidencia(5L, rm);
            item.setEstado(EstadoColaboradorCurso.EN_CURSO);
            when(colaboradorCursoRepository.findByIdConDetalle(5L)).thenReturn(Optional.of(item));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> servicio.aprobarEvidencia(5L, 1L));

            assertEquals("Esta inscripción no tiene una evidencia pendiente de revisión.", ex.getMessage());
            verify(colaboradorCursoRepository, never()).save(any());
            verifyNoInteractions(auditoriaService, notificacionService);
        }

        @Test
        void inscripcionInexistenteODeOtroRmLanza() {
            rmExiste();
            sesionDelRm();
            when(colaboradorCursoRepository.findByIdConDetalle(5L)).thenReturn(Optional.empty());
            when(colaboradorCursoRepository.findByIdConDetalle(6L)).thenReturn(Optional.of(conEvidencia(6L, otroRm)));

            for (Long id : List.of(5L, 6L)) {
                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                        () -> servicio.aprobarEvidencia(id, 1L));
                assertEquals("No se encontró la inscripción al curso.", ex.getMessage());
            }
            verifyNoInteractions(auditoriaService);
        }
    }

    @Nested
    class RechazarEvidencia {

        @Test
        void vuelveAEnCursoConRevisorYGuardaElMotivoPrevioEnAuditoria() {
            rmExiste();
            sesionDelRm();
            guardarDevuelveLoMismo();
            when(colaboradorCursoRepository.findByIdConDetalle(5L)).thenReturn(Optional.of(conEvidencia(5L, rm)));

            ColaboradorCurso resultado = servicio.rechazarEvidencia(5L, "Archivo ilegible", 1L);

            assertEquals(EstadoColaboradorCurso.EN_CURSO, resultado.getEstado());
            assertEquals("Archivo ilegible", resultado.getMotivoRespuesta());
            assertSame(rm, resultado.getEvidenciaRevisadaPor());
            assertNotNull(resultado.getFechaRevisionEvidencia());
            assertNotNull(resultado.getEvidenciaUrl());
            verify(notificacionService).crear(eq(colaborador), eq("EVIDENCIA_CURSO_RECHAZADA"),
                    eq(CategoriaNotificacion.CURSO), eq("Evidencia de curso rechazada"),
                    contains("Motivo: Archivo ilegible Puedes volver a subirla."), eq("COLABORADOR_CURSO"), eq(5L));
            ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
            verify(auditoriaService).registrar(eq(rm), eq("RECHAZAR_EVIDENCIA_CURSO"), eq("COLABORADOR_CURSO"),
                    eq(5L), detalle.capture(), eq("EVIDENCIA_PENDIENTE"), eq("EN_CURSO"), eq(null));
            assertTrue(detalle.getValue().contains("Motivo previo de la inscripción: Aprobado por relevancia."));
            assertTrue(detalle.getValue().endsWith("Motivo: Archivo ilegible"));
        }

        @Test
        void sinMotivoPrevioNoLoMencionaEnAuditoria() {
            rmExiste();
            sesionDelRm();
            guardarDevuelveLoMismo();
            ColaboradorCurso item = conEvidencia(5L, rm);
            item.setMotivoRespuesta(null);
            when(colaboradorCursoRepository.findByIdConDetalle(5L)).thenReturn(Optional.of(item));

            servicio.rechazarEvidencia(5L, "x", 1L);

            ArgumentCaptor<String> detalle = ArgumentCaptor.forClass(String.class);
            verify(auditoriaService).registrar(any(), anyString(), anyString(), anyLong(), detalle.capture(),
                    anyString(), anyString(), eq(null));
            assertFalse(detalle.getValue().contains("Motivo previo"));
        }

        @Test
        void motivoVacioLanzaSinCambiarEstado() {
            rmExiste();
            sesionDelRm();
            ColaboradorCurso item = conEvidencia(5L, rm);
            when(colaboradorCursoRepository.findByIdConDetalle(5L)).thenReturn(Optional.of(item));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.rechazarEvidencia(5L, " ", 1L));

            assertEquals("El motivo del rechazo de la evidencia es obligatorio.", ex.getMessage());
            assertEquals(EstadoColaboradorCurso.EVIDENCIA_PENDIENTE, item.getEstado());
            verify(colaboradorCursoRepository, never()).save(any());
            verifyNoInteractions(auditoriaService);
        }
    }

    @Nested
    class AsignarDirectamente {

        private void datosValidos() {
            rmExiste();
            when(usuarioRepository.findById(10L)).thenReturn(Optional.of(colaborador));
            when(cursoRepository.findById(100L)).thenReturn(Optional.of(curso));
        }

        @Test
        void creaLaInscripcionEnCursoAsignadaPorElRm() {
            datosValidos();
            guardarDevuelveLoMismo();
            curso.setFechaFin(LocalDate.now());

            ColaboradorCurso resultado = servicio.asignarDirectamente(10L, 100L, " Necesario ", 1L);

            assertSame(colaborador, resultado.getColaborador());
            assertSame(curso, resultado.getCurso());
            assertEquals(OrigenCurso.ASIGNADO_POR_RM, resultado.getOrigen());
            assertEquals(EstadoColaboradorCurso.EN_CURSO, resultado.getEstado());
            assertSame(rm, resultado.getAsignadoPor());
            assertSame(rm, resultado.getRmResponsable());
            assertNotNull(resultado.getFechaRespuesta());
            assertEquals("Necesario", resultado.getMotivoRespuesta());
            verify(notificacionService).crear(eq(colaborador), eq("CURSO_ASIGNADO"), eq(CategoriaNotificacion.CURSO),
                    eq("Nuevo curso asignado"), eq("El Resource Manager te inscribió en “Curso100”."),
                    eq("COLABORADOR_CURSO"), any());
        }

        @Test
        void cursoCaducadoLanza() {
            datosValidos();
            curso.setFechaFin(LocalDate.now().minusDays(1));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> servicio.asignarDirectamente(10L, 100L, "x", 1L));

            assertEquals("Este curso ya terminó: su fecha de fin ya pasó.", ex.getMessage());
            verify(colaboradorCursoRepository, never()).save(any());
        }

        @Test
        void colaboradorYaInscritoOConSolicitudLanza() {
            datosValidos();
            when(colaboradorCursoRepository.existsByColaboradorAndCursoAndEstadoIn(eq(colaborador), eq(curso), any()))
                    .thenReturn(true);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> servicio.asignarDirectamente(10L, 100L, "x", 1L));

            assertEquals("El colaborador ya tiene una solicitud pendiente o una inscripción activa en este curso.",
                    ex.getMessage());
            verify(colaboradorCursoRepository, never()).save(any());
            verifyNoInteractions(notificacionService);
        }

        @Test
        void losEstadosDuplicadosSonSolicitadoEnCursoYEvidenciaPendiente() {
            datosValidos();
            guardarDevuelveLoMismo();

            servicio.asignarDirectamente(10L, 100L, "x", 1L);

            verify(colaboradorCursoRepository).existsByColaboradorAndCursoAndEstadoIn(colaborador, curso,
                    List.of(EstadoColaboradorCurso.SOLICITADO, EstadoColaboradorCurso.EN_CURSO,
                            EstadoColaboradorCurso.EVIDENCIA_PENDIENTE));
        }

        @Test
        void motivoVacioLanza() {
            datosValidos();

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.asignarDirectamente(10L, 100L, "", 1L));

            assertEquals("El motivo de la asignación es obligatorio.", ex.getMessage());
            verify(colaboradorCursoRepository, never()).save(any());
        }

        @Test
        void destinatarioQueNoEsColaboradorLanza() {
            rmExiste();
            when(usuarioRepository.findById(3L)).thenReturn(Optional.of(pm(3L)));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.asignarDirectamente(3L, 100L, "x", 1L));

            assertEquals("No se encontró un colaborador activo.", ex.getMessage());
        }

        @Test
        void cursoInexistenteLanza() {
            rmExiste();
            when(usuarioRepository.findById(10L)).thenReturn(Optional.of(colaborador));
            when(cursoRepository.findById(100L)).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> servicio.asignarDirectamente(10L, 100L, "x", 1L));

            assertEquals("No se encontró un curso activo.", ex.getMessage());
        }
    }

    @Nested
    class MarcarCursosSinEvidenciaVencidos {

        @Test
        void delegaEnLaReglaDelColaborador() {
            servicio.marcarCursosSinEvidenciaVencidos();

            verify(colaboradorCursoService).marcarTodosLosCursosSinEvidenciaVencidos();
            verifyNoInteractions(colaboradorCursoRepository);
        }
    }

    @Nested
    class ContarSolicitudesPendientes {

        @Test
        void cuentaSoloSolicitudesDeColaboradorPendientesDelRm() {
            sesionDelRm();
            ColaboradorCurso asignada = solicitud(3L, rm);
            asignada.setOrigen(OrigenCurso.ASIGNADO_POR_RM);
            ColaboradorCurso aprobada = solicitud(4L, rm);
            aprobada.setEstado(EstadoColaboradorCurso.EN_CURSO);
            when(colaboradorCursoRepository.findAllConDetalle()).thenReturn(List.of(
                    solicitud(1L, rm), solicitud(2L, null), solicitud(5L, otroRm), asignada, aprobada));

            assertEquals(2, servicio.contarSolicitudesPendientes());
        }
    }

    @Nested
    class NormalizarFiltrosBandeja {

        @Test
        void sinEstadoMuestraPendientes() {
            FiltrosBandeja filtros = servicio.normalizarFiltrosBandeja(null, null, null);

            assertEquals(FiltroEstadoBandeja.SOLICITADO, filtros.estado());
            assertNull(filtros.busqueda());
            assertNull(filtros.origen());
        }

        @Test
        void estadoVacioODesconocidoEsTodos() {
            assertNull(servicio.normalizarFiltrosBandeja(null, "", null).estado());
            assertNull(servicio.normalizarFiltrosBandeja(null, "INVENTADO", null).estado());
            assertNull(servicio.normalizarFiltrosBandeja(null, "rechazado", null).estado());
        }

        @Test
        void valoresValidosSeRecortanYSeConservan() {
            FiltrosBandeja filtros = servicio.normalizarFiltrosBandeja("  ana  ", " RECHAZADO ", " ASIGNADO_POR_RM ");

            assertEquals("ana", filtros.busqueda());
            assertEquals(FiltroEstadoBandeja.RECHAZADO, filtros.estado());
            assertEquals(OrigenCurso.ASIGNADO_POR_RM, filtros.origen());
        }

        @Test
        void origenDesconocidoEsTodosYBusquedaSeCortaA100() {
            FiltrosBandeja filtros = servicio.normalizarFiltrosBandeja("x".repeat(150), "EN_CURSO", "OTRO");

            assertEquals(100, filtros.busqueda().length());
            assertNull(filtros.origen());
            assertNull(servicio.normalizarFiltrosBandeja("   ", "EN_CURSO", null).busqueda());
        }
    }

    @Nested
    class NormalizarFiltrosCatalogo {

        @Test
        void categoriaExistenteSinTildesNiMayusculasDevuelveLaOriginal() {
            Curso otro = curso(101L);
            otro.setCategoria("Gestión");
            when(cursoRepository.findByActivoTrueOrderByNombreAsc()).thenReturn(List.of(curso, otro));

            FiltrosCatalogo filtros = servicio.normalizarFiltrosCatalogo(" sql ", "GESTION", " Media ");

            assertEquals("sql", filtros.busqueda());
            assertEquals("Gestión", filtros.categoria());
            assertEquals("media", filtros.duracion());
        }

        @Test
        void categoriaYDuracionDesconocidasSonTodas() {
            when(cursoRepository.findByActivoTrueOrderByNombreAsc()).thenReturn(List.of(curso));

            FiltrosCatalogo filtros = servicio.normalizarFiltrosCatalogo(null, "Cocina", "eterna");

            assertNull(filtros.categoria());
            assertNull(filtros.duracion());
            assertNull(filtros.busqueda());
        }

        @Test
        void categoriaVaciaNoConsultaCursos() {
            FiltrosCatalogo filtros = servicio.normalizarFiltrosCatalogo(null, " ", null);

            assertNull(filtros.categoria());
            verifyNoInteractions(cursoRepository);
        }
    }

    @Nested
    class ObtenerCatalogo {

        private Curso cursoDe(Long id, String nombre, String categoria, String horas) {
            Curso item = curso(id);
            item.setNombre(nombre);
            item.setCategoria(categoria);
            item.setHoras(new BigDecimal(horas));
            return item;
        }

        @Test
        void filtraPorDuracionSegunHoras() {
            List<Curso> cursos = List.of(cursoDe(1L, "A", "Datos", "12"), cursoDe(2L, "B", "Datos", "12.5"),
                    cursoDe(3L, "C", "Datos", "30"), cursoDe(4L, "D", "Datos", "31"));
            when(cursoRepository.findByActivoTrueOrderByNombreAsc()).thenReturn(cursos);
            when(colaboradorCursoRepository.findAllConDetalle()).thenReturn(List.of());

            assertEquals(List.of(1L), ids(servicio.obtenerCatalogo(new FiltrosCatalogo(null, null, "corta"), null)));
            assertEquals(List.of(2L, 3L), ids(servicio.obtenerCatalogo(new FiltrosCatalogo(null, null, "media"), null)));
            assertEquals(List.of(4L), ids(servicio.obtenerCatalogo(new FiltrosCatalogo(null, null, "larga"), null)));
        }

        @Test
        void buscaSinTildesEnNombreDescripcionYCategoria() {
            Curso conDescripcion = cursoDe(2L, "Otro", "Datos", "10");
            conDescripcion.setDescripcion("Introducción a la programación");
            List<Curso> cursos = List.of(cursoDe(1L, "Gestión ágil", "Gestión", "10"), conDescripcion,
                    cursoDe(3L, "Excel", "Ofimática", "10"));
            when(cursoRepository.findByActivoTrueOrderByNombreAsc()).thenReturn(cursos);
            when(colaboradorCursoRepository.findAllConDetalle()).thenReturn(List.of());

            assertEquals(List.of(1L), ids(servicio.obtenerCatalogo(new FiltrosCatalogo("AGIL", null, null), null)));
            assertEquals(List.of(2L), ids(servicio.obtenerCatalogo(new FiltrosCatalogo("programacion", null, null), null)));
            assertEquals(List.of(3L), ids(servicio.obtenerCatalogo(new FiltrosCatalogo(null, "ofimatica", null), null)));
        }

        @Test
        void paginaDeSeisYPaginaFueraDeRangoVaALaUltima() {
            List<Curso> cursos = new ArrayList<>();
            for (long i = 1; i <= 8; i++) cursos.add(cursoDe(i, "C" + i, "Datos", "10"));
            when(cursoRepository.findByActivoTrueOrderByNombreAsc()).thenReturn(cursos);
            when(colaboradorCursoRepository.findAllConDetalle()).thenReturn(List.of());
            FiltrosCatalogo todos = new FiltrosCatalogo(null, null, null);

            RmCursoView.Catalogo primera = servicio.obtenerCatalogo(todos, "abc");
            RmCursoView.Catalogo ultima = servicio.obtenerCatalogo(todos, "99");

            assertEquals(1, primera.getPaginaActual());
            assertEquals(6, primera.getCursos().size());
            assertEquals(2, primera.getTotalPaginas());
            assertEquals(8, primera.getTotalRegistros());
            assertEquals(2, ultima.getPaginaActual());
            assertEquals(List.of(7L, 8L), ids(ultima));
            assertEquals(1, servicio.obtenerCatalogo(todos, "-3").getPaginaActual());
        }

        @Test
        void indicadoresSeCalculanSobreTodoSinFiltros() {
            Curso otro = cursoDe(101L, "Otro", null, "40");
            when(cursoRepository.findByActivoTrueOrderByNombreAsc()).thenReturn(List.of(curso, otro));
            ColaboradorCurso enCurso = solicitud(2L, rm);
            enCurso.setEstado(EstadoColaboradorCurso.EN_CURSO);
            ColaboradorCurso asignadaHoy = inscripcion(3L, colaborador, otro, rm, OrigenCurso.ASIGNADO_POR_RM,
                    EstadoColaboradorCurso.EN_CURSO);
            asignadaHoy.setFechaSolicitud(LocalDateTime.now());
            ColaboradorCurso asignadaAntes = inscripcion(4L, colaborador, otro, rm, OrigenCurso.ASIGNADO_POR_RM,
                    EstadoColaboradorCurso.COMPLETADO);
            asignadaAntes.setFechaSolicitud(LocalDateTime.now().minusMonths(2));
            when(colaboradorCursoRepository.findAllConDetalle())
                    .thenReturn(List.of(solicitud(1L, rm), enCurso, asignadaHoy, asignadaAntes));

            RmCursoView.Catalogo catalogo = servicio.obtenerCatalogo(new FiltrosCatalogo(null, null, "corta"), null);

            assertEquals(0, catalogo.getTotalRegistros());
            assertEquals(2, catalogo.getCursosActivos());
            assertEquals(List.of("Datos"), catalogo.getCategorias());
            assertEquals(1, catalogo.getSolicitudesPendientes());
            assertEquals(2, catalogo.getInscripcionesActivas());
            assertEquals(1, catalogo.getAsignadosPorRmEsteMes());
        }

        @Test
        void cadaCursoCuentaSusInscritosEnCursoYUsaTextosPorDefecto() {
            Curso otro = cursoDe(101L, "Otro", " ", "40");
            when(cursoRepository.findByActivoTrueOrderByNombreAsc()).thenReturn(List.of(curso, otro));
            ColaboradorCurso enCurso = solicitud(2L, rm);
            enCurso.setEstado(EstadoColaboradorCurso.EN_CURSO);
            when(colaboradorCursoRepository.findAllConDetalle()).thenReturn(List.of(solicitud(1L, rm), enCurso));

            List<RmCursoView.CursoItem> items = servicio.obtenerCatalogo(new FiltrosCatalogo(null, null, null), null)
                    .getCursos();

            assertEquals(1, items.get(0).getInscritos());
            assertEquals(0, items.get(1).getInscritos());
            assertEquals("Sin categoría", items.get(1).getCategoria());
            assertEquals("Sin descripción registrada.", items.get(1).getDescripcion());
        }

        private List<Long> ids(RmCursoView.Catalogo catalogo) {
            return catalogo.getCursos().stream().map(RmCursoView.CursoItem::getId).toList();
        }
    }

    @Nested
    class ObtenerBandeja {

        private List<Long> ids(RmCursoView.Bandeja bandeja) {
            return bandeja.getInscripciones().stream().map(RmCursoView.InscripcionItem::getId).toList();
        }

        @Test
        void soloVeLasInscripcionesDeSuAlcance() {
            sesionDelRm();
            when(colaboradorCursoRepository.findAllConDetalle())
                    .thenReturn(List.of(solicitud(1L, rm), solicitud(2L, otroRm), solicitud(3L, null)));

            RmCursoView.Bandeja bandeja = servicio.obtenerBandeja(new FiltrosBandeja(null, null, null), null);

            assertEquals(List.of(1L, 3L), ids(bandeja));
            assertEquals(2, bandeja.getPendientes());
        }

        @Test
        void filtroRechazadoAgrupaSolicitudYEvidenciaRechazadas() {
            sesionDelRm();
            ColaboradorCurso solicitudRechazada = solicitud(1L, rm);
            solicitudRechazada.setEstado(EstadoColaboradorCurso.RECHAZADO);
            ColaboradorCurso evidenciaRechazada = conEvidencia(2L, rm);
            evidenciaRechazada.setEstado(EstadoColaboradorCurso.EN_CURSO);
            ColaboradorCurso enCurso = solicitud(3L, rm);
            enCurso.setEstado(EstadoColaboradorCurso.EN_CURSO);
            when(colaboradorCursoRepository.findAllConDetalle())
                    .thenReturn(List.of(solicitudRechazada, evidenciaRechazada, enCurso));

            assertEquals(List.of(1L, 2L),
                    ids(servicio.obtenerBandeja(new FiltrosBandeja(null, FiltroEstadoBandeja.RECHAZADO, null), null)));
            assertEquals(List.of(3L),
                    ids(servicio.obtenerBandeja(new FiltrosBandeja(null, FiltroEstadoBandeja.EN_CURSO, null), null)));
            assertEquals(List.of(2L), ids(servicio.obtenerBandeja(
                    new FiltrosBandeja(null, FiltroEstadoBandeja.EVIDENCIA_RECHAZADA, null), null)));
        }

        @Test
        void filtraPorOrigenYBusquedaSinTildes() {
            sesionDelRm();
            colaborador.setNombre("José");
            ColaboradorCurso asignada = solicitud(2L, rm);
            asignada.setOrigen(OrigenCurso.ASIGNADO_POR_RM);
            Curso otro = curso(101L);
            otro.setNombre("Python");
            ColaboradorCurso deOtroCurso = inscripcion(3L, colaborador(11L), otro, rm,
                    OrigenCurso.SOLICITUD_COLABORADOR, EstadoColaboradorCurso.SOLICITADO);
            when(colaboradorCursoRepository.findAllConDetalle())
                    .thenReturn(List.of(solicitud(1L, rm), asignada, deOtroCurso));

            assertEquals(List.of(2L), ids(servicio.obtenerBandeja(
                    new FiltrosBandeja(null, null, OrigenCurso.ASIGNADO_POR_RM), null)));
            assertEquals(List.of(1L, 2L), ids(servicio.obtenerBandeja(new FiltrosBandeja("jose", null, null), null)));
            assertEquals(List.of(3L), ids(servicio.obtenerBandeja(new FiltrosBandeja("PYTHON", null, null), null)));
        }

        @Test
        void paginaDeDiez() {
            sesionDelRm();
            List<ColaboradorCurso> todas = new ArrayList<>();
            for (long i = 1; i <= 12; i++) todas.add(solicitud(i, rm));
            when(colaboradorCursoRepository.findAllConDetalle()).thenReturn(todas);

            RmCursoView.Bandeja segunda = servicio.obtenerBandeja(new FiltrosBandeja(null, null, null), " 2 ");

            assertEquals(2, segunda.getPaginaActual());
            assertEquals(2, segunda.getTotalPaginas());
            assertEquals(12, segunda.getTotalRegistros());
            assertEquals(List.of(11L, 12L), ids(segunda));
        }

        @Test
        void indicadoresIgnoranLosFiltrosYUsanElMesActual() {
            sesionDelRm();
            ColaboradorCurso aprobadaHoy = solicitud(1L, rm);
            aprobadaHoy.setEstado(EstadoColaboradorCurso.EN_CURSO);
            aprobadaHoy.setFechaRespuesta(LocalDateTime.now());
            ColaboradorCurso aprobadaAntes = solicitud(2L, rm);
            aprobadaAntes.setEstado(EstadoColaboradorCurso.EN_CURSO);
            aprobadaAntes.setFechaRespuesta(LocalDateTime.now().minusMonths(1));
            ColaboradorCurso rechazadaHoy = solicitud(3L, rm);
            rechazadaHoy.setEstado(EstadoColaboradorCurso.RECHAZADO);
            rechazadaHoy.setFechaRespuesta(LocalDateTime.now());
            ColaboradorCurso evidenciaRechazada = conEvidencia(4L, rm);
            evidenciaRechazada.setEstado(EstadoColaboradorCurso.EN_CURSO);
            when(colaboradorCursoRepository.findAllConDetalle()).thenReturn(List.of(aprobadaHoy, aprobadaAntes,
                    rechazadaHoy, evidenciaRechazada, conEvidencia(5L, rm), solicitud(6L, rm)));

            RmCursoView.Bandeja bandeja = servicio.obtenerBandeja(
                    new FiltrosBandeja("nadie coincide", FiltroEstadoBandeja.COMPLETADO, null), null);

            assertEquals(0, bandeja.getTotalRegistros());
            assertEquals(1, bandeja.getTotalPaginas());
            assertEquals(1, bandeja.getPendientes());
            assertEquals(1, bandeja.getAprobadasEsteMes());
            assertEquals(1, bandeja.getRechazadasEsteMes());
            assertEquals(2, bandeja.getEnCurso());
            assertEquals(1, bandeja.getEvidenciasPorRevisar());
        }

        @Test
        void etiquetaDelMotivoYTextoDeRevisionSegunSituacion() {
            sesionDelRm();
            ColaboradorCurso asignada = solicitud(1L, rm);
            asignada.setOrigen(OrigenCurso.ASIGNADO_POR_RM);
            asignada.setEstado(EstadoColaboradorCurso.EN_CURSO);
            ColaboradorCurso aprobada = solicitud(2L, rm);
            aprobada.setEstado(EstadoColaboradorCurso.EN_CURSO);
            ColaboradorCurso completada = conEvidencia(3L, rm);
            completada.setEstado(EstadoColaboradorCurso.COMPLETADO);
            completada.setEvidenciaRevisadaPor(rm);
            ColaboradorCurso evidenciaRechazada = conEvidencia(4L, rm);
            evidenciaRechazada.setEstado(EstadoColaboradorCurso.EN_CURSO);
            evidenciaRechazada.setEvidenciaRevisadaPor(rm);
            ColaboradorCurso rechazada = solicitud(5L, rm);
            rechazada.setEstado(EstadoColaboradorCurso.RECHAZADO);
            when(colaboradorCursoRepository.findAllConDetalle()).thenReturn(List.of(asignada, aprobada, completada,
                    evidenciaRechazada, rechazada, solicitud(6L, rm), conEvidencia(7L, rm)));

            List<RmCursoView.InscripcionItem> items = servicio.obtenerBandeja(
                    new FiltrosBandeja(null, null, null), null).getInscripciones();

            assertEquals("Motivo de la asignación", items.get(0).getMotivoEtiqueta());
            assertEquals("Motivo de la aprobación", items.get(1).getMotivoEtiqueta());
            assertNull(items.get(2).getMotivoEtiqueta());
            assertEquals("Evidencia validada por", items.get(2).getRevisionTexto());
            assertEquals("EVIDENCIA_RECHAZADA", items.get(3).getSituacion());
            assertEquals("Motivo del rechazo de la evidencia", items.get(3).getMotivoEtiqueta());
            assertEquals("Evidencia rechazada por", items.get(3).getRevisionTexto());
            assertEquals("Motivo del rechazo de la solicitud", items.get(4).getMotivoEtiqueta());
            assertEquals("Justificación", items.get(5).getMotivoEtiqueta());
            assertNull(items.get(5).getRevisionTexto());
            assertEquals("EVIDENCIA_PENDIENTE", items.get(6).getSituacion());
        }
    }

    @Nested
    class EstadosYEtiquetas {

        @Test
        void tablaDeSituacionATextoYClase() {
            assertEquals("Pendiente", SituacionInscripcion.SOLICITADO.getTexto());
            assertEquals("bg-yellow-lt text-yellow", SituacionInscripcion.SOLICITADO.getClase());
            assertEquals("En curso", SituacionInscripcion.EN_CURSO.getTexto());
            assertEquals("bg-blue-lt text-blue", SituacionInscripcion.EN_CURSO.getClase());
            assertEquals("Evidencia en revisión", SituacionInscripcion.EVIDENCIA_PENDIENTE.getTexto());
            assertEquals("bg-purple-lt text-purple", SituacionInscripcion.EVIDENCIA_PENDIENTE.getClase());
            assertEquals("Evidencia rechazada", SituacionInscripcion.EVIDENCIA_RECHAZADA.getTexto());
            assertEquals("bg-red-lt text-red", SituacionInscripcion.EVIDENCIA_RECHAZADA.getClase());
            assertEquals("Solicitud rechazada", SituacionInscripcion.SOLICITUD_RECHAZADA.getTexto());
            assertEquals("bg-red-lt text-red", SituacionInscripcion.SOLICITUD_RECHAZADA.getClase());
            assertEquals("Completado", SituacionInscripcion.COMPLETADO.getTexto());
            assertEquals("bg-green-lt text-green", SituacionInscripcion.COMPLETADO.getClase());
            assertEquals("No completado", SituacionInscripcion.NO_COMPLETADO.getTexto());
            assertEquals("bg-secondary-lt text-secondary", SituacionInscripcion.NO_COMPLETADO.getClase());
        }

        @Test
        void filtroRechazadoIncluyeAmbosRechazosYLosDemasSoloElSuyo() {
            assertTrue(FiltroEstadoBandeja.RECHAZADO.incluye(SituacionInscripcion.SOLICITUD_RECHAZADA));
            assertTrue(FiltroEstadoBandeja.RECHAZADO.incluye(SituacionInscripcion.EVIDENCIA_RECHAZADA));
            assertFalse(FiltroEstadoBandeja.RECHAZADO.incluye(SituacionInscripcion.EN_CURSO));
            assertFalse(FiltroEstadoBandeja.EN_CURSO.incluye(SituacionInscripcion.EVIDENCIA_RECHAZADA));
            for (FiltroEstadoBandeja filtro : FiltroEstadoBandeja.values()) {
                if (filtro != FiltroEstadoBandeja.RECHAZADO) {
                    assertTrue(filtro.incluye(SituacionInscripcion.valueOf(filtro.name())));
                }
            }
        }
    }
}
