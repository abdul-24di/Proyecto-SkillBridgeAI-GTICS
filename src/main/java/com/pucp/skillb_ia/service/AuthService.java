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

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Épica 2, Historia A6 (Activación de cuenta) + Historia de Recuperación de contraseña. Por decisión
 * C1/C2: esta clase es la única dueña de la lógica de tokens (generar, invalidar, reenviar) — la Épica 5
 * (gestión de usuarios) solo va a llamar a invitarUsuario()/reenviarActivacion() desde su UI, sin
 * reimplementar nada de esto.
 */
@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int VIGENCIA_ACTIVACION_HORAS = 48; // coincide con el texto de activar-cuenta.html
    private static final int VIGENCIA_RECUPERACION_MINUTOS = 15;
    private static final int MAX_INTENTOS_CODIGO = 5;
    private static final int ESPERA_ENTRE_CODIGOS_SEGUNDOS = 60;
    private static final BigDecimal JORNADA_SEMANAL_COLABORADOR = BigDecimal.valueOf(40);

    /** Estado de un enlace o código. */
    public enum EstadoToken { VALIDO, EXPIRADO, USADO, INVALIDO }

    /**
     * Resultado de validar un token; el token es {@code null} si no existe.
     *
     * @param estado estado del token
     * @param token token del enlace de activación
     */
    public record ResultadoToken(EstadoToken estado, TokenUsuario token) {
        /** Indica si el enlace o código se puede usar. */
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

    /** Crea el componente con sus dependencias, que Spring inyecta por el constructor. */
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
            java.util.regex.Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");

    /**
     * Limpia el correo y comprueba su formato.
     * <p>
     * El correo es el identificador de acceso y a donde llega la invitación: se limpia (espacios) y se exige un
     * formato válido antes de crear el usuario (también lo usa la carga masiva).
     *
     * @param correo correo del usuario
     * @return correo sin espacios
     * @throws IllegalArgumentException si está vacío, es muy largo o no tiene un formato válido
     */
    public String validarCorreo(String correo) {
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

    /**
     * Crea una cuenta por invitación.
     * <p>
     * A5 solo llama a este método (correo + rol) — el nombre y la contraseña los define el propio usuario al
     * activar (ver C1).
     *
     * @param correo correo del usuario
     * @param nombreRol nombre técnico del rol
     * @param creadoPorAdmin Administrador que invita
     * @return usuario creado
     * @throws IllegalArgumentException si el correo o el rol no son válidos
     */
    @Transactional
    public Usuario invitarUsuario(String correo, String nombreRol, Usuario creadoPorAdmin) {
        return invitar(correo, nombreRol, creadoPorAdmin).usuario();
    }

    /**
     * Crea una cuenta sin contraseña y envía el correo con el enlace de activación.
     *
     * @param correo correo del usuario
     * @param nombreRol nombre técnico del rol
     * @param creadoPorAdmin Administrador que invita
     * @return usuario creado y si el correo se envió
     * @throws IllegalArgumentException si el correo no es válido o ya existe, o el rol no existe
     */
    @Transactional
    public Invitacion invitar(String correo, String nombreRol, Usuario creadoPorAdmin) {
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
        // Todo colaborador empieza con la jornada fija de 40 h/semana, toda disponible.
        if ("COLABORADOR".equals(rol.getNombre())) {
            usuario.setHorasContratadasSemana(JORNADA_SEMANAL_COLABORADOR);
            usuario.setHorasDisponibles(JORNADA_SEMANAL_COLABORADOR);
        }
        // passwordHash, nombre y apellido quedan NULL hasta que el usuario active su cuenta.
        usuario = usuarioRepository.save(usuario);

        boolean correoEnviado = enviarNuevoTokenActivacion(usuario);

        auditoriaService.registrar(creadoPorAdmin, "CREAR_USUARIO", "USUARIO", usuario.getId(),
                "Creó el usuario " + correo + " con rol " + nombreRol + ".");

        return new Invitacion(usuario, correoEnviado);
    }

    /**
     * Resultado de una invitación.
     * <p>
     * Usuario creado y si el correo de invitación salió de verdad: si el servidor de correo falla, la cuenta
     * igual queda creada (el enlace se puede reenviar), pero el Admin tiene que saberlo.
     *
     * @param usuario usuario al que corresponde la operación
     * @param correoEnviado {@code true} si el correo se envió
     */
    public record Invitacion(Usuario usuario, boolean correoEnviado) {
    }

    /**
     * Genera y envía un nuevo enlace de activación.
     * <p>
     * El botón "Reenviar enlace" del panel de Admin llama a esto (C2) — invalida cualquier token de activación
     * anterior sin usar y genera uno nuevo. Devuelve si el correo se envió.
     *
     * @param usuario usuario al que corresponde la operación
     * @param solicitadoPorAdmin Administrador que lo pide; {@code null} si es automático
     * @return {@code true} si el correo se envió
     */
    @Transactional
    public boolean reenviarActivacion(Usuario usuario, Usuario solicitadoPorAdmin) {
        boolean enviado = enviarNuevoTokenActivacion(usuario);
        if (solicitadoPorAdmin != null) {
            auditoriaService.registrar(solicitadoPorAdmin, "REENVIAR_ACTIVACION", "USUARIO", usuario.getId(),
                    "Reenvió el enlace de activación a " + usuario.getCorreo() + ".");
        }
        return enviado;
    }

    /**
     * Anula los enlaces pendientes, crea uno de 48 horas, lo envía y registra el resultado en la auditoría.
     *
     * @param usuario usuario al que corresponde la operación
     * @return {@code true} si el correo se envió
     */
    private boolean enviarNuevoTokenActivacion(Usuario usuario) {
        // Al reenviar, el enlace anterior que no se usó deja de servir.
        anularTokensPendientes(usuario, TipoToken.ACTIVACION);

        TokenUsuario token = new TokenUsuario();
        token.setUsuario(usuario);
        token.setTipo(TipoToken.ACTIVACION);
        token.setToken(generarTokenUnico(48));
        token.setFechaExpiracion(LocalDateTime.now().plusHours(VIGENCIA_ACTIVACION_HORAS));
        tokenUsuarioRepository.save(token);

        String enlace = "/activar-cuenta?token=" + token.getToken();
        boolean enviado = emailService.enviarActivacion(usuario.getCorreo(), enlace,
                usuario.getRol() != null ? usuario.getRol().getNombre() : null, VIGENCIA_ACTIVACION_HORAS);

        // Acción automática del sistema — usuario null en el log (consideraciones_bd_v4.md: NULL = sistema).
        if (enviado) {
            auditoriaService.registrar(null, "ENVIO_CORREO_ACTIVACION", "USUARIO", usuario.getId(),
                    "Se envió el correo de activación a " + usuario.getCorreo() + ".");
        } else {
            auditoriaService.registrar(null, "ERROR_CORREO", "USUARIO", usuario.getId(),
                    "No se pudo enviar el correo de activación a " + usuario.getCorreo()
                            + ". Revisa la configuración del correo del servidor.");
        }
        return enviado;
    }

    /**
     * Comprueba un enlace de activación.
     *
     * @param token token del enlace de activación
     * @return estado del enlace y su token
     */
    public ResultadoToken validarTokenActivacion(String token) {
        return validarToken(token, TipoToken.ACTIVACION);
    }

    /**
     * Activa la cuenta: guarda los datos personales y la contraseña, y marca el enlace como usado. Un colaborador
     * queda pendiente de aprobación; de los demás roles se avisa a los Administradores. La activación queda en
     * el log de auditoría (C3).
     * <p>
     * Es también el pre-registro: guarda directo en el perfil del usuario los datos personales que escribió
     * (nombre, teléfono, descripción), así quedan en "Mi perfil" sin copiarlos después. Un colaborador queda
     * con el registro PENDIENTE hasta que el Admin lo apruebe. Devuelve el usuario para que el controlador pueda
     * subir su CV.
     *
     * @param token token del enlace de activación
     * @param nombres nombres del usuario
     * @param apellidos apellidos del usuario
     * @param telefono teléfono ingresado
     * @param descripcion descripción; puede ser {@code null} o vacía
     * @param password contraseña en texto plano; se guarda cifrada con BCrypt
     * @return usuario activado
     * @throws IllegalStateException si el enlace no es válido
     */
    @Transactional
    public Usuario activarCuenta(String token, String nombres, String apellidos, String telefono,
                                 String descripcion, String password) {
        ResultadoToken resultado = validarTokenActivacion(token);
        if (!resultado.esValido()) {
            throw new IllegalStateException("El enlace de activación no es válido.");
        }

        TokenUsuario tokenUsuario = resultado.token();
        Usuario usuario = tokenUsuario.getUsuario();

        usuario.setNombre(nombres.strip());
        usuario.setApellido(apellidos.strip());
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
                    usuario.getNombre() + " " + usuario.getApellido() + " activó su cuenta como " + usuario.getRol().getNombre() + ".",
                    "USUARIO", usuario.getId());
        }
        return usuario;
    }

    // ============================================================
    // RECUPERACIÓN DE CONTRASEÑA
    // ============================================================

    /**
     * Envía un código de recuperación de 15 minutos si la cuenta existe y puede recuperarse.
     * <p>
     * No revela si el correo existe (mismo comportamiento sin importar el resultado) — ya reflejado en el
     * mensaje de recuperar-contrasena.html.
     *
     * @param correo correo del usuario
     */
    @Transactional
    public void solicitarRecuperacion(String correo) {
        usuarioRepository.findByCorreo(correo).filter(this::puedeRecuperarContrasena).ifPresent(usuario -> {
            // Máximo un correo por minuto a la misma cuenta: evita llenar la bandeja de alguien (y agotar el
            // cupo de envíos de Gmail) repitiendo este formulario. El código anterior sigue valiendo.
            if (pidioCodigoHacePoco(usuario)) {
                return;
            }
            // Pedir un código nuevo anula los anteriores: solo el último que llegó al correo sirve.
            anularTokensPendientes(usuario, TipoToken.RECUPERACION);
            String codigo = generarCodigoNumericoUnico(6);
            TokenUsuario token = new TokenUsuario();
            token.setUsuario(usuario);
            token.setTipo(TipoToken.RECUPERACION);
            token.setToken(codigo);
            token.setFechaExpiracion(LocalDateTime.now().plusMinutes(VIGENCIA_RECUPERACION_MINUTOS));
            tokenUsuarioRepository.save(token);

            emailService.enviarCodigoRecuperacion(usuario.getCorreo(), codigo, VIGENCIA_RECUPERACION_MINUTOS);
        });
    }

    /**
     * Indica si la cuenta pidió un código hace menos de un minuto.
     *
     * @param usuario usuario al que corresponde la operación
     * @return {@code true} si debe esperar
     */
    private boolean pidioCodigoHacePoco(Usuario usuario) {
        LocalDateTime limite = LocalDateTime.now()
                .plusMinutes(VIGENCIA_RECUPERACION_MINUTOS).minusSeconds(ESPERA_ENTRE_CODIGOS_SEGUNDOS);
        return tokenUsuarioRepository.findByUsuarioAndTipoAndUsadoFalse(usuario, TipoToken.RECUPERACION).stream()
                .anyMatch(t -> t.getFechaExpiracion().isAfter(limite));
    }

    /**
     * Comprueba el código de recuperación de un correo.
     * <p>
     * Se valida contra el correo Y el código a la vez, para que un código no pueda probarse contra un correo
     * distinto del que lo solicitó. Cada fallo queda en la auditoría y, al llegar a MAX_INTENTOS_CODIGO, se
     * anulan los códigos pendientes: hay que pedir uno nuevo.
     *
     * @param correo correo del usuario
     * @param codigo código de recuperación de seis dígitos
     * @return el token si el código es válido
     */
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

    /**
     * Busca el código sin usar y vigente de un usuario.
     *
     * @param usuario usuario al que corresponde la operación
     * @param codigo código de recuperación de seis dígitos
     * @return el token si existe
     */
    private Optional<TokenUsuario> codigoVigenteDe(Usuario usuario, String codigo) {
        return tokenUsuarioRepository.findByToken(codigo)
                .filter(t -> t.getTipo() == TipoToken.RECUPERACION)
                .filter(t -> !t.isUsado())
                .filter(t -> t.getFechaExpiracion().isAfter(LocalDateTime.now()))
                .filter(t -> t.getUsuario().getId().equals(usuario.getId()));
    }

    /**
     * Registra el intento fallido y, al quinto, anula los códigos pendientes.
     *
     * @param usuario usuario al que corresponde la operación
     */
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

    /**
     * Indica si la cuenta puede recuperar su contraseña.
     * <p>
     * Solo una cuenta ya activada (con contraseña) y activa puede recuperar su contraseña. Una cuenta invitada
     * que nunca se activó debe usar su enlace de activación: si pudiera "recuperar", pondría una contraseña
     * sin pasar por el pre-registro ni por la aprobación del Administrador.
     *
     * @param usuario usuario al que corresponde la operación
     * @return {@code true} si está activa y ya tiene contraseña
     */
    private boolean puedeRecuperarContrasena(Usuario usuario) {
        return usuario.isActivo() && usuario.getPasswordHash() != null;
    }

    /**
     * Cambia la contraseña con un código verificado y cierra las sesiones abiertas de la cuenta.
     * <p>
     * Recibe el correo y el código que el controlador guardó en la sesión al verificarlos, no un valor que
     * llegue del formulario: así no se puede saltar el paso de verificación.
     *
     * @param correo correo del usuario
     * @param codigo código de recuperación de seis dígitos
     * @param nuevaPassword contraseña nueva en texto plano
     * @throws IllegalStateException si el código no es válido o venció
     */
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

    /**
     * Cierra todas las sesiones guardadas de una cuenta.
     *
     * @param correo correo del usuario
     * @return cantidad de sesiones cerradas
     */
    public int cerrarSesiones(String correo) {
        FindByIndexNameSessionRepository<? extends Session> sesiones = repositorioSesiones.getIfAvailable();
        if (sesiones == null) {
            return 0;
        }
        var abiertas = sesiones.findByPrincipalName(correo).keySet();
        abiertas.forEach(sesiones::deleteById);
        return abiertas.size();
    }

    /**
     * Anula los tokens sin usar de un usuario.
     * <p>
     * Un token que no se usó deja de servir: se marca como vencido (no como "usado", porque en la activación eso
     * mostraría "Esta cuenta ya fue activada" en vez de "El enlace ya no es válido").
     *
     * @param usuario usuario al que corresponde la operación
     * @param tipo tipo de token
     */
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

    /**
     * Comprueba que el token exista, sea del tipo esperado, no se haya usado y no haya vencido.
     *
     * @param valor token recibido
     * @param tipoEsperado tipo de token que corresponde
     * @return estado del token
     */
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

    /**
     * Genera un token aleatorio que no exista en la base.
     *
     * @param bytesLength bytes aleatorios del token
     * @return token para un enlace
     */
    private String generarTokenUnico(int bytesLength) {
        byte[] bytes = new byte[bytesLength];
        String token;
        do {
            RANDOM.nextBytes(bytes);
            token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (tokenUsuarioRepository.findByToken(token).isPresent());
        return token;
    }

    /**
     * Genera un código numérico que no exista en la base.
     *
     * @param digitos cantidad de dígitos
     * @return código con ceros a la izquierda
     */
    private String generarCodigoNumericoUnico(int digitos) {
        String codigo;
        do {
            int max = (int) Math.pow(10, digitos);
            codigo = String.format("%0" + digitos + "d", RANDOM.nextInt(max));
        } while (tokenUsuarioRepository.findByToken(codigo).isPresent());
        return codigo;
    }
}
