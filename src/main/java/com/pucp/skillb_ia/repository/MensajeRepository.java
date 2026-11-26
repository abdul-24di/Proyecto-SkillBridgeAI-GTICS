package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Conversacion;
import com.pucp.skillb_ia.model.Mensaje;
import com.pucp.skillb_ia.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MensajeRepository extends JpaRepository<Mensaje, Long> {

    List<Mensaje> findByConversacionOrderByFechaHoraAsc(Conversacion conversacion);

    Mensaje findFirstByConversacionOrderByIdDesc(Conversacion conversacion);

    long countByConversacionAndIdGreaterThanAndAutorNot(Conversacion conversacion, Long id, Usuario autor);

}
