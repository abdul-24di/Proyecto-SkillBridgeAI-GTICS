package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmAsignacionView;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.*;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ColaboradorHabilidadRepository;
import com.pucp.skillb_ia.repository.ConfiguracionSistemaRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.DisponibilidadService;
import com.pucp.skillb_ia.service.NotificacionService;
import com.pucp.skillb_ia.service.EvaluacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RmAsignacionService {

    private static final String ROL_RM = "RESOURCE_MANAGER";
    private static final String ROL_COLABORADOR = "COLABORADOR";
    private static final String CLAVE_MAX_ASIGNACIONES = "MAX_ASIGNACIONES_POR_COLABORADOR";
    private static final int MAX_ASIGNACIONES_POR_DEFECTO = 3;
    private static final BigDecimal MAX_HORAS_SEMANALES = new BigDecimal("168");
    private static final int LONGITUD_MAXIMA_BUSQUEDA = 100;
    // Cupos del proyecto (TASK-034): ocupan cupo las asignaciones activas y las pendientes.
    public static final int MAX_JUSTIFICACION_CUPO = 500;
    private static final Set<EstadoAsignacion> ESTADOS_CON_CUPO =
            EnumSet.of(EstadoAsignacion.ACTIVA, EstadoAsignacion.PENDIENTE);
    // Dashboard (TASK-033): excluye proyectos RECHAZADO, CANCELADO y FINALIZADO.
    private static final Set<EstadoProyecto> ESTADOS_PROYECTO_DASHBOARD =
            EnumSet.of(EstadoProyecto.ACTIVO, EstadoProyecto.EN_ESPERA, EstadoProyecto.EN_REVISION);

    // Bandeja de asignaciones (TASK-027): filtros GET y paginación en el servidor.
    public static final int TAMANIO_PAGINA = 10;
    public static final String GRUPO_POR_DEFECTO = "pending";
    public static final List<String> ORIGENES = List.of("PM", "RM", "Colaborador");
    // Estados que los flujos pueden producir en cada pestaña. No se ofrece "Pendiente" a secas:
    // ninguna propuesta ni postulación queda PENDIENTE sin ser "Pendiente RM", "Pendiente PM" o
    // "Pendiente RM y PM" (con ambas aprobaciones pasa a ACTIVA en la misma transacción).
    private static final Map<String, List<String>> ESTADOS_POR_GRUPO = Map.of(
            "pending", List.of("Pendiente RM", "Pendiente PM", "Pendiente RM y PM"),
            "active", List.of("Activa"),
            "history", List.of("Finalizada", "Rechazada"));

    /** Filtros ya validados: los valores nulos significan "Todos". */
    public record FiltrosAsignacion(String grupo, String busqueda, String origen, String estado,
                                    Long proyectoId, String proyectoNombre) {
    }

    /** Contadores de tarjetas y pestañas sobre el conjunto completo (todo o solo el proyecto filtrado). */
    public record ContadoresAsignaciones(long pendientesRm, long pendientesPm, long solicitudesColaborador,
                                         long activas, long historial) {
    }

    public record PaginaAsignaciones(List<RmAsignacionView> filas, int paginaActual, int totalPaginas,
                                     long totalRegistros, ContadoresAsignaciones contadores) {
    }

    private final AsignacionRepository asignacionRepository;
    private final ProyectoRepository proyectoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ColaboradorHabilidadRepository colaboradorHabilidadRepository;
    private final ConfiguracionSistemaRepository configuracionSistemaRepository;

    private final AuditoriaService auditoriaService;
    private final RmPresupuestoService presupuestoService;
    private final DisponibilidadService disponibilidadService;
    private final NotificacionService notificacionService;
    private final EvaluacionService evaluacionService;

    public RmAsignacionService(
            AsignacionRepository asignacionRepository,
            ProyectoRepository proyectoRepository,
            UsuarioRepository usuarioRepository,
            ColaboradorHabilidadRepository colaboradorHabilidadRepository,
            ConfiguracionSistemaRepository configuracionSistemaRepository,
            AuditoriaService auditoriaService,
            RmPresupuestoService presupuestoService,
            DisponibilidadService disponibilidadService,
            NotificacionService notificacionService,
            EvaluacionService evaluacionService) {
        this.asignacionRepository = asignacionRepository;
        this.proyectoRepository = proyectoRepository;
        this.usuarioRepository = usuarioRepository;
        this.colaboradorHabilidadRepository = colaboradorHabilidadRepository;
        this.configuracionSistemaRepository = configuracionSistemaRepository;
        this.auditoriaService = auditoriaService;
        this.presupuestoService = presupuestoService;
        this.disponibilidadService = disponibilidadService;
        this.notificacionService = notificacionService;
        this.evaluacionService = evaluacionService;
    }



    @Transactional(readOnly = true)
    public List<RmAsignacionView> listar() {
        int maxAsignaciones = obtenerMaxAsignaciones();
        return asignacionRepository.findAllConDetalleOrderByFechaSolicitudDesc().stream()
                .map(asignacion -> crearVista(asignacion, maxAsignaciones))
                .toList();
    }

    /**
     * Conjunto del dashboard (TASK-033): mismo orden que listar(), solo de proyectos ACTIVO,
     * EN_ESPERA o EN_REVISION. EN_REVISION se muestra para seguimiento; sigue sin ser asignable.
     */
    @Transactional(readOnly = true)
    public List<RmAsignacionView> listarParaDashboard() {
        int maxAsignaciones = obtenerMaxAsignaciones();
        return asignacionRepository.findAllConDetalleOrderByFechaSolicitudDesc().stream()
                .filter(asignacion -> ESTADOS_PROYECTO_DASHBOARD.contains(asignacion.getProyecto().getEstado()))
                .map(asignacion -> crearVista(asignacion, maxAsignaciones))
                .toList();
    }

    /** Opciones de estado de la pestaña indicada (grupo ya normalizado). */
    public List<String> estadosDelGrupo(String grupo) {
        return ESTADOS_POR_GRUPO.getOrDefault(grupo, ESTADOS_POR_GRUPO.get(GRUPO_POR_DEFECTO));
    }

    /**
     * Normaliza los parámetros GET de la bandeja. Vacíos, "all" o valores desconocidos
     * vuelven al comportamiento general: pestaña "pending" y sin filtro.
     */
    @Transactional(readOnly = true)
    public FiltrosAsignacion normalizarFiltros(String grupo, String busqueda, String origen,
                                               String estado, String proyectoId) {
        String grupoValido = grupo != null && ESTADOS_POR_GRUPO.containsKey(grupo.trim())
                ? grupo.trim() : GRUPO_POR_DEFECTO;
        String busquedaLimpia = busqueda == null ? "" : busqueda.trim();
        if (busquedaLimpia.length() > LONGITUD_MAXIMA_BUSQUEDA) {
            busquedaLimpia = busquedaLimpia.substring(0, LONGITUD_MAXIMA_BUSQUEDA);
        }
        Proyecto proyecto = buscarProyecto(proyectoId);
        return new FiltrosAsignacion(
                grupoValido,
                busquedaLimpia.isEmpty() ? null : busquedaLimpia,
                opcionValida(origen, ORIGENES),
                opcionValida(estado, estadosDelGrupo(grupoValido)),
                proyecto == null ? null : proyecto.getId(),
                proyecto == null ? null : proyecto.getNombre());
    }

    /**
     * Filtra, cuenta y pagina la bandeja en orden de fechaSolicitud descendente. Solo las filas
     * de la página se construyen con la vista completa (consultas de capacidad y habilidades).
     */
    @Transactional(readOnly = true)
    public PaginaAsignaciones listarPagina(FiltrosAsignacion filtros, String pagina) {
        List<RmAsignacionView> conjunto = asignacionRepository.findAllConDetalleOrderByFechaSolicitudDesc().stream()
                .filter(asignacion -> filtros.proyectoId() == null
                        || filtros.proyectoId().equals(asignacion.getProyecto().getId()))
                .map(this::crearVistaBasica)
                .toList();

        String busqueda = textoComparable(filtros.busqueda());
        List<RmAsignacionView> filtrados = conjunto.stream()
                .filter(item -> item.getGrupo().equals(filtros.grupo()))
                .filter(item -> filtros.origen() == null || filtros.origen().equals(item.getOrigenCodigo()))
                .filter(item -> filtros.estado() == null || filtros.estado().equals(item.getEstadoBandeja()))
                .filter(item -> busqueda.isEmpty() || textoComparable(
                        item.getColaboradorNombre() + " " + item.getAsignacion().getProyecto().getNombre())
                        .contains(busqueda))
                .toList();

        int totalPaginas = Math.max(1, (int) Math.ceil(filtrados.size() / (double) TAMANIO_PAGINA));
        int paginaActual = Math.min(Math.max(1, numeroPagina(pagina)), totalPaginas);
        int desde = (paginaActual - 1) * TAMANIO_PAGINA;
        int hasta = Math.min(desde + TAMANIO_PAGINA, filtrados.size());
        int maxAsignaciones = obtenerMaxAsignaciones();
        List<RmAsignacionView> filas = desde < hasta
                ? filtrados.subList(desde, hasta).stream()
                        .map(item -> crearVista(item.getAsignacion(), maxAsignaciones))
                        .toList()
                : List.of();
        return new PaginaAsignaciones(filas, paginaActual, totalPaginas, filtrados.size(), contar(conjunto));
    }

    @Transactional(readOnly = true)
    public RmAsignacionView obtener(Long asignacionId) {
        Asignacion asignacion = asignacionRepository.findByIdConDetalle(asignacionId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la asignación solicitada."));
        return crearVista(asignacion, obtenerMaxAsignaciones());
    }

    @Transactional(readOnly = true)
    public List<RmAsignacionView> listarPorColaborador(Long colaboradorId) {
        Usuario colaborador = obtenerColaborador(colaboradorId);
        int maxAsignaciones = obtenerMaxAsignaciones();
        return asignacionRepository.findByColaboradorIdConDetalle(colaborador.getId()).stream()
                .map(asignacion -> crearVista(asignacion, maxAsignaciones))
                .toList();
    }

    // Ids de colaboradores que ya tienen una asignación pendiente o activa en el
    // proyecto: no se les puede proponer otra (ver proponerDesdeRm).
    @Transactional(readOnly = true)
    public Set<Long> colaboradoresConAsignacionVigente(Proyecto proyecto) {
        return asignacionRepository.findByProyecto(proyecto).stream()
                .filter(a -> a.getEstado() == EstadoAsignacion.PENDIENTE || a.getEstado() == EstadoAsignacion.ACTIVA)
                .map(a -> a.getColaborador().getId())
                .collect(Collectors.toSet());
    }

    // Ids de proyectos donde el colaborador ya tiene una asignación pendiente o activa.
    @Transactional(readOnly = true)
    public Set<Long> proyectosConAsignacionVigente(Long colaboradorId) {
        return asignacionRepository.findByColaboradorIdConDetalle(colaboradorId).stream()
                .filter(a -> a.getEstado() == EstadoAsignacion.PENDIENTE || a.getEstado() == EstadoAsignacion.ACTIVA)
                .map(a -> a.getProyecto().getId())
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public List<Proyecto> listarProyectosAsignables() {
        return proyectoRepository.findAllConPmOrderByFechaCreacionDesc().stream()
                .filter(proyecto -> proyecto.getEstado() == EstadoProyecto.ACTIVO
                        || proyecto.getEstado() == EstadoProyecto.EN_ESPERA)
                .toList();
    }

    // Cupos ocupados del proyecto: asignaciones ACTIVA + PENDIENTE (no cuentan las cerradas).
    @Transactional(readOnly = true)
    public long contarCuposOcupados(Proyecto proyecto) {
        return asignacionRepository.countByProyectoAndEstadoIn(proyecto, ESTADOS_CON_CUPO);
    }

    // Cupos ocupados por id de proyecto, para los modales que listan varios proyectos.
    @Transactional(readOnly = true)
    public Map<Long, Long> cuposOcupadosPorProyecto(List<Proyecto> proyectos) {
        return proyectos.stream().collect(Collectors.toMap(Proyecto::getId, this::contarCuposOcupados));
    }

    @Transactional
    public Asignacion proponerDesdeRm(
            Long proyectoId,
            Long colaboradorId,
            BigDecimal horasSemanales,
            String justificacion,
            String motivoCapacidad,
            String justificacionCupo,
            Long rmId) {
        Usuario rm = obtenerRm(rmId);
        Proyecto proyecto = obtenerProyectoAsignable(proyectoId);
        Usuario colaborador = obtenerColaborador(colaboradorId);
        validarHoras(horasSemanales);
        // A20 / sección 13: nunca se propone sin presupuesto suficiente — el RM
        // reserva el costo de inmediato porque proponerDesdeRm() auto-aprueba por el RM.
        presupuestoService.validarPresupuestoSuficiente(proyecto, colaborador, horasSemanales);

        boolean yaTieneAsignacion = asignacionRepository
                .existsByProyectoAndColaboradorAndEstadoIn(
                        proyecto, colaborador,
                        EnumSet.of(EstadoAsignacion.PENDIENTE, EstadoAsignacion.ACTIVA));
        if (yaTieneAsignacion) {
            throw new IllegalStateException("El colaborador ya tiene una asignación pendiente o activa en este proyecto.");
        }

        // TASK-034: con los cupos completos se puede proponer, pero solo con una justificación de cupo.
        // El conteo se hace aquí; no se usa ningún valor enviado por el navegador.
        long cuposOcupados = contarCuposOcupados(proyecto);
        boolean cuposCompletos = cuposOcupados >= proyecto.getColaboradoresRequeridos();
        String justificacionCupoValida = cuposCompletos ? validarJustificacionCupo(justificacionCupo) : null;

        int activas = (int) asignacionRepository.countByColaboradorAndEstado(
                colaborador, EstadoAsignacion.ACTIVA);
        boolean excedeCapacidad = activas >= obtenerMaxAsignaciones()
                || horasSemanales.compareTo(horasDisponibles(colaborador)) > 0;
        String mensaje = construirMensaje(justificacion, motivoCapacidad, excedeCapacidad);

        Asignacion asignacion = new Asignacion();
        asignacion.setProyecto(proyecto);
        asignacion.setColaborador(colaborador);
        asignacion.setHorasSemanales(horasSemanales);
        asignacion.setOrigen(OrigenAsignacion.PROPUESTA_RM);
        asignacion.setMensajeSolicitud(mensaje);
        asignacion.setEstado(EstadoAsignacion.PENDIENTE);
        asignacion.setAprobadoPorRm(true);
        asignacion.setFechaAprobacionRm(LocalDateTime.now());

        Asignacion guardada = asignacionRepository.save(asignacion);
        auditoriaService.registrar(
                rm, "PROPUESTA_ASIGNACION", "ASIGNACION", guardada.getId(),
                "El RM propuso a " + nombreCompleto(colaborador)
                        + " para el proyecto " + proyecto.getNombre() + "."
                        + (cuposCompletos ? " Supera los cupos del proyecto." : ""));
        if (cuposCompletos) {
            // detalle admite 500 caracteres: el texto completo va en su propio registro.
            auditoriaService.registrar(
                    rm, "JUSTIFICACION_CUPO_ASIGNACION", "ASIGNACION", guardada.getId(),
                    justificacionCupoValida,
                    "Cupos ocupados: " + cuposOcupados + " de " + proyecto.getColaboradoresRequeridos(),
                    null, null);
        }

        notificacionService.crear(proyecto.getPm(), "ASIGNACION_PENDIENTE_PM", CategoriaNotificacion.ASIGNACION,
                "Asignación pendiente de tu aprobación",
                "El Resource Manager propuso a " + nombreCompleto(colaborador)
                        + " para tu proyecto \"" + proyecto.getNombre() + "\".",
                "ASIGNACION", guardada.getId());
        return guardada;
    }

    @Transactional
    public void aprobar(Long asignacionId, String motivoCapacidad, Long rmId) {
        Usuario rm = obtenerRm(rmId);
        Asignacion asignacion = obtenerEntidad(asignacionId);
        validarDecisionRm(asignacion);
        obtenerProyectoAsignable(asignacion.getProyecto().getId());
        // Al aprobar, el RM reserva el costo de esta asignación (sección 6/7.1).
        presupuestoService.validarPresupuestoSuficiente(
                asignacion.getProyecto(), asignacion.getColaborador(), asignacion.getHorasSemanales());

        int activas = (int) asignacionRepository.countByColaboradorAndEstado(
                asignacion.getColaborador(), EstadoAsignacion.ACTIVA);
        boolean excedeCapacidad = activas >= obtenerMaxAsignaciones()
                || asignacion.getHorasSemanales().compareTo(horasDisponibles(asignacion.getColaborador())) > 0;

        asignacion.setMensajeSolicitud(construirMensaje(
                asignacion.getMensajeSolicitud(), motivoCapacidad, excedeCapacidad));
        asignacion.setAprobadoPorRm(true);
        asignacion.setFechaAprobacionRm(LocalDateTime.now());

        if (asignacion.isAprobadoPorPm()) {
            validarSinAsignacionActivaDuplicada(asignacion);
            asignacion.setEstado(EstadoAsignacion.ACTIVA);
            asignacion.setFechaActivacion(LocalDateTime.now());
        }

        asignacionRepository.save(asignacion);

        //En caso de que quede activa, recalculamos la disponibilidad del colaborador
        if (asignacion.getEstado() == EstadoAsignacion.ACTIVA) {
            disponibilidadService.recalcular(asignacion.getColaborador());
            notificacionService.crear(asignacion.getColaborador(), "ASIGNACION_APROBADA", CategoriaNotificacion.ASIGNACION,
                    "Asignación aprobada",
                    "Tu asignación al proyecto \"" + asignacion.getProyecto().getNombre() + "\" quedó activa.",
                    "ASIGNACION", asignacion.getId());
        }


        auditoriaService.registrar(
                rm, "APROBACION_ASIGNACION", "ASIGNACION", asignacion.getId(),
                asignacion.getEstado() == EstadoAsignacion.ACTIVA
                        ? "El RM aprobó la asignación; quedó activa."
                        : "El RM aprobó la asignación; continúa pendiente del PM.");
    }

    @Transactional
    public void rechazar(Long asignacionId, String motivo, Long rmId) {
        Usuario rm = obtenerRm(rmId);
        Asignacion asignacion = obtenerEntidad(asignacionId);
        validarDecisionRm(asignacion);
        String motivoLimpio = textoObligatorio(motivo, "Debes indicar el motivo del rechazo.", 300);

        asignacion.setEstado(EstadoAsignacion.RECHAZADA);
        asignacion.setMotivoRechazo(motivoLimpio);
        asignacion.setRechazadoPor(rm);
        asignacionRepository.save(asignacion);

        notificacionService.crear(asignacion.getColaborador(), "ASIGNACION_RECHAZADA", CategoriaNotificacion.ASIGNACION,
                "Asignación rechazada",
                "Tu asignación al proyecto \"" + asignacion.getProyecto().getNombre()
                        + "\" fue rechazada. Motivo: " + motivoLimpio,
                "ASIGNACION", asignacion.getId());

        auditoriaService.registrar(
                rm, "RECHAZO_ASIGNACION", "ASIGNACION", asignacion.getId(),
                "El RM rechazó la asignación. Motivo: " + motivoLimpio);
    }

    @Transactional
    public void finalizar(
            Long asignacionId,
            MotivoFinalizacion motivo,
            String observacion,
            Integer calificacion,
            String feedback,
            Long rmId) {
        Usuario rm = obtenerRm(rmId);
        Asignacion asignacion = obtenerEntidad(asignacionId);
        if (asignacion.getEstado() != EstadoAsignacion.ACTIVA) {
            throw new IllegalStateException("Solo se puede finalizar una asignación activa.");
        }
        if (motivo == null) throw new IllegalArgumentException("Selecciona un motivo de finalización.");

        asignacion.setEstado(EstadoAsignacion.FINALIZADA);
        asignacion.setMotivoFinalizacion(motivo);
        asignacion.setDesasignadoPor(rm);
        asignacion.setFechaFinalizacion(LocalDateTime.now());
        asignacionRepository.save(asignacion);

        // Si se provee una calificación, registramos la evaluación
        if (calificacion != null) {
            evaluacionService.evaluarAsignacion(asignacion.getId(), calificacion, feedback, rm);
        }


        String detalle = "El RM finalizó la asignación. Motivo: " + motivo.name();

        //En caso de que se desasigne a un colaborador de un proyecto, tendremos que recalcular su disponibilidad
        disponibilidadService.recalcular(asignacion.getColaborador());

        notificacionService.crear(asignacion.getColaborador(), "ASIGNACION_FINALIZADA", CategoriaNotificacion.ASIGNACION,
                "Te desasignaron de un proyecto",
                "El Resource Manager te desasignó del proyecto \"" + asignacion.getProyecto().getNombre() + "\".",
                "ASIGNACION", asignacion.getId());


        if (observacion != null && !observacion.isBlank()) {
            detalle += ". Observación: " + limitar(observacion.trim(), 220);
        }
        auditoriaService.registrar(
                rm, "FINALIZACION_ASIGNACION", "ASIGNACION", asignacion.getId(), detalle);
    }

    private RmAsignacionView crearVista(Asignacion asignacion, int maxAsignaciones) {
        Usuario colaborador = asignacion.getColaborador();
        int activas = (int) asignacionRepository.countByColaboradorAndEstado(
                colaborador, EstadoAsignacion.ACTIVA);
        BigDecimal horasComprometidas = asignacionRepository
                .findByColaboradorAndEstado(colaborador, EstadoAsignacion.ACTIVA).stream()
                .map(Asignacion::getHorasSemanales)
                .filter(horas -> horas != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int equipoActual = (int) asignacionRepository.countByProyectoAndEstado(
                asignacion.getProyecto(), EstadoAsignacion.ACTIVA);
        int vacantes = Math.max(0,
                asignacion.getProyecto().getColaboradoresRequeridos() - equipoActual);
        // Criterios de asignación: solo habilidades validadas (TASK-015).
        List<String> habilidades = colaboradorHabilidadRepository
                .findByColaboradorAndActivoTrueAndEstadoValidacion(colaborador, EstadoValidacion.VALIDADA).stream()
                .map(item -> item.getHabilidad().getNombre())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        return new RmAsignacionView(
                asignacion,
                nombreCompleto(colaborador),
                iniciales(colaborador),
                nombreCompleto(asignacion.getProyecto().getPm()),
                activas,
                maxAsignaciones,
                horasComprometidas,
                equipoActual,
                vacantes,
                habilidades);
    }

    // Vista sin consultas adicionales: basta para clasificar (grupo, origen, estado) y buscar.
    private RmAsignacionView crearVistaBasica(Asignacion asignacion) {
        Usuario colaborador = asignacion.getColaborador();
        return new RmAsignacionView(asignacion, nombreCompleto(colaborador), iniciales(colaborador),
                null, 0, 0, BigDecimal.ZERO, 0, 0, List.of());
    }

    private ContadoresAsignaciones contar(List<RmAsignacionView> conjunto) {
        return new ContadoresAsignaciones(
                conjunto.stream().filter(RmAsignacionView::isRequiereDecisionRm).count(),
                conjunto.stream().filter(RmAsignacionView::isPendientePm).count(),
                conjunto.stream()
                        .filter(RmAsignacionView::isSolicitudColaborador)
                        .filter(item -> item.getAsignacion().getEstado() == EstadoAsignacion.PENDIENTE)
                        .count(),
                conjunto.stream().filter(RmAsignacionView::isActiva).count(),
                conjunto.stream().filter(item -> "history".equals(item.getGrupo())).count());
    }

    private Proyecto buscarProyecto(String proyectoId) {
        if (proyectoId == null || proyectoId.isBlank()) return null;
        try {
            return proyectoRepository.findById(Long.valueOf(proyectoId.trim())).orElse(null);
        } catch (NumberFormatException ex) {
            return null;
        }
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

    private String textoComparable(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private Asignacion obtenerEntidad(Long asignacionId) {
        return asignacionRepository.findByIdConDetalle(asignacionId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la asignación solicitada."));
    }

    private Proyecto obtenerProyectoAsignable(Long proyectoId) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el proyecto seleccionado."));
        if (!esProyectoAsignable(proyecto)) {
            throw new IllegalStateException("Solo se pueden proponer asignaciones para proyectos activos o en espera.");
        }
        return proyecto;
    }

    // Criterio único para buscar colaboradores y proponer asignaciones: proyecto ACTIVO o EN_ESPERA.
    public boolean esProyectoAsignable(Proyecto proyecto) {
        return proyecto != null
                && (proyecto.getEstado() == EstadoProyecto.ACTIVO
                || proyecto.getEstado() == EstadoProyecto.EN_ESPERA);
    }

    private Usuario obtenerColaborador(Long colaboradorId) {
        return usuarioRepository.findById(colaboradorId)
                .filter(Usuario::isActivo)
                .filter(usuario -> usuario.getRol() != null
                        && ROL_COLABORADOR.equals(usuario.getRol().getNombre()))
                .orElseThrow(() -> new IllegalArgumentException("No se encontró un colaborador activo válido."));
    }

    private Usuario obtenerRm(Long rmId) {
        return usuarioRepository.findById(rmId)
                .filter(Usuario::isActivo)
                .filter(usuario -> usuario.getRol() != null && ROL_RM.equals(usuario.getRol().getNombre()))
                .orElseThrow(() -> new IllegalArgumentException("El usuario actual no es un Resource Manager válido."));
    }

    private void validarDecisionRm(Asignacion asignacion) {
        if (asignacion.getEstado() != EstadoAsignacion.PENDIENTE) {
            throw new IllegalStateException("La asignación ya fue resuelta.");
        }
        if (asignacion.isAprobadoPorRm() || asignacion.getOrigen() == OrigenAsignacion.PROPUESTA_RM) {
            throw new IllegalStateException("Esta asignación no está pendiente de decisión del RM.");
        }
    }

    private void validarSinAsignacionActivaDuplicada(Asignacion asignacion) {
        if (asignacionRepository.existsByProyectoAndColaboradorAndEstado(
                asignacion.getProyecto(), asignacion.getColaborador(), EstadoAsignacion.ACTIVA)) {
            throw new IllegalStateException("El colaborador ya tiene una asignación activa en este proyecto.");
        }
    }

    private void validarHoras(BigDecimal horas) {
        if (horas == null || horas.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Las horas semanales deben ser mayores que cero.");
        }
        if (horas.compareTo(MAX_HORAS_SEMANALES) > 0) {
            throw new IllegalArgumentException("Las horas semanales no pueden superar 168.");
        }
    }

    private String construirMensaje(String base, String motivoCapacidad, boolean motivoRequerido) {
        String mensaje = base == null ? "" : base.trim();
        if (mensaje.length() > 500) {
            throw new IllegalArgumentException("La justificación no puede superar 500 caracteres.");
        }
        if (!motivoRequerido) return mensaje.isEmpty() ? null : mensaje;

        String motivo = textoObligatorio(
                motivoCapacidad,
                "La propuesta supera la disponibilidad o el límite de asignaciones; debes justificar la excepción.",
                300);
        String combinado = mensaje + (mensaje.isEmpty() ? "" : "\n")
                + "Justificación de capacidad: " + motivo;
        if (combinado.length() > 500) {
            throw new IllegalArgumentException("La justificación total no puede superar 500 caracteres.");
        }
        return combinado;
    }

    private String validarJustificacionCupo(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    "El proyecto ya tiene sus cupos completos; justifica por qué propones otro colaborador.");
        }
        String limpio = valor.trim();
        if (limpio.length() > MAX_JUSTIFICACION_CUPO) {
            throw new IllegalArgumentException(
                    "La justificación de cupo no puede superar " + MAX_JUSTIFICACION_CUPO + " caracteres.");
        }
        return limpio;
    }

    private String textoObligatorio(String valor, String mensaje, int maximo) {
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException(mensaje);
        String limpio = valor.trim();
        if (limpio.length() > maximo) {
            throw new IllegalArgumentException("El texto no puede superar " + maximo + " caracteres.");
        }
        return limpio;
    }

    private int obtenerMaxAsignaciones() {
        return configuracionSistemaRepository.findByClave(CLAVE_MAX_ASIGNACIONES)
                .map(configuracion -> parsearEnteroPositivo(configuracion.getValor()))
                .orElse(MAX_ASIGNACIONES_POR_DEFECTO);
    }

    private int parsearEnteroPositivo(String valor) {
        try {
            int numero = Integer.parseInt(valor);
            return numero > 0 ? numero : MAX_ASIGNACIONES_POR_DEFECTO;
        } catch (NumberFormatException ex) {
            return MAX_ASIGNACIONES_POR_DEFECTO;
        }
    }

    private BigDecimal horasDisponibles(Usuario colaborador) {
        return colaborador.getHorasDisponibles() == null
                ? BigDecimal.ZERO : colaborador.getHorasDisponibles();
    }

    private String nombreCompleto(Usuario usuario) {
        String nombre = usuario.getNombre() == null ? "" : usuario.getNombre().trim();
        String apellido = usuario.getApellido() == null ? "" : usuario.getApellido().trim();
        String completo = (nombre + " " + apellido).trim();
        return completo.isEmpty() ? usuario.getCorreo() : completo;
    }

    private String iniciales(Usuario usuario) {
        String nombre = usuario.getNombre() == null ? "" : usuario.getNombre().trim();
        String apellido = usuario.getApellido() == null ? "" : usuario.getApellido().trim();
        String iniciales = (nombre.isEmpty() ? "" : nombre.substring(0, 1))
                + (apellido.isEmpty() ? "" : apellido.substring(0, 1));
        return iniciales.isEmpty() ? "CO" : iniciales.toUpperCase();
    }

    private String limitar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
