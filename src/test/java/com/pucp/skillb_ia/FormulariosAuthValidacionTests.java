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
    @Autowired private com.pucp.skillb_ia.service.AuthService authService;
    @Autowired private com.pucp.skillb_ia.repository.RolRepository rolRepository;
    @Autowired private com.pucp.skillb_ia.repository.TokenUsuarioRepository tokenUsuarioRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void preparar() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // Un enlace de activación vigente: con uno inválido el formulario ni siquiera se procesa.
    private String tokenVigente() {
        rolRepository.findByNombre("PROJECT_MANAGER").orElseGet(() -> {
            com.pucp.skillb_ia.model.Rol rol = new com.pucp.skillb_ia.model.Rol();
            rol.setNombre("PROJECT_MANAGER");
            return rolRepository.save(rol);
        });
        var usuario = authService.invitarUsuario("form." + java.util.UUID.randomUUID() + "@test.local", "PROJECT_MANAGER", null);
        return tokenUsuarioRepository.findAll().stream()
                .filter(t -> t.getUsuario().getId().equals(usuario.getId()))
                .findFirst().orElseThrow().getToken();
    }

    @Test
    void activarCuentaConPasswordDebilYSinPoliticaMuestraErroresPorCampo() throws Exception {
        mockMvc.perform(post("/activar-cuenta")
                        .param("token", tokenVigente())
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
                        .param("token", tokenVigente())
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
