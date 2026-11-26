package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoCertificado;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.model.enums.TipoForo;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.security.UsuarioDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;


@SpringBootTest
@ActiveProfiles("test")
class ColaboradorControllerTests {

    private static final byte[] PNG = new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
    private static final byte[] PDF = "%PDF-1.4 contenido de prueba".getBytes();

    @Autowired private WebApplicationContext context;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private ActividadRepository actividadRepository;
    @Autowired private EducacionRepository educacionRepository;
    @Autowired private ForoRepository foroRepository;
    @Autowired private PublicacionForoRepository publicacionRepository;
    @Autowired private RespuestaForoRepository respuestaRepository;
    @Autowired private VotoPublicacionRepository votoPublicacionRepository;
    @Autowired private VotoRespuestaRepository votoRespuestaRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private MockMvc mvc;
    private Usuario pm;
    private Usuario col;
    private Usuario otroCol;
    private final List<Foro> foros = new ArrayList<>();

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        pm = usuario("pm", "PROJECT_MANAGER");
        col = usuario("col", "COLABORADOR");
        otroCol = usuario("col2", "COLABORADOR");
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        transactionTemplate.executeWithoutResult(estado -> {
            for (Foro foro : foros) {
                foroRepository.borrarVotosDeRespuestas(foro.getId());
                foroRepository.borrarRespuestas(foro.getId());
                foroRepository.borrarVotosDePublicaciones(foro.getId());
                foroRepository.borrarPublicaciones(foro.getId());
                foroRepository.deleteById(foro.getId());
            }
        });
        foros.clear();
    }

    // ======================= Sin sesión =======================

    @Test
    void sinSesionLasPantallasYAccionesRedirigenAlLogin() throws Exception {
        mvc.perform(get("/colaborador/proyectos")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/colaborador/desempeno")).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/colaborador/perfil/sobre-mi").param("descripcion", "Hola"))
                .andExpect(redirectedUrl("/login"));
        mvc.perform(post("/colaborador/cursos/solicitar").param("cursoId", "1"))
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void solicitarIncorporacionPorAjaxSinSesionDevuelve401() throws Exception {
        mvc.perform(post("/colaborador/proyectos/solicitar-ajax").param("proyectoId", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Tu sesión expiró. Vuelve a iniciar sesión."));
    }

    @Test
    void laRaizDelColaboradorRedirigeAlDashboard() throws Exception {
        autenticar(col);

        mvc.perform(get("/colaborador")).andExpect(redirectedUrl("/colaborador/dashboard"));
    }

    // ======================= Proyectos =======================

    @Test
    void laPantallaDeProyectosListaMisAsignaciones() throws Exception {
        Proyecto proyecto = proyecto(pm);
        asignar(col, proyecto, EstadoAsignacion.ACTIVA);
        autenticar(col);

        mvc.perform(get("/colaborador/proyectos"))
                .andExpect(status().isOk())
                .andExpect(view().name("col/col-proyectos"))
                .andExpect(model().attribute("totalAsignaciones", 1))
                .andExpect(model().attribute("estadoFiltro", "Todos"))
                .andExpect(model().attribute("misAsignaciones", hasSize(1)));
    }

    @Test
    void laBusquedaSinCoincidenciasDejaLaListaVaciaPeroConservaElTotal() throws Exception {
        asignar(col, proyecto(pm), EstadoAsignacion.ACTIVA);
        autenticar(col);

        mvc.perform(get("/colaborador/proyectos").param("busqueda", "zzz-no-existe-zzz"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalAsignaciones", 1))
                .andExpect(model().attribute("misAsignaciones", hasSize(0)));
    }

    @Test
    void elDetalleSinAsignacionRedirigeALaListaConUnMensaje() throws Exception {
        autenticar(col);

        mvc.perform(get("/colaborador/proyectos/detalle"))
                .andExpect(redirectedUrl("/colaborador/proyectos"))
                .andExpect(flash().attribute("mensajeError", "No se indicó qué proyecto quieres ver."));
    }

    @Test
    void elDetalleDeLaAsignacionDeOtroColaboradorSeRechaza() throws Exception {
        Asignacion ajena = asignar(otroCol, proyecto(pm), EstadoAsignacion.ACTIVA);
        autenticar(col);

        mvc.perform(get("/colaborador/proyectos/detalle").param("asignacionId", ajena.getId().toString()))
                .andExpect(redirectedUrl("/colaborador/proyectos"))
                .andExpect(flash().attribute("mensajeError", "No tienes acceso a este proyecto."));
    }

    @Test
    void elDetalleDeMiAsignacionActivaSeMuestra() throws Exception {
        Asignacion propia = asignar(col, proyecto(pm), EstadoAsignacion.ACTIVA);
        autenticar(col);

        mvc.perform(get("/colaborador/proyectos/detalle").param("asignacionId", propia.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("col/col-detalle-proyecto"))
                .andExpect(model().attributeExists("proyecto", "asignacion", "integrantes"))
                .andExpect(model().attribute("totalMisActividades", 0L));
    }

    @Test
    void laPantallaDelChatListaSoloMisProyectosActivos() throws Exception {
        asignar(col, proyecto(pm), EstadoAsignacion.ACTIVA);
        asignar(col, proyecto(pm), EstadoAsignacion.FINALIZADA);
        autenticar(col);

        mvc.perform(get("/colaborador/chat"))
                .andExpect(status().isOk())
                .andExpect(view().name("col/col-chat"))
                .andExpect(model().attribute("proyectos", hasSize(1)));
    }

    @Test
    void laPantallaDelAsistenteSeMuestra() throws Exception {
        autenticar(col);

        mvc.perform(get("/colaborador/asistente"))
                .andExpect(status().isOk())
                .andExpect(view().name("col/col-asistente"));
    }

    // ======================= Marcar actividad como lista =======================

    @Test
    void marcarListoSinEvidenciaVuelveAlDetalleYReabreElModal() throws Exception {
        Proyecto proyecto = proyecto(pm);
        Asignacion asignacion = asignar(col, proyecto, EstadoAsignacion.ACTIVA);
        Actividad actividad = actividad(proyecto, col);
        autenticar(col);

        mvc.perform(multipart("/colaborador/proyectos/actividades/{id}/marcar-listo", actividad.getId())
                        .param("asignacionId", asignacion.getId().toString())
                        .param("comentario", "Terminé"))
                .andExpect(redirectedUrl("/colaborador/proyectos/detalle?asignacionId="
                        + asignacion.getId() + "&tab=actividades"))
                .andExpect(flash().attribute("abrirModalActividadId", actividad.getId()));

        assertThat(actividadRepository.findById(actividad.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoActividad.EN_PROGRESO);
    }

    @Test
    void marcarListoConEvidenciaYComentarioPasaLaActividadARevision() throws Exception {
        Proyecto proyecto = proyecto(pm);
        Asignacion asignacion = asignar(col, proyecto, EstadoAsignacion.ACTIVA);
        Actividad actividad = actividad(proyecto, col);
        autenticar(col);

        mvc.perform(multipart("/colaborador/proyectos/actividades/{id}/marcar-listo", actividad.getId())
                        .file(new MockMultipartFile("evidencia", "evidencia.pdf", "application/pdf", PDF))
                        .param("asignacionId", asignacion.getId().toString())
                        .param("comentario", "Todo listo para revisar"))
                .andExpect(redirectedUrl("/colaborador/proyectos/detalle?asignacionId="
                        + asignacion.getId() + "&tab=actividades"))
                .andExpect(flash().attribute("mensajeExito",
                        "Actividad marcada como lista para revisión. El PM la revisará pronto."));

        assertThat(actividadRepository.findById(actividad.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoActividad.EN_REVISION);
    }

    // ======================= Perfil =======================

    @Test
    void sobreMiSeActualizaYUnTextoLargoSeRechaza() throws Exception {
        autenticar(col);

        mvc.perform(post("/colaborador/perfil/sobre-mi").param("descripcion", "Soy backend."))
                .andExpect(redirectedUrl("/colaborador/perfil"))
                .andExpect(flash().attribute("mensajeExito", "Tu descripción se actualizó correctamente."));
        mvc.perform(post("/colaborador/perfil/sobre-mi").param("descripcion", "x".repeat(501)))
                .andExpect(redirectedUrl("/colaborador/perfil"))
                .andExpect(flash().attribute("mensajeError", "La descripción no puede superar los 500 caracteres."));

        assertThat(usuarioRepository.findById(col.getId()).orElseThrow().getDescripcion()).isEqualTo("Soy backend.");
    }

    @Test
    void elTelefonoValidoSeGuardaYElInvalidoSeRechaza() throws Exception {
        autenticar(col);

        mvc.perform(post("/colaborador/perfil/telefono").param("telefono", "987654321"))
                .andExpect(redirectedUrl("/colaborador/perfil"))
                .andExpect(flash().attribute("mensajeExito", "Tu teléfono se actualizó correctamente."));
        mvc.perform(post("/colaborador/perfil/telefono").param("telefono", "123"))
                .andExpect(redirectedUrl("/colaborador/perfil"))
                .andExpect(flash().attributeExists("mensajeError"));

        assertThat(usuarioRepository.findById(col.getId()).orElseThrow().getTelefono()).isEqualTo("987654321");
    }

    @Test
    void laFotoPngSeSubeYUnGifSeRechaza() throws Exception {
        autenticar(col);

        mvc.perform(multipart("/colaborador/perfil/foto")
                        .file(new MockMultipartFile("foto", "foto.png", "image/png", PNG)))
                .andExpect(redirectedUrl("/colaborador/perfil?tab=cuenta"))
                .andExpect(flash().attribute("mensajeExito", "Tu foto de perfil se actualizó correctamente."));
        mvc.perform(multipart("/colaborador/perfil/foto")
                        .file(new MockMultipartFile("foto", "foto.gif", "image/gif", new byte[]{1})))
                .andExpect(redirectedUrl("/colaborador/perfil?tab=cuenta"))
                .andExpect(flash().attribute("mensajeError", "Solo se aceptan imágenes JPG o PNG."));

        assertThat(usuarioRepository.findById(col.getId()).orElseThrow().getFotoUrl()).contains(".png");
    }

    @Test
    void eliminarLaFotoSoloFuncionaSiExiste() throws Exception {
        autenticar(col);

        mvc.perform(post("/colaborador/perfil/foto/eliminar"))
                .andExpect(redirectedUrl("/colaborador/perfil?tab=cuenta"))
                .andExpect(flash().attribute("mensajeError", "No tienes una foto de perfil para eliminar."));

        mvc.perform(multipart("/colaborador/perfil/foto")
                .file(new MockMultipartFile("foto", "foto.png", "image/png", PNG)));
        mvc.perform(post("/colaborador/perfil/foto/eliminar"))
                .andExpect(redirectedUrl("/colaborador/perfil?tab=cuenta"))
                .andExpect(flash().attribute("mensajeExito", "Tu foto de perfil se eliminó correctamente."));

        assertThat(usuarioRepository.findById(col.getId()).orElseThrow().getFotoUrl()).isNull();
    }

    @Test
    void elCvEnPdfSeSubeYUnArchivoDeTextoSeRechaza() throws Exception {
        autenticar(col);

        mvc.perform(multipart("/colaborador/perfil/cv")
                        .file(new MockMultipartFile("cv", "cv.pdf", "application/pdf", PDF)))
                .andExpect(redirectedUrl("/colaborador/perfil?tab=cuenta"))
                .andExpect(flash().attribute("mensajeExito",
                        "Tu CV se subió correctamente y quedó pendiente de revisión del Administrador."));
        mvc.perform(multipart("/colaborador/perfil/cv")
                        .file(new MockMultipartFile("cv", "cv.txt", "text/plain", new byte[]{1})))
                .andExpect(redirectedUrl("/colaborador/perfil?tab=cuenta"))
                .andExpect(flash().attributeExists("mensajeError"));
    }

    @Test
    void cambiarLaContrasenaConUnaActualIncorrectaMuestraElError() throws Exception {
        autenticar(col);

        mvc.perform(post("/colaborador/perfil/password")
                        .param("passwordActual", "NoEsLaActual1!")
                        .param("passwordNueva", "Nueva123!")
                        .param("passwordConfirmar", "Nueva123!"))
                .andExpect(redirectedUrl("/colaborador/perfil?tab=cuenta"))
                .andExpect(flash().attribute("mensajeError", "La contraseña actual no es correcta."));
    }

    @Test
    void eliminarLaFormacionAcademicaDeOtroColaboradorSeRechaza() throws Exception {
        Educacion ajena = educacion(otroCol);
        Educacion propia = educacion(col);
        autenticar(col);

        mvc.perform(post("/colaborador/perfil/educacion/{id}/eliminar", ajena.getId()))
                .andExpect(redirectedUrl("/colaborador/perfil"))
                .andExpect(flash().attribute("mensajeError", "No puedes eliminar una formación académica que no es tuya."));
        mvc.perform(post("/colaborador/perfil/educacion/{id}/eliminar", propia.getId()))
                .andExpect(redirectedUrl("/colaborador/perfil"))
                .andExpect(flash().attribute("mensajeExito", "Formación académica eliminada de tu perfil."));

        assertThat(educacionRepository.findById(ajena.getId()).orElseThrow().isActivo()).isTrue();
        assertThat(educacionRepository.findById(propia.getId()).orElseThrow().isActivo()).isFalse();
    }

    // ======================= Solicitudes =======================

    @Test
    void solicitarUnProyectoInexistenteVuelveAExplorarConError() throws Exception {
        autenticar(col);

        mvc.perform(post("/colaborador/proyectos/solicitar").param("proyectoId", "999999999"))
                .andExpect(redirectedUrl("/colaborador/explorar"))
                .andExpect(flash().attributeExists("mensajeError"));
    }

    @Test
    void solicitarPorAjaxUnProyectoInexistenteDevuelve400ConElMensaje() throws Exception {
        autenticar(col);

        mvc.perform(post("/colaborador/proyectos/solicitar-ajax").param("proyectoId", "999999999"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyString())));
    }

    @Test
    void solicitarUnCursoInexistenteVuelveAlPanelDeCursosConError() throws Exception {
        autenticar(col);

        mvc.perform(post("/colaborador/cursos/solicitar").param("cursoId", "999999999"))
                .andExpect(redirectedUrl("/colaborador/explorar?panel=cursos"))
                .andExpect(flash().attributeExists("mensajeError"));
    }

    // ======================= Foro =======================

    @Test
    void darMeGustaAUnaPublicacionLoAlternaYVuelveAlDetalle() throws Exception {
        Foro comunidad = foroGeneral();
        PublicacionForo publicacion = publicacion(comunidad, otroCol);
        autenticar(col);

        mvc.perform(post("/colaborador/foro/publicacion/{id}/like", publicacion.getId())
                        .param("foroId", comunidad.getId().toString()))
                .andExpect(redirectedUrl("/colaborador/foros/detalle?foroId=" + comunidad.getId()));
        assertThat(votoPublicacionRepository.findByUsuarioAndPublicacion(col, publicacion)).isPresent();

        mvc.perform(post("/colaborador/foro/publicacion/{id}/like", publicacion.getId())
                .param("foroId", comunidad.getId().toString()));
        assertThat(votoPublicacionRepository.findByUsuarioAndPublicacion(col, publicacion)).isEmpty();
    }

    @Test
    void darMeGustaAUnaRespuestaLoAlterna() throws Exception {
        Foro comunidad = foroGeneral();
        PublicacionForo publicacion = publicacion(comunidad, otroCol);
        RespuestaForo respuesta = respuesta(publicacion, otroCol);
        autenticar(col);

        mvc.perform(post("/colaborador/foro/respuesta/{id}/like", respuesta.getId())
                        .param("foroId", comunidad.getId().toString()))
                .andExpect(redirectedUrl("/colaborador/foros/detalle?foroId=" + comunidad.getId()));

        assertThat(votoRespuestaRepository.findByUsuarioAndRespuesta(col, respuesta)).isPresent();
    }

    @Test
    void soloElAutorMarcaLaSolucionDesdeElController() throws Exception {
        Foro comunidad = foroGeneral();
        PublicacionForo publicacion = publicacion(comunidad, otroCol);
        RespuestaForo respuesta = respuesta(publicacion, col);
        autenticar(col);

        mvc.perform(post("/colaborador/foro/respuesta/{id}/marcar-solucion", respuesta.getId())
                        .param("foroId", comunidad.getId().toString()))
                .andExpect(redirectedUrl("/colaborador/foros/detalle?foroId=" + comunidad.getId()))
                .andExpect(flash().attribute("mensajeError",
                        "Solo el autor de la publicación puede marcar una respuesta como solución."));

        autenticar(otroCol);
        mvc.perform(post("/colaborador/foro/respuesta/{id}/marcar-solucion", respuesta.getId())
                        .param("foroId", comunidad.getId().toString()))
                .andExpect(flash().attributeCount(0));

        assertThat(respuestaRepository.findById(respuesta.getId()).orElseThrow().isEsSolucion()).isTrue();
    }

    @Test
    void eliminarPublicacionesYRespuestasDesdeElController() throws Exception {
        Foro comunidad = foroGeneral();
        PublicacionForo propia = publicacion(comunidad, col);
        PublicacionForo ajena = publicacion(comunidad, otroCol);
        RespuestaForo respuesta = respuesta(ajena, col);
        autenticar(col);

        mvc.perform(post("/colaborador/foro/publicacion/{id}/eliminar", propia.getId())
                        .param("foroId", comunidad.getId().toString()))
                .andExpect(redirectedUrl("/colaborador/foros/detalle?foroId=" + comunidad.getId()))
                .andExpect(flash().attribute("mensajeExito", "Publicación eliminada."));
        mvc.perform(post("/colaborador/foro/publicacion/{id}/eliminar", ajena.getId())
                        .param("foroId", comunidad.getId().toString()))
                .andExpect(flash().attribute("mensajeError", "No puedes eliminar una publicación que no es tuya."));
        mvc.perform(post("/colaborador/foro/respuesta/{id}/eliminar", respuesta.getId())
                        .param("foroId", comunidad.getId().toString()))
                .andExpect(flash().attribute("mensajeExito", "Respuesta eliminada."));

        assertThat(publicacionRepository.findById(propia.getId()).orElseThrow().isActivo()).isFalse();
        assertThat(publicacionRepository.findById(ajena.getId()).orElseThrow().isActivo()).isTrue();
        assertThat(respuestaRepository.findById(respuesta.getId()).orElseThrow().isActivo()).isFalse();
    }

    @Test
    void laImagenDelEditorDevuelveSuUrlYUnTipoInvalidoDevuelve400() throws Exception {
        autenticar(col);

        mvc.perform(multipart("/colaborador/foro/upload-imagen")
                        .file(new MockMultipartFile("image", "a.png", "image/png", PNG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(containsString("/uploads/foro/foro-")));
        mvc.perform(multipart("/colaborador/foro/upload-imagen")
                        .file(new MockMultipartFile("image", "a.pdf", "application/pdf", PDF)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Solo se permiten imágenes JPEG, PNG, GIF o WEBP."));
    }

    // ======================= Mi desempeño: descargas =======================

    @Test
    void elReportePdfSeDescargaConSuNombreDeArchivo() throws Exception {
        autenticar(col);

        mvc.perform(get("/colaborador/desempeno/pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"mi-desempeno-" + YearMonth.now() + ".pdf\""));
    }

    @Test
    void elReporteExcelUsaElMesActualSiPidenUnMesAnteriorALaCreacionDelPerfil() throws Exception {
        autenticar(col);

        mvc.perform(get("/colaborador/desempeno/excel").param("mes", "2000-01"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"mi-desempeno-" + YearMonth.now() + ".xlsx\""));
    }

    @Test
    void elReporteConUnMesFuturoOMalEscritoUsaElMesActual() throws Exception {
        autenticar(col);
        String esperado = "attachment; filename=\"mi-desempeno-" + YearMonth.now() + ".pdf\"";

        mvc.perform(get("/colaborador/desempeno/pdf").param("mes", YearMonth.now().plusMonths(3).toString()))
                .andExpect(header().string("Content-Disposition", esperado));
        mvc.perform(get("/colaborador/desempeno/pdf").param("mes", "no-es-un-mes"))
                .andExpect(header().string("Content-Disposition", esperado));
    }

    // ======================= Registro en revisión =======================

    @Test
    void elRegistroEnRevisionAceptaUnCvNuevoEnPdf() throws Exception {
        autenticar(col);

        mvc.perform(multipart("/colaborador/registro/cv")
                        .file(new MockMultipartFile("cv", "cv.pdf", "application/pdf", PDF)))
                .andExpect(redirectedUrl("/colaborador/registro"))
                .andExpect(flash().attribute("mensajeOk", "Tu CV se envió. El Administrador lo revisará pronto."));
    }

    @Test
    void elRegistroEnRevisionRechazaUnCvQueNoEsPdf() throws Exception {
        autenticar(col);

        mvc.perform(multipart("/colaborador/registro/cv")
                        .file(new MockMultipartFile("cv", "cv.txt", "text/plain", new byte[]{1})))
                .andExpect(redirectedUrl("/colaborador/registro"))
                .andExpect(flash().attributeExists("mensajeError"));
    }

    // ======================= Chat REST =======================

    @Test
    void lasCompanerosDelChatSonElPmYLosDemasColaboradoresActivos() throws Exception {
        Proyecto proyecto = proyecto(pm);
        asignar(col, proyecto, EstadoAsignacion.ACTIVA);
        asignar(otroCol, proyecto, EstadoAsignacion.ACTIVA);
        autenticar(col);

        mvc.perform(get("/colaborador/api/chat/companeros").param("proyectoId", proyecto.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].rol").value("Project Manager"))
                .andExpect(jsonPath("$[1].rol").value("Colaborador"))
                .andExpect(jsonPath("$[1].id").value(otroCol.getId()));
    }

    @Test
    void elChatSinProyectoOSinSerMiembroDevuelve400() throws Exception {
        Proyecto ajeno = proyecto(pm);
        autenticar(col);

        mvc.perform(get("/colaborador/api/chat/companeros"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Debes seleccionar un proyecto."));
        mvc.perform(get("/colaborador/api/chat/companeros").param("proyectoId", ajeno.getId().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No eres un colaborador activo de este proyecto."));
    }

    @Test
    void unMensajeGrupalEnviadoApareceEnElHistorialDelGrupo() throws Exception {
        Proyecto proyecto = proyecto(pm);
        asignar(col, proyecto, EstadoAsignacion.ACTIVA);
        autenticar(col);

        mvc.perform(post("/colaborador/api/chat/enviar")
                        .param("proyectoId", proyecto.getId().toString())
                        .param("contenido", "  Hola equipo  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").value("Hola equipo"));
        mvc.perform(get("/colaborador/api/chat/mensajes").param("proyectoId", proyecto.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].contenido").value("Hola equipo"));
    }

    @Test
    void unMensajePrivadoSoloApareceEnLaConversacionConEsaPersona() throws Exception {
        Proyecto proyecto = proyecto(pm);
        asignar(col, proyecto, EstadoAsignacion.ACTIVA);
        asignar(otroCol, proyecto, EstadoAsignacion.ACTIVA);
        autenticar(col);

        mvc.perform(post("/colaborador/api/chat/enviar")
                        .param("proyectoId", proyecto.getId().toString())
                        .param("usuarioId", otroCol.getId().toString())
                        .param("contenido", "Hola en privado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").value("Hola en privado"));
        mvc.perform(get("/colaborador/api/chat/mensajes")
                        .param("proyectoId", proyecto.getId().toString())
                        .param("usuarioId", otroCol.getId().toString()))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/colaborador/api/chat/mensajes").param("proyectoId", proyecto.getId().toString()))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void noSePuedeEscribirseAUnoMismoNiAUnaPersonaAjenaAlProyecto() throws Exception {
        Proyecto proyecto = proyecto(pm);
        asignar(col, proyecto, EstadoAsignacion.ACTIVA);
        Usuario ajeno = usuario("ajeno", "COLABORADOR");
        autenticar(col);

        mvc.perform(post("/colaborador/api/chat/enviar")
                        .param("proyectoId", proyecto.getId().toString())
                        .param("usuarioId", col.getId().toString())
                        .param("contenido", "Hola"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No puedes escribirte a ti mismo."));
        mvc.perform(post("/colaborador/api/chat/enviar")
                        .param("proyectoId", proyecto.getId().toString())
                        .param("usuarioId", ajeno.getId().toString())
                        .param("contenido", "Hola"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Esa persona no es parte de este proyecto."));
    }

    @Test
    void losNoLeidosSeConsultanPorProyecto() throws Exception {
        Proyecto proyecto = proyecto(pm);
        asignar(col, proyecto, EstadoAsignacion.ACTIVA);
        autenticar(col);

        mvc.perform(get("/colaborador/api/chat/noleidos").param("proyectoId", proyecto.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    // ======================= Ayudas =======================

    private void autenticar(Usuario usuario) {
        UsuarioDetails details = new UsuarioDetails(usuario);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    private Foro foroGeneral() {
        Foro foro = new Foro();
        foro.setNombre("Comunidad ctrl " + UUID.randomUUID().toString().substring(0, 8));
        foro.setTipo(TipoForo.GENERAL);
        foro.setEsPublico(true);
        foro = foroRepository.save(foro);
        foros.add(foro);
        return foro;
    }

    private PublicacionForo publicacion(Foro foro, Usuario autor) {
        PublicacionForo p = new PublicacionForo();
        p.setForo(foro);
        p.setAutor(autor);
        p.setTitulo("Publicación " + UUID.randomUUID().toString().substring(0, 6));
        p.setContenido("<p>Contenido</p>");
        p.setFechaCreacion(LocalDateTime.now());
        return publicacionRepository.save(p);
    }

    private RespuestaForo respuesta(PublicacionForo publicacion, Usuario autor) {
        RespuestaForo r = new RespuestaForo();
        r.setPublicacion(publicacion);
        r.setAutor(autor);
        r.setContenido("<p>Respuesta</p>");
        r.setFechaCreacion(LocalDateTime.now());
        return respuestaRepository.save(r);
    }

    private Educacion educacion(Usuario duenio) {
        Educacion e = new Educacion();
        e.setColaborador(duenio);
        e.setInstitucion("PUCP");
        e.setTitulo("Ingeniería");
        e.setEstado(EstadoCertificado.PENDIENTE);
        return educacionRepository.save(e);
    }

    private Actividad actividad(Proyecto proyecto, Usuario colaborador) {
        Actividad a = new Actividad();
        a.setProyecto(proyecto);
        a.setColaborador(colaborador);
        a.setCreadoPor(pm);
        a.setTitulo("Actividad " + System.nanoTime());
        a.setHorasEstimadas(new BigDecimal("4"));
        a.setFechaAsignacion(LocalDateTime.now().minusDays(1));
        a.setFechaLimite(LocalDate.now().plusDays(10));
        a.setEstado(EstadoActividad.EN_PROGRESO);
        return actividadRepository.save(a);
    }

    private Asignacion asignar(Usuario colaborador, Proyecto proyecto, EstadoAsignacion estado) {
        Asignacion a = new Asignacion();
        a.setProyecto(proyecto);
        a.setColaborador(colaborador);
        a.setHorasSemanales(new BigDecimal("8"));
        a.setOrigen(OrigenAsignacion.PROPUESTA_PM);
        a.setEstado(estado);
        a.setAprobadoPorPm(true);
        a.setAprobadoPorRm(true);
        return asignacionRepository.save(a);
    }

    private Proyecto proyecto(Usuario pmProyecto) {
        Proyecto p = new Proyecto();
        p.setNombre("Proyecto ctrl " + System.nanoTime());
        p.setDescripcion("Proyecto de prueba.");
        p.setEstado(EstadoProyecto.ACTIVO);
        p.setPrioridad(Prioridad.MEDIA);
        p.setJustificacionPrioridad("Prueba.");
        p.setColaboradoresRequeridos(3);
        p.setPm(pmProyecto);
        p.setPresupuesto(new BigDecimal("10000.00"));
        p.setFechaInicio(LocalDate.now().minusMonths(1));
        p.setFechaFinEstimada(LocalDate.now().plusMonths(1));
        return proyectoRepository.save(p);
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

    private Rol rol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol r = new Rol();
            r.setNombre(nombre);
            return rolRepository.save(r);
        });
    }
}