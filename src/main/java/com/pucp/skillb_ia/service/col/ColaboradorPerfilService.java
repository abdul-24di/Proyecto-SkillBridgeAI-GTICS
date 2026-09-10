package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.model.ColaboradorHabilidad;
import com.pucp.skillb_ia.model.ColaboradorHabilidadId;
import com.pucp.skillb_ia.model.Habilidad;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.NivelDominio;
import com.pucp.skillb_ia.repository.ColaboradorHabilidadRepository;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
public class ColaboradorPerfilService {

    private static final Set<String> TIPOS_IMAGEN_PERMITIDOS = Set.of("image/jpeg", "image/png");
    private static final long TAMANO_MAXIMO_FOTO_BYTES = 2L * 1024 * 1024; // 2MB

    private final UsuarioRepository usuarioRepository;
    private final ColaboradorHabilidadRepository colaboradorHabilidadRepository;
    private final HabilidadRepository habilidadRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final String uploadDir;

    public ColaboradorPerfilService(UsuarioRepository usuarioRepository,
                                    ColaboradorHabilidadRepository colaboradorHabilidadRepository,
                                    HabilidadRepository habilidadRepository,
                                    PasswordEncoder passwordEncoder,
                                    AuditoriaService auditoriaService,
                                    @Value("${app.upload-dir:uploads}") String uploadDir) {
        this.usuarioRepository = usuarioRepository;
        this.colaboradorHabilidadRepository = colaboradorHabilidadRepository;
        this.habilidadRepository = habilidadRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
        this.uploadDir = uploadDir;
    }

    //Listado de las habilidades del colaborador
    public List<ColaboradorHabilidad> listarHabilidades(Usuario colaborador) {
        return colaboradorHabilidadRepository.findByColaborador(colaborador);
    }

    //Listado de habilidades con las que no cuenta el colaborador
    public List<Habilidad> listarHabilidadesDisponibles(Usuario colaborador) {
        Set<Long> yaAgregadas = listarHabilidades(colaborador).stream().map(ch -> ch.getHabilidad().getId())
                .collect(Collectors.toSet());

        return habilidadRepository.findByActivaTrue().stream()
                .filter(h -> !yaAgregadas.contains(h.getId()))
                .toList();
    }

    //Calculamos el porcentaje que medira si el perfil esta completado del colaborador, considerando la sección de foto, sobre mí y habilidad.
    public int calcularPorcentajeCompletado(Usuario colaborador) {
        int total = 3;
        int listos = 0;
        if (colaborador.getFotoUrl() != null && !colaborador.getFotoUrl().isBlank()) listos++;
        if (colaborador.getDescripcion() != null && !colaborador.getDescripcion().isBlank()) listos++;
        if (!listarHabilidades(colaborador).isEmpty()) listos++;
        return Math.round((listos * 100f) / total);
    }

    //Listado de cosas que le faltan completar en su perfil
    public List<String> listarPendientesCompletar(Usuario colaborador) {
        return java.util.stream.Stream.of(
                (colaborador.getFotoUrl() == null || colaborador.getFotoUrl().isBlank()) ? "Agrega una foto de perfil" : null,
                (colaborador.getDescripcion() == null || colaborador.getDescripcion().isBlank()) ? "Agrega una descripción en \"Sobre mí\"" : null,
                listarHabilidades(colaborador).isEmpty() ? "Agrega una habilidad" : null

                //Filtramos y formamos una lista con los elementos que no sean nulos
        ).filter(java.util.Objects::nonNull).toList();
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

        if (colaboradorHabilidadRepository.existsById(id)) {
            throw new IllegalArgumentException("Ya tienes esa habilidad agregada.");
        }
        //Buscamoa la habilidad, en caso de que exista y este activa, la guárdamos en habilidad y en caso de que no, lanzamos un error
        Habilidad habilidad = habilidadRepository.findById(habilidadId)
                .filter(Habilidad::isActiva)
                .orElseThrow(() -> new IllegalArgumentException("La habilidad seleccionada no existe o ya no está activa."));

        ColaboradorHabilidad ch = new ColaboradorHabilidad();
        ch.setId(id);
        ch.setColaborador(colaborador);
        ch.setHabilidad(habilidad);
        ch.setNivelDominio(nivel);
        colaboradorHabilidadRepository.save(ch);

        auditoriaService.registrar(colaborador, "AGREGAR_HABILIDAD", "COLABORADOR_HABILIDAD", habilidadId,
                "Agregó la habilidad \"" + habilidad.getNombre() + "\" (" + nivel + ") a su perfil.");
    }

    @Transactional
    public void eliminarHabilidad(Usuario colaborador, Long habilidadId) {

        ColaboradorHabilidadId id = new ColaboradorHabilidadId(colaborador.getId(), habilidadId);

        colaboradorHabilidadRepository.findById(id).ifPresent(ch -> {
            colaboradorHabilidadRepository.deleteById(id);
            auditoriaService.registrar(colaborador, "ELIMINAR_HABILIDAD", "COLABORADOR_HABILIDAD", habilidadId,
                    "Quitó la habilidad \"" + ch.getHabilidad().getNombre() + "\" de su perfil.");
        });


    }

}

