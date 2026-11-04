package com.pucp.skillb_ia;

import com.pucp.skillb_ia.controller.GlobalExceptionHandler;
import com.pucp.skillb_ia.model.LogAuditoria;
import com.pucp.skillb_ia.model.PublicacionForo;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.LogAuditoriaRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.HtmlSeguro;
import com.pucp.skillb_ia.security.LimiteSolicitudesFilter;
import com.pucp.skillb_ia.security.UsuarioDetailsService;
import com.pucp.skillb_ia.service.AdminUsuarioService;
import com.pucp.skillb_ia.service.ArchivoAlmacenamientoService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Defensas pensadas para un intento real de ataque: XSS, archivos falsos, fuerza bruta, inundación de
// peticiones, redirecciones manipuladas y sesiones de usuarios desactivados.
@SpringBootTest
@ActiveProfiles("test")
class SeguridadTests {

    @Autowired private WebApplicationContext context;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private LogAuditoriaRepository logAuditoriaRepository;
    @Autowired private UsuarioDetailsService usuarioDetailsService;
    @Autowired private AdminUsuarioService adminUsuarioService;
    @Autowired private com.pucp.skillb_ia.service.AuthService authService;
    @Autowired private ArchivoAlmacenamientoService archivoAlmacenamientoService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> repositorioSesiones;

    private MockMvc mvcConSeguridad;
    private MockMvc mvcSinSeguridad;

    @BeforeEach
    void preparar() {
        mvcConSeguridad = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        mvcSinSeguridad = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // ---------- XSS en foros ----------

    @Test
    void elHtmlDeLosForosPierdeTodoLoPeligroso() {
        PublicacionForo publicacion = new PublicacionForo();
        publicacion.setContenido("<p onclick=\"robar()\">Hola <strong>equipo</strong></p>"
                + "<script>robar()</script>"
                + "<img src=x onerror=robar()>"
                + "<a href=\"javascript:robar()\">clic</a>"
                + "<iframe src=\"http://malo.com\"></iframe>");

        assertThat(publicacion.getContenido())
                .contains("<strong>equipo</strong>")
                .doesNotContain("<script", "onerror", "onclick", "javascript:", "<iframe", "robar");
    }

    @Test
    void laLimpiezaConservaElFormatoDeQuillYLasImagenesDelForo() {
        String html = HtmlSeguro.limpiar("<p class=\"ql-align-center\"><em>texto</em></p>"
                + "<ul><li>uno</li></ul><img src=\"/uploads/foro/foto.png\">");

        assertThat(html).contains("ql-align-center", "<em>texto</em>", "<li>uno</li>", "/uploads/foro/foto.png");
    }

    // ---------- archivos ----------

    @Test
    void noSeGuardaUnArchivoCuyoContenidoNoCoincideConSuFormato() {
        MockMultipartFile falso = new MockMultipartFile("archivo", "foto.png", "image/png", "<html>".getBytes());

        assertThatThrownBy(() -> archivoAlmacenamientoService.guardar(falso, "pruebas", "foto.png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no corresponde");
    }

    @Test
    void unaSubidaSinArchivoEsUnError400Y_noUn500() throws Exception {
        mvcSinSeguridad.perform(post("/pm/foro/upload-imagen"))
                .andExpect(status().isBadRequest());
    }

    // ---------- inundación de peticiones ----------

    private int responder(LimiteSolicitudesFilter filtro, String metodo, String ruta, String ip) throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest(metodo, ruta);
        peticion.setRemoteAddr(ip);
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        filtro.doFilter(peticion, respuesta, new MockFilterChain());
        return respuesta.getStatus();
    }

    @Test
    void unaIpQueSuperaElLimiteRecibe429YLasDemasNoSeAfectan() throws Exception {
        LimiteSolicitudesFilter filtro = new LimiteSolicitudesFilter(3, 100);

        for (int i = 0; i < 3; i++) {
            assertThat(responder(filtro, "GET", "/rm/dashboard", "10.0.0.1")).isEqualTo(200);
        }
        assertThat(responder(filtro, "GET", "/rm/dashboard", "10.0.0.1")).isEqualTo(429);
        assertThat(responder(filtro, "GET", "/rm/dashboard", "10.0.0.2")).isEqualTo(200);
    }

    @Test
    void losFormulariosPublicosTienenUnLimiteMasBajoYLosEstaticosNoCuentan() throws Exception {
        LimiteSolicitudesFilter filtro = new LimiteSolicitudesFilter(100, 2);

        assertThat(responder(filtro, "POST", "/login", "10.0.0.3")).isEqualTo(200);
        assertThat(responder(filtro, "POST", "/recuperar", "10.0.0.3")).isEqualTo(200);
        assertThat(responder(filtro, "POST", "/login", "10.0.0.3")).isEqualTo(429);

        LimiteSolicitudesFilter estricto = new LimiteSolicitudesFilter(1, 1);
        for (int i = 0; i < 5; i++) {
            assertThat(responder(estricto, "GET", "/css/auth-css/auth.css", "10.0.0.4")).isEqualTo(200);
        }
    }

    // ---------- fuerza bruta en el login ----------

    private Usuario usuarioNuevo(String rol) {
        Usuario usuario = new Usuario();
        usuario.setCorreo("seguridad." + UUID.randomUUID() + "@test.local");
        usuario.setNombre("Sara");
        usuario.setApellido("Segura");
        usuario.setPasswordHash(passwordEncoder.encode("Correcta#123"));
        usuario.setRol(rolRepository.findByNombre(rol).orElseGet(() -> {
            Rol nuevo = new Rol();
            nuevo.setNombre(rol);
            return rolRepository.save(nuevo);
        }));
        usuario.setActivo(true);
        return usuarioRepository.save(usuario);
    }

    private void registrar(Usuario usuario, String accion) {
        LogAuditoria log = new LogAuditoria();
        log.setUsuario(usuario);
        log.setAccion(accion);
        log.setEntidad("USUARIO");
        logAuditoriaRepository.save(log);
    }

    private String tokenDelFormulario(String ruta, MockHttpSession sesion) throws Exception {
        String html = mvcConSeguridad.perform(get(ruta).session(sesion)).andReturn().getResponse().getContentAsString();
        Matcher campo = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(html);
        assertThat(campo.find()).isTrue();
        return campo.group(1);
    }

    @Test
    void tras5ContrasenasIncorrectasLaCuentaSeBloqueaAunqueLuegoAcierte() throws Exception {
        Usuario usuario = usuarioNuevo("COLABORADOR");
        MockHttpSession sesion = new MockHttpSession();
        String token = tokenDelFormulario("/login", sesion);

        for (int i = 0; i < 5; i++) {
            mvcConSeguridad.perform(post("/login").session(sesion).param("_csrf", token)
                            .param("username", usuario.getCorreo()).param("password", "Incorrecta#1"))
                    .andExpect(redirectedUrl("/login?error"));
        }

        mvcConSeguridad.perform(post("/login").session(sesion).param("_csrf", token)
                        .param("username", usuario.getCorreo()).param("password", "Correcta#123"))
                .andExpect(redirectedUrl("/login?bloqueado"));
    }

    @Test
    void unInicioDeSesionCorrectoReiniciaLaCuentaDeIntentos() {
        Usuario usuario = usuarioNuevo("COLABORADOR");
        for (int i = 0; i < 5; i++) {
            registrar(usuario, "LOGIN_FALLIDO");
        }
        assertThat(usuarioDetailsService.loadUserByUsername(usuario.getCorreo()).isAccountNonLocked()).isFalse();

        registrar(usuario, "LOGIN");

        assertThat(usuarioDetailsService.loadUserByUsername(usuario.getCorreo()).isAccountNonLocked()).isTrue();
    }

    // ---------- sesiones ----------

    @Test
    @SuppressWarnings("unchecked")
    void desactivarAUnUsuarioCierraSusSesionesAbiertas() {
        Usuario admin = usuarioNuevo("ADMINISTRADOR");
        Usuario usuario = usuarioNuevo("COLABORADOR");
        FindByIndexNameSessionRepository<Session> sesiones =
                (FindByIndexNameSessionRepository<Session>) repositorioSesiones.getObject();
        Session abierta = sesiones.createSession();
        abierta.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, usuario.getCorreo());
        sesiones.save(abierta);

        adminUsuarioService.desactivar(usuario.getId(), admin);

        assertThat(sesiones.findByPrincipalName(usuario.getCorreo())).isEmpty();
    }

    // ---------- redirecciones y cabeceras ----------

    @Test
    void unRefererManipuladoNuncaRedirigeAOtroSitio() {
        assertThat(paginaAnteriorCon("http://localhost:8080/rm/cursos?pagina=2")).isEqualTo("/rm/cursos?pagina=2");
        assertThat(paginaAnteriorCon("http://sitio-malo.com/robar?x=1")).isEqualTo("/robar?x=1");
        assertThat(paginaAnteriorCon("http://malo.com/{variable}")).isEqualTo("/inicio");
        assertThat(paginaAnteriorCon("//malo.com")).isEqualTo("/inicio");
        assertThat(paginaAnteriorCon("javascript:alert(1)")).isEqualTo("/inicio");
    }

    private String paginaAnteriorCon(String referer) {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.addHeader("Referer", referer);
        return GlobalExceptionHandler.paginaAnterior(peticion, "/inicio");
    }

    @Test
    void unCorreoConCaracteresRarosNoRompeLaRecuperacion() throws Exception {
        mvcSinSeguridad.perform(post("/recuperar").param("correo", "a{b}@x.com"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void noSeInvitaAUnCorreoConCaracteresPeligrosos() {
        for (String correo : new String[]{"<script>alert(1)</script>@x.com", "a'b@x.com", "a b@x.com", "a@x", "a@-"}) {
            assertThatThrownBy(() -> authService.validarCorreo(correo))
                    .as(correo).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(authService.validarCorreo(" ana.perez+rrhh@pucp.edu.pe ")).isEqualTo("ana.perez+rrhh@pucp.edu.pe");
    }

    @Test
    void lasRespuestasLlevanLasCabecerasDeSeguridad() throws Exception {
        mvcConSeguridad.perform(get("/login"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("frame-ancestors")))
                .andExpect(header().string("Referrer-Policy", "same-origin"));
    }
}
