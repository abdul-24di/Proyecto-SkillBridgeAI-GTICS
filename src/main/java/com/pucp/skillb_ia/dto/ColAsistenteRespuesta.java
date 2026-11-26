package com.pucp.skillb_ia.dto;

import java.util.List;


/**
 * Respuesta del asistente de IA al colaborador.
 * <p>
 * Además del texto, trae tarjetas con contenido interno de SkillBridge (publicaciones del foro,
 * cursos y proyectos) y acciones que el colaborador puede ejecutar con un clic.
 *
 * @param respuesta texto a mostrar en el chat
 * @param usoDatosInternos {@code true} si la respuesta se apoyó en datos internos de la plataforma
 * @param iaDisponible {@code false} cuando la IA no respondió y se devolvió solo lo encontrado
 *                     en SkillBridge (modo sin IA)
 * @param fuentes publicaciones del foro usadas como fuente (máximo 3 en el modo sin IA)
 * @param cursos cursos recomendados
 * @param proyectos proyectos recomendados
 * @param acciones acciones propuestas, como {@code SOLICITAR_PROYECTO} o {@code SOLICITAR_CURSO}
 * @see com.pucp.skillb_ia.service.col.ColAsistenteService#responder
 */
public record ColAsistenteRespuesta(
        String respuesta,
        boolean usoDatosInternos,
        boolean iaDisponible,
        List<Fuente> fuentes,
        List<CursoTarjeta> cursos,
        List<ProyectoTarjeta> proyectos,
        List<Accion> acciones) {

    /**
     * Publicación del foro usada como fuente de la respuesta.
     *
     * @param id id de la publicación
     * @param titulo título de la publicación
     * @param foro nombre del foro donde está
     * @param url enlace para abrirla
     * @param resuelta si la publicación ya tiene una respuesta marcada como solución
     */
    public record Fuente(Long id, String titulo, String foro, String url, boolean resuelta) {}

    /**
     * Tarjeta de un curso recomendado. {@code puedeSolicitar} indica si el colaborador
     * todavía puede pedir la inscripción desde la tarjeta.
     */
    public record CursoTarjeta(Long id, String nombre, String categoria, String modalidad,
                               String fechaInicio, String fechaFin, String horas,
                               String descripcion, String estado, boolean puedeSolicitar) {}

    /** Tarjeta de un proyecto recomendado, con su estado y el enlace a su pantalla. */
    public record ProyectoTarjeta(Long id, String nombre, String estado, String url) {}


    /**
     * Acción que el colaborador puede confirmar desde el chat.
     *
     * @param tipo tipo de acción: {@code SOLICITAR_PROYECTO} o {@code SOLICITAR_CURSO}
     * @param id id del proyecto o del curso
     * @param habilidadId habilidad (perfil) a la que se postula; {@code null} en los cursos
     * @param etiqueta texto del botón
     */
    public record Accion(String tipo, Long id, Long habilidadId, String etiqueta) {}
}