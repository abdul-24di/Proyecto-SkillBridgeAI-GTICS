package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.CategoriaHabilidad;
import com.pucp.skillb_ia.model.Foro;
import com.pucp.skillb_ia.model.Habilidad;
import com.pucp.skillb_ia.model.PublicacionForo;
import com.pucp.skillb_ia.model.RespuestaForo;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.CategoriaHabilidadRepository;
import com.pucp.skillb_ia.repository.ForoRepository;
import com.pucp.skillb_ia.repository.HabilidadRepository;
import com.pucp.skillb_ia.repository.PublicacionForoRepository;
import com.pucp.skillb_ia.repository.RespuestaForoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.AdminHabilidadService;
import com.pucp.skillb_ia.service.EmailService;
import com.pucp.skillb_ia.service.admin.AdminForoService;
import com.pucp.skillb_ia.service.pm.PmForoService;
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
import org.thymeleaf.context.Context;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Observaciones del grupo (nov 2026): habilidades que no se podían editar, foro de comunidad del Admin,
// correos con plantilla HTML y la activación de una cuenta con otra sesión abierta en el navegador.
@SpringBootTest
@ActiveProfiles("test")
class ObservacionesGrupoTests {

    @Autowired private WebApplicationContext context;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private CategoriaHabilidadRepository categoriaHabilidadRepository;
    @Autowired private HabilidadRepository habilidadRepository;
    @Autowired private AdminHabilidadService adminHabilidadService;
    @Autowired private AdminForoService adminForoService;
    @Autowired private PmForoService pmForoService;
    @Autowired private ForoRepository foroRepository;
    @Autowired private PublicacionForoRepository publicacionForoRepository;
    @Autowired private RespuestaForoRepository respuestaForoRepository;
    @Autowired private EmailService emailService;

    private Usuario admin;

    @BeforeEach
    void preparar() {
        admin = usuario("admin.obs@test.local", "ADMINISTRADOR");
        UsuarioDetails details = new UsuarioDetails(admin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    // ---------- habilidades ----------

    @Test
    void seEditaUnaHabilidadAunqueLaBaseTengaSuNombreRepetidoEnOtraCategoria() {
        // Datos como los que dejaba el script demo antiguo: "Java" en dos categorías. Antes, editar daba
        // "No se pudo completar la operación" sin decir por qué.
        String nombre = "Repetida" + UUID.randomUUID().toString().substring(0, 8);
        CategoriaHabilidad tecnica = categoria();
        CategoriaHabilidad demo = categoria();
        Habilidad original = habilidad(nombre, tecnica);
        habilidad(nombre, demo);

        CategoriaHabilidad otra = categoria();
        adminHabilidadService.editar(original.getId(), nombre, otra.getId(), admin);

        assertThat(habilidadRepository.findById(original.getId()).orElseThrow().getCategoria().getId())
                .isEqualTo(otra.getId());
    }

    @Test
    void renombrarAUnNombreQueYaExisteDaUnMensajeClaro() {
        CategoriaHabilidad cat = categoria();
        Habilidad a = habilidad("Uno" + UUID.randomUUID().toString().substring(0, 8), cat);
        Habilidad b = habilidad("Dos" + UUID.randomUUID().toString().substring(0, 8), cat);

        assertThatThrownBy(() -> adminHabilidadService.editar(a.getId(), b.getNombre().toUpperCase(), cat.getId(), admin))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Ya existe");
        assertThatThrownBy(() -> adminHabilidadService.editar(a.getId(), "x".repeat(101), cat.getId(), admin))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("100 caracteres");
    }

    // ---------- foro de comunidad ----------

    @Test
    void seEliminaUnForoQueYaTienePublicacionesRespuestasYMeGusta() {
        Foro foro = adminForoService.crearForo("Foro con contenido " + UUID.randomUUID(), true);
        adminForoService.publicar(foro.getId(), "Anuncio", "Contenido del anuncio", admin);
        PublicacionForo pub = publicacionForoRepository.findByForoAndActivoTrueOrderByFechaCreacionDesc(foro).get(0);
        Usuario colaborador = usuario("colab.foro.obs@test.local", "COLABORADOR");
        pmForoService.responder(pub.getId(), "Una respuesta", colaborador);
        pmForoService.alternarLikePublicacion(colaborador, pub.getId());
        RespuestaForo respuesta = respuestaForoRepository.findByPublicacionConAutor(pub).get(0);
        pmForoService.alternarLikeRespuesta(admin, respuesta.getId());

        adminForoService.eliminarForo(foro.getId());

        assertThat(foroRepository.findById(foro.getId())).isEmpty();
        assertThat(publicacionForoRepository.findById(pub.getId())).isEmpty();
        assertThat(respuestaForoRepository.findById(respuesta.getId())).isEmpty();
    }

    @Test
    void elAdminRetiraLaRespuestaDeOtroUsuarioEnElForoDeComunidad() {
        Foro foro = adminForoService.crearForo("Foro moderado " + UUID.randomUUID(), true);
        adminForoService.publicar(foro.getId(), "Anuncio", "Contenido", admin);
        PublicacionForo pub = publicacionForoRepository.findByForoAndActivoTrueOrderByFechaCreacionDesc(foro).get(0);
        pmForoService.responder(pub.getId(), "Respuesta de otra persona", usuario("colab.mod.obs@test.local", "COLABORADOR"));
        RespuestaForo respuesta = respuestaForoRepository.findByPublicacionConAutor(pub).get(0);

        adminForoService.eliminarRespuesta(respuesta.getId());

        assertThat(respuestaForoRepository.findById(respuesta.getId()).orElseThrow().isActivo()).isFalse();
    }

    // ---------- correos ----------

    @Test
    void elCorreoDeInvitacionMuestraElRolElBotonYElEnlace() {
        Context datos = new Context();
        datos.setVariable("enlace", "http://localhost:8080/activar-cuenta?token=abc");
        datos.setVariable("rol", "Resource Manager");
        datos.setVariable("esColaborador", false);
        datos.setVariable("horasVigencia", 48);

        String html = emailService.armarHtml("email/activacion", datos);

        assertThat(html).contains("SkillBridge AI", "Resource Manager", "Activar mi cuenta",
                "href=\"http://localhost:8080/activar-cuenta?token=abc\"", "48 horas");
        assertThat(html).doesNotContain("CV en PDF");
    }

    @Test
    void elCorreoDeRecuperacionMuestraElCodigoYSuVigencia() {
        Context datos = new Context();
        datos.setVariable("codigo", "482913");
        datos.setVariable("minutosVigencia", 15);

        String html = emailService.armarHtml("email/recuperacion", datos);

        assertThat(html).contains("Recupera tu contraseña", "482913", "15 minutos");
    }

    // ---------- activación con otra sesión abierta ----------

    @Test
    void abrirElEnlaceDeActivacionConOtraSesionAbiertaLaCierraPrimero() throws Exception {
        // Antes, si el Admin (u otro usuario) seguía conectado en el navegador, al activar la cuenta nueva
        // el login llevaba al panel de esa sesión y parecía que la cuenta tenía otro rol.
        // El Admin del @BeforeEach sigue con su sesión iniciada.
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();

        mvc.perform(get("/activar-cuenta").param("token", "cualquiera"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/activar-cuenta?token=cualquiera"));
    }

    @Test
    void elLoginCargaConYSinElAvisoDeCuentaActivada() throws Exception {
        SecurityContextHolder.clearContext();
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/login").param("activado", ""))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Cuenta activada")));
        mvc.perform(get("/login").param("activado", "")
                        .flashAttr("avisoPreRegistro", "Tu cuenta se activó, pero no se pudo guardar: CV."))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("no se pudo guardar: CV")));
    }

    @Test
    void unaInvitacionSinActivarNoCuentaComoUsuarioActivoDeSuRol() {
        // Antes, un RM invitado que aún no activaba su cuenta podía quedar como responsable de un proyecto
        // (la auditoría mostraba "asignado a null null").
        Usuario invitado = new Usuario();
        invitado.setCorreo("rm.invitado." + UUID.randomUUID() + "@test.local");
        invitado.setRol(obtenerRol("RESOURCE_MANAGER"));
        invitado.setActivo(true);
        invitado = usuarioRepository.save(invitado);
        Usuario activado = usuario("rm.activado.obs@test.local", "RESOURCE_MANAGER");

        var ids = usuarioRepository.findActivosByRolNombre("RESOURCE_MANAGER").stream().map(Usuario::getId).toList();

        assertThat(ids).contains(activado.getId()).doesNotContain(invitado.getId());
    }

    // ---------- datos personales del pre-registro en el perfil ----------

    @Autowired private com.pucp.skillb_ia.service.AuthService authService;
    @Autowired private com.pucp.skillb_ia.repository.TokenUsuarioRepository tokenUsuarioRepository;

    @Test
    void elSegundoNombreNoQuedaComoApellidoAlActivarLaCuenta() {
        Usuario invitado = new Usuario();
        invitado.setCorreo("nombres." + UUID.randomUUID() + "@test.local");
        invitado.setRol(obtenerRol("PROJECT_MANAGER"));
        invitado.setActivo(true);
        invitado = usuarioRepository.save(invitado);
        var token = new com.pucp.skillb_ia.model.TokenUsuario();
        token.setUsuario(invitado);
        token.setTipo(com.pucp.skillb_ia.model.enums.TipoToken.ACTIVACION);
        token.setToken(UUID.randomUUID().toString());
        token.setFechaExpiracion(java.time.LocalDateTime.now().plusHours(1));
        tokenUsuarioRepository.save(token);

        authService.activarCuenta(token.getToken(), " Camila Rosa ", " Vargas Luna ", "987654321", null, "Segura#123");

        Usuario activado = usuarioRepository.findById(invitado.getId()).orElseThrow();
        assertThat(activado.getNombre()).isEqualTo("Camila Rosa");
        assertThat(activado.getApellido()).isEqualTo("Vargas Luna");
    }

    @Test
    void sobreMiSeVeYSeEditaEnElPerfilDelAdminElPmYElRm() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        for (String[] caso : new String[][]{{"admin", "ADMINISTRADOR"}, {"pm", "PROJECT_MANAGER"}, {"rm", "RESOURCE_MANAGER"}}) {
            Usuario u = usuario("sobremi." + caso[0] + "@test.local", caso[1]);
            u.setDescripcion("Texto del registro " + caso[0]);
            u = usuarioRepository.save(u);
            u.setRol(obtenerRol(caso[1]));
            UsuarioDetails details = new UsuarioDetails(u);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
            String perfil = "/" + caso[0] + "/perfil";

            mvc.perform(get(perfil)).andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                            .string(org.hamcrest.Matchers.containsString("Texto del registro " + caso[0])));
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(perfil + "/sobre-mi")
                            .param("descripcion", "Nuevo <b>texto</b>"))
                    .andExpect(redirectedUrl(perfil + "#sobre-mi"));
            mvc.perform(get(perfil)).andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                            .string(org.hamcrest.Matchers.containsString("Nuevo &lt;b&gt;texto&lt;/b&gt;")));

            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(perfil + "/sobre-mi")
                            .param("descripcion", "x".repeat(501)))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash()
                            .attributeExists("sobreMiError"));
            assertThat(usuarioRepository.findById(u.getId()).orElseThrow().getDescripcion())
                    .isEqualTo("Nuevo <b>texto</b>");
        }
    }

    // ---------- datos ----------

    private CategoriaHabilidad categoria() {
        CategoriaHabilidad cat = new CategoriaHabilidad();
        cat.setNombre("Cat obs " + UUID.randomUUID());
        cat.setActiva(true);
        return categoriaHabilidadRepository.save(cat);
    }

    private Habilidad habilidad(String nombre, CategoriaHabilidad categoria) {
        Habilidad h = new Habilidad();
        h.setNombre(nombre);
        h.setCategoria(categoria);
        h.setActiva(true);
        return habilidadRepository.save(h);
    }

    private Usuario usuario(String correo, String rol) {
        Usuario u = usuarioRepository.findByCorreo(correo).orElseGet(() -> {
            Usuario nuevo = new Usuario();
            nuevo.setCorreo(correo);
            nuevo.setNombre("Prueba");
            nuevo.setApellido("Observaciones");
            nuevo.setRol(obtenerRol(rol));
            nuevo.setActivo(true);
            return usuarioRepository.save(nuevo);
        });
        u.setRol(obtenerRol(rol)); // el rol cargado es LAZY
        return u;
    }

    private Rol obtenerRol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol r = new Rol();
            r.setNombre(nombre);
            return rolRepository.save(r);
        });
    }
}
