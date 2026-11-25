package com.pucp.skillb_ia;

import com.pucp.skillb_ia.dto.PmForoView;
import com.pucp.skillb_ia.model.Foro;
import com.pucp.skillb_ia.model.LogAuditoria;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.PublicacionForo;
import com.pucp.skillb_ia.model.RespuestaForo;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.model.enums.TipoForo;
import com.pucp.skillb_ia.repository.ForoRepository;
import com.pucp.skillb_ia.repository.LogAuditoriaRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.PublicacionForoRepository;
import com.pucp.skillb_ia.repository.RespuestaForoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.admin.AdminForoService;
import com.pucp.skillb_ia.service.col.ColaboradorForoService;
import com.pucp.skillb_ia.service.pm.PmForoService;
import com.pucp.skillb_ia.util.Paginador;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

// Arreglos de la revisión hecha sobre la aplicación desplegada (24/11/2026).
@SpringBootTest
@ActiveProfiles("test")
class RevisionNubeTests {

    @Autowired private WebApplicationContext context;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private ForoRepository foroRepository;
    @Autowired private PublicacionForoRepository publicacionRepository;
    @Autowired private RespuestaForoRepository respuestaRepository;
    @Autowired private LogAuditoriaRepository logAuditoriaRepository;
    @Autowired private PmForoService pmForoService;
    @Autowired private AdminForoService adminForoService;
    @Autowired private ColaboradorForoService colaboradorForoService;
    @Autowired private TransactionTemplate transactionTemplate;

    private MockMvc mvc;
    private Usuario pm;
    private Usuario otroPm;
    private Usuario admin;
    private final List<Foro> foros = new ArrayList<>();

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        pm = usuario("pm", "PROJECT_MANAGER");
        otroPm = usuario("pm2", "PROJECT_MANAGER");
        admin = usuario("admin", "ADMINISTRADOR");
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        transactionTemplate.executeWithoutResult(estado -> foros.forEach(foro -> {
            foroRepository.borrarVotosDeRespuestas(foro.getId());
            foroRepository.borrarRespuestas(foro.getId());
            foroRepository.borrarVotosDePublicaciones(foro.getId());
            foroRepository.borrarPublicaciones(foro.getId());
            foroRepository.deleteById(foro.getId());
        }));
        foros.clear();
    }

    // ---------- PM ----------

    @Test
    void actividadesSinProyectoVuelveALaListaDeProyectos() throws Exception {
        autenticar(pm);
        mvc.perform(get("/pm/actividades")).andExpect(redirectedUrl("/pm/proyectos"));
    }

    @Test
    void otroPmRespondeEnElForoPublicoDeUnProyectoPeroNoEnElPrivado() {
        Foro publico = foro(proyecto(pm, EstadoProyecto.ACTIVO), true);
        Foro privado = foro(proyecto(pm, EstadoProyecto.ACTIVO), false);
        PublicacionForo enPublico = pmForoService.publicarEnForo(publico.getId(), "Tema", "<p>Hola</p>", otroPm);
        PublicacionForo enPrivado = pmForoService.publicarEnForo(privado.getId(), "Tema", "<p>Hola</p>", pm);

        RespuestaForo respuesta = pmForoService.responder(enPublico.getId(), "<p>Respuesta</p>", otroPm);

        assertThat(respuestaRepository.findById(respuesta.getId())).isPresent();
        assertThat(acciones(otroPm)).contains("CREAR_RESPUESTA_FORO");
        assertThatThrownBy(() -> pmForoService.responder(enPrivado.getId(), "<p>Respuesta</p>", otroPm))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void enElForoDeAnunciosSoloPublicaElAdminYLosDemasLeenYReaccionan() throws Exception {
        Foro anuncios = adminForoService.crearForo("Avisos " + UUID.randomUUID(), false, admin);
        foros.add(anuncios);
        adminForoService.publicar(anuncios.getId(), "Aviso importante", "Texto del aviso", admin);
        PublicacionForo aviso = publicacionRepository.findByForoAndActivoTrueOrderByFechaCreacionDesc(anuncios).get(0);
        Usuario rm = usuario("rm", "RESOURCE_MANAGER");
        Usuario col = usuario("col", "COLABORADOR");

        // Todos lo leen, sin botones de publicar ni responder.
        String[] urls = {"/pm/foro/detalle?foroId=", "/rm/foros/detalle?id=", "/colaborador/foros/detalle?foroId="};
        Usuario[] lectores = {pm, rm, col};
        for (int i = 0; i < urls.length; i++) {
            autenticar(lectores[i]);
            assertThat(html(urls[i] + anuncios.getId()))
                    .contains("Aviso importante")
                    .contains("/publicacion/" + aviso.getId() + "/like")
                    .doesNotContain("#newPostModal", "#replyModal", "Esta publicación aún no tiene respuestas");
        }
        autenticar(pm);
        assertThat(html("/pm/foros")).contains(anuncios.getNombre());
        autenticar(col);
        assertThat(html("/colaborador/foros?busqueda=" + anuncios.getNombre())).contains(anuncios.getNombre());

        // Reaccionan.
        pmForoService.alternarLikePublicacion(pm, aviso.getId());
        colaboradorForoService.alternarLikePublicacion(col, aviso.getId());
        autenticar(rm);
        mvc.perform(post("/rm/foros/publicacion/" + aviso.getId() + "/like").param("foroId", anuncios.getId().toString()));
        assertThat(html("/rm/foros/detalle?id=" + anuncios.getId())).contains("♥ <span>3</span>");

        // El servidor bloquea publicar y responder aunque se fuerce la petición.
        assertThatThrownBy(() -> pmForoService.publicarEnForo(anuncios.getId(), "Tema", "<p>Hola</p>", pm))
                .isInstanceOf(SecurityException.class).hasMessageContaining("foro de anuncios");
        assertThatThrownBy(() -> pmForoService.responder(aviso.getId(), "<p>Hola</p>", rm))
                .isInstanceOf(SecurityException.class).hasMessageContaining("foro de anuncios");
        assertThatThrownBy(() -> colaboradorForoService.crearPublicacion(col, anuncios.getId(), "Tema", "<p>Hola</p>", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("foro de anuncios");
        assertThatThrownBy(() -> colaboradorForoService.crearRespuesta(col, aviso.getId(), "<p>Hola</p>"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("foro de anuncios");
        assertThat(colaboradorForoService.listarForosParaPublicar(col)).extracting(Foro::getId)
                .doesNotContain(anuncios.getId());

        autenticar(admin);
        assertThat(html("/admin/foros/" + anuncios.getId() + "/publicaciones"))
                .contains("+ Nuevo anuncio", "Aviso importante").doesNotContain("#replyModal");
    }

    @Test
    void elRmPublicaRespondeYReaccionaEnElForoDeComunidad() throws Exception {
        Foro comunidad = adminForoService.crearForo("Comunidad " + UUID.randomUUID(), true, admin);
        foros.add(comunidad);
        Usuario rm = usuario("rm", "RESOURCE_MANAGER");
        autenticar(rm);
        String id = comunidad.getId().toString();

        mvc.perform(post("/rm/foros/publicar").param("foroId", id).param("titulo", "Tema del RM").param("contenido", "<p>Hola</p>"))
                .andExpect(redirectedUrl("/rm/foros/detalle?id=" + id));
        PublicacionForo pub = publicacionRepository.findByForoAndActivoTrueOrderByFechaCreacionDesc(comunidad).get(0);
        mvc.perform(post("/rm/foros/responder").param("foroId", id).param("publicacionId", pub.getId().toString())
                .param("contenido", "<p>Respuesta del RM</p>"));
        RespuestaForo respuesta = respuestaRepository.findByPublicacionConAutor(pub).get(0);
        mvc.perform(post("/rm/foros/respuesta/" + respuesta.getId() + "/marcar-solucion").param("foroId", id));
        mvc.perform(post("/rm/foros/respuesta/" + respuesta.getId() + "/like").param("foroId", id));

        assertThat(html("/rm/foros/detalle?id=" + id))
                .contains("Tema del RM", "Respuesta del RM", "✓ Solución", "#newPostModal", "#deletePostModal-" + pub.getId());

        mvc.perform(post("/rm/foros/respuesta/" + respuesta.getId() + "/like").param("foroId", id));
        mvc.perform(post("/rm/foros/respuesta/" + respuesta.getId() + "/eliminar").param("foroId", id));
        mvc.perform(post("/rm/foros/publicacion/" + pub.getId() + "/eliminar").param("foroId", id));
        assertThat(publicacionRepository.findByForoAndActivoTrueOrderByFechaCreacionDesc(comunidad)).isEmpty();
        assertThat(acciones(rm)).contains("PUBLICAR", "CREAR_RESPUESTA_FORO", "ELIMINAR_PUBLICACION_FORO", "ELIMINAR_RESPUESTA_FORO");
    }

    @Test
    void elRmNoEliminaLoAjenoNiParticipaEnLosForosDeProyecto() throws Exception {
        Usuario rm = usuario("rm", "RESOURCE_MANAGER");
        Foro publico = foro(proyecto(pm, EstadoProyecto.ACTIVO), true);
        PublicacionForo pub = pmForoService.publicarEnForo(publico.getId(), "Tema del PM", "<p>Hola</p>", pm);
        autenticar(rm);

        assertThatThrownBy(() -> pmForoService.publicarEnForo(publico.getId(), "Tema", "<p>Hola</p>", rm))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> pmForoService.responder(pub.getId(), "<p>Hola</p>", rm)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> pmForoService.alternarLikePublicacion(rm, pub.getId())).isInstanceOf(SecurityException.class);
        mvc.perform(post("/rm/foros/publicacion/" + pub.getId() + "/eliminar").param("foroId", publico.getId().toString()));
        assertThat(publicacionRepository.findById(pub.getId()).orElseThrow().isActivo()).isTrue();
    }

    @Test
    void alPublicarConErroresElRmVuelveAlModalConLoEscrito() throws Exception {
        Foro comunidad = adminForoService.crearForo("Comunidad " + UUID.randomUUID(), true, admin);
        foros.add(comunidad);
        autenticar(usuario("rm", "RESOURCE_MANAGER"));

        var flash = mvc.perform(post("/rm/foros/publicar").param("foroId", comunidad.getId().toString())
                .param("titulo", " ").param("contenido", "<p>Texto escrito</p>")).andReturn().getFlashMap();
        var peticion = get("/rm/foros/detalle?id=" + comunidad.getId());
        flash.forEach(peticion::flashAttr);
        assertThat(mvc.perform(peticion).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("El título de la publicación es obligatorio.", "Texto escrito");
    }

    @Test
    void misProyectosListaLosActivosQueAunNoTienenForo() throws Exception {
        Proyecto activo = proyecto(pm, EstadoProyecto.ACTIVO);
        proyecto(pm, EstadoProyecto.EN_REVISION);
        Proyecto conForo = proyecto(pm, EstadoProyecto.ACTIVO);
        foro(conForo, false);

        assertThat(pmForoService.listarForosPm(pm)).extracting(PmForoView::getProyectoId)
                .containsExactlyInAnyOrder(activo.getId(), conForo.getId());

        autenticar(pm);
        assertThat(html("/pm/foros?tab=mios"))
                .contains(activo.getNombre())
                .contains("/pm/foro/detalle?proyectoId=" + activo.getId())
                .doesNotContain("No tienes proyectos activos con foros asignados");
        mvc.perform(get("/pm/foro/detalle?proyectoId=" + activo.getId()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));
    }

    @Test
    void elErrorDeUnModalDelForoSeMuestraUnaSolaVez() throws Exception {
        Foro privado = foro(proyecto(pm, EstadoProyecto.ACTIVO), false);
        PublicacionForo publicacion = pmForoService.publicarEnForo(privado.getId(), "Tema", "<p>Hola</p>", pm);
        autenticar(pm);

        String conModal = mvc.perform(get("/pm/foro/detalle?foroId=" + privado.getId())
                        .flashAttr("error", "Mensaje de prueba")
                        .flashAttr("abrirModalResponderId", publicacion.getId()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String sinModal = mvc.perform(get("/pm/foro/detalle?foroId=" + privado.getId())
                        .flashAttr("error", "Mensaje de prueba"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(conModal).containsOnlyOnce("Mensaje de prueba");
        assertThat(sinModal).containsOnlyOnce("Mensaje de prueba");
    }

    // ---------- Admin ----------

    @Test
    void lasAccionesDelAdminEnLosForosQuedanEnLaAuditoria() throws Exception {
        autenticar(admin);
        String nombre = "Foro auditado " + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(post("/admin/foros/crear").param("nombre", nombre));
        Foro foro = foroRepository.findAll().stream()
                .filter(f -> f.getNombre().equals(nombre)).findFirst().orElseThrow();
        mvc.perform(post("/admin/foros/" + foro.getId() + "/publicar")
                .param("titulo", "Anuncio").param("contenido", "Hola"));
        PublicacionForo pub = publicacionRepository.findByForoAndActivoTrueOrderByFechaCreacionDesc(foro).get(0);
        mvc.perform(post("/admin/foros/responder").param("publicacionId", pub.getId().toString())
                .param("foroId", foro.getId().toString()).param("contenido", "Respuesta"));
        RespuestaForo respuesta = respuestaRepository.findByPublicacionConAutor(pub).get(0);
        mvc.perform(post("/admin/foros/respuesta/" + respuesta.getId() + "/eliminar")
                .param("foroId", foro.getId().toString()));
        mvc.perform(post("/admin/foros/publicacion/" + pub.getId() + "/eliminar")
                .param("foroId", foro.getId().toString()));
        mvc.perform(post("/admin/foros/" + foro.getId() + "/eliminar"));

        assertThat(acciones(admin)).contains("CREAR_FORO", "CREAR_PUBLICACION_FORO", "CREAR_RESPUESTA_FORO",
                "ELIMINAR_RESPUESTA_FORO", "ELIMINAR_PUBLICACION_FORO", "ELIMINAR_FORO");
        assertThat(html("/admin/auditoria")).contains("Eliminó foro");
    }

    // ---------- Colaborador ----------

    @Test
    void ajustesDeCuentaMuestraLaFotoDelColaborador() throws Exception {
        Usuario colaborador = usuario("colfoto", "COLABORADOR");
        colaborador.setFotoUrl("/uploads/fotos/prueba.png");
        usuarioRepository.save(colaborador);
        autenticar(colaborador);

        String html = html("/colaborador/perfil");

        assertThat(html.substring(html.indexOf("id=\"tab-cuenta\"")))
                .contains("<img src=\"/uploads/fotos/prueba.png\" class=\"avatar avatar-xl mb-4 rounded\"");
    }

    // ---------- Categorías de cursos ----------

    @Test
    void lasCategoriasConYSinTildeSonUnaSolaOpcionYFiltranIgual() {
        assertThat(Paginador.opcionesUnicas(Arrays.asList(
                "Tecnico", "Técnico", "Certificación", "Certificacion", null, " ", "Blandas")))
                .containsExactly("Blandas", "Certificación", "Técnico");
        assertThat(Paginador.coincideSinTildes("Tecnico", "Técnico")).isTrue();
        assertThat(Paginador.coincideSinTildes("Técnico", "tecnico")).isTrue();
        assertThat(Paginador.coincideSinTildes("Técnico", "Blandas")).isFalse();
        assertThat(Paginador.coincideSinTildes(null, "Blandas")).isFalse();
        assertThat(Paginador.coincideSinTildes("Técnico", "")).isTrue();
        assertThat(Paginador.coincideSinTildes("Técnico", null)).isTrue();
    }

    // ---------- Ayudas ----------

    private List<String> acciones(Usuario usuario) {
        return logAuditoriaRepository.findAllConUsuario().stream()
                .filter(l -> l.getUsuario() != null && l.getUsuario().getId().equals(usuario.getId()))
                .map(LogAuditoria::getAccion).toList();
    }

    private String html(String url) throws Exception {
        return mvc.perform(get(url)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private void autenticar(Usuario usuario) {
        UsuarioDetails details = new UsuarioDetails(usuario);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    private Foro foro(Proyecto proyecto, boolean publico) {
        Foro foro = new Foro();
        foro.setNombre("Foro " + UUID.randomUUID());
        foro.setTipo(TipoForo.PROYECTO);
        foro.setEsPublico(publico);
        foro.setProyecto(proyecto);
        foro = foroRepository.save(foro);
        foros.add(foro);
        return foro;
    }

    private Usuario usuario(String prefijo, String nombreRol) {
        Usuario u = new Usuario();
        u.setCorreo(prefijo + "." + UUID.randomUUID() + "@test.local");
        u.setNombre("Prueba");
        u.setApellido("Nube");
        u.setRol(rolRepository.findByNombre(nombreRol).orElseGet(() -> {
            Rol r = new Rol();
            r.setNombre(nombreRol);
            return rolRepository.save(r);
        }));
        u.setActivo(true);
        u.setHorasContratadasSemana(new BigDecimal("40.00"));
        u.setHorasDisponibles(new BigDecimal("40.00"));
        return usuarioRepository.save(u);
    }

    private Proyecto proyecto(Usuario pmProyecto, EstadoProyecto estado) {
        Proyecto p = new Proyecto();
        p.setNombre("Proyecto nube " + System.nanoTime());
        p.setDescripcion("Proyecto de prueba.");
        p.setEstado(estado);
        p.setPrioridad(Prioridad.MEDIA);
        p.setJustificacionPrioridad("Prueba.");
        p.setColaboradoresRequeridos(2);
        p.setPm(pmProyecto);
        p.setPresupuesto(new BigDecimal("10000.00"));
        p.setFechaInicio(LocalDate.now());
        p.setFechaFinEstimada(LocalDate.now().plusMonths(1));
        return proyectoRepository.save(p);
    }
}
