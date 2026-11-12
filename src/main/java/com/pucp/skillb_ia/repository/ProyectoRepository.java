package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    // Contamos los proyectos asignados al RM revisor según los estados especificados.
    long countByRmRevisorAndEstadoIn(Usuario rm, Collection<EstadoProyecto> estados);

    // Obtenemos los proyectos supervisados por el RM incluyendo la información de sus PM
    @Query("select p from Proyecto p join fetch p.pm where p.rmRevisor = :rm order by p.fechaCreacion desc")
    List<Proyecto> findByRmRevisorConPm(@Param("rm") Usuario rm);

    // Realizamos la asignación del RM solo si el proyecto no tiene un revisor asignado.
    // Devuelve 1 si se actualizó el registro y 0 si no se cumplió la condición.
    @Modifying
    @Query("update Proyecto p set p.rmRevisor = :rm where p.id = :id and p.rmRevisor is null")
    int reclamar(@Param("id") Long id, @Param("rm") Usuario rm);




}
