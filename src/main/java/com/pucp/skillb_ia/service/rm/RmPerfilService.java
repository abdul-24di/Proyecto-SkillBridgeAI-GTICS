package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RmPerfilService {
    private final UsuarioRepository usuarioRepository;

    public RmPerfilService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public Usuario obtenerPerfil(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(()->
                        new IllegalArgumentException("No se encontró el usuario autenticado."));
    }
}
