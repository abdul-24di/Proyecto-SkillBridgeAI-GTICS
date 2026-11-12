package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.dto.PmMensajeView;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Conversacion;
import com.pucp.skillb_ia.model.ConversacionUsuario;
import com.pucp.skillb_ia.model.ConversacionUsuarioId;
import com.pucp.skillb_ia.model.Mensaje;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.TipoConversacion;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ConversacionRepository;
import com.pucp.skillb_ia.repository.ConversacionUsuarioRepository;
import com.pucp.skillb_ia.repository.MensajeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;


@Service
public class ChatBaseService {

    private static final int MAX_LONGITUD_MENSAJE = 1000;

    private final ConversacionRepository conversacionRepository;
    private final ConversacionUsuarioRepository conversacionUsuarioRepository;
    private final MensajeRepository mensajeRepository;
    private final AsignacionRepository asignacionRepository;
    private final NotificacionService notificacionService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatBaseService(ConversacionRepository conversacionRepository,
                           ConversacionUsuarioRepository conversacionUsuarioRepository,
                           MensajeRepository mensajeRepository,
                           AsignacionRepository asignacionRepository,
                           NotificacionService notificacionService,
                           SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.conversacionRepository = conversacionRepository;
        this.conversacionUsuarioRepository = conversacionUsuarioRepository;
        this.mensajeRepository = mensajeRepository;
        this.asignacionRepository = asignacionRepository;
        this.notificacionService = notificacionService;
    }

    public void validarTexto(String contenido) {
        if (contenido == null || contenido.trim().isEmpty()) {
            throw new IllegalArgumentException("El mensaje no puede estar vacío.");
        }
        if (contenido.trim().length() > MAX_LONGITUD_MENSAJE) {
            throw new IllegalArgumentException("El mensaje no puede superar los " + MAX_LONGITUD_MENSAJE + " caracteres.");
        }
    }

    // ---------------- Chat grupal ----------------


    @Transactional
    public Conversacion obtenerOCrearGrupal(Proyecto proyecto) {
        Optional<Conversacion> existente = conversacionRepository
                .findFirstByProyectoAndTipoOrderByIdAsc(proyecto, TipoConversacion.GRUPAL);
        if (existente.isPresent()) {
            return existente.get();
        }
        Conversacion nueva = new Conversacion();
        nueva.setProyecto(proyecto);
        nueva.setTipo(TipoConversacion.GRUPAL);
        return conversacionRepository.save(nueva);
    }

    @Transactional
    public PmMensajeView enviarGrupal(Proyecto proyecto, Usuario autor, String texto) {

        //Buscamos la conversación grupal del proyecto. En caso de que aun no exista, la creamos
        Conversacion conversacion = obtenerOCrearGrupal(proyecto);

        Mensaje guardado = guardar(conversacion, autor, texto);


        List<Usuario> destinatarios = new ArrayList<>();
        Usuario pm = proyecto.getPm();
        if (!pm.getId().equals(autor.getId())) {
            destinatarios.add(pm);
        }
        List<Asignacion> activas = asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA);
        for (Asignacion a : activas) {
            Usuario col = a.getColaborador();
            if (!col.getId().equals(autor.getId())) {
                destinatarios.add(col);
            }
        }

        String titulo = recortar("Nuevo mensaje en " + proyecto.getNombre(), 150);
        String descripcion = recortar(nombreCompleto(autor) + ": " + texto, 200);
        for (Usuario destinatario : destinatarios) {
            notificacionService.crear(destinatario, "MENSAJE_GRUPAL", CategoriaNotificacion.MENSAJE,
                    titulo, descripcion, "CHAT", proyecto.getId());
        }

        //Aviso en tiempo real a todos los miembros
        List<String> correos = new ArrayList<>();
        correos.add(autor.getCorreo());
        for (Usuario destinatario : destinatarios) {
            correos.add(destinatario.getCorreo());
        }
        avisarPorWebSocket(correos, proyecto.getId(), null);

        return new PmMensajeView(guardado, autor.getId());
    }

    // ---------------- Chat privado ----------------
    public Conversacion buscarPrivada(Proyecto proyecto, Usuario a, Usuario b) {
        List<Conversacion> privadas = conversacionRepository
                .findByProyectoAndTipoOrderByIdAsc(proyecto, TipoConversacion.PRIVADA);

        for (Conversacion c : privadas) {
            boolean estaA = false;
            boolean estaB = false;
            List<ConversacionUsuario> miembros = conversacionUsuarioRepository.findByConversacion(c);
            for (ConversacionUsuario m : miembros) {
                Long id = m.getUsuario().getId();
                if (id.equals(a.getId())) estaA = true;
                if (id.equals(b.getId())) estaB = true;
            }
            if (estaA && estaB) {
                return c;
            }
        }
        return null;
    }

    @Transactional
    public PmMensajeView enviarPrivado(Proyecto proyecto, Usuario autor, Usuario otro, String texto) {
        Conversacion conversacion = buscarPrivada(proyecto, autor, otro);
        if (conversacion == null) {
            conversacion = new Conversacion();
            conversacion.setProyecto(proyecto);
            conversacion.setTipo(TipoConversacion.PRIVADA);
            conversacion = conversacionRepository.save(conversacion);
            agregarMiembro(conversacion, autor);
            agregarMiembro(conversacion, otro);
        }

        Mensaje guardado = guardar(conversacion, autor, texto);

        String titulo = recortar("Mensaje de " + nombreCompleto(autor), 150);
        String descripcion = recortar(proyecto.getNombre() + ": " + texto, 200);
        notificacionService.crear(otro, "MENSAJE_PRIVADO", CategoriaNotificacion.MENSAJE,
                titulo, descripcion, "CHAT_PRIVADO", conversacion.getId());


        avisarPorWebSocket(List.of(otro.getCorreo()), proyecto.getId(), autor.getId());
        avisarPorWebSocket(List.of(autor.getCorreo()), proyecto.getId(), otro.getId());

        return new PmMensajeView(guardado, autor.getId());
    }


    public List<PmMensajeView> listar(Conversacion conversacion, Long usuarioActualId) {
        List<PmMensajeView> vistas = new ArrayList<>();
        List<Mensaje> mensajes = mensajeRepository.findByConversacionOrderByFechaHoraAsc(conversacion);
        for (Mensaje m : mensajes) {
            vistas.add(new PmMensajeView(m, usuarioActualId));
        }
        return vistas;
    }

    @Transactional
    public void marcarLeida(Conversacion conversacion, Usuario usuario) {
        Mensaje ultimo = mensajeRepository.findFirstByConversacionOrderByIdDesc(conversacion);
        if (ultimo == null) {
            return;
        }
        conversacionUsuarioRepository.marcarLeido(conversacion.getId(), usuario.getId(), ultimo.getId());
    }

    private long contarNoLeidos(Conversacion conversacion, Usuario usuario) {
        long ultimoLeido = 0;
        ConversacionUsuario fila = conversacionUsuarioRepository.findByConversacionAndUsuario(conversacion, usuario);
        if (fila != null && fila.getUltimoLeidoId() != null) {
            ultimoLeido = fila.getUltimoLeidoId();
        }
        return mensajeRepository.countByConversacionAndIdGreaterThanAndAutorNot(conversacion, ultimoLeido, usuario);
    }


    @Transactional(readOnly = true)
    public Map<String, Long> contarNoLeidosPorChat(Proyecto proyecto, Usuario yo, List<Usuario> otros) {
        Map<String, Long> resultado = new HashMap<>();

        Optional<Conversacion> grupal = conversacionRepository
                .findFirstByProyectoAndTipoOrderByIdAsc(proyecto, TipoConversacion.GRUPAL);
        if (grupal.isPresent()) {
            resultado.put("grupo", contarNoLeidos(grupal.get(), yo));
        } else {
            resultado.put("grupo", 0L);
        }

        for (Usuario otro : otros) {
            Conversacion privada = buscarPrivada(proyecto, yo, otro);
            long cantidad = 0;
            if (privada != null) {
                cantidad = contarNoLeidos(privada, yo);
            }
            resultado.put(String.valueOf(otro.getId()), cantidad);
        }
        return resultado;
    }

    private Mensaje guardar(Conversacion conversacion, Usuario autor, String texto) {
        Mensaje mensaje = new Mensaje();
        mensaje.setConversacion(conversacion);
        mensaje.setAutor(autor);
        mensaje.setContenido(texto);
        return mensajeRepository.save(mensaje);
    }

    private void agregarMiembro(Conversacion conversacion, Usuario usuario) {
        conversacionUsuarioRepository.agregarMiembro(conversacion.getId(), usuario.getId());
    }

    private void avisarPorWebSocket(List<String> correos, Long proyectoId, Long otroUsuarioId) {
        final String aviso = "{\"proyectoId\":" + proyectoId + ",\"usuarioId\":" + otroUsuarioId + "}";
        final List<String> destinos = new ArrayList<>(correos);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviarAvisos(destinos, aviso);
                }
            });
        } else {
            enviarAvisos(destinos, aviso);
        }
    }

    private void enviarAvisos(List<String> correos, String aviso) {
        for (String correo : correos) {
            try {
                messagingTemplate.convertAndSendToUser(correo, "/queue/chat", aviso);
            } catch (Exception e) {
                // El mensaje ya está guardado: si falla el aviso, la consulta de respaldo lo recoge
            }
        }
    }

    private String nombreCompleto(Usuario u) {
        return u.getNombre() + " " + u.getApellido();
    }

    // Metodo que limita la longitud de un texto a una cantidad máxima de caracteres
    private String recortar(String texto, int max) {

        // Si el texto no supera el máximo permitido, se devuelve completo
        if (texto.length() <= max) {
            return texto;
        }

        // Si supera el límite, se recorta dejando espacio para agregar "..." al final
        return texto.substring(0, max - 3) + "...";
    }

}