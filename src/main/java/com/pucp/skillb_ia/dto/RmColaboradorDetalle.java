package com.pucp.skillb_ia.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Perfil completo de un colaborador que consulta el Resource Manager (ficha y modal "Ver perfil").
 * Lo arma {@code RmColaboradorConsultaService}; reúne el {@link RmColaboradorResumen} con contacto,
 * habilidades, asignaciones, experiencia y formación académica.
 */
public class RmColaboradorDetalle {

    private final RmColaboradorResumen resumen;
    private final String correo;
    private final String telefono;
    private final String descripcion;
    private final BigDecimal horasContratadasSemana;
    private final LocalDate fechaContratacion;
    private final List<HabilidadDetalle> habilidades;
    private final List<AsignacionDetalle> asignaciones;
    private final List<ExperienciaDetalle> experiencias;
    private final List<EducacionDetalle> educacion;

    /** Arma el perfil completo a partir del resumen y las listas de habilidades, asignaciones, experiencia y educación. */
    public RmColaboradorDetalle(
            RmColaboradorResumen resumen,
            String correo,
            String telefono,
            String descripcion,
            BigDecimal horasContratadasSemana,
            LocalDate fechaContratacion,
            List<HabilidadDetalle> habilidades,
            List<AsignacionDetalle> asignaciones,
            List<ExperienciaDetalle> experiencias,
            List<EducacionDetalle> educacion) {
        this.resumen = resumen;
        this.correo = correo;
        this.telefono = telefono;
        this.descripcion = descripcion;
        this.horasContratadasSemana = horasContratadasSemana;
        this.fechaContratacion = fechaContratacion;
        this.habilidades = List.copyOf(habilidades);
        this.asignaciones = List.copyOf(asignaciones);
        this.experiencias = List.copyOf(experiencias);
        this.educacion = List.copyOf(educacion);
    }

    public RmColaboradorResumen getResumen() { return resumen; }
    public String getCorreo() { return correo; }
    public String getTelefono() { return telefono; }
    public String getDescripcion() { return descripcion; }
    public BigDecimal getHorasContratadasSemana() { return horasContratadasSemana; }
    public LocalDate getFechaContratacion() { return fechaContratacion; }
    public List<HabilidadDetalle> getHabilidades() { return habilidades; }

    /**
     * Habilidades que cuentan para asignar, usadas en el modal "Ver perfil" de la propuesta (TASK-015).
     *
     * @return solo las habilidades con estado {@code VALIDADA}
     */
    public List<HabilidadDetalle> getHabilidadesValidadas() {
        return habilidades.stream()
                .filter(habilidad -> "VALIDADA".equals(habilidad.getEstadoCodigo()))
                .toList();
    }
    public List<AsignacionDetalle> getAsignaciones() { return asignaciones; }
    public List<ExperienciaDetalle> getExperiencias() { return experiencias; }
    public List<EducacionDetalle> getEducacion() { return educacion; }

    /**
     * Muestra el acceso a la bandeja de certificados desde el perfil (TASK-053).
     *
     * @return {@code true} si alguna habilidad tiene un certificado pendiente que el RM actual puede revisar
     */
    public boolean isTieneCertificadosPendientes() {
        return habilidades.stream().anyMatch(habilidad -> habilidad.getCertificadoPendienteId() != null);
    }

    /**
     * Muestra el acceso a la bandeja de formación académica desde el perfil (TASK-053).
     *
     * @return {@code true} si alguna formación está pendiente y el RM actual puede revisarla
     */
    public boolean isTieneEducacionPendiente() {
        return educacion.stream().anyMatch(estudio -> estudio.getRevisionId() != null);
    }

    /** @return horas contratadas como "40 h / semana", o "Sin registrar" si no hay dato */
    public String getHorasContratadasTexto() {
        return horasContratadasSemana == null
                ? "Sin registrar"
                : horasContratadasSemana.stripTrailingZeros().toPlainString() + " h / semana";
    }

    /** Habilidad del colaborador con su nivel y estado de validación. */
    public static class HabilidadDetalle {
        private final String nombre;
        private final String nivel;
        /** Estado legible de la validación. */
        private final String estadoValidacion;
        /** Nombre del enum del estado ({@code VALIDADA}, {@code RECHAZADA}, pendiente...). */
        private final String estadoCodigo;
        // Certificado pendiente que el RM actual puede revisar; null si no hay.
        private final Long certificadoPendienteId;

        public HabilidadDetalle(String nombre, String nivel, String estadoValidacion, String estadoCodigo,
                                Long certificadoPendienteId) {
            this.nombre = nombre;
            this.nivel = nivel;
            this.estadoValidacion = estadoValidacion;
            this.estadoCodigo = estadoCodigo;
            this.certificadoPendienteId = certificadoPendienteId;
        }

        public String getNombre() { return nombre; }
        public String getNivel() { return nivel; }
        public String getEstadoValidacion() { return estadoValidacion; }
        public String getEstadoCodigo() { return estadoCodigo; }
        public Long getCertificadoPendienteId() { return certificadoPendienteId; }

        /**
         * Insignia distinta por estado: solo las validadas cuentan para asignar.
         *
         * @return verde si está validada, rojo si fue rechazada y amarillo en otro caso
         */
        public String getEstadoClase() {
            if ("VALIDADA".equals(estadoCodigo)) return "bg-green-lt";
            if ("RECHAZADA".equals(estadoCodigo)) return "bg-red-lt";
            return "bg-yellow-lt";
        }
    }

    /** Asignación del colaborador a un proyecto, tal como se lista en su perfil. */
    public static class AsignacionDetalle {
        private final Long id;
        private final String proyecto;
        private final BigDecimal horasSemanales;
        /** Estado legible de la asignación. */
        private final String estado;
        private final LocalDate fechaInicio;
        private final LocalDate fechaFin;

        public AsignacionDetalle(Long id, String proyecto, BigDecimal horasSemanales,
                                 String estado, LocalDate fechaInicio, LocalDate fechaFin) {
            this.id = id;
            this.proyecto = proyecto;
            this.horasSemanales = horasSemanales;
            this.estado = estado;
            this.fechaInicio = fechaInicio;
            this.fechaFin = fechaFin;
        }

        public Long getId() { return id; }
        public String getProyecto() { return proyecto; }
        public BigDecimal getHorasSemanales() { return horasSemanales; }
        public String getEstado() { return estado; }
        public LocalDate getFechaInicio() { return fechaInicio; }
        public LocalDate getFechaFin() { return fechaFin; }
    }

    /** Experiencia laboral declarada por el colaborador. */
    public static class ExperienciaDetalle {
        private final String empresa;
        private final String cargo;
        private final String descripcion;
        private final LocalDate fechaInicio;
        private final LocalDate fechaFin;
        /** {@code true} si es el trabajo actual (sin fecha de fin). */
        private final boolean actual;

        public ExperienciaDetalle(String empresa, String cargo, String descripcion,
                                  LocalDate fechaInicio, LocalDate fechaFin, boolean actual) {
            this.empresa = empresa;
            this.cargo = cargo;
            this.descripcion = descripcion;
            this.fechaInicio = fechaInicio;
            this.fechaFin = fechaFin;
            this.actual = actual;
        }

        public String getEmpresa() { return empresa; }
        public String getCargo() { return cargo; }
        public String getDescripcion() { return descripcion; }
        public LocalDate getFechaInicio() { return fechaInicio; }
        public LocalDate getFechaFin() { return fechaFin; }
        public boolean isActual() { return actual; }
    }

    /** Formación académica del colaborador con su estado de revisión y documento de respaldo. */
    public static class EducacionDetalle {
        private final String institucion;
        private final String titulo;
        private final LocalDate fechaInicio;
        private final LocalDate fechaFin;
        /** {@code true} si el estudio sigue en curso. */
        private final boolean actual;
        /** Estado legible de la revisión. */
        private final String estado;
        /** Ruta del documento guardado; puede ser {@code null}. */
        private final String archivoUrl;
        // Id de la formación si está pendiente y el RM actual puede revisarla; null en otro caso.
        private final Long revisionId;

        public EducacionDetalle(String institucion, String titulo, LocalDate fechaInicio,
                                LocalDate fechaFin, boolean actual, String estado, String archivoUrl,
                                Long revisionId) {
            this.institucion = institucion;
            this.titulo = titulo;
            this.fechaInicio = fechaInicio;
            this.fechaFin = fechaFin;
            this.actual = actual;
            this.estado = estado;
            this.archivoUrl = archivoUrl;
            this.revisionId = revisionId;
        }

        public String getInstitucion() { return institucion; }
        public String getTitulo() { return titulo; }
        public LocalDate getFechaInicio() { return fechaInicio; }
        public LocalDate getFechaFin() { return fechaFin; }
        public boolean isActual() { return actual; }
        public String getEstado() { return estado; }
        public String getArchivoUrl() { return archivoUrl; }
        public Long getRevisionId() { return revisionId; }
    }
}
