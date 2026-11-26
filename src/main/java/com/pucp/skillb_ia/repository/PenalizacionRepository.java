package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Penalizacion;
import com.pucp.skillb_ia.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface PenalizacionRepository extends JpaRepository<Penalizacion, Long> {
    List<Penalizacion> findByColaborador(Usuario colaborador);
    boolean existsByActividad(com.pucp.skillb_ia.model.Actividad actividad);
}