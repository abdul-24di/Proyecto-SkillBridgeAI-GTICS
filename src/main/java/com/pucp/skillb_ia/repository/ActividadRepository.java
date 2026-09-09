package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Épica 9 — Tier 3, diferida. Repository ya listo para cuando se active.
public interface ActividadRepository extends JpaRepository<Actividad, Long> {
    List<Actividad> findByProyecto(Proyecto proyecto);
    List<Actividad> findByColaborador(Usuario colaborador);
}
