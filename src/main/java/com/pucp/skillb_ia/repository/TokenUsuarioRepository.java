package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.TokenUsuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TokenUsuarioRepository extends JpaRepository<TokenUsuario, Long> {
    // Para validar el enlace de activación (A6) o el código de recuperación al hacer clic/ingresarlo.
    Optional<TokenUsuario> findByToken(String token);
}
