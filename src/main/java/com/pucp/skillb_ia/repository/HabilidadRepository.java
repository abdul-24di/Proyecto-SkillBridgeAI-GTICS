package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Habilidad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HabilidadRepository extends JpaRepository<Habilidad, Long> {
    List<Habilidad> findByActivaTrue();

    Optional<Habilidad> findByNombreIgnoreCaseAndCategoria_Id(String nombre, Long categoriaId);

    // El nombre de una habilidad debe ser único en todo el catálogo, sin
    // importar la categoría (una habilidad no puede repetirse en dos categorías).
    Optional<Habilidad> findByNombreIgnoreCase(String nombre);

    // Para validar que un nombre no se repita. Toma la primera coincidencia: una base con datos antiguos puede
    // tener el mismo nombre en dos categorías, y con findByNombreIgnoreCase eso terminaba en un error genérico.
    Optional<Habilidad> findFirstByNombreIgnoreCaseOrderByIdAsc(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);

    long countByCategoria_Id(Long categoriaId);
}