package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Conversacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.enums.TipoConversacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversacionRepository extends JpaRepository<Conversacion, Long> {
    Optional<Conversacion> findByProyecto(Proyecto proyecto);


    List<Conversacion> findByProyectoOrderByIdAsc(Proyecto proyecto);

    List<Conversacion> findByProyectoAndTipoOrderByIdAsc(Proyecto proyecto, TipoConversacion tipo);

    Optional<Conversacion> findFirstByProyectoAndTipoOrderByIdAsc(Proyecto proyecto, TipoConversacion tipo);


}

