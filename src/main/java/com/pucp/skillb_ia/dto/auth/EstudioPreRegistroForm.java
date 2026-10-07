package com.pucp.skillb_ia.dto.auth;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

// Una fila de formación académica del pre-registro: institución, título, fechas y archivo.
public class EstudioPreRegistroForm {

    private String institucion;
    private String titulo;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaInicio;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaFin;

    private MultipartFile archivo;

    public boolean estaVacia() {
        return esVacio(institucion) && esVacio(titulo) && fechaInicio == null && fechaFin == null
                && (archivo == null || archivo.isEmpty());
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }

    public String getInstitucion() { return institucion; }
    public void setInstitucion(String institucion) { this.institucion = institucion; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }
    public MultipartFile getArchivo() { return archivo; }
    public void setArchivo(MultipartFile archivo) { this.archivo = archivo; }
}
