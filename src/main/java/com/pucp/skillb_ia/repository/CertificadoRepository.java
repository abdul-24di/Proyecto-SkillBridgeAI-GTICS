package com.pucp.skillb_ia.repository;

import com.pucp.skillb_ia.model.Certificado;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.Habilidad;
import com.pucp.skillb_ia.model.enums.EstadoCertificado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface CertificadoRepository extends JpaRepository<Certificado, Long> {
    List<Certificado> findByColaborador(Usuario colaborador);

    // Bandeja de certificados pendientes del RM (Historia A23).
    List<Certificado> findByEstado(EstadoCertificado estado);

    long countByColaboradorAndEstado(Usuario colaborador, EstadoCertificado estado);

    long countByColaboradorAndHabilidadAndEstado(
            Usuario colaborador, Habilidad habilidad, EstadoCertificado estado);

    @Query("""
            select c from Certificado c
            join fetch c.colaborador u
            join fetch u.rol
            join fetch c.habilidad h
            left join fetch c.revisadoPor
            where c.estado = :estado
              and (u.registroEstado is null
                   or u.registroEstado = com.pucp.skillb_ia.model.enums.EstadoRegistro.APROBADO)
            order by c.fechaSubida asc
            """)
    List<Certificado> findByEstadoConDetalle(@Param("estado") EstadoCertificado estado);

    @Query("""
            select c from Certificado c
            join fetch c.colaborador u
            join fetch u.rol
            join fetch c.habilidad h
            left join fetch c.revisadoPor
            where c.id = :id
            """)
    Optional<Certificado> findByIdConDetalle(@Param("id") Long id);

    @Query("""
            select c from Certificado c
            join fetch c.colaborador u
            join fetch u.rol
            join fetch c.habilidad h
            left join fetch c.revisadoPor
            where u.id = :colaboradorId
            order by c.fechaSubida desc
            """)
    List<Certificado> findByColaboradorIdConDetalle(@Param("colaboradorId") Long colaboradorId);

    long countByFechaRevisionBetween(LocalDateTime inicio, LocalDateTime fin);
}
