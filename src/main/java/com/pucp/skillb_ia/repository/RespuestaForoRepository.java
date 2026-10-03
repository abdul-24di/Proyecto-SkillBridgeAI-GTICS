package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.PublicacionForo;
import com.pucp.skillb_ia.model.RespuestaForo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RespuestaForoRepository extends JpaRepository<RespuestaForo, Long> {
    List<RespuestaForo> findByPublicacion(PublicacionForo publicacion);

    @Query("""
            select r from RespuestaForo r
            join fetch r.autor a
            join fetch a.rol
            where r.publicacion = :publicacion and r.activo = true
            order by r.fechaCreacion asc
            """)
    List<RespuestaForo> findByPublicacionConAutor(
            @Param("publicacion") PublicacionForo publicacion);

    Optional<RespuestaForo> findByIdAndActivoTrue(Long id);
}

