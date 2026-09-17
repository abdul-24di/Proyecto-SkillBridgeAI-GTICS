package com.pucp.skillb_ia.dto;

import com.pucp.skillb_ia.model.Curso;

public class CursoDisponibleView {
    private final Curso curso;
    private final boolean puedeSolicitar;
    private final String estadoTexto;
    private final String estadoClase;

    public CursoDisponibleView(Curso curso, boolean puedeSolicitar, String estadoTexto, String estadoClase) {
        this.curso = curso;
        this.puedeSolicitar = puedeSolicitar;
        this.estadoTexto = estadoTexto;
        this.estadoClase = estadoClase;
    }

    public Curso getCurso() { return curso; }
    public boolean isPuedeSolicitar() { return puedeSolicitar; }
    public String getEstadoTexto() { return estadoTexto; }
    public String getEstadoClase() { return estadoClase; }
}