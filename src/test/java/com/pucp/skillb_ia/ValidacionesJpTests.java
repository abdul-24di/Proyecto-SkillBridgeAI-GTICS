package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.CategoriaHabilidad;
import com.pucp.skillb_ia.model.Foro;
import com.pucp.skillb_ia.model.PublicacionForo;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.TipoForo;
import com.pucp.skillb_ia.repository.CategoriaHabilidadRepository;
import com.pucp.skillb_ia.repository.ForoRepository;
import com.pucp.skillb_ia.repository.PublicacionForoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;

// Validaciones que faltaban (revisión del 16/11/2026 con datos inválidos en todos los formularios de texto):
// anuncio vacío del Admin, respuestas sin límite de largo en el foro del colaborador y textos largos que la base
// de datos rechazaba con un error 500 (formación académica y habilidad nueva).
@SpringBootTest
@ActiveProfiles("test")
class ValidacionesJpTests {

    private static final String LARGO = "x".repeat(6000);

    @Autowired private WebApplicationContext context;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private ForoRepository foroRepository;
    @Autowired private PublicacionForoRepository publicacionRepository;
    @Autowired private CategoriaHabilidadRepository categoriaRepository;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void elAdminNoPuedePublicarUnAnuncioVacio() throws Exception {
        autenticar(usuario("admin", "ADMINISTRADOR"));
        Foro foro = foro();
        long antes = publicacionRepository.count();

        mvc.perform(post("/admin/foros/" + foro.getId() + "/publicar").param("titulo", "  ").param("contenido", "Hola"))
                .andExpect(flash().attribute("error", "El título del anuncio es obligatorio."));
        mvc.perform(post("/admin/foros/" + foro.getId() + "/publicar").param("titulo", "Aviso").param("contenido", "<p><br></p>"))
                .andExpect(flash().attribute("error", "El contenido del anuncio es obligatorio."));
        mvc.perform(post("/admin/foros/" + foro.getId() + "/publicar").param("titulo", "x".repeat(201)).param("contenido", "Hola"))
                .andExpect(flash().attribute("error", "El título no puede superar los 200 caracteres."));

        assertThat(publicacionRepository.count()).isEqualTo(antes);
    }

    @Test
    void laRespuestaDelColaboradorEnElForoTieneLimiteDeLargo() throws Exception {
        Usuario colaborador = usuario("col", "COLABORADOR");
        autenticar(colaborador);
        Foro foro = foro();
        PublicacionForo pub = new PublicacionForo();
        pub.setForo(foro);
        pub.setAutor(colaborador);
        pub.setTitulo("Consulta");
        pub.setContenido("Contenido");
        pub = publicacionRepository.save(pub);

        mvc.perform(post("/colaborador/foro/responder").param("publicacionId", pub.getId().toString())
                        .param("foroId", foro.getId().toString()).param("contenido", LARGO))
                .andExpect(flash().attribute("mensajeError", "La respuesta no puede superar los 5000 caracteres."));
    }

    @Test
    void textosLargosEnElPerfilDelColaboradorDanUnMensajeYNoUnError500() throws Exception {
        autenticar(usuario("col", "COLABORADOR"));
        MockMultipartFile pdf = new MockMultipartFile("certificado", "titulo.pdf", "application/pdf", "%PDF-1.4 x".getBytes());

        mvc.perform(multipart("/colaborador/perfil/educacion").file(pdf).param("institucion", "x".repeat(151))
                        .param("titulo", "Ingeniería").param("fechaInicio", "2020-01-01").param("fechaFin", "2021-01-01"))
                .andExpect(flash().attribute("mensajeError", "La institución no puede superar los 150 caracteres."));
        mvc.perform(multipart("/colaborador/perfil/educacion").file(pdf).param("institucion", "PUCP")
                        .param("titulo", "x".repeat(151)).param("fechaInicio", "2020-01-01").param("fechaFin", "2021-01-01"))
                .andExpect(flash().attribute("mensajeError", "El título o carrera no puede superar los 150 caracteres."));

        CategoriaHabilidad categoria = new CategoriaHabilidad();
        categoria.setNombre("Categoría validación " + System.nanoTime());
        categoria = categoriaRepository.save(categoria);
        mvc.perform(post("/colaborador/perfil/habilidades").param("nuevaHabilidadNombre", "x".repeat(101))
                        .param("categoriaId", categoria.getId().toString()).param("nivel", "BASICO"))
                .andExpect(flash().attribute("mensajeError", "El nombre de la habilidad no puede superar los 100 caracteres."));
    }

    // ---------- Utilidades ----------

    private Foro foro() {
        Foro foro = new Foro();
        foro.setNombre("Foro validación " + System.nanoTime());
        foro.setTipo(TipoForo.GENERAL);
        return foroRepository.save(foro);
    }

    private Usuario usuario(String prefijo, String nombreRol) {
        Usuario u = new Usuario();
        u.setCorreo(prefijo + "." + UUID.randomUUID() + "@test.local");
        u.setNombre(prefijo);
        u.setApellido("Prueba");
        u.setRol(rolRepository.findByNombre(nombreRol).orElseGet(() -> {
            Rol r = new Rol();
            r.setNombre(nombreRol);
            return rolRepository.save(r);
        }));
        u.setActivo(true);
        u.setHorasContratadasSemana(new BigDecimal("40.00"));
        u.setHorasDisponibles(new BigDecimal("40.00"));
        return usuarioRepository.save(u);
    }

    private void autenticar(Usuario usuario) {
        UsuarioDetails details = new UsuarioDetails(usuario);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }
}
