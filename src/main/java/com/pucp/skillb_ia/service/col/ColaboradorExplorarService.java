package com.pucp.skillb_ia.service.col;

import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoColaboradorCurso;
import com.pucp.skillb_ia.model.enums.EstadoValidacion;
import com.pucp.skillb_ia.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Service
public class ColaboradorExplorarService {

    private static final String ROL_COLABORADOR = "COLABORADOR";

    private final UsuarioRepository usuarioRepository;
    private final ColaboradorHabilidadRepository colaboradorHabilidadRepository;
    private final EducacionRepository educacionRepository;
    private final ExperienciaProfesionalRepository experienciaProfesionalRepository;
    private final AsignacionRepository asignacionRepository;
    private final ProyectoHabilidadRequeridaRepository proyectoHabilidadRequeridaRepository;
    private final ColaboradorCursoRepository colaboradorCursoRepository;

    public ColaboradorExplorarService(UsuarioRepository usuarioRepository,
                                      ColaboradorHabilidadRepository colaboradorHabilidadRepository,
                                      EducacionRepository educacionRepository,
                                      ExperienciaProfesionalRepository experienciaProfesionalRepository,
                                      AsignacionRepository asignacionRepository,
                                      ProyectoHabilidadRequeridaRepository proyectoHabilidadRequeridaRepository,
                                      ColaboradorCursoRepository colaboradorCursoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.colaboradorHabilidadRepository = colaboradorHabilidadRepository;
        this.educacionRepository = educacionRepository;
        this.experienciaProfesionalRepository = experienciaProfesionalRepository;
        this.asignacionRepository = asignacionRepository;
        this.proyectoHabilidadRequeridaRepository = proyectoHabilidadRequeridaRepository;
        this.colaboradorCursoRepository = colaboradorCursoRepository;
    }

    //Listamos a todos los colaboradores activos de la organización, excluyendo al que esta buscando.
    @Transactional(readOnly = true)
    public List<Usuario> listarColaboradores(Usuario colaboradorActual) {
        List<Usuario> todos = usuarioRepository.findActivosByRolNombre(ROL_COLABORADOR);
        List<Usuario> resultado = new ArrayList<>();

        for (Usuario usuario : todos) {
            if (!usuario.getId().equals(colaboradorActual.getId())) {
                resultado.add(usuario);
            }
        }
        return resultado;
    }

    //Armamos el perfil público de cada colaborador que contiene habilidades validadas, experiencia, educación, proyectos.
    @Transactional(readOnly = true)
    public Map<Long, PerfilExplorar> obtenerPerfiles(List<Usuario> colaboradores) {
        Map<Long, PerfilExplorar> perfiles = new LinkedHashMap<>();
        for (Usuario colaborador : colaboradores) {
            perfiles.put(colaborador.getId(), construirPerfil(colaborador));
        }
        return perfiles;
    }

    private PerfilExplorar construirPerfil(Usuario colaborador) {
        //Solo mostramos las habilidades validadas por el RM
        List<ColaboradorHabilidad> todasLasHabilidades =
                colaboradorHabilidadRepository.findByColaboradorAndActivoTrue(colaborador);

        List<ColaboradorHabilidad> habilidadesValidadas = new ArrayList<>();

        for (ColaboradorHabilidad ch : todasLasHabilidades) {
            if (ch.getEstadoValidacion() == EstadoValidacion.VALIDADA) {
                habilidadesValidadas.add(ch);
            }
        }

        List<ExperienciaProfesional> experiencia =
                experienciaProfesionalRepository.findByColaboradorOrderByFechaInicioDesc(colaborador);

        List<Educacion> educacion =
                educacionRepository.findByColaboradorAndActivoTrueOrderByFechaInicioDesc(colaborador);

        //Listamos los proyectos donde el colaborador tiene o tuvo una asignación activa o finalizada.
        List<Asignacion> todasLasAsignaciones =
                asignacionRepository.findByColaboradorIdConDetalle(colaborador.getId());

        List<Asignacion> proyectosDestacados = new ArrayList<>();
        for (Asignacion asignacion : todasLasAsignaciones) {
            if (asignacion.getEstado() == EstadoAsignacion.ACTIVA
                    || asignacion.getEstado() == EstadoAsignacion.FINALIZADA) {
                proyectosDestacados.add(asignacion);
            }
        }

        Map<Long, String> habilidadesPorProyecto = new LinkedHashMap<>();

        for (Asignacion asignacion : proyectosDestacados) {
            Proyecto proyecto = asignacion.getProyecto();
            if (!habilidadesPorProyecto.containsKey(proyecto.getId())) {
                habilidadesPorProyecto.put(proyecto.getId(), habilidadesDeProyecto(proyecto));
            }
        }

        String resumenHabilidades = resumenDeHabilidades(habilidadesValidadas);

        //Solo mostramos los cursos que ya terminó (con evidencia aprobada por el RM).
        List<ColaboradorCurso> todosMisCursos = colaboradorCursoRepository.findByColaborador(colaborador);
        List<ColaboradorCurso> cursosCompletados = new ArrayList<>();
        for (ColaboradorCurso registro : todosMisCursos) {
            if (registro.getEstado() == EstadoColaboradorCurso.COMPLETADO) {
                cursosCompletados.add(registro);
            }
        }

        return new PerfilExplorar(habilidadesValidadas, experiencia, educacion, proyectosDestacados,
                habilidadesPorProyecto, resumenHabilidades, cursosCompletados);
    }

    private String habilidadesDeProyecto(Proyecto proyecto) {

        List<ProyectoHabilidadRequerida> requeridas = proyectoHabilidadRequeridaRepository.findByProyecto(proyecto);

        StringBuilder texto = new StringBuilder();

        for (int i = 0; i < requeridas.size(); i++) {
            Habilidad habilidad = requeridas.get(i).getHabilidad();
            if (i > 0) {
                texto.append(" · ");
            }
            texto.append(habilidad.getNombre());
        }
        return texto.length() == 0 ? "Sin tecnologías registradas" : texto.toString();
    }

    private String resumenDeHabilidades(List<ColaboradorHabilidad> habilidades) {
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < habilidades.size(); i++) {
            if (i > 0) {
                texto.append(" · ");
            }
            texto.append(habilidades.get(i).getHabilidad().getNombre());
        }
        return texto.toString();
    }

    //Agrupamos la información que se muestra del perfil público de un colaborador
    public static class PerfilExplorar {
        private final List<ColaboradorHabilidad> habilidades;
        private final List<ExperienciaProfesional> experiencia;
        private final List<Educacion> educacion;
        private final List<Asignacion> proyectos;
        private final Map<Long, String> habilidadesPorProyecto;
        private final String resumenHabilidades;
        private final List<ColaboradorCurso> cursosCompletados;

        public PerfilExplorar(List<ColaboradorHabilidad> habilidades, List<ExperienciaProfesional> experiencia,
                              List<Educacion> educacion, List<Asignacion> proyectos,
                              Map<Long, String> habilidadesPorProyecto, String resumenHabilidades,
                              List<ColaboradorCurso> cursosCompletados) {
            this.habilidades = habilidades;
            this.experiencia = experiencia;
            this.educacion = educacion;
            this.proyectos = proyectos;
            this.habilidadesPorProyecto = habilidadesPorProyecto;
            this.resumenHabilidades = resumenHabilidades;
            this.cursosCompletados = cursosCompletados;
        }

        public List<ColaboradorHabilidad> getHabilidades() { return habilidades; }
        public List<ExperienciaProfesional> getExperiencia() { return experiencia; }
        public List<Educacion> getEducacion() { return educacion; }
        public List<Asignacion> getProyectos() { return proyectos; }
        public Map<Long, String> getHabilidadesPorProyecto() { return habilidadesPorProyecto; }
        public String getResumenHabilidades() { return resumenHabilidades; }
        public List<ColaboradorCurso> getCursosCompletados() { return cursosCompletados; }
    }
}