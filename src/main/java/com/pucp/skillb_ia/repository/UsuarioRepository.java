package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    // El correo es el identificador de login (C4) — debe ser único.
    Optional<Usuario> findByCorreo(String correo);
    boolean existsByCorreo(String correo);
}
