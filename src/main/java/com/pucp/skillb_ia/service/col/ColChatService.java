package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.dto.PmMensajeView;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Conversacion;
import com.pucp.skillb_ia.model.Mensaje;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ConversacionRepository;
import com.pucp.skillb_ia.repository.MensajeRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ColChatService {

    private final AsignacionRepository asignacionRepository;
    private final ProyectoRepository proyectoRepository;
    private final ConversacionRepository conversacionRepository;
    private final MensajeRepository mensajeRepository;

    public ColChatService(AsignacionRepository asignacionRepository,
                          ProyectoRepository proyectoRepository,
                          ConversacionRepository conversacionRepository,
                          MensajeRepository mensajeRepository) {
        this.asignacionRepository = asignacionRepository;
        this.proyectoRepository = proyectoRepository;
        this.conversacionRepository = conversacionRepository;
        this.mensajeRepository = mensajeRepository;
    }

    public List<Proyecto> listarProyectosActivos(Usuario colaborador) {
        return asignacionRepository.findByColaboradorAndEstado(colaborador, EstadoAsignacion.ACTIVA)
                .stream()
                .map(Asignacion::getProyecto)
                .toList();
    }

    private void validarAcceso(Proyecto proyecto, Usuario colaborador) {
        boolean activo = asignacionRepository.findByColaboradorAndEstado(colaborador, EstadoAsignacion.ACTIVA)
                .stream()
                .anyMatch(a -> a.getProyecto().getId().equals(proyecto.getId()));
        if (!activo) {
            throw new IllegalArgumentException("No eres un colaborador activo de este proyecto.");
        }
    }

    @Transactional(readOnly = true)
    public List<PmMensajeView> obtenerMensajes(Long proyectoId, Usuario colaborador) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));

        validarAcceso(proyecto, colaborador);

        return conversacionRepository.findByProyecto(proyecto)
                .map(conv -> mensajeRepository.findByConversacionOrderByFechaHoraAsc(conv)
                        .stream()
                        .map(m -> new PmMensajeView(m, colaborador.getId()))
                        .toList())
                .orElse(List.of());
    }

    @Transactional
    public PmMensajeView enviarMensaje(Long proyectoId, String contenido, Usuario colaborador) {
        if (contenido == null || contenido.trim().isEmpty()) {
            throw new IllegalArgumentException("El mensaje no puede estar vacío.");
        }

        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));

        validarAcceso(proyecto, colaborador);

        Conversacion conversacion = conversacionRepository.findByProyecto(proyecto)
                .orElseThrow(() -> new IllegalStateException("El chat del proyecto aún no ha sido iniciado por el PM."));

        Mensaje mensaje = new Mensaje();
        mensaje.setConversacion(conversacion);
        mensaje.setAutor(colaborador);
        mensaje.setContenido(contenido);

        Mensaje guardado = mensajeRepository.save(mensaje);
        return new PmMensajeView(guardado, colaborador.getId());
    }
}
