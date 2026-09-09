package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.LogAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LogAuditoriaRepository extends JpaRepository<LogAuditoria, Long> {
    // La vista admin-auditoria.html filtra por usuario, rol, tipo de acción y
    // rango de fechas — esas consultas se arman con Specifications/JPQL en el
    // service cuando se construya el controller, no aquí (evitar sobrecargar
    // el repository con combinaciones de filtros que aún no se necesitan).
}
