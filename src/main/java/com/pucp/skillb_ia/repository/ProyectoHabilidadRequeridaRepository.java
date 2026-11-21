package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.ProyectoHabilidadRequerida;
import com.pucp.skillb_ia.model.ProyectoHabilidadRequeridaId;
import com.pucp.skillb_ia.model.Habilidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProyectoHabilidadRequeridaRepository extends JpaRepository<ProyectoHabilidadRequerida, ProyectoHabilidadRequeridaId> {
    List<ProyectoHabilidadRequerida> findByProyecto(Proyecto proyecto);

    @Query("""
            select r from ProyectoHabilidadRequerida r
            join fetch r.habilidad
            where r.proyecto.id in :proyectoIds
            """)
    List<ProyectoHabilidadRequerida> findByProyectoIdInConHabilidad(@Param("proyectoIds") Collection<Long> proyectoIds);

    java.util.Optional<ProyectoHabilidadRequerida> findByProyectoAndHabilidad(Proyecto proyecto, Habilidad habilidad);
}