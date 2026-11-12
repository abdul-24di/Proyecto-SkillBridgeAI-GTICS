package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoCertificado;
import com.pucp.skillb_ia.model.enums.EstadoColaboradorCurso;
import com.pucp.skillb_ia.repository.CertificadoRepository;
import com.pucp.skillb_ia.repository.ColaboradorCursoRepository;
import com.pucp.skillb_ia.repository.EducacionRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Elige qué RM revisará un certificado, una formación o un curso
// el que tenga menos revisiones pendientes en total
@Service
public class RmRevisionAsignadorService {

    private static final List<EstadoColaboradorCurso> CURSOS_PENDIENTES =
            List.of(EstadoColaboradorCurso.SOLICITADO, EstadoColaboradorCurso.EVIDENCIA_PENDIENTE);

    private final UsuarioRepository usuarioRepository;
    private final CertificadoRepository certificadoRepository;
    private final EducacionRepository educacionRepository;
    private final ColaboradorCursoRepository colaboradorCursoRepository;

    public RmRevisionAsignadorService(UsuarioRepository usuarioRepository,
                                      CertificadoRepository certificadoRepository,
                                      EducacionRepository educacionRepository,
                                      ColaboradorCursoRepository colaboradorCursoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.certificadoRepository = certificadoRepository;
        this.educacionRepository = educacionRepository;
        this.colaboradorCursoRepository = colaboradorCursoRepository;
    }

    // Devuelve el RM activo con menos pendientes a revisar que son certificados, formaciones y cursos
    // Si no hay ningún RM activo devuelve null.
    @Transactional(readOnly = true)
    public Usuario elegirRm() {
        List<Usuario> rms = usuarioRepository.findActivosByRolNombre("RESOURCE_MANAGER");
        Usuario elegido = null;
        long menor = Long.MAX_VALUE;

        for (Usuario rm : rms) {
            long pendientes = certificadoRepository.countByRmResponsableAndEstado(rm, EstadoCertificado.PENDIENTE)
                    + educacionRepository.countByRmResponsableAndEstadoAndActivoTrue(rm, EstadoCertificado.PENDIENTE)
                    + colaboradorCursoRepository.countByRmResponsableAndEstadoIn(rm, CURSOS_PENDIENTES);
            if (pendientes < menor) {
                menor = pendientes;
                elegido = rm;
            }
        }
        return elegido;
    }
}