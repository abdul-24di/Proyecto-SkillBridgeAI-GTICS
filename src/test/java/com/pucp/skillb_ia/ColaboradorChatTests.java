package com.pucp.skillb_ia;

import com.pucp.skillb_ia.dto.ChatContactoView;
import com.pucp.skillb_ia.dto.PmMensajeView;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Notificacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.NotificacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.col.ColChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@SpringBootTest
@ActiveProfiles("test")
class ColaboradorChatTests {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private NotificacionRepository notificacionRepository;
    @Autowired private ColChatService chatService;

    @Test
    void soloSeListanLosProyectosDondeLaAsignacionEstaActiva() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario col = usuario("col", "COLABORADOR");
        Proyecto activo = proyecto(pm);
        Proyecto finalizado = proyecto(pm);
        Proyecto pendiente = proyecto(pm);
        asignar(col, activo, EstadoAsignacion.ACTIVA);
        asignar(col, finalizado, EstadoAsignacion.FINALIZADA);
        asignar(col, pendiente, EstadoAsignacion.PENDIENTE);

        List<Proyecto> proyectos = chatService.listarProyectosActivos(col);

        assertThat(proyectos).extracting(Proyecto::getId).containsExactly(activo.getId());
    }

    @Test
    void losContactosSonElPmYLosCompanerosActivosSinIncluirseAUnoMismo() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario yo = usuario("yo", "COLABORADOR");
        Usuario companero = usuario("comp", "COLABORADOR");
        Usuario pendiente = usuario("pend", "COLABORADOR");
        Proyecto proyecto = proyecto(pm);
        asignar(yo, proyecto, EstadoAsignacion.ACTIVA);
        asignar(companero, proyecto, EstadoAsignacion.ACTIVA);
        asignar(pendiente, proyecto, EstadoAsignacion.PENDIENTE);

        List<ChatContactoView> contactos = chatService.listarContactos(proyecto.getId(), yo);

        assertThat(contactos).extracting(ChatContactoView::getId).containsExactly(pm.getId(), companero.getId());
        assertThat(contactos).extracting(ChatContactoView::getRol).containsExactly("Project Manager", "Colaborador");
    }

    @Test
    void unMensajeGrupalLoVenTodosYNotificaALosDemasMiembros() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario yo = usuario("yo", "COLABORADOR");
        Usuario companero = usuario("comp", "COLABORADOR");
        Proyecto proyecto = proyecto(pm);
        asignar(yo, proyecto, EstadoAsignacion.ACTIVA);
        asignar(companero, proyecto, EstadoAsignacion.ACTIVA);

        PmMensajeView enviado = chatService.enviarMensaje(proyecto.getId(), "   Hola equipo   ", yo);

        assertThat(enviado.getContenido()).isEqualTo("Hola equipo");
        assertThat(enviado.isEsPropio()).isTrue();

        List<PmMensajeView> vistosPorMi = chatService.obtenerMensajes(proyecto.getId(), yo);
        List<PmMensajeView> vistosPorElCompanero = chatService.obtenerMensajes(proyecto.getId(), companero);
        assertThat(vistosPorMi).hasSize(1);
        assertThat(vistosPorMi.get(0).isEsPropio()).isTrue();
        assertThat(vistosPorElCompanero).hasSize(1);
        assertThat(vistosPorElCompanero.get(0).isEsPropio()).isFalse();
        assertThat(vistosPorElCompanero.get(0).getContenido()).isEqualTo("Hola equipo");

        assertThat(notificacionesDeTipo(pm, "MENSAJE_GRUPAL")).hasSize(1);
        assertThat(notificacionesDeTipo(companero, "MENSAJE_GRUPAL")).hasSize(1);
        assertThat(notificacionesDeTipo(yo, "MENSAJE_GRUPAL")).isEmpty();
    }

    @Test
    void elMensajeDebeTenerTextoYNoPasarDe1000Caracteres() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario yo = usuario("yo", "COLABORADOR");
        Proyecto proyecto = proyecto(pm);
        asignar(yo, proyecto, EstadoAsignacion.ACTIVA);

        assertThatThrownBy(() -> chatService.enviarMensaje(proyecto.getId(), "   ", yo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El mensaje no puede estar vacío.");
        assertThatThrownBy(() -> chatService.enviarMensaje(proyecto.getId(), null, yo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El mensaje no puede estar vacío.");
        assertThatThrownBy(() -> chatService.enviarMensaje(proyecto.getId(), "x".repeat(1001), yo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El mensaje no puede superar los 1000 caracteres.");

        // El límite exacto sí se acepta.
        assertThat(chatService.enviarMensaje(proyecto.getId(), "x".repeat(1000), yo).getContenido()).hasSize(1000);
    }

    @Test
    void sinSerMiembroActivoDelProyectoNoSeAccedeAlChat() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario miembro = usuario("miembro", "COLABORADOR");
        Usuario ajeno = usuario("ajeno", "COLABORADOR");
        Usuario finalizado = usuario("fin", "COLABORADOR");
        Proyecto proyecto = proyecto(pm);
        asignar(miembro, proyecto, EstadoAsignacion.ACTIVA);
        asignar(finalizado, proyecto, EstadoAsignacion.FINALIZADA);

        for (Usuario sinAcceso : List.of(ajeno, finalizado)) {
            assertThatThrownBy(() -> chatService.obtenerMensajes(proyecto.getId(), sinAcceso))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("No eres un colaborador activo de este proyecto.");
            assertThatThrownBy(() -> chatService.enviarMensaje(proyecto.getId(), "Hola", sinAcceso))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("No eres un colaborador activo de este proyecto.");
            assertThatThrownBy(() -> chatService.listarContactos(proyecto.getId(), sinAcceso))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("No eres un colaborador activo de este proyecto.");
            assertThatThrownBy(() -> chatService.obtenerNoLeidos(proyecto.getId(), sinAcceso))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("No eres un colaborador activo de este proyecto.");
        }

        assertThatThrownBy(() -> chatService.obtenerMensajes(null, miembro))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Debes seleccionar un proyecto.");
        assertThatThrownBy(() -> chatService.obtenerMensajes(999_999_999L, miembro))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Proyecto no encontrado.");
    }

    @Test
    void elChatPrivadoSoloLoVenLosDosInterlocutoresYNotificaAlDestinatario() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario yo = usuario("yo", "COLABORADOR");
        Usuario companero = usuario("comp", "COLABORADOR");
        Usuario tercero = usuario("ter", "COLABORADOR");
        Proyecto proyecto = proyecto(pm);
        asignar(yo, proyecto, EstadoAsignacion.ACTIVA);
        asignar(companero, proyecto, EstadoAsignacion.ACTIVA);
        asignar(tercero, proyecto, EstadoAsignacion.ACTIVA);

        // Todavía no hay conversación: la lista viene vacía.
        assertThat(chatService.obtenerMensajesPrivados(proyecto.getId(), companero.getId(), yo)).isEmpty();

        chatService.enviarMensajePrivado(proyecto.getId(), companero.getId(), "  ¿Revisaste el avance?  ", yo);
        chatService.enviarMensajePrivado(proyecto.getId(), yo.getId(), "Sí, ya lo revisé.", companero);

        List<PmMensajeView> mios = chatService.obtenerMensajesPrivados(proyecto.getId(), companero.getId(), yo);
        assertThat(mios).extracting(PmMensajeView::getContenido)
                .containsExactlyInAnyOrder("¿Revisaste el avance?", "Sí, ya lo revisé.");
        assertThat(mios).filteredOn(PmMensajeView::isEsPropio)
                .extracting(PmMensajeView::getContenido).containsExactly("¿Revisaste el avance?");

        // Un tercero no ve esa conversación.
        assertThat(chatService.obtenerMensajesPrivados(proyecto.getId(), yo.getId(), tercero)).isEmpty();

        assertThat(notificacionesDeTipo(companero, "MENSAJE_PRIVADO")).hasSize(1);
        assertThat(notificacionesDeTipo(yo, "MENSAJE_PRIVADO")).hasSize(1);
        assertThat(notificacionesDeTipo(tercero, "MENSAJE_PRIVADO")).isEmpty();
    }

    @Test
    void elChatPrivadoValidaAlDestinatario() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario yo = usuario("yo", "COLABORADOR");
        Usuario fueraDelProyecto = usuario("fuera", "COLABORADOR");
        Proyecto proyecto = proyecto(pm);
        asignar(yo, proyecto, EstadoAsignacion.ACTIVA);

        assertThatThrownBy(() -> chatService.enviarMensajePrivado(proyecto.getId(), null, "Hola", yo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Debes seleccionar con quién chatear.");
        assertThatThrownBy(() -> chatService.enviarMensajePrivado(proyecto.getId(), yo.getId(), "Hola", yo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No puedes escribirte a ti mismo.");
        assertThatThrownBy(() -> chatService.enviarMensajePrivado(proyecto.getId(), 999_999_999L, "Hola", yo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuario no encontrado.");
        assertThatThrownBy(() -> chatService.enviarMensajePrivado(proyecto.getId(), fueraDelProyecto.getId(), "Hola", yo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Esa persona no es parte de este proyecto.");

        // Con el PM del proyecto sí se puede conversar en privado.
        assertThat(chatService.enviarMensajePrivado(proyecto.getId(), pm.getId(), "Consulta para el PM", yo).getContenido())
                .isEqualTo("Consulta para el PM");
    }

    @Test
    void losNoLeidosSeContabilizanPorChatYSeLimpianAlAbrirCadaConversacion() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario yo = usuario("yo", "COLABORADOR");
        Usuario companero = usuario("comp", "COLABORADOR");
        Proyecto proyecto = proyecto(pm);
        asignar(yo, proyecto, EstadoAsignacion.ACTIVA);
        asignar(companero, proyecto, EstadoAsignacion.ACTIVA);

        // Sin conversaciones todo está en cero.
        Map<String, Long> inicial = chatService.obtenerNoLeidos(proyecto.getId(), yo);
        assertThat(inicial).containsEntry("grupo", 0L)
                .containsEntry(String.valueOf(pm.getId()), 0L)
                .containsEntry(String.valueOf(companero.getId()), 0L);

        chatService.enviarMensaje(proyecto.getId(), "Mensaje al grupo", companero);
        chatService.enviarMensajePrivado(proyecto.getId(), yo.getId(), "Mensaje privado 1", companero);
        chatService.enviarMensajePrivado(proyecto.getId(), yo.getId(), "Mensaje privado 2", companero);
        // Lo que escribo yo no cuenta como no leído para mí.
        chatService.enviarMensaje(proyecto.getId(), "Respuesta mía", yo);

        Map<String, Long> conPendientes = chatService.obtenerNoLeidos(proyecto.getId(), yo);
        assertThat(conPendientes).containsEntry("grupo", 1L)
                .containsEntry(String.valueOf(companero.getId()), 2L)
                .containsEntry(String.valueOf(pm.getId()), 0L);

        chatService.obtenerMensajes(proyecto.getId(), yo);
        Map<String, Long> trasAbrirElGrupo = chatService.obtenerNoLeidos(proyecto.getId(), yo);
        assertThat(trasAbrirElGrupo).containsEntry("grupo", 0L)
                .containsEntry(String.valueOf(companero.getId()), 2L);

        chatService.obtenerMensajesPrivados(proyecto.getId(), companero.getId(), yo);
        assertThat(chatService.obtenerNoLeidos(proyecto.getId(), yo))
                .containsEntry("grupo", 0L)
                .containsEntry(String.valueOf(companero.getId()), 0L);
    }

    // ---------- Utilidades ----------

    private List<Notificacion> notificacionesDeTipo(Usuario usuario, String tipo) {
        return notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(usuario).stream()
                .filter(n -> tipo.equals(n.getTipo()))
                .toList();
    }

    private Usuario usuario(String prefijo, String nombreRol) {
        Usuario u = new Usuario();
        u.setCorreo(prefijo + "." + UUID.randomUUID() + "@test.local");
        u.setNombre(prefijo);
        u.setApellido("Prueba");
        u.setRol(rol(nombreRol));
        u.setActivo(true);
        u.setHorasContratadasSemana(new BigDecimal("40.00"));
        u.setHorasDisponibles(new BigDecimal("40.00"));
        return usuarioRepository.save(u);
    }

    private Rol rol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol r = new Rol();
            r.setNombre(nombre);
            return rolRepository.save(r);
        });
    }

    private Proyecto proyecto(Usuario pm) {
        Proyecto p = new Proyecto();
        p.setNombre("Proyecto chat " + System.nanoTime());
        p.setDescripcion("Proyecto de prueba.");
        p.setEstado(EstadoProyecto.ACTIVO);
        p.setPrioridad(Prioridad.MEDIA);
        p.setJustificacionPrioridad("Prueba.");
        p.setColaboradoresRequeridos(4);
        p.setPm(pm);
        p.setPresupuesto(new BigDecimal("10000.00"));
        p.setFechaInicio(LocalDate.now().minusMonths(1));
        p.setFechaFinEstimada(LocalDate.now().plusMonths(3));
        return proyectoRepository.save(p);
    }

    private void asignar(Usuario colaborador, Proyecto proyecto, EstadoAsignacion estado) {
        Asignacion a = new Asignacion();
        a.setProyecto(proyecto);
        a.setColaborador(colaborador);
        a.setHorasSemanales(new BigDecimal("8"));
        a.setOrigen(OrigenAsignacion.PROPUESTA_PM);
        a.setEstado(estado);
        a.setAprobadoPorPm(true);
        a.setAprobadoPorRm(true);
        asignacionRepository.save(a);
    }
}