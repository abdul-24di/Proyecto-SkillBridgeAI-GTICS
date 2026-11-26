package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Curso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Catálogo de cursos de la Épica 5.
public interface CursoRepository extends JpaRepository<Curso, Long> {
    List<Curso> findByActivoTrueOrderByNombreAsc();
    List<Curso> findAllByOrderByNombreAsc();
}
