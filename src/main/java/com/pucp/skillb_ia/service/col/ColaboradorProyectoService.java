package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.dto.ColHistorialProyectoView;
import com.pucp.skillb_ia.dto.ColPerfilRequeridoView;
import com.pucp.skillb_ia.dto.ColProyectoDetalleView;
import com.pucp.skillb_ia.dto.ColProyectoDisponibleView;
import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.model.enums.NivelDominio;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.service.AuditoriaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ColaboradorProyectoService {

    private static final Set<String> TIPOS_EVIDENCIA_PERMITIDOS =
            Set.of("application/pdf", "image/jpeg", "image/png");
    private static final long TAMANO_MAXIMO_EVIDENCIA_BYTES = 10L * 1024 * 1024; // 10MB

    private final ProyectoRepository proyectoRepository;
    private final AsignacionRepository asignacionRepository;
    private final ProyectoHabilidadRequeridaRepository proyectoHabilidadRequeridaRepository;
    private final HabilidadRepository habilidadRepository;
    private final ActividadRepository actividadRepository;
    private final AuditoriaService auditoriaService;
    private final com.pucp.skillb_ia.service.PenalizacionService penalizacionService;
    private final String uploadDir;

    public ColaboradorProyectoService(ProyectoRepository proyectoRepository,
                                      AsignacionRepository asignacionRepository,
                                      ProyectoHabilidadRequeridaRepository proyectoHabilidadRequeridaRepository,
                                      HabilidadRepository habilidadRepository,
                                      ActividadRepository actividadRepository,
                                      AuditoriaService auditoriaService,
                                      com.pucp.skillb_ia.service.PenalizacionService penalizacionService,
                                      @Value("${app.upload-dir:uploads}") String uploadDir) {
        this.proyectoRepository = proyectoRepository;
        this.asignacionRepository = asignacionRepository;
        this.proyectoHabilidadRequeridaRepository = proyectoHabilidadRequeridaRepository;
        this.habilidadRepository = habilidadRepository;
        this.actividadRepository = actividadRepository;
        this.auditoriaService = auditoriaService;
        this.penalizacionService = penalizacionService;
        this.uploadDir = uploadDir;
    }

    // ============================================================
    // CONSULTA DE PROYECTOS
    // ============================================================

    public List<ColProyectoDisponibleView> listarTodosLosProyectos(Usuario colaborador) {
        List<Proyecto> todosLosProyectos = proyectoRepository.findAllConPmOrderByFechaCreacionDesc();
        List<ColProyectoDisponibleView> vistas = new ArrayList<>();

        for (Proyecto proyecto : todosLosProyectos) {
            if (proyecto.getEstado() == EstadoProyecto.RECHAZADO) {
                continue;
            }
            vistas.add(construirVista(proyecto, colaborador));
        }
        return vistas;
    }

    private ColProyectoDisponibleView construirVista(Proyecto proyecto, Usuario colaborador) {
        List<Asignacion> asignacionesDelProyecto = asignacionRepository.findByProyecto(proyecto);

        int activos = 0;
        boolean yaTieneSolicitud = false;

        for (Asignacion asignacion : asignacionesDelProyecto) {
            if (asignacion.getEstado() == EstadoAsignacion.ACTIVA) {
                activos++;
            }

            boolean esDeEsteColaborador = asignacion.getColaborador().getId().equals(colaborador.getId());
            boolean estaPendienteOActiva = asignacion.getEstado() == EstadoAsignacion.PENDIENTE
                    || asignacion.getEstado() == EstadoAsignacion.ACTIVA;

            if (esDeEsteColaborador && estaPendienteOActiva) {
                yaTieneSolicitud = true;
            }
        }


        List<ProyectoHabilidadRequerida> requeridas = proyectoHabilidadRequeridaRepository.findByProyecto(proyecto);
        List<ColPerfilRequeridoView> perfiles = new ArrayList<>();
        boolean algunPerfilConCupo = false;

        for (ProyectoHabilidadRequerida requerida : requeridas) {
            int ocupadosDeEstePerfil = 0;
            for (Asignacion asignacion : asignacionesDelProyecto) {
                boolean esActiva = asignacion.getEstado() == EstadoAsignacion.ACTIVA;
                boolean esDeEstaHabilidad = asignacion.getHabilidadSolicitada() != null
                        && asignacion.getHabilidadSolicitada().getId().equals(requerida.getHabilidad().getId());
                if (esActiva && esDeEstaHabilidad) {
                    ocupadosDeEstePerfil++;
                }
            }

            int cuposPerfil = requerida.getCantidadPersonas() - ocupadosDeEstePerfil;
            if (cuposPerfil > 0) {
                algunPerfilConCupo = true;
            }

            String nivel = requerida.getNivelRequerido() != null ? nombreNivel(requerida.getNivelRequerido()) : null;
            String etiqueta = requerida.getHabilidad().getNombre() + (nivel != null ? " (" + nivel + ")" : "");
            perfiles.add(new ColPerfilRequeridoView(requerida.getHabilidad().getId(), etiqueta, cuposPerfil));
        }

        int cupos = proyecto.getColaboradoresRequeridos() - activos;
        boolean tieneHorasSuficientes = tieneHorasSuficientes(colaborador, proyecto);

        // Postulaciones abiertas = proyecto activo Y al menos un perfil con cupo.
        boolean postulacionesAbiertas = proyecto.getEstado() == EstadoProyecto.ACTIVO && algunPerfilConCupo;

        return new ColProyectoDisponibleView(proyecto, activos, cupos, perfiles, yaTieneSolicitud,
                tieneHorasSuficientes, postulacionesAbiertas);
    }

    private String nombreNivel(NivelDominio nivel) {
        if (nivel == NivelDominio.AVANZADO) return "Avanzado";
        if (nivel == NivelDominio.INTERMEDIO) return "Intermedio";
        return "Básico";
    }

    private boolean tieneHorasSuficientes(Usuario colaborador, Proyecto proyecto) {
        BigDecimal disponibles = colaborador.getHorasDisponibles();
        if (disponibles == null) {
            return false;
        }
        return disponibles.compareTo(proyecto.getHorasSemanalesRequeridas()) >= 0;
    }

    public List<Asignacion> listarMisSolicitudes(Usuario colaborador) {
        List<Asignacion> todasMisAsignaciones = asignacionRepository.findByColaborador(colaborador);
        List<Asignacion> misSolicitudes = new ArrayList<>();

        for (Asignacion asignacion : todasMisAsignaciones) {
            if (asignacion.getOrigen() == OrigenAsignacion.SOLICITADA_COLABORADOR) {
                misSolicitudes.add(asignacion);
            }
        }

        misSolicitudes.sort(Comparator.comparing(Asignacion::getFechaSolicitud).reversed());
        return misSolicitudes;
    }

    public List<Asignacion> listarMisAsignaciones(Usuario colaborador) {
        List<Asignacion> encontradas = asignacionRepository.findByColaborador(colaborador);
        List<Asignacion> misAsignaciones = new ArrayList<>(encontradas);
        misAsignaciones.sort(Comparator.comparing(Asignacion::getFechaSolicitud).reversed());
        return misAsignaciones;
    }

    // ============================================================
    // SOLICITAR INCORPORACIÓN A UN PERFIL ESPECÍFICO DEL PROYECTO
    // ============================================================
    @Transactional
    public void solicitarIncorporacion(Usuario colaborador, Long proyectoId, Long habilidadId,
                                       String mensaje, String habilidadesRelevantes) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("El proyecto seleccionado no existe."));

        if (proyecto.getEstado() != EstadoProyecto.ACTIVO) {
            throw new IllegalArgumentException("Este proyecto ya no está activo.");
        }
        if (habilidadId == null) {
            throw new IllegalArgumentException("Selecciona el perfil al que deseas postularte.");
        }
        if (!tieneHorasSuficientes(colaborador, proyecto)) {
            throw new IllegalArgumentException("No tienes suficientes horas disponibles para este proyecto. Se requieren "
                    + proyecto.getHorasSemanalesRequeridas() + " horas/semana.");
        }

        Habilidad habilidad = habilidadRepository.findById(habilidadId)
                .orElseThrow(() -> new IllegalArgumentException("El perfil seleccionado no es válido."));

        ProyectoHabilidadRequerida requerida = proyectoHabilidadRequeridaRepository
                .findByProyectoAndHabilidad(proyecto, habilidad)
                .orElseThrow(() -> new IllegalArgumentException("Ese perfil no pertenece a este proyecto."));

        List<Asignacion> asignacionesDelProyecto = asignacionRepository.findByProyecto(proyecto);

        int ocupadosDeEsePerfil = 0;
        boolean yaTieneSolicitud = false;

        for (Asignacion asignacion : asignacionesDelProyecto) {
            boolean esDeEsaHabilidad = asignacion.getHabilidadSolicitada() != null
                    && asignacion.getHabilidadSolicitada().getId().equals(habilidadId);
            if (asignacion.getEstado() == EstadoAsignacion.ACTIVA && esDeEsaHabilidad) {
                ocupadosDeEsePerfil++;
            }

            boolean esDeEsteColaborador = asignacion.getColaborador().getId().equals(colaborador.getId());
            boolean estaPendienteOActiva = asignacion.getEstado() == EstadoAsignacion.PENDIENTE
                    || asignacion.getEstado() == EstadoAsignacion.ACTIVA;
            if (esDeEsteColaborador && estaPendienteOActiva) {
                yaTieneSolicitud = true;
            }
        }

        if (ocupadosDeEsePerfil >= requerida.getCantidadPersonas()) {
            throw new IllegalArgumentException("Ese perfil ya no tiene cupos disponibles.");
        }
        if (yaTieneSolicitud) {
            throw new IllegalArgumentException("Ya tienes una solicitud pendiente o una asignación activa en este proyecto.");
        }

        Asignacion asignacion = new Asignacion();
        asignacion.setProyecto(proyecto);
        asignacion.setColaborador(colaborador);
        asignacion.setHorasSemanales(proyecto.getHorasSemanalesRequeridas());
        asignacion.setOrigen(OrigenAsignacion.SOLICITADA_COLABORADOR);
        asignacion.setHabilidadSolicitada(habilidad);
        asignacion.setHabilidadesRelevantes(habilidadesRelevantes == null || habilidadesRelevantes.isBlank()
                ? null : habilidadesRelevantes.trim());
        asignacion.setMensajeSolicitud(mensaje == null || mensaje.isBlank() ? null : mensaje.trim());
        Asignacion guardada = asignacionRepository.save(asignacion);

        auditoriaService.registrar(colaborador, "SOLICITAR_ASIGNACION", "ASIGNACION", guardada.getId(),
                "Solicitó incorporarse al proyecto \"" + proyecto.getNombre() + "\" para el perfil \""
                        + habilidad.getNombre() + "\".");
    }

    // ============================================================
    // HISTORIAL DE PROYECTOS FINALIZADOS (Perfil Profesional)
    // ============================================================
    public List<ColHistorialProyectoView> listarHistorialProyectos(Usuario colaborador) {
        List<Asignacion> finalizadas = asignacionRepository
                .findByColaboradorAndEstadoOrderByFechaFinalizacionDesc(colaborador, EstadoAsignacion.FINALIZADA);

        List<ColHistorialProyectoView> historial = new ArrayList<>();
        for (Asignacion asignacion : finalizadas) {
            Proyecto proyecto = asignacion.getProyecto();

            List<ProyectoHabilidadRequerida> requeridas = proyectoHabilidadRequeridaRepository.findByProyecto(proyecto);
            List<String> habilidades = new ArrayList<>();
            for (ProyectoHabilidadRequerida requerida : requeridas) {
                habilidades.add(requerida.getHabilidad().getNombre());
            }

            historial.add(new ColHistorialProyectoView(proyecto, asignacion, habilidades));
        }
        return historial;
    }

    // ============================================================
    // DETALLE DE UN PROYECTO (Resumen/ Integrantes/ Actividades)
    // ============================================================
    public ColProyectoDetalleView obtenerDetalleProyecto(Usuario colaborador, Long asignacionId) {
        Asignacion asignacion = asignacionRepository.findByIdConDetalle(asignacionId)
                .orElseThrow(() -> new IllegalArgumentException("El proyecto que buscas no existe."));

        if (!asignacion.getColaborador().getId().equals(colaborador.getId())) {
            throw new IllegalArgumentException("No tienes acceso a este proyecto.");
        }

        boolean esActiva = asignacion.getEstado() == EstadoAsignacion.ACTIVA;
        boolean esFinalizada = asignacion.getEstado() == EstadoAsignacion.FINALIZADA;

        if (!esActiva && !esFinalizada) {
            throw new IllegalArgumentException("Todavía no tienes acceso a los detalles de este proyecto.");
        }

        Proyecto proyecto = asignacion.getProyecto();

        //Listamos a los integrantes que son colaboradores con asignación ACTIVA en ese proyecto
        List<Asignacion> integrantes = asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA);

        //Listamos las actividades del colaborador que está viendo el proyecto
        List<Actividad> todasLasActividades = actividadRepository.findByProyecto(proyecto);
        List<Actividad> misActividades = new ArrayList<>();
        for (Actividad actividad : todasLasActividades) {
            if (actividad.getColaborador().getId().equals(colaborador.getId())) {
                misActividades.add(actividad);
            }
        }

        //Revisamos si alguna actividad venció sin que la entregara.
        penalizacionService.revisarVencidasSinEntregar(misActividades);

        return new ColProyectoDetalleView(proyecto, asignacion, integrantes, todasLasActividades, misActividades);
    }

    // ============================================================
    // MARCAR ACTIVIDAD COMO "LISTO PARA REVISAR"
    // ============================================================
    @Transactional
    public void marcarActividadListaParaRevision(Usuario colaborador, Long actividadId,
                                                 MultipartFile evidencia, String comentario) {
        Actividad actividad = actividadRepository.findById(actividadId)
                .orElseThrow(() -> new IllegalArgumentException("La actividad no existe."));

        if (!actividad.getColaborador().getId().equals(colaborador.getId())) {
            throw new IllegalArgumentException("Esta actividad no te pertenece.");
        }
        if (actividad.getEstado() == EstadoActividad.EN_REVISION) {
            throw new IllegalArgumentException("Esta actividad ya está en revisión.");
        }
        if (actividad.getEstado() == EstadoActividad.COMPLETADA) {
            throw new IllegalArgumentException("Esta actividad ya fue completada.");
        }
        if (evidencia == null || evidencia.isEmpty()) {
            throw new IllegalArgumentException("Debes adjuntar una evidencia para marcar la actividad como lista.");
        }
        if (evidencia.getContentType() == null || !TIPOS_EVIDENCIA_PERMITIDOS.contains(evidencia.getContentType())) {
            throw new IllegalArgumentException("La evidencia debe estar en formato PDF, JPG o PNG.");
        }
        if (evidencia.getSize() > TAMANO_MAXIMO_EVIDENCIA_BYTES) {
            throw new IllegalArgumentException("La evidencia supera el máximo de 10MB.");
        }
        if (comentario == null || comentario.isBlank()) {
            throw new IllegalArgumentException("Debes escribir un comentario para marcar la actividad como lista.");
        }
        if (comentario.trim().length() > 300) {
            throw new IllegalArgumentException("El comentario no puede superar los 300 caracteres.");
        }

        String evidenciaUrl = guardarEvidencia(evidencia, actividad.getId());

        actividad.setEvidenciaUrl(evidenciaUrl);
        actividad.setComentarioColaborador(comentario.trim());
        actividad.setEstado(EstadoActividad.EN_REVISION);
        actividad.setFechaMarcadoRevision(LocalDateTime.now());
        actividadRepository.save(actividad);

        auditoriaService.registrar(colaborador, "MARCAR_ACTIVIDAD_LISTA", "ACTIVIDAD", actividad.getId(),
                "Marcó la actividad \"" + actividad.getTitulo() + "\" como lista para revisión.");

        // Si la entregó después de la fecha límite, es un strike. No importa sí la entregó, esta fue entregada tarde.
        if (java.time.LocalDate.now().isAfter(actividad.getFechaLimite())) {
            penalizacionService.aplicarStrikePorTardanza(actividad);
            penalizacionService.verificarYRemoverPorStrikes(colaborador, actividad.getProyecto());
        }
    }

    //Guardamos la evidencia adjuntada por el colaborador
    private String guardarEvidencia(MultipartFile archivo, Long actividadId) {
        String extension;
        if ("application/pdf".equals(archivo.getContentType())) {
            extension = ".pdf";
        } else if ("image/png".equals(archivo.getContentType())) {
            extension = ".png";
        } else {
            extension = ".jpg";
        }
        String nombreArchivo = "actividad-" + actividadId + "-" + UUID.randomUUID() + extension;

        try {
            Path carpetaDestino = Path.of(uploadDir, "evidencias-actividades");
            Files.createDirectories(carpetaDestino);
            Files.copy(archivo.getInputStream(), carpetaDestino.resolve(nombreArchivo),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo guardar la evidencia. Inténtalo nuevamente.", e);
        }

        return "/uploads/evidencias-actividades/" + nombreArchivo;
    }
}