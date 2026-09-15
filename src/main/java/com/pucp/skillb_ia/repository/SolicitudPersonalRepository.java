package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.SolicitudPersonal;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.enums.EstadoSolicitudPersonal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface SolicitudPersonalRepository extends JpaRepository<SolicitudPersonal, Long> {

    @Query("""
            select s from SolicitudPersonal s
            join fetch s.proyecto p
            join fetch p.pm
            left join fetch s.rmResponsable
            order by s.fechaSolicitud desc
            """)
    List<SolicitudPersonal> findAllConDetalleOrderByFechaSolicitudDesc();

    @Query("""
            select s from SolicitudPersonal s
            join fetch s.proyecto p
            join fetch p.pm
            left join fetch s.rmResponsable
            where s.id = :id
            """)
    Optional<SolicitudPersonal> findByIdConDetalle(@Param("id") Long id);

    List<SolicitudPersonal> findByEstado(EstadoSolicitudPersonal estado);

    boolean existsByProyectoAndEstadoIn(
            Proyecto proyecto, Collection<EstadoSolicitudPersonal> estados);
}
