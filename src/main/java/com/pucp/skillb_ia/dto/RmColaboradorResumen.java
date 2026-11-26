package com.pucp.skillb_ia.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tarjeta de un colaborador en el directorio y en la búsqueda de talento del Resource Manager.
 * La arma {@code RmColaboradorConsultaService}; además de los datos básicos, calcula los textos y
 * clases CSS de disponibilidad y carga.
 */
public class RmColaboradorResumen {

    private final Long id;
    private final String nombreCompleto;
    private final String iniciales;
    private final String fotoUrl;
    private final String cargo;
    /** Nivel de experiencia legible. */
    private final String nivel;
    /** Nombre del enum del nivel, usado como valor del filtro. */
    private final String nivelCodigo;
    private final List<String> habilidades;
    /** Horas semanales libres del colaborador; se asume no nulo en {@link #getDisponibilidadClase()}. */
    private final BigDecimal horasDisponibles;
    private final BigDecimal aniosExperiencia;
    private final int asignacionesActivas;
    /** Tope de asignaciones activas; 0 o menos = sin tope. */
    private final int maxAsignaciones;
    private final long certificadosAprobados;

    /** Arma la tarjeta del colaborador con los datos ya calculados por el servicio. */
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

    /** @return horas disponibles sin ceros a la derecha; "0" si no hay dato */
    public String getHorasDisponiblesTexto() {
        return numero(horasDisponibles);
    }

    /** @return años de experiencia como "1 año" o "3.5 años"; "Sin registrar" si no hay dato */
    public String getExperienciaTexto() {
        if (aniosExperiencia == null) return "Sin registrar";
        String valor = numero(aniosExperiencia);
        return valor + (BigDecimal.ONE.compareTo(aniosExperiencia) == 0 ? " año" : " años");
    }

    /** @return {@code true} si hay tope configurado y las asignaciones activas ya lo alcanzan */
    public boolean isCargaMaxima() {
        return maxAsignaciones > 0 && asignacionesActivas >= maxAsignaciones;
    }

    /** @return verde con 16 h o más libres, amarillo con menos de 16 y rojo sin horas libres */
    public String getDisponibilidadClase() {
        if (horasDisponibles.compareTo(BigDecimal.valueOf(16)) >= 0) return "bg-green-lt";
        if (horasDisponibles.signum() > 0) return "bg-yellow-lt";
        return "bg-red-lt";
    }

    /** @return rojo con carga máxima, verde sin asignaciones activas y amarillo en otro caso */
    public String getCargaClase() {
        if (isCargaMaxima()) return "bg-red-lt";
        if (asignacionesActivas == 0) return "bg-green-lt";
        return "bg-yellow-lt";
    }

    /** @return texto del buscador: nombre, cargo, nivel y habilidades */
    public String getTextoBusqueda() {
        return String.join(" ", nombreCompleto, cargo, nivel, String.join(" ", habilidades));
    }

    private static String numero(BigDecimal valor) {
        if (valor == null) return "0";
        return valor.stripTrailingZeros().toPlainString();
    }
}
