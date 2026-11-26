package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.model.ConfiguracionSistema;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.ConfiguracionSistemaRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.NotificacionService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.pm;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.proyecto;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.rm;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Reparto de proyectos entre RM: carga, tope, urgencia, tomar/liberar, responsable y escalamiento.
// Las fechas de urgencia se arman relativas a hoy porque el servicio usa LocalDate.now().
@ExtendWith(MockitoExtension.class)
class RmResponsableUnitTests {

    @Mock
    private ProyectoRepository proyectoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ConfiguracionSistemaRepository configuracionRepository;
    @Mock
    private NotificacionService notificacionService;
    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private RmResponsableService service;

    private Usuario yo;
    private Usuario otro;
    private Usuario elPm;

    // Carga (EN_REVISION + ACTIVO + EN_ESPERA) y proyectos solo en revisión, por id de RM.
    private final Map<Long, Long> carga = new HashMap<>();
    private final Map<Long, Long> enRevision = new HashMap<>();

    @BeforeEach
    void preparar() {
        yo = rm(1L);
        otro = rm(2L);
        elPm = pm(3L);
        lenient().when(configuracionRepository.findByClave(anyString())).thenReturn(Optional.empty());
        lenient().when(proyectoRepository.countByRmRevisorAndEstadoIn(any(), anyCollection())).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            Collection<?> estados = inv.getArgument(1);
            Map<Long, Long> fuente = estados.size() == 1 ? enRevision : carga;
            return fuente.getOrDefault(u.getId(), 0L);
        });
    }

    private void config(String clave, String valor) {
        ConfiguracionSistema c = new ConfiguracionSistema();
        c.setClave(clave);
        c.setValor(valor);
        when(configuracionRepository.findByClave(clave)).thenReturn(Optional.of(c));
    }

    // Proyecto normal: prioridad media, inicia dentro de 30 días (no urgente con 7 días por defecto).
    private Proyecto proyectoCon(Long id, Usuario responsable, EstadoProyecto estado) {
        Proyecto p = proyecto(id, LocalDate.now().plusDays(30), LocalDate.now().plusDays(100), "1000");
        p.setRmRevisor(responsable);
        p.setPm(elPm);
        p.setEstado(estado);
        p.setPrioridad(Prioridad.MEDIA);
        return p;
    }

    private Proyecto urgente(Long id, Usuario responsable, EstadoProyecto estado) {
        Proyecto p = proyectoCon(id, responsable, estado);
        p.setPrioridad(Prioridad.ALTA);
        return p;
    }

    @Nested
    class Configuracion {

        @Test
        void sinConfiguracionUsaValoresPorDefecto() {
            assertEquals(5, service.maxProyectosPorRm());
            assertEquals(7, service.diasUrgenciaInicio());
        }

        @Test
        void usaElValorConfiguradoSinEspacios() {
            config("MAX_PROYECTOS_POR_RM", " 8 ");
            config("DIAS_URGENCIA_INICIO", "3");
            assertEquals(8, service.maxProyectosPorRm());
            assertEquals(3, service.diasUrgenciaInicio());
        }

        @ParameterizedTest
        @ValueSource(strings = {"abc", "", "0", "-2"})
        void valorInvalidoOCeroUsaElPorDefecto(String valor) {
            config("MAX_PROYECTOS_POR_RM", valor);
            assertEquals(5, service.maxProyectosPorRm());
        }
    }

    @Nested
    class CargaDe {

        @Test
        void cuentaSoloEstadosConCarga() {
            when(proyectoRepository.countByRmRevisorAndEstadoIn(yo,
                    List.of(EstadoProyecto.EN_REVISION, EstadoProyecto.ACTIVO, EstadoProyecto.EN_ESPERA)))
                    .thenReturn(4L);
            assertEquals(4L, service.cargaDe(yo));
        }
    }

    @Nested
    class Urgencia {

        @Test
        void prioridadAltaEsUrgente() {
            Proyecto p = urgente(10L, null, EstadoProyecto.EN_REVISION);
            assertEquals("Prioridad alta", service.motivoUrgencia(p));
            assertTrue(service.esUrgente(p));
        }

        @Test
        void inicioEnElLimiteDeDiasEsUrgente() {
            Proyecto p = proyectoCon(10L, null, EstadoProyecto.EN_REVISION);
            LocalDate inicio = LocalDate.now().plusDays(7);
            p.setFechaInicio(inicio);
            assertEquals("Inicia pronto (" + inicio + ")", service.motivoUrgencia(p));
        }

        @Test
        void inicioHoyEsUrgentePeroNoVencido() {
            Proyecto p = proyectoCon(10L, null, EstadoProyecto.EN_REVISION);
            p.setFechaInicio(LocalDate.now());
            assertTrue(service.motivoUrgencia(p).startsWith("Inicia pronto"));
        }

        @Test
        void inicioUnDiaDespuesDelLimiteNoEsUrgente() {
            Proyecto p = proyectoCon(10L, null, EstadoProyecto.EN_REVISION);
            p.setFechaInicio(LocalDate.now().plusDays(8));
            assertNull(service.motivoUrgencia(p));
            assertFalse(service.esUrgente(p));
        }

        @Test
        void inicioPasadoEsUrgente() {
            Proyecto p = proyectoCon(10L, null, EstadoProyecto.EN_REVISION);
            p.setFechaInicio(LocalDate.now().minusDays(1));
            assertEquals("La fecha de inicio ya pasó", service.motivoUrgencia(p));
        }

        @Test
        void sinFechaNiPrioridadAltaNoEsUrgente() {
            Proyecto p = proyectoCon(10L, null, EstadoProyecto.EN_REVISION);
            p.setFechaInicio(null);
            p.setPrioridad(null);
            assertNull(service.motivoUrgencia(p));
        }

        @Test
        void respetaLosDiasConfigurados() {
            config("DIAS_URGENCIA_INICIO", "2");
            Proyecto p = proyectoCon(10L, null, EstadoProyecto.EN_REVISION);
            p.setFechaInicio(LocalDate.now().plusDays(3));
            assertNull(service.motivoUrgencia(p));
        }
    }

    @Nested
    class EsGestionable {

        @ParameterizedTest
        @EnumSource(value = EstadoProyecto.class, names = {"EN_REVISION", "ACTIVO", "EN_ESPERA"})
        void estadosConCargaSonGestionables(EstadoProyecto estado) {
            assertTrue(service.esGestionable(proyectoCon(10L, null, estado)));
        }

        @ParameterizedTest
        @EnumSource(value = EstadoProyecto.class, names = {"RECHAZADO", "CANCELADO", "FINALIZADO"})
        void estadosCerradosNoSonGestionables(EstadoProyecto estado) {
            assertFalse(service.esGestionable(proyectoCon(10L, null, estado)));
        }
    }

    @Nested
    class AsignarAlCrear {

        @Test
        void eligeAlRmConMenosCargaNotificaAAmbosYAudita() {
            carga.put(1L, 3L);
            carga.put(2L, 1L);
            when(usuarioRepository.findActivosByRolNombre("RESOURCE_MANAGER")).thenReturn(List.of(yo, otro));
            Proyecto p = proyectoCon(10L, null, EstadoProyecto.EN_REVISION);

            service.asignarAlCrear(p);

            assertSame(otro, p.getRmRevisor());
            verify(proyectoRepository).save(p);
            verify(notificacionService).crear(eq(otro), eq("PROYECTO_ASIGNADO_RM"), eq(CategoriaNotificacion.PROYECTO),
                    eq("Nuevo proyecto asignado"), anyString(), eq("PROYECTO"), eq(10L));
            verify(notificacionService).crear(eq(elPm), eq("PROYECTO_RM_ASIGNADO"), eq(CategoriaNotificacion.PROYECTO),
                    eq("RM responsable asignado"), anyString(), eq("PROYECTO"), eq(10L));
            verify(auditoriaService).registrar(isNull(), eq("ASIGNAR_RM_PROYECTO"), eq("PROYECTO"), eq(10L),
                    contains("Asignación automática"), isNull(), eq("Usuario2 Prueba"), isNull());
        }

        @Test
        void enEmpateEligeAlQueTieneMenosEnRevision() {
            carga.put(1L, 2L);
            carga.put(2L, 2L);
            enRevision.put(1L, 2L);
            enRevision.put(2L, 0L);
            when(usuarioRepository.findActivosByRolNombre("RESOURCE_MANAGER")).thenReturn(List.of(yo, otro));
            Proyecto p = proyectoCon(10L, null, EstadoProyecto.EN_REVISION);

            service.asignarAlCrear(p);

            assertSame(otro, p.getRmRevisor());
        }

        @Test
        void todosEnElTopeYNoUrgenteQuedaSinResponsable() {
            carga.put(1L, 5L);
            carga.put(2L, 6L);
            when(usuarioRepository.findActivosByRolNombre("RESOURCE_MANAGER")).thenReturn(List.of(yo, otro));
            Proyecto p = proyectoCon(10L, null, EstadoProyecto.EN_REVISION);

            service.asignarAlCrear(p);

            assertNull(p.getRmRevisor());
            verify(proyectoRepository, never()).save(any());
            verifyNoInteractions(notificacionService, auditoriaService);
        }

        @Test
        void todosEnElTopeYUrgenteSeAsignaIgnorandoElTope() {
            carga.put(1L, 6L);
            carga.put(2L, 5L);
            when(usuarioRepository.findActivosByRolNombre("RESOURCE_MANAGER")).thenReturn(List.of(yo, otro));
            Proyecto p = urgente(10L, null, EstadoProyecto.EN_REVISION);

            service.asignarAlCrear(p);

            assertSame(otro, p.getRmRevisor());
            verify(notificacionService).crear(eq(otro), eq("PROYECTO_ASIGNADO_RM"), any(),
                    eq("Proyecto urgente asignado"), contains("Urgente: Prioridad alta."), any(), any());
        }

        @Test
        void sinRmActivosQuedaSinResponsable() {
            when(usuarioRepository.findActivosByRolNombre("RESOURCE_MANAGER")).thenReturn(List.of());
            Proyecto p = urgente(10L, null, EstadoProyecto.EN_REVISION);

            service.asignarAlCrear(p);

            assertNull(p.getRmRevisor());
            verify(proyectoRepository, never()).save(any());
        }
    }

    @Nested
    class Tomar {

        @Test
        void proyectoInexistenteLanza() {
            when(proyectoRepository.findById(99L)).thenReturn(Optional.empty());
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.tomar(99L, yo));
            assertEquals("No se encontró el proyecto solicitado.", ex.getMessage());
        }

        @Test
        void proyectoNoGestionableLanza() {
            when(proyectoRepository.findById(10L))
                    .thenReturn(Optional.of(proyectoCon(10L, null, EstadoProyecto.FINALIZADO)));
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.tomar(10L, yo));
            assertEquals("Solo se pueden tomar proyectos en revisión, activos o en espera.", ex.getMessage());
            verify(proyectoRepository, never()).reclamar(anyLong(), any());
        }

        @Test
        void proyectoDeOtroRmLanza() {
            when(proyectoRepository.findById(10L))
                    .thenReturn(Optional.of(proyectoCon(10L, otro, EstadoProyecto.EN_REVISION)));
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.tomar(10L, yo));
            assertEquals("Este proyecto ya está a cargo de Usuario2 Prueba.", ex.getMessage());
            verify(proyectoRepository, never()).reclamar(anyLong(), any());
        }

        @Test
        void proyectoYaPropioNoHaceNada() {
            when(proyectoRepository.findById(10L))
                    .thenReturn(Optional.of(proyectoCon(10L, yo, EstadoProyecto.ACTIVO)));
            assertDoesNotThrow(() -> service.tomar(10L, yo));
            verify(proyectoRepository, never()).reclamar(anyLong(), any());
            verifyNoInteractions(notificacionService, auditoriaService);
        }

        @Test
        void enElTopeYNoUrgenteLanza() {
            carga.put(1L, 5L);
            when(proyectoRepository.findById(10L))
                    .thenReturn(Optional.of(proyectoCon(10L, null, EstadoProyecto.EN_REVISION)));
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.tomar(10L, yo));
            assertEquals("Alcanzaste tu límite de 5 proyectos. Finaliza o libera uno antes de tomar otro.",
                    ex.getMessage());
            verify(proyectoRepository, never()).reclamar(anyLong(), any());
        }

        @Test
        void enElTopePeroUrgenteSePuedeTomar() {
            carga.put(1L, 5L);
            Proyecto p = urgente(10L, null, EstadoProyecto.EN_REVISION);
            when(proyectoRepository.findById(10L)).thenReturn(Optional.of(p));
            when(proyectoRepository.reclamar(10L, yo)).thenReturn(1);

            service.tomar(10L, yo);

            assertSame(yo, p.getRmRevisor());
        }

        @Test
        void otroRmLoTomoAntesLanza() {
            when(proyectoRepository.findById(10L))
                    .thenReturn(Optional.of(proyectoCon(10L, null, EstadoProyecto.EN_REVISION)));
            when(proyectoRepository.reclamar(10L, yo)).thenReturn(0);
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.tomar(10L, yo));
            assertEquals("Otro Resource Manager tomó este proyecto hace un momento.", ex.getMessage());
            verifyNoInteractions(notificacionService, auditoriaService);
        }

        @Test
        void exitoAsignaNotificaAlPmYAudita() {
            Proyecto p = proyectoCon(10L, null, EstadoProyecto.EN_REVISION);
            when(proyectoRepository.findById(10L)).thenReturn(Optional.of(p));
            when(proyectoRepository.reclamar(10L, yo)).thenReturn(1);

            service.tomar(10L, yo);

            assertSame(yo, p.getRmRevisor());
            verify(notificacionService).crear(eq(elPm), eq("PROYECTO_RM_ASIGNADO"), eq(CategoriaNotificacion.PROYECTO),
                    eq("RM responsable asignado"), contains("Usuario1 Prueba"), eq("PROYECTO"), eq(10L));
            verify(auditoriaService).registrar(eq(yo), eq("TOMAR_PROYECTO"), eq("PROYECTO"), eq(10L),
                    anyString(), isNull(), eq("Usuario1 Prueba"), isNull());
        }
    }

    @Nested
    class Liberar {

        @Test
        void proyectoInexistenteLanza() {
            when(proyectoRepository.findById(99L)).thenReturn(Optional.empty());
            assertThrows(IllegalArgumentException.class, () -> service.liberar(99L, yo));
        }

        @Test
        void sinResponsableLanza() {
            when(proyectoRepository.findById(10L))
                    .thenReturn(Optional.of(proyectoCon(10L, null, EstadoProyecto.EN_REVISION)));
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.liberar(10L, yo));
            assertEquals("Solo el RM responsable puede liberar este proyecto.", ex.getMessage());
        }

        @Test
        void deOtroRmLanza() {
            when(proyectoRepository.findById(10L))
                    .thenReturn(Optional.of(proyectoCon(10L, otro, EstadoProyecto.EN_REVISION)));
            assertThrows(IllegalStateException.class, () -> service.liberar(10L, yo));
            verify(proyectoRepository, never()).save(any());
        }

        @Test
        void proyectoQueNoEstaEnRevisionLanza() {
            when(proyectoRepository.findById(10L))
                    .thenReturn(Optional.of(proyectoCon(10L, yo, EstadoProyecto.ACTIVO)));
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.liberar(10L, yo));
            assertEquals("Solo se puede liberar un proyecto que aún está en revisión.", ex.getMessage());
            verify(proyectoRepository, never()).save(any());
        }

        @Test
        void exitoQuitaElResponsableGuardaYAudita() {
            Proyecto p = proyectoCon(10L, yo, EstadoProyecto.EN_REVISION);
            when(proyectoRepository.findById(10L)).thenReturn(Optional.of(p));

            service.liberar(10L, yo);

            assertNull(p.getRmRevisor());
            verify(proyectoRepository).save(p);
            verify(auditoriaService).registrar(eq(yo), eq("LIBERAR_PROYECTO"), eq("PROYECTO"), eq(10L),
                    anyString(), eq("Usuario1 Prueba"), isNull(), isNull());
        }
    }

    @Nested
    class ValidarResponsable {

        @Test
        void proyectoPropioNoLanza() {
            assertDoesNotThrow(() -> service.validarResponsable(proyectoCon(10L, yo, EstadoProyecto.ACTIVO), yo));
            verify(proyectoRepository, never()).reclamar(anyLong(), any());
        }

        @Test
        void proyectoDeOtroRmLanza() {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.validarResponsable(proyectoCon(10L, otro, EstadoProyecto.ACTIVO), yo));
            assertEquals("Este proyecto está a cargo de Usuario2 Prueba. Solo el RM responsable puede gestionarlo.",
                    ex.getMessage());
        }

        @Test
        void sinResponsableYGestionableLoToma() {
            Proyecto p = proyectoCon(10L, null, EstadoProyecto.EN_REVISION);
            when(proyectoRepository.reclamar(10L, yo)).thenReturn(1);

            service.validarResponsable(p, yo);

            assertSame(yo, p.getRmRevisor());
        }

        @Test
        void sinResponsableYCerradoNoHaceNada() {
            Proyecto p = proyectoCon(10L, null, EstadoProyecto.FINALIZADO);
            assertDoesNotThrow(() -> service.validarResponsable(p, yo));
            assertNull(p.getRmRevisor());
            verify(proyectoRepository, never()).reclamar(anyLong(), any());
        }
    }

    @Nested
    class EscalarProyectosEnRevision {

        private Proyecto creadoHace(Long id, Usuario responsable, int dias) {
            Proyecto p = proyectoCon(id, responsable, EstadoProyecto.EN_REVISION);
            p.setFechaCreacion(LocalDateTime.now().minusDays(dias).minusMinutes(1));
            return p;
        }

        @Test
        void ignoraLosQueNoSonUrgentesNiVencidos() {
            Proyecto reciente = creadoHace(10L, null, 1);
            Proyecto sinFecha = proyectoCon(11L, yo, EstadoProyecto.EN_REVISION);
            when(proyectoRepository.findByEstado(EstadoProyecto.EN_REVISION)).thenReturn(List.of(reciente, sinFecha));

            service.escalarProyectosEnRevision();

            verifyNoInteractions(notificacionService, auditoriaService, usuarioRepository);
        }

        @Test
        void vencidoSinResponsableSeAsignaAunqueTodosEstenEnElTope() {
            carga.put(1L, 5L);
            when(usuarioRepository.findActivosByRolNombre("RESOURCE_MANAGER")).thenReturn(List.of(yo));
            Proyecto p = creadoHace(10L, null, 2);
            when(proyectoRepository.findByEstado(EstadoProyecto.EN_REVISION)).thenReturn(List.of(p));

            service.escalarProyectosEnRevision();

            assertSame(yo, p.getRmRevisor());
            verify(auditoriaService).registrar(isNull(), eq("ASIGNAR_RM_PROYECTO"), eq("PROYECTO"), eq(10L),
                    contains("Escalamiento por falta de revisión"), isNull(), eq("Usuario1 Prueba"), isNull());
        }

        @Test
        void vencidoSinRmActivosQuedaSinResponsable() {
            when(usuarioRepository.findActivosByRolNombre("RESOURCE_MANAGER")).thenReturn(List.of());
            Proyecto p = creadoHace(10L, null, 5);
            when(proyectoRepository.findByEstado(EstadoProyecto.EN_REVISION)).thenReturn(List.of(p));

            service.escalarProyectosEnRevision();

            assertNull(p.getRmRevisor());
            verifyNoInteractions(notificacionService, auditoriaService);
        }

        @Test
        void vencidoConResponsableLeRecuerdaConLosDias() {
            Proyecto p = creadoHace(10L, yo, 2);
            when(proyectoRepository.findByEstado(EstadoProyecto.EN_REVISION)).thenReturn(List.of(p));

            service.escalarProyectosEnRevision();

            verify(notificacionService).crear(eq(yo), eq("PROYECTO_REVISION_PENDIENTE"),
                    eq(CategoriaNotificacion.PROYECTO), eq("Proyecto pendiente de revisión"),
                    contains("Lleva más de 2 día(s) en revisión"), eq("PROYECTO"), eq(10L));
            verify(proyectoRepository, never()).save(any());
        }

        @Test
        void urgenteConResponsableLeRecuerdaConElMotivo() {
            Proyecto p = urgente(10L, yo, EstadoProyecto.EN_REVISION);
            when(proyectoRepository.findByEstado(EstadoProyecto.EN_REVISION)).thenReturn(List.of(p));

            service.escalarProyectosEnRevision();

            verify(notificacionService).crear(eq(yo), eq("PROYECTO_REVISION_PENDIENTE"), any(), any(),
                    contains("Prioridad alta."), any(), eq(10L));
        }

        @Test
        void respetaLosDiasSinRevisarConfigurados() {
            config("DIAS_MAX_SIN_REVISAR", "4");
            Proyecto p = creadoHace(10L, yo, 3);
            when(proyectoRepository.findByEstado(EstadoProyecto.EN_REVISION)).thenReturn(List.of(p));

            service.escalarProyectosEnRevision();

            verifyNoInteractions(notificacionService);
        }
    }
}
