package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Foro;
import com.pucp.skillb_ia.model.Proyecto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ForoRepository extends JpaRepository<Foro, Long> {
    Optional<Foro> findByProyecto(Proyecto proyecto);

    // A7/A9: el RM lee todos los foros de la organización sin necesidad de asignación.
    List<Foro> findByEsPublicoTrue();

    @Query("""
            select f from Foro f
            left join fetch f.proyecto p
            left join fetch p.pm
            order by f.fechaCreacion desc
            """)
    List<Foro> findTodosConProyectoYPm();

    @Query("""
            select f from Foro f
            left join fetch f.proyecto p
            left join fetch p.pm
            where f.id = :id
            """)
    Optional<Foro> findByIdConProyectoYPm(@Param("id") Long id);
}
