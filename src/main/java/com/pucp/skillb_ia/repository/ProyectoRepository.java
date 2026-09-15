package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProyectoRepository extends JpaRepository<Proyecto, Long> {
    List<Proyecto> findByPm(Usuario pm);


    List<Proyecto> findByEstado(EstadoProyecto estado);

    @Query("select p from Proyecto p join fetch p.pm order by p.fechaCreacion desc")
    List<Proyecto> findAllConPmOrderByFechaCreacionDesc();
}
