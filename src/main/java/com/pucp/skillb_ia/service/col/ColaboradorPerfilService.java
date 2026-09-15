package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.NivelDominio;
import com.pucp.skillb_ia.model.enums.EstadoCertificado;
import com.pucp.skillb_ia.model.enums.EstadoValidacion;
import com.pucp.skillb_ia.repository.CertificadoRepository;
import com.pucp.skillb_ia.repository.ColaboradorHabilidadRepository;
import com.pucp.skillb_ia.repository.EducacionRepository;
import com.pucp.skillb_ia.repository.HabilidadRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
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
import java.time.LocalDate;
import java.util.*;
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
    private final EducacionRepository educacionRepository;
    private final CertificadoRepository certificadoRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final String uploadDir;

    public ColaboradorPerfilService(UsuarioRepository usuarioRepository,
                                    ColaboradorHabilidadRepository colaboradorHabilidadRepository,
                                    HabilidadRepository habilidadRepository,
                                    EducacionRepository educacionRepository,
                                    CertificadoRepository certificadoRepository,
                                    PasswordEncoder passwordEncoder,
                                    AuditoriaService auditoriaService,
                                    @Value("${app.upload-dir:uploads}") String uploadDir) {
        this.usuarioRepository = usuarioRepository;
        this.colaboradorHabilidadRepository = colaboradorHabilidadRepository;
        this.habilidadRepository = habilidadRepository;
        this.educacionRepository = educacionRepository;
        this.certificadoRepository = certificadoRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
        this.uploadDir = uploadDir;
    }

    //Listado de las habilidades activas del colaborador
    public List<ColaboradorHabilidad> listarHabilidades(Usuario colaborador) {
        return colaboradorHabilidadRepository.findByColaboradorAndActivoTrue(colaborador);
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

    //Calculamos el porcentaje que medira si el perfil esta completado del colaborador, considerando la sección de foto, sobre mí y habilidad.
    public int calcularPorcentajeCompletado(Usuario colaborador) {
        int total = 4;
        int listos = 0;
        if (colaborador.getFotoUrl() != null && !colaborador.getFotoUrl().isBlank()) listos++;
        if (colaborador.getDescripcion() != null && !colaborador.getDescripcion().isBlank()) listos++;
        if (!listarHabilidades(colaborador).isEmpty()) listos++;
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
        if (listarHabilidades(colaborador).isEmpty()) {
            pendientes.add("Agrega una habilidad");
        }
        if (listarEducacion(colaborador).isEmpty()) {
            pendientes.add("Agrega tu formación académica");
        }
        return pendientes;
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

        try {

            //Definimos la ruta de la carpeta física donde se guardarán las fotos
            Path carpeta = Path.of(uploadDir, "perfil");
            // Creamos físicamente las carpetas de la ruta definida en caso de que todavía no existan
            Files.createDirectories(carpeta);

            //Determinamos la extensión del archivo según el tipo de imagen
            String extension = "image/png".equals(foto.getContentType()) ? ".png" : ".jpg";

            //Generamos un nombre único e irrepetible para el archivo con la estructura de: usuario-idColaborador-codigoAleatorio.formato
            //Esto lo hacemos para evitar que los archivos se reemplacen en caso de que dos usuarios los suban con el mismo nombre
            String nombreArchivo = "usuario-" + colaborador.getId() + "-" + UUID.randomUUID() + extension;

            //Combinamos la carpeta con el nombre del archivo para obtener la ruta completa donde se guardará la foto
            Path destino = carpeta.resolve(nombreArchivo);

            //Copiamos el contenido de la foto subida hacia la ruta destino y en caso de que ya exista un archivo con ese nombre, lo reemplazamos
            Files.copy(foto.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);

            //Guardamos la URL con la cual se podrá acceder posteriormente a la foto desde la aplicación web
            colaborador.setFotoUrl("/uploads/perfil/" + nombreArchivo);

            //Guardamos los cambios del usuario en la base de datos.
            usuarioRepository.save(colaborador);

            // Registramos en el sistema de auditoría que el usuario actualizó su foto de perfil
            auditoriaService.registrar(colaborador, "ACTUALIZAR_PERFIL", "USUARIO", colaborador.getId(),
                    "Actualizó su foto de perfil.");
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la foto de perfil.", e);
        }
    }

    // ============================================================
    // CAMBIO DE CONTRASEÑA
    // ============================================================

    @Transactional
    public void cambiarPassword(Usuario colaborador, String actual, String nueva, String confirmar) {
        if (actual == null || !passwordEncoder.matches(actual, colaborador.getPasswordHash())) {
            throw new IllegalArgumentException("La contraseña actual no es correcta.");
        }
        if (nueva == null || nueva.length() < 6) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos 6 caracteres.");
        }
        if (!nueva.equals(confirmar)) {
            throw new IllegalArgumentException("Las contraseñas nuevas no coinciden.");
        }
        colaborador.setPasswordHash(passwordEncoder.encode(nueva));
        usuarioRepository.save(colaborador);
        auditoriaService.registrar(colaborador, "CAMBIO_PASSWORD", "USUARIO", colaborador.getId(),
                "Cambió su contraseña desde Ajustes de Cuenta.");
    }

    // ============================================================
    // HABILIDADES DEL COLABORADOR
    // ============================================================
    @Transactional
    public void agregarHabilidad(Usuario colaborador, Long habilidadId, NivelDominio nivel) {
        if (habilidadId == null || nivel == null) {
            throw new IllegalArgumentException("Selecciona una habilidad y un nivel.");
        }

        //Creamos el ID compuesto que identifica la relación entre el colaborador y la habilidad
        ColaboradorHabilidadId id = new ColaboradorHabilidadId(colaborador.getId(), habilidadId);

        //Buscamos si ya existe un registro con ese ID compuesto
        Optional<ColaboradorHabilidad> existente = colaboradorHabilidadRepository.findById(id);

        if (existente.isPresent() && existente.get().isActivo()) {
            throw new IllegalArgumentException("Ya tienes esa habilidad agregada.");
        }

        Habilidad habilidad = habilidadRepository.findById(habilidadId)
                .filter(Habilidad::isActiva)
                .orElseThrow(() -> new IllegalArgumentException("La habilidad seleccionada no existe o ya no está activa."));

        //Evaluamos en caso de que la habilidad ya haya sido registrada anteriormente pero fue borrada
        if (existente.isPresent()) {

            //Reactivamos la misma fila en lugar de crear una nueva
            ColaboradorHabilidad ch = existente.get();
            ch.setNivelDominio(nivel);
            ch.setActivo(true);
            colaboradorHabilidadRepository.save(ch);
        } else {  //En caso de que la habilidad no este presente, la agregamos.
            ColaboradorHabilidad ch = new ColaboradorHabilidad();
            ch.setId(id);
            ch.setColaborador(colaborador);
            ch.setHabilidad(habilidad);
            ch.setNivelDominio(nivel);
            colaboradorHabilidadRepository.save(ch);
        }

        auditoriaService.registrar(colaborador, "AGREGAR_HABILIDAD", "COLABORADOR_HABILIDAD", habilidadId,
                "Agregó la habilidad \"" + habilidad.getNombre() + "\" (" + nivel + ") a su perfil.");
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
    public Certificado subirCertificado(Usuario colaborador, Long habilidadId, MultipartFile archivo) {
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

        try {
            Path carpeta = Path.of(uploadDir, "certificados");
            Files.createDirectories(carpeta);
            Files.copy(archivo.getInputStream(), carpeta.resolve(nombreArchivo),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo guardar el certificado. Inténtalo nuevamente.", e);
        }

        Certificado certificado = new Certificado();
        certificado.setColaborador(colaborador);
        certificado.setHabilidad(habilidad);
        certificado.setArchivoUrl("/uploads/certificados/" + nombreArchivo);
        certificado.setEstado(EstadoCertificado.PENDIENTE);
        certificado = certificadoRepository.save(certificado);

        // Un nuevo intento vuelve a quedar pendiente, salvo que la habilidad ya
        // estuviera validada por un certificado aprobado anteriormente.
        if (perfilHabilidad.getEstadoValidacion() != EstadoValidacion.VALIDADA) {
            perfilHabilidad.setEstadoValidacion(EstadoValidacion.PENDIENTE);
            colaboradorHabilidadRepository.save(perfilHabilidad);
        }

        auditoriaService.registrar(colaborador, "SUBIR_CERTIFICADO", "CERTIFICADO",
                certificado.getId(), "Subió un certificado para la habilidad \""
                        + habilidad.getNombre() + "\".");
        return certificado;
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
                                 LocalDate fechaInicio, LocalDate fechaFin, boolean actual) {
        if (institucion == null || institucion.isBlank()) {
            throw new IllegalArgumentException("Indica la institución.");
        }
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("Indica el título o carrera.");
        }

        Educacion educacion = new Educacion();
        educacion.setColaborador(colaborador);
        educacion.setInstitucion(institucion.trim());
        educacion.setTitulo(titulo.trim());
        educacion.setFechaInicio(fechaInicio);
        educacion.setFechaFin(actual ? null : fechaFin);
        educacion.setActual(actual);
        //Guardamos con estado en pendiente por defecto hasta que el adminsitrador lo valide.
        educacionRepository.save(educacion);

        auditoriaService.registrar(colaborador, "AGREGAR_EDUCACION", "EDUCACION", educacion.getId(),
                "Agregó la formación académica \"" + titulo.trim() + "\" (" + institucion.trim() + ") a su perfil.");
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

