package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.CategoriaHabilidad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoriaHabilidadRepository extends JpaRepository<CategoriaHabilidad, Long> {
    List<CategoriaHabilidad> findByActivaTrue();
}
