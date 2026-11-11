package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Conversacion;
import com.pucp.skillb_ia.model.ConversacionUsuario;
import com.pucp.skillb_ia.model.ConversacionUsuarioId;
import com.pucp.skillb_ia.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConversacionUsuarioRepository extends JpaRepository<ConversacionUsuario, ConversacionUsuarioId> {

    List<ConversacionUsuario> findByConversacion(Conversacion conversacion);
    List<ConversacionUsuario> findByUsuario(Usuario usuario);

    @Modifying
    @Query(value = "INSERT INTO conversacion_usuario (conversacion_id, usuario_id) VALUES (:conversacionId, :usuarioId)", nativeQuery = true)
    void agregarMiembro(@Param("conversacionId") Long conversacionId, @Param("usuarioId") Long usuarioId);
}
