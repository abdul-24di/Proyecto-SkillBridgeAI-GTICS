package com.pucp.skillb_ia;

import com.pucp.skillb_ia.dto.RmCursoView;
import com.pucp.skillb_ia.model.ColaboradorCurso;
import com.pucp.skillb_ia.model.Curso;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoColaboradorCurso;
import com.pucp.skillb_ia.model.enums.OrigenCurso;
import com.pucp.skillb_ia.repository.ColaboradorCursoRepository;
import com.pucp.skillb_ia.repository.CursoRepository;
import com.pucp.skillb_ia.repository.NotificacionRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.rm.RmCursoService;
import com.pucp.skillb_ia.service.NotificacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class RmCursoTests {
    @Autowired private WebApplicationContext context;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CursoRepository cursoRepository;
    @Autowired private ColaboradorCursoRepository colaboradorCursoRepository;
    @Autowired private NotificacionRepository notificacionRepository;
    @Autowired private RmCursoService cursoService;
    @Autowired private NotificacionService notificacionService;

    private MockMvc mockMvc;
    private Usuario admin;
    private Usuario rm;
    private Usuario colaboradorUno;
    private Usuario colaboradorDos;
    private Curso spring;
    private Curso aws;
    private Curso inactivo;

    @BeforeEach
    void prepararDatos() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        notificacionRepository.deleteAll();
        colaboradorCursoRepository.deleteAll();
        cursoRepository.deleteAll();

        admin = usuario("admin.cursos@skillbridge.test", "Adriana", "Curso", rol("ADMIN"), null);
        rm = usuario("rm.cursos@skillbridge.test", "Rosa", "Mendoza", rol("RESOURCE_MANAGER"), null);
        colaboradorUno = usuario("ana.cursos@skillbridge.test", "Ana", "Torres", rol("COLABORADOR"), "Backend Developer");
        colaboradorDos = usuario("luis.cursos@skillbridge.test", "Luis", "Ramos", rol("COLABORADOR"), "Cloud Engineer");
        spring = curso("Spring Boot avanzado test", "Técnico", "20.00", true);
        aws = curso("AWS Practitioner test", "Certificación", "40.00", true);
        inactivo = curso("Curso inactivo test", "Técnico", "8.00", false);
    }

    @Test
    void catalogoMuestraSoloActivosYAplicaFiltrosReales() {
        inscripcion(colaboradorUno, spring, OrigenCurso.SOLICITUD_COLABORADOR,
                EstadoColaboradorCurso.SOLICITADO);
        inscripcion(colaboradorDos, aws, OrigenCurso.ASIGNADO_POR_RM,
                EstadoColaboradorCurso.EN_CURSO);

        RmCursoView.Catalogo catalogo = cursoService.obtenerCatalogo("", "", "");
        RmCursoView.Catalogo filtrado = cursoService.obtenerCatalogo("spring", "Técnico", "media");

        assertEquals(2, catalogo.getCursosActivos());
        assertEquals(1, catalogo.getSolicitudesPendientes());
        assertEquals(1, catalogo.getInscripcionesActivas());
        assertTrue(catalogo.getCursos().stream().noneMatch(item -> item.getId().equals(inactivo.getId())));
        assertEquals(1, filtrado.getCursos().size());
        assertEquals(spring.getId(), filtrado.getCursos().get(0).getId());
    }

    @Test
    void asignacionDirectaEvitaDuplicadosYNotificaAlColaborador() {
        ColaboradorCurso creada = cursoService.asignarDirectamente(
                colaboradorUno.getId(), spring.getId(),
                "Fortalecer competencias backend.", rm.getId());

        assertEquals(OrigenCurso.ASIGNADO_POR_RM, creada.getOrigen());
        assertEquals(EstadoColaboradorCurso.EN_CURSO, creada.getEstado());
        assertEquals("Fortalecer competencias backend.", creada.getMotivoRespuesta());
        assertEquals(1, notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(colaboradorUno).size());
        Long notificacionId = notificacionService.listar(colaboradorUno.getId()).get(0).id();
        notificacionService.marcarLeida(colaboradorUno.getId(), notificacionId);
        assertTrue(notificacionService.listar(colaboradorUno.getId()).get(0).leida());
        assertThrows(IllegalStateException.class, () -> cursoService.asignarDirectamente(
                colaboradorUno.getId(), spring.getId(), "Intento duplicado.", rm.getId()));
    }

    @Test
    void rmApruebaYRechazaSolicitudesConMotivoYNotificacion() {
        ColaboradorCurso paraAprobar = inscripcion(colaboradorUno, spring,
                OrigenCurso.SOLICITUD_COLABORADOR, EstadoColaboradorCurso.SOLICITADO);
        ColaboradorCurso paraRechazar = inscripcion(colaboradorDos, aws,
                OrigenCurso.SOLICITUD_COLABORADOR, EstadoColaboradorCurso.SOLICITADO);

        cursoService.aprobar(paraAprobar.getId(), rm.getId());
        cursoService.rechazar(paraRechazar.getId(), "Primero debe completar el curso básico.", rm.getId());

        ColaboradorCurso aprobada = colaboradorCursoRepository.findById(paraAprobar.getId()).orElseThrow();
        ColaboradorCurso rechazada = colaboradorCursoRepository.findById(paraRechazar.getId()).orElseThrow();
        assertEquals(EstadoColaboradorCurso.EN_CURSO, aprobada.getEstado());
        assertEquals(EstadoColaboradorCurso.RECHAZADO, rechazada.getEstado());
        assertEquals("Primero debe completar el curso básico.", rechazada.getMotivoRespuesta());
        assertEquals(1, notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(colaboradorUno).size());
        assertEquals(1, notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(colaboradorDos).size());
        assertThrows(IllegalStateException.class,
                () -> cursoService.aprobar(paraAprobar.getId(), rm.getId()));
    }

    @Test
    void noPermiteNuevasInscripcionesEnCursosOColaboradoresInactivos() {
        ColaboradorCurso solicitud = inscripcion(colaboradorUno, inactivo,
                OrigenCurso.SOLICITUD_COLABORADOR, EstadoColaboradorCurso.SOLICITADO);
        assertThrows(IllegalArgumentException.class,
                () -> cursoService.aprobar(solicitud.getId(), rm.getId()));
        assertThrows(IllegalArgumentException.class, () -> cursoService.asignarDirectamente(
                colaboradorUno.getId(), inactivo.getId(), "Capacitación técnica.", rm.getId()));

        ColaboradorCurso otra = inscripcion(colaboradorDos, spring,
                OrigenCurso.SOLICITUD_COLABORADOR, EstadoColaboradorCurso.SOLICITADO);
        colaboradorDos.setActivo(false);
        usuarioRepository.save(colaboradorDos);
        assertThrows(IllegalArgumentException.class,
                () -> cursoService.aprobar(otra.getId(), rm.getId()));
        assertEquals(EstadoColaboradorCurso.SOLICITADO,
                colaboradorCursoRepository.findById(otra.getId()).orElseThrow().getEstado());
        assertTrue(notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(colaboradorDos).isEmpty());
    }

    @Test
    void filtroTodosIncluyeHistorialSinCambiarElPredeterminado() throws Exception {
        inscripcion(colaboradorUno, spring, OrigenCurso.SOLICITUD_COLABORADOR,
                EstadoColaboradorCurso.SOLICITADO);
        inscripcion(colaboradorDos, aws, OrigenCurso.SOLICITUD_COLABORADOR,
                EstadoColaboradorCurso.RECHAZADO);

        mockMvc.perform(get("/rm/cursos/solicitudes"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("estadoSeleccionado", "SOLICITADO"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Luis Ramos"))));
        mockMvc.perform(get("/rm/cursos/solicitudes").param("estado", ""))
                .andExpect(status().isOk())
                .andExpect(model().attribute("estadoSeleccionado", ""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Luis Ramos")));
    }

    @Test
    void renderizaCatalogoBandejaYFormularioSinDatosSimulados() throws Exception {
        inscripcion(colaboradorUno, spring, OrigenCurso.SOLICITUD_COLABORADOR,
                EstadoColaboradorCurso.SOLICITADO);

        mockMvc.perform(get("/rm/cursos"))
                .andExpect(status().isOk()).andExpect(view().name("rm/rm-cursos"))
                .andExpect(model().attributeExists("catalogo"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Spring Boot avanzado test")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Kubernetes para entornos productivos"))));

        mockMvc.perform(get("/rm/cursos/solicitudes"))
                .andExpect(status().isOk()).andExpect(view().name("rm/rm-solicitudes-cursos"))
                .andExpect(model().attributeExists("bandeja"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ana Torres")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Motivo obligatorio")));

        mockMvc.perform(get("/rm/cursos/asignar").param("curso", spring.getId().toString()))
                .andExpect(status().isOk()).andExpect(view().name("rm/rm-asignar-curso"))
                .andExpect(model().attributeExists("colaboradoresCurso", "cursosActivos"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Confirmar inscripción")));
    }

    private Curso curso(String nombre, String categoria, String horas, boolean activo) {
        Curso curso = new Curso();
        curso.setNombre(nombre);
        curso.setDescripcion("Descripción del curso de prueba.");
        curso.setCategoria(categoria);
        curso.setHoras(new BigDecimal(horas));
        curso.setActivo(activo);
        curso.setCreadoPor(admin);
        return cursoRepository.save(curso);
    }

    private ColaboradorCurso inscripcion(Usuario colaborador, Curso curso,
                                         OrigenCurso origen, EstadoColaboradorCurso estado) {
        ColaboradorCurso inscripcion = new ColaboradorCurso();
        inscripcion.setColaborador(colaborador);
        inscripcion.setCurso(curso);
        inscripcion.setOrigen(origen);
        inscripcion.setEstado(estado);
        if (origen == OrigenCurso.ASIGNADO_POR_RM || estado != EstadoColaboradorCurso.SOLICITADO) {
            inscripcion.setAsignadoPor(rm);
            inscripcion.setFechaRespuesta(LocalDateTime.now());
        }
        return colaboradorCursoRepository.save(inscripcion);
    }

    private Rol rol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol rol = new Rol();
            rol.setNombre(nombre);
            return rolRepository.save(rol);
        });
    }

    private Usuario usuario(String correo, String nombre, String apellido, Rol rol, String cargo) {
        Usuario usuario = usuarioRepository.findByCorreo(correo).orElseGet(Usuario::new);
        usuario.setCorreo(correo);
        usuario.setNombre(nombre);
        usuario.setApellido(apellido);
        usuario.setRol(rol);
        usuario.setCargo(cargo);
        usuario.setActivo(true);
        return usuarioRepository.save(usuario);
    }
}
