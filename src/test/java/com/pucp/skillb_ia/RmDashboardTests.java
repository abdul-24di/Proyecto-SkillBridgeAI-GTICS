package com.pucp.skillb_ia;

import com.pucp.skillb_ia.controller.RmViewController;
import com.pucp.skillb_ia.dto.RmAsignacionView;
import com.pucp.skillb_ia.dto.RmCertificadoView;
import com.pucp.skillb_ia.dto.RmProyectoView;
import com.pucp.skillb_ia.dto.RmSolicitudPersonalView;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.service.rm.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@ActiveProfiles("test")
class RmDashboardTests {
    @Autowired private WebApplicationContext context;
    private MockMvc mockMvc;

    @BeforeEach
    void prepararMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void dashboardRenderizaTodasLasSeccionesDinamicas() throws Exception {
        mockMvc.perform(get("/rm/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-dashboard"))
                .andExpect(model().attributeExists(
                        "totalAprobacionesRm", "totalEsperandoPm", "totalPostulaciones",
                        "totalProyectosVacantes", "totalProyectosRevision",
                        "accionesPendientes", "solicitudesRecientes", "proyectosAtencion",
                        "totalSolicitudesAbiertas", "totalCertificadosPendientes"));
    }

    @Test
    void dashboardCalculaMetricasYPriorizaDatosReales() {
        RmPerfilService perfilService = mock(RmPerfilService.class);
        RmColaboradorConsultaService colaboradorService = mock(RmColaboradorConsultaService.class);
        RmProyectoConsultaService proyectoService = mock(RmProyectoConsultaService.class);
        RmProyectoRevisionService revisionService = mock(RmProyectoRevisionService.class);
        RmAsignacionService asignacionService = mock(RmAsignacionService.class);
        RmSolicitudPersonalService solicitudService = mock(RmSolicitudPersonalService.class);
        RmCertificadoService certificadoService = mock(RmCertificadoService.class);
        RmForoConsultaService foroService = mock(RmForoConsultaService.class);
        RmReporteService reporteService = mock(RmReporteService.class);
        RmReporteExportService reporteExportService = mock(RmReporteExportService.class);
        RmCursoService cursoService = mock(RmCursoService.class);

        RmAsignacionView postulacion = asignacion(
                OrigenAsignacion.SOLICITADA_COLABORADOR, false, true);
        RmAsignacionView esperandoPm = asignacion(
                OrigenAsignacion.PROPUESTA_RM, true, false);
        when(asignacionService.listar()).thenReturn(List.of(postulacion, esperandoPm));

        RmProyectoView activoConVacantes = proyecto(
                "Proyecto activo", EstadoProyecto.ACTIVO, Prioridad.ALTA, 1, 2, 1);
        RmProyectoView enRevision = proyecto(
                "Proyecto en revisión", EstadoProyecto.EN_REVISION, Prioridad.MEDIA, 0, 0, 0);
        when(proyectoService.listar()).thenReturn(List.of(activoConVacantes, enRevision));

        RmSolicitudPersonalView solicitudAbierta = mock(RmSolicitudPersonalView.class);
        when(solicitudAbierta.isPendiente()).thenReturn(true);
        when(solicitudService.listar()).thenReturn(List.of(solicitudAbierta));
        when(certificadoService.listarPendientes()).thenReturn(List.of(
                mock(RmCertificadoView.class), mock(RmCertificadoView.class)));

        RmViewController controller = new RmViewController(
                perfilService, colaboradorService, proyectoService, revisionService,
                asignacionService, solicitudService, certificadoService, foroService,
                reporteService, reporteExportService, cursoService);
        ConcurrentModel model = new ConcurrentModel();

        assertEquals("rm/rm-dashboard", controller.dashboard(model));
        assertEquals(1L, model.getAttribute("totalAprobacionesRm"));
        assertEquals(1L, model.getAttribute("totalEsperandoPm"));
        assertEquals(1L, model.getAttribute("totalPostulaciones"));
        assertEquals(1L, model.getAttribute("totalProyectosVacantes"));
        assertEquals(1L, model.getAttribute("totalProyectosRevision"));
        assertEquals(1L, model.getAttribute("totalSolicitudesAbiertas"));
        assertEquals(2L, model.getAttribute("totalCertificadosPendientes"));
        assertEquals(2, model.getAttribute("totalAccionesPendientes"));
        assertEquals(2, model.getAttribute("totalProyectosAtencion"));
        assertSame(enRevision, model.getAttribute("proyectoPrioritario"));
    }

    private RmAsignacionView asignacion(OrigenAsignacion origen,
                                         boolean aprobadoRm,
                                         boolean aprobadoPm) {
        Proyecto proyecto = entidadProyecto("Proyecto de asignación", EstadoProyecto.ACTIVO,
                Prioridad.MEDIA, 2);
        Usuario colaborador = new Usuario();
        colaborador.setHorasDisponibles(new BigDecimal("40"));
        colaborador.setHorasContratadasSemana(new BigDecimal("40"));

        Asignacion asignacion = new Asignacion();
        asignacion.setProyecto(proyecto);
        asignacion.setColaborador(colaborador);
        asignacion.setEstado(EstadoAsignacion.PENDIENTE);
        asignacion.setOrigen(origen);
        asignacion.setAprobadoPorRm(aprobadoRm);
        asignacion.setAprobadoPorPm(aprobadoPm);
        asignacion.setHorasSemanales(new BigDecimal("10"));

        return new RmAsignacionView(asignacion, "Colaborador Test", "CT", "PM Test",
                0, 3, BigDecimal.ZERO, 0, 2, List.of());
    }

    private RmProyectoView proyecto(String nombre, EstadoProyecto estado,
                                    Prioridad prioridad, int integrantes,
                                    int vacantes, int pendientesRm) {
        Proyecto proyecto = entidadProyecto(nombre, estado, prioridad, integrantes + vacantes);
        return new RmProyectoView(proyecto, "PM Test", nombreEstado(estado),
                prioridad.name(), integrantes, vacantes, pendientesRm, List.of(), List.of());
    }

    private Proyecto entidadProyecto(String nombre, EstadoProyecto estado,
                                     Prioridad prioridad, int requeridos) {
        Proyecto proyecto = new Proyecto();
        proyecto.setNombre(nombre);
        proyecto.setEstado(estado);
        proyecto.setPrioridad(prioridad);
        proyecto.setColaboradoresRequeridos(requeridos);
        proyecto.setFechaCreacion(LocalDateTime.now());
        return proyecto;
    }

    private String nombreEstado(EstadoProyecto estado) {
        return estado.name().toLowerCase().replace('_', ' ');
    }
}
