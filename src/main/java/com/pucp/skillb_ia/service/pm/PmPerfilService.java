package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.dto.PmPerfilView;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.ArchivoAlmacenamientoService;
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

@Service
public class PmPerfilService {

    private static final Set<String> TIPOS_IMAGEN_PERMITIDOS = Set.of("image/jpeg", "image/png");
    private static final long TAMANO_MAXIMO_FOTO_BYTES = 2L * 1024 * 1024; // 2 MB

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    private final ArchivoAlmacenamientoService archivoAlmacenamientoService;

    public PmPerfilService(UsuarioRepository usuarioRepository,
                           PasswordEncoder passwordEncoder,
                           AuditoriaService auditoriaService,
                           ArchivoAlmacenamientoService archivoAlmacenamientoService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
        this.archivoAlmacenamientoService = archivoAlmacenamientoService;
    }

    @Transactional(readOnly = true)
    public PmPerfilView obtener(Usuario pm) {
        return new PmPerfilView(pm);
    }

    @Transactional
    public void actualizarDatos(String nombre, String apellido, String cargo, Usuario pm) {
        if (nombre == null || nombre.isBlank()) throw new IllegalArgumentException("El nombre no puede estar vacío.");
        pm.setNombre(nombre.strip());
        pm.setApellido(apellido != null ? apellido.strip() : pm.getApellido());
        // pm.setCargo() ya no es editable desde el formulario de perfil, controlado por Admin
        usuarioRepository.save(pm);
        auditoriaService.registrar(pm, "ACTUALIZAR_PERFIL", "USUARIO", pm.getId(),
                "PM actualizó sus datos personales.");
    }

    @Transactional
    public void actualizarFoto(MultipartFile foto, Usuario pm) {
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
        String nombreArchivo = "usuario-" + pm.getId() + "-" + UUID.randomUUID() + extension;

        String fotoUrl = archivoAlmacenamientoService.guardar(foto, "perfil", nombreArchivo);
        pm.setFotoUrl(fotoUrl);
        usuarioRepository.save(pm);
        auditoriaService.registrar(pm, "ACTUALIZAR_PERFIL", "USUARIO", pm.getId(),
                "PM actualizó su foto de perfil.");
    }

    @Transactional
    public void actualizarPassword(String passwordActual, String passwordNueva, Usuario pm) {
        if (!passwordEncoder.matches(passwordActual, pm.getPasswordHash())) {
            throw new IllegalArgumentException("La contraseña actual es incorrecta.");
        }
        if (passwordNueva == null || !passwordNueva.matches("(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).{8,}")) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos 8 caracteres, una mayúscula, un número y un símbolo.");
        }
        pm.setPasswordHash(passwordEncoder.encode(passwordNueva));
        usuarioRepository.save(pm);
        auditoriaService.registrar(pm, "CAMBIAR_PASSWORD", "USUARIO", pm.getId(),
                "PM cambió su contraseña.");
    }
}
