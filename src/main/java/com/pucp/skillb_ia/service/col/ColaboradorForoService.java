package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.model.Etiqueta;
import com.pucp.skillb_ia.model.Foro;
import com.pucp.skillb_ia.model.PublicacionForo;
import com.pucp.skillb_ia.model.RespuestaForo;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.TipoForo;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.EtiquetaRepository;
import com.pucp.skillb_ia.repository.ForoRepository;
import com.pucp.skillb_ia.repository.PublicacionForoRepository;
import com.pucp.skillb_ia.repository.RespuestaForoRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ColaboradorForoService {

    private static final int TITULO_MAXIMO = 200;

    private final ForoRepository foroRepository;
    private final PublicacionForoRepository publicacionForoRepository;
    private final RespuestaForoRepository respuestaForoRepository;
    private final AsignacionRepository asignacionRepository;
    private final EtiquetaRepository etiquetaRepository;
    private final AuditoriaService auditoriaService;

    public ColaboradorForoService(ForoRepository foroRepository,
                                  PublicacionForoRepository publicacionForoRepository,
                                  RespuestaForoRepository respuestaForoRepository,
                                  AsignacionRepository asignacionRepository,
                                  EtiquetaRepository etiquetaRepository,
                                  AuditoriaService auditoriaService) {
        this.foroRepository = foroRepository;
        this.publicacionForoRepository = publicacionForoRepository;
        this.respuestaForoRepository = respuestaForoRepository;
        this.asignacionRepository = asignacionRepository;
        this.etiquetaRepository = etiquetaRepository;
        this.auditoriaService = auditoriaService;
    }

    // ============================================================
    // LISTAR FOROS A LOS QUE EL COLABORADOR TIENE ACCESO
    // ============================================================

    public List<Foro> listarForosComunidad() {
        return foroRepository.findByEsPublicoTrue();
    }

    //El colaborador solo ve foros de proyecto donde tiene asignación ACTIVA
    public List<Foro> listarForosDeMisProyectos(Usuario colaborador) {
        List<Foro> todos = foroRepository.findTodosConProyectoYPm();
        List<Foro> misForos = new ArrayList<>();

        for (Foro foro : todos) {
            if (foro.getTipo() == TipoForo.PROYECTO && foro.getProyecto() != null) {
                boolean tieneAsignacionActiva = asignacionRepository.existsByProyectoAndColaboradorAndEstado(
                        foro.getProyecto(), colaborador, EstadoAsignacion.ACTIVA);
                if (tieneAsignacionActiva) {
                    misForos.add(foro);
                }
            }
        }
        return misForos;
    }

    //Validamos que el colaborador tenga permiso para ver o publicar en un foro puntual
    private Foro validarAccesoAForo(Usuario colaborador, Long foroId) {
        Optional<Foro> foroOpt = foroRepository.findByIdConProyectoYPm(foroId);
        if (foroOpt.isEmpty()) {
            throw new IllegalArgumentException("El foro no existe.");
        }
        Foro foro = foroOpt.get();

        if (foro.isEsPublico()) {
            return foro;
        }

        //Validación para foro privado de proyecto
        boolean tieneAsignacionActiva = asignacionRepository.existsByProyectoAndColaboradorAndEstado(
                foro.getProyecto(), colaborador, EstadoAsignacion.ACTIVA);
        if (!tieneAsignacionActiva) {
            throw new IllegalArgumentException(
                    "No tienes una asignación activa en este proyecto, no puedes acceder a su foro.");
        }
        return foro;
    }

    //Devuelve el foro validando que el colaborador tenga acceso
    public Foro obtenerForo(Usuario colaborador, Long foroId) {
        return validarAccesoAForo(colaborador, foroId);
    }

    public List<Etiqueta> listarEtiquetas() {
        return etiquetaRepository.findAll();
    }

    // ============================================================
    // PUBLICACIONES
    // ============================================================
    public List<PublicacionForo> listarPublicaciones(Usuario colaborador, Long foroId) {
        Foro foro = validarAccesoAForo(colaborador, foroId);
        return publicacionForoRepository.findByForoConDetalle(foro);
    }

    public PublicacionForo obtenerPublicacion(Usuario colaborador, Long publicacionId) {
        PublicacionForo publicacion = buscarPublicacionActivaConDetalle(publicacionId);
        validarAccesoAForo(colaborador, publicacion.getForo().getId());
        return publicacion;
    }

    @Transactional
    public void crearPublicacion(Usuario colaborador, Long foroId, String titulo,
                                 String contenido, Long etiquetaId) {
        Foro foro = validarAccesoAForo(colaborador, foroId);

        String tituloValidado = validarTitulo(titulo);
        String contenidoValidado = validarContenido(contenido);
        Etiqueta etiqueta = obtenerEtiquetaOpcional(etiquetaId);

        PublicacionForo publicacion = new PublicacionForo();
        publicacion.setForo(foro);
        publicacion.setAutor(colaborador);
        publicacion.setEtiqueta(etiqueta);
        publicacion.setTitulo(tituloValidado);
        publicacion.setContenido(contenidoValidado);
        publicacionForoRepository.save(publicacion);

        auditoriaService.registrar(colaborador, "CREAR_PUBLICACION_FORO", "PUBLICACION_FORO",
                publicacion.getId(),
                "Publicó \"" + tituloValidado + "\" en el foro \"" + foro.getNombre() + "\".");
    }

    @Transactional
    public void editarPublicacion(Usuario colaborador, Long publicacionId, String titulo,
                                  String contenido, Long etiquetaId) {
        PublicacionForo publicacion = buscarPublicacionActiva(publicacionId);

        if (!publicacion.getAutor().getId().equals(colaborador.getId())) {
            throw new IllegalArgumentException("No puedes editar una publicación que no es tuya.");
        }

        String tituloValidado = validarTitulo(titulo);
        String contenidoValidado = validarContenido(contenido);
        Etiqueta etiqueta = obtenerEtiquetaOpcional(etiquetaId);

        publicacion.setTitulo(tituloValidado);
        publicacion.setContenido(contenidoValidado);
        publicacion.setEtiqueta(etiqueta);
        publicacionForoRepository.save(publicacion);

        auditoriaService.registrar(colaborador, "EDITAR_PUBLICACION_FORO", "PUBLICACION_FORO",
                publicacion.getId(), "Editó la publicación \"" + tituloValidado + "\".");
    }

    @Transactional
    public void eliminarPublicacion(Usuario colaborador, Long publicacionId) {
        PublicacionForo publicacion = buscarPublicacionActiva(publicacionId);

        boolean esElAutor = publicacion.getAutor().getId().equals(colaborador.getId());
        boolean esAdministrador = colaborador.getRol().getNombre().equals("ADMINISTRADOR");

        if (!esElAutor && !esAdministrador) {
            throw new IllegalArgumentException("No puedes eliminar una publicación que no es tuya.");
        }

        publicacion.setActivo(false);
        publicacionForoRepository.save(publicacion);

        auditoriaService.registrar(colaborador, "ELIMINAR_PUBLICACION_FORO", "PUBLICACION_FORO",
                publicacion.getId(), "Eliminó la publicación \"" + publicacion.getTitulo() + "\".");
    }

    private PublicacionForo buscarPublicacionActiva(Long publicacionId) {
        Optional<PublicacionForo> pubOpt = publicacionForoRepository.findByIdAndActivoTrue(publicacionId);
        if (pubOpt.isEmpty()) {
            throw new IllegalArgumentException("La publicación no existe o ya fue eliminada.");
        }
        return pubOpt.get();
    }

    private PublicacionForo buscarPublicacionActivaConDetalle(Long publicacionId) {
        Optional<PublicacionForo> pubOpt = publicacionForoRepository.findByIdConDetalle(publicacionId);
        if (pubOpt.isEmpty()) {
            throw new IllegalArgumentException("La publicación no existe o ya fue eliminada.");
        }
        return pubOpt.get();
    }

    // ============================================================
    // RESPUESTAS
    // ============================================================

    public List<RespuestaForo> listarRespuestas(Usuario colaborador, Long publicacionId) {
        PublicacionForo publicacion = buscarPublicacionActiva(publicacionId);
        validarAccesoAForo(colaborador, publicacion.getForo().getId());
        return respuestaForoRepository.findByPublicacionConAutor(publicacion);
    }

    @Transactional
    public void crearRespuesta(Usuario colaborador, Long publicacionId, String contenido) {
        PublicacionForo publicacion = buscarPublicacionActiva(publicacionId);
        validarAccesoAForo(colaborador, publicacion.getForo().getId());

        String contenidoValidado = validarContenido(contenido);

        RespuestaForo respuesta = new RespuestaForo();
        respuesta.setPublicacion(publicacion);
        respuesta.setAutor(colaborador);
        respuesta.setContenido(contenidoValidado);
        respuestaForoRepository.save(respuesta);

        auditoriaService.registrar(colaborador, "CREAR_RESPUESTA_FORO", "RESPUESTA_FORO",
                respuesta.getId(), "Respondió la publicación \"" + publicacion.getTitulo() + "\".");
    }

    @Transactional
    public void editarRespuesta(Usuario colaborador, Long respuestaId, String contenido) {
        RespuestaForo respuesta = buscarRespuestaActiva(respuestaId);

        if (!respuesta.getAutor().getId().equals(colaborador.getId())) {
            throw new IllegalArgumentException("No puedes editar una respuesta que no es tuya.");
        }

        String contenidoValidado = validarContenido(contenido);
        respuesta.setContenido(contenidoValidado);
        respuestaForoRepository.save(respuesta);

        auditoriaService.registrar(colaborador, "EDITAR_RESPUESTA_FORO", "RESPUESTA_FORO",
                respuesta.getId(), "Editó una respuesta.");
    }

    @Transactional
    public void eliminarRespuesta(Usuario colaborador, Long respuestaId) {
        RespuestaForo respuesta = buscarRespuestaActiva(respuestaId);

        boolean esElAutor = respuesta.getAutor().getId().equals(colaborador.getId());
        boolean esAdministrador = colaborador.getRol().getNombre().equals("ADMINISTRADOR");

        if (!esElAutor && !esAdministrador) {
            throw new IllegalArgumentException("No puedes eliminar una respuesta que no es tuya.");
        }

        respuesta.setActivo(false);
        respuestaForoRepository.save(respuesta);

        auditoriaService.registrar(colaborador, "ELIMINAR_RESPUESTA_FORO", "RESPUESTA_FORO",
                respuesta.getId(), "Eliminó una respuesta.");
    }

    private RespuestaForo buscarRespuestaActiva(Long respuestaId) {
        Optional<RespuestaForo> respOpt = respuestaForoRepository.findByIdAndActivoTrue(respuestaId);
        if (respOpt.isEmpty()) {
            throw new IllegalArgumentException("La respuesta no existe o ya fue eliminada.");
        }
        return respOpt.get();
    }

    // ============================================================
    // VALIDACIONES
    // ============================================================

    private String validarTitulo(String titulo) {
        if (titulo == null || titulo.trim().isEmpty()) {
            throw new IllegalArgumentException("El título es obligatorio.");
        }
        String tituloLimpio = titulo.trim();
        if (tituloLimpio.length() > TITULO_MAXIMO) {
            throw new IllegalArgumentException("El título no puede tener más de " + TITULO_MAXIMO + " caracteres.");
        }
        return tituloLimpio;
    }

    private String validarContenido(String contenido) {
        if (contenido == null || contenido.trim().isEmpty()) {
            throw new IllegalArgumentException("El contenido no puede estar vacío.");
        }
        return contenido.trim();
    }

    private Etiqueta obtenerEtiquetaOpcional(Long etiquetaId) {
        if (etiquetaId == null) {
            return null;
        }
        Optional<Etiqueta> etiquetaOpt = etiquetaRepository.findById(etiquetaId);
        if (etiquetaOpt.isEmpty()) {
            throw new IllegalArgumentException("La etiqueta seleccionada no existe.");
        }
        return etiquetaOpt.get();
    }
}