package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.model.enums.TipoForo;
import com.pucp.skillb_ia.model.enums.TipoVoto;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.admin.AdminForoService;
import com.pucp.skillb_ia.service.col.ColaboradorForoService;
import com.pucp.skillb_ia.service.pm.PmForoService;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-046: el detalle del foro se ve igual en RM, PM, Colaborador y Admin (contenido saneado,
 * "me gusta", solución y orden) y la solución la marca el autor o quien modera ese foro.
 */
@SpringBootTest
@ActiveProfiles("test")
class ForoDetalleUniformeTests {

    private static final String CONTENIDO = "<p>Texto con <strong>negrita</strong></p>"
            + "<img src=\"https://example.com/diagrama.png\">"
            + "<script>alert('xss')</script>";

    @Autowired private WebApplicationContext context;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private ForoRepository foroRepository;
    @Autowired private PublicacionForoRepository publicacionRepository;
    @Autowired private RespuestaForoRepository respuestaRepository;
    @Autowired private VotoPublicacionRepository votoPublicacionRepository;
    @Autowired private VotoRespuestaRepository votoRespuestaRepository;
    @Autowired private PmForoService pmForoService;
    @Autowired private AdminForoService adminForoService;
    @Autowired private ColaboradorForoService colaboradorForoService;
    @Autowired private TransactionTemplate transactionTemplate;

    private MockMvc mockMvc;
    private Usuario rm;
    private Usuario pm;
    private Usuario otroPm;
    private Usuario admin;
    private Usuario autor;
    private Usuario otroColaborador;
    private Foro comunidad;
    private Foro foroProyecto;
    private final List<Foro> foros = new ArrayList<>();

    @BeforeEach
    void preparar() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        rm = usuario("rm", "RESOURCE_MANAGER");
        pm = usuario("pm", "PROJECT_MANAGER");
        otroPm = usuario("pm2", "PROJECT_MANAGER");
        admin = usuario("admin", "ADMINISTRADOR");
        autor = usuario("autor", "COLABORADOR");
        otroColaborador = usuario("col2", "COLABORADOR");
        comunidad = foro("Comunidad uniforme", null);
        foroProyecto = foro("Foro proyecto uniforme", proyecto(pm));
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

    // ---------- Misma presentación en los 4 roles ----------

    @Test
    void elMismoContenidoSeVeIgualYSinScriptEnLosCuatroRoles() throws Exception {
        PublicacionForo publicacion = publicacion(comunidad, autor, "Duda de despliegue", LocalDateTime.now());
        RespuestaForo respuesta = respuesta(publicacion, otroColaborador, true);
        votoPublicacion(publicacion, otroColaborador);
        votoPublicacion(publicacion, pm);
        votoRespuesta(respuesta, autor);

        for (String html : detallesDeLosCuatroRoles(comunidad)) {
            assertThat(html)
                    .contains("id=\"publicacion-" + publicacion.getId() + "\"")
                    .contains("Texto con <strong>negrita</strong>")
                    .contains("<img src=\"https://example.com/diagrama.png\">")
                    .doesNotContain("alert('xss')", "&lt;p&gt;", "&lt;strong&gt;")
                    .contains("✓ Resuelta")
                    .contains("✓ Solución")
                    .contains("♥ <span>2</span>")
                    .contains("♥ <span>1</span>")
                    .contains("autor Prueba")
                    .contains("Colaborador · ")
                    .contains("Más votadas")
                    .doesNotContain("▲", "Puntaje");
        }
    }

    @Test
    void ordenarPorVotosFuncionaEnLosCuatroRoles() throws Exception {
        PublicacionForo votada = publicacion(comunidad, autor, "Antigua votada", LocalDateTime.now().minusDays(2));
        PublicacionForo reciente = publicacion(comunidad, autor, "Reciente sin votos", LocalDateTime.now().minusHours(1));
        votoPublicacion(votada, otroColaborador);
        String idVotada = "id=\"publicacion-" + votada.getId() + "\"";
        String idReciente = "id=\"publicacion-" + reciente.getId() + "\"";

        for (String html : detallesDeLosCuatroRoles(comunidad, "votos")) {
            assertThat(html.indexOf(idVotada)).isPositive().isLessThan(html.indexOf(idReciente));
            assertThat(html).contains("value=\"votos\" selected=\"selected\"");
        }
        for (String html : detallesDeLosCuatroRoles(comunidad, "fecha")) {
            assertThat(html.indexOf(idReciente)).isPositive().isLessThan(html.indexOf(idVotada));
        }
    }

    @Test
    void cadaRolConservaSusAcciones() throws Exception {
        PublicacionForo publicacion = publicacion(comunidad, autor, "Acciones", LocalDateTime.now());
        RespuestaForo respuesta = respuesta(publicacion, otroColaborador, false);
        String marcar = "/respuesta/" + respuesta.getId() + "/marcar-solucion";

        String rmHtml = html(rm, "/rm/foros/detalle?id=" + comunidad.getId());
        assertThat(rmHtml.substring(rmHtml.indexOf("</header>"))).doesNotContain("method=\"post\"");

        // PM ajeno en comunidad: da "me gusta" y responde, pero no marca la solución ni elimina.
        String pmHtml = html(otroPm, "/pm/foro/detalle?foroId=" + comunidad.getId());
        assertThat(pmHtml).contains("/pm/foro/publicacion/" + publicacion.getId() + "/like")
                .contains("#replyModal" + publicacion.getId())
                .doesNotContain(marcar)
                .doesNotContain("#deletePostModal-" + publicacion.getId());

        // Autor colaborador: marca la solución y elimina su publicación.
        String autorHtml = html(autor, "/colaborador/foros/detalle?foroId=" + comunidad.getId());
        assertThat(autorHtml).contains("/colaborador/foro" + marcar)
                .contains("#deletePostModal-" + publicacion.getId());

        // Otro colaborador: no marca la solución de una publicación ajena.
        assertThat(html(otroColaborador, "/colaborador/foros/detalle?foroId=" + comunidad.getId()))
                .doesNotContain(marcar);

        // Admin en comunidad: modera la solución y elimina.
        assertThat(html(admin, "/admin/foros/" + comunidad.getId() + "/publicaciones"))
                .contains("/admin/foros" + marcar)
                .contains("/admin/foros/respuesta/" + respuesta.getId() + "/eliminar")
                .contains("#deletePostModal-" + publicacion.getId());
    }

    // ---------- Quién marca la solución ----------

    @Test
    void enComunidadSoloElAutorOElAdminMarcanLaSolucion() {
        PublicacionForo publicacion = publicacion(comunidad, autor, "Pregunta", LocalDateTime.now());
        RespuestaForo respuesta = respuesta(publicacion, otroColaborador, false);

        assertThatThrownBy(() -> pmForoService.alternarSolucion(otroPm, respuesta.getId()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> colaboradorForoService.alternarSolucion(otroColaborador, respuesta.getId()))
                .isInstanceOf(IllegalArgumentException.class);

        colaboradorForoService.alternarSolucion(autor, respuesta.getId());
        assertThat(respuestaRepository.findById(respuesta.getId()).orElseThrow().isEsSolucion()).isTrue();

        adminForoService.alternarSolucion(admin, respuesta.getId());
        assertThat(respuestaRepository.findById(respuesta.getId()).orElseThrow().isEsSolucion()).isFalse();
    }

    @Test
    void elPmDeSuPublicacionEnComunidadSiMarcaLaSolucion() {
        PublicacionForo publicacion = publicacion(comunidad, otroPm, "Pregunta del PM", LocalDateTime.now());
        RespuestaForo respuesta = respuesta(publicacion, autor, false);

        pmForoService.alternarSolucion(otroPm, respuesta.getId());

        assertThat(respuestaRepository.findById(respuesta.getId()).orElseThrow().isEsSolucion()).isTrue();
    }

    @Test
    void enElForoDelProyectoModeraSuPmYNoElAdminNiOtroPm() {
        PublicacionForo publicacion = publicacion(foroProyecto, autor, "Bloqueo", LocalDateTime.now());
        RespuestaForo respuesta = respuesta(publicacion, otroColaborador, false);

        assertThatThrownBy(() -> pmForoService.alternarSolucion(otroPm, respuesta.getId()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adminForoService.alternarSolucion(admin, respuesta.getId()))
                .isInstanceOf(IllegalStateException.class);

        pmForoService.alternarSolucion(pm, respuesta.getId());
        assertThat(respuestaRepository.findById(respuesta.getId()).orElseThrow().isEsSolucion()).isTrue();
    }

    @Test
    void marcarOtraRespuestaDesmarcaLaAnterior() {
        PublicacionForo publicacion = publicacion(foroProyecto, autor, "Una sola solución", LocalDateTime.now());
        RespuestaForo primera = respuesta(publicacion, otroColaborador, true);
        RespuestaForo segunda = respuesta(publicacion, pm, false);

        pmForoService.alternarSolucion(pm, segunda.getId());

        assertThat(respuestaRepository.findById(primera.getId()).orElseThrow().isEsSolucion()).isFalse();
        assertThat(respuestaRepository.findById(segunda.getId()).orElseThrow().isEsSolucion()).isTrue();
    }

    // ---------- Ayudas ----------

    private List<String> detallesDeLosCuatroRoles(Foro foro) throws Exception {
        return detallesDeLosCuatroRoles(foro, null);
    }

    private List<String> detallesDeLosCuatroRoles(Foro foro, String orden) throws Exception {
        String sufijo = orden == null ? "" : "&ordenar=" + orden;
        String sufijoAdmin = orden == null ? "" : "?ordenar=" + orden;
        return List.of(
                html(rm, "/rm/foros/detalle?id=" + foro.getId() + sufijo),
                html(pm, "/pm/foro/detalle?foroId=" + foro.getId() + sufijo),
                html(otroColaborador, "/colaborador/foros/detalle?foroId=" + foro.getId() + sufijo),
                html(admin, "/admin/foros/" + foro.getId() + "/publicaciones" + sufijoAdmin));
    }

    private String html(Usuario usuario, String url) throws Exception {
        autenticar(usuario);
        return mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private void autenticar(Usuario usuario) {
        UsuarioDetails details = new UsuarioDetails(usuario);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    private Foro foro(String nombre, Proyecto proyecto) {
        Foro foro = new Foro();
        foro.setNombre(nombre + " " + UUID.randomUUID().toString().substring(0, 8));
        foro.setTipo(proyecto == null ? TipoForo.GENERAL : TipoForo.PROYECTO);
        foro.setEsPublico(proyecto == null);
        foro.setProyecto(proyecto);
        foro = foroRepository.save(foro);
        foros.add(foro);
        return foro;
    }

    private PublicacionForo publicacion(Foro foro, Usuario autorPublicacion, String titulo, LocalDateTime fecha) {
        PublicacionForo publicacion = new PublicacionForo();
        publicacion.setForo(foro);
        publicacion.setAutor(autorPublicacion);
        publicacion.setTitulo(titulo);
        publicacion.setContenido(CONTENIDO);
        publicacion.setFechaCreacion(fecha);
        return publicacionRepository.save(publicacion);
    }

    private RespuestaForo respuesta(PublicacionForo publicacion, Usuario autorRespuesta, boolean solucion) {
        RespuestaForo respuesta = new RespuestaForo();
        respuesta.setPublicacion(publicacion);
        respuesta.setAutor(autorRespuesta);
        respuesta.setContenido("<p>Prueba <em>esto</em></p>");
        respuesta.setEsSolucion(solucion);
        respuesta.setFechaCreacion(LocalDateTime.now());
        return respuestaRepository.save(respuesta);
    }

    private void votoPublicacion(PublicacionForo publicacion, Usuario usuario) {
        VotoPublicacion voto = new VotoPublicacion();
        voto.setId(new VotoPublicacionId(usuario.getId(), publicacion.getId()));
        voto.setUsuario(usuario);
        voto.setPublicacion(publicacion);
        voto.setTipo(TipoVoto.POSITIVO);
        votoPublicacionRepository.save(voto);
    }

    private void votoRespuesta(RespuestaForo respuesta, Usuario usuario) {
        VotoRespuesta voto = new VotoRespuesta();
        voto.setId(new VotoRespuestaId(usuario.getId(), respuesta.getId()));
        voto.setUsuario(usuario);
        voto.setRespuesta(respuesta);
        voto.setTipo(TipoVoto.POSITIVO);
        votoRespuestaRepository.save(voto);
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

    private Proyecto proyecto(Usuario pmProyecto) {
        Proyecto p = new Proyecto();
        p.setNombre("Proyecto foro uniforme " + System.nanoTime());
        p.setDescripcion("Proyecto de prueba.");
        p.setEstado(EstadoProyecto.ACTIVO);
        p.setPrioridad(Prioridad.MEDIA);
        p.setJustificacionPrioridad("Prueba.");
        p.setColaboradoresRequeridos(2);
        p.setPm(pmProyecto);
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
}
