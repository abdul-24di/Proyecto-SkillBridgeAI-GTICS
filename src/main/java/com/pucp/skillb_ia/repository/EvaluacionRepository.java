package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Evaluacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvaluacionRepository extends JpaRepository<Evaluacion, Long> {
    List<Evaluacion> findByColaboradorIdOrderByFechaCreacionDesc(Long colaboradorId);
    List<Evaluacion> findByEvaluadorIdOrderByFechaCreacionDesc(Long evaluadorId);
    Optional<Evaluacion> findByAsignacionId(Long asignacionId);
}
