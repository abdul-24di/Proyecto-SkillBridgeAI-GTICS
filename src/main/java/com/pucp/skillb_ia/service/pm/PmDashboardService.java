package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.repository.ActividadRepository;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PmDashboardService {

    private final ProyectoRepository proyectoRepository;
    private final ActividadRepository actividadRepository;
    private final AsignacionRepository asignacionRepository;

    public PmDashboardService(ProyectoRepository proyectoRepository,
                              ActividadRepository actividadRepository,
                              AsignacionRepository asignacionRepository) {
        this.proyectoRepository = proyectoRepository;
        this.actividadRepository = actividadRepository;
        this.asignacionRepository = asignacionRepository;
    }

    @Transactional(readOnly = true)
    public long getCountProyectosActivos(Usuario pm) {
        return proyectoRepository.findByPmAndEstado(pm, EstadoProyecto.ACTIVO).size();
    }

    @Transactional(readOnly = true)
    public long getCountActividadesPendientes(Usuario pm) {
        return actividadRepository.countByPmAndEstado(pm, EstadoActividad.EN_REVISION);
    }

    @Transactional(readOnly = true)
    public long getCountColaboradoresEquipo(Usuario pm) {
        return asignacionRepository.countColaboradoresUnicosActivosPorPm(pm, com.pucp.skillb_ia.model.enums.EstadoAsignacion.ACTIVA);
    }

    @Transactional(readOnly = true)
    public long getCountAsignacionesEnRevision(Usuario pm) {
        return asignacionRepository.countPendientesRmByPm(pm, com.pucp.skillb_ia.model.enums.EstadoAsignacion.PENDIENTE, com.pucp.skillb_ia.model.enums.OrigenAsignacion.PROPUESTA_PM);
    }

    @Transactional(readOnly = true)
    public List<Actividad> getActividadesPendientes(Usuario pm) {
        return actividadRepository.findByPmAndEstadoConDetalle(pm, EstadoActividad.EN_REVISION);
    }

    @Transactional(readOnly = true)
    public List<Proyecto> getProyectosQueNecesitanAtencion(Usuario pm) {
        List<Proyecto> activos = proyectoRepository.findByPmAndEstado(pm, EstadoProyecto.ACTIVO);
        return activos.stream().limit(5).toList();
    }
}
