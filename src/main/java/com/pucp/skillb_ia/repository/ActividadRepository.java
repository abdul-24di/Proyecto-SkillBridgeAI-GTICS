package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;


public interface ActividadRepository extends JpaRepository<Actividad, Long> {
    List<Actividad> findByProyectoAndActivoTrue(Proyecto proyecto);
    List<Actividad> findByColaboradorAndActivoTrue(Usuario colaborador);

    List<Actividad> findByProyectoAndActivoTrueOrderByFechaLimiteAsc(Proyecto proyecto);

    List<Actividad> findByProyectoAndEstadoAndActivoTrue(Proyecto proyecto, EstadoActividad estado);

    long countByProyectoAndEstadoAndActivoTrue(Proyecto proyecto, EstadoActividad estado);

    List<Actividad> findByProyectoAndColaboradorAndActivoTrue(Proyecto proyecto, Usuario colaborador);

    @Query("""
            select a from Actividad a
            join fetch a.proyecto p
            join fetch a.colaborador c
            where a.estado = :estado
              and a.activo = true
              and a.fechaEntrega >= :inicio
              and a.fechaEntrega < :fin
            order by c.apellido, c.nombre, p.nombre, a.fechaEntrega
            """)
    List<Actividad> findByEstadoAndFechaEntregaBetweenConDetalle(
            @Param("estado") EstadoActividad estado,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin);
}