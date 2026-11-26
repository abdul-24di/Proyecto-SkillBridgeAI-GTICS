package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.VotoRespuesta;
import com.pucp.skillb_ia.model.VotoRespuestaId;
import com.pucp.skillb_ia.model.RespuestaForo;
import com.pucp.skillb_ia.model.enums.TipoVoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VotoRespuestaRepository extends JpaRepository<VotoRespuesta, VotoRespuestaId> {
    long countByRespuestaAndTipo(RespuestaForo respuesta, TipoVoto tipo);

    Optional<VotoRespuesta> findByUsuarioAndRespuesta(Usuario usuario, RespuestaForo respuesta);
}