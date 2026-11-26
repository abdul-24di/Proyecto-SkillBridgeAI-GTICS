package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.dto.PmForoView;
import com.pucp.skillb_ia.dto.ForoPublicacionView;
import com.pucp.skillb_ia.service.ForoDetalleService;
import com.pucp.skillb_ia.service.ForoDetalleService.PermisosForo;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Foro;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.PublicacionForo;
import com.pucp.skillb_ia.model.RespuestaForo;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.TipoForo;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ForoRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.PublicacionForoRepository;
import com.pucp.skillb_ia.repository.RespuestaForoRepository;
import com.pucp.skillb_ia.repository.VotoPublicacionRepository;
import com.pucp.skillb_ia.repository.VotoRespuestaRepository;
import com.pucp.skillb_ia.model.VotoPublicacion;
import com.pucp.skillb_ia.model.VotoRespuesta;
import com.pucp.skillb_ia.model.enums.TipoVoto;
import com.pucp.skillb_ia.service.ArchivoAlmacenamientoService;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.NotificacionService;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.Set;

/**
 * Servicio de foros para el Project Manager: listado de foros propios y de comunidad/anuncios,
 * publicación, respuestas, reacciones, moderación de soluciones, subida de imágenes y eliminación lógica.
 */
@Service
public class PmForoService {

    private static final Set<String> TIPOS_IMAGEN_PERMITIDOS = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final long TAMANO_MAXIMO_FOTO_BYTES = 5L * 1024 * 1024; // 5 MB

    private final ForoRepository foroRepository;
    private final PublicacionForoRepository publicacionRepository;
    private final RespuestaForoRepository respuestaRepository;
    private final AsignacionRepository asignacionRepository;
    private final ProyectoRepository proyectoRepository;
    private final VotoPublicacionRepository votoPublicacionRepository;
    private final VotoRespuestaRepository votoRespuestaRepository;

    private final AuditoriaService auditoriaService;
    private final ArchivoAlmacenamientoService archivoAlmacenamientoService;
    private final NotificacionService notificacionService;
    private final ForoDetalleService foroDetalleService;

    public PmForoService(ForoRepository foroRepository,
                         PublicacionForoRepository publicacionRepository,
                         RespuestaForoRepository respuestaRepository,
                         AsignacionRepository asignacionRepository,
                         ProyectoRepository proyectoRepository,
                         VotoPublicacionRepository votoPublicacionRepository,
                         VotoRespuestaRepository votoRespuestaRepository,
                         AuditoriaService auditoriaService,
                         ArchivoAlmacenamientoService archivoAlmacenamientoService,
                         NotificacionService notificacionService,
                         ForoDetalleService foroDetalleService) {
        this.notificacionService = notificacionService;
        this.foroDetalleService = foroDetalleService;
        this.foroRepository = foroRepository;
        this.publicacionRepository = publicacionRepository;
        this.respuestaRepository = respuestaRepository;
        this.asignacionRepository = asignacionRepository;
        this.proyectoRepository = proyectoRepository;
        this.votoPublicacionRepository = votoPublicacionRepository;
        this.votoRespuestaRepository = votoRespuestaRepository;
        this.auditoriaService = auditoriaService;
        this.archivoAlmacenamientoService = archivoAlmacenamientoService;
    }

    /** Valida formato (JPEG, PNG, GIF o WEBP) y tamaño (máximo 5 MB) y almacena una imagen para publicaciones del foro. */
    public String subirImagen(MultipartFile foto) {
        if (foto == null || foto.isEmpty()) {
            throw new IllegalArgumentException("La imagen no puede estar vacía.");
        }
        if (!TIPOS_IMAGEN_PERMITIDOS.contains(foto.getContentType())) {
            throw new IllegalArgumentException("Solo se permiten imágenes JPEG, PNG, GIF o WEBP.");
        }
        if (foto.getSize() > TAMANO_MAXIMO_FOTO_BYTES) {
            throw new IllegalArgumentException("La imagen no puede superar los 5 MB.");
        }

        String extension = ".jpg";
        if ("image/png".equals(foto.getContentType())) extension = ".png";
        else if ("image/gif".equals(foto.getContentType())) extension = ".gif";
        else if ("image/webp".equals(foto.getContentType())) extension = ".webp";

        String nombreArchivo = "foro-" + UUID.randomUUID() + extension;
        return archivoAlmacenamientoService.guardar(foto, "foro", nombreArchivo);
    }

    /**
     * Lista los foros de todos los proyectos que gestiona el PM. El foro de un proyecto se crea con la primera
     * publicación, así que los proyectos activos que aún no lo tienen también se muestran, sin temas.
     */
    @Transactional(readOnly = true)
    public List<PmForoView> listarForosPm(Usuario pm) {
        List<Proyecto> proyectosPm = proyectoRepository.findByPm(pm);
        List<PmForoView> vistas = new ArrayList<>();

        for (Proyecto proyecto : proyectosPm) {
            foroRepository.findByProyecto(proyecto).ifPresentOrElse(foro -> {
                List<PublicacionForo> pubs = publicacionRepository.findByForo(foro);
                int total = pubs.size();
                String ultimaTitulo = pubs.isEmpty() ? null : pubs.get(0).getTitulo();
                var ultimaFecha = pubs.isEmpty() ? null : pubs.get(0).getFechaCreacion();
                vistas.add(new PmForoView(foro, total, ultimaTitulo, ultimaFecha));
            }, () -> {
                if (proyecto.getEstado() == EstadoProyecto.ACTIVO) {
                    vistas.add(new PmForoView(proyecto));
                }
            });
        }
        return vistas;
    }

    /** Lista los foros que ven todos: comunidad, anuncios y proyectos públicos. */
    @Transactional(readOnly = true)
    public List<PmForoView> listarForosComunidad() {
        List<Foro> forosComunidad = foroRepository.findVisiblesParaTodos();
        List<PmForoView> vistas = new ArrayList<>();
        
        for (Foro foro : forosComunidad) {
            List<PublicacionForo> pubs = publicacionRepository.findByForo(foro);
            int total = pubs.size();
            String ultimaTitulo = pubs.isEmpty() ? null : pubs.get(0).getTitulo();
            var ultimaFecha = pubs.isEmpty() ? null : pubs.get(0).getFechaCreacion();
            vistas.add(new PmForoView(foro, total, ultimaTitulo, ultimaFecha));
        }
        return vistas;
    }

    /** Devuelve las publicaciones del foro del proyecto indicado (orden "fecha" o "votos"). */
    @Transactional(readOnly = true)
    public List<ForoPublicacionView> obtenerDetalle(Long proyectoId, Usuario pm, String orden) {
        Proyecto proyecto = obtenerProyectoDelPm(proyectoId, pm);
        return foroRepository.findByProyecto(proyecto)
                .map(foro -> foroDetalleService.listar(foro, pm, permisos(foro, pm), orden))
                .orElse(java.util.Collections.emptyList());
    }

    /** Obtiene las publicaciones de un foro específico validando permisos de acceso del usuario. */
    @Transactional(readOnly = true)
    public List<ForoPublicacionView> obtenerDetallePorForo(Long foroId, Usuario pm, String orden) {
        Foro foro = foroRepository.findById(foroId)
                .orElseThrow(() -> new IllegalArgumentException("Foro no encontrado"));

        validarAcceso(foro, pm);
        return foroDetalleService.listar(foro, pm, permisos(foro, pm), orden);
    }

    /**
     * El PM vota y participa en los foros que ve; modera la solución solo en el foro de sus
     * proyectos; elimina solo lo propio.
     */
    private PermisosForo permisos(Foro foro, Usuario pm) {
        return new PermisosForo(true, !foro.isAnuncios(), esForoDeSuProyecto(foro, pm), false);
    }

    private boolean esForoDeSuProyecto(Foro foro, Usuario pm) {
        return foro.getProyecto() != null && foro.getProyecto().getPm() != null
                && foro.getProyecto().getPm().getId().equals(pm.getId());
    }

    /** Devuelve la entidad {@link Foro} por su identificador, o {@code null} si no existe. */
    @Transactional(readOnly = true)
    public Foro obtenerForoBase(Long foroId) {
        return foroRepository.findById(foroId).orElse(null);
    }

    /** Crea una publicación en el foro del proyecto. Crea el Foro si aún no existe. */
    @Transactional
    public PublicacionForo publicar(Long proyectoId, String titulo, String contenido, Usuario pm) {
        Proyecto proyecto = obtenerProyectoDelPm(proyectoId, pm);
        Foro foro = foroRepository.findByProyecto(proyecto)
                .orElseGet(() -> crearForo(proyecto));

        return publicarInterno(foro, titulo, contenido, pm);
    }

    /** Crea una publicación en un foro existente verificando que el usuario pueda participar (no anuncios). */
    @Transactional
    public PublicacionForo publicarEnForo(Long foroId, String titulo, String contenido, Usuario pm) {
        Foro foro = foroRepository.findById(foroId)
                .orElseThrow(() -> new IllegalArgumentException("Foro no encontrado"));
                
        validarParticipacion(foro, pm);
        return publicarInterno(foro, titulo, contenido, pm);
    }

    private PublicacionForo publicarInterno(Foro foro, String titulo, String contenido, Usuario pm) {
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("El título de la publicación es obligatorio.");
        }
        String tituloLimpio = titulo.trim();
        if (tituloLimpio.length() > 200) {
            throw new IllegalArgumentException("El título no puede superar los 200 caracteres.");
        }
        if (contenidoForoVacio(contenido)) {
            throw new IllegalArgumentException("El contenido de la publicación es obligatorio.");
        }
        if (contenido.length() > 10000) {
            throw new IllegalArgumentException("El contenido no puede superar los 10000 caracteres.");
        }

        PublicacionForo pub = new PublicacionForo();
        pub.setForo(foro);
        pub.setAutor(pm);
        pub.setTitulo(tituloLimpio);
        pub.setContenido(contenido);
        PublicacionForo saved = publicacionRepository.save(pub);

        String nombreForo = foro.getProyecto() != null ? foro.getProyecto().getNombre() : foro.getNombre();
        auditoriaService.registrar(pm, "PUBLICAR", "FORO", foro.getId(),
                "Publicó en el foro '" + nombreForo + "': " + tituloLimpio);
        return saved;
    }

    /** Responde a una publicación existente. */
    @Transactional
    public RespuestaForo responder(Long publicacionId, String contenido, Usuario pm) {
        if (publicacionId == null) {
            throw new IllegalArgumentException("La publicación es obligatoria.");
        }
        if (contenidoForoVacio(contenido)) {
            throw new IllegalArgumentException("El contenido de la respuesta no puede estar vacío.");
        }
        if (contenido.length() > 5000) {
            throw new IllegalArgumentException("La respuesta no puede superar los 5000 caracteres.");
        }

        PublicacionForo publicacion = publicacionRepository.findById(publicacionId)
                .orElseThrow(() -> new IllegalArgumentException("Publicación no encontrada."));

        Foro foro = publicacion.getForo();
        validarParticipacion(foro, pm);

        RespuestaForo respuesta = new RespuestaForo();
        respuesta.setPublicacion(publicacion);
        respuesta.setAutor(pm);
        respuesta.setContenido(contenido);
        RespuestaForo guardada = respuestaRepository.save(respuesta);
        auditoriaService.registrar(pm, "CREAR_RESPUESTA_FORO", "RESPUESTA_FORO", guardada.getId(),
                "Respondió la publicación \"" + publicacion.getTitulo() + "\" en el foro \"" + foro.getNombre() + "\".");

        // Igual que en el foro del colaborador: se avisa al autor, salvo que se responda a sí mismo.
        if (!publicacion.getAutor().getId().equals(pm.getId())) {
            notificacionService.crear(publicacion.getAutor(), "RESPUESTA_FORO", CategoriaNotificacion.MENSAJE,
                    "Nueva respuesta en tu publicación",
                    pm.getNombre() + " " + pm.getApellido() + " respondió tu publicación \""
                            + publicacion.getTitulo() + "\".",
                    "FORO", publicacion.getForo().getId());
        }
        return guardada;
    }

    /** Verifica si el contenido HTML de una publicación o respuesta carece de texto visible y de imágenes. */
    public static boolean contenidoForoVacio(String contenido) {
        if (contenido == null || contenido.isBlank()) {
            return true;
        }
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parseBodyFragment(contenido);
        return doc.text().isBlank() && doc.select("img").isEmpty();
    }

    /**
     * Marca o desmarca una respuesta como solución de la publicación (TASK-046): puede el autor
     * de la publicación o el PM del proyecto dueño del foro. En un foro de comunidad, solo el autor.
     */
    @Transactional
    public void alternarSolucion(Usuario pm, Long respuestaId) {
        RespuestaForo respuesta = respuestaRepository.findById(respuestaId)
                .filter(RespuestaForo::isActivo)
                .orElseThrow(() -> new IllegalArgumentException("Respuesta no encontrada."));
        PublicacionForo publicacion = respuesta.getPublicacion();

        boolean esAutor = publicacion.getAutor() != null && publicacion.getAutor().getId().equals(pm.getId());
        if (!esAutor && !esForoDeSuProyecto(publicacion.getForo(), pm)) {
            throw new IllegalStateException(
                    "Solo el autor de la publicación o el PM del proyecto pueden marcar la solución.");
        }
        foroDetalleService.alternarSolucion(pm, respuesta);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Foro crearForo(Proyecto proyecto) {
        Foro foro = new Foro();
        foro.setProyecto(proyecto);
        foro.setNombre("Foro — " + proyecto.getNombre());
        foro.setTipo(TipoForo.PROYECTO);
        foro.setEsPublico(false);
        return foroRepository.save(foro);
    }

    /**
     * Ver y dar "me gusta": los foros visibles para todos y, si es privado de un proyecto, su PM o el Admin.
     * El RM usa este servicio solo en los foros del Admin: los de proyecto los lee sin participar.
     */
    private void validarAcceso(Foro foro, Usuario usuario) {
        boolean visible = foro.isVisibleParaTodos() || tieneRol(usuario, "ADMINISTRADOR") || esForoDeSuProyecto(foro, usuario);
        boolean rmEnForoDeProyecto = tieneRol(usuario, "RESOURCE_MANAGER") && foro.getProyecto() != null;
        if (!visible || rmEnForoDeProyecto) {
            throw new SecurityException("No tienes permiso sobre este foro.");
        }
    }

    /** Publicar y responder: igual que ver, salvo en los foros de anuncios, donde solo publica el Admin desde su vista. */
    private void validarParticipacion(Foro foro, Usuario usuario) {
        validarAcceso(foro, usuario);
        if (foro.isAnuncios()) {
            throw new SecurityException("Este es un foro de anuncios: solo publica el Administrador y no admite respuestas.");
        }
    }

    private static boolean tieneRol(Usuario usuario, String rol) {
        return usuario.getRol() != null && rol.equals(usuario.getRol().getNombre());
    }

    private Proyecto obtenerProyectoDelPm(Long proyectoId, Usuario pm) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));
        if (!proyecto.getPm().getId().equals(pm.getId())) {
            throw new SecurityException("No tienes permiso sobre este proyecto.");
        }
        return proyecto;
    }

    /** Alterna el voto positivo ("me gusta") del PM sobre una publicación accesible. */
    @Transactional
    public void alternarLikePublicacion(Usuario pm, Long publicacionId) {
        PublicacionForo publicacion = publicacionRepository.findById(publicacionId)
                .orElseThrow(() -> new IllegalArgumentException("Publicación no encontrada."));
        validarAcceso(publicacion.getForo(), pm);

        Optional<VotoPublicacion> votoOpt = votoPublicacionRepository.findByUsuarioAndPublicacion(pm, publicacion);
        if (votoOpt.isPresent()) {
            votoPublicacionRepository.delete(votoOpt.get());
        } else {
            VotoPublicacion voto = new VotoPublicacion();
            voto.setUsuario(pm);
            voto.setPublicacion(publicacion);
            voto.setTipo(TipoVoto.POSITIVO);
            votoPublicacionRepository.save(voto);
        }
    }

    /** Alterna el voto positivo ("me gusta") del PM sobre una respuesta accesible. */
    @Transactional
    public void alternarLikeRespuesta(Usuario pm, Long respuestaId) {
        RespuestaForo respuesta = respuestaRepository.findById(respuestaId)
                .orElseThrow(() -> new IllegalArgumentException("Respuesta no encontrada."));
        validarAcceso(respuesta.getPublicacion().getForo(), pm);

        Optional<VotoRespuesta> votoOpt = votoRespuestaRepository.findByUsuarioAndRespuesta(pm, respuesta);
        if (votoOpt.isPresent()) {
            votoRespuestaRepository.delete(votoOpt.get());
        } else {
            VotoRespuesta voto = new VotoRespuesta();
            voto.setUsuario(pm);
            voto.setRespuesta(respuesta);
            voto.setTipo(TipoVoto.POSITIVO);
            votoRespuestaRepository.save(voto);
        }
    }

    /** Elimina lógicamente una publicación del foro verificando que pertenezca al usuario autenticado. */
    @Transactional
    public void eliminarPublicacion(Usuario pm, Long publicacionId) {
        PublicacionForo publicacion = publicacionRepository.findById(publicacionId)
                .orElseThrow(() -> new IllegalArgumentException("Publicación no encontrada."));

        if (!publicacion.getAutor().getId().equals(pm.getId())) {
            throw new IllegalArgumentException("Solo puedes eliminar tus propias publicaciones.");
        }
        publicacion.setActivo(false);
        publicacionRepository.save(publicacion);
        auditoriaService.registrar(pm, "ELIMINAR_PUBLICACION_FORO", "PUBLICACION_FORO", publicacionId,
                "Eliminó la publicación \"" + publicacion.getTitulo() + "\".");
    }

    /** Elimina lógicamente una respuesta del foro verificando que pertenezca al usuario autenticado. */
    @Transactional
    public void eliminarRespuesta(Usuario pm, Long respuestaId) {
        RespuestaForo respuesta = respuestaRepository.findById(respuestaId)
                .orElseThrow(() -> new IllegalArgumentException("Respuesta no encontrada."));

        if (!respuesta.getAutor().getId().equals(pm.getId())) {
            throw new IllegalArgumentException("Solo puedes eliminar tus propias respuestas.");
        }
        respuesta.setActivo(false);
        respuestaRepository.save(respuesta);
        auditoriaService.registrar(pm, "ELIMINAR_RESPUESTA_FORO", "RESPUESTA_FORO", respuestaId, "Eliminó una respuesta.");
    }
}
