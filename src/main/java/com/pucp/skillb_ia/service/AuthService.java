package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.TokenUsuario;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoRegistro;
import com.pucp.skillb_ia.model.enums.TipoToken;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.TokenUsuarioRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

// Épica 2, Historia A6 (Activación de cuenta) + Historia de Recuperación de
// contraseña. Por decisión C1/C2: esta clase es la única dueña de la lógica
// de tokens (generar, invalidar, reenviar) — la Épica 5 (gestión de usuarios)
// solo va a llamar a invitarUsuario()/reenviarActivacion() desde su UI, sin
// reimplementar nada de esto.
@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int VIGENCIA_ACTIVACION_HORAS = 48; // coincide con el texto de activar-cuenta.html
    private static final int VIGENCIA_RECUPERACION_MINUTOS = 15;

    public enum EstadoToken { VALIDO, EXPIRADO, USADO, INVALIDO }

    public record ResultadoToken(EstadoToken estado, TokenUsuario token) {
        public boolean esValido() { return estado == EstadoToken.VALIDO; }
    }

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final TokenUsuarioRepository tokenUsuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;

    public AuthService(UsuarioRepository usuarioRepository, RolRepository rolRepository,
                        TokenUsuarioRepository tokenUsuarioRepository, PasswordEncoder passwordEncoder,
                        EmailService emailService, AuditoriaService auditoriaService,
                        NotificacionService notificacionService) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.tokenUsuarioRepository = tokenUsuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
    }

    // ============================================================
    // ACTIVACIÓN DE CUENTA (A6)
    // ============================================================

    private static final java.util.regex.Pattern CORREO_VALIDO =
            java.util.regex.Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");

    // El correo es el identificador de acceso y a donde llega la invitación: se limpia (espacios) y
    // se exige un formato mínimo válido antes de crear el usuario.
    private String validarCorreo(String correo) {
        String limpio = correo == null ? "" : correo.strip();
        if (limpio.isEmpty()) {
            throw new IllegalArgumentException("El correo es obligatorio.");
        }
        if (limpio.length() > 150) {
            throw new IllegalArgumentException("El correo no puede superar los 150 caracteres.");
        }
        if (!CORREO_VALIDO.matcher(limpio).matches()) {
            throw new IllegalArgumentException("El correo \"" + limpio + "\" no tiene un formato válido.");
        }
        return limpio;
    }

    // A5 solo llama a este método (correo + rol) — el nombre y la contraseña
    // los define el propio usuario al activar (ver C1).
    @Transactional
    public Usuario invitarUsuario(String correo, String nombreRol, Usuario creadoPorAdmin) {
        correo = validarCorreo(correo);
        if (usuarioRepository.existsByCorreo(correo)) {
            throw new IllegalArgumentException("Ya existe un usuario con el correo " + correo);
        }
        Rol rol = rolRepository.findByNombre(nombreRol)
                .orElseThrow(() -> new IllegalArgumentException("Rol inválido: " + nombreRol));

        Usuario usuario = new Usuario();
        usuario.setCorreo(correo);
        usuario.setRol(rol);
        usuario.setActivo(true);
        // passwordHash, nombre y apellido quedan NULL hasta que el usuario active su cuenta.
        usuario = usuarioRepository.save(usuario);

        enviarNuevoTokenActivacion(usuario);

        auditoriaService.registrar(creadoPorAdmin, "CREAR_USUARIO", "USUARIO", usuario.getId(),
                "Creó el usuario " + correo + " con rol " + nombreRol + ".");

        return usuario;
    }

    // El botón "Reenviar enlace" del panel de Admin llama a esto (C2) —
    // invalida cualquier token de activación anterior sin usar y genera uno nuevo.
    @Transactional
    public void reenviarActivacion(Usuario usuario, Usuario solicitadoPorAdmin) {
        enviarNuevoTokenActivacion(usuario);
        if (solicitadoPorAdmin != null) {
            auditoriaService.registrar(solicitadoPorAdmin, "REENVIAR_ACTIVACION", "USUARIO", usuario.getId(),
                    "Reenvió el enlace de activación a " + usuario.getCorreo() + ".");
        }
    }

    private void enviarNuevoTokenActivacion(Usuario usuario) {
        TokenUsuario token = new TokenUsuario();
        token.setUsuario(usuario);
        token.setTipo(TipoToken.ACTIVACION);
        token.setToken(generarTokenUnico(48));
        token.setFechaExpiracion(LocalDateTime.now().plusHours(VIGENCIA_ACTIVACION_HORAS));
        tokenUsuarioRepository.save(token);

        String enlace = "/activar-cuenta?token=" + token.getToken();
        emailService.enviarActivacion(usuario.getCorreo(), enlace);

        // Acción automática del sistema — usuario null en el log (consideraciones_bd_v4.md: NULL = sistema).
        auditoriaService.registrar(null, "ENVIO_CORREO_ACTIVACION", "USUARIO", usuario.getId(),
                "Se envió el correo de activación a " + usuario.getCorreo() + ".");
    }

    public ResultadoToken validarTokenActivacion(String token) {
        return validarToken(token, TipoToken.ACTIVACION);
    }

    // C3: la activación completada queda en el log de auditoría.
    @Transactional
    // Es también el pre-registro: guarda directo en el perfil del usuario los datos personales que
    // escribió (nombre, teléfono, descripción), así quedan en "Mi perfil" sin copiarlos después. Un
    // colaborador queda con el registro PENDIENTE hasta que el Admin lo apruebe. Devuelve el usuario
    // para que el controlador pueda subir su CV.
    public Usuario activarCuenta(String token, String nombreCompleto, String telefono,
                                 String descripcion, String password) {
        ResultadoToken resultado = validarTokenActivacion(token);
        if (!resultado.esValido()) {
            throw new IllegalStateException("El enlace de activación no es válido.");
        }

        TokenUsuario tokenUsuario = resultado.token();
        Usuario usuario = tokenUsuario.getUsuario();

        String nombre = nombreCompleto.trim();
        int espacio = nombre.indexOf(' ');
        if (espacio > 0) {
            usuario.setNombre(nombre.substring(0, espacio));
            usuario.setApellido(nombre.substring(espacio + 1).trim());
        } else {
            usuario.setNombre(nombre);
        }
        usuario.setTelefono(telefono != null ? telefono.strip() : null);
        usuario.setDescripcion(descripcion != null && !descripcion.isBlank() ? descripcion.strip() : null);
        usuario.setPasswordHash(passwordEncoder.encode(password));
        if ("COLABORADOR".equals(usuario.getRol().getNombre())) {
            usuario.setRegistroEstado(EstadoRegistro.PENDIENTE);
        }
        usuarioRepository.save(usuario);

        tokenUsuario.setUsado(true);
        tokenUsuarioRepository.save(tokenUsuario);

        auditoriaService.registrar(usuario, "ACTIVACION_CUENTA", "USUARIO", usuario.getId(),
                "Completó la activación de su cuenta y estableció su contraseña.");

        // Un colaborador avisa al Admin con su CV pendiente; los demás roles no tienen revisión,
        // así que se avisa solo de que la invitación se completó.
        if (!"COLABORADOR".equals(usuario.getRol().getNombre())) {
            notificacionService.crearParaTodosLosAdmins("CUENTA_ACTIVADA", CategoriaNotificacion.SISTEMA,
                    "Cuenta activada",
                    nombre + " activó su cuenta como " + usuario.getRol().getNombre() + ".",
                    "USUARIO", usuario.getId());
        }
        return usuario;
    }

    // ============================================================
    // RECUPERACIÓN DE CONTRASEÑA
    // ============================================================

    // No revela si el correo existe (mismo comportamiento sin importar el resultado) —
    // ya reflejado en el mensaje de recuperar-contrasena.html.
    @Transactional
    public void solicitarRecuperacion(String correo) {
        usuarioRepository.findByCorreo(correo).ifPresent(usuario -> {
            String codigo = generarCodigoNumericoUnico(6);
            TokenUsuario token = new TokenUsuario();
            token.setUsuario(usuario);
            token.setTipo(TipoToken.RECUPERACION);
            token.setToken(codigo);
            token.setFechaExpiracion(LocalDateTime.now().plusMinutes(VIGENCIA_RECUPERACION_MINUTOS));
            tokenUsuarioRepository.save(token);

            emailService.enviarCodigoRecuperacion(usuario.getCorreo(), codigo);
        });
    }

    // Se valida contra el correo Y el código a la vez, para que un código no
    // pueda probarse contra un correo distinto del que lo solicitó.
    public Optional<TokenUsuario> validarCodigoRecuperacion(String correo, String codigo) {
        return tokenUsuarioRepository.findByToken(codigo)
                .filter(t -> t.getTipo() == TipoToken.RECUPERACION)
                .filter(t -> !t.isUsado())
                .filter(t -> t.getFechaExpiracion().isAfter(LocalDateTime.now()))
                .filter(t -> t.getUsuario().getCorreo().equalsIgnoreCase(correo));
    }

    @Transactional
    public void restablecerContrasena(String tokenValor, String nuevaPassword) {
        TokenUsuario token = tokenUsuarioRepository.findByToken(tokenValor)
                .filter(t -> t.getTipo() == TipoToken.RECUPERACION)
                .filter(t -> !t.isUsado())
                .filter(t -> t.getFechaExpiracion().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new IllegalStateException("El código de recuperación no es válido o expiró."));

        Usuario usuario = token.getUsuario();
        usuario.setPasswordHash(passwordEncoder.encode(nuevaPassword));
        usuarioRepository.save(usuario);

        token.setUsado(true);
        tokenUsuarioRepository.save(token);

        auditoriaService.registrar(usuario, "RECUPERACION_CONTRASENA", "USUARIO", usuario.getId(),
                "Restableció su contraseña mediante el flujo de recuperación.");
    }

    // ============================================================
    // Helpers
    // ============================================================

    private ResultadoToken validarToken(String valor, TipoToken tipoEsperado) {
        Optional<TokenUsuario> opt = tokenUsuarioRepository.findByToken(valor);
        if (opt.isEmpty() || opt.get().getTipo() != tipoEsperado) {
            return new ResultadoToken(EstadoToken.INVALIDO, null);
        }
        TokenUsuario token = opt.get();
        if (token.isUsado()) {
            return new ResultadoToken(EstadoToken.USADO, token);
        }
        if (token.getFechaExpiracion().isBefore(LocalDateTime.now())) {
            return new ResultadoToken(EstadoToken.EXPIRADO, token);
        }
        return new ResultadoToken(EstadoToken.VALIDO, token);
    }

    private String generarTokenUnico(int bytesLength) {
        byte[] bytes = new byte[bytesLength];
        String token;
        do {
            RANDOM.nextBytes(bytes);
            token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (tokenUsuarioRepository.findByToken(token).isPresent());
        return token;
    }

    private String generarCodigoNumericoUnico(int digitos) {
        String codigo;
        do {
            int max = (int) Math.pow(10, digitos);
            codigo = String.format("%0" + digitos + "d", RANDOM.nextInt(max));
        } while (tokenUsuarioRepository.findByToken(codigo).isPresent());
        return codigo;
    }
}
