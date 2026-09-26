package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.dto.RmAsignacionView;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.MotivoFinalizacion;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ColaboradorHabilidadRepository;
import com.pucp.skillb_ia.repository.ConfiguracionSistemaRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.DisponibilidadService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RmAsignacionService {

    private static final String ROL_RM = "RESOURCE_MANAGER";
    private static final String ROL_COLABORADOR = "COLABORADOR";
    private static final String CLAVE_MAX_ASIGNACIONES = "MAX_ASIGNACIONES_POR_COLABORADOR";
    private static final int MAX_ASIGNACIONES_POR_DEFECTO = 3;
    private static final BigDecimal MAX_HORAS_SEMANALES = new BigDecimal("168");

    private final AsignacionRepository asignacionRepository;
    private final ProyectoRepository proyectoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ColaboradorHabilidadRepository colaboradorHabilidadRepository;
    private final ConfiguracionSistemaRepository configuracionSistemaRepository;
    private final AuditoriaService auditoriaService;
    private final RmPresupuestoService presupuestoService;
    private final DisponibilidadService disponibilidadService;

    public RmAsignacionService(
            AsignacionRepository asignacionRepository,
            ProyectoRepository proyectoRepository,
            UsuarioRepository usuarioRepository,
            ColaboradorHabilidadRepository colaboradorHabilidadRepository,
            ConfiguracionSistemaRepository configuracionSistemaRepository,
            AuditoriaService auditoriaService,
            RmPresupuestoService presupuestoService,
            DisponibilidadService disponibilidadService) {
        this.asignacionRepository = asignacionRepository;
        this.proyectoRepository = proyectoRepository;
        this.usuarioRepository = usuarioRepository;
        this.colaboradorHabilidadRepository = colaboradorHabilidadRepository;
        this.configuracionSistemaRepository = configuracionSistemaRepository;
        this.auditoriaService = auditoriaService;
        this.presupuestoService = presupuestoService;
        this.disponibilidadService = disponibilidadService;
    }

    @Transactional(readOnly = true)
    public List<RmAsignacionView> listar() {
        int maxAsignaciones = obtenerMaxAsignaciones();
        return asignacionRepository.findAllConDetalleOrderByFechaSolicitudDesc().stream()
                .map(asignacion -> crearVista(asignacion, maxAsignaciones))
                .toList();
    }

    @Transactional(readOnly = true)
    public RmAsignacionView obtener(Long asignacionId) {
        Asignacion asignacion = asignacionRepository.findByIdConDetalle(asignacionId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la asignación solicitada."));
        return crearVista(asignacion, obtenerMaxAsignaciones());
    }

    @Transactional(readOnly = true)
    public List<RmAsignacionView> listarPorColaborador(Long colaboradorId) {
        Usuario colaborador = obtenerColaborador(colaboradorId);
        int maxAsignaciones = obtenerMaxAsignaciones();
        return asignacionRepository.findByColaboradorIdConDetalle(colaborador.getId()).stream()
                .map(asignacion -> crearVista(asignacion, maxAsignaciones))
                .toList();
    }

    // Ids de colaboradores que ya tienen una asignación pendiente o activa en el
    // proyecto: no se les puede proponer otra (ver proponerDesdeRm).
    @Transactional(readOnly = true)
    public Set<Long> colaboradoresConAsignacionVigente(Proyecto proyecto) {
        return asignacionRepository.findByProyecto(proyecto).stream()
                .filter(a -> a.getEstado() == EstadoAsignacion.PENDIENTE || a.getEstado() == EstadoAsignacion.ACTIVA)
                .map(a -> a.getColaborador().getId())
                .collect(Collectors.toSet());
    }

    // Ids de proyectos donde el colaborador ya tiene una asignación pendiente o activa.
    @Transactional(readOnly = true)
    public Set<Long> proyectosConAsignacionVigente(Long colaboradorId) {
        return asignacionRepository.findByColaboradorIdConDetalle(colaboradorId).stream()
                .filter(a -> a.getEstado() == EstadoAsignacion.PENDIENTE || a.getEstado() == EstadoAsignacion.ACTIVA)
                .map(a -> a.getProyecto().getId())
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public List<Proyecto> listarProyectosAsignables() {
        return proyectoRepository.findAllConPmOrderByFechaCreacionDesc().stream()
                .filter(proyecto -> proyecto.getEstado() == EstadoProyecto.ACTIVO
                        || proyecto.getEstado() == EstadoProyecto.EN_ESPERA)
                .toList();
    }

    @Transactional
    public Asignacion proponerDesdeRm(
            Long proyectoId,
            Long colaboradorId,
            BigDecimal horasSemanales,
            String justificacion,
            String motivoCapacidad,
            Long rmId) {
        Usuario rm = obtenerRm(rmId);
        Proyecto proyecto = obtenerProyectoAsignable(proyectoId);
        Usuario colaborador = obtenerColaborador(colaboradorId);
        validarHoras(horasSemanales);
        // A20 / sección 13: nunca se propone sin presupuesto suficiente — el RM
        // reserva el costo de inmediato porque proponerDesdeRm() auto-aprueba por el RM.
        presupuestoService.validarPresupuestoSuficiente(proyecto, colaborador, horasSemanales);

        boolean yaTieneAsignacion = asignacionRepository
                .existsByProyectoAndColaboradorAndEstadoIn(
                        proyecto, colaborador,
                        EnumSet.of(EstadoAsignacion.PENDIENTE, EstadoAsignacion.ACTIVA));
        if (yaTieneAsignacion) {
            throw new IllegalStateException("El colaborador ya tiene una asignación pendiente o activa en este proyecto.");
        }

        int activas = (int) asignacionRepository.countByColaboradorAndEstado(
                colaborador, EstadoAsignacion.ACTIVA);
        boolean excedeCapacidad = activas >= obtenerMaxAsignaciones()
                || horasSemanales.compareTo(horasDisponibles(colaborador)) > 0;
        String mensaje = construirMensaje(justificacion, motivoCapacidad, excedeCapacidad);

        Asignacion asignacion = new Asignacion();
        asignacion.setProyecto(proyecto);
        asignacion.setColaborador(colaborador);
        asignacion.setHorasSemanales(horasSemanales);
        asignacion.setOrigen(OrigenAsignacion.PROPUESTA_RM);
        asignacion.setMensajeSolicitud(mensaje);
        asignacion.setEstado(EstadoAsignacion.PENDIENTE);
        asignacion.setAprobadoPorRm(true);
        asignacion.setFechaAprobacionRm(LocalDateTime.now());

        Asignacion guardada = asignacionRepository.save(asignacion);
        auditoriaService.registrar(
                rm, "PROPUESTA_ASIGNACION", "ASIGNACION", guardada.getId(),
                "El RM propuso a " + nombreCompleto(colaborador)
                        + " para el proyecto " + proyecto.getNombre() + ".");
        return guardada;
    }

    @Transactional
    public void aprobar(Long asignacionId, String motivoCapacidad, Long rmId) {
        Usuario rm = obtenerRm(rmId);
        Asignacion asignacion = obtenerEntidad(asignacionId);
        validarDecisionRm(asignacion);
        obtenerProyectoAsignable(asignacion.getProyecto().getId());
        // Al aprobar, el RM reserva el costo de esta asignación (sección 6/7.1).
        presupuestoService.validarPresupuestoSuficiente(
                asignacion.getProyecto(), asignacion.getColaborador(), asignacion.getHorasSemanales());

        int activas = (int) asignacionRepository.countByColaboradorAndEstado(
                asignacion.getColaborador(), EstadoAsignacion.ACTIVA);
        boolean excedeCapacidad = activas >= obtenerMaxAsignaciones()
                || asignacion.getHorasSemanales().compareTo(horasDisponibles(asignacion.getColaborador())) > 0;

        asignacion.setMensajeSolicitud(construirMensaje(
                asignacion.getMensajeSolicitud(), motivoCapacidad, excedeCapacidad));
        asignacion.setAprobadoPorRm(true);
        asignacion.setFechaAprobacionRm(LocalDateTime.now());

        if (asignacion.isAprobadoPorPm()) {
            validarSinAsignacionActivaDuplicada(asignacion);
            asignacion.setEstado(EstadoAsignacion.ACTIVA);
            asignacion.setFechaActivacion(LocalDateTime.now());
        }

        asignacionRepository.save(asignacion);

        //En caso de que quede activa, recalculamos la disponibilidad del colaborador
        if (asignacion.getEstado() == EstadoAsignacion.ACTIVA) {
            disponibilidadService.recalcular(asignacion.getColaborador());
        }


        auditoriaService.registrar(
                rm, "APROBACION_ASIGNACION", "ASIGNACION", asignacion.getId(),
                asignacion.getEstado() == EstadoAsignacion.ACTIVA
                        ? "El RM aprobó la asignación; quedó activa."
                        : "El RM aprobó la asignación; continúa pendiente del PM.");
    }

    @Transactional
    public void rechazar(Long asignacionId, String motivo, Long rmId) {
        Usuario rm = obtenerRm(rmId);
        Asignacion asignacion = obtenerEntidad(asignacionId);
        validarDecisionRm(asignacion);
        String motivoLimpio = textoObligatorio(motivo, "Debes indicar el motivo del rechazo.", 300);

        asignacion.setEstado(EstadoAsignacion.RECHAZADA);
        asignacion.setMotivoRechazo(motivoLimpio);
        asignacion.setRechazadoPor(rm);
        asignacionRepository.save(asignacion);

        auditoriaService.registrar(
                rm, "RECHAZO_ASIGNACION", "ASIGNACION", asignacion.getId(),
                "El RM rechazó la asignación. Motivo: " + motivoLimpio);
    }

    @Transactional
    public void finalizar(
            Long asignacionId,
            MotivoFinalizacion motivo,
            String observacion,
            Long rmId) {
        Usuario rm = obtenerRm(rmId);
        Asignacion asignacion = obtenerEntidad(asignacionId);
        if (asignacion.getEstado() != EstadoAsignacion.ACTIVA) {
            throw new IllegalStateException("Solo se puede finalizar una asignación activa.");
        }
        if (motivo == null) throw new IllegalArgumentException("Selecciona un motivo de finalización.");

        asignacion.setEstado(EstadoAsignacion.FINALIZADA);
        asignacion.setMotivoFinalizacion(motivo);
        asignacion.setDesasignadoPor(rm);
        asignacion.setFechaFinalizacion(LocalDateTime.now());
        asignacionRepository.save(asignacion);

        String detalle = "El RM finalizó la asignación. Motivo: " + motivo.name();

        //En caso de que se desasigne a un colaborador de un proyecto, tendremos que recalcular su disponibilidad
        disponibilidadService.recalcular(asignacion.getColaborador());


        if (observacion != null && !observacion.isBlank()) {
            detalle += ". Observación: " + limitar(observacion.trim(), 220);
        }
        auditoriaService.registrar(
                rm, "FINALIZACION_ASIGNACION", "ASIGNACION", asignacion.getId(), detalle);
    }

    private RmAsignacionView crearVista(Asignacion asignacion, int maxAsignaciones) {
        Usuario colaborador = asignacion.getColaborador();
        int activas = (int) asignacionRepository.countByColaboradorAndEstado(
                colaborador, EstadoAsignacion.ACTIVA);
        BigDecimal horasComprometidas = asignacionRepository
                .findByColaboradorAndEstado(colaborador, EstadoAsignacion.ACTIVA).stream()
                .map(Asignacion::getHorasSemanales)
                .filter(horas -> horas != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int equipoActual = (int) asignacionRepository.countByProyectoAndEstado(
                asignacion.getProyecto(), EstadoAsignacion.ACTIVA);
        int vacantes = Math.max(0,
                asignacion.getProyecto().getColaboradoresRequeridos() - equipoActual);
        List<String> habilidades = colaboradorHabilidadRepository
                .findByColaboradorAndActivoTrue(colaborador).stream()
                .map(item -> item.getHabilidad().getNombre())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        return new RmAsignacionView(
                asignacion,
                nombreCompleto(colaborador),
                iniciales(colaborador),
                nombreCompleto(asignacion.getProyecto().getPm()),
                activas,
                maxAsignaciones,
                horasComprometidas,
                equipoActual,
                vacantes,
                habilidades);
    }

    private Asignacion obtenerEntidad(Long asignacionId) {
        return asignacionRepository.findByIdConDetalle(asignacionId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la asignación solicitada."));
    }

    private Proyecto obtenerProyectoAsignable(Long proyectoId) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el proyecto seleccionado."));
        if (proyecto.getEstado() != EstadoProyecto.ACTIVO
                && proyecto.getEstado() != EstadoProyecto.EN_ESPERA) {
            throw new IllegalStateException("Solo se pueden proponer asignaciones para proyectos activos o en espera.");
        }
        return proyecto;
    }

    private Usuario obtenerColaborador(Long colaboradorId) {
        return usuarioRepository.findById(colaboradorId)
                .filter(Usuario::isActivo)
                .filter(usuario -> usuario.getRol() != null
                        && ROL_COLABORADOR.equals(usuario.getRol().getNombre()))
                .orElseThrow(() -> new IllegalArgumentException("No se encontró un colaborador activo válido."));
    }

    private Usuario obtenerRm(Long rmId) {
        return usuarioRepository.findById(rmId)
                .filter(Usuario::isActivo)
                .filter(usuario -> usuario.getRol() != null && ROL_RM.equals(usuario.getRol().getNombre()))
                .orElseThrow(() -> new IllegalArgumentException("El usuario actual no es un Resource Manager válido."));
    }

    private void validarDecisionRm(Asignacion asignacion) {
        if (asignacion.getEstado() != EstadoAsignacion.PENDIENTE) {
            throw new IllegalStateException("La asignación ya fue resuelta.");
        }
        if (asignacion.isAprobadoPorRm() || asignacion.getOrigen() == OrigenAsignacion.PROPUESTA_RM) {
            throw new IllegalStateException("Esta asignación no está pendiente de decisión del RM.");
        }
    }

    private void validarSinAsignacionActivaDuplicada(Asignacion asignacion) {
        if (asignacionRepository.existsByProyectoAndColaboradorAndEstado(
                asignacion.getProyecto(), asignacion.getColaborador(), EstadoAsignacion.ACTIVA)) {
            throw new IllegalStateException("El colaborador ya tiene una asignación activa en este proyecto.");
        }
    }

    private void validarHoras(BigDecimal horas) {
        if (horas == null || horas.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Las horas semanales deben ser mayores que cero.");
        }
        if (horas.compareTo(MAX_HORAS_SEMANALES) > 0) {
            throw new IllegalArgumentException("Las horas semanales no pueden superar 168.");
        }
    }

    private String construirMensaje(String base, String motivoCapacidad, boolean motivoRequerido) {
        String mensaje = base == null ? "" : base.trim();
        if (mensaje.length() > 500) {
            throw new IllegalArgumentException("La justificación no puede superar 500 caracteres.");
        }
        if (!motivoRequerido) return mensaje.isEmpty() ? null : mensaje;

        String motivo = textoObligatorio(
                motivoCapacidad,
                "La propuesta supera la disponibilidad o el límite de asignaciones; debes justificar la excepción.",
                300);
        String combinado = mensaje + (mensaje.isEmpty() ? "" : "\n")
                + "Justificación de capacidad: " + motivo;
        if (combinado.length() > 500) {
            throw new IllegalArgumentException("La justificación total no puede superar 500 caracteres.");
        }
        return combinado;
    }

    private String textoObligatorio(String valor, String mensaje, int maximo) {
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException(mensaje);
        String limpio = valor.trim();
        if (limpio.length() > maximo) {
            throw new IllegalArgumentException("El texto no puede superar " + maximo + " caracteres.");
        }
        return limpio;
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

    private BigDecimal horasDisponibles(Usuario colaborador) {
        return colaborador.getHorasDisponibles() == null
                ? BigDecimal.ZERO : colaborador.getHorasDisponibles();
    }

    private String nombreCompleto(Usuario usuario) {
        String nombre = usuario.getNombre() == null ? "" : usuario.getNombre().trim();
        String apellido = usuario.getApellido() == null ? "" : usuario.getApellido().trim();
        String completo = (nombre + " " + apellido).trim();
        return completo.isEmpty() ? usuario.getCorreo() : completo;
    }

    private String iniciales(Usuario usuario) {
        String nombre = usuario.getNombre() == null ? "" : usuario.getNombre().trim();
        String apellido = usuario.getApellido() == null ? "" : usuario.getApellido().trim();
        String iniciales = (nombre.isEmpty() ? "" : nombre.substring(0, 1))
                + (apellido.isEmpty() ? "" : apellido.substring(0, 1));
        return iniciales.isEmpty() ? "CO" : iniciales.toUpperCase();
    }

    private String limitar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
