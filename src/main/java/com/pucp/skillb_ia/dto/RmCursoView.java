package com.pucp.skillb_ia.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Contenedor de las vistas de capacitación del Resource Manager (catálogo de cursos, bandeja de
 * inscripciones y modal "Asignar curso"). No se instancia: solo agrupa las clases anidadas que arma
 * {@code RmCursoService}.
 */
public final class RmCursoView {
    private RmCursoView() {}

    /**
     * Página del catálogo de cursos con sus indicadores: cursos activos, solicitudes pendientes,
     * inscripciones activas y cursos asignados por el RM en el mes en curso.
     */
    public static class Catalogo {
        private final List<CursoItem> cursos;
        private final int paginaActual;
        private final int totalPaginas;
        private final long totalRegistros;
        private final List<String> categorias;
        private final long cursosActivos;
        private final long solicitudesPendientes;
        private final long inscripcionesActivas;
        private final long asignadosPorRmEsteMes;

        public Catalogo(List<CursoItem> cursos, int paginaActual, int totalPaginas,
                        long totalRegistros, List<String> categorias,
                        long cursosActivos, long solicitudesPendientes,
                        long inscripcionesActivas, long asignadosPorRmEsteMes) {
            this.cursos = List.copyOf(cursos);
            this.paginaActual = paginaActual;
            this.totalPaginas = totalPaginas;
            this.totalRegistros = totalRegistros;
            this.categorias = List.copyOf(categorias);
            this.cursosActivos = cursosActivos;
            this.solicitudesPendientes = solicitudesPendientes;
            this.inscripcionesActivas = inscripcionesActivas;
            this.asignadosPorRmEsteMes = asignadosPorRmEsteMes;
        }

        public List<CursoItem> getCursos() { return cursos; }
        public int getPaginaActual() { return paginaActual; }
        public int getTotalPaginas() { return totalPaginas; }
        public long getTotalRegistros() { return totalRegistros; }
        public List<String> getCategorias() { return categorias; }
        public long getCursosActivos() { return cursosActivos; }
        public long getSolicitudesPendientes() { return solicitudesPendientes; }
        public long getInscripcionesActivas() { return inscripcionesActivas; }
        public long getAsignadosPorRmEsteMes() { return asignadosPorRmEsteMes; }
    }

    /**
     * Página de la bandeja de inscripciones con sus indicadores: pendientes, aprobadas y rechazadas
     * en el mes, inscripciones en curso y evidencias por revisar.
     */
    public static class Bandeja {
        private final List<InscripcionItem> inscripciones;
        private final int paginaActual;
        private final int totalPaginas;
        private final long totalRegistros;
        private final long pendientes;
        private final long aprobadasEsteMes;
        private final long rechazadasEsteMes;
        private final long enCurso;
        private final long evidenciasPorRevisar;

        public Bandeja(List<InscripcionItem> inscripciones, int paginaActual, int totalPaginas,
                       long totalRegistros, long pendientes,
                       long aprobadasEsteMes, long rechazadasEsteMes, long enCurso,
                       long evidenciasPorRevisar) {
            this.inscripciones = List.copyOf(inscripciones);
            this.paginaActual = paginaActual;
            this.totalPaginas = totalPaginas;
            this.totalRegistros = totalRegistros;
            this.pendientes = pendientes;
            this.aprobadasEsteMes = aprobadasEsteMes;
            this.rechazadasEsteMes = rechazadasEsteMes;
            this.enCurso = enCurso;
            this.evidenciasPorRevisar = evidenciasPorRevisar;
        }

        public List<InscripcionItem> getInscripciones() { return inscripciones; }
        public int getPaginaActual() { return paginaActual; }
        public int getTotalPaginas() { return totalPaginas; }
        public long getTotalRegistros() { return totalRegistros; }
        public long getPendientes() { return pendientes; }
        public long getAprobadasEsteMes() { return aprobadasEsteMes; }
        public long getRechazadasEsteMes() { return rechazadasEsteMes; }
        public long getEnCurso() { return enCurso; }
        public long getEvidenciasPorRevisar() { return evidenciasPorRevisar; }
    }

    /** Curso del catálogo con su número de inscritos. */
    public static class CursoItem {
        private final Long id;
        private final String nombre;
        private final String descripcion;
        private final String categoria;
        private final BigDecimal horas;
        private final long inscritos;

        public CursoItem(Long id, String nombre, String descripcion, String categoria,
                         BigDecimal horas, long inscritos) {
            this.id = id;
            this.nombre = nombre;
            this.descripcion = descripcion;
            this.categoria = categoria;
            this.horas = horas;
            this.inscritos = inscritos;
        }

        public Long getId() { return id; }
        public String getNombre() { return nombre; }
        public String getDescripcion() { return descripcion; }
        public String getCategoria() { return categoria; }
        public BigDecimal getHoras() { return horas; }
        public long getInscritos() { return inscritos; }
    }

    /**
     * Fila de la bandeja de inscripciones: la solicitud o asignación de un curso a un colaborador,
     * su estado y, si lo hay, la evidencia de finalización con su revisión.
     */
    public static class InscripcionItem {
        private final Long id;
        private final Long colaboradorId;
        private final String colaborador;
        private final String iniciales;
        private final String cargo;
        private final Long cursoId;
        private final String curso;
        private final String categoria;
        private final BigDecimal horas;
        /** Quién originó la inscripción (solicitud del colaborador o asignación del RM), como código del filtro. */
        private final String origenCodigo;
        private final String origenTexto;
        private final String estadoCodigo;
        private final String estadoTexto;
        private final String estadoClase;




        private final LocalDateTime fechaSolicitud;
        private final String justificacion;
        private final String motivoRespuesta;
        /** {@code true} si la solicitud espera que el RM la apruebe o rechace. */
        private final boolean pendienteGestionRm;
        private final String evidenciaUrl;
        /** {@code true} si hay una evidencia subida que el RM aún no revisó. */
        private final boolean pendienteRevisionEvidencia;
        private final String evidenciaRevisadaPor;
        private final LocalDateTime fechaRevisionEvidencia;
        /** Situación mostrada y filtrada (misma regla en {@code RmCursoService.situacion}). */
        private final String situacion;
        /** Rótulo del motivo según la decisión a la que pertenece; {@code null} si no se muestra motivo. */
        private final String motivoEtiqueta;
        /** "Evidencia validada por" o "Evidencia rechazada por"; {@code null} si no hay revisión vigente. */
        private final String revisionTexto;
        /** Fecha en que el colaborador subió la evidencia. */
        private final LocalDateTime fechaEvidencia;

        public String getSituacion() { return situacion; }
        public String getMotivoEtiqueta() { return motivoEtiqueta; }
        public String getRevisionTexto() { return revisionTexto; }
        public LocalDateTime getFechaEvidencia() { return fechaEvidencia; }

        public InscripcionItem(Long id, Long colaboradorId, String colaborador,
                               String iniciales, String cargo, Long cursoId,
                               String curso, String categoria, BigDecimal horas,
                               String origenCodigo, String origenTexto,
                               String estadoCodigo, String estadoTexto, String estadoClase,
                               LocalDateTime fechaSolicitud, String justificacion, String motivoRespuesta,
                               boolean pendienteGestionRm, String evidenciaUrl, boolean pendienteRevisionEvidencia,
                               String evidenciaRevisadaPor, LocalDateTime fechaRevisionEvidencia,
                               String situacion, String motivoEtiqueta, String revisionTexto,
                               LocalDateTime fechaEvidencia) {
            this.situacion = situacion;
            this.motivoEtiqueta = motivoEtiqueta;
            this.revisionTexto = revisionTexto;
            this.fechaEvidencia = fechaEvidencia;
            this.id = id;
            this.colaboradorId = colaboradorId;
            this.colaborador = colaborador;
            this.iniciales = iniciales;
            this.cargo = cargo;
            this.cursoId = cursoId;
            this.curso = curso;
            this.categoria = categoria;
            this.horas = horas;
            this.origenCodigo = origenCodigo;
            this.origenTexto = origenTexto;
            this.estadoCodigo = estadoCodigo;
            this.estadoTexto = estadoTexto;
            this.estadoClase = estadoClase;
            this.fechaSolicitud = fechaSolicitud;
            this.justificacion = justificacion;
            this.motivoRespuesta = motivoRespuesta;
            this.pendienteGestionRm = pendienteGestionRm;
            this.evidenciaUrl = evidenciaUrl;
            this.pendienteRevisionEvidencia = pendienteRevisionEvidencia;
            this.evidenciaRevisadaPor = evidenciaRevisadaPor;
            this.fechaRevisionEvidencia = fechaRevisionEvidencia;
        }

        public Long getId() { return id; }
        public Long getColaboradorId() { return colaboradorId; }
        public String getColaborador() { return colaborador; }
        public String getIniciales() { return iniciales; }
        public String getCargo() { return cargo; }
        public Long getCursoId() { return cursoId; }
        public String getCurso() { return curso; }
        public String getCategoria() { return categoria; }
        public BigDecimal getHoras() { return horas; }
        public String getOrigenCodigo() { return origenCodigo; }
        public String getOrigenTexto() { return origenTexto; }
        public String getEstadoCodigo() { return estadoCodigo; }
        public String getEstadoTexto() { return estadoTexto; }
        public String getEstadoClase() { return estadoClase; }
        public LocalDateTime getFechaSolicitud() { return fechaSolicitud; }
        public String getJustificacion() { return justificacion; }
        public String getMotivoRespuesta() { return motivoRespuesta; }
        public boolean isPendienteGestionRm() { return pendienteGestionRm; }
        public String getEvidenciaUrl() { return evidenciaUrl; }
        public boolean isPendienteRevisionEvidencia() { return pendienteRevisionEvidencia; }
        public String getEvidenciaRevisadaPor() { return evidenciaRevisadaPor; }
        public LocalDateTime getFechaRevisionEvidencia() { return fechaRevisionEvidencia; }
    }

    /** Curso que se puede elegir en el modal "Asignar curso" (ficha del curso). */
    public static class CursoModal {
        private final Long id;
        private final String nombre;
        private final String categoria;
        private final String modalidad;
        private final String dias;
        private final String fechaInicio;
        private final String fechaFin;
        private final String horas;
        private final long inscritos;
        private final String descripcion;

        public CursoModal(Long id, String nombre, String categoria, String modalidad, String dias,
                          String fechaInicio, String fechaFin, String horas, long inscritos,
                          String descripcion) {
            this.id = id;
            this.nombre = nombre;
            this.categoria = categoria;
            this.modalidad = modalidad;
            this.dias = dias;
            this.fechaInicio = fechaInicio;
            this.fechaFin = fechaFin;
            this.horas = horas;
            this.inscritos = inscritos;
            this.descripcion = descripcion;
        }

        public Long getId() { return id; }
        public String getNombre() { return nombre; }
        public String getCategoria() { return categoria; }
        public String getModalidad() { return modalidad; }
        public String getDias() { return dias; }
        public String getFechaInicio() { return fechaInicio; }
        public String getFechaFin() { return fechaFin; }
        public String getHoras() { return horas; }
        public long getInscritos() { return inscritos; }
        public String getDescripcion() { return descripcion; }
    }

    /** Colaborador que se puede elegir en el modal "Asignar curso" (ficha del colaborador). */
    public static class ColaboradorModal {
        private final Long id;
        private final String nombre;
        private final String iniciales;
        private final String fotoUrl;
        private final String cargo;
        private final String nivel;
        private final String horasDisponibles;
        /** Texto de la carga de asignaciones del colaborador. */
        private final String carga;
        /** Nombres de los cursos que lleva actualmente. */
        private final String cursosEnCurso;
        /** Ids de cursos con solicitud pendiente o inscripción activa, separados por coma; el JS los deshabilita. */
        private final String cursosBloqueados;

        public ColaboradorModal(Long id, String nombre, String iniciales, String fotoUrl, String cargo,
                                String nivel, String horasDisponibles, String carga,
                                String cursosEnCurso, String cursosBloqueados) {
            this.id = id;
            this.nombre = nombre;
            this.iniciales = iniciales;
            this.fotoUrl = fotoUrl;
            this.cargo = cargo;
            this.nivel = nivel;
            this.horasDisponibles = horasDisponibles;
            this.carga = carga;
            this.cursosEnCurso = cursosEnCurso;
            this.cursosBloqueados = cursosBloqueados;
        }

        public Long getId() { return id; }
        public String getNombre() { return nombre; }
        public String getIniciales() { return iniciales; }
        public String getFotoUrl() { return fotoUrl; }
        public String getCargo() { return cargo; }
        public String getNivel() { return nivel; }
        public String getHorasDisponibles() { return horasDisponibles; }
        public String getCarga() { return carga; }
        public String getCursosEnCurso() { return cursosEnCurso; }
        public String getCursosBloqueados() { return cursosBloqueados; }
    }
}
