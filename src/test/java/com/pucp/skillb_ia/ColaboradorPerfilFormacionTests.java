package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.*;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.service.col.ColaboradorPerfilService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ColaboradorPerfilFormacionTests {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private CategoriaHabilidadRepository categoriaHabilidadRepository;
    @Autowired private HabilidadRepository habilidadRepository;
    @Autowired private ColaboradorHabilidadRepository colaboradorHabilidadRepository;
    @Autowired private CertificadoRepository certificadoRepository;
    @Autowired private EducacionRepository educacionRepository;
    @Autowired private ExperienciaProfesionalRepository experienciaProfesionalRepository;
    @Autowired private LogAuditoriaRepository logAuditoriaRepository;
    @Autowired private NotificacionRepository notificacionRepository;
    @Autowired private ColaboradorPerfilService perfilService;

    // ======================= Agregar habilidad del catálogo =======================

    @Test
    void agregarUnaHabilidadLaDejaPendienteYEnviaSuCertificadoAlRm() {
        usuario("rm", "RESOURCE_MANAGER");
        Usuario col = usuario("col", "COLABORADOR");
        Habilidad java = habilidad("Java");

        perfilService.agregarHabilidad(col, java.getId(), null, null, NivelDominio.INTERMEDIO, pdf());

        ColaboradorHabilidad ch = colaboradorHabilidadRepository.findByColaboradorAndHabilidad(col, java).orElseThrow();
        assertThat(ch.isActivo()).isTrue();
        assertThat(ch.getNivelDominio()).isEqualTo(NivelDominio.INTERMEDIO);
        assertThat(ch.getEstadoValidacion()).isEqualTo(EstadoValidacion.PENDIENTE);

        List<Certificado> certificados = certificadoRepository.findByColaboradorIdConDetalle(col.getId());
        assertThat(certificados).hasSize(1);
        assertThat(certificados.get(0).getEstado()).isEqualTo(EstadoCertificado.PENDIENTE);
        assertThat(certificados.get(0).getArchivoUrl()).isNotBlank().endsWith(".pdf");
        // El sistema reparte la revisión entre los RM activos: debe quedar uno asignado.
        assertThat(certificados.get(0).getRmResponsable()).isNotNull();
        assertThat(certificados.get(0).getRmResponsable().getRol().getNombre()).isEqualTo("RESOURCE_MANAGER");

        assertThat(logAuditoriaRepository.findTop20ByEntidadOrderByFechaHoraDesc("COLABORADOR_HABILIDAD"))
                .anyMatch(l -> "AGREGAR_HABILIDAD".equals(l.getAccion()));
    }

    @Test
    void sinNivelOSinCertificadoOConCertificadoInvalidoNoSeAgregaLaHabilidad() {
        usuario("rm", "RESOURCE_MANAGER");
        Usuario col = usuario("col", "COLABORADOR");
        Habilidad java = habilidad("Java");

        assertThatThrownBy(() -> perfilService.agregarHabilidad(col, java.getId(), null, null, null, pdf()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selecciona un nivel.");
        assertThatThrownBy(() -> perfilService.agregarHabilidad(col, java.getId(), null, null, NivelDominio.BASICO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Debes adjuntar un certificado.");
        assertThatThrownBy(() -> perfilService.agregarHabilidad(col, java.getId(), null, null, NivelDominio.BASICO,
                new MockMultipartFile("c", "c.txt", "text/plain", new byte[]{1})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El certificado debe estar en formato PDF, JPG o PNG.");
        assertThatThrownBy(() -> perfilService.agregarHabilidad(col, java.getId(), null, null, NivelDominio.BASICO,
                new MockMultipartFile("c", "c.pdf", "application/pdf", new byte[10 * 1024 * 1024 + 1])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El certificado supera el máximo de 10MB.");

        assertThat(colaboradorHabilidadRepository.findByColaboradorAndActivoTrue(col)).isEmpty();
        assertThat(certificadoRepository.findByColaboradorIdConDetalle(col.getId())).isEmpty();
    }

    @Test
    void unaHabilidadInexistenteOInactivaDelCatalogoSeRechaza() {
        usuario("rm", "RESOURCE_MANAGER");
        Usuario col = usuario("col", "COLABORADOR");
        Habilidad inactiva = habilidad("Cobol");
        inactiva.setActiva(false);
        habilidadRepository.save(inactiva);

        assertThatThrownBy(() -> perfilService.agregarHabilidad(col, 987654321L, null, null, NivelDominio.BASICO, pdf()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La habilidad seleccionada no existe");
        assertThatThrownBy(() -> perfilService.agregarHabilidad(col, inactiva.getId(), null, null, NivelDominio.BASICO, pdf()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La habilidad seleccionada no existe");
    }

    @Test
    void noSePuedeAgregarDosVecesLaMismaHabilidadActiva() {
        usuario("rm", "RESOURCE_MANAGER");
        Usuario col = usuario("col", "COLABORADOR");
        Habilidad java = habilidad("Java");
        perfilService.agregarHabilidad(col, java.getId(), null, null, NivelDominio.BASICO, pdf());

        assertThatThrownBy(() -> perfilService.agregarHabilidad(col, java.getId(), null, null, NivelDominio.AVANZADO, pdf()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ya tienes esa habilidad agregada.");

        assertThat(certificadoRepository.findByColaboradorIdConDetalle(col.getId())).hasSize(1);
    }

    @Test
    void unaHabilidadQuitadaSeReactivaEnLaMismaFilaYVuelveAPendiente() {
        usuario("rm", "RESOURCE_MANAGER");
        Usuario col = usuario("col", "COLABORADOR");
        Habilidad java = habilidad("Java");
        perfilService.agregarHabilidad(col, java.getId(), null, null, NivelDominio.BASICO, pdf());
        ColaboradorHabilidad ch = colaboradorHabilidadRepository.findByColaboradorAndHabilidad(col, java).orElseThrow();
        ch.setEstadoValidacion(EstadoValidacion.VALIDADA);
        colaboradorHabilidadRepository.save(ch);

        perfilService.eliminarHabilidad(col, java.getId());
        assertThat(perfilService.listarHabilidades(col)).isEmpty();

        perfilService.agregarHabilidad(col, java.getId(), null, null, NivelDominio.AVANZADO, pdf());

        List<ColaboradorHabilidad> activas = perfilService.listarHabilidades(col);
        assertThat(activas).hasSize(1);
        assertThat(activas.get(0).getNivelDominio()).isEqualTo(NivelDominio.AVANZADO);
        assertThat(activas.get(0).getEstadoValidacion()).isEqualTo(EstadoValidacion.PENDIENTE);
    }

    // ======================= Crear una habilidad nueva =======================

    @Test
    void sePuedeCrearUnaHabilidadNuevaEnUnaCategoriaActiva() {
        usuario("rm", "RESOURCE_MANAGER");
        Usuario col = usuario("col", "COLABORADOR");
        CategoriaHabilidad categoria = categoria();
        String nombre = "Zig-" + UUID.randomUUID().toString().substring(0, 6);

        perfilService.agregarHabilidad(col, null, "  " + nombre + "  ", categoria.getId(), NivelDominio.BASICO, pdf());

        Habilidad creada = habilidadRepository.findFirstByNombreIgnoreCaseOrderByIdAsc(nombre).orElseThrow();
        assertThat(creada.isActiva()).isTrue();
        assertThat(creada.getCategoria().getId()).isEqualTo(categoria.getId());
        assertThat(perfilService.listarHabilidades(col))
                .extracting(h -> h.getHabilidad().getId()).containsExactly(creada.getId());
    }

    @Test
    void laHabilidadNuevaExigeNombreCategoriaYUnNombreNoRepetido() {
        usuario("rm", "RESOURCE_MANAGER");
        Usuario col = usuario("col", "COLABORADOR");
        CategoriaHabilidad categoria = categoria();
        Habilidad existente = habilidad("Python");

        assertThatThrownBy(() -> perfilService.agregarHabilidad(col, null, "   ", categoria.getId(), NivelDominio.BASICO, pdf()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Escribe el nombre de la nueva habilidad.");
        assertThatThrownBy(() -> perfilService.agregarHabilidad(col, null, "Nueva", null, NivelDominio.BASICO, pdf()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selecciona una categoría para la nueva habilidad.");
        assertThatThrownBy(() -> perfilService.agregarHabilidad(col, null, "x".repeat(101), categoria.getId(), NivelDominio.BASICO, pdf()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El nombre de la habilidad no puede superar los 100 caracteres.");
        // Ya existe en el catálogo (sin importar mayúsculas): debe elegirla del selector.
        assertThatThrownBy(() -> perfilService.agregarHabilidad(col, null, existente.getNombre().toUpperCase(),
                categoria.getId(), NivelDominio.BASICO, pdf()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ya existe en nuestro catálogo");
        assertThatThrownBy(() -> perfilService.agregarHabilidad(col, null, "Nueva-" + UUID.randomUUID(),
                987654321L, NivelDominio.BASICO, pdf()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La categoría seleccionada no existe");
    }

    // ======================= Quitar y listar habilidades =======================

    @Test
    void quitarUnaHabilidadLaDesactivaSinBorrarlaYQuedaEnLaAuditoria() {
        usuario("rm", "RESOURCE_MANAGER");
        Usuario col = usuario("col", "COLABORADOR");
        Habilidad java = habilidad("Java");
        perfilService.agregarHabilidad(col, java.getId(), null, null, NivelDominio.BASICO, pdf());

        perfilService.eliminarHabilidad(col, java.getId());

        assertThat(colaboradorHabilidadRepository.findByColaboradorAndHabilidad(col, java).orElseThrow().isActivo()).isFalse();
        assertThat(logAuditoriaRepository.findTop20ByEntidadOrderByFechaHoraDesc("COLABORADOR_HABILIDAD"))
                .anyMatch(l -> "ELIMINAR_HABILIDAD".equals(l.getAccion()));
    }

    @Test
    void quitarUnaHabilidadQueNoTieneNoHaceNada() {
        Usuario col = usuario("col", "COLABORADOR");

        perfilService.eliminarHabilidad(col, 987654321L);

        assertThat(perfilService.listarHabilidades(col)).isEmpty();
    }

    @Test
    void lasHabilidadesDisponiblesExcluyenLasQueYaTieneYLasInactivas() {
        usuario("rm", "RESOURCE_MANAGER");
        Usuario col = usuario("col", "COLABORADOR");
        Habilidad tengo = habilidad("Java");
        Habilidad libre = habilidad("Go");
        Habilidad inactiva = habilidad("Cobol");
        inactiva.setActiva(false);
        habilidadRepository.save(inactiva);
        perfilService.agregarHabilidad(col, tengo.getId(), null, null, NivelDominio.BASICO, pdf());

        List<Long> ids = perfilService.listarHabilidadesDisponibles(col).stream().map(Habilidad::getId).toList();

        assertThat(ids).contains(libre.getId());
        assertThat(ids).doesNotContain(tengo.getId(), inactiva.getId());
    }

    @Test
    void elMapaDeCertificadosUsaElMasRecienteDeCadaHabilidad() {
        Usuario col = usuario("col", "COLABORADOR");
        Habilidad java = habilidad("Java");
        certificado(col, java, "/viejo.pdf", EstadoCertificado.RECHAZADO, "Borroso", LocalDateTime.now().minusDays(5));
        certificado(col, java, "/nuevo.pdf", EstadoCertificado.PENDIENTE, null, LocalDateTime.now().minusDays(1));

        Map<Long, String> mapa = perfilService.mapaCertificadosPorHabilidad(col);

        assertThat(mapa).containsEntry(java.getId(), "/nuevo.pdf").hasSize(1);
    }

    @Test
    void elMotivoDeRechazoSoloApareceSiElCertificadoMasRecienteFueRechazado() {
        Usuario col = usuario("col", "COLABORADOR");
        Habilidad rechazada = habilidad("Java");
        Habilidad corregida = habilidad("React");
        certificado(col, rechazada, "/a.pdf", EstadoCertificado.RECHAZADO, "Ilegible", LocalDateTime.now().minusDays(1));
        certificado(col, corregida, "/b.pdf", EstadoCertificado.RECHAZADO, "Vencido", LocalDateTime.now().minusDays(5));
        certificado(col, corregida, "/c.pdf", EstadoCertificado.PENDIENTE, null, LocalDateTime.now().minusDays(1));

        Map<Long, String> motivos = perfilService.mapaMotivosRechazoPorHabilidad(col);

        assertThat(motivos).containsEntry(rechazada.getId(), "Ilegible");
        assertThat(motivos).doesNotContainKey(corregida.getId());
    }

    // ======================= Educación =======================

    @Test
    void laFormacionAcademicaValidaQuedaPendienteYAvisaAlRm() {
        usuario("rm", "RESOURCE_MANAGER");
        Usuario col = usuario("col", "COLABORADOR");

        perfilService.agregarEducacion(col, "  PUCP  ", "  Ingeniería  ", "2018-03-01", "2022-12-15", pdf());

        List<Educacion> lista = perfilService.listarEducacion(col);
        assertThat(lista).hasSize(1);
        Educacion e = lista.get(0);
        assertThat(e.getInstitucion()).isEqualTo("PUCP");
        assertThat(e.getTitulo()).isEqualTo("Ingeniería");
        assertThat(e.getFechaInicio()).isEqualTo(LocalDate.of(2018, 3, 1));
        assertThat(e.getFechaFin()).isEqualTo(LocalDate.of(2022, 12, 15));
        assertThat(e.getEstado()).isEqualTo(EstadoCertificado.PENDIENTE);
        assertThat(e.getArchivoUrl()).isNotBlank();
        assertThat(e.getRmResponsable()).isNotNull();
        assertThat(notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(e.getRmResponsable()))
                .anyMatch(n -> "EDUCACION_PENDIENTE".equals(n.getTipo()) && e.getId().equals(n.getEntidadId()));
    }

    @Test
    void enElPreRegistroLaFormacionNoNotificaAlRmHastaAprobarElRegistro() {
        usuario("rm", "RESOURCE_MANAGER");
        Usuario col = usuario("col", "COLABORADOR");

        perfilService.agregarEducacion(col, "PUCP", "Ingeniería", "2018-03-01", "2022-12-15", pdf(), false);

        Educacion e = perfilService.listarEducacion(col).get(0);
        Usuario rmResponsable = e.getRmResponsable();
        assertThat(rmResponsable).isNotNull();
        assertThat(notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(rmResponsable))
                .noneMatch(n -> e.getId().equals(n.getEntidadId()) && "EDUCACION_PENDIENTE".equals(n.getTipo()));

        perfilService.avisarAlRmDelRegistroAprobado(col);

        assertThat(notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(rmResponsable))
                .filteredOn(n -> "EDUCACION_PENDIENTE".equals(n.getTipo()) && e.getId().equals(n.getEntidadId()))
                .hasSize(1);
    }

    @Test
    void laFormacionAcademicaInvalidaSeRechazaConSuMensaje() {
        usuario("rm", "RESOURCE_MANAGER");
        Usuario col = usuario("col", "COLABORADOR");

        assertThatThrownBy(() -> perfilService.agregarEducacion(col, " ", "Ing", "2018-01-01", "2020-01-01", pdf()))
                .hasMessage("Indica la institución.");
        assertThatThrownBy(() -> perfilService.agregarEducacion(col, "PUCP", null, "2018-01-01", "2020-01-01", pdf()))
                .hasMessage("Indica el título o carrera.");
        assertThatThrownBy(() -> perfilService.agregarEducacion(col, "x".repeat(151), "Ing", "2018-01-01", "2020-01-01", pdf()))
                .hasMessage("La institución no puede superar los 150 caracteres.");
        assertThatThrownBy(() -> perfilService.agregarEducacion(col, "PUCP", "x".repeat(151), "2018-01-01", "2020-01-01", pdf()))
                .hasMessage("El título o carrera no puede superar los 150 caracteres.");
        assertThatThrownBy(() -> perfilService.agregarEducacion(col, "PUCP", "Ing", "", "2020-01-01", pdf()))
                .hasMessage("Indica la fecha de inicio.");
        assertThatThrownBy(() -> perfilService.agregarEducacion(col, "PUCP", "Ing", "2018-01-01", null, pdf()))
                .hasMessage("Indica la fecha de fin.");
        assertThatThrownBy(() -> perfilService.agregarEducacion(col, "PUCP", "Ing", "01/01/2018", "2020-01-01", pdf()))
                .hasMessage("El formato de la fecha de inicio no es válido.");
        assertThatThrownBy(() -> perfilService.agregarEducacion(col, "PUCP", "Ing", "2020-01-01", "2018-01-01", pdf()))
                .hasMessage("La fecha de fin no puede ser anterior a la fecha de inicio.");
        assertThatThrownBy(() -> perfilService.agregarEducacion(col, "PUCP", "Ing", "2018-01-01",
                LocalDate.now().plusDays(1).toString(), pdf()))
                .hasMessage("La fecha de fin no puede ser una fecha futura.");
        assertThatThrownBy(() -> perfilService.agregarEducacion(col, "PUCP", "Ing", "2018-01-01", "2020-01-01", null))
                .hasMessage("Debes adjuntar un certificado.");

        assertThat(perfilService.listarEducacion(col)).isEmpty();
    }

    @Test
    void laFormacionSeEliminaPorSuDuenoOPorUnAdministradorPeroNoPorOtroColaborador() {
        usuario("rm", "RESOURCE_MANAGER");
        Usuario col = usuario("col", "COLABORADOR");
        Usuario otro = usuario("otro", "COLABORADOR");
        Usuario admin = usuario("admin", "ADMINISTRADOR");
        perfilService.agregarEducacion(col, "PUCP", "Ingeniería", "2018-03-01", "2022-12-15", pdf());
        perfilService.agregarEducacion(col, "UNI", "Maestría", "2023-03-01", "2024-12-15", pdf());
        Long primera = perfilService.listarEducacion(col).get(0).getId();
        Long segunda = perfilService.listarEducacion(col).get(1).getId();

        assertThatThrownBy(() -> perfilService.eliminarEducacion(otro, primera))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No puedes eliminar una formación académica que no es tuya.");
        assertThat(perfilService.listarEducacion(col)).hasSize(2);

        perfilService.eliminarEducacion(col, primera);
        perfilService.eliminarEducacion(admin, segunda);

        assertThat(perfilService.listarEducacion(col)).isEmpty();
        // Sigue en la BD (borrado lógico).
        assertThat(educacionRepository.findById(primera).orElseThrow().isActivo()).isFalse();
    }

    @Test
    void eliminarUnaFormacionInexistenteNoHaceNada() {
        Usuario col = usuario("col", "COLABORADOR");

        perfilService.eliminarEducacion(col, 987654321L);

        assertThat(perfilService.listarEducacion(col)).isEmpty();
    }

    // ======================= Experiencia profesional =======================

    @Test
    void laExperienciaValidaSeGuardaYQuedaEnLaAuditoria() {
        Usuario col = usuario("col", "COLABORADOR");

        perfilService.agregarExperiencia(col, experiencia("Acme", "Dev", "Backend", LocalDate.now().minusYears(2),
                LocalDate.now().minusYears(1), false));

        List<ExperienciaProfesional> lista = perfilService.listarExperienciaProfesional(col);
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).getColaborador().getId()).isEqualTo(col.getId());
        assertThat(logAuditoriaRepository.findTop20ByEntidadOrderByFechaHoraDesc("EXPERIENCIA"))
                .anyMatch(l -> "AGREGAR".equals(l.getAccion()));
    }

    @Test
    void siTrabajaAhiActualmenteSeIgnoraLaFechaDeFin() {
        Usuario col = usuario("col", "COLABORADOR");

        perfilService.agregarExperiencia(col, experiencia("Acme", "Dev", "Backend", LocalDate.now().minusYears(1),
                LocalDate.now(), true));

        assertThat(perfilService.listarExperienciaProfesional(col).get(0).getFechaFin()).isNull();
    }

    @Test
    void laExperienciaInvalidaSeRechazaConSuMensaje() {
        Usuario col = usuario("col", "COLABORADOR");
        LocalDate ini = LocalDate.now().minusYears(2);
        LocalDate fin = LocalDate.now().minusYears(1);

        assertThatThrownBy(() -> perfilService.agregarExperiencia(col, experiencia("Acme", " ", "d", ini, fin, false)))
                .hasMessage("El cargo es obligatorio.");
        assertThatThrownBy(() -> perfilService.agregarExperiencia(col, experiencia("Acme", "x".repeat(101), "d", ini, fin, false)))
                .hasMessage("El cargo no puede superar los 100 caracteres.");
        assertThatThrownBy(() -> perfilService.agregarExperiencia(col, experiencia(null, "Dev", "d", ini, fin, false)))
                .hasMessage("La empresa es obligatoria.");
        assertThatThrownBy(() -> perfilService.agregarExperiencia(col, experiencia("x".repeat(151), "Dev", "d", ini, fin, false)))
                .hasMessage("La empresa no puede superar los 150 caracteres.");
        assertThatThrownBy(() -> perfilService.agregarExperiencia(col, experiencia("Acme", "Dev", "  ", ini, fin, false)))
                .hasMessage("Describe brevemente las funciones del cargo.");
        assertThatThrownBy(() -> perfilService.agregarExperiencia(col, experiencia("Acme", "Dev", "x".repeat(501), ini, fin, false)))
                .hasMessage("La descripción no puede superar los 500 caracteres.");
        assertThatThrownBy(() -> perfilService.agregarExperiencia(col, experiencia("Acme", "Dev", "d", null, fin, false)))
                .hasMessage("La fecha de inicio es obligatoria.");
        assertThatThrownBy(() -> perfilService.agregarExperiencia(col,
                experiencia("Acme", "Dev", "d", LocalDate.now().plusDays(1), null, true)))
                .hasMessage("La fecha de inicio no puede ser futura.");
        assertThatThrownBy(() -> perfilService.agregarExperiencia(col, experiencia("Acme", "Dev", "d", ini, null, false)))
                .hasMessage("Indica la fecha de fin o marca que trabaja ahí actualmente.");
        assertThatThrownBy(() -> perfilService.agregarExperiencia(col, experiencia("Acme", "Dev", "d", fin, ini, false)))
                .hasMessage("La fecha de fin no puede ser anterior a la fecha de inicio.");

        assertThat(perfilService.listarExperienciaProfesional(col)).isEmpty();
    }

    @Test
    void laExperienciaSoloLaEliminaSuDuenoYSeConservaComoBorradoLogico() {
        Usuario col = usuario("col", "COLABORADOR");
        Usuario otro = usuario("otro", "COLABORADOR");
        perfilService.agregarExperiencia(col, experiencia("Acme", "Dev", "Backend", LocalDate.now().minusYears(2),
                LocalDate.now().minusYears(1), false));
        Long id = perfilService.listarExperienciaProfesional(col).get(0).getId();

        // Otro colaborador intenta borrarla: se ignora.
        perfilService.eliminarExperiencia(otro, id);
        assertThat(perfilService.listarExperienciaProfesional(col)).hasSize(1);

        perfilService.eliminarExperiencia(col, id);

        assertThat(perfilService.listarExperienciaProfesional(col)).isEmpty();
        assertThat(experienciaProfesionalRepository.findById(id).orElseThrow().isActivo()).isFalse();
        assertThat(logAuditoriaRepository.findTop20ByEntidadOrderByFechaHoraDesc("EXPERIENCIA"))
                .anyMatch(l -> "ELIMINAR".equals(l.getAccion()) && id.equals(l.getEntidadId()));
    }

    @Test
    void cuandoElAdminEliminaLaAuditoriaQuedaAFavorDelAdmin() {
        Usuario col = usuario("col", "COLABORADOR");
        Usuario admin = usuario("admin", "ADMINISTRADOR");
        perfilService.agregarExperiencia(col, experiencia("Acme", "Dev", "Backend", LocalDate.now().minusYears(2),
                LocalDate.now().minusYears(1), false));
        Long id = perfilService.listarExperienciaProfesional(col).get(0).getId();

        perfilService.eliminarExperiencia(col, id, admin);

        assertThat(logAuditoriaRepository.findTop20ByEntidadOrderByFechaHoraDesc("EXPERIENCIA"))
                .anyMatch(l -> "ELIMINAR".equals(l.getAccion()) && admin.getId().equals(l.getUsuario().getId()));
    }

    // ---------- Utilidades ----------

    private MockMultipartFile pdf() {
        return new MockMultipartFile("certificado", "certificado.pdf", "application/pdf", "%PDF-1.4 prueba".getBytes());
    }

    private ExperienciaProfesional experiencia(String empresa, String cargo, String descripcion,
                                               LocalDate inicio, LocalDate fin, boolean actual) {
        ExperienciaProfesional e = new ExperienciaProfesional();
        e.setEmpresa(empresa);
        e.setCargo(cargo);
        e.setDescripcion(descripcion);
        e.setFechaInicio(inicio);
        e.setFechaFin(fin);
        e.setActual(actual);
        return e;
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

    private CategoriaHabilidad categoria() {
        return categoriaHabilidadRepository.findByActivaTrue().stream().findFirst().orElseGet(() -> {
            CategoriaHabilidad c = new CategoriaHabilidad();
            c.setNombre("Categoria " + UUID.randomUUID());
            return categoriaHabilidadRepository.save(c);
        });
    }

    private Habilidad habilidad(String nombre) {
        Habilidad h = new Habilidad();
        h.setNombre(nombre + " " + UUID.randomUUID().toString().substring(0, 6));
        h.setCategoria(categoria());
        return habilidadRepository.save(h);
    }

    private void certificado(Usuario col, Habilidad habilidad, String url, EstadoCertificado estado,
                             String motivo, LocalDateTime fechaSubida) {
        Certificado c = new Certificado();
        c.setColaborador(col);
        c.setHabilidad(habilidad);
        c.setArchivoUrl(url);
        c.setEstado(estado);
        c.setMotivoRechazo(motivo);
        c.setFechaSubida(fechaSubida);
        certificadoRepository.save(c);
    }
}