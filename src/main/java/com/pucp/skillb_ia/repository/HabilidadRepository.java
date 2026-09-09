package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Habilidad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HabilidadRepository extends JpaRepository<Habilidad, Long> {
    // Catálogo oficial que consume el select del colaborador (nunca texto libre) —
    // ver corrección aplicada en col-perfil.html.
    List<Habilidad> findByActivaTrue();
}
