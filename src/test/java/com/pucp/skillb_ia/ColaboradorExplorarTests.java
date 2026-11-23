package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.*;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.service.col.ColaboradorExplorarService;
import com.pucp.skillb_ia.service.col.ColaboradorExplorarService.PerfilExplorar;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ColaboradorExplorarTests {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private CategoriaHabilidadRepository categoriaHabilidadRepository;
    @Autowired private HabilidadRepository habilidadRepository;
    @Autowired private ColaboradorHabilidadRepository colaboradorHabilidadRepository;
    @Autowired private ProyectoHabilidadRequeridaRepository proyectoHabilidadRequeridaRepository;
    @Autowired private ExperienciaProfesionalRepository experienciaProfesionalRepository;
    @Autowired private EducacionRepository educacionRepository;
    @Autowired private CursoRepository cursoRepository;
    @Autowired private ColaboradorCursoRepository colaboradorCursoRepository;
    @Autowired private ColaboradorExplorarService colaboradorExplorarService;

    // ======================= listarColaboradores =======================

    @Test
    void elDirectorioNoIncluyeAlColaboradorQueBusca() {
        Usuario yo = usuario("yo", "COLABORADOR", true, true);
        Usuario companero = usuario("companero", "COLABORADOR", true, true);

        List<Long> ids = colaboradorExplorarService.listarColaboradores(yo).stream().map(Usuario::getId).toList();

        assertThat(ids).contains(companero.getId());
        assertThat(ids).doesNotContain(yo.getId());
    }

    @Test
    void elDirectorioSoloMuestraColaboradoresActivosYConLaCuentaYaActivada() {
        Usuario yo = usuario("yo", "COLABORADOR", true, true);
        Usuario desactivado = usuario("baja", "COLABORADOR", false, true);
        Usuario invitadoSinActivar = usuario("invitado", "COLABORADOR", true, false);
        Usuario pm = usuario("pm", "PROJECT_MANAGER", true, true);
        Usuario rm = usuario("rm", "RESOURCE_MANAGER", true, true);

        List<Long> ids = colaboradorExplorarService.listarColaboradores(yo).stream().map(Usuario::getId).toList();

        assertThat(ids).doesNotContain(desactivado.getId(), invitadoSinActivar.getId(), pm.getId(), rm.getId());
    }

    // ======================= obtenerPerfiles =======================

    @Test
    void elPerfilPublicoSoloMuestraLasHabilidadesValidadasPorElRm() {
        Usuario companero = usuario("companero", "COLABORADOR", true, true);
        Habilidad java = habilidad("Java");
        Habilidad python = habilidad("Python");
        Habilidad go = habilidad("Go");
        Habilidad rust = habilidad("Rust");
        habilidadDelColaborador(companero, java, EstadoValidacion.VALIDADA, true);
        habilidadDelColaborador(companero, python, EstadoValidacion.PENDIENTE, true);
        habilidadDelColaborador(companero, go, EstadoValidacion.RECHAZADA, true);
        habilidadDelColaborador(companero, rust, EstadoValidacion.VALIDADA, false); // eliminada del perfil

        PerfilExplorar perfil = perfilDe(companero);

        assertThat(perfil.getHabilidades()).hasSize(1);
        assertThat(perfil.getHabilidades().get(0).getHabilidad().getId()).isEqualTo(java.getId());
        assertThat(perfil.getResumenHabilidades()).isEqualTo(java.getNombre());
    }

    @Test
    void elResumenDeHabilidadesUneLasValidadasConPuntoMedio() {
        Usuario companero = usuario("companero", "COLABORADOR", true, true);
        Habilidad java = habilidad("Java");
        Habilidad react = habilidad("React");
        habilidadDelColaborador(companero, java, EstadoValidacion.VALIDADA, true);
        habilidadDelColaborador(companero, react, EstadoValidacion.VALIDADA, true);

        PerfilExplorar perfil = perfilDe(companero);

        assertThat(perfil.getResumenHabilidades()).contains(" · ")
                .contains(java.getNombre()).contains(react.getNombre());
    }

    @Test
    void unColaboradorSinNadaValidadoTieneUnPerfilVacioYSinResumen() {
        Usuario companero = usuario("companero", "COLABORADOR", true, true);

        PerfilExplorar perfil = perfilDe(companero);

        assertThat(perfil.getHabilidades()).isEmpty();
        assertThat(perfil.getExperiencia()).isEmpty();
        assertThat(perfil.getEducacion()).isEmpty();
        assertThat(perfil.getProyectos()).isEmpty();
        assertThat(perfil.getCursosCompletados()).isEmpty();
        assertThat(perfil.getResumenHabilidades()).isEmpty();
    }

    @Test
    void elPerfilIncluyeSoloExperienciaYEducacionActivas() {
        Usuario companero = usuario("companero", "COLABORADOR", true, true);
        experiencia(companero, "Empresa vigente", true, LocalDate.now().minusYears(1));
        experiencia(companero, "Empresa borrada", false, LocalDate.now().minusYears(3));
        educacion(companero, "PUCP", true, LocalDate.now().minusYears(5));
        educacion(companero, "Instituto borrado", false, LocalDate.now().minusYears(8));

        PerfilExplorar perfil = perfilDe(companero);

        assertThat(perfil.getExperiencia()).extracting(ExperienciaProfesional::getEmpresa)
                .containsExactly("Empresa vigente");
        assertThat(perfil.getEducacion()).extracting(Educacion::getInstitucion)
                .containsExactly("PUCP");
    }

    @Test
    void elPerfilListaLaExperienciaDeLaMasRecienteALaMasAntigua() {
        Usuario companero = usuario("companero", "COLABORADOR", true, true);
        experiencia(companero, "Antigua", true, LocalDate.now().minusYears(4));
        experiencia(companero, "Reciente", true, LocalDate.now().minusMonths(2));
        experiencia(companero, "Intermedia", true, LocalDate.now().minusYears(2));

        PerfilExplorar perfil = perfilDe(companero);

        assertThat(perfil.getExperiencia()).extracting(ExperienciaProfesional::getEmpresa)
                .containsExactly("Reciente", "Intermedia", "Antigua");
    }

    @Test
    void losProyectosDestacadosSonSoloLosActivosOFinalizadosConSusTecnologias() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", true, true);
        Usuario companero = usuario("companero", "COLABORADOR", true, true);
        Habilidad java = habilidad("Java");
        Habilidad react = habilidad("React");
        Proyecto conTecnologias = proyecto(pm, EstadoProyecto.ACTIVO);
        Proyecto sinTecnologias = proyecto(pm, EstadoProyecto.FINALIZADO);
        Proyecto soloPendiente = proyecto(pm, EstadoProyecto.ACTIVO);
        Proyecto rechazado = proyecto(pm, EstadoProyecto.ACTIVO);
        requerir(conTecnologias, java);
        requerir(conTecnologias, react);
        asignacion(conTecnologias, companero, EstadoAsignacion.ACTIVA);
        asignacion(sinTecnologias, companero, EstadoAsignacion.FINALIZADA);
        asignacion(soloPendiente, companero, EstadoAsignacion.PENDIENTE);
        asignacion(rechazado, companero, EstadoAsignacion.RECHAZADA);

        PerfilExplorar perfil = perfilDe(companero);

        assertThat(perfil.getProyectos()).extracting(a -> a.getProyecto().getId())
                .containsExactlyInAnyOrder(conTecnologias.getId(), sinTecnologias.getId());

        Map<Long, String> tecnologias = perfil.getHabilidadesPorProyecto();
        assertThat(tecnologias.get(conTecnologias.getId()))
                .contains(java.getNombre()).contains(react.getNombre()).contains(" · ");
        assertThat(tecnologias.get(sinTecnologias.getId())).isEqualTo("Sin tecnologías registradas");
        assertThat(tecnologias).doesNotContainKeys(soloPendiente.getId(), rechazado.getId());
    }

    @Test
    void soloSeMuestranLosCursosCompletadosConEvidenciaAprobada() {
        Usuario admin = usuario("admin", "ADMINISTRADOR", true, true);
        Usuario companero = usuario("companero", "COLABORADOR", true, true);
        Curso completado = curso(admin, "Spring Boot");
        Curso enCurso = curso(admin, "Docker");
        Curso solicitado = curso(admin, "Kubernetes");
        Curso evidenciaPendiente = curso(admin, "AWS");
        Curso noCompletado = curso(admin, "Linux");
        Curso rechazado = curso(admin, "Redis");
        inscripcion(companero, completado, EstadoColaboradorCurso.COMPLETADO);
        inscripcion(companero, enCurso, EstadoColaboradorCurso.EN_CURSO);
        inscripcion(companero, solicitado, EstadoColaboradorCurso.SOLICITADO);
        inscripcion(companero, evidenciaPendiente, EstadoColaboradorCurso.EVIDENCIA_PENDIENTE);
        inscripcion(companero, noCompletado, EstadoColaboradorCurso.NO_COMPLETADO);
        inscripcion(companero, rechazado, EstadoColaboradorCurso.RECHAZADO);

        PerfilExplorar perfil = perfilDe(companero);

        assertThat(perfil.getCursosCompletados()).hasSize(1);
        assertThat(perfil.getCursosCompletados().get(0).getCurso().getId()).isEqualTo(completado.getId());
    }

    @Test
    void obtenerPerfilesDevuelveUnPerfilPorCadaColaboradorSinMezclarSusDatos() {
        Usuario a = usuario("a", "COLABORADOR", true, true);
        Usuario b = usuario("b", "COLABORADOR", true, true);
        Habilidad java = habilidad("Java");
        Habilidad react = habilidad("React");
        habilidadDelColaborador(a, java, EstadoValidacion.VALIDADA, true);
        habilidadDelColaborador(b, react, EstadoValidacion.VALIDADA, true);

        Map<Long, PerfilExplorar> perfiles = colaboradorExplorarService.obtenerPerfiles(List.of(a, b));

        assertThat(perfiles).containsOnlyKeys(a.getId(), b.getId());
        assertThat(perfiles.get(a.getId()).getResumenHabilidades()).isEqualTo(java.getNombre());
        assertThat(perfiles.get(b.getId()).getResumenHabilidades()).isEqualTo(react.getNombre());
    }

    @Test
    void sinColaboradoresElMapaDePerfilesEstaVacio() {
        assertThat(colaboradorExplorarService.obtenerPerfiles(List.of())).isEmpty();
    }

    // ---------- Utilidades ----------

    private PerfilExplorar perfilDe(Usuario colaborador) {
        return colaboradorExplorarService.obtenerPerfiles(List.of(colaborador)).get(colaborador.getId());
    }

    private Usuario usuario(String prefijo, String nombreRol, boolean activo, boolean cuentaActivada) {
        Usuario u = new Usuario();
        u.setCorreo(prefijo + "." + UUID.randomUUID() + "@test.local");
        u.setNombre(cuentaActivada ? prefijo : null);
        u.setApellido(cuentaActivada ? "Prueba" : null);
        u.setRol(rol(nombreRol));
        u.setActivo(activo);
        u.setHorasContratadasSemana(new BigDecimal("40.00"));
        u.setHorasDisponibles(new BigDecimal("40.00"));
        return usuarioRepository.save(u);
    }

    private Rol rol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol r = new Rol();
            r.setNombre(nombre);
            return rolRepository.save(r);
        });
    }

    private Habilidad habilidad(String nombre) {
        CategoriaHabilidad categoria = categoriaHabilidadRepository.findAll().stream().findFirst().orElseGet(() -> {
            CategoriaHabilidad c = new CategoriaHabilidad();
            c.setNombre("Categoria " + UUID.randomUUID());
            return categoriaHabilidadRepository.save(c);
        });
        Habilidad h = new Habilidad();
        h.setNombre(nombre + " " + UUID.randomUUID().toString().substring(0, 6));
        h.setCategoria(categoria);
        return habilidadRepository.save(h);
    }

    private void habilidadDelColaborador(Usuario colaborador, Habilidad habilidad, EstadoValidacion estado, boolean activo) {
        ColaboradorHabilidad ch = new ColaboradorHabilidad();
        ch.setColaborador(colaborador);
        ch.setHabilidad(habilidad);
        ch.setNivelDominio(NivelDominio.INTERMEDIO);
        ch.setEstadoValidacion(estado);
        ch.setActivo(activo);
        colaboradorHabilidadRepository.save(ch);
    }

    private void experiencia(Usuario colaborador, String empresa, boolean activa, LocalDate inicio) {
        ExperienciaProfesional e = new ExperienciaProfesional();
        e.setColaborador(colaborador);
        e.setEmpresa(empresa);
        e.setCargo("Desarrollador");
        e.setDescripcion("Trabajo en " + empresa);
        e.setFechaInicio(inicio);
        e.setActivo(activa);
        experienciaProfesionalRepository.save(e);
    }

    private void educacion(Usuario colaborador, String institucion, boolean activa, LocalDate inicio) {
        Educacion e = new Educacion();
        e.setColaborador(colaborador);
        e.setInstitucion(institucion);
        e.setTitulo("Ingeniería");
        e.setFechaInicio(inicio);
        e.setActivo(activa);
        educacionRepository.save(e);
    }

    private Proyecto proyecto(Usuario pm, EstadoProyecto estado) {
        Proyecto p = new Proyecto();
        p.setNombre("Proyecto explorar " + UUID.randomUUID());
        p.setDescripcion("Proyecto de prueba.");
        p.setEstado(estado);
        p.setPrioridad(Prioridad.MEDIA);
        p.setJustificacionPrioridad("Prueba.");
        p.setColaboradoresRequeridos(3);
        p.setPm(pm);
        p.setPresupuesto(new BigDecimal("10000.00"));
        p.setFechaInicio(LocalDate.now().minusMonths(2));
        p.setFechaFinEstimada(LocalDate.now().plusMonths(2));
        return proyectoRepository.save(p);
    }

    private void requerir(Proyecto proyecto, Habilidad habilidad) {
        ProyectoHabilidadRequerida r = new ProyectoHabilidadRequerida();
        r.setProyecto(proyecto);
        r.setHabilidad(habilidad);
        r.setNivelRequerido(NivelDominio.INTERMEDIO);
        r.setCantidadPersonas(1);
        r.setHorasSemanales(new BigDecimal("10"));
        proyectoHabilidadRequeridaRepository.save(r);
    }

    private void asignacion(Proyecto proyecto, Usuario colaborador, EstadoAsignacion estado) {
        Asignacion a = new Asignacion();
        a.setProyecto(proyecto);
        a.setColaborador(colaborador);
        a.setHorasSemanales(new BigDecimal("8"));
        a.setOrigen(OrigenAsignacion.PROPUESTA_PM);
        a.setEstado(estado);
        a.setFechaSolicitud(LocalDateTime.now().minusDays(1));
        asignacionRepository.save(a);
    }

    private Curso curso(Usuario admin, String nombre) {
        Curso c = new Curso();
        c.setNombre(nombre + " " + UUID.randomUUID().toString().substring(0, 5));
        c.setDescripcion("Curso de prueba.");
        c.setCategoria("Técnico");
        c.setHoras(new BigDecimal("10.00"));
        c.setActivo(true);
        c.setCreadoPor(admin);
        return cursoRepository.save(c);
    }

    private void inscripcion(Usuario colaborador, Curso curso, EstadoColaboradorCurso estado) {
        ColaboradorCurso cc = new ColaboradorCurso();
        cc.setColaborador(colaborador);
        cc.setCurso(curso);
        cc.setOrigen(OrigenCurso.SOLICITUD_COLABORADOR);
        cc.setEstado(estado);
        colaboradorCursoRepository.save(cc);
    }
}