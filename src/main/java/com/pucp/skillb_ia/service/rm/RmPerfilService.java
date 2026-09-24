package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.model.Usuario;
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
import java.util.Set;
import java.util.UUID;

// Perfil del RM: solo foto, teléfono y contraseña — nombre/apellido/correo son
// de solo lectura (los administra el Admin), igual que en pm-perfil.html.
// Mismo patrón que PmPerfilService, para que los 3 roles se vean igualitarios.
@Service
public class RmPerfilService {

    private static final Set<String> TIPOS_IMAGEN_PERMITIDOS = Set.of("image/jpeg", "image/png");
    private static final long TAMANO_MAXIMO_FOTO_BYTES = 2L * 1024 * 1024; // 2 MB

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final String uploadDir;

    public RmPerfilService(UsuarioRepository usuarioRepository,
                            PasswordEncoder passwordEncoder,
                            AuditoriaService auditoriaService,
                            @Value("${app.upload-dir:uploads}") String uploadDir) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
        this.uploadDir = uploadDir;
    }

    @Transactional(readOnly = true)
    public Usuario obtenerPerfil(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el usuario autenticado."));
    }

    @Transactional
    public void actualizarTelefono(String telefono, Usuario rm) {
        rm.setTelefono(telefono != null ? telefono.strip() : null);
        usuarioRepository.save(rm);
        auditoriaService.registrar(rm, "ACTUALIZAR_PERFIL", "USUARIO", rm.getId(),
                "RM actualizó su teléfono.");
    }

    @Transactional
    public void actualizarFoto(MultipartFile foto, Usuario rm) {
        if (foto == null || foto.isEmpty()) {
            throw new IllegalArgumentException("La foto no puede estar vacía.");
        }
        if (!TIPOS_IMAGEN_PERMITIDOS.contains(foto.getContentType())) {
            throw new IllegalArgumentException("Solo se permiten imágenes JPEG o PNG.");
        }
        if (foto.getSize() > TAMANO_MAXIMO_FOTO_BYTES) {
            throw new IllegalArgumentException("La foto no puede superar los 2 MB.");
        }

        try {
            Path carpeta = Path.of(uploadDir, "perfil");
            Files.createDirectories(carpeta);
            String extension = "image/png".equals(foto.getContentType()) ? ".png" : ".jpg";
            String nombreArchivo = "usuario-" + rm.getId() + "-" + UUID.randomUUID() + extension;
            Path destino = carpeta.resolve(nombreArchivo);
            Files.copy(foto.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);
            rm.setFotoUrl("/uploads/perfil/" + nombreArchivo);
            usuarioRepository.save(rm);
            auditoriaService.registrar(rm, "ACTUALIZAR_PERFIL", "USUARIO", rm.getId(),
                    "RM actualizó su foto de perfil.");
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la foto de perfil.", e);
        }
    }

    @Transactional
    public void actualizarPassword(String passwordActual, String passwordNueva, Usuario rm) {
        if (!passwordEncoder.matches(passwordActual, rm.getPasswordHash())) {
            throw new IllegalArgumentException("La contraseña actual es incorrecta.");
        }
        if (passwordNueva == null || passwordNueva.length() < 6) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos 6 caracteres.");
        }
        rm.setPasswordHash(passwordEncoder.encode(passwordNueva));
        usuarioRepository.save(rm);
        auditoriaService.registrar(rm, "CAMBIAR_PASSWORD", "USUARIO", rm.getId(),
                "RM cambió su contraseña.");
    }
}
