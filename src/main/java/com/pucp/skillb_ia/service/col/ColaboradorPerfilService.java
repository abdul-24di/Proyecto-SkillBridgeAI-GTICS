package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.NivelDominio;
import com.pucp.skillb_ia.model.enums.EstadoCertificado;
import com.pucp.skillb_ia.model.enums.EstadoValidacion;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.service.ArchivoAlmacenamientoService;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.NotificacionService;
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
import java.time.format.DateTimeParseException;
import java.util.stream.Collectors;


@Service
public class ColaboradorPerfilService {

    private static final Set<String> TIPOS_IMAGEN_PERMITIDOS = Set.of("image/jpeg", "image/png");
    private static final long TAMANO_MAXIMO_FOTO_BYTES = 2L * 1024 * 1024; // 2MB
    private static final Set<String> TIPOS_CERTIFICADO_PERMITIDOS =
            Set.of("application/pdf", "image/jpeg", "image/png");
    private static final long TAMANO_MAXIMO_CERTIFICADO_BYTES = 10L * 1024 * 1024;

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
                                    ArchivoAlmacenamientoService archivoAlmacenamientoService) {
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
    }

    //Listado de categorías activas
    public List<CategoriaHabilidad> listarCategoriasHabilidad() {
        return categoriaHabilidadRepository.findByActivaTrue();
    }

    //Listado de las habilidades activas del colaborador
    public List<ColaboradorHabilidad> listarHabilidades(Usuario colaborador) {
        return colaboradorHabilidadRepository.findByColaboradorAndActivoTrue(colaborador);
    }


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

    //Listado de habilidades con las que no cuenta el colaborador
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

    // Certificados que el colaborador ha presentado, incluidos los rechazados.
    // Se conservan para que el motivo y el historial siempre permanezcan visibles.
    @Transactional(readOnly = true)
    public List<Certificado> listarCertificados(Usuario colaborador) {
        return certificadoRepository.findByColaboradorIdConDetalle(colaborador.getId());
    }


    //Calculamos el porcentaje que medira si el perfil esta completado del colaborador
    //Considerando la sección de foto, sobre mí, habilidad y formación academica
    public int calcularPorcentajeCompletado(Usuario colaborador) {
        int total = 4;
        int listos = 0;
        if (colaborador.getFotoUrl() != null && !colaborador.getFotoUrl().isBlank()) listos++;
        if (colaborador.getDescripcion() != null && !colaborador.getDescripcion().isBlank()) listos++;
        if (tieneHabilidadValidada(colaborador)) listos++;
        if (tieneEducacionAprobada(colaborador)) listos++;
        return Math.round((listos * 100f) / total);
    }

    //Implementamos el listado de cosas que le faltan completar en su perfil al colaborador
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

    //Evaluamos que el colaborador tenga al menos una habilidad ya validada por el RM.
    private boolean tieneHabilidadValidada(Usuario colaborador) {
        for (ColaboradorHabilidad ch : listarHabilidades(colaborador)) {
            if (ch.getEstadoValidacion() == EstadoValidacion.VALIDADA) {
                return true;
            }
        }
        return false;
    }

    //Evaluamos que el colaborador tenga al menos una formación académica ya aprobada por el RM
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
    @Transactional
    public void actualizarTelefono(Usuario colaborador, String telefono) {
        if (telefono == null || telefono.isBlank()) {
            throw new IllegalArgumentException("Indica tu número de teléfono.");
        }
        String limpio = telefono.trim();

        if (!limpio.matches("\\d{9}")) {
            throw new IllegalArgumentException("El teléfono debe tener exactamente 9 dígitos numéricos, sin letras ni otros caracteres.");
        }

        colaborador.setTelefono(limpio);
        usuarioRepository.save(colaborador);
        auditoriaService.registrar(colaborador, "ACTUALIZAR_PERFIL", "USUARIO", colaborador.getId(), "Actualizó su número de teléfono.");
    }

    // ============================================================
    // FOTO DE PERFIL
    // ============================================================

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

    //Validamos contraseña
    private void validarPassword(String password) {
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos 6 caracteres.");
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
        certificadoRepository.save(certificadoEntidad);

        auditoriaService.registrar(colaborador, "AGREGAR_HABILIDAD", "COLABORADOR_HABILIDAD", habilidad.getId(),
                "Agregó la habilidad \"" + habilidad.getNombre() + "\" (" + nivel
                        + ") a su perfil y envió un certificado a revisión del RM.");
    }

    //En caso de que el colaborador no encuentre una habilidad, tiene la opción de crearla.
    private Habilidad crearOReutilizarHabilidad(String nombre, Long categoriaId) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("Escribe el nombre de la nueva habilidad.");
        }
        if (categoriaId == null) {
            throw new IllegalArgumentException("Selecciona una categoría para la nueva habilidad.");
        }

        String nombreLimpio = nombre.trim();

        //El nombre es único en todo el catalogo. Si ya existe, se lo decimos al colaborador para que la busque en el
        //selector del catálogo.
        Optional<Habilidad> existente = habilidadRepository.findByNombreIgnoreCase(nombreLimpio);

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


    //Guardamos certificado
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

        notificacionService.crearParaTodosLosRm("CERTIFICADO_PENDIENTE", CategoriaNotificacion.HABILIDAD,
                "Certificado pendiente de revisión",
                colaborador.getNombre() + " " + colaborador.getApellido()
                        + " subió un certificado para \"" + habilidad.getNombre() + "\".",
                "CERTIFICADO", certificado.getId());
        return certificado;
    }

    // ============================================================
    // EXPERIENCIA PROFESIONAL
    // ============================================================
    public List<ExperienciaProfesional> listarExperienciaProfesional(Usuario colaborador) {
        return experienciaProfesionalRepository.findByColaboradorOrderByFechaInicioDesc(colaborador);
    }

    // ============================================================
    // EDUCACIÓN
    // ============================================================

    //Listamos la formación académica activa del colaborador
    public List<Educacion> listarEducacion(Usuario colaborador) {
        return educacionRepository.findByColaboradorAndActivoTrue(colaborador);
    }

    //Agregamos educación
    @Transactional
    public void agregarEducacion(Usuario colaborador, String institucion, String titulo,
                                 String fechaInicioTexto, String fechaFinTexto,
                                 MultipartFile certificado) {
        if (institucion == null || institucion.isBlank()) {
            throw new IllegalArgumentException("Indica la institución.");
        }
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("Indica el título o carrera.");
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
        educacionRepository.save(educacion);

        auditoriaService.registrar(colaborador, "AGREGAR_EDUCACION", "EDUCACION", educacion.getId(),
                "Agregó la formación académica \"" + titulo.trim() + "\" (" + institucion.trim()
                        + ") a su perfil y adjuntó un certificado.");

        notificacionService.crearParaTodosLosRm("EDUCACION_PENDIENTE", CategoriaNotificacion.HABILIDAD,
                "Formación académica pendiente de revisión",
                colaborador.getNombre() + " " + colaborador.getApellido()
                        + " agregó \"" + titulo.trim() + "\" (" + institucion.trim() + ") a su perfil.",
                "EDUCACION", educacion.getId());
    }

    //Convertimos el texto del input type="date" a LocalDate, exigiendo que venga lleno y con formato válido
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

