package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Conversacion;
import com.pucp.skillb_ia.model.Mensaje;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MensajeRepository extends JpaRepository<Mensaje, Long> {
    // Historial de mensajes en orden cronológico (Historia de Gestión del chat).
    List<Mensaje> findByConversacionOrderByFechaHoraAsc(Conversacion conversacion);
}
