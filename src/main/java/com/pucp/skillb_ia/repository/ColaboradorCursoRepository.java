package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.ColaboradorCurso;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoColaboradorCurso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Historias A26/A27 — Tier 3, diferida.
public interface ColaboradorCursoRepository extends JpaRepository<ColaboradorCurso, Long> {
    List<ColaboradorCurso> findByColaborador(Usuario colaborador);
    List<ColaboradorCurso> findByEstado(EstadoColaboradorCurso estado);
}
