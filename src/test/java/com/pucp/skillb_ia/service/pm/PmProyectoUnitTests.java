package com.pucp.skillb_ia.service.pm;

import com.pucp.skillb_ia.dto.PmProyectoView;
import com.pucp.skillb_ia.model.Habilidad;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.ProyectoHabilidadRequerida;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.MotivoFinalizacion;
import com.pucp.skillb_ia.model.enums.NivelDominio;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.ActividadRepository;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.HabilidadRepository;
import com.pucp.skillb_ia.repository.ProyectoHabilidadRequeridaRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.service.ArchivoAlmacenamientoService;
import com.pucp.skillb_ia.service.AuditoriaService;
import com.pucp.skillb_ia.service.ChatBaseService;
import com.pucp.skillb_ia.service.CierreAsignacionesService;
import com.pucp.skillb_ia.service.NotificacionService;
import com.pucp.skillb_ia.service.rm.RmResponsableService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pruebas unitarias de {@link PmProyectoService} con JUnit 5 y Mockito. */
@ExtendWith(MockitoExtension.class)
class PmProyectoUnitTests {

    @Mock
    private ProyectoRepository proyectoRepository;
    @Mock
    private AsignacionRepository asignacionRepository;
    @Mock
    private ActividadRepository actividadRepository;
    @Mock
    private ProyectoHabilidadRequeridaRepository habilidadRequeridaRepository;
    @Mock
    private HabilidadRepository habilidadRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private CierreAsignacionesService cierreAsignacionesService;
    @Mock
    private NotificacionService notificacionService;
    @Mock
    private ChatBaseService chatBaseService;
    @Mock
    private RmResponsableService rmResponsableService;
    @Mock
    private ArchivoAlmacenamientoService archivoAlmacenamientoService;

    @InjectMocks
    private PmProyectoService servicio;

    @Nested
    class Crear {

        @Test
        void validaFechasHorasYCantidadesPositivas() {
            Usuario pm = PmDatosPrueba.pm(1L);
            LocalDate inicio = LocalDate.of(2026, 6, 1);
            LocalDate finAnterior = LocalDate.of(2026, 5, 1);

            assertThrows(IllegalArgumentException.class, () -> servicio.crear(
                    "App", "Desc", inicio, finAnterior, "ALTA", "Just", BigDecimal.TEN, "Just",
                    2, new BigDecimal("20"), null, null, null, null, pm));

            assertThrows(IllegalArgumentException.class, () -> servicio.crear(
                    "App", "Desc", inicio, inicio.plusDays(10), "ALTA", "Just", BigDecimal.TEN, "Just",
                    2, BigDecimal.ZERO, null, null, null, null, pm));

            assertThrows(IllegalArgumentException.class, () -> servicio.crear(
                    "App", "Desc", inicio, inicio.plusDays(10), "ALTA", "Just", BigDecimal.TEN, "Just",
                    2, new BigDecimal("20"), List.of(1L), List.of("BASICO"), List.of(0), List.of(new BigDecimal("10")), pm));
        }

        @Test
        void creaProyectoEnRevisionAsignaRmCreaChatYGuardaHabilidades() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Habilidad java = PmDatosPrueba.habilidad(5L, "Java");
            when(proyectoRepository.save(any(Proyecto.class))).thenAnswer(inv -> {
                Proyecto p = inv.getArgument(0);
                p.setId(42L);
                return p;
            });
            when(habilidadRepository.findById(5L)).thenReturn(Optional.of(java));

            Proyecto creado = servicio.crear(
                    "Portal Ventas", "E-commerce",
                    LocalDate.of(2026, 2, 1), LocalDate.of(2026, 6, 1),
                    "ALTA", "Alta prioridad comercial",
                    new BigDecimal("80000"), "Equipo senior",
                    3, new BigDecimal("30"),
                    List.of(5L), List.of("AVANZADO"), List.of(2), List.of(new BigDecimal("20")),
                    pm);

            assertEquals(42L, creado.getId());
            assertEquals(EstadoProyecto.EN_REVISION, creado.getEstado());
            assertEquals(Prioridad.ALTA, creado.getPrioridad());
            verify(chatBaseService).obtenerOCrearGrupal(creado);
            verify(rmResponsableService).asignarAlCrear(creado);
            verify(habilidadRequeridaRepository).save(any(ProyectoHabilidadRequerida.class));
            verify(auditoriaService).registrar(eq(pm), eq("CREAR"), eq("PROYECTO"), eq(42L), anyString());
        }
    }

    @Nested
    class ConsultarYCancelar {

        @Test
        void obtenerConstruyeVistaConMetricasYRechazaProyectoAjeno() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Usuario otro = PmDatosPrueba.pm(2L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm, EstadoProyecto.ACTIVO);
            Habilidad sql = PmDatosPrueba.habilidad(3L, "SQL");

            when(proyectoRepository.findByIdConPm(10L)).thenReturn(Optional.of(proyecto));
            when(asignacionRepository.countByProyectoAndEstado(proyecto, EstadoAsignacion.ACTIVA)).thenReturn(2L);
            when(asignacionRepository.findPendientesPmByProyecto(proyecto)).thenReturn(List.of());
            when(actividadRepository.countByProyectoAndEstadoAndActivoTrue(eq(proyecto), any(EstadoActividad.class)))
                    .thenReturn(1L);
            when(habilidadRequeridaRepository.findByProyecto(proyecto))
                    .thenReturn(List.of(PmDatosPrueba.requisito(proyecto, sql, NivelDominio.INTERMEDIO, 1)));

            PmProyectoView vista = servicio.obtener(10L, pm);

            assertEquals("Activo", vista.getEstadoTexto());
            assertEquals(2, vista.getIntegrantesActivos());
            assertEquals(4, vista.getTotalActividades());
            assertEquals(1, vista.getRequisitos().size());

            assertThrows(SecurityException.class, () -> servicio.obtener(10L, otro));
        }

        @Test
        void cancelarCierraAsignacionesYRechazaProyectoActivo() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Proyecto enRev = PmDatosPrueba.proyecto(10L, pm, EstadoProyecto.EN_REVISION);
            CierreAsignacionesService.Resultado res = new CierreAsignacionesService.Resultado(2, 1);

            when(proyectoRepository.findById(10L)).thenReturn(Optional.of(enRev));
            when(cierreAsignacionesService.cerrarAbiertas(enRev, pm, "cancelado", MotivoFinalizacion.PROYECTO_CANCELADO))
                    .thenReturn(res);

            CierreAsignacionesService.Resultado obtenido = servicio.cancelar(10L, "  Cambio de prioridades  ", pm);

            assertEquals(EstadoProyecto.CANCELADO, enRev.getEstado());
            assertEquals("Cambio de prioridades", enRev.getMotivoCierre());
            assertEquals(2, obtenido.pendientesRechazadas());
            verify(notificacionService, never()).crear(any(), any(), any(), any(), any(), any(), any());

            Proyecto activo = PmDatosPrueba.proyecto(11L, pm, EstadoProyecto.ACTIVO);
            when(proyectoRepository.findById(11L)).thenReturn(Optional.of(activo));
            assertThrows(IllegalStateException.class, () -> servicio.cancelar(11L, pm));
        }
    }

    @Nested
    class DocumentoContexto {

        @Test
        void validarDocumentoAceptaPdfYRechazaExe() {
            MockMultipartFile pdf = new MockMultipartFile("doc", "alcance.pdf", "application/pdf", new byte[]{1, 2});
            MockMultipartFile exe = new MockMultipartFile("doc", "virus.exe", "application/octet-stream", new byte[]{1});

            assertNull(servicio.validarDocumentoContexto(null));
            assertNull(servicio.validarDocumentoContexto(pdf));
            assertNotNull(servicio.validarDocumentoContexto(exe));
        }

        @Test
        void guardarDocumentoContextoAlmacenaUrlEnProyecto() {
            Usuario pm = PmDatosPrueba.pm(1L);
            Proyecto proyecto = PmDatosPrueba.proyecto(10L, pm);
            MockMultipartFile pdf = new MockMultipartFile("doc", "brief.pdf", "application/pdf", new byte[]{1, 2, 3});

            when(proyectoRepository.findById(10L)).thenReturn(Optional.of(proyecto));
            when(archivoAlmacenamientoService.guardar(eq(pdf), eq("documentos/proyecto-10"), contains(".pdf")))
                    .thenReturn("/uploads/documentos/proyecto-10/contexto.pdf");

            servicio.guardarDocumentoContexto(10L, pdf, pm);

            assertEquals("/uploads/documentos/proyecto-10/contexto.pdf", proyecto.getDocumentoContextoUrl());
            verify(proyectoRepository).save(proyecto);
        }
    }
}
