package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoEntrega;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.ActividadRepository;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.AdminReporteService;
import com.pucp.skillb_ia.service.col.ColDesempenoService;
import com.pucp.skillb_ia.service.pm.PmReporteService;
import com.pucp.skillb_ia.util.Periodo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Reportes pedidos por el jefe de práctica: Admin (usuarios y accesos, actividad de auditoría),
// PM (avance del proyecto) y colaborador (mi desempeño), con descarga en Excel.
@SpringBootTest
@ActiveProfiles("test")
class ReportesTests {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired private WebApplicationContext context;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private ActividadRepository actividadRepository;
    @Autowired private AdminReporteService adminReporteService;
    @Autowired private PmReporteService pmReporteService;
    @Autowired private ColDesempenoService colDesempenoService;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    // ---------- Periodo ----------

    @Test
    void elMesDelFiltroVacioMalEscritoOFuturoEsElMesActual() {
        YearMonth actual = YearMonth.now();
        assertThat(Periodo.leer(null)).isEqualTo(actual);
        assertThat(Periodo.leer("noviembre")).isEqualTo(actual);
        assertThat(Periodo.leer(actual.plusMonths(2).toString())).isEqualTo(actual);
        assertThat(Periodo.leer("2026-01")).isEqualTo(YearMonth.of(2026, 1));
    }

    // ---------- Admin ----------

    @Test
    void elAdminVeAmbosReportesYLosDescargaEnExcel() throws Exception {
        autenticar(usuario("admin", "ADMINISTRADOR"));

        assertThat(html("/admin/reportes")).contains("Usuarios por rol y estado").contains("Últimos 6 meses");
        assertThat(html("/admin/reportes?tab=auditoria")).contains("Acciones registradas");

        excel("/admin/reportes/excel?tab=usuarios");
        excel("/admin/reportes/excel?tab=auditoria");
    }

    @Test
    void elReporteDeUsuariosCuentaPorRolYElDeAuditoriaValidaElRango() throws Exception {
        usuario("col.reporte", "COLABORADOR");
        AdminReporteService.ReporteUsuarios r = adminReporteService.usuarios(YearMonth.now());
        assertThat(r.porRol()).anySatisfy(f -> {
            assertThat(f.rol()).isEqualTo("Colaborador");
            assertThat(f.total()).isPositive();
        });
        assertThat(r.historial()).hasSize(AdminReporteService.MESES_HISTORIAL);
        assertThat(r.altas()).isPositive();

        LocalDate hoy = LocalDate.now();
        assertThatThrownBy(() -> adminReporteService.auditoria(hoy, hoy.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adminReporteService.auditoria(hoy.minusYears(2), hoy))
                .isInstanceOf(IllegalArgumentException.class);

        autenticar(usuario("admin", "ADMINISTRADOR"));
        assertThat(html("/admin/reportes?tab=auditoria&desde=" + hoy + "&hasta=" + hoy.minusDays(3)))
                .contains("no puede ser posterior");
    }

    // ---------- PM ----------

    @Test
    void elAvanceDelProyectoCuentaPuntualidadVencidasDevolucionesYHoras() throws Exception {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario colaborador = usuario("col", "COLABORADOR");
        Proyecto proyecto = proyecto(pm);
        asignar(colaborador, proyecto);
        actividad(proyecto, colaborador, pm, EstadoActividad.COMPLETADA, EstadoEntrega.A_TIEMPO, "6", 0,
                LocalDate.now().plusDays(3), LocalDateTime.now());
        actividad(proyecto, colaborador, pm, EstadoActividad.COMPLETADA, EstadoEntrega.TARDIA, "4", 1,
                LocalDate.now().plusDays(2), LocalDateTime.now());
        actividad(proyecto, colaborador, pm, EstadoActividad.PENDIENTE, null, "2", 0,
                LocalDate.now().minusDays(5), null);

        PmReporteService.Avance avance = pmReporteService.avance(proyecto.getId(), pm, null);
        assertThat(avance.total()).isEqualTo(3);
        assertThat(avance.aTiempo()).isEqualTo(1);
        assertThat(avance.tardias()).isEqualTo(1);
        assertThat(avance.puntualidad()).isEqualTo(50);
        assertThat(avance.vencidas()).isEqualTo(1);
        assertThat(avance.devoluciones()).isEqualTo(1);
        assertThat(avance.horasCompletadas()).isEqualByComparingTo("10");
        assertThat(avance.colaboradores()).singleElement().satisfies(c -> assertThat(c.completadas()).isEqualTo(2));

        Usuario otroPm = usuario("otro.pm", "PROJECT_MANAGER");
        assertThatThrownBy(() -> pmReporteService.avance(proyecto.getId(), otroPm, null))
                .isInstanceOf(SecurityException.class);

        autenticar(pm);
        assertThat(html("/pm/reportes/detalle?proyectoId=" + proyecto.getId())).contains("Avance y puntualidad");
        excel("/pm/reportes/excel?proyectoId=" + proyecto.getId());

        // Reporte consolidado del PM (estilo RM) con filtros y exportación Excel personalizable
        assertThat(html("/pm/reportes?todoPeriodo=true&proyecto=" + proyecto.getId()))
                .contains("Reporte de Proyectos y Desempeño")
                .contains("Comparativa por proyecto")
                .contains("Desempeño y puntualidad por colaborador")
                .contains(proyecto.getNombre());
        excel("/pm/reportes/consolidado/excel?todoPeriodo=true&proyecto=" + proyecto.getId());
        excel("/pm/reportes/consolidado/excel?personalizado=true&resumen=true&hojas=proyectos&colProyecto=0&colProyecto=3&colProyecto=8");
    }

    // ---------- Colaborador ----------

    @Test
    void miDesempenoSumaLasHorasDeLoCompletadoEnElMes() throws Exception {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario colaborador = usuario("col", "COLABORADOR");
        Proyecto proyecto = proyecto(pm);
        asignar(colaborador, proyecto);
        actividad(proyecto, colaborador, pm, EstadoActividad.COMPLETADA, EstadoEntrega.A_TIEMPO, "5", 0,
                LocalDate.now().plusDays(1), LocalDateTime.now());
        actividad(proyecto, colaborador, pm, EstadoActividad.COMPLETADA, EstadoEntrega.TARDIA, "3", 0,
                LocalDate.now().minusMonths(2), LocalDateTime.now().minusMonths(2));

        ColDesempenoService.Desempeno d = colDesempenoService.obtener(colaborador, YearMonth.now());
        assertThat(d.horas()).isEqualByComparingTo("5");
        assertThat(d.completadas()).isEqualTo(1);
        assertThat(d.porProyecto()).singleElement().satisfies(f -> assertThat(f.proyecto()).isEqualTo(proyecto.getNombre()));
        assertThat(d.historial()).hasSize(ColDesempenoService.MESES_HISTORIAL);

        autenticar(colaborador);
        assertThat(html("/colaborador/desempeno")).contains("Mi desempeño").contains("Horas por proyecto");
        excel("/colaborador/desempeno/excel");
    }

    // ---------- Utilidades ----------

    private String html(String url) throws Exception {
        return mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private void excel(String url) throws Exception {
        MvcResult r = mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentType(XLSX)).andReturn();
        byte[] archivo = r.getResponse().getContentAsByteArray();
        // Un .xlsx es un ZIP: empieza con "PK".
        assertThat(archivo.length).isGreaterThan(100);
        assertThat(new String(archivo, 0, 2)).isEqualTo("PK");
    }

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
        p.setNombre("Proyecto reporte " + System.nanoTime());
        p.setDescripcion("Proyecto de prueba.");
        p.setEstado(EstadoProyecto.ACTIVO);
        p.setPrioridad(Prioridad.MEDIA);
        p.setJustificacionPrioridad("Prueba.");
        p.setColaboradoresRequeridos(2);
        p.setPm(pm);
        p.setPresupuesto(new BigDecimal("10000.00"));
        p.setFechaInicio(LocalDate.now().minusMonths(3));
        p.setFechaFinEstimada(LocalDate.now().plusMonths(3));
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

    private void actividad(Proyecto proyecto, Usuario colaborador, Usuario pm, EstadoActividad estado,
                           EstadoEntrega entrega, String horas, int devuelta, LocalDate limite, LocalDateTime entregada) {
        Actividad a = new Actividad();
        a.setProyecto(proyecto);
        a.setColaborador(colaborador);
        a.setCreadoPor(pm);
        a.setTitulo("Actividad " + System.nanoTime());
        a.setHorasEstimadas(new BigDecimal(horas));
        a.setFechaAsignacion(LocalDateTime.now().minusMonths(3));
        a.setFechaLimite(limite);
        a.setEstado(estado);
        a.setEstadoEntrega(entrega);
        a.setFechaEntrega(entregada);
        a.setVecesDevuelta(devuelta);
        actividadRepository.save(a);
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
