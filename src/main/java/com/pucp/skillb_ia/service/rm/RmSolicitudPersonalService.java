package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmProyectoView;
import com.pucp.skillb_ia.dto.RmSolicitudPersonalView;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.SolicitudPersonal;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.EstadoSolicitudPersonal;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.SolicitudPersonalRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.NotificacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

@Service
public class RmSolicitudPersonalService {

    // Listado de solicitudes de personal (TASK-038): filtros GET y paginación en el servidor.
    public static final int TAMANIO_PAGINA = 6;
    private static final int LONGITUD_MAXIMA_BUSQUEDA = 100;
    // Igual que toLowerCase() del JS anterior: sin mayúsculas, con tildes.
    private static final Locale LOCALE_BUSQUEDA = Locale.forLanguageTag("es");

    /** Filtros ya validados: los valores nulos significan "Todos". */
    public record FiltrosSolicitud(String busqueda, String estado, String prioridad) {
    }

    /** Indicadores de las tarjetas sobre todas las solicitudes. */
    public record ContadoresSolicitudes(long pendientes, long enAtencion, long atendidas, long totalSolicitados) {
    }

    public record PaginaSolicitudes(List<RmSolicitudPersonalView> filas, int paginaActual, int totalPaginas,
                                    long totalRegistros, ContadoresSolicitudes contadores) {
    }

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

    /**
     * Normaliza los parámetros GET del listado. Vacíos, "all" o valores desconocidos
     * significan "Todos" (null); la búsqueda se recorta a 100 caracteres.
     */
    public FiltrosSolicitud normalizarFiltros(String busqueda, String estado, String prioridad) {
        String busquedaLimpia = busqueda == null ? "" : busqueda.trim();
        if (busquedaLimpia.length() > LONGITUD_MAXIMA_BUSQUEDA) {
            busquedaLimpia = busquedaLimpia.substring(0, LONGITUD_MAXIMA_BUSQUEDA);
        }
        return new FiltrosSolicitud(
                busquedaLimpia.isEmpty() ? null : busquedaLimpia,
                opcionValida(estado, Arrays.stream(EstadoSolicitudPersonal.values()).map(Enum::name).toList()),
                opcionValida(prioridad, Arrays.stream(Prioridad.values()).map(Enum::name).toList()));
    }

    /**
     * Filtra, cuenta y pagina el listado conservando el orden de listar() (fecha de solicitud
     * descendente). Los indicadores se calculan sobre todas las solicitudes, sin filtros ni página.
     */
    @Transactional(readOnly = true)
    public PaginaSolicitudes listarPagina(FiltrosSolicitud filtros, String pagina) {
        List<SolicitudPersonal> todas = solicitudRepository.findAllConDetalleOrderByFechaSolicitudDesc();
        ContadoresSolicitudes contadores = new ContadoresSolicitudes(
                contarPorEstado(todas, EstadoSolicitudPersonal.PENDIENTE),
                contarPorEstado(todas, EstadoSolicitudPersonal.EN_ATENCION),
                contarPorEstado(todas, EstadoSolicitudPersonal.ATENDIDA),
                todas.stream().mapToLong(SolicitudPersonal::getCantidadColaboradores).sum());

        // Estado y prioridad se filtran sobre la entidad; la vista solo se arma para la búsqueda.
        String busqueda = filtros.busqueda() == null ? "" : filtros.busqueda().toLowerCase(LOCALE_BUSQUEDA);
        List<RmSolicitudPersonalView> filtradas = todas.stream()
                .filter(solicitud -> filtros.estado() == null
                        || filtros.estado().equals(solicitud.getEstado().name()))
                .filter(solicitud -> filtros.prioridad() == null
                        || (solicitud.getProyecto().getPrioridad() != null
                        && filtros.prioridad().equals(solicitud.getProyecto().getPrioridad().name())))
                .map(this::crearVista)
                .filter(item -> busqueda.isEmpty()
                        || item.getTextoBusqueda().toLowerCase(LOCALE_BUSQUEDA).contains(busqueda))
                .toList();

        int totalPaginas = Math.max(1, (int) Math.ceil(filtradas.size() / (double) TAMANIO_PAGINA));
        int paginaActual = Math.min(Math.max(1, numeroPagina(pagina)), totalPaginas);
        int desde = (paginaActual - 1) * TAMANIO_PAGINA;
        int hasta = Math.min(desde + TAMANIO_PAGINA, filtradas.size());
        List<RmSolicitudPersonalView> filas = desde < hasta ? filtradas.subList(desde, hasta) : List.of();
        return new PaginaSolicitudes(filas, paginaActual, totalPaginas, filtradas.size(), contadores);
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

    private long contarPorEstado(List<SolicitudPersonal> solicitudes, EstadoSolicitudPersonal estado) {
        return solicitudes.stream().filter(solicitud -> solicitud.getEstado() == estado).count();
    }

    private String opcionValida(String valor, List<String> opciones) {
        if (valor == null) return null;
        return opciones.stream()
                .filter(opcion -> opcion.equalsIgnoreCase(valor.trim()))
                .findFirst().orElse(null);
    }

    private int numeroPagina(String pagina) {
        if (pagina == null) return 1;
        try {
            return Integer.parseInt(pagina.trim());
        } catch (NumberFormatException ex) {
            return 1;
        }
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
