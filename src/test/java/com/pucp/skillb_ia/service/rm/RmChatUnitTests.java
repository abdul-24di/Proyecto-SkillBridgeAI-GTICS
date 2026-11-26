package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.PmMensajeView;
import com.pucp.skillb_ia.model.Conversacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.service.ChatBaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.pm;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.proyecto;
import static com.pucp.skillb_ia.service.rm.RmDatosPrueba.rm;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Chat privado RM <-> PM: solo con proyectos donde el RM es responsable y que no estén rechazados ni cancelados.
@ExtendWith(MockitoExtension.class)
class RmChatUnitTests {

    @Mock
    private ProyectoRepository proyectoRepository;
    @Mock
    private ChatBaseService chatBaseService;

    @InjectMocks
    private RmChatService service;

    private Usuario yo;
    private Usuario elPm;

    @BeforeEach
    void preparar() {
        yo = rm(1L);
        elPm = pm(2L);
    }

    private Proyecto proyectoCon(Long id, Usuario responsable, EstadoProyecto estado) {
        Proyecto p = proyecto(id);
        p.setRmRevisor(responsable);
        p.setPm(elPm);
        p.setEstado(estado);
        return p;
    }

    @Nested
    class ListarProyectosDelRm {

        @Test
        void excluyeRechazadosYCancelados() {
            Proyecto activo = proyectoCon(10L, yo, EstadoProyecto.ACTIVO);
            Proyecto rechazado = proyectoCon(11L, yo, EstadoProyecto.RECHAZADO);
            Proyecto cancelado = proyectoCon(12L, yo, EstadoProyecto.CANCELADO);
            when(proyectoRepository.findByRmRevisorConPm(yo)).thenReturn(List.of(activo, rechazado, cancelado));

            assertEquals(List.of(activo), service.listarProyectosDelRm(yo));
        }
    }

    @Nested
    class ObtenerMensajes {

        @Test
        void sinProyectoLanza() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.obtenerMensajes(null, yo));
            assertEquals("Debes seleccionar un proyecto.", ex.getMessage());
            verifyNoInteractions(chatBaseService);
        }

        @Test
        void proyectoInexistenteLanza() {
            when(proyectoRepository.findByIdConPm(99L)).thenReturn(Optional.empty());
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.obtenerMensajes(99L, yo));
            assertEquals("Proyecto no encontrado.", ex.getMessage());
        }

        @Test
        void proyectoDeOtroRmLanza() {
            when(proyectoRepository.findByIdConPm(10L))
                    .thenReturn(Optional.of(proyectoCon(10L, rm(3L), EstadoProyecto.ACTIVO)));
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.obtenerMensajes(10L, yo));
            assertEquals("No tienes acceso al chat de este proyecto.", ex.getMessage());
            verifyNoInteractions(chatBaseService);
        }

        @Test
        void proyectoSinResponsableLanza() {
            when(proyectoRepository.findByIdConPm(10L))
                    .thenReturn(Optional.of(proyectoCon(10L, null, EstadoProyecto.ACTIVO)));
            assertThrows(IllegalArgumentException.class, () -> service.obtenerMensajes(10L, yo));
        }

        @Test
        void proyectoCanceladoLanza() {
            when(proyectoRepository.findByIdConPm(10L))
                    .thenReturn(Optional.of(proyectoCon(10L, yo, EstadoProyecto.CANCELADO)));
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.obtenerMensajes(10L, yo));
            assertEquals("No tienes acceso al chat de este proyecto.", ex.getMessage());
        }

        @Test
        void sinConversacionDevuelveListaVaciaYNoMarcaLeida() {
            Proyecto p = proyectoCon(10L, yo, EstadoProyecto.ACTIVO);
            when(proyectoRepository.findByIdConPm(10L)).thenReturn(Optional.of(p));
            when(chatBaseService.buscarPrivada(p, yo, elPm)).thenReturn(null);

            assertTrue(service.obtenerMensajes(10L, yo).isEmpty());
            verify(chatBaseService, never()).marcarLeida(any(), any());
        }

        @Test
        void conConversacionMarcaLeidaYLista() {
            Proyecto p = proyectoCon(10L, yo, EstadoProyecto.ACTIVO);
            Conversacion conversacion = new Conversacion();
            List<PmMensajeView> mensajes = List.of(mock(PmMensajeView.class));
            when(proyectoRepository.findByIdConPm(10L)).thenReturn(Optional.of(p));
            when(chatBaseService.buscarPrivada(p, yo, elPm)).thenReturn(conversacion);
            when(chatBaseService.listar(conversacion, 1L)).thenReturn(mensajes);

            assertSame(mensajes, service.obtenerMensajes(10L, yo));
            verify(chatBaseService).marcarLeida(conversacion, yo);
        }
    }

    @Nested
    class EnviarMensaje {

        @Test
        void textoInvalidoNoBuscaElProyecto() {
            doThrow(new IllegalArgumentException("vacío")).when(chatBaseService).validarTexto(" ");
            assertThrows(IllegalArgumentException.class, () -> service.enviarMensaje(10L, " ", yo));
            verifyNoInteractions(proyectoRepository);
        }

        @Test
        void proyectoDeOtroRmNoEnvia() {
            when(proyectoRepository.findByIdConPm(10L))
                    .thenReturn(Optional.of(proyectoCon(10L, rm(3L), EstadoProyecto.ACTIVO)));
            assertThrows(IllegalArgumentException.class, () -> service.enviarMensaje(10L, "Hola", yo));
            verify(chatBaseService, never()).enviarPrivado(any(), any(), any(), any());
        }

        @Test
        void enviaAlPmDelProyectoConElTextoRecortado() {
            Proyecto p = proyectoCon(10L, yo, EstadoProyecto.ACTIVO);
            PmMensajeView vista = mock(PmMensajeView.class);
            when(proyectoRepository.findByIdConPm(10L)).thenReturn(Optional.of(p));
            when(chatBaseService.enviarPrivado(p, yo, elPm, "Hola")).thenReturn(vista);

            assertSame(vista, service.enviarMensaje(10L, "  Hola  ", yo));
        }
    }

    @Nested
    class ObtenerNoLeidos {

        @Test
        void cuentaPorProyectoLosNoLeidosDelPmYCeroSiNoHay() {
            Proyecto conMensajes = proyectoCon(10L, yo, EstadoProyecto.ACTIVO);
            Proyecto sinMensajes = proyectoCon(11L, yo, EstadoProyecto.ACTIVO);
            Proyecto cancelado = proyectoCon(12L, yo, EstadoProyecto.CANCELADO);
            when(proyectoRepository.findByRmRevisorConPm(yo)).thenReturn(List.of(conMensajes, sinMensajes, cancelado));
            when(chatBaseService.contarNoLeidosPorChat(conMensajes, yo, List.of(elPm))).thenReturn(Map.of("2", 3L));
            when(chatBaseService.contarNoLeidosPorChat(sinMensajes, yo, List.of(elPm))).thenReturn(Map.of());

            Map<String, Long> resultado = service.obtenerNoLeidos(yo);

            assertEquals(Map.of("10", 3L, "11", 0L), resultado);
            verify(chatBaseService, never()).contarNoLeidosPorChat(cancelado, yo, List.of(elPm));
        }
    }
}
