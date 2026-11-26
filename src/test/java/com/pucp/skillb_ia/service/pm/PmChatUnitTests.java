package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.dto.ChatContactoView;
import com.pucp.skillb_ia.dto.PmMensajeView;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Conversacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.service.ChatBaseService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pruebas unitarias de {@link PmChatService} con JUnit 5 y Mockito. */
@ExtendWith(MockitoExtension.class)
class PmChatUnitTests {

    @Mock
    private ProyectoRepository proyectoRepository;
    @Mock
    private AsignacionRepository asignacionRepository;
    @Mock
    private ChatBaseService chatBaseService;

    @InjectMocks
    private PmChatService servicio;

    @Test
    void listarContactosIncluyeRmYColaboradoresActivos() {
        Usuario pm = PmDatosPrueba.pm(1L);
        Usuario rm = PmDatosPrueba.rm(2L);
        Usuario col = PmDatosPrueba.colaborador(3L);
        Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm);
        proyecto.setRmRevisor(rm);
        Asignacion a = PmDatosPrueba.asignacion(100L, proyecto, col, "20", EstadoAsignacion.ACTIVA);

        when(proyectoRepository.findByIdConPm(10L)).thenReturn(Optional.of(proyecto));
        when(asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA)).thenReturn(List.of(a));

        List<ChatContactoView> contactos = servicio.listarContactos(10L, pm);

        assertEquals(2, contactos.size());
        assertEquals("Resource Manager", contactos.get(0).getRol());
        assertEquals("Colaborador", contactos.get(1).getRol());
    }

    @Test
    void rechazaAccesoSiProyectoNoPerteneceAlPm() {
        Usuario pm = PmDatosPrueba.pm(1L);
        Usuario otroPm = PmDatosPrueba.pm(99L);
        Proyecto ajeno = PmDatosPrueba.proyecto(10L, otroPm);
        when(proyectoRepository.findByIdConPm(10L)).thenReturn(Optional.of(ajeno));

        assertThrows(IllegalArgumentException.class, () -> servicio.listarContactos(10L, pm));
    }

    @Nested
    class ChatGrupal {

        @Test
        void obtenerMensajesCreaGrupalMarcaLeidaYLista() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm);
            Conversacion conv = new Conversacion();
            PmMensajeView msg = mock(PmMensajeView.class);

            when(proyectoRepository.findByIdConPm(10L)).thenReturn(Optional.of(proyecto));
            when(chatBaseService.obtenerOCrearGrupal(proyecto)).thenReturn(conv);
            when(chatBaseService.listar(conv, 1L)).thenReturn(List.of(msg));

            List<PmMensajeView> resultado = servicio.obtenerMensajes(10L, pm);

            assertEquals(1, resultado.size());
            verify(chatBaseService).marcarLeida(conv, pm);
        }

        @Test
        void enviarMensajeRecortaEspaciosYValidaProyectoNoNulo() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm);
            PmMensajeView esperado = mock(PmMensajeView.class);

            assertThrows(IllegalArgumentException.class,
                    () -> servicio.enviarMensaje(null, "Hola", pm));

            when(proyectoRepository.findByIdConPm(10L)).thenReturn(Optional.of(proyecto));
            when(chatBaseService.enviarGrupal(proyecto, pm, "Hola equipo")).thenReturn(esperado);

            PmMensajeView resultado = servicio.enviarMensaje(10L, "  Hola equipo  ", pm);

            assertSame(esperado, resultado);
            verify(chatBaseService).validarTexto("  Hola equipo  ");
        }
    }

    @Nested
    class ChatPrivado {

        @Test
        void permiteChatearConRmRevisorDelProyecto() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Usuario rm = PmDatosPrueba.rm(2L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm);
            proyecto.setRmRevisor(rm);
            PmMensajeView esperado = mock(PmMensajeView.class);

            when(proyectoRepository.findByIdConPm(10L)).thenReturn(Optional.of(proyecto));
            when(chatBaseService.enviarPrivado(proyecto, pm, rm, "Hola RM")).thenReturn(esperado);

            PmMensajeView resultado = servicio.enviarMensajePrivado(10L, 2L, "  Hola RM ", pm);

            assertSame(esperado, resultado);
            verify(asignacionRepository, never()).findByProyectoAndEstado(any(), any());
        }

        @Test
        void devuelveVacioCuandoNoExisteConversacionPrivada() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Usuario col = PmDatosPrueba.colaborador(3L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm);
            Asignacion a = PmDatosPrueba.asignacion(100L, proyecto, col, "20", EstadoAsignacion.ACTIVA);

            when(proyectoRepository.findByIdConPm(10L)).thenReturn(Optional.of(proyecto));
            when(asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA)).thenReturn(List.of(a));
            when(chatBaseService.buscarPrivada(proyecto, pm, col)).thenReturn(null);

            List<PmMensajeView> mensajes = servicio.obtenerMensajesPrivados(10L, 3L, pm);

            assertTrue(mensajes.isEmpty());
            verify(chatBaseService, never()).marcarLeida(any(), any());
        }

        @Test
        void rechazaColaboradorSinAsignacionActiva() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm);

            when(proyectoRepository.findByIdConPm(10L)).thenReturn(Optional.of(proyecto));
            when(asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA)).thenReturn(List.of());

            assertThrows(IllegalArgumentException.class,
                    () -> servicio.obtenerMensajesPrivados(10L, 999L, pm));
        }
    }

    @Test
    void obtenerNoLeidosIncluyeRmYColaboradoresActivos() {
        Usuario pm = PmDatosPrueba.pm(1L);
        Usuario rm = PmDatosPrueba.rm(2L);
        Usuario col = PmDatosPrueba.colaborador(3L);
        Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm);
        proyecto.setRmRevisor(rm);
        Asignacion a = PmDatosPrueba.asignacion(100L, proyecto, col, "20", EstadoAsignacion.ACTIVA);

        when(proyectoRepository.findByIdConPm(10L)).thenReturn(Optional.of(proyecto));
        when(asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA)).thenReturn(List.of(a));
        when(chatBaseService.contarNoLeidosPorChat(eq(proyecto), eq(pm), eq(List.of(rm, col))))
                .thenReturn(Map.of("grupal", 2L, "2", 1L));

        Map<String, Long> resultado = servicio.obtenerNoLeidos(10L, pm);

        assertEquals(2L, resultado.get("grupal"));
        assertEquals(1L, resultado.get("2"));
    }
}
