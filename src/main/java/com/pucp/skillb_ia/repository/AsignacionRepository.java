package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AsignacionRepository extends JpaRepository<Asignacion, Long> {
    // A2: "mis proyectos y asignaciones" del colaborador, de solo lectura.
    List<Asignacion> findByColaborador(Usuario colaborador);

    List<Asignacion> findByProyecto(Proyecto proyecto);

    // Carga de trabajo actual del colaborador — se consulta antes de aprobar (A4)
    // y para validar MAX_ASIGNACIONES_POR_COLABORADOR.
    List<Asignacion> findByColaboradorAndEstado(Usuario colaborador, EstadoAsignacion estado);

    // Bandejas de aprobaciones pendientes de PM y RM (A4/A10).
    List<Asignacion> findByEstado(EstadoAsignacion estado);
}
