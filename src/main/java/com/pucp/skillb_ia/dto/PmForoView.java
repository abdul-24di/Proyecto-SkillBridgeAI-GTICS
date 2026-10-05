package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Foro;
import com.pucp.skillb_ia.model.PublicacionForo;
import com.pucp.skillb_ia.model.RespuestaForo;

import java.time.LocalDateTime;
import java.util.List;

/** Vista ligera de la lista de foros del PM (uno por proyecto). */
public class PmForoView {

    private final Long foroId;
    private final String foroNombre;
    private final Long proyectoId;
    private final String proyectoNombre;
    private final int totalPublicaciones;
    private final String ultimaPublicacionTitulo;
    private final LocalDateTime ultimaPublicacionFecha;

    public PmForoView(Foro foro, int totalPublicaciones,
                      String ultimaPublicacionTitulo, LocalDateTime ultimaPublicacionFecha) {
        this.foroId = foro.getId();
        this.foroNombre = foro.getNombre();
        this.proyectoId = foro.getProyecto() != null ? foro.getProyecto().getId() : null;
        this.proyectoNombre = foro.getProyecto() != null ? foro.getProyecto().getNombre() : null;
        this.totalPublicaciones = totalPublicaciones;
        this.ultimaPublicacionTitulo = ultimaPublicacionTitulo;
        this.ultimaPublicacionFecha = ultimaPublicacionFecha;
    }

    public Long getForoId() { return foroId; }
    public String getForoNombre() { return foroNombre; }
    public Long getProyectoId() { return proyectoId; }
    public String getProyectoNombre() { return proyectoNombre; }
    public int getTotalPublicaciones() { return totalPublicaciones; }
    public String getUltimaPublicacionTitulo() { return ultimaPublicacionTitulo; }
    public LocalDateTime getUltimaPublicacionFecha() { return ultimaPublicacionFecha; }
    public boolean isTienePublicaciones() { return totalPublicaciones > 0; }
}
