package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.MotivoFinalizacion;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

//Cierra las asignaciones abiertas de un proyecto que se cancela o se finaliza (TASK-041).
//Se usa dentro de la transacción de quien cierra el proyecto: si algo falla, se revierte todo.
@Service
public class CierreAsignacionesService {

    private final AsignacionRepository asignacionRepository;
    private final DisponibilidadService disponibilidadService;
    private final NotificacionService notificacionService;

    public CierreAsignacionesService(AsignacionRepository asignacionRepository,
                                     DisponibilidadService disponibilidadService,
                                     NotificacionService notificacionService) {
        this.asignacionRepository = asignacionRepository;
        this.disponibilidadService = disponibilidadService;
        this.notificacionService = notificacionService;
    }

    //Cuántas asignaciones se cerraron de cada tipo.
    public record Resultado(int pendientesRechazadas, int activasFinalizadas) {}

    //cierre: "cancelado" o "finalizado". Las PENDIENTE pasan a RECHAZADA ("Proyecto {cierre}") y las
    //ACTIVA a FINALIZADA con el motivo indicado, sin strike ni penalización. No se borra nada.
    @Transactional
    public Resultado cerrarAbiertas(Proyecto proyecto, Usuario responsable, String cierre,
                                   MotivoFinalizacion motivoFinalizacion) {
        String motivoRechazo = "Proyecto " + cierre;
        LocalDateTime ahora = LocalDateTime.now();
        //Colaboradores afectados por id, para recalcular la disponibilidad una sola vez por persona.
        Map<Long, Usuario> afectados = new LinkedHashMap<>();

        List<Asignacion> pendientes = asignacionRepository.findByProyectoAndEstado(
                proyecto, EstadoAsignacion.PENDIENTE);
        for (Asignacion asignacion : pendientes) {
            asignacion.setEstado(EstadoAsignacion.RECHAZADA);
            asignacion.setMotivoRechazo(motivoRechazo);
            asignacion.setRechazadoPor(responsable);
            //asignacion no tiene columna de fecha de rechazo: la fecha de la decisión
            //se guarda como fecha de cierre en fechaFinalizacion.
            asignacion.setFechaFinalizacion(ahora);
            afectados.putIfAbsent(asignacion.getColaborador().getId(), asignacion.getColaborador());
        }

        List<Asignacion> activas = asignacionRepository.findByProyectoAndEstado(
                proyecto, EstadoAsignacion.ACTIVA);
        for (Asignacion asignacion : activas) {
            asignacion.setEstado(EstadoAsignacion.FINALIZADA);
            asignacion.setMotivoFinalizacion(motivoFinalizacion);
            asignacion.setFechaFinalizacion(ahora);
            asignacion.setDesasignadoPor(responsable);
            afectados.putIfAbsent(asignacion.getColaborador().getId(), asignacion.getColaborador());
        }
        asignacionRepository.saveAll(pendientes);
        asignacionRepository.saveAll(activas);

        //Las horas de las activas finalizadas quedan libres.
        afectados.values().forEach(disponibilidadService::recalcular);

        //Solo se notifica a los colaboradores; quien cierra el proyecto no recibe notificación.
        for (Asignacion asignacion : pendientes) {
            if (!asignacion.getColaborador().getId().equals(responsable.getId())) {
                notificacionService.crear(asignacion.getColaborador(), "ASIGNACION_RECHAZADA",
                        CategoriaNotificacion.ASIGNACION, "Asignación rechazada",
                        "Tu asignación al proyecto \"" + proyecto.getNombre()
                                + "\" fue rechazada. Motivo: " + motivoRechazo,
                        "ASIGNACION", asignacion.getId());
            }
        }
        for (Asignacion asignacion : activas) {
            if (!asignacion.getColaborador().getId().equals(responsable.getId())) {
                notificacionService.crear(asignacion.getColaborador(), "ASIGNACION_FINALIZADA",
                        CategoriaNotificacion.ASIGNACION, motivoRechazo,
                        "El proyecto \"" + proyecto.getNombre() + "\" fue " + cierre
                                + " y tu asignación finalizó. Tus horas quedaron libres.",
                        "ASIGNACION", asignacion.getId());
            }
        }
        return new Resultado(pendientes.size(), activas.size());
    }

    //Mensaje común para quien cierra el proyecto.
    public static String mensaje(String cierre, Resultado resultado) {
        return "Proyecto " + cierre + ". Se rechazaron " + resultado.pendientesRechazadas()
                + " asignaciones pendientes y se finalizaron " + resultado.activasFinalizadas()
                + " asignaciones activas.";
    }
}
