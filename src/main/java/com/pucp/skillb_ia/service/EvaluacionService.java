package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Evaluacion;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.EvaluacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class EvaluacionService {

    private final EvaluacionRepository evaluacionRepository;
    private final AsignacionRepository asignacionRepository;
    private final AuditoriaService auditoriaService;

    public EvaluacionService(EvaluacionRepository evaluacionRepository, AsignacionRepository asignacionRepository, AuditoriaService auditoriaService) {
        this.evaluacionRepository = evaluacionRepository;
        this.asignacionRepository = asignacionRepository;
        this.auditoriaService = auditoriaService;
    }

    public List<Evaluacion> obtenerEvaluacionesPorColaborador(Long colaboradorId) {
        return evaluacionRepository.findByColaboradorIdOrderByFechaCreacionDesc(colaboradorId);
    }

    public Optional<Evaluacion> obtenerPorAsignacion(Long asignacionId) {
        return evaluacionRepository.findByAsignacionId(asignacionId);
    }

    @Transactional
    public void evaluarAsignacion(Long asignacionId, Integer calificacion, String comentarios, Usuario evaluador) {
        if (calificacion == null || calificacion < 1 || calificacion > 5) {
            throw new IllegalArgumentException("La calificación debe estar entre 1 y 5.");
        }

        Asignacion asignacion = asignacionRepository.findById(asignacionId)
                .orElseThrow(() -> new IllegalArgumentException("Asignación no encontrada."));

        if (!asignacion.getProyecto().getPm().getId().equals(evaluador.getId()) &&
            !evaluador.getRol().getNombre().equals("RESOURCE_MANAGER")) {
            throw new IllegalArgumentException("No tienes permiso para evaluar esta asignación.");
        }

        Optional<Evaluacion> existente = evaluacionRepository.findByAsignacionId(asignacionId);
        Evaluacion evaluacion;
        
        if (existente.isPresent()) {
            evaluacion = existente.get();
            evaluacion.setCalificacion(calificacion);
            evaluacion.setComentarios(comentarios);
            evaluacion.setEvaluador(evaluador);
        } else {
            evaluacion = new Evaluacion();
            evaluacion.setAsignacion(asignacion);
            evaluacion.setColaborador(asignacion.getColaborador());
            evaluacion.setEvaluador(evaluador);
            evaluacion.setCalificacion(calificacion);
            evaluacion.setComentarios(comentarios);
        }

        evaluacionRepository.save(evaluacion);
        
        String accion = existente.isPresent() ? "EDITAR_EVALUACION" : "CREAR_EVALUACION";
        auditoriaService.registrar(evaluador, accion, "EVALUACION", evaluacion.getId(),
                "Evaluó al colaborador " + asignacion.getColaborador().getNombre() + 
                " con " + calificacion + " estrellas en la asignación " + asignacion.getId());
    }
}
