package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.ExperienciaProfesional;
import com.pucp.skillb_ia.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExperienciaProfesionalRepository extends JpaRepository<ExperienciaProfesional, Long> {
    List<ExperienciaProfesional> findByColaborador(Usuario colaborador);
}
