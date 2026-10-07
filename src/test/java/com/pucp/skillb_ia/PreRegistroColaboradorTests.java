package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.CategoriaHabilidad;
import com.pucp.skillb_ia.model.Habilidad;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.TokenUsuario;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoCv;
import com.pucp.skillb_ia.model.enums.EstadoRegistro;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
