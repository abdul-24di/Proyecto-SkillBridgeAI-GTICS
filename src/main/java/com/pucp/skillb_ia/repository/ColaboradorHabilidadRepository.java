package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.ColaboradorHabilidad;
import com.pucp.skillb_ia.model.ColaboradorHabilidadId;
import com.pucp.skillb_ia.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ColaboradorHabilidadRepository extends JpaRepository<ColaboradorHabilidad, ColaboradorHabilidadId> {


    //Buscamos las habilidades del colaborador que no han sido borradas.
    List<ColaboradorHabilidad> findByColaboradorAndActivoTrue(Usuario colaborador);

}