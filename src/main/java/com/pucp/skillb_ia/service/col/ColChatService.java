package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.dto.ChatContactoView;
import com.pucp.skillb_ia.dto.PmMensajeView;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Conversacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.ChatBaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lógica del chat del proyecto desde el lado del colaborador.
 * <p>
 * Cada proyecto tiene un <b>chat grupal</b> y chats <b>privados</b> entre dos miembros. Los
 * miembros son el PM del proyecto y los colaboradores con asignación {@code ACTIVA}. El
 * Resource Manager <b>no</b> tiene acceso al chat, a diferencia del foro.
 * <p>
 * Este service solo valida los permisos y delega el trabajo (guardar, leer, marcar como leído y
 * avisar) en {@code ChatBaseService}, que es común a PM y colaborador.
 *
 * @see com.pucp.skillb_ia.service.ChatBaseService
 */
@Service
public class ColChatService {

    private final AsignacionRepository asignacionRepository;
    private final ProyectoRepository proyectoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ChatBaseService chatBaseService;

    /**
     * Inyecta por constructor las dependencias del service.
     *
     * @param asignacionRepository acceso a asignaciones (define quién es miembro de un proyecto)
     * @param proyectoRepository acceso a proyectos
     * @param usuarioRepository acceso a usuarios
     * @param chatBaseService lógica de chat compartida entre roles
     */
    public ColChatService(AsignacionRepository asignacionRepository,
                          ProyectoRepository proyectoRepository,
                          UsuarioRepository usuarioRepository,
                          ChatBaseService chatBaseService) {
        this.asignacionRepository = asignacionRepository;
        this.proyectoRepository = proyectoRepository;
        this.usuarioRepository = usuarioRepository;
        this.chatBaseService = chatBaseService;
    }

    /**
     * Lista los proyectos donde el colaborador tiene asignación {@code ACTIVA}: son los únicos
     * cuyo chat puede abrir.
     *
     * @param colaborador colaborador consultado
     * @return sus proyectos activos
     */
    public List<Proyecto> listarProyectosActivos(Usuario colaborador) {
        List<Proyecto> proyectos = new ArrayList<>();
        List<Asignacion> asignaciones = asignacionRepository.findByColaboradorAndEstado(colaborador, EstadoAsignacion.ACTIVA);

        for (Asignacion a : asignaciones) {
            proyectos.add(a.getProyecto());
        }

        return proyectos;
    }

    // ---------------- Validaciones ----------------

    /**
     * @param proyectoId id del proyecto
     * @return el proyecto
     * @throws IllegalArgumentException si no se indicó proyecto o no existe
     */
    private Proyecto buscarProyecto(Long proyectoId) {
        if (proyectoId == null) {
            throw new IllegalArgumentException("Debes seleccionar un proyecto.");
        }
        return proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado."));
    }

    /**
     * Verifica que el colaborador tenga una asignación {@code ACTIVA} en el proyecto. Es la
     * validación previa a cualquier operación del chat.
     *
     * @param proyecto proyecto del chat
     * @param colaborador colaborador que quiere usarlo
     * @throws IllegalArgumentException si no es un colaborador activo del proyecto
     */
    private void validarAcceso(Proyecto proyecto, Usuario colaborador) {
        List<Asignacion> asignaciones = asignacionRepository.findByColaboradorAndEstado(colaborador, EstadoAsignacion.ACTIVA);

        for (Asignacion a : asignaciones) {
            if (a.getProyecto().getId().equals(proyecto.getId())) {
                return;
            }
        }

        throw new IllegalArgumentException("No eres un colaborador activo de este proyecto.");
    }

    /**
     * Valida con quién se quiere chatear en privado: el otro usuario debe existir, no ser el
     * propio colaborador y ser el PM del proyecto o un colaborador con asignación {@code ACTIVA}.
     *
     * @param proyecto proyecto del chat
     * @param otroUsuarioId usuario con quien se quiere chatear
     * @param colaborador colaborador que escribe
     * @return el otro usuario
     * @throws IllegalArgumentException si falta el destinatario, es uno mismo, no existe o no es parte del proyecto
     */
    private Usuario validarDestinatario(Proyecto proyecto, Long otroUsuarioId, Usuario colaborador) {
        if (otroUsuarioId == null) {
            throw new IllegalArgumentException("Debes seleccionar con quién chatear.");
        }
        if (otroUsuarioId.equals(colaborador.getId())) {
            throw new IllegalArgumentException("No puedes escribirte a ti mismo.");
        }

        Usuario otro = usuarioRepository.findById(otroUsuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        if (proyecto.getPm().getId().equals(otroUsuarioId)) {
            return otro;
        }

        List<Asignacion> activas = asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA);
        for (Asignacion a : activas) {
            if (a.getColaborador().getId().equals(otroUsuarioId)) {
                return otro;
            }
        }

        throw new IllegalArgumentException("Esa persona no es parte de este proyecto.");
    }

    // ---------------- Contactos de PM y colaboradores ----------------

    /**
     * Lista las personas con quienes el colaborador puede chatear en privado: primero el PM del
     * proyecto (con el rótulo "Project Manager") y luego los demás colaboradores activos (con el
     * rótulo "Colaborador"), sin incluirse a sí mismo. Es una consulta de solo lectura.
     *
     * @param proyectoId proyecto del chat
     * @param colaborador colaborador que consulta
     * @return contactos disponibles
     * @throws IllegalArgumentException si el proyecto no existe o no es colaborador activo de él
     */
    @Transactional(readOnly = true)
    public List<ChatContactoView> listarContactos(Long proyectoId, Usuario colaborador) {
        Proyecto proyecto = buscarProyecto(proyectoId);
        validarAcceso(proyecto, colaborador);

        List<ChatContactoView> contactos = new ArrayList<>();
        contactos.add(new ChatContactoView(proyecto.getPm(), "Project Manager"));

        List<Asignacion> activas = asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA);
        for (Asignacion a : activas) {
            Usuario otro = a.getColaborador();
            if (!otro.getId().equals(colaborador.getId())) {
                contactos.add(new ChatContactoView(otro, "Colaborador"));
            }
        }
        return contactos;
    }

    // ---------------- Chat grupal ----------------

    /**
     * Obtiene los mensajes del chat <b>grupal</b> del proyecto. Si el proyecto aún no tiene chat
     * grupal, se crea en ese momento. Efecto secundario: marca la conversación como leída para
     * el colaborador.
     *
     * @param proyectoId proyecto del chat
     * @param colaborador colaborador que lee
     * @return mensajes de la conversación
     * @throws IllegalArgumentException si el proyecto no existe o no es colaborador activo de él
     */
    @Transactional
    public List<PmMensajeView> obtenerMensajes(Long proyectoId, Usuario colaborador) {
        Proyecto proyecto = buscarProyecto(proyectoId);
        validarAcceso(proyecto, colaborador);

        Conversacion conversacion = chatBaseService.obtenerOCrearGrupal(proyecto);
        chatBaseService.marcarLeida(conversacion, colaborador);
        return chatBaseService.listar(conversacion, colaborador.getId());
    }

    /**
     * Envía un mensaje al chat <b>grupal</b> del proyecto. El texto no puede estar vacío ni pasar
     * de 1000 caracteres. Se notifica a los demás miembros y se les avisa en tiempo real.
     *
     * @param proyectoId proyecto del chat
     * @param contenido texto del mensaje
     * @param colaborador autor del mensaje
     * @return el mensaje guardado
     * @throws IllegalArgumentException si el texto no es válido o no es colaborador activo del proyecto
     */
    @Transactional
    public PmMensajeView enviarMensaje(Long proyectoId, String contenido, Usuario colaborador) {
        chatBaseService.validarTexto(contenido);
        Proyecto proyecto = buscarProyecto(proyectoId);
        validarAcceso(proyecto, colaborador);

        return chatBaseService.enviarGrupal(proyecto, colaborador, contenido.trim());
    }

    // ---------------- Chat privado ----------------

    /**
     * Obtiene los mensajes de la conversación <b>privada</b> con otro miembro del proyecto. Si
     * todavía nunca hablaron, devuelve una lista vacía (la conversación se crea recién al enviar
     * el primer mensaje). Si existe, la marca como leída para el colaborador.
     *
     * @param proyectoId proyecto del chat
     * @param otroUsuarioId el otro participante
     * @param colaborador colaborador que lee
     * @return mensajes de la conversación, o lista vacía
     * @throws IllegalArgumentException si el destinatario no es válido o no es colaborador activo del proyecto
     */
    @Transactional
    public List<PmMensajeView> obtenerMensajesPrivados(Long proyectoId, Long otroUsuarioId, Usuario colaborador) {
        Proyecto proyecto = buscarProyecto(proyectoId);
        validarAcceso(proyecto, colaborador);
        Usuario otro = validarDestinatario(proyecto, otroUsuarioId, colaborador);

        Conversacion conversacion = chatBaseService.buscarPrivada(proyecto, colaborador, otro);
        if (conversacion == null) {
            return new ArrayList<>();
        }
        chatBaseService.marcarLeida(conversacion, colaborador);
        return chatBaseService.listar(conversacion, colaborador.getId());
    }

    /**
     * Envía un mensaje privado a otro miembro del proyecto (PM o colaborador activo). El texto
     * no puede estar vacío ni pasar de 1000 caracteres.
     *
     * @param proyectoId proyecto del chat
     * @param otroUsuarioId destinatario
     * @param contenido texto del mensaje
     * @param colaborador autor del mensaje
     * @return el mensaje guardado
     * @throws IllegalArgumentException si el texto o el destinatario no son válidos, o no es colaborador activo del proyecto
     */
    @Transactional
    public PmMensajeView enviarMensajePrivado(Long proyectoId, Long otroUsuarioId, String contenido, Usuario colaborador) {
        chatBaseService.validarTexto(contenido);
        Proyecto proyecto = buscarProyecto(proyectoId);
        validarAcceso(proyecto, colaborador);
        Usuario otro = validarDestinatario(proyecto, otroUsuarioId, colaborador);

        return chatBaseService.enviarPrivado(proyecto, colaborador, otro, contenido.trim());
    }


    // ---------------- Mensajes no leídos ----------------

    /**
     * Cuenta los mensajes no leídos del colaborador en cada chat del proyecto (el grupal y un
     * privado con cada miembro: el PM y los demás colaboradores activos). Es una consulta de
     * solo lectura; alimenta los indicadores de la pantalla del chat.
     *
     * @param proyectoId proyecto del chat
     * @param colaborador colaborador que consulta
     * @return cantidad de mensajes no leídos por chat
     * @throws IllegalArgumentException si el proyecto no existe o no es colaborador activo de él
     */
    @Transactional(readOnly = true)
    public Map<String, Long> obtenerNoLeidos(Long proyectoId, Usuario colaborador) {
        Proyecto proyecto = buscarProyecto(proyectoId);
        validarAcceso(proyecto, colaborador);

        List<Usuario> otros = new ArrayList<>();
        otros.add(proyecto.getPm());

        List<Asignacion> activas = asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA);
        for (Asignacion a : activas) {
            Usuario otro = a.getColaborador();
            if (!otro.getId().equals(colaborador.getId())) {
                otros.add(otro);
            }
        }
        return chatBaseService.contarNoLeidosPorChat(proyecto, colaborador, otros);
    }








}