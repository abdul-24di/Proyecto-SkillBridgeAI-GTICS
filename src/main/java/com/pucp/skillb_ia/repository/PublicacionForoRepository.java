package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Foro;
import com.pucp.skillb_ia.model.PublicacionForo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PublicacionForoRepository extends JpaRepository<PublicacionForo, Long> {
    List<PublicacionForo> findByForo(Foro foro);
}
