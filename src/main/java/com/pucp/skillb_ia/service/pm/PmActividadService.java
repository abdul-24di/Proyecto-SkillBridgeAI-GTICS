package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.dto.PmActividadView;
import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.repository.ActividadRepository;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PmActividadService {

    private final ActividadRepository actividadRepository;
    private final ProyectoRepository proyectoRepository;
    private final AsignacionRepository asignacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;
    private final com.pucp.skillb_ia.service.PenalizacionService penalizacionService;

    public PmActividadService(ActividadRepository actividadRepository,
                              ProyectoRepository proyectoRepository,
                              AsignacionRepository asignacionRepository,
                              UsuarioRepository usuarioRepository,
                              AuditoriaService auditoriaService,
                              com.pucp.skillb_ia.service.PenalizacionService penalizacionService) {
        this.actividadRepository = actividadRepository;
        this.proyectoRepository = proyectoRepository;
        this.asignacionRepository = asignacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaService = auditoriaService;
        this.penalizacionService = penalizacionService;
    }

    @Transactional(readOnly = true)
    public List<PmActividadView> listarPorProyecto(Long proyectoId, Usuario pm) {
        Proyecto proyecto = obtenerProyectoDelPm(proyectoId, pm);
        return actividadRepository.findByProyectoOrderByFechaLimiteAsc(proyecto)
                .stream()
                .map(PmActividadView::new)
                .toList();
    }

    @Transactional
    public Actividad crear(Long proyectoId, Long colaboradorId,
                           String titulo, String descripcion,
                           BigDecimal horasEstimadas, LocalDate fechaLimite,
                           Usuario pm) {
        Proyecto proyecto = obtenerProyectoDelPm(proyectoId, pm);

        // Verificar que el colaborador tiene asignación ACTIVA en el proyecto
        Usuario colaborador = usuarioRepository.findById(colaboradorId)
                .orElseThrow(() -> new IllegalArgumentException("Colaborador no encontrado."));
        boolean tieneAsignacionActiva = asignacionRepository
                .existsByProyectoAndColaboradorAndEstado(proyecto, colaborador, EstadoAsignacion.ACTIVA);
        if (!tieneAsignacionActiva) {
            throw new IllegalStateException(
                    "El colaborador no tiene una asignación activa en este proyecto.");
        }

        Actividad actividad = new Actividad();
        actividad.setProyecto(proyecto);
        actividad.setColaborador(colaborador);
        actividad.setTitulo(titulo);
        actividad.setDescripcion(descripcion);
        actividad.setHorasEstimadas(horasEstimadas);
        actividad.setFechaLimite(fechaLimite);
        actividad.setEstado(EstadoActividad.PENDIENTE);
        actividad.setCreadoPor(pm);
        Actividad saved = actividadRepository.save(actividad);

        auditoriaService.registrar(pm, "CREAR", "ACTIVIDAD", saved.getId(),
                "PM creó actividad '" + titulo + "' para colaborador ID " + colaboradorId
                        + " en proyecto '" + proyecto.getNombre() + "'.");
        return saved;
    }

    @Transactional
    public void confirmar(Long actividadId, Usuario pm) {
        Actividad actividad = obtenerActividadDelPm(actividadId, pm);

        if (actividad.getEstado() != EstadoActividad.EN_REVISION) {
            throw new IllegalStateException(
                    "Solo se pueden confirmar actividades en estado EN_REVISION.");
        }
        actividad.setEstado(EstadoActividad.COMPLETADA);
        actividad.setFechaEntrega(LocalDateTime.now());
        actividadRepository.save(actividad);

        auditoriaService.registrar(pm, "CONFIRMAR", "ACTIVIDAD", actividadId,
                "PM confirmó la entrega de la actividad '" + actividad.getTitulo() + "'.");
    }

    @Transactional
    public void devolver(Long actividadId, String comentario, Usuario pm) {
        Actividad actividad = obtenerActividadDelPm(actividadId, pm);

        if (actividad.getEstado() != EstadoActividad.EN_REVISION) {
            throw new IllegalStateException(
                    "Solo se pueden devolver actividades en estado EN_REVISION.");
        }
        actividad.setEstado(EstadoActividad.EN_PROGRESO);
        actividad.setComentarioDevolucion(comentario);
        actividad.setVecesDevuelta(actividad.getVecesDevuelta() + 1);
        actividad.setFechaMarcadoRevision(null);
        actividadRepository.save(actividad);

        auditoriaService.registrar(pm, "DEVOLVER", "ACTIVIDAD", actividadId,
                "PM devolvió la actividad '" + actividad.getTitulo() + "': " + comentario);

        //En caso de que el PM devuelva una actividad por no estar bien hecha se contará como un strike.
        penalizacionService.aplicarStrikePorDevolucion(actividad);
        penalizacionService.verificarYRemoverPorStrikes(actividad.getColaborador(), actividad.getProyecto());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Proyecto obtenerProyectoDelPm(Long proyectoId, Usuario pm) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));
        if (!proyecto.getPm().getId().equals(pm.getId())) {
            throw new SecurityException("No tienes permiso para acceder a este proyecto.");
        }
        return proyecto;
    }

    private Actividad obtenerActividadDelPm(Long actividadId, Usuario pm) {
        Actividad actividad = actividadRepository.findById(actividadId)
                .orElseThrow(() -> new IllegalArgumentException("Actividad no encontrada."));
        if (!actividad.getProyecto().getPm().getId().equals(pm.getId())) {
            throw new SecurityException("No tienes permiso para acceder a esta actividad.");
        }
        return actividad;
    }
}