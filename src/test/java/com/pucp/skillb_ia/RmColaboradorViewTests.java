package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.Cargo;
import com.pucp.skillb_ia.repository.CargoRepository;
import com.pucp.skillb_ia.model.enums.NivelExperiencia;
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

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@ActiveProfiles("test")
class RmColaboradorViewTests {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired private CargoRepository cargoRepository;

    private MockMvc mockMvc;
    private Long colaboradorId;

    @BeforeEach
    void prepararDatos() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        Usuario colaborador = usuarioRepository.findByCorreo("colaborador.vista@skillbridge.test")
                .orElseGet(() -> {
                    Rol rol = rolRepository.findByNombre("COLABORADOR")
                            .orElseGet(() -> {
                                Rol nuevoRol = new Rol();
                                nuevoRol.setNombre("COLABORADOR");
                                return rolRepository.save(nuevoRol);
                            });

                    Usuario nuevoColaborador = new Usuario();
                    nuevoColaborador.setCorreo("colaborador.vista@skillbridge.test");
                    nuevoColaborador.setNombre("María");
                    nuevoColaborador.setApellido("Prueba");
                    nuevoColaborador.setCargo(cargoDePrueba("Backend Developer"));
                    nuevoColaborador.setRol(rol);
                    nuevoColaborador.setActivo(true);
                    nuevoColaborador.setNivelExperiencia(NivelExperiencia.SEMI_SENIOR);
                    nuevoColaborador.setHorasDisponibles(BigDecimal.valueOf(16));
                    nuevoColaborador.setAniosExperiencia(BigDecimal.valueOf(3));
                    return usuarioRepository.save(nuevoColaborador);
                });

        colaboradorId = colaborador.getId();
    }

    @Test
    void renderizaDirectorioConDatosReales() throws Exception {
        mockMvc.perform(get("/rm/colaboradores"))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-colaboradores"))
                .andExpect(model().attributeExists("colaboradores", "totalColaboradores"));
    }

    @Test
    void renderizaPerfilDelColaboradorSeleccionado() throws Exception {
        mockMvc.perform(get("/rm/colaboradores/perfil").param("id", colaboradorId.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-perfil-colaborador"))
                .andExpect(model().attributeExists("colaborador"));
    }

    private Cargo cargoDePrueba(String nombre) {
        return cargoRepository.findByNombre(nombre).orElseGet(() -> cargoRepository.save(
                new Cargo(nombre, new BigDecimal("2000"), new BigDecimal("3000"), new BigDecimal("4000"))));
    }
}
