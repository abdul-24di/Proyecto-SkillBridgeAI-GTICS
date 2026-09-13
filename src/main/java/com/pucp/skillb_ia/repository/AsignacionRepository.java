package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AsignacionRepository extends JpaRepository<Asignacion, Long> {

    List<Asignacion> findByColaborador(Usuario colaborador);

    List<Asignacion> findByProyecto(Proyecto proyecto);


    List<Asignacion> findByColaboradorAndEstado(Usuario colaborador, EstadoAsignacion estado);


    List<Asignacion> findByEstado(EstadoAsignacion estado);
}
