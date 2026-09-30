package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmForoView;
import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.TipoVoto;
import com.pucp.skillb_ia.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class RmForoConsultaService {
    public static final int TAMANIO_PAGINA = 4;
    public static final String ESTADO_GENERAL = "GENERAL";
    public static final String ACTIVIDAD_RECIENTE = "recent";
    public static final String ACTIVIDAD_ANTIGUA = "older";
    private static final int LONGITUD_MAXIMA_BUSQUEDA = 100;

    /** Filtros ya validados: los valores nulos significan "Todos". */
    public record FiltrosForo(String busqueda, String estado, String actividad) {
    }

    /** Indicadores de las tarjetas sobre todos los foros, sin filtros ni página. */
    public record IndicadoresForos(int totalForos, long totalActivos, int totalPublicaciones,
                                   LocalDateTime ultimaActividad) {
    }

    public record PaginaForos(List<RmForoView> foros, int paginaActual, int totalPaginas,
                              long totalRegistros, IndicadoresForos indicadores) {
    }

    private final ForoRepository foroRepository;
    private final PublicacionForoRepository publicacionRepository;
    private final RespuestaForoRepository respuestaRepository;
    private final VotoPublicacionRepository votoPublicacionRepository;
    private final VotoRespuestaRepository votoRespuestaRepository;
    private final AsignacionRepository asignacionRepository;

    public RmForoConsultaService(ForoRepository foroRepository,
                                 PublicacionForoRepository publicacionRepository,
                                 RespuestaForoRepository respuestaRepository,
                                 VotoPublicacionRepository votoPublicacionRepository,
                                 VotoRespuestaRepository votoRespuestaRepository,
                                 AsignacionRepository asignacionRepository) {
        this.foroRepository = foroRepository;
        this.publicacionRepository = publicacionRepository;
        this.respuestaRepository = respuestaRepository;
        this.votoPublicacionRepository = votoPublicacionRepository;
        this.votoRespuestaRepository = votoRespuestaRepository;
        this.asignacionRepository = asignacionRepository;
    }

    /** El RM consulta todos los foros, incluidos los privados de proyecto. */
    @Transactional(readOnly = true)
    public List<RmForoView> listar() {
        return foroRepository.findTodosConProyectoYPm().stream()
                .map(foro -> crearVista(foro, "fecha"))
                .toList();
    }

    /**
     * Normaliza los parámetros GET del listado. Vacíos, "all" o valores desconocidos
     * significan "Todos" (null); la búsqueda se recorta a 100 caracteres.
     */
    public FiltrosForo normalizarFiltros(String busqueda, String estado, String actividad) {
        String busquedaLimpia = busqueda == null ? "" : busqueda.trim();
        if (busquedaLimpia.length() > LONGITUD_MAXIMA_BUSQUEDA) {
            busquedaLimpia = busquedaLimpia.substring(0, LONGITUD_MAXIMA_BUSQUEDA);
        }
        List<String> estados = new ArrayList<>();
        estados.add(ESTADO_GENERAL);
        Arrays.stream(EstadoProyecto.values()).map(Enum::name).forEach(estados::add);
        return new FiltrosForo(
                busquedaLimpia.isEmpty() ? null : busquedaLimpia,
                opcionValida(estado, estados),
                opcionValida(actividad, List.of(ACTIVIDAD_RECIENTE, ACTIVIDAD_ANTIGUA)));
    }

    /**
     * Filtra y pagina el listado conservando el orden de listar() (fecha de creación
     * descendente). Los indicadores se calculan sobre todos los foros, sin filtros ni página.
     */
    @Transactional(readOnly = true)
    public PaginaForos listarPagina(FiltrosForo filtros, String pagina) {
        List<RmForoView> todos = listar();
        IndicadoresForos indicadores = new IndicadoresForos(
                todos.size(),
                todos.stream().filter(foro -> "ACTIVO".equals(foro.getProyectoEstadoCodigo())).count(),
                todos.stream().mapToInt(RmForoView::getTotalPublicaciones).sum(),
                todos.stream().map(RmForoView::getUltimaActividad)
                        .filter(Objects::nonNull)
                        .max(LocalDateTime::compareTo)
                        .orElse(null));

        // Como el JS anterior: sin tildes ni mayúsculas sobre textoBusqueda.
        String busqueda = normalizarTexto(filtros.busqueda());
        List<RmForoView> filtrados = todos.stream()
                .filter(foro -> busqueda.isEmpty()
                        || normalizarTexto(foro.getTextoBusqueda()).contains(busqueda))
                .filter(foro -> filtros.estado() == null
                        || filtros.estado().equals(foro.getProyectoEstadoCodigo()))
                .filter(foro -> filtros.actividad() == null
                        || ACTIVIDAD_RECIENTE.equals(filtros.actividad()) == foro.isActividadReciente())
                .toList();

        int totalPaginas = Math.max(1, (int) Math.ceil(filtrados.size() / (double) TAMANIO_PAGINA));
        int paginaActual = Math.min(Math.max(1, numeroPagina(pagina)), totalPaginas);
        int desde = (paginaActual - 1) * TAMANIO_PAGINA;
        int hasta = Math.min(desde + TAMANIO_PAGINA, filtrados.size());
        List<RmForoView> foros = desde < hasta ? filtrados.subList(desde, hasta) : List.of();
        return new PaginaForos(foros, paginaActual, totalPaginas, filtrados.size(), indicadores);
    }

    @Transactional(readOnly = true)
    public RmForoView obtener(Long id, String orden) {
        Foro foro = foroRepository.findByIdConProyectoYPm(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el foro solicitado."));
        return crearVista(foro, "votos".equalsIgnoreCase(orden) ? "votos" : "fecha");
    }

    /** Id del foro del proyecto (como máximo uno), para enlazarlo desde su detalle. */
    @Transactional(readOnly = true)
    public Optional<Long> buscarIdPorProyecto(Proyecto proyecto) {
        return foroRepository.findByProyecto(proyecto).map(Foro::getId);
    }

    private RmForoView crearVista(Foro foro, String orden) {
        List<RmForoView.Publicacion> publicaciones = publicacionRepository
                .findByForoConDetalle(foro).stream()
                .map(this::crearPublicacion)
                .sorted(comparadorPublicaciones(orden))
                .toList();

        Set<Long> participantes = new HashSet<>();
        publicaciones.forEach(publicacion -> {
            participantes.add(publicacion.getAutorId());
            publicacion.getRespuestas().forEach(
                    respuesta -> participantes.add(respuesta.getAutorId()));
        });

        LocalDateTime ultimaActividad = publicaciones.stream()
                .flatMap(publicacion -> {
                    List<LocalDateTime> fechas = new ArrayList<>();
                    fechas.add(publicacion.getFechaCreacion());
                    publicacion.getRespuestas().forEach(respuesta -> fechas.add(respuesta.getFechaCreacion()));
                    return fechas.stream();
                })
                .max(LocalDateTime::compareTo)
                .orElse(foro.getFechaCreacion());

        List<String> etiquetas = publicaciones.stream()
                .map(RmForoView.Publicacion::getEtiqueta)
                .filter(etiqueta -> etiqueta != null && !etiqueta.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        Proyecto proyecto = foro.getProyecto();
        int integrantes = proyecto == null ? participantes.size()
                : (int) asignacionRepository.countByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA);
        int requeridos = proyecto == null ? participantes.size() : proyecto.getColaboradoresRequeridos();

        return new RmForoView(
                foro.getId(), foro.getNombre(), textoEnum(foro.getTipo().name()), foro.isEsPublico(),
                proyecto == null ? null : proyecto.getId(),
                proyecto == null ? "Comunidad general" : proyecto.getNombre(),
                proyecto == null ? "General" : textoEnum(proyecto.getEstado().name()),
                proyecto == null ? "GENERAL" : proyecto.getEstado().name(),
                proyecto == null ? "bg-azure-lt" : claseEstado(proyecto.getEstado()),
                proyecto == null ? "No aplica" : textoEnum(proyecto.getPrioridad().name()),
                proyecto == null ? "bg-secondary-lt" : clasePrioridad(proyecto),
                proyecto == null ? "Organización" : nombreCompleto(proyecto.getPm()),
                integrantes, requeridos, participantes.size(), ultimaActividad,
                publicaciones, etiquetas);
    }

    private RmForoView.Publicacion crearPublicacion(PublicacionForo publicacion) {
        List<RmForoView.Respuesta> respuestas = respuestaRepository
                .findByPublicacionConAutor(publicacion).stream()
                .map(this::crearRespuesta)
                .sorted(Comparator.comparingLong(RmForoView.Respuesta::getPuntaje).reversed()
                        .thenComparing(RmForoView.Respuesta::getFechaCreacion))
                .toList();
        long positivos = votoPublicacionRepository
                .countByPublicacionAndTipo(publicacion, TipoVoto.POSITIVO);
        long negativos = votoPublicacionRepository
                .countByPublicacionAndTipo(publicacion, TipoVoto.NEGATIVO);
        return new RmForoView.Publicacion(
                publicacion.getId(), publicacion.getAutor().getId(),
                publicacion.getTitulo(), publicacion.getContenido(),
                nombreCompleto(publicacion.getAutor()), iniciales(publicacion.getAutor()),
                cargoAutor(publicacion.getAutor()),
                publicacion.getEtiqueta() == null ? null : publicacion.getEtiqueta().getNombre(),
                publicacion.getFechaCreacion(), positivos, negativos, respuestas);
    }

    private RmForoView.Respuesta crearRespuesta(RespuestaForo respuesta) {
        long positivos = votoRespuestaRepository
                .countByRespuestaAndTipo(respuesta, TipoVoto.POSITIVO);
        long negativos = votoRespuestaRepository
                .countByRespuestaAndTipo(respuesta, TipoVoto.NEGATIVO);
        return new RmForoView.Respuesta(
                respuesta.getId(), respuesta.getAutor().getId(), respuesta.getContenido(),
                nombreCompleto(respuesta.getAutor()), iniciales(respuesta.getAutor()),
                cargoAutor(respuesta.getAutor()), respuesta.isEsSolucion(),
                respuesta.getFechaCreacion(), positivos, negativos);
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

    private String normalizarTexto(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private Comparator<RmForoView.Publicacion> comparadorPublicaciones(String orden) {
        if ("votos".equals(orden)) {
            return Comparator.comparingLong(RmForoView.Publicacion::getPuntaje).reversed()
                    .thenComparing(RmForoView.Publicacion::getFechaCreacion,
                            Comparator.reverseOrder());
        }
        return Comparator.comparing(RmForoView.Publicacion::getFechaCreacion).reversed();
    }

    private String claseEstado(EstadoProyecto estado) {
        return switch (estado) {
            case ACTIVO -> "bg-green-lt";
            case EN_REVISION -> "bg-yellow-lt";
            case RECHAZADO -> "bg-red-lt";
            case EN_ESPERA -> "bg-orange-lt";
            case FINALIZADO -> "bg-blue-lt";
            case CANCELADO -> "bg-secondary-lt";
        };
    }

    private String clasePrioridad(Proyecto proyecto) {
        return switch (proyecto.getPrioridad()) {
            case ALTA -> "bg-red-lt text-red";
            case MEDIA -> "bg-yellow-lt text-yellow";
            case BAJA -> "bg-blue-lt text-blue";
        };
    }

    private String cargoAutor(Usuario usuario) {
        if (usuario.getCargo() != null) return usuario.getCargo().getNombre();
        return switch (usuario.getRol().getNombre()) {
            case "PROJECT_MANAGER" -> "Project Manager";
            case "RESOURCE_MANAGER" -> "Resource Manager";
            case "ADMINISTRADOR" -> "Administrador";
            default -> "Colaborador";
        };
    }

    private String nombreCompleto(Usuario usuario) {
        String nombre = valor(usuario.getNombre());
        String apellido = valor(usuario.getApellido());
        String completo = (nombre + " " + apellido).trim();
        return completo.isEmpty() ? usuario.getCorreo() : completo;
    }

    private String iniciales(Usuario usuario) {
        String nombre = valor(usuario.getNombre()).trim();
        String apellido = valor(usuario.getApellido()).trim();
        String iniciales = (nombre.isEmpty() ? "" : nombre.substring(0, 1))
                + (apellido.isEmpty() ? "" : apellido.substring(0, 1));
        return iniciales.isEmpty() ? "US" : iniciales.toUpperCase();
    }

    private String valor(String texto) {
        return texto == null ? "" : texto;
    }

    private String textoEnum(String valor) {
        String texto = valor.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
