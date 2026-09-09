package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.ColaboradorHabilidad;
import com.pucp.skillb_ia.model.ColaboradorHabilidadId;
import com.pucp.skillb_ia.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ColaboradorHabilidadRepository extends JpaRepository<ColaboradorHabilidad, ColaboradorHabilidadId> {
    List<ColaboradorHabilidad> findByColaborador(Usuario colaborador);
}
