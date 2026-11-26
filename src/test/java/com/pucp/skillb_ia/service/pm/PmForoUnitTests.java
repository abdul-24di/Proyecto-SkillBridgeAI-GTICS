package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.dto.PmForoView;
import com.pucp.skillb_ia.model.Foro;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.PublicacionForo;
import com.pucp.skillb_ia.model.RespuestaForo;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.VotoPublicacion;
import com.pucp.skillb_ia.model.enums.CategoriaNotificacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ForoRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.PublicacionForoRepository;
import com.pucp.skillb_ia.repository.RespuestaForoRepository;
import com.pucp.skillb_ia.repository.VotoPublicacionRepository;
import com.pucp.skillb_ia.repository.VotoRespuestaRepository;
import com.pucp.skillb_ia.service.ArchivoAlmacenamientoService;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.ForoDetalleService;
import com.pucp.skillb_ia.service.NotificacionService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pruebas unitarias de {@link PmForoService} con JUnit 5 y Mockito. */
@ExtendWith(MockitoExtension.class)
class PmForoUnitTests {

    @Mock
    private ForoRepository foroRepository;
    @Mock
    private PublicacionForoRepository publicacionRepository;
    @Mock
    private RespuestaForoRepository respuestaRepository;
    @Mock
    private AsignacionRepository asignacionRepository;
    @Mock
    private ProyectoRepository proyectoRepository;
    @Mock
    private VotoPublicacionRepository votoPublicacionRepository;
    @Mock
    private VotoRespuestaRepository votoRespuestaRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private ArchivoAlmacenamientoService archivoAlmacenamientoService;
    @Mock
    private NotificacionService notificacionService;
    @Mock
    private ForoDetalleService foroDetalleService;

    @InjectMocks
    private PmForoService servicio;

    @Test
    void contenidoForoVacioDetectaHtmlVacioYAceptaImagen() {
        assertTrue(PmForoService.contenidoForoVacio(null));
        assertTrue(PmForoService.contenidoForoVacio("<p>   </p>"));
        assertFalse(PmForoService.contenidoForoVacio("<p>Hola</p>"));
        assertFalse(PmForoService.contenidoForoVacio("<p><img src='/a.png'></p>"));
    }

    @Test
    void subirImagenValidaFormatoYTamano() {
        MockMultipartFile bad = new MockMultipartFile("foto", "a.txt", "text/plain", new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> servicio.subirImagen(bad));

        MockMultipartFile ok = new MockMultipartFile("foto", "a.webp", "image/webp", new byte[]{1, 2});
        when(archivoAlmacenamientoService.guardar(eq(ok), eq("foro"), contains(".webp")))
                .thenReturn("/uploads/foro/img.webp");

        assertEquals("/uploads/foro/img.webp", servicio.subirImagen(ok));
    }

    @Test
    void listarForosPmIncluyeProyectosActivosSinForoYOmitenInactivosSinForo() {
        Usuario pm = PmDatosPrueba.pm(1L);
        Proyecto activoSinForo = PmDatosPrueba.proyecto(10L, pm, EstadoProyecto.ACTIVO);
        Proyecto cerradoSinForo = PmDatosPrueba.proyecto(11L, pm, EstadoProyecto.CANCELADO);

        when(proyectoRepository.findByPm(pm)).thenReturn(List.of(activoSinForo, cerradoSinForo));
        when(foroRepository.findByProyecto(activoSinForo)).thenReturn(Optional.empty());
        when(foroRepository.findByProyecto(cerradoSinForo)).thenReturn(Optional.empty());

        List<PmForoView> vistas = servicio.listarForosPm(pm);

        assertEquals(1, vistas.size());
        assertEquals(10L, vistas.get(0).getProyectoId());
    }

    @Nested
    class PublicarYResponder {

        @Test
        void publicarEnForoBloqueaForoDeAnuncios() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Foro anuncios = PmDatosPrueba.foroGeneral(5L, false);
            when(foroRepository.findById(5L)).thenReturn(Optional.of(anuncios));

            assertThrows(SecurityException.class,
                    () -> servicio.publicarEnForo(5L, "Título", "<p>Texto</p>", pm));
        }

        @Test
        void publicarCreaForoSiNoExisteYRegistraAuditoria() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm);
            Foro nuevoForo = PmDatosPrueba.foroProyecto(20L, proyecto, false);

            when(proyectoRepository.findById(10L)).thenReturn(Optional.of(proyecto));
            when(foroRepository.findByProyecto(proyecto)).thenReturn(Optional.empty());
            when(foroRepository.save(any(Foro.class))).thenReturn(nuevoForo);
            when(publicacionRepository.save(any(PublicacionForo.class))).thenAnswer(inv -> inv.getArgument(0));

            PublicacionForo pub = servicio.publicar(10L, "  Inicio Sprint  ", "<p>Bienvenidos</p>", pm);

            assertEquals("Inicio Sprint", pub.getTitulo());
            assertEquals(nuevoForo, pub.getForo());
            verify(auditoriaService).registrar(eq(pm), eq("PUBLICAR"), eq("FORO"), eq(20L), anyString());
        }

        @Test
        void responderNotificaAlAutorCuandoEsDistinto() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Usuario autor = PmDatosPrueba.colaborador(2L);
            Foro comunidad = PmDatosPrueba.foroGeneral(5L, true);
            PublicacionForo pub = PmDatosPrueba.publicacion(30L, comunidad, autor, "Duda despliegue");

            when(publicacionRepository.findById(30L)).thenReturn(Optional.of(pub));
            when(respuestaRepository.save(any(RespuestaForo.class))).thenAnswer(inv -> {
                RespuestaForo r = inv.getArgument(0);
                r.setId(40L);
                return r;
            });

            RespuestaForo r = servicio.responder(30L, "<p>Usa el perfil prod</p>", pm);

            assertEquals(40L, r.getId());
            verify(notificacionService).crear(eq(autor), eq("RESPUESTA_FORO"), eq(CategoriaNotificacion.MENSAJE),
                    anyString(), anyString(), eq("FORO"), eq(5L));
        }
    }

    @Nested
    class ReaccionesSolucionYEliminacion {

        @Test
        void alternarLikePublicacionCreaOEliminaVoto() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Foro anuncios = PmDatosPrueba.foroGeneral(5L, false);
            PublicacionForo pub = PmDatosPrueba.publicacion(30L, anuncios, PmDatosPrueba.admin(9L), "Aviso");

            when(publicacionRepository.findById(30L)).thenReturn(Optional.of(pub));
            when(votoPublicacionRepository.findByUsuarioAndPublicacion(pm, pub)).thenReturn(Optional.empty());

            servicio.alternarLikePublicacion(pm, 30L);
            verify(votoPublicacionRepository).save(any(VotoPublicacion.class));

            VotoPublicacion existente = new VotoPublicacion();
            when(votoPublicacionRepository.findByUsuarioAndPublicacion(pm, pub)).thenReturn(Optional.of(existente));

            servicio.alternarLikePublicacion(pm, 30L);
            verify(votoPublicacionRepository).delete(existente);
        }

        @Test
        void alternarSolucionPermiteAlPmDelProyectoYRechazaAjeno() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Usuario otroPm = PmDatosPrueba.pm(2L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm);
            Foro foro = PmDatosPrueba.foroProyecto(20L, proyecto, true);
            PublicacionForo pub = PmDatosPrueba.publicacion(30L, foro, PmDatosPrueba.colaborador(3L), "Tema");
            RespuestaForo resp = PmDatosPrueba.respuesta(40L, pub, PmDatosPrueba.colaborador(4L));

            when(respuestaRepository.findById(40L)).thenReturn(Optional.of(resp));

            servicio.alternarSolucion(pm, 40L);
            verify(foroDetalleService).alternarSolucion(pm, resp);

            assertThrows(IllegalStateException.class, () -> servicio.alternarSolucion(otroPm, 40L));
        }

        @Test
        void eliminarPublicacionSoloPermiteAlAutor() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Usuario otro = PmDatosPrueba.pm(2L);
            Foro comunidad = PmDatosPrueba.foroGeneral(5L, true);
            PublicacionForo pub = PmDatosPrueba.publicacion(30L, comunidad, pm, "Mi tema");

            when(publicacionRepository.findById(30L)).thenReturn(Optional.of(pub));

            assertThrows(IllegalArgumentException.class, () -> servicio.eliminarPublicacion(otro, 30L));
            verify(publicacionRepository, never()).save(any());

            servicio.eliminarPublicacion(pm, 30L);
            assertFalse(pub.isActivo());
            verify(publicacionRepository).save(pub);
            verify(auditoriaService).registrar(eq(pm), eq("ELIMINAR_PUBLICACION_FORO"),
                    eq("PUBLICACION_FORO"), eq(30L), anyString());
        }
    }
}
