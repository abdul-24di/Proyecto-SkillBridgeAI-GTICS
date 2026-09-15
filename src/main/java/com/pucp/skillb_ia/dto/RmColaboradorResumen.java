package com.pucp.skillb_ia.dto;

import java.math.BigDecimal;
import java.util.List;

public class RmColaboradorResumen {

    private final Long id;
    private final String nombreCompleto;
    private final String iniciales;
    private final String fotoUrl;
    private final String cargo;
    private final String nivel;
    private final String nivelCodigo;
    private final List<String> habilidades;
    private final BigDecimal horasDisponibles;
    private final BigDecimal aniosExperiencia;
    private final int asignacionesActivas;
    private final int maxAsignaciones;
    private final long certificadosAprobados;

    public RmColaboradorResumen(
            Long id,
            String nombreCompleto,
            String iniciales,
            String fotoUrl,
            String cargo,
            String nivel,
            String nivelCodigo,
            List<String> habilidades,
            BigDecimal horasDisponibles,
            BigDecimal aniosExperiencia,
            int asignacionesActivas,
            int maxAsignaciones,
            long certificadosAprobados) {
        this.id = id;
        this.nombreCompleto = nombreCompleto;
        this.iniciales = iniciales;
        this.fotoUrl = fotoUrl;
        this.cargo = cargo;
        this.nivel = nivel;
        this.nivelCodigo = nivelCodigo;
        this.habilidades = List.copyOf(habilidades);
        this.horasDisponibles = horasDisponibles;
        this.aniosExperiencia = aniosExperiencia;
        this.asignacionesActivas = asignacionesActivas;
        this.maxAsignaciones = maxAsignaciones;
        this.certificadosAprobados = certificadosAprobados;
    }

    public Long getId() { return id; }
    public String getNombreCompleto() { return nombreCompleto; }
    public String getIniciales() { return iniciales; }
    public String getFotoUrl() { return fotoUrl; }
    public String getCargo() { return cargo; }
    public String getNivel() { return nivel; }
    public String getNivelCodigo() { return nivelCodigo; }
    public List<String> getHabilidades() { return habilidades; }
    public BigDecimal getHorasDisponibles() { return horasDisponibles; }
    public BigDecimal getAniosExperiencia() { return aniosExperiencia; }
    public int getAsignacionesActivas() { return asignacionesActivas; }
    public int getMaxAsignaciones() { return maxAsignaciones; }
    public long getCertificadosAprobados() { return certificadosAprobados; }

    public String getHorasDisponiblesTexto() {
        return numero(horasDisponibles);
    }

    public String getExperienciaTexto() {
        if (aniosExperiencia == null) return "Sin registrar";
        String valor = numero(aniosExperiencia);
        return valor + (BigDecimal.ONE.compareTo(aniosExperiencia) == 0 ? " año" : " años");
    }

    public boolean isCargaMaxima() {
        return maxAsignaciones > 0 && asignacionesActivas >= maxAsignaciones;
    }

    public String getDisponibilidadClase() {
        if (horasDisponibles.compareTo(BigDecimal.valueOf(16)) >= 0) return "bg-green-lt";
        if (horasDisponibles.signum() > 0) return "bg-yellow-lt";
        return "bg-red-lt";
    }

    public String getCargaClase() {
        if (isCargaMaxima()) return "bg-red-lt";
        if (asignacionesActivas == 0) return "bg-green-lt";
        return "bg-yellow-lt";
    }

    public String getTextoBusqueda() {
        return String.join(" ", nombreCompleto, cargo, nivel, String.join(" ", habilidades));
    }

    private static String numero(BigDecimal valor) {
        if (valor == null) return "0";
        return valor.stripTrailingZeros().toPlainString();
    }
}
