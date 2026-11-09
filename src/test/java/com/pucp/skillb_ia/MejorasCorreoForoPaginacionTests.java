package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Notificacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.NotificacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.AdminUsuarioService;
import com.pucp.skillb_ia.service.rm.RmAsignacionService;
import com.pucp.skillb_ia.util.Paginador;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Correo que no se pudo enviar, validación de la nueva publicación del colaborador,
// notificaciones nuevas y la paginación que pasó de JavaScript al servidor.
@SpringBootTest
@ActiveProfiles("test")
class MejorasCorreoForoPaginacionTests {

    @Autowired private WebApplicationContext context;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private NotificacionRepository notificacionRepository;
    @Autowired private AdminUsuarioService adminUsuarioService;
    @Autowired private RmAsignacionService rmAsignacionService;

    private MockMvc mockMvc;

    @BeforeEach
    void preparar() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    // ---------- Correo ----------

    @Test
    void siElCorreoNoSePuedeEnviarElAdminVeUnAvisoYElUsuarioIgualSeCrea() throws Exception {
        // En el perfil de pruebas no hay MAIL_USERNAME, así que el envío falla como en un servidor mal configurado.
        Usuario admin = usuario("admin", "ADMINISTRADOR");
        autenticar(admin);
        String correo = "nuevo." + UUID.randomUUID() + "@test.local";

        mockMvc.perform(post("/admin/usuarios").param("correo", correo).param("rol", "Colaborador"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/usuarios"))
                .andExpect(flash().attribute("mensajeError", containsString("no se pudo enviar el correo de invitación")))
                .andExpect(flash().attribute("mensajeError", containsString("Reenviar enlace")));

        assertThat(usuarioRepository.findByCorreo(correo)).isPresent();
    }

    // ---------- Validación del foro (colaborador) ----------

    @Test
    void publicarSinDatosVuelveALaListaDeForosConErroresYReabreElModal() throws Exception {
        autenticar(usuario("col", "COLABORADOR"));

        MvcResult resultado = mockMvc.perform(post("/colaborador/foro/publicar")
                        .param("origen", "foros").param("titulo", "  ").param("contenido", "<p><br></p>"))
                .andExpect(redirectedUrl("/colaborador/foros"))
                .andExpect(flash().attributeExists("abrirModalNuevaPublicacion"))
                .andReturn();

        BindingResult errores = (BindingResult) resultado.getFlashMap()
                .get("org.springframework.validation.BindingResult.colForoPublicacionForm");
        assertThat(errores.hasFieldErrors("foroId")).isTrue();
        assertThat(errores.hasFieldErrors("titulo")).isTrue();
        assertThat(errores.hasFieldErrors("contenido")).isTrue();
    }

    @Test
    void desdeElDetalleElErrorVuelveAlMismoForoYNoAOtro() throws Exception {
        autenticar(usuario("col", "COLABORADOR"));

        mockMvc.perform(post("/colaborador/foro/publicar")
                        .param("origen", "detalle").param("foroId", "123")
                        .param("titulo", "").param("contenido", "Texto"))
                .andExpect(redirectedUrl("/colaborador/foros/detalle?foroId=123"))
                .andExpect(flash().attributeExists("abrirModalNuevaPublicacion"));
    }

    // ---------- Notificaciones ----------

    @Test
    void cuandoElRmRechazaUnaAsignacionElPmRecibeUnaNotificacion() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario rm = usuario("rm", "RESOURCE_MANAGER");
        Usuario colaborador = usuario("col", "COLABORADOR");
        Proyecto proyecto = proyecto(pm);
        Asignacion pendiente = new Asignacion();
        pendiente.setProyecto(proyecto);
        pendiente.setColaborador(colaborador);
        pendiente.setHorasSemanales(new BigDecimal("8"));
        pendiente.setOrigen(OrigenAsignacion.PROPUESTA_PM);
        pendiente.setEstado(EstadoAsignacion.PENDIENTE);
        pendiente.setAprobadoPorPm(true);
        pendiente.setAprobadoPorRm(false);
        pendiente = asignacionRepository.save(pendiente);

        rmAsignacionService.rechazar(pendiente.getId(), "No cumple el perfil requerido.", rm.getId());

        List<Notificacion> delPm = notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(pm);
        assertThat(delPm).anySatisfy(n -> {
            assertThat(n.getTipo()).isEqualTo("ASIGNACION_RECHAZADA_RM");
            assertThat(n.getDescripcion()).contains("No cumple el perfil requerido.");
        });
    }

    // ---------- Paginación en el servidor ----------

    @Test
    void elPaginadorDevuelveSoloLaPaginaPedidaYCorrigeNumerosFueraDeRango() {
        List<Integer> numeros = IntStream.rangeClosed(1, 23).boxed().toList();

        Page<Integer> segunda = Paginador.paginar(numeros, 2);
        assertThat(segunda.getContent()).containsExactly(11, 12, 13, 14, 15, 16, 17, 18, 19, 20);
        assertThat(segunda.getTotalPages()).isEqualTo(3);
        assertThat(segunda.getTotalElements()).isEqualTo(23);

        assertThat(Paginador.paginar(numeros, 99).getContent()).containsExactly(21, 22, 23);
        assertThat(Paginador.paginar(numeros, -5).getNumber()).isZero();
        assertThat(Paginador.paginar(List.of(), 3).getContent()).isEmpty();
    }

    @Test
    void elDirectorioDeUsuariosSeFiltraYPaginaEnElServidor() {
        String marca = "pag" + System.nanoTime();
        for (int i = 0; i < 12; i++) {
            Usuario u = new Usuario();
            u.setCorreo(marca + "." + i + "@test.local");
            u.setNombre("Persona" + i);
            u.setApellido("Prueba");
            u.setRol(rol("COLABORADOR"));
            u.setActivo(true);
            usuarioRepository.save(u);
        }

        Page<AdminUsuarioService.UsuarioFila> primera = adminUsuarioService.listarPagina(marca, null, null, 1);
        Page<AdminUsuarioService.UsuarioFila> segunda = adminUsuarioService.listarPagina(marca, null, null, 2);
        assertThat(primera.getTotalElements()).isEqualTo(12);
        assertThat(primera.getContent()).hasSize(10);
        assertThat(segunda.getContent()).hasSize(2);

        assertThat(adminUsuarioService.listarPagina(marca, "Project Manager", null, 1).getTotalElements()).isZero();
    }

    @Test
    void laVistaDeUsuariosYaNoCargaElScriptDePaginacion() throws Exception {
        autenticar(usuario("admin", "ADMINISTRADOR"));

        String html = mockMvc.perform(get("/admin/usuarios").param("pagina", "1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContain("admin-table-pagination.js");
        assertThat(html).contains("id=\"filtrosUsuarios\"");
    }

    // ---------- Utilidades ----------

    private Usuario usuario(String prefijo, String nombreRol) {
        Usuario u = new Usuario();
        u.setCorreo(prefijo + "." + UUID.randomUUID() + "@test.local");
        u.setNombre(prefijo);
        u.setApellido("Prueba");
        u.setRol(rol(nombreRol));
        u.setActivo(true);
        u.setHorasContratadasSemana(new BigDecimal("40.00"));
        u.setHorasDisponibles(new BigDecimal("40.00"));
        return usuarioRepository.save(u);
    }

    private Proyecto proyecto(Usuario pm) {
        Proyecto p = new Proyecto();
        p.setNombre("Proyecto mejoras " + System.nanoTime());
        p.setDescripcion("Proyecto de prueba.");
        p.setEstado(EstadoProyecto.ACTIVO);
        p.setPrioridad(Prioridad.MEDIA);
        p.setJustificacionPrioridad("Prueba.");
        p.setColaboradoresRequeridos(2);
        p.setPm(pm);
        p.setPresupuesto(new BigDecimal("10000.00"));
        p.setFechaInicio(LocalDate.now());
        p.setFechaFinEstimada(LocalDate.now().plusMonths(1));
        return proyectoRepository.save(p);
    }

    private Rol rol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol r = new Rol();
            r.setNombre(nombre);
            return rolRepository.save(r);
        });
    }

    private void autenticar(Usuario usuario) {
        UsuarioDetails details = new UsuarioDetails(usuario);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }
}
