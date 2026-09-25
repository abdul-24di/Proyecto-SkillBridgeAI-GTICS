package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.dto.PmActividadView;
import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.repository.ActividadRepository;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.NotificacionService;
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
    private final NotificacionService notificacionService;

    public PmActividadService(ActividadRepository actividadRepository,
                              ProyectoRepository proyectoRepository,
                              AsignacionRepository asignacionRepository,
                              UsuarioRepository usuarioRepository,
                              AuditoriaService auditoriaService,
                              com.pucp.skillb_ia.service.PenalizacionService penalizacionService,
                              NotificacionService notificacionService) {
        this.actividadRepository = actividadRepository;
        this.proyectoRepository = proyectoRepository;
        this.asignacionRepository = asignacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaService = auditoriaService;
        this.penalizacionService = penalizacionService;
        this.notificacionService = notificacionService;
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

        notificacionService.crear(colaborador, "NUEVA_ACTIVIDAD", CategoriaNotificacion.ACTIVIDAD,
                "Nueva actividad asignada",
                "Te asignaron \"" + titulo + "\" en " + proyecto.getNombre() + ". Fecha límite: " + fechaLimite + ".",
                "ACTIVIDAD", saved.getId());

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

        notificacionService.crear(actividad.getColaborador(), "ACTIVIDAD_CONFIRMADA", CategoriaNotificacion.ACTIVIDAD,
                "Actividad confirmada",
                "El PM confirmó tu entrega de \"" + actividad.getTitulo() + "\".",
                "ACTIVIDAD", actividad.getId());
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
        penalizacionService.verificarYNotificarPorStrikes(actividad.getColaborador(), actividad.getProyecto());

        notificacionService.crear(actividad.getColaborador(), "ACTIVIDAD_DEVUELTA", CategoriaNotificacion.ACTIVIDAD,
                "Actividad devuelta",
                "El PM devolvió \"" + actividad.getTitulo() + "\": " + comentario,
                "ACTIVIDAD", actividad.getId());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    @Transactional
    public Actividad editar(Long actividadId, String titulo, String descripcion,
                            BigDecimal horasEstimadas, LocalDate fechaLimite,
                            Usuario pm) {
        Actividad actividad = obtenerActividadDelPm(actividadId, pm);

        if (actividad.getEstado() == com.pucp.skillb_ia.model.enums.EstadoActividad.COMPLETADA) {
            throw new IllegalStateException("No se puede editar una actividad completada.");
        }

        actividad.setTitulo(titulo);
        actividad.setDescripcion(descripcion);
        actividad.setHorasEstimadas(horasEstimadas);
        actividad.setFechaLimite(fechaLimite);

        Actividad saved = actividadRepository.save(actividad);

        auditoriaService.registrar(pm, "EDITAR", "ACTIVIDAD", saved.getId(),
                "PM editó la actividad '" + titulo + "'.");

        notificacionService.crear(actividad.getColaborador(), "ACTIVIDAD_EDITADA", com.pucp.skillb_ia.model.enums.CategoriaNotificacion.ACTIVIDAD,
                "Actividad modificada",
                "El PM ha modificado los detalles de la actividad \"" + titulo + "\".",
                "ACTIVIDAD", saved.getId());

        return saved;
    }

    @Transactional
    public void eliminar(Long actividadId, Usuario pm) {
        Actividad actividad = obtenerActividadDelPm(actividadId, pm);

        if (actividad.getEstado() == com.pucp.skillb_ia.model.enums.EstadoActividad.COMPLETADA || actividad.getEstado() == com.pucp.skillb_ia.model.enums.EstadoActividad.EN_REVISION) {
            throw new IllegalStateException("No se puede eliminar una actividad que ya fue entregada o completada.");
        }

        String titulo = actividad.getTitulo();

        actividadRepository.delete(actividad);

        auditoriaService.registrar(pm, "ELIMINAR", "ACTIVIDAD", actividadId,
                "PM eliminó la actividad '" + titulo + "'.");

        notificacionService.crear(actividad.getColaborador(), "ACTIVIDAD_ELIMINADA", com.pucp.skillb_ia.model.enums.CategoriaNotificacion.ACTIVIDAD,
                "Actividad eliminada",
                "El PM ha eliminado la actividad \"" + titulo + "\".",
                "ACTIVIDAD", null);
    }

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