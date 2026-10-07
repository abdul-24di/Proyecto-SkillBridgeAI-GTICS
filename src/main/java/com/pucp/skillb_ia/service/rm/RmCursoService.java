package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmCursoView;
import com.pucp.skillb_ia.model.ColaboradorCurso;
import com.pucp.skillb_ia.model.Curso;
import com.pucp.skillb_ia.model.Notificacion;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoColaboradorCurso;
import com.pucp.skillb_ia.model.enums.OrigenCurso;
import com.pucp.skillb_ia.repository.ColaboradorCursoRepository;
import com.pucp.skillb_ia.repository.CursoRepository;
import com.pucp.skillb_ia.repository.NotificacionRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.col.ColaboradorCursoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class RmCursoService {
    private static final String ROL_RM = "RESOURCE_MANAGER";
    private static final String ROL_COLABORADOR = "COLABORADOR";
    private static final List<EstadoColaboradorCurso> ESTADOS_DUPLICADOS =
            List.of(EstadoColaboradorCurso.SOLICITADO, EstadoColaboradorCurso.EN_CURSO,
                    EstadoColaboradorCurso.EVIDENCIA_PENDIENTE);
    // Paginación en el servidor (TASK-030).
    public static final int TAMANIO_PAGINA_CATALOGO = 6;
    public static final int TAMANIO_PAGINA_BANDEJA = 10;
    public static final List<String> OPCIONES_DURACION = List.of("corta", "media", "larga");
    private static final int LONGITUD_MAXIMA_BUSQUEDA = 100;

    /** Filtros del catálogo ya validados: los valores nulos significan "Todas". */
    public record FiltrosCatalogo(String busqueda, String categoria, String duracion) {
    }

    /** Filtros de la bandeja ya validados: los valores nulos significan "Todos". */
    public record FiltrosBandeja(String busqueda, EstadoColaboradorCurso estado, OrigenCurso origen) {
    }

    private final CursoRepository cursoRepository;
    private final ColaboradorCursoRepository colaboradorCursoRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionRepository notificacionRepository;
    private final AuditoriaService auditoriaService;
    private final ColaboradorCursoService colaboradorCursoService;

    public RmCursoService(CursoRepository cursoRepository,
                          ColaboradorCursoRepository colaboradorCursoRepository,
                          UsuarioRepository usuarioRepository,
                          NotificacionRepository notificacionRepository,
                          AuditoriaService auditoriaService,
                          ColaboradorCursoService colaboradorCursoService) {
        this.cursoRepository = cursoRepository;
        this.colaboradorCursoRepository = colaboradorCursoRepository;
        this.usuarioRepository = usuarioRepository;
        this.notificacionRepository = notificacionRepository;
        this.auditoriaService = auditoriaService;
        this.colaboradorCursoService = colaboradorCursoService;
    }

    /**
     * Normaliza los parámetros GET del catálogo. Vacíos o desconocidos significan "Todas";
     * la categoría debe existir entre los cursos activos y la búsqueda se recorta a 100 caracteres.
     */
    @Transactional(readOnly = true)
    public FiltrosCatalogo normalizarFiltrosCatalogo(String busqueda, String categoria, String duracion) {
        String categoriaValida = categoria == null || categoria.isBlank() ? null
                : categoriasDisponibles(cursoRepository.findByActivoTrueOrderByNombreAsc()).stream()
                        .filter(item -> normalizar(item).equals(normalizar(categoria)))
                        .findFirst().orElse(null);
        String duracionValida = duracion == null ? null : OPCIONES_DURACION.stream()
                .filter(opcion -> opcion.equalsIgnoreCase(duracion.trim())).findFirst().orElse(null);
        return new FiltrosCatalogo(busquedaLimpia(busqueda), categoriaValida, duracionValida);
    }

    /**
     * Normaliza los parámetros GET de la bandeja. Sin parámetro "estado" se muestran las pendientes
     * (SOLICITADO); vacío o desconocido significa "Todos". Origen vacío o desconocido: "Todos".
     */
    public FiltrosBandeja normalizarFiltrosBandeja(String busqueda, String estado, String origen) {
        EstadoColaboradorCurso estadoValido = estado == null
                ? EstadoColaboradorCurso.SOLICITADO
                : enumSeguro(EstadoColaboradorCurso.class, estado.trim());
        return new FiltrosBandeja(busquedaLimpia(busqueda), estadoValido,
                enumSeguro(OrigenCurso.class, origen == null ? null : origen.trim()));
    }

    /**
     * Filtra y pagina el catálogo (6 por página, por nombre). Las categorías y los indicadores
     * se calculan sobre todos los cursos activos e inscripciones, sin filtros ni página.
     */
    @Transactional(readOnly = true)
    public RmCursoView.Catalogo obtenerCatalogo(FiltrosCatalogo filtros, String pagina) {
        List<Curso> cursosActivos = cursoRepository.findByActivoTrueOrderByNombreAsc();
        List<ColaboradorCurso> inscripciones = colaboradorCursoRepository.findAllConDetalle();

        List<Curso> filtrados = cursosActivos.stream()
                .filter(curso -> coincide(curso, filtros.busqueda(), filtros.categoria(), filtros.duracion()))
                .toList();
        int totalPaginas = totalPaginas(filtrados.size(), TAMANIO_PAGINA_CATALOGO);
        int paginaActual = paginaActual(pagina, totalPaginas);

        List<RmCursoView.CursoItem> cursos = recortar(filtrados, paginaActual, TAMANIO_PAGINA_CATALOGO).stream()
                .map(curso -> new RmCursoView.CursoItem(
                        curso.getId(), curso.getNombre(),
                        valor(curso.getDescripcion(), "Sin descripción registrada."),
                        valor(curso.getCategoria(), "Sin categoría"), curso.getHoras(),
                        inscripciones.stream()
                                .filter(item -> item.getCurso().getId().equals(curso.getId()))
                                .filter(item -> item.getEstado() == EstadoColaboradorCurso.EN_CURSO)
                                .count()))
                .toList();

        YearMonth mesActual = YearMonth.now();
        return new RmCursoView.Catalogo(
                cursos, paginaActual, totalPaginas, filtrados.size(),
                categoriasDisponibles(cursosActivos), cursosActivos.size(),
                inscripciones.stream().filter(this::esSolicitudPendiente).count(),
                inscripciones.stream().filter(item -> item.getEstado() == EstadoColaboradorCurso.EN_CURSO).count(),
                inscripciones.stream()
                        .filter(item -> item.getOrigen() == OrigenCurso.ASIGNADO_POR_RM)
                        .filter(item -> perteneceAlMes(item.getFechaSolicitud(), mesActual)).count());
    }

    /**
     * Pasa a NO_COMPLETADO los cursos EN_CURSO vencidos sin evidencia de todos los colaboradores, con la
     * misma regla, auditoría y notificación que usa el colaborador. Se llama antes de consultar.
     */
    public void marcarCursosSinEvidenciaVencidos() {
        colaboradorCursoService.marcarTodosLosCursosSinEvidenciaVencidos();
    }

    /** Solicitudes de colaboradores en estado SOLICITADO (para el dashboard del RM). */
    @Transactional(readOnly = true)
    public long contarSolicitudesPendientes() {
        return colaboradorCursoRepository.countByOrigenAndEstado(
                OrigenCurso.SOLICITUD_COLABORADOR, EstadoColaboradorCurso.SOLICITADO);
    }

    /**
     * Filtra y pagina la bandeja (10 por página, fecha de solicitud descendente). Los cinco
     * indicadores se calculan sobre todas las inscripciones, sin filtros ni página.
     */
    @Transactional(readOnly = true)
    public RmCursoView.Bandeja obtenerBandeja(FiltrosBandeja filtros, String pagina) {
        List<ColaboradorCurso> todas = colaboradorCursoRepository.findAllConDetalle();
        EstadoColaboradorCurso estado = filtros.estado();
        OrigenCurso origen = filtros.origen();
        String textoBusqueda = normalizar(filtros.busqueda());

        List<ColaboradorCurso> filtradas = todas.stream()
                .filter(item -> estado == null || item.getEstado() == estado)
                .filter(item -> origen == null || item.getOrigen() == origen)
                .filter(item -> textoBusqueda.isBlank() || textoBusqueda(item).contains(textoBusqueda))
                .toList();
        int totalPaginas = totalPaginas(filtradas.size(), TAMANIO_PAGINA_BANDEJA);
        int paginaActual = paginaActual(pagina, totalPaginas);

        YearMonth mesActual = YearMonth.now();
        return new RmCursoView.Bandeja(
                recortar(filtradas, paginaActual, TAMANIO_PAGINA_BANDEJA).stream()
                        .map(this::crearInscripcionItem).toList(),
                paginaActual, totalPaginas, filtradas.size(),
                todas.stream().filter(this::esSolicitudPendiente).count(),
                todas.stream()
                        .filter(item -> item.getOrigen() == OrigenCurso.SOLICITUD_COLABORADOR)
                        .filter(item -> item.getEstado() == EstadoColaboradorCurso.EN_CURSO)
                        .filter(item -> perteneceAlMes(item.getFechaRespuesta(), mesActual)).count(),
                todas.stream().filter(item -> item.getEstado() == EstadoColaboradorCurso.RECHAZADO)
                        .filter(item -> perteneceAlMes(item.getFechaRespuesta(), mesActual)).count(),
                todas.stream().filter(item -> item.getEstado() == EstadoColaboradorCurso.EN_CURSO).count(),
                todas.stream().filter(this::esEvidenciaPendiente).count());
    }

    @Transactional(readOnly = true)
    public List<RmCursoView.ColaboradorOpcion> listarColaboradoresActivos() {
        return usuarioRepository.findActivosByRolNombre(ROL_COLABORADOR).stream()
                .map(usuario -> new RmCursoView.ColaboradorOpcion(
                        usuario.getId(), nombreCompleto(usuario),
                        valor(usuario.getCargo() != null ? usuario.getCargo().getNombre() : null, "Cargo sin registrar")))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RmCursoView.CursoItem> listarCursosActivos() {
        return cursoRepository.findByActivoTrueOrderByNombreAsc().stream()
                .map(curso -> new RmCursoView.CursoItem(
                        curso.getId(), curso.getNombre(), curso.getDescripcion(),
                        valor(curso.getCategoria(), "Sin categoría"), curso.getHoras(), 0))
                .toList();
    }

    @Transactional
    public ColaboradorCurso asignarDirectamente(Long colaboradorId, Long cursoId,
                                                 String motivo, Long rmId) {
        Usuario rm = obtenerRm(rmId);
        Usuario colaborador = obtenerColaborador(colaboradorId);
        Curso curso = obtenerCursoActivo(cursoId);
        if (curso.getFechaFin() != null && curso.getFechaFin().isBefore(java.time.LocalDate.now())) {
            throw new IllegalStateException("Este curso ya terminó: su fecha de fin ya pasó.");
        }
        if (colaboradorCursoRepository.existsByColaboradorAndCursoAndEstadoIn(
                colaborador, curso, ESTADOS_DUPLICADOS)) {
            throw new IllegalStateException(
                    "El colaborador ya tiene una solicitud pendiente o una inscripción activa en este curso.");
        }

        ColaboradorCurso inscripcion = new ColaboradorCurso();
        inscripcion.setColaborador(colaborador);
        inscripcion.setCurso(curso);
        inscripcion.setOrigen(OrigenCurso.ASIGNADO_POR_RM);
        inscripcion.setEstado(EstadoColaboradorCurso.EN_CURSO);
        inscripcion.setAsignadoPor(rm);
        inscripcion.setFechaRespuesta(LocalDateTime.now());
        inscripcion.setMotivoRespuesta(validarMotivo(motivo, "El motivo de la asignación es obligatorio."));
        inscripcion = colaboradorCursoRepository.save(inscripcion);
        notificar(inscripcion, "CURSO_ASIGNADO", "Nuevo curso asignado",
                "El Resource Manager te inscribió en “" + curso.getNombre() + "”.");
        return inscripcion;
    }

    @Transactional
    public ColaboradorCurso aprobar(Long inscripcionId, String motivo, Long rmId) {
        Usuario rm = obtenerRm(rmId);
        ColaboradorCurso inscripcion = obtenerSolicitudPendiente(inscripcionId);
        String motivoValidado = validarMotivo(motivo, "El motivo de la aprobación es obligatorio.");
        obtenerColaborador(inscripcion.getColaborador().getId());
        obtenerCursoActivo(inscripcion.getCurso().getId());
        if (colaboradorCursoRepository.existsByColaboradorAndCursoAndEstado(
                inscripcion.getColaborador(), inscripcion.getCurso(), EstadoColaboradorCurso.EN_CURSO)) {
            throw new IllegalStateException("El colaborador ya tiene una inscripción activa en este curso.");
        }
        inscripcion.setEstado(EstadoColaboradorCurso.EN_CURSO);
        inscripcion.setAsignadoPor(rm);
        inscripcion.setFechaRespuesta(LocalDateTime.now());
        inscripcion.setMotivoRespuesta(motivoValidado);
        inscripcion = colaboradorCursoRepository.save(inscripcion);
        notificar(inscripcion, "CURSO_APROBADO", "Solicitud de curso aprobada",
                "Tu solicitud para “" + inscripcion.getCurso().getNombre() + "” fue aprobada.");
        return inscripcion;
    }

    @Transactional
    public ColaboradorCurso rechazar(Long inscripcionId, String motivo, Long rmId) {
        Usuario rm = obtenerRm(rmId);
        ColaboradorCurso inscripcion = obtenerSolicitudPendiente(inscripcionId);
        String motivoValidado = validarMotivo(motivo, "El motivo del rechazo es obligatorio.");
        inscripcion.setEstado(EstadoColaboradorCurso.RECHAZADO);
        inscripcion.setAsignadoPor(rm);
        inscripcion.setFechaRespuesta(LocalDateTime.now());
        inscripcion.setMotivoRespuesta(motivoValidado);
        inscripcion = colaboradorCursoRepository.save(inscripcion);
        notificar(inscripcion, "CURSO_RECHAZADO", "Solicitud de curso rechazada",
                "Tu solicitud para “" + inscripcion.getCurso().getNombre()
                        + "” fue rechazada. Motivo: " + motivoValidado);
        return inscripcion;
    }

    // TASK-054: el revisor y la fecha se guardan aparte; asignadoPor sigue siendo quien aprobó la inscripción.
    @Transactional
    public ColaboradorCurso aprobarEvidencia(Long inscripcionId, Long rmId) {
        Usuario rm = obtenerRm(rmId);
        ColaboradorCurso inscripcion = obtenerEvidenciaPendiente(inscripcionId);
        LocalDateTime ahora = LocalDateTime.now();
        inscripcion.setEstado(EstadoColaboradorCurso.COMPLETADO);
        inscripcion.setFechaCompletado(ahora);
        inscripcion.setEvidenciaRevisadaPor(rm);
        inscripcion.setFechaRevisionEvidencia(ahora);
        inscripcion = colaboradorCursoRepository.save(inscripcion);
        notificar(inscripcion, "CURSO_COMPLETADO", "Curso completado",
                "Tu evidencia para “" + inscripcion.getCurso().getNombre() + "” fue validada. ¡Curso completado!");
        auditoriaService.registrar(rm, "APROBAR_EVIDENCIA_CURSO", "COLABORADOR_CURSO", inscripcion.getId(),
                recortar(detalleAuditoria("Aprobó la evidencia del curso", inscripcion)
                        + " Horas confirmadas: " + inscripcion.getCurso().getHoras().toPlainString()
                        + " h, sumadas a " + YearMonth.from(ahora) + ".", 500),
                EstadoColaboradorCurso.EVIDENCIA_PENDIENTE.name(), EstadoColaboradorCurso.COMPLETADO.name(), null);
        return inscripcion;
    }

    @Transactional
    public ColaboradorCurso rechazarEvidencia(Long inscripcionId, String motivo, Long rmId) {
        Usuario rm = obtenerRm(rmId);
        ColaboradorCurso inscripcion = obtenerEvidenciaPendiente(inscripcionId);
        String motivoValidado = validarMotivo(motivo, "El motivo del rechazo de la evidencia es obligatorio.");
        inscripcion.setEstado(EstadoColaboradorCurso.EN_CURSO);
        inscripcion.setMotivoRespuesta(motivoValidado);
        inscripcion.setEvidenciaRevisadaPor(rm);
        inscripcion.setFechaRevisionEvidencia(LocalDateTime.now());
        inscripcion = colaboradorCursoRepository.save(inscripcion);
        notificar(inscripcion, "EVIDENCIA_CURSO_RECHAZADA", "Evidencia de curso rechazada",
                "Tu evidencia para “" + inscripcion.getCurso().getNombre()
                        + "” fue rechazada. Motivo: " + motivoValidado + " Puedes volver a subirla.");
        auditoriaService.registrar(rm, "RECHAZAR_EVIDENCIA_CURSO", "COLABORADOR_CURSO", inscripcion.getId(),
                recortar(detalleAuditoria("Rechazó la evidencia del curso", inscripcion)
                        + " Motivo: " + motivoValidado, 500),
                EstadoColaboradorCurso.EVIDENCIA_PENDIENTE.name(), EstadoColaboradorCurso.EN_CURSO.name(), null);
        return inscripcion;
    }

    private String detalleAuditoria(String accion, ColaboradorCurso inscripcion) {
        return accion + " “" + inscripcion.getCurso().getNombre() + "” (id " + inscripcion.getCurso().getId()
                + ") de " + nombreCompleto(inscripcion.getColaborador())
                + " (id " + inscripcion.getColaborador().getId() + ").";
    }

    private String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 3) + "...";
    }

    private ColaboradorCurso obtenerSolicitudPendiente(Long id) {
        ColaboradorCurso inscripcion = colaboradorCursoRepository.findByIdConDetalle(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la solicitud de curso."));
        if (!esSolicitudPendiente(inscripcion)) {
            throw new IllegalStateException("La solicitud ya fue gestionada o no fue enviada por un colaborador.");
        }
        return inscripcion;
    }

    private ColaboradorCurso obtenerEvidenciaPendiente(Long id) {
        ColaboradorCurso inscripcion = colaboradorCursoRepository.findByIdConDetalle(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la inscripción al curso."));
        if (inscripcion.getEstado() != EstadoColaboradorCurso.EVIDENCIA_PENDIENTE) {
            throw new IllegalStateException("Esta inscripción no tiene una evidencia pendiente de revisión.");
        }
        return inscripcion;
    }

    private Usuario obtenerRm(Long id) {
        return usuarioRepository.findById(id)
                .filter(Usuario::isActivo)
                .filter(usuario -> ROL_RM.equals(usuario.getRol().getNombre()))
                .orElseThrow(() -> new IllegalArgumentException("No se encontró un Resource Manager activo."));
    }

    private Usuario obtenerColaborador(Long id) {
        return usuarioRepository.findById(id)
                .filter(Usuario::isActivo)
                .filter(usuario -> ROL_COLABORADOR.equals(usuario.getRol().getNombre()))
                .orElseThrow(() -> new IllegalArgumentException("No se encontró un colaborador activo."));
    }

    private Curso obtenerCursoActivo(Long id) {
        return cursoRepository.findById(id).filter(Curso::isActivo)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró un curso activo."));
    }

    private void notificar(ColaboradorCurso inscripcion, String tipo,
                           String titulo, String descripcion) {
        Notificacion notificacion = new Notificacion();
        notificacion.setUsuario(inscripcion.getColaborador());
        notificacion.setTipo(tipo);
        notificacion.setCategoria(CategoriaNotificacion.CURSO);
        notificacion.setTitulo(titulo);
        notificacion.setDescripcion(descripcion.length() <= 400
                ? descripcion : descripcion.substring(0, 397) + "...");
        notificacion.setEntidad("COLABORADOR_CURSO");
        notificacion.setEntidadId(inscripcion.getId());
        notificacionRepository.save(notificacion);
    }

    private List<String> categoriasDisponibles(List<Curso> cursosActivos) {
        return cursosActivos.stream()
                .map(Curso::getCategoria).filter(Objects::nonNull)
                .filter(item -> !item.isBlank()).distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private String busquedaLimpia(String busqueda) {
        String limpia = busqueda == null ? "" : busqueda.trim();
        if (limpia.length() > LONGITUD_MAXIMA_BUSQUEDA) limpia = limpia.substring(0, LONGITUD_MAXIMA_BUSQUEDA);
        return limpia.isEmpty() ? null : limpia;
    }

    private int totalPaginas(int totalRegistros, int tamanioPagina) {
        return Math.max(1, (int) Math.ceil(totalRegistros / (double) tamanioPagina));
    }

    /** Página pedida entre 1 y el total: vacía o no numérica → 1, menor que 1 → 1, mayor que el total → última. */
    private int paginaActual(String pagina, int totalPaginas) {
        int numero;
        try {
            numero = pagina == null ? 1 : Integer.parseInt(pagina.trim());
        } catch (NumberFormatException ex) {
            numero = 1;
        }
        return Math.min(Math.max(1, numero), totalPaginas);
    }

    private <T> List<T> recortar(List<T> registros, int paginaActual, int tamanioPagina) {
        int desde = (paginaActual - 1) * tamanioPagina;
        int hasta = Math.min(desde + tamanioPagina, registros.size());
        return desde < hasta ? registros.subList(desde, hasta) : List.of();
    }

    private boolean coincide(Curso curso, String busqueda, String categoria, String duracion) {
        String filtro = normalizar(busqueda);
        boolean coincideTexto = filtro.isBlank() || normalizar(
                curso.getNombre() + " " + valor(curso.getDescripcion(), "") + " "
                        + valor(curso.getCategoria(), "")).contains(filtro);
        boolean coincideCategoria = categoria == null || categoria.isBlank()
                || normalizar(curso.getCategoria()).equals(normalizar(categoria));
        BigDecimal horas = curso.getHoras();
        boolean coincideDuracion = switch (valor(duracion, "")) {
            case "corta" -> horas.compareTo(BigDecimal.valueOf(12)) <= 0;
            case "media" -> horas.compareTo(BigDecimal.valueOf(12)) > 0
                    && horas.compareTo(BigDecimal.valueOf(30)) <= 0;
            case "larga" -> horas.compareTo(BigDecimal.valueOf(30)) > 0;
            default -> true;
        };
        return coincideTexto && coincideCategoria && coincideDuracion;
    }

    private RmCursoView.InscripcionItem crearInscripcionItem(ColaboradorCurso item) {
        Usuario colaborador = item.getColaborador();
        Curso curso = item.getCurso();
        return new RmCursoView.InscripcionItem(
                item.getId(), colaborador.getId(), nombreCompleto(colaborador),
                iniciales(colaborador), valor(colaborador.getCargo() != null ? colaborador.getCargo().getNombre() : null, "Cargo sin registrar"),
                curso.getId(), curso.getNombre(), valor(curso.getCategoria(), "Sin categoría"),
                curso.getHoras(), item.getOrigen().name(), textoOrigen(item.getOrigen()),
                item.getEstado().name(), textoEstado(item), claseEstado(item),
                item.getFechaSolicitud(), item.getJustificacion(), item.getMotivoRespuesta(), esSolicitudPendiente(item),
                item.getEvidenciaUrl(), esEvidenciaPendiente(item),
                item.getEvidenciaRevisadaPor() != null ? nombreCompleto(item.getEvidenciaRevisadaPor()) : null,
                item.getFechaRevisionEvidencia());
    }

    private boolean esSolicitudPendiente(ColaboradorCurso item) {
        return item.getOrigen() == OrigenCurso.SOLICITUD_COLABORADOR
                && item.getEstado() == EstadoColaboradorCurso.SOLICITADO;
    }

    private boolean esEvidenciaPendiente(ColaboradorCurso item) {
        return item.getEstado() == EstadoColaboradorCurso.EVIDENCIA_PENDIENTE;
    }

    private String textoBusqueda(ColaboradorCurso item) {
        return normalizar(nombreCompleto(item.getColaborador()) + " "
                + valor(item.getColaborador().getCargo() != null ? item.getColaborador().getCargo().getNombre() : null, "") + " "
                + item.getCurso().getNombre() + " " + valor(item.getCurso().getCategoria(), ""));
    }

    private boolean perteneceAlMes(LocalDateTime fecha, YearMonth periodo) {
        return fecha != null && YearMonth.from(fecha).equals(periodo);
    }

    private <E extends Enum<E>> E enumSeguro(Class<E> tipo, String valor) {
        if (valor == null || valor.isBlank()) return null;
        return Arrays.stream(tipo.getEnumConstants())
                .filter(item -> item.name().equals(valor)).findFirst().orElse(null);
    }

    private String validarMotivo(String motivo, String mensajeVacio) {
        String limpio = motivo == null ? "" : motivo.trim();
        if (limpio.isBlank()) throw new IllegalArgumentException(mensajeVacio);
        if (limpio.length() > 500) throw new IllegalArgumentException("El motivo no puede superar 500 caracteres.");
        return limpio;
    }

    private String nombreCompleto(Usuario usuario) {
        String completo = (valor(usuario.getNombre(), "") + " "
                + valor(usuario.getApellido(), "")).trim();
        return completo.isBlank() ? usuario.getCorreo() : completo;
    }

    private String iniciales(Usuario usuario) {
        String nombre = valor(usuario.getNombre(), "").trim();
        String apellido = valor(usuario.getApellido(), "").trim();
        String resultado = (nombre.isBlank() ? "" : nombre.substring(0, 1))
                + (apellido.isBlank() ? "" : apellido.substring(0, 1));
        return resultado.isBlank() ? "CO" : resultado.toUpperCase(Locale.ROOT);
    }

    private String textoOrigen(OrigenCurso origen) {
        return origen == OrigenCurso.SOLICITUD_COLABORADOR
                ? "Solicitud del colaborador" : "Asignado por RM";
    }

    private String textoEstado(ColaboradorCurso item) {
        //Si volvió a "En curso" pero ya tenía una evidencia guardada, es porque el RM
        //la rechazó y está esperando que el colaborador vuelva a subir una nueva.
        if (item.getEstado() == EstadoColaboradorCurso.EN_CURSO && item.getEvidenciaUrl() != null) {
            return "Evidencia rechazada";
        }
        return switch (item.getEstado()) {
            case SOLICITADO -> "Pendiente";
            case EN_CURSO -> "En curso";
            case EVIDENCIA_PENDIENTE -> "Evidencia en revisión";
            case COMPLETADO -> "Completado";
            case RECHAZADO -> "Rechazado";
            case NO_COMPLETADO -> "No completado";
        };
    }

    private String claseEstado(ColaboradorCurso item) {
        if (item.getEstado() == EstadoColaboradorCurso.EN_CURSO && item.getEvidenciaUrl() != null) {
            return "bg-red-lt text-red";
        }
        return switch (item.getEstado()) {
            case SOLICITADO -> "bg-yellow-lt text-yellow";
            case EN_CURSO -> "bg-blue-lt text-blue";
            case EVIDENCIA_PENDIENTE -> "bg-purple-lt text-purple";
            case COMPLETADO -> "bg-green-lt text-green";
            case RECHAZADO -> "bg-red-lt text-red";
            case NO_COMPLETADO -> "bg-secondary-lt text-secondary";
        };
    }

    private String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    private String valor(String texto, String alternativa) {
        return texto == null || texto.isBlank() ? alternativa : texto;
    }
}
