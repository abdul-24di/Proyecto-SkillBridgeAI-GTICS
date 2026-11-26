package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Documento;
import com.pucp.skillb_ia.model.Proyecto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {
    List<Documento> findByProyectoAndActivoTrueOrderByFechaCreacionDesc(Proyecto proyecto);

    Optional<Documento> findByIdAndActivoTrue(Long id);
}