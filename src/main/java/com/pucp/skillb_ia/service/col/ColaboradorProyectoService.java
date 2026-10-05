package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.dto.ColHistorialProyectoView;
import com.pucp.skillb_ia.dto.ColPerfilRequeridoView;
import com.pucp.skillb_ia.dto.ColProyectoDetalleView;
import com.pucp.skillb_ia.dto.ColProyectoDisponibleView;
import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.*;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.service.ArchivoAlmacenamientoService;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.NotificacionService;
import com.pucp.skillb_ia.service.PenalizacionService;
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
import java.util.*;

@Service
public class ColaboradorProyectoService {

    private static final Set<String> TIPOS_EVIDENCIA_PERMITIDOS =
            Set.of("application/pdf", "image/jpeg", "image/png");
    private static final Set<String> EXTENSIONES_EVIDENCIA_PERMITIDAS =
            Set.of("pdf", "jpg", "jpeg", "png");
    private static final long TAMANO_MAXIMO_EVIDENCIA_BYTES = 10L * 1024 * 1024; // 10MB

    private final ProyectoRepository proyectoRepository;
    private final AsignacionRepository asignacionRepository;
    private final ProyectoHabilidadRequeridaRepository proyectoHabilidadRequeridaRepository;
    private final HabilidadRepository habilidadRepository;
    private final ActividadRepository actividadRepository;
    private final AuditoriaService auditoriaService;

    private final com.pucp.skillb_ia.service.PenalizacionService penalizacionService;
    private final ArchivoAlmacenamientoService archivoAlmacenamientoService;
    private final ColaboradorExplorarService colaboradorExplorarService;
    private final NotificacionService notificacionService;
    private final ColaboradorHabilidadRepository colaboradorHabilidadRepository;

    public ColaboradorProyectoService(ProyectoRepository proyectoRepository,
                                      AsignacionRepository asignacionRepository,
                                      ProyectoHabilidadRequeridaRepository proyectoHabilidadRequeridaRepository,
                                      HabilidadRepository habilidadRepository,
                                      ActividadRepository actividadRepository,
                                      ColaboradorExplorarService colaboradorExplorarService,
                                      AuditoriaService auditoriaService,
                                      PenalizacionService penalizacionService,
                                      NotificacionService notificacionService,
                                      ArchivoAlmacenamientoService archivoAlmacenamientoService,
                                      ColaboradorHabilidadRepository colaboradorHabilidadRepository) {
        this.proyectoRepository = proyectoRepository;
        this.asignacionRepository = asignacionRepository;
        this.notificacionService = notificacionService;
        this.proyectoHabilidadRequeridaRepository = proyectoHabilidadRequeridaRepository;
        this.habilidadRepository = habilidadRepository;
        this.actividadRepository = actividadRepository;
        this.colaboradorExplorarService = colaboradorExplorarService;
        this.auditoriaService = auditoriaService;
        this.penalizacionService = penalizacionService;
        this.archivoAlmacenamientoService = archivoAlmacenamientoService;
        this.colaboradorHabilidadRepository = colaboradorHabilidadRepository;
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
        boolean algunPerfilConCupoYHorasSuficientes = false;

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


            BigDecimal horasDeEstePerfil = requerida.getHorasSemanales() != null ? requerida.getHorasSemanales() : proyecto.getHorasSemanalesRequeridas();

            if (cuposPerfil > 0 && tieneHorasSuficientes(colaborador, horasDeEstePerfil)) {
                algunPerfilConCupoYHorasSuficientes = true;
            }

            String nivel = requerida.getNivelRequerido() != null ? nombreNivel(requerida.getNivelRequerido()) : null;
            String etiqueta = requerida.getHabilidad().getNombre() + (nivel != null ? " (" + nivel + ")" : "");
            perfiles.add(new ColPerfilRequeridoView(requerida.getHabilidad().getId(), etiqueta, cuposPerfil, horasDeEstePerfil));
        }

        int cupos = proyecto.getColaboradoresRequeridos() - activos;

        // Postulaciones abiertas = proyecto activo Y al menos un perfil con cupo.
        boolean postulacionesAbiertas = proyecto.getEstado() == EstadoProyecto.ACTIVO && algunPerfilConCupo;

        return new ColProyectoDisponibleView(proyecto, activos, cupos, perfiles, yaTieneSolicitud,
                algunPerfilConCupoYHorasSuficientes, postulacionesAbiertas);
    }

    private String nombreNivel(NivelDominio nivel) {
        if (nivel == NivelDominio.AVANZADO) return "Avanzado";
        if (nivel == NivelDominio.INTERMEDIO) return "Intermedio";
        return "Básico";
    }

    private boolean tieneHorasSuficientes(Usuario colaborador, BigDecimal horasRequeridas) {
        BigDecimal disponibles = colaborador.getHorasDisponibles();
        if (disponibles == null || horasRequeridas == null) {
            return false;
        }
        return disponibles.compareTo(horasRequeridas) >= 0;
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

        Habilidad habilidad = habilidadRepository.findById(habilidadId)
                .orElseThrow(() -> new IllegalArgumentException("El perfil seleccionado no es válido."));

        ProyectoHabilidadRequerida requerida = proyectoHabilidadRequeridaRepository
                .findByProyectoAndHabilidad(proyecto, habilidad)
                .orElseThrow(() -> new IllegalArgumentException("Ese perfil no pertenece a este proyecto."));

        boolean tieneLaHabilidadValidada = false;
        for (ColaboradorHabilidad ch : colaboradorHabilidadRepository.findByColaboradorAndActivoTrue(colaborador)) {
            if (ch.getHabilidad().getId().equals(habilidadId) && ch.getEstadoValidacion() == EstadoValidacion.VALIDADA) {
                tieneLaHabilidadValidada = true;
                break;
            }
        }
        if (!tieneLaHabilidadValidada) {
            throw new IllegalArgumentException(
                    "No cuentas con la habilidad \"" + habilidad.getNombre() + "\" validada en tu perfil, así que no puedes postularte a ese perfil.");
        }

        BigDecimal horasDeEsePerfil = requerida.getHorasSemanales() != null
                ? requerida.getHorasSemanales() : proyecto.getHorasSemanalesRequeridas();
        if (!tieneHorasSuficientes(colaborador, horasDeEsePerfil)) {
            throw new IllegalArgumentException("No tienes suficientes horas disponibles para este perfil. Se requieren "
                    + horasDeEsePerfil + " horas/semana.");
        }
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
        asignacion.setHorasSemanales(horasDeEsePerfil);
        asignacion.setOrigen(OrigenAsignacion.SOLICITADA_COLABORADOR);
        asignacion.setHabilidadSolicitada(habilidad);
        asignacion.setHabilidadesRelevantes(habilidadesRelevantes == null || habilidadesRelevantes.isBlank()
                ? null : habilidadesRelevantes.trim());
        asignacion.setMensajeSolicitud(mensaje == null || mensaje.isBlank() ? null : mensaje.trim());

        Asignacion guardada = asignacionRepository.save(asignacion);

        auditoriaService.registrar(colaborador, "SOLICITAR_ASIGNACION", "ASIGNACION", guardada.getId(),
                "Solicitó incorporarse al proyecto \"" + proyecto.getNombre() + "\" para el perfil \""
                        + habilidad.getNombre() + "\".");

        String descripcionSolicitud = colaborador.getNombre() + " " + colaborador.getApellido() + " solicitó incorporarse al proyecto \"" + proyecto.getNombre() + "\".";

        notificacionService.crear(proyecto.getPm(), "SOLICITUD_INCORPORACION", CategoriaNotificacion.ASIGNACION,
                "Solicitud de incorporación pendiente",
                descripcionSolicitud, "ASIGNACION", guardada.getId());

        notificacionService.crearParaTodosLosRm("SOLICITUD_INCORPORACION", CategoriaNotificacion.ASIGNACION,
                "Solicitud de incorporación pendiente",
                descripcionSolicitud, "ASIGNACION", guardada.getId());

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


    public static final int TAMANIO_PAGINA_ACTIVIDADES = 5;

    // ============================================================
    // DETALLE DE UN PROYECTO (Resumen/ Integrantes/ Actividades)
    // ============================================================
    public ColProyectoDetalleView obtenerDetalleProyecto(Usuario colaborador, Long asignacionId,
                                                         String paginaTodas, String paginaMias) {
        Asignacion asignacion = asignacionRepository.findByIdConDetalle(asignacionId)
                .orElseThrow(() -> new IllegalArgumentException("El proyecto que buscas no existe."));

        if (!asignacion.getColaborador().getId().equals(colaborador.getId())) {
            throw new IllegalArgumentException("No tienes acceso a este proyecto.");
        }

        boolean esActiva = asignacion.getEstado() == EstadoAsignacion.ACTIVA;
        boolean esFinalizadaNormal = asignacion.getEstado() == EstadoAsignacion.FINALIZADA
                && asignacion.getMotivoFinalizacion() != com.pucp.skillb_ia.model.enums.MotivoFinalizacion.BAJO_DESEMPENO;

        if (!esActiva && !esFinalizadaNormal) {
            throw new IllegalArgumentException("Ya no tienes acceso a este proyecto: fuiste removido por bajo desempeño.");
        }

        Proyecto proyecto = asignacion.getProyecto();

        //Listamos a los integrantes que son colaboradores con asignación ACTIVA en ese proyecto
        List<Asignacion> integrantes = asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA);

        //Listamos las actividades del colaborador que está viendo el proyecto
        List<Actividad> todasLasActividades = actividadRepository.findByProyectoAndActivoTrue(proyecto);
        List<Actividad> misActividades = new ArrayList<>();
        for (Actividad actividad : todasLasActividades) {
            if (actividad.getColaborador().getId().equals(colaborador.getId())) {
                misActividades.add(actividad);
            }
        }

        //Revisamos si alguna actividad venció sin que la entregara.
        penalizacionService.revisarVencidasSinEntregar(misActividades);

        //Armamos el perfil público (habilidades, experiencia, educación) del PM y de cada integrante para mostrarlo.
        List<Usuario> personasDelProyecto = new ArrayList<>();
        personasDelProyecto.add(proyecto.getPm());
        for (Asignacion miembro : integrantes) {
            personasDelProyecto.add(miembro.getColaborador());
        }


        Map<Long, ColaboradorExplorarService.PerfilExplorar> perfilesIntegrantes =
                colaboradorExplorarService.obtenerPerfiles(personasDelProyecto);

        int misStrikes = penalizacionService.contarStrikes(colaborador, proyecto);

        List<Actividad> todasOrdenadas = new ArrayList<>(todasLasActividades);

        todasOrdenadas.sort(Comparator.comparing(Actividad::getFechaAsignacion).reversed());

        int totalPaginasTodas = totalPaginasActividades(todasOrdenadas.size());

        int paginaActualTodas = paginaActualActividades(paginaTodas, totalPaginasTodas);

        List<Actividad> actividadesDelProyectoPagina = recortarActividades(todasOrdenadas, paginaActualTodas);

        List<Actividad> misActividadesOrdenadas = new ArrayList<>(misActividades);

        misActividadesOrdenadas.sort(Comparator.comparing(Actividad::getFechaAsignacion).reversed());

        int totalPaginasMias = totalPaginasActividades(misActividadesOrdenadas.size());

        int paginaActualMias = paginaActualActividades(paginaMias, totalPaginasMias);

        List<Actividad> misActividadesPagina = recortarActividades(misActividadesOrdenadas, paginaActualMias);

        return new ColProyectoDetalleView(proyecto, asignacion, integrantes,
                actividadesDelProyectoPagina, misActividadesPagina, perfilesIntegrantes, misStrikes,
                paginaActualTodas, totalPaginasTodas, todasOrdenadas.size(),
                paginaActualMias, totalPaginasMias, misActividadesOrdenadas.size());
    }

    private int totalPaginasActividades(int totalRegistros) {
        return Math.max(1, (int) Math.ceil(totalRegistros / (double) TAMANIO_PAGINA_ACTIVIDADES));
    }

    private int paginaActualActividades(String pagina, int totalPaginas) {
        int numero;
        try {
            numero = pagina == null ? 1 : Integer.parseInt(pagina.trim());
        } catch (NumberFormatException ex) {
            numero = 1;
        }
        return Math.min(Math.max(1, numero), totalPaginas);
    }

    private List<Actividad> recortarActividades(List<Actividad> registros, int paginaActual) {
        int desde = (paginaActual - 1) * TAMANIO_PAGINA_ACTIVIDADES;
        int hasta = Math.min(desde + TAMANIO_PAGINA_ACTIVIDADES, registros.size());
        return desde < hasta ? registros.subList(desde, hasta) : List.of();
    }

    // ============================================================
    // MARCAR ACTIVIDAD COMO "LISTO PARA REVISAR"
    // ============================================================
    @Transactional
    public void marcarActividadListaParaRevision(Usuario colaborador, Long actividadId,
                                                 MultipartFile evidencia, String comentario) {
        Actividad actividad = actividadRepository.findById(actividadId)
                .orElseThrow(() -> new IllegalArgumentException("La actividad no existe."));

        if (!actividad.isActivo()) {
            throw new IllegalArgumentException("Esta actividad ya no existe.");
        }
        if (!actividad.getColaborador().getId().equals(colaborador.getId())) {
            throw new IllegalArgumentException("Esta actividad no te pertenece.");
        }
        boolean sigueActivoEnElProyecto = asignacionRepository.existsByProyectoAndColaboradorAndEstado(
                actividad.getProyecto(), colaborador, EstadoAsignacion.ACTIVA);
        if (!sigueActivoEnElProyecto) {
            throw new IllegalArgumentException("Ya no tienes una asignación activa en este proyecto.");
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
        String nombreEvidencia = evidencia.getOriginalFilename();
        if (nombreEvidencia == null || !EXTENSIONES_EVIDENCIA_PERMITIDAS.contains(extensionDe(nombreEvidencia))) {
            throw new IllegalArgumentException("La evidencia debe ser un archivo .pdf, .jpg o .png.");
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

        notificacionService.crear(actividad.getProyecto().getPm(), "ACTIVIDAD_LISTA", com.pucp.skillb_ia.model.enums.CategoriaNotificacion.ACTIVIDAD,
                "Actividad lista para revisar",
                colaborador.getNombre() + " " + colaborador.getApellido() + " marcó \"" + actividad.getTitulo() + "\" como lista.",
                "ACTIVIDAD", actividad.getId());

        // Si la entregó después de la fecha límite, es un strike. No importa sí la entregó, esta fue entregada tarde.
        if (java.time.LocalDate.now().isAfter(actividad.getFechaLimite())) {
            penalizacionService.aplicarStrikePorTardanza(actividad);
            penalizacionService.verificarYNotificarPorStrikes(colaborador, actividad.getProyecto());
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
        return archivoAlmacenamientoService.guardar(archivo, "evidencias-actividades", nombreArchivo);
    }

    private String extensionDe(String nombreArchivo) {
        int punto = nombreArchivo.lastIndexOf('.');
        if (punto == -1 || punto == nombreArchivo.length() - 1) {
            return "";
        }
        return nombreArchivo.substring(punto + 1).toLowerCase();
    }

}