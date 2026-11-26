package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.VotoPublicacion;
import com.pucp.skillb_ia.model.VotoPublicacionId;
import com.pucp.skillb_ia.model.PublicacionForo;
import com.pucp.skillb_ia.model.enums.TipoVoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VotoPublicacionRepository extends JpaRepository<VotoPublicacion, VotoPublicacionId> {
    long countByPublicacionAndTipo(PublicacionForo publicacion, TipoVoto tipo);

    Optional<VotoPublicacion> findByUsuarioAndPublicacion(Usuario usuario, PublicacionForo publicacion);
}