package com.pucp.skillb_ia.service;

import com.pucp.skillb_ia.model.Curso;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.CursoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Catálogo de cursos del Administrador: registro, edición y activación. */
@Service
public class AdminCursoService {

    private final CursoRepository cursoRepository;
    private final AuditoriaService auditoriaService;

    /** Crea el componente con sus dependencias, que Spring inyecta por el constructor. */
    public AdminCursoService(CursoRepository cursoRepository, AuditoriaService auditoriaService) {
        this.cursoRepository = cursoRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Lista todos los cursos por nombre.
     *
     * @return cursos
     */
    public List<Curso> listarTodos() {
        return cursoRepository.findAllByOrderByNombreAsc();
    }

    /**
     * Indica si el curso ya caducó.
     * <p>
     * Un curso está finalizado cuando su fecha de fin ya pasó. Los autodidactas pueden no tener fecha de fin y
     * por eso nunca pasan a "finalizados".
     *
     * @param curso datos del curso
     * @return {@code true} si su fecha de fin ya pasó
     */
    public boolean estaFinalizado(Curso curso) {
        return curso.getFechaFin() != null && curso.getFechaFin().isBefore(LocalDate.now());
    }

    /**
     * Lista los cursos que no han caducado.
     *
     * @return cursos vigentes
     */
    public List<Curso> listarVigentes() {
        return listarTodos().stream().filter(c -> !estaFinalizado(c)).toList();
    }

    /**
     * Lista los cursos caducados.
     *
     * @return cursos caducados
     */
    public List<Curso> listarFinalizados() {
        return listarTodos().stream().filter(this::estaFinalizado).toList();
    }

    /**
     * Registra un curso; la fecha de inicio no puede ser anterior a hoy.
     *
     * @param curso datos del curso
     * @param admin Administrador que realiza la acción; queda en la auditoría
     * @throws IllegalArgumentException si algún dato no es válido
     */
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

    /**
     * Guarda los cambios de un curso.
     *
     * @param id id del registro
     * @param datos datos editados del curso
     * @param admin Administrador que realiza la acción; queda en la auditoría
     * @throws IllegalArgumentException si no existe o algún dato no es válido
     */
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

    /**
     * Activa el curso si estaba inactivo, o lo desactiva.
     *
     * @param id id del registro
     * @param admin Administrador que realiza la acción; queda en la auditoría
     * @throws IllegalArgumentException si no existe
     */
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

    /**
     * Comprueba que las horas sean mayores que cero, con dos decimales como máximo y hasta 1000.
     * <p>
     * La columna es DECIMAL(6,2) con CHECK horas &gt; 0: se valida antes para no llegar a un error de BD.
     *
     * @param horas horas de duración
     * @throws IllegalArgumentException si no cumplen
     */
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

    /**
     * Comprueba la categoría y las fechas del curso.
     * <p>
     * Categoría siempre obligatoria (A25). Fechas obligatorias solo si el curso NO es autodidacta (uno
     * autodidacta ya usa "horas" como su duración). La restricción de "fecha de inicio no anterior a hoy" se
     * valida aparte, solo al crear — al editar un curso que ya empezó, su fecha de inicio original sigue siendo
     * válida.
     *
     * @param categoria categoría del curso
     * @param autodidacta {@code true} si el curso no exige fechas
     * @param fechaInicio fecha de inicio
     * @param fechaFin fecha de fin
     * @throws IllegalArgumentException si no cumplen
     */
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
