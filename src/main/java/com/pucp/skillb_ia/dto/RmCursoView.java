package com.pucp.skillb_ia.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class RmCursoView {
    private RmCursoView() {}

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

    public static class Bandeja {
        private final List<InscripcionItem> inscripciones;
        private final int paginaActual;
        private final int totalPaginas;
        private final long totalRegistros;
        private final long pendientes;
        private final long aprobadasEsteMes;
        private final long rechazadasEsteMes;
        private final long enCurso;

        public Bandeja(List<InscripcionItem> inscripciones, int paginaActual, int totalPaginas,
                       long totalRegistros, long pendientes,
                       long aprobadasEsteMes, long rechazadasEsteMes, long enCurso) {
            this.inscripciones = List.copyOf(inscripciones);
            this.paginaActual = paginaActual;
            this.totalPaginas = totalPaginas;
            this.totalRegistros = totalRegistros;
            this.pendientes = pendientes;
            this.aprobadasEsteMes = aprobadasEsteMes;
            this.rechazadasEsteMes = rechazadasEsteMes;
            this.enCurso = enCurso;
        }

        public List<InscripcionItem> getInscripciones() { return inscripciones; }
        public int getPaginaActual() { return paginaActual; }
        public int getTotalPaginas() { return totalPaginas; }
        public long getTotalRegistros() { return totalRegistros; }
        public long getPendientes() { return pendientes; }
        public long getAprobadasEsteMes() { return aprobadasEsteMes; }
        public long getRechazadasEsteMes() { return rechazadasEsteMes; }
        public long getEnCurso() { return enCurso; }
    }

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
        private final String origenCodigo;
        private final String origenTexto;
        private final String estadoCodigo;
        private final String estadoTexto;
        private final String estadoClase;




        private final LocalDateTime fechaSolicitud;
        private final String justificacion;
        private final String motivoRespuesta;
        private final boolean pendienteGestionRm;
        private final String evidenciaUrl;
        private final boolean pendienteRevisionEvidencia;

        public InscripcionItem(Long id, Long colaboradorId, String colaborador,
                               String iniciales, String cargo, Long cursoId,
                               String curso, String categoria, BigDecimal horas,
                               String origenCodigo, String origenTexto,
                               String estadoCodigo, String estadoTexto, String estadoClase,
                               LocalDateTime fechaSolicitud, String justificacion, String motivoRespuesta,
                               boolean pendienteGestionRm, String evidenciaUrl, boolean pendienteRevisionEvidencia) {
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
    }

    public static class ColaboradorOpcion {
        private final Long id;
        private final String nombre;
        private final String cargo;

        public ColaboradorOpcion(Long id, String nombre, String cargo) {
            this.id = id;
            this.nombre = nombre;
            this.cargo = cargo;
        }

        public Long getId() { return id; }
        public String getNombre() { return nombre; }
        public String getCargo() { return cargo; }
    }
}
