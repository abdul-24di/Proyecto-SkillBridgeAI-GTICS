package com.pucp.skillb_ia.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Respuesta del asistente IA del Resource Manager, lista para pintarse en el chat.
 * La arma el servicio del asistente del RM y se guarda en la sesión como parte del historial.
 *
 * @param respuesta        texto completo devuelto por el asistente
 * @param bloques          el mismo texto dividido en párrafos y listas ({@link #estructurarTexto(String)})
 * @param usoDatosInternos {@code true} si la respuesta se basó en datos de la plataforma
 * @param iaDisponible     {@code false} si el modelo no respondió y se muestra una respuesta de respaldo
 * @param proyectos        tarjetas de proyectos citados en la respuesta
 * @param candidatos       tarjetas de colaboradores sugeridos para un proyecto
 */
public record RmAsistenteRespuesta(
        String respuesta,
        List<BloqueTexto> bloques,
        boolean usoDatosInternos,
        boolean iaDisponible,
        List<ProyectoTarjetaRm> proyectos,
        List<CandidatoTarjetaRm> candidatos) implements Serializable {

    /**
     * Fragmento del texto de la respuesta.
     *
     * @param esLista {@code true} si el bloque es una lista de viñetas
     * @param parrafo texto del párrafo; {@code null} si es lista
     * @param items   elementos de la lista; vacío si es párrafo
     */
    public record BloqueTexto(boolean esLista, String parrafo, List<String> items) implements Serializable {}

    /**
     * Tarjeta de un proyecto en el chat, con sus enlaces de acción para el RM.
     * {@code pendientesRm} cuenta las asignaciones que esperan la decisión del RM.
     */
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

    /**
     * Colaborador sugerido para un proyecto. {@code matchPorcentaje} es la coincidencia de habilidades,
     * {@code costoTotalEstimado} el costo de las {@code horasSugeridas}, {@code cabeEnPresupuesto} indica
     * si ese costo entra en el presupuesto disponible y {@code puedeProponer} si el RM puede proponerlo ya.
     */
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

    /** Pregunta del RM y respuesta del asistente; el historial del chat es una lista de turnos. */
    public record TurnoConversacionRm(String pregunta, RmAsistenteRespuesta respuesta) implements Serializable {}

    /** Indicadores que se muestran en la cabecera del chat del RM. */
    public record KpisAsistenteRm(
            int totalProyectos,
            int totalVacantes,
            long asignacionesPendientesRm,
            long validacionesPendientesRm) implements Serializable {}

    /** Botón de consulta sugerida: {@code etiqueta} es lo que se ve y {@code prompt} lo que se envía. */
    public record ConsultaRapidaRm(String etiqueta, String prompt) implements Serializable {}

    /**
     * Divide el texto del asistente en bloques. Quita las marcas {@code **}; las líneas que empiezan
     * con {@code -}, {@code *} o {@code •} seguidas de espacio se agrupan en una lista, y cada otra línea
     * no vacía es un párrafo. Una línea vacía o un párrafo cierran la lista abierta.
     *
     * @param texto texto plano devuelto por el asistente; puede ser {@code null}
     * @return bloques en orden; lista vacía si el texto es nulo o está en blanco
     */
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
