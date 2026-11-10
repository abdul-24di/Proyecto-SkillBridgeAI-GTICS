package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.dto.PmMensajeView;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Conversacion;
import com.pucp.skillb_ia.model.Mensaje;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.TipoConversacion;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ConversacionRepository;
import com.pucp.skillb_ia.repository.MensajeRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ColChatService {

    private static final int MAX_LONGITUD_MENSAJE = 1000;

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
        List<Proyecto> proyectos = new ArrayList<>();
        List<Asignacion> asignaciones = asignacionRepository.findByColaboradorAndEstado(colaborador, EstadoAsignacion.ACTIVA);

        for (Asignacion a : asignaciones) {
            proyectos.add(a.getProyecto());
        }

        return proyectos;
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

    @Transactional(readOnly = true)
    public List<PmMensajeView> obtenerMensajes(Long proyectoId, Usuario colaborador) {

        if (proyectoId == null) {
            throw new IllegalArgumentException("Debes seleccionar un proyecto.");
        }

        Proyecto proyecto = proyectoRepository.findById(proyectoId).orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));

        validarAcceso(proyecto, colaborador);

        List<PmMensajeView> vistas = new ArrayList<>();

        List<Conversacion> conversaciones = conversacionRepository.findByProyectoOrderByIdAsc(proyecto);

        if (conversaciones.isEmpty()) {
            return vistas;
        }

        List<Mensaje> mensajes = mensajeRepository.findByConversacionOrderByFechaHoraAsc(conversaciones.get(0));
        for (Mensaje m : mensajes) {
            vistas.add(new PmMensajeView(m, colaborador.getId()));
        }
        return vistas;
    }

    @Transactional
    public PmMensajeView enviarMensaje(Long proyectoId, String contenido, Usuario colaborador) {
        if (proyectoId == null) {
            throw new IllegalArgumentException("Debes seleccionar un proyecto para enviar un mensaje.");
        }
        if (contenido == null || contenido.trim().isEmpty()) {
            throw new IllegalArgumentException("El mensaje no puede estar vacío.");
        }
<<<<<<< Updated upstream
        // Mismas reglas que el chat del PM (PmChatService).
        String contenidoLimpio = contenido.trim();
        if (contenidoLimpio.length() > 1000) {
            throw new IllegalArgumentException("El mensaje no puede superar los 1000 caracteres.");
=======
        String texto = contenido.trim();
        if (texto.length() > MAX_LONGITUD_MENSAJE) {
            throw new IllegalArgumentException("El mensaje no puede superar los " + MAX_LONGITUD_MENSAJE + " caracteres.");
>>>>>>> Stashed changes
        }

        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));

        validarAcceso(proyecto, colaborador);


        Conversacion conversacion;
        List<Conversacion> conversaciones = conversacionRepository.findByProyectoOrderByIdAsc(proyecto);
        if (conversaciones.isEmpty()) {
            conversacion = new Conversacion();
            conversacion.setProyecto(proyecto);
            conversacion.setTipo(TipoConversacion.GRUPAL);
            conversacion = conversacionRepository.save(conversacion);
        } else {
            conversacion = conversaciones.get(0);
        }

        Mensaje mensaje = new Mensaje();
        mensaje.setConversacion(conversacion);
        mensaje.setAutor(colaborador);
<<<<<<< Updated upstream
        mensaje.setContenido(contenidoLimpio);
=======
        mensaje.setContenido(texto);
>>>>>>> Stashed changes

        Mensaje guardado = mensajeRepository.save(mensaje);
        return new PmMensajeView(guardado, colaborador.getId());
    }
}