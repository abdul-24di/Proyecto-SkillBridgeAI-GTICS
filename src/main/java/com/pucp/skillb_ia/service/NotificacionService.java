package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.dto.NotificacionView;
import com.pucp.skillb_ia.model.Notificacion;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.NotificacionRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificacionService {
    private final NotificacionRepository notificacionRepository;
    private final UsuarioRepository usuarioRepository;

    public NotificacionService(NotificacionRepository notificacionRepository,
                               UsuarioRepository usuarioRepository) {
        this.notificacionRepository = notificacionRepository;
        this.usuarioRepository = usuarioRepository;
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

    private String url(Notificacion item) {
        // La notificación muestra el resultado sin enlazar a las vistas aún simuladas.
        return "#";
    }
}
