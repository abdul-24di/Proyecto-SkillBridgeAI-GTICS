package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Notificacion;
import com.pucp.skillb_ia.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// Notificaciones persistentes compartidas por todos los roles.
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
    List<Notificacion> findByUsuarioOrderByFechaCreacionDesc(Usuario usuario);
    long countByUsuarioAndLeidaFalse(Usuario usuario);
    Optional<Notificacion> findByIdAndUsuario(Long id, Usuario usuario);
}
