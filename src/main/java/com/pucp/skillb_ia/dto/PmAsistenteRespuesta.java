package com.pucp.skillb_ia.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Respuesta estructurada del Asistente IA del Project Manager (texto, bloques, tarjetas y fuentes). */
public record PmAsistenteRespuesta(
        String respuesta,
        List<BloqueTexto> bloques,
        boolean usoDatosInternos,
        boolean iaDisponible,
        List<ProyectoTarjeta> proyectos,
        List<CandidatoTarjeta> candidatos,
        List<Fuente> fuentes) implements Serializable {

    public record BloqueTexto(boolean esLista, String parrafo, List<String> items) implements Serializable {}

    public record ProyectoTarjeta(
            Long id,
            String nombre,
            String estado,
            String prioridad,
            String equipoTexto,
            int progresoPorc,
            int vacantes,
            int pendientesRevision,
            String urlDetalle,
            String urlActividades,
            String urlReporte) implements Serializable {}

    public record CandidatoTarjeta(
            Long colaboradorId,
            String nombre,
            String cargo,
            String nivelExperiencia,
            BigDecimal horasDisponibles,
            String habilidadesMatch,
            int puntajeAfinidad,
            Long proyectoId,
            String proyectoNombre,
            BigDecimal horasSugeridas,
            boolean puedeProponer) implements Serializable {}

    public record Fuente(Long id, String titulo, String foro, String url, boolean resuelta) implements Serializable {}

    public record TurnoConversacion(String pregunta, PmAsistenteRespuesta respuesta) implements Serializable {}

    public record KpisAsistente(int totalProyectos, int totalColaboradores, int totalVacantes, int totalPendientesPm) implements Serializable {}

    public record ConsultaRapida(String etiqueta, String prompt) implements Serializable {}

    public static List<BloqueTexto> estructurarTexto(String texto) {
        List<BloqueTexto> bloques = new ArrayList<>();
        if (texto == null || texto.isBlank()) {
            return bloques;
        }
        String[] lineas = texto.split("\\r?\\n");
        List<String> itemsActuales = null;
        for (String cruda : lineas) {
            String linea = cruda.trim().replace("**", "");
            if (linea.isEmpty()) {
                if (itemsActuales != null && !itemsActuales.isEmpty()) {
                    bloques.add(new BloqueTexto(true, null, new ArrayList<>(itemsActuales)));
                    itemsActuales = null;
                }
                continue;
            }
            if (linea.matches("^([-*•])\\s+.*")) {
                if (itemsActuales == null) {
                    itemsActuales = new ArrayList<>();
                }
                itemsActuales.add(linea.replaceFirst("^([-*•])\\s+", "").trim());
            } else {
                if (itemsActuales != null && !itemsActuales.isEmpty()) {
                    bloques.add(new BloqueTexto(true, null, new ArrayList<>(itemsActuales)));
                    itemsActuales = null;
                }
                bloques.add(new BloqueTexto(false, linea, List.of()));
            }
        }
        if (itemsActuales != null && !itemsActuales.isEmpty()) {
            bloques.add(new BloqueTexto(true, null, new ArrayList<>(itemsActuales)));
        }
        return bloques;
    }
}
