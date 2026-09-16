package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Foro;
import com.pucp.skillb_ia.model.PublicacionForo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PublicacionForoRepository extends JpaRepository<PublicacionForo, Long> {

    List<PublicacionForo> findByForo(Foro foro);

    @Query("""
            select p from PublicacionForo p
            join fetch p.autor a
            join fetch a.rol
            left join fetch p.etiqueta
            where p.foro = :foro and p.activo = true
            order by p.fechaCreacion desc
            """)
    List<PublicacionForo> findByForoConDetalle(@Param("foro") Foro foro);

    @Query("""
            select p from PublicacionForo p
            join fetch p.autor a
            join fetch a.rol
            left join fetch p.etiqueta
            where p.id = :id and p.activo = true
            """)
    Optional<PublicacionForo> findByIdConDetalle(@Param("id") Long id);

    Optional<PublicacionForo> findByIdAndActivoTrue(Long id);
}