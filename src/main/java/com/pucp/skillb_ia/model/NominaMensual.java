package com.pucp.skillb_ia.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Historia 9.3 — Tier 3, diferida. Snapshot del cálculo de sueldo de un
// colaborador en un mes: valor_hora = sueldo_base/160, horas_extra pagables
// topeadas por TOPE_HORAS_EXTRA_BONO (A16), solo horas a tiempo generan bono
// (C7: visible únicamente para el colaborador y el Administrador).
@Entity
@Table(name = "nomina_mensual", uniqueConstraints = @UniqueConstraint(columnNames = {"colaborador_id", "anio", "mes"}))
public class NominaMensual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "colaborador_id", nullable = false)
    private Usuario colaborador;

    @Column(nullable = false)
    private int anio;

    @Column(nullable = false)
    private int mes;

    @Column(name = "horas_contratadas", nullable = false, precision = 7, scale = 2)
    private BigDecimal horasContratadas;

    @Column(name = "horas_actividades", nullable = false, precision = 7, scale = 2)
    private BigDecimal horasActividades = BigDecimal.ZERO;

    @Column(name = "horas_capacitacion", nullable = false, precision = 7, scale = 2)
    private BigDecimal horasCapacitacion = BigDecimal.ZERO;

    @Column(name = "horas_cumplidas", nullable = false, precision = 7, scale = 2)
    private BigDecimal horasCumplidas = BigDecimal.ZERO;

    @Column(name = "horas_faltantes", nullable = false, precision = 7, scale = 2)
    private BigDecimal horasFaltantes = BigDecimal.ZERO;

    @Column(name = "horas_extra_pagadas", nullable = false, precision = 6, scale = 2)
    private BigDecimal horasExtraPagadas = BigDecimal.ZERO;

    @Column(name = "sueldo_base_aplicado", nullable = false, precision = 10, scale = 2)
    private BigDecimal sueldoBaseAplicado;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal bono = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal penalizacion = BigDecimal.ZERO;

    @Column(name = "pago_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal pagoTotal;

    @Column(name = "fecha_calculo", nullable = false, updatable = false)
    private LocalDateTime fechaCalculo;

    @PrePersist
    protected void onCreate() {
        if (fechaCalculo == null) fechaCalculo = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Usuario getColaborador() { return colaborador; }
    public void setColaborador(Usuario colaborador) { this.colaborador = colaborador; }

    public int getAnio() { return anio; }
    public void setAnio(int anio) { this.anio = anio; }

    public int getMes() { return mes; }
    public void setMes(int mes) { this.mes = mes; }

    public BigDecimal getHorasContratadas() { return horasContratadas; }
    public void setHorasContratadas(BigDecimal horasContratadas) { this.horasContratadas = horasContratadas; }

    public BigDecimal getHorasActividades() { return horasActividades; }
    public void setHorasActividades(BigDecimal horasActividades) { this.horasActividades = horasActividades; }

    public BigDecimal getHorasCapacitacion() { return horasCapacitacion; }
    public void setHorasCapacitacion(BigDecimal horasCapacitacion) { this.horasCapacitacion = horasCapacitacion; }

    public BigDecimal getHorasCumplidas() { return horasCumplidas; }
    public void setHorasCumplidas(BigDecimal horasCumplidas) { this.horasCumplidas = horasCumplidas; }

    public BigDecimal getHorasFaltantes() { return horasFaltantes; }
    public void setHorasFaltantes(BigDecimal horasFaltantes) { this.horasFaltantes = horasFaltantes; }

    public BigDecimal getHorasExtraPagadas() { return horasExtraPagadas; }
    public void setHorasExtraPagadas(BigDecimal horasExtraPagadas) { this.horasExtraPagadas = horasExtraPagadas; }

    public BigDecimal getSueldoBaseAplicado() { return sueldoBaseAplicado; }
    public void setSueldoBaseAplicado(BigDecimal sueldoBaseAplicado) { this.sueldoBaseAplicado = sueldoBaseAplicado; }

    public BigDecimal getBono() { return bono; }
    public void setBono(BigDecimal bono) { this.bono = bono; }

    public BigDecimal getPenalizacion() { return penalizacion; }
    public void setPenalizacion(BigDecimal penalizacion) { this.penalizacion = penalizacion; }

    public BigDecimal getPagoTotal() { return pagoTotal; }
    public void setPagoTotal(BigDecimal pagoTotal) { this.pagoTotal = pagoTotal; }

    public LocalDateTime getFechaCalculo() { return fechaCalculo; }
    public void setFechaCalculo(LocalDateTime fechaCalculo) { this.fechaCalculo = fechaCalculo; }
}
