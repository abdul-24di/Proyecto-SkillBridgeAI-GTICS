package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.MotivoFinalizacion;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.model.enums.TipoForo;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.service.col.ColaboradorForoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@SpringBootTest
@ActiveProfiles("test")
class ColaboradorForoTests {

    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private ForoRepository foroRepository;
    @Autowired private PublicacionForoRepository publicacionRepository;
    @Autowired private RespuestaForoRepository respuestaRepository;
    @Autowired private EtiquetaRepository etiquetaRepository;
    @Autowired private VotoPublicacionRepository votoPublicacionRepository;
    @Autowired private VotoRespuestaRepository votoRespuestaRepository;
    @Autowired private NotificacionRepository notificacionRepository;
    @Autowired private LogAuditoriaRepository logAuditoriaRepository;
    @Autowired private ColaboradorForoService foroService;
    @Autowired private TransactionTemplate transactionTemplate;

    private Usuario pm;
    private Usuario col;
    private Usuario otroCol;
    private Usuario admin;
    private Foro comunidad;
    private Proyecto proyecto;
    private Foro foroProyecto;
    private final List<Foro> foros = new ArrayList<>();

    @BeforeEach
    void preparar() {
        pm = usuario("pm", "PROJECT_MANAGER");
        col = usuario("col", "COLABORADOR");
        otroCol = usuario("col2", "COLABORADOR");
        admin = usuario("admin", "ADMINISTRADOR");
        comunidad = foro("Comunidad col", null);
        proyecto = proyecto(pm);
        foroProyecto = foro("Foro proyecto col", proyecto);
    }

    @AfterEach
    void limpiar() {
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

    // ======================= Acceso a foros =======================

    @Test
    void losForosDeComunidadSoloIncluyenLosPublicos() {
        List<Foro> lista = foroService.listarForosComunidad();

        assertThat(contieneForo(lista, comunidad)).isTrue();
        assertThat(contieneForo(lista, foroProyecto)).isFalse();
    }

    @Test
    void soloVeLosForosDeProyectosConAsignacionActiva() {
        Proyecto otroProyecto = proyecto(pm);
        Foro foroFinalizado = foro("Foro finalizado col", otroProyecto);
        asignar(col, proyecto, EstadoAsignacion.ACTIVA, null);
        asignar(col, otroProyecto, EstadoAsignacion.FINALIZADA, MotivoFinalizacion.OTRO);

        List<Foro> lista = foroService.listarForosDeMisProyectos(col);

        assertThat(contieneForo(lista, foroProyecto)).isTrue();
        assertThat(contieneForo(lista, foroFinalizado)).isFalse();
    }

    @Test
    void losForosParaPublicarSonLosGeneralesMasLosDeMisProyectos() {
        Proyecto ajeno = proyecto(pm);
        Foro foroAjeno = foro("Foro ajeno col", ajeno);
        asignar(col, proyecto, EstadoAsignacion.ACTIVA, null);

        List<Foro> lista = foroService.listarForosParaPublicar(col);

        assertThat(contieneForo(lista, comunidad)).isTrue();
        assertThat(contieneForo(lista, foroProyecto)).isTrue();
        assertThat(contieneForo(lista, foroAjeno)).isFalse();
    }

    @Test
    void puedeParticiparEnForoGeneralPublicoSinAsignacion() {
        assertThat(foroService.puedeParticipar(col, comunidad)).isTrue();
        assertThat(foroService.puedeParticipar(col, comunidad.getId())).isTrue();
    }

    @Test
    void enUnForoDeProyectoSoloParticipaQuienTieneAsignacionActiva() {
        asignar(col, proyecto, EstadoAsignacion.ACTIVA, null);
        asignar(otroCol, proyecto, EstadoAsignacion.FINALIZADA, MotivoFinalizacion.OTRO);

        assertThat(foroService.puedeParticipar(col, foroProyecto)).isTrue();
        assertThat(foroService.puedeParticipar(otroCol, foroProyecto)).isFalse();
    }

    @Test
    void elForoInexistenteSeRechaza() {
        assertThatThrownBy(() -> foroService.obtenerForo(col, 999999999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El foro no existe.");
    }

    @Test
    void elForoPrivadoSinAsignacionNoSePuedeLeer() {
        assertThatThrownBy(() -> foroService.obtenerForo(col, foroProyecto.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No tienes acceso al foro de este proyecto.");
    }

    @Test
    void quienTerminoElProyectoSinBajoDesempenoLeeElForoPeroNoParticipa() {
        asignar(col, proyecto, EstadoAsignacion.FINALIZADA, MotivoFinalizacion.OTRO);

        Foro leido = foroService.obtenerForo(col, foroProyecto.getId());

        assertThat(leido.getId()).isEqualTo(foroProyecto.getId());
        assertThat(foroService.puedeParticipar(col, foroProyecto.getId())).isFalse();
    }

    @Test
    void quienSalioPorBajoDesempenoNoLeeElForo() {
        asignar(col, proyecto, EstadoAsignacion.FINALIZADA, MotivoFinalizacion.BAJO_DESEMPENO);

        assertThatThrownBy(() -> foroService.obtenerForo(col, foroProyecto.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No tienes acceso al foro de este proyecto.");
    }

    @Test
    void lasEtiquetasDisponiblesSeListan() {
        Etiqueta etiqueta = etiqueta();

        List<Etiqueta> lista = foroService.listarEtiquetas();

        boolean encontrada = false;
        for (Etiqueta e : lista) {
            if (e.getId().equals(etiqueta.getId())) {
                encontrada = true;
            }
        }
        assertThat(encontrada).isTrue();
    }

    // ======================= Crear publicación =======================

    @Test
    void crearPublicacionGuardaTituloLimpioEtiquetaYAuditoria() {
        Etiqueta etiqueta = etiqueta();

        foroService.crearPublicacion(col, comunidad.getId(), "  Duda con JPA  ", "<p>Hola</p>", etiqueta.getId());

        List<PublicacionForo> lista = foroService.listarPublicaciones(col, comunidad.getId());
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).getTitulo()).isEqualTo("Duda con JPA");
        assertThat(lista.get(0).getContenido()).isEqualTo("<p>Hola</p>");
        assertThat(lista.get(0).getEtiqueta().getId()).isEqualTo(etiqueta.getId());
        assertThat(hayAuditoria("PUBLICACION_FORO", "CREAR_PUBLICACION_FORO", lista.get(0).getId())).isTrue();
    }

    @Test
    void crearPublicacionSinEtiquetaEsValido() {
        foroService.crearPublicacion(col, comunidad.getId(), "Sin etiqueta", "<p>Texto</p>", null);

        List<PublicacionForo> lista = foroService.listarPublicaciones(col, comunidad.getId());
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).getEtiqueta()).isNull();
    }

    @Test
    void elTituloEsObligatorioYTieneMaximo() {
        assertThatThrownBy(() -> foroService.crearPublicacion(col, comunidad.getId(), "   ", "<p>Texto</p>", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El título es obligatorio.");
        assertThatThrownBy(() -> foroService.crearPublicacion(col, comunidad.getId(), null, "<p>Texto</p>", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El título es obligatorio.");
        assertThatThrownBy(() -> foroService.crearPublicacion(col, comunidad.getId(), "a".repeat(201), "<p>Texto</p>", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El título no puede tener más de 200 caracteres.");
    }

    @Test
    void elContenidoVacioDelEditorSeRechaza() {
        assertThatThrownBy(() -> foroService.crearPublicacion(col, comunidad.getId(), "Titulo", "<p><br></p>", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Escribe algo antes de publicar.");
        assertThatThrownBy(() -> foroService.crearPublicacion(col, comunidad.getId(), "Titulo", "<p>&nbsp;</p>", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Escribe algo antes de publicar.");
        assertThatThrownBy(() -> foroService.crearPublicacion(col, comunidad.getId(), "Titulo", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Escribe algo antes de publicar.");
    }

    @Test
    void laPublicacionNoPuedeSuperarElMaximoDeContenido() {
        String largo = "<p>" + "a".repeat(10000) + "</p>";

        assertThatThrownBy(() -> foroService.crearPublicacion(col, comunidad.getId(), "Titulo", largo, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La publicación no puede superar los 10000 caracteres.");
    }

    @Test
    void unaEtiquetaInexistenteSeRechaza() {
        assertThatThrownBy(() -> foroService.crearPublicacion(col, comunidad.getId(), "Titulo", "<p>Texto</p>", 999999999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La etiqueta seleccionada no existe.");
    }

    @Test
    void noSePuedePublicarEnUnForoDeProyectoSinAsignacionActiva() {
        assertThatThrownBy(() -> foroService.crearPublicacion(col, foroProyecto.getId(), "Titulo", "<p>Texto</p>", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No tienes acceso al foro de este proyecto.");

        asignar(otroCol, proyecto, EstadoAsignacion.FINALIZADA, MotivoFinalizacion.OTRO);
        assertThatThrownBy(() -> foroService.crearPublicacion(otroCol, foroProyecto.getId(), "Titulo", "<p>Texto</p>", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Este foro es de solo lectura para ti: no tienes una asignación activa en este proyecto.");
    }

    @Test
    void publicarEnElForoDeUnProyectoNotificaAlPm() {
        asignar(col, proyecto, EstadoAsignacion.ACTIVA, null);

        foroService.crearPublicacion(col, foroProyecto.getId(), "Aviso", "<p>Texto</p>", null);

        assertThat(contarNotificaciones(pm, "PUBLICACION_FORO_PROYECTO")).isEqualTo(1);
    }

    @Test
    void publicarEnUnForoGeneralNoNotificaANadie() {
        foroService.crearPublicacion(col, comunidad.getId(), "Aviso", "<p>Texto</p>", null);

        assertThat(contarNotificaciones(pm, "PUBLICACION_FORO_PROYECTO")).isZero();
    }

    // ======================= Editar y eliminar publicación =======================

    @Test
    void elAutorEditaSuPublicacion() {
        PublicacionForo publicacion = publicacion(comunidad, col, "Original");

        foroService.editarPublicacion(col, publicacion.getId(), "  Editada ", "<p>Nuevo</p>", null);

        PublicacionForo editada = publicacionRepository.findById(publicacion.getId()).orElseThrow();
        assertThat(editada.getTitulo()).isEqualTo("Editada");
        assertThat(editada.getContenido()).isEqualTo("<p>Nuevo</p>");
        assertThat(hayAuditoria("PUBLICACION_FORO", "EDITAR_PUBLICACION_FORO", publicacion.getId())).isTrue();
    }

    @Test
    void nadieMasEditaLaPublicacionDeOtro() {
        PublicacionForo publicacion = publicacion(comunidad, col, "Original");

        assertThatThrownBy(() -> foroService.editarPublicacion(otroCol, publicacion.getId(), "Hack", "<p>Hack</p>", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No puedes editar una publicación que no es tuya.");

        assertThat(publicacionRepository.findById(publicacion.getId()).orElseThrow().getTitulo()).isEqualTo("Original");
    }

    @Test
    void editarUnaPublicacionInexistenteSeRechaza() {
        assertThatThrownBy(() -> foroService.editarPublicacion(col, 999999999L, "Titulo", "<p>Texto</p>", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La publicación no existe o ya fue eliminada.");
    }

    @Test
    void elAutorEliminaSuPublicacionYYaNoSeLista() {
        PublicacionForo publicacion = publicacion(comunidad, col, "Para borrar");

        foroService.eliminarPublicacion(col, publicacion.getId());

        assertThat(publicacionRepository.findById(publicacion.getId()).orElseThrow().isActivo()).isFalse();
        assertThat(foroService.listarPublicaciones(col, comunidad.getId())).isEmpty();
        assertThat(hayAuditoria("PUBLICACION_FORO", "ELIMINAR_PUBLICACION_FORO", publicacion.getId())).isTrue();
        assertThatThrownBy(() -> foroService.obtenerPublicacion(col, publicacion.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La publicación no existe o ya fue eliminada.");
    }

    @Test
    void otroColaboradorNoEliminaPeroElAdministradorSi() {
        PublicacionForo publicacion = publicacion(comunidad, col, "Ajena");

        assertThatThrownBy(() -> foroService.eliminarPublicacion(otroCol, publicacion.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No puedes eliminar una publicación que no es tuya.");
        assertThat(publicacionRepository.findById(publicacion.getId()).orElseThrow().isActivo()).isTrue();

        foroService.eliminarPublicacion(admin, publicacion.getId());

        assertThat(publicacionRepository.findById(publicacion.getId()).orElseThrow().isActivo()).isFalse();
    }

    @Test
    void obtenerPublicacionDevuelveLaPublicacionConSuAutor() {
        PublicacionForo publicacion = publicacion(comunidad, col, "Detalle");

        PublicacionForo obtenida = foroService.obtenerPublicacion(otroCol, publicacion.getId());

        assertThat(obtenida.getTitulo()).isEqualTo("Detalle");
        assertThat(obtenida.getAutor().getId()).isEqualTo(col.getId());
    }

    // ======================= Respuestas =======================

    @Test
    void responderGuardaLaRespuestaNotificaAlAutorYQuedaEnAuditoria() {
        PublicacionForo publicacion = publicacion(comunidad, col, "Pregunta");

        foroService.crearRespuesta(otroCol, publicacion.getId(), "<p>Prueba esto</p>");

        List<RespuestaForo> respuestas = foroService.listarRespuestas(col, publicacion.getId());
        assertThat(respuestas).hasSize(1);
        assertThat(respuestas.get(0).getContenido()).isEqualTo("<p>Prueba esto</p>");
        assertThat(respuestas.get(0).getAutor().getId()).isEqualTo(otroCol.getId());
        assertThat(contarNotificaciones(col, "RESPUESTA_FORO")).isEqualTo(1);
        assertThat(hayAuditoria("RESPUESTA_FORO", "CREAR_RESPUESTA_FORO", respuestas.get(0).getId())).isTrue();
    }

    @Test
    void responderTuPropiaPublicacionNoTeNotifica() {
        PublicacionForo publicacion = publicacion(comunidad, col, "Mi pregunta");

        foroService.crearRespuesta(col, publicacion.getId(), "<p>Ya lo resolví</p>");

        assertThat(contarNotificaciones(col, "RESPUESTA_FORO")).isZero();
    }

    @Test
    void laRespuestaVaciaOMuyLargaSeRechaza() {
        PublicacionForo publicacion = publicacion(comunidad, col, "Pregunta");

        assertThatThrownBy(() -> foroService.crearRespuesta(otroCol, publicacion.getId(), "<p><br></p>"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Escribe algo antes de publicar.");
        assertThatThrownBy(() -> foroService.crearRespuesta(otroCol, publicacion.getId(), "a".repeat(5001)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La respuesta no puede superar los 5000 caracteres.");
        assertThat(foroService.listarRespuestas(col, publicacion.getId())).isEmpty();
    }

    @Test
    void noSeRespondeEnUnForoDeProyectoSinAsignacionActiva() {
        PublicacionForo publicacion = publicacion(foroProyecto, pm, "Aviso del PM");

        assertThatThrownBy(() -> foroService.crearRespuesta(col, publicacion.getId(), "<p>Hola</p>"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No tienes acceso al foro de este proyecto.");
    }

    @Test
    void elAutorEditaSuRespuestaYOtroNoPuede() {
        PublicacionForo publicacion = publicacion(comunidad, col, "Pregunta");
        RespuestaForo respuesta = respuesta(publicacion, otroCol, "<p>Original</p>");

        foroService.editarRespuesta(otroCol, respuesta.getId(), "<p>Editada</p>");

        assertThat(respuestaRepository.findById(respuesta.getId()).orElseThrow().getContenido()).isEqualTo("<p>Editada</p>");
        assertThatThrownBy(() -> foroService.editarRespuesta(col, respuesta.getId(), "<p>Hack</p>"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No puedes editar una respuesta que no es tuya.");
    }

    @Test
    void elAutorOElAdminEliminanUnaRespuestaPeroOtroColaboradorNo() {
        PublicacionForo publicacion = publicacion(comunidad, col, "Pregunta");
        RespuestaForo propia = respuesta(publicacion, otroCol, "<p>Mía</p>");
        RespuestaForo deOtro = respuesta(publicacion, otroCol, "<p>Otra</p>");

        assertThatThrownBy(() -> foroService.eliminarRespuesta(col, propia.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No puedes eliminar una respuesta que no es tuya.");

        foroService.eliminarRespuesta(otroCol, propia.getId());
        foroService.eliminarRespuesta(admin, deOtro.getId());

        assertThat(respuestaRepository.findById(propia.getId()).orElseThrow().isActivo()).isFalse();
        assertThat(respuestaRepository.findById(deOtro.getId()).orElseThrow().isActivo()).isFalse();
        assertThat(foroService.listarRespuestas(col, publicacion.getId())).isEmpty();
    }

    @Test
    void editarUnaRespuestaEliminadaSeRechaza() {
        PublicacionForo publicacion = publicacion(comunidad, col, "Pregunta");
        RespuestaForo respuesta = respuesta(publicacion, otroCol, "<p>Texto</p>");
        foroService.eliminarRespuesta(otroCol, respuesta.getId());

        assertThatThrownBy(() -> foroService.editarRespuesta(otroCol, respuesta.getId(), "<p>Otra vez</p>"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La respuesta no existe o ya fue eliminada.");
    }

    // ======================= Me gusta =======================

    @Test
    void elMeGustaDeUnaPublicacionSeDaYSeQuita() {
        PublicacionForo publicacion = publicacion(comunidad, col, "Pregunta");

        foroService.alternarLikePublicacion(otroCol, publicacion.getId());
        assertThat(votoPublicacionRepository.findByUsuarioAndPublicacion(otroCol, publicacion)).isPresent();

        foroService.alternarLikePublicacion(otroCol, publicacion.getId());
        assertThat(votoPublicacionRepository.findByUsuarioAndPublicacion(otroCol, publicacion)).isEmpty();
    }

    @Test
    void elMeGustaDeUnaRespuestaSeDaYSeQuita() {
        PublicacionForo publicacion = publicacion(comunidad, col, "Pregunta");
        RespuestaForo respuesta = respuesta(publicacion, otroCol, "<p>Texto</p>");

        foroService.alternarLikeRespuesta(col, respuesta.getId());
        assertThat(votoRespuestaRepository.findByUsuarioAndRespuesta(col, respuesta)).isPresent();

        foroService.alternarLikeRespuesta(col, respuesta.getId());
        assertThat(votoRespuestaRepository.findByUsuarioAndRespuesta(col, respuesta)).isEmpty();
    }

    @Test
    void noSeDaMeGustaEnUnForoPrivadoSinAcceso() {
        PublicacionForo publicacion = publicacion(foroProyecto, pm, "Aviso del PM");

        assertThatThrownBy(() -> foroService.alternarLikePublicacion(col, publicacion.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No tienes acceso al foro de este proyecto.");
    }

    // ======================= Solución =======================

    @Test
    void soloElAutorDeLaPublicacionMarcaLaSolucion() {
        PublicacionForo publicacion = publicacion(comunidad, col, "Pregunta");
        RespuestaForo respuesta = respuesta(publicacion, otroCol, "<p>Esto</p>");

        assertThatThrownBy(() -> foroService.alternarSolucion(otroCol, respuesta.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Solo el autor de la publicación puede marcar una respuesta como solución.");
        assertThat(respuestaRepository.findById(respuesta.getId()).orElseThrow().isEsSolucion()).isFalse();
    }

    @Test
    void marcarUnaNuevaSolucionDesmarcaLaAnteriorYNotificaAlAutorDeLaRespuesta() {
        PublicacionForo publicacion = publicacion(comunidad, col, "Pregunta");
        RespuestaForo primera = respuesta(publicacion, otroCol, "<p>Primera</p>");
        RespuestaForo segunda = respuesta(publicacion, otroCol, "<p>Segunda</p>");

        foroService.alternarSolucion(col, primera.getId());
        foroService.alternarSolucion(col, segunda.getId());

        assertThat(respuestaRepository.findById(primera.getId()).orElseThrow().isEsSolucion()).isFalse();
        assertThat(respuestaRepository.findById(segunda.getId()).orElseThrow().isEsSolucion()).isTrue();
        assertThat(contarNotificaciones(otroCol, "RESPUESTA_MARCADA_SOLUCION")).isEqualTo(2);
    }

    @Test
    void volverAMarcarLaSolucionLaDesmarca() {
        PublicacionForo publicacion = publicacion(comunidad, col, "Pregunta");
        RespuestaForo respuesta = respuesta(publicacion, otroCol, "<p>Esto</p>");

        foroService.alternarSolucion(col, respuesta.getId());
        assertThat(respuestaRepository.findById(respuesta.getId()).orElseThrow().isEsSolucion()).isTrue();
        assertThat(hayAuditoria("RESPUESTA_FORO", "MARCAR_SOLUCION_FORO", respuesta.getId())).isTrue();

        foroService.alternarSolucion(col, respuesta.getId());
        assertThat(respuestaRepository.findById(respuesta.getId()).orElseThrow().isEsSolucion()).isFalse();
        assertThat(hayAuditoria("RESPUESTA_FORO", "DESMARCAR_SOLUCION_FORO", respuesta.getId())).isTrue();
    }

    @Test
    void marcarTuPropiaRespuestaComoSolucionNoGeneraNotificacion() {
        PublicacionForo publicacion = publicacion(comunidad, col, "Pregunta");
        RespuestaForo propia = respuesta(publicacion, col, "<p>La resolví yo</p>");

        foroService.alternarSolucion(col, propia.getId());

        assertThat(respuestaRepository.findById(propia.getId()).orElseThrow().isEsSolucion()).isTrue();
        assertThat(contarNotificaciones(col, "RESPUESTA_MARCADA_SOLUCION")).isZero();
    }

    // ======================= Imágenes del editor =======================

    @Test
    void subirImagenRechazaVaciaTipoInvalidoYMuyPesada() {
        assertThatThrownBy(() -> foroService.subirImagenForo(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La imagen no puede estar vacía.");
        assertThatThrownBy(() -> foroService.subirImagenForo(imagen("a.png", "image/png", new byte[0])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La imagen no puede estar vacía.");
        assertThatThrownBy(() -> foroService.subirImagenForo(imagen("a.pdf", "application/pdf", new byte[]{1})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Solo se permiten imágenes JPEG, PNG, GIF o WEBP.");
        assertThatThrownBy(() -> foroService.subirImagenForo(
                imagen("grande.png", "image/png", new byte[5 * 1024 * 1024 + 1])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La imagen no puede superar los 5 MB.");
    }

    @Test
    void subirUnaImagenValidaDevuelveSuUrlConLaExtensionCorrecta() {
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        byte[] jpg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};

        String urlPng = foroService.subirImagenForo(imagen("a.png", "image/png", png));
        String urlJpg = foroService.subirImagenForo(imagen("a.jpg", "image/jpeg", jpg));

        assertThat(urlPng).startsWith("/uploads/foro/foro-").endsWith(".png");
        assertThat(urlJpg).startsWith("/uploads/foro/foro-").endsWith(".jpg");
    }

    @Test
    void unaImagenQueDiceSerPngPeroNoLoEsSeRechaza() {
        assertThatThrownBy(() -> foroService.subirImagenForo(imagen("falsa.png", "image/png", new byte[]{1, 2, 3})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El contenido del archivo no corresponde a un PNG válido.");
    }



    private boolean contieneForo(List<Foro> lista, Foro buscado) {
        for (Foro foro : lista) {
            if (foro.getId().equals(buscado.getId())) {
                return true;
            }
        }
        return false;
    }

    private boolean hayAuditoria(String entidad, String accion, Long entidadId) {
        List<LogAuditoria> logs = logAuditoriaRepository.findTop20ByEntidadOrderByFechaHoraDesc(entidad);
        for (LogAuditoria log : logs) {
            if (accion.equals(log.getAccion()) && entidadId.equals(log.getEntidadId())) {
                return true;
            }
        }
        return false;
    }

    private int contarNotificaciones(Usuario usuario, String tipo) {
        int total = 0;
        List<Notificacion> lista = notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(usuario);
        for (Notificacion n : lista) {
            if (tipo.equals(n.getTipo())) {
                total++;
            }
        }
        return total;
    }

    private MockMultipartFile imagen(String nombre, String tipo, byte[] contenido) {
        return new MockMultipartFile("imagen", nombre, tipo, contenido);
    }

    private Etiqueta etiqueta() {
        Etiqueta e = new Etiqueta();
        e.setNombre("et-" + UUID.randomUUID().toString().substring(0, 8));
        return etiquetaRepository.save(e);
    }

    private Foro foro(String nombre, Proyecto proyectoDelForo) {
        Foro foro = new Foro();
        foro.setNombre(nombre + " " + UUID.randomUUID().toString().substring(0, 8));
        foro.setTipo(proyectoDelForo == null ? TipoForo.GENERAL : TipoForo.PROYECTO);
        foro.setEsPublico(proyectoDelForo == null);
        foro.setProyecto(proyectoDelForo);
        foro = foroRepository.save(foro);
        foros.add(foro);
        return foro;
    }

    private PublicacionForo publicacion(Foro foro, Usuario autor, String titulo) {
        PublicacionForo publicacion = new PublicacionForo();
        publicacion.setForo(foro);
        publicacion.setAutor(autor);
        publicacion.setTitulo(titulo);
        publicacion.setContenido("<p>Contenido de prueba</p>");
        publicacion.setFechaCreacion(LocalDateTime.now());
        return publicacionRepository.save(publicacion);
    }

    private RespuestaForo respuesta(PublicacionForo publicacion, Usuario autor, String contenido) {
        RespuestaForo respuesta = new RespuestaForo();
        respuesta.setPublicacion(publicacion);
        respuesta.setAutor(autor);
        respuesta.setContenido(contenido);
        respuesta.setFechaCreacion(LocalDateTime.now());
        return respuestaRepository.save(respuesta);
    }

    private void asignar(Usuario colaborador, Proyecto proyectoAsignado, EstadoAsignacion estado,
                         MotivoFinalizacion motivo) {
        Asignacion a = new Asignacion();
        a.setProyecto(proyectoAsignado);
        a.setColaborador(colaborador);
        a.setHorasSemanales(new BigDecimal("8"));
        a.setOrigen(OrigenAsignacion.PROPUESTA_PM);
        a.setEstado(estado);
        a.setAprobadoPorPm(true);
        a.setAprobadoPorRm(true);
        a.setMotivoFinalizacion(motivo);
        asignacionRepository.save(a);
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
        p.setNombre("Proyecto foro col " + System.nanoTime());
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