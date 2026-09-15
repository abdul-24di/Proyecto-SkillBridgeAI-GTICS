package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Conversacion;
import com.pucp.skillb_ia.model.ConversacionUsuario;
import com.pucp.skillb_ia.model.ConversacionUsuarioId;
import com.pucp.skillb_ia.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversacionUsuarioRepository extends JpaRepository<ConversacionUsuario, ConversacionUsuarioId> {
    // A9: para validar que el usuario (nunca el RM) es miembro del chat antes de mostrarlo.
    List<ConversacionUsuario> findByConversacion(Conversacion conversacion);
    List<ConversacionUsuario> findByUsuario(Usuario usuario);
}
