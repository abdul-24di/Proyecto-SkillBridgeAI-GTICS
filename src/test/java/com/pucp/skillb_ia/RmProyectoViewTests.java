package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@ActiveProfiles("test")
class RmProyectoViewTests {
    @Autowired private WebApplicationContext context;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProyectoRepository proyectoRepository;

    private MockMvc mockMvc;
    private Long proyectoId;

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
}
