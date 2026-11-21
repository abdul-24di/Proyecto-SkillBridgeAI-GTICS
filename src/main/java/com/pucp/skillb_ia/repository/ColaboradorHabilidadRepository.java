package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.ColaboradorHabilidad;
import com.pucp.skillb_ia.model.ColaboradorHabilidadId;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.Habilidad;
import com.pucp.skillb_ia.model.enums.EstadoValidacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ColaboradorHabilidadRepository extends JpaRepository<ColaboradorHabilidad, ColaboradorHabilidadId> {


    //Buscamos las habilidades del colaborador que no han sido borradas.
    List<ColaboradorHabilidad> findByColaboradorAndActivoTrue(Usuario colaborador);

    // Habilidades activas en un estado de validación (búsqueda y asignación usan solo VALIDADA).
    List<ColaboradorHabilidad> findByColaboradorAndActivoTrueAndEstadoValidacion(
            Usuario colaborador, EstadoValidacion estadoValidacion);

    Optional<ColaboradorHabilidad> findByColaboradorAndHabilidad(
            Usuario colaborador, Habilidad habilidad);

    long countByHabilidadAndActivoTrue(Habilidad habilidad);

    // TASK-069: habilidades activas de todos los colaboradores en un estado, en una sola consulta (Talent Matching).
    @Query("""
            select ch from ColaboradorHabilidad ch
            join fetch ch.habilidad
            where ch.activo = true and ch.estadoValidacion = :estado
            """)
    List<ColaboradorHabilidad> findActivasPorEstadoValidacion(@Param("estado") EstadoValidacion estado);

}
