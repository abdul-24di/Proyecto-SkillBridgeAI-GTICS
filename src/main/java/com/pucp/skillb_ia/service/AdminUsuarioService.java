package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
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
import java.util.Map;
import java.util.Set;

// Épica 5 (Gestión de usuarios). Por decisión C1/C2 esta clase NO reimplementa
// nada de tokens/activación — delega siempre a AuthService (ver comentario en
// esa clase). Solo se encarga de: listar/filtrar el directorio, cambiar rol,
// activar/desactivar y la carga masiva por CSV.
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

    public record UsuarioFila(Long id, String nombreCompleto, String iniciales, String correo,
                               String rolNombre, String rolEtiqueta, String rolBadgeClase,
                               String estado, String estadoBadgeClase, String estadoDot,
                               boolean pendiente, boolean activo, String fechaCreacion) {
    }

    public record ResumenUsuarios(long total, long pendientes, long activos, long inactivos) {
    }

    public record ResultadoCargaMasiva(int creados, List<String> errores) {
        public boolean tieneErrores() { return !errores.isEmpty(); }
    }

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final AuthService authService;
    private final AuditoriaService auditoriaService;

    public AdminUsuarioService(UsuarioRepository usuarioRepository, RolRepository rolRepository,
                                AuthService authService, AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.authService = authService;
        this.auditoriaService = auditoriaService;
    }

    public static List<String> etiquetasRoles() {
        return new ArrayList<>(ETIQUETA_A_ROL.keySet());
    }

    // Para los <select> de "Nuevo usuario" y "Editar rol": excluye Administrador.
    public static List<String> etiquetasRolesAsignables() {
        return ETIQUETA_A_ROL.keySet().stream().filter(e -> !e.equals("Administrador")).toList();
    }

    @Transactional(readOnly = true)
    public List<UsuarioFila> listar() {
        return usuarioRepository.findAllWithRol().stream().map(this::aFila).toList();
    }

    @Transactional(readOnly = true)
    public ResumenUsuarios resumen() {
        List<UsuarioFila> filas = listar();
        long pendientes = filas.stream().filter(UsuarioFila::pendiente).count();
        long activos = filas.stream().filter(f -> f.activo() && !f.pendiente()).count();
        long inactivos = filas.stream().filter(f -> !f.activo()).count();
        return new ResumenUsuarios(filas.size(), pendientes, activos, inactivos);
    }

    @Transactional
    public void crear(String correo, String rolEtiqueta, Usuario admin) {
        String rolNombre = resolverRolAsignable(rolEtiqueta);
        authService.invitarUsuario(correo.trim(), rolNombre, admin);
    }

    @Transactional
    public void cambiarRol(Long usuarioId, String nuevoRolEtiqueta, Usuario admin) {
        String nuevoRolNombre = resolverRolAsignable(nuevoRolEtiqueta);
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        String rolAnterior = usuario.getRol().getNombre();
        if (rolAnterior.equals(nuevoRolNombre)) return;

        Rol nuevoRol = rolRepository.findByNombre(nuevoRolNombre)
                .orElseThrow(() -> new IllegalArgumentException("Rol inválido: " + nuevoRolNombre));
        usuario.setRol(nuevoRol);
        usuarioRepository.save(usuario);

        auditoriaService.registrar(admin, "CAMBIAR_ROL", "USUARIO", usuario.getId(),
                "Cambió el rol de " + usuario.getCorreo() + ".", rolAnterior, nuevoRolNombre, null);
    }

    @Transactional
    public void desactivar(Long usuarioId, Usuario admin) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        if ("ADMINISTRADOR".equals(usuario.getRol().getNombre())) {
            throw new IllegalArgumentException("No se puede desactivar a un Administrador desde esta pantalla.");
        }
        usuario.setActivo(false);
        usuarioRepository.save(usuario);
        auditoriaService.registrar(admin, "DESACTIVAR_USUARIO", "USUARIO", usuario.getId(),
                "Desactivó al usuario " + usuario.getCorreo() + ".");
    }

    @Transactional
    public void reactivar(Long usuarioId, Usuario admin) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        usuario.setActivo(true);
        usuarioRepository.save(usuario);
        auditoriaService.registrar(admin, "REACTIVAR_USUARIO", "USUARIO", usuario.getId(),
                "Reactivó al usuario " + usuario.getCorreo() + ".");
    }

    @Transactional
    public void reenviarActivacion(Long usuarioId, Usuario admin) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        authService.reenviarActivacion(usuario, admin);
    }

    // Fila cruda leída del archivo (CSV o Excel), antes de validar. `completa`
    // indica si tiene correo Y rol no vacíos (si no, es un error de "fila incompleta").
    private record FilaCruda(int numeroFila, String correo, String rol, boolean completa) {
    }

    // Se valida TODO el archivo antes de crear un solo usuario: si hay al menos
    // un error (correo duplicado, rol inválido, fila incompleta), no se crea
    // nada — el admin corrige el archivo y vuelve a intentar. Evita cargas
    // parciales confusas a mitad de archivo.
    @Transactional
    public ResultadoCargaMasiva cargaMasiva(MultipartFile archivo, Usuario admin) throws IOException {
        List<FilaCruda> filasCrudas = leerFilas(archivo);

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

            if (!correo.contains("@")) {
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
            return new ResultadoCargaMasiva(0, errores);
        }

        for (String[] fila : filasValidas) {
            crear(fila[0], fila[1], admin);
        }

        auditoriaService.registrar(admin, "CARGA_MASIVA_USUARIOS", "USUARIO", null,
                "Cargó " + filasValidas.size() + " usuario(s) mediante archivo.");

        return new ResultadoCargaMasiva(filasValidas.size(), errores);
    }

    private List<FilaCruda> leerFilas(MultipartFile archivo) throws IOException {
        String nombre = archivo.getOriginalFilename() != null ? archivo.getOriginalFilename().toLowerCase() : "";
        if (nombre.endsWith(".xlsx") || nombre.endsWith(".xls")) {
            return leerFilasExcel(archivo);
        }
        return leerFilasCsv(archivo);
    }

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

    // Acepta la etiqueta legible ("Colaborador") o el nombre técnico
    // ("COLABORADOR"), pero SIEMPRE rechaza Administrador — ese rol no se
    // asigna desde esta pantalla (solo existe por seed directo en la BD).
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
                u.getFechaCreacion() != null ? u.getFechaCreacion().format(FECHA_FORMATO) : "");
    }
}
