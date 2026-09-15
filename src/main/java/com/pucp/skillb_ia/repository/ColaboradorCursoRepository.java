package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.ColaboradorCurso;
import com.pucp.skillb_ia.model.Curso;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoColaboradorCurso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// Solicitudes e inscripciones de cursos (A26/A27).
public interface ColaboradorCursoRepository extends JpaRepository<ColaboradorCurso, Long> {
    List<ColaboradorCurso> findByColaborador(Usuario colaborador);
    List<ColaboradorCurso> findByEstado(EstadoColaboradorCurso estado);

    @Query("""
            select cc from ColaboradorCurso cc
            join fetch cc.colaborador colaborador
            join fetch colaborador.rol
            join fetch cc.curso curso
            left join fetch cc.asignadoPor
            order by cc.fechaSolicitud desc, cc.id desc
            """)
    List<ColaboradorCurso> findAllConDetalle();

    @Query("""
            select cc from ColaboradorCurso cc
            join fetch cc.colaborador colaborador
            join fetch colaborador.rol
            join fetch cc.curso curso
            left join fetch cc.asignadoPor
            where cc.id = :id
            """)
    Optional<ColaboradorCurso> findByIdConDetalle(@Param("id") Long id);

    boolean existsByColaboradorAndCursoAndEstadoIn(
            Usuario colaborador, Curso curso,
            List<EstadoColaboradorCurso> estados);

    boolean existsByColaboradorAndCursoAndEstado(
            Usuario colaborador, Curso curso,
            EstadoColaboradorCurso estado);
}
