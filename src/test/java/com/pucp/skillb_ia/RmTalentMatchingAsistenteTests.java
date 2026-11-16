package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.*;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.security.UsuarioDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
                .contains("CarlosTalento Prueba");

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
