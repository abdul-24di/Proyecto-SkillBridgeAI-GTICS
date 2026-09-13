package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Habilidad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HabilidadRepository extends JpaRepository<Habilidad, Long> {
    List<Habilidad> findByActivaTrue();
}
