package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.UsuarioRepository;
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

// Perfil del Admin: solo foto, teléfono y contraseña — mismo patrón que
// RmPerfilService/PmPerfilService para que los 4 roles se vean igualitarios.
@Service
public class AdminPerfilService {

    private static final Set<String> TIPOS_IMAGEN_PERMITIDOS = Set.of("image/jpeg", "image/png");
    private static final long TAMANO_MAXIMO_FOTO_BYTES = 2L * 1024 * 1024; // 2 MB

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    private final AuditoriaService auditoriaService;
    private final ArchivoAlmacenamientoService archivoAlmacenamientoService;

    public AdminPerfilService(UsuarioRepository usuarioRepository,
                              PasswordEncoder passwordEncoder,
                              AuditoriaService auditoriaService,
                              ArchivoAlmacenamientoService archivoAlmacenamientoService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
        this.archivoAlmacenamientoService = archivoAlmacenamientoService;
    }

    @Transactional
    public void actualizarTelefono(String telefono, Usuario admin) {
        admin.setTelefono(telefono != null ? telefono.strip() : null);
        usuarioRepository.save(admin);
        auditoriaService.registrar(admin, "ACTUALIZAR_PERFIL", "USUARIO", admin.getId(),
                "Administrador actualizó su teléfono.");
    }

    @Transactional
    public void actualizarFoto(MultipartFile foto, Usuario admin) {
        if (foto == null || foto.isEmpty()) {
            throw new IllegalArgumentException("La foto no puede estar vacía.");
        }
        if (!TIPOS_IMAGEN_PERMITIDOS.contains(foto.getContentType())) {
            throw new IllegalArgumentException("Solo se permiten imágenes JPEG o PNG.");
        }
        if (foto.getSize() > TAMANO_MAXIMO_FOTO_BYTES) {
            throw new IllegalArgumentException("La foto no puede superar los 2 MB.");
        }

        String extension = "image/png".equals(foto.getContentType()) ? ".png" : ".jpg";
        String nombreArchivo = "usuario-" + admin.getId() + "-" + UUID.randomUUID() + extension;

        String fotoUrl = archivoAlmacenamientoService.guardar(foto, "perfil", nombreArchivo);
        admin.setFotoUrl(fotoUrl);
        usuarioRepository.save(admin);
        auditoriaService.registrar(admin, "ACTUALIZAR_PERFIL", "USUARIO", admin.getId(),
                "Administrador actualizó su foto de perfil.");
    }

    @Transactional
    public void actualizarPassword(String passwordActual, String passwordNueva, Usuario admin) {
        if (!passwordEncoder.matches(passwordActual, admin.getPasswordHash())) {
            throw new IllegalArgumentException("La contraseña actual es incorrecta.");
        }
        if (passwordNueva == null || !passwordNueva.matches("(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).{8,}")) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos 8 caracteres, una mayúscula, un número y un símbolo.");
        }
        admin.setPasswordHash(passwordEncoder.encode(passwordNueva));
        usuarioRepository.save(admin);
        auditoriaService.registrar(admin, "CAMBIAR_PASSWORD", "USUARIO", admin.getId(),
                "Administrador cambió su contraseña.");
    }
}
