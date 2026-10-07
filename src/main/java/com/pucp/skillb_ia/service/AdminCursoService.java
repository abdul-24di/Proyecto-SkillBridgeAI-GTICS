package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.model.Curso;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.CursoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    // Un curso está finalizado cuando su fecha de fin ya pasó. Los autodidactas
    // pueden no tener fecha de fin y por eso nunca pasan a "finalizados".
    public boolean estaFinalizado(Curso curso) {
        return curso.getFechaFin() != null && curso.getFechaFin().isBefore(LocalDate.now());
    }

    public List<Curso> listarVigentes() {
        return listarTodos().stream().filter(c -> !estaFinalizado(c)).toList();
    }

    public List<Curso> listarFinalizados() {
        return listarTodos().stream().filter(this::estaFinalizado).toList();
    }

    @Transactional
    public void crear(Curso curso, Usuario admin) {
        if (curso.getNombre() == null || curso.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del curso es obligatorio.");
        }
        validarHoras(curso.getHoras());
        validarCategoriaYFechas(curso.getCategoria(), curso.isAutodidacta(), curso.getFechaInicio(), curso.getFechaFin());
        if (curso.getFechaInicio() != null && curso.getFechaInicio().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("La fecha de inicio no puede ser anterior a hoy.");
        }

        curso.setCreadoPor(admin);

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
        validarHoras(datos.getHoras());
        validarCategoriaYFechas(datos.getCategoria(), datos.isAutodidacta(), datos.getFechaInicio(), datos.getFechaFin());

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

    // La columna es DECIMAL(6,2) con CHECK horas > 0: se valida antes para no llegar a un error de BD.
    private void validarHoras(BigDecimal horas) {
        if (horas == null) {
            throw new IllegalArgumentException("Las horas del curso son obligatorias.");
        }
        if (horas.signum() <= 0) {
            throw new IllegalArgumentException("Las horas del curso deben ser mayores que cero.");
        }
        if (horas.scale() > 2) {
            throw new IllegalArgumentException("Las horas pueden tener como máximo 2 decimales.");
        }
        if (horas.compareTo(new BigDecimal("1000")) > 0) {
            throw new IllegalArgumentException("Las horas del curso no pueden superar las 1000.");
        }
    }

    // Categoría siempre obligatoria (A25). Fechas obligatorias solo si el curso NO es
    // autodidacta (uno autodidacta ya usa "horas" como su duración). La restricción de
    // "fecha de inicio no anterior a hoy" se valida aparte, solo al crear — al editar un
    // curso que ya empezó, su fecha de inicio original sigue siendo válida.
    private void validarCategoriaYFechas(String categoria, boolean autodidacta,
                                          LocalDate fechaInicio, LocalDate fechaFin) {
        if (categoria == null || categoria.isBlank()) {
            throw new IllegalArgumentException("La categoría del curso es obligatoria.");
        }
        if (!autodidacta && (fechaInicio == null || fechaFin == null)) {
            throw new IllegalArgumentException(
                    "La fecha de inicio y de fin son obligatorias para un curso que no es autodidacta.");
        }
        if (fechaInicio != null && fechaFin != null && fechaFin.isBefore(fechaInicio)) {
            throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la de inicio.");
        }
    }
}
