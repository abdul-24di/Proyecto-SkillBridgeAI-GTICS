package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.*;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.col.ColAsistenteIaClient;
import com.pucp.skillb_ia.service.rm.RmTalentMatchingService;
import com.pucp.skillb_ia.service.rm.RmTalentMatchingService.RecomendacionCandidato;
import org.junit.jupiter.api.AfterEach;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RmTalentMatchingAsistenteTests {

    @Autowired private WebApplicationContext context;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private CategoriaHabilidadRepository categoriaHabilidadRepository;
    @Autowired private HabilidadRepository habilidadRepository;
    @Autowired private ProyectoHabilidadRequeridaRepository proyectoHabilidadRequeridaRepository;
    @Autowired private ColaboradorHabilidadRepository colaboradorHabilidadRepository;
    @Autowired private ConfiguracionSistemaRepository configuracionSistemaRepository;
    @Autowired private RmTalentMatchingService rmTalentMatchingService;
    @MockitoBean private ColAsistenteIaClient iaClient;

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
    void talentMatchingCalculaBrechasHabilidadesYCandidatosEnServidorYAsistenteRmResponde() throws Exception {
        Usuario rm = crearUsuario("rm.tm", "RESOURCE_MANAGER");
        Usuario pm = crearUsuario("pm.tm", "PROJECT_MANAGER");
        Usuario candidato = crearUsuario("CarlosTalento", "COLABORADOR");
        candidato.setNivelExperiencia(NivelExperiencia.SENIOR);
        candidato.setHorasDisponibles(new BigDecimal("28.00"));
        candidato.setHorasContratadasSemana(new BigDecimal("40.00"));
        candidato.setSueldoBase(new BigDecimal("4500.00"));
        usuarioRepository.save(candidato);

        Proyecto proyecto = crearProyecto(pm, rm, "ERP Logístico Cloud", 3);
        Habilidad hab = crearHabilidad("Kubernetes Cluster");
        requerirHabilidad(proyecto, hab, NivelDominio.INTERMEDIO, 2, new BigDecimal("12.00"));
        validarHabilidadColaborador(candidato, hab, NivelDominio.AVANZADO);

        autenticar(rm);
        MockHttpSession session = new MockHttpSession();

        // 1. Vista Talent Matching renderiza en servidor el proyecto, la habilidad faltante y el candidato recomendado
        String html = mvc.perform(get("/rm/talent-matching")
                        .session(session)
                        .param("proyectoId", proyecto.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .contains("ERP Logístico Cloud")
                .contains(hab.getNombre())
                .contains("CarlosTalento Prueba")
                .contains("% match")
                .doesNotContain("Clínica AI");

        // 2. Validación en Java del formulario del Asistente IA del RM
        String htmlError = mvc.perform(post("/rm/talent-matching/asistente/mensaje")
                        .session(session)
                        .param("mensaje", "   ")
                        .param("proyectoId", proyecto.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(htmlError).contains("Escribe tu consulta para el asistente del RM.");

        // 3. El RM consulta al Asistente IA sobre recomendaciones y presupuesto
        mvc.perform(post("/rm/talent-matching/asistente/mensaje")
                        .session(session)
                        .param("proyectoId", proyecto.getId().toString())
                        .param("mensaje", "Recomienda los mejores candidatos para " + proyecto.getNombre() + " y su impacto presupuestal"))
                .andExpect(status().is3xxRedirection());

        String htmlConRespuesta = mvc.perform(get("/rm/talent-matching")
                        .session(session)
                        .param("proyectoId", proyecto.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(htmlConRespuesta)
                .contains("Según SkillBridge (Resource Manager)")
                .contains("CarlosTalento Prueba")
                // "Proponer para el proyecto" abre el modal de propuesta, no la vista antigua.
                .contains("id=\"propuestaModal\"")
                .contains("data-origen=\"talent\"")
                .contains("data-abrir-propuesta")
                .contains("/js/rm-js/rm-propuesta-asignacion.js")
                .doesNotContain("/rm/proyectos/proponer-asignacion");

        // El modal envía con origen "talent" y vuelve a Talent Matching del proyecto.
        mvc.perform(post("/rm/asignaciones/proponer")
                        .session(session)
                        .param("proyectoId", proyecto.getId().toString())
                        .param("colaboradorId", candidato.getId().toString())
                        .param("horasSemanales", "abc")
                        .param("origen", "talent"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rm/talent-matching?proyectoId=" + proyecto.getId()))
                .andExpect(flash().attributeExists("mensajeError"));

        // 4. El RM propone al candidato desde Talent Matching
        mvc.perform(post("/rm/talent-matching/proponer")
                        .session(session)
                        .param("proyectoId", proyecto.getId().toString())
                        .param("colaboradorId", candidato.getId().toString())
                        .param("horasSemanales", "12")
                        .param("justificacion", "Propuesto desde Talent Matching RM"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rm/talent-matching?proyectoId=" + proyecto.getId()))
                .andExpect(flash().attributeExists("mensajeExito"));

        assertThat(asignacionRepository.existsByProyectoAndColaboradorAndEstado(
                proyecto, candidato, EstadoAsignacion.PENDIENTE)).isTrue();
    }

    @Test
    void noRecomiendaAQuienEstaEnElMaximoDeAsignaciones() {
        Usuario rm = crearUsuario("rm.max", "RESOURCE_MANAGER");
        Usuario pm = crearUsuario("pm.max", "PROJECT_MANAGER");
        Usuario lleno = crearUsuario("ColLleno", "COLABORADOR");
        Habilidad hab = crearHabilidad("Java Max");
        validarHabilidadColaborador(lleno, hab, NivelDominio.INTERMEDIO);
        for (int i = 0; i < maxAsignaciones(); i++) {
            crearAsignacionActiva(crearProyecto(pm, rm, "Otro " + i, 5), lleno);
        }
        Proyecto proyecto = crearProyecto(pm, rm, "Proyecto Max", 3);
        requerirHabilidad(proyecto, hab, NivelDominio.INTERMEDIO, 1, new BigDecimal("12.00"));

        assertThat(recomendar(proyecto))
                .extracting(RecomendacionCandidato::colaboradorId)
                .doesNotContain(lleno.getId());
    }

    @Test
    void matchEsUnPorcentajeRealSobreElMaximoPosible() {
        Usuario rm = crearUsuario("rm.pct", "RESOURCE_MANAGER");
        Usuario pm = crearUsuario("pm.pct", "PROJECT_MANAGER");
        Proyecto proyecto = crearProyecto(pm, rm, "Proyecto Pct", 3);
        Habilidad h1 = crearHabilidad("Spring Pct");
        Habilidad h2 = crearHabilidad("SQL Pct");
        requerirHabilidad(proyecto, h1, NivelDominio.INTERMEDIO, 1, new BigDecimal("12.00"));
        requerirHabilidad(proyecto, h2, NivelDominio.INTERMEDIO, 1, new BigDecimal("12.00"));

        Usuario senior = crearUsuario("SeniorPct", "COLABORADOR");
        senior.setNivelExperiencia(NivelExperiencia.SENIOR);
        usuarioRepository.save(senior);
        validarHabilidadColaborador(senior, h1, NivelDominio.INTERMEDIO);
        validarHabilidadColaborador(senior, h2, NivelDominio.INTERMEDIO);
        Usuario junior = crearUsuario("JuniorPct", "COLABORADOR");
        validarHabilidadColaborador(junior, h1, NivelDominio.INTERMEDIO);

        List<RecomendacionCandidato> recs = recomendar(proyecto);

        // Senior: (84 + 22 + 15 + 12) / 139 = 96 %. Junior: (42 + 22 + 15 + 5) / 139 = 60 %.
        assertThat(buscar(recs, senior).matchPorcentaje()).isEqualTo(96);
        assertThat(buscar(recs, junior).matchPorcentaje()).isEqualTo(60);
    }

    @Test
    void recomiendaNivelSuperiorOInferiorConComentario() {
        Usuario rm = crearUsuario("rm.niv", "RESOURCE_MANAGER");
        Usuario pm = crearUsuario("pm.niv", "PROJECT_MANAGER");
        Proyecto proyecto = crearProyecto(pm, rm, "Proyecto Nivel", 3);
        Habilidad hab = crearHabilidad("Docker Nivel");
        requerirHabilidad(proyecto, hab, NivelDominio.INTERMEDIO, 1, new BigDecimal("12.00"));

        Usuario basico = crearUsuario("BasicoNiv", "COLABORADOR");
        validarHabilidadColaborador(basico, hab, NivelDominio.BASICO);
        Usuario avanzado = crearUsuario("AvanzadoNiv", "COLABORADOR");
        validarHabilidadColaborador(avanzado, hab, NivelDominio.AVANZADO);

        List<RecomendacionCandidato> recs = recomendar(proyecto);
        RecomendacionCandidato recBasico = buscar(recs, basico);
        RecomendacionCandidato recAvanzado = buscar(recs, avanzado);

        assertThat(recBasico.notasNivel()).singleElement().asString().contains("nivel inferior");
        assertThat(recBasico.explicacion()).contains("nivel inferior");
        assertThat(recAvanzado.notasNivel()).singleElement().asString().contains("nivel superior");
        assertThat(recs.indexOf(recAvanzado)).isLessThan(recs.indexOf(recBasico));
    }

    @Test
    void proyectoSinVacantesNoSeListaNiRecibeRecomendaciones() throws Exception {
        Usuario rm = crearUsuario("rm.vac", "RESOURCE_MANAGER");
        Usuario pm = crearUsuario("pm.vac", "PROJECT_MANAGER");
        Proyecto lleno = crearProyecto(pm, rm, "Proyecto Completo Vac", 1);
        crearAsignacionActiva(lleno, crearUsuario("IntegranteVac", "COLABORADOR"));
        Habilidad hab = crearHabilidad("Go Vac");
        requerirHabilidad(lleno, hab, NivelDominio.BASICO, 1, new BigDecimal("12.00"));
        validarHabilidadColaborador(crearUsuario("CandidatoVac", "COLABORADOR"), hab, NivelDominio.AVANZADO);

        assertThat(rmTalentMatchingService.construirMatching(null).proyectosConVacantes())
                .extracting(v -> v.getProyecto().getId())
                .doesNotContain(lleno.getId());

        autenticar(rm);
        String html = mvc.perform(get("/rm/talent-matching").param("proyectoId", lleno.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html)
                .contains("Este proyecto no tiene vacantes; no se recomiendan candidatos.")
                .doesNotContain("CandidatoVac Prueba");
    }

    @Test
    void explicacionGeneradaPorIaYRespaldoSiFalla() throws Exception {
        Usuario rm = crearUsuario("rm.ia", "RESOURCE_MANAGER");
        Usuario pm = crearUsuario("pm.ia", "PROJECT_MANAGER");
        Habilidad hab = crearHabilidad("Kafka IA");
        Usuario candidato = crearUsuario("CandidatoIa", "COLABORADOR");
        validarHabilidadColaborador(candidato, hab, NivelDominio.BASICO);
        Proyecto conIa = crearProyecto(pm, rm, "Proyecto Con IA", 3);
        requerirHabilidad(conIa, hab, NivelDominio.INTERMEDIO, 1, new BigDecimal("12.00"));
        Proyecto sinIa = crearProyecto(pm, rm, "Proyecto Sin IA", 3);
        requerirHabilidad(sinIa, hab, NivelDominio.INTERMEDIO, 1, new BigDecimal("12.00"));
        autenticar(rm);

        when(iaClient.estaConfigurado()).thenReturn(true);
        when(iaClient.generar(anyString(), anyList(), anyString()))
                .thenReturn(candidato.getId() + "|Texto IA de prueba: su nivel es inferior al requerido.");
        String html = mvc.perform(get("/rm/talent-matching").param("proyectoId", conIa.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html)
                .contains("Texto IA de prueba: su nivel es inferior al requerido.")
                .contains("sugerencias de IA");
        ArgumentCaptor<String> datos = ArgumentCaptor.forClass(String.class);
        verify(iaClient).generar(anyString(), anyList(), datos.capture());
        assertThat(datos.getValue()).contains("nivel inferior");

        when(iaClient.generar(anyString(), anyList(), anyString())).thenReturn(null);
        String htmlRespaldo = mvc.perform(get("/rm/talent-matching").param("proyectoId", sinIa.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(htmlRespaldo)
                .contains("Cubre las habilidades requeridas")
                .doesNotContain("sugerencias de IA");
    }

    @Test
    void brechasEnLoteCoincidenConElCalculoPorProyecto() {
        Usuario rm = crearUsuario("rm.lote", "RESOURCE_MANAGER");
        Usuario pm = crearUsuario("pm.lote", "PROJECT_MANAGER");
        Habilidad habA = crearHabilidad("Lote A");
        Habilidad habB = crearHabilidad("Lote B");
        Proyecto uno = crearProyecto(pm, rm, "Proyecto Lote Uno", 4);
        requerirHabilidad(uno, habA, NivelDominio.INTERMEDIO, 2, new BigDecimal("10.00"));
        requerirHabilidad(uno, habB, NivelDominio.BASICO, 1, new BigDecimal("8.00"));
        Proyecto dos = crearProyecto(pm, rm, "Proyecto Lote Dos", 2);
        requerirHabilidad(dos, habA, NivelDominio.BASICO, 1, new BigDecimal("6.00"));

        // Integrante sin habilidad solicitada: cubre lo que tenga validado (habA).
        Usuario sinSolicitada = crearUsuario("IntegranteLibre", "COLABORADOR");
        validarHabilidadColaborador(sinSolicitada, habA, NivelDominio.AVANZADO);
        crearAsignacionActiva(uno, sinSolicitada);
        // Integrante con habilidad solicitada: cubre solo esa (habB).
        Usuario conSolicitada = crearUsuario("IntegranteB", "COLABORADOR");
        Asignacion conHab = new Asignacion();
        conHab.setProyecto(uno);
        conHab.setColaborador(conSolicitada);
        conHab.setHabilidadSolicitada(habB);
        conHab.setHorasSemanales(new BigDecimal("4.00"));
        conHab.setOrigen(OrigenAsignacion.PROPUESTA_PM);
        conHab.setEstado(EstadoAsignacion.ACTIVA);
        asignacionRepository.save(conHab);

        var brechas = rmTalentMatchingService.cargarDatosBrechas(List.of(uno.getId(), dos.getId()));
        var candidatos = rmTalentMatchingService.cargarDatosCandidatos();

        for (Proyecto p : List.of(uno, dos)) {
            assertThat(rmTalentMatchingService.calcularHabilidadesFaltantes(p, brechas, candidatos))
                    .containsExactlyInAnyOrderElementsOf(rmTalentMatchingService.calcularHabilidadesFaltantes(p));
        }
        assertThat(rmTalentMatchingService.calcularHabilidadesFaltantes(uno, brechas, candidatos))
                .singleElement()
                .satisfies(hf -> {
                    assertThat(hf.habilidadId()).isEqualTo(habA.getId());
                    assertThat(hf.cubiertos()).isEqualTo(1);
                    assertThat(hf.faltantes()).isEqualTo(1);
                });
        assertThat(rmTalentMatchingService.calcularHabilidadesFaltantes(dos, brechas, candidatos)).hasSize(1);
    }

    private List<RecomendacionCandidato> recomendar(Proyecto proyecto) {
        return rmTalentMatchingService.recomendarParaProyecto(
                proyecto, rmTalentMatchingService.calcularHabilidadesFaltantes(proyecto));
    }

    private static RecomendacionCandidato buscar(List<RecomendacionCandidato> recs, Usuario col) {
        return recs.stream().filter(r -> r.colaboradorId().equals(col.getId())).findFirst()
                .orElseThrow(() -> new AssertionError("No se recomendó a " + col.getNombre()));
    }

    private int maxAsignaciones() {
        return configuracionSistemaRepository.findByClave("MAX_ASIGNACIONES_POR_COLABORADOR")
                .map(c -> Integer.parseInt(c.getValor().trim()))
                .orElse(3);
    }

    private void crearAsignacionActiva(Proyecto proyecto, Usuario col) {
        Asignacion a = new Asignacion();
        a.setProyecto(proyecto);
        a.setColaborador(col);
        a.setHorasSemanales(new BigDecimal("4.00"));
        a.setOrigen(OrigenAsignacion.PROPUESTA_PM);
        a.setEstado(EstadoAsignacion.ACTIVA);
        asignacionRepository.save(a);
    }

    private Usuario crearUsuario(String nombre, String nombreRol) {
        Rol r = rolRepository.findByNombre(nombreRol).orElseGet(() -> {
            Rol nuevo = new Rol();
            nuevo.setNombre(nombreRol);
            return rolRepository.save(nuevo);
        });
        Usuario u = new Usuario();
        u.setCorreo(nombre.toLowerCase() + "." + UUID.randomUUID() + "@test.local");
        u.setNombre(nombre);
        u.setApellido("Prueba");
        u.setRol(r);
        u.setActivo(true);
        u.setHorasContratadasSemana(new BigDecimal("40.00"));
        u.setHorasDisponibles(new BigDecimal("30.00"));
        return usuarioRepository.save(u);
    }

    private Proyecto crearProyecto(Usuario pm, Usuario rm, String nombre, int requeridos) {
        Proyecto p = new Proyecto();
        p.setNombre(nombre);
        p.setDescripcion("Proyecto de prueba para Talent Matching y Asistente IA del RM.");
        p.setEstado(EstadoProyecto.ACTIVO);
        p.setPrioridad(Prioridad.ALTA);
        p.setJustificacionPrioridad("Prioridad alta.");
        p.setColaboradoresRequeridos(requeridos);
        p.setHorasSemanalesRequeridas(new BigDecimal("12.00"));
        p.setPm(pm);
        p.setRmRevisor(rm);
        p.setPresupuesto(new BigDecimal("60000.00"));
        p.setFechaInicio(LocalDate.now().minusWeeks(2));
        p.setFechaFinEstimada(LocalDate.now().plusMonths(3));
        return proyectoRepository.save(p);
    }

    private Habilidad crearHabilidad(String nombre) {
        CategoriaHabilidad cat = categoriaHabilidadRepository.findAll().stream().findFirst().orElseGet(() -> {
            CategoriaHabilidad c = new CategoriaHabilidad();
            c.setNombre("Infra " + UUID.randomUUID());
            return categoriaHabilidadRepository.save(c);
        });
        Habilidad h = new Habilidad();
        h.setNombre(nombre + " " + UUID.randomUUID().toString().substring(0, 5));
        h.setCategoria(cat);
        return habilidadRepository.save(h);
    }

    private void requerirHabilidad(Proyecto p, Habilidad h, NivelDominio nivel, int personas, BigDecimal horas) {
        ProyectoHabilidadRequerida phr = new ProyectoHabilidadRequerida();
        phr.setProyecto(p);
        phr.setHabilidad(h);
        phr.setNivelRequerido(nivel);
        phr.setCantidadPersonas(personas);
        phr.setHorasSemanales(horas);
        proyectoHabilidadRequeridaRepository.save(phr);
    }

    private void validarHabilidadColaborador(Usuario col, Habilidad h, NivelDominio nivel) {
        ColaboradorHabilidad ch = new ColaboradorHabilidad();
        ch.setColaborador(col);
        ch.setHabilidad(h);
        ch.setNivelDominio(nivel);
        ch.setEstadoValidacion(EstadoValidacion.VALIDADA);
        ch.setActivo(true);
        colaboradorHabilidadRepository.save(ch);
    }

    private void autenticar(Usuario u) {
        UsuarioDetails details = new UsuarioDetails(u);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }
}
