package com.pucp.skillb_ia;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// Validación con Bean Validation (@Valid + BindingResult) de los formularios de
// activación de cuenta y nueva contraseña: si hay errores se vuelve a mostrar el
// formulario con los mensajes por campo, sin tocar el servicio.
@SpringBootTest
@ActiveProfiles("test")
class FormulariosAuthValidacionTests {

    @Autowired private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void preparar() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void activarCuentaConPasswordDebilYSinPoliticaMuestraErroresPorCampo() throws Exception {
        mockMvc.perform(post("/activar-cuenta")
                        .param("token", "token-que-no-existe")
                        .param("nombreCompleto", "Ab")
                        .param("password", "abcdefgh")
                        .param("confirmarPassword", "abcdefgh"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/activar-cuenta"))
                .andExpect(model().attributeHasFieldErrors("activarCuentaForm",
                        "nombreCompleto", "password", "aceptaPolitica"));
    }

    @Test
    void activarCuentaConPasswordsDistintasMarcaElError() throws Exception {
        mockMvc.perform(post("/activar-cuenta")
                        .param("token", "token-que-no-existe")
                        .param("nombreCompleto", "Ana Perez")
                        .param("password", "Segura#123")
                        .param("confirmarPassword", "Segura#124")
                        .param("aceptaPolitica", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/activar-cuenta"))
                .andExpect(model().attributeHasFieldErrors("activarCuentaForm", "passwordsCoinciden"));
    }

    @Test
    void nuevaContrasenaDebilOQueNoCoincideVuelveAlFormulario() throws Exception {
        mockMvc.perform(post("/nueva-contrasena")
                        .param("token", "token-que-no-existe")
                        .param("password", "corta")
                        .param("confirmarPassword", "otra"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/nueva-contrasena"))
                .andExpect(model().attributeHasFieldErrors("nuevaContrasenaForm",
                        "password", "passwordsCoinciden"));
    }

    @Test
    void nuevaContrasenaValidaPeroConTokenInexistenteRedirigeConError() throws Exception {
        mockMvc.perform(post("/nueva-contrasena")
                        .param("token", "token-que-no-existe")
                        .param("password", "Segura#123")
                        .param("confirmarPassword", "Segura#123"))
                .andExpect(status().is3xxRedirection());
    }
}
