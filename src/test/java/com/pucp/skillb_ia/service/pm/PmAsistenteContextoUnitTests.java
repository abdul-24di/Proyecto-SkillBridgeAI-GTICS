package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.dto.PmAsistenteRespuesta.ConsultaRapida;
import com.pucp.skillb_ia.dto.PmAsistenteRespuesta.KpisAsistente;
import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Foro;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.PublicacionForo;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.repository.ActividadRepository;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.PublicacionForoRepository;
import com.pucp.skillb_ia.repository.RespuestaForoRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.PenalizacionService;
import com.pucp.skillb_ia.service.pm.PmAsistenteContextoService.ContextoPm;
import com.pucp.skillb_ia.service.rm.RmTalentMatchingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Pruebas unitarias de {@link PmAsistenteContextoService} con JUnit 5 y Mockito. */
@ExtendWith(MockitoExtension.class)
class PmAsistenteContextoUnitTests {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ProyectoRepository proyectoRepository;
    @Mock
    private AsignacionRepository asignacionRepository;
    @Mock
    private ActividadRepository actividadRepository;
    @Mock
    private PublicacionForoRepository publicacionForoRepository;
    @Mock
    private RespuestaForoRepository respuestaForoRepository;
    @Mock
    private PmSolicitudPersonalService pmSolicitudPersonalService;
    @Mock
    private PenalizacionService penalizacionService;
    @Mock
    private RmTalentMatchingService rmTalentMatchingService;

    @InjectMocks
    private PmAsistenteContextoService servicio;

    @Test
    void obtenerKpisCalculaProyectosColaboradoresUnicosVacantesYPendientes() {
        Usuario pm = PmDatosPrueba.pm(1L);
        Usuario col = PmDatosPrueba.colaborador(2L);
        Proyecto p1 = PmDatosPrueba.proyecto(10L, pm, EstadoProyecto.ACTIVO);
        p1.setColaboradoresRequeridos(3);
        Proyecto p2 = PmDatosPrueba.proyecto(11L, pm, EstadoProyecto.FINALIZADO);
        p2.setColaboradoresRequeridos(5);

        Asignacion a1 = PmDatosPrueba.asignacion(1L, p1, col, "20", EstadoAsignacion.ACTIVA);
        Asignacion a2 = PmDatosPrueba.asignacion(2L, p2, col, "10", EstadoAsignacion.ACTIVA);
        Asignacion pend = PmDatosPrueba.asignacion(3L, p1, PmDatosPrueba.colaborador(3L), "15", EstadoAsignacion.PENDIENTE);

        when(proyectoRepository.findByPmOrderByFechaCreacionDesc(pm)).thenReturn(List.of(p1, p2));
        when(asignacionRepository.findByProyectoAndEstado(p1, EstadoAsignacion.ACTIVA)).thenReturn(List.of(a1));
        when(asignacionRepository.findByProyectoAndEstado(p2, EstadoAsignacion.ACTIVA)).thenReturn(List.of(a2));
        when(asignacionRepository.findPendientesPmByProyecto(p1)).thenReturn(List.of(pend));
        when(asignacionRepository.findPendientesPmByProyecto(p2)).thenReturn(List.of());
        when(actividadRepository.countByProyectoAndEstadoAndActivoTrue(p1, EstadoActividad.EN_REVISION)).thenReturn(2L);
        when(actividadRepository.countByProyectoAndEstadoAndActivoTrue(p2, EstadoActividad.EN_REVISION)).thenReturn(0L);

        KpisAsistente kpis = servicio.obtenerKpis(pm);

        assertEquals(2, kpis.totalProyectos());
        assertEquals(1, kpis.totalColaboradores());
        assertEquals(2, kpis.totalVacantes());
        assertEquals(3, kpis.totalPendientesPm());
    }

    @Test
    void listarConsultasRapidasPersonalizaPromptCuandoHayProyectoEnfocado() {
        Usuario pm = PmDatosPrueba.pm(1L);
        Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm);
        proyecto.setNombre("App Bancaria");

        when(proyectoRepository.findById(10L)).thenReturn(Optional.of(proyecto));

        List<ConsultaRapida> conProyecto = servicio.listarConsultasRapidas(pm, 10L);
        List<ConsultaRapida> generales = servicio.listarConsultasRapidas(pm, null);

        assertEquals(6, conProyecto.size());
        assertTrue(conProyecto.get(0).prompt().contains("App Bancaria"));
        assertEquals(6, generales.size());
        assertTrue(generales.get(0).prompt().contains("mis equipos actuales"));
    }

    @Test
    void construirIncluyeAnunciosYEncapsulaDatosDeUsuario() {
        Usuario pm = PmDatosPrueba.pm(1L);
        Usuario col = PmDatosPrueba.colaborador(2L);
        Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm, EstadoProyecto.ACTIVO);
        proyecto.setNombre("Sistema ERP");
        Asignacion a = PmDatosPrueba.asignacion(1L, proyecto, col, "20", EstadoAsignacion.ACTIVA);
        Actividad vencida = PmDatosPrueba.actividad(5L, proyecto, col, EstadoActividad.EN_PROGRESO,
                LocalDate.now().minusDays(2));

        Foro anuncios = PmDatosPrueba.foroGeneral(50L, false);
        PublicacionForo aviso = PmDatosPrueba.publicacion(90L, anuncios, PmDatosPrueba.admin(9L),
                "Política de despliegues ERP");

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(pm));
        when(proyectoRepository.findByPmOrderByFechaCreacionDesc(pm)).thenReturn(List.of(proyecto));
        when(asignacionRepository.findByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA)).thenReturn(List.of(a));
        when(asignacionRepository.findPendientesPmByProyecto(proyecto)).thenReturn(List.of());
        when(actividadRepository.countByProyectoAndEstadoAndActivoTrue(proyecto, EstadoActividad.EN_REVISION)).thenReturn(0L);
        when(actividadRepository.findByProyectoAndActivoTrue(proyecto)).thenReturn(List.of(vencida));
        when(pmSolicitudPersonalService.listarCupos(proyecto)).thenReturn(List.of());
        when(penalizacionService.contarStrikes(col, proyecto)).thenReturn(2);
        when(rmTalentMatchingService.calcularHabilidadesFaltantes(proyecto)).thenReturn(List.of());
        when(rmTalentMatchingService.recomendarParaProyecto(proyecto, List.of())).thenReturn(List.of());
        when(publicacionForoRepository.buscarPorPalabra(any(), any())).thenReturn(List.of(aviso));
        when(respuestaForoRepository.findByPublicacionAndEsSolucionTrue(aviso)).thenReturn(Optional.empty());

        ContextoPm ctx = servicio.construir(1L, 10L, "riesgos y despliegues en Sistema ERP");

        assertEquals(proyecto, ctx.proyectoEnfocado());
        assertTrue(ctx.proyectos().containsKey(10L));
        assertTrue(ctx.publicaciones().containsKey(90L));
        assertTrue(ctx.textoPrompt().contains("<dato_usuario>"));
        assertTrue(ctx.textoPrompt().contains("<extracto_foro>"));
        assertTrue(ctx.resumenAnaliticoLocal().contains("[[PROY:10]]"));
    }

    @Test
    void construirFallaSiPmNoExiste() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> servicio.construir(99L, null, "hola"));
    }
}
