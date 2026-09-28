package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.SolicitudPersonal;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.EstadoSolicitudPersonal;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.SolicitudPersonalRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.rm.RmSolicitudPersonalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@ActiveProfiles("test")
class RmSolicitudPersonalTests {

    @Autowired private WebApplicationContext context;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private SolicitudPersonalRepository solicitudRepository;
    @Autowired private RmSolicitudPersonalService solicitudService;

    private MockMvc mockMvc;
    private Usuario rm;
    private Usuario pm;
    private Proyecto proyecto;

    @BeforeEach
    void prepararDatos() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        solicitudRepository.deleteAll();
        rm = obtenerUsuario("rm.solicitudes@skillbridge.test", "Rosa", "Mendoza",
                obtenerRol("RESOURCE_MANAGER"));
        pm = obtenerUsuario("pm.solicitudes@skillbridge.test", "Pablo", "Morales",
                obtenerRol("PROJECT_MANAGER"));

        proyecto = new Proyecto();
        proyecto.setNombre("Proyecto solicitudes " + System.nanoTime());
        proyecto.setDescripcion("Proyecto para validar solicitudes de personal.");
        proyecto.setEstado(EstadoProyecto.ACTIVO);
        proyecto.setPrioridad(Prioridad.ALTA);
        proyecto.setJustificacionPrioridad("Debe completar el equipo pronto.");
        proyecto.setColaboradoresRequeridos(3);
        proyecto.setPm(pm);
        proyecto = proyectoRepository.save(proyecto);
    }

    @Test
    void creaSolicitudSeparadaDeUnaAsignacion() {
        SolicitudPersonal solicitud = solicitudService.crearDesdePm(
                proyecto.getId(), 2, "Backend y DevOps", "Priorizar Docker.", pm.getId());

        assertNotNull(solicitud.getId());
        assertEquals(EstadoSolicitudPersonal.PENDIENTE, solicitud.getEstado());
        assertEquals(2, solicitud.getCantidadColaboradores());
        assertNull(solicitud.getRmResponsable());
    }

    @Test
    void recorrePendienteEnAtencionYAtendida() {
        SolicitudPersonal solicitud = crearSolicitud();

        solicitudService.iniciarAtencion(solicitud.getId(), rm.getId());
        SolicitudPersonal iniciada = solicitudRepository.findById(solicitud.getId()).orElseThrow();
        assertEquals(EstadoSolicitudPersonal.EN_ATENCION, iniciada.getEstado());
        assertEquals(rm.getId(), iniciada.getRmResponsable().getId());
        assertNotNull(iniciada.getFechaInicioAtencion());

        solicitudService.marcarAtendida(solicitud.getId(), rm.getId());
        SolicitudPersonal atendida = solicitudRepository.findById(solicitud.getId()).orElseThrow();
        assertEquals(EstadoSolicitudPersonal.ATENDIDA, atendida.getEstado());
        assertNotNull(atendida.getFechaAtencion());
    }

    @Test
    void impideDosSolicitudesAbiertasParaElMismoProyecto() {
        crearSolicitud();
        assertThrows(IllegalStateException.class, () -> solicitudService.crearDesdePm(
                proyecto.getId(), 1, null, null, pm.getId()));
    }

    @Test
    void renderizaListadoDetalleYVistaDeProyecto() throws Exception {
        SolicitudPersonal solicitud = crearSolicitud();

        mockMvc.perform(get("/rm/asignaciones/solicitudes-colaboradores"))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-solicitudes-colaboradores"))
                .andExpect(model().attributeExists("solicitudes", "pendientes", "enAtencion", "atendidas"));

        mockMvc.perform(get("/rm/asignaciones/solicitudes-colaboradores/detalle")
                        .param("id", solicitud.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-detalle-solicitud-colaboradores"))
                .andExpect(model().attributeExists("solicitud"));

        mockMvc.perform(get("/rm/proyectos/detalle-solicitudes")
                        .param("solicitudId", solicitud.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-detalle-proyecto-solicitudes"))
                .andExpect(model().attributeExists("solicitud", "proyecto"));
    }

    @Test
    void iniciarAtencionMuestraElMensajeDeExitoEnLaBusqueda() throws Exception {
        SolicitudPersonal solicitud = crearSolicitud();
        String destino = "/rm/proyectos/buscar-colaboradores?proyectoId=" + proyecto.getId();
        UsuarioDetails details = new UsuarioDetails(usuarioRepository.findActivosByRolNombre("RESOURCE_MANAGER")
                .stream().filter(u -> u.getId().equals(rm.getId())).findFirst().orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
        try {
            MvcResult resultado = mockMvc.perform(
                            post("/rm/asignaciones/solicitudes-colaboradores/" + solicitud.getId() + "/iniciar"))
                    .andExpect(redirectedUrl(destino))
                    .andExpect(flash().attribute("mensajeExito", "La solicitud pasó a En atención."))
                    .andExpect(flash().attributeCount(1))
                    .andReturn();

            // Simula la solicitud GET que hace el navegador tras la redirección, con el flash del POST.
            String html = mockMvc.perform(get(destino).flashAttrs(resultado.getFlashMap()))
                    .andExpect(status().isOk())
                    .andExpect(view().name("rm/rm-buscar-colaboradores-proyecto"))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertTrue(html.contains(
                    "<div class=\"alert alert-success\" role=\"alert\">La solicitud pasó a En atención.</div>"));
            assertEquals(1, html.split("La solicitud pasó a En atención.", -1).length - 1);
            assertFalse(html.contains("class=\"alert alert-danger\" role=\"alert\""));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void busquedaMuestraErrorYOmiteMensajesVacios() throws Exception {
        String destino = "/rm/proyectos/buscar-colaboradores?proyectoId=" + proyecto.getId();

        String html = mockMvc.perform(get(destino).flashAttr("mensajeError", "No se pudo proponer."))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("<div class=\"alert alert-danger\" role=\"alert\">No se pudo proponer.</div>"));
        assertFalse(html.contains("class=\"alert alert-success\" role=\"alert\""));

        html = mockMvc.perform(get(destino).flashAttr("mensajeExito", "").flashAttr("mensajeError", " "))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(html.contains("class=\"alert alert-success\" role=\"alert\""));
        assertFalse(html.contains("class=\"alert alert-danger\" role=\"alert\""));
    }

    private SolicitudPersonal crearSolicitud() {
        return solicitudService.crearDesdePm(
                proyecto.getId(), 2, "Backend y DevOps", "Priorizar Docker.", pm.getId());
    }

    private Rol obtenerRol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol rol = new Rol();
            rol.setNombre(nombre);
            return rolRepository.save(rol);
        });
    }

    private Usuario obtenerUsuario(String correo, String nombre, String apellido, Rol rol) {
        return usuarioRepository.findByCorreo(correo).orElseGet(() -> {
            Usuario usuario = new Usuario();
            usuario.setCorreo(correo);
            usuario.setNombre(nombre);
            usuario.setApellido(apellido);
            usuario.setRol(rol);
            return usuarioRepository.save(usuario);
        });
    }
}
