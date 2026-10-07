package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.dto.PmCupoHabilidadView;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.SolicitudPersonal;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.NivelDominio;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ProyectoHabilidadRequeridaRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.service.rm.RmSolicitudPersonalService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Solicitud de personal del PM con desglose por habilidad del proyecto (TASK-058).
 * Valida el desglose y lo guarda como texto en "perfiles requeridos"; el resto de reglas
 * (estado del proyecto, cantidad, solicitud abierta, auditoría y notificación a los RM)
 * está en {@link RmSolicitudPersonalService#crearDesdePm}.
 */
@Service
public class PmSolicitudPersonalService {

    private static final int LONGITUD_MAXIMA_PERFILES = 1000;

    private final ProyectoRepository proyectoRepository;
    private final ProyectoHabilidadRequeridaRepository habilidadRequeridaRepository;
    private final AsignacionRepository asignacionRepository;
    private final RmSolicitudPersonalService rmSolicitudPersonalService;

    public PmSolicitudPersonalService(ProyectoRepository proyectoRepository,
                                      ProyectoHabilidadRequeridaRepository habilidadRequeridaRepository,
                                      AsignacionRepository asignacionRepository,
                                      RmSolicitudPersonalService rmSolicitudPersonalService) {
        this.proyectoRepository = proyectoRepository;
        this.habilidadRequeridaRepository = habilidadRequeridaRepository;
        this.asignacionRepository = asignacionRepository;
        this.rmSolicitudPersonalService = rmSolicitudPersonalService;
    }

    /** Habilidades requeridas del proyecto con los cupos que aún puede pedir el PM. */
    @Transactional(readOnly = true)
    public List<PmCupoHabilidadView> listarCupos(Proyecto proyecto) {
        Map<Long, Long> ocupados = asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA)
                .stream()
                .map(Asignacion::getHabilidadSolicitada)
                .filter(habilidad -> habilidad != null)
                .collect(Collectors.groupingBy(habilidad -> habilidad.getId(), Collectors.counting()));
        return habilidadRequeridaRepository.findByProyecto(proyecto).stream()
                .map(requisito -> new PmCupoHabilidadView(
                        requisito.getHabilidad().getId(),
                        requisito.getHabilidad().getNombre(),
                        textoNivel(requisito.getNivelRequerido()),
                        requisito.getCantidadPersonas(),
                        ocupados.getOrDefault(requisito.getHabilidad().getId(), 0L).intValue()))
                .sorted(Comparator.comparing(PmCupoHabilidadView::getHabilidad, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public SolicitudPersonal solicitar(Long proyectoId, int cantidad, Map<Long, Integer> cantidadesPorHabilidad,
                                       String perfiles, String mensaje, Usuario pm) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el proyecto solicitado."));
        // Antes de leer las habilidades y asignaciones del proyecto.
        if (!proyecto.getPm().getId().equals(pm.getId())) {
            throw new SecurityException("No tienes permiso para acceder a este proyecto.");
        }
        if (cantidad > proyecto.getColaboradoresRequeridos()) {
            throw new IllegalArgumentException("La cantidad total no puede superar los "
                    + proyecto.getColaboradoresRequeridos() + " colaboradores requeridos por el proyecto.");
        }

        List<PmCupoHabilidadView> cupos = listarCupos(proyecto);
        Map<Long, Integer> pedidosPorHabilidad = cantidadesPorHabilidad == null ? Map.of() : cantidadesPorHabilidad;
        Set<Long> habilidadesDelProyecto = cupos.stream()
                .map(PmCupoHabilidadView::getHabilidadId).collect(Collectors.toSet());
        for (Map.Entry<Long, Integer> entrada : pedidosPorHabilidad.entrySet()) {
            int pedidos = entrada.getValue() == null ? 0 : entrada.getValue();
            if (pedidos < 0) {
                throw new IllegalArgumentException("La cantidad por habilidad no puede ser negativa.");
            }
            if (pedidos > 0 && !habilidadesDelProyecto.contains(entrada.getKey())) {
                throw new IllegalArgumentException("Una de las habilidades indicadas no pertenece al proyecto.");
            }
        }

        // En el orden de la lista del modal, para que el texto del RM sea estable.
        List<String> lineas = new ArrayList<>();
        int suma = 0;
        for (PmCupoHabilidadView cupo : cupos) {
            Integer valor = pedidosPorHabilidad.get(cupo.getHabilidadId());
            int pedidos = valor == null ? 0 : valor;
            if (pedidos == 0) continue;
            if (pedidos > cupo.getDisponibles()) {
                throw new IllegalArgumentException("Para " + cupo.getHabilidad() + " puedes solicitar como máximo "
                        + cupo.getDisponibles() + " colaborador(es).");
            }
            suma += pedidos;
            lineas.add(cupo.getHabilidad() + " · " + cupo.getNivel() + ": " + pedidos + " colaborador(es)");
        }
        if (suma > cantidad) {
            throw new IllegalArgumentException("La suma por habilidad (" + suma
                    + ") no puede superar la cantidad total solicitada (" + cantidad + ").");
        }
        boolean sinPerfiles = perfiles == null || perfiles.isBlank();
        if (cantidad > suma && sinPerfiles) {
            throw new IllegalArgumentException("Describe en Perfiles requeridos los " + (cantidad - suma)
                    + " colaborador(es) que no salen de las habilidades del proyecto.");
        }

        String detalle = armarPerfiles(lineas, cantidad - suma, perfiles);
        if (detalle != null && detalle.length() > LONGITUD_MAXIMA_PERFILES) {
            throw new IllegalArgumentException("El detalle de perfiles supera los " + LONGITUD_MAXIMA_PERFILES
                    + " caracteres. Acorta la descripción de los otros perfiles.");
        }
        return rmSolicitudPersonalService.crearDesdePm(proyectoId, cantidad, detalle, mensaje, pm.getId());
    }

    // Texto que el RM ve en "Perfiles requeridos" (se muestra con saltos de línea).
    private static String armarPerfiles(List<String> lineas, int otros, String perfiles) {
        if (lineas.isEmpty()) return perfiles;
        StringBuilder texto = new StringBuilder("Por habilidad del proyecto:");
        lineas.forEach(linea -> texto.append("\n• ").append(linea));
        if (perfiles != null && !perfiles.isBlank()) {
            texto.append("\n").append(otros > 0 ? "Otros perfiles (" + otros + "):" : "Comentarios sobre los perfiles:")
                    .append("\n").append(perfiles.trim());
        }
        return texto.toString();
    }

    private static String textoNivel(NivelDominio nivel) {
        if (nivel == null) return "Cualquier nivel";
        return switch (nivel) {
            case BASICO -> "Básico";
            case INTERMEDIO -> "Intermedio";
            case AVANZADO -> "Avanzado";
        };
    }
}
