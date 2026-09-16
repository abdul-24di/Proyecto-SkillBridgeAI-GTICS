package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProyectoRepository extends JpaRepository<Proyecto, Long> {
    List<Proyecto> findByPm(Usuario pm);

    List<Proyecto> findByEstado(EstadoProyecto estado);

    List<Proyecto> findByPmOrderByFechaCreacionDesc(Usuario pm);

    List<Proyecto> findByPmAndEstado(Usuario pm, EstadoProyecto estado);

    @Query("select p from Proyecto p join fetch p.pm order by p.fechaCreacion desc")
    List<Proyecto> findAllConPmOrderByFechaCreacionDesc();

    @Query("""
            select p from Proyecto p
            join fetch p.pm
            where p.id = :id
            """)
    Optional<Proyecto> findByIdConPm(@Param("id") Long id);
}
