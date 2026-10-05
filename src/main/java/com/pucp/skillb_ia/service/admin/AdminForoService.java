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
