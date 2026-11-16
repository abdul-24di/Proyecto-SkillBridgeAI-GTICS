package com.pucp.skillb_ia.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public record RmAsistenteRespuesta(
        String respuesta,
        List<BloqueTexto> bloques,
        boolean usoDatosInternos,
        boolean iaDisponible,
        List<ProyectoTarjetaRm> proyectos,
        List<CandidatoTarjetaRm> candidatos) implements Serializable {

    public record BloqueTexto(boolean esLista, String parrafo, List<String> items) implements Serializable {}

    public record ProyectoTarjetaRm(
            Long id,
            String nombre,
            String pmNombre,
            String estado,
            String prioridad,
            String equipoTexto,
            int vacantes,
            BigDecimal presupuestoAsignado,
            BigDecimal presupuestoDisponible,
            int pendientesRm,
            String urlDetalle,
            String urlTalentMatching,
            String urlBuscarColaboradores) implements Serializable {}

    public record CandidatoTarjetaRm(
            Long colaboradorId,
            String nombre,
            String cargo,
            String nivelExperiencia,
            BigDecimal horasDisponibles,
            int asignacionesActivas,
            int maxAsignaciones,
            String habilidadesMatch,
            int matchPorcentaje,
            Long proyectoId,
            String proyectoNombre,
            BigDecimal horasSugeridas,
            BigDecimal costoTotalEstimado,
            boolean cabeEnPresupuesto,
            boolean puedeProponer) implements Serializable {}

    public record TurnoConversacionRm(String pregunta, RmAsistenteRespuesta respuesta) implements Serializable {}

    public record KpisAsistenteRm(
            int totalProyectos,
            int totalVacantes,
            long asignacionesPendientesRm,
            long validacionesPendientesRm) implements Serializable {}

    public record ConsultaRapidaRm(String etiqueta, String prompt) implements Serializable {}

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
