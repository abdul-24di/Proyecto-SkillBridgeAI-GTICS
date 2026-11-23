package com.pucp.skillb_ia;

import com.pucp.skillb_ia.dto.ColBonoMensualView;
import com.pucp.skillb_ia.dto.ColHorasResumenView;
import com.pucp.skillb_ia.dto.ColProyectoAvanceView;
import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.*;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.service.col.ColaboradorActividadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ColaboradorActividadResumenTests {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private ActividadRepository actividadRepository;
    @Autowired private PenalizacionRepository penalizacionRepository;
    @Autowired private ConfiguracionSistemaRepository configuracionSistemaRepository;
    @Autowired private CursoRepository cursoRepository;
    @Autowired private ColaboradorCursoRepository colaboradorCursoRepository;
    @Autowired private ColaboradorActividadService actividadService;

    // ======================= Listados =======================

    @Test
    void misActividadesSalenPorFechaLimiteSoloLasActivasYLasMias() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario col = usuario("col", "COLABORADOR", null);
        Usuario otro = usuario("otro", "COLABORADOR", null);
        Proyecto p = proyecto(pm);
        asignar(col, p, EstadoAsignacion.ACTIVA);
        LocalDate hoy = LocalDate.now();
        Actividad lejana = actividad(p, col, pm, hoy.plusDays(20), EstadoActividad.PENDIENTE, "4");
        Actividad cercana = actividad(p, col, pm, hoy.plusDays(2), EstadoActividad.EN_PROGRESO, "4");
        Actividad borrada = actividad(p, col, pm, hoy.plusDays(5), EstadoActividad.PENDIENTE, "4");
        borrada.setActivo(false);
        actividadRepository.save(borrada);
        actividad(p, otro, pm, hoy.plusDays(1), EstadoActividad.PENDIENTE, "4");

        List<Actividad> mias = actividadService.listarMisActividades(col);

        assertThat(mias).extracting(Actividad::getId).containsExactly(cercana.getId(), lejana.getId());
    }

    @Test
    void unaActividadVencidaSinEntregarGeneraUnStrikeUnaSolaVez() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario col = usuario("col", "COLABORADOR", null);
        Proyecto p = proyecto(pm);
        asignar(col, p, EstadoAsignacion.ACTIVA);
        Actividad vencida = actividad(p, col, pm, LocalDate.now().minusDays(2), EstadoActividad.EN_PROGRESO, "4");
        actividad(p, col, pm, LocalDate.now().plusDays(3), EstadoActividad.EN_PROGRESO, "4"); // vigente: sin strike

        actividadService.listarMisActividades(col);
        actividadService.listarMisActividades(col); // volver a entrar no duplica el strike

        List<Penalizacion> strikes = actividadService.listarMisPenalizaciones(col);
        assertThat(strikes).hasSize(1);
        assertThat(strikes.get(0).getActividad().getId()).isEqualTo(vencida.getId());
        assertThat(strikes.get(0).getTipo()).isEqualTo(TipoPenalizacion.ACTIVIDAD_TARDIA);
    }

    @Test
    void unaActividadVencidaPeroYaEnRevisionOCompletadaNoGeneraStrikeAlListar() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario col = usuario("col", "COLABORADOR", null);
        Proyecto p = proyecto(pm);
        asignar(col, p, EstadoAsignacion.ACTIVA);
        actividad(p, col, pm, LocalDate.now().minusDays(2), EstadoActividad.EN_REVISION, "4");
        actividad(p, col, pm, LocalDate.now().minusDays(3), EstadoActividad.COMPLETADA, "4");

        actividadService.listarMisActividades(col);

        assertThat(actividadService.listarMisPenalizaciones(col)).isEmpty();
    }

    @Test
    void lasPendientesExcluyenCompletadasYProyectosDondeYaNoEstaActivo() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario col = usuario("col", "COLABORADOR", null);
        Proyecto vigente = proyecto(pm);
        Proyecto expulsado = proyecto(pm);
        asignar(col, vigente, EstadoAsignacion.ACTIVA);
        asignar(col, expulsado, EstadoAsignacion.FINALIZADA);
        LocalDate futuro = LocalDate.now().plusDays(10);
        Actividad pendiente = actividad(vigente, col, pm, futuro, EstadoActividad.PENDIENTE, "4");
        Actividad enRevision = actividad(vigente, col, pm, futuro, EstadoActividad.EN_REVISION, "4");
        actividad(vigente, col, pm, futuro, EstadoActividad.COMPLETADA, "4");
        actividad(expulsado, col, pm, futuro, EstadoActividad.PENDIENTE, "4");

        List<Actividad> pendientes = actividadService.listarActividadesPendientes(col);

        assertThat(pendientes).extracting(Actividad::getId)
                .containsExactlyInAnyOrder(pendiente.getId(), enRevision.getId());
    }

    // ======================= Avance =======================

    @Test
    void sinActividadesElAvanceEsCero() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario col = usuario("col", "COLABORADOR", null);
        Proyecto p = proyecto(pm);

        assertThat(actividadService.calcularAvance(p, col)).isZero();
    }

    @Test
    void elAvanceEsElPorcentajeDeActividadesCompletadasRedondeado() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario col = usuario("col", "COLABORADOR", null);
        Proyecto p = proyecto(pm);
        LocalDate futuro = LocalDate.now().plusDays(10);
        actividad(p, col, pm, futuro, EstadoActividad.COMPLETADA, "4");
        actividad(p, col, pm, futuro, EstadoActividad.EN_PROGRESO, "4");
        actividad(p, col, pm, futuro, EstadoActividad.PENDIENTE, "4");

        assertThat(actividadService.calcularAvance(p, col)).isEqualTo(33); // 1 de 3

        actividad(p, col, pm, futuro, EstadoActividad.COMPLETADA, "4");
        assertThat(actividadService.calcularAvance(p, col)).isEqualTo(50); // 2 de 4

        Actividad ultima = actividad(p, col, pm, futuro, EstadoActividad.COMPLETADA, "4");
        assertThat(actividadService.calcularAvance(p, col)).isEqualTo(60); // 3 de 5
        assertThat(ultima.getId()).isNotNull();
    }

    @Test
    void lasActividadesEliminadasYLasDeOtrosNoCuentanParaElAvance() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario col = usuario("col", "COLABORADOR", null);
        Usuario otro = usuario("otro", "COLABORADOR", null);
        Proyecto p = proyecto(pm);
        LocalDate futuro = LocalDate.now().plusDays(10);
        actividad(p, col, pm, futuro, EstadoActividad.COMPLETADA, "4");
        actividad(p, col, pm, futuro, EstadoActividad.PENDIENTE, "4");
        Actividad borrada = actividad(p, col, pm, futuro, EstadoActividad.PENDIENTE, "4");
        borrada.setActivo(false);
        actividadRepository.save(borrada);
        actividad(p, otro, pm, futuro, EstadoActividad.COMPLETADA, "4");

        assertThat(actividadService.calcularAvance(p, col)).isEqualTo(50);
    }

    @Test
    void elAvanceSoloSeCalculaParaLosProyectosDondeEstaActivo() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario col = usuario("col", "COLABORADOR", null);
        Proyecto activo = proyecto(pm);
        Proyecto finalizado = proyecto(pm);
        Proyecto pendiente = proyecto(pm);
        Asignacion a1 = asignar(col, activo, EstadoAsignacion.ACTIVA);
        Asignacion a2 = asignar(col, finalizado, EstadoAsignacion.FINALIZADA);
        Asignacion a3 = asignar(col, pendiente, EstadoAsignacion.PENDIENTE);
        actividad(activo, col, pm, LocalDate.now().plusDays(3), EstadoActividad.COMPLETADA, "4");

        List<ColProyectoAvanceView> vistas =
                actividadService.listarProyectosActivosConAvance(col, List.of(a1, a2, a3));

        assertThat(vistas).hasSize(1);
        assertThat(vistas.get(0).getProyecto().getId()).isEqualTo(activo.getId());
        assertThat(vistas.get(0).getPorcentajeAvance()).isEqualTo(100);
    }

    // ======================= Resumen de horas (meta 160 h) =======================

    @Test
    void lasHorasDelMesSumanActividadesCompletadasEntregadasEsteMes() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario col = usuario("col", "COLABORADOR", null);
        Proyecto p = proyecto(pm);
        LocalDateTime ahora = LocalDateTime.now();
        entregada(p, col, pm, "40", EstadoActividad.COMPLETADA, ahora);
        entregada(p, col, pm, "20", EstadoActividad.COMPLETADA, ahora);
        // No cuentan: de otro mes, sin completar o sin fecha de entrega.
        entregada(p, col, pm, "30", EstadoActividad.COMPLETADA, ahora.minusMonths(1));
        entregada(p, col, pm, "50", EstadoActividad.EN_REVISION, ahora);
        entregada(p, col, pm, "60", EstadoActividad.COMPLETADA, null);

        ColHorasResumenView resumen = actividadService.obtenerResumenHoras(col);

        assertThat(resumen.getHorasTrabajadasMes()).isEqualByComparingTo("60");
        assertThat(resumen.getMetaMensual()).isEqualByComparingTo("160");
        assertThat(resumen.getHorasFaltantesMes()).isEqualByComparingTo("100");
        assertThat(resumen.getPorcentajeMeta()).isEqualTo(38); // 60/160 = 37.5 -> 38
        assertThat(resumen.isMetaCumplida()).isFalse();
    }

    @Test
    void lasHorasDeCapacitacionDeCursosCompletadosEsteMesTambienSumanALaMeta() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario col = usuario("col", "COLABORADOR", null);
        Proyecto p = proyecto(pm);
        entregada(p, col, pm, "100", EstadoActividad.COMPLETADA, LocalDateTime.now());
        Curso curso = curso(pm, "12");
        Curso viejo = curso(pm, "30");
        Curso enCurso = curso(pm, "40");
        inscripcion(col, curso, EstadoColaboradorCurso.COMPLETADO, LocalDateTime.now());
        inscripcion(col, viejo, EstadoColaboradorCurso.COMPLETADO, LocalDateTime.now().minusMonths(2));
        inscripcion(col, enCurso, EstadoColaboradorCurso.EN_CURSO, null);

        ColHorasResumenView resumen = actividadService.obtenerResumenHoras(col);

        assertThat(resumen.getHorasTrabajadasMes()).isEqualByComparingTo("112");
    }

    @Test
    void alLlegarALaMetaNoFaltanHorasYElProgresoSeTopeaEnCien() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario col = usuario("col", "COLABORADOR", null);
        Proyecto p = proyecto(pm);
        entregada(p, col, pm, "200", EstadoActividad.COMPLETADA, LocalDateTime.now());

        ColHorasResumenView resumen = actividadService.obtenerResumenHoras(col);

        assertThat(resumen.getHorasFaltantesMes()).isEqualByComparingTo("0");
        assertThat(resumen.getPorcentajeMeta()).isEqualTo(100);
        assertThat(resumen.isMetaCumplida()).isTrue();
    }

    @Test
    void lasHorasComprometidasSonLasContratadasMenosLasDisponibles() {
        Usuario col = usuario("col", "COLABORADOR", null);
        col.setHorasContratadasSemana(new BigDecimal("40"));
        col.setHorasDisponibles(new BigDecimal("15"));
        usuarioRepository.save(col);

        ColHorasResumenView resumen = actividadService.obtenerResumenHoras(col);

        assertThat(resumen.getHorasContratadasSemana()).isEqualByComparingTo("40");
        assertThat(resumen.getHorasDisponibles()).isEqualByComparingTo("15");
        assertThat(resumen.getHorasComprometidas()).isEqualByComparingTo("25");
        assertThat(resumen.getHorasTrabajadasMes()).isEqualByComparingTo("0");
        assertThat(resumen.getPorcentajeMeta()).isZero();
    }

    // ======================= Bono por horas extra =======================

    @Test
    void sinSuperarLas160HorasNoHayBono() {
        configurarTope("20");
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario col = usuario("col", "COLABORADOR", "3200");
        Proyecto p = proyecto(pm);
        entregada(p, col, pm, "150", EstadoActividad.COMPLETADA, LocalDateTime.now());

        ColBonoMensualView bono = actividadService.obtenerResumenBonoMensual(col);

        assertThat(bono.getHorasExtra()).isEqualByComparingTo("0");
        assertThat(bono.getHorasExtraBonificables()).isEqualByComparingTo("0");
        assertThat(bono.getMontoBono()).isEqualByComparingTo("0");
    }

    @Test
    void lasHorasExtraDentroDelTopeSePaganALaTarifaDeLaHoraNormal() {
        configurarTope("20");
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario col = usuario("col", "COLABORADOR", "3200"); // valor hora = 3200 / 160 = 20
        Proyecto p = proyecto(pm);
        entregada(p, col, pm, "170", EstadoActividad.COMPLETADA, LocalDateTime.now());

        ColBonoMensualView bono = actividadService.obtenerResumenBonoMensual(col);

        assertThat(bono.getHorasExtra()).isEqualByComparingTo("10");
        assertThat(bono.getHorasExtraBonificables()).isEqualByComparingTo("10");
        assertThat(bono.getMontoBono()).isEqualByComparingTo("200");
        assertThat(bono.getPorcentajeProgreso()).isEqualTo(50);
    }

    @Test
    void lasHorasExtraPorEncimaDelTopeConfigurableNoSePagan() {
        configurarTope("5");
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario col = usuario("col", "COLABORADOR", "3200");
        Proyecto p = proyecto(pm);
        entregada(p, col, pm, "195", EstadoActividad.COMPLETADA, LocalDateTime.now());

        ColBonoMensualView bono = actividadService.obtenerResumenBonoMensual(col);

        assertThat(bono.getTopeHorasExtra()).isEqualByComparingTo("5");
        assertThat(bono.getHorasExtra()).isEqualByComparingTo("35");
        assertThat(bono.getHorasExtraBonificables()).isEqualByComparingTo("5");
        assertThat(bono.getMontoBono()).isEqualByComparingTo("100");
        assertThat(bono.getPorcentajeProgreso()).isEqualTo(100);
    }

    @Test
    void unTopeInvalidoEnLaConfiguracionUsaElValorPorDefectoDe20() {
        configurarTope("no-es-numero");
        Usuario col = usuario("col", "COLABORADOR", "3200");

        assertThat(actividadService.obtenerResumenBonoMensual(col).getTopeHorasExtra()).isEqualByComparingTo("20");
    }

    @Test
    void sinSueldoBaseElBonoEsCero() {
        configurarTope("20");
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario col = usuario("col", "COLABORADOR", null);
        Proyecto p = proyecto(pm);
        entregada(p, col, pm, "180", EstadoActividad.COMPLETADA, LocalDateTime.now());

        ColBonoMensualView bono = actividadService.obtenerResumenBonoMensual(col);

        assertThat(bono.getHorasExtraBonificables()).isEqualByComparingTo("20");
        assertThat(bono.getMontoBono()).isEqualByComparingTo("0");
    }

    @Test
    void siTuvoDescuentoPorStrikesEsteMesSePierdeElBono() {
        configurarTope("20");
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario col = usuario("col", "COLABORADOR", "3200");
        Proyecto p = proyecto(pm);
        entregada(p, col, pm, "180", EstadoActividad.COMPLETADA, LocalDateTime.now());
        strikes(col, p, 3, LocalDateTime.now());

        ColBonoMensualView bono = actividadService.obtenerResumenBonoMensual(col);

        assertThat(bono.isTuvoDescuentoEsteMes()).isTrue();
        assertThat(bono.getMontoBono()).isEqualByComparingTo("0");
    }

    // ======================= Strikes y descuentos =======================

    @Test
    void cadaBloqueDeTresStrikesDelMesDescuentaUnCincoPorCiento() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario col = usuario("col", "COLABORADOR", "3200");
        Proyecto p = proyecto(pm);

        strikes(col, p, 2, LocalDateTime.now());
        assertThat(actividadService.obtenerPorcentajeDescuentoMesActual(col)).isZero();
        assertThat(actividadService.obtenerMontoDescuentoMesActual(col)).isEqualByComparingTo("0");

        strikes(col, p, 1, LocalDateTime.now()); // 3 en total
        assertThat(actividadService.obtenerPorcentajeDescuentoMesActual(col)).isEqualTo(5);
        assertThat(actividadService.obtenerMontoDescuentoMesActual(col)).isEqualByComparingTo("160");

        strikes(col, p, 3, LocalDateTime.now()); // 6 en total
        assertThat(actividadService.obtenerPorcentajeDescuentoMesActual(col)).isEqualTo(10);
        assertThat(actividadService.obtenerMontoDescuentoMesActual(col)).isEqualByComparingTo("320");
    }

    @Test
    void losStrikesDeOtrosMesesNoCuentanParaElDescuentoActual() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario col = usuario("col", "COLABORADOR", "3200");
        Proyecto p = proyecto(pm);
        strikes(col, p, 5, LocalDateTime.now().minusMonths(2));

        assertThat(actividadService.obtenerPorcentajeDescuentoMesActual(col)).isZero();
    }

    @Test
    void sinSueldoBaseElMontoDelDescuentoEsCeroAunqueHayaPorcentaje() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario col = usuario("col", "COLABORADOR", null);
        Proyecto p = proyecto(pm);
        strikes(col, p, 3, LocalDateTime.now());

        assertThat(actividadService.obtenerPorcentajeDescuentoMesActual(col)).isEqualTo(5);
        assertThat(actividadService.obtenerMontoDescuentoMesActual(col)).isEqualByComparingTo("0");
    }

    @Test
    void lasPenalizacionesSalenDeLaMasRecienteALaMasAntigua() {
        Usuario pm = usuario("pm", "PROJECT_MANAGER", null);
        Usuario col = usuario("col", "COLABORADOR", null);
        Proyecto p = proyecto(pm);
        strike(col, p, "Antigua", LocalDateTime.now().minusDays(10));
        strike(col, p, "Reciente", LocalDateTime.now().minusDays(1));
        strike(col, p, "Intermedia", LocalDateTime.now().minusDays(5));

        assertThat(actividadService.listarMisPenalizaciones(col)).extracting(Penalizacion::getMotivo)
                .containsExactly("Reciente", "Intermedia", "Antigua");
    }

    // ---------- Utilidades ----------

    private void configurarTope(String valor) {
        ConfiguracionSistema c = configuracionSistemaRepository.findByClave("TOPE_HORAS_EXTRA_BONO")
                .orElseGet(() -> {
                    ConfiguracionSistema nueva = new ConfiguracionSistema();
                    nueva.setClave("TOPE_HORAS_EXTRA_BONO");
                    return nueva;
                });
        c.setValor(valor);
        c.setDescripcion("Tope de horas extra pagables como bono");
        configuracionSistemaRepository.save(c);
    }

    private void strikes(Usuario col, Proyecto p, int cantidad, LocalDateTime fecha) {
        for (int i = 0; i < cantidad; i++) {
            strike(col, p, "Strike " + UUID.randomUUID(), fecha);
        }
    }

    private void strike(Usuario col, Proyecto p, String motivo, LocalDateTime fecha) {
        Penalizacion s = new Penalizacion();
        s.setColaborador(col);
        s.setProyecto(p);
        s.setTipo(TipoPenalizacion.ACTIVIDAD_TARDIA);
        s.setMotivo(motivo);
        s.setMonto(BigDecimal.ONE);
        s.setFecha(fecha);
        penalizacionRepository.save(s);
    }

    private Usuario usuario(String prefijo, String nombreRol, String sueldoBase) {
        Usuario u = new Usuario();
        u.setCorreo(prefijo + "." + UUID.randomUUID() + "@test.local");
        u.setNombre(prefijo);
        u.setApellido("Prueba");
        u.setRol(rol(nombreRol));
        u.setActivo(true);
        u.setHorasContratadasSemana(new BigDecimal("40.00"));
        u.setHorasDisponibles(new BigDecimal("40.00"));
        if (sueldoBase != null) {
            u.setSueldoBase(new BigDecimal(sueldoBase));
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

    private Proyecto proyecto(Usuario pm) {
        Proyecto p = new Proyecto();
        p.setNombre("Proyecto resumen " + UUID.randomUUID());
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

    private Asignacion asignar(Usuario col, Proyecto p, EstadoAsignacion estado) {
        Asignacion a = new Asignacion();
        a.setProyecto(p);
        a.setColaborador(col);
        a.setHorasSemanales(new BigDecimal("8"));
        a.setOrigen(OrigenAsignacion.PROPUESTA_PM);
        a.setEstado(estado);
        a.setAprobadoPorPm(true);
        a.setAprobadoPorRm(true);
        return asignacionRepository.save(a);
    }

    private Actividad actividad(Proyecto p, Usuario col, Usuario pm, LocalDate limite,
                                EstadoActividad estado, String horas) {
        Actividad a = new Actividad();
        a.setProyecto(p);
        a.setColaborador(col);
        a.setCreadoPor(pm);
        a.setTitulo("Actividad " + UUID.randomUUID());
        a.setHorasEstimadas(new BigDecimal(horas));
        a.setFechaAsignacion(LocalDateTime.now().minusDays(30));
        a.setFechaLimite(limite);
        a.setEstado(estado);
        return actividadRepository.save(a);
    }

    private void entregada(Proyecto p, Usuario col, Usuario pm, String horas, EstadoActividad estado,
                           LocalDateTime fechaEntrega) {
        Actividad a = actividad(p, col, pm, LocalDate.now().plusDays(30), estado, horas);
        a.setFechaEntrega(fechaEntrega);
        actividadRepository.save(a);
    }

    private Curso curso(Usuario creador, String horas) {
        Curso c = new Curso();
        c.setNombre("Curso " + UUID.randomUUID().toString().substring(0, 6));
        c.setDescripcion("Curso de prueba.");
        c.setCategoria("Técnico");
        c.setHoras(new BigDecimal(horas));
        c.setActivo(true);
        c.setCreadoPor(creador);
        return cursoRepository.save(c);
    }

    private void inscripcion(Usuario col, Curso curso, EstadoColaboradorCurso estado, LocalDateTime completado) {
        ColaboradorCurso cc = new ColaboradorCurso();
        cc.setColaborador(col);
        cc.setCurso(curso);
        cc.setOrigen(OrigenCurso.SOLICITUD_COLABORADOR);
        cc.setEstado(estado);
        cc.setFechaCompletado(completado);
        colaboradorCursoRepository.save(cc);
    }
}