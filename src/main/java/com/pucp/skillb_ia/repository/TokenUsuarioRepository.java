package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.TokenUsuario;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.TipoToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TokenUsuarioRepository extends JpaRepository<TokenUsuario, Long> {
    // Para validar el enlace de activación (A6) o el código de recuperación al hacer clic/ingresarlo.
    Optional<TokenUsuario> findByToken(String token);

    // Tokens de un tipo que el usuario todavía no usó (p. ej. invitaciones anteriores al reenviar).
    List<TokenUsuario> findByUsuarioAndTipoAndUsadoFalse(Usuario usuario, TipoToken tipo);
}
