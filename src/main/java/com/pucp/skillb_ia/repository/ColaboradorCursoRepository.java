package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.ColaboradorCurso;
import com.pucp.skillb_ia.model.Curso;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoColaboradorCurso;
import com.pucp.skillb_ia.model.enums.OrigenCurso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ColaboradorCursoRepository extends JpaRepository<ColaboradorCurso, Long> {
    List<ColaboradorCurso> findByColaborador(Usuario colaborador);
    List<ColaboradorCurso> findByEstado(EstadoColaboradorCurso estado);
    long countByOrigenAndEstado(OrigenCurso origen, EstadoColaboradorCurso estado);

    @Query("""
            select cc from ColaboradorCurso cc
            join fetch cc.colaborador colaborador
            join fetch colaborador.rol
            join fetch cc.curso curso
            left join fetch cc.asignadoPor
            left join fetch cc.evidenciaRevisadaPor
            order by cc.fechaSolicitud desc, cc.id desc
            """)
    List<ColaboradorCurso> findAllConDetalle();

    @Query("""
            select cc from ColaboradorCurso cc
            join fetch cc.colaborador colaborador
            join fetch colaborador.rol
            join fetch cc.curso curso
            left join fetch cc.asignadoPor
            left join fetch cc.evidenciaRevisadaPor
            where cc.id = :id
            """)
    Optional<ColaboradorCurso> findByIdConDetalle(@Param("id") Long id);

    boolean existsByColaboradorAndCursoAndEstadoIn(Usuario colaborador, Curso curso, List<EstadoColaboradorCurso> estados);

    boolean existsByColaboradorAndCursoAndEstado(Usuario colaborador, Curso curso, EstadoColaboradorCurso estado);

    //Lo usabmos para subir la evidencia de que llevo el curso. Confirmamos que la inscripción sea del propio colaborador.
    Optional<ColaboradorCurso> findByIdAndColaborador(Long id, Usuario colaborador);



}
