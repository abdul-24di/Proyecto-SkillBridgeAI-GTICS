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
            List.of(EstadoColaboradorCurso.SOLICITADO, EstadoColaboradorCurso.EN_CURSO);

    private final CursoRepository cursoRepository;
    private final ColaboradorCursoRepository colaboradorCursoRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionRepository notificacionRepository;

    public RmCursoService(CursoRepository cursoRepository,
                          ColaboradorCursoRepository colaboradorCursoRepository,
                          UsuarioRepository usuarioRepository,
                          NotificacionRepository notificacionRepository) {
        this.cursoRepository = cursoRepository;
        this.colaboradorCursoRepository = colaboradorCursoRepository;
        this.usuarioRepository = usuarioRepository;
        this.notificacionRepository = notificacionRepository;
    }

    @Transactional(readOnly = true)
    public RmCursoView.Catalogo obtenerCatalogo(String busqueda, String categoria,
                                                 String duracion) {
        List<Curso> cursosActivos = cursoRepository.findByActivoTrueOrderByNombreAsc();
        List<ColaboradorCurso> inscripciones = colaboradorCursoRepository.findAllConDetalle();

        List<RmCursoView.CursoItem> cursos = cursosActivos.stream()
                .filter(curso -> coincide(curso, busqueda, categoria, duracion))
                .map(curso -> new RmCursoView.CursoItem(
                        curso.getId(), curso.getNombre(),
                        valor(curso.getDescripcion(), "Sin descripción registrada."),
                        valor(curso.getCategoria(), "Sin categoría"), curso.getHoras(),
                        inscripciones.stream()
                                .filter(item -> item.getCurso().getId().equals(curso.getId()))
                                .filter(item -> item.getEstado() == EstadoColaboradorCurso.EN_CURSO)
                                .count()))
                .toList();

        List<String> categorias = cursosActivos.stream()
                .map(Curso::getCategoria).filter(Objects::nonNull)
                .filter(item -> !item.isBlank()).distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        YearMonth mesActual = YearMonth.now();
        return new RmCursoView.Catalogo(
                cursos, categorias, cursosActivos.size(),
                inscripciones.stream().filter(this::esSolicitudPendiente).count(),
                inscripciones.stream().filter(item -> item.getEstado() == EstadoColaboradorCurso.EN_CURSO).count(),
                inscripciones.stream()
                        .filter(item -> item.getOrigen() == OrigenCurso.ASIGNADO_POR_RM)
                        .filter(item -> perteneceAlMes(item.getFechaSolicitud(), mesActual)).count());
    }

    /** Solicitudes de colaboradores en estado SOLICITADO (para el dashboard del RM). */
    @Transactional(readOnly = true)
    public long contarSolicitudesPendientes() {
        return colaboradorCursoRepository.countByOrigenAndEstado(
                OrigenCurso.SOLICITUD_COLABORADOR, EstadoColaboradorCurso.SOLICITADO);
    }

    @Transactional(readOnly = true)
    public RmCursoView.Bandeja obtenerBandeja(String busqueda, String estadoValor,
                                               String origenValor) {
        List<ColaboradorCurso> todas = colaboradorCursoRepository.findAllConDetalle();
        EstadoColaboradorCurso estado = enumSeguro(EstadoColaboradorCurso.class, estadoValor);
        OrigenCurso origen = enumSeguro(OrigenCurso.class, origenValor);
        String textoBusqueda = normalizar(busqueda);

        List<RmCursoView.InscripcionItem> filtradas = todas.stream()
                .filter(item -> estado == null || item.getEstado() == estado)
                .filter(item -> origen == null || item.getOrigen() == origen)
                .filter(item -> textoBusqueda.isBlank() || textoBusqueda(item).contains(textoBusqueda))
                .map(this::crearInscripcionItem).toList();

        YearMonth mesActual = YearMonth.now();
        return new RmCursoView.Bandeja(
                filtradas,
                todas.stream().filter(this::esSolicitudPendiente).count(),
                todas.stream()
                        .filter(item -> item.getOrigen() == OrigenCurso.SOLICITUD_COLABORADOR)
                        .filter(item -> item.getEstado() == EstadoColaboradorCurso.EN_CURSO)
                        .filter(item -> perteneceAlMes(item.getFechaRespuesta(), mesActual)).count(),
                todas.stream().filter(item -> item.getEstado() == EstadoColaboradorCurso.RECHAZADO)
                        .filter(item -> perteneceAlMes(item.getFechaRespuesta(), mesActual)).count(),
                todas.stream().filter(item -> item.getEstado() == EstadoColaboradorCurso.EN_CURSO).count());
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

    private ColaboradorCurso obtenerSolicitudPendiente(Long id) {
        ColaboradorCurso inscripcion = colaboradorCursoRepository.findByIdConDetalle(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la solicitud de curso."));
        if (!esSolicitudPendiente(inscripcion)) {
            throw new IllegalStateException("La solicitud ya fue gestionada o no fue enviada por un colaborador.");
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
                item.getEstado().name(), textoEstado(item.getEstado()), claseEstado(item.getEstado()),
                item.getFechaSolicitud(), item.getJustificacion(), item.getMotivoRespuesta(), esSolicitudPendiente(item));
    }

    private boolean esSolicitudPendiente(ColaboradorCurso item) {
        return item.getOrigen() == OrigenCurso.SOLICITUD_COLABORADOR
                && item.getEstado() == EstadoColaboradorCurso.SOLICITADO;
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

    private String textoEstado(EstadoColaboradorCurso estado) {
        return switch (estado) {
            case SOLICITADO -> "Pendiente";
            case EN_CURSO -> "En curso";
            case COMPLETADO -> "Completado";
            case RECHAZADO -> "Rechazado";
        };
    }

    private String claseEstado(EstadoColaboradorCurso estado) {
        return switch (estado) {
            case SOLICITADO -> "bg-yellow-lt text-yellow";
            case EN_CURSO -> "bg-blue-lt text-blue";
            case COMPLETADO -> "bg-green-lt text-green";
            case RECHAZADO -> "bg-red-lt text-red";
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
