package com.pucp.skillb_ia;

import com.pucp.skillb_ia.dto.NotificacionView;
import com.pucp.skillb_ia.model.ColaboradorCurso;
import com.pucp.skillb_ia.model.Curso;
import com.pucp.skillb_ia.model.Notificacion;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoColaboradorCurso;
import com.pucp.skillb_ia.model.enums.OrigenCurso;
import com.pucp.skillb_ia.repository.ColaboradorCursoRepository;
import com.pucp.skillb_ia.repository.CursoRepository;
import com.pucp.skillb_ia.repository.NotificacionRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.NotificacionService;
import com.pucp.skillb_ia.service.col.ColaboradorCursoService;
import com.pucp.skillb_ia.service.rm.RmCursoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// TASK-016: cada nueva solicitud de curso de un colaborador notifica a todos los RM activos.
@SpringBootTest
@ActiveProfiles("test")
class ColaboradorCursoSolicitudTests {
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CursoRepository cursoRepository;
    @Autowired private ColaboradorCursoRepository colaboradorCursoRepository;
    @Autowired private NotificacionRepository notificacionRepository;
    @Autowired private ColaboradorCursoService colaboradorCursoService;
    @Autowired private RmCursoService rmCursoService;
    @Autowired private NotificacionService notificacionService;

    private Usuario rmUno;
    private Usuario rmDos;
    private Usuario rmInactivo;
    private Usuario colaborador;
    private Curso curso;
    private Curso cursoInactivo;

    @BeforeEach
    void prepararDatos() {
        notificacionRepository.deleteAll();
        colaboradorCursoRepository.deleteAll();
        cursoRepository.deleteAll();

        Usuario admin = usuario("admin.solcurso@skillbridge.test", "Alba", "Admin", rol("ADMIN"), true);
        rmUno = usuario("rm1.solcurso@skillbridge.test", "Rosa", "Uno", rol("RESOURCE_MANAGER"), true);
        rmDos = usuario("rm2.solcurso@skillbridge.test", "Raúl", "Dos", rol("RESOURCE_MANAGER"), true);
        rmInactivo = usuario("rm3.solcurso@skillbridge.test", "Rita", "Inactiva", rol("RESOURCE_MANAGER"), false);
        colaborador = usuario("col.solcurso@skillbridge.test", "Carla", "Quispe", rol("COLABORADOR"), true);
        curso = curso(admin, "Docker esencial test", true);
        cursoInactivo = curso(admin, "Curso retirado test", false);
    }

    @Test
    void solicitudCreadaNotificaUnaVezACadaRmActivoConEnlaceALaBandeja() {
        colaboradorCursoService.solicitarInscripcion(colaborador, curso.getId(), "Lo necesito en el proyecto.");

        List<ColaboradorCurso> registros = colaboradorCursoRepository.findByColaborador(colaborador);
        assertEquals(1, registros.size());
        ColaboradorCurso solicitud = registros.get(0);
        assertEquals(EstadoColaboradorCurso.SOLICITADO, solicitud.getEstado());
        assertEquals(OrigenCurso.SOLICITUD_COLABORADOR, solicitud.getOrigen());

        List<Usuario> rmsActivos = usuarioRepository.findActivosByRolNombre("RESOURCE_MANAGER");
        assertTrue(rmsActivos.stream().anyMatch(item -> item.getId().equals(rmUno.getId())));
        assertTrue(rmsActivos.stream().anyMatch(item -> item.getId().equals(rmDos.getId())));
        assertEquals(rmsActivos.size(), notificacionRepository.count());
        for (Usuario rm : rmsActivos) {
            List<Notificacion> propias = notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(rm);
            assertEquals(1, propias.size(), "Cada RM debe recibir exactamente una notificación");
            Notificacion notificacion = propias.get(0);
            assertEquals("CURSO_SOLICITADO", notificacion.getTipo());
            assertEquals(CategoriaNotificacion.CURSO, notificacion.getCategoria());
            assertEquals("COLABORADOR_CURSO", notificacion.getEntidad());
            assertEquals(solicitud.getId(), notificacion.getEntidadId());
        }
        assertTrue(notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(rmInactivo).isEmpty());
        assertTrue(notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(colaborador).isEmpty());

        NotificacionView vista = notificacionService.listar(rmUno.getId()).get(0);
        assertEquals("Nueva solicitud de curso", vista.titulo());
        assertEquals("Carla Quispe solicitó inscribirse al curso \"Docker esencial test\".", vista.descripcion());
        assertEquals("/rm/cursos/solicitudes", vista.url());
    }

    @Test
    void solicitudRechazadaPorValidacionNoCreaRegistroNiNotificaciones() {
        assertThrows(IllegalArgumentException.class,
                () -> colaboradorCursoService.solicitarInscripcion(colaborador, cursoInactivo.getId(), null));
        assertThrows(IllegalArgumentException.class,
                () -> colaboradorCursoService.solicitarInscripcion(colaborador, curso.getId(), "x".repeat(501)));
        assertEquals(0, notificacionRepository.count());
        assertTrue(colaboradorCursoRepository.findByColaborador(colaborador).isEmpty());

        colaboradorCursoService.solicitarInscripcion(colaborador, curso.getId(), null);
        long trasPrimera = notificacionRepository.count();
        assertThrows(IllegalArgumentException.class,
                () -> colaboradorCursoService.solicitarInscripcion(colaborador, curso.getId(), null));
        assertEquals(trasPrimera, notificacionRepository.count());
        assertEquals(1, colaboradorCursoRepository.findByColaborador(colaborador).size());
    }

    @Test
    void conteoPendienteSoloIncluyeSolicitudesEnEstadoSolicitadoYAprobarRechazarSiguenFuncionando() {
        colaboradorCursoService.solicitarInscripcion(colaborador, curso.getId(), null);
        Usuario otro = usuario("col2.solcurso@skillbridge.test", "Mario", "Paz", rol("COLABORADOR"), true);
        colaboradorCursoService.solicitarInscripcion(otro, curso.getId(), null);
        assertEquals(2, rmCursoService.contarSolicitudesPendientes());

        ColaboradorCurso primera = colaboradorCursoRepository.findByColaborador(colaborador).get(0);
        ColaboradorCurso segunda = colaboradorCursoRepository.findByColaborador(otro).get(0);
        rmCursoService.aprobar(primera.getId(), "Refuerza el plan de formación.", rmUno.getId());
        assertEquals(1, rmCursoService.contarSolicitudesPendientes());
        rmCursoService.rechazar(segunda.getId(), "No corresponde al plan de formación.", rmUno.getId());
        assertEquals(0, rmCursoService.contarSolicitudesPendientes());

        assertEquals(EstadoColaboradorCurso.EN_CURSO,
                colaboradorCursoRepository.findById(primera.getId()).orElseThrow().getEstado());
        assertEquals(EstadoColaboradorCurso.RECHAZADO,
                colaboradorCursoRepository.findById(segunda.getId()).orElseThrow().getEstado());
    }

    private Curso curso(Usuario admin, String nombre, boolean activo) {
        Curso nuevo = new Curso();
        nuevo.setNombre(nombre);
        nuevo.setDescripcion("Curso de prueba.");
        nuevo.setCategoria("Técnico");
        nuevo.setHoras(new BigDecimal("12.00"));
        nuevo.setActivo(activo);
        nuevo.setCreadoPor(admin);
        return cursoRepository.save(nuevo);
    }

    private Rol rol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol nuevo = new Rol();
            nuevo.setNombre(nombre);
            return rolRepository.save(nuevo);
        });
    }

    private Usuario usuario(String correo, String nombre, String apellido, Rol rol, boolean activo) {
        Usuario usuario = usuarioRepository.findByCorreo(correo).orElseGet(Usuario::new);
        usuario.setCorreo(correo);
        usuario.setNombre(nombre);
        usuario.setApellido(apellido);
        usuario.setRol(rol);
        usuario.setActivo(activo);
        return usuarioRepository.save(usuario);
    }
}
