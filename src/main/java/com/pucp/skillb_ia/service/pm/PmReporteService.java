package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.dto.PmReporteView;
import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.repository.ActividadRepository;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PmReporteService {

    private final ProyectoRepository proyectoRepository;
    private final ActividadRepository actividadRepository;
    private final AsignacionRepository asignacionRepository;

    public PmReporteService(ProyectoRepository proyectoRepository,
                            ActividadRepository actividadRepository,
                            AsignacionRepository asignacionRepository) {
        this.proyectoRepository = proyectoRepository;
        this.actividadRepository = actividadRepository;
        this.asignacionRepository = asignacionRepository;
    }

    @Transactional(readOnly = true)
    public PmReporteView obtener(Long proyectoId, Usuario pm) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));
        if (!proyecto.getPm().getId().equals(pm.getId())) {
            throw new SecurityException("No tienes permiso sobre este proyecto.");
        }

        List<Actividad> actividades = actividadRepository.findByProyectoAndActivoTrue(proyecto);
        int total       = actividades.size();
        int pendientes  = (int) actividades.stream().filter(a -> a.getEstado() == EstadoActividad.PENDIENTE).count();
        int enProgreso  = (int) actividades.stream().filter(a -> a.getEstado() == EstadoActividad.EN_PROGRESO).count();
        int enRevision  = (int) actividades.stream().filter(a -> a.getEstado() == EstadoActividad.EN_REVISION).count();
        int completadas = (int) actividades.stream().filter(a -> a.getEstado() == EstadoActividad.COMPLETADA).count();

        BigDecimal horasTotal = actividades.stream()
                .map(Actividad::getHorasEstimadas)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Asignacion> activas = asignacionRepository.findByProyectoAndEstado(
                proyecto, EstadoAsignacion.ACTIVA);

        // Métricas por colaborador
        Map<Long, List<Actividad>> porColaborador = actividades.stream()
                .collect(Collectors.groupingBy(a -> a.getColaborador().getId()));

        List<PmReporteView.ColaboradorMetrica> metricas = activas.stream()
                .map(asig -> {
                    Usuario col = asig.getColaborador();
                    List<Actividad> acts = porColaborador.getOrDefault(col.getId(), List.of());
                    int asignadas = acts.size();
                    int comp = (int) acts.stream()
                            .filter(a -> a.getEstado() == EstadoActividad.COMPLETADA).count();
                    String nombre = col.getNombre() + " " + col.getApellido();
                    String iniciales = iniciales(col.getNombre(), col.getApellido());
                    return new PmReporteView.ColaboradorMetrica(
                            nombre, iniciales, asig.getHorasSemanales(), asignadas, comp);
                })
                .toList();

        return new PmReporteView(proyecto, total, pendientes, enProgreso, enRevision,
                completadas, activas.size(), horasTotal, metricas);
    }

    private static String iniciales(String nombre, String apellido) {
        String n = (nombre != null && !nombre.isEmpty()) ? String.valueOf(nombre.charAt(0)) : "";
        String a = (apellido != null && !apellido.isEmpty()) ? String.valueOf(apellido.charAt(0)) : "";
        return (n + a).toUpperCase();
    }
}
