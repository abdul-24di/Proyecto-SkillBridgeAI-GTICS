package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.VotoPublicacion;
import com.pucp.skillb_ia.model.VotoPublicacionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VotoPublicacionRepository extends JpaRepository<VotoPublicacion, VotoPublicacionId> {
}
