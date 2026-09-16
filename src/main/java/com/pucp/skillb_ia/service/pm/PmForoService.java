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
import com.pucp.skillb_ia.service.AuditoriaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class PmForoService {

    private final ForoRepository foroRepository;
    private final PublicacionForoRepository publicacionRepository;
    private final RespuestaForoRepository respuestaRepository;
    private final AsignacionRepository asignacionRepository;
    private final ProyectoRepository proyectoRepository;
    private final AuditoriaService auditoriaService;

    public PmForoService(ForoRepository foroRepository,
                         PublicacionForoRepository publicacionRepository,
                         RespuestaForoRepository respuestaRepository,
                         AsignacionRepository asignacionRepository,
                         ProyectoRepository proyectoRepository,
                         AuditoriaService auditoriaService) {
        this.foroRepository = foroRepository;
        this.publicacionRepository = publicacionRepository;
        this.respuestaRepository = respuestaRepository;
        this.asignacionRepository = asignacionRepository;
        this.proyectoRepository = proyectoRepository;
        this.auditoriaService = auditoriaService;
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

    /** Devuelve las publicaciones del foro del proyecto indicado. */
    @Transactional(readOnly = true)
    public List<PmPublicacionView> obtenerDetalle(Long proyectoId, Usuario pm) {
        Proyecto proyecto = obtenerProyectoDelPm(proyectoId, pm);
        Foro foro = foroRepository.findByProyecto(proyecto)
                .orElseThrow(() -> new IllegalStateException(
                        "Este proyecto no tiene un foro creado todavía."));

        return publicacionRepository.findByForoConDetalle(foro)
                .stream()
                .map(pub -> {
                    List<RespuestaForo> respuestas =
                            respuestaRepository.findByPublicacionConAutor(pub);
                    return new PmPublicacionView(pub, respuestas);
                })
                .toList();
    }

    /** Crea una publicación en el foro del proyecto. Crea el Foro si aún no existe. */
    @Transactional
    public PublicacionForo publicar(Long proyectoId, String titulo, String contenido, Usuario pm) {
        Proyecto proyecto = obtenerProyectoDelPm(proyectoId, pm);
        Foro foro = foroRepository.findByProyecto(proyecto)
                .orElseGet(() -> crearForo(proyecto));

        PublicacionForo pub = new PublicacionForo();
        pub.setForo(foro);
        pub.setAutor(pm);
        pub.setTitulo(titulo);
        pub.setContenido(contenido);
        PublicacionForo saved = publicacionRepository.save(pub);

        auditoriaService.registrar(pm, "PUBLICAR", "FORO", foro.getId(),
                "PM publicó en el foro del proyecto '" + proyecto.getNombre() + "': " + titulo);
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
