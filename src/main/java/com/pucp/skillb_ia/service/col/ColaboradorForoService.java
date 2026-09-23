package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.dto.ColPublicacionForoView;
import com.pucp.skillb_ia.dto.ColRespuestaForoView;
import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.TipoForo;
import com.pucp.skillb_ia.model.enums.TipoVoto;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.service.AuditoriaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ColaboradorForoService {

    private static final int TITULO_MAXIMO = 200;

    //Para la subida de imagenes dentro del contenido (editor Quill)
    private static final Set<String> TIPOS_IMAGEN_PERMITIDOS =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final long TAMANO_MAXIMO_IMAGEN_BYTES = 5L * 1024 * 1024; // 5 MB

    private final ForoRepository foroRepository;
    private final PublicacionForoRepository publicacionForoRepository;
    private final RespuestaForoRepository respuestaForoRepository;
    private final AsignacionRepository asignacionRepository;
    private final EtiquetaRepository etiquetaRepository;
    private final VotoPublicacionRepository votoPublicacionRepository;
    private final VotoRespuestaRepository votoRespuestaRepository;
    private final AuditoriaService auditoriaService;
    private final String uploadDir;

    public ColaboradorForoService(ForoRepository foroRepository,
                                  PublicacionForoRepository publicacionForoRepository,
                                  RespuestaForoRepository respuestaForoRepository,
                                  AsignacionRepository asignacionRepository,
                                  EtiquetaRepository etiquetaRepository,
                                  VotoPublicacionRepository votoPublicacionRepository,
                                  VotoRespuestaRepository votoRespuestaRepository,
                                  AuditoriaService auditoriaService,
                                  @Value("${app.upload-dir:uploads}") String uploadDir) {
        this.foroRepository = foroRepository;
        this.publicacionForoRepository = publicacionForoRepository;
        this.respuestaForoRepository = respuestaForoRepository;
        this.asignacionRepository = asignacionRepository;
        this.etiquetaRepository = etiquetaRepository;
        this.votoPublicacionRepository = votoPublicacionRepository;
        this.votoRespuestaRepository = votoRespuestaRepository;
        this.auditoriaService = auditoriaService;
        this.uploadDir = uploadDir;
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

    //Foro solo del proyecto
    public Optional<Foro> obtenerForoDeProyecto(Proyecto proyecto) {
        return foroRepository.findByProyecto(proyecto);
    }





    //Acceso de LECTURA: cualquier foro público (general o de proyecto compartido a comunidad)
    //se puede leer, o un foro de proyecto donde el colaborador tiene asignación activa.
    private Foro validarAccesoLectura(Usuario colaborador, Long foroId) {
        Optional<Foro> foroOpt = foroRepository.findByIdConProyectoYPm(foroId);
        if (foroOpt.isEmpty()) {
            throw new IllegalArgumentException("El foro no existe.");
        }
        Foro foro = foroOpt.get();

        if (foro.isEsPublico()) {
            return foro;
        }

        //Foro privado de proyecto. Para leer basta con haber tenido asignación ACTIVA o FINALIZADA
        //Así el foro queda visible en modo solo-lectura incluso después de que el proyecto termine
        boolean tieneAcceso = asignacionRepository.tieneAccesoVigente(foro.getProyecto(), colaborador);
        if (!tieneAcceso) {
            throw new IllegalArgumentException("No tienes acceso al foro de este proyecto.");
        }
        return foro;
    }

    //Indicamos si el colaborador puede participar (publicar o responder) en el foro, no solo leerlo.
    //Un foro GENERAL público es de participación abierta. Un foro de PROYECTO, aunque esté
    //compartido con la comunidad (esPublico = true), solo admite participación de quienes
    //tienen asignación activa en ese proyecto; para el resto queda de solo lectura.
    public boolean puedeParticipar(Usuario colaborador, Foro foro) {
        if (foro.getTipo() == TipoForo.GENERAL) {
            return foro.isEsPublico();
        }
        return asignacionRepository.existsByProyectoAndColaboradorAndEstado(
                foro.getProyecto(), colaborador, EstadoAsignacion.ACTIVA);
    }

    //Validamos que se pueda leer el foro y luego que pueda participar en él
    private Foro validarAccesoEscritura(Usuario colaborador, Long foroId) {
        Foro foro = validarAccesoLectura(colaborador, foroId);
        if (!puedeParticipar(colaborador, foro)) {
            throw new IllegalArgumentException(
                    "Este foro es de solo lectura para ti: no tienes una asignación activa en este proyecto.");
        }
        return foro;
    }

    //Devolvemos el foro validando que el colaborador al menos pueda leerlo
    public Foro obtenerForo(Usuario colaborador, Long foroId) {
        return validarAccesoLectura(colaborador, foroId);
    }

    //Indicamos si el colaborador puede participar en un foro dado su id
    public boolean puedeParticipar(Usuario colaborador, Long foroId) {
        Foro foro = validarAccesoLectura(colaborador, foroId);
        return puedeParticipar(colaborador, foro);
    }

    //Lista de foros válidos para publicar, en los cuales se incluye los foros de comunidad de tipo GENERAL
    // Y los foros de los proyectos donde el colaborador tiene asignación activa.
    //(No los foros de proyecto que solo estén compartidos en comunidad, ya que esos son de solo lectura
    //para quienes no forma parte del proyecto)
    public List<Foro> listarForosParaPublicar(Usuario colaborador) {
        List<Foro> destinos = new ArrayList<>();

        List<Foro> publicos = foroRepository.findByEsPublicoTrue();
        for (Foro foro : publicos) {
            if (foro.getTipo() == TipoForo.GENERAL) {
                destinos.add(foro);
            }
        }

        List<Foro> misProyectos = listarForosDeMisProyectos(colaborador);
        for (Foro foro : misProyectos) {
            destinos.add(foro);
        }

        return destinos;
    }

    public List<Etiqueta> listarEtiquetas() {
        return etiquetaRepository.findAll();
    }


    // ============================================================
    // PUBLICACIONES
    // ============================================================
    public List<PublicacionForo> listarPublicaciones(Usuario colaborador, Long foroId) {
        Foro foro = validarAccesoLectura(colaborador, foroId);
        return publicacionForoRepository.findByForoConDetalle(foro);
    }

    //Para la vista de detalle. Cada publicación ya viene con su lista de respuestas y sus likes
    public List<ColPublicacionForoView> listarPublicacionesConRespuestas(Usuario colaborador, Long foroId) {
        List<PublicacionForo> publicaciones = listarPublicaciones(colaborador, foroId);
        List<ColPublicacionForoView> resultado = new ArrayList<>();

        for (PublicacionForo publicacion : publicaciones) {
            List<RespuestaForo> respuestas = respuestaForoRepository.findByPublicacionConAutor(publicacion);
            List<ColRespuestaForoView> respuestasVista = new ArrayList<>();

            for (RespuestaForo respuesta : respuestas) {
                long totalLikesResp = votoRespuestaRepository.countByRespuestaAndTipo(respuesta, TipoVoto.POSITIVO);
                boolean meGustaResp = votoRespuestaRepository.findByUsuarioAndRespuesta(colaborador, respuesta).isPresent();
                respuestasVista.add(new ColRespuestaForoView(respuesta, totalLikesResp, meGustaResp));
            }

            long totalLikesPub = votoPublicacionRepository.countByPublicacionAndTipo(publicacion, TipoVoto.POSITIVO);
            boolean meGustaPub = votoPublicacionRepository.findByUsuarioAndPublicacion(colaborador, publicacion).isPresent();

            resultado.add(new ColPublicacionForoView(publicacion, respuestasVista, totalLikesPub, meGustaPub));
        }
        return resultado;
    }

    // ============================================================
    // LIKES
    // ============================================================

    @Transactional
    public void alternarLikePublicacion(Usuario colaborador, Long publicacionId) {
        PublicacionForo publicacion = buscarPublicacionActiva(publicacionId);
        validarAccesoLectura(colaborador, publicacion.getForo().getId());

        Optional<VotoPublicacion> votoOpt =
                votoPublicacionRepository.findByUsuarioAndPublicacion(colaborador, publicacion);

        if (votoOpt.isPresent()) {
            votoPublicacionRepository.delete(votoOpt.get());
            return;
        }

        VotoPublicacion voto = new VotoPublicacion();
        voto.setUsuario(colaborador);
        voto.setPublicacion(publicacion);
        voto.setTipo(TipoVoto.POSITIVO);
        votoPublicacionRepository.save(voto);
    }

    @Transactional
    public void alternarLikeRespuesta(Usuario colaborador, Long respuestaId) {
        RespuestaForo respuesta = buscarRespuestaActiva(respuestaId);
        validarAccesoLectura(colaborador, respuesta.getPublicacion().getForo().getId());

        Optional<VotoRespuesta> votoOpt =
                votoRespuestaRepository.findByUsuarioAndRespuesta(colaborador, respuesta);

        if (votoOpt.isPresent()) {
            votoRespuestaRepository.delete(votoOpt.get());
            return;
        }

        VotoRespuesta voto = new VotoRespuesta();
        voto.setUsuario(colaborador);
        voto.setRespuesta(respuesta);
        voto.setTipo(TipoVoto.POSITIVO);
        votoRespuestaRepository.save(voto);
    }


    public PublicacionForo obtenerPublicacion(Usuario colaborador, Long publicacionId) {
        PublicacionForo publicacion = buscarPublicacionActivaConDetalle(publicacionId);
        validarAccesoLectura(colaborador, publicacion.getForo().getId());
        return publicacion;
    }

    @Transactional
    public void crearPublicacion(Usuario colaborador, Long foroId, String titulo,
                                 String contenido, Long etiquetaId) {
        Foro foro = validarAccesoEscritura(colaborador, foroId);

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
        validarAccesoLectura(colaborador, publicacion.getForo().getId());
        return respuestaForoRepository.findByPublicacionConAutor(publicacion);
    }

    @Transactional
    public void crearRespuesta(Usuario colaborador, Long publicacionId, String contenido) {
        PublicacionForo publicacion = buscarPublicacionActiva(publicacionId);
        validarAccesoEscritura(colaborador, publicacion.getForo().getId());

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
        if (contenido == null || esContenidoVacio(contenido)) {
            throw new IllegalArgumentException("Escribe algo antes de publicar.");
        }
        return contenido.trim();
    }

    //El editor de texto (Quill) manda algo como "<p><br></p>" cuando no se escribió nada,
    //entonces no basta con mirar si el string está vacío sino que debemos quitarle las etiquetas HTML
    //y los espacios en blanco que use como relleno, ya que ahi recién podremos revisar si quedó texto real.
    private boolean esContenidoVacio(String contenidoHtml) {
        //Eliminamos todas las etiquetas HTML como <p>, <strong>, <br>, etc.
        //buscando cualquier patrón que empiece con '<' y termine con '>', reemplazándolo por vacío.
        String sinEtiquetas = contenidoHtml.replaceAll("<[^>]*>", "");

        //Eliminamos los espacios en blanco especiales de HTML como &nbsp; generados por la barra espaciadora
        //y aplicamos .trim() para limpiar los espacios en blanco ordinarios al inicio y al final
        String sinEspacios = sinEtiquetas.replace("&nbsp;", "").trim();
        return sinEspacios.isEmpty();
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

    // ============================================================
    // SUBIDA DE IMÁGENES DEL EDITOR (Quill)
    // ============================================================

    public String subirImagenForo(MultipartFile imagen) {
        if (imagen == null || imagen.isEmpty()) {
            throw new IllegalArgumentException("La imagen no puede estar vacía.");
        }
        if (!TIPOS_IMAGEN_PERMITIDOS.contains(imagen.getContentType())) {
            throw new IllegalArgumentException("Solo se permiten imágenes JPEG, PNG, GIF o WEBP.");
        }
        if (imagen.getSize() > TAMANO_MAXIMO_IMAGEN_BYTES) {
            throw new IllegalArgumentException("La imagen no puede superar los 5 MB.");
        }

        String extension = ".jpg";
        if ("image/png".equals(imagen.getContentType())) {
            extension = ".png";
        } else if ("image/gif".equals(imagen.getContentType())) {
            extension = ".gif";
        } else if ("image/webp".equals(imagen.getContentType())) {
            extension = ".webp";
        }

        try {
            Path carpeta = Path.of(uploadDir, "foro");
            Files.createDirectories(carpeta);
            String nombreArchivo = "foro-" + UUID.randomUUID() + extension;
            Path destino = carpeta.resolve(nombreArchivo);
            Files.copy(imagen.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/foro/" + nombreArchivo;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la imagen del foro.", e);
        }
    }
}