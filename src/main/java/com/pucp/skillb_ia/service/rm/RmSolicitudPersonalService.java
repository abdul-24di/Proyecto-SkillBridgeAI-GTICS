package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmProyectoView;
import com.pucp.skillb_ia.dto.RmSolicitudPersonalView;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.SolicitudPersonal;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.EstadoSolicitudPersonal;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.SolicitudPersonalRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.NotificacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Service
public class RmSolicitudPersonalService {

    private final SolicitudPersonalRepository solicitudRepository;
    private final ProyectoRepository proyectoRepository;
    private final UsuarioRepository usuarioRepository;
    private final RmProyectoConsultaService proyectoConsultaService;
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;

    public RmSolicitudPersonalService(
            SolicitudPersonalRepository solicitudRepository,
            ProyectoRepository proyectoRepository,
            UsuarioRepository usuarioRepository,
            RmProyectoConsultaService proyectoConsultaService,
            AuditoriaService auditoriaService,
            NotificacionService notificacionService) {
        this.solicitudRepository = solicitudRepository;
        this.proyectoRepository = proyectoRepository;
        this.usuarioRepository = usuarioRepository;
        this.proyectoConsultaService = proyectoConsultaService;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
    }

    @Transactional(readOnly = true)
    public List<RmSolicitudPersonalView> listar() {
        return solicitudRepository.findAllConDetalleOrderByFechaSolicitudDesc().stream()
                .map(this::crearVista)
                .toList();
    }

    @Transactional(readOnly = true)
    public RmSolicitudPersonalView obtener(Long id) {
        return crearVista(obtenerEntidad(id));
    }

    /** Queda listo para conectarlo al CRUD del PM cuando se implemente esa vista. */
    @Transactional
    public SolicitudPersonal crearDesdePm(Long proyectoId, int cantidad,
                                           String perfiles, String mensaje, Long pmId) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el proyecto solicitado."));
        Usuario pm = usuarioRepository.findById(pmId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el Project Manager."));
        if (!"PROJECT_MANAGER".equals(pm.getRol().getNombre())
                || !proyecto.getPm().getId().equals(pm.getId())) {
            throw new IllegalStateException("Solo el Project Manager responsable puede crear la solicitud.");
        }
        if (proyecto.getEstado() != EstadoProyecto.ACTIVO
                && proyecto.getEstado() != EstadoProyecto.EN_ESPERA) {
            throw new IllegalStateException("El proyecto debe estar activo o en espera para solicitar personal.");
        }
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad solicitada debe ser mayor que cero.");
        }
        if (solicitudRepository.existsByProyectoAndEstadoIn(proyecto,
                EnumSet.of(EstadoSolicitudPersonal.PENDIENTE, EstadoSolicitudPersonal.EN_ATENCION))) {
            throw new IllegalStateException("El proyecto ya tiene una solicitud de personal abierta.");
        }

        SolicitudPersonal solicitud = new SolicitudPersonal();
        solicitud.setProyecto(proyecto);
        solicitud.setCantidadColaboradores(cantidad);
        solicitud.setPerfilesRequeridos(limitar(perfiles, 1000));
        solicitud.setMensajePm(limitar(mensaje, 1000));
        solicitud.setEstado(EstadoSolicitudPersonal.PENDIENTE);
        SolicitudPersonal guardada = solicitudRepository.save(solicitud);
        auditoriaService.registrar(pm, "CREACION_SOLICITUD_PERSONAL", "SOLICITUD_PERSONAL",
                guardada.getId(), "El PM solicitó " + cantidad + " colaborador(es) para "
                        + proyecto.getNombre() + ".");

        notificacionService.crearParaTodosLosRm("SOLICITUD_PERSONAL_PENDIENTE", CategoriaNotificacion.PROYECTO,
                "Solicitud de personal pendiente",
                "El PM del proyecto \"" + proyecto.getNombre() + "\" solicitó " + cantidad + " colaborador(es).",
                "SOLICITUD_PERSONAL", guardada.getId());
        return guardada;
    }

    @Transactional
    public void iniciarAtencion(Long id, Long rmId) {
        Usuario rm = obtenerRm(rmId);
        SolicitudPersonal solicitud = obtenerEntidad(id);
        if (solicitud.getEstado() != EstadoSolicitudPersonal.PENDIENTE) {
            throw new IllegalStateException("Solo una solicitud pendiente puede pasar a atención.");
        }
        solicitud.setEstado(EstadoSolicitudPersonal.EN_ATENCION);
        solicitud.setRmResponsable(rm);
        solicitud.setFechaInicioAtencion(LocalDateTime.now());
        solicitudRepository.save(solicitud);

        notificacionService.crear(solicitud.getProyecto().getPm(), "SOLICITUD_PERSONAL_EN_ATENCION", CategoriaNotificacion.PROYECTO,
                "Tu solicitud de personal está en atención",
                "El Resource Manager comenzó a atender tu solicitud de personal para \""
                        + solicitud.getProyecto().getNombre() + "\".",
                "SOLICITUD_PERSONAL", id);

        auditoriaService.registrar(rm, "INICIO_ATENCION_SOLICITUD", "SOLICITUD_PERSONAL", id,
                "El RM inició la atención de la solicitud del proyecto "
                        + solicitud.getProyecto().getNombre() + ".");
    }

    @Transactional
    public void marcarAtendida(Long id, Long rmId) {
        Usuario rm = obtenerRm(rmId);
        SolicitudPersonal solicitud = obtenerEntidad(id);
        if (solicitud.getEstado() != EstadoSolicitudPersonal.EN_ATENCION) {
            throw new IllegalStateException("Solo una solicitud en atención puede marcarse como atendida.");
        }
        solicitud.setEstado(EstadoSolicitudPersonal.ATENDIDA);
        solicitud.setRmResponsable(rm);
        solicitud.setFechaAtencion(LocalDateTime.now());
        solicitudRepository.save(solicitud);

        notificacionService.crear(solicitud.getProyecto().getPm(), "SOLICITUD_PERSONAL_ATENDIDA", CategoriaNotificacion.PROYECTO,
                "Solicitud de personal atendida",
                "El Resource Manager marcó como atendida tu solicitud de personal para \""
                        + solicitud.getProyecto().getNombre() + "\".",
                "SOLICITUD_PERSONAL", id);

        auditoriaService.registrar(rm, "CIERRE_SOLICITUD_PERSONAL", "SOLICITUD_PERSONAL", id,
                "El RM marcó como atendida la solicitud del proyecto "
                        + solicitud.getProyecto().getNombre() + ".");
    }

    private SolicitudPersonal obtenerEntidad(Long id) {
        return solicitudRepository.findByIdConDetalle(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la solicitud de personal."));
    }

    private Usuario obtenerRm(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el Resource Manager."));
        if (!"RESOURCE_MANAGER".equals(usuario.getRol().getNombre())) {
            throw new IllegalStateException("La operación requiere un Resource Manager.");
        }
        return usuario;
    }

    private RmSolicitudPersonalView crearVista(SolicitudPersonal solicitud) {
        RmProyectoView proyecto = proyectoConsultaService.obtener(solicitud.getProyecto().getId());
        return new RmSolicitudPersonalView(
                solicitud,
                proyecto,
                nombreCompleto(solicitud.getProyecto().getPm()),
                solicitud.getRmResponsable() == null
                        ? "Sin asignar" : nombreCompleto(solicitud.getRmResponsable()),
                proyecto.getRequisitos());
    }

    private String nombreCompleto(Usuario usuario) {
        String nombre = usuario.getNombre() == null ? "" : usuario.getNombre().trim();
        String apellido = usuario.getApellido() == null ? "" : usuario.getApellido().trim();
        String completo = (nombre + " " + apellido).trim();
        return completo.isEmpty() ? usuario.getCorreo() : completo;
    }

    private String limitar(String texto, int maximo) {
        if (texto == null || texto.isBlank()) return null;
        String limpio = texto.trim();
        return limpio.length() <= maximo ? limpio : limpio.substring(0, maximo);
    }
}
