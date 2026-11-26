package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.rm.RmPerfilService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Teléfono del perfil del RM (TASK-012): obligatorio, dígitos con '+' inicial opcional, 7-20 caracteres.
@SpringBootTest
@ActiveProfiles("test")
class RmPerfilTelefonoTests {
    private static final String TELEFONO_INICIAL = "900000000";

    @Autowired private WebApplicationContext context;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private RmPerfilService rmPerfilService;
    private MockMvc mockMvc;
    private Usuario rm;

    @BeforeEach
    void preparar() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        rm = usuarioRepository.findByCorreo("rm.telefono@test.local").orElseGet(() -> {
            Usuario usuario = new Usuario();
            usuario.setCorreo("rm.telefono@test.local");
            usuario.setNombre("Tomas");
            usuario.setApellido("Telefono");
            usuario.setRol(obtenerRol("RESOURCE_MANAGER"));
            usuario.setActivo(true);
            return usuarioRepository.save(usuario);
        });
        rm.setTelefono(TELEFONO_INICIAL);
        rm = usuarioRepository.save(rm);
        rm.setRol(obtenerRol("RESOURCE_MANAGER")); // el rol cargado es LAZY
        UsuarioDetails details = new UsuarioDetails(rm);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aceptaTelefonosValidos() {
        String[] validos = {
                "987654321",
                "+51987654321",
                "9876543",                    // 7 dígitos
                "12345678901234567890",       // 20 dígitos
                "+1234567890123456789",       // + y 19 dígitos = 20 caracteres
                "+1234567",                   // + y 7 dígitos
                "  987654321  "               // se recortan los espacios externos
        };
        for (String telefono : validos) {
            rmPerfilService.actualizarTelefono(telefono, rm);
            assertThat(telefonoGuardado()).isEqualTo(telefono.strip());
        }
    }

    @Test
    void rechazaTelefonoNuloVacioOEnBlancoSinGuardar() {
        for (String telefono : new String[]{null, "", "   "}) {
            assertThatThrownBy(() -> rmPerfilService.actualizarTelefono(telefono, rm))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(RmPerfilService.MENSAJE_TELEFONO_OBLIGATORIO);
            assertThat(telefonoGuardado()).isEqualTo(TELEFONO_INICIAL);
        }
    }

    @Test
    void rechazaTelefonosMalFormadosSinGuardar() {
        String[] invalidos = {
                "123456",                     // 6 dígitos
                "123456789012345678901",      // 21 dígitos
                "+12345678901234567890",      // 21 caracteres con +
                "+123456",                    // + sin suficientes dígitos
                "+",
                "98765432a",
                "987 654 321",
                "987-654-321",
                "(01) 1234567",
                "(01)1234567",
                "51+987654",
                "987654321+",
                "++51987654321",
                "+51+987654321"
        };
        for (String telefono : invalidos) {
            assertThatThrownBy(() -> rmPerfilService.actualizarTelefono(telefono, rm))
                    .as(telefono)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(RmPerfilService.MENSAJE_TELEFONO_FORMATO);
            assertThat(telefonoGuardado()).isEqualTo(TELEFONO_INICIAL);
        }
    }

    @Test
    void postValidoGuardaYMuestraExito() throws Exception {
        mockMvc.perform(post("/rm/perfil/telefono").param("telefono", "+51987654321"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rm/perfil"))
                .andExpect(flash().attribute("mensajeExito", "Teléfono actualizado correctamente."));
        assertThat(telefonoGuardado()).isEqualTo("+51987654321");
    }

    @Test
    void postInvalidoMuestraMensajeYConservaElValorIngresado() throws Exception {
        String largo = "1234567890123456789012345"; // excede la columna VARCHAR(20)
        mockMvc.perform(post("/rm/perfil/telefono").param("telefono", largo))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rm/perfil"))
                .andExpect(flash().attribute("mensajeError", RmPerfilService.MENSAJE_TELEFONO_FORMATO))
                .andExpect(flash().attribute("telefonoIngresado", largo));
        assertThat(telefonoGuardado()).isEqualTo(TELEFONO_INICIAL);

        mockMvc.perform(post("/rm/perfil/telefono").param("telefono", "  "))
                .andExpect(flash().attribute("mensajeError", RmPerfilService.MENSAJE_TELEFONO_OBLIGATORIO));
        assertThat(telefonoGuardado()).isEqualTo(TELEFONO_INICIAL);

        // Tras el redirect, el formulario muestra el mensaje y el valor ingresado en lugar del guardado.
        mockMvc.perform(get("/rm/perfil")
                        .flashAttr("mensajeError", RmPerfilService.MENSAJE_TELEFONO_FORMATO)
                        .flashAttr("telefonoIngresado", "987 654 321"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("entre 7 y 20 caracteres, solo d")))
                .andExpect(content().string(containsString("value=\"987 654 321\"")));
    }

    private String telefonoGuardado() {
        return usuarioRepository.findById(rm.getId()).orElseThrow().getTelefono();
    }

    private Rol obtenerRol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol rol = new Rol();
            rol.setNombre(nombre);
            return rolRepository.save(rol);
        });
    }
}
