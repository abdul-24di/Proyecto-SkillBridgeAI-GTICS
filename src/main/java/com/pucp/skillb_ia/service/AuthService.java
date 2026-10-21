package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.TokenUsuario;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoRegistro;
import com.pucp.skillb_ia.model.enums.TipoToken;
import com.pucp.skillb_ia.repository.LogAuditoriaRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.TokenUsuarioRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
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
    private static final int MAX_INTENTOS_CODIGO = 5;

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
    private final LogAuditoriaRepository logAuditoriaRepository;
    // Sesiones guardadas por Spring Session JDBC; se usan para cerrar las de un usuario al cambiar su contraseña.
    private final ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> repositorioSesiones;

    public AuthService(UsuarioRepository usuarioRepository, RolRepository rolRepository,
                        TokenUsuarioRepository tokenUsuarioRepository, PasswordEncoder passwordEncoder,
                        EmailService emailService, AuditoriaService auditoriaService,
                        NotificacionService notificacionService, LogAuditoriaRepository logAuditoriaRepository,
                        ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> repositorioSesiones) {
        this.logAuditoriaRepository = logAuditoriaRepository;
        this.repositorioSesiones = repositorioSesiones;
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
        // Al reenviar, el enlace anterior que no se usó deja de servir.
        anularTokensPendientes(usuario, TipoToken.ACTIVACION);

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
        usuarioRepository.findByCorreo(correo).filter(this::puedeRecuperarContrasena).ifPresent(usuario -> {
            // Pedir un código nuevo anula los anteriores: solo el último que llegó al correo sirve.
            anularTokensPendientes(usuario, TipoToken.RECUPERACION);
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
    // pueda probarse contra un correo distinto del que lo solicitó. Cada fallo queda en la auditoría y,
    // al llegar a MAX_INTENTOS_CODIGO, se anulan los códigos pendientes: hay que pedir uno nuevo.
    @Transactional
    public Optional<TokenUsuario> validarCodigoRecuperacion(String correo, String codigo) {
        Optional<Usuario> usuario = usuarioRepository.findByCorreo(correo).filter(this::puedeRecuperarContrasena);
        if (usuario.isEmpty()) {
            return Optional.empty();
        }
        Optional<TokenUsuario> token = codigoVigenteDe(usuario.get(), codigo);
        if (token.isEmpty()) {
            registrarCodigoIncorrecto(usuario.get());
        }
        return token;
    }

    private Optional<TokenUsuario> codigoVigenteDe(Usuario usuario, String codigo) {
        return tokenUsuarioRepository.findByToken(codigo)
                .filter(t -> t.getTipo() == TipoToken.RECUPERACION)
                .filter(t -> !t.isUsado())
                .filter(t -> t.getFechaExpiracion().isAfter(LocalDateTime.now()))
                .filter(t -> t.getUsuario().getId().equals(usuario.getId()));
    }

    private void registrarCodigoIncorrecto(Usuario usuario) {
        auditoriaService.registrar(usuario, "RECUPERACION_CODIGO_INCORRECTO", "USUARIO", usuario.getId(),
                "Ingresó un código de recuperación incorrecto.");
        LocalDateTime ahora = LocalDateTime.now();
        tokenUsuarioRepository.findByUsuarioAndTipoAndUsadoFalse(usuario, TipoToken.RECUPERACION).stream()
                .filter(t -> t.getFechaExpiracion().isAfter(ahora))
                .map(TokenUsuario::getFechaCreacion)
                .max(LocalDateTime::compareTo)
                .ifPresent(pedidoEl -> {
                    long fallidos = logAuditoriaRepository.countByUsuarioAndAccionAndFechaHoraGreaterThanEqual(
                            usuario, "RECUPERACION_CODIGO_INCORRECTO", pedidoEl);
                    if (fallidos >= MAX_INTENTOS_CODIGO) {
                        anularTokensPendientes(usuario, TipoToken.RECUPERACION);
                        auditoriaService.registrar(usuario, "RECUPERACION_BLOQUEADA", "USUARIO", usuario.getId(),
                                "Se anuló el código de recuperación tras " + fallidos + " intentos fallidos.");
                    }
                });
    }

    // Solo una cuenta ya activada (con contraseña) y activa puede recuperar su contraseña. Una cuenta
    // invitada que nunca se activó debe usar su enlace de activación: si pudiera "recuperar", pondría
    // una contraseña sin pasar por el pre-registro ni por la aprobación del Administrador.
    private boolean puedeRecuperarContrasena(Usuario usuario) {
        return usuario.isActivo() && usuario.getPasswordHash() != null;
    }

    // Recibe el correo y el código que el controlador guardó en la sesión al verificarlos, no un valor
    // que llegue del formulario: así no se puede saltar el paso de verificación.
    @Transactional
    public void restablecerContrasena(String correo, String codigo, String nuevaPassword) {
        Usuario usuario = usuarioRepository.findByCorreo(correo).filter(this::puedeRecuperarContrasena)
                .orElseThrow(() -> new IllegalStateException("El código de recuperación no es válido o expiró."));
        TokenUsuario token = codigoVigenteDe(usuario, codigo)
                .orElseThrow(() -> new IllegalStateException("El código de recuperación no es válido o expiró."));

        usuario.setPasswordHash(passwordEncoder.encode(nuevaPassword));
        usuarioRepository.save(usuario);

        token.setUsado(true);
        tokenUsuarioRepository.save(token);

        // Con la contraseña cambiada, se cierran las sesiones que la cuenta tuviera abiertas en otros equipos.
        int cerradas = cerrarSesiones(usuario.getCorreo());

        auditoriaService.registrar(usuario, "RECUPERACION_CONTRASENA", "USUARIO", usuario.getId(),
                "Restableció su contraseña mediante el flujo de recuperación."
                        + (cerradas > 0 ? " Sesiones abiertas cerradas: " + cerradas + "." : ""));
    }

    private int cerrarSesiones(String correo) {
        FindByIndexNameSessionRepository<? extends Session> sesiones = repositorioSesiones.getIfAvailable();
        if (sesiones == null) {
            return 0;
        }
        var abiertas = sesiones.findByPrincipalName(correo).keySet();
        abiertas.forEach(sesiones::deleteById);
        return abiertas.size();
    }

    // Un token que no se usó deja de servir: se marca como vencido (no como "usado", porque en la activación
    // eso mostraría "Esta cuenta ya fue activada" en vez de "El enlace ya no es válido").
    private void anularTokensPendientes(Usuario usuario, TipoToken tipo) {
        LocalDateTime ahora = LocalDateTime.now();
        for (TokenUsuario anterior : tokenUsuarioRepository.findByUsuarioAndTipoAndUsadoFalse(usuario, tipo)) {
            if (anterior.getFechaExpiracion().isAfter(ahora)) {
                anterior.setFechaExpiracion(ahora.minusSeconds(1));
                tokenUsuarioRepository.save(anterior);
            }
        }
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
