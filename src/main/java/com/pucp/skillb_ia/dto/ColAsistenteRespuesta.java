package com.pucp.skillb_ia.dto;

import java.util.List;


public record ColAsistenteRespuesta(
        String respuesta,
        boolean usoDatosInternos,
        boolean iaDisponible,
        List<Fuente> fuentes,
        List<CursoTarjeta> cursos,
        List<ProyectoTarjeta> proyectos,
        List<Accion> acciones) {

    public record Fuente(Long id, String titulo, String foro, String url, boolean resuelta) {}

    public record CursoTarjeta(Long id, String nombre, String categoria, String modalidad,
                               String fechaInicio, String fechaFin, String horas,
                               String descripcion, String estado, boolean puedeSolicitar) {}

    public record ProyectoTarjeta(Long id, String nombre, String estado, String url) {}


    public record Accion(String tipo, Long id, Long habilidadId, String etiqueta) {}
}