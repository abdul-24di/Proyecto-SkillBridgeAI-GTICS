package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AsignacionRepository extends JpaRepository<Asignacion, Long> {

    List<Asignacion> findByColaborador(Usuario colaborador);

    List<Asignacion> findByProyecto(Proyecto proyecto);

    List<Asignacion> findByProyectoAndEstado(Proyecto proyecto, EstadoAsignacion estado);

    List<Asignacion> findByProyectoOrderByFechaSolicitudDesc(Proyecto proyecto);

    @Query("""
            select a from Asignacion a
            join fetch a.colaborador c
            join fetch c.rol
            where a.proyecto = :proyecto
              and a.estado = 'PENDIENTE'
              and a.origen = 'PROPUESTA_RM'
              and a.aprobadoPorPm = false
            order by a.fechaSolicitud desc
            """)
    List<Asignacion> findPendientesPmByProyecto(@Param("proyecto") Proyecto proyecto);


    List<Asignacion> findByColaboradorAndEstado(Usuario colaborador, EstadoAsignacion estado);

    List<Asignacion> findByColaboradorAndEstadoOrderByFechaFinalizacionDesc(Usuario colaborador, EstadoAsignacion estado);

    List<Asignacion> findByEstado(EstadoAsignacion estado);
    
    boolean existsByProyectoAndColaboradorAndEstado(
            Proyecto proyecto, Usuario colaborador, EstadoAsignacion estado);

    boolean existsByProyectoAndColaboradorAndEstadoIn(
            Proyecto proyecto, Usuario colaborador, Collection<EstadoAsignacion> estados);

    long countByProyectoAndEstado(Proyecto proyecto, EstadoAsignacion estado);

    long countByColaboradorAndEstado(Usuario colaborador, EstadoAsignacion estado);

    @Query("""
            select a
            from Asignacion a
            join fetch a.proyecto p
            join fetch p.pm
            join fetch a.colaborador c
            join fetch c.rol
            order by a.fechaSolicitud desc
            """)
    List<Asignacion> findAllConDetalleOrderByFechaSolicitudDesc();

    @Query("""
            select a
            from Asignacion a
            join fetch a.proyecto p
            join fetch p.pm
            join fetch a.colaborador c
            join fetch c.rol
            where a.id = :id
            """)
    Optional<Asignacion> findByIdConDetalle(@Param("id") Long id);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Asignacion a " +
            "WHERE a.proyecto = :proyecto AND a.colaborador = :colaborador AND " +
            "(a.estado = com.pucp.skillb_ia.model.enums.EstadoAsignacion.ACTIVA " +
            "OR (a.estado = com.pucp.skillb_ia.model.enums.EstadoAsignacion.FINALIZADA " +
            "AND a.motivoFinalizacion <> com.pucp.skillb_ia.model.enums.MotivoFinalizacion.BAJO_DESEMPENO))")
    boolean tieneAccesoVigente(@Param("proyecto") Proyecto proyecto, @Param("colaborador") Usuario colaborador);

    Optional<Asignacion> findFirstByProyectoAndColaboradorAndEstado(Proyecto proyecto, Usuario colaborador, EstadoAsignacion estado);

    @Query("""
            select a
            from Asignacion a
            join fetch a.proyecto p
            join fetch p.pm
            join fetch a.colaborador c
            join fetch c.rol
            where c.id = :colaboradorId
            order by a.fechaSolicitud desc
            """)
    List<Asignacion> findByColaboradorIdConDetalle(@Param("colaboradorId") Long colaboradorId);
}
