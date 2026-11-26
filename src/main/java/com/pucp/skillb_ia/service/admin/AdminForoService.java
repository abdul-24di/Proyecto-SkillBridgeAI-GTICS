package com.pucp.skillb_ia.service.admin;

import com.pucp.skillb_ia.model.Foro;
import com.pucp.skillb_ia.model.PublicacionForo;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.TipoForo;
import com.pucp.skillb_ia.repository.ForoRepository;
import com.pucp.skillb_ia.repository.PublicacionForoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import com.pucp.skillb_ia.dto.ForoPublicacionView;
import com.pucp.skillb_ia.model.RespuestaForo;
import com.pucp.skillb_ia.repository.RespuestaForoRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.ForoDetalleService;
import com.pucp.skillb_ia.service.ForoDetalleService.PermisosForo;

/**
 * Foros del Administrador: creación, publicación y moderación de los foros de comunidad y de anuncios.
 */
@Service
public class AdminForoService {

    private final ForoRepository foroRepository;
    private final PublicacionForoRepository publicacionForoRepository;
    private final RespuestaForoRepository respuestaForoRepository;
    private final ForoDetalleService foroDetalleService;
    private final AuditoriaService auditoriaService;

    /** Crea el componente con sus dependencias, que Spring inyecta por el constructor. */
    public AdminForoService(ForoRepository foroRepository,
                            PublicacionForoRepository publicacionForoRepository,
                            RespuestaForoRepository respuestaForoRepository,
                            ForoDetalleService foroDetalleService,
                            AuditoriaService auditoriaService) {
        this.foroRepository = foroRepository;
        this.publicacionForoRepository = publicacionForoRepository;
        this.respuestaForoRepository = respuestaForoRepository;
        this.foroDetalleService = foroDetalleService;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Detalle del foro para el Admin (orden "fecha" o "votos"): vota, responde (salvo en anuncios) y elimina cualquier
     * publicación o respuesta; modera la solución solo en los foros de comunidad.
     */
    @Transactional(readOnly = true)
    public List<ForoPublicacionView> obtenerDetalle(Long foroId, Usuario admin, String orden) {
        Foro foro = obtenerForo(foroId);
        PermisosForo permisos = new PermisosForo(true, !foro.isAnuncios(), foro.getProyecto() == null, true);
        return foroDetalleService.listar(foro, admin, permisos, orden);
    }

    /**
     * Marca o desmarca la solución (TASK-046): el Admin puede en los foros de comunidad;
     * en el foro de un proyecto, solo si es el autor de la publicación.
     */
    @Transactional
    public void alternarSolucion(Usuario admin, Long respuestaId) {
        RespuestaForo respuesta = respuestaForoRepository.findById(respuestaId)
                .filter(RespuestaForo::isActivo)
                .orElseThrow(() -> new IllegalArgumentException("Respuesta no encontrada."));
        PublicacionForo publicacion = respuesta.getPublicacion();
        boolean esAutor = publicacion.getAutor() != null && publicacion.getAutor().getId().equals(admin.getId());
        if (!esAutor && publicacion.getForo().getProyecto() != null) {
            throw new IllegalStateException(
                    "En el foro de un proyecto solo el autor de la publicación o el PM pueden marcar la solución.");
        }
        foroDetalleService.alternarSolucion(admin, respuesta);
    }

    /**
     * Lista los foros que crea el Administrador: los de comunidad y los de anuncios.
     *
     * @return foros de tipo general
     */
    @Transactional(readOnly = true)
    public List<Foro> listarForosComunidad() {
        return foroRepository.findAll().stream()
                .filter(f -> f.getTipo() == TipoForo.GENERAL)
                .toList();
    }

    /**
     * Crea un foro y lo registra en la auditoría.
     *
     * @param nombre nombre ingresado
     * @param esPublico {@code true} para un foro de comunidad, {@code false} para uno de anuncios
     * @param admin Administrador que realiza la acción; queda en la auditoría
     * @return el foro creado
     * @throws IllegalArgumentException si el nombre está vacío o pasa de 150 caracteres
     */
    @Transactional
    public Foro crearForo(String nombre, boolean esPublico, Usuario admin) {
        String nombreLimpio = nombre == null ? "" : nombre.strip();
        if (nombreLimpio.isEmpty()) {
            throw new IllegalArgumentException("El nombre del foro es obligatorio.");
        }
        if (nombreLimpio.length() > 150) {
            throw new IllegalArgumentException("El nombre del foro no puede superar los 150 caracteres.");
        }
        Foro foro = new Foro();
        foro.setNombre(nombreLimpio);
        foro.setTipo(TipoForo.GENERAL);
        foro.setEsPublico(esPublico);
        foro.setProyecto(null);
        Foro guardado = foroRepository.save(foro);
        auditoriaService.registrar(admin, "CREAR_FORO", "FORO", guardado.getId(),
                "Creó el foro de " + (esPublico ? "comunidad" : "anuncios") + " \"" + nombreLimpio + "\".");
        return guardado;
    }

    /**
     * Elimina un foro del Administrador con sus publicaciones, respuestas y votos.
     *
     * @param foroId id del foro
     * @param admin Administrador que realiza la acción; queda en la auditoría
     * @throws IllegalArgumentException si no existe o es el foro de un proyecto
     */
    @Transactional
    public void eliminarForo(Long foroId, Usuario admin) {
        Foro foro = obtenerForo(foroId);
        // Desde aquí solo se eliminan foros de comunidad; el foro de un proyecto vive mientras exista el proyecto.
        if (foro.getTipo() != TipoForo.GENERAL) {
            throw new IllegalArgumentException("Solo se pueden eliminar foros de comunidad.");
        }
        // Antes fallaba con un error de la base si el foro ya tenía publicaciones.
        foroRepository.borrarVotosDeRespuestas(foroId);
        foroRepository.borrarRespuestas(foroId);
        foroRepository.borrarVotosDePublicaciones(foroId);
        foroRepository.borrarPublicaciones(foroId);
        foroRepository.delete(foro);
        auditoriaService.registrar(admin, "ELIMINAR_FORO", "FORO", foroId,
                "Eliminó el foro de " + (foro.isAnuncios() ? "anuncios" : "comunidad") + " \"" + foro.getNombre() + "\" con todo su contenido.");
    }

    /**
     * Busca un foro por su id.
     *
     * @param foroId id del foro
     * @return el foro
     * @throws IllegalArgumentException si no existe
     */
    @Transactional(readOnly = true)
    public Foro obtenerForo(Long foroId) {
        return foroRepository.findById(foroId)
                .orElseThrow(() -> new IllegalArgumentException("Foro no encontrado: " + foroId));
    }

    /**
     * Publica en un foro y lo registra en la auditoría.
     *
     * @param foroId id del foro
     * @param titulo título de la publicación
     * @param contenido contenido escrito por el usuario
     * @param autor Administrador que publica
     * @throws IllegalArgumentException si el título o el contenido están vacíos o son muy largos
     */
    @Transactional
    public void publicar(Long foroId, String titulo, String contenido, Usuario autor) {
        // Mismas reglas que la publicación del PM (PmForoService.publicar). Antes un anuncio vacío se guardaba.
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("El título del anuncio es obligatorio.");
        }
        String tituloLimpio = titulo.trim();
        if (tituloLimpio.length() > 200) {
            throw new IllegalArgumentException("El título no puede superar los 200 caracteres.");
        }
        // El editor envía "<p><br></p>" cuando no se escribió nada: se mira el texto sin etiquetas.
        if (contenido == null || contenido.replaceAll("<[^>]*>", "").replace("&nbsp;", " ").isBlank()) {
            throw new IllegalArgumentException("El contenido del anuncio es obligatorio.");
        }
        if (contenido.length() > 10000) {
            throw new IllegalArgumentException("El contenido no puede superar los 10000 caracteres.");
        }
        Foro foro = obtenerForo(foroId);
        PublicacionForo pub = new PublicacionForo();
        pub.setForo(foro);
        pub.setAutor(autor);
        pub.setTitulo(tituloLimpio);
        pub.setContenido(contenido.trim());
        pub.setActivo(true);
        PublicacionForo guardada = publicacionForoRepository.save(pub);
        auditoriaService.registrar(autor, "CREAR_PUBLICACION_FORO", "PUBLICACION_FORO", guardada.getId(),
                "Publicó \"" + tituloLimpio + "\" en el foro \"" + foro.getNombre() + "\".");
    }

    /**
     * Retira una publicación del foro (baja lógica); si no existe no hace nada.
     *
     * @param pubId id de la publicación
     * @param admin Administrador que realiza la acción; queda en la auditoría
     */
    @Transactional
    public void eliminarPublicacion(Long pubId, Usuario admin) {
        publicacionForoRepository.findById(pubId).ifPresent(p -> {
            p.setActivo(false);
            publicacionForoRepository.save(p);
            auditoriaService.registrar(admin, "ELIMINAR_PUBLICACION_FORO", "PUBLICACION_FORO", pubId,
                    "Eliminó la publicación \"" + p.getTitulo() + "\" del foro \"" + p.getForo().getNombre() + "\".");
        });
    }

    /**
     * Retira cualquier respuesta del foro (baja lógica).
     * <p>
     * El Administrador modera el foro de comunidad: puede retirar cualquier respuesta, igual que las
     * publicaciones. Antes solo podía borrar las suyas.
     *
     * @param respuestaId id de la respuesta
     * @param admin Administrador que realiza la acción; queda en la auditoría
     * @throws IllegalArgumentException si no existe
     */
    @Transactional
    public void eliminarRespuesta(Long respuestaId, Usuario admin) {
        var respuesta = respuestaForoRepository.findById(respuestaId)
                .orElseThrow(() -> new IllegalArgumentException("Respuesta no encontrada."));
        respuesta.setActivo(false);
        respuestaForoRepository.save(respuesta);
        auditoriaService.registrar(admin, "ELIMINAR_RESPUESTA_FORO", "RESPUESTA_FORO", respuestaId,
                "Eliminó una respuesta de la publicación \"" + respuesta.getPublicacion().getTitulo() + "\".");
    }
}
