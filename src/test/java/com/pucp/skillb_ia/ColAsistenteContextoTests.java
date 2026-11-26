package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.*;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.service.PenalizacionService;
import com.pucp.skillb_ia.service.col.ColAsistenteContextoService;
import com.pucp.skillb_ia.service.col.ColaboradorActividadService;
import com.pucp.skillb_ia.service.col.ColaboradorCursoService;
import com.pucp.skillb_ia.service.col.ColaboradorProyectoService;
import com.pucp.skillb_ia.service.col.ColAsistenteIaClient;
import com.pucp.skillb_ia.service.col.ColAsistenteService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// TASK-075: el asistente del colaborador explica la plataforma y resume horas, strikes y bono sin montos en soles.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ColAsistenteContextoTests {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private PenalizacionRepository penalizacionRepository;
    @Autowired private ColAsistenteContextoService contextoService;
    @Autowired private ColAsistenteService asistenteService;
    @MockitoBean private ColAsistenteIaClient iaClient;

    @Test
    void contextoIncluyeGuiaStrikesYBonoSinMontos() {
        Usuario col = crearUsuario("ColStrikes", "COLABORADOR");
        col.setSueldoBase(new BigDecimal("4321.00"));
        usuarioRepository.save(col);
        Proyecto proyecto = crearProyecto("Proyecto Strikes Asistente");
        asignarActivo(proyecto, col);
        for (int i = 0; i < 3; i++) {
            strike(proyecto, col);
        }

        String texto = contextoService.construir(col.getId(), "¿cómo va mi bono?").texto();

        assertThat(texto)
                .contains("## GUÍA DE USO DE SKILLBRIDGE")
                .contains("/colaborador/desempeno")
                .contains("## HORAS, STRIKES Y BONO DEL MES")
                .contains("Strikes este mes (todos los proyectos): 3 | descuento del mes: 5%")
                .contains("Strikes en Proyecto Strikes Asistente: 3 de 3")
                .contains("Bono del mes: no aplica")
                .doesNotContain("4321")
                .doesNotContain("S/");
    }

    @Test
    void sinStrikesNoHayDescuentoYElPromptLlevaLaReglaDeLaGuia() {
        Usuario col = crearUsuario("ColSinStrikes", "COLABORADOR");
        when(iaClient.estaConfigurado()).thenReturn(true);
        when(iaClient.generar(anyString(), anyList(), anyString())).thenReturn("**Según SkillBridge**\n- Respuesta de prueba.");

        asistenteService.responder(col.getId(), "¿Cómo subo la evidencia de un curso?", new MockHttpSession());

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(iaClient).generar(prompt.capture(), anyList(), anyString());
        assertThat(prompt.getValue())
                .contains("GUÍA DE USO DE SKILLBRIDGE")
                .contains("ni de montos en soles")
                .contains("10. Para preguntas de uso de la plataforma")
                .contains("Strikes este mes (todos los proyectos): 0 | descuento del mes: 0%")
                .contains("Bono del mes: aún no");
    }

    @Test
    void laGuiaUsaLasReglasRealesDelCodigo() {
        long mbActividad = ColaboradorProyectoService.TAMANO_MAXIMO_EVIDENCIA_BYTES / (1024 * 1024);
        long mbCurso = ColaboradorCursoService.TAMANO_MAXIMO_EVIDENCIA_BYTES / (1024 * 1024);

        assertThat(ColAsistenteContextoService.GUIA_PLATAFORMA)
                .contains("evidencia PDF, JPG o PNG de " + mbActividad + " MB como máximo")
                .contains("hasta " + ColaboradorProyectoService.MAX_COMENTARIO_ENTREGA + " caracteres")
                .contains("Con " + PenalizacionService.MAXIMO_STRIKES + " strikes en un mismo proyecto")
                .contains("El descuento del mes es " + PenalizacionService.PORCENTAJE_POR_BLOQUE
                        + " % por cada bloque completo de " + PenalizacionService.STRIKES_POR_BLOQUE + " strikes")
                .contains("la meta es " + ColaboradorActividadService.META_MENSUAL_HORAS + " horas al mes")
                .contains("(PDF, JPG o PNG, " + mbCurso + " MB como máximo)")
                .doesNotContain("%d")
                .doesNotContain("%s");
    }

    private void strike(Proyecto proyecto, Usuario col) {
        Penalizacion p = new Penalizacion();
        p.setColaborador(col);
        p.setProyecto(proyecto);
        p.setTipo(TipoPenalizacion.ACTIVIDAD_TARDIA);
        p.setMotivo("Entrega tardía de prueba");
        p.setMonto(BigDecimal.ONE);
        penalizacionRepository.save(p);
    }

    private void asignarActivo(Proyecto proyecto, Usuario col) {
        Asignacion a = new Asignacion();
        a.setProyecto(proyecto);
        a.setColaborador(col);
        a.setHorasSemanales(new BigDecimal("4.00"));
        a.setOrigen(OrigenAsignacion.PROPUESTA_PM);
        a.setEstado(EstadoAsignacion.ACTIVA);
        asignacionRepository.save(a);
    }

    private Proyecto crearProyecto(String nombre) {
        Proyecto p = new Proyecto();
        p.setNombre(nombre);
        p.setDescripcion("Proyecto de prueba para el asistente del colaborador.");
        p.setEstado(EstadoProyecto.ACTIVO);
        p.setPrioridad(Prioridad.MEDIA);
        p.setJustificacionPrioridad("Prioridad media.");
        p.setColaboradoresRequeridos(2);
        p.setHorasSemanalesRequeridas(new BigDecimal("8.00"));
        p.setPm(crearUsuario("PmAsistCol", "PROJECT_MANAGER"));
        p.setRmRevisor(crearUsuario("RmAsistCol", "RESOURCE_MANAGER"));
        p.setPresupuesto(new BigDecimal("10000.00"));
        p.setFechaInicio(LocalDate.now().minusWeeks(1));
        p.setFechaFinEstimada(LocalDate.now().plusMonths(2));
        return proyectoRepository.save(p);
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
}
