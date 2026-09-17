package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.dto.ColProyectoAvanceView;
import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.repository.ActividadRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


@Service
public class ColaboradorActividadService {

    private final ActividadRepository actividadRepository;

    public ColaboradorActividadService(ActividadRepository actividadRepository) {
        this.actividadRepository = actividadRepository;
    }

    //Listamos todas las actividades del colaborador, de la fecha límite más próxima a la más lejana
    public List<Actividad> listarMisActividades(Usuario colaborador) {
        List<Actividad> encontradas = actividadRepository.findByColaborador(colaborador);
        List<Actividad> ordenadas = new ArrayList<>(encontradas);
        ordenadas.sort(Comparator.comparing(Actividad::getFechaLimite));
        return ordenadas;
    }

    //Listamos las actividades que todavía no están completas para el contador y la tabla del dashboard.
    public List<Actividad> listarActividadesPendientes(Usuario colaborador) {
        List<Actividad> todas = listarMisActividades(colaborador);
        List<Actividad> pendientes = new ArrayList<>();

        for (Actividad actividad : todas) {
            if (actividad.getEstado() != EstadoActividad.COMPLETADA) {
                pendientes.add(actividad);
            }
        }
        return pendientes;
    }

    //Calculamos el porcentaje de avance del colaborador en un proyecto
    //Sus actividades completadas sobre el total de las que tiene en ese proyecto. Si no tiene ninguna, es 0.
    public int calcularAvance(Proyecto proyecto, Usuario colaborador) {
        List<Actividad> actividades = actividadRepository.findByProyectoAndColaborador(proyecto, colaborador);
        if (actividades.isEmpty()) {
            return 0;
        }

        int completadas = 0;
        for (Actividad actividad : actividades) {
            if (actividad.getEstado() == EstadoActividad.COMPLETADA) {
                completadas++;
            }
        }
        return Math.round((completadas * 100f) / actividades.size());
    }


    public List<ColProyectoAvanceView> listarProyectosActivosConAvance(Usuario colaborador, List<Asignacion> misAsignaciones) {
        List<ColProyectoAvanceView> resultado = new ArrayList<>();

        for (Asignacion asignacion : misAsignaciones) {
            if (asignacion.getEstado() == EstadoAsignacion.ACTIVA) {
                int avance = calcularAvance(asignacion.getProyecto(), colaborador);
                resultado.add(new ColProyectoAvanceView(asignacion.getProyecto(), avance));
            }
        }
        return resultado;
    }
}