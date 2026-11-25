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

@Service
public class AdminForoService {

    private final ForoRepository foroRepository;
    private final PublicacionForoRepository publicacionForoRepository;
    private final RespuestaForoRepository respuestaForoRepository;
    private final ForoDetalleService foroDetalleService;
    private final AuditoriaService auditoriaService;

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

    @Transactional(readOnly = true)
    public List<Foro> listarForosComunidad() {
        return foroRepository.findAll().stream()
                .filter(f -> f.getTipo() == TipoForo.GENERAL)
                .toList();
    }

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

    @Transactional(readOnly = true)
    public Foro obtenerForo(Long foroId) {
        return foroRepository.findById(foroId)
                .orElseThrow(() -> new IllegalArgumentException("Foro no encontrado: " + foroId));
    }

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

    @Transactional
    public void eliminarPublicacion(Long pubId, Usuario admin) {
        publicacionForoRepository.findById(pubId).ifPresent(p -> {
            p.setActivo(false);
            publicacionForoRepository.save(p);
            auditoriaService.registrar(admin, "ELIMINAR_PUBLICACION_FORO", "PUBLICACION_FORO", pubId,
                    "Eliminó la publicación \"" + p.getTitulo() + "\" del foro \"" + p.getForo().getNombre() + "\".");
        });
    }

    // El Administrador modera el foro de comunidad: puede retirar cualquier respuesta, igual que las publicaciones.
    // Antes solo podía borrar las suyas.
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
