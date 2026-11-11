package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.dto.ChatContactoView;
import com.pucp.skillb_ia.dto.PmMensajeView;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Conversacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.ChatBaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ColChatService {

    private final AsignacionRepository asignacionRepository;
    private final ProyectoRepository proyectoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ChatBaseService chatBaseService;

    public ColChatService(AsignacionRepository asignacionRepository,
                          ProyectoRepository proyectoRepository,
                          UsuarioRepository usuarioRepository,
                          ChatBaseService chatBaseService) {
        this.asignacionRepository = asignacionRepository;
        this.proyectoRepository = proyectoRepository;
        this.usuarioRepository = usuarioRepository;
        this.chatBaseService = chatBaseService;
    }

    public List<Proyecto> listarProyectosActivos(Usuario colaborador) {
        List<Proyecto> proyectos = new ArrayList<>();
        List<Asignacion> asignaciones = asignacionRepository.findByColaboradorAndEstado(colaborador, EstadoAsignacion.ACTIVA);

        for (Asignacion a : asignaciones) {
            proyectos.add(a.getProyecto());
        }

        return proyectos;
    }

    // ---------------- Validaciones ----------------

    private Proyecto buscarProyecto(Long proyectoId) {
        if (proyectoId == null) {
            throw new IllegalArgumentException("Debes seleccionar un proyecto.");
        }
        return proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));
    }

    private void validarAcceso(Proyecto proyecto, Usuario colaborador) {
        List<Asignacion> asignaciones = asignacionRepository.findByColaboradorAndEstado(colaborador, EstadoAsignacion.ACTIVA);

        for (Asignacion a : asignaciones) {
            if (a.getProyecto().getId().equals(proyecto.getId())) {
                return;
            }
        }

        throw new IllegalArgumentException("No eres un colaborador activo de este proyecto.");
    }

    // El otro usuario debe ser el PM del proyecto o un colaborador con asignación activa
    private Usuario validarDestinatario(Proyecto proyecto, Long otroUsuarioId, Usuario colaborador) {
        if (otroUsuarioId == null) {
            throw new IllegalArgumentException("Debes seleccionar con quién chatear.");
        }
        if (otroUsuarioId.equals(colaborador.getId())) {
            throw new IllegalArgumentException("No puedes escribirte a ti mismo.");
        }

        Usuario otro = usuarioRepository.findById(otroUsuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        if (proyecto.getPm().getId().equals(otroUsuarioId)) {
            return otro;
        }

        List<Asignacion> activas = asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA);
        for (Asignacion a : activas) {
            if (a.getColaborador().getId().equals(otroUsuarioId)) {
                return otro;
            }
        }

        throw new IllegalArgumentException("Esa persona no es parte de este proyecto.");
    }

    // ---------------- Contactos de PM y colaboradores ----------------

    @Transactional(readOnly = true)
    public List<ChatContactoView> listarContactos(Long proyectoId, Usuario colaborador) {
        Proyecto proyecto = buscarProyecto(proyectoId);
        validarAcceso(proyecto, colaborador);

        List<ChatContactoView> contactos = new ArrayList<>();
        contactos.add(new ChatContactoView(proyecto.getPm(), "Project Manager"));

        List<Asignacion> activas = asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA);
        for (Asignacion a : activas) {
            Usuario otro = a.getColaborador();
            if (!otro.getId().equals(colaborador.getId())) {
                contactos.add(new ChatContactoView(otro, "Colaborador"));
            }
        }
        return contactos;
    }

    // ---------------- Chat grupal ----------------

    @Transactional
    public List<PmMensajeView> obtenerMensajes(Long proyectoId, Usuario colaborador) {
        Proyecto proyecto = buscarProyecto(proyectoId);
        validarAcceso(proyecto, colaborador);

        Conversacion conversacion = chatBaseService.obtenerOCrearGrupal(proyecto);
        return chatBaseService.listar(conversacion, colaborador.getId());
    }

    @Transactional
    public PmMensajeView enviarMensaje(Long proyectoId, String contenido, Usuario colaborador) {
        chatBaseService.validarTexto(contenido);
        Proyecto proyecto = buscarProyecto(proyectoId);
        validarAcceso(proyecto, colaborador);

        return chatBaseService.enviarGrupal(proyecto, colaborador, contenido.trim());
    }

    // ---------------- Chat privado ----------------

    @Transactional(readOnly = true)
    public List<PmMensajeView> obtenerMensajesPrivados(Long proyectoId, Long otroUsuarioId, Usuario colaborador) {
        Proyecto proyecto = buscarProyecto(proyectoId);
        validarAcceso(proyecto, colaborador);
        Usuario otro = validarDestinatario(proyecto, otroUsuarioId, colaborador);

        Conversacion conversacion = chatBaseService.buscarPrivada(proyecto, colaborador, otro);
        if (conversacion == null) {
            return new ArrayList<>();
        }
        return chatBaseService.listar(conversacion, colaborador.getId());
    }

    @Transactional
    public PmMensajeView enviarMensajePrivado(Long proyectoId, Long otroUsuarioId, String contenido, Usuario colaborador) {
        chatBaseService.validarTexto(contenido);
        Proyecto proyecto = buscarProyecto(proyectoId);
        validarAcceso(proyecto, colaborador);
        Usuario otro = validarDestinatario(proyecto, otroUsuarioId, colaborador);

        return chatBaseService.enviarPrivado(proyecto, colaborador, otro, contenido.trim());
    }
}