package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.dto.NotificacionView;
import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class NotificacionService {
    private final NotificacionRepository notificacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final ActividadRepository actividadRepository;
    private final AsignacionRepository asignacionRepository;
    private final ForoRepository foroRepository;
    private final DocumentoRepository documentoRepository;

    public NotificacionService(NotificacionRepository notificacionRepository,
                               UsuarioRepository usuarioRepository,
                               ActividadRepository actividadRepository,
                               AsignacionRepository asignacionRepository,
                               ForoRepository foroRepository,
                               DocumentoRepository documentoRepository) {
        this.notificacionRepository = notificacionRepository;
        this.usuarioRepository = usuarioRepository;
        this.actividadRepository = actividadRepository;
        this.asignacionRepository = asignacionRepository;
        this.foroRepository = foroRepository;
        this.documentoRepository = documentoRepository;
    }

    //Creamos una notificación para un usuario
    @Transactional
    public void crear(Usuario destinatario, String tipo, CategoriaNotificacion categoria,
                      String titulo, String descripcion, String entidad, Long entidadId) {
        Notificacion notificacion = new Notificacion();
        notificacion.setUsuario(destinatario);
        notificacion.setTipo(tipo);
        notificacion.setCategoria(categoria);
        notificacion.setTitulo(titulo);
        notificacion.setDescripcion(descripcion);
        notificacion.setEntidad(entidad);
        notificacion.setEntidadId(entidadId);
        notificacionRepository.save(notificacion);
    }


    //Creamos notificaciones para todos los RM
    @Transactional
    public void crearParaTodosLosRm(String tipo, CategoriaNotificacion categoria, String titulo, String descripcion, String entidad, Long entidadId) {

        List<Usuario> resourceManagers = usuarioRepository.findActivosByRolNombre("RESOURCE_MANAGER");

        for (Usuario rm : resourceManagers) {
            crear(rm, tipo, categoria, titulo, descripcion, entidad, entidadId);
        }

    }

    @Transactional(readOnly = true)
    public List<NotificacionView> listar(Long usuarioId) {
        Usuario usuario = obtenerUsuario(usuarioId);
        return notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(usuario).stream()
                .limit(20).map(this::crearView).toList();
    }

    @Transactional
    public void marcarLeida(Long usuarioId, Long notificacionId) {
        Usuario usuario = obtenerUsuario(usuarioId);
        Notificacion notificacion = notificacionRepository.findByIdAndUsuario(notificacionId, usuario)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la notificación."));
        notificacion.setLeida(true);
        notificacionRepository.save(notificacion);
    }

    @Transactional
    public void marcarTodasLeidas(Long usuarioId) {
        Usuario usuario = obtenerUsuario(usuarioId);
        List<Notificacion> notificaciones =
                notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(usuario);
        notificaciones.forEach(item -> item.setLeida(true));
        notificacionRepository.saveAll(notificaciones);
    }

    private Usuario obtenerUsuario(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el usuario autenticado."));
    }

    private NotificacionView crearView(Notificacion item) {
        return new NotificacionView(
                item.getId(), item.getTitulo(), item.getDescripcion(),
                item.getCategoria().name(), item.isLeida(), item.getFechaCreacion(),
                url(item));
    }

    //Armamos el enlace al que redirige al hacer click en la notificación
    private String url(Notificacion item) {

        boolean esColaborador = item.getUsuario().getRol().getNombre().equals("COLABORADOR");

        if ("ASIGNACION".equals(item.getEntidad()) && item.getEntidadId() != null && esColaborador) {
            return "/colaborador/proyectos/detalle?asignacionId=" + item.getEntidadId();
        }
        if ("CERTIFICADO".equals(item.getEntidad()) && esColaborador) {
            return "/colaborador/perfil";
        }
        if ("EDUCACION".equals(item.getEntidad()) && item.getEntidadId() != null) {
            if (item.getUsuario().getRol().getNombre().equals("RESOURCE_MANAGER")) {
                return "/rm/colaboradores/educacion/revision?id=" + item.getEntidadId();
            }
            if (esColaborador) {
                return "/colaborador/perfil";
            }
        }
        if ("COLABORADOR_CURSO".equals(item.getEntidad())
                && item.getUsuario().getRol().getNombre().equals("RESOURCE_MANAGER")) {
            if ("EVIDENCIA_CURSO_PENDIENTE".equals(item.getTipo())) {
                return "/rm/cursos/solicitudes?estado=EVIDENCIA_PENDIENTE";
            }
            return "/rm/cursos/solicitudes";
        }
        if ("FORO".equals(item.getEntidad()) && item.getEntidadId() != null) {
            Optional<Foro> foroOpt = foroRepository.findById(item.getEntidadId());
            if (foroOpt.isPresent()) {
                Foro foro = foroOpt.get();
                String rolNombre = item.getUsuario().getRol().getNombre();
                if (rolNombre.equals("PROJECT_MANAGER") && foro.getProyecto() != null) {
                    return "/pm/foro/detalle?proyectoId=" + foro.getProyecto().getId();
                }
                if (rolNombre.equals("RESOURCE_MANAGER")) {
                    return "/rm/foros/detalle?id=" + foro.getId();
                }
                return "/colaborador/foros/detalle?foroId=" + foro.getId();
            }
        }
        if ("DOCUMENTO".equals(item.getEntidad()) && item.getEntidadId() != null) {
            Optional<Documento> documentoOpt = documentoRepository.findById(item.getEntidadId());
            if (documentoOpt.isPresent()) {
                Documento documento = documentoOpt.get();
                String rolNombre = item.getUsuario().getRol().getNombre();
                if (rolNombre.equals("PROJECT_MANAGER")) {
                    return "/pm/proyectos/detalle?id=" + documento.getProyecto().getId();
                }
                if (esColaborador) {
                    Optional<Asignacion> asignacionOpt = asignacionRepository.findFirstByProyectoAndColaboradorAndEstado(
                            documento.getProyecto(), item.getUsuario(), EstadoAsignacion.ACTIVA);
                    if (asignacionOpt.isPresent()) {
                        return "/colaborador/proyectos/detalle?asignacionId=" + asignacionOpt.get().getId() + "&tab=documentos";
                    }
                }
            }
        }
        if ("ACTIVIDAD".equals(item.getEntidad()) && item.getEntidadId() != null) {

            Optional<Actividad> actividadOpt = actividadRepository.findById(item.getEntidadId());

            if (actividadOpt.isPresent()) {
                Actividad actividad = actividadOpt.get();
                boolean esPm = item.getUsuario().getRol().getNombre().equals("PROJECT_MANAGER");

                if (esPm) {
                    return "/pm/actividades?proyectoId=" + actividad.getProyecto().getId();
                }

                Optional<Asignacion> asignacionOpt = asignacionRepository.findFirstByProyectoAndColaboradorAndEstado(actividad.getProyecto(), actividad.getColaborador(), EstadoAsignacion.ACTIVA);

                if (asignacionOpt.isPresent()) {
                    return "/colaborador/proyectos/detalle?asignacionId=" + asignacionOpt.get().getId();
                }


            }


        }
        return "#";
    }
}