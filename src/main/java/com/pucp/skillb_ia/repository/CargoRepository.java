package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Cargo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CargoRepository extends JpaRepository<Cargo, Long> {
    List<Cargo> findByActivoTrueOrderByNombreAsc();
    List<Cargo> findAllByOrderByNombreAsc();
    boolean existsByNombreIgnoreCase(String nombre);
    Optional<Cargo> findByNombre(String nombre);
}
