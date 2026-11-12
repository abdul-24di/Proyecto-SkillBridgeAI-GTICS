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
    private final ConversacionRepository conversacionRepository;
    private final ConversacionUsuarioRepository conversacionUsuarioRepository;


    public NotificacionService(NotificacionRepository notificacionRepository,
                               UsuarioRepository usuarioRepository,
                               ActividadRepository actividadRepository,
                               AsignacionRepository asignacionRepository,
                               ForoRepository foroRepository,
                               DocumentoRepository documentoRepository,
                               ConversacionRepository conversacionRepository,
                               ConversacionUsuarioRepository conversacionUsuarioRepository) {
        this.conversacionRepository = conversacionRepository;
        this.conversacionUsuarioRepository = conversacionUsuarioRepository;
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

    // Avisa solo al RM responsable del proyecto. Si todavía no tiene RM no avisa a nadie
    // cuando se le asigne uno, ese RM recibe su aviso de proyecto asignado.
    @Transactional
    public void crearParaRmDelProyecto(Proyecto proyecto, String tipo, CategoriaNotificacion categoria,
                                       String titulo, String descripcion, String entidad, Long entidadId) {
        Usuario rm = proyecto.getRmRevisor();
        if (rm != null) {
            crear(rm, tipo, categoria, titulo, descripcion, entidad, entidadId);
        }
    }



    // Avisa a un RM concreto. Si no hay RM o avisa a nadie.
    @Transactional
    public void crearParaRm(Usuario rm, String tipo, CategoriaNotificacion categoria,
                            String titulo, String descripcion, String entidad, Long entidadId) {
        if (rm != null) {
            crear(rm, tipo, categoria, titulo, descripcion, entidad, entidadId);
        }
    }


    //Creamos notificaciones para todos los Administradores
    @Transactional
    public void crearParaTodosLosAdmins(String tipo, CategoriaNotificacion categoria, String titulo, String descripcion, String entidad, Long entidadId) {

        List<Usuario> administradores = usuarioRepository.findActivosByRolNombre("ADMINISTRADOR");

        for (Usuario admin : administradores) {
            crear(admin, tipo, categoria, titulo, descripcion, entidad, entidadId);
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

        String rolNombre = item.getUsuario().getRol().getNombre();
        boolean esColaborador = rolNombre.equals("COLABORADOR");
        boolean esPm = rolNombre.equals("PROJECT_MANAGER");
        boolean esRm = rolNombre.equals("RESOURCE_MANAGER");

        if ("ASIGNACION".equals(item.getEntidad()) && item.getEntidadId() != null) {
            if (esColaborador) return "/colaborador/proyectos/detalle?asignacionId=" + item.getEntidadId();
            if (esPm) {

                return "/pm/proyectos";
            }
        }
        if ("PROYECTO".equals(item.getEntidad()) && item.getEntidadId() != null) {
            if (esPm) return "/pm/proyectos/detalle?id=" + item.getEntidadId();
            if (esRm) return "/rm/proyectos/detalle?id=" + item.getEntidadId();
        }
        if ("CERTIFICADO".equals(item.getEntidad()) && esColaborador) {
            return "/colaborador/perfil";
        }
        if ("EDUCACION".equals(item.getEntidad()) && item.getEntidadId() != null) {
            if (esRm) return "/rm/colaboradores/educacion/revision?id=" + item.getEntidadId();
            if (esColaborador) return "/colaborador/perfil";
        }
        if ("COLABORADOR_CURSO".equals(item.getEntidad()) && esRm) {
            if ("EVIDENCIA_CURSO_PENDIENTE".equals(item.getTipo())) {
                return "/rm/cursos/solicitudes?estado=EVIDENCIA_PENDIENTE";
            }
            return "/rm/cursos/solicitudes";
        }
        if ("COLABORADOR_CURSO".equals(item.getEntidad()) && esColaborador) {
            return "/colaborador/perfil#mis-cursos";
        }
        if ("USUARIO".equals(item.getEntidad()) && item.getEntidadId() != null) {
            if ("CV_PENDIENTE".equals(item.getTipo()) && rolNombre.equals("ADMINISTRADOR")) {
                return "/admin/experiencia/" + item.getEntidadId();
            }
            if ("CUENTA_ACTIVADA".equals(item.getTipo()) && rolNombre.equals("ADMINISTRADOR")) {
                return "/admin/usuarios";
            }
            if ("CV_REVISADO".equals(item.getTipo()) && esColaborador) {
                return "/colaborador/perfil";
            }
            if ("CV_RECHAZADO".equals(item.getTipo()) && esColaborador) {
                return "/colaborador/registro";
            }
        }

        if ("FORO".equals(item.getEntidad()) && item.getEntidadId() != null) {
            Optional<Foro> foroOpt = foroRepository.findById(item.getEntidadId());
            if (foroOpt.isPresent()) {
                Foro foro = foroOpt.get();
                if (esPm && foro.getProyecto() != null) {
                    return "/pm/foro/detalle?proyectoId=" + foro.getProyecto().getId();
                }
                if (esRm) return "/rm/foros/detalle?id=" + foro.getId();
                return "/colaborador/foros/detalle?foroId=" + foro.getId();
            }
        }

        if ("DOCUMENTO".equals(item.getEntidad()) && item.getEntidadId() != null) {
            Optional<Documento> documentoOpt = documentoRepository.findById(item.getEntidadId());
            if (documentoOpt.isPresent()) {
                Documento documento = documentoOpt.get();
                if (esPm) return "/pm/proyectos/detalle?id=" + documento.getProyecto().getId();
                if (esColaborador) return "/colaborador/proyectos/detalle?asignacionId=" + documento.getProyecto().getId();
            }
        }

        if ("ACTIVIDAD".equals(item.getEntidad()) && item.getEntidadId() != null) {
            Optional<Actividad> actividadOpt = actividadRepository.findById(item.getEntidadId());
            if (actividadOpt.isPresent()) {
                Actividad actividad = actividadOpt.get();
                if (esPm) return "/pm/actividades?proyectoId=" + actividad.getProyecto().getId();
                if (esColaborador) return "/colaborador/proyectos/detalle?asignacionId=" + actividad.getProyecto().getId();
            }
        }


            if ("CHAT".equals(item.getEntidad()) && item.getEntidadId() != null) {
                if (esPm) return "/pm/chat?proyectoId=" + item.getEntidadId();
                if (esColaborador) return "/colaborador/chat?proyectoId=" + item.getEntidadId();
            }


            if ("CHAT_PRIVADO".equals(item.getEntidad()) && item.getEntidadId() != null) {
                Optional<Conversacion> convOpt = conversacionRepository.findById(item.getEntidadId());
                if (convOpt.isPresent()) {
                    Conversacion conv = convOpt.get();
                    Long otroId = null;
                    List<ConversacionUsuario> miembros = conversacionUsuarioRepository.findByConversacion(conv);
                    for (ConversacionUsuario m : miembros) {
                        if (!m.getUsuario().getId().equals(item.getUsuario().getId())) {
                            otroId = m.getUsuario().getId();
                        }
                    }
                    if (otroId != null) {
                        String base = esPm ? "/pm/chat" : (esRm ? "/rm/chat" : "/colaborador/chat");
                        return base + "?proyectoId=" + conv.getProyecto().getId() + "&usuarioId=" + otroId;
                    }
                }
            }


        if (esPm) return "/pm/dashboard";
        if (esRm) return "/rm/dashboard";
        if (esColaborador) return "/colaborador/dashboard";
        
        return "#";
    }
}