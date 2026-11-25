package com.pucp.skillb_ia.dto.auth;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Una fila de experiencia profesional del pre-registro. No lleva anotaciones de validación porque las filas
 * vacías se ignoran: el controlador valida solo las filas que el usuario empezó a llenar.
 */
public class ExperienciaForm {

    private String cargo;
    private String empresa;
    private String descripcion;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaInicio;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaFin;

    private boolean actual;

    /**
     * Indica si la fila no tiene ningún dato.
     * <p>
     * Una fila vacía (sin ningún dato) no cuenta como experiencia.
     *
     * @return {@code true} si está vacía
     */
    public boolean estaVacia() {
        return esVacio(cargo) && esVacio(empresa) && esVacio(descripcion)
                && fechaInicio == null && fechaFin == null && !actual;
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
     * Devuelve {@code cargo}.
     *
     * @return el valor de {@code cargo}
     */
    public String getCargo() { return cargo; }
    /**
     * Asigna {@code cargo}.
     *
     * @param cargo cargo desempeñado
     */
    public void setCargo(String cargo) { this.cargo = cargo; }
    /**
     * Devuelve {@code empresa}.
     *
     * @return el valor de {@code empresa}
     */
    public String getEmpresa() { return empresa; }
    /**
     * Asigna {@code empresa}.
     *
     * @param empresa empresa
     */
    public void setEmpresa(String empresa) { this.empresa = empresa; }
    /**
     * Devuelve {@code descripcion}.
     *
     * @return el valor de {@code descripcion}
     */
    public String getDescripcion() { return descripcion; }
    /**
     * Asigna {@code descripcion}.
     *
     * @param descripcion descripción; puede ser {@code null} o vacía
     */
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
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
     * Devuelve {@code actual}.
     *
     * @return {@code true} si se cumple
     */
    public boolean isActual() { return actual; }
    /**
     * Asigna {@code actual}.
     *
     * @param actual {@code true} si es su trabajo actual
     */
    public void setActual(boolean actual) { this.actual = actual; }
}
