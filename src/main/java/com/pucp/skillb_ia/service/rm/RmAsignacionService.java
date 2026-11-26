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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Lógica del Resource Manager sobre las asignaciones de colaboradores a proyectos: proponer,
 * aprobar, rechazar y finalizar, además de la bandeja con filtros y paginación.
 * <p>
 * <b>Regla central (doble aprobación):</b> una asignación solo queda {@code ACTIVA} cuando la
 * aprueban el PM del proyecto <b>y</b> el RM. Según quién la origina:
 * <ul>
 *   <li><b>Propuesta del RM:</b> nace {@code PENDIENTE} con la aprobación del RM ya registrada;
 *       falta la del PM.</li>
 *   <li><b>Propuesta del PM:</b> falta la aprobación del RM (este service la da con {@link #aprobar}).</li>
 *   <li><b>Solicitud del colaborador:</b> faltan las dos.</li>
 * </ul>
 * Si cualquiera de los dos la rechaza, queda {@code RECHAZADA}. El colaborador no decide nada.
 * <p>
 * <b>Excepciones:</b> {@link IllegalArgumentException} para datos inválidos y
 * {@link IllegalStateException} para acciones que no corresponden al estado actual.
 * Cada acción importante registra auditoría y notifica a los involucrados.
 *
 * @see DisponibilidadService
 * @see RmPresupuestoService
 */
@Service
public class RmAsignacionService {

    private static final String ROL_RM = "RESOURCE_MANAGER";
    private static final String ROL_COLABORADOR = "COLABORADOR";
    private static final String CLAVE_MAX_ASIGNACIONES = "MAX_ASIGNACIONES_POR_COLABORADOR";
    private static final int MAX_ASIGNACIONES_POR_DEFECTO = 3;
    private static final BigDecimal MAX_HORAS_SEMANALES = new BigDecimal("168");
    private final RmResponsableService rmResponsableService;
    private static final int LONGITUD_MAXIMA_BUSQUEDA = 100;
    // Cupos del proyecto (TASK-034): ocupan cupo las asignaciones activas y las pendientes.
    /** Longitud máxima de la justificación que se pide al proponer con los cupos completos. */
    public static final int MAX_JUSTIFICACION_CUPO = 500;
    private static final Set<EstadoAsignacion> ESTADOS_CON_CUPO =
            EnumSet.of(EstadoAsignacion.ACTIVA, EstadoAsignacion.PENDIENTE);
    // Dashboard (TASK-033): excluye proyectos RECHAZADO, CANCELADO y FINALIZADO.
    private static final Set<EstadoProyecto> ESTADOS_PROYECTO_DASHBOARD =
            EnumSet.of(EstadoProyecto.ACTIVO, EstadoProyecto.EN_ESPERA, EstadoProyecto.EN_REVISION);

    // Bandeja de asignaciones (TASK-027): filtros GET y paginación en el servidor.
    /** Filas por página de la bandeja y del historial del colaborador. */
    public static final int TAMANIO_PAGINA = 10;
    /** Pestaña que se muestra cuando el parámetro {@code grupo} falta o no es válido. */
    public static final String GRUPO_POR_DEFECTO = "pending";
    /** Orígenes aceptados por el filtro de la bandeja. */
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

    /**
     * Página de la bandeja de asignaciones.
     *
     * @param filas asignaciones de la página, con la vista completa
     * @param paginaActual página mostrada (desde 1)
     * @param totalPaginas número de páginas, al menos 1
     * @param totalRegistros asignaciones que cumplen los filtros
     * @param contadores contadores de tarjetas y pestañas
     */
    public record PaginaAsignaciones(List<RmAsignacionView> filas, int paginaActual, int totalPaginas,
                                     long totalRegistros, ContadoresAsignaciones contadores) {
    }

    // Asignaciones del colaborador (TASK-051): filtros GET y paginación en el servidor.
    /** Filtros ya validados: los valores nulos significan "Todos". El año es el de proyecto.fechaInicio. */
    public record FiltrosAsignacionesColaborador(String busqueda, EstadoAsignacion estado, Integer anio) {
    }

    /** totalHistorial cuenta todas las asignaciones del colaborador, sin filtros (distingue los estados vacíos). */
    public record PaginaAsignacionesColaborador(List<RmAsignacionView> filas, int paginaActual, int totalPaginas,
                                                long totalRegistros, long totalHistorial,
                                                List<Integer> aniosDisponibles,
                                                FiltrosAsignacionesColaborador filtros) {
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
    private final RmAlcanceService rmAlcanceService;

    /** Crea el componente con sus dependencias, que Spring inyecta por el constructor. */
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
            RmResponsableService rmResponsableService,
            EvaluacionService evaluacionService,
            RmAlcanceService rmAlcanceService) {
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
        this.rmAlcanceService = rmAlcanceService;
        this.rmResponsableService = rmResponsableService;
    }

    // Listamos solo las asignaciones de los proyectos del RM que tiene la sesión iniciada
    private List<Asignacion> asignacionesDelRm() {
        Long rmId = rmAlcanceService.idRmActual();
        List<Asignacion> propias = new ArrayList<>();
        for (Asignacion asignacion : asignacionRepository.findAllConDetalleOrderByFechaSolicitudDesc()) {
            if (rmAlcanceService.puedeVer(asignacion.getProyecto(), rmId)) {
                propias.add(asignacion);
            }
        }
        return propias;
    }

    /**
     * Lista las asignaciones de los proyectos que ve el RM de la sesión, de la más reciente a la
     * más antigua (por fecha de solicitud).
     *
     * @return vistas completas de las asignaciones
     */
    @Transactional(readOnly = true)
    public List<RmAsignacionView> listar() {
        int maxAsignaciones = obtenerMaxAsignaciones();
        return asignacionesDelRm().stream()
                .map(asignacion -> crearVista(asignacion, maxAsignaciones))
                .toList();
    }

    /**
     * Conjunto del dashboard (TASK-033): mismo orden que listar(), solo de proyectos ACTIVO,
     * EN_ESPERA o EN_REVISION. EN_REVISION se muestra para seguimiento; sigue sin ser asignable.
     *
     * @return vistas de las asignaciones para el dashboard
     */
    @Transactional(readOnly = true)
    public List<RmAsignacionView> listarParaDashboard() {
        int maxAsignaciones = obtenerMaxAsignaciones();
        return asignacionesDelRm().stream()
                .filter(asignacion -> ESTADOS_PROYECTO_DASHBOARD.contains(asignacion.getProyecto().getEstado()))
                .map(asignacion -> crearVista(asignacion, maxAsignaciones))
                .toList();
    }

    /**
     * Opciones de estado de la pestaña indicada (grupo ya normalizado).
     *
     * @param grupo pestaña: {@code pending}, {@code active} o {@code history}
     * @return estados de esa pestaña; los de {@code pending} si el grupo no existe
     */
    public List<String> estadosDelGrupo(String grupo) {
        return ESTADOS_POR_GRUPO.getOrDefault(grupo, ESTADOS_POR_GRUPO.get(GRUPO_POR_DEFECTO));
    }

    /**
     * Normaliza los parámetros GET de la bandeja. Vacíos, "all" o valores desconocidos
     * vuelven al comportamiento general: pestaña "pending" y sin filtro. La búsqueda se recorta a
     * 100 caracteres y un proyecto inexistente se ignora.
     *
     * @param grupo pestaña pedida
     * @param busqueda texto sobre colaborador y proyecto
     * @param origen origen pedido (PM, RM o Colaborador)
     * @param estado estado pedido dentro de la pestaña
     * @param proyectoId id del proyecto como texto
     * @return filtros válidos; {@code null} en un campo significa "Todos"
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
     * Una página fuera de rango se ajusta a la primera o la última.
     *
     * @param filtros filtros ya normalizados con {@link #normalizarFiltros}
     * @param pagina número de página como texto
     * @return página con filas y contadores
     */
    @Transactional(readOnly = true)
    public PaginaAsignaciones listarPagina(FiltrosAsignacion filtros, String pagina) {
        List<RmAsignacionView> conjunto = asignacionesDelRm().stream()
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

    /**
     * Obtiene el detalle de una asignación de un proyecto que el RM de la sesión puede ver.
     *
     * @param asignacionId id de la asignación
     * @return vista completa de la asignación
     * @throws IllegalArgumentException si no existe o pertenece a un proyecto fuera del alcance del RM
     */
    @Transactional(readOnly = true)
    public RmAsignacionView obtener(Long asignacionId) {
        Asignacion asignacion = asignacionRepository.findByIdConDetalle(asignacionId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la asignación solicitada."));
        if (!rmAlcanceService.puedeVer(asignacion.getProyecto(), rmAlcanceService.idRmActual())) {
            throw new IllegalArgumentException("No se encontró la asignación solicitada.");
        }
        return crearVista(asignacion, obtenerMaxAsignaciones());
    }

    /**
     * Lista todas las asignaciones de un colaborador, de la más reciente a la más antigua.
     *
     * @param colaboradorId id del colaborador
     * @return vistas completas de sus asignaciones
     * @throws IllegalArgumentException si el usuario no existe, está inactivo o no es colaborador
     */
    @Transactional(readOnly = true)
    public List<RmAsignacionView> listarPorColaborador(Long colaboradorId) {
        Usuario colaborador = obtenerColaborador(colaboradorId);
        int maxAsignaciones = obtenerMaxAsignaciones();
        return asignacionRepository.findByColaboradorIdConDetalle(colaborador.getId()).stream()
                .map(asignacion -> crearVista(asignacion, maxAsignaciones))
                .toList();
    }

    /**
     * Normaliza los parámetros GET de las asignaciones del colaborador. Vacíos, "all" o valores
     * desconocidos equivalen a "Todos"; el año solo vale si está entre los disponibles.
     *
     * @param busqueda texto sobre proyecto y PM
     * @param estado nombre de un {@link EstadoAsignacion}, sin distinguir mayúsculas
     * @param anio año de inicio del proyecto como texto
     * @param aniosDisponibles años que aparecen en el historial del colaborador
     * @return filtros válidos; {@code null} en un campo significa "Todos"
     */
    public FiltrosAsignacionesColaborador normalizarFiltrosColaborador(String busqueda, String estado, String anio,
                                                                       List<Integer> aniosDisponibles) {
        String busquedaLimpia = busqueda == null ? "" : busqueda.trim();
        if (busquedaLimpia.length() > LONGITUD_MAXIMA_BUSQUEDA) {
            busquedaLimpia = busquedaLimpia.substring(0, LONGITUD_MAXIMA_BUSQUEDA);
        }
        EstadoAsignacion estadoValido = estado == null ? null : Arrays.stream(EstadoAsignacion.values())
                .filter(opcion -> opcion.name().equalsIgnoreCase(estado.trim()))
                .findFirst().orElse(null);
        Integer anioValido = null;
        if (anio != null) {
            try {
                int valor = Integer.parseInt(anio.trim());
                if (aniosDisponibles.contains(valor)) anioValido = valor;
            } catch (NumberFormatException ex) {
                anioValido = null;
            }
        }
        return new FiltrosAsignacionesColaborador(
                busquedaLimpia.isEmpty() ? null : busquedaLimpia, estadoValido, anioValido);
    }

    /**
     * Filtra y pagina el historial del colaborador conservando el orden de
     * findByColaboradorIdConDetalle (fechaSolicitud descendente). La búsqueda cubre el nombre
     * del proyecto y el del PM. Solo las filas de la página se construyen con la vista completa.
     *
     * @param colaboradorId id del colaborador
     * @param busqueda texto sobre proyecto y PM
     * @param estado estado de la asignación
     * @param anio año de inicio del proyecto
     * @param pagina número de página como texto
     * @return página con filas, años disponibles y filtros aplicados
     * @throws IllegalArgumentException si el usuario no es un colaborador activo
     */
    @Transactional(readOnly = true)
    public PaginaAsignacionesColaborador listarPaginaPorColaborador(Long colaboradorId, String busqueda,
                                                                    String estado, String anio, String pagina) {
        Usuario colaborador = obtenerColaborador(colaboradorId);
        List<Asignacion> historial = asignacionRepository.findByColaboradorIdConDetalle(colaborador.getId());
        List<Integer> aniosDisponibles = historial.stream()
                .map(asignacion -> asignacion.getProyecto().getFechaInicio())
                .filter(fecha -> fecha != null)
                .map(LocalDate::getYear)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
        FiltrosAsignacionesColaborador filtros =
                normalizarFiltrosColaborador(busqueda, estado, anio, aniosDisponibles);

        String texto = textoComparable(filtros.busqueda());
        List<Asignacion> filtradas = historial.stream()
                .filter(asignacion -> filtros.estado() == null || filtros.estado() == asignacion.getEstado())
                .filter(asignacion -> filtros.anio() == null || (asignacion.getProyecto().getFechaInicio() != null
                        && asignacion.getProyecto().getFechaInicio().getYear() == filtros.anio()))
                .filter(asignacion -> texto.isEmpty() || textoComparable(asignacion.getProyecto().getNombre()
                        + " " + nombreCompleto(asignacion.getProyecto().getPm())).contains(texto))
                .toList();

        int totalPaginas = Math.max(1, (int) Math.ceil(filtradas.size() / (double) TAMANIO_PAGINA));
        int paginaActual = Math.min(Math.max(1, numeroPagina(pagina)), totalPaginas);
        int desde = (paginaActual - 1) * TAMANIO_PAGINA;
        int hasta = Math.min(desde + TAMANIO_PAGINA, filtradas.size());
        int maxAsignaciones = obtenerMaxAsignaciones();
        List<RmAsignacionView> filas = desde < hasta
                ? filtradas.subList(desde, hasta).stream()
                        .map(asignacion -> crearVista(asignacion, maxAsignaciones))
                        .toList()
                : List.of();
        return new PaginaAsignacionesColaborador(filas, paginaActual, totalPaginas, filtradas.size(),
                historial.size(), aniosDisponibles, filtros);
    }

    /**
     * Ids de colaboradores que ya tienen una asignación pendiente o activa en el proyecto: no se
     * les puede proponer otra (ver {@link #proponerDesdeRm}).
     *
     * @param proyecto proyecto consultado
     * @return ids de esos colaboradores
     */
    @Transactional(readOnly = true)
    public Set<Long> colaboradoresConAsignacionVigente(Proyecto proyecto) {
        return asignacionRepository.findByProyecto(proyecto).stream()
                .filter(a -> a.getEstado() == EstadoAsignacion.PENDIENTE || a.getEstado() == EstadoAsignacion.ACTIVA)
                .map(a -> a.getColaborador().getId())
                .collect(Collectors.toSet());
    }

    /**
     * Ids de proyectos donde el colaborador ya tiene una asignación pendiente o activa.
     *
     * @param colaboradorId id del colaborador
     * @return ids de esos proyectos
     */
    @Transactional(readOnly = true)
    public Set<Long> proyectosConAsignacionVigente(Long colaboradorId) {
        return asignacionRepository.findByColaboradorIdConDetalle(colaboradorId).stream()
                .filter(a -> a.getEstado() == EstadoAsignacion.PENDIENTE || a.getEstado() == EstadoAsignacion.ACTIVA)
                .map(a -> a.getProyecto().getId())
                .collect(Collectors.toSet());
    }

    /**
     * Proyectos ACTIVO o EN_ESPERA que ve el RM de la sesión, del más reciente al más antiguo.
     *
     * @return proyectos donde se puede proponer una asignación
     */
    @Transactional(readOnly = true)
    public List<Proyecto> listarProyectosAsignables() {
        Long rmId = rmAlcanceService.idRmActual();
        return proyectoRepository.findAllConPmOrderByFechaCreacionDesc().stream()
                .filter(proyecto -> proyecto.getEstado() == EstadoProyecto.ACTIVO
                        || proyecto.getEstado() == EstadoProyecto.EN_ESPERA)
                .filter(proyecto -> rmAlcanceService.puedeVer(proyecto, rmId))
                .toList();
    }

    /**
     * Cupos ocupados del proyecto: asignaciones ACTIVA + PENDIENTE (no cuentan las cerradas).
     *
     * @param proyecto proyecto consultado
     * @return número de cupos ocupados
     */
    @Transactional(readOnly = true)
    public long contarCuposOcupados(Proyecto proyecto) {
        return asignacionRepository.countByProyectoAndEstadoIn(proyecto, ESTADOS_CON_CUPO);
    }

    /**
     * Cupos ocupados por id de proyecto, para los modales que listan varios proyectos.
     *
     * @param proyectos proyectos consultados
     * @return mapa id de proyecto → cupos ocupados
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> cuposOcupadosPorProyecto(List<Proyecto> proyectos) {
        return proyectos.stream().collect(Collectors.toMap(Proyecto::getId, this::contarCuposOcupados));
    }

    /**
     * Propone a un colaborador para un proyecto. La propuesta queda PENDIENTE y ya aprobada por el
     * RM, por lo que solo falta el PM, a quien se notifica. Si el colaborador alcanzó el límite de
     * asignaciones activas, se exige un motivo de capacidad; si el proyecto tiene los cupos
     * completos, se exige una justificación de cupo que se audita aparte.
     *
     * @param proyectoId proyecto ACTIVO o EN_ESPERA del que el RM es responsable
     * @param colaboradorId colaborador visible para el RM
     * @param horasSemanales horas por semana, mayores que 0, hasta 168 y sin superar las disponibles
     * @param justificacion mensaje opcional, hasta 500 caracteres en total
     * @param motivoCapacidad motivo obligatorio si se excede el límite de asignaciones activas (hasta 300)
     * @param justificacionCupo justificación obligatoria si los cupos están completos (hasta 500)
     * @param rmId id del RM de la sesión
     * @return asignación creada
     * @throws IllegalArgumentException si el RM, el proyecto o el colaborador no son válidos, las horas
     *         no son válidas o superan la disponibilidad, o falta un motivo o justificación obligatorio
     * @throws IllegalStateException si el proyecto no es asignable, el RM no es su responsable, falta
     *         presupuesto o el colaborador ya tiene una asignación pendiente o activa en el proyecto
     */
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
        rmResponsableService.validarResponsable(proyecto, rm);
        Usuario colaborador = obtenerColaboradorAsignable(colaboradorId);
        validarHoras(horasSemanales);
        // TASK-054: las horas de más no admiten excepción.
        validarHorasDentroDeDisponibilidad(horasSemanales, colaborador);
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
        // La excepción con justificación solo cubre el límite de asignaciones activas.
        boolean excedeCapacidad = activas >= obtenerMaxAsignaciones();
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

    /**
     * Registra la aprobación del RM sobre una solicitud pendiente (del PM o del colaborador). Si el
     * PM ya había aprobado, la asignación pasa a ACTIVA, se recalcula la disponibilidad del
     * colaborador y se le notifica; en cualquier caso se avisa al PM.
     *
     * @param asignacionId asignación PENDIENTE que espera la decisión del RM
     * @param motivoCapacidad motivo obligatorio si el colaborador alcanzó el límite de asignaciones activas
     * @param rmId id del RM de la sesión
     * @throws IllegalArgumentException si la asignación o el RM no existen, las horas superan la
     *         disponibilidad o falta el motivo de capacidad
     * @throws IllegalStateException si ya fue resuelta, no espera al RM, el proyecto no es asignable,
     *         falta presupuesto o el colaborador ya está activo en el proyecto
     */
    @Transactional
    public void aprobar(Long asignacionId, String motivoCapacidad, Long rmId) {
        Usuario rm = obtenerRm(rmId);
        Asignacion asignacion = obtenerEntidad(asignacionId);
        validarDecisionRm(asignacion);
        rmResponsableService.validarResponsable(asignacion.getProyecto(), rm);
        obtenerProyectoAsignable(asignacion.getProyecto().getId());
        validarHorasDentroDeDisponibilidad(asignacion.getHorasSemanales(), asignacion.getColaborador());
        // Al aprobar, el RM reserva el costo de esta asignación (sección 6/7.1).
        presupuestoService.validarPresupuestoSuficiente(
                asignacion.getProyecto(), asignacion.getColaborador(), asignacion.getHorasSemanales());

        int activas = (int) asignacionRepository.countByColaboradorAndEstado(
                asignacion.getColaborador(), EstadoAsignacion.ACTIVA);
        boolean excedeCapacidad = activas >= obtenerMaxAsignaciones();

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

        // El PM también decide sobre su equipo: se le avisa si ya quedó activa o si ahora le toca aprobar.
        Usuario colaborador = asignacion.getColaborador();
        notificacionService.crear(asignacion.getProyecto().getPm(),
                asignacion.getEstado() == EstadoAsignacion.ACTIVA ? "ASIGNACION_ACTIVA" : "ASIGNACION_PENDIENTE_PM",
                CategoriaNotificacion.ASIGNACION,
                asignacion.getEstado() == EstadoAsignacion.ACTIVA ? "Asignación activa" : "Asignación pendiente de tu aprobación",
                "El RM aprobó a " + colaborador.getNombre() + " " + colaborador.getApellido() + " en el proyecto \""
                        + asignacion.getProyecto().getNombre() + "\""
                        + (asignacion.getEstado() == EstadoAsignacion.ACTIVA ? "; ya forma parte del equipo." : "; falta tu aprobación."),
                "PROYECTO", asignacion.getProyecto().getId());


        auditoriaService.registrar(
                rm, "APROBACION_ASIGNACION", "ASIGNACION", asignacion.getId(),
                asignacion.getEstado() == EstadoAsignacion.ACTIVA
                        ? "El RM aprobó la asignación; quedó activa."
                        : "El RM aprobó la asignación; continúa pendiente del PM.");
    }

    /**
     * Rechaza una solicitud pendiente de decisión del RM y notifica al colaborador y al PM.
     *
     * @param asignacionId asignación PENDIENTE que espera la decisión del RM
     * @param motivo motivo obligatorio, hasta 300 caracteres
     * @param rmId id del RM de la sesión
     * @throws IllegalArgumentException si la asignación o el RM no existen, o el motivo falta o es largo
     * @throws IllegalStateException si ya fue resuelta, no espera al RM o el RM no es responsable del proyecto
     */
    @Transactional
    public void rechazar(Long asignacionId, String motivo, Long rmId) {
        Usuario rm = obtenerRm(rmId);
        Asignacion asignacion = obtenerEntidad(asignacionId);
        validarDecisionRm(asignacion);
        rmResponsableService.validarResponsable(asignacion.getProyecto(), rm);
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
        notificacionService.crear(asignacion.getProyecto().getPm(), "ASIGNACION_RECHAZADA_RM", CategoriaNotificacion.ASIGNACION,
                "Asignación rechazada por el RM",
                "El RM rechazó a " + asignacion.getColaborador().getNombre() + " " + asignacion.getColaborador().getApellido()
                        + " en el proyecto \"" + asignacion.getProyecto().getNombre() + "\". Motivo: " + motivoLimpio,
                "PROYECTO", asignacion.getProyecto().getId());

        auditoriaService.registrar(
                rm, "RECHAZO_ASIGNACION", "ASIGNACION", asignacion.getId(),
                "El RM rechazó la asignación. Motivo: " + motivoLimpio);
    }

    /**
     * Finaliza (desasigna) una asignación activa, recalcula la disponibilidad del colaborador y le
     * notifica. Si se envía una calificación, registra además la evaluación de la asignación.
     *
     * @param asignacionId asignación ACTIVA
     * @param motivo motivo de finalización, obligatorio
     * @param observacion observación opcional; se audita recortada a 220 caracteres
     * @param calificacion calificación opcional del desempeño
     * @param feedback comentario de la evaluación
     * @param rmId id del RM de la sesión
     * @throws IllegalArgumentException si la asignación o el RM no existen, o falta el motivo
     * @throws IllegalStateException si la asignación no está activa o el RM no es responsable del proyecto
     */
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
        rmResponsableService.validarResponsable(asignacion.getProyecto(), rm);
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

    /**
     * Criterio único para buscar colaboradores y proponer asignaciones: proyecto ACTIVO o EN_ESPERA.
     *
     * @param proyecto proyecto evaluado; puede ser {@code null}
     * @return {@code true} si admite asignaciones
     */
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

    // Proponer (TASK-053): misma regla que el directorio; un registro pendiente o rechazado no se asigna.
    private Usuario obtenerColaboradorAsignable(Long colaboradorId) {
        return usuarioRepository.findColaboradorVisibleRmById(colaboradorId)
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
                "El colaborador alcanzó el límite de asignaciones activas; debes justificar la excepción.",
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

    // TASK-054: no se propone ni se aprueba con más horas que las disponibles del colaborador.
    private void validarHorasDentroDeDisponibilidad(BigDecimal horas, Usuario colaborador) {
        BigDecimal disponibles = horasDisponibles(colaborador);
        if (horas != null && horas.compareTo(disponibles) > 0) {
            throw new IllegalArgumentException("Las horas propuestas (" + horas.stripTrailingZeros().toPlainString()
                    + " h/sem) superan las horas disponibles del colaborador ("
                    + disponibles.stripTrailingZeros().toPlainString() + " h/sem).");
        }
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
