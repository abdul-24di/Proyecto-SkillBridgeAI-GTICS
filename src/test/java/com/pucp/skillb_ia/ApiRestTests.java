package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Conversacion;
import com.pucp.skillb_ia.model.Notificacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.model.enums.TipoConversacion;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ConversacionRepository;
import com.pucp.skillb_ia.repository.MensajeRepository;
import com.pucp.skillb_ia.repository.NotificacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.NotificacionService;
import jakarta.servlet.Filter;
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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// La API REST (chat del PM, chat del colaborador y notificaciones) responde sus errores en JSON con su
// código HTTP, en vez de redirigir como las vistas. Sin sesión responde 401.
@SpringBootTest
@ActiveProfiles("test")
class ApiRestTests {

    @Autowired private WebApplicationContext context;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private ConversacionRepository conversacionRepository;
    @Autowired private MensajeRepository mensajeRepository;
    @Autowired private NotificacionRepository notificacionRepository;
    @Autowired private NotificacionService notificacionService;

    private MockMvc mvc;
    private MockMvc mvcConSeguridad;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        mvcConSeguridad = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    // ---------- Sin sesión ----------

    @Test
    void sinSesionLaApiResponde401YLasVistasSiguenRedirigiendoAlLogin() throws Exception {
        mvcConSeguridad.perform(get("/api/notificaciones")).andExpect(status().isUnauthorized());
        mvcConSeguridad.perform(get("/pm/api/chat/mensajes").param("proyectoId", "1"))
                .andExpect(status().isUnauthorized());
        mvcConSeguridad.perform(get("/colaborador/api/chat/mensajes").param("proyectoId", "1"))
                .andExpect(status().isUnauthorized());

        mvcConSeguridad.perform(get("/pm/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ---------- Chat del PM ----------

    @Test
    void elPmRecibe400EnJsonSiConsultaElChatDeUnProyectoAjeno() throws Exception {
        Proyecto ajeno = proyecto(usuario("otro.pm", "PROJECT_MANAGER"));
        autenticar(usuario("pm", "PROJECT_MANAGER"));

        mvc.perform(get("/pm/api/chat/mensajes").param("proyectoId", ajeno.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No eres el PM de este proyecto."));
    }

    @Test
    void parametrosFaltantesOConTipoInvalidoDevuelven400EnJson() throws Exception {
        autenticar(usuario("pm", "PROJECT_MANAGER"));

        mvc.perform(get("/pm/api/chat/mensajes"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Falta el parámetro \"proyectoId\"."));
        mvc.perform(get("/pm/api/chat/mensajes").param("proyectoId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El valor del parámetro \"proyectoId\" no es válido."));
    }

    @Test
    void elPmSigueRecibiendo400SiElMensajeEstaVacio() throws Exception {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Proyecto proyecto = proyecto(pm);
        autenticar(pm);

        mvc.perform(post("/pm/api/chat/enviar")
                        .param("proyectoId", proyecto.getId().toString()).param("contenido", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El mensaje no puede estar vacío."));
    }

    // ---------- Chat del colaborador ----------

    @Test
    void elColaboradorRecibe400SiNoEstaAsignadoAlProyecto() throws Exception {
        Proyecto proyecto = proyecto(usuario("pm", "PROJECT_MANAGER"));
        autenticar(usuario("col", "COLABORADOR"));

        mvc.perform(post("/colaborador/api/chat/enviar")
                        .param("proyectoId", proyecto.getId().toString()).param("contenido", "Hola"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No eres un colaborador activo de este proyecto."));
    }

    @Test
    void elColaboradorRecibe409SiElPmAunNoInicioElChat() throws Exception {
        Usuario colaborador = usuario("col", "COLABORADOR");
        Proyecto proyecto = proyecto(usuario("pm", "PROJECT_MANAGER"));
        asignar(colaborador, proyecto);
        autenticar(colaborador);

        mvc.perform(post("/colaborador/api/chat/enviar")
                        .param("proyectoId", proyecto.getId().toString()).param("contenido", "Hola"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("El chat del proyecto aún no ha sido iniciado por el PM."));
    }

    @Test
    void elChatDelColaboradorTieneLasMismasReglasQueElDelPm() throws Exception {
        Usuario colaborador = usuario("col", "COLABORADOR");
        Proyecto proyecto = proyecto(usuario("pm", "PROJECT_MANAGER"));
        asignar(colaborador, proyecto);
        iniciarChat(proyecto);
        autenticar(colaborador);

        mvc.perform(post("/colaborador/api/chat/enviar")
                        .param("proyectoId", proyecto.getId().toString()).param("contenido", "x".repeat(1001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El mensaje no puede superar los 1000 caracteres."));

        mvc.perform(post("/colaborador/api/chat/enviar")
                        .param("proyectoId", proyecto.getId().toString()).param("contenido", "  Listo, ya subí el avance  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").value("Listo, ya subí el avance"))
                .andExpect(jsonPath("$.esPropio").value(true));
    }

    // ---------- Notificaciones ----------

    @Test
    void marcarLaNotificacionDeOtroUsuarioDevuelve400YNoLaCambia() throws Exception {
        Usuario duenio = usuario("duenio", "COLABORADOR");
        notificacionService.crear(duenio, "PRUEBA", CategoriaNotificacion.SISTEMA,
                "Aviso", "Notificación de prueba.", null, null);
        Notificacion ajena = notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(duenio).get(0);
        autenticar(usuario("intruso", "COLABORADOR"));

        mvc.perform(post("/api/notificaciones/" + ajena.getId() + "/leer"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No se encontró la notificación."));

        assertThat(notificacionRepository.findById(ajena.getId()).orElseThrow().isLeida()).isFalse();
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
        p.setNombre("Proyecto API " + System.nanoTime());
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

    private void asignar(Usuario colaborador, Proyecto proyecto) {
        Asignacion a = new Asignacion();
        a.setProyecto(proyecto);
        a.setColaborador(colaborador);
        a.setHorasSemanales(new BigDecimal("8"));
        a.setOrigen(OrigenAsignacion.PROPUESTA_PM);
        a.setEstado(EstadoAsignacion.ACTIVA);
        a.setAprobadoPorPm(true);
        a.setAprobadoPorRm(true);
        asignacionRepository.save(a);
    }

    private void iniciarChat(Proyecto proyecto) {
        Conversacion c = new Conversacion();
        c.setProyecto(proyecto);
        c.setTipo(TipoConversacion.GRUPAL);
        conversacionRepository.save(c);
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
