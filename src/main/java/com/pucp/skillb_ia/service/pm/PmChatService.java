package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.dto.PmMensajeView;
import com.pucp.skillb_ia.model.Conversacion;
import com.pucp.skillb_ia.model.Mensaje;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.TipoConversacion;
import com.pucp.skillb_ia.repository.ConversacionRepository;
import com.pucp.skillb_ia.repository.MensajeRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PmChatService {

    private final ProyectoRepository proyectoRepository;
    private final ConversacionRepository conversacionRepository;
    private final MensajeRepository mensajeRepository;

    public PmChatService(ProyectoRepository proyectoRepository,
                         ConversacionRepository conversacionRepository,
                         MensajeRepository mensajeRepository) {
        this.proyectoRepository = proyectoRepository;
        this.conversacionRepository = conversacionRepository;
        this.mensajeRepository = mensajeRepository;
    }

    public List<Proyecto> listarProyectosDelPm(Usuario pm) {
        return proyectoRepository.findByPmOrderByFechaCreacionDesc(pm);
    }

    @Transactional
    public List<PmMensajeView> obtenerMensajes(Long proyectoId, Usuario pm) {
        Proyecto proyecto = proyectoRepository.findByIdConPm(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));

        if (!proyecto.getPm().getId().equals(pm.getId())) {
            throw new IllegalArgumentException("No eres el PM de este proyecto.");
        }

        Conversacion conversacion = conversacionRepository.findByProyecto(proyecto)
                .orElseGet(() -> crearConversacion(proyecto));

        return mensajeRepository.findByConversacionOrderByFechaHoraAsc(conversacion)
                .stream()
                .map(m -> new PmMensajeView(m, pm.getId()))
                .toList();
    }

    @Transactional
    public PmMensajeView enviarMensaje(Long proyectoId, String contenido, Usuario pm) {
        if (contenido == null || contenido.trim().isEmpty()) {
            throw new IllegalArgumentException("El mensaje no puede estar vacío.");
        }

        Proyecto proyecto = proyectoRepository.findByIdConPm(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));

        if (!proyecto.getPm().getId().equals(pm.getId())) {
            throw new IllegalArgumentException("No eres el PM de este proyecto.");
        }

        Conversacion conversacion = conversacionRepository.findByProyecto(proyecto)
                .orElseGet(() -> crearConversacion(proyecto));

        Mensaje mensaje = new Mensaje();
        mensaje.setConversacion(conversacion);
        mensaje.setAutor(pm);
        mensaje.setContenido(contenido);

        Mensaje guardado = mensajeRepository.save(mensaje);
        return new PmMensajeView(guardado, pm.getId());
    }

    private Conversacion crearConversacion(Proyecto proyecto) {
        Conversacion conv = new Conversacion();
        conv.setProyecto(proyecto);
        conv.setTipo(TipoConversacion.GRUPAL);
        return conversacionRepository.save(conv);
    }
}
