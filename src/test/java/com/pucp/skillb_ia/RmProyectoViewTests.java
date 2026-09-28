package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.rm.RmProyectoRevisionService;
import org.junit.jupiter.api.AfterEach;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@ActiveProfiles("test")
class RmProyectoViewTests {
    @Autowired private WebApplicationContext context;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private RmProyectoRevisionService revisionService;

    private MockMvc mockMvc;
    private Long proyectoId;
    private Long rmId;
    private Long pmId;
    private UsuarioDetails rmDetails;

    @BeforeEach
    void prepararDatos() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        Rol rolPm = rolRepository.findByNombre("PROJECT_MANAGER").orElseGet(() -> {
            Rol rol = new Rol();
            rol.setNombre("PROJECT_MANAGER");
            return rolRepository.save(rol);
        });
        Usuario pm = usuarioRepository.findByCorreo("pm.proyecto@skillbridge.test").orElseGet(() -> {
            Usuario usuario = new Usuario();
            usuario.setCorreo("pm.proyecto@skillbridge.test");
            usuario.setNombre("Project");
            usuario.setApellido("Manager");
            usuario.setRol(rolPm);
            return usuarioRepository.save(usuario);
        });
        pmId = pm.getId();
        Rol rolRm = rolRepository.findByNombre("RESOURCE_MANAGER").orElseGet(() -> {
            Rol rol = new Rol();
            rol.setNombre("RESOURCE_MANAGER");
            return rolRepository.save(rol);
        });
        Usuario rm = usuarioRepository.findByCorreo("rm.revision@skillbridge.test").orElseGet(() -> {
            Usuario usuario = new Usuario();
            usuario.setCorreo("rm.revision@skillbridge.test");
            usuario.setNombre("Resource");
            usuario.setApellido("Manager");
            usuario.setRol(rolRm);
            return usuarioRepository.save(usuario);
        });
        rmId = rm.getId();
        rm.setRol(rolRm); // el rol cargado es LAZY
        rmDetails = new UsuarioDetails(rm);
        Proyecto proyecto = new Proyecto();
        proyecto.setNombre("Proyecto de prueba RM");
        proyecto.setDescripcion("Proyecto utilizado para validar las vistas de consulta.");
        proyecto.setPrioridad(Prioridad.MEDIA);
        proyecto.setJustificacionPrioridad("Validación de la consulta del Resource Manager.");
        proyecto.setPm(pm);
        proyectoId = proyectoRepository.save(proyecto).getId();
    }

    @Test
    void renderizaListadoDeProyectos() throws Exception {
        mockMvc.perform(get("/rm/proyectos"))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-proyectos"))
                .andExpect(model().attributeExists("proyectos", "totalProyectos"));
    }

    @Test
    void renderizaDetallePorId() throws Exception {
        mockMvc.perform(get("/rm/proyectos/detalle").param("id", proyectoId.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-detalle-proyecto"))
                .andExpect(model().attributeExists("proyecto"));
    }

    @Test
    void renderizaRevisionPorId() throws Exception {
        mockMvc.perform(get("/rm/proyectos/revision").param("id", proyectoId.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-revision-proyecto"))
                .andExpect(model().attributeExists("proyecto"));
    }

    @Test
    void asignaPresupuestoYAprobarActivaElProyecto() {
        revisionService.asignarPresupuesto(proyectoId, new BigDecimal("45000"), rmId);
        revisionService.aprobar(proyectoId, "Alcance validado", rmId);

        Proyecto proyecto = proyectoRepository.findById(proyectoId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(EstadoProyecto.ACTIVO, proyecto.getEstado());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("45000.00"), proyecto.getPresupuesto());
        org.junit.jupiter.api.Assertions.assertEquals(rmId, proyecto.getRmRevisor().getId());
    }

    @Test
    void rechazarRegistraEstadoMotivoYRevisor() {
        revisionService.rechazar(proyectoId, "El alcance necesita mayor precisión.", rmId);

        Proyecto proyecto = proyectoRepository.findById(proyectoId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(EstadoProyecto.RECHAZADO, proyecto.getEstado());
        org.junit.jupiter.api.Assertions.assertEquals("El alcance necesita mayor precisión.", proyecto.getMotivoRechazo());
        org.junit.jupiter.api.Assertions.assertEquals(rmId, proyecto.getRmRevisor().getId());
    }

    @Test
    void noPermiteAprobarSinPresupuesto() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> revisionService.aprobar(proyectoId, null, rmId));
    }

    @Test
    void noPermiteModificarUnProyectoYaRevisado() {
        revisionService.rechazar(proyectoId, "Rechazado en la primera decisión.", rmId);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> revisionService.asignarPresupuesto(proyectoId, new BigDecimal("1000"), rmId));
    }

    @Test
    void permiteActualizarPresupuestoDeProyectoActivo() {
        Proyecto proyecto = proyectoRepository.findById(proyectoId).orElseThrow();
        proyecto.setEstado(EstadoProyecto.ACTIVO);
        proyecto.setPresupuesto(new BigDecimal("10000.00"));
        proyectoRepository.save(proyecto);

        revisionService.asignarPresupuesto(proyectoId, new BigDecimal("12500"), rmId);

        Proyecto actualizado = proyectoRepository.findById(proyectoId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("12500.00"), actualizado.getPresupuesto());
    }

    @Test
    void projectManagerNoPuedeEditarPresupuesto() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> revisionService.asignarPresupuesto(proyectoId, new BigDecimal("10000"), pmId));
    }

    @Test
    void noPermiteEditarPresupuestoDeProyectoFinalizado() {
        Proyecto proyecto = proyectoRepository.findById(proyectoId).orElseThrow();
        proyecto.setEstado(EstadoProyecto.FINALIZADO);
        proyectoRepository.save(proyecto);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> revisionService.asignarPresupuesto(proyectoId, new BigDecimal("10000"), rmId));
    }

    @AfterEach
    void limpiarSesion() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void presupuestoVacioEnRevisionVuelveConMensaje() throws Exception {
        String destino = "/rm/proyectos/revision?id=" + proyectoId;
        verificarPresupuestoRechazado("", destino, "El presupuesto debe ser mayor que cero.");
    }

    @Test
    void presupuestoNoNumericoEnRevisionVuelveConMensaje() throws Exception {
        String destino = "/rm/proyectos/revision?id=" + proyectoId;
        verificarPresupuestoRechazado("abc", destino, "El presupuesto debe ser un monto numérico válido.");
    }

    @Test
    void presupuestoVacioEnDetalleVuelveConMensaje() throws Exception {
        activarProyecto();
        String destino = "/rm/proyectos/detalle?id=" + proyectoId;
        verificarPresupuestoRechazado("", destino, "El presupuesto debe ser mayor que cero.");
        verificarPresupuestoRechazado(null, destino, "El presupuesto debe ser mayor que cero.");
    }

    @Test
    void presupuestoNoNumericoEnDetalleVuelveConMensaje() throws Exception {
        activarProyecto();
        String destino = "/rm/proyectos/detalle?id=" + proyectoId;
        verificarPresupuestoRechazado("12,5.0", destino, "El presupuesto debe ser un monto numérico válido.");
    }

    @Test
    void presupuestoValidoSeGuardaDesdeRevisionYDetalle() throws Exception {
        autenticarRm();
        mockMvc.perform(post("/rm/proyectos/" + proyectoId + "/presupuesto").param("presupuesto", "45000"))
                .andExpect(redirectedUrl("/rm/proyectos/revision?id=" + proyectoId))
                .andExpect(flash().attribute("mensajeExito", "Presupuesto guardado correctamente."));
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("45000.00"),
                proyectoRepository.findById(proyectoId).orElseThrow().getPresupuesto());

        activarProyecto();
        mockMvc.perform(post("/rm/proyectos/" + proyectoId + "/presupuesto").param("presupuesto", "52000.50"))
                .andExpect(redirectedUrl("/rm/proyectos/detalle?id=" + proyectoId))
                .andExpect(flash().attribute("mensajeExito", "Presupuesto guardado correctamente."));
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("52000.50"),
                proyectoRepository.findById(proyectoId).orElseThrow().getPresupuesto());
    }

    private void verificarPresupuestoRechazado(String valor, String destino, String mensaje) throws Exception {
        autenticarRm();
        var peticion = post("/rm/proyectos/" + proyectoId + "/presupuesto");
        if (valor != null) peticion.param("presupuesto", valor);
        MvcResult resultado = mockMvc.perform(peticion)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(destino))
                .andExpect(flash().attribute("mensajeError", mensaje))
                .andReturn();
        String html = mockMvc.perform(get(destino).flashAttrs(resultado.getFlashMap()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertTrue(
                html.contains("<div class=\"alert alert-danger\">" + mensaje + "</div>"));
        org.junit.jupiter.api.Assertions.assertNull(
                proyectoRepository.findById(proyectoId).orElseThrow().getPresupuesto());
    }

    private void activarProyecto() {
        Proyecto proyecto = proyectoRepository.findById(proyectoId).orElseThrow();
        proyecto.setEstado(EstadoProyecto.ACTIVO);
        proyectoRepository.save(proyecto);
    }

    private void autenticarRm() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(rmDetails, null, rmDetails.getAuthorities()));
    }
}
