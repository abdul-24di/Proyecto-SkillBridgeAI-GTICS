package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.model.Curso;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.CursoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminCursoService {

    private final CursoRepository cursoRepository;
    private final AuditoriaService auditoriaService;

    public AdminCursoService(CursoRepository cursoRepository, AuditoriaService auditoriaService) {
        this.cursoRepository = cursoRepository;
        this.auditoriaService = auditoriaService;
    }

    public List<Curso> listarTodos() {
        return cursoRepository.findAllByOrderByNombreAsc();
    }

    @Transactional
    public void crear(Curso curso, Usuario admin) {
        if (curso.getNombre() == null || curso.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del curso es obligatorio.");
        }
        
        curso.setCreadoPor(admin);
        if (curso.getFechaInicio() != null && curso.getFechaFin() != null) {
            if (curso.getFechaFin().isBefore(curso.getFechaInicio())) {
                throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la de inicio.");
            }
        }
        
        cursoRepository.save(curso);
        
        auditoriaService.registrar(admin, "CREAR", "CURSO", curso.getId(),
                "Creó el curso: " + curso.getNombre());
    }

    @Transactional
    public void editar(Long id, Curso datos, Usuario admin) {
        Curso curso = cursoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Curso no encontrado."));
                
        if (datos.getNombre() == null || datos.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del curso es obligatorio.");
        }
        
        if (datos.getFechaInicio() != null && datos.getFechaFin() != null) {
            if (datos.getFechaFin().isBefore(datos.getFechaInicio())) {
                throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la de inicio.");
            }
        }

        String nombreAnterior = curso.getNombre();
        
        curso.setNombre(datos.getNombre());
        curso.setDescripcion(datos.getDescripcion());
        curso.setCategoria(datos.getCategoria());
        curso.setModalidad(datos.getModalidad());
        curso.setAutodidacta(datos.isAutodidacta());
        curso.setDias(datos.getDias());
        curso.setFechaInicio(datos.getFechaInicio());
        curso.setFechaFin(datos.getFechaFin());
        curso.setHoras(datos.getHoras());
        
        cursoRepository.save(curso);
        
        auditoriaService.registrar(admin, "EDITAR", "CURSO", curso.getId(),
                "Editó el curso: " + nombreAnterior + " -> " + curso.getNombre());
    }

    @Transactional
    public void alternarEstado(Long id, Usuario admin) {
        Curso curso = cursoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Curso no encontrado."));
                
        curso.setActivo(!curso.isActivo());
        cursoRepository.save(curso);
        
        String accion = curso.isActivo() ? "REACTIVAR" : "DESACTIVAR";
        auditoriaService.registrar(admin, accion, "CURSO", curso.getId(),
                accion + " el curso: " + curso.getNombre());
    }
}
