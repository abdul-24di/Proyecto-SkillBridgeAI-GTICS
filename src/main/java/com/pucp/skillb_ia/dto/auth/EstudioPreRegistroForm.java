package com.pucp.skillb_ia.dto.auth;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/** Una fila de formación académica del pre-registro: institución, título, fechas y archivo. */
public class EstudioPreRegistroForm {

    private String institucion;
    private String titulo;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaInicio;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaFin;

    private MultipartFile archivo;

    // Id del archivo que ya se recibió en un envío anterior (ver PreRegistroArchivosService).
    private String archivoGuardado;

    /**
     * Indica si la fila no tiene ningún dato.
     *
     * @return {@code true} si está vacía
     */
    public boolean estaVacia() {
        return esVacio(institucion) && esVacio(titulo) && fechaInicio == null && fechaFin == null
                && (archivo == null || archivo.isEmpty()) && esVacio(archivoGuardado);
    }

    /**
     * Indica si un texto es nulo o está en blanco.
     *
     * @param valor valor ingresado
     * @return {@code true} si no tiene contenido
     */
    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }

    /**
     * Devuelve {@code institucion}.
     *
     * @return el valor de {@code institucion}
     */
    public String getInstitucion() { return institucion; }
    /**
     * Asigna {@code institucion}.
     *
     * @param institucion institución
     */
    public void setInstitucion(String institucion) { this.institucion = institucion; }
    /**
     * Devuelve {@code titulo}.
     *
     * @return el valor de {@code titulo}
     */
    public String getTitulo() { return titulo; }
    /**
     * Asigna {@code titulo}.
     *
     * @param titulo título obtenido
     */
    public void setTitulo(String titulo) { this.titulo = titulo; }
    /**
     * Devuelve {@code fechaInicio}.
     *
     * @return el valor de {@code fechaInicio}
     */
    public LocalDate getFechaInicio() { return fechaInicio; }
    /**
     * Asigna {@code fechaInicio}.
     *
     * @param fechaInicio fecha de inicio
     */
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }
    /**
     * Devuelve {@code fechaFin}.
     *
     * @return el valor de {@code fechaFin}
     */
    public LocalDate getFechaFin() { return fechaFin; }
    /**
     * Asigna {@code fechaFin}.
     *
     * @param fechaFin fecha de fin
     */
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }
    /**
     * Devuelve {@code archivo}.
     *
     * @return el valor de {@code archivo}
     */
    public MultipartFile getArchivo() { return archivo; }
    /**
     * Asigna {@code archivo}.
     *
     * @param archivo archivo subido
     */
    public void setArchivo(MultipartFile archivo) { this.archivo = archivo; }
    /**
     * Devuelve {@code archivoGuardado}.
     *
     * @return el valor de {@code archivoGuardado}
     */
    public String getArchivoGuardado() { return archivoGuardado; }
    /**
     * Asigna {@code archivoGuardado}.
     *
     * @param archivoGuardado id del archivo ya recibido
     */
    public void setArchivoGuardado(String archivoGuardado) { this.archivoGuardado = archivoGuardado; }
}
