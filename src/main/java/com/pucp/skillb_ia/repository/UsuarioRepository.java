package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Cargo;
import com.pucp.skillb_ia.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByCorreo(String correo);
    boolean existsByCorreo(String correo);
    List<Usuario> findByCargo(Cargo cargo);

    @Query("""
            select u
            from Usuario u
            join fetch u.rol r
            where r.nombre = :rolNombre
              and u.activo = true
            order by u.nombre asc, u.apellido asc
            """)
    List<Usuario> findActivosByRolNombre(@Param("rolNombre") String rolNombre);

    // Colaboradores que el RM puede ver y asignar (TASK-053): activos y con el pre-registro
    // aprobado; NULL (anterior al pre-registro) cuenta como aprobado.
    @Query("""
            select u
            from Usuario u
            join fetch u.rol r
            where r.nombre = 'COLABORADOR'
              and u.activo = true
              and (u.registroEstado is null
                   or u.registroEstado = com.pucp.skillb_ia.model.enums.EstadoRegistro.APROBADO)
            order by u.nombre asc, u.apellido asc
            """)
    List<Usuario> findColaboradoresVisiblesRm();

    @Query("""
            select u
            from Usuario u
            join fetch u.rol r
            where u.id = :id
              and r.nombre = 'COLABORADOR'
              and u.activo = true
              and (u.registroEstado is null
                   or u.registroEstado = com.pucp.skillb_ia.model.enums.EstadoRegistro.APROBADO)
            """)
    Optional<Usuario> findColaboradorVisibleRmById(@Param("id") Long id);

    @Query("""
            select u
            from Usuario u
            join fetch u.rol r
            left join fetch u.cargo
            order by u.fechaCreacion desc
            """)
    List<Usuario> findAllWithRol();
}
