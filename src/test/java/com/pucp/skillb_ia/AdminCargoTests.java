package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.Cargo;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.NivelExperiencia;
import com.pucp.skillb_ia.repository.CargoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.AdminUsuarioService;
import com.pucp.skillb_ia.service.admin.AdminCargoService;
import com.pucp.skillb_ia.service.rm.RmCertificadoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AdminCargoTests {

    @Autowired private WebApplicationContext context;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CargoRepository cargoRepository;
    @Autowired private AdminCargoService cargoService;
    @Autowired private AdminUsuarioService usuarioService;
    @Autowired private RmCertificadoService certificadoService;

    private MockMvc mockMvc;
    private Usuario admin;
    private Usuario rm;

    @BeforeEach
    void preparar() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        admin = usuario("admin.cargos@skillbridge.test", "ADMINISTRADOR", null);
        rm = usuario("rm.cargos@skillbridge.test", "RESOURCE_MANAGER", null);
    }

    @Test
    void creaCargoYValidaTarifas() {
        cargoService.crear("  QA Automation  ", new BigDecimal("2100"), new BigDecimal("3100"),
                new BigDecimal("4100"), admin);

        Cargo cargo = cargoRepository.findByNombre("QA Automation").orElseThrow();
        assertThat(cargo.isActivo()).isTrue();

        assertThatThrownBy(() -> cargoService.crear("qa automation", new BigDecimal("1"),
                new BigDecimal("2"), new BigDecimal("3"), admin))
                .hasMessageContaining("Ya existe");
        assertThatThrownBy(() -> cargoService.crear("Arquitecto", new BigDecimal("5000"),
                new BigDecimal("3000"), new BigDecimal("6000"), admin))
                .hasMessageContaining("orden");
        assertThatThrownBy(() -> cargoService.crear("Sin tarifas", null, null, null, admin))
                .hasMessageContaining("tres tarifas");
    }

    @Test
    void editarTarifasRecalculaSueldoDeColaboradoresConEseCargo() {
        Cargo cargo = cargo("Data Engineer Test");
        Usuario colaborador = usuario("col.cargos1@skillbridge.test", "COLABORADOR", NivelExperiencia.SEMI_SENIOR);
        usuarioService.asignarCargo(colaborador.getId(), cargo.getId(), admin);
        assertThat(sueldo(colaborador)).isEqualByComparingTo("3000");

        int recalculados = cargoService.editarTarifas(cargo.getId(), new BigDecimal("2500"),
                new BigDecimal("3600"), new BigDecimal("5000"), admin);

        assertThat(recalculados).isEqualTo(1);
        assertThat(sueldo(colaborador)).isEqualByComparingTo("3600");
    }

    @Test
    void cambioDeNivelPorRmActualizaSueldoSegunCargo() {
        Cargo cargo = cargo("Mobile Developer Test");
        Usuario colaborador = usuario("col.cargos2@skillbridge.test", "COLABORADOR", NivelExperiencia.JUNIOR);
        usuarioService.asignarCargo(colaborador.getId(), cargo.getId(), admin);
        assertThat(sueldo(colaborador)).isEqualByComparingTo("2000");

        certificadoService.actualizarNivelExperiencia(colaborador.getId(), NivelExperiencia.SENIOR, rm.getId());

        assertThat(sueldo(colaborador)).isEqualByComparingTo("4000");
    }

    @Test
    void soloSeAsignanCargosActivosAColaboradores() {
        Cargo cargo = cargo("Soporte Test");
        Usuario pm = usuario("pm.cargos@skillbridge.test", "PROJECT_MANAGER", null);
        assertThatThrownBy(() -> usuarioService.asignarCargo(pm.getId(), cargo.getId(), admin))
                .hasMessageContaining("colaboradores");

        cargoService.alternarEstado(cargo.getId(), admin);
        Usuario colaborador = usuario("col.cargos3@skillbridge.test", "COLABORADOR", null);
        assertThatThrownBy(() -> usuarioService.asignarCargo(colaborador.getId(), cargo.getId(), admin))
                .hasMessageContaining("desactivado");
    }

    @Test
    void pantallasDelAdminMuestranCargos() throws Exception {
        cargo("Cargo Visible Test");

        mockMvc.perform(get("/admin/habilidades").param("tab", "cargos"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cargo Visible Test")));
        mockMvc.perform(get("/admin/usuarios"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("editCargoModal")));
    }

    private BigDecimal sueldo(Usuario usuario) {
        return usuarioRepository.findById(usuario.getId()).orElseThrow().getSueldoBase();
    }

    private Cargo cargo(String nombre) {
        return cargoRepository.findByNombre(nombre).orElseGet(() -> cargoRepository.save(
                new Cargo(nombre, new BigDecimal("2000"), new BigDecimal("3000"), new BigDecimal("4000"))));
    }

    private Usuario usuario(String correo, String rolNombre, NivelExperiencia nivel) {
        Rol rol = rolRepository.findByNombre(rolNombre).orElseGet(() -> {
            Rol nuevo = new Rol();
            nuevo.setNombre(rolNombre);
            return rolRepository.save(nuevo);
        });
        return usuarioRepository.findByCorreo(correo).orElseGet(() -> {
            Usuario usuario = new Usuario();
            usuario.setCorreo(correo);
            usuario.setNombre("Prueba");
            usuario.setApellido("Cargos");
            usuario.setRol(rol);
            usuario.setActivo(true);
            usuario.setNivelExperiencia(nivel);
            return usuarioRepository.save(usuario);
        });
    }
}
