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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PmChatService {

    private final ProyectoRepository proyectoRepository;
    private final AsignacionRepository asignacionRepository;
    private final ChatBaseService chatBaseService;

    public PmChatService(ProyectoRepository proyectoRepository,
                         AsignacionRepository asignacionRepository,
                         ChatBaseService chatBaseService) {
        this.proyectoRepository = proyectoRepository;
        this.asignacionRepository = asignacionRepository;
        this.chatBaseService = chatBaseService;
    }

    public List<Proyecto> listarProyectosDelPm(Usuario pm) {
        return proyectoRepository.findByPmOrderByFechaCreacionDesc(pm);
    }

    private Proyecto proyectoDelPm(Long proyectoId, Usuario pm) {
        Proyecto proyecto = proyectoRepository.findByIdConPm(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));

        if (!proyecto.getPm().getId().equals(pm.getId())) {
            throw new IllegalArgumentException("No eres el PM de este proyecto.");
        }
        return proyecto;
    }

    // El colaborador debe tener asignación activa en el proyecto
    private Usuario validarColaborador(Proyecto proyecto, Long colaboradorId) {
        if (colaboradorId == null) {
            throw new IllegalArgumentException("Debes seleccionar con quién chatear.");
        }
        List<Asignacion> activas = asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA);
        for (Asignacion a : activas) {
            if (a.getColaborador().getId().equals(colaboradorId)) {
                return a.getColaborador();
            }
        }
        throw new IllegalArgumentException("Ese colaborador no está activo en este proyecto.");
    }

    // ---------------- Contactos ----------------

    @Transactional(readOnly = true)
    public List<ChatContactoView> listarContactos(Long proyectoId, Usuario pm) {
        Proyecto proyecto = proyectoDelPm(proyectoId, pm);

        List<ChatContactoView> contactos = new ArrayList<>();
        List<Asignacion> activas = asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA);
        for (Asignacion a : activas) {
            contactos.add(new ChatContactoView(a.getColaborador(), "Colaborador"));
        }
        return contactos;
    }

    // ---------------- Chat grupal----------------
    @Transactional
    public List<PmMensajeView> obtenerMensajes(Long proyectoId, Usuario pm) {
        Proyecto proyecto = proyectoDelPm(proyectoId, pm);
        Conversacion conversacion = chatBaseService.obtenerOCrearGrupal(proyecto);
        chatBaseService.marcarLeida(conversacion, pm);
        return chatBaseService.listar(conversacion, pm.getId());
    }

    @Transactional
    public PmMensajeView enviarMensaje(Long proyectoId, String contenido, Usuario pm) {
        if (proyectoId == null) {
            throw new IllegalArgumentException("Debes seleccionar un proyecto para enviar un mensaje.");
        }
        chatBaseService.validarTexto(contenido);
        Proyecto proyecto = proyectoDelPm(proyectoId, pm);

        return chatBaseService.enviarGrupal(proyecto, pm, contenido.trim());
    }

    // ---------------- Chat privado con un colaborador ----------------
    @Transactional
    public List<PmMensajeView> obtenerMensajesPrivados(Long proyectoId, Long colaboradorId, Usuario pm) {
        Proyecto proyecto = proyectoDelPm(proyectoId, pm);
        Usuario colaborador = validarColaborador(proyecto, colaboradorId);

        Conversacion conversacion = chatBaseService.buscarPrivada(proyecto, pm, colaborador);
        if (conversacion == null) {
            return new ArrayList<>();
        }
        chatBaseService.marcarLeida(conversacion, pm);
        return chatBaseService.listar(conversacion, pm.getId());
    }

    @Transactional
    public PmMensajeView enviarMensajePrivado(Long proyectoId, Long colaboradorId, String contenido, Usuario pm) {
        if (proyectoId == null) {
            throw new IllegalArgumentException("Debes seleccionar un proyecto para enviar un mensaje.");
        }
        chatBaseService.validarTexto(contenido);
        Proyecto proyecto = proyectoDelPm(proyectoId, pm);
        Usuario colaborador = validarColaborador(proyecto, colaboradorId);

        return chatBaseService.enviarPrivado(proyecto, pm, colaborador, contenido.trim());
    }


    // ---------------- Mensajes no leídos ----------------

    @Transactional(readOnly = true)
    public Map<String, Long> obtenerNoLeidos(Long proyectoId, Usuario pm) {
        Proyecto proyecto = proyectoDelPm(proyectoId, pm);

        List<Usuario> otros = new ArrayList<>();
        List<Asignacion> activas = asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA);
        for (Asignacion a : activas) {
            otros.add(a.getColaborador());
        }
        return chatBaseService.contarNoLeidosPorChat(proyecto, pm, otros);
    }




}