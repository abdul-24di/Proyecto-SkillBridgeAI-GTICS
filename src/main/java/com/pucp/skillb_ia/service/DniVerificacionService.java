package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Un DNI "sin verificar" no se queda así: el usuario lo corrige o pulsa "Verificar ahora" en su perfil, y una tarea
// programada (DniReintentoScheduler) lo vuelve a consultar. Cada vez que no se verifica, el usuario recibe una
// notificación con el motivo. Un DNI verificado ya no cambia.
@Service
public class DniVerificacionService {

    private static final Logger log = LoggerFactory.getLogger(DniVerificacionService.class);

    public static final String TIPO_VERIFICADO = "DNI_VERIFICADO";
    public static final String TIPO_SIN_VERIFICAR = "DNI_SIN_VERIFICAR";
    public static final String TIPO_NO_VERIFICADO = "DNI_NO_VERIFICADO";

    private final DniService dniService;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionService notificacionService;

    public DniVerificacionService(DniService dniService, UsuarioRepository usuarioRepository,
                                  NotificacionService notificacionService) {
        this.dniService = dniService;
        this.usuarioRepository = usuarioRepository;
        this.notificacionService = notificacionService;
    }

    /**
     * Registra, corrige o vuelve a verificar el DNI desde el perfil.
     *
     * @return true si quedó verificado; false si la API no estaba disponible ("sin verificar")
     * @throws IllegalStateException    si el DNI ya está verificado
     * @throws IllegalArgumentException si el formato es inválido, está repetido, no existe o el nombre no coincide
     */
    @Transactional
    public boolean registrarDesdePerfil(Long usuarioId, String dni) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el usuario autenticado."));
        if (usuario.isDniVerificado() && usuario.getDni() != null && !usuario.getDni().isBlank()) {
            throw new IllegalStateException(DniService.MENSAJE_YA_VERIFICADO);
        }
        boolean verificado = dniService.validar(dni, nombreCompleto(usuario), usuario.getId());
        String limpio = dni.trim();
        boolean cambio = !limpio.equals(usuario.getDni());
        usuario.setDni(limpio);
        usuario.setDniVerificado(verificado);
        usuarioRepository.save(usuario);
        // Si solo volvió a pulsar "Verificar ahora" con el mismo DNI y la API sigue caída, ya tiene el aviso.
        if (!verificado && cambio) {
            notificarSinVerificar(usuario);
        }
        return verificado;
    }

    /** Avisa que el DNI quedó sin verificar porque la consulta no estaba disponible. */
    public void notificarSinVerificar(Usuario usuario) {
        notificacionService.crear(usuario, TIPO_SIN_VERIFICAR, CategoriaNotificacion.SISTEMA,
                "Tu DNI quedó sin verificar",
                "El servicio de consulta de DNI no estaba disponible. Lo volveremos a intentar automáticamente; "
                        + "también puedes pulsar \"Verificar ahora\" en tu perfil.",
                "USUARIO", usuario.getId());
    }

    /** Vuelve a consultar todos los DNI sin verificar. Lo llama la tarea programada. */
    @Transactional
    public int reintentarPendientes() {
        if (!dniService.apiConfigurada()) {
            return 0;
        }
        return reintentar(usuarioRepository.findByDniIsNotNullAndDniVerificadoFalse());
    }

    /**
     * Consulta cada DNI: si coincide, se marca verificado; si no existe o no corresponde al nombre de la cuenta, se
     * quita para que el usuario lo registre de nuevo. En ambos casos se le notifica. Si la API no responde, se
     * detiene para no gastar consultas y se reintenta en la siguiente ejecución.
     *
     * @return cuántos DNI quedaron verificados
     */
    @Transactional
    public int reintentar(List<Usuario> pendientes) {
        int verificados = 0;
        for (Usuario usuario : pendientes) {
            if (usuario.getDni() == null || usuario.getDni().isBlank() || usuario.isDniVerificado()) {
                continue;
            }
            String dni = usuario.getDni();
            String nombre = nombreCompleto(usuario);
            DniService.Consulta consulta = dniService.consultar(dni);
            switch (consulta.estado()) {
                case NO_DISPONIBLE -> {
                    log.info("[DNI] La API sigue sin responder: se reintentará en la siguiente ejecución.");
                    return verificados;
                }
                case NO_ENCONTRADO -> rechazar(usuario,
                        "La consulta no encontró el DNI " + dni + ". Revisa que esté bien escrito y regístralo "
                                + "de nuevo desde tu perfil.");
                case ENCONTRADO -> {
                    if (dniService.nombresCoinciden(nombre, consulta.nombreCompleto())) {
                        usuario.setDniVerificado(true);
                        usuarioRepository.save(usuario);
                        notificacionService.crear(usuario, TIPO_VERIFICADO, CategoriaNotificacion.SISTEMA,
                                "Tu DNI fue verificado",
                                "La consulta confirmó tu DNI " + dni + ". Ya no se puede modificar.",
                                "USUARIO", usuario.getId());
                        verificados++;
                    } else {
                        rechazar(usuario, "El DNI " + dni + " no corresponde al nombre de tu cuenta (" + nombre
                                + "). Regístralo de nuevo desde tu perfil o pide al Administrador que corrija tu nombre.");
                    }
                }
            }
        }
        return verificados;
    }

    /** Copia el DNI guardado en la base de datos a la copia del usuario de la sesión (perfiles del Admin y del PM). */
    @Transactional(readOnly = true)
    public void sincronizar(Usuario copiaSesion) {
        if (copiaSesion == null || copiaSesion.getId() == null) {
            return;
        }
        usuarioRepository.findById(copiaSesion.getId()).ifPresent(u -> {
            copiaSesion.setDni(u.getDni());
            copiaSesion.setDniVerificado(u.isDniVerificado());
        });
    }

    private void rechazar(Usuario usuario, String motivo) {
        usuario.setDni(null);
        usuario.setDniVerificado(false);
        usuarioRepository.save(usuario);
        notificacionService.crear(usuario, TIPO_NO_VERIFICADO, CategoriaNotificacion.SISTEMA,
                "No pudimos verificar tu DNI", motivo, "USUARIO", usuario.getId());
    }

    private String nombreCompleto(Usuario usuario) {
        String nombre = usuario.getNombre() == null ? "" : usuario.getNombre();
        String apellido = usuario.getApellido() == null ? "" : usuario.getApellido();
        return (nombre + " " + apellido).trim();
    }
}
