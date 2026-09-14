package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Educacion;
import com.pucp.skillb_ia.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EducacionRepository extends JpaRepository<Educacion, Long> {
    List<Educacion> findByColaboradorAndActivoTrue(Usuario colaborador);

    List<Educacion> findByColaboradorAndActivoTrueOrderByFechaInicioDesc(Usuario colaborador);
}
