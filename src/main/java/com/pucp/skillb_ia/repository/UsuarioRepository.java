package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByCorreo(String correo);
    boolean existsByCorreo(String correo);

    @Query("""
            select u
            from Usuario u
            join fetch u.rol r
            where r.nombre = :rolNombre
              and u.activo = true
            order by u.nombre asc, u.apellido asc
            """)
    List<Usuario> findActivosByRolNombre(@Param("rolNombre") String rolNombre);
}
