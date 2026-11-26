package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.NominaMensual;
import com.pucp.skillb_ia.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// Épica 9, Historia 9.3 — Tier 3, diferida. C7: solo el propio colaborador y
// el Administrador pueden consultar esto — esa restricción va en el service.
public interface NominaMensualRepository extends JpaRepository<NominaMensual, Long> {
    List<NominaMensual> findByColaborador(Usuario colaborador);
    Optional<NominaMensual> findByColaboradorAndAnioAndMes(Usuario colaborador, int anio, int mes);
}
