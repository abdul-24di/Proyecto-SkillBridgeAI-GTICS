package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.repository.ActividadRepository;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.service.PenalizacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Servicio de indicadores y alertas para el dashboard principal del Project Manager:
 * conteos de proyectos activos, entregas por revisar, equipo asignado y panel de strikes.
 */
@Service
public class PmDashboardService {

    private final ProyectoRepository proyectoRepository;
    private final ActividadRepository actividadRepository;
    private final AsignacionRepository asignacionRepository;
    private final PenalizacionService penalizacionService;

    // Al llegar a este número de strikes en un proyecto se notifica al PM (ver PenalizacionService).
    public static final int MAXIMO_STRIKES = 3;

    // Fila del panel "Strikes del equipo" del dashboard del PM.
    public record StrikeColaborador(Long colaboradorId, String colaboradorNombre, String iniciales,
                                    Long proyectoId, String proyectoNombre, int strikes) {
        public boolean enLimite() { return strikes >= MAXIMO_STRIKES; }
        public int porcentaje() { return Math.min(100, strikes * 100 / MAXIMO_STRIKES); }
    }

    public PmDashboardService(ProyectoRepository proyectoRepository,
                              ActividadRepository actividadRepository,
                              AsignacionRepository asignacionRepository,
                              PenalizacionService penalizacionService) {
        this.penalizacionService = penalizacionService;
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

    // Colaboradores activos de los proyectos del PM (activos o en espera) con al menos un strike en ese proyecto,
    // de más a menos strikes. Un mismo colaborador aparece una vez por proyecto.
    @Transactional(readOnly = true)
    public List<StrikeColaborador> getStrikesEquipo(Usuario pm) {
        List<Proyecto> proyectos = new ArrayList<>(proyectoRepository.findByPmAndEstado(pm, EstadoProyecto.ACTIVO));
        proyectos.addAll(proyectoRepository.findByPmAndEstado(pm, EstadoProyecto.EN_ESPERA));

        List<StrikeColaborador> resultado = new ArrayList<>();
        for (Proyecto proyecto : proyectos) {
            for (Asignacion asignacion : asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA)) {
                Usuario colaborador = asignacion.getColaborador();
                int strikes = penalizacionService.contarStrikes(colaborador, proyecto);
                if (strikes > 0) {
                    resultado.add(new StrikeColaborador(colaborador.getId(),
                            colaborador.getNombre() + " " + colaborador.getApellido(),
                            iniciales(colaborador), proyecto.getId(), proyecto.getNombre(), strikes));
                }
            }
        }
        resultado.sort(Comparator.comparingInt(StrikeColaborador::strikes).reversed()
                .thenComparing(StrikeColaborador::colaboradorNombre));
        return resultado;
    }

    private String iniciales(Usuario usuario) {
        String n = usuario.getNombre() == null || usuario.getNombre().isEmpty() ? "" : usuario.getNombre().substring(0, 1);
        String a = usuario.getApellido() == null || usuario.getApellido().isEmpty() ? "" : usuario.getApellido().substring(0, 1);
        return (n + a).toUpperCase();
    }

    @Transactional(readOnly = true)
    public List<Proyecto> getProyectosQueNecesitanAtencion(Usuario pm) {
        List<Proyecto> activos = proyectoRepository.findByPmAndEstado(pm, EstadoProyecto.ACTIVO);
        return activos.stream().limit(5).toList();
    }
}
