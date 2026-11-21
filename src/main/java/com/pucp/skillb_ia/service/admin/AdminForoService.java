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

import com.pucp.skillb_ia.dto.AdminPublicacionView;
import com.pucp.skillb_ia.repository.RespuestaForoRepository;
import java.util.ArrayList;

@Service
public class AdminForoService {

    private final ForoRepository foroRepository;
    private final PublicacionForoRepository publicacionForoRepository;
    private final RespuestaForoRepository respuestaForoRepository;

    public AdminForoService(ForoRepository foroRepository,
                            PublicacionForoRepository publicacionForoRepository,
                            RespuestaForoRepository respuestaForoRepository) {
        this.foroRepository = foroRepository;
        this.publicacionForoRepository = publicacionForoRepository;
        this.respuestaForoRepository = respuestaForoRepository;
    }

    @Transactional(readOnly = true)
    public List<Foro> listarForosComunidad() {
        return foroRepository.findAll().stream()
                .filter(f -> f.getTipo() == TipoForo.GENERAL)
                .toList();
    }

    @Transactional
    public Foro crearForo(String nombre, boolean esPublico) {
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
        return foroRepository.save(foro);
    }

    @Transactional
    public void eliminarForo(Long foroId) {
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
    }

    @Transactional(readOnly = true)
    public Foro obtenerForo(Long foroId) {
        return foroRepository.findById(foroId)
                .orElseThrow(() -> new IllegalArgumentException("Foro no encontrado: " + foroId));
    }

    @Transactional(readOnly = true)
    public List<AdminPublicacionView> listarPublicaciones(Long foroId) {
        Foro foro = obtenerForo(foroId);
        List<PublicacionForo> publicaciones = publicacionForoRepository.findByForoAndActivoTrueOrderByFechaCreacionDesc(foro);
        List<AdminPublicacionView> vistas = new ArrayList<>();
        for (PublicacionForo pub : publicaciones) {
            vistas.add(new AdminPublicacionView(pub, respuestaForoRepository.findByPublicacionConAutor(pub)));
        }
        return vistas;
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
        publicacionForoRepository.save(pub);
    }

    @Transactional
    public void eliminarPublicacion(Long pubId) {
        publicacionForoRepository.findById(pubId).ifPresent(p -> {
            p.setActivo(false);
            publicacionForoRepository.save(p);
        });
    }

    // El Administrador modera el foro de comunidad: puede retirar cualquier respuesta, igual que las publicaciones.
    // Antes solo podía borrar las suyas.
    @Transactional
    public void eliminarRespuesta(Long respuestaId) {
        var respuesta = respuestaForoRepository.findById(respuestaId)
                .orElseThrow(() -> new IllegalArgumentException("Respuesta no encontrada."));
        respuesta.setActivo(false);
        respuestaForoRepository.save(respuesta);
    }
}
