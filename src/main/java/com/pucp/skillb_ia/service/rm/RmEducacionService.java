package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmEducacionView;
import com.pucp.skillb_ia.model.Educacion;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoCertificado;
import com.pucp.skillb_ia.model.enums.NivelExperiencia;
import com.pucp.skillb_ia.repository.EducacionRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.NotificacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Revisión de la formación académica por el Resource Manager (TASK-014).
 * Aprobar solo valida la formación: no cambia el nivel general de experiencia ni el sueldo.
 */
@Service
public class RmEducacionService {
    private static final String ROL_RM = "RESOURCE_MANAGER";
    private static final int LONGITUD_MAXIMA_BUSQUEDA = 100;
    private static final int LONGITUD_MAXIMA_MOTIVO = 300;

    public static final int TAMANIO_PAGINA = 6;

    public record FiltrosEducacion(String busqueda, String institucion) {
    }

    public record PaginaEducacion(List<RmEducacionView> filas, int paginaActual,
                                  int totalPaginas, long totalRegistros) {
    }

    private final EducacionRepository educacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;

    public RmEducacionService(EducacionRepository educacionRepository,
                              UsuarioRepository usuarioRepository,
                              AuditoriaService auditoriaService,
                              NotificacionService notificacionService) {
        this.educacionRepository = educacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
    }

    @Transactional(readOnly = true)
    public List<RmEducacionView> listarPendientes() {
        return educacionRepository.findActivasByEstadoConDetalle(EstadoCertificado.PENDIENTE).stream()
                .map(educacion -> crearVista(educacion, 0))
                .toList();
    }

    @Transactional(readOnly = true)
    public long contarPendientes() {
        return educacionRepository.countByEstadoAndActivoTrue(EstadoCertificado.PENDIENTE);
    }

    /** Instituciones distintas (sin repetir) de las formaciones recibidas, en orden alfabético. */
    public List<String> institucionesDisponibles(List<RmEducacionView> formaciones) {
        return formaciones.stream()
                .map(item -> item.getEducacion().getInstitucion())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /** Normaliza los filtros GET: recorta la búsqueda y descarta instituciones que no son una opción válida. */
    public FiltrosEducacion normalizarFiltros(String busqueda, String institucion,
                                              List<String> institucionesDisponibles) {
        String busquedaLimpia = busqueda == null ? "" : busqueda.trim();
        if (busquedaLimpia.length() > LONGITUD_MAXIMA_BUSQUEDA) {
            busquedaLimpia = busquedaLimpia.substring(0, LONGITUD_MAXIMA_BUSQUEDA);
        }
        String institucionValida = institucion == null ? null : institucionesDisponibles.stream()
                .filter(nombre -> nombre.equalsIgnoreCase(institucion.trim()))
                .findFirst().orElse(null);
        return new FiltrosEducacion(busquedaLimpia.isEmpty() ? null : busquedaLimpia, institucionValida);
    }

    /** Filtra (búsqueda e institución) y pagina la bandeja de formaciones pendientes. */
    public PaginaEducacion paginarPendientes(List<RmEducacionView> pendientes,
                                             FiltrosEducacion filtros, String pagina) {
        String busqueda = textoComparable(filtros.busqueda());
        List<RmEducacionView> filtrados = pendientes.stream()
                .filter(item -> busqueda.isEmpty() || textoComparable(item.getTextoBusqueda()).contains(busqueda))
                .filter(item -> filtros.institucion() == null
                        || filtros.institucion().equals(item.getEducacion().getInstitucion()))
                .toList();

        int totalPaginas = Math.max(1, (int) Math.ceil(filtrados.size() / (double) TAMANIO_PAGINA));
        int paginaActual = Math.min(Math.max(1, numeroPagina(pagina)), totalPaginas);
        int desde = (paginaActual - 1) * TAMANIO_PAGINA;
        int hasta = Math.min(desde + TAMANIO_PAGINA, filtrados.size());
        List<RmEducacionView> filas = desde < hasta ? filtrados.subList(desde, hasta) : List.of();
        return new PaginaEducacion(filas, paginaActual, totalPaginas, filtrados.size());
    }

    @Transactional(readOnly = true)
    public RmEducacionView obtener(Long educacionId) {
        Educacion educacion = obtenerEntidad(educacionId);
        return crearVista(educacion, educacionRepository.countByColaboradorAndEstadoAndActivoTrue(
                educacion.getColaborador(), EstadoCertificado.APROBADO));
    }

    /** URL del documento tal como la generó ArchivoAlmacenamientoService (ruta local /uploads/... o URL de S3). */
    @Transactional(readOnly = true)
    public String obtenerUrlDocumento(Long educacionId) {
        Educacion educacion = obtenerEntidad(educacionId);
        if (educacion.getArchivoUrl() == null || educacion.getArchivoUrl().isBlank()) {
            throw new IllegalStateException("Esta formación académica no tiene un documento adjunto.");
        }
        return educacion.getArchivoUrl();
    }

    @Transactional
    public void aprobar(Long educacionId, Long rmId) {
        Usuario rm = obtenerRm(rmId);
        Educacion educacion = obtenerEntidad(educacionId);
        validarPendiente(educacion);

        educacion.setEstado(EstadoCertificado.APROBADO);
        educacion.setMotivoRechazo(null);
        educacion.setRevisadoPor(rm);
        educacion.setFechaRevision(LocalDateTime.now());
        educacionRepository.save(educacion);

        notificacionService.crear(educacion.getColaborador(), "EDUCACION_APROBADA", CategoriaNotificacion.HABILIDAD,
                "Formación académica aprobada",
                "Tu formación \"" + educacion.getTitulo() + "\" (" + educacion.getInstitucion() + ") fue aprobada.",
                "EDUCACION", educacion.getId());

        auditoriaService.registrar(rm, "APROBACION_EDUCACION", "EDUCACION", educacionId,
                "Se aprobó la formación académica \"" + educacion.getTitulo() + "\" (" + educacion.getInstitucion()
                        + ") de " + nombreCompleto(educacion.getColaborador()) + ".");
    }

    @Transactional
    public void rechazar(Long educacionId, String motivo, Long rmId) {
        Usuario rm = obtenerRm(rmId);
        Educacion educacion = obtenerEntidad(educacionId);
        validarPendiente(educacion);
        String motivoLimpio = motivoObligatorio(motivo);

        educacion.setEstado(EstadoCertificado.RECHAZADO);
        educacion.setMotivoRechazo(motivoLimpio);
        educacion.setRevisadoPor(rm);
        educacion.setFechaRevision(LocalDateTime.now());
        educacionRepository.save(educacion);

        notificacionService.crear(educacion.getColaborador(), "EDUCACION_RECHAZADA", CategoriaNotificacion.HABILIDAD,
                "Formación académica rechazada",
                "Tu formación \"" + educacion.getTitulo() + "\" (" + educacion.getInstitucion()
                        + ") fue rechazada. Motivo: " + motivoLimpio,
                "EDUCACION", educacion.getId());

        auditoriaService.registrar(rm, "RECHAZO_EDUCACION", "EDUCACION", educacionId,
                "Se rechazó la formación académica \"" + educacion.getTitulo() + "\" de "
                        + nombreCompleto(educacion.getColaborador()) + ". Motivo: " + motivoLimpio);
    }

    private RmEducacionView crearVista(Educacion educacion, long formacionesAprobadas) {
        Usuario colaborador = educacion.getColaborador();
        return new RmEducacionView(
                educacion,
                nombreCompleto(colaborador),
                iniciales(colaborador),
                valor(colaborador.getCargo() != null ? colaborador.getCargo().getNombre() : null, "Cargo sin registrar"),
                colaborador.getNivelExperiencia() == null
                        ? "Sin definir" : textoNivel(colaborador.getNivelExperiencia()),
                educacion.getRevisadoPor() == null ? "—" : nombreCompleto(educacion.getRevisadoPor()),
                formacionesAprobadas);
    }

    private Educacion obtenerEntidad(Long id) {
        return educacionRepository.findByIdConDetalle(id)
                .filter(Educacion::isActivo)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la formación académica solicitada."));
    }

    private Usuario obtenerRm(Long id) {
        Usuario rm = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el Resource Manager."));
        if (!ROL_RM.equals(rm.getRol().getNombre())) {
            throw new IllegalStateException("La operación requiere un Resource Manager.");
        }
        return rm;
    }

    private void validarPendiente(Educacion educacion) {
        if (educacion.getEstado() != EstadoCertificado.PENDIENTE) {
            throw new IllegalStateException("Esta formación académica ya fue revisada.");
        }
    }

    private String motivoObligatorio(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Debes indicar el motivo del rechazo.");
        }
        String limpio = motivo.trim();
        if (limpio.length() > LONGITUD_MAXIMA_MOTIVO) {
            throw new IllegalArgumentException("El motivo del rechazo no puede superar los 300 caracteres.");
        }
        return limpio;
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

    private String textoNivel(NivelExperiencia nivel) {
        if (nivel == NivelExperiencia.SEMI_SENIOR) return "Semi Senior";
        String texto = nivel.name().toLowerCase();
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
