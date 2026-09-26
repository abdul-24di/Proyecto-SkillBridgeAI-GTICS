package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmCertificadoView;
import com.pucp.skillb_ia.model.Certificado;
import com.pucp.skillb_ia.model.ColaboradorHabilidad;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.*;
import com.pucp.skillb_ia.repository.CertificadoRepository;
import com.pucp.skillb_ia.repository.ColaboradorHabilidadRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.NotificacionService;
import com.pucp.skillb_ia.service.admin.AdminCargoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RmCertificadoService {
    private static final String ROL_RM = "RESOURCE_MANAGER";
    private static final String ROL_COLABORADOR = "COLABORADOR";

    private final CertificadoRepository certificadoRepository;
    private final ColaboradorHabilidadRepository colaboradorHabilidadRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;

    public RmCertificadoService(CertificadoRepository certificadoRepository,
                                ColaboradorHabilidadRepository colaboradorHabilidadRepository,
                                UsuarioRepository usuarioRepository,
                                AuditoriaService auditoriaService,
                                NotificacionService notificacionService) {
        this.certificadoRepository = certificadoRepository;
        this.colaboradorHabilidadRepository = colaboradorHabilidadRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
    }

    @Transactional(readOnly = true)
    public List<RmCertificadoView> listarPendientes() {
        return certificadoRepository.findByEstadoConDetalle(EstadoCertificado.PENDIENTE).stream()
                .map(this::crearVista)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RmCertificadoView> listarHistorial(Long colaboradorId) {
        obtenerColaborador(colaboradorId);
        return certificadoRepository.findByColaboradorIdConDetalle(colaboradorId).stream()
                .map(this::crearVista)
                .toList();
    }

    @Transactional(readOnly = true)
    public RmCertificadoView obtener(Long certificadoId) {
        return crearVista(obtenerEntidad(certificadoId));
    }

    @Transactional(readOnly = true)
    public long contarRevisadosHoy() {
        LocalDate hoy = LocalDate.now();
        return certificadoRepository.countByFechaRevisionBetween(
                hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay());
    }

    @Transactional
    public void aprobar(Long certificadoId, NivelDominio nivelHabilidad,
                        NivelExperiencia nivelGeneral, Long rmId) {
        Usuario rm = obtenerRm(rmId);
        Certificado certificado = obtenerEntidad(certificadoId);
        validarPendiente(certificado);
        ColaboradorHabilidad habilidad = obtenerHabilidadDelPerfil(certificado);

        if (nivelHabilidad != null) habilidad.setNivelDominio(nivelHabilidad);
        habilidad.setEstadoValidacion(EstadoValidacion.VALIDADA);
        habilidad.setActivo(true);
        colaboradorHabilidadRepository.save(habilidad);

        if (nivelGeneral != null) {
            certificado.getColaborador().setNivelExperiencia(nivelGeneral);
            AdminCargoService.aplicarSueldoSegunCargo(certificado.getColaborador());
            usuarioRepository.save(certificado.getColaborador());
        }

        certificado.setEstado(EstadoCertificado.APROBADO);
        certificado.setMotivoRechazo(null);
        certificado.setRevisadoPor(rm);
        certificado.setFechaRevision(LocalDateTime.now());
        certificadoRepository.save(certificado);

        notificacionService.crear(certificado.getColaborador(), "CERTIFICADO_APROBADO", CategoriaNotificacion.HABILIDAD,
                "Certificado aprobado",
                "Tu certificado de \"" + certificado.getHabilidad().getNombre() + "\" fue aprobado.",
                "CERTIFICADO", certificado.getId());

        auditoriaService.registrar(rm, "APROBACION_CERTIFICADO", "CERTIFICADO", certificadoId,
                "Se aprobó el certificado de " + certificado.getHabilidad().getNombre()
                        + " presentado por " + nombreCompleto(certificado.getColaborador()) + ".");
    }

    @Transactional
    public void rechazar(Long certificadoId, String motivo, Long rmId) {
        Usuario rm = obtenerRm(rmId);
        Certificado certificado = obtenerEntidad(certificadoId);
        validarPendiente(certificado);
        String motivoLimpio = textoObligatorio(motivo);

        certificado.setEstado(EstadoCertificado.RECHAZADO);
        certificado.setMotivoRechazo(motivoLimpio);
        certificado.setRevisadoPor(rm);
        certificado.setFechaRevision(LocalDateTime.now());
        certificadoRepository.save(certificado);

        if (certificadoRepository.countByColaboradorAndHabilidadAndEstado(
                certificado.getColaborador(), certificado.getHabilidad(), EstadoCertificado.APROBADO) == 0) {
            obtenerHabilidadDelPerfil(certificado).setEstadoValidacion(EstadoValidacion.RECHAZADA);
        }

        notificacionService.crear(certificado.getColaborador(), "CERTIFICADO_RECHAZADO", CategoriaNotificacion.HABILIDAD,
                "Certificado rechazado",
                "Tu certificado de \"" + certificado.getHabilidad().getNombre()
                        + "\" fue rechazado. Motivo: " + motivoLimpio,
                "CERTIFICADO", certificado.getId());

        auditoriaService.registrar(rm, "RECHAZO_CERTIFICADO", "CERTIFICADO", certificadoId,
                "Se rechazó el certificado de " + certificado.getHabilidad().getNombre()
                        + ". Motivo: " + motivoLimpio);
    }

    @Transactional
    public void actualizarNivelExperiencia(Long colaboradorId, NivelExperiencia nivel, Long rmId) {
        Usuario rm = obtenerRm(rmId);
        Usuario colaborador = obtenerColaborador(colaboradorId);
        if (nivel == null) throw new IllegalArgumentException("Selecciona un nivel de experiencia.");
        NivelExperiencia anterior = colaborador.getNivelExperiencia();
        colaborador.setNivelExperiencia(nivel);
        AdminCargoService.aplicarSueldoSegunCargo(colaborador);
        usuarioRepository.save(colaborador);
        auditoriaService.registrar(rm, "ACTUALIZACION_NIVEL_EXPERIENCIA", "USUARIO", colaboradorId,
                "El RM actualizó el nivel general de " + nombreCompleto(colaborador) + ".",
                anterior == null ? null : anterior.name(), nivel.name(), null);
    }

    private RmCertificadoView crearVista(Certificado certificado) {
        Usuario colaborador = certificado.getColaborador();
        ColaboradorHabilidad habilidad = colaboradorHabilidadRepository
                .findByColaboradorAndHabilidad(colaborador, certificado.getHabilidad())
                .orElse(null);
        List<String> habilidades = colaboradorHabilidadRepository
                .findByColaboradorAndActivoTrue(colaborador).stream()
                .map(item -> item.getHabilidad().getNombre())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        return new RmCertificadoView(
                certificado,
                nombreCompleto(colaborador),
                iniciales(colaborador),
                valor(colaborador.getCargo() != null ? colaborador.getCargo().getNombre() : null, "Cargo sin registrar"),
                habilidad == null ? "Sin registrar" : textoEnum(habilidad.getNivelDominio().name()),
                colaborador.getNivelExperiencia() == null
                        ? "Sin definir" : textoNivel(colaborador.getNivelExperiencia()),
                certificado.getRevisadoPor() == null
                        ? "—" : nombreCompleto(certificado.getRevisadoPor()),
                certificadoRepository.countByColaboradorAndEstado(
                        colaborador, EstadoCertificado.APROBADO),
                habilidades);
    }

    private Certificado obtenerEntidad(Long id) {
        return certificadoRepository.findByIdConDetalle(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el certificado solicitado."));
    }

    private ColaboradorHabilidad obtenerHabilidadDelPerfil(Certificado certificado) {
        return colaboradorHabilidadRepository
                .findByColaboradorAndHabilidad(certificado.getColaborador(), certificado.getHabilidad())
                .filter(ColaboradorHabilidad::isActivo)
                .orElseThrow(() -> new IllegalStateException(
                        "La habilidad asociada ya no forma parte del perfil del colaborador."));
    }

    private Usuario obtenerRm(Long id) {
        Usuario rm = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el Resource Manager."));
        if (!ROL_RM.equals(rm.getRol().getNombre())) {
            throw new IllegalStateException("La operación requiere un Resource Manager.");
        }
        return rm;
    }

    private Usuario obtenerColaborador(Long id) {
        return usuarioRepository.findById(id)
                .filter(Usuario::isActivo)
                .filter(usuario -> ROL_COLABORADOR.equals(usuario.getRol().getNombre()))
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el colaborador solicitado."));
    }

    private void validarPendiente(Certificado certificado) {
        if (certificado.getEstado() != EstadoCertificado.PENDIENTE) {
            throw new IllegalStateException("Este certificado ya fue revisado.");
        }
    }

    private String textoObligatorio(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("Debes indicar el motivo del rechazo.");
        }
        String limpio = texto.trim();
        return limpio.length() <= 300 ? limpio : limpio.substring(0, 300);
    }

    private String nombreCompleto(Usuario usuario) {
        String completo = (valor(usuario.getNombre(), "") + " " + valor(usuario.getApellido(), "")).trim();
        return completo.isEmpty() ? usuario.getCorreo() : completo;
    }

    private String iniciales(Usuario usuario) {
        String nombre = valor(usuario.getNombre(), "").trim();
        String apellido = valor(usuario.getApellido(), "").trim();
        String texto = (nombre.isEmpty() ? "" : nombre.substring(0, 1))
                + (apellido.isEmpty() ? "" : apellido.substring(0, 1));
        return texto.isEmpty() ? "CO" : texto.toUpperCase();
    }

    private String valor(String texto, String alternativa) {
        return texto == null || texto.isBlank() ? alternativa : texto;
    }

    private String textoEnum(String valor) {
        String texto = valor.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    private String textoNivel(NivelExperiencia nivel) {
        return nivel == NivelExperiencia.SEMI_SENIOR ? "Semi Senior" : textoEnum(nivel.name());
    }
}
