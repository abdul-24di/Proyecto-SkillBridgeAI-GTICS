package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.dto.ForoPublicacionView;
import com.pucp.skillb_ia.service.ForoDetalleService;
import com.pucp.skillb_ia.service.ForoDetalleService.PermisosForo;
import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.TipoForo;
import com.pucp.skillb_ia.model.enums.TipoVoto;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.service.ArchivoAlmacenamientoService;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.NotificacionService;
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

/**
 * Lógica de los foros desde el lado del colaborador: qué foros puede ver, dónde puede
 * participar, y cómo crea, edita y elimina publicaciones y respuestas, da "me gusta" y marca
 * soluciones.
 * <p>
 * Distingue dos niveles de acceso:
 * <ul>
 *   <li><b>Lectura</b>: cualquier foro público, o un foro de proyecto donde el colaborador tuvo
 *       una asignación {@code ACTIVA} o {@code FINALIZADA} (así el foro sigue visible, en solo
 *       lectura, cuando el proyecto termina).</li>
 *   <li><b>Participación</b> (publicar y responder): un foro general público, o un foro de
 *       proyecto donde tiene asignación {@code ACTIVA}.</li>
 * </ul>
 * Las publicaciones y respuestas se eliminan de forma lógica (quedan inactivas). Las reglas
 * inválidas se informan con {@link IllegalArgumentException}.
 */
@Service
public class ColaboradorForoService {

    /** Máximo de caracteres del título de una publicación. */
    private static final int TITULO_MAXIMO = 200;


    /**
     * Máximo de caracteres del contenido de una publicación. Usa los mismos topes que el foro
     * del PM ({@code PmForoPublicacionForm} y {@code PmForoRespuestaForm}).
     */
    private static final int CONTENIDO_PUBLICACION_MAXIMO = 10000;

    /** Máximo de caracteres del contenido de una respuesta. */
    private static final int CONTENIDO_RESPUESTA_MAXIMO = 5000;

    /** Tipos MIME de imagen aceptados dentro del contenido (editor Quill): JPEG, PNG, GIF y WEBP. */
    private static final Set<String> TIPOS_IMAGEN_PERMITIDOS =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    /** Tamaño máximo de una imagen del editor: 5 MB. */
    private static final long TAMANO_MAXIMO_IMAGEN_BYTES = 5L * 1024 * 1024; // 5 MB 5 MB

    private final ForoRepository foroRepository;
    private final PublicacionForoRepository publicacionForoRepository;
    private final RespuestaForoRepository respuestaForoRepository;
    private final AsignacionRepository asignacionRepository;
    private final EtiquetaRepository etiquetaRepository;
    private final VotoPublicacionRepository votoPublicacionRepository;
    private final VotoRespuestaRepository votoRespuestaRepository;
    private final AuditoriaService auditoriaService;

    private final NotificacionService notificacionService;
    private final ArchivoAlmacenamientoService archivoAlmacenamientoService;
    private final ForoDetalleService foroDetalleService;

    /**
     * Inyecta por constructor las dependencias del service.
     *
     * @param foroRepository acceso a foros
     * @param publicacionForoRepository acceso a publicaciones
     * @param respuestaForoRepository acceso a respuestas
     * @param asignacionRepository acceso a asignaciones (para saber quién pertenece a un proyecto)
     * @param etiquetaRepository etiquetas de las publicaciones
     * @param votoPublicacionRepository "me gusta" de publicaciones
     * @param votoRespuestaRepository "me gusta" de respuestas
     * @param auditoriaService registro en el log de auditoría
     * @param notificacionService envío de notificaciones
     * @param archivoAlmacenamientoService guardado de imágenes
     * @param foroDetalleService arma la vista de detalle común a los cuatro roles
     */
    public ColaboradorForoService(ForoRepository foroRepository,
                                  PublicacionForoRepository publicacionForoRepository,
                                  RespuestaForoRepository respuestaForoRepository,
                                  AsignacionRepository asignacionRepository,
                                  EtiquetaRepository etiquetaRepository,
                                  VotoPublicacionRepository votoPublicacionRepository,
                                  VotoRespuestaRepository votoRespuestaRepository,
                                  AuditoriaService auditoriaService,
                                  NotificacionService notificacionService,
                                  ArchivoAlmacenamientoService archivoAlmacenamientoService,
                                  ForoDetalleService foroDetalleService) {
        this.foroDetalleService = foroDetalleService;
        this.foroRepository = foroRepository;
        this.publicacionForoRepository = publicacionForoRepository;
        this.respuestaForoRepository = respuestaForoRepository;
        this.asignacionRepository = asignacionRepository;
        this.etiquetaRepository = etiquetaRepository;
        this.votoPublicacionRepository = votoPublicacionRepository;
        this.votoRespuestaRepository = votoRespuestaRepository;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
        this.archivoAlmacenamientoService = archivoAlmacenamientoService;
    }

    // ============================================================
    // LISTAR FOROS A LOS QUE EL COLABORADOR TIENE ACCESO
    // ============================================================

    /**
     * Lista los foros públicos: los generales y los de proyectos que el PM compartió con toda la
     * comunidad. Alimenta la pestaña "Comunidad".
     *
     * @return foros públicos
     */
    public List<Foro> listarForosComunidad() {
        return foroRepository.findVisiblesParaTodos();
    }

    /**
     * Lista los foros de los proyectos donde el colaborador tiene asignación {@code ACTIVA}.
     * Alimenta la pestaña "Mis proyectos". Solo cuenta foros de tipo {@code PROYECTO}.
     *
     * @param colaborador colaborador consultado
     * @return foros de sus proyectos activos
     */
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

    /**
     * Obtiene el foro de un proyecto concreto (el de la pestaña "Foro" del detalle del proyecto).
     *
     * @param proyecto proyecto consultado
     * @return el foro, o vacío si el proyecto no tiene uno
     */
    public Optional<Foro> obtenerForoDeProyecto(Proyecto proyecto) {
        return foroRepository.findByProyecto(proyecto);
    }





    /**
     * Verifica que el colaborador pueda <b>leer</b> un foro y lo devuelve.
     * <ul>
     *   <li>Un foro público (general, o de proyecto compartido con la comunidad) siempre se puede leer.</li>
     *   <li>Un foro privado de proyecto se puede leer si el colaborador tuvo asignación
     *       {@code ACTIVA} o {@code FINALIZADA} en él; así queda visible en solo lectura aun
     *       después de que el proyecto termine.</li>
     * </ul>
     *
     * @param colaborador colaborador que quiere leer
     * @param foroId foro solicitado
     * @return el foro
     * @throws IllegalArgumentException si el foro no existe o no tiene acceso
     */
    private Foro validarAccesoLectura(Usuario colaborador, Long foroId) {
        Optional<Foro> foroOpt = foroRepository.findByIdConProyectoYPm(foroId);
        if (foroOpt.isEmpty()) {
            throw new IllegalArgumentException("El foro no existe.");
        }
        Foro foro = foroOpt.get();

        if (foro.isVisibleParaTodos()) {
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

    /**
     * Indica si el colaborador puede <b>participar</b> (publicar o responder) en el foro, no solo leerlo.
     * <ul>
     *   <li>Foro {@code GENERAL}: participa cualquiera si el foro es público.</li>
     *   <li>Foro de {@code PROYECTO}: solo quien tiene asignación {@code ACTIVA} en ese proyecto,
     *       aunque el foro esté compartido con la comunidad; para el resto es de solo lectura.</li>
     * </ul>
     *
     * @param colaborador colaborador evaluado
     * @param foro foro evaluado
     * @return {@code true} si puede publicar y responder
     */
    public boolean puedeParticipar(Usuario colaborador, Foro foro) {
        if (foro.getTipo() == TipoForo.GENERAL) {
            return foro.isEsPublico();
        }
        return asignacionRepository.existsByProyectoAndColaboradorAndEstado(
                foro.getProyecto(), colaborador, EstadoAsignacion.ACTIVA);
    }

    /**
     * Verifica que el colaborador pueda leer el foro y, además, participar en él. Es la
     * validación previa a crear publicaciones y respuestas.
     *
     * @param colaborador colaborador que quiere escribir
     * @param foroId foro solicitado
     * @return el foro
     * @throws IllegalArgumentException si no puede leerlo o si es de solo lectura para él
     */
    private Foro validarAccesoEscritura(Usuario colaborador, Long foroId) {
        Foro foro = validarAccesoLectura(colaborador, foroId);
        if (foro.isAnuncios()) {
            throw new IllegalArgumentException(
                    "Este es un foro de anuncios: solo publica el Administrador y no admite respuestas.");
        }
        if (!puedeParticipar(colaborador, foro)) {
            throw new IllegalArgumentException(
                    "Este foro es de solo lectura para ti: no tienes una asignación activa en este proyecto.");
        }
        return foro;
    }

    /**
     * Devuelve un foro después de comprobar que el colaborador al menos pueda leerlo.
     *
     * @param colaborador colaborador que consulta
     * @param foroId foro solicitado
     * @return el foro
     * @throws IllegalArgumentException si no existe o no tiene acceso
     * @see #validarAccesoLectura
     */
    public Foro obtenerForo(Usuario colaborador, Long foroId) {
        return validarAccesoLectura(colaborador, foroId);
    }

    /**
     * Versión de {@link #puedeParticipar(Usuario, Foro)} que recibe el id del foro. Primero
     * comprueba que pueda leerlo.
     *
     * @param colaborador colaborador evaluado
     * @param foroId id del foro
     * @return {@code true} si puede publicar y responder
     * @throws IllegalArgumentException si el foro no existe o no puede leerlo
     */
    public boolean puedeParticipar(Usuario colaborador, Long foroId) {
        Foro foro = validarAccesoLectura(colaborador, foroId);
        return puedeParticipar(colaborador, foro);
    }

    /**
     * Lista los foros donde el colaborador puede publicar: los generales públicos y los de los
     * proyectos donde tiene asignación activa. Excluye los foros de proyecto que solo están
     * compartidos con la comunidad, porque para quien no es del proyecto son de solo lectura.
     * Alimenta el selector del modal "Nueva publicación".
     *
     * @param colaborador colaborador consultado
     * @return foros destino válidos
     */
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

    /**
     * Lista todas las etiquetas disponibles para clasificar publicaciones.
     *
     * @return etiquetas
     */
    public List<Etiqueta> listarEtiquetas() {
        return etiquetaRepository.findAll();
    }


    // ============================================================
    // PUBLICACIONES
    // ============================================================
    /**
     * Lista las publicaciones de un foro con su detalle, tras comprobar que el colaborador puede
     * leerlo.
     *
     * @param colaborador colaborador que consulta
     * @param foroId foro consultado
     * @return publicaciones del foro
     * @throws IllegalArgumentException si no existe el foro o no puede leerlo
     */
    public List<PublicacionForo> listarPublicaciones(Usuario colaborador, Long foroId) {
        Foro foro = validarAccesoLectura(colaborador, foroId);
        return publicacionForoRepository.findByForoConDetalle(foro);
    }

    /**
     * Arma las publicaciones con sus respuestas para la vista de detalle, que es común a los
     * cuatro roles. Quien puede leer el foro puede dar "me gusta"; responder y marcar la
     * solución de sus propias publicaciones exige poder participar. El colaborador nunca
     * puede moderar soluciones ajenas ni eliminar contenido de otros.
     *
     * @param colaborador colaborador que consulta
     * @param foroId foro consultado
     * @param orden criterio de orden ya normalizado (por ejemplo por fecha o por más votadas)
     * @return publicaciones con sus respuestas y permisos
     * @throws IllegalArgumentException si no existe el foro o no puede leerlo
     * @see ForoDetalleService#listar
     */
    @Transactional(readOnly = true)
    public List<ForoPublicacionView> listarPublicacionesConRespuestas(Usuario colaborador, Long foroId, String orden) {
        Foro foro = validarAccesoLectura(colaborador, foroId);
        PermisosForo permisos = new PermisosForo(true, puedeParticipar(colaborador, foro), false, false);
        return foroDetalleService.listar(foro, colaborador, permisos, orden);
    }

    // ============================================================
    // LIKES
    // ============================================================

    /**
     * Da o quita el "me gusta" a una publicación. Es un interruptor: si el colaborador ya había
     * votado, el voto se elimina; si no, se crea un voto {@code POSITIVO}. Un usuario tiene como
     * máximo un voto por publicación. Basta con poder <b>leer</b> el foro, no hace falta participar.
     *
     * @param colaborador colaborador que vota
     * @param publicacionId publicación votada
     * @throws IllegalArgumentException si la publicación no existe o no puede leer su foro
     */
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


    /**
     * Obtiene una publicación activa con su detalle, tras comprobar que puede leer su foro.
     *
     * @param colaborador colaborador que consulta
     * @param publicacionId publicación solicitada
     * @return la publicación
     * @throws IllegalArgumentException si no existe, fue eliminada o no puede leer su foro
     */
    public PublicacionForo obtenerPublicacion(Usuario colaborador, Long publicacionId) {
        PublicacionForo publicacion = buscarPublicacionActivaConDetalle(publicacionId);
        validarAccesoLectura(colaborador, publicacion.getForo().getId());
        return publicacion;
    }

    /**
     * Crea una publicación en un foro.
     * <p>
     * Exige poder <b>participar</b> en el foro. Valida el título (obligatorio, máx. 200
     * caracteres), el contenido (no vacío de verdad y máx. 10000 caracteres) y la etiqueta (opcional,
     * debe existir). Registra auditoría y, si el foro es de un proyecto y quien publica no es el
     * PM, notifica al PM del proyecto.
     *
     * @param colaborador autor de la publicación
     * @param foroId foro donde publica
     * @param titulo título
     * @param contenido contenido en HTML del editor
     * @param etiquetaId etiqueta opcional
     * @throws IllegalArgumentException si no puede participar o los datos no son válidos
     */
    @Transactional
    public void crearPublicacion(Usuario colaborador, Long foroId, String titulo,
                                 String contenido, Long etiquetaId) {
        Foro foro = validarAccesoEscritura(colaborador, foroId);

        String tituloValidado = validarTitulo(titulo);
        String contenidoValidado = validarContenido(contenido, CONTENIDO_PUBLICACION_MAXIMO, "La publicación");
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

        if (foro.getTipo() == TipoForo.PROYECTO && foro.getProyecto() != null
                && foro.getProyecto().getPm() != null
                && !foro.getProyecto().getPm().getId().equals(colaborador.getId())) {
            notificacionService.crear(foro.getProyecto().getPm(), "PUBLICACION_FORO_PROYECTO",
                    CategoriaNotificacion.MENSAJE,
                    "Nueva publicación en el foro de tu proyecto",
                    colaborador.getNombre() + " " + colaborador.getApellido()
                            + " publicó \"" + tituloValidado + "\" en el foro de \""
                            + foro.getProyecto().getNombre() + "\".",
                    "FORO", foro.getId());
        }
    }

    /**
     * Edita título, contenido y etiqueta de una publicación. Solo puede hacerlo su autor.
     *
     * @param colaborador usuario que edita
     * @param publicacionId publicación a editar
     * @param titulo título nuevo
     * @param contenido contenido nuevo
     * @param etiquetaId etiqueta nueva (opcional)
     * @throws IllegalArgumentException si no es el autor o los datos no son válidos
     */
    @Transactional
    public void editarPublicacion(Usuario colaborador, Long publicacionId, String titulo,
                                  String contenido, Long etiquetaId) {
        PublicacionForo publicacion = buscarPublicacionActiva(publicacionId);

        if (!publicacion.getAutor().getId().equals(colaborador.getId())) {
            throw new IllegalArgumentException("No puedes editar una publicación que no es tuya.");
        }

        String tituloValidado = validarTitulo(titulo);
        String contenidoValidado = validarContenido(contenido, CONTENIDO_PUBLICACION_MAXIMO, "La publicación");
        Etiqueta etiqueta = obtenerEtiquetaOpcional(etiquetaId);

        publicacion.setTitulo(tituloValidado);
        publicacion.setContenido(contenidoValidado);
        publicacion.setEtiqueta(etiqueta);
        publicacionForoRepository.save(publicacion);

        auditoriaService.registrar(colaborador, "EDITAR_PUBLICACION_FORO", "PUBLICACION_FORO",
                publicacion.getId(), "Editó la publicación \"" + tituloValidado + "\".");
    }

    /**
     * Elimina una publicación. Solo puede hacerlo su autor o un Administrador. Es un borrado
     * lógico: la publicación queda inactiva y deja de mostrarse. Registra auditoría.
     *
     * @param colaborador usuario que elimina
     * @param publicacionId publicación a eliminar
     * @throws IllegalArgumentException si no existe o si no es el autor ni Administrador
     */
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


    /**
     * @param publicacionId id de la publicación
     * @return la publicación, solo si está activa (no eliminada)
     * @throws IllegalArgumentException si no existe o ya fue eliminada
     */
    private PublicacionForo buscarPublicacionActiva(Long publicacionId) {
        Optional<PublicacionForo> pubOpt = publicacionForoRepository.findByIdAndActivoTrue(publicacionId);
        if (pubOpt.isEmpty()) {
            throw new IllegalArgumentException("La publicación no existe o ya fue eliminada.");
        }
        return pubOpt.get();
    }

    /**
     * Igual que {@link #buscarPublicacionActiva}, pero trae también el foro y el autor en la
     * misma consulta, para no hacer consultas adicionales al usarlos.
     *
     * @param publicacionId id de la publicación
     * @return la publicación con su detalle
     * @throws IllegalArgumentException si no existe o ya fue eliminada
     */
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

    /**
     * Lista las respuestas de una publicación con su autor, tras comprobar que el colaborador
     * puede leer el foro.
     *
     * @param colaborador colaborador que consulta
     * @param publicacionId publicación consultada
     * @return respuestas de la publicación
     * @throws IllegalArgumentException si la publicación no existe o no puede leer su foro
     */
    public List<RespuestaForo> listarRespuestas(Usuario colaborador, Long publicacionId) {
        PublicacionForo publicacion = buscarPublicacionActiva(publicacionId);
        validarAccesoLectura(colaborador, publicacion.getForo().getId());
        return respuestaForoRepository.findByPublicacionConAutor(publicacion);
    }


    /**
     * Responde a una publicación. Exige poder <b>participar</b> en su foro y un contenido no
     * vacío de máximo 5000 caracteres. Registra auditoría y notifica al autor de la
     * publicación, salvo que se responda a sí mismo.
     *
     * @param colaborador autor de la respuesta
     * @param publicacionId publicación que se responde
     * @param contenido contenido en HTML del editor
     * @throws IllegalArgumentException si no puede participar o el contenido no es válido
     */
    @Transactional
    public void crearRespuesta(Usuario colaborador, Long publicacionId, String contenido) {
        PublicacionForo publicacion = buscarPublicacionActiva(publicacionId);
        validarAccesoEscritura(colaborador, publicacion.getForo().getId());

        String contenidoValidado = validarContenido(contenido, CONTENIDO_RESPUESTA_MAXIMO, "La respuesta");

        RespuestaForo respuesta = new RespuestaForo();
        respuesta.setPublicacion(publicacion);
        respuesta.setAutor(colaborador);
        respuesta.setContenido(contenidoValidado);
        respuestaForoRepository.save(respuesta);

        auditoriaService.registrar(colaborador, "CREAR_RESPUESTA_FORO", "RESPUESTA_FORO",
                respuesta.getId(), "Respondió la publicación \"" + publicacion.getTitulo() + "\".");

        //No nos notificamos a nosotros mismos si respondemos nuestra propia publicación
        if (!publicacion.getAutor().getId().equals(colaborador.getId())) {
            notificacionService.crear(publicacion.getAutor(), "RESPUESTA_FORO", CategoriaNotificacion.MENSAJE,
                    "Nueva respuesta en tu publicación",
                    colaborador.getNombre() + " " + colaborador.getApellido()
                            + " respondió tu publicación \"" + publicacion.getTitulo() + "\".",
                    "FORO", publicacion.getForo().getId());
        }
    }

    /**
     * Edita el contenido de una respuesta. Solo puede hacerlo su autor. Valida el contenido
     * (no vacío, máx. 5000 caracteres) y registra auditoría.
     * @param colaborador usuario que edita
     * @param respuestaId respuesta a editar
     * @param contenido contenido nuevo
     * @throws IllegalArgumentException si no es el autor o el contenido no es válido
     */
    @Transactional
    public void editarRespuesta(Usuario colaborador, Long respuestaId, String contenido) {
        RespuestaForo respuesta = buscarRespuestaActiva(respuestaId);

        if (!respuesta.getAutor().getId().equals(colaborador.getId())) {
            throw new IllegalArgumentException("No puedes editar una respuesta que no es tuya.");
        }

        String contenidoValidado = validarContenido(contenido, CONTENIDO_RESPUESTA_MAXIMO, "La respuesta");
        respuesta.setContenido(contenidoValidado);
        respuestaForoRepository.save(respuesta);

        auditoriaService.registrar(colaborador, "EDITAR_RESPUESTA_FORO", "RESPUESTA_FORO",
                respuesta.getId(), "Editó una respuesta.");
    }


    /**
     * Elimina una respuesta. Solo puede hacerlo su autor o un Administrador. Es un borrado
     * lógico y se registra en auditoría.
     *
     * @param colaborador usuario que elimina
     * @param respuestaId respuesta a eliminar
     * @throws IllegalArgumentException si no existe o si no es el autor ni Administrador
     */
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

    /**
     * @param respuestaId id de la respuesta
     * @return la respuesta, solo si está activa (no eliminada)
     * @throws IllegalArgumentException si no existe o ya fue eliminada
     */
    private RespuestaForo buscarRespuestaActiva(Long respuestaId) {
        Optional<RespuestaForo> respOpt = respuestaForoRepository.findByIdAndActivoTrue(respuestaId);
        if (respOpt.isEmpty()) {
            throw new IllegalArgumentException("La respuesta no existe o ya fue eliminada.");
        }
        return respOpt.get();
    }

    // ============================================================
    // MARCAR UNA RESPUESTA COMO SOLUCIÓN
    // Solo el autor de la publicación puede decidir cuál respuesta resolvió su
    // pregunta. Solo puede haber una solución por
    // publicación a la vez: al marcar una nueva se desmarca la anterior.
    // Volver a hacer clic sobre la que ya es la solución la desmarca.
    // ============================================================
    /**
     * Marca o desmarca una respuesta como la solución de la publicación.
     * <p>
     * Reglas: el colaborador debe poder leer el foro y ser el <b>autor de la publicación</b>
     * (solo quien preguntó decide qué respuesta lo resolvió). Solo puede haber una solución por
     * publicación: al marcar una nueva se desmarca la anterior; volver a pulsar sobre la que ya
     * es solución la desmarca. Registra auditoría y, al marcar, notifica al autor de la respuesta
     * salvo que sea el mismo colaborador.
     *
     * @param colaborador autor de la publicación
     * @param respuestaId respuesta a marcar o desmarcar
     * @throws IllegalArgumentException si la respuesta no existe, no puede leer el foro o no es el autor
     */
    @Transactional
    public void alternarSolucion(Usuario colaborador, Long respuestaId) {
        RespuestaForo respuesta = buscarRespuestaActiva(respuestaId);
        PublicacionForo publicacion = respuesta.getPublicacion();
        validarAccesoLectura(colaborador, publicacion.getForo().getId());

        if (!publicacion.getAutor().getId().equals(colaborador.getId())) {
            throw new IllegalArgumentException("Solo el autor de la publicación puede marcar una respuesta como solución.");
        }

        if (respuesta.isEsSolucion()) {

            respuesta.setEsSolucion(false);

            respuestaForoRepository.save(respuesta);

            auditoriaService.registrar(colaborador, "DESMARCAR_SOLUCION_FORO", "RESPUESTA_FORO",
                    respuesta.getId(), "Desmarcó la respuesta como solución en \"" + publicacion.getTitulo() + "\".");

            return;

        }

        respuestaForoRepository.findByPublicacionAndEsSolucionTrue(publicacion).ifPresent(anterior -> {
            anterior.setEsSolucion(false);
            respuestaForoRepository.save(anterior);
        });

        respuesta.setEsSolucion(true);
        respuestaForoRepository.save(respuesta);

        auditoriaService.registrar(colaborador, "MARCAR_SOLUCION_FORO", "RESPUESTA_FORO",
                respuesta.getId(), "Marcó una respuesta como solución en \"" + publicacion.getTitulo() + "\".");

        if (!respuesta.getAutor().getId().equals(colaborador.getId())) {

            notificacionService.crear(respuesta.getAutor(), "RESPUESTA_MARCADA_SOLUCION", CategoriaNotificacion.MENSAJE,
                    "Tu respuesta fue marcada como solución",
                    colaborador.getNombre() + " " + colaborador.getApellido()
                            + " marcó tu respuesta como la solución de \"" + publicacion.getTitulo() + "\".",
                    "FORO", publicacion.getForo().getId());
        }


    }

    // ============================================================
    // VALIDACIONES
    // ============================================================

    /**
     * Valida el título de una publicación.
     *
     * @param titulo título ingresado
     * @return el título sin espacios sobrantes
     * @throws IllegalArgumentException si está vacío o supera {@value #TITULO_MAXIMO} caracteres
     */
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

    /**
     * Valida el contenido de una publicación o respuesta.
     *
     * @param contenido contenido en HTML del editor
     * @param maximo máximo de caracteres permitido
     * @param que nombre de lo validado ("La publicación" o "La respuesta"), usado en el mensaje de error
     * @return el contenido sin espacios sobrantes
     * @throws IllegalArgumentException si está vacío (aunque tenga etiquetas) o supera el máximo
     */
    private String validarContenido(String contenido, int maximo, String que) {
        if (contenido == null || esContenidoVacio(contenido)) {
            throw new IllegalArgumentException("Escribe algo antes de publicar.");
        }
        String limpio = contenido.trim();
        if (limpio.length() > maximo) {
            throw new IllegalArgumentException(que + " no puede superar los " + maximo + " caracteres.");
        }
        return limpio;
    }

    /**
     * Indica si el contenido HTML no tiene texto real. El editor (Quill) envía algo como
     * {@code <p><br></p>} cuando no se escribió nada, así que no basta con mirar si el texto está
     * vacío: se quitan las etiquetas HTML y los {@code &nbsp;} y recién entonces se revisa si queda texto.
     *
     * @param contenidoHtml contenido a evaluar
     * @return {@code true} si solo hay etiquetas o espacios
     */
    private boolean esContenidoVacio(String contenidoHtml) {
        //Eliminamos todas las etiquetas HTML como <p>, <strong>, <br>, etc.
        //buscando cualquier patrón que empiece con '<' y termine con '>', reemplazándolo por vacío.
        String sinEtiquetas = contenidoHtml.replaceAll("<[^>]*>", "");

        //Eliminamos los espacios en blanco especiales de HTML como &nbsp; generados por la barra espaciadora
        //y aplicamos .trim() para limpiar los espacios en blanco ordinarios al inicio y al final
        String sinEspacios = sinEtiquetas.replace("&nbsp;", "").trim();
        return sinEspacios.isEmpty();
    }



    /**
     * @param etiquetaId id de la etiqueta, o {@code null} si no se eligió
     * @return la etiqueta, o {@code null} si no se eligió ninguna
     * @throws IllegalArgumentException si se indicó un id que no existe
     */
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

    /**
     * Sube una imagen insertada dentro del contenido de una publicación (editor Quill). Acepta
     * JPEG, PNG, GIF y WEBP de hasta 5 MB. Se guarda con nombre único
     * ({@code foro-<uuid>.<ext>}) en la carpeta {@code foro}.
     *
     * @param imagen imagen enviada por el editor
     * @return la URL donde quedó guardada, para insertarla en el contenido
     * @throws IllegalArgumentException si está vacía, no es de un tipo permitido o supera 5 MB
     */
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

        String nombreArchivo = "foro-" + UUID.randomUUID() + extension;
        return archivoAlmacenamientoService.guardar(imagen, "foro", nombreArchivo);
    }
}