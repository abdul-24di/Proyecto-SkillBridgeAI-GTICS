package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Educacion;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoCertificado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EducacionRepository extends JpaRepository<Educacion, Long> {
    List<Educacion> findByColaboradorAndActivoTrue(Usuario colaborador);

    List<Educacion> findByColaboradorAndActivoTrueOrderByFechaInicioDesc(Usuario colaborador);

    // Bandeja del RM: la solicitud más antigua primero; el id desempata para un orden estable.
    @Query("""
            select e from Educacion e
            join fetch e.colaborador u
            join fetch u.rol
            left join fetch u.cargo
            left join fetch e.revisadoPor
            where e.estado = :estado and e.activo = true
            order by e.fechaCreacion asc, e.id asc
            """)
    List<Educacion> findActivasByEstadoConDetalle(@Param("estado") EstadoCertificado estado);

    @Query("""
            select e from Educacion e
            join fetch e.colaborador u
            join fetch u.rol
            left join fetch u.cargo
            left join fetch e.revisadoPor
            where e.id = :id
            """)
    Optional<Educacion> findByIdConDetalle(@Param("id") Long id);

    long countByEstadoAndActivoTrue(EstadoCertificado estado);

    long countByColaboradorAndEstadoAndActivoTrue(Usuario colaborador, EstadoCertificado estado);
}
