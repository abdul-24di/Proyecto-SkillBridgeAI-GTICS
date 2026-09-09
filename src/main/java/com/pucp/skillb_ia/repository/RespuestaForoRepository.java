package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.PublicacionForo;
import com.pucp.skillb_ia.model.RespuestaForo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RespuestaForoRepository extends JpaRepository<RespuestaForo, Long> {
    List<RespuestaForo> findByPublicacion(PublicacionForo publicacion);
}
