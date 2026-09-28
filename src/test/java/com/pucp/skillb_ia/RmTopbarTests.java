package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Barra superior compartida del RM (fragments/rm-topbar.html): foto o iniciales.
@SpringBootTest
@ActiveProfiles("test")
class RmTopbarTests {
    private static final String AVATAR_INICIALES = "class=\"avatar avatar-sm user-avatar\">RT</span>";

    @Autowired private WebApplicationContext context;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    private MockMvc mockMvc;
    private Usuario rm;

    @BeforeEach
    void preparar() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        rm = usuarioRepository.findByCorreo("rm.topbar@test.local").orElseGet(() -> {
            Usuario usuario = new Usuario();
            usuario.setCorreo("rm.topbar@test.local");
            usuario.setNombre("Rita");
            usuario.setApellido("Topbar");
            usuario.setRol(obtenerRol("RESOURCE_MANAGER"));
            usuario.setActivo(true);
            return usuarioRepository.save(usuario);
        });
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
    void conFotoLocalMuestraLaImagenEnDashboardYPerfil() throws Exception {
        guardarFoto("/uploads/perfil/usuario-topbar.png");

        for (String ruta : new String[]{"/rm/dashboard", "/rm/perfil"}) {
            mockMvc.perform(get(ruta))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("src=\"/uploads/perfil/usuario-topbar.png\"")))
                    .andExpect(content().string(containsString("alt=\"Foto de perfil de Rita Topbar\"")))
                    .andExpect(content().string(not(containsString(AVATAR_INICIALES))))
                    .andExpect(content().string(containsString("Rita Topbar")));
        }
    }

    @Test
    void conFotoEnS3UsaLaUrlAbsolutaSinCambios() throws Exception {
        String urlS3 = "https://bucket-prueba.s3.amazonaws.com/perfil/usuario-topbar.jpg";
        guardarFoto(urlS3);

        mockMvc.perform(get("/rm/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("src=\"" + urlS3 + "\"")))
                .andExpect(content().string(not(containsString(AVATAR_INICIALES))));
    }

    @Test
    void sinFotoOEnBlancoMuestraIniciales() throws Exception {
        for (String foto : new String[]{null, "", "   "}) {
            guardarFoto(foto);
            mockMvc.perform(get("/rm/dashboard"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(AVATAR_INICIALES)))
                    .andExpect(content().string(not(containsString("user-avatar-foto"))))
                    .andExpect(content().string(containsString("Rita Topbar")));
        }
    }

    @Test
    void laSiguienteSolicitudUsaLaFotoActualizadaSinRenovarLaSesion() throws Exception {
        guardarFoto(null);
        mockMvc.perform(get("/rm/dashboard"))
                .andExpect(content().string(containsString(AVATAR_INICIALES)));

        // La copia del usuario en la autenticación no cambia; solo la BD.
        guardarFoto("/uploads/perfil/usuario-topbar-nueva.png");

        mockMvc.perform(get("/rm/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("src=\"/uploads/perfil/usuario-topbar-nueva.png\"")))
                .andExpect(content().string(not(containsString(AVATAR_INICIALES))));
    }

    private void guardarFoto(String fotoUrl) {
        Usuario usuario = usuarioRepository.findById(rm.getId()).orElseThrow();
        usuario.setFotoUrl(fotoUrl);
        usuarioRepository.save(usuario);
    }

    private Rol obtenerRol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol rol = new Rol();
            rol.setNombre(nombre);
            return rolRepository.save(rol);
        });
    }
}
