package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.*;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.service.col.ColaboradorProyectoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import com.pucp.skillb_ia.dto.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ColaboradorSolicitudProyectoTests {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private CategoriaHabilidadRepository categoriaHabilidadRepository;
    @Autowired private HabilidadRepository habilidadRepository;
    @Autowired private ColaboradorHabilidadRepository colaboradorHabilidadRepository;
    @Autowired private ProyectoHabilidadRequeridaRepository proyectoHabilidadRequeridaRepository;
    @Autowired private NotificacionRepository notificacionRepository;
    @Autowired private LogAuditoriaRepository logAuditoriaRepository;
    @Autowired private ColaboradorProyectoService colaboradorProyectoService;

    // ======================= solicitarIncorporacion =======================

    @Test
    void unaSolicitudValidaQuedaPendienteConOrigenColaboradorYAvisaAlPmYAlRm() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario rm = usuario("rm", "RESOURCE_MANAGER", null);
        Usuario colaborador = usuario("col", "COLABORADOR", "40");
        Habilidad java = habilidad("Java");
        Proyecto proyecto = proyecto(pm, rm, EstadoProyecto.ACTIVO, 2);
        requerir(proyecto, java, 1, "12");
        validar(colaborador, java, EstadoValidacion.VALIDADA);

        colaboradorProyectoService.solicitarIncorporacion(colaborador, proyecto.getId(), java.getId(), "  Quiero sumarme.  ");

        List<Asignacion> mias = asignacionRepository.findByColaborador(colaborador);
        assertThat(mias).hasSize(1);
        Asignacion solicitud = mias.get(0);
        assertThat(solicitud.getEstado()).isEqualTo(EstadoAsignacion.PENDIENTE);
        assertThat(solicitud.getOrigen()).isEqualTo(OrigenAsignacion.SOLICITADA_COLABORADOR);
        assertThat(solicitud.getHabilidadSolicitada().getId()).isEqualTo(java.getId());
        assertThat(solicitud.getHorasSemanales()).isEqualByComparingTo("12");
        assertThat(solicitud.getMensajeSolicitud()).isEqualTo("Quiero sumarme.");
        // Todavía no tiene ninguna aprobación: requiere la del PM y la del RM.
        assertThat(solicitud.getFechaAprobacionPm()).isNull();
        assertThat(solicitud.getFechaAprobacionRm()).isNull();

        assertThat(notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(pm))
                .anyMatch(n -> "SOLICITUD_INCORPORACION".equals(n.getTipo())
                        && solicitud.getId().equals(n.getEntidadId()));
        assertThat(notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(rm))
                .anyMatch(n -> "SOLICITUD_INCORPORACION".equals(n.getTipo())
                        && solicitud.getId().equals(n.getEntidadId()));
    }

    @Test
    void laSolicitudQuedaRegistradaEnLaAuditoria() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario colaborador = usuario("col", "COLABORADOR", "40");
        Habilidad java = habilidad("Java");
        Proyecto proyecto = proyecto(pm, null, EstadoProyecto.ACTIVO, 2);
        requerir(proyecto, java, 1, "12");
        validar(colaborador, java, EstadoValidacion.VALIDADA);

        colaboradorProyectoService.solicitarIncorporacion(colaborador, proyecto.getId(), java.getId(), null);

        Asignacion solicitud = asignacionRepository.findByColaborador(colaborador).get(0);
        assertThat(logAuditoriaRepository.findTop20ByEntidadOrderByFechaHoraDesc("ASIGNACION"))
                .anyMatch(l -> "SOLICITAR_ASIGNACION".equals(l.getAccion())
                        && solicitud.getId().equals(l.getEntidadId()));
    }

    @Test
    void unMensajeEnBlancoSeGuardaComoNulo() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario colaborador = usuario("col", "COLABORADOR", "40");
        Habilidad java = habilidad("Java");
        Proyecto proyecto = proyecto(pm, null, EstadoProyecto.ACTIVO, 2);
        requerir(proyecto, java, 1, "12");
        validar(colaborador, java, EstadoValidacion.VALIDADA);

        colaboradorProyectoService.solicitarIncorporacion(colaborador, proyecto.getId(), java.getId(), "    ");

        assertThat(asignacionRepository.findByColaborador(colaborador).get(0).getMensajeSolicitud()).isNull();
    }

    @Test
    void sinHorasEspecificasDelPerfilSeUsanLasHorasSemanalesDelProyecto() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario colaborador = usuario("col", "COLABORADOR", "40");
        Habilidad java = habilidad("Java");
        Proyecto proyecto = proyecto(pm, null, EstadoProyecto.ACTIVO, 2);
        proyecto.setHorasSemanalesRequeridas(new BigDecimal("9"));
        proyectoRepository.save(proyecto);
        requerir(proyecto, java, 1, null);
        validar(colaborador, java, EstadoValidacion.VALIDADA);

        colaboradorProyectoService.solicitarIncorporacion(colaborador, proyecto.getId(), java.getId(), null);

        assertThat(asignacionRepository.findByColaborador(colaborador).get(0).getHorasSemanales())
                .isEqualByComparingTo("9");
    }

    @Test
    void noSePuedeSolicitarUnProyectoInexistenteOInactivo() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario colaborador = usuario("col", "COLABORADOR", "40");
        Habilidad java = habilidad("Java");
        Proyecto enRevision = proyecto(pm, null, EstadoProyecto.EN_REVISION, 2);
        requerir(enRevision, java, 1, "12");
        validar(colaborador, java, EstadoValidacion.VALIDADA);

        assertThatThrownBy(() -> colaboradorProyectoService.solicitarIncorporacion(
                colaborador, 987654321L, java.getId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El proyecto seleccionado no existe.");

        assertThatThrownBy(() -> colaboradorProyectoService.solicitarIncorporacion(
                colaborador, enRevision.getId(), java.getId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Este proyecto ya no está activo.");

        assertThat(asignacionRepository.findByColaborador(colaborador)).isEmpty();
    }

    @Test
    void sinElegirPerfilOConPerfilInvalidoOAjenoAlProyectoSeRechaza() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario colaborador = usuario("col", "COLABORADOR", "40");
        Habilidad java = habilidad("Java");
        Habilidad python = habilidad("Python");
        Proyecto proyecto = proyecto(pm, null, EstadoProyecto.ACTIVO, 2);
        requerir(proyecto, java, 1, "12");
        validar(colaborador, python, EstadoValidacion.VALIDADA);

        assertThatThrownBy(() -> colaboradorProyectoService.solicitarIncorporacion(
                colaborador, proyecto.getId(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selecciona el perfil al que deseas postularte.");

        assertThatThrownBy(() -> colaboradorProyectoService.solicitarIncorporacion(
                colaborador, proyecto.getId(), 987654321L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El perfil seleccionado no es válido.");

        // Python existe y el colaborador lo domina, pero el proyecto no lo pide.
        assertThatThrownBy(() -> colaboradorProyectoService.solicitarIncorporacion(
                colaborador, proyecto.getId(), python.getId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ese perfil no pertenece a este proyecto.");
    }

    @Test
    void sinLaHabilidadValidadaEnElPerfilNoSePuedePostular() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario sinHabilidad = usuario("sin", "COLABORADOR", "40");
        Usuario pendiente = usuario("pend", "COLABORADOR", "40");
        Usuario rechazada = usuario("rech", "COLABORADOR", "40");
        Habilidad java = habilidad("Java");
        Proyecto proyecto = proyecto(pm, null, EstadoProyecto.ACTIVO, 3);
        requerir(proyecto, java, 3, "12");
        validar(pendiente, java, EstadoValidacion.PENDIENTE);
        validar(rechazada, java, EstadoValidacion.RECHAZADA);

        for (Usuario candidato : List.of(sinHabilidad, pendiente, rechazada)) {
            assertThatThrownBy(() -> colaboradorProyectoService.solicitarIncorporacion(
                    candidato, proyecto.getId(), java.getId(), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No cuentas con la habilidad")
                    .hasMessageContaining("validada en tu perfil");
            assertThat(asignacionRepository.findByColaborador(candidato)).isEmpty();
        }
    }

    @Test
    void sinHorasDisponiblesSuficientesSeRechazaLaSolicitud() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario justo = usuario("justo", "COLABORADOR", "5");
        Usuario sinDato = usuario("sindato", "COLABORADOR", null);
        Habilidad java = habilidad("Java");
        Proyecto proyecto = proyecto(pm, null, EstadoProyecto.ACTIVO, 3);
        requerir(proyecto, java, 3, "12");
        validar(justo, java, EstadoValidacion.VALIDADA);
        validar(sinDato, java, EstadoValidacion.VALIDADA);

        for (Usuario candidato : List.of(justo, sinDato)) {
            assertThatThrownBy(() -> colaboradorProyectoService.solicitarIncorporacion(
                    candidato, proyecto.getId(), java.getId(), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No tienes suficientes horas disponibles para este perfil");
        }
    }

    @Test
    void conExactamenteLasHorasRequeridasSiSePuedePostular() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario colaborador = usuario("col", "COLABORADOR", "12");
        Habilidad java = habilidad("Java");
        Proyecto proyecto = proyecto(pm, null, EstadoProyecto.ACTIVO, 2);
        requerir(proyecto, java, 1, "12");
        validar(colaborador, java, EstadoValidacion.VALIDADA);

        colaboradorProyectoService.solicitarIncorporacion(colaborador, proyecto.getId(), java.getId(), null);

        assertThat(asignacionRepository.findByColaborador(colaborador)).hasSize(1);
    }

    @Test
    void unPerfilSinCuposYaNoAceptaSolicitudes() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario ocupante = usuario("ocupante", "COLABORADOR", "40");
        Usuario aspirante = usuario("aspirante", "COLABORADOR", "40");
        Habilidad java = habilidad("Java");
        Proyecto proyecto = proyecto(pm, null, EstadoProyecto.ACTIVO, 2);
        requerir(proyecto, java, 1, "12");
        validar(aspirante, java, EstadoValidacion.VALIDADA);
        asignacion(proyecto, ocupante, java, EstadoAsignacion.ACTIVA, OrigenAsignacion.PROPUESTA_PM,
                LocalDateTime.now().minusDays(1));

        assertThatThrownBy(() -> colaboradorProyectoService.solicitarIncorporacion(
                aspirante, proyecto.getId(), java.getId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ese perfil ya no tiene cupos disponibles.");
    }

    @Test
    void noSePuedeSolicitarDosVecesElMismoProyecto() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario colaborador = usuario("col", "COLABORADOR", "40");
        Habilidad java = habilidad("Java");
        Proyecto proyecto = proyecto(pm, null, EstadoProyecto.ACTIVO, 3);
        requerir(proyecto, java, 3, "12");
        validar(colaborador, java, EstadoValidacion.VALIDADA);

        colaboradorProyectoService.solicitarIncorporacion(colaborador, proyecto.getId(), java.getId(), null);

        assertThatThrownBy(() -> colaboradorProyectoService.solicitarIncorporacion(
                colaborador, proyecto.getId(), java.getId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ya tienes una solicitud pendiente o una asignación activa en este proyecto.");

        assertThat(asignacionRepository.findByColaborador(colaborador)).hasSize(1);
    }

    @Test
    void conUnaAsignacionActivaTampocoSePuedeVolverASolicitar() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario colaborador = usuario("col", "COLABORADOR", "40");
        Habilidad java = habilidad("Java");
        Proyecto proyecto = proyecto(pm, null, EstadoProyecto.ACTIVO, 3);
        requerir(proyecto, java, 3, "12");
        validar(colaborador, java, EstadoValidacion.VALIDADA);
        asignacion(proyecto, colaborador, java, EstadoAsignacion.ACTIVA, OrigenAsignacion.PROPUESTA_RM,
                LocalDateTime.now().minusDays(1));

        assertThatThrownBy(() -> colaboradorProyectoService.solicitarIncorporacion(
                colaborador, proyecto.getId(), java.getId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ya tienes una solicitud pendiente o una asignación activa en este proyecto.");
    }

    @Test
    void unaSolicitudRechazadaAnteriormenteNoImpideVolverAPostular() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario colaborador = usuario("col", "COLABORADOR", "40");
        Habilidad java = habilidad("Java");
        Proyecto proyecto = proyecto(pm, null, EstadoProyecto.ACTIVO, 3);
        requerir(proyecto, java, 3, "12");
        validar(colaborador, java, EstadoValidacion.VALIDADA);
        asignacion(proyecto, colaborador, java, EstadoAsignacion.RECHAZADA, OrigenAsignacion.SOLICITADA_COLABORADOR,
                LocalDateTime.now().minusDays(3));

        colaboradorProyectoService.solicitarIncorporacion(colaborador, proyecto.getId(), java.getId(), null);

        assertThat(asignacionRepository.findByColaborador(colaborador))
                .extracting(Asignacion::getEstado)
                .containsExactlyInAnyOrder(EstadoAsignacion.RECHAZADA, EstadoAsignacion.PENDIENTE);
    }

    // ======================= listarTodosLosProyectos =======================

    @Test
    void elListadoDeProyectosOcultaLosRechazadosYMuestraLosDemas() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario colaborador = usuario("col", "COLABORADOR", "40");
        Proyecto activo = proyecto(pm, null, EstadoProyecto.ACTIVO, 2);
        Proyecto enRevision = proyecto(pm, null, EstadoProyecto.EN_REVISION, 2);
        Proyecto rechazado = proyecto(pm, null, EstadoProyecto.RECHAZADO, 2);

        List<Long> ids = colaboradorProyectoService.listarTodosLosProyectos(colaborador).stream()
                .map(v -> v.getProyecto().getId()).toList();

        assertThat(ids).contains(activo.getId(), enRevision.getId());
        assertThat(ids).doesNotContain(rechazado.getId());
    }

    @Test
    void laVistaDelProyectoCalculaCuposPerfilesYEstadoDeLaSolicitud() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario ocupante = usuario("ocupante", "COLABORADOR", "40");
        Usuario colaborador = usuario("col", "COLABORADOR", "40");
        Habilidad java = habilidad("Java");
        Habilidad react = habilidad("React");
        Proyecto proyecto = proyecto(pm, null, EstadoProyecto.ACTIVO, 3);
        requerir(proyecto, java, 1, "12");
        requerir(proyecto, react, 2, "10");
        asignacion(proyecto, ocupante, java, EstadoAsignacion.ACTIVA, OrigenAsignacion.PROPUESTA_PM,
                LocalDateTime.now().minusDays(2));

        ColProyectoDisponibleView antes = vistaDe(proyecto, colaborador);

        assertThat(antes.getColaboradoresActivos()).isEqualTo(1);
        assertThat(antes.getCuposDisponibles()).isEqualTo(2); // 3 requeridos - 1 activo
        assertThat(antes.isYaTieneSolicitud()).isFalse();
        assertThat(antes.isPostulacionesAbiertas()).isTrue(); // React todavía tiene cupos
        assertThat(antes.isTieneHorasSuficientes()).isTrue();

        ColPerfilRequeridoView perfilJava = perfil(antes, java);
        ColPerfilRequeridoView perfilReact = perfil(antes, react);
        assertThat(perfilJava.getCuposDisponibles()).isZero();
        assertThat(perfilReact.getCuposDisponibles()).isEqualTo(2);
        assertThat(perfilReact.getHorasSemanales()).isEqualByComparingTo("10");
        assertThat(perfilReact.getEtiqueta()).startsWith(react.getNombre());

        asignacion(proyecto, colaborador, react, EstadoAsignacion.PENDIENTE, OrigenAsignacion.SOLICITADA_COLABORADOR,
                LocalDateTime.now());

        assertThat(vistaDe(proyecto, colaborador).isYaTieneSolicitud()).isTrue();
    }

    @Test
    void sinCuposEnNingunPerfilOSinProyectoActivoLasPostulacionesEstanCerradas() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario ocupante = usuario("ocupante", "COLABORADOR", "40");
        Usuario colaborador = usuario("col", "COLABORADOR", "40");
        Habilidad java = habilidad("Java");

        Proyecto lleno = proyecto(pm, null, EstadoProyecto.ACTIVO, 1);
        requerir(lleno, java, 1, "12");
        asignacion(lleno, ocupante, java, EstadoAsignacion.ACTIVA, OrigenAsignacion.PROPUESTA_PM,
                LocalDateTime.now().minusDays(1));

        Proyecto enEspera = proyecto(pm, null, EstadoProyecto.EN_ESPERA, 2);
        requerir(enEspera, java, 1, "12");

        assertThat(vistaDe(lleno, colaborador).isPostulacionesAbiertas()).isFalse();
        assertThat(vistaDe(lleno, colaborador).isTieneHorasSuficientes()).isFalse();
        // Tiene cupo en el perfil, pero el proyecto no está ACTIVO.
        assertThat(vistaDe(enEspera, colaborador).isPostulacionesAbiertas()).isFalse();
    }

    @Test
    void siElColaboradorNoTieneHorasLaVistaLoIndicaParaDeshabilitarElBoton() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario colaborador = usuario("col", "COLABORADOR", "4");
        Habilidad java = habilidad("Java");
        Proyecto proyecto = proyecto(pm, null, EstadoProyecto.ACTIVO, 2);
        requerir(proyecto, java, 1, "12");

        ColProyectoDisponibleView vista = vistaDe(proyecto, colaborador);

        assertThat(vista.isPostulacionesAbiertas()).isTrue();
        assertThat(vista.isTieneHorasSuficientes()).isFalse();
    }

    @Test
    void unProyectoSinPerfilesRequeridosNoAbrePostulaciones() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario colaborador = usuario("col", "COLABORADOR", "40");
        Proyecto proyecto = proyecto(pm, null, EstadoProyecto.ACTIVO, 2);

        ColProyectoDisponibleView vista = vistaDe(proyecto, colaborador);

        assertThat(vista.getPerfiles()).isEmpty();
        assertThat(vista.isPostulacionesAbiertas()).isFalse();
    }

    // ======================= mis solicitudes / mis asignaciones / historial =======================

    @Test
    void misSolicitudesSoloIncluyeLasQueYoPediAlRevesDeLasPropuestasPorPmORm() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario colaborador = usuario("col", "COLABORADOR", "40");
        Usuario otro = usuario("otro", "COLABORADOR", "40");
        Habilidad java = habilidad("Java");
        Proyecto p1 = proyecto(pm, null, EstadoProyecto.ACTIVO, 2);
        Proyecto p2 = proyecto(pm, null, EstadoProyecto.ACTIVO, 2);
        Proyecto p3 = proyecto(pm, null, EstadoProyecto.ACTIVO, 2);
        Proyecto p4 = proyecto(pm, null, EstadoProyecto.ACTIVO, 2);
        LocalDateTime ahora = LocalDateTime.now();

        Asignacion vieja = asignacion(p1, colaborador, java, EstadoAsignacion.RECHAZADA,
                OrigenAsignacion.SOLICITADA_COLABORADOR, ahora.minusDays(5));
        Asignacion reciente = asignacion(p2, colaborador, java, EstadoAsignacion.PENDIENTE,
                OrigenAsignacion.SOLICITADA_COLABORADOR, ahora.minusDays(1));
        asignacion(p3, colaborador, java, EstadoAsignacion.PENDIENTE, OrigenAsignacion.PROPUESTA_PM, ahora);
        asignacion(p4, otro, java, EstadoAsignacion.PENDIENTE, OrigenAsignacion.SOLICITADA_COLABORADOR, ahora);

        List<Asignacion> solicitudes = colaboradorProyectoService.listarMisSolicitudes(colaborador);

        assertThat(solicitudes).extracting(Asignacion::getId)
                .containsExactly(reciente.getId(), vieja.getId()); // más reciente primero
    }

    @Test
    void misAsignacionesMuestraTodosLosEstadosYOrigenesSoloDelColaboradorOrdenadasPorFecha() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario colaborador = usuario("col", "COLABORADOR", "40");
        Usuario otro = usuario("otro", "COLABORADOR", "40");
        Habilidad java = habilidad("Java");
        Proyecto p1 = proyecto(pm, null, EstadoProyecto.ACTIVO, 2);
        Proyecto p2 = proyecto(pm, null, EstadoProyecto.ACTIVO, 2);
        Proyecto p3 = proyecto(pm, null, EstadoProyecto.ACTIVO, 2);
        LocalDateTime ahora = LocalDateTime.now();

        Asignacion finalizada = asignacion(p1, colaborador, java, EstadoAsignacion.FINALIZADA,
                OrigenAsignacion.PROPUESTA_RM, ahora.minusDays(10));
        Asignacion activa = asignacion(p2, colaborador, java, EstadoAsignacion.ACTIVA,
                OrigenAsignacion.PROPUESTA_PM, ahora.minusDays(2));
        Asignacion pendiente = asignacion(p3, colaborador, java, EstadoAsignacion.PENDIENTE,
                OrigenAsignacion.SOLICITADA_COLABORADOR, ahora.minusDays(1));
        asignacion(p1, otro, java, EstadoAsignacion.ACTIVA, OrigenAsignacion.PROPUESTA_PM, ahora);

        List<Asignacion> mias = colaboradorProyectoService.listarMisAsignaciones(colaborador);

        assertThat(mias).extracting(Asignacion::getId)
                .containsExactly(pendiente.getId(), activa.getId(), finalizada.getId());
    }

    @Test
    void elColaboradorSinAsignacionesVeListasVacias() {
        Usuario colaborador = usuario("col", "COLABORADOR", "40");

        assertThat(colaboradorProyectoService.listarMisAsignaciones(colaborador)).isEmpty();
        assertThat(colaboradorProyectoService.listarMisSolicitudes(colaborador)).isEmpty();
        assertThat(colaboradorProyectoService.listarHistorialProyectos(colaborador)).isEmpty();
    }

    @Test
    void elHistorialSoloIncluyeProyectosFinalizadosConSusHabilidades() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario colaborador = usuario("col", "COLABORADOR", "40");
        Habilidad java = habilidad("Java");
        Habilidad react = habilidad("React");
        Proyecto terminado = proyecto(pm, null, EstadoProyecto.FINALIZADO, 2);
        Proyecto vigente = proyecto(pm, null, EstadoProyecto.ACTIVO, 2);
        Proyecto rechazado = proyecto(pm, null, EstadoProyecto.ACTIVO, 2);
        requerir(terminado, java, 1, "12");
        requerir(terminado, react, 1, "8");
        LocalDateTime ahora = LocalDateTime.now();

        Asignacion fin = asignacion(terminado, colaborador, java, EstadoAsignacion.FINALIZADA,
                OrigenAsignacion.PROPUESTA_PM, ahora.minusMonths(2));
        fin.setFechaFinalizacion(ahora.minusDays(3));
        asignacionRepository.save(fin);
        asignacion(vigente, colaborador, java, EstadoAsignacion.ACTIVA, OrigenAsignacion.PROPUESTA_PM, ahora.minusDays(10));
        asignacion(rechazado, colaborador, java, EstadoAsignacion.RECHAZADA, OrigenAsignacion.PROPUESTA_PM, ahora.minusDays(9));

        List<ColHistorialProyectoView> historial = colaboradorProyectoService.listarHistorialProyectos(colaborador);

        assertThat(historial).hasSize(1);
        assertThat(historial.get(0).getProyecto().getId()).isEqualTo(terminado.getId());
        assertThat(historial.get(0).getAsignacion().getEstado()).isEqualTo(EstadoAsignacion.FINALIZADA);
        assertThat(historial.get(0).getHabilidadesRequeridas())
                .containsExactlyInAnyOrder(java.getNombre(), react.getNombre());
    }

    @Test
    void elHistorialDeUnColaboradorNoMezclaProyectosDeOtros() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario colaborador = usuario("col", "COLABORADOR", "40");
        Usuario otro = usuario("otro", "COLABORADOR", "40");
        Habilidad java = habilidad("Java");
        Proyecto terminado = proyecto(pm, null, EstadoProyecto.FINALIZADO, 2);
        Asignacion deOtro = asignacion(terminado, otro, java, EstadoAsignacion.FINALIZADA,
                OrigenAsignacion.PROPUESTA_PM, LocalDateTime.now().minusMonths(1));
        deOtro.setFechaFinalizacion(LocalDateTime.now().minusDays(1));
        asignacionRepository.save(deOtro);

        assertThat(colaboradorProyectoService.listarHistorialProyectos(colaborador)).isEmpty();
        assertThat(colaboradorProyectoService.listarHistorialProyectos(otro)).hasSize(1);
    }

    // ---------- Utilidades ----------

    private ColProyectoDisponibleView vistaDe(Proyecto proyecto, Usuario colaborador) {
        return colaboradorProyectoService.listarTodosLosProyectos(colaborador).stream()
                .filter(v -> v.getProyecto().getId().equals(proyecto.getId()))
                .findFirst().orElseThrow();
    }

    private ColPerfilRequeridoView perfil(ColProyectoDisponibleView vista, Habilidad habilidad) {
        return vista.getPerfiles().stream()
                .filter(p -> p.getHabilidadId().equals(habilidad.getId()))
                .findFirst().orElseThrow();
    }

    private Usuario usuario(String prefijo, String nombreRol, String horasDisponibles) {
        Usuario u = new Usuario();
        u.setCorreo(prefijo + "." + UUID.randomUUID() + "@test.local");
        u.setNombre(prefijo);
        u.setApellido("Prueba");
        u.setRol(rol(nombreRol));
        u.setActivo(true);
        if (horasDisponibles != null) {
            u.setHorasContratadasSemana(new BigDecimal("40.00"));
            u.setHorasDisponibles(new BigDecimal(horasDisponibles));
        }
        return usuarioRepository.save(u);
    }

    private Rol rol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol r = new Rol();
            r.setNombre(nombre);
            return rolRepository.save(r);
        });
    }

    private Proyecto proyecto(Usuario pm, Usuario rm, EstadoProyecto estado, int requeridos) {
        Proyecto p = new Proyecto();
        p.setNombre("Proyecto solicitud " + UUID.randomUUID());
        p.setDescripcion("Proyecto de prueba.");
        p.setEstado(estado);
        p.setPrioridad(Prioridad.MEDIA);
        p.setJustificacionPrioridad("Prueba.");
        p.setColaboradoresRequeridos(requeridos);
        p.setHorasSemanalesRequeridas(new BigDecimal("12.00"));
        p.setPm(pm);
        p.setRmRevisor(rm);
        p.setPresupuesto(new BigDecimal("10000.00"));
        p.setFechaInicio(LocalDate.now().minusMonths(1));
        p.setFechaFinEstimada(LocalDate.now().plusMonths(3));
        return proyectoRepository.save(p);
    }

    private Habilidad habilidad(String nombre) {
        CategoriaHabilidad categoria = categoriaHabilidadRepository.findAll().stream().findFirst().orElseGet(() -> {
            CategoriaHabilidad c = new CategoriaHabilidad();
            c.setNombre("Categoria " + UUID.randomUUID());
            return categoriaHabilidadRepository.save(c);
        });
        Habilidad h = new Habilidad();
        h.setNombre(nombre + " " + UUID.randomUUID().toString().substring(0, 6));
        h.setCategoria(categoria);
        return habilidadRepository.save(h);
    }

    private void requerir(Proyecto proyecto, Habilidad habilidad, int personas, String horas) {
        ProyectoHabilidadRequerida r = new ProyectoHabilidadRequerida();
        r.setProyecto(proyecto);
        r.setHabilidad(habilidad);
        r.setNivelRequerido(NivelDominio.INTERMEDIO);
        r.setCantidadPersonas(personas);
        r.setHorasSemanales(horas == null ? null : new BigDecimal(horas));
        proyectoHabilidadRequeridaRepository.save(r);
    }

    private void validar(Usuario colaborador, Habilidad habilidad, EstadoValidacion estado) {
        ColaboradorHabilidad ch = new ColaboradorHabilidad();
        ch.setColaborador(colaborador);
        ch.setHabilidad(habilidad);
        ch.setNivelDominio(NivelDominio.INTERMEDIO);
        ch.setEstadoValidacion(estado);
        ch.setActivo(true);
        colaboradorHabilidadRepository.save(ch);
    }

    private Asignacion asignacion(Proyecto proyecto, Usuario colaborador, Habilidad habilidad,
                                  EstadoAsignacion estado, OrigenAsignacion origen, LocalDateTime fechaSolicitud) {
        Asignacion a = new Asignacion();
        a.setProyecto(proyecto);
        a.setColaborador(colaborador);
        a.setHabilidadSolicitada(habilidad);
        a.setHorasSemanales(new BigDecimal("8"));
        a.setOrigen(origen);
        a.setEstado(estado);
        a.setFechaSolicitud(fechaSolicitud);
        return asignacionRepository.save(a);
    }
}