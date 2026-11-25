package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.*;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.pm.PmAsistenteContextoService;
import com.pucp.skillb_ia.service.pm.PmAsistenteService;
import com.pucp.skillb_ia.service.rm.RmTalentMatchingService;
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

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PmAsistenteIaTests {

    @Autowired private WebApplicationContext context;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private CategoriaHabilidadRepository categoriaHabilidadRepository;
    @Autowired private HabilidadRepository habilidadRepository;
    @Autowired private ProyectoHabilidadRequeridaRepository proyectoHabilidadRequeridaRepository;
    @Autowired private ColaboradorHabilidadRepository colaboradorHabilidadRepository;
    @Autowired private PmAsistenteContextoService pmAsistenteContextoService;
    @Autowired private RmTalentMatchingService rmTalentMatchingService;

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
    void vistaAsistenteCargaKpisRealesYValidaFormularioEnJava() throws Exception {
        Usuario pm = crearUsuario("pm.ia", "PROJECT_MANAGER");
        Proyecto proyecto = crearProyecto(pm, "Plataforma Cloud IA", 3);
        autenticar(pm);

        String html = mvc.perform(get("/pm/asistente").param("proyectoId", proyecto.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .contains("SkillBridge IA")
                .contains("Plataforma Cloud IA")
                .contains("Analiza el equipo y desempeño de Plataforma Cloud IA");

        // Mensaje vacío es rechazado por @Valid PmAsistenteMensajeForm en Java
        String htmlError = mvc.perform(post("/pm/asistente/mensaje")
                        .param("mensaje", "   ")
                        .param("proyectoId", proyecto.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(htmlError).contains("Escribe tu consulta para el asistente.");
    }

    @Test
    void asistenteRecomiendaCandidatosConHabilidadValidadaYPermiteProponerAlRm() throws Exception {
        Usuario pm = crearUsuario("pm.match", "PROJECT_MANAGER");
        Usuario candidato = crearUsuario("AnaCandidata", "COLABORADOR");
        candidato.setNivelExperiencia(NivelExperiencia.SENIOR);
        candidato.setHorasDisponibles(new BigDecimal("24.00"));
        usuarioRepository.save(candidato);

        Proyecto proyecto = crearProyecto(pm, "Sistema Analítico Financiero", 2);
        Habilidad hab = crearHabilidad("Spring Boot Microservices");
        requerirHabilidad(proyecto, hab, NivelDominio.INTERMEDIO, 2, new BigDecimal("12.00"));
        validarHabilidadColaborador(candidato, hab, NivelDominio.AVANZADO);

        autenticar(pm);
        MockHttpSession session = new MockHttpSession();

        // 1. El PM consulta recomendaciones de candidatos para su proyecto
        mvc.perform(post("/pm/asistente/mensaje").session(session)
                        .param("proyectoId", proyecto.getId().toString())
                        .param("mensaje", "Recomienda candidatos disponibles para proponer a " + proyecto.getNombre()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pm/asistente?proyectoId=" + proyecto.getId()));

        String htmlConversacion = mvc.perform(get("/pm/asistente").session(session)
                        .param("proyectoId", proyecto.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(htmlConversacion)
                .contains("AnaCandidata Prueba")
                .contains("Spring Boot Microservices")
                .contains("Confirmar propuesta al RM");

        // 2. El PM confirma desde la tarjeta del asistente proponer a la candidata al proyecto
        mvc.perform(post("/pm/asistente/proponer").session(session)
                        .param("proyectoId", proyecto.getId().toString())
                        .param("colaboradorId", candidato.getId().toString())
                        .param("horasSemanales", "12")
                        .param("mensajeSolicitud", "Propuesta confirmada desde el Asistente IA")
                        .param("contextoProyectoId", proyecto.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pm/asistente?proyectoId=" + proyecto.getId()))
                .andExpect(flash().attributeExists("success"));

        assertThat(asignacionRepository.existsByProyectoAndColaboradorAndEstado(
                proyecto, candidato, EstadoAsignacion.PENDIENTE)).isTrue();

        // 3. Reiniciar conversación limpia los turnos de la sesión
        mvc.perform(post("/pm/asistente/limpiar").session(session)
                        .param("proyectoId", proyecto.getId().toString()))
                .andExpect(status().is3xxRedirection());

        String htmlLimpio = mvc.perform(get("/pm/asistente").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(htmlLimpio).doesNotContain("Confirmar propuesta al RM");
    }

    @Test
    void asistenteDelPmRecomiendaLosMismosCandidatosYPorcentajesQueTalentMatchingDelRm() {
        Usuario pm = crearUsuario("pm.unificado", "PROJECT_MANAGER");
        Proyecto proyecto = crearProyecto(pm, "Portal Unificado de Talento", 3);
        Habilidad backend = crearHabilidad("Java Backend");
        Habilidad datos = crearHabilidad("Modelado de Datos");
        requerirHabilidad(proyecto, backend, NivelDominio.INTERMEDIO, 2, new BigDecimal("12.00"));
        requerirHabilidad(proyecto, datos, NivelDominio.AVANZADO, 1, new BigDecimal("8.00"));

        Usuario senior = crearUsuario("SeniorUnificado", "COLABORADOR");
        senior.setNivelExperiencia(NivelExperiencia.SENIOR);
        senior.setHorasDisponibles(new BigDecimal("24.00"));
        usuarioRepository.save(senior);
        validarHabilidadColaborador(senior, backend, NivelDominio.AVANZADO);
        validarHabilidadColaborador(senior, datos, NivelDominio.AVANZADO);

        Usuario junior = crearUsuario("JuniorUnificado", "COLABORADOR");
        junior.setNivelExperiencia(NivelExperiencia.JUNIOR);
        junior.setHorasDisponibles(new BigDecimal("6.00"));
        usuarioRepository.save(junior);
        validarHabilidadColaborador(junior, backend, NivelDominio.BASICO);

        Usuario intermedio = crearUsuario("IntermedioUnificado", "COLABORADOR");
        intermedio.setNivelExperiencia(NivelExperiencia.SEMI_SENIOR);
        intermedio.setHorasDisponibles(new BigDecimal("12.00"));
        usuarioRepository.save(intermedio);
        validarHabilidadColaborador(intermedio, datos, NivelDominio.INTERMEDIO);

        PmAsistenteContextoService.ContextoPm contexto = pmAsistenteContextoService.construir(
                pm.getId(), proyecto.getId(), "Recomienda candidatos para " + proyecto.getNombre());
        List<RmTalentMatchingService.RecomendacionCandidato> delRm = rmTalentMatchingService.recomendarParaProyecto(
                proyecto, rmTalentMatchingService.calcularHabilidadesFaltantes(proyecto));

        List<CandidatoTarjeta> delPm = List.copyOf(contexto.candidatos().values());
        assertThat(delRm).isNotEmpty();
        assertThat(delPm).extracting(CandidatoTarjeta::colaboradorId)
                .containsExactlyElementsOf(delRm.stream()
                        .map(RmTalentMatchingService.RecomendacionCandidato::colaboradorId).toList());
        assertThat(delPm).extracting(CandidatoTarjeta::puntajeAfinidad)
                .containsExactlyElementsOf(delRm.stream()
                        .map(RmTalentMatchingService.RecomendacionCandidato::matchPorcentaje).toList());
        assertThat(delPm).extracting(CandidatoTarjeta::colaboradorId)
                .contains(senior.getId(), junior.getId(), intermedio.getId());

        // Decisión S18: el PM ve el ranking, pero nunca el costo estimado del candidato.
        String texto = contexto.textoPrompt();
        String seccionCandidatos = texto.substring(texto.indexOf("## CANDIDATOS RECOMENDADOS"),
                texto.indexOf("## BÚSQUEDA EN LOS FOROS"));
        assertThat(seccionCandidatos).doesNotContainIgnoringCase("costo").doesNotContain("S/");
        assertThat(contexto.resumenAnaliticoLocal()).doesNotContainIgnoringCase("costo");
    }

    @Test
    void proyectoSinVacantesNoRecibeCandidatosComoEnTalentMatching() {
        Usuario pm = crearUsuario("pm.completo", "PROJECT_MANAGER");
        Proyecto proyecto = crearProyecto(pm, "Proyecto Completo", 1);
        Habilidad hab = crearHabilidad("Kotlin");
        requerirHabilidad(proyecto, hab, NivelDominio.BASICO, 1, new BigDecimal("10.00"));

        Usuario integrante = crearUsuario("IntegranteActivo", "COLABORADOR");
        Asignacion activa = new Asignacion();
        activa.setProyecto(proyecto);
        activa.setColaborador(integrante);
        activa.setEstado(EstadoAsignacion.ACTIVA);
        activa.setOrigen(OrigenAsignacion.PROPUESTA_PM);
        activa.setHorasSemanales(new BigDecimal("10.00"));
        asignacionRepository.save(activa);

        Usuario libre = crearUsuario("CandidatoLibre", "COLABORADOR");
        validarHabilidadColaborador(libre, hab, NivelDominio.AVANZADO);

        PmAsistenteContextoService.ContextoPm contexto = pmAsistenteContextoService.construir(
                pm.getId(), proyecto.getId(), "Recomienda candidatos");

        assertThat(contexto.candidatos()).isEmpty();
    }

    @Test
    void confirmarPropuestaCandidatoEsTransaccional() throws NoSuchMethodException {
        Method metodo = PmAsistenteService.class.getMethod("confirmarPropuestaCandidato",
                Long.class, Long.class, Long.class, BigDecimal.class, String.class);
        org.springframework.transaction.annotation.Transactional tx =
                metodo.getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertThat(tx).isNotNull();
        assertThat(tx.readOnly()).isFalse();
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

    private Proyecto crearProyecto(Usuario pm, String nombre, int requeridos) {
        Proyecto p = new Proyecto();
        p.setNombre(nombre);
        p.setDescripcion("Proyecto de prueba para Asistente IA del PM.");
        p.setEstado(EstadoProyecto.ACTIVO);
        p.setPrioridad(Prioridad.ALTA);
        p.setJustificacionPrioridad("Prioridad alta.");
        p.setColaboradoresRequeridos(requeridos);
        p.setHorasSemanalesRequeridas(new BigDecimal("12.00"));
        p.setPm(pm);
        p.setPresupuesto(new BigDecimal("25000.00"));
        p.setFechaInicio(LocalDate.now().minusWeeks(2));
        p.setFechaFinEstimada(LocalDate.now().plusMonths(2));
        return proyectoRepository.save(p);
    }

    private Habilidad crearHabilidad(String nombre) {
        CategoriaHabilidad cat = categoriaHabilidadRepository.findAll().stream().findFirst().orElseGet(() -> {
            CategoriaHabilidad c = new CategoriaHabilidad();
            c.setNombre("Backend " + UUID.randomUUID());
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
