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
import com.pucp.skillb_ia.service.NotificacionService;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.service.rm.RmResponsableService;
import org.springframework.stereotype.Service;
import com.pucp.skillb_ia.service.ChatBaseService;
import org.springframework.web.multipart.MultipartFile;
import com.pucp.skillb_ia.service.ArchivoAlmacenamientoService;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Servicio de administración de proyectos del Project Manager: listado, consulta detallada con métricas,
 * creación con habilidades requeridas y documento de contexto, y cancelación con cierre transaccional de asignaciones.
 */
@Service
public class PmProyectoService {

    private final ProyectoRepository proyectoRepository;
    private final AsignacionRepository asignacionRepository;
    private final ActividadRepository actividadRepository;
    private final ProyectoHabilidadRequeridaRepository habilidadRequeridaRepository;
    private final HabilidadRepository habilidadRepository;
    private final AuditoriaService auditoriaService;
    private final CierreAsignacionesService cierreAsignacionesService;
    private final NotificacionService notificacionService;
    private final ChatBaseService chatBaseService;
    private final RmResponsableService rmResponsableService;
    private final ArchivoAlmacenamientoService archivoAlmacenamientoService;

    private static final long TAMANO_MAXIMO_DOCUMENTO_CONTEXTO = 20L * 1024 * 1024;
    private static final Set<String> EXTENSIONES_DOCUMENTO_CONTEXTO =
            Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "png", "jpg", "jpeg");


    public PmProyectoService(ProyectoRepository proyectoRepository,
                             AsignacionRepository asignacionRepository,
                             ActividadRepository actividadRepository,
                             ProyectoHabilidadRequeridaRepository habilidadRequeridaRepository,
                             HabilidadRepository habilidadRepository,
                             AuditoriaService auditoriaService,
                             CierreAsignacionesService cierreAsignacionesService,
                             NotificacionService notificacionService,
                             ChatBaseService chatBaseService,
                             RmResponsableService rmResponsableService,
                             ArchivoAlmacenamientoService archivoAlmacenamientoService) {
        this.archivoAlmacenamientoService = archivoAlmacenamientoService;
        this.chatBaseService = chatBaseService;
        this.rmResponsableService = rmResponsableService;
        this.notificacionService = notificacionService;
        this.proyectoRepository = proyectoRepository;
        this.asignacionRepository = asignacionRepository;
        this.actividadRepository = actividadRepository;
        this.habilidadRequeridaRepository = habilidadRequeridaRepository;
        this.habilidadRepository = habilidadRepository;
        this.auditoriaService = auditoriaService;
        this.cierreAsignacionesService = cierreAsignacionesService;
    }

    /** Lista todos los proyectos del PM con sus métricas resumidas de equipo y actividades. */
    @Transactional(readOnly = true)
    public List<PmProyectoView> listar(Usuario pm) {
        return proyectoRepository.findByPmOrderByFechaCreacionDesc(pm)
                .stream()
                .map(p -> construirVista(p, pm))
                .toList();
    }

    /** Obtiene la vista detallada de un proyecto del PM verificando su propiedad. */
    @Transactional(readOnly = true)
    public PmProyectoView obtener(Long proyectoId, Usuario pm) {
        Proyecto proyecto = proyectoRepository.findByIdConPm(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));
        verificarPropietario(proyecto, pm);
        return construirVista(proyecto, pm);
    }

    /** Crea un proyecto en estado EN_REVISION, inicializa su chat grupal, asigna RM responsable y guarda habilidades requeridas. */
    @Transactional
    public Proyecto crear(String nombre, String descripcion,
                          LocalDate fechaInicio, LocalDate fechaFinEstimada,
                          String prioridadStr, String justificacionPrioridad,
                          BigDecimal presupuestoSolicitado, String justificacionPresupuesto,
                          int colaboradoresRequeridos, BigDecimal horasSemanalesRequeridas,
                          List<Long> habilidadIds, List<String> nivelesRequeridos,
                          List<Integer> cantidadesPersonas, List<BigDecimal> horasSemanalesHab,
                          Usuario pm) {
        
        if (fechaInicio != null && fechaFinEstimada != null && fechaFinEstimada.isBefore(fechaInicio)) {
            throw new IllegalArgumentException("La fecha de fin estimada no puede ser anterior a la fecha de inicio.");
        }
        if (horasSemanalesRequeridas != null && horasSemanalesRequeridas.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Las horas semanales requeridas deben ser mayores a cero.");
        }
        if (horasSemanalesHab != null) {
            for (BigDecimal horas : horasSemanalesHab) {
                if (horas != null && horas.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Las horas semanales por habilidad deben ser mayores a cero.");
                }
            }
        }
        if (cantidadesPersonas != null) {
            for (Integer cant : cantidadesPersonas) {
                if (cant != null && cant <= 0) {
                    throw new IllegalArgumentException("La cantidad de personas debe ser mayor a cero.");
                }
            }
        }

        Proyecto proyecto = new Proyecto();
        proyecto.setNombre(nombre);
        proyecto.setDescripcion(descripcion);
        proyecto.setFechaInicio(fechaInicio);
        proyecto.setFechaFinEstimada(fechaFinEstimada);
        proyecto.setPrioridad(Prioridad.valueOf(prioridadStr));
        proyecto.setJustificacionPrioridad(justificacionPrioridad);
        proyecto.setPresupuestoSolicitado(presupuestoSolicitado != null ? presupuestoSolicitado : BigDecimal.ZERO);
        proyecto.setJustificacionPresupuesto(justificacionPresupuesto);
        proyecto.setColaboradoresRequeridos(colaboradoresRequeridos);
        proyecto.setHorasSemanalesRequeridas(horasSemanalesRequeridas != null
                ? horasSemanalesRequeridas : BigDecimal.valueOf(20));
        proyecto.setPm(pm);
        proyecto.setEstado(EstadoProyecto.EN_REVISION);
        Proyecto saved = proyectoRepository.save(proyecto);
        chatBaseService.obtenerOCrearGrupal(saved);
        // Asigna el RM responsable (menos cargado; si el proyecto es urgente, ignora el tope)
        rmResponsableService.asignarAlCrear(saved);

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
                if (horasSemanalesHab != null && i < horasSemanalesHab.size() && horasSemanalesHab.get(i) != null) {
                    phr.setHorasSemanales(horasSemanalesHab.get(i));
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
    /** Cancela un proyecto EN_REVISION o EN_ESPERA sin motivo explícito y cierra sus asignaciones abiertas. */
    @Transactional
    public CierreAsignacionesService.Resultado cancelar(Long proyectoId, Usuario pm) {
        return cancelar(proyectoId, null, pm);
    }

    /** Cancela un proyecto EN_REVISION o EN_ESPERA guardando el motivo y cerrando sus asignaciones abiertas. */
    @Transactional
    public CierreAsignacionesService.Resultado cancelar(Long proyectoId, String motivo, Usuario pm) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));
        verificarPropietario(proyecto, pm);

        if (proyecto.getEstado() != EstadoProyecto.EN_REVISION
                && proyecto.getEstado() != EstadoProyecto.EN_ESPERA) {
            throw new IllegalStateException(
                    "Solo se pueden cancelar proyectos en estado EN_REVISION o EN_ESPERA.");
        }
        proyecto.setEstado(EstadoProyecto.CANCELADO);
        if (motivo != null && !motivo.isBlank()) {
            proyecto.setMotivoCierre(motivo.trim());
        }
        proyectoRepository.save(proyecto);

        //El PM que cancela no recibe notificación; solo ve el mensaje en pantalla.
        CierreAsignacionesService.Resultado resultado = cierreAsignacionesService.cerrarAbiertas(
                proyecto, pm, "cancelado", MotivoFinalizacion.PROYECTO_CANCELADO);

        auditoriaService.registrar(pm, "CANCELAR", "PROYECTO", proyectoId,
                "PM canceló el proyecto '" + proyecto.getNombre() + "'. Asignaciones pendientes rechazadas: "
                        + resultado.pendientesRechazadas() + ". Asignaciones activas finalizadas: "
                        + resultado.activasFinalizadas() + ".");
        // Por diseño (PmProyectoCancelacionTests) los RM no reciben notificación al cancelar:
        // el proyecto aún estaba en revisión y la acción queda en la auditoría.
        return resultado;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private PmProyectoView construirVista(Proyecto proyecto, Usuario pm) {
        long activos = asignacionRepository.countByProyectoAndEstado(
                proyecto, EstadoAsignacion.ACTIVA);
        long pendientesPm = asignacionRepository.findPendientesPmByProyecto(proyecto).size();
        long total = actividadRepository.countByProyectoAndEstadoAndActivoTrue(
                proyecto, EstadoActividad.PENDIENTE)
                + actividadRepository.countByProyectoAndEstadoAndActivoTrue(proyecto, EstadoActividad.EN_PROGRESO)
                + actividadRepository.countByProyectoAndEstadoAndActivoTrue(proyecto, EstadoActividad.EN_REVISION)
                + actividadRepository.countByProyectoAndEstadoAndActivoTrue(proyecto, EstadoActividad.COMPLETADA);
        long completadas = actividadRepository.countByProyectoAndEstadoAndActivoTrue(
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

    /** Devuelve el mensaje de error del documento de contexto, o null si es válido o no se adjuntó. */
    public String validarDocumentoContexto(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) return null;
        if (archivo.getSize() > TAMANO_MAXIMO_DOCUMENTO_CONTEXTO) {
            return "El documento de contexto no puede superar los 20 MB.";
        }
        String extension = extensionDe(archivo.getOriginalFilename());
        if (!EXTENSIONES_DOCUMENTO_CONTEXTO.contains(extension)) {
            return "Tipo de documento no permitido. Formatos aceptados: PDF, Word, Excel, PowerPoint, TXT e imágenes.";
        }
        return null;
    }

    /** Guarda el documento de contexto que adjunta el PM al crear el proyecto (lo consulta el RM). */
    @Transactional
    public void guardarDocumentoContexto(Long proyectoId, MultipartFile archivo, Usuario pm) {
        if (archivo == null || archivo.isEmpty()) return;
        String error = validarDocumentoContexto(archivo);
        if (error != null) throw new IllegalArgumentException(error);
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));
        verificarPropietario(proyecto, pm);
        String nombreArchivo = "contexto-" + java.util.UUID.randomUUID() + "." + extensionDe(archivo.getOriginalFilename());
        String url = archivoAlmacenamientoService.guardar(archivo, "documentos/proyecto-" + proyecto.getId(), nombreArchivo);
        proyecto.setDocumentoContextoUrl(url);
        proyectoRepository.save(proyecto);
    }

    private static String extensionDe(String nombre) {
        if (nombre == null) return "";
        int punto = nombre.lastIndexOf('.');
        return punto < 0 || punto == nombre.length() - 1 ? "" : nombre.substring(punto + 1).toLowerCase();
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
