package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmColaboradorDetalle;
import com.pucp.skillb_ia.dto.RmColaboradorResumen;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.ColaboradorHabilidad;
import com.pucp.skillb_ia.model.Educacion;
import com.pucp.skillb_ia.model.ExperienciaProfesional;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoCertificado;
import com.pucp.skillb_ia.model.enums.NivelExperiencia;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.CertificadoRepository;
import com.pucp.skillb_ia.repository.ColaboradorHabilidadRepository;
import com.pucp.skillb_ia.repository.ConfiguracionSistemaRepository;
import com.pucp.skillb_ia.repository.EducacionRepository;
import com.pucp.skillb_ia.repository.ExperienciaProfesionalRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class RmColaboradorConsultaService {

    private static final String ROL_COLABORADOR = "COLABORADOR";
    private static final String CLAVE_MAX_ASIGNACIONES = "MAX_ASIGNACIONES_POR_COLABORADOR";
    private static final int MAX_ASIGNACIONES_POR_DEFECTO = 3;

    private final UsuarioRepository usuarioRepository;
    private final ColaboradorHabilidadRepository colaboradorHabilidadRepository;
    private final AsignacionRepository asignacionRepository;
    private final CertificadoRepository certificadoRepository;
    private final ConfiguracionSistemaRepository configuracionSistemaRepository;
    private final ExperienciaProfesionalRepository experienciaProfesionalRepository;
    private final EducacionRepository educacionRepository;
    private final RmPresupuestoService presupuestoService;

    public RmColaboradorConsultaService(
            UsuarioRepository usuarioRepository,
            ColaboradorHabilidadRepository colaboradorHabilidadRepository,
            AsignacionRepository asignacionRepository,
            CertificadoRepository certificadoRepository,
            ConfiguracionSistemaRepository configuracionSistemaRepository,
            ExperienciaProfesionalRepository experienciaProfesionalRepository,
            EducacionRepository educacionRepository,
            RmPresupuestoService presupuestoService) {
        this.usuarioRepository = usuarioRepository;
        this.colaboradorHabilidadRepository = colaboradorHabilidadRepository;
        this.asignacionRepository = asignacionRepository;
        this.certificadoRepository = certificadoRepository;
        this.configuracionSistemaRepository = configuracionSistemaRepository;
        this.experienciaProfesionalRepository = experienciaProfesionalRepository;
        this.educacionRepository = educacionRepository;
        this.presupuestoService = presupuestoService;
    }

    // Candidato + costo estimado para un proyecto concreto (sección 11 de la
    // especificación de presupuesto). Solo se usa en la pantalla de búsqueda
    // de candidatos del RM — el listado general de colaboradores no expone sueldo.
    public record CandidatoConCosto(RmColaboradorResumen resumen, java.math.BigDecimal sueldoBase,
                                     java.math.BigDecimal horasContratadas,
                                     RmPresupuestoService.CostoAsignacion costoEstimado, boolean cabeEnPresupuesto) {
    }

    // Usado por la pantalla "Proponer asignación" para calcular el costo en
    // vivo en el navegador (JS) al elegir colaborador + horas.
    @Transactional(readOnly = true)
    public java.util.Map<Long, BigDecimal> mapaSueldosBase() {
        return usuarioRepository.findActivosByRolNombre(ROL_COLABORADOR).stream()
                .collect(java.util.stream.Collectors.toMap(Usuario::getId,
                        u -> u.getSueldoBase() == null ? BigDecimal.ZERO : u.getSueldoBase()));
    }

    @Transactional(readOnly = true)
    public List<CandidatoConCosto> listarCandidatosParaProyecto(Proyecto proyecto) {
        int maxAsignaciones = obtenerMaxAsignaciones();
        RmPresupuestoService.ResumenPresupuesto resumenPresupuesto = presupuestoService.calcularResumen(proyecto);

        return usuarioRepository.findActivosByRolNombre(ROL_COLABORADOR).stream()
                .map(colaborador -> {
                    RmColaboradorResumen resumen = crearResumen(colaborador, maxAsignaciones);
                    RmPresupuestoService.CostoAsignacion costo = presupuestoService.calcularCosto(
                            proyecto, colaborador, proyecto.getHorasSemanalesRequeridas());
                    boolean cabe = costo.calculable()
                            && costo.costoTotal().compareTo(resumenPresupuesto.disponible()) <= 0;
                    return new CandidatoConCosto(resumen, colaborador.getSueldoBase(),
                            colaborador.getHorasContratadasSemana(), costo, cabe);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RmColaboradorResumen> listarColaboradoresActivos() {
        int maxAsignaciones = obtenerMaxAsignaciones();

        return usuarioRepository.findActivosByRolNombre(ROL_COLABORADOR)
                .stream()
                .map(colaborador -> crearResumen(colaborador, maxAsignaciones))
                .toList();
    }

    @Transactional(readOnly = true)
    public RmColaboradorDetalle obtenerDetalle(Long colaboradorId) {
        Usuario colaborador = usuarioRepository.findById(colaboradorId)
                .filter(Usuario::isActivo)
                .filter(usuario -> ROL_COLABORADOR.equals(usuario.getRol().getNombre()))
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el colaborador solicitado."));

        int maxAsignaciones = obtenerMaxAsignaciones();
        RmColaboradorResumen resumen = crearResumen(colaborador, maxAsignaciones);

        List<RmColaboradorDetalle.HabilidadDetalle> habilidades = obtenerHabilidades(colaborador)
                .stream()
                .map(item -> new RmColaboradorDetalle.HabilidadDetalle(
                        item.getHabilidad().getNombre(),
                        textoEnum(item.getNivelDominio().name()),
                        textoEnum(item.getEstadoValidacion().name())))
                .toList();

        List<RmColaboradorDetalle.AsignacionDetalle> asignaciones = obtenerAsignacionesActivas(colaborador)
                .stream()
                .map(asignacion -> new RmColaboradorDetalle.AsignacionDetalle(
                        asignacion.getId(),
                        asignacion.getProyecto().getNombre(),
                        asignacion.getHorasSemanales(),
                        textoEnum(asignacion.getEstado().name()),
                        asignacion.getProyecto().getFechaInicio(),
                        asignacion.getProyecto().getFechaFinEstimada()))
                .toList();

        List<RmColaboradorDetalle.ExperienciaDetalle> experiencias =
                experienciaProfesionalRepository.findByColaboradorOrderByFechaInicioDesc(colaborador)
                        .stream()
                        .map(this::crearExperiencia)
                        .toList();

        List<RmColaboradorDetalle.EducacionDetalle> educacion =
                educacionRepository.findByColaboradorAndActivoTrueOrderByFechaInicioDesc(colaborador)
                        .stream()
                        .map(this::crearEducacion)
                        .toList();

        return new RmColaboradorDetalle(
                resumen,
                colaborador.getCorreo(),
                colaborador.getTelefono(),
                colaborador.getDescripcion(),
                colaborador.getHorasContratadasSemana(),
                colaborador.getFechaContratacion(),
                habilidades,
                asignaciones,
                experiencias,
                educacion);
    }

    private RmColaboradorResumen crearResumen(Usuario colaborador, int maxAsignaciones) {
        List<String> habilidades = obtenerHabilidades(colaborador)
                .stream()
                .map(item -> item.getHabilidad().getNombre())
                .toList();

        int asignacionesActivas = obtenerAsignacionesActivas(colaborador).size();
        long certificadosAprobados = certificadoRepository
                .countByColaboradorAndEstado(colaborador, EstadoCertificado.APROBADO);

        NivelExperiencia nivel = colaborador.getNivelExperiencia();
        String nivelCodigo = nivel == null ? "SIN_DEFINIR" : nivel.name();

        return new RmColaboradorResumen(
                colaborador.getId(),
                nombreCompleto(colaborador),
                iniciales(colaborador),
                colaborador.getFotoUrl(),
                valorOAlternativa(colaborador.getCargo() != null ? colaborador.getCargo().getNombre() : null, "Cargo sin registrar"),
                nivel == null ? "Sin definir" : textoNivel(nivel),
                nivelCodigo,
                habilidades,
                colaborador.getHorasDisponibles() == null ? BigDecimal.ZERO : colaborador.getHorasDisponibles(),
                colaborador.getAniosExperiencia(),
                asignacionesActivas,
                maxAsignaciones,
                certificadosAprobados);
    }

    private List<ColaboradorHabilidad> obtenerHabilidades(Usuario colaborador) {
        return colaboradorHabilidadRepository.findByColaboradorAndActivoTrue(colaborador)
                .stream()
                .sorted(Comparator.comparing(item -> item.getHabilidad().getNombre(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<Asignacion> obtenerAsignacionesActivas(Usuario colaborador) {
        return asignacionRepository.findByColaboradorAndEstado(colaborador, EstadoAsignacion.ACTIVA);
    }

    private int obtenerMaxAsignaciones() {
        return configuracionSistemaRepository.findByClave(CLAVE_MAX_ASIGNACIONES)
                .map(configuracion -> parsearEnteroPositivo(configuracion.getValor()))
                .orElse(MAX_ASIGNACIONES_POR_DEFECTO);
    }

    private int parsearEnteroPositivo(String valor) {
        try {
            int numero = Integer.parseInt(valor);
            return numero > 0 ? numero : MAX_ASIGNACIONES_POR_DEFECTO;
        } catch (NumberFormatException ex) {
            return MAX_ASIGNACIONES_POR_DEFECTO;
        }
    }

    private RmColaboradorDetalle.ExperienciaDetalle crearExperiencia(ExperienciaProfesional experiencia) {
        return new RmColaboradorDetalle.ExperienciaDetalle(
                valorOAlternativa(experiencia.getEmpresa(), "Empresa sin registrar"),
                valorOAlternativa(experiencia.getCargo(), "Cargo sin registrar"),
                experiencia.getDescripcion(),
                experiencia.getFechaInicio(),
                experiencia.getFechaFin(),
                experiencia.isActual());
    }

    private RmColaboradorDetalle.EducacionDetalle crearEducacion(Educacion educacion) {
        return new RmColaboradorDetalle.EducacionDetalle(
                educacion.getInstitucion(),
                educacion.getTitulo(),
                educacion.getFechaInicio(),
                educacion.getFechaFin(),
                educacion.isActual(),
                textoEnum(educacion.getEstado().name()),
                educacion.getArchivoUrl());
    }

    private String nombreCompleto(Usuario usuario) {
        String nombre = valorOAlternativa(usuario.getNombre(), "");
        String apellido = valorOAlternativa(usuario.getApellido(), "");
        String completo = (nombre + " " + apellido).trim();
        return completo.isEmpty() ? usuario.getCorreo() : completo;
    }

    private String iniciales(Usuario usuario) {
        String nombre = valorOAlternativa(usuario.getNombre(), "").trim();
        String apellido = valorOAlternativa(usuario.getApellido(), "").trim();
        String iniciales = (nombre.isEmpty() ? "" : nombre.substring(0, 1))
                + (apellido.isEmpty() ? "" : apellido.substring(0, 1));
        return iniciales.isEmpty() ? "CO" : iniciales.toUpperCase();
    }

    private String valorOAlternativa(String valor, String alternativa) {
        return valor == null || valor.isBlank() ? alternativa : valor;
    }

    private String textoEnum(String valor) {
        String texto = valor.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    private String textoNivel(NivelExperiencia nivel) {
        return nivel == NivelExperiencia.SEMI_SENIOR ? "Semi Senior" : textoEnum(nivel.name());
    }
}
