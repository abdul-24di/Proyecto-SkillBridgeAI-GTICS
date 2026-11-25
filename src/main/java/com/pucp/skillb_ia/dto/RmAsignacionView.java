package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Fila de la bandeja de asignaciones del Resource Manager y de su detalle.
 * La arma {@code RmAsignacionService} y la usan las vistas {@code rm-asignaciones*}.
 * Envuelve la {@link Asignacion} y añade la carga del colaborador y del proyecto,
 * además de los textos y clases CSS que necesita la vista.
 */
public class RmAsignacionView {

    private final Asignacion asignacion;
    private final String colaboradorNombre;
    private final String colaboradorIniciales;
    private final String projectManagerNombre;
    /** Asignaciones ACTIVAS del colaborador en el momento de armar la vista. */
    private final int asignacionesActivas;
    /** Tope de asignaciones activas configurado para el colaborador. */
    private final int maxAsignaciones;
    /** Horas semanales que el colaborador ya tiene comprometidas en otras asignaciones activas. */
    private final BigDecimal horasComprometidas;
    /** Integrantes activos del proyecto. */
    private final int equipoActual;
    /** Puestos que aún faltan cubrir en el proyecto. */
    private final int vacantes;
    private final List<String> habilidades;

    public RmAsignacionView(
            Asignacion asignacion,
            String colaboradorNombre,
            String colaboradorIniciales,
            String projectManagerNombre,
            int asignacionesActivas,
            int maxAsignaciones,
            BigDecimal horasComprometidas,
            int equipoActual,
            int vacantes,
            List<String> habilidades) {
        this.asignacion = asignacion;
        this.colaboradorNombre = colaboradorNombre;
        this.colaboradorIniciales = colaboradorIniciales;
        this.projectManagerNombre = projectManagerNombre;
        this.asignacionesActivas = asignacionesActivas;
        this.maxAsignaciones = maxAsignaciones;
        this.horasComprometidas = horasComprometidas;
        this.equipoActual = equipoActual;
        this.vacantes = vacantes;
        this.habilidades = List.copyOf(habilidades);
    }

    public Asignacion getAsignacion() { return asignacion; }
    public String getColaboradorNombre() { return colaboradorNombre; }
    public String getColaboradorIniciales() { return colaboradorIniciales; }
    public String getProjectManagerNombre() { return projectManagerNombre; }
    public int getAsignacionesActivas() { return asignacionesActivas; }
    public int getMaxAsignaciones() { return maxAsignaciones; }
    public BigDecimal getHorasComprometidas() { return horasComprometidas; }
    public int getEquipoActual() { return equipoActual; }
    public int getVacantes() { return vacantes; }
    public List<String> getHabilidades() { return habilidades; }

    /**
     * Pestaña de la bandeja a la que pertenece la asignación.
     *
     * @return {@code "pending"} si está PENDIENTE, {@code "active"} si está ACTIVA
     *         y {@code "history"} en cualquier otro estado
     */
    public String getGrupo() {
        if (asignacion.getEstado() == EstadoAsignacion.PENDIENTE) return "pending";
        if (asignacion.getEstado() == EstadoAsignacion.ACTIVA) return "active";
        return "history";
    }

    /**
     * Indica si el RM debe aprobar o rechazar la asignación: está PENDIENTE, el RM aún no la aprobó
     * y no la propuso él mismo (una propuesta del RM ya cuenta con su aprobación).
     *
     * @return {@code true} si la decisión del RM está pendiente
     */
    public boolean isRequiereDecisionRm() {
        return asignacion.getEstado() == EstadoAsignacion.PENDIENTE
                && !asignacion.isAprobadoPorRm()
                && asignacion.getOrigen() != OrigenAsignacion.PROPUESTA_RM;
    }

    /**
     * Indica si la asignación ya tiene la aprobación del RM y solo espera la del PM.
     *
     * @return {@code true} si está PENDIENTE, aprobada por el RM y sin aprobación del PM
     */
    public boolean isPendientePm() {
        return asignacion.getEstado() == EstadoAsignacion.PENDIENTE
                && asignacion.isAprobadoPorRm()
                && !asignacion.isAprobadoPorPm();
    }

    /** @return {@code true} si la asignación nació como postulación del propio colaborador */
    public boolean isSolicitudColaborador() {
        return asignacion.getOrigen() == OrigenAsignacion.SOLICITADA_COLABORADOR;
    }

    public boolean isActiva() { return asignacion.getEstado() == EstadoAsignacion.ACTIVA; }

    /**
     * Aviso de carga: el colaborador ya alcanzó su tope de asignaciones activas.
     *
     * @return {@code true} si las activas son iguales o mayores que el máximo
     */
    public boolean isSuperaLimite() { return asignacionesActivas >= maxAsignaciones; }

    /**
     * Aviso de horas: las horas semanales pedidas superan las horas disponibles del colaborador.
     *
     * @return {@code true} si la asignación tiene horas y son mayores que {@link #getHorasDisponibles()}
     */
    public boolean isSuperaDisponibilidad() {
        BigDecimal disponibles = getHorasDisponibles();
        return asignacion.getHorasSemanales() != null
                && asignacion.getHorasSemanales().compareTo(disponibles) > 0;
    }

    /** @return horas disponibles del colaborador, o cero si no están registradas */
    public BigDecimal getHorasDisponibles() {
        return asignacion.getColaborador().getHorasDisponibles() == null
                ? BigDecimal.ZERO : asignacion.getColaborador().getHorasDisponibles();
    }

    /** @return horas contratadas por semana del colaborador, o cero si no están registradas */
    public BigDecimal getHorasContratadas() {
        return asignacion.getColaborador().getHorasContratadasSemana() == null
                ? BigDecimal.ZERO : asignacion.getColaborador().getHorasContratadasSemana();
    }

    /** @return origen legible de la asignación (por ejemplo, "Propuesta del PM") */
    public String getOrigenTexto() {
        return switch (asignacion.getOrigen()) {
            case PROPUESTA_PM -> "Propuesta del PM";
            case PROPUESTA_RM -> "Propuesta del RM";
            case SOLICITADA_COLABORADOR -> "Solicitud del colaborador";
        };
    }

    /** @return código corto del origen ({@code PM}, {@code RM} o {@code Colaborador}), usado en el filtro de la bandeja */
    public String getOrigenCodigo() {
        return switch (asignacion.getOrigen()) {
            case PROPUESTA_PM -> "PM";
            case PROPUESTA_RM -> "RM";
            case SOLICITADA_COLABORADOR -> "Colaborador";
        };
    }

    /** @return clase CSS de Tabler de la insignia del origen */
    public String getOrigenClase() {
        return switch (asignacion.getOrigen()) {
            case PROPUESTA_PM -> "bg-blue-lt";
            case PROPUESTA_RM -> "bg-azure-lt";
            case SOLICITADA_COLABORADOR -> "bg-purple-lt";
        };
    }

    /**
     * Estado legible. Una asignación pendiente se distingue según quién debe decidir:
     * "Pendiente RM" o "Pendiente PM"; el resto usa el nombre del estado.
     *
     * @return texto del estado para la vista
     */
    public String getEstadoTexto() {
        if (isRequiereDecisionRm()) return "Pendiente RM";
        if (isPendientePm()) return "Pendiente PM";
        return switch (asignacion.getEstado()) {
            case PENDIENTE -> "Pendiente";
            case ACTIVA -> "Activa";
            case RECHAZADA -> "Rechazada";
            case FINALIZADA -> "Finalizada";
        };
    }

    /**
     * Estado que ve el RM en la bandeja y valor del filtro "estado": una postulación
     * pendiente sin ninguna aprobación se muestra como "Pendiente RM y PM" (TASK-027).
     *
     * @return texto del estado en la bandeja; en otros casos, {@link #getEstadoTexto()}
     */
    public String getEstadoBandeja() {
        if (isSolicitudColaborador()
                && asignacion.getEstado() == EstadoAsignacion.PENDIENTE
                && !asignacion.isAprobadoPorPm()
                && !asignacion.isAprobadoPorRm()) {
            return "Pendiente RM y PM";
        }
        return getEstadoTexto();
    }

    /** @return clase CSS de la insignia del estado, con la misma distinción que {@link #getEstadoTexto()} */
    public String getEstadoClase() {
        if (isRequiereDecisionRm()) return "bg-yellow-lt";
        if (isPendientePm()) return "bg-blue-lt";
        return switch (asignacion.getEstado()) {
            case ACTIVA -> "bg-green-lt";
            case RECHAZADA -> "bg-red-lt text-red";
            case FINALIZADA -> "bg-secondary-lt";
            case PENDIENTE -> "bg-yellow-lt";
        };
    }

    /**
     * Días de participación (TASK-057), desde la activación hasta la finalización, o hasta hoy
     * si sigue activa. Nunca es negativo.
     *
     * @return días en el proyecto, o {@code null} si la asignación nunca se activó
     */
    public Long getDiasEnProyecto() {
        if (asignacion.getFechaActivacion() == null) return null;
        LocalDate fin = asignacion.getFechaFinalizacion() != null
                ? asignacion.getFechaFinalizacion().toLocalDate() : LocalDate.now();
        return Math.max(ChronoUnit.DAYS.between(asignacion.getFechaActivacion().toLocalDate(), fin), 0);
    }

    /** @return motivo de finalización legible, o {@code null} si la asignación no se finalizó con motivo */
    public String getMotivoFinalizacionTexto() {
        if (asignacion.getMotivoFinalizacion() == null) return null;
        return switch (asignacion.getMotivoFinalizacion()) {
            case BAJO_DESEMPENO -> "Bajo desempeño";
            case PROYECTO_CANCELADO -> "Proyecto cancelado";
            case PROYECTO_FINALIZADO -> "Proyecto finalizado";
            case OTRO -> "Otro";
        };
    }

    /** @return estado legible del proyecto de la asignación, o "Sin estado" si no tiene */
    public String getProyectoEstadoTexto() {
        if (asignacion.getProyecto().getEstado() == null) return "Sin estado";
        return switch (asignacion.getProyecto().getEstado()) {
            case EN_REVISION -> "En revisión";
            case RECHAZADO -> "Rechazado";
            case ACTIVO -> "Activo";
            case EN_ESPERA -> "En espera";
            case CANCELADO -> "Cancelado";
            case FINALIZADO -> "Finalizado";
        };
    }

    /**
     * Marca de la aprobación del PM: "PM ✓" si aprobó, "PM ·" si falta, y
     * "PM rechazó/pendiente" si la asignación quedó RECHAZADA sin su aprobación.
     *
     * @return texto de la aprobación del PM
     */
    public String getAprobacionPmTexto() {
        if (asignacion.getEstado() == EstadoAsignacion.RECHAZADA && !asignacion.isAprobadoPorPm()) return "PM rechazó/pendiente";
        return asignacion.isAprobadoPorPm() ? "PM ✓" : "PM ·";
    }

    /**
     * Marca de la aprobación del RM, con la misma regla que {@link #getAprobacionPmTexto()}.
     *
     * @return texto de la aprobación del RM
     */
    public String getAprobacionRmTexto() {
        if (asignacion.getEstado() == EstadoAsignacion.RECHAZADA && !asignacion.isAprobadoPorRm()) return "RM rechazó/pendiente";
        return asignacion.isAprobadoPorRm() ? "RM ✓" : "RM ·";
    }
}
