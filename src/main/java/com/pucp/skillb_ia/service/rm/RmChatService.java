package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.PmMensajeView;
import com.pucp.skillb_ia.model.Conversacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.service.ChatBaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Service
public class RmChatService {


    private static final Set<EstadoProyecto> SIN_CHAT = EnumSet.of(EstadoProyecto.RECHAZADO, EstadoProyecto.CANCELADO);

    private final ProyectoRepository proyectoRepository;
    private final ChatBaseService chatBaseService;

    public RmChatService(ProyectoRepository proyectoRepository, ChatBaseService chatBaseService) {
        this.proyectoRepository = proyectoRepository;
        this.chatBaseService = chatBaseService;
    }

    @Transactional(readOnly = true)
    public List<Proyecto> listarProyectosDelRm(Usuario rm) {
        List<Proyecto> resultado = new ArrayList<>();
        for (Proyecto p : proyectoRepository.findByRmRevisorConPm(rm)) {
            if (!SIN_CHAT.contains(p.getEstado())) {
                resultado.add(p);
            }
        }
        return resultado;
    }

    private Proyecto proyectoDelRm(Long proyectoId, Usuario rm) {
        if (proyectoId == null) {
            throw new IllegalArgumentException("Debes seleccionar un proyecto.");
        }
        Proyecto proyecto = proyectoRepository.findByIdConPm(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));

        boolean esSuRm = proyecto.getRmRevisor() != null && proyecto.getRmRevisor().getId().equals(rm.getId());
        if (!esSuRm || SIN_CHAT.contains(proyecto.getEstado())) {
            throw new IllegalArgumentException("No tienes acceso al chat de este proyecto.");
        }
        return proyecto;
    }

    @Transactional
    public List<PmMensajeView> obtenerMensajes(Long proyectoId, Usuario rm) {
        Proyecto proyecto = proyectoDelRm(proyectoId, rm);
        Conversacion conversacion = chatBaseService.buscarPrivada(proyecto, rm, proyecto.getPm());
        if (conversacion == null) {
            return new ArrayList<>();
        }
        chatBaseService.marcarLeida(conversacion, rm);
        return chatBaseService.listar(conversacion, rm.getId());
    }

    @Transactional
    public PmMensajeView enviarMensaje(Long proyectoId, String contenido, Usuario rm) {
        chatBaseService.validarTexto(contenido);
        Proyecto proyecto = proyectoDelRm(proyectoId, rm);
        return chatBaseService.enviarPrivado(proyecto, rm, proyecto.getPm(), contenido.trim());
    }


    @Transactional(readOnly = true)
    public Map<String, Long> obtenerNoLeidos(Usuario rm) {
        Map<String, Long> resultado = new HashMap<>();
        for (Proyecto p : listarProyectosDelRm(rm)) {
            Map<String, Long> conteos = chatBaseService.contarNoLeidosPorChat(p, rm, List.of(p.getPm()));
            resultado.put(String.valueOf(p.getId()), conteos.getOrDefault(String.valueOf(p.getPm().getId()), 0L));
        }
        return resultado;
    }
}