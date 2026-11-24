package com.pucp.skillb_ia.dto;

import java.math.BigDecimal;

/**
 * Perfil (habilidad) que un proyecto necesita, con los cupos que aún quedan libres.
 * <p>
 * Un proyecto puede pedir varias habilidades, cada una con su nivel, cantidad de personas y
 * horas semanales. Esta clase representa una de ellas para mostrarla en la pantalla de
 * proyectos disponibles.
 *
 * @see ColProyectoDisponibleView
 */
public class ColPerfilRequeridoView {

    private final Long habilidadId;
    private final String etiqueta;
    private final int cuposDisponibles;
    private final BigDecimal horasSemanales;

    /**
     * @param habilidadId id de la habilidad requerida
     * @param etiqueta texto a mostrar: nombre de la habilidad y, si existe, su nivel
     *                 (por ejemplo {@code "Java (Avanzado)"})
     * @param cuposDisponibles cantidad de personas pedidas menos las asignaciones activas
     *                         de esa habilidad
     * @param horasSemanales horas por semana que exige el perfil; si no tiene, se usan las
     *                       del proyecto
     */
    public ColPerfilRequeridoView(Long habilidadId, String etiqueta, int cuposDisponibles, BigDecimal horasSemanales) {
        this.habilidadId = habilidadId;
        this.etiqueta = etiqueta;
        this.cuposDisponibles = cuposDisponibles;
        this.horasSemanales = horasSemanales;
    }

    public Long getHabilidadId() { return habilidadId; }
    public String getEtiqueta() { return etiqueta; }
    public int getCuposDisponibles() { return cuposDisponibles; }
    public BigDecimal getHorasSemanales() { return horasSemanales; }
}