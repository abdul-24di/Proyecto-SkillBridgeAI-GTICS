package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.dto.auth.PoliticaTelefono;
import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.NivelDominio;
import com.pucp.skillb_ia.model.enums.EstadoCertificado;
import com.pucp.skillb_ia.model.enums.EstadoCv;
import com.pucp.skillb_ia.model.enums.EstadoRegistro;
import com.pucp.skillb_ia.model.enums.EstadoValidacion;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.service.ArchivoAlmacenamientoService;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.NotificacionService;
import com.pucp.skillb_ia.service.RmRevisionAsignadorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.stream.Collectors;


/**
 * Lógica del perfil profesional del colaborador.
 * <p>
 * Gestiona sus datos personales (sobre mí, teléfono, foto, CV, contraseña), sus habilidades y
 * certificados, su educación y su experiencia profesional, además de calcular qué tan
 * completo está el perfil.
 * <p>
 * Muchos datos pasan por revisión: las habilidades y certificados los valida el Resource
 * Manager, la educación la aprueba el RM y el CV lo revisa el Administrador. Cada cambio
 * relevante queda en el log de auditoría. Las reglas inválidas se informan con
 * {@link IllegalArgumentException} y un mensaje apto para el usuario.
 * <p>
 * Algunos métodos de validación son públicos porque también los usa el pre-registro, para
 * que lo que se acepta al registrarse sea lo mismo que acepta el perfil después.
 */
@Service
public class ColaboradorPerfilService {

    /** Tipos MIME aceptados para la foto de perfil: JPG y PNG. */
    private static final Set<String> TIPOS_IMAGEN_PERMITIDOS = Set.of("image/jpeg", "image/png");

    /** Tamaño máximo de la foto de perfil: 2 MB. */
    private static final long TAMANO_MAXIMO_FOTO_BYTES = 2L * 1024 * 1024; // 2MB

    /** Tipos MIME aceptados para certificados: PDF, JPG y PNG. */
    private static final Set<String> TIPOS_CERTIFICADO_PERMITIDOS =
            Set.of("application/pdf", "image/jpeg", "image/png");

    /** Tamaño máximo de un certificado: 10 MB. */
    private static final long TAMANO_MAXIMO_CERTIFICADO_BYTES = 10L * 1024 * 1024;

    /** El CV solo se acepta en PDF. */
    private static final Set<String> TIPOS_CV_PERMITIDOS = Set.of("application/pdf");

    /** Tamaño máximo del CV: 5 MB. */
    private static final long TAMANO_MAXIMO_CV_BYTES = 5L * 1024 * 1024; // 5MB


    private final UsuarioRepository usuarioRepository;

    private final ColaboradorHabilidadRepository colaboradorHabilidadRepository;

    private final HabilidadRepository habilidadRepository;

    private final CategoriaHabilidadRepository categoriaHabilidadRepository;




    private final EducacionRepository educacionRepository;
    private final CertificadoRepository certificadoRepository;
    private final ExperienciaProfesionalRepository experienciaProfesionalRepository;


    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;
    private final String uploadDir;
    private final ArchivoAlmacenamientoService archivoAlmacenamientoService;
    private final RmRevisionAsignadorService rmRevisionAsignadorService;

    /**
     * Inyecta por constructor las dependencias del service.
     *
     * @param usuarioRepository acceso a usuarios
     * @param colaboradorHabilidadRepository habilidades de cada colaborador
     * @param habilidadRepository catálogo de habilidades
     * @param categoriaHabilidadRepository categorías de habilidades
     * @param educacionRepository formación académica
     * @param certificadoRepository certificados de habilidades
     * @param experienciaProfesionalRepository experiencia profesional
     * @param passwordEncoder codifica y compara contraseñas
     * @param auditoriaService registro en el log de auditoría
     * @param notificacionService envío de notificaciones
     * @param uploadDir carpeta de subidas, de la propiedad {@code app.upload-dir} (por defecto {@code uploads})
     * @param archivoAlmacenamientoService guardado de archivos (disco local o S3 según configuración)
     * @param rmRevisionAsignadorService elige qué RM revisa cada certificado o formación
     */
    public ColaboradorPerfilService(UsuarioRepository usuarioRepository,
                                    ColaboradorHabilidadRepository colaboradorHabilidadRepository,
                                    HabilidadRepository habilidadRepository,
                                    CategoriaHabilidadRepository categoriaHabilidadRepository,
                                    EducacionRepository educacionRepository,
                                    CertificadoRepository certificadoRepository,
                                    ExperienciaProfesionalRepository experienciaProfesionalRepository,
                                    PasswordEncoder passwordEncoder,
                                    AuditoriaService auditoriaService,
                                    NotificacionService notificacionService,
                                    @Value("${app.upload-dir:uploads}") String uploadDir,
                                    ArchivoAlmacenamientoService archivoAlmacenamientoService,
                                    RmRevisionAsignadorService rmRevisionAsignadorService) {
        this.usuarioRepository = usuarioRepository;
        this.colaboradorHabilidadRepository = colaboradorHabilidadRepository;
        this.habilidadRepository = habilidadRepository;
        this.categoriaHabilidadRepository = categoriaHabilidadRepository;
        this.educacionRepository = educacionRepository;
        this.certificadoRepository = certificadoRepository;
        this.experienciaProfesionalRepository = experienciaProfesionalRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
        this.uploadDir = uploadDir;
        this.archivoAlmacenamientoService = archivoAlmacenamientoService;
        this.rmRevisionAsignadorService = rmRevisionAsignadorService;
    }

    /**
     * Lista las categorías de habilidades que están activas.
     *
     * @return categorías activas
     */
    public List<CategoriaHabilidad> listarCategoriasHabilidad() {
        return categoriaHabilidadRepository.findByActivaTrue();
    }

    /**
     * Lista las habilidades del colaborador que no fueron quitadas de su perfil.
     *
     * @param colaborador colaborador consultado
     * @return sus habilidades activas, con su nivel y estado de validación
     */
    public List<ColaboradorHabilidad> listarHabilidades(Usuario colaborador) {
        return colaboradorHabilidadRepository.findByColaboradorAndActivoTrue(colaborador);
    }


    /**
     * Arma un mapa {@code idHabilidad -> archivo del certificado más reciente}, para mostrar
     * el último certificado de cada habilidad en el perfil.
     *
     * @param colaborador colaborador consultado
     * @return mapa con el enlace al certificado más reciente de cada habilidad
     */
    public java.util.Map<Long, String> mapaCertificadosPorHabilidad(Usuario colaborador) {
        List<Certificado> certificados = certificadoRepository.findByColaboradorIdConDetalle(colaborador.getId());
        java.util.Map<Long, String> mapa = new java.util.HashMap<>();

        for (Certificado certificado : certificados) {
            Long habilidadId = certificado.getHabilidad().getId();
            //Como vienen ordenados del más reciente al más antiguo, la primera vez
            //que vemos una habilidad es su certificado más reciente, asi evitamos sobreescribirlo.
            if (!mapa.containsKey(habilidadId)) {
                mapa.put(habilidadId, certificado.getArchivoUrl());
            }
        }
        return mapa;
    }

    /**
     * Arma un mapa {@code idHabilidad -> motivo de rechazo}. Solo incluye una habilidad si su
     * certificado <b>más reciente</b> está {@code RECHAZADO} y tiene motivo; si ya subió uno
     * nuevo, el rechazo anterior deja de mostrarse.
     *
     * @param colaborador colaborador consultado
     * @return mapa de motivos de rechazo por habilidad
     */
    public java.util.Map<Long, String> mapaMotivosRechazoPorHabilidad(Usuario colaborador) {
        List<Certificado> certificados = certificadoRepository.findByColaboradorIdConDetalle(colaborador.getId());
        java.util.Map<Long, String> mapa = new java.util.HashMap<>();
        Set<Long> vistas = new HashSet<>();

        for (Certificado certificado : certificados) {
            Long habilidadId = certificado.getHabilidad().getId();
            //Vienen del más reciente al más antiguo: solo cuenta el primero de cada habilidad.
            if (!vistas.add(habilidadId)) continue;
            String motivo = certificado.getMotivoRechazo();
            if (certificado.getEstado() == EstadoCertificado.RECHAZADO && motivo != null && !motivo.isBlank()) {
                mapa.put(habilidadId, motivo);
            }
        }
        return mapa;
    }

    /**
     * Lista las habilidades activas del catálogo que el colaborador aún no tiene en su perfil
     * (para el selector de "agregar habilidad").
     *
     * @param colaborador colaborador consultado
     * @return habilidades que puede agregar
     */
    public List<Habilidad> listarHabilidadesDisponibles(Usuario colaborador) {
        List<ColaboradorHabilidad> misHabilidades = listarHabilidades(colaborador);

        // Armamos la lista de ids que el colaborador ya tiene,
        List<Long> idsYaAgregados = new ArrayList<>();
        for (ColaboradorHabilidad ch : misHabilidades) {
            idsYaAgregados.add(ch.getHabilidad().getId());
        }

        List<Habilidad> catalogoActivo = habilidadRepository.findByActivaTrue();
        List<Habilidad> disponibles = new ArrayList<>();
        for (Habilidad h : catalogoActivo) {
            //Verificamos si el id de habilidad pertenece las habilidades del colaborador
            if (!idsYaAgregados.contains(h.getId())) {
                disponibles.add(h);
            }
        }
        return disponibles;
    }

    /**
     * Lista los certificados que el colaborador ha presentado, incluidos los rechazados. Los
     * rechazados no se eliminan: se conservan para que el motivo y el historial sigan visibles.
     * Es una consulta de solo lectura.
     *
     * @param colaborador colaborador consultado
     * @return sus certificados, del más reciente al más antiguo
     */
    @Transactional(readOnly = true)
    public List<Certificado> listarCertificados(Usuario colaborador) {
        return certificadoRepository.findByColaboradorIdConDetalle(colaborador.getId());
    }


    /**
     * Calcula qué tan completo está el perfil. Se evalúan 4 criterios, y cada uno vale 25%:
     * <ol>
     *   <li>tiene foto de perfil,</li>
     *   <li>tiene texto en "Sobre mí",</li>
     *   <li>tiene al menos una habilidad {@code VALIDADA} por el RM,</li>
     *   <li>tiene al menos una formación académica {@code APROBADA} por el RM.</li>
     * </ol>
     * Que la habilidad y la formación estén <i>validadas</i> (no solo agregadas) es lo que las cuenta.
     *
     * @param colaborador colaborador evaluado
     * @return porcentaje de 0 a 100 (0, 25, 50, 75 o 100)
     */
    public int calcularPorcentajeCompletado(Usuario colaborador) {
        int total = 4;
        int listos = 0;
        if (colaborador.getFotoUrl() != null && !colaborador.getFotoUrl().isBlank()) listos++;
        if (colaborador.getDescripcion() != null && !colaborador.getDescripcion().isBlank()) listos++;
        if (tieneHabilidadValidada(colaborador)) listos++;
        if (tieneEducacionAprobada(colaborador)) listos++;
        return Math.round((listos * 100f) / total);
    }

    /**
     * Lista los mensajes de lo que le falta al colaborador para completar su perfil, según los
     * mismos 4 criterios de {@link #calcularPorcentajeCompletado}. Si ya agregó la habilidad o la
     * formación pero el RM no la validó, el mensaje le indica que espere la revisión.
     *
     * @param colaborador colaborador evaluado
     * @return mensajes pendientes; vacío si el perfil está completo
     */
    public List<String> listarPendientesCompletar(Usuario colaborador) {
        List<String> pendientes = new ArrayList<>();

        if (colaborador.getFotoUrl() == null || colaborador.getFotoUrl().isBlank()) {
            pendientes.add("Agrega una foto de perfil");
        }
        if (colaborador.getDescripcion() == null || colaborador.getDescripcion().isBlank()) {
            pendientes.add("Agrega una descripción en \"Sobre mí\"");
        }
        if (!tieneHabilidadValidada(colaborador)) {
            pendientes.add(listarHabilidades(colaborador).isEmpty() ? "Agrega una habilidad" : "Espera a que el RM valide alguna de tus habilidades");
        }
        if (!tieneEducacionAprobada(colaborador)) {
            pendientes.add(listarEducacion(colaborador).isEmpty() ? "Agrega tu formación académica" : "Espera a que el RM apruebe tu formación académica");
        }
        return pendientes;
    }

    /**
     * @param colaborador colaborador evaluado
     * @return {@code true} si tiene al menos una habilidad activa en estado {@code VALIDADA} por el RM
     */
    private boolean tieneHabilidadValidada(Usuario colaborador) {
        for (ColaboradorHabilidad ch : listarHabilidades(colaborador)) {
            if (ch.getEstadoValidacion() == EstadoValidacion.VALIDADA) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param colaborador colaborador evaluado
     * @return {@code true} si tiene al menos una formación académica activa en estado {@code APROBADO} por el RM
     */
    private boolean tieneEducacionAprobada(Usuario colaborador) {
        for (Educacion edu : listarEducacion(colaborador)) {
            if (edu.getEstado() == EstadoCertificado.APROBADO) {
                return true;
            }
        }
        return false;
    }




    // ============================================================
    // SOBRE MÍ
    // ============================================================

    /**
     * Actualiza el texto "Sobre mí". Se recorta el texto, el máximo es de 500 caracteres y un
     * texto vacío se guarda como {@code null}. Deja registro en auditoría.
     *
     * @param colaborador colaborador que edita su perfil
     * @param descripcion nuevo texto
     * @throws IllegalArgumentException si supera los 500 caracteres
     */
    @Transactional
    public void actualizarSobreMi(Usuario colaborador, String descripcion) {

        String limpio = descripcion == null ? "" : descripcion.trim();

        if (limpio.length() > 500) {
            throw new IllegalArgumentException("La descripción no puede superar los 500 caracteres.");
        }
        colaborador.setDescripcion(limpio.isEmpty() ? null : limpio);
        usuarioRepository.save(colaborador);
        auditoriaService.registrar(colaborador, "ACTUALIZAR_PERFIL", "USUARIO", colaborador.getId(),
                "Actualizó la sección \"Sobre mí\" de su perfil.");
    }

    // ============================================================
    // TELÉFONO
    // ============================================================
    /**
     * Valida el teléfono con la política definida en {@code PoliticaTelefono}. También la usa el
     * pre-registro, para que el número que guarda sea uno que el perfil acepte después.
     *
     * @param telefono teléfono ingresado
     * @return el teléfono sin espacios sobrantes
     * @throws IllegalArgumentException si está vacío o no cumple el formato
     */
    public String validarTelefono(String telefono) {
        if (telefono == null || telefono.isBlank()) {
            throw new IllegalArgumentException("Indica tu número de teléfono.");
        }
        String limpio = telefono.trim();

        if (!limpio.matches(PoliticaTelefono.REGEX)) {
            throw new IllegalArgumentException(PoliticaTelefono.MENSAJE);
        }
        return limpio;
    }

    /**
     * Actualiza el teléfono del colaborador, tras validarlo con {@link #validarTelefono}, y lo
     * registra en auditoría. El correo, en cambio, no se edita desde el perfil: solo lo cambia el Administrador.
     *
     * @param colaborador colaborador que edita su perfil
     * @param telefono nuevo teléfono
     * @throws IllegalArgumentException si el teléfono no es válido
     */
    @Transactional
    public void actualizarTelefono(Usuario colaborador, String telefono) {
        String limpio = validarTelefono(telefono);

        colaborador.setTelefono(limpio);
        usuarioRepository.save(colaborador);
        auditoriaService.registrar(colaborador, "ACTUALIZAR_PERFIL", "USUARIO", colaborador.getId(), "Actualizó su número de teléfono.");
    }

    // ============================================================
    // FOTO DE PERFIL
    // ============================================================

    /**
     * Cambia la foto de perfil. Reglas: debe existir, ser JPG o PNG y pesar como máximo 2 MB.
     * El archivo se guarda con nombre único ({@code usuario-<id>-<uuid>.<ext>}) en la carpeta
     * {@code perfil}, en disco local o en S3 según la configuración, y se guarda su URL en el usuario.
     * El nombre único evita que dos usuarios con el mismo nombre de archivo se reemplacen.
     *
     * @param colaborador colaborador que cambia su foto
     * @param foto imagen nueva
     * @throws IllegalArgumentException si falta, no es JPG/PNG o supera 2 MB
     */
    @Transactional
    public void actualizarFoto(Usuario colaborador, MultipartFile foto) {
        if (foto == null || foto.isEmpty()) {
            throw new IllegalArgumentException("Selecciona una imagen para subir.");
        }
        if (!TIPOS_IMAGEN_PERMITIDOS.contains(foto.getContentType())) {
            throw new IllegalArgumentException("Solo se aceptan imágenes JPG o PNG.");
        }
        if (foto.getSize() > TAMANO_MAXIMO_FOTO_BYTES) {
            throw new IllegalArgumentException("La imagen supera el máximo de 2MB.");
        }

        //Determinamos la extensión del archivo según el tipo de imagen
        String extension = "image/png".equals(foto.getContentType()) ? ".png" : ".jpg";

        //Generamos un nombre único e irrepetible para el archivo con la estructura de: usuario-idColaborador-codigoAleatorio.formato
        //Esto lo hacemos para evitar que los archivos se reemplacen en caso de que dos usuarios los suban con el mismo nombre
        String nombreArchivo = "usuario-" + colaborador.getId() + "-" + UUID.randomUUID() + extension;

        //Guardamos el archivo (en disco local o en S3, según la configuración) y obtenemos su URL
        String fotoUrl = archivoAlmacenamientoService.guardar(foto, "perfil", nombreArchivo);
        colaborador.setFotoUrl(fotoUrl);

        //Guardamos los cambios del usuario en la base de datos.
        usuarioRepository.save(colaborador);

        // Registramos en el sistema de auditoría que el usuario actualizó su foto de perfil
        auditoriaService.registrar(colaborador, "ACTUALIZAR_PERFIL", "USUARIO", colaborador.getId(),
                "Actualizó su foto de perfil.");
    }

    // ============================================================
    // CV (hoja de vida) — lo revisa el Admin, que luego llena la experiencia
    // profesional del colaborador basado en lo que diga el documento.
    // ============================================================

    /**
     * Valida el archivo del CV: debe existir, ser PDF y pesar como máximo 5 MB, y además se
     * comprueba que el contenido real del archivo sea un PDF. También la usa el pre-registro,
     * antes de crear la cuenta.
     *
     * @param cv archivo del CV
     * @throws IllegalArgumentException si no cumple alguna regla
     */
    public void validarCv(MultipartFile cv) {
        if (cv == null || cv.isEmpty()) {
            throw new IllegalArgumentException("Selecciona un archivo PDF para subir.");
        }
        if (!TIPOS_CV_PERMITIDOS.contains(cv.getContentType())) {
            throw new IllegalArgumentException("El CV debe estar en formato PDF.");
        }
        if (cv.getSize() > TAMANO_MAXIMO_CV_BYTES) {
            throw new IllegalArgumentException("El archivo supera el máximo de 5MB.");
        }
        archivoAlmacenamientoService.validarContenidoReal(cv);
    }


    /**
     * Sube o reemplaza el CV del colaborador. Lo revisa el Administrador, quien luego completa
     * la experiencia profesional según lo que diga el documento.
     * <p>
     * Tras validar el archivo: lo guarda, deja el CV en estado {@code PENDIENTE} con su fecha de
     * subida y borra el motivo de rechazo anterior. Si el registro del colaborador estaba
     * {@code RECHAZADO}, vuelve a {@code PENDIENTE} para una nueva revisión. Registra auditoría
     * y notifica a todos los administradores.
     *
     * @param colaborador colaborador que sube su CV
     * @param cv archivo PDF
     * @throws IllegalArgumentException si el archivo no es válido
     * @see #validarCv
     */
    @Transactional
    public void actualizarCv(Usuario colaborador, MultipartFile cv) {
        validarCv(cv);

        String nombreArchivo = "cv-" + colaborador.getId() + "-" + UUID.randomUUID() + ".pdf";
        String cvUrl = archivoAlmacenamientoService.guardar(cv, "cv", nombreArchivo);

        colaborador.setCvUrl(cvUrl);
        colaborador.setCvEstado(EstadoCv.PENDIENTE);
        colaborador.setCvFechaSubida(LocalDateTime.now());
        colaborador.setMotivoRechazo(null);
        // Un registro rechazado vuelve a revisión al subir un CV corregido.
        if (colaborador.getRegistroEstado() == EstadoRegistro.RECHAZADO) {
            colaborador.setRegistroEstado(EstadoRegistro.PENDIENTE);
        }
        usuarioRepository.save(colaborador);

        auditoriaService.registrar(colaborador, "SUBIR_CV", "USUARIO", colaborador.getId(),
                "Subió su CV para revisión del Administrador.");

        notificacionService.crearParaTodosLosAdmins("CV_PENDIENTE", CategoriaNotificacion.SISTEMA,
                "CV pendiente de revisión",
                colaborador.getNombre() + " " + colaborador.getApellido() + " subió su CV.",
                "USUARIO", colaborador.getId());
    }


    /**
     * Quita la foto de perfil. Solo borra la referencia guardada en el usuario; no elimina el
     * archivo del almacenamiento. Deja registro en auditoría.
     *
     * @param colaborador colaborador que quita su foto
     * @throws IllegalArgumentException si no tiene foto
     */
    @Transactional
    public void eliminarFoto(Usuario colaborador) {
        if (colaborador.getFotoUrl() == null || colaborador.getFotoUrl().isBlank()) {
            throw new IllegalArgumentException("No tienes una foto de perfil para eliminar.");
        }
        colaborador.setFotoUrl(null);
        usuarioRepository.save(colaborador);
        auditoriaService.registrar(colaborador, "ACTUALIZAR_PERFIL", "USUARIO", colaborador.getId(),
                "Eliminó su foto de perfil.");
    }

    // ============================================================
    // CAMBIO DE CONTRASEÑA
    // ============================================================

    /**
     * Cambia la contraseña del colaborador desde Ajustes de Cuenta.
     * <p>
     * Orden de validaciones: la contraseña actual coincide con la guardada (comparación del
     * {@code PasswordEncoder}), la nueva cumple la política de {@link #validarPassword} y la
     * confirmación es igual a la nueva. La contraseña nueva se guarda codificada, nunca en texto
     * plano. Deja registro en auditoría.
     *
     * @param colaborador colaborador que cambia su contraseña
     * @param actual contraseña actual
     * @param nueva contraseña nueva
     * @param confirmar repetición de la contraseña nueva
     * @throws IllegalArgumentException si alguna validación falla
     */
    @Transactional
    public void cambiarPassword(Usuario colaborador, String actual, String nueva, String confirmar) {
        if (actual == null || !passwordEncoder.matches(actual, colaborador.getPasswordHash())) {
            throw new IllegalArgumentException("La contraseña actual no es correcta.");
        }
        validarPassword(nueva);
        if (!nueva.equals(confirmar)) {
            throw new IllegalArgumentException("Las contraseñas nuevas no coinciden.");
        }
        colaborador.setPasswordHash(passwordEncoder.encode(nueva));
        usuarioRepository.save(colaborador);
        auditoriaService.registrar(colaborador, "CAMBIO_PASSWORD", "USUARIO", colaborador.getId(),
                "Cambió su contraseña desde Ajustes de Cuenta.");
    }

    /**
     * Aplica la política de contraseñas: al menos 8 caracteres, una mayúscula, un número y un
     * símbolo (carácter que no es letra, dígito ni espacio).
     *
     * @param password contraseña a validar
     * @throws IllegalArgumentException con el requisito que falta
     */
    private void validarPassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos 8 caracteres.");
        }

        boolean tieneMayuscula = false;
        boolean tieneNumero = false;
        boolean tieneSimbolo = false;

        for (int i = 0; i < password.length(); i++) {
            char caracter = password.charAt(i);
            if (Character.isUpperCase(caracter)) {
                tieneMayuscula = true;
            } else if (Character.isDigit(caracter)) {
                tieneNumero = true;
            } else if (!Character.isLetter(caracter) && !Character.isWhitespace(caracter)) {
                tieneSimbolo = true;
            }
        }

        if (!tieneMayuscula) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos una letra mayúscula.");
        }
        if (!tieneNumero) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos un número.");
        }
        if (!tieneSimbolo) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos un símbolo (por ejemplo: !@#$%).");
        }
    }

    // ============================================================
    // HABILIDADES DEL COLABORADOR
    // ============================================================

    /**
     * Agrega una habilidad al perfil, con su nivel de dominio y un certificado obligatorio.
     * <p>
     * La habilidad puede ser del catálogo ({@code habilidadId}) o nueva
     * ({@code nuevaHabilidadNombre} + categoría, ver {@link #crearOReutilizarHabilidad}). Si el
     * colaborador ya la tenía activa, se rechaza. Si la había quitado antes, se <b>reactiva la
     * misma fila</b> en vez de crear otra.
     * <p>
     * La habilidad queda {@code PENDIENTE} de validación y se crea un certificado también
     * {@code PENDIENTE}, asignado a un RM elegido por {@code RmRevisionAsignadorService}. Deja
     * registro en auditoría.
     *
     * @param colaborador colaborador que agrega la habilidad
     * @param habilidadId id de una habilidad del catálogo (o {@code null} si propone una nueva)
     * @param nuevaHabilidadNombre nombre de la habilidad nueva
     * @param categoriaId categoría de la habilidad nueva
     * @param nivel nivel de dominio (obligatorio)
     * @param certificado certificado que la respalda (obligatorio)
     * @throws IllegalArgumentException si falta el nivel o el certificado, la habilidad no existe o ya la tiene
     */
    @Transactional
    public void agregarHabilidad(Usuario colaborador, Long habilidadId, String nuevaHabilidadNombre,
                                 Long categoriaId, NivelDominio nivel, MultipartFile certificado) {
        if (nivel == null) {
            throw new IllegalArgumentException("Selecciona un nivel.");
        }

        //Si el colaborador eligió una habilidad del catálogo, la usamos.
        //Si no, significa que quiere crear una habilidad nueva ya que no la encontró en la lista.
        Habilidad habilidad;
        if (habilidadId != null) {
            habilidad = habilidadRepository.findById(habilidadId)
                    .filter(Habilidad::isActiva)
                    .orElseThrow(() -> new IllegalArgumentException("La habilidad seleccionada no existe"));
        } else {
            habilidad = crearOReutilizarHabilidad(nuevaHabilidadNombre, categoriaId);
        }

        //Creamos el ID compuesto que identifica la relación entre el colaborador y la habilidad
        ColaboradorHabilidadId id = new ColaboradorHabilidadId(colaborador.getId(), habilidad.getId());

        //Buscamos si ya existe un registro con ese ID compuesto
        Optional<ColaboradorHabilidad> existente = colaboradorHabilidadRepository.findById(id);

        if (existente.isPresent() && existente.get().isActivo()) {
            throw new IllegalArgumentException("Ya tienes esa habilidad agregada.");
        }


        String archivoUrl = guardarCertificado(certificado, "certificados",
                "certificado-" + colaborador.getId());

        //Evaluamos en caso de que la habilidad ya haya sido registrada anteriormente pero fue borrada
        ColaboradorHabilidad ch;
        if (existente.isPresent()) {
            //Reactivamos la misma fila en lugar de crear una nueva
            ch = existente.get();
            ch.setNivelDominio(nivel);
            ch.setActivo(true);
        } else {
            ch = new ColaboradorHabilidad();
            ch.setId(id);
            ch.setColaborador(colaborador);
            ch.setHabilidad(habilidad);
            ch.setNivelDominio(nivel);
        }

        ch.setEstadoValidacion(EstadoValidacion.PENDIENTE);
        colaboradorHabilidadRepository.save(ch);

        Certificado certificadoEntidad = new Certificado();
        certificadoEntidad.setColaborador(colaborador);
        certificadoEntidad.setHabilidad(habilidad);
        certificadoEntidad.setArchivoUrl(archivoUrl);
        certificadoEntidad.setEstado(EstadoCertificado.PENDIENTE);
        certificadoEntidad.setRmResponsable(rmRevisionAsignadorService.elegirRm());
        certificadoRepository.save(certificadoEntidad);

        auditoriaService.registrar(colaborador, "AGREGAR_HABILIDAD", "COLABORADOR_HABILIDAD", habilidad.getId(),
                "Agregó la habilidad \"" + habilidad.getNombre() + "\" (" + nivel
                        + ") a su perfil y envió un certificado a revisión del RM.");
    }

    /**
     * Crea una habilidad nueva en el catálogo cuando el colaborador no la encuentra. A pesar del
     * nombre del método, <b>no reutiliza</b> habilidades existentes: si ya existe una con ese
     * nombre (sin distinguir mayúsculas), lanza un error indicando que la busque en el catálogo.
     * <p>
     * Validaciones: nombre no vacío y de máximo 100 caracteres (límite de la columna), categoría
     * obligatoria y activa.
     *
     * @param nombre nombre propuesto
     * @param categoriaId categoría de la habilidad
     * @return la habilidad nueva, ya guardada y activa
     * @throws IllegalArgumentException si falta algún dato o el nombre ya existe
     */
    private Habilidad crearOReutilizarHabilidad(String nombre, Long categoriaId) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("Escribe el nombre de la nueva habilidad.");
        }
        if (categoriaId == null) {
            throw new IllegalArgumentException("Selecciona una categoría para la nueva habilidad.");
        }

        String nombreLimpio = nombre.trim();
        // La columna es VARCHAR(100): sin este control, un nombre largo terminaba en un error 500.
        if (nombreLimpio.length() > 100) {
            throw new IllegalArgumentException("El nombre de la habilidad no puede superar los 100 caracteres.");
        }

        //El nombre es único en todo el catalogo. Si ya existe, se lo decimos al colaborador para que la busque en el
        //selector del catálogo.
        Optional<Habilidad> existente = habilidadRepository.findFirstByNombreIgnoreCaseOrderByIdAsc(nombreLimpio);

        if (existente.isPresent()) {
            throw new IllegalArgumentException(
                    "La habilidad \"" + existente.get().getNombre() + "\" ya existe en nuestro catálogo. "
                            + "Búscala en \"Seleccionar del catálogo\" para agregarla, o si ya la tienes en tu "
                            + "perfil, usa el botón \"Certificar un nivel superior\" junto a esa habilidad.");
        }

        CategoriaHabilidad categoria = categoriaHabilidadRepository.findById(categoriaId)
                .filter(CategoriaHabilidad::isActiva)
                .orElseThrow(() -> new IllegalArgumentException("La categoría seleccionada no existe"));

        Habilidad nueva = new Habilidad();
        nueva.setNombre(nombreLimpio);
        nueva.setCategoria(categoria);
        nueva.setActiva(true);
        return habilidadRepository.save(nueva);
    }


    /**
     * Valida un certificado: debe existir, ser PDF, JPG o PNG, pesar como máximo 10 MB y su
     * contenido real debe coincidir con el tipo declarado. También la usa el pre-registro.
     *
     * @param archivo certificado a validar
     * @throws IllegalArgumentException si no cumple alguna regla
     */
    public void validarArchivoCertificado(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Debes adjuntar un certificado.");
        }
        if (!TIPOS_CERTIFICADO_PERMITIDOS.contains(archivo.getContentType())) {
            throw new IllegalArgumentException("El certificado debe estar en formato PDF, JPG o PNG.");
        }
        if (archivo.getSize() > TAMANO_MAXIMO_CERTIFICADO_BYTES) {
            throw new IllegalArgumentException("El certificado supera el máximo de 10MB.");
        }
        archivoAlmacenamientoService.validarContenidoReal(archivo);
    }

    /**
     * Lista todas las habilidades activas del catálogo. Se usa en el pre-registro, donde el
     * colaborador todavía no tiene habilidades.
     *
     * @return habilidades activas
     */
    public List<Habilidad> listarHabilidadesActivas() {
        return habilidadRepository.findByActivaTrue();
    }

    /**
     * Valida (existencia, tipo y tamaño) y guarda un certificado con nombre único
     * ({@code <prefijo>-<uuid>.<ext>}) en la carpeta indicada.
     *
     * @param archivo certificado a guardar
     * @param carpeta carpeta de almacenamiento (por ejemplo {@code certificados})
     * @param prefijoArchivo prefijo del nombre del archivo
     * @return la ruta o URL donde quedó guardado
     * @throws IllegalArgumentException si falta, no es PDF/JPG/PNG o supera 10 MB
     */
    private String guardarCertificado(MultipartFile archivo, String carpeta, String prefijoArchivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Debes adjuntar un certificado.");
        }
        if (!TIPOS_CERTIFICADO_PERMITIDOS.contains(archivo.getContentType())) {
            throw new IllegalArgumentException("El certificado debe estar en formato PDF, JPG o PNG.");
        }
        if (archivo.getSize() > TAMANO_MAXIMO_CERTIFICADO_BYTES) {
            throw new IllegalArgumentException("El certificado supera el máximo de 10MB.");
        }

        String extension;
        if ("application/pdf".equals(archivo.getContentType())) {
            extension = ".pdf";
        } else if ("image/png".equals(archivo.getContentType())) {
            extension = ".png";
        } else {
            extension = ".jpg";
        }
        String nombreArchivo = prefijoArchivo + "-" + UUID.randomUUID() + extension;

        return archivoAlmacenamientoService.guardar(archivo, carpeta, nombreArchivo);
    }



    /**
     * Quita una habilidad del perfil. Es un borrado lógico: la habilidad pasa a inactiva, no se
     * borra, así que sus certificados se conservan y se puede reactivar al agregarla de nuevo. Si
     * el colaborador no tiene esa habilidad, no hace nada. Deja registro en auditoría.
     *
     * @param colaborador colaborador dueño del perfil
     * @param habilidadId id de la habilidad a quitar
     */
    @Transactional
    public void eliminarHabilidad(Usuario colaborador, Long habilidadId) {
        ColaboradorHabilidadId id = new ColaboradorHabilidadId(colaborador.getId(), habilidadId);

        Optional<ColaboradorHabilidad> ch = colaboradorHabilidadRepository.findById(id);

        if (ch.isPresent()) {

            ColaboradorHabilidad habilidadColaborador = ch.get();
            //La habilidad cambia a inactivo
            habilidadColaborador.setActivo(false);
            colaboradorHabilidadRepository.save(habilidadColaborador);

            auditoriaService.registrar(colaborador, "ELIMINAR_HABILIDAD", "COLABORADOR_HABILIDAD", habilidadId,
                    "Quitó la habilidad \"" + habilidadColaborador.getHabilidad().getNombre() + "\" de su perfil.");
        }
    }

    // ============================================================
    // CERTIFICADOS DE HABILIDADES
    // ============================================================


    /**
     * Sube un certificado nuevo para una habilidad que el colaborador ya tiene.
     * <p>
     * Validaciones: se eligió habilidad, el archivo existe y es PDF/JPG/PNG de máximo 10 MB, la
     * habilidad está activa en su perfil y no tiene ya otro certificado {@code PENDIENTE} para ella.
     * <p>
     * El certificado se crea {@code PENDIENTE} con un RM responsable. Efecto sobre la habilidad:
     * <ul>
     *   <li>si el colaborador indica un <b>nuevo nivel</b>, se actualiza y la habilidad vuelve a
     *       {@code PENDIENTE} de validación;</li>
     *   <li>si no cambia el nivel y la habilidad no estaba {@code VALIDADA}, se mantiene
     *       {@code PENDIENTE};</li>
     *   <li>si no cambia el nivel y ya estaba {@code VALIDADA}, se conserva así.</li>
     * </ul>
     * Registra auditoría y notifica al RM responsable.
     *
     * @param colaborador colaborador que sube el certificado
     * @param habilidadId habilidad que se certifica
     * @param nuevoNivel nivel que se quiere validar (opcional)
     * @param archivo archivo del certificado
     * @return el certificado guardado
     * @throws IllegalArgumentException si no se cumple alguna validación
     */
    @Transactional
    public Certificado subirCertificado(Usuario colaborador, Long habilidadId, NivelDominio nuevoNivel,
                                        MultipartFile archivo) {
        if (habilidadId == null) {
            throw new IllegalArgumentException("Selecciona la habilidad que deseas certificar.");
        }
        if (archivo == null || archivo.isEmpty()) {
            throw new IllegalArgumentException("Selecciona un certificado para subir.");
        }
        if (!TIPOS_CERTIFICADO_PERMITIDOS.contains(archivo.getContentType())) {
            throw new IllegalArgumentException("El certificado debe estar en formato PDF, JPG o PNG.");
        }
        if (archivo.getSize() > TAMANO_MAXIMO_CERTIFICADO_BYTES) {
            throw new IllegalArgumentException("El certificado supera el máximo de 10MB.");
        }

        Habilidad habilidad = habilidadRepository.findById(habilidadId)
                .orElseThrow(() -> new IllegalArgumentException("La habilidad seleccionada no existe."));
        ColaboradorHabilidad perfilHabilidad = colaboradorHabilidadRepository
                .findByColaboradorAndHabilidad(colaborador, habilidad)
                .filter(ColaboradorHabilidad::isActivo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Solo puedes certificar una habilidad activa de tu perfil."));

        if (certificadoRepository.countByColaboradorAndHabilidadAndEstado(
                colaborador, habilidad, EstadoCertificado.PENDIENTE) > 0) {
            throw new IllegalArgumentException(
                    "Ya tienes un certificado pendiente de revisión para esta habilidad.");
        }

        String extension = switch (archivo.getContentType()) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            default -> ".jpg";
        };


        String nombreArchivo = "certificado-" + colaborador.getId() + "-"
                + UUID.randomUUID() + extension;

        String archivoUrl = archivoAlmacenamientoService.guardar(archivo, "certificados", nombreArchivo);

        Certificado certificado = new Certificado();
        certificado.setColaborador(colaborador);
        certificado.setHabilidad(habilidad);
        certificado.setArchivoUrl(archivoUrl);

        certificado.setEstado(EstadoCertificado.PENDIENTE);
        certificado.setRmResponsable(rmRevisionAsignadorService.elegirRm());
        certificado = certificadoRepository.save(certificado);

        //Si el colaborador cambia su nivel de dominio, la habilidad vuelve a quedar pendiente de validación.
        if (nuevoNivel != null) {
            perfilHabilidad.setNivelDominio(nuevoNivel);
            perfilHabilidad.setEstadoValidacion(EstadoValidacion.PENDIENTE);
            colaboradorHabilidadRepository.save(perfilHabilidad);

            //Si no cambia el nivel y la habilidad todavía no está validada, la mantenemos como pendiente de validación.
        } else if (perfilHabilidad.getEstadoValidacion() != EstadoValidacion.VALIDADA) {
            perfilHabilidad.setEstadoValidacion(EstadoValidacion.PENDIENTE);
            colaboradorHabilidadRepository.save(perfilHabilidad);
        }

        auditoriaService.registrar(colaborador, "SUBIR_CERTIFICADO", "CERTIFICADO",
                certificado.getId(), "Subió un certificado para la habilidad \""
                        + habilidad.getNombre() + "\".");

        notificacionService.crearParaRm(certificado.getRmResponsable(), "CERTIFICADO_PENDIENTE", CategoriaNotificacion.HABILIDAD,
                "Certificado pendiente de revisión",
                colaborador.getNombre() + " " + colaborador.getApellido()
                        + " subió un certificado para \"" + habilidad.getNombre() + "\".",
                "CERTIFICADO", certificado.getId());
        return certificado;
    }

    // ============================================================
    // EXPERIENCIA PROFESIONAL
    // ============================================================
    /**
     * Lista la experiencia profesional activa del colaborador, de la más reciente a la más antigua
     * según su fecha de inicio.
     *
     * @param colaborador colaborador consultado
     * @return su experiencia profesional
     */
    public List<ExperienciaProfesional> listarExperienciaProfesional(Usuario colaborador) {
        return experienciaProfesionalRepository.findByColaboradorAndActivoTrueOrderByFechaInicioDesc(colaborador);
    }

    /**
     * Valida una experiencia profesional. Es la misma regla del formulario del pre-registro.
     * Cargo (máx. 100), empresa (máx. 150) y descripción (máx. 500) son obligatorios; la fecha de
     * inicio es obligatoria y no puede ser futura; si no es el trabajo actual, la fecha de fin
     * es obligatoria.
     *
     * @param exp experiencia a validar
     * @throws IllegalArgumentException con el primer problema encontrado
     */
    private void validarExperiencia(ExperienciaProfesional exp) {
        if (exp.getCargo() == null || exp.getCargo().isBlank()) {
            throw new IllegalArgumentException("El cargo es obligatorio.");
        }
        if (exp.getCargo().length() > 100) {
            throw new IllegalArgumentException("El cargo no puede superar los 100 caracteres.");
        }
        if (exp.getEmpresa() == null || exp.getEmpresa().isBlank()) {
            throw new IllegalArgumentException("La empresa es obligatoria.");
        }
        if (exp.getEmpresa().length() > 150) {
            throw new IllegalArgumentException("La empresa no puede superar los 150 caracteres.");
        }
        if (exp.getDescripcion() == null || exp.getDescripcion().isBlank()) {
            throw new IllegalArgumentException("Describe brevemente las funciones del cargo.");
        }
        if (exp.getDescripcion().length() > 500) {
            throw new IllegalArgumentException("La descripción no puede superar los 500 caracteres.");
        }
        if (exp.getFechaInicio() == null) {
            throw new IllegalArgumentException("La fecha de inicio es obligatoria.");
        }
        if (exp.getFechaInicio().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de inicio no puede ser futura.");
        }
        if (!exp.isActual() && exp.getFechaFin() == null) {
            throw new IllegalArgumentException("Indica la fecha de fin o marca que trabaja ahí actualmente.");
        }
    }

    /**
     * Agrega una experiencia profesional al colaborador. Valida los campos y, si está marcada
     * como trabajo actual, borra la fecha de fin. Además exige que la fecha de fin no sea
     * anterior a la de inicio. Deja registro en auditoría.
     *
     * @param colaborador colaborador dueño de la experiencia
     * @param exp experiencia a guardar
     * @throws IllegalArgumentException si no cumple las validaciones
     */
    @Transactional
    public void agregarExperiencia(Usuario colaborador, ExperienciaProfesional exp) {
        validarExperiencia(exp);
        exp.setColaborador(colaborador);

        // Si dice que es actual, limpiamos la fecha de fin por seguridad
        if (exp.isActual()) {
            exp.setFechaFin(null);
        }
        
        if (exp.getFechaInicio() != null && exp.getFechaFin() != null) {
            if (exp.getFechaFin().isBefore(exp.getFechaInicio())) {
                throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la fecha de inicio.");
            }
        }
        
        experienciaProfesionalRepository.save(exp);
        
        auditoriaService.registrar(colaborador, "AGREGAR", "EXPERIENCIA", exp.getId(),
                "Agregó experiencia profesional en: " + exp.getEmpresa());
    }

    /**
     * Elimina una experiencia profesional haciendo que el propio colaborador sea quien actúa.
     *
     * @param colaborador colaborador dueño de la experiencia
     * @param id id de la experiencia
     * @see #eliminarExperiencia(Usuario, Long, Usuario)
     */
    @Transactional
    public void eliminarExperiencia(Usuario colaborador, Long id) {
        eliminarExperiencia(colaborador, id, colaborador);
    }

    /**
     * Elimina (borrado lógico) una experiencia profesional. Solo lo hace si la experiencia
     * pertenece realmente al colaborador indicado; si no existe o es de otro, no hace nada. La
     * auditoría se registra a nombre del {@code actor}.
     *
     * @param colaborador dueño de la experiencia
     * @param id id de la experiencia
     * @param actor quien la elimina: el propio colaborador o el Administrador al revisar su registro
     */
    @Transactional
    public void eliminarExperiencia(Usuario colaborador, Long id, Usuario actor) {
        experienciaProfesionalRepository.findById(id).ifPresent(exp -> {
            // Verificamos que esta experiencia pertenezca realmente al colaborador
            if (exp.getColaborador().getId().equals(colaborador.getId())) {
                exp.setActivo(false);
                experienciaProfesionalRepository.save(exp);

                auditoriaService.registrar(actor, "ELIMINAR", "EXPERIENCIA", id,
                        "Eliminó experiencia profesional en: " + exp.getEmpresa());
            }
        });
    }

    // ============================================================
    // EDUCACIÓN
    // ============================================================

    /**
     * Lista la formación académica activa del colaborador (no eliminada), incluida la que está
     * pendiente de aprobación.
     *
     * @param colaborador colaborador consultado
     * @return su formación académica
     */
    public List<Educacion> listarEducacion(Usuario colaborador) {
        return educacionRepository.findByColaboradorAndActivoTrue(colaborador);
    }

    /**
     * Agrega una formación académica y avisa al RM. Equivale a llamar a
     * {@link #agregarEducacion(Usuario, String, String, String, String, MultipartFile, boolean)}
     * con {@code notificarRm = true}.
     *
     * @param colaborador colaborador dueño de la formación
     * @param institucion institución educativa
     * @param titulo título o carrera
     * @param fechaInicioTexto fecha de inicio como texto
     * @param fechaFinTexto fecha de fin como texto
     * @param certificado certificado que respalda el estudio
     */
    @Transactional
    public void agregarEducacion(Usuario colaborador, String institucion, String titulo,
                                 String fechaInicioTexto, String fechaFinTexto,
                                 MultipartFile certificado) {
        agregarEducacion(colaborador, institucion, titulo, fechaInicioTexto, fechaFinTexto, certificado, true);
    }

    /**
     * Agrega una formación académica al perfil.
     * <p>
     * Validaciones: institución y título obligatorios (máx. 150 caracteres cada uno, por el
     * tamaño de la columna), ambas fechas obligatorias y con formato válido, fin no anterior a
     * inicio y fin no futura, y certificado obligatorio. Queda pendiente de aprobación, con un RM
     * responsable asignado. Se registra auditoría.
     * <p>
     * {@code notificarRm} es {@code false} en el pre-registro, porque el RM todavía no ve al
     * colaborador (su bandeja excluye registros sin aprobar); se le avisa después, cuando el
     * Administrador aprueba el registro.
     *
     * @param colaborador colaborador dueño de la formación
     * @param institucion institución educativa
     * @param titulo título o carrera
     * @param fechaInicioTexto fecha de inicio ({@code yyyy-MM-dd})
     * @param fechaFinTexto fecha de fin ({@code yyyy-MM-dd})
     * @param certificado certificado que respalda el estudio (obligatorio)
     * @param notificarRm si se debe notificar al RM responsable
     * @throws IllegalArgumentException si no se cumple alguna validación
     */
    @Transactional
    public void agregarEducacion(Usuario colaborador, String institucion, String titulo,
                                 String fechaInicioTexto, String fechaFinTexto,
                                 MultipartFile certificado, boolean notificarRm) {
        if (institucion == null || institucion.isBlank()) {
            throw new IllegalArgumentException("Indica la institución.");
        }
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("Indica el título o carrera.");
        }
        // Las columnas son VARCHAR(150): sin este control, un texto largo terminaba en un error 500.
        if (institucion.trim().length() > 150) {
            throw new IllegalArgumentException("La institución no puede superar los 150 caracteres.");
        }
        if (titulo.trim().length() > 150) {
            throw new IllegalArgumentException("El título o carrera no puede superar los 150 caracteres.");
        }

        LocalDate fechaInicio = parseFechaObligatoria(fechaInicioTexto, "la fecha de inicio");
        LocalDate fechaFin = parseFechaObligatoria(fechaFinTexto, "la fecha de fin");

        if (fechaFin.isBefore(fechaInicio)) {
            throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la fecha de inicio.");
        }
        if (fechaFin.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de fin no puede ser una fecha futura.");
        }

        String archivoUrl = guardarCertificado(certificado, "certificados-educacion",
                "educacion-" + colaborador.getId());

        Educacion educacion = new Educacion();
        educacion.setColaborador(colaborador);
        educacion.setInstitucion(institucion.trim());
        educacion.setTitulo(titulo.trim());
        educacion.setFechaInicio(fechaInicio);
        educacion.setFechaFin(fechaFin);
        educacion.setActual(false);
        educacion.setArchivoUrl(archivoUrl);
        educacion.setRmResponsable(rmRevisionAsignadorService.elegirRm());
        educacionRepository.save(educacion);

        auditoriaService.registrar(colaborador, "AGREGAR_EDUCACION", "EDUCACION", educacion.getId(),
                "Agregó la formación académica \"" + titulo.trim() + "\" (" + institucion.trim()
                        + ") a su perfil y adjuntó un certificado.");

        if (notificarRm) {
            notificarRmEducacionPendiente(educacion);
        }
    }

    /**
     * Notifica al RM responsable que hay una formación académica pendiente de revisión.
     *
     * @param educacion formación recién agregada
     */
    private void notificarRmEducacionPendiente(Educacion educacion) {
        Usuario colaborador = educacion.getColaborador();
        notificacionService.crearParaRm(educacion.getRmResponsable(), "EDUCACION_PENDIENTE", CategoriaNotificacion.HABILIDAD,
                "Formación académica pendiente de revisión",
                colaborador.getNombre() + " " + colaborador.getApellido()
                        + " agregó \"" + educacion.getTitulo() + "\" (" + educacion.getInstitucion() + ") a su perfil.",
                "EDUCACION", educacion.getId());
    }

    /**
     * Se llama cuando el Administrador aprueba el pre-registro. Desde ese momento el RM ya ve al
     * colaborador, así que se le notifica cada formación que quedó {@code PENDIENTE}.
     *
     * @param colaborador colaborador cuyo registro fue aprobado
     */
    @Transactional
    public void avisarAlRmDelRegistroAprobado(Usuario colaborador) {
        listarEducacion(colaborador).stream()
                .filter(e -> e.getEstado() == EstadoCertificado.PENDIENTE)
                .forEach(this::notificarRmEducacionPendiente);
    }

    /**
     * Convierte el texto de un campo de fecha del formulario en {@link LocalDate}.
     *
     * @param valor texto en formato {@code yyyy-MM-dd}
     * @param nombreCampo nombre del campo, usado en el mensaje de error
     * @return la fecha
     * @throws IllegalArgumentException si viene vacío o con formato inválido
     */
    private LocalDate parseFechaObligatoria(String valor, String nombreCampo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Indica " + nombreCampo + ".");
        }
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("El formato de " + nombreCampo + " no es válido.");
        }
    }

    /**
     * Elimina (borrado lógico) una formación académica. Solo puede hacerlo su dueño o un
     * Administrador. Si no existe, no hace nada. Deja registro en auditoría a nombre de quien la elimina.
     *
     * @param usuarioQueElimina usuario que ejecuta la acción
     * @param educacionId id de la formación
     * @throws IllegalArgumentException si no es el dueño ni un Administrador
     */
    @Transactional
    public void eliminarEducacion(Usuario usuarioQueElimina, Long educacionId) {
        Optional<Educacion> educacionOpt = educacionRepository.findById(educacionId);

        if (educacionOpt.isPresent()) {
            Educacion educacion = educacionOpt.get();

            //Verficamos que solo el colaborador y el administrador puedan eliminar
            boolean esElDueno = educacion.getColaborador().getId().equals(usuarioQueElimina.getId());
            boolean esAdministrador = usuarioQueElimina.getRol().getNombre().equals("ADMINISTRADOR");

            if (!esElDueno && !esAdministrador) {
                throw new IllegalArgumentException("No puedes eliminar una formación académica que no es tuya.");
            }

            educacion.setActivo(false);
            educacionRepository.save(educacion);

            auditoriaService.registrar(usuarioQueElimina, "ELIMINAR_EDUCACION", "EDUCACION", educacionId,
                    "Quitó la formación académica \"" + educacion.getTitulo() + "\" del perfil de "
                            + educacion.getColaborador().getNombre() + ".");
        }
    }







}

