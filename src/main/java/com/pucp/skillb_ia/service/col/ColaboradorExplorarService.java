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


/**
 * Lógica de la pestaña "Colaboradores" de Explorar: lista a los demás colaboradores y arma
 * el <b>perfil público</b> de cada uno.
 * <p>
 * El perfil público solo muestra información ya validada: habilidades {@code VALIDADA} por el
 * RM, cursos {@code COMPLETADO} y proyectos donde participa o participó. La usa también
 * {@code ColaboradorProyectoService} para mostrar el perfil de los integrantes de un proyecto.
 */
@Service
public class ColaboradorExplorarService {

    /** Nombre del rol que se usa para listar solo colaboradores. */
    private static final String ROL_COLABORADOR = "COLABORADOR";

    private final UsuarioRepository usuarioRepository;
    private final ColaboradorHabilidadRepository colaboradorHabilidadRepository;
    private final EducacionRepository educacionRepository;
    private final ExperienciaProfesionalRepository experienciaProfesionalRepository;
    private final AsignacionRepository asignacionRepository;
    private final ProyectoHabilidadRequeridaRepository proyectoHabilidadRequeridaRepository;
    private final ColaboradorCursoRepository colaboradorCursoRepository;

    /**
     * Inyecta por constructor las dependencias del service.
     *
     * @param usuarioRepository acceso a usuarios
     * @param colaboradorHabilidadRepository habilidades de los colaboradores
     * @param educacionRepository formación académica
     * @param experienciaProfesionalRepository experiencia profesional
     * @param asignacionRepository asignaciones (para listar proyectos del colaborador)
     * @param proyectoHabilidadRequeridaRepository habilidades que pide cada proyecto
     * @param colaboradorCursoRepository cursos de los colaboradores
     */
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

    /**
     * Lista a todos los colaboradores activos de la organización, excluyendo al que está
     * buscando. Es una consulta de solo lectura.
     *
     * @param colaboradorActual colaborador que explora (no aparece en la lista)
     * @return los demás colaboradores activos
     */
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

    /**
     * Arma el perfil público de cada colaborador indicado: habilidades validadas, experiencia,
     * educación, proyectos y cursos completados. Es una consulta de solo lectura.
     *
     * @param colaboradores colaboradores cuyo perfil se necesita
     * @return mapa {@code idColaborador -> perfil}, en el mismo orden de la lista recibida
     */
    @Transactional(readOnly = true)
    public Map<Long, PerfilExplorar> obtenerPerfiles(List<Usuario> colaboradores) {
        Map<Long, PerfilExplorar> perfiles = new LinkedHashMap<>();
        for (Usuario colaborador : colaboradores) {
            perfiles.put(colaborador.getId(), construirPerfil(colaborador));
        }
        return perfiles;
    }

    /**
     * Construye el perfil público de un colaborador aplicando estos filtros:
     * <ul>
     *   <li>Habilidades: solo las activas y {@code VALIDADA} por el RM.</li>
     *   <li>Proyectos: solo asignaciones {@code ACTIVA} o {@code FINALIZADA}; por cada proyecto
     *       se calcula una sola vez el texto con sus tecnologías.</li>
     *   <li>Cursos: solo los {@code COMPLETADO}, es decir, con evidencia aprobada por el RM.</li>
     *   <li>Experiencia y educación: las activas, de la más reciente a la más antigua. La
     *       educación se muestra sin filtrar por estado de aprobación.</li>
     * </ul>
     *
     * @param colaborador colaborador del que se arma el perfil
     * @return el perfil público
     */
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
                experienciaProfesionalRepository.findByColaboradorAndActivoTrueOrderByFechaInicioDesc(colaborador);
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

    /**
     * Junta los nombres de las habilidades que pide un proyecto, separados por " · ".
     *
     * @param proyecto proyecto consultado
     * @return el texto, o "Sin tecnologías registradas" si no pide ninguna
     */
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

    /**
     * Junta los nombres de las habilidades de un colaborador, separados por " · ". Sirve de
     * texto para la búsqueda por habilidad en la pantalla de Explorar.
     *
     * @param habilidades habilidades del colaborador
     * @return el texto; vacío si no tiene
     */
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

    /**
     * Datos que se muestran del perfil público de un colaborador. Es un objeto de solo lectura
     * (todos sus campos son {@code final}).
     */
    public static class PerfilExplorar {
        private final List<ColaboradorHabilidad> habilidades;
        private final List<ExperienciaProfesional> experiencia;
        private final List<Educacion> educacion;
        private final List<Asignacion> proyectos;
        private final Map<Long, String> habilidadesPorProyecto;
        private final String resumenHabilidades;
        private final List<ColaboradorCurso> cursosCompletados;

        /**
         * @param habilidades habilidades validadas por el RM
         * @param experiencia experiencia profesional
         * @param educacion formación académica
         * @param proyectos asignaciones activas o finalizadas
         * @param habilidadesPorProyecto texto con las tecnologías de cada proyecto, indexado por id de proyecto
         * @param resumenHabilidades nombres de las habilidades en una sola línea
         * @param cursosCompletados cursos completados con evidencia aprobada
         */
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