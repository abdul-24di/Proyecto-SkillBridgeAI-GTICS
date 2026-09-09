package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.VotoRespuesta;
import com.pucp.skillb_ia.model.VotoRespuestaId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VotoRespuestaRepository extends JpaRepository<VotoRespuesta, VotoRespuestaId> {
}
