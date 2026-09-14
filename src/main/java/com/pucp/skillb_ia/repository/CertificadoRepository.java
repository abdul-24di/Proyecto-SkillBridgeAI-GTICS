package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Certificado;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoCertificado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CertificadoRepository extends JpaRepository<Certificado, Long> {
    List<Certificado> findByColaborador(Usuario colaborador);

    // Bandeja de certificados pendientes del RM (Historia A23).
    List<Certificado> findByEstado(EstadoCertificado estado);

    long countByColaboradorAndEstado(Usuario colaborador, EstadoCertificado estado);
}
