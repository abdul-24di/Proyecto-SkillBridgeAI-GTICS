package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.CategoriaHabilidad;
import com.pucp.skillb_ia.model.Habilidad;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.TokenUsuario;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoCv;
import com.pucp.skillb_ia.model.enums.EstadoRegistro;
import com.pucp.skillb_ia.model.enums.TipoToken;
import com.pucp.skillb_ia.repository.CategoriaHabilidadRepository;
import com.pucp.skillb_ia.repository.HabilidadRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.TokenUsuarioRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.AdminUsuarioService;
import com.pucp.skillb_ia.service.AuthService;
import com.pucp.skillb_ia.service.col.ColaboradorPerfilService;
import com.pucp.skillb_ia.service.rm.RmCertificadoService;
import com.pucp.skillb_ia.service.rm.RmEducacionService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// Pre-registro por invitación: el colaborador completa sus datos y sube su CV al activar la cuenta,
// el Admin lo aprueba o rechaza, y mientras tanto el colaborador solo ve "Registro en revisión".
@SpringBootTest
@ActiveProfiles("test")
class PreRegistroColaboradorTests {

    private static final byte[] PDF = "%PDF-1.4 prueba".getBytes();

    @Autowired private WebApplicationContext context;
    @Autowired private AuthService authService;
    @Autowired private AdminUsuarioService adminUsuarioService;
    @Autowired private ColaboradorPerfilService colaboradorPerfilService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private TokenUsuarioRepository tokenUsuarioRepository;
    @Autowired private HabilidadRepository habilidadRepository;
    @Autowired private CategoriaHabilidadRepository categoriaHabilidadRepository;
    @Autowired private RmCertificadoService rmCertificadoService;
    @Autowired private RmEducacionService rmEducacionService;
    @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Autowired private org.springframework.beans.factory.ObjectProvider<
            org.springframework.session.FindByIndexNameSessionRepository<? extends org.springframework.session.Session>> repositorioSesiones;
    private MockMvc mockMvc;
    private Usuario admin;

    @BeforeEach
    void preparar() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        for (String rol : new String[]{"COLABORADOR", "PROJECT_MANAGER"}) {
            obtenerRol(rol);
        }
        admin = usuarioRepository.findByCorreo("admin.preregistro@test.local").orElseGet(() -> {
            Usuario u = new Usuario();
            u.setCorreo("admin.preregistro@test.local");
            u.setNombre("Ada");
            u.setApellido("Admin");
            u.setRol(obtenerRol("ADMINISTRADOR"));
            u.setActivo(true);
            return usuarioRepository.save(u);
        });
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activarComoColaboradorGuardaDatosYCvYDejaElRegistroPendiente() throws Exception {
        String correo = correoNuevo();
        String token = invitar(correo, "COLABORADOR");

        mockMvc.perform(multipart("/activar-cuenta")
                        .file(new MockMultipartFile("cv", "cv.pdf", "application/pdf", PDF))
                        .param("token", token).param("nombreCompleto", "Ana Perez")
                        .param("telefono", "987654321").param("descripcion", "Desarrolladora backend")
                        .param("password", "Segura#123").param("confirmarPassword", "Segura#123")
                        .param("aceptaPolitica", "true"))
                .andExpect(status().is3xxRedirection());

        Usuario u = usuarioRepository.findByCorreo(correo).orElseThrow();
        assertThat(u.getTelefono()).isEqualTo("987654321");
        assertThat(u.getDescripcion()).isEqualTo("Desarrolladora backend");
        assertThat(u.getRegistroEstado()).isEqualTo(EstadoRegistro.PENDIENTE);
        assertThat(u.getCvUrl()).isNotBlank();
        assertThat(u.getCvEstado()).isEqualTo(EstadoCv.PENDIENTE);
    }

    @Test
    void laExperienciaDelPreRegistroQuedaEnElPerfilIgnorandoFilasVacias() throws Exception {
        String correo = correoNuevo();
        String token = invitar(correo, "COLABORADOR");

        mockMvc.perform(multipart("/activar-cuenta")
                        .file(new MockMultipartFile("cv", "cv.pdf", "application/pdf", PDF))
                        .param("token", token).param("nombreCompleto", "Ana Perez")
                        .param("telefono", "987654321")
                        .param("experiencias[0].cargo", "Analista").param("experiencias[0].empresa", "Acme")
                        .param("experiencias[0].descripcion", "Reportes y tableros")
                        .param("experiencias[0].fechaInicio", "2021-03-01").param("experiencias[0].fechaFin", "2023-06-30")
                        .param("experiencias[1].cargo", "Desarrolladora").param("experiencias[1].empresa", "Beta")
                        .param("experiencias[1].descripcion", "APIs REST")
                        .param("experiencias[1].fechaInicio", "2023-07-01").param("experiencias[1].actual", "true")
                        .param("experiencias[2].cargo", "")
                        .param("password", "Segura#123").param("confirmarPassword", "Segura#123")
                        .param("aceptaPolitica", "true"))
                .andExpect(status().is3xxRedirection());

        Usuario u = usuarioRepository.findByCorreo(correo).orElseThrow();
        var experiencias = colaboradorPerfilService.listarExperienciaProfesional(u);
        assertThat(experiencias).hasSize(2);
        assertThat(experiencias).extracting("empresa").containsExactlyInAnyOrder("Acme", "Beta");
        assertThat(experiencias.stream().filter(e -> e.isActual()).findFirst().orElseThrow().getFechaFin()).isNull();
    }

    @Test
    void unaFilaDeExperienciaIncompletaOConFechasInvertidasVuelveAlFormulario() throws Exception {
        String token = invitar(correoNuevo(), "COLABORADOR");

        mockMvc.perform(multipart("/activar-cuenta")
                        .file(new MockMultipartFile("cv", "cv.pdf", "application/pdf", PDF))
                        .param("token", token).param("nombreCompleto", "Ana Perez")
                        .param("telefono", "987654321")
                        .param("experiencias[0].cargo", "Analista")
                        .param("experiencias[0].fechaInicio", "2023-06-30").param("experiencias[0].fechaFin", "2021-03-01")
                        .param("password", "Segura#123").param("confirmarPassword", "Segura#123")
                        .param("aceptaPolitica", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/activar-cuenta"))
                .andExpect(model().attributeHasFieldErrors("activarCuentaForm",
                        "experiencias[0].empresa", "experiencias[0].descripcion", "experiencias[0].fechaFin"));
    }

    @Test
    void certificadosYFormacionDelPreRegistroQuedanEnElPerfilYElRmLosVeSoloTrasAprobar() throws Exception {
        Habilidad java = habilidadDelCatalogo();
        String marca = "Zoe" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String correo = correoNuevo();
        String token = invitar(correo, "COLABORADOR");

        mockMvc.perform(multipart("/activar-cuenta")
                        .file(new MockMultipartFile("cv", "cv.pdf", "application/pdf", PDF))
                        .file(new MockMultipartFile("certificados[0].archivo", "c.pdf", "application/pdf", PDF))
                        .file(new MockMultipartFile("estudios[0].archivo", "e.pdf", "application/pdf", PDF))
                        .param("token", token).param("nombreCompleto", "Ana " + marca)
                        .param("telefono", "987654321")
                        .param("certificados[0].habilidadId", String.valueOf(java.getId()))
                        .param("certificados[0].nivel", "INTERMEDIO")
                        .param("estudios[0].institucion", "PUCP").param("estudios[0].titulo", "Ingeniería Informática")
                        .param("estudios[0].fechaInicio", "2015-03-01").param("estudios[0].fechaFin", "2020-12-15")
                        .param("password", "Segura#123").param("confirmarPassword", "Segura#123")
                        .param("aceptaPolitica", "true"))
                .andExpect(status().is3xxRedirection());

        Usuario u = usuarioRepository.findByCorreo(correo).orElseThrow();
        assertThat(colaboradorPerfilService.listarCertificados(u)).hasSize(1);
        assertThat(colaboradorPerfilService.listarHabilidades(u)).hasSize(1);
        assertThat(colaboradorPerfilService.listarEducacion(u)).hasSize(1);

        // Registro sin aprobar: el RM todavía no ve nada de este colaborador.
        assertThat(rmCertificadoService.listarPendientes())
                .noneMatch(v -> v.getColaboradorNombre().contains(marca));
        assertThat(rmEducacionService.listarPendientes())
                .noneMatch(v -> v.getColaboradorNombre().contains(marca));

        adminUsuarioService.aprobarRegistro(u.getId(), admin);

        assertThat(rmCertificadoService.listarPendientes())
                .anyMatch(v -> v.getColaboradorNombre().contains(marca));
        assertThat(rmEducacionService.listarPendientes())
                .anyMatch(v -> v.getColaboradorNombre().contains(marca));
    }

    @Test
    void certificadoOFormacionIncompletosVuelvenAlFormularioConErroresPorFila() throws Exception {
        Habilidad java = habilidadDelCatalogo();
        String token = invitar(correoNuevo(), "COLABORADOR");

        mockMvc.perform(multipart("/activar-cuenta")
                        .file(new MockMultipartFile("cv", "cv.pdf", "application/pdf", PDF))
                        .file(new MockMultipartFile("certificados[0].archivo", "c.txt", "text/plain", PDF))
                        .param("token", token).param("nombreCompleto", "Ana Perez")
                        .param("telefono", "987654321")
                        .param("certificados[0].habilidadId", String.valueOf(java.getId()))
                        .param("estudios[0].institucion", "PUCP")
                        .param("estudios[0].fechaInicio", "2020-01-01").param("estudios[0].fechaFin", "2030-01-01")
                        .param("password", "Segura#123").param("confirmarPassword", "Segura#123")
                        .param("aceptaPolitica", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/activar-cuenta"))
                .andExpect(model().attributeHasFieldErrors("activarCuentaForm",
                        "certificados[0].nivel", "certificados[0].archivo",
                        "estudios[0].titulo", "estudios[0].fechaFin", "estudios[0].archivo"));
    }

    @Test
    void elTelefonoDeUnColaboradorSigueLaReglaDeSuPerfil() throws Exception {
        String token = invitar(correoNuevo(), "COLABORADOR");

        mockMvc.perform(multipart("/activar-cuenta")
                        .file(new MockMultipartFile("cv", "cv.pdf", "application/pdf", PDF))
                        .param("token", token).param("nombreCompleto", "Ana Perez")
                        .param("telefono", "+51987654321")
                        .param("password", "Segura#123").param("confirmarPassword", "Segura#123")
                        .param("aceptaPolitica", "true"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("activarCuentaForm", "telefono"));
    }

    @Test
    void colaboradorSinCvOConTelefonoInvalidoVuelveAlFormulario() throws Exception {
        String token = invitar(correoNuevo(), "COLABORADOR");

        mockMvc.perform(multipart("/activar-cuenta")
                        .param("token", token).param("nombreCompleto", "Ana Perez")
                        .param("telefono", "12-34")
                        .param("password", "Segura#123").param("confirmarPassword", "Segura#123")
                        .param("aceptaPolitica", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/activar-cuenta"))
                .andExpect(model().attributeHasFieldErrors("activarCuentaForm", "telefono", "cv"));
    }

    @Test
    void otrosRolesActivanSinCvNiAprobacion() throws Exception {
        String correo = correoNuevo();
        String token = invitar(correo, "PROJECT_MANAGER");

        mockMvc.perform(multipart("/activar-cuenta")
                        .param("token", token).param("nombreCompleto", "Pablo Gomez")
                        .param("telefono", "987654321")
                        .param("password", "Segura#123").param("confirmarPassword", "Segura#123")
                        .param("aceptaPolitica", "true"))
                .andExpect(status().is3xxRedirection());

        Usuario u = usuarioRepository.findByCorreo(correo).orElseThrow();
        assertThat(u.getRegistroEstado()).isNull();
        assertThat(u.getTelefono()).isEqualTo("987654321");
    }

    @Test
    void mientrasEstaPendienteSoloVeLaPantallaDeRegistro() throws Exception {
        Usuario colaborador = colaboradorPendiente();
        autenticar(colaborador);

        mockMvc.perform(get("/colaborador/dashboard"))
                .andExpect(redirectedUrl("/colaborador/registro"));
        mockMvc.perform(get("/colaborador/registro"))
                .andExpect(status().isOk())
                .andExpect(view().name("col/col-registro"));

        adminUsuarioService.aprobarRegistro(colaborador.getId(), admin);

        mockMvc.perform(get("/colaborador/dashboard"))
                .andExpect(status().isOk());
        assertThat(usuarioRepository.findById(colaborador.getId()).orElseThrow().getRegistroEstado())
                .isEqualTo(EstadoRegistro.APROBADO);
    }

    @Test
    void rechazarExigeMotivoYPermiteReenviarElCv() throws Exception {
        Usuario colaborador = colaboradorPendiente();

        assertThatThrownBy(() -> adminUsuarioService.rechazarRegistro(colaborador.getId(), "  ", admin))
                .isInstanceOf(IllegalArgumentException.class);

        adminUsuarioService.rechazarRegistro(colaborador.getId(), "El CV está incompleto.", admin);
        Usuario rechazado = usuarioRepository.findById(colaborador.getId()).orElseThrow();
        assertThat(rechazado.getRegistroEstado()).isEqualTo(EstadoRegistro.RECHAZADO);
        assertThat(rechazado.getCvEstado()).isEqualTo(EstadoCv.RECHAZADO);
        assertThat(rechazado.getMotivoRechazo()).isEqualTo("El CV está incompleto.");

        autenticar(rechazado);
        mockMvc.perform(get("/colaborador/dashboard")).andExpect(redirectedUrl("/colaborador/registro"));

        colaboradorPerfilService.actualizarCv(rechazado,
                new MockMultipartFile("cv", "nuevo.pdf", "application/pdf", PDF));
        Usuario reenviado = usuarioRepository.findById(colaborador.getId()).orElseThrow();
        assertThat(reenviado.getRegistroEstado()).isEqualTo(EstadoRegistro.PENDIENTE);
        assertThat(reenviado.getCvEstado()).isEqualTo(EstadoCv.PENDIENTE);
        assertThat(reenviado.getMotivoRechazo()).isNull();
    }

    @Test
    void noSePuedeAprobarSinCv() {
        String correo = correoNuevo();
        String token = invitar(correo, "COLABORADOR");
        authService.activarCuenta(token, "Sin Cv", "987654321", null, "Segura#123");
        Usuario u = usuarioRepository.findByCorreo(correo).orElseThrow();

        assertThatThrownBy(() -> adminUsuarioService.aprobarRegistro(u.getId(), admin))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- reenviar la invitación invalida el enlace anterior ----------

    @Test
    void alReenviarLaInvitacionElEnlaceAnteriorDejaDeServir() throws Exception {
        String correo = correoNuevo();
        Usuario invitado = authService.invitarUsuario(correo, "PROJECT_MANAGER", admin);
        String viejo = tokensDeActivacion(invitado).get(0).getToken();

        authService.reenviarActivacion(invitado, admin);
        String nuevo = tokensDeActivacion(invitado).stream()
                .map(TokenUsuario::getToken).filter(t -> !t.equals(viejo)).findFirst().orElseThrow();

        assertThat(authService.validarTokenActivacion(viejo).estado()).isEqualTo(AuthService.EstadoToken.EXPIRADO);
        assertThat(authService.validarTokenActivacion(nuevo).estado()).isEqualTo(AuthService.EstadoToken.VALIDO);

        // El enlace viejo muestra "El enlace ya no es válido" y no permite activar.
        mockMvc.perform(get("/activar-cuenta").param("token", viejo))
                .andExpect(model().attribute("estado", AuthService.EstadoToken.EXPIRADO));
        assertThatThrownBy(() -> authService.activarCuenta(viejo, "Vic Viejo", "987654321", null, "Segura#123"))
                .isInstanceOf(IllegalStateException.class);

        // Con el enlace nuevo sí se activa.
        authService.activarCuenta(nuevo, "Nora Nueva", "987654321", null, "Segura#123");
        assertThat(usuarioRepository.findByCorreo(correo).orElseThrow().getPasswordHash()).isNotBlank();
    }

    @Test
    void conVariosReenviosSoloFuncionaElUltimoEnlace() {
        String correo = correoNuevo();
        Usuario invitado = authService.invitarUsuario(correo, "PROJECT_MANAGER", admin);
        authService.reenviarActivacion(invitado, admin);
        authService.reenviarActivacion(invitado, admin);

        List<TokenUsuario> tokens = tokensDeActivacion(invitado);
        assertThat(tokens).hasSize(3);
        long validos = tokens.stream()
                .filter(t -> authService.validarTokenActivacion(t.getToken()).esValido())
                .count();
        assertThat(validos).isEqualTo(1);
        TokenUsuario ultimo = tokens.stream().max(java.util.Comparator.comparing(TokenUsuario::getId)).orElseThrow();
        assertThat(authService.validarTokenActivacion(ultimo.getToken()).esValido()).isTrue();
    }

    private List<TokenUsuario> tokensDeActivacion(Usuario usuario) {
        return tokenUsuarioRepository.findAll().stream()
                .filter(t -> t.getUsuario().getId().equals(usuario.getId()))
                .filter(t -> t.getTipo() == TipoToken.ACTIVACION)
                .toList();
    }

    // ---------- recuperación de contraseña: no debe saltarse la activación ----------

    @Test
    void unInvitadoQueNoActivoSuCuentaNoRecibeCodigoDeRecuperacion() throws Exception {
        String correo = correoNuevo();
        Usuario invitado = usuarioRepository.findById(invitarYDevolverId(correo)).orElseThrow();

        // La pantalla responde igual (no revela si la cuenta existe), pero no se crea ningún código.
        mockMvc.perform(post("/recuperar").param("correo", correo))
                .andExpect(redirectedUrl("/verificar-codigo?correo=" + correo));
        assertThat(codigosDeRecuperacion(invitado)).isEmpty();
        assertThat(usuarioRepository.findById(invitado.getId()).orElseThrow().getPasswordHash()).isNull();
    }

    @Test
    void unaCuentaActivadaSiPuedeRecuperarSuContrasena() {
        String correo = correoNuevo();
        String token = invitar(correo, "PROJECT_MANAGER");
        Usuario activado = authService.activarCuenta(token, "Pia Activa", "987654321", null, "Segura#123");

        authService.solicitarRecuperacion(correo);

        List<TokenUsuario> codigos = codigosDeRecuperacion(activado);
        assertThat(codigos).hasSize(1);
        assertThat(authService.validarCodigoRecuperacion(correo, codigos.get(0).getToken())).isPresent();
        authService.restablecerContrasena(correo, codigos.get(0).getToken(), "Nueva#1234");
    }

    @Test
    void unCodigoDeRecuperacionEmitidoAUnInvitadoSinActivarNoSirve() {
        // Simula un código creado antes del arreglo para una cuenta que nunca se activó.
        String correo = correoNuevo();
        Usuario invitado = usuarioRepository.findById(invitarYDevolverId(correo)).orElseThrow();
        TokenUsuario antiguo = new TokenUsuario();
        antiguo.setUsuario(invitado);
        antiguo.setTipo(TipoToken.RECUPERACION);
        antiguo.setToken(String.valueOf(100000 + (int) (Math.random() * 899999)));
        antiguo.setFechaExpiracion(LocalDateTime.now().plusMinutes(15));
        tokenUsuarioRepository.save(antiguo);

        assertThat(authService.validarCodigoRecuperacion(correo, antiguo.getToken())).isEmpty();
        assertThatThrownBy(() -> authService.restablecerContrasena(correo, antiguo.getToken(), "Segura#123"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(usuarioRepository.findById(invitado.getId()).orElseThrow().getPasswordHash()).isNull();
    }

    @Test
    void unaCuentaDesactivadaNoRecibeCodigoDeRecuperacion() {
        String correo = correoNuevo();
        String token = invitar(correo, "PROJECT_MANAGER");
        Usuario usuario = authService.activarCuenta(token, "Dina Desactivada", "987654321", null, "Segura#123");
        usuario.setActivo(false);
        usuarioRepository.save(usuario);

        authService.solicitarRecuperacion(correo);

        assertThat(codigosDeRecuperacion(usuario)).isEmpty();
    }

    // ---------- recuperación de contraseña: verificación obligatoria, intentos, códigos y sesiones ----------

    @Test
    void elFlujoCompletoCambiaLaContrasenaConElCodigoVerificadoEnLaSesion() throws Exception {
        String correo = correoNuevo();
        Usuario usuario = usuarioActivado(correo);
        authService.solicitarRecuperacion(correo);
        String codigo = codigosDeRecuperacion(usuario).get(0).getToken();
        org.springframework.mock.web.MockHttpSession sesion = new org.springframework.mock.web.MockHttpSession();

        mockMvc.perform(post("/verificar-codigo").param("correo", correo).param("codigo", codigo).session(sesion))
                .andExpect(redirectedUrl("/nueva-contrasena"));
        mockMvc.perform(get("/nueva-contrasena").session(sesion))
                .andExpect(view().name("auth/nueva-contrasena"));
        mockMvc.perform(post("/nueva-contrasena").session(sesion)
                        .param("password", "Nueva#1234").param("confirmarPassword", "Nueva#1234"))
                .andExpect(redirectedUrl("/login?recuperado"));

        assertThat(passwordEncoder.matches("Nueva#1234",
                usuarioRepository.findById(usuario.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void sinPasarPorLaVerificacionNoSePuedeCambiarLaContrasena() throws Exception {
        String correo = correoNuevo();
        Usuario usuario = usuarioActivado(correo);
        authService.solicitarRecuperacion(correo);
        String codigo = codigosDeRecuperacion(usuario).get(0).getToken();
        String hashAntes = usuarioRepository.findById(usuario.getId()).orElseThrow().getPasswordHash();

        // Aunque se conozca un código vigente, sin verificarlo con el correo la pantalla final no lo acepta.
        mockMvc.perform(get("/nueva-contrasena").param("token", codigo))
                .andExpect(redirectedUrl("/recuperar?vencido"));
        mockMvc.perform(post("/nueva-contrasena").param("token", codigo)
                        .param("password", "Intrusa#123").param("confirmarPassword", "Intrusa#123"))
                .andExpect(redirectedUrl("/recuperar?vencido"));

        assertThat(usuarioRepository.findById(usuario.getId()).orElseThrow().getPasswordHash()).isEqualTo(hashAntes);
    }

    @Test
    void tras5CodigosIncorrectosElCodigoSeAnula() {
        String correo = correoNuevo();
        Usuario usuario = usuarioActivado(correo);
        authService.solicitarRecuperacion(correo);
        String codigo = codigosDeRecuperacion(usuario).get(0).getToken();
        String incorrecto = codigo.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            assertThat(authService.validarCodigoRecuperacion(correo, incorrecto)).isEmpty();
        }

        assertThat(authService.validarCodigoRecuperacion(correo, codigo)).isEmpty();
        assertThatThrownBy(() -> authService.restablecerContrasena(correo, codigo, "Nueva#1234"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void con4CodigosIncorrectosElCorrectoTodaviaSirve() {
        String correo = correoNuevo();
        Usuario usuario = usuarioActivado(correo);
        authService.solicitarRecuperacion(correo);
        String codigo = codigosDeRecuperacion(usuario).get(0).getToken();
        String incorrecto = codigo.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 4; i++) {
            authService.validarCodigoRecuperacion(correo, incorrecto);
        }

        assertThat(authService.validarCodigoRecuperacion(correo, codigo)).isPresent();
    }

    @Test
    void pedirUnCodigoNuevoAnulaElAnterior() {
        String correo = correoNuevo();
        Usuario usuario = usuarioActivado(correo);
        authService.solicitarRecuperacion(correo);
        String primero = codigosDeRecuperacion(usuario).get(0).getToken();

        authService.solicitarRecuperacion(correo);
        String segundo = codigosDeRecuperacion(usuario).stream().map(TokenUsuario::getToken)
                .filter(c -> !c.equals(primero)).findFirst().orElseThrow();

        assertThat(authService.validarCodigoRecuperacion(correo, primero)).isEmpty();
        assertThat(authService.validarCodigoRecuperacion(correo, segundo)).isPresent();
    }

    @Test
    void cambiarLaContrasenaCierraLasSesionesAbiertasDeLaCuenta() {
        String correo = correoNuevo();
        Usuario usuario = usuarioActivado(correo);
        var sesiones = repositorioSesiones.getObject();
        var abierta = sesiones.createSession();
        abierta.setAttribute(org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, correo);
        guardarSesion(sesiones, abierta);
        assertThat(sesiones.findByPrincipalName(correo)).hasSize(1);

        authService.solicitarRecuperacion(correo);
        authService.restablecerContrasena(correo, codigosDeRecuperacion(usuario).get(0).getToken(), "Nueva#1234");

        assertThat(sesiones.findByPrincipalName(correo)).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private <S extends org.springframework.session.Session> void guardarSesion(
            org.springframework.session.FindByIndexNameSessionRepository<S> repositorio, Object sesion) {
        repositorio.save((S) sesion);
    }

    private Usuario usuarioActivado(String correo) {
        return authService.activarCuenta(invitar(correo, "PROJECT_MANAGER"), "Rita Recupera", "987654321", null, "Segura#123");
    }

    private Long invitarYDevolverId(String correo) {
        return authService.invitarUsuario(correo, "COLABORADOR", admin).getId();
    }

    private List<TokenUsuario> codigosDeRecuperacion(Usuario usuario) {
        return tokenUsuarioRepository.findAll().stream()
                .filter(t -> t.getUsuario().getId().equals(usuario.getId()))
                .filter(t -> t.getTipo() == TipoToken.RECUPERACION)
                .toList();
    }

    // ---------- helpers ----------

    private Habilidad habilidadDelCatalogo() {
        return habilidadRepository.findByNombreIgnoreCase("Java prerregistro").orElseGet(() -> {
            CategoriaHabilidad cat = new CategoriaHabilidad();
            cat.setNombre("Cat preregistro " + UUID.randomUUID());
            cat.setActiva(true);
            cat = categoriaHabilidadRepository.save(cat);
            Habilidad h = new Habilidad();
            h.setNombre("Java prerregistro");
            h.setCategoria(cat);
            h.setActiva(true);
            return habilidadRepository.save(h);
        });
    }

    private Usuario colaboradorPendiente() {
        String correo = correoNuevo();
        String token = invitar(correo, "COLABORADOR");
        Usuario u = authService.activarCuenta(token, "Carla Diaz", "987654321", null, "Segura#123");
        colaboradorPerfilService.actualizarCv(u, new MockMultipartFile("cv", "cv.pdf", "application/pdf", PDF));
        return usuarioRepository.findByCorreo(correo).orElseThrow();
    }

    private String invitar(String correo, String rol) {
        Usuario u = authService.invitarUsuario(correo, rol, admin);
        return tokenUsuarioRepository.findAll().stream()
                .filter(t -> t.getUsuario().getId().equals(u.getId()))
                .map(TokenUsuario::getToken).findFirst().orElseThrow();
    }

    private void autenticar(Usuario usuario) {
        usuario.setRol(obtenerRol("COLABORADOR")); // el rol cargado es LAZY
        UsuarioDetails details = new UsuarioDetails(usuario);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    private String correoNuevo() {
        return "preregistro." + UUID.randomUUID() + "@test.local";
    }

    private Rol obtenerRol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol rol = new Rol();
            rol.setNombre(nombre);
            return rolRepository.save(rol);
        });
    }
}
