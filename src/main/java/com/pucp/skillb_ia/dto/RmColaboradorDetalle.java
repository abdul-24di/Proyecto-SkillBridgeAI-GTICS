package com.pucp.skillb_ia.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
    public List<AsignacionDetalle> getAsignaciones() { return asignaciones; }
    public List<ExperienciaDetalle> getExperiencias() { return experiencias; }
    public List<EducacionDetalle> getEducacion() { return educacion; }

    public String getHorasContratadasTexto() {
        return horasContratadasSemana == null
                ? "Sin registrar"
                : horasContratadasSemana.stripTrailingZeros().toPlainString() + " h / semana";
    }

    public static class HabilidadDetalle {
        private final String nombre;
        private final String nivel;
        private final String estadoValidacion;

        public HabilidadDetalle(String nombre, String nivel, String estadoValidacion) {
            this.nombre = nombre;
            this.nivel = nivel;
            this.estadoValidacion = estadoValidacion;
        }

        public String getNombre() { return nombre; }
        public String getNivel() { return nivel; }
        public String getEstadoValidacion() { return estadoValidacion; }
    }

    public static class AsignacionDetalle {
        private final Long id;
        private final String proyecto;
        private final BigDecimal horasSemanales;
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

    public static class ExperienciaDetalle {
        private final String empresa;
        private final String cargo;
        private final String descripcion;
        private final LocalDate fechaInicio;
        private final LocalDate fechaFin;
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

    public static class EducacionDetalle {
        private final String institucion;
        private final String titulo;
        private final LocalDate fechaInicio;
        private final LocalDate fechaFin;
        private final boolean actual;
        private final String estado;

        public EducacionDetalle(String institucion, String titulo, LocalDate fechaInicio,
                                LocalDate fechaFin, boolean actual, String estado) {
            this.institucion = institucion;
            this.titulo = titulo;
            this.fechaInicio = fechaInicio;
            this.fechaFin = fechaFin;
            this.actual = actual;
            this.estado = estado;
        }

        public String getInstitucion() { return institucion; }
        public String getTitulo() { return titulo; }
        public LocalDate getFechaInicio() { return fechaInicio; }
        public LocalDate getFechaFin() { return fechaFin; }
        public boolean isActual() { return actual; }
        public String getEstado() { return estado; }
    }
}
