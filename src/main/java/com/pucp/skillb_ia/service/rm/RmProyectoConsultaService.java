package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmProyectoView;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ProyectoHabilidadRequeridaRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class RmProyectoConsultaService {
    // Listado de proyectos (TASK-028): filtros GET y paginación en el servidor.
    public static final int TAMANIO_PAGINA = 6;
    // "with": vacantes > 0; "full": vacantes == 0 (mismo criterio que usaba rm-proyectos.js).
    public static final List<String> OPCIONES_VACANTES = List.of("with", "full");
    private static final int LONGITUD_MAXIMA_BUSQUEDA = 100;
    // Igual que toLocaleLowerCase("es") del JS anterior: sin mayúsculas, con tildes.
    private static final Locale LOCALE_BUSQUEDA = Locale.forLanguageTag("es");

    /** Filtros ya validados: los valores nulos significan "Todos". */
    public record FiltrosProyecto(String busqueda, String estado, String prioridad, String vacantes) {
    }

    /** Contadores de las tarjetas sobre todos los proyectos. */
    public record ContadoresProyectos(long totalProyectos, long conVacantes, long pendientesRm, long enRevision) {
    }

    public record PaginaProyectos(List<RmProyectoView> filas, int paginaActual, int totalPaginas,
                                  long totalRegistros, ContadoresProyectos contadores) {
    }

    private final ProyectoRepository proyectoRepository;
    private final AsignacionRepository asignacionRepository;
    private final ProyectoHabilidadRequeridaRepository habilidadRequeridaRepository;
    private final RmPresupuestoService presupuestoService;

    public RmProyectoConsultaService(ProyectoRepository proyectoRepository,
                                     AsignacionRepository asignacionRepository,
                                     ProyectoHabilidadRequeridaRepository habilidadRequeridaRepository,
                                     RmPresupuestoService presupuestoService) {
        this.proyectoRepository = proyectoRepository;
        this.asignacionRepository = asignacionRepository;
        this.habilidadRequeridaRepository = habilidadRequeridaRepository;
        this.presupuestoService = presupuestoService;
    }

    @Transactional(readOnly = true)
    public List<RmProyectoView> listar() {
        return proyectoRepository.findAllConPmOrderByFechaCreacionDesc().stream()
                .map(this::crearVista)
                .toList();
    }

    /**
     * Normaliza los parámetros GET del listado. Vacíos, "all" o valores desconocidos
     * significan "Todos" (null); la búsqueda se recorta a 100 caracteres.
     */
    public FiltrosProyecto normalizarFiltros(String busqueda, String estado, String prioridad, String vacantes) {
        String busquedaLimpia = busqueda == null ? "" : busqueda.trim();
        if (busquedaLimpia.length() > LONGITUD_MAXIMA_BUSQUEDA) {
            busquedaLimpia = busquedaLimpia.substring(0, LONGITUD_MAXIMA_BUSQUEDA);
        }
        return new FiltrosProyecto(
                busquedaLimpia.isEmpty() ? null : busquedaLimpia,
                opcionValida(estado, Arrays.stream(EstadoProyecto.values()).map(Enum::name).toList()),
                opcionValida(prioridad, Arrays.stream(Prioridad.values()).map(Enum::name).toList()),
                opcionValida(vacantes, OPCIONES_VACANTES));
    }

    /**
     * Filtra, cuenta y pagina el listado conservando el orden de listar() (fecha de creación
     * descendente). Los contadores se calculan sobre todos los proyectos, sin filtros ni página.
     */
    @Transactional(readOnly = true)
    public PaginaProyectos listarPagina(FiltrosProyecto filtros, String pagina) {
        List<RmProyectoView> todos = listar();
        String busqueda = filtros.busqueda() == null ? "" : filtros.busqueda().toLowerCase(LOCALE_BUSQUEDA);
        List<RmProyectoView> filtrados = todos.stream()
                .filter(item -> busqueda.isEmpty()
                        || item.getTextoBusqueda().toLowerCase(LOCALE_BUSQUEDA).contains(busqueda))
                .filter(item -> filtros.estado() == null
                        || filtros.estado().equals(item.getProyecto().getEstado().name()))
                .filter(item -> filtros.prioridad() == null
                        || filtros.prioridad().equals(item.getProyecto().getPrioridad().name()))
                .filter(item -> filtros.vacantes() == null
                        || ("with".equals(filtros.vacantes()) && item.getVacantes() > 0)
                        || ("full".equals(filtros.vacantes()) && item.getVacantes() == 0))
                .toList();

        int totalPaginas = Math.max(1, (int) Math.ceil(filtrados.size() / (double) TAMANIO_PAGINA));
        int paginaActual = Math.min(Math.max(1, numeroPagina(pagina)), totalPaginas);
        int desde = (paginaActual - 1) * TAMANIO_PAGINA;
        int hasta = Math.min(desde + TAMANIO_PAGINA, filtrados.size());
        List<RmProyectoView> filas = desde < hasta ? filtrados.subList(desde, hasta) : List.of();

        ContadoresProyectos contadores = new ContadoresProyectos(
                todos.size(),
                todos.stream().filter(RmProyectoView::isConVacantesParaDotacion).count(),
                todos.stream().mapToLong(RmProyectoView::getPendientesRm).sum(),
                todos.stream().filter(item -> item.getProyecto().getEstado() == EstadoProyecto.EN_REVISION).count());
        return new PaginaProyectos(filas, paginaActual, totalPaginas, filtrados.size(), contadores);
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

    @Transactional(readOnly = true)
    public RmProyectoView obtener(Long id) {
        Proyecto proyecto = proyectoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el proyecto solicitado."));
        proyecto.getPm().getNombre();
        return crearVista(proyecto);
    }

    private RmProyectoView crearVista(Proyecto proyecto) {
        List<Asignacion> asignaciones = asignacionRepository.findByProyecto(proyecto);
        List<Asignacion> activas = asignaciones.stream()
                .filter(a -> a.getEstado() == EstadoAsignacion.ACTIVA)
                .toList();

        int pendientesRm = (int) asignaciones.stream()
                .filter(this::requiereDecisionRm)
                .count();

        List<RmProyectoView.MiembroEquipo> equipo = activas.stream()
                .map(a -> crearMiembro(a.getColaborador(), a, proyecto))
                .toList();

        List<RmProyectoView.RequisitoTalento> requisitos = habilidadRequeridaRepository.findByProyecto(proyecto)
                .stream()
                .map(r -> new RmProyectoView.RequisitoTalento(
                        r.getHabilidad().getNombre(),
                        r.getNivelRequerido() == null ? "Sin definir" : textoEnum(r.getNivelRequerido().name()),
                        r.getCantidadPersonas(),
                          r.getHorasSemanales()))
                .toList();

        int vacantes = Math.max(0, proyecto.getColaboradoresRequeridos() - activas.size());
        return new RmProyectoView(
                proyecto,
                nombreCompleto(proyecto.getPm()),
                textoEnum(proyecto.getEstado().name()),
                textoEnum(proyecto.getPrioridad().name()),
                activas.size(),
                vacantes,
                pendientesRm,
                equipo,
                requisitos,
                presupuestoService.calcularResumen(proyecto));
    }

    private boolean requiereDecisionRm(Asignacion asignacion) {
        if (asignacion.getEstado() != EstadoAsignacion.PENDIENTE || asignacion.isAprobadoPorRm()) return false;
        return asignacion.getOrigen() == OrigenAsignacion.PROPUESTA_PM
                || asignacion.getOrigen() == OrigenAsignacion.SOLICITADA_COLABORADOR;
    }

    private RmProyectoView.MiembroEquipo crearMiembro(Usuario usuario, Asignacion asignacion, Proyecto proyecto) {
        String nivel = usuario.getNivelExperiencia() == null
                ? "Sin definir"
                : (usuario.getNivelExperiencia().name().equals("SEMI_SENIOR")
                    ? "Semi Senior" : textoEnum(usuario.getNivelExperiencia().name()));
        RmPresupuestoService.CostoAsignacion costo =
                presupuestoService.calcularCosto(proyecto, usuario, asignacion.getHorasSemanales());
        return new RmProyectoView.MiembroEquipo(
                usuario.getId(),
                nombreCompleto(usuario),
                iniciales(usuario),
                valor(usuario.getCargo() != null ? usuario.getCargo().getNombre() : null, "Cargo sin registrar"),
                nivel,
                asignacion.getHorasSemanales(),
                costo.costoSemanal(),
                costo.costoTotal(),
                costo.calculable());
    }

    private String nombreCompleto(Usuario usuario) {
        String completo = (valor(usuario.getNombre(), "") + " " + valor(usuario.getApellido(), "")).trim();
        return completo.isEmpty() ? usuario.getCorreo() : completo;
    }

    private String iniciales(Usuario usuario) {
        String nombre = valor(usuario.getNombre(), "").trim();
        String apellido = valor(usuario.getApellido(), "").trim();
        String texto = (nombre.isEmpty() ? "" : nombre.substring(0, 1))
                + (apellido.isEmpty() ? "" : apellido.substring(0, 1));
        return texto.isEmpty() ? "CO" : texto.toUpperCase();
    }

    private String valor(String texto, String alternativa) {
        return texto == null || texto.isBlank() ? alternativa : texto;
    }

    private String textoEnum(String valor) {
        String texto = valor.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
