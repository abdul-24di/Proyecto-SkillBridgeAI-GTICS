package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Sobre mí" del perfil del Administrador, el PM y el RM: lo que escribieron en "Sobre ti" al activar su
 * cuenta. El colaborador lo edita en su propio perfil (ColaboradorPerfilService).
 */
@Service
public class PerfilSobreMiService {

    private static final int MAX_LONGITUD = 500; // columna usuario.descripcion

    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    /** Crea el componente con sus dependencias, que Spring inyecta por el constructor. */
    public PerfilSobreMiService(UsuarioRepository usuarioRepository, AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.auditoriaService = auditoriaService;
    }

    /** Guarda la descripción (vacía = se quita) y devuelve el texto guardado. */
    @Transactional
    public String actualizar(Long usuarioId, String descripcion) {
        String limpio = descripcion == null ? "" : descripcion.strip();
        // El navegador cuenta un salto de línea como un carácter, pero lo envía como dos (\r\n).
        limpio = limpio.replace("\r\n", "\n");
        if (limpio.length() > MAX_LONGITUD) {
            throw new IllegalArgumentException("La descripción no puede superar los " + MAX_LONGITUD + " caracteres.");
        }
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el usuario autenticado."));
        usuario.setDescripcion(limpio.isEmpty() ? null : limpio);
        usuarioRepository.save(usuario);
        auditoriaService.registrar(usuario, "ACTUALIZAR_PERFIL", "USUARIO", usuario.getId(),
                "Actualizó la sección \"Sobre mí\" de su perfil.");
        return usuario.getDescripcion();
    }
}
