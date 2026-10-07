package com.pucp.skillb_ia.dto.auth;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

// Una fila de experiencia profesional del pre-registro. No lleva anotaciones de validación porque
// las filas vacías se ignoran: el controlador valida solo las filas que el usuario empezó a llenar.
public class ExperienciaForm {

    private String cargo;
    private String empresa;
    private String descripcion;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaInicio;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaFin;

    private boolean actual;

    // Una fila vacía (sin ningún dato) no cuenta como experiencia.
    public boolean estaVacia() {
        return esVacio(cargo) && esVacio(empresa) && esVacio(descripcion)
                && fechaInicio == null && fechaFin == null && !actual;
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }

    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }
    public String getEmpresa() { return empresa; }
    public void setEmpresa(String empresa) { this.empresa = empresa; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }
    public boolean isActual() { return actual; }
    public void setActual(boolean actual) { this.actual = actual; }
}
