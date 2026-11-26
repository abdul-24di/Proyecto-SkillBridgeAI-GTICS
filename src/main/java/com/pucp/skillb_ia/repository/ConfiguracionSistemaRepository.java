package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.ConfiguracionSistema;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConfiguracionSistemaRepository extends JpaRepository<ConfiguracionSistema, Long> {
    // Usado por el ConfiguracionService.getValor(clave) que leerá cualquier
    // parte del sistema (MAX_ASIGNACIONES_POR_COLABORADOR, TOPE_HORAS_EXTRA_BONO, etc.).
    Optional<ConfiguracionSistema> findByClave(String clave);
}
