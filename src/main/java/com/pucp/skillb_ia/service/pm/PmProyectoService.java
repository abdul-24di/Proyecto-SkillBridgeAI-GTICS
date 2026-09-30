package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.dto.PmProyectoView;
import com.pucp.skillb_ia.model.Habilidad;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.ProyectoHabilidadRequerida;
import com.pucp.skillb_ia.model.ProyectoHabilidadRequeridaId;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.MotivoFinalizacion;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.ActividadRepository;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.HabilidadRepository;
import com.pucp.skillb_ia.repository.ProyectoHabilidadRequeridaRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.CierreAsignacionesService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.pucp.skillb_ia.service.ArchivoAlmacenamientoService;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class PmProyectoService {

    private final ProyectoRepository proyectoRepository;
    private final AsignacionRepository asignacionRepository;
    private final ActividadRepository actividadRepository;
    private final ProyectoHabilidadRequeridaRepository habilidadRequeridaRepository;
    private final HabilidadRepository habilidadRepository;
    private final AuditoriaService auditoriaService;
    private final CierreAsignacionesService cierreAsignacionesService;

    public PmProyectoService(ProyectoRepository proyectoRepository,
                             AsignacionRepository asignacionRepository,
                             ActividadRepository actividadRepository,
                             ProyectoHabilidadRequeridaRepository habilidadRequeridaRepository,
                             HabilidadRepository habilidadRepository,
                             AuditoriaService auditoriaService,
                             CierreAsignacionesService cierreAsignacionesService) {
        this.proyectoRepository = proyectoRepository;
        this.asignacionRepository = asignacionRepository;
        this.actividadRepository = actividadRepository;
        this.habilidadRequeridaRepository = habilidadRequeridaRepository;
        this.habilidadRepository = habilidadRepository;
        this.auditoriaService = auditoriaService;
        this.cierreAsignacionesService = cierreAsignacionesService;
    }

    @Transactional(readOnly = true)
    public List<PmProyectoView> listar(Usuario pm) {
        return proyectoRepository.findByPmOrderByFechaCreacionDesc(pm)
                .stream()
                .map(p -> construirVista(p, pm))
                .toList();
    }

    @Transactional(readOnly = true)
    public PmProyectoView obtener(Long proyectoId, Usuario pm) {
        Proyecto proyecto = proyectoRepository.findByIdConPm(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));
        verificarPropietario(proyecto, pm);
        return construirVista(proyecto, pm);
    }

    @Transactional
    public Proyecto crear(String nombre, String descripcion,
                          LocalDate fechaInicio, LocalDate fechaFinEstimada,
                          String prioridadStr, String justificacionPrioridad,
                          BigDecimal presupuestoSolicitado, String justificacionPresupuesto,
                          int colaboradoresRequeridos, BigDecimal horasSemanalesRequeridas,
                          List<Long> habilidadIds, List<String> nivelesRequeridos,
                          List<Integer> cantidadesPersonas,
                          Usuario pm) {
        
        if (fechaInicio != null && fechaFinEstimada != null && fechaFinEstimada.isBefore(fechaInicio)) {
            throw new IllegalArgumentException("La fecha de fin estimada no puede ser anterior a la fecha de inicio.");
        }

        Proyecto proyecto = new Proyecto();
        proyecto.setNombre(nombre);
        proyecto.setDescripcion(descripcion);
        proyecto.setFechaInicio(fechaInicio);
        proyecto.setFechaFinEstimada(fechaFinEstimada);
        proyecto.setPrioridad(Prioridad.valueOf(prioridadStr));
        proyecto.setJustificacionPrioridad(justificacionPrioridad);
        proyecto.setPresupuestoSolicitado(presupuestoSolicitado);
        proyecto.setJustificacionPresupuesto(justificacionPresupuesto);
        proyecto.setColaboradoresRequeridos(colaboradoresRequeridos);
        proyecto.setHorasSemanalesRequeridas(horasSemanalesRequeridas != null
                ? horasSemanalesRequeridas : BigDecimal.valueOf(20));
        proyecto.setPm(pm);
        proyecto.setEstado(EstadoProyecto.EN_REVISION);
        Proyecto saved = proyectoRepository.save(proyecto);

        // Guardar habilidades requeridas
        if (habilidadIds != null) {
            for (int i = 0; i < habilidadIds.size(); i++) {
                Long habilidadId = habilidadIds.get(i);
                Habilidad hab = habilidadRepository.findById(habilidadId)
                        .orElseThrow(() -> new IllegalArgumentException("Habilidad no encontrada."));
                ProyectoHabilidadRequerida phr = new ProyectoHabilidadRequerida();
                phr.setProyecto(saved);
                phr.setHabilidad(hab);
                if (nivelesRequeridos != null && i < nivelesRequeridos.size()
                        && nivelesRequeridos.get(i) != null && !nivelesRequeridos.get(i).isBlank()) {
                    phr.setNivelRequerido(
                        com.pucp.skillb_ia.model.enums.NivelDominio.valueOf(nivelesRequeridos.get(i)));
                }
                if (cantidadesPersonas != null && i < cantidadesPersonas.size()
                        && cantidadesPersonas.get(i) != null) {
                    phr.setCantidadPersonas(cantidadesPersonas.get(i));
                }
                habilidadRequeridaRepository.save(phr);
            }
        }

        auditoriaService.registrar(pm, "CREAR", "PROYECTO", saved.getId(),
                "PM creó el proyecto '" + nombre + "' en estado EN_REVISION.");
        return saved;
    }

    //Cancelamos el proyecto y, en la misma transacción, cerramos sus asignaciones abiertas:
    //las PENDIENTE pasan a RECHAZADA y las ACTIVA a FINALIZADA (sin strike ni penalización).
    //Si algo falla, se revierte todo y el proyecto no queda cancelado.
    @Transactional
    public CierreAsignacionesService.Resultado cancelar(Long proyectoId, Usuario pm) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));
        verificarPropietario(proyecto, pm);

        if (proyecto.getEstado() != EstadoProyecto.EN_REVISION
                && proyecto.getEstado() != EstadoProyecto.EN_ESPERA) {
            throw new IllegalStateException(
                    "Solo se pueden cancelar proyectos en estado EN_REVISION o EN_ESPERA.");
        }
        proyecto.setEstado(EstadoProyecto.CANCELADO);
        proyectoRepository.save(proyecto);

        //El PM que cancela no recibe notificación; solo ve el mensaje en pantalla.
        CierreAsignacionesService.Resultado resultado = cierreAsignacionesService.cerrarAbiertas(
                proyecto, pm, "cancelado", MotivoFinalizacion.PROYECTO_CANCELADO);

        auditoriaService.registrar(pm, "CANCELAR", "PROYECTO", proyectoId,
                "PM canceló el proyecto '" + proyecto.getNombre() + "'. Asignaciones pendientes rechazadas: "
                        + resultado.pendientesRechazadas() + ". Asignaciones activas finalizadas: "
                        + resultado.activasFinalizadas() + ".");
        return resultado;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private PmProyectoView construirVista(Proyecto proyecto, Usuario pm) {
        long activos = asignacionRepository.countByProyectoAndEstado(
                proyecto, EstadoAsignacion.ACTIVA);
        long pendientesPm = asignacionRepository.findPendientesPmByProyecto(proyecto).size();
        long total = actividadRepository.countByProyectoAndEstado(
                proyecto, EstadoActividad.PENDIENTE)
                + actividadRepository.countByProyectoAndEstado(proyecto, EstadoActividad.EN_PROGRESO)
                + actividadRepository.countByProyectoAndEstado(proyecto, EstadoActividad.EN_REVISION)
                + actividadRepository.countByProyectoAndEstado(proyecto, EstadoActividad.COMPLETADA);
        long completadas = actividadRepository.countByProyectoAndEstado(
                proyecto, EstadoActividad.COMPLETADA);

        List<ProyectoHabilidadRequerida> habs = habilidadRequeridaRepository.findByProyecto(proyecto);
        List<PmProyectoView.RequisitoHabilidad> requisitos = habs.stream()
                .map(h -> new PmProyectoView.RequisitoHabilidad(
                        h.getHabilidad().getId(),
                        h.getHabilidad().getNombre(),
                        h.getNivelRequerido() != null ? h.getNivelRequerido().name() : "Cualquiera",
                        h.getCantidadPersonas(),
                          h.getHorasSemanales()))
                .toList();

        return new PmProyectoView(
                proyecto,
                traducirEstado(proyecto.getEstado()),
                proyecto.getPrioridad().name(),
                (int) activos,
                (int) pendientesPm,
                (int) total,
                (int) completadas,
                requisitos);
    }

    private static void verificarPropietario(Proyecto proyecto, Usuario pm) {
        if (!proyecto.getPm().getId().equals(pm.getId())) {
            throw new SecurityException("No tienes permiso para acceder a este proyecto.");
        }
    }

    private static String traducirEstado(EstadoProyecto estado) {
        return switch (estado) {
            case EN_REVISION -> "En revisión";
            case ACTIVO      -> "Activo";
            case RECHAZADO   -> "Rechazado";
            case EN_ESPERA   -> "En espera";
            case CANCELADO   -> "Cancelado";
            case FINALIZADO  -> "Finalizado";
        };
    }
}
