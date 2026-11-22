package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.dto.ForoPublicacionView;
import com.pucp.skillb_ia.dto.ForoRespuestaView;
import com.pucp.skillb_ia.model.Foro;
import com.pucp.skillb_ia.model.PublicacionForo;
import com.pucp.skillb_ia.model.RespuestaForo;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.TipoVoto;
import com.pucp.skillb_ia.repository.PublicacionForoRepository;
import com.pucp.skillb_ia.repository.RespuestaForoRepository;
import com.pucp.skillb_ia.repository.VotoPublicacionRepository;
import com.pucp.skillb_ia.repository.VotoRespuestaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Detalle de foro común a RM, PM, Colaborador y Admin (TASK-046): mismas publicaciones,
 * respuestas, "me gusta", solución y orden. Cada rol decide sus permisos con {@link PermisosForo}.
 */
@Service
public class ForoDetalleService {

    public static final String ORDEN_FECHA = "fecha";
    public static final String ORDEN_VOTOS = "votos";

    /**
     * Lo que el usuario puede hacer en el foro.
     *
     * @param votar           dar o quitar "me gusta"
     * @param participar      responder y marcar la solución de sus propias publicaciones
     * @param moderarSolucion marcar la solución en publicaciones ajenas
     * @param eliminarAjeno   eliminar publicaciones y respuestas de otros
     */
    public record PermisosForo(boolean votar, boolean participar, boolean moderarSolucion,
                               boolean eliminarAjeno) {
        public static final PermisosForo SOLO_LECTURA = new PermisosForo(false, false, false, false);
    }

    private final PublicacionForoRepository publicacionRepository;
    private final RespuestaForoRepository respuestaRepository;
    private final VotoPublicacionRepository votoPublicacionRepository;
    private final VotoRespuestaRepository votoRespuestaRepository;
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;

    public ForoDetalleService(PublicacionForoRepository publicacionRepository,
                              RespuestaForoRepository respuestaRepository,
                              VotoPublicacionRepository votoPublicacionRepository,
                              VotoRespuestaRepository votoRespuestaRepository,
                              AuditoriaService auditoriaService,
                              NotificacionService notificacionService) {
        this.publicacionRepository = publicacionRepository;
        this.respuestaRepository = respuestaRepository;
        this.votoPublicacionRepository = votoPublicacionRepository;
        this.votoRespuestaRepository = votoRespuestaRepository;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
    }

    /** "votos" (sin distinguir mayúsculas) o, para cualquier otro valor, "fecha". */
    public static String normalizarOrden(String orden) {
        return ORDEN_VOTOS.equalsIgnoreCase(orden == null ? "" : orden.trim()) ? ORDEN_VOTOS : ORDEN_FECHA;
    }

    /**
     * Publicaciones activas del foro con sus respuestas activas.
     * Publicaciones: "fecha" = más recientes primero; "votos" = más "me gusta" y luego más recientes.
     * Respuestas: la solución primero, luego más "me gusta" y luego la más antigua.
     *
     * @param usuario usuario que mira el foro; null para una vista de solo lectura sin "me gusta" propio
     */
    @Transactional(readOnly = true)
    public List<ForoPublicacionView> listar(Foro foro, Usuario usuario, PermisosForo permisos, String orden) {
        return publicacionRepository.findByForoConDetalle(foro).stream()
                .map(publicacion -> crearPublicacion(publicacion, usuario, permisos))
                .sorted(comparador(normalizarOrden(orden)))
                .toList();
    }

    /**
     * Marca o desmarca la respuesta como solución (una por publicación). Quien llama ya
     * validó que el usuario puede hacerlo; avisa al autor de la respuesta si es otra persona.
     */
    @Transactional
    public void alternarSolucion(Usuario usuario, RespuestaForo respuesta) {
        PublicacionForo publicacion = respuesta.getPublicacion();
        if (respuesta.isEsSolucion()) {
            respuesta.setEsSolucion(false);
            respuestaRepository.save(respuesta);
            auditoriaService.registrar(usuario, "DESMARCAR_SOLUCION_FORO", "RESPUESTA_FORO",
                    respuesta.getId(), "Desmarcó la respuesta como solución en \"" + publicacion.getTitulo() + "\".");
            return;
        }

        respuestaRepository.findByPublicacionAndEsSolucionTrue(publicacion).ifPresent(anterior -> {
            anterior.setEsSolucion(false);
            respuestaRepository.save(anterior);
        });
        respuesta.setEsSolucion(true);
        respuestaRepository.save(respuesta);
        auditoriaService.registrar(usuario, "MARCAR_SOLUCION_FORO", "RESPUESTA_FORO",
                respuesta.getId(), "Marcó una respuesta como solución en \"" + publicacion.getTitulo() + "\".");

        if (respuesta.getAutor() != null && !respuesta.getAutor().getId().equals(usuario.getId())) {
            notificacionService.crear(respuesta.getAutor(), "RESPUESTA_MARCADA_SOLUCION",
                    CategoriaNotificacion.MENSAJE,
                    "Tu respuesta fue marcada como solución",
                    nombreCompleto(usuario) + " marcó tu respuesta como la solución de \""
                            + publicacion.getTitulo() + "\".",
                    "FORO", publicacion.getForo().getId());
        }
    }

    /** Cargo del usuario o, si no tiene, el nombre legible de su rol. */
    public static String cargoAutor(Usuario usuario) {
        if (usuario.getCargo() != null) return usuario.getCargo().getNombre();
        String rol = usuario.getRol() == null ? "" : usuario.getRol().getNombre();
        return switch (rol) {
            case "PROJECT_MANAGER" -> "Project Manager";
            case "RESOURCE_MANAGER" -> "Resource Manager";
            case "ADMINISTRADOR" -> "Administrador";
            default -> "Colaborador";
        };
    }

    private ForoPublicacionView crearPublicacion(PublicacionForo publicacion, Usuario usuario,
                                                 PermisosForo permisos) {
        boolean esAutorPublicacion = esAutor(usuario, publicacion.getAutor());
        boolean puedeMarcar = usuario != null && permisos.participar()
                && (esAutorPublicacion || permisos.moderarSolucion());

        List<ForoRespuestaView> respuestas = respuestaRepository.findByPublicacionConAutor(publicacion).stream()
                .map(respuesta -> crearRespuesta(respuesta, usuario, permisos, puedeMarcar))
                .sorted(Comparator.comparing(ForoRespuestaView::isSolucion).reversed()
                        .thenComparing(Comparator.comparingLong(ForoRespuestaView::getTotalLikes).reversed())
                        .thenComparing(ForoRespuestaView::getFechaCreacion))
                .toList();

        long likes = votoPublicacionRepository.countByPublicacionAndTipo(publicacion, TipoVoto.POSITIVO);
        boolean meGusta = usuario != null
                && votoPublicacionRepository.findByUsuarioAndPublicacion(usuario, publicacion).isPresent();
        Usuario autor = publicacion.getAutor();
        return new ForoPublicacionView(
                publicacion.getId(), autor.getId(), publicacion.getTitulo(), publicacion.getContenido(),
                nombreCompleto(autor), iniciales(autor), cargoAutor(autor),
                publicacion.getEtiqueta() == null ? null : publicacion.getEtiqueta().getNombre(),
                publicacion.getFechaCreacion(), likes, meGusta, respuestas,
                usuario != null && permisos.votar(),
                usuario != null && permisos.participar(),
                usuario != null && (esAutorPublicacion || permisos.eliminarAjeno()));
    }

    private ForoRespuestaView crearRespuesta(RespuestaForo respuesta, Usuario usuario,
                                             PermisosForo permisos, boolean puedeMarcar) {
        long likes = votoRespuestaRepository.countByRespuestaAndTipo(respuesta, TipoVoto.POSITIVO);
        boolean meGusta = usuario != null
                && votoRespuestaRepository.findByUsuarioAndRespuesta(usuario, respuesta).isPresent();
        Usuario autor = respuesta.getAutor();
        return new ForoRespuestaView(
                respuesta.getId(), autor.getId(), respuesta.getContenido(),
                nombreCompleto(autor), iniciales(autor), cargoAutor(autor),
                respuesta.getFechaCreacion(), respuesta.isEsSolucion(), likes, meGusta,
                usuario != null && permisos.votar(),
                usuario != null && (esAutor(usuario, autor) || permisos.eliminarAjeno()),
                puedeMarcar);
    }

    private Comparator<ForoPublicacionView> comparador(String orden) {
        Comparator<ForoPublicacionView> masRecientes =
                Comparator.comparing(ForoPublicacionView::getFechaCreacion, Comparator.reverseOrder());
        if (ORDEN_VOTOS.equals(orden)) {
            return Comparator.comparingLong(ForoPublicacionView::getTotalLikes).reversed()
                    .thenComparing(masRecientes);
        }
        return masRecientes;
    }

    private static boolean esAutor(Usuario usuario, Usuario autor) {
        return usuario != null && autor != null && autor.getId().equals(usuario.getId());
    }

    private static String nombreCompleto(Usuario usuario) {
        String nombre = usuario.getNombre() == null ? "" : usuario.getNombre();
        String apellido = usuario.getApellido() == null ? "" : usuario.getApellido();
        String completo = (nombre + " " + apellido).trim();
        return completo.isEmpty() ? "Usuario" : completo;
    }

    private static String iniciales(Usuario usuario) {
        String nombre = usuario.getNombre() == null ? "" : usuario.getNombre().trim();
        String apellido = usuario.getApellido() == null ? "" : usuario.getApellido().trim();
        String iniciales = (nombre.isEmpty() ? "" : nombre.substring(0, 1))
                + (apellido.isEmpty() ? "" : apellido.substring(0, 1));
        return iniciales.isEmpty() ? "US" : iniciales.toUpperCase();
    }
}
