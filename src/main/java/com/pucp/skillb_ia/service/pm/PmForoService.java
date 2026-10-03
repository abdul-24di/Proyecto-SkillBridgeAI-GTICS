package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.dto.PmForoView;
import com.pucp.skillb_ia.dto.PmPublicacionView;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Foro;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.PublicacionForo;
import com.pucp.skillb_ia.model.RespuestaForo;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.TipoForo;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ForoRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.PublicacionForoRepository;
import com.pucp.skillb_ia.repository.RespuestaForoRepository;
import com.pucp.skillb_ia.service.ArchivoAlmacenamientoService;
import com.pucp.skillb_ia.service.AuditoriaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.Set;

@Service
public class PmForoService {

    private static final Set<String> TIPOS_IMAGEN_PERMITIDOS = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final long TAMANO_MAXIMO_FOTO_BYTES = 5L * 1024 * 1024; // 5 MB

    private final ForoRepository foroRepository;
    private final PublicacionForoRepository publicacionRepository;
    private final RespuestaForoRepository respuestaRepository;
    private final AsignacionRepository asignacionRepository;
    private final ProyectoRepository proyectoRepository;

    private final AuditoriaService auditoriaService;
    private final ArchivoAlmacenamientoService archivoAlmacenamientoService;

    public PmForoService(ForoRepository foroRepository,
                         PublicacionForoRepository publicacionRepository,
                         RespuestaForoRepository respuestaRepository,
                         AsignacionRepository asignacionRepository,
                         ProyectoRepository proyectoRepository,
                         AuditoriaService auditoriaService,
                         ArchivoAlmacenamientoService archivoAlmacenamientoService) {
        this.foroRepository = foroRepository;
        this.publicacionRepository = publicacionRepository;
        this.respuestaRepository = respuestaRepository;
        this.asignacionRepository = asignacionRepository;
        this.proyectoRepository = proyectoRepository;
        this.auditoriaService = auditoriaService;
        this.archivoAlmacenamientoService = archivoAlmacenamientoService;
    }

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

    /** Lista los foros de todos los proyectos que gestiona el PM. */
    @Transactional(readOnly = true)
    public List<PmForoView> listarForosPm(Usuario pm) {
        List<Proyecto> proyectosPm = proyectoRepository.findByPm(pm);
        List<PmForoView> vistas = new ArrayList<>();

        for (Proyecto proyecto : proyectosPm) {
            foroRepository.findByProyecto(proyecto).ifPresent(foro -> {
                List<PublicacionForo> pubs = publicacionRepository.findByForo(foro);
                int total = pubs.size();
                String ultimaTitulo = pubs.isEmpty() ? null : pubs.get(0).getTitulo();
                var ultimaFecha = pubs.isEmpty() ? null : pubs.get(0).getFechaCreacion();
                vistas.add(new PmForoView(foro, total, ultimaTitulo, ultimaFecha));
            });
        }
        return vistas;
    }

    /** Lista los foros de la comunidad. */
    @Transactional(readOnly = true)
    public List<PmForoView> listarForosComunidad() {
        List<Foro> forosComunidad = foroRepository.findByEsPublicoTrue();
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

    /** Devuelve las publicaciones del foro del proyecto indicado. */
    @Transactional(readOnly = true)
    public List<PmPublicacionView> obtenerDetalle(Long proyectoId, Usuario pm) {
        Proyecto proyecto = obtenerProyectoDelPm(proyectoId, pm);
        
        return foroRepository.findByProyecto(proyecto)
                .map(foro -> publicacionRepository.findByForoConDetalle(foro)
                        .stream()
                        .map(pub -> {
                            List<RespuestaForo> respuestas = respuestaRepository.findByPublicacionConAutor(pub);
                            return new PmPublicacionView(pub, respuestas);
                        })
                        .toList())
                .orElse(java.util.Collections.emptyList());
    }

    @Transactional(readOnly = true)
    public List<PmPublicacionView> obtenerDetallePorForo(Long foroId, Usuario pm) {
        Foro foro = foroRepository.findById(foroId)
                .orElseThrow(() -> new IllegalArgumentException("Foro no encontrado"));
        
        if (!foro.isEsPublico() && foro.getTipo() == TipoForo.PROYECTO) {
            if (foro.getProyecto() != null && !foro.getProyecto().getPm().getId().equals(pm.getId())) {
                throw new SecurityException("No tienes permiso para ver este foro.");
            }
        }

        return publicacionRepository.findByForoConDetalle(foro)
                .stream()
                .map(pub -> {
                    List<RespuestaForo> respuestas = respuestaRepository.findByPublicacionConAutor(pub);
                    return new PmPublicacionView(pub, respuestas);
                })
                .toList();
    }

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

    @Transactional
    public PublicacionForo publicarEnForo(Long foroId, String titulo, String contenido, Usuario pm) {
        Foro foro = foroRepository.findById(foroId)
                .orElseThrow(() -> new IllegalArgumentException("Foro no encontrado"));
                
        if (!foro.isEsPublico() && foro.getTipo() == TipoForo.PROYECTO) {
            if (foro.getProyecto() != null && !foro.getProyecto().getPm().getId().equals(pm.getId())) {
                throw new SecurityException("No tienes permiso para publicar en este foro.");
            }
        }
        return publicarInterno(foro, titulo, contenido, pm);
    }

    private PublicacionForo publicarInterno(Foro foro, String titulo, String contenido, Usuario pm) {
        PublicacionForo pub = new PublicacionForo();
        pub.setForo(foro);
        pub.setAutor(pm);
        pub.setTitulo(titulo);
        pub.setContenido(contenido);
        PublicacionForo saved = publicacionRepository.save(pub);

        String nombreForo = foro.getProyecto() != null ? foro.getProyecto().getNombre() : foro.getNombre();
        auditoriaService.registrar(pm, "PUBLICAR", "FORO", foro.getId(),
                "PM publicó en el foro '" + nombreForo + "': " + titulo);
        return saved;
    }

    /** Responde a una publicación existente. */
    @Transactional
    public RespuestaForo responder(Long publicacionId, String contenido, Usuario pm) {
        PublicacionForo publicacion = publicacionRepository.findById(publicacionId)
                .orElseThrow(() -> new IllegalArgumentException("Publicación no encontrada."));

        // Verificar que el PM tiene acceso al foro de este proyecto
        if (publicacion.getForo().getProyecto() != null) {
            obtenerProyectoDelPm(publicacion.getForo().getProyecto().getId(), pm);
        }

        RespuestaForo respuesta = new RespuestaForo();
        respuesta.setPublicacion(publicacion);
        respuesta.setAutor(pm);
        respuesta.setContenido(contenido);
        return respuestaRepository.save(respuesta);
    }

    /** Marca o desmarca una respuesta como solución de la publicación. */
    @Transactional
    public void alternarSolucion(Usuario pm, Long respuestaId) {
        RespuestaForo respuesta = respuestaRepository.findById(respuestaId)
                .orElseThrow(() -> new IllegalArgumentException("Respuesta no encontrada."));
        PublicacionForo publicacion = respuesta.getPublicacion();

        // Verificar que el PM tiene acceso al foro de este proyecto
        if (publicacion.getForo().getProyecto() != null) {
            obtenerProyectoDelPm(publicacion.getForo().getProyecto().getId(), pm);
        }

        if (respuesta.isEsSolucion()) {
            respuesta.setEsSolucion(false);
            respuestaRepository.save(respuesta);
            auditoriaService.registrar(pm, "DESMARCAR_SOLUCION_FORO", "RESPUESTA_FORO",
                    respuesta.getId(), "PM desmarcó la respuesta como solución en \"" + publicacion.getTitulo() + "\".");
            return;
        }

        respuestaRepository.findByPublicacionAndEsSolucionTrue(publicacion).ifPresent(anterior -> {
            anterior.setEsSolucion(false);
            respuestaRepository.save(anterior);
        });

        respuesta.setEsSolucion(true);
        respuestaRepository.save(respuesta);
        auditoriaService.registrar(pm, "MARCAR_SOLUCION_FORO", "RESPUESTA_FORO",
                respuesta.getId(), "PM marcó una respuesta como solución en \"" + publicacion.getTitulo() + "\".");
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

    private Proyecto obtenerProyectoDelPm(Long proyectoId, Usuario pm) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));
        if (!proyecto.getPm().getId().equals(pm.getId())) {
            throw new SecurityException("No tienes permiso sobre este proyecto.");
        }
        return proyecto;
    }
}
