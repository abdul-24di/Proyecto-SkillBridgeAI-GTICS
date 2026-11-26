package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.service.rm.RmPresupuestoService.CostoAsignacion;
import com.pucp.skillb_ia.service.rm.RmPresupuestoService.ImpactoPresupuesto;
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

import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.asignacion;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.colaborador;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.proyecto;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Prueba unitaria piloto del RM (TASK-076): RmPresupuestoService con el repositorio simulado.
// Datos base: sueldo S/ 3200 -> S/ 20.00 la hora; proyecto de 70 días -> 10.00 semanas; S/ 100 000 de presupuesto.
@ExtendWith(MockitoExtension.class)
class RmPresupuestoUnitTests {

    @Mock
    private AsignacionRepository asignacionRepository;

    @InjectMocks
    private RmPresupuestoService service;

    private Proyecto proyecto;
    private Usuario colaborador;

    @BeforeEach
    void preparar() {
        proyecto = proyecto(1L);
        colaborador = colaborador(10L, "3200");
    }

    private static BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }

    // Simula las asignaciones del proyecto: activas y pendientes.
    private void conAsignaciones(List<Asignacion> activas, List<Asignacion> pendientes) {
        when(asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA)).thenReturn(activas);
        when(asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.PENDIENTE)).thenReturn(pendientes);
    }

    private Asignacion pendienteAprobadaPorRm(Long id, String horas) {
        Asignacion a = asignacion(id, proyecto, colaborador, horas, EstadoAsignacion.PENDIENTE);
        a.setAprobadoPorRm(true);
        return a;
    }

    @Nested
    class CalcularCosto {

        @Test
        void calculaValorHoraDuracionYCostos() {
            CostoAsignacion costo = service.calcularCosto(proyecto, colaborador, bd("10"));

            assertTrue(costo.calculable());
            assertEquals(bd("20.00"), costo.valorHora());
            assertEquals(bd("10.00"), costo.duracionSemanas());
            assertEquals(bd("200.00"), costo.costoSemanal());
            assertEquals(bd("100.00"), costo.horasTotales());
            assertEquals(bd("2000.00"), costo.costoTotal());
            verifyNoInteractions(asignacionRepository);
        }

        @Test
        void redondeaElValorHoraADosDecimales() {
            CostoAsignacion costo = service.calcularCosto(proyecto, colaborador(11L, "1000"), bd("1"));

            assertEquals(bd("6.25"), costo.valorHora());
        }

        @Test
        void fechasInvertidasDanDuracionCero() {
            Proyecto invertido = proyecto(2L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 1), "1000");

            CostoAsignacion costo = service.calcularCosto(invertido, colaborador, bd("10"));

            assertTrue(costo.calculable());
            assertEquals(0, costo.duracionSemanas().signum());
            assertEquals(0, costo.costoTotal().signum());
        }

        @Test
        void sinSueldoNoEsCalculable() {
            assertFalse(service.calcularCosto(proyecto, colaborador(11L, null), bd("10")).calculable());
            assertFalse(service.calcularCosto(proyecto, colaborador(12L, "0"), bd("10")).calculable());
        }

        @Test
        void sinHorasValidasNoEsCalculable() {
            assertFalse(service.calcularCosto(proyecto, colaborador, null).calculable());
            assertFalse(service.calcularCosto(proyecto, colaborador, bd("0")).calculable());
        }

        @Test
        void sinFechasNiProyectoNoEsCalculable() {
            assertFalse(service.calcularCosto(proyecto(3L, null, LocalDate.now(), "1000"), colaborador, bd("10")).calculable());
            assertFalse(service.calcularCosto(proyecto(4L, LocalDate.now(), null, "1000"), colaborador, bd("10")).calculable());
            assertFalse(service.calcularCosto(null, colaborador, bd("10")).calculable());
            assertFalse(service.calcularCosto(proyecto, null, bd("10")).calculable());
        }
    }

    @Nested
    class CalcularResumen {

        @Test
        void sumaActivasComoComprometidoYPendientesAprobadasComoReservado() {
            Asignacion activa = asignacion(100L, proyecto, colaborador, "10", EstadoAsignacion.ACTIVA);
            Asignacion pendienteAprobada = pendienteAprobadaPorRm(101L, "20");
            Asignacion pendienteSinAprobar = asignacion(102L, proyecto, colaborador, "40", EstadoAsignacion.PENDIENTE);
            conAsignaciones(List.of(activa), List.of(pendienteAprobada, pendienteSinAprobar));

            ResumenPresupuesto resumen = service.calcularResumen(proyecto);

            assertEquals(bd("100000"), resumen.total());
            assertEquals(bd("2000.00"), resumen.comprometido());
            assertEquals(bd("4000.00"), resumen.reservado(), "La pendiente sin aprobar del RM no reserva presupuesto");
            assertEquals(bd("94000.00"), resumen.disponible());
            assertEquals(6, resumen.porcentajeConsumido());
        }

        @Test
        void sinAsignacionesTodoEstaDisponible() {
            conAsignaciones(List.of(), List.of());

            ResumenPresupuesto resumen = service.calcularResumen(proyecto);

            assertEquals(0, resumen.comprometido().signum());
            assertEquals(0, resumen.reservado().signum());
            assertEquals(bd("100000"), resumen.disponible());
            assertEquals(0, resumen.porcentajeConsumido());
        }

        @Test
        void elDisponibleNoBajaDeCeroYElPorcentajeNoPasaDeCien() {
            proyecto.setPresupuesto(bd("1000"));
            conAsignaciones(List.of(asignacion(100L, proyecto, colaborador, "10", EstadoAsignacion.ACTIVA)), List.of());

            ResumenPresupuesto resumen = service.calcularResumen(proyecto);

            assertEquals(0, resumen.disponible().signum());
            assertEquals(100, resumen.porcentajeConsumido());
        }

        @Test
        void sinPresupuestoElTotalEsCeroYElPorcentajeTambien() {
            proyecto.setPresupuesto(null);
            conAsignaciones(List.of(asignacion(100L, proyecto, colaborador, "10", EstadoAsignacion.ACTIVA)), List.of());

            ResumenPresupuesto resumen = service.calcularResumen(proyecto);

            assertEquals(0, resumen.total().signum());
            assertEquals(0, resumen.porcentajeConsumido());
        }

        @Test
        void conOtrasFechasRecalculaSinModificarElProyecto() {
            conAsignaciones(List.of(asignacion(100L, proyecto, colaborador, "10", EstadoAsignacion.ACTIVA)), List.of());
            LocalDate inicio = proyecto.getFechaInicio();
            LocalDate finOriginal = proyecto.getFechaFinEstimada();

            // 140 días = 20 semanas: el costo de la activa se duplica.
            ResumenPresupuesto resumen = service.calcularResumenConFechas(proyecto, inicio, inicio.plusDays(140));

            assertEquals(bd("4000.00"), resumen.comprometido());
            assertEquals(finOriginal, proyecto.getFechaFinEstimada(), "No debe tocar la entidad");
        }
    }

    @Nested
    class CalcularImpacto {

        @Test
        void restaElCostoAlDisponible() {
            ResumenPresupuesto resumen = new ResumenPresupuesto(bd("10000"), bd("0"), bd("0"), bd("10000"), 0);

            ImpactoPresupuesto impacto = service.calcularImpacto(proyecto, colaborador, bd("10"), resumen);

            assertTrue(impacto.suficiente());
            assertEquals(bd("8000.00"), impacto.disponibleDespues());
            assertEquals(0, impacto.deficit().signum());
            verify(asignacionRepository, never()).findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA);
        }

        @Test
        void siNoAlcanzaInformaElDeficitYElDisponibleQuedaEnCero() {
            ResumenPresupuesto resumen = new ResumenPresupuesto(bd("1500"), bd("0"), bd("0"), bd("1500"), 0);

            ImpactoPresupuesto impacto = service.calcularImpacto(proyecto, colaborador, bd("10"), resumen);

            assertFalse(impacto.suficiente());
            assertEquals(bd("500.00"), impacto.deficit());
            assertEquals(0, impacto.disponibleDespues().signum());
        }

        @Test
        void unCostoNoCalculableNuncaEsSuficiente() {
            ResumenPresupuesto resumen = new ResumenPresupuesto(bd("10000"), bd("0"), bd("0"), bd("10000"), 0);

            ImpactoPresupuesto impacto = service.calcularImpacto(proyecto, colaborador(11L, null), bd("10"), resumen);

            assertFalse(impacto.suficiente());
        }

        @Test
        void sinResumenLoCalculaDesdeLasAsignaciones() {
            conAsignaciones(List.of(), List.of());

            ImpactoPresupuesto impacto = service.calcularImpacto(proyecto, colaborador, bd("10"));

            assertEquals(bd("98000.00"), impacto.disponibleDespues());
        }
    }

    @Nested
    class ValidarPresupuestoSuficiente {

        @Test
        void conPresupuestoSuficienteNoLanza() {
            conAsignaciones(List.of(), List.of());

            assertDoesNotThrow(() -> service.validarPresupuestoSuficiente(proyecto, colaborador, bd("10")));
        }

        @Test
        void sinPresupuestoAprobadoLanza() {
            proyecto.setPresupuesto(null);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.validarPresupuestoSuficiente(proyecto, colaborador, bd("10")));
            assertEquals("El proyecto no tiene presupuesto aprobado todavía.", ex.getMessage());
            verifyNoInteractions(asignacionRepository);
        }

        @Test
        void sinSueldoBaseLanza() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.validarPresupuestoSuficiente(proyecto, colaborador(11L, null), bd("10")));
            assertEquals("El colaborador no tiene un sueldo base válido registrado.", ex.getMessage());
        }

        @Test
        void sinFechasLanza() {
            proyecto.setFechaFinEstimada(null);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.validarPresupuestoSuficiente(proyecto, colaborador, bd("10")));
            assertEquals("El proyecto no tiene fechas de inicio y fin definidas.", ex.getMessage());
        }

        @Test
        void sinHorasLanza() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.validarPresupuestoSuficiente(proyecto, colaborador, bd("0")));
            assertEquals("Las horas semanales deben ser mayores a cero.", ex.getMessage());
        }

        @Test
        void conPresupuestoInsuficienteLanzaConElDeficit() {
            proyecto.setPresupuesto(bd("1500"));
            conAsignaciones(List.of(), List.of());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.validarPresupuestoSuficiente(proyecto, colaborador, bd("10")));
            assertEquals("Presupuesto insuficiente: faltan S/ 500.00 para esta asignación.", ex.getMessage());
        }
    }
}
