package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Curso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Épica 5, Historia A25 — Tier 3, diferida.
public interface CursoRepository extends JpaRepository<Curso, Long> {
    List<Curso> findByActivoTrue();
}
