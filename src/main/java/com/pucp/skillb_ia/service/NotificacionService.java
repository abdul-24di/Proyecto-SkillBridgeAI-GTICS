package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.dto.NotificacionView;
import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Notificacion;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.repository.ActividadRepository;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.NotificacionRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class NotificacionService {
    private final NotificacionRepository notificacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final ActividadRepository actividadRepository;
    private final AsignacionRepository asignacionRepository;

    public NotificacionService(NotificacionRepository notificacionRepository,
                               UsuarioRepository usuarioRepository,
                               ActividadRepository actividadRepository,
                               AsignacionRepository asignacionRepository) {
        this.notificacionRepository = notificacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.actividadRepository = actividadRepository;
        this.asignacionRepository = asignacionRepository;
    }

    //Creamos una notificación para un usuario
    @Transactional
    public void crear(Usuario destinatario, String tipo, CategoriaNotificacion categoria,
                      String titulo, String descripcion, String entidad, Long entidadId) {
        Notificacion notificacion = new Notificacion();
        notificacion.setUsuario(destinatario);
        notificacion.setTipo(tipo);
        notificacion.setCategoria(categoria);
        notificacion.setTitulo(titulo);
        notificacion.setDescripcion(descripcion);
        notificacion.setEntidad(entidad);
        notificacion.setEntidadId(entidadId);
        notificacionRepository.save(notificacion);
    }

    @Transactional(readOnly = true)
    public List<NotificacionView> listar(Long usuarioId) {
        Usuario usuario = obtenerUsuario(usuarioId);
        return notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(usuario).stream()
                .limit(20).map(this::crearView).toList();
    }

    @Transactional
    public void marcarLeida(Long usuarioId, Long notificacionId) {
        Usuario usuario = obtenerUsuario(usuarioId);
        Notificacion notificacion = notificacionRepository.findByIdAndUsuario(notificacionId, usuario)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la notificación."));
        notificacion.setLeida(true);
        notificacionRepository.save(notificacion);
    }

    @Transactional
    public void marcarTodasLeidas(Long usuarioId) {
        Usuario usuario = obtenerUsuario(usuarioId);
        List<Notificacion> notificaciones =
                notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(usuario);
        notificaciones.forEach(item -> item.setLeida(true));
        notificacionRepository.saveAll(notificaciones);
    }

    private Usuario obtenerUsuario(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el usuario autenticado."));
    }

    private NotificacionView crearView(Notificacion item) {
        return new NotificacionView(
                item.getId(), item.getTitulo(), item.getDescripcion(),
                item.getCategoria().name(), item.isLeida(), item.getFechaCreacion(),
                url(item));
    }

    //Armamos el enlace al que redirige un clic en la notificación, según qué entidad referencia.
    private String url(Notificacion item) {
        if ("ACTIVIDAD".equals(item.getEntidad()) && item.getEntidadId() != null) {
            Optional<Actividad> actividadOpt = actividadRepository.findById(item.getEntidadId());
            if (actividadOpt.isPresent()) {
                Actividad actividad = actividadOpt.get();
                boolean esPm = item.getUsuario().getRol().getNombre().equals("PROJECT_MANAGER");
                if (esPm) {
                    return "/pm/actividades?proyectoId=" + actividad.getProyecto().getId();
                }
                Optional<Asignacion> asignacionOpt = asignacionRepository.findFirstByProyectoAndColaboradorAndEstado(
                        actividad.getProyecto(), actividad.getColaborador(), EstadoAsignacion.ACTIVA);
                if (asignacionOpt.isPresent()) {
                    return "/colaborador/proyectos/detalle?asignacionId=" + asignacionOpt.get().getId();
                }
            }
        }
        return "#";
    }
}