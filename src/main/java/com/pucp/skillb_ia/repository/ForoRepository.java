package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Foro;
import com.pucp.skillb_ia.model.Proyecto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ForoRepository extends JpaRepository<Foro, Long> {
    Optional<Foro> findByProyecto(Proyecto proyecto);

    // A7/A9: el RM lee todos los foros de la organización sin necesidad de asignación.
    List<Foro> findByEsPublicoTrue();
}
