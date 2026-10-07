package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.CategoriaHabilidad;
import com.pucp.skillb_ia.model.Curso;
import com.pucp.skillb_ia.model.enums.ModalidadCurso;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.TokenUsuario;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.CategoriaHabilidadRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.TokenUsuarioRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.AdminCursoService;
import com.pucp.skillb_ia.service.AdminHabilidadService;
import com.pucp.skillb_ia.service.AuthService;
import com.pucp.skillb_ia.service.NotificacionService;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// Puntos de revisión del Admin: teléfono del perfil, habilidades sin nombres repetidos y
// notificación al Admin cuando una cuenta invitada se activa.
@SpringBootTest
@ActiveProfiles("test")
class AdminPerfilYCatalogosTests {

    @Autowired private WebApplicationContext context;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private TokenUsuarioRepository tokenUsuarioRepository;
    @Autowired private CategoriaHabilidadRepository categoriaHabilidadRepository;
    @Autowired private AdminHabilidadService adminHabilidadService;
    @Autowired private AdminCursoService adminCursoService;
    @Autowired private com.pucp.skillb_ia.repository.CursoRepository cursoRepository;
    @Autowired private com.pucp.skillb_ia.service.col.ColaboradorCursoService colaboradorCursoService;
    @Autowired private com.pucp.skillb_ia.service.AdminConfiguracionService adminConfiguracionService;
    @Autowired private com.pucp.skillb_ia.service.admin.AdminForoService adminForoService;
    @Autowired private com.pucp.skillb_ia.service.col.ColaboradorPerfilService colaboradorPerfilService;
    @Autowired private AuthService authService;
    @Autowired private NotificacionService notificacionService;
    private MockMvc mockMvc;
    private Usuario admin;

    @BeforeEach
    void preparar() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        obtenerRol("PROJECT_MANAGER");
        obtenerRol("COLABORADOR");
        admin = usuarioRepository.findByCorreo("admin.perfil@test.local").orElseGet(() -> {
            Usuario u = new Usuario();
            u.setCorreo("admin.perfil@test.local");
            u.setNombre("Alba");
            u.setApellido("Admin");
            u.setRol(obtenerRol("ADMINISTRADOR"));
            u.setActivo(true);
            return usuarioRepository.save(u);
        });
        admin.setRol(obtenerRol("ADMINISTRADOR")); // el rol cargado es LAZY
        UsuarioDetails details = new UsuarioDetails(admin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void telefonoInvalidoVuelveAlPerfilConElErrorDelCampo() throws Exception {
        for (String telefono : new String[]{"", "12-34", "999 999 999", "abc1234567", "00000000000000000000", "0000000000", "12345678", "+51987654321"}) {
            mockMvc.perform(post("/admin/perfil/telefono").param("telefono", telefono))
                    .andExpect(status().isOk())
                    .andExpect(view().name("admin/admin-perfil"))
                    .andExpect(model().attributeHasFieldErrors("telefonoPerfilForm", "telefono"));
        }
    }

    @Test
    void telefonoValidoSeGuardaYRedirige() throws Exception {
        mockMvc.perform(post("/admin/perfil/telefono").param("telefono", "987654321"))
                .andExpect(status().is3xxRedirection());
        assertThat(usuarioRepository.findById(admin.getId()).orElseThrow().getTelefono())
                .isEqualTo("987654321");
    }

    @Test
    void noSePuedeCrearUnaHabilidadConElMismoNombreAunqueCambieMayusculasOEspacios() {
        CategoriaHabilidad cat = new CategoriaHabilidad();
        cat.setNombre("Categoria " + UUID.randomUUID());
        cat.setActiva(true);
        cat = categoriaHabilidadRepository.save(cat);
        String base = "Habilidad" + UUID.randomUUID().toString().substring(0, 8);

        adminHabilidadService.crear(base, cat.getId(), admin);

        Long catId = cat.getId();
        assertThatThrownBy(() -> adminHabilidadService.crear(base, catId, admin))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Ya existe");
        assertThatThrownBy(() -> adminHabilidadService.crear("  " + base.toUpperCase() + " ", catId, admin))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Ya existe");
    }

    @Test
    void cuandoUnaCuentaInvitadaSeActivaElAdminRecibeUnaNotificacion() {
        String correo = "pm." + UUID.randomUUID() + "@test.local";
        Usuario invitado = authService.invitarUsuario(correo, "PROJECT_MANAGER", admin);
        String token = tokenUsuarioRepository.findAll().stream()
                .filter(t -> t.getUsuario().getId().equals(invitado.getId()))
                .map(TokenUsuario::getToken).findFirst().orElseThrow();

        authService.activarCuenta(token, "Paula Mendez", "987654321", null, "Segura#123");

        assertThat(notificacionService.listar(admin.getId()))
                .anySatisfy(n -> {
                    assertThat(n.titulo()).isEqualTo("Cuenta activada");
                    assertThat(n.descripcion()).contains("Paula Mendez");
                    assertThat(n.url()).isEqualTo("/admin/usuarios");
                });
    }

    @Test
    void lasHorasDeUnCursoSeValidanAntesDeLlegarALaBaseDeDatos() {
        for (String horas : new String[]{null, "0", "-3", "1000.01", "8.555"}) {
            Curso curso = cursoAutodidacta("Curso horas " + horas + UUID.randomUUID());
            curso.setHoras(horas == null ? null : new BigDecimal(horas));
            assertThatThrownBy(() -> adminCursoService.crear(curso, admin))
                    .as("horas=%s", horas)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        Curso valido = cursoAutodidacta("Curso horas ok " + UUID.randomUUID());
        valido.setHoras(new BigDecimal("8.5"));
        adminCursoService.crear(valido, admin);
        assertThat(valido.getId()).isNotNull();
    }

    @Test
    void unValorNoNumericoEnLasHorasMuestraUnMensajeEnVezDeUnaPaginaDeError() throws Exception {
        mockMvc.perform(post("/admin/cursos/crear")
                        .param("nombre", "Curso texto").param("categoria", "Tec")
                        .param("modalidad", "VIRTUAL").param("horas", "abc").param("autodidacta", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("mensajeError", org.hamcrest.Matchers.containsString("número válido")));
    }

    @Test
    void noSePuedeInvitarConUnCorreoVacioOSinFormato() {
        for (String correo : new String[]{null, "", "   ", "noesuncorreo", "a@b", "con espacio@x.com", "@x.com"}) {
            assertThatThrownBy(() -> authService.invitarUsuario(correo, "PROJECT_MANAGER", admin))
                    .as("correo=%s", correo)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        String ok = "  valido." + UUID.randomUUID() + "@test.local ";
        assertThat(authService.invitarUsuario(ok, "PROJECT_MANAGER", admin).getCorreo()).isEqualTo(ok.strip());
    }

    @Test
    void laClaveDeUnParametroSoloAceptaLetrasNumerosYGuionesBajos() {
        for (String clave : new String[]{"ZZ raro!!", "A-B", "ñandú", "X".repeat(61)}) {
            assertThatThrownBy(() -> adminConfiguracionService.crear(clave, "d", "1", admin))
                    .as("clave=%s", clave)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        String clave = "PARAM prueba " + UUID.randomUUID().toString().substring(0, 6);
        adminConfiguracionService.crear(clave, "d", "1", admin);
    }

    @Test
    void unForoNoSePuedeCrearSinNombre() {
        assertThatThrownBy(() -> adminForoService.crearForo("   ", true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adminForoService.crearForo("F".repeat(151), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laExperienciaExigeCamposYFechasCoherentes() {
        Usuario colaborador = usuarioRepository.findByCorreo("col.exp.val@test.local").orElseGet(() -> {
            Usuario u = new Usuario();
            u.setCorreo("col.exp.val@test.local");
            u.setNombre("Cora");
            u.setApellido("Exp");
            u.setRol(obtenerRol("COLABORADOR"));
            u.setActivo(true);
            return usuarioRepository.save(u);
        });
        assertThatThrownBy(() -> colaboradorPerfilService.agregarExperiencia(colaborador, exp("", "E", "D", "2020-01-01", null, true)))
                .hasMessageContaining("cargo");
        assertThatThrownBy(() -> colaboradorPerfilService.agregarExperiencia(colaborador, exp("C", "E", " ", "2020-01-01", null, true)))
                .hasMessageContaining("Describe");
        assertThatThrownBy(() -> colaboradorPerfilService.agregarExperiencia(colaborador, exp("C", "E", "D", "2999-01-01", null, true)))
                .hasMessageContaining("futura");
        assertThatThrownBy(() -> colaboradorPerfilService.agregarExperiencia(colaborador, exp("C", "E", "D", "2020-01-01", null, false)))
                .hasMessageContaining("fecha de fin");
        assertThatThrownBy(() -> colaboradorPerfilService.agregarExperiencia(colaborador, exp("C", "E", "D", "2022-01-01", "2021-01-01", false)))
                .hasMessageContaining("anterior");
        colaboradorPerfilService.agregarExperiencia(colaborador, exp("C", "E", "D", "2020-01-01", null, true));
    }

    private com.pucp.skillb_ia.model.ExperienciaProfesional exp(String cargo, String empresa, String descripcion,
                                                                String inicio, String fin, boolean actual) {
        var e = new com.pucp.skillb_ia.model.ExperienciaProfesional();
        e.setCargo(cargo);
        e.setEmpresa(empresa);
        e.setDescripcion(descripcion);
        e.setFechaInicio(java.time.LocalDate.parse(inicio));
        e.setFechaFin(fin == null ? null : java.time.LocalDate.parse(fin));
        e.setActual(actual);
        return e;
    }

    @Test
    void unCursoCaducadoNoSeOfreceNiSePuedeSolicitar() {
        Usuario colaborador = usuarioRepository.findByCorreo("col.caducado@test.local").orElseGet(() -> {
            Usuario u = new Usuario();
            u.setCorreo("col.caducado@test.local");
            u.setNombre("Cora");
            u.setApellido("Caducado");
            u.setRol(obtenerRol("COLABORADOR"));
            u.setActivo(true);
            return usuarioRepository.save(u);
        });
        Curso caducado = cursoAutodidacta("Curso caducado " + UUID.randomUUID());
        caducado.setAutodidacta(false);
        caducado.setHoras(new BigDecimal("4"));
        caducado.setFechaInicio(java.time.LocalDate.now().minusDays(30));
        caducado.setFechaFin(java.time.LocalDate.now().minusDays(1));
        caducado.setCreadoPor(admin);
        Curso guardado = cursoRepository.save(caducado);

        assertThat(colaboradorCursoService.listarCursosDisponibles(colaborador))
                .noneMatch(v -> v.getCurso().getId().equals(guardado.getId()));
        assertThatThrownBy(() -> colaboradorCursoService.solicitarInscripcion(colaborador, guardado.getId(), "quiero"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("terminó");
        assertThat(adminCursoService.listarFinalizados()).anyMatch(c -> c.getId().equals(guardado.getId()));
        assertThat(adminCursoService.listarVigentes()).noneMatch(c -> c.getId().equals(guardado.getId()));
    }

    @Test
    void laFotoDelAdminApareceEnElTopbarYSinFotoSeVenLasIniciales() throws Exception {
        admin.setFotoUrl(null);
        for (String ruta : new String[]{"/admin/dashboard", "/admin/perfil"}) {
            mockMvc.perform(get(ruta))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("class=\"avatar avatar-sm user-avatar\">AA</span>")));
        }

        admin.setFotoUrl("/uploads/perfil/admin-topbar.png");
        for (String ruta : new String[]{"/admin/dashboard", "/admin/perfil", "/admin/cursos"}) {
            mockMvc.perform(get(ruta))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("background-image: url(/uploads/perfil/admin-topbar.png)")))
                    .andExpect(content().string(not(containsString("class=\"avatar avatar-sm user-avatar\">AA</span>"))));
        }
        admin.setFotoUrl(null);
    }

    private Curso cursoAutodidacta(String nombre) {
        Curso curso = new Curso();
        curso.setNombre(nombre);
        curso.setCategoria("Tec");
        curso.setModalidad(ModalidadCurso.VIRTUAL);
        curso.setAutodidacta(true);
        return curso;
    }

    private Rol obtenerRol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol rol = new Rol();
            rol.setNombre(nombre);
            return rolRepository.save(rol);
        });
    }
}
