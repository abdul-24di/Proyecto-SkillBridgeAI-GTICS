package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.LogAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LogAuditoriaRepository extends JpaRepository<LogAuditoria, Long> {
    // Usado por admin-configuracion.html: última modificación por parámetro
    // e historial reciente de cambios de configuración.
    List<LogAuditoria> findTop20ByEntidadOrderByFechaHoraDesc(String entidad);

    // admin-auditoria.html: los filtros (usuario, rol, tipo de acción, fechas)
    // se aplican en el service sobre esta lista completa — el volumen esperado
    // para un proyecto de curso no justifica Specifications dinámicas.
    @Query("""
            select l
            from LogAuditoria l
            left join fetch l.usuario u
            left join fetch u.rol
            order by l.fechaHora desc
            """)
    List<LogAuditoria> findAllConUsuario();
}
