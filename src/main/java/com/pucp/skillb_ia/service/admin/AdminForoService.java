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

@Service
public class AdminForoService {

    private final ForoRepository foroRepository;
    private final PublicacionForoRepository publicacionForoRepository;

    public AdminForoService(ForoRepository foroRepository,
                            PublicacionForoRepository publicacionForoRepository) {
        this.foroRepository = foroRepository;
        this.publicacionForoRepository = publicacionForoRepository;
    }

    @Transactional(readOnly = true)
    public List<Foro> listarForosComunidad() {
        return foroRepository.findAll().stream()
                .filter(f -> f.getTipo() == TipoForo.GENERAL)
                .toList();
    }

    @Transactional
    public Foro crearForo(String nombre, boolean esPublico) {
        Foro foro = new Foro();
        foro.setNombre(nombre);
        foro.setTipo(TipoForo.GENERAL);
        foro.setEsPublico(esPublico);
        foro.setProyecto(null);
        return foroRepository.save(foro);
    }

    @Transactional
    public void eliminarForo(Long foroId) {
        foroRepository.deleteById(foroId);
    }

    @Transactional(readOnly = true)
    public Foro obtenerForo(Long foroId) {
        return foroRepository.findById(foroId)
                .orElseThrow(() -> new IllegalArgumentException("Foro no encontrado: " + foroId));
    }

    @Transactional(readOnly = true)
    public List<PublicacionForo> listarPublicaciones(Long foroId) {
        Foro foro = obtenerForo(foroId);
        return publicacionForoRepository.findByForoAndActivoTrueOrderByFechaCreacionDesc(foro);
    }

    @Transactional
    public void publicar(Long foroId, String titulo, String contenido, Usuario autor) {
        Foro foro = obtenerForo(foroId);
        PublicacionForo pub = new PublicacionForo();
        pub.setForo(foro);
        pub.setAutor(autor);
        pub.setTitulo(titulo);
        pub.setContenido(contenido);
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
}
