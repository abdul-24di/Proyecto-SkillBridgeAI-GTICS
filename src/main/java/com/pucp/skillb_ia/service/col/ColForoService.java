package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.dto.PmForoView;
import com.pucp.skillb_ia.dto.PmPublicacionView;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Foro;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.PublicacionForo;
import com.pucp.skillb_ia.model.RespuestaForo;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ForoRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.PublicacionForoRepository;
import com.pucp.skillb_ia.repository.RespuestaForoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ColForoService {

    private static final Set<String> TIPOS_IMAGEN_PERMITIDOS = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final long TAMANO_MAXIMO_FOTO_BYTES = 5L * 1024 * 1024; // 5 MB

    private final AsignacionRepository asignacionRepository;
    private final ForoRepository foroRepository;
    private final ProyectoRepository proyectoRepository;
    private final PublicacionForoRepository publicacionRepository;
    private final RespuestaForoRepository respuestaRepository;
    private final String uploadDir;

    public ColForoService(AsignacionRepository asignacionRepository,
                          ForoRepository foroRepository,
                          ProyectoRepository proyectoRepository,
                          PublicacionForoRepository publicacionRepository,
                          RespuestaForoRepository respuestaRepository,
                          @Value("${app.upload-dir:uploads}") String uploadDir) {
        this.asignacionRepository = asignacionRepository;
        this.foroRepository = foroRepository;
        this.proyectoRepository = proyectoRepository;
        this.publicacionRepository = publicacionRepository;
        this.respuestaRepository = respuestaRepository;
        this.uploadDir = uploadDir;
    }

    private void validarAcceso(Proyecto proyecto, Usuario colaborador) {
        boolean activo = asignacionRepository.findByColaboradorAndEstado(colaborador, EstadoAsignacion.ACTIVA)
                .stream()
                .anyMatch(a -> a.getProyecto().getId().equals(proyecto.getId()));
        if (!activo) {
            throw new IllegalArgumentException("No eres un colaborador activo de este proyecto.");
        }
    }

    @Transactional(readOnly = true)
    public List<PmForoView> listarForos(Usuario colaborador) {
        return asignacionRepository.findByColaboradorAndEstado(colaborador, EstadoAsignacion.ACTIVA)
                .stream()
                .map(Asignacion::getProyecto)
                .map(p -> {
                    Foro f = foroRepository.findByProyecto(p).orElse(null);
                    if (f == null) return null;
                    List<PublicacionForo> pubs = publicacionRepository.findByForo(f);
                    int total = pubs.size();
                    String ultimaTitulo = pubs.isEmpty() ? null : pubs.get(0).getTitulo();
                    var ultimaFecha = pubs.isEmpty() ? null : pubs.get(0).getFechaCreacion();
                    return new PmForoView(f, total, ultimaTitulo, ultimaFecha);
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PmPublicacionView> obtenerDetalleForo(Long proyectoId, Usuario colaborador) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));

        validarAcceso(proyecto, colaborador);

        Foro foro = foroRepository.findByProyecto(proyecto).orElse(null);
        if (foro == null) return List.of();

        List<PublicacionForo> pubs = publicacionRepository.findByForoConDetalle(foro);
        return pubs.stream().map(p -> {
            List<RespuestaForo> resps = respuestaRepository.findByPublicacionConAutor(p);
            return new PmPublicacionView(p, resps);
        }).toList();
    }

    @Transactional
    public void publicar(Long proyectoId, String titulo, String contenido, Usuario colaborador) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));

        validarAcceso(proyecto, colaborador);

        Foro foro = foroRepository.findByProyecto(proyecto)
                .orElseGet(() -> {
                    Foro nuevo = new Foro();
                    nuevo.setProyecto(proyecto);
                    return foroRepository.save(nuevo);
                });

        PublicacionForo pub = new PublicacionForo();
        pub.setForo(foro);
        pub.setAutor(colaborador);
        pub.setTitulo(titulo);
        pub.setContenido(contenido);
        publicacionRepository.save(pub);
    }

    @Transactional
    public void responder(Long publicacionId, String contenido, Usuario colaborador) {
        PublicacionForo pub = publicacionRepository.findById(publicacionId)
                .orElseThrow(() -> new IllegalArgumentException("Publicación no encontrada."));

        validarAcceso(pub.getForo().getProyecto(), colaborador);

        RespuestaForo resp = new RespuestaForo();
        resp.setPublicacion(pub);
        resp.setAutor(colaborador);
        resp.setContenido(contenido);
        resp.setEsSolucion(false);
        respuestaRepository.save(resp);
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

        try {
            Path carpeta = Path.of(uploadDir, "foro");
            Files.createDirectories(carpeta);
            String extension = ".jpg";
            if ("image/png".equals(foto.getContentType())) extension = ".png";
            else if ("image/gif".equals(foto.getContentType())) extension = ".gif";
            else if ("image/webp".equals(foto.getContentType())) extension = ".webp";
            
            String nombreArchivo = "foro-colab-" + UUID.randomUUID() + extension;
            Path destino = carpeta.resolve(nombreArchivo);
            Files.copy(foto.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/foro/" + nombreArchivo;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la imagen del foro.", e);
        }
    }
}
