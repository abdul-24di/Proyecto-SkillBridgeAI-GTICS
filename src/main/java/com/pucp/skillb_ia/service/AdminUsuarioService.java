package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.model.Cargo;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoCv;
import com.pucp.skillb_ia.model.enums.EstadoRegistro;
import com.pucp.skillb_ia.repository.CargoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.admin.AdminCargoService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import com.pucp.skillb_ia.util.Paginador;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.Map;
import java.util.Set;

/**
 * Épica 5 (Gestión de usuarios). Por decisión C1/C2 esta clase NO reimplementa nada de tokens/activación
 * — delega siempre a AuthService (ver comentario en esa clase). Solo se encarga de: listar/filtrar el
 * directorio, cambiar rol, activar/desactivar y la carga masiva por CSV.
 */
@Service
public class AdminUsuarioService {

    private static final DateTimeFormatter FECHA_FORMATO = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // Las vistas del Admin usan las etiquetas legibles del rol (coinciden con
    // las de los <select> ya existentes en admin-usuarios.html); la BD guarda
    // el nombre técnico (ver rol en skillbridge_db_v4.sql).
    private static final Map<String, String> ETIQUETA_A_ROL = new LinkedHashMap<>();
    private static final Map<String, String> ROL_A_ETIQUETA = new LinkedHashMap<>();
    private static final Map<String, String> ROL_A_BADGE = new LinkedHashMap<>();

    // El Administrador NUNCA se crea ni se asigna desde esta pantalla (solo
    // existe por seed directo en la BD, por seguridad) — estos son los únicos
    // 3 roles que el Admin puede asignar a otros usuarios.
    private static final Map<String, String> NUMERO_A_ROL = new LinkedHashMap<>();
    private static final Map<String, String> ROL_A_NUMERO = new LinkedHashMap<>();
    private static final int MAX_FILAS_CARGA_MASIVA = 200;

    static {
        ETIQUETA_A_ROL.put("Administrador", "ADMINISTRADOR");
        ETIQUETA_A_ROL.put("Resource Manager", "RESOURCE_MANAGER");
        ETIQUETA_A_ROL.put("Project Manager", "PROJECT_MANAGER");
        ETIQUETA_A_ROL.put("Colaborador", "COLABORADOR");
        ETIQUETA_A_ROL.forEach((etiqueta, rol) -> ROL_A_ETIQUETA.put(rol, etiqueta));

        ROL_A_BADGE.put("ADMINISTRADOR", "bg-azure-lt text-azure");
        ROL_A_BADGE.put("RESOURCE_MANAGER", "bg-green-lt text-green");
        ROL_A_BADGE.put("PROJECT_MANAGER", "bg-blue-lt text-blue");
        ROL_A_BADGE.put("COLABORADOR", "bg-orange-lt text-orange");

        NUMERO_A_ROL.put("1", "COLABORADOR");
        NUMERO_A_ROL.put("2", "PROJECT_MANAGER");
        NUMERO_A_ROL.put("3", "RESOURCE_MANAGER");
        NUMERO_A_ROL.forEach((numero, rol) -> ROL_A_NUMERO.put(rol, numero));
    }

    /**
     * Fila del directorio de usuarios, lista para la vista.
     *
     * @param id id del registro
     * @param nombreCompleto nombre y apellido, o {@code null} si aún no activó su cuenta
     * @param iniciales inicial para el avatar
     * @param correo correo del usuario
     * @param rolNombre nombre técnico del rol
     * @param rolEtiqueta etiqueta o nombre técnico del rol
     * @param rolBadgeClase clase CSS de la etiqueta del rol
     * @param estado estado por el que se filtra; {@code null} para no filtrar
     * @param estadoBadgeClase clase CSS de la etiqueta del estado
     * @param estadoDot clase CSS del punto del estado
     * @param pendiente {@code true} si aún no activó su cuenta
     * @param activo {@code true} si la cuenta no está desactivada
     * @param fechaCreacion fecha de creación, ya formateada
     * @param cargoId id del cargo
     * @param cargoNombre nombre del cargo
     */
    public record UsuarioFila(Long id, String nombreCompleto, String iniciales, String correo,
                               String rolNombre, String rolEtiqueta, String rolBadgeClase,
                               String estado, String estadoBadgeClase, String estadoDot,
                               boolean pendiente, boolean activo, String fechaCreacion,
                               Long cargoId, String cargoNombre) {
    }

    /**
     * Totales de las tarjetas del directorio de usuarios.
     *
     * @param total cantidad total
     * @param pendientes cuentas sin activar
     * @param activos cuentas activas
     * @param inactivos cuentas desactivadas
     */
    public record ResumenUsuarios(long total, long pendientes, long activos, long inactivos) {
    }

    /**
     * Usuarios activos de un rol, para el gráfico del dashboard.
     * <p>
     * Dashboard: usuarios activos de un rol y su porcentaje sobre el total, para dibujar la barra.
     *
     * @param rolEtiqueta etiqueta o nombre técnico del rol
     * @param rolBadgeClase clase CSS de la etiqueta del rol
     * @param barraClase clase CSS de la barra
     * @param total cantidad total
     * @param porcentaje porcentaje sobre el total
     */
    public record RolConteo(String rolEtiqueta, String rolBadgeClase, String barraClase, long total, int porcentaje) {
    }

    /**
     * Resultado de una carga masiva.
     *
     * @param creados cuentas creadas
     * @param errores errores por fila
     * @param sinCorreo cuentas cuyo correo no se pudo enviar
     */
    public record ResultadoCargaMasiva(int creados, List<String> errores, int sinCorreo) {
        /** Indica si el archivo tuvo errores; en ese caso no se creó ninguna cuenta. */
        public boolean tieneErrores() { return !errores.isEmpty(); }
    }

    /**
     * Fila de la bandeja de pre-registros.
     *
     * @param id id del registro
     * @param nombreCompleto nombre y apellido, o {@code null} si aún no activó su cuenta
     * @param correo correo del usuario
     * @param cvUrl ruta del CV
     * @param fechaSubida fecha en que se subió el CV, ya formateada
     */
    public record CvFila(Long id, String nombreCompleto, String correo, String cvUrl,
                          String fechaSubida) {
    }

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final CargoRepository cargoRepository;
    private final AuthService authService;
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;
    private final EmailService emailService;
    private final com.pucp.skillb_ia.service.col.ColaboradorPerfilService colaboradorPerfilService;

    /** Crea el componente con sus dependencias, que Spring inyecta por el constructor. */
    public AdminUsuarioService(UsuarioRepository usuarioRepository, RolRepository rolRepository,
                                CargoRepository cargoRepository,
                                AuthService authService, AuditoriaService auditoriaService,
                                NotificacionService notificacionService, EmailService emailService,
                                com.pucp.skillb_ia.service.col.ColaboradorPerfilService colaboradorPerfilService) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.cargoRepository = cargoRepository;
        this.authService = authService;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
        this.emailService = emailService;
        this.colaboradorPerfilService = colaboradorPerfilService;
    }

    /**
     * Devuelve las etiquetas de todos los roles, para los filtros.
     *
     * @return lista de etiquetas
     */
    public static List<String> etiquetasRoles() {
        return new ArrayList<>(ETIQUETA_A_ROL.keySet());
    }

    /**
     * Devuelve los roles que el Administrador puede asignar.
     * <p>
     * Para los &lt;select&gt; de "Nuevo usuario" y "Editar rol": excluye Administrador.
     *
     * @return lista de etiquetas
     */
    public static List<String> etiquetasRolesAsignables() {
        return ETIQUETA_A_ROL.keySet().stream().filter(e -> !e.equals("Administrador")).toList();
    }

    /**
     * Lista todos los usuarios con su rol y estado.
     *
     * @return filas del directorio
     */
    @Transactional(readOnly = true)
    public List<UsuarioFila> listar() {
        return usuarioRepository.findAllWithRol().stream().map(this::aFila).toList();
    }

    /** Directorio filtrado y paginado en el servidor (antes lo hacía admin-usuarios.js). */
    @Transactional(readOnly = true)
    public Page<UsuarioFila> listarPagina(String busqueda, String rol, String estado, Integer pagina) {
        List<UsuarioFila> filtradas = listar().stream()
                .filter(u -> Paginador.contiene((u.nombreCompleto() != null ? u.nombreCompleto() : "") + " " + u.correo(), busqueda))
                .filter(u -> Paginador.coincide(u.rolEtiqueta(), rol))
                .filter(u -> Paginador.coincide(u.estado(), estado))
                .toList();
        return Paginador.paginar(filtradas, pagina);
    }

    /**
     * Cuenta los usuarios por estado.
     *
     * @return totales por estado
     */
    @Transactional(readOnly = true)
    public ResumenUsuarios resumen() {
        List<UsuarioFila> filas = listar();
        long pendientes = filas.stream().filter(UsuarioFila::pendiente).count();
        long activos = filas.stream().filter(f -> f.activo() && !f.pendiente()).count();
        long inactivos = filas.stream().filter(f -> !f.activo()).count();
        return new ResumenUsuarios(filas.size(), pendientes, activos, inactivos);
    }

    /**
     * Cuenta los usuarios activos de cada rol.
     * <p>
     * Usuarios activos (cuenta ya activada) por rol, en el orden de ROL_A_ETIQUETA.
     *
     * @return un conteo por rol
     */
    @Transactional(readOnly = true)
    public List<RolConteo> usuariosPorRol() {
        List<UsuarioFila> activos = listar().stream().filter(f -> f.activo() && !f.pendiente()).toList();
        return ROL_A_ETIQUETA.entrySet().stream()
                .map(rol -> {
                    long total = activos.stream().filter(f -> rol.getKey().equals(f.rolNombre())).count();
                    int porcentaje = activos.isEmpty() ? 0 : (int) Math.round(total * 100.0 / activos.size());
                    String badge = ROL_A_BADGE.get(rol.getKey());
                    // "bg-green-lt text-green" -> "bg-green": la barra usa el color fuerte del badge del rol.
                    String barra = "bg-" + badge.substring(badge.indexOf("text-") + "text-".length());
                    return new RolConteo(rol.getValue(), badge, barra, total, porcentaje);
                })
                .toList();
    }

    /**
     * Crea una cuenta por invitación.
     * <p>
     * Devuelve si el correo de invitación salió (la cuenta se crea igual; el enlace se puede reenviar).
     *
     * @param correo correo del usuario
     * @param rolEtiqueta etiqueta o nombre técnico del rol
     * @param admin Administrador que realiza la acción; queda en la auditoría
     * @return {@code true} si el correo de invitación se envió
     * @throws IllegalArgumentException si el correo o el rol no son válidos, o el correo ya existe
     */
    @Transactional
    public boolean crear(String correo, String rolEtiqueta, Usuario admin) {
        String rolNombre = resolverRolAsignable(rolEtiqueta);
        return authService.invitar(correo.trim(), rolNombre, admin).correoEnviado();
    }

    /**
     * Cambia el rol de un usuario, cierra sus sesiones y lo registra en la auditoría. Si ya tenía ese rol no
     * hace nada.
     *
     * @param usuarioId id del usuario
     * @param nuevoRolEtiqueta etiqueta o nombre técnico del nuevo rol
     * @param admin Administrador que realiza la acción; queda en la auditoría
     * @throws IllegalArgumentException si el usuario o el rol no son válidos, o si dejaría al sistema sin un
     *         Administrador activo
     */
    @Transactional
    public void cambiarRol(Long usuarioId, String nuevoRolEtiqueta, Usuario admin) {
        String nuevoRolNombre = resolverRolAsignable(nuevoRolEtiqueta);
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        String rolAnterior = usuario.getRol().getNombre();
        if (rolAnterior.equals(nuevoRolNombre)) return;

        // El sistema nunca puede quedar sin un Administrador activo. La pantalla no ofrece cambiar el
        // rol de un Administrador, pero la regla se valida aquí por si la petición llega de otra forma.
        if ("ADMINISTRADOR".equals(rolAnterior) && usuario.isActivo()
                && usuarioRepository.countByRol_NombreAndActivoTrue("ADMINISTRADOR") <= 1) {
            throw new IllegalArgumentException("Debe quedar al menos un Administrador activo.");
        }

        Rol nuevoRol = rolRepository.findByNombre(nuevoRolNombre)
                .orElseThrow(() -> new IllegalArgumentException("Rol inválido: " + nuevoRolNombre));
        usuario.setRol(nuevoRol);
        usuarioRepository.save(usuario);
        // Sus sesiones abiertas guardan el rol anterior: se cierran para que entre con el nuevo.
        authService.cerrarSesiones(usuario.getCorreo());

        auditoriaService.registrar(admin, "CAMBIAR_ROL", "USUARIO", usuario.getId(),
                "Cambió el rol de " + usuario.getCorreo() + ".", rolAnterior, nuevoRolNombre, null);
    }

    /**
     * Asigna un cargo a un colaborador y recalcula su sueldo base.
     * <p>
     * El cargo solo aplica a colaboradores. Al asignarlo se recalcula su sueldo base con la tarifa del cargo para
     * su nivel de experiencia (si ya lo tiene).
     *
     * @param usuarioId id del usuario
     * @param cargoId id del cargo
     * @param admin Administrador que realiza la acción; queda en la auditoría
     * @throws IllegalArgumentException si el usuario no es colaborador, o el cargo no existe o está desactivado
     */
    @Transactional
    public void asignarCargo(Long usuarioId, Long cargoId, Usuario admin) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        if (!"COLABORADOR".equals(usuario.getRol().getNombre())) {
            throw new IllegalArgumentException("Solo se puede asignar un cargo a colaboradores.");
        }
        Cargo cargo = cargoRepository.findById(cargoId)
                .orElseThrow(() -> new IllegalArgumentException("Cargo no encontrado."));
        if (!cargo.isActivo()) {
            throw new IllegalArgumentException("El cargo \"" + cargo.getNombre() + "\" está desactivado.");
        }

        String anterior = usuario.getCargo() != null ? usuario.getCargo().getNombre() : null;
        usuario.setCargo(cargo);
        AdminCargoService.aplicarSueldoSegunCargo(usuario);
        usuarioRepository.save(usuario);

        auditoriaService.registrar(admin, "ASIGNAR_CARGO", "USUARIO", usuario.getId(),
                "Asignó el cargo \"" + cargo.getNombre() + "\" a " + usuario.getCorreo() + ".",
                anterior, cargo.getNombre(), null);
    }

    /**
     * Desactiva una cuenta, cierra sus sesiones y lo registra en la auditoría.
     *
     * @param usuarioId id del usuario
     * @param admin Administrador que realiza la acción; queda en la auditoría
     * @throws IllegalArgumentException si el usuario no existe o es Administrador
     */
    @Transactional
    public void desactivar(Long usuarioId, Usuario admin) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        if ("ADMINISTRADOR".equals(usuario.getRol().getNombre())) {
            throw new IllegalArgumentException("No se puede desactivar a un Administrador desde esta pantalla.");
        }
        usuario.setActivo(false);
        usuarioRepository.save(usuario);
        // Un usuario desactivado no debe seguir usando el sistema con la sesión que ya tenía abierta.
        authService.cerrarSesiones(usuario.getCorreo());
        auditoriaService.registrar(admin, "DESACTIVAR_USUARIO", "USUARIO", usuario.getId(),
                "Desactivó al usuario " + usuario.getCorreo() + ".");
    }

    /**
     * Reactiva una cuenta desactivada.
     *
     * @param usuarioId id del usuario
     * @param admin Administrador que realiza la acción; queda en la auditoría
     * @throws IllegalArgumentException si el usuario no existe
     */
    @Transactional
    public void reactivar(Long usuarioId, Usuario admin) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        usuario.setActivo(true);
        usuarioRepository.save(usuario);
        auditoriaService.registrar(admin, "REACTIVAR_USUARIO", "USUARIO", usuario.getId(),
                "Reactivó al usuario " + usuario.getCorreo() + ".");
    }

    /**
     * Genera un nuevo enlace de activación; el anterior deja de servir.
     *
     * @param usuarioId id del usuario
     * @param admin Administrador que realiza la acción; queda en la auditoría
     * @return {@code true} si el correo se envió
     * @throws IllegalArgumentException si el usuario no existe
     */
    @Transactional
    public boolean reenviarActivacion(Long usuarioId, Usuario admin) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        return authService.reenviarActivacion(usuario, admin);
    }

    /**
     * Fila leída del archivo de carga masiva, antes de validarla.
     * <p>
     * Fila cruda leída del archivo (CSV o Excel), antes de validar. `completa` indica si tiene correo Y rol no
     * vacíos (si no, es un error de "fila incompleta").
     *
     * @param numeroFila número de fila del archivo
     * @param correo correo del usuario
     * @param rol etiqueta del rol por el que se filtra; {@code null} para no filtrar
     * @param completa {@code true} si tiene correo y rol
     */
    private record FilaCruda(int numeroFila, String correo, String rol, boolean completa) {
    }

    /**
     * Crea cuentas desde un archivo CSV o Excel de hasta 200 filas.
     * <p>
     * Se valida TODO el archivo antes de crear un solo usuario: si hay al menos un error (correo duplicado, rol
     * inválido, fila incompleta), no se crea nada — el admin corrige el archivo y vuelve a intentar. Evita
     * cargas parciales confusas a mitad de archivo.
     *
     * @param archivo archivo subido
     * @param admin Administrador que realiza la acción; queda en la auditoría
     * @return cuentas creadas, o los errores por fila si no se creó ninguna
     * @throws IOException si no se puede leer el archivo
     */
    @Transactional
    public ResultadoCargaMasiva cargaMasiva(MultipartFile archivo, Usuario admin) throws IOException {
        List<FilaCruda> filasCrudas = leerFilas(archivo);

        // Cada fila envía un correo y agota el cupo diario de Gmail: se evita una carga accidental o abusiva.
        if (filasCrudas.size() > MAX_FILAS_CARGA_MASIVA) {
            return new ResultadoCargaMasiva(0, List.of("El archivo tiene " + filasCrudas.size()
                    + " filas. El máximo por carga es " + MAX_FILAS_CARGA_MASIVA + "."), 0);
        }

        List<String> errores = new ArrayList<>();
        List<String[]> filasValidas = new ArrayList<>();
        Set<String> correosEnArchivo = new LinkedHashSet<>();

        for (FilaCruda fila : filasCrudas) {
            if (!fila.completa()) {
                errores.add("Fila " + fila.numeroFila() + ": debe tener correo y rol.");
                continue;
            }

            String correo = fila.correo();
            String numeroRol = fila.rol();

            try {
                authService.validarCorreo(correo);
            } catch (IllegalArgumentException e) {
                errores.add("Fila " + fila.numeroFila() + ": correo inválido (\"" + correo + "\").");
                continue;
            }
            if (!NUMERO_A_ROL.containsKey(numeroRol)) {
                errores.add("Fila " + fila.numeroFila() + ": rol inválido (\"" + numeroRol + "\"). Usa 1 (Colaborador), 2 (Project Manager) o 3 (Resource Manager).");
                continue;
            }
            String correoNormalizado = correo.toLowerCase();
            if (!correosEnArchivo.add(correoNormalizado)) {
                errores.add("Fila " + fila.numeroFila() + ": correo duplicado dentro del archivo (\"" + correo + "\").");
                continue;
            }
            if (usuarioRepository.existsByCorreo(correo)) {
                errores.add("Fila " + fila.numeroFila() + ": ya existe un usuario con el correo \"" + correo + "\".");
                continue;
            }

            filasValidas.add(new String[]{correo, NUMERO_A_ROL.get(numeroRol)});
        }

        if (!errores.isEmpty()) {
            return new ResultadoCargaMasiva(0, errores, 0);
        }

        int sinCorreo = 0;
        for (String[] fila : filasValidas) {
            if (!crear(fila[0], fila[1], admin)) {
                sinCorreo++;
            }
        }

        auditoriaService.registrar(admin, "CARGA_MASIVA_USUARIOS", "USUARIO", null,
                "Cargó " + filasValidas.size() + " usuario(s) mediante archivo.");

        return new ResultadoCargaMasiva(filasValidas.size(), errores, sinCorreo);
    }

    /**
     * Lee el archivo como Excel o como CSV según su extensión.
     *
     * @param archivo archivo subido
     * @return filas sin validar
     * @throws IOException si no se puede leer el archivo
     */
    private List<FilaCruda> leerFilas(MultipartFile archivo) throws IOException {
        String nombre = archivo.getOriginalFilename() != null ? archivo.getOriginalFilename().toLowerCase() : "";
        if (nombre.endsWith(".xlsx") || nombre.endsWith(".xls")) {
            return leerFilasExcel(archivo);
        }
        return leerFilasCsv(archivo);
    }

    /**
     * Lee las filas de un CSV; ignora la cabecera y las líneas vacías.
     *
     * @param archivo archivo subido
     * @return filas sin validar
     * @throws IOException si no se puede leer el archivo
     */
    private List<FilaCruda> leerFilasCsv(MultipartFile archivo) throws IOException {
        List<FilaCruda> filas = new ArrayList<>();
        try (BufferedReader lector = new BufferedReader(
                new InputStreamReader(archivo.getInputStream(), StandardCharsets.UTF_8))) {
            String linea;
            int numeroFila = 0;
            boolean primeraLinea = true;
            while ((linea = lector.readLine()) != null) {
                numeroFila++;
                if (linea.isBlank()) continue;
                if (primeraLinea) {
                    primeraLinea = false;
                    if (linea.toLowerCase().replace(" ", "").startsWith("correo,rol")) continue;
                }

                String[] columnas = linea.split(",", -1);
                String correo = columnas.length > 0 ? columnas[0].trim() : "";
                String rol = columnas.length > 1 ? columnas[1].trim() : "";
                boolean completa = columnas.length >= 2 && !correo.isEmpty() && !rol.isEmpty();
                filas.add(new FilaCruda(numeroFila, correo, rol, completa));
            }
        }
        return filas;
    }

    /**
     * Lee las filas de la primera hoja de un Excel; ignora la cabecera y las filas vacías.
     *
     * @param archivo archivo subido
     * @return filas sin validar
     * @throws IOException si no se puede leer el archivo
     */
    private List<FilaCruda> leerFilasExcel(MultipartFile archivo) throws IOException {
        List<FilaCruda> filas = new ArrayList<>();
        DataFormatter formateador = new DataFormatter();

        try (Workbook libro = WorkbookFactory.create(archivo.getInputStream())) {
            Sheet hoja = libro.getSheetAt(0);
            for (Row fila : hoja) {
                int numeroFila = fila.getRowNum() + 1;
                Cell celdaCorreo = fila.getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                Cell celdaRol = fila.getCell(1, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                String correo = formateador.formatCellValue(celdaCorreo).trim();
                String rol = formateador.formatCellValue(celdaRol).trim();

                if (correo.isEmpty() && rol.isEmpty()) continue;
                if (numeroFila == 1 && correo.equalsIgnoreCase("correo")) continue;

                filas.add(new FilaCruda(numeroFila, correo, rol, !correo.isEmpty() && !rol.isEmpty()));
            }
        }
        return filas;
    }

    /**
     * Convierte la etiqueta del rol en su nombre técnico.
     * <p>
     * Acepta la etiqueta legible ("Colaborador") o el nombre técnico ("COLABORADOR"), pero SIEMPRE rechaza
     * Administrador — ese rol no se asigna desde esta pantalla (solo existe por seed directo en la BD).
     *
     * @param rolEtiquetaORol etiqueta o nombre técnico del rol
     * @return nombre técnico del rol
     * @throws IllegalArgumentException si el rol no existe o es Administrador
     */
    private String resolverRolAsignable(String rolEtiquetaORol) {
        String rolNombre;
        if (ETIQUETA_A_ROL.containsKey(rolEtiquetaORol)) {
            rolNombre = ETIQUETA_A_ROL.get(rolEtiquetaORol);
        } else if (ROL_A_ETIQUETA.containsKey(rolEtiquetaORol)) {
            rolNombre = rolEtiquetaORol;
        } else {
            throw new IllegalArgumentException("Rol inválido: " + rolEtiquetaORol);
        }
        if ("ADMINISTRADOR".equals(rolNombre)) {
            throw new IllegalArgumentException("El rol Administrador no se puede asignar desde esta pantalla.");
        }
        return rolNombre;
    }

    /**
     * Convierte un usuario en una fila del directorio.
     *
     * @param u usuario
     * @return fila para la vista
     */
    private UsuarioFila aFila(Usuario u) {
        String rolNombre = u.getRol().getNombre();
        String nombreCompleto = (u.getNombre() != null || u.getApellido() != null)
                ? ((u.getNombre() != null ? u.getNombre() : "") + " " + (u.getApellido() != null ? u.getApellido() : "")).trim()
                : null;
        boolean pendiente = u.isActivo() && u.getPasswordHash() == null;

        String estado;
        String estadoBadge;
        String estadoDot;
        if (!u.isActivo()) {
            estado = "Inactivo";
            estadoBadge = "bg-secondary-lt";
            estadoDot = "bg-secondary";
        } else if (pendiente) {
            estado = "Pendiente";
            estadoBadge = "bg-yellow-lt";
            estadoDot = "bg-yellow";
        } else {
            estado = "Activo";
            estadoBadge = "bg-green-lt";
            estadoDot = "bg-green";
        }

        String iniciales = nombreCompleto != null && !nombreCompleto.isBlank()
                ? String.valueOf(nombreCompleto.trim().charAt(0)).toUpperCase()
                : "?";

        return new UsuarioFila(u.getId(), nombreCompleto, iniciales, u.getCorreo(),
                rolNombre, ROL_A_ETIQUETA.getOrDefault(rolNombre, rolNombre),
                ROL_A_BADGE.getOrDefault(rolNombre, "bg-secondary-lt text-secondary"),
                estado, estadoBadge, estadoDot, pendiente, u.isActivo(),
                u.getFechaCreacion() != null ? u.getFechaCreacion().format(FECHA_FORMATO) : "",
                u.getCargo() != null ? u.getCargo().getId() : null,
                u.getCargo() != null ? u.getCargo().getNombre() : null);
    }

    // ============================================================
    // CV DE COLABORADORES (revisión + experiencia profesional)
    // ============================================================

    /**
     * Busca un usuario por su id.
     *
     * @param id id del registro
     * @return el usuario
     * @throws IllegalArgumentException si no existe
     */
    @Transactional(readOnly = true)
    public Usuario obtenerUsuario(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el usuario."));
    }

    /**
     * Lista los colaboradores con el pre-registro o el CV pendiente de revisión.
     *
     * @return filas de la bandeja
     */
    @Transactional(readOnly = true)
    public List<CvFila> listarCvsPendientes() {
        return listarCvs(u -> u.getRegistroEstado() == EstadoRegistro.PENDIENTE
                || u.getCvEstado() == EstadoCv.PENDIENTE);
    }

    /**
     * Lista los colaboradores con el registro aprobado.
     * <p>
     * "Aprobados": el Admin ya revisó su CV (cv_estado REVISADO).
     *
     * @return filas de la bandeja
     */
    @Transactional(readOnly = true)
    public List<CvFila> listarCvsRevisados() {
        return listarCvs(u -> u.getCvEstado() == EstadoCv.REVISADO
                && u.getRegistroEstado() != EstadoRegistro.PENDIENTE
                && u.getRegistroEstado() != EstadoRegistro.RECHAZADO);
    }

    /**
     * Lista los colaboradores con el registro devuelto con observaciones.
     *
     * @return filas de la bandeja
     */
    @Transactional(readOnly = true)
    public List<CvFila> listarCvsRechazados() {
        return listarCvs(u -> u.getCvEstado() == EstadoCv.RECHAZADO
                && u.getRegistroEstado() != EstadoRegistro.PENDIENTE);
    }

    /**
     * Lista los colaboradores activos que cumplen una condición.
     *
     * @param filtro condición que debe cumplir el colaborador
     * @return filas de la bandeja
     */
    private List<CvFila> listarCvs(Predicate<Usuario> filtro) {
        return usuarioRepository.findActivosByRolNombre("COLABORADOR").stream()
                .filter(filtro)
                .map(u -> new CvFila(u.getId(), nombreCompletoCv(u), u.getCorreo(), u.getCvUrl(),
                        u.getCvFechaSubida() != null ? u.getCvFechaSubida().format(FECHA_FORMATO) : ""))
                .toList();
    }

    /**
     * Aprueba el registro, avisa al colaborador y, si venía de un pre-registro, también al Resource Manager.
     * <p>
     * Aprueba el pre-registro (o el CV de reemplazo) del colaborador. Los datos personales que escribió ya están
     * en su perfil; lo que falta es la experiencia profesional, que el Admin completa antes.
     *
     * @param colaboradorId id del colaborador
     * @param admin Administrador que realiza la acción; queda en la auditoría
     * @throws IllegalArgumentException si el colaborador no existe o no subió su CV
     */
    @Transactional
    public void aprobarRegistro(Long colaboradorId, Usuario admin) {
        Usuario colaborador = usuarioRepository.findById(colaboradorId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el colaborador."));
        if (colaborador.getCvUrl() == null || colaborador.getCvUrl().isBlank()) {
            throw new IllegalArgumentException("El colaborador todavía no subió su CV.");
        }
        colaborador.setCvEstado(EstadoCv.REVISADO);
        colaborador.setMotivoRechazo(null);
        // Si venía de un pre-registro sin aprobar, desde ahora el RM ya ve lo que cargó
        // (certificados y formación) y se le avisa de lo pendiente.
        boolean eraRegistroSinAprobar = colaborador.getRegistroEstado() == EstadoRegistro.PENDIENTE
                || colaborador.getRegistroEstado() == EstadoRegistro.RECHAZADO;
        if (colaborador.getRegistroEstado() != null) {
            colaborador.setRegistroEstado(EstadoRegistro.APROBADO);
        }
        usuarioRepository.save(colaborador);
        if (eraRegistroSinAprobar) {
            colaboradorPerfilService.avisarAlRmDelRegistroAprobado(colaborador);
        }

        auditoriaService.registrar(admin, "REVISAR_CV", "USUARIO", colaborador.getId(),
                "Aprobó el registro/CV de " + nombreCompletoCv(colaborador) + ".");

        notificacionService.crear(colaborador, "CV_REVISADO", CategoriaNotificacion.SISTEMA,
                "Tu registro fue aprobado",
                "El Administrador revisó tu CV y aprobó tu registro. Ya tienes acceso completo.",
                "USUARIO", colaborador.getId());
    }

    /**
     * Devuelve el registro con un motivo y avisa al colaborador por correo y con una notificación.
     * <p>
     * Rechazar = devolver con observaciones: no borra nada, el colaborador corrige y vuelve a subir su CV.
     *
     * @param colaboradorId id del colaborador
     * @param motivo motivo del rechazo, de hasta 500 caracteres
     * @param admin Administrador que realiza la acción; queda en la auditoría
     * @throws IllegalArgumentException si falta el motivo, es muy largo o el colaborador no existe
     */
    @Transactional
    public void rechazarRegistro(Long colaboradorId, String motivo, Usuario admin) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Escribe el motivo del rechazo.");
        }
        String motivoLimpio = motivo.strip();
        if (motivoLimpio.length() > 500) {
            throw new IllegalArgumentException("El motivo no puede superar los 500 caracteres.");
        }
        Usuario colaborador = usuarioRepository.findById(colaboradorId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el colaborador."));
        colaborador.setCvEstado(EstadoCv.RECHAZADO);
        colaborador.setMotivoRechazo(motivoLimpio);
        if (colaborador.getRegistroEstado() == EstadoRegistro.PENDIENTE) {
            colaborador.setRegistroEstado(EstadoRegistro.RECHAZADO);
        }
        usuarioRepository.save(colaborador);

        auditoriaService.registrar(admin, "RECHAZAR_CV", "USUARIO", colaborador.getId(),
                "Rechazó el registro/CV de " + nombreCompletoCv(colaborador) + ": " + motivoLimpio);

        notificacionService.crear(colaborador, "CV_RECHAZADO", CategoriaNotificacion.SISTEMA,
                "Tu registro fue devuelto con observaciones", motivoLimpio,
                "USUARIO", colaborador.getId());
        emailService.enviarRechazoRegistro(colaborador.getCorreo(), motivoLimpio);
    }

    /**
     * Devuelve el nombre y apellido del usuario, o su correo si no los tiene.
     *
     * @param u usuario
     * @return nombre para mostrar
     */
    private String nombreCompletoCv(Usuario u) {
        String completo = ((u.getNombre() != null ? u.getNombre() : "") + " "
                + (u.getApellido() != null ? u.getApellido() : "")).trim();
        return completo.isEmpty() ? u.getCorreo() : completo;
    }
}
