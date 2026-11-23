package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.Actividad;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoActividad;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoEntrega;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.ActividadRepository;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.col.ColaboradorProyectoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Entrega de actividades del colaborador: marcar una actividad como "lista para revisión" con evidencia y
// comentario (ColaboradorProyectoService.marcarActividadListaParaRevision).
@SpringBootTest
@ActiveProfiles("test")
class ColaboradorActividadEntregaTests {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private ActividadRepository actividadRepository;
    @Autowired private ColaboradorProyectoService colaboradorProyectoService;

    @Test
    void laEntregaATiempoPasaAEnRevisionConSuEvidenciaYComentario() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario colaborador = usuario("col", "COLABORADOR");
        Proyecto proyecto = proyecto(pm);
        asignar(colaborador, proyecto);
        Actividad actividad = actividad(proyecto, colaborador, pm, LocalDate.now().plusDays(3));

        colaboradorProyectoService.marcarActividadListaParaRevision(
                colaborador, actividad.getId(), pdf(), "  Dejo el avance en la evidencia.  ");

        Actividad guardada = actividadRepository.findById(actividad.getId()).orElseThrow();
        assertThat(guardada.getEstado()).isEqualTo(EstadoActividad.EN_REVISION);
        assertThat(guardada.getEstadoEntrega()).isEqualTo(EstadoEntrega.A_TIEMPO);
        assertThat(guardada.getComentarioColaborador()).isEqualTo("Dejo el avance en la evidencia.");
        assertThat(guardada.getEvidenciaUrl()).isNotBlank();
        assertThat(guardada.getFechaMarcadoRevision()).isNotNull();
    }

    @Test
    void laEntregaDespuesDeLaFechaLimiteQuedaComoTardia() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario colaborador = usuario("col", "COLABORADOR");
        Proyecto proyecto = proyecto(pm);
        asignar(colaborador, proyecto);
        Actividad actividad = actividad(proyecto, colaborador, pm, LocalDate.now().minusDays(1));

        colaboradorProyectoService.marcarActividadListaParaRevision(
                colaborador, actividad.getId(), pdf(), "Entrego con retraso.");

        Actividad guardada = actividadRepository.findById(actividad.getId()).orElseThrow();
        assertThat(guardada.getEstado()).isEqualTo(EstadoActividad.EN_REVISION);
        assertThat(guardada.getEstadoEntrega()).isEqualTo(EstadoEntrega.TARDIA);
    }

    @Test
    void sinEvidenciaOConArchivoInvalidoSeRechazaLaEntrega() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario colaborador = usuario("col", "COLABORADOR");
        Proyecto proyecto = proyecto(pm);
        asignar(colaborador, proyecto);
        Actividad actividad = actividad(proyecto, colaborador, pm, LocalDate.now().plusDays(3));

        assertThatThrownBy(() -> colaboradorProyectoService.marcarActividadListaParaRevision(
                colaborador, actividad.getId(), null, "Comentario"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Debes adjuntar una evidencia para marcar la actividad como lista.");

        MockMultipartFile texto = new MockMultipartFile("evidencia", "nota.txt", "text/plain", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> colaboradorProyectoService.marcarActividadListaParaRevision(
                colaborador, actividad.getId(), texto, "Comentario"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La evidencia debe estar en formato PDF, JPG o PNG.");

        assertThat(actividadRepository.findById(actividad.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoActividad.EN_PROGRESO);
    }

    @Test
    void sinComentarioOConComentarioMuyLargoSeRechazaLaEntrega() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario colaborador = usuario("col", "COLABORADOR");
        Proyecto proyecto = proyecto(pm);
        asignar(colaborador, proyecto);
        Actividad actividad = actividad(proyecto, colaborador, pm, LocalDate.now().plusDays(3));

        assertThatThrownBy(() -> colaboradorProyectoService.marcarActividadListaParaRevision(
                colaborador, actividad.getId(), pdf(), "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Debes escribir un comentario para marcar la actividad como lista.");

        String largo = "x".repeat(ColaboradorProyectoService.MAX_COMENTARIO_ENTREGA + 1);
        assertThatThrownBy(() -> colaboradorProyectoService.marcarActividadListaParaRevision(
                colaborador, actividad.getId(), pdf(), largo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El comentario no puede superar los "
                        + ColaboradorProyectoService.MAX_COMENTARIO_ENTREGA + " caracteres.");
    }

    @Test
    void unaActividadAjenaOSinAsignacionActivaNoSePuedeEntregar() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario colaborador = usuario("col", "COLABORADOR");
        Usuario otro = usuario("otro", "COLABORADOR");
        Proyecto proyecto = proyecto(pm);
        asignar(otro, proyecto);
        // La actividad es del colaborador, que NO tiene asignación activa en el proyecto.
        Actividad actividad = actividad(proyecto, colaborador, pm, LocalDate.now().plusDays(3));

        assertThatThrownBy(() -> colaboradorProyectoService.marcarActividadListaParaRevision(
                otro, actividad.getId(), pdf(), "Comentario"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Esta actividad no te pertenece.");

        assertThatThrownBy(() -> colaboradorProyectoService.marcarActividadListaParaRevision(
                colaborador, actividad.getId(), pdf(), "Comentario"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ya no tienes una asignación activa en este proyecto.");
    }

    @Test
    void unaActividadQueYaEstaEnRevisionNoSeEntregaDosVeces() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        Usuario colaborador = usuario("col", "COLABORADOR");
        Proyecto proyecto = proyecto(pm);
        asignar(colaborador, proyecto);
        Actividad actividad = actividad(proyecto, colaborador, pm, LocalDate.now().plusDays(3));

        colaboradorProyectoService.marcarActividadListaParaRevision(colaborador, actividad.getId(), pdf(), "Primera entrega.");

        assertThatThrownBy(() -> colaboradorProyectoService.marcarActividadListaParaRevision(
                colaborador, actividad.getId(), pdf(), "Segunda entrega."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Esta actividad ya está en revisión.");
    }

    // ---------- Utilidades ----------

    private MockMultipartFile pdf() {
        return new MockMultipartFile("evidencia", "entrega.pdf", "application/pdf", "%PDF-1.4 prueba".getBytes());
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

    private Proyecto proyecto(Usuario pm) {
        Proyecto p = new Proyecto();
        p.setNombre("Proyecto entrega " + System.nanoTime());
        p.setDescripcion("Proyecto de prueba.");
        p.setEstado(EstadoProyecto.ACTIVO);
        p.setPrioridad(Prioridad.MEDIA);
        p.setJustificacionPrioridad("Prueba.");
        p.setColaboradoresRequeridos(2);
        p.setPm(pm);
        p.setPresupuesto(new BigDecimal("10000.00"));
        p.setFechaInicio(LocalDate.now().minusMonths(3));
        p.setFechaFinEstimada(LocalDate.now().plusMonths(3));
        return proyectoRepository.save(p);
    }

    private void asignar(Usuario colaborador, Proyecto proyecto) {
        Asignacion a = new Asignacion();
        a.setProyecto(proyecto);
        a.setColaborador(colaborador);
        a.setHorasSemanales(new BigDecimal("8"));
        a.setOrigen(OrigenAsignacion.PROPUESTA_PM);
        a.setEstado(EstadoAsignacion.ACTIVA);
        a.setAprobadoPorPm(true);
        a.setAprobadoPorRm(true);
        asignacionRepository.save(a);
    }

    private Actividad actividad(Proyecto proyecto, Usuario colaborador, Usuario pm, LocalDate limite) {
        Actividad a = new Actividad();
        a.setProyecto(proyecto);
        a.setColaborador(colaborador);
        a.setCreadoPor(pm);
        a.setTitulo("Actividad " + System.nanoTime());
        a.setHorasEstimadas(new BigDecimal("4"));
        a.setFechaAsignacion(LocalDateTime.now().minusDays(5));
        a.setFechaLimite(limite);
        a.setEstado(EstadoActividad.EN_PROGRESO);
        return actividadRepository.save(a);
    }
}