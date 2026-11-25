package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.MotivoFinalizacion;
import com.pucp.skillb_ia.repository.ActividadRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.CierreAsignacionesService;
import com.pucp.skillb_ia.service.NotificacionService;
import com.pucp.skillb_ia.service.rm.RmPresupuestoService.ResumenPresupuesto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.pm;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.proyecto;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.rm;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// TASK-084: RmProyectoRevisionService con sus 8 dependencias simuladas.
@ExtendWith(MockitoExtension.class)
class RmProyectoRevisionUnitTests {

    @Mock private ProyectoRepository proyectoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AuditoriaService auditoriaService;
    @Mock private RmPresupuestoService presupuestoService;
    @Mock private NotificacionService notificacionService;
    @Mock private ActividadRepository actividadRepository;
    @Mock private CierreAsignacionesService cierreAsignacionesService;
    @Mock private RmResponsableService rmResponsableService;

    @InjectMocks
    private RmProyectoRevisionService service;

    private Usuario rm;
    private Usuario pm;
    private Proyecto proyecto;

    @BeforeEach
    void preparar() {
        rm = rm(1L);
        pm = pm(2L);
        proyecto = proyecto(10L);
        proyecto.setPm(pm);
    }

    private static BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }

    private void conRm() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(rm));
    }

    private void conProyecto(EstadoProyecto estado) {
        proyecto.setEstado(estado);
        when(proyectoRepository.findById(10L)).thenReturn(Optional.of(proyecto));
    }

    private static ResumenPresupuesto resumen(String total, String comprometido, String reservado) {
        return new ResumenPresupuesto(bd(total), bd(comprometido), bd(reservado), BigDecimal.ZERO, 0);
    }

    private void sinCambios() {
        verify(proyectoRepository, never()).save(any());
        verifyNoInteractions(notificacionService, auditoriaService);
    }

    @Nested
    class ObtenerRm {

        @Test
        void sinRmIdLanza() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.aprobar(10L, null));
            assertEquals("Debes iniciar sesión como Resource Manager.", ex.getMessage());
        }

        @Test
        void usuarioQueNoEsRmLanza() {
            when(usuarioRepository.findById(2L)).thenReturn(Optional.of(pm));
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.aprobar(10L, 2L));
            assertEquals("El usuario autenticado no es un Resource Manager activo.", ex.getMessage());
        }

        @Test
        void rmInactivoLanza() {
            rm.setActivo(false);
            conRm();
            assertThrows(IllegalArgumentException.class, () -> service.aprobar(10L, 1L));
            verifyNoInteractions(proyectoRepository);
        }
    }

    @Nested
    class AsignarPresupuesto {

        @Test
        void actualizaPresupuestoYAudita() {
            conRm();
            conProyecto(EstadoProyecto.ACTIVO);
            when(presupuestoService.calcularResumen(proyecto)).thenReturn(resumen("100000", "30000", "10000"));

            EstadoProyecto estado = service.asignarPresupuesto(10L, bd("40000"), 1L);

            assertEquals(EstadoProyecto.ACTIVO, estado);
            assertEquals(bd("40000.00"), proyecto.getPresupuesto());
            verify(rmResponsableService).validarResponsable(proyecto, rm);
            verify(proyectoRepository).save(proyecto);
            verify(auditoriaService).registrar(eq(rm), eq("ACTUALIZAR_PRESUPUESTO_PROYECTO"), eq("PROYECTO"),
                    eq(10L), anyString(), eq("100000"), eq("40000.00"), any());
        }

        @Test
        void sinPresupuestoPrevioAuditaComoAsignacion() {
            proyecto.setPresupuesto(null);
            conRm();
            conProyecto(EstadoProyecto.EN_REVISION);
            when(presupuestoService.calcularResumen(proyecto)).thenReturn(resumen("0", "0", "0"));

            service.asignarPresupuesto(10L, bd("5000.5"), 1L);

            verify(auditoriaService).registrar(eq(rm), eq("ASIGNAR_PRESUPUESTO_PROYECTO"), eq("PROYECTO"),
                    eq(10L), anyString(), eq(null), eq("5000.50"), any());
        }

        @Test
        void menorQueComprometidoYReservadoLanza() {
            conRm();
            conProyecto(EstadoProyecto.ACTIVO);
            when(presupuestoService.calcularResumen(proyecto)).thenReturn(resumen("100000", "30000", "10000"));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.asignarPresupuesto(10L, bd("39999.99"), 1L));
            assertEquals("El presupuesto no puede ser menor que el monto ya comprometido o reservado (S/ 40000.00).",
                    ex.getMessage());
            assertEquals(bd("100000"), proyecto.getPresupuesto());
            sinCambios();
        }

        @Test
        void presupuestoCeroONuloLanza() {
            conRm();
            conProyecto(EstadoProyecto.ACTIVO);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.asignarPresupuesto(10L, BigDecimal.ZERO, 1L));
            assertEquals("El presupuesto debe ser mayor que cero.", ex.getMessage());
            assertThrows(IllegalArgumentException.class, () -> service.asignarPresupuesto(10L, null, 1L));
            sinCambios();
        }

        @Test
        void masDeDosDecimalesLanza() {
            conRm();
            conProyecto(EstadoProyecto.ACTIVO);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.asignarPresupuesto(10L, bd("1.000"), 1L));
            assertEquals("El presupuesto admite como máximo 2 decimales.", ex.getMessage());
        }

        @Test
        void superaMaximoLanza() {
            conRm();
            conProyecto(EstadoProyecto.ACTIVO);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.asignarPresupuesto(10L, bd("10000000000"), 1L));
            assertEquals("El presupuesto supera el monto máximo permitido.", ex.getMessage());
        }

        @Test
        void proyectoCerradoLanza() {
            conRm();
            conProyecto(EstadoProyecto.FINALIZADO);
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.asignarPresupuesto(10L, bd("1000"), 1L));
            assertEquals("El presupuesto no puede modificarse en el estado actual del proyecto.", ex.getMessage());
            sinCambios();
        }

        @Test
        void proyectoInexistenteLanza() {
            conRm();
            when(proyectoRepository.findById(10L)).thenReturn(Optional.empty());
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.asignarPresupuesto(10L, bd("1000"), 1L));
            assertEquals("No se encontró el proyecto solicitado.", ex.getMessage());
        }
    }

    @Nested
    class Aprobar {

        @Test
        void apruebaNotificaYAudita() {
            proyecto.setMotivoRechazo("antes");
            conRm();
            conProyecto(EstadoProyecto.EN_REVISION);

            service.aprobar(10L, 1L);

            assertEquals(EstadoProyecto.ACTIVO, proyecto.getEstado());
            assertSame(rm, proyecto.getRmRevisor());
            assertNull(proyecto.getMotivoRechazo());
            verify(proyectoRepository).save(proyecto);
            verify(notificacionService).crear(eq(pm), eq("PROYECTO_APROBADO"), any(), anyString(), anyString(),
                    eq("PROYECTO"), eq(10L));
            verify(auditoriaService).registrar(eq(rm), eq("APROBAR_PROYECTO"), eq("PROYECTO"), eq(10L),
                    anyString(), eq("EN_REVISION"), eq("ACTIVO"), any());
        }

        @Test
        void noEnRevisionLanza() {
            conRm();
            conProyecto(EstadoProyecto.ACTIVO);
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.aprobar(10L, 1L));
            assertEquals("El proyecto ya no se encuentra en revisión.", ex.getMessage());
            sinCambios();
        }

        @Test
        void otroRmResponsableLanza() {
            conRm();
            conProyecto(EstadoProyecto.EN_REVISION);
            doThrow(new IllegalStateException("Otro RM")).when(rmResponsableService).validarResponsable(proyecto, rm);
            assertThrows(IllegalStateException.class, () -> service.aprobar(10L, 1L));
            sinCambios();
        }

        @Test
        void sinPresupuestoLanza() {
            proyecto.setPresupuesto(null);
            conRm();
            conProyecto(EstadoProyecto.EN_REVISION);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.aprobar(10L, 1L));
            assertEquals("Asigna un presupuesto válido antes de aprobar el proyecto.", ex.getMessage());
            sinCambios();
        }

        @Test
        void sinFechasLanza() {
            proyecto.setFechaFinEstimada(null);
            conRm();
            conProyecto(EstadoProyecto.EN_REVISION);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.aprobar(10L, 1L));
            assertEquals("Define la fecha de inicio y la fecha de fin estimada antes de aprobar el proyecto.",
                    ex.getMessage());
        }

        @Test
        void finNoPosteriorAlInicioLanza() {
            proyecto.setFechaFinEstimada(proyecto.getFechaInicio());
            conRm();
            conProyecto(EstadoProyecto.EN_REVISION);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.aprobar(10L, 1L));
            assertEquals("La fecha de fin debe ser posterior a la fecha de inicio.", ex.getMessage());
            assertEquals(EstadoProyecto.EN_REVISION, proyecto.getEstado());
        }
    }

    @Nested
    class Rechazar {

        @Test
        void rechazaConMotivoNotificaYAudita() {
            conRm();
            conProyecto(EstadoProyecto.EN_REVISION);

            service.rechazar(10L, "  Falta alcance  ", 1L);

            assertEquals(EstadoProyecto.RECHAZADO, proyecto.getEstado());
            assertEquals("Falta alcance", proyecto.getMotivoRechazo());
            assertSame(rm, proyecto.getRmRevisor());
            verify(proyectoRepository).save(proyecto);
            verify(notificacionService).crear(eq(pm), eq("PROYECTO_RECHAZADO"), any(), anyString(),
                    eq("El Resource Manager rechazó tu proyecto \"Proyecto10\". Motivo: Falta alcance"),
                    eq("PROYECTO"), eq(10L));
            verify(auditoriaService).registrar(eq(rm), eq("RECHAZAR_PROYECTO"), eq("PROYECTO"), eq(10L),
                    eq("Proyecto rechazado. Motivo: Falta alcance"), eq("EN_REVISION"), eq("RECHAZADO"), any());
        }

        @Test
        void motivoVacioLanza() {
            conRm();
            conProyecto(EstadoProyecto.EN_REVISION);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.rechazar(10L, "   ", 1L));
            assertEquals("Debes registrar el motivo del rechazo.", ex.getMessage());
            assertThrows(IllegalArgumentException.class, () -> service.rechazar(10L, null, 1L));
            sinCambios();
        }

        @Test
        void motivoLargoLanza() {
            conRm();
            conProyecto(EstadoProyecto.EN_REVISION);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.rechazar(10L, "x".repeat(501), 1L));
            assertEquals("El motivo del rechazo no puede superar los 500 caracteres.", ex.getMessage());
            sinCambios();
        }

        @Test
        void motivoDe500SeAceptaYAuditoriaSeRecorta() {
            conRm();
            conProyecto(EstadoProyecto.EN_REVISION);
            service.rechazar(10L, "x".repeat(500), 1L);
            String esperado = ("Proyecto rechazado. Motivo: " + "x".repeat(500)).substring(0, 500);
            verify(auditoriaService).registrar(eq(rm), eq("RECHAZAR_PROYECTO"), eq("PROYECTO"), eq(10L),
                    eq(esperado), eq("EN_REVISION"), eq("RECHAZADO"), any());
        }

        @Test
        void noEnRevisionLanza() {
            conRm();
            conProyecto(EstadoProyecto.RECHAZADO);
            assertThrows(IllegalStateException.class, () -> service.rechazar(10L, "motivo", 1L));
            sinCambios();
        }
    }

    @Nested
    class CambiarFechas {

        private final LocalDate hoy = LocalDate.now();
        private final LocalDate inicio = hoy.plusDays(1);
        private final LocalDate fin = hoy.plusDays(60);

        private Actividad actividad(String titulo, LocalDate limite) {
            Actividad actividad = new Actividad();
            actividad.setTitulo(titulo);
            actividad.setFechaLimite(limite);
            return actividad;
        }

        @Test
        void cambiaFechasAuditaYNotifica() {
            conRm();
            conProyecto(EstadoProyecto.ACTIVO);
            when(actividadRepository.findByProyectoAndActivoTrueOrderByFechaLimiteAsc(proyecto))
                    .thenReturn(List.of(actividad("A", hoy.plusDays(30)), actividad("Sin fecha", null)));
            when(presupuestoService.calcularResumenConFechas(proyecto, inicio, fin))
                    .thenReturn(resumen("100000", "50000", "50000"));

            service.cambiarFechas(10L, inicio, fin, 1L);

            assertEquals(inicio, proyecto.getFechaInicio());
            assertEquals(fin, proyecto.getFechaFinEstimada());
            verify(proyectoRepository).save(proyecto);
            verify(auditoriaService).registrar(eq(rm), eq("ACTUALIZAR_FECHAS_PROYECTO"), eq("PROYECTO"), eq(10L),
                    anyString(), eq("Inicio: 05/01/2026 | Fin: 16/03/2026"), anyString(), any());
            verify(notificacionService).crear(eq(pm), eq("PROYECTO_FECHAS_ACTUALIZADAS"), any(), anyString(),
                    anyString(), eq("PROYECTO"), eq(10L));
        }

        @Test
        void conservaInicioPasadoDelProyecto() {
            LocalDate inicioPasado = proyecto.getFechaInicio();
            conRm();
            conProyecto(EstadoProyecto.ACTIVO);
            when(actividadRepository.findByProyectoAndActivoTrueOrderByFechaLimiteAsc(proyecto)).thenReturn(List.of());
            when(presupuestoService.calcularResumenConFechas(proyecto, inicioPasado, fin))
                    .thenReturn(resumen("100000", "0", "0"));

            service.cambiarFechas(10L, inicioPasado, fin, 1L);

            assertEquals(fin, proyecto.getFechaFinEstimada());
            verify(proyectoRepository).save(proyecto);
        }

        @Test
        void estadoNoEditableLanza() {
            conRm();
            conProyecto(EstadoProyecto.CANCELADO);
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.cambiarFechas(10L, inicio, fin, 1L));
            assertEquals("Las fechas no pueden modificarse en el estado actual del proyecto.", ex.getMessage());
            sinCambios();
        }

        @Test
        void fechaNulaLanza() {
            conRm();
            conProyecto(EstadoProyecto.ACTIVO);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.cambiarFechas(10L, null, fin, 1L));
            assertEquals("Debes indicar la fecha de inicio y la fecha de fin del proyecto.", ex.getMessage());
        }

        @Test
        void inicioNuevoEnElPasadoLanza() {
            conRm();
            conProyecto(EstadoProyecto.EN_REVISION);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.cambiarFechas(10L, hoy.minusDays(1), fin, 1L));
            assertEquals("La fecha de inicio no puede ser anterior a la fecha actual.", ex.getMessage());
            sinCambios();
        }

        @Test
        void finNoPosteriorLanza() {
            conRm();
            conProyecto(EstadoProyecto.EN_ESPERA);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.cambiarFechas(10L, inicio, inicio, 1L));
            assertEquals("La fecha de fin debe ser posterior a la fecha de inicio.", ex.getMessage());
        }

        @Test
        void actividadesFueraDeRangoLanza() {
            conRm();
            conProyecto(EstadoProyecto.ACTIVO);
            LocalDate limite = fin.plusDays(5);
            when(actividadRepository.findByProyectoAndActivoTrueOrderByFechaLimiteAsc(proyecto))
                    .thenReturn(List.of(actividad("Entrega final", limite), actividad("Antes", hoy)));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.cambiarFechas(10L, inicio, fin, 1L));
            assertTrue(ex.getMessage().startsWith("No se cambiaron las fechas: 2 actividad(es) vencen fuera del nuevo rango"));
            assertTrue(ex.getMessage().contains("\"Entrega final\""));
            verifyNoInteractions(presupuestoService);
            sinCambios();
        }

        @Test
        void presupuestoInsuficienteLanza() {
            conRm();
            conProyecto(EstadoProyecto.ACTIVO);
            when(actividadRepository.findByProyectoAndActivoTrueOrderByFechaLimiteAsc(proyecto)).thenReturn(List.of());
            when(presupuestoService.calcularResumenConFechas(proyecto, inicio, fin))
                    .thenReturn(resumen("100000", "80000", "20000.01"));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.cambiarFechas(10L, inicio, fin, 1L));
            assertEquals("No se cambiaron las fechas: con el nuevo rango, el costo comprometido y reservado "
                    + "(S/ 100000.01) supera el presupuesto del proyecto (S/ 100000.00).", ex.getMessage());
            sinCambios();
        }
    }

    @Nested
    class EsProyectoCerrable {

        @Test
        void soloActivoOEnEspera() {
            for (EstadoProyecto estado : EstadoProyecto.values()) {
                proyecto.setEstado(estado);
                boolean esperado = estado == EstadoProyecto.ACTIVO || estado == EstadoProyecto.EN_ESPERA;
                assertEquals(esperado, RmProyectoRevisionService.esProyectoCerrable(proyecto), estado.name());
            }
        }
    }

    @Nested
    class ContarActividadesAbiertas {

        @Test
        void sumaPendientesEnProgresoYEnRevision() {
            when(actividadRepository.countByProyectoAndEstadoAndActivoTrue(proyecto, EstadoActividad.PENDIENTE)).thenReturn(1L);
            when(actividadRepository.countByProyectoAndEstadoAndActivoTrue(proyecto, EstadoActividad.EN_PROGRESO)).thenReturn(2L);
            when(actividadRepository.countByProyectoAndEstadoAndActivoTrue(proyecto, EstadoActividad.EN_REVISION)).thenReturn(3L);

            assertEquals(6L, service.contarActividadesAbiertas(proyecto));
            verify(actividadRepository, never()).countByProyectoAndEstadoAndActivoTrue(proyecto, EstadoActividad.COMPLETADA);
        }
    }

    @Nested
    class Cancelar {

        @Test
        void cancelaCierraAsignacionesNotificaYAudita() {
            conRm();
            conProyecto(EstadoProyecto.ACTIVO);
            CierreAsignacionesService.Resultado resultado = new CierreAsignacionesService.Resultado(2, 3);
            when(cierreAsignacionesService.cerrarAbiertas(proyecto, rm, "cancelado", MotivoFinalizacion.PROYECTO_CANCELADO))
                    .thenReturn(resultado);

            assertSame(resultado, service.cancelar(10L, " Sin fondos ", " Proyecto10 ", 1L));

            assertEquals(EstadoProyecto.CANCELADO, proyecto.getEstado());
            assertEquals("Sin fondos", proyecto.getMotivoCierre());
            verify(proyectoRepository).save(proyecto);
            verify(notificacionService).crear(eq(pm), eq("PROYECTO_CANCELADO"), any(), eq("Proyecto cancelado"),
                    anyString(), eq("PROYECTO"), eq(10L));
            verify(auditoriaService).registrar(eq(rm), eq("CANCELAR_PROYECTO"), eq("PROYECTO"), eq(10L),
                    eq("Proyecto cancelado por el RM. Asignaciones pendientes rechazadas: 2. "
                            + "Asignaciones activas finalizadas: 3. Motivo: Sin fondos"),
                    eq("ACTIVO"), eq("CANCELADO"), any());
            verifyNoInteractions(actividadRepository);
        }

        @Test
        void noCerrableLanza() {
            conRm();
            conProyecto(EstadoProyecto.EN_REVISION);
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.cancelar(10L, "m", "Proyecto10", 1L));
            assertEquals("Solo se pueden cancelar proyectos activos o en espera.", ex.getMessage());
            sinCambios();
            verifyNoInteractions(cierreAsignacionesService);
        }

        @Test
        void sinMotivoLanza() {
            conRm();
            conProyecto(EstadoProyecto.ACTIVO);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.cancelar(10L, " ", "Proyecto10", 1L));
            assertEquals("Debes indicar el motivo para cancelar el proyecto.", ex.getMessage());
        }

        @Test
        void motivoLargoLanza() {
            conRm();
            conProyecto(EstadoProyecto.ACTIVO);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.cancelar(10L, "x".repeat(501), "Proyecto10", 1L));
            assertEquals("El motivo no puede superar los 500 caracteres.", ex.getMessage());
        }

        @Test
        void nombreDeConfirmacionDistintoLanza() {
            conRm();
            conProyecto(EstadoProyecto.EN_ESPERA);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.cancelar(10L, "motivo", "proyecto10", 1L));
            assertEquals("Para confirmar, escribe el nombre exacto del proyecto. No se realizó ningún cambio.",
                    ex.getMessage());
            assertThrows(IllegalArgumentException.class, () -> service.cancelar(10L, "motivo", null, 1L));
            assertEquals(EstadoProyecto.EN_ESPERA, proyecto.getEstado());
            sinCambios();
            verifyNoInteractions(cierreAsignacionesService);
        }

        @Test
        void notificacionLargaSeRecortaA400() {
            conRm();
            conProyecto(EstadoProyecto.ACTIVO);
            when(cierreAsignacionesService.cerrarAbiertas(any(), any(), anyString(), any()))
                    .thenReturn(new CierreAsignacionesService.Resultado(0, 0));

            service.cancelar(10L, "x".repeat(500), "Proyecto10", 1L);

            verify(notificacionService).crear(eq(pm), anyString(), any(), anyString(),
                    org.mockito.ArgumentMatchers.argThat(m -> m.length() == 400 && m.endsWith("...")),
                    anyString(), eq(10L));
        }
    }

    @Nested
    class Finalizar {

        @Test
        void finalizaSinActividadesAbiertas() {
            conRm();
            conProyecto(EstadoProyecto.EN_ESPERA);
            when(cierreAsignacionesService.cerrarAbiertas(proyecto, rm, "finalizado", MotivoFinalizacion.PROYECTO_FINALIZADO))
                    .thenReturn(new CierreAsignacionesService.Resultado(0, 1));

            service.finalizar(10L, "Entregado", "Proyecto10", 1L);

            assertEquals(EstadoProyecto.FINALIZADO, proyecto.getEstado());
            verify(notificacionService).crear(eq(pm), eq("PROYECTO_FINALIZADO"), any(), eq("Proyecto finalizado"),
                    anyString(), eq("PROYECTO"), eq(10L));
            verify(auditoriaService).registrar(eq(rm), eq("FINALIZAR_PROYECTO"), eq("PROYECTO"), eq(10L),
                    anyString(), eq("EN_ESPERA"), eq("FINALIZADO"), any());
        }

        @Test
        void conActividadesAbiertasLanza() {
            conRm();
            conProyecto(EstadoProyecto.ACTIVO);
            when(actividadRepository.countByProyectoAndEstadoAndActivoTrue(proyecto, EstadoActividad.PENDIENTE)).thenReturn(0L);
            when(actividadRepository.countByProyectoAndEstadoAndActivoTrue(proyecto, EstadoActividad.EN_PROGRESO)).thenReturn(1L);
            when(actividadRepository.countByProyectoAndEstadoAndActivoTrue(proyecto, EstadoActividad.EN_REVISION)).thenReturn(1L);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.finalizar(10L, "m", "Proyecto10", 1L));
            assertEquals("No se puede finalizar el proyecto: 2 actividad(es) siguen pendientes, en progreso o en revisión.",
                    ex.getMessage());
            assertFalse(proyecto.getEstado() == EstadoProyecto.FINALIZADO);
            sinCambios();
            verifyNoInteractions(cierreAsignacionesService);
        }

        @Test
        void noCerrableLanzaConVerboFinalizar() {
            conRm();
            conProyecto(EstadoProyecto.FINALIZADO);
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.finalizar(10L, "m", "Proyecto10", 1L));
            assertEquals("Solo se pueden finalizar proyectos activos o en espera.", ex.getMessage());
        }
    }
}
