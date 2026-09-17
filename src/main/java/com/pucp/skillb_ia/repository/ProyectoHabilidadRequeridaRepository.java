package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.ProyectoHabilidadRequerida;
import com.pucp.skillb_ia.model.ProyectoHabilidadRequeridaId;
import com.pucp.skillb_ia.model.Habilidad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProyectoHabilidadRequeridaRepository extends JpaRepository<ProyectoHabilidadRequerida, ProyectoHabilidadRequeridaId> {
    List<ProyectoHabilidadRequerida> findByProyecto(Proyecto proyecto);

    java.util.Optional<ProyectoHabilidadRequerida> findByProyectoAndHabilidad(Proyecto proyecto, Habilidad habilidad);
}