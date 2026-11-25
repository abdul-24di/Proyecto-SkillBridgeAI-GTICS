package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.service.rm.RmPresupuestoService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Proyecto tal como lo ve el Resource Manager en la lista, el detalle y la dotación de personal.
 * Lo arma {@code RmProyectoConsultaService}; envuelve el {@link Proyecto} con su equipo, los
 * requisitos de talento, el resumen de presupuesto y los textos y clases CSS para la vista.
 */
public class RmProyectoView {
    private final Proyecto proyecto;
    private final String pmNombre;
    private final String estadoTexto;
    private final String prioridadTexto;
    private final int integrantesActivos;
    /** Puestos que aún faltan cubrir. */
    private final int vacantes;
    /** Asignaciones del proyecto que esperan la decisión del RM. */
    private final int pendientesRm;
    private final List<MiembroEquipo> equipo;
    private final List<RequisitoTalento> requisitos;
    private final RmPresupuestoService.ResumenPresupuesto resumenPresupuesto;
    // TASK-056: solo se llenan en el detalle (RmProyectoConsultaService.obtener).
    private final List<AsignacionProyecto> propuestas;
    private final List<AsignacionProyecto> participantes;

    public RmProyectoView(Proyecto proyecto, String pmNombre, String estadoTexto, String prioridadTexto,
                          int integrantesActivos, int vacantes, int pendientesRm,
                          List<MiembroEquipo> equipo, List<RequisitoTalento> requisitos,
                          RmPresupuestoService.ResumenPresupuesto resumenPresupuesto) {
        this(proyecto, pmNombre, estadoTexto, prioridadTexto, integrantesActivos, vacantes, pendientesRm,
                equipo, requisitos, resumenPresupuesto, List.of(), List.of());
    }

    public RmProyectoView(Proyecto proyecto, String pmNombre, String estadoTexto, String prioridadTexto,
                          int integrantesActivos, int vacantes, int pendientesRm,
                          List<MiembroEquipo> equipo, List<RequisitoTalento> requisitos,
                          RmPresupuestoService.ResumenPresupuesto resumenPresupuesto,
                          List<AsignacionProyecto> propuestas, List<AsignacionProyecto> participantes) {
        this.proyecto = proyecto;
        this.pmNombre = pmNombre;
        this.estadoTexto = estadoTexto;
        this.prioridadTexto = prioridadTexto;
        this.integrantesActivos = integrantesActivos;
        this.vacantes = vacantes;
        this.pendientesRm = pendientesRm;
        this.equipo = List.copyOf(equipo);
        this.requisitos = List.copyOf(requisitos);
        this.resumenPresupuesto = resumenPresupuesto;
        this.propuestas = List.copyOf(propuestas);
        this.participantes = List.copyOf(participantes);
    }

    /** Todas las asignaciones del proyecto, en cualquier estado (TASK-056). */
    public List<AsignacionProyecto> getPropuestas() { return propuestas; }

    /** Quienes llegaron a trabajar en el proyecto: asignaciones activas o finalizadas con fecha de activación. */
    public List<AsignacionProyecto> getParticipantes() { return participantes; }

    /** Proyecto cerrado: el equipo se muestra como histórico de participantes. */
    public boolean isCerrado() {
        return proyecto.getEstado() == com.pucp.skillb_ia.model.enums.EstadoProyecto.FINALIZADO
                || proyecto.getEstado() == com.pucp.skillb_ia.model.enums.EstadoProyecto.CANCELADO;
    }

    /** Asignación de un colaborador al proyecto, con sus textos de origen y estado y sus fechas clave. */
    public record AsignacionProyecto(Long asignacionId, Long colaboradorId, String colaboradorNombre,
                                     String iniciales, String cargo, BigDecimal horasSemanales,
                                     String origenTexto, String estadoTexto, String estadoClase,
                                     LocalDateTime fechaSolicitud, LocalDateTime fechaActivacion,
                                     LocalDateTime fechaFinalizacion) {}

    public RmPresupuestoService.ResumenPresupuesto getResumenPresupuesto() { return resumenPresupuesto; }

    public Proyecto getProyecto() { return proyecto; }
    public String getPmNombre() { return pmNombre; }
    public String getEstadoTexto() { return estadoTexto; }
    public String getPrioridadTexto() { return prioridadTexto; }
    public int getIntegrantesActivos() { return integrantesActivos; }
    public int getVacantes() { return vacantes; }
    public int getPendientesRm() { return pendientesRm; }
    public List<MiembroEquipo> getEquipo() { return equipo; }
    public List<RequisitoTalento> getRequisitos() { return requisitos; }

    /**
     * Requisitos de talento serializados a mano para el JS de la vista: un arreglo de objetos con
     * {@code habilidad}, {@code nivel}, {@code horas} ({@code null} si no hay) y {@code cantidad}.
     * Solo escapa las comillas dobles.
     *
     * @return el arreglo JSON como texto; {@code "[]"} si no hay requisitos
     */
    public String getRequisitosJson() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < requisitos.size(); i++) {
            RequisitoTalento r = requisitos.get(i);
            sb.append("{")
              .append("\"habilidad\":\"").append(r.habilidad.replace("\"", "\\\"")).append("\",")
              .append("\"nivel\":\"").append(r.nivel.replace("\"", "\\\"")).append("\",")
              .append("\"horas\":").append(r.horasSemanales != null ? r.horasSemanales : "null").append(",")
              .append("\"cantidad\":").append(r.cantidadPersonas)
              .append("}");
            if (i < requisitos.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    /** @return clase CSS de la insignia del estado del proyecto */
    public String getEstadoClase() {
        return switch (proyecto.getEstado()) {
            case ACTIVO -> "bg-green-lt";
            case EN_REVISION -> "bg-yellow-lt";
            case RECHAZADO -> "bg-red-lt";
            case EN_ESPERA -> "bg-orange-lt";
            case FINALIZADO -> "bg-blue-lt";
            case CANCELADO -> "bg-secondary-lt";
        };
    }

    /** @return clase CSS de la insignia de la prioridad */
    public String getPrioridadClase() {
        return switch (proyecto.getPrioridad()) {
            case ALTA -> "bg-red-lt text-red";
            case MEDIA -> "bg-yellow-lt text-yellow";
            case BAJA -> "bg-blue-lt text-blue";
        };
    }

    /** @return presupuesto asignado si existe; si no, el presupuesto que solicitó el PM */
    public BigDecimal getPresupuestoVisible() {
        return proyecto.getPresupuesto() != null ? proyecto.getPresupuesto() : proyecto.getPresupuestoSolicitado();
    }

    /** @return rótulo que acompaña a {@link #getPresupuestoVisible()} */
    public String getPresupuestoEtiqueta() {
        return proyecto.getPresupuesto() != null ? "Presupuesto asignado" : "Presupuesto solicitado";
    }

    /**
     * Duración entre el inicio y el fin estimado propuestos (TASK-055). Menos de 14 días se expresa
     * en días; desde 14, en semanas y días sobrantes.
     *
     * @return por ejemplo "10 días" o "3 semanas y 2 días"; "Sin definir" si falta una fecha o el fin es anterior
     */
    public String getDuracionTexto() {
        LocalDate inicio = proyecto.getFechaInicio();
        LocalDate fin = proyecto.getFechaFinEstimada();
        if (inicio == null || fin == null || fin.isBefore(inicio)) return "Sin definir";
        long dias = ChronoUnit.DAYS.between(inicio, fin);
        if (dias < 14) return dias + (dias == 1 ? " día" : " días");
        long semanas = dias / 7;
        long resto = dias % 7;
        return semanas + " semanas" + (resto == 0 ? "" : " y " + resto + (resto == 1 ? " día" : " días"));
    }

    /**
     * Separa la descripción en las secciones que agrega el PM al crear el proyecto
     * ("Habilidades adicionales sugeridas:" y "Notas para el RM:", ver PmViewController).
     * El texto anterior al primer marcador va en la sección "Descripción"; las secciones vacías se omiten.
     *
     * @return secciones en orden; lista vacía si el proyecto no tiene descripción
     */
    public List<SeccionDescripcion> getDescripcionSecciones() {
        String texto = proyecto.getDescripcion();
        if (texto == null || texto.isBlank()) return List.of();
        List<SeccionDescripcion> secciones = new ArrayList<>();
        Matcher marcador = MARCADOR_SECCION.matcher(texto);
        String titulo = "Descripción";
        int inicio = 0;
        while (marcador.find()) {
            agregarSeccion(secciones, titulo, texto.substring(inicio, marcador.start()));
            titulo = marcador.group(1);
            inicio = marcador.end();
        }
        agregarSeccion(secciones, titulo, texto.substring(inicio));
        return secciones;
    }

    private static final Pattern MARCADOR_SECCION =
            Pattern.compile("(?:^|\\s+)(Habilidades adicionales sugeridas|Notas para el RM):\\s*");

    private static void agregarSeccion(List<SeccionDescripcion> secciones, String titulo, String texto) {
        if (!texto.isBlank()) secciones.add(new SeccionDescripcion(titulo, texto.strip()));
    }

    /** Sección de la descripción del proyecto: título y texto sin espacios en los extremos. */
    public record SeccionDescripcion(String titulo, String texto) {}

    /** @return texto del buscador: proyecto, PM y RM revisor ("sin asignar" si no tiene) */
    public String getTextoBusqueda() {
        String rm = proyecto.getRmRevisor() == null
                ? "sin asignar"
                : proyecto.getRmRevisor().getNombre() + " " + proyecto.getRmRevisor().getApellido();
        return proyecto.getNombre() + " " + pmNombre + " " + rm;
    }

    /**
     * Indica si el proyecto aparece en la dotación de personal: tiene vacantes y está en revisión,
     * activo o en espera (no rechazado, cancelado ni finalizado).
     *
     * @return {@code true} si el RM puede buscar colaboradores para el proyecto
     */
    public boolean isConVacantesParaDotacion() {
        return vacantes > 0 && switch (proyecto.getEstado()) {
            case EN_REVISION, ACTIVO, EN_ESPERA -> true;
            case RECHAZADO, CANCELADO, FINALIZADO -> false;
        };
    }

    /** Integrante activo del equipo con sus horas y el costo que representa para el presupuesto. */
    public static class MiembroEquipo {
        private final Long id;
        private final String nombre;
        private final String iniciales;
        private final String cargo;
        private final String nivel;
        private final BigDecimal horasSemanales;
        private final BigDecimal costoSemanal;
        /** Costo durante toda la duración del proyecto. */
        private final BigDecimal costoTotal;
        /** {@code false} si falta el sueldo del colaborador o las fechas del proyecto y el costo no se pudo calcular. */
        private final boolean costoCalculable;

        public MiembroEquipo(Long id, String nombre, String iniciales, String cargo,
                             String nivel, BigDecimal horasSemanales,
                             BigDecimal costoSemanal, BigDecimal costoTotal, boolean costoCalculable) {
            this.id = id;
            this.nombre = nombre;
            this.iniciales = iniciales;
            this.cargo = cargo;
            this.nivel = nivel;
            this.horasSemanales = horasSemanales;
            this.costoSemanal = costoSemanal;
            this.costoTotal = costoTotal;
            this.costoCalculable = costoCalculable;
        }

        public Long getId() { return id; }
        public String getNombre() { return nombre; }
        public String getIniciales() { return iniciales; }
        public String getCargo() { return cargo; }
        public String getNivel() { return nivel; }
        public BigDecimal getHorasSemanales() { return horasSemanales; }
        public BigDecimal getCostoSemanal() { return costoSemanal; }
        public BigDecimal getCostoTotal() { return costoTotal; }
        public boolean isCostoCalculable() { return costoCalculable; }
    }

    /** Requisito de talento del proyecto: habilidad, nivel, cuántas personas y cuántas horas semanales. */
    public static class RequisitoTalento {
        private final String habilidad;
        private final String nivel;
        private final int cantidadPersonas;
        private final java.math.BigDecimal horasSemanales;

        public RequisitoTalento(String habilidad, String nivel, int cantidadPersonas, java.math.BigDecimal horasSemanales) {
            this.habilidad = habilidad;
            this.nivel = nivel;
            this.cantidadPersonas = cantidadPersonas;
            this.horasSemanales = horasSemanales;
        }

        public String getHabilidad() { return habilidad; }
        public String getNivel() { return nivel; }
        public int getCantidadPersonas() { return cantidadPersonas; }
        public java.math.BigDecimal getHorasSemanales() { return horasSemanales; }
    }
}
