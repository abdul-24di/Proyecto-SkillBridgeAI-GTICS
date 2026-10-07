package com.pucp.skillb_ia.dto;

/**
 * Habilidad requerida del proyecto en el modal "Solicitar personal" del PM.
 * Disponibles = personas exigidas al crear el proyecto menos los colaboradores
 * con asignación ACTIVA para esa habilidad.
 */
public class PmCupoHabilidadView {

    private final Long habilidadId;
    private final String habilidad;
    private final String nivel;
    private final int exigidos;
    private final int ocupados;

    public PmCupoHabilidadView(Long habilidadId, String habilidad, String nivel, int exigidos, int ocupados) {
        this.habilidadId = habilidadId;
        this.habilidad = habilidad;
        this.nivel = nivel;
        this.exigidos = exigidos;
        this.ocupados = ocupados;
    }

    public Long getHabilidadId() { return habilidadId; }
    public String getHabilidad() { return habilidad; }
    public String getNivel() { return nivel; }
    public int getExigidos() { return exigidos; }
    public int getOcupados() { return ocupados; }
    public int getDisponibles() { return Math.max(0, exigidos - ocupados); }
}
