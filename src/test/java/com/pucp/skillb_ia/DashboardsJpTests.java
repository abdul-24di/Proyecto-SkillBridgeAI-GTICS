package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Penalizacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.model.enums.TipoPenalizacion;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.PenalizacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.pm.PmDashboardService;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Observaciones del jefe de práctica: dashboard del Admin que lleva a cada sección y strikes en el dashboard del PM.
@SpringBootTest
@ActiveProfiles("test")
class DashboardsJpTests {

    @Autowired private WebApplicationContext context;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private PenalizacionRepository penalizacionRepository;
    @Autowired private PmDashboardService pmDashboardService;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    // ---------- Admin ----------

    @Test
    void lasTarjetasYBarrasDelDashboardDelAdminLlevanASuSeccionFiltrada() throws Exception {
        autenticar(usuario("admin", "ADMINISTRADOR"));

        String html = mvc.perform(get("/admin/dashboard"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String hoy = LocalDate.now().toString();
        assertThat(html).contains("href=\"/admin/usuarios\"");
        assertThat(html).contains("href=\"/admin/usuarios?estado=Pendiente\"");
        assertThat(html).contains("href=\"/admin/habilidades?estado=Activa\"");
        assertThat(html).contains("href=\"/admin/auditoria?desde=" + hoy + "&amp;hasta=" + hoy + "\"");
        assertThat(html).contains("href=\"/admin/usuarios?rol=");
        assertThat(html).contains("class=\"activity-col activity-col-link\"");
    }

    // ---------- PM ----------

    @Test
    void elDashboardDelPmMuestraLosStrikesDeSuEquipoOrdenados() throws Exception {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario conDos = usuario("col.dos", "COLABORADOR");
        Usuario conUno = usuario("col.uno", "COLABORADOR");
        Usuario sinStrikes = usuario("col.cero", "COLABORADOR");
        Proyecto proyecto = proyecto(pm, EstadoProyecto.ACTIVO);
        asignar(conDos, proyecto);
        asignar(conUno, proyecto);
        asignar(sinStrikes, proyecto);
        strike(conDos, proyecto);
        strike(conDos, proyecto);
        strike(conUno, proyecto);

        List<PmDashboardService.StrikeColaborador> filas = pmDashboardService.getStrikesEquipo(pm);
        assertThat(filas).extracting(PmDashboardService.StrikeColaborador::colaboradorId)
                .containsExactly(conDos.getId(), conUno.getId());
        assertThat(filas.get(0).strikes()).isEqualTo(2);
        assertThat(filas.get(0).enLimite()).isFalse();

        autenticar(pm);
        String html = mvc.perform(get("/pm/dashboard"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Strikes del equipo");
        assertThat(html).contains("2 / 3");
        assertThat(html).contains("/pm/actividades?proyectoId=" + proyecto.getId());
    }

    @Test
    void sinStrikesElPanelLoIndicaYNoMuestraProyectosDeOtroPm() throws Exception {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario otroPm = usuario("otro.pm", "PROJECT_MANAGER");
        Usuario colaborador = usuario("col", "COLABORADOR");
        Proyecto ajeno = proyecto(otroPm, EstadoProyecto.ACTIVO);
        asignar(colaborador, ajeno);
        strike(colaborador, ajeno);

        assertThat(pmDashboardService.getStrikesEquipo(pm)).isEmpty();

        autenticar(pm);
        String html = mvc.perform(get("/pm/dashboard"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Ningún colaborador de tus proyectos tiene strikes.");
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

    private Proyecto proyecto(Usuario pm, EstadoProyecto estado) {
        Proyecto p = new Proyecto();
        p.setNombre("Proyecto dashboard " + System.nanoTime());
        p.setDescripcion("Proyecto de prueba.");
        p.setEstado(estado);
        p.setPrioridad(Prioridad.MEDIA);
        p.setJustificacionPrioridad("Prueba.");
        p.setColaboradoresRequeridos(3);
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

    private void strike(Usuario colaborador, Proyecto proyecto) {
        Penalizacion p = new Penalizacion();
        p.setColaborador(colaborador);
        p.setProyecto(proyecto);
        p.setTipo(TipoPenalizacion.ACTIVIDAD_TARDIA);
        p.setMotivo("Entrega tardía.");
        p.setMonto(BigDecimal.ZERO);
        p.setFecha(LocalDateTime.now());
        penalizacionRepository.save(p);
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
