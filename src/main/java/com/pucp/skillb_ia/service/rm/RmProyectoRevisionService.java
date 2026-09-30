package com.pucp.skillb_ia.service.rm;

import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.MotivoFinalizacion;
import com.pucp.skillb_ia.repository.ActividadRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.CierreAsignacionesService;
import com.pucp.skillb_ia.service.NotificacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class RmProyectoRevisionService {
    private static final String ROL_RM = "RESOURCE_MANAGER";
    private static final BigDecimal PRESUPUESTO_MAXIMO = new BigDecimal("9999999999.99");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ProyectoRepository proyectoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;
    private final RmPresupuestoService presupuestoService;
    private final NotificacionService notificacionService;
    private final ActividadRepository actividadRepository;
    private final CierreAsignacionesService cierreAsignacionesService;

    public RmProyectoRevisionService(ProyectoRepository proyectoRepository,
                                     UsuarioRepository usuarioRepository,
                                     AuditoriaService auditoriaService,
                                     RmPresupuestoService presupuestoService,
                                     NotificacionService notificacionService,
                                     ActividadRepository actividadRepository,
                                     CierreAsignacionesService cierreAsignacionesService) {
        this.proyectoRepository = proyectoRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaService = auditoriaService;
        this.presupuestoService = presupuestoService;
        this.notificacionService = notificacionService;
        this.actividadRepository = actividadRepository;
        this.cierreAsignacionesService = cierreAsignacionesService;
    }

    @Transactional
    public EstadoProyecto asignarPresupuesto(Long proyectoId, BigDecimal presupuesto, Long rmId) {
        Usuario rm = obtenerRm(rmId);
        Proyecto proyecto = obtenerConPresupuestoEditable(proyectoId);
        BigDecimal presupuestoValidado = validarPresupuesto(presupuesto);
        RmPresupuestoService.ResumenPresupuesto resumen = presupuestoService.calcularResumen(proyecto);
        BigDecimal comprometidoYReservado = resumen.comprometido().add(resumen.reservado());
        if (presupuestoValidado.compareTo(comprometidoYReservado) < 0) {
            throw new IllegalArgumentException(
                    "El presupuesto no puede ser menor que el monto ya comprometido o reservado (S/ "
                            + comprometidoYReservado.setScale(2, RoundingMode.HALF_UP).toPlainString() + ").");
        }
        String anterior = proyecto.getPresupuesto() == null ? null : proyecto.getPresupuesto().toPlainString();

        proyecto.setPresupuesto(presupuestoValidado);
        proyectoRepository.save(proyecto);
        String accion = anterior == null ? "ASIGNAR_PRESUPUESTO_PROYECTO" : "ACTUALIZAR_PRESUPUESTO_PROYECTO";
        auditoriaService.registrar(rm, accion, "PROYECTO", proyectoId,
                "Se registró el presupuesto del proyecto " + proyecto.getNombre(),
                anterior, presupuestoValidado.toPlainString(), null);
        return proyecto.getEstado();
    }

    @Transactional
    public void aprobar(Long proyectoId, Long rmId) {
        Usuario rm = obtenerRm(rmId);
        Proyecto proyecto = obtenerEnRevision(proyectoId);
        if (proyecto.getPresupuesto() == null || proyecto.getPresupuesto().signum() <= 0) {
            throw new IllegalArgumentException("Asigna un presupuesto válido antes de aprobar el proyecto.");
        }

        proyecto.setEstado(EstadoProyecto.ACTIVO);
        proyecto.setRmRevisor(rm);
        proyecto.setMotivoRechazo(null);
        proyectoRepository.save(proyecto);

        notificacionService.crear(proyecto.getPm(), "PROYECTO_APROBADO", CategoriaNotificacion.PROYECTO,
                "Proyecto aprobado",
                "El Resource Manager aprobó tu proyecto \"" + proyecto.getNombre() + "\".",
                "PROYECTO", proyecto.getId());

        auditoriaService.registrar(rm, "APROBAR_PROYECTO", "PROYECTO", proyectoId,
                "Proyecto aprobado", "EN_REVISION", "ACTIVO", null);

    }

    @Transactional
    public void rechazar(Long proyectoId, String motivo, Long rmId) {
        Usuario rm = obtenerRm(rmId);
        Proyecto proyecto = obtenerEnRevision(proyectoId);
        String motivoValidado = motivo == null ? "" : motivo.trim();
        if (motivoValidado.isEmpty()) {
            throw new IllegalArgumentException("Debes registrar el motivo del rechazo.");
        }
        if (motivoValidado.length() > 500) {
            throw new IllegalArgumentException("El motivo del rechazo no puede superar los 500 caracteres.");
        }

        proyecto.setEstado(EstadoProyecto.RECHAZADO);
        proyecto.setRmRevisor(rm);
        proyecto.setMotivoRechazo(motivoValidado);
        proyectoRepository.save(proyecto);

        notificacionService.crear(proyecto.getPm(), "PROYECTO_RECHAZADO", CategoriaNotificacion.PROYECTO,
                "Proyecto rechazado",
                "El Resource Manager rechazó tu proyecto \"" + proyecto.getNombre()
                        + "\". Motivo: " + motivoValidado,
                "PROYECTO", proyecto.getId());

        String detalleAuditoria = "Proyecto rechazado. Motivo: " + motivoValidado;


        auditoriaService.registrar(rm, "RECHAZAR_PROYECTO", "PROYECTO", proyectoId,
                detalleAuditoria.substring(0, Math.min(detalleAuditoria.length(), 500)),
                "EN_REVISION", "RECHAZADO", null);
    }

    // TASK-021: solo el RM cambia las fechas. Todas las validaciones van antes de modificar
    // el proyecto: un cambio inválido no guarda, no audita ni notifica.
    @Transactional
    public void cambiarFechas(Long proyectoId, LocalDate fechaInicio, LocalDate fechaFin, Long rmId) {
        Usuario rm = obtenerRm(rmId);
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el proyecto solicitado."));
        if (proyecto.getEstado() != EstadoProyecto.EN_REVISION
                && proyecto.getEstado() != EstadoProyecto.ACTIVO
                && proyecto.getEstado() != EstadoProyecto.EN_ESPERA) {
            throw new IllegalStateException("Las fechas no pueden modificarse en el estado actual del proyecto.");
        }
        if (fechaInicio == null || fechaFin == null) {
            throw new IllegalArgumentException("Debes indicar la fecha de inicio y la fecha de fin del proyecto.");
        }
        // Un proyecto ya iniciado puede conservar su inicio pasado (p. ej., solo extender el fin).
        if (fechaInicio.isBefore(LocalDate.now()) && !fechaInicio.equals(proyecto.getFechaInicio())) {
            throw new IllegalArgumentException("La fecha de inicio no puede ser anterior a la fecha actual.");
        }
        if (!fechaFin.isAfter(fechaInicio)) {
            throw new IllegalArgumentException("La fecha de fin debe ser posterior a la fecha de inicio.");
        }

        List<Actividad> fueraDeRango = actividadRepository.findByProyectoOrderByFechaLimiteAsc(proyecto).stream()
                .filter(a -> a.getFechaLimite() != null
                        && (a.getFechaLimite().isBefore(fechaInicio) || a.getFechaLimite().isAfter(fechaFin)))
                .toList();
        if (!fueraDeRango.isEmpty()) {
            Actividad primera = fueraDeRango.get(0);
            throw new IllegalArgumentException("No se cambiaron las fechas: " + fueraDeRango.size()
                    + " actividad(es) vencen fuera del nuevo rango, por ejemplo \"" + primera.getTitulo()
                    + "\" con fecha límite " + primera.getFechaLimite().format(FORMATO_FECHA) + ".");
        }

        RmPresupuestoService.ResumenPresupuesto resumen =
                presupuestoService.calcularResumenConFechas(proyecto, fechaInicio, fechaFin);
        BigDecimal comprometidoYReservado = resumen.comprometido().add(resumen.reservado());
        if (comprometidoYReservado.compareTo(resumen.total()) > 0) {
            throw new IllegalArgumentException("No se cambiaron las fechas: con el nuevo rango, el costo comprometido y reservado (S/ "
                    + comprometidoYReservado.setScale(2, RoundingMode.HALF_UP).toPlainString()
                    + ") supera el presupuesto del proyecto (S/ "
                    + resumen.total().setScale(2, RoundingMode.HALF_UP).toPlainString() + ").");
        }

        String anterior = describirFechas(proyecto.getFechaInicio(), proyecto.getFechaFinEstimada());
        String nuevo = describirFechas(fechaInicio, fechaFin);
        proyecto.setFechaInicio(fechaInicio);
        proyecto.setFechaFinEstimada(fechaFin);
        proyectoRepository.save(proyecto);

        auditoriaService.registrar(rm, "ACTUALIZAR_FECHAS_PROYECTO", "PROYECTO", proyectoId,
                "Se actualizaron las fechas del proyecto " + proyecto.getNombre(), anterior, nuevo, null);
        notificacionService.crear(proyecto.getPm(), "PROYECTO_FECHAS_ACTUALIZADAS", CategoriaNotificacion.PROYECTO,
                "Fechas del proyecto actualizadas",
                "El Resource Manager cambió las fechas de tu proyecto \"" + proyecto.getNombre() + "\": del "
                        + fechaInicio.format(FORMATO_FECHA) + " al " + fechaFin.format(FORMATO_FECHA) + ".",
                "PROYECTO", proyecto.getId());
    }

    // Cancelar y finalizar desde el RM: solo en ACTIVO o EN_ESPERA.
    public static boolean esProyectoCerrable(Proyecto proyecto) {
        return proyecto.getEstado() == EstadoProyecto.ACTIVO
                || proyecto.getEstado() == EstadoProyecto.EN_ESPERA;
    }

    // Actividades que impiden finalizar (todas menos COMPLETADA).
    @Transactional(readOnly = true)
    public long contarActividadesAbiertas(Proyecto proyecto) {
        return actividadRepository.countByProyectoAndEstado(proyecto, EstadoActividad.PENDIENTE)
                + actividadRepository.countByProyectoAndEstado(proyecto, EstadoActividad.EN_PROGRESO)
                + actividadRepository.countByProyectoAndEstado(proyecto, EstadoActividad.EN_REVISION);
    }

    // El RM cancela un proyecto ACTIVO o EN_ESPERA. Exige motivo y el nombre exacto del proyecto.
    // En la misma transacción cierra las asignaciones abiertas (como TASK-041) y notifica al PM.
    @Transactional
    public CierreAsignacionesService.Resultado cancelar(Long proyectoId, String motivo,
                                                         String confirmacionNombre, Long rmId) {
        return cerrar(proyectoId, motivo, confirmacionNombre, rmId, false);
    }

    // El RM finaliza un proyecto ACTIVO o EN_ESPERA sin actividades pendientes, en progreso ni en revisión.
    @Transactional
    public CierreAsignacionesService.Resultado finalizar(Long proyectoId, String motivo,
                                                          String confirmacionNombre, Long rmId) {
        return cerrar(proyectoId, motivo, confirmacionNombre, rmId, true);
    }

    // Todas las validaciones van antes de modificar: un intento inválido no cambia nada.
    private CierreAsignacionesService.Resultado cerrar(Long proyectoId, String motivo, String confirmacionNombre,
                                                       Long rmId, boolean finalizar) {
        String accion = finalizar ? "finalizar" : "cancelar";
        Usuario rm = obtenerRm(rmId);
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el proyecto solicitado."));
        if (!esProyectoCerrable(proyecto)) {
            throw new IllegalStateException("Solo se pueden " + accion + " proyectos activos o en espera.");
        }
        String motivoValidado = motivo == null ? "" : motivo.trim();
        if (motivoValidado.isEmpty()) {
            throw new IllegalArgumentException("Debes indicar el motivo para " + accion + " el proyecto.");
        }
        if (motivoValidado.length() > 500) {
            throw new IllegalArgumentException("El motivo no puede superar los 500 caracteres.");
        }
        if (confirmacionNombre == null || !confirmacionNombre.trim().equals(proyecto.getNombre().trim())) {
            throw new IllegalArgumentException(
                    "Para confirmar, escribe el nombre exacto del proyecto. No se realizó ningún cambio.");
        }
        if (finalizar) {
            long abiertas = contarActividadesAbiertas(proyecto);
            if (abiertas > 0) {
                throw new IllegalStateException("No se puede finalizar el proyecto: " + abiertas
                        + " actividad(es) siguen pendientes, en progreso o en revisión.");
            }
        }

        EstadoProyecto anterior = proyecto.getEstado();
        EstadoProyecto nuevo = finalizar ? EstadoProyecto.FINALIZADO : EstadoProyecto.CANCELADO;
        String cierre = finalizar ? "finalizado" : "cancelado";
        proyecto.setEstado(nuevo);
        proyectoRepository.save(proyecto);

        // El enum no tiene PROYECTO_FINALIZADO (agregarlo exige cambiar el CHECK del esquema): se usa OTRO.
        CierreAsignacionesService.Resultado resultado = cierreAsignacionesService.cerrarAbiertas(proyecto, rm, cierre,
                finalizar ? MotivoFinalizacion.OTRO : MotivoFinalizacion.PROYECTO_CANCELADO);

        notificacionService.crear(proyecto.getPm(), finalizar ? "PROYECTO_FINALIZADO" : "PROYECTO_CANCELADO",
                CategoriaNotificacion.PROYECTO, finalizar ? "Proyecto finalizado" : "Proyecto cancelado",
                limitar("El Resource Manager " + (finalizar ? "finalizó" : "canceló") + " tu proyecto \""
                        + proyecto.getNombre() + "\". Motivo: " + motivoValidado, 400),
                "PROYECTO", proyecto.getId());

        auditoriaService.registrar(rm, finalizar ? "FINALIZAR_PROYECTO" : "CANCELAR_PROYECTO", "PROYECTO", proyectoId,
                limitar("Proyecto " + cierre + " por el RM. Asignaciones pendientes rechazadas: "
                        + resultado.pendientesRechazadas() + ". Asignaciones activas finalizadas: "
                        + resultado.activasFinalizadas() + ". Motivo: " + motivoValidado, 500),
                anterior.name(), nuevo.name(), null);
        return resultado;
    }

    private static String limitar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 3) + "...";
    }

    private static String describirFechas(LocalDate inicio, LocalDate fin) {
        return "Inicio: " + (inicio != null ? inicio.format(FORMATO_FECHA) : "sin definir")
                + " | Fin: " + (fin != null ? fin.format(FORMATO_FECHA) : "sin definir");
    }

    private Proyecto obtenerEnRevision(Long proyectoId) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el proyecto solicitado."));
        if (proyecto.getEstado() != EstadoProyecto.EN_REVISION) {
            throw new IllegalStateException("El proyecto ya no se encuentra en revisión.");
        }
        return proyecto;
    }

    private Proyecto obtenerConPresupuestoEditable(Long proyectoId) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el proyecto solicitado."));
        if (proyecto.getEstado() != EstadoProyecto.EN_REVISION
                && proyecto.getEstado() != EstadoProyecto.ACTIVO
                && proyecto.getEstado() != EstadoProyecto.EN_ESPERA) {
            throw new IllegalStateException("El presupuesto no puede modificarse en el estado actual del proyecto.");
        }
        return proyecto;
    }

    private Usuario obtenerRm(Long rmId) {
        if (rmId == null) throw new IllegalArgumentException("Debes iniciar sesión como Resource Manager.");
        return usuarioRepository.findById(rmId)
                .filter(Usuario::isActivo)
                .filter(usuario -> ROL_RM.equals(usuario.getRol().getNombre()))
                .orElseThrow(() -> new IllegalArgumentException("El usuario autenticado no es un Resource Manager activo."));
    }

    private BigDecimal validarPresupuesto(BigDecimal presupuesto) {
        if (presupuesto == null || presupuesto.signum() <= 0) {
            throw new IllegalArgumentException("El presupuesto debe ser mayor que cero.");
        }
        // Se rechaza antes de redondear: "1.000" o "12345.675" no deben guardarse como otro monto.
        if (presupuesto.scale() > 2) {
            throw new IllegalArgumentException("El presupuesto admite como máximo 2 decimales.");
        }
        if (presupuesto.compareTo(PRESUPUESTO_MAXIMO) > 0) {
            throw new IllegalArgumentException("El presupuesto supera el monto máximo permitido.");
        }
        return presupuesto.setScale(2, RoundingMode.HALF_UP);
    }
}
