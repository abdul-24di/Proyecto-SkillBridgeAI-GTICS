package com.pucp.skillb_ia.service.rm;

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
import java.util.regex.Pattern;

// Perfil del RM: solo foto, teléfono y contraseña — nombre/apellido/correo son
// de solo lectura (los administra el Admin), igual que en pm-perfil.html.
// Mismo patrón que PmPerfilService, para que los 3 roles se vean igualitarios.
@Service
public class RmPerfilService {

    private static final Set<String> TIPOS_IMAGEN_PERMITIDOS = Set.of("image/jpeg", "image/png");
    private static final long TAMANO_MAXIMO_FOTO_BYTES = 2L * 1024 * 1024; // 2 MB
    // Solo dígitos y un '+' inicial opcional; 7 a 20 caracteres en total (columna VARCHAR(20)).
    private static final Pattern TELEFONO_PATRON = Pattern.compile("^(?:[0-9]{7,20}|\\+[0-9]{7,19})$");
    public static final String MENSAJE_TELEFONO_OBLIGATORIO = "El teléfono es obligatorio.";
    public static final String MENSAJE_TELEFONO_FORMATO = "El teléfono debe tener entre 7 y 20 caracteres, solo dígitos "
            + "y, opcionalmente, un signo + al inicio (sin espacios, guiones ni paréntesis).";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final ArchivoAlmacenamientoService archivoAlmacenamientoService;

    public RmPerfilService(UsuarioRepository usuarioRepository,
                           PasswordEncoder passwordEncoder,
                           AuditoriaService auditoriaService,
                           ArchivoAlmacenamientoService archivoAlmacenamientoService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
        this.archivoAlmacenamientoService = archivoAlmacenamientoService;
    }

    @Transactional(readOnly = true)
    public Usuario obtenerPerfil(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el usuario autenticado."));
    }

    @Transactional
    public void actualizarTelefono(String telefono, Usuario rm) {
        if (telefono == null || telefono.isBlank()) {
            throw new IllegalArgumentException(MENSAJE_TELEFONO_OBLIGATORIO);
        }
        String telefonoLimpio = telefono.strip();
        if (!TELEFONO_PATRON.matcher(telefonoLimpio).matches()) {
            throw new IllegalArgumentException(MENSAJE_TELEFONO_FORMATO);
        }
        rm.setTelefono(telefonoLimpio);
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

        String extension = "image/png".equals(foto.getContentType()) ? ".png" : ".jpg";
        String nombreArchivo = "usuario-" + rm.getId() + "-" + UUID.randomUUID() + extension;

        String fotoUrl = archivoAlmacenamientoService.guardar(foto, "perfil", nombreArchivo);
        rm.setFotoUrl(fotoUrl);
        usuarioRepository.save(rm);
        auditoriaService.registrar(rm, "ACTUALIZAR_PERFIL", "USUARIO", rm.getId(),
                "RM actualizó su foto de perfil.");
    }

    @Transactional
    public void actualizarPassword(String passwordActual, String passwordNueva, Usuario rm) {
        if (!passwordEncoder.matches(passwordActual, rm.getPasswordHash())) {
            throw new IllegalArgumentException("La contraseña actual es incorrecta.");
        }
        if (passwordNueva == null || !passwordNueva.matches("(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).{8,}")) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos 8 caracteres, una mayúscula, un número y un símbolo.");
        }
        rm.setPasswordHash(passwordEncoder.encode(passwordNueva));
        usuarioRepository.save(rm);
        auditoriaService.registrar(rm, "CAMBIAR_PASSWORD", "USUARIO", rm.getId(),
                "RM cambió su contraseña.");
    }
}
