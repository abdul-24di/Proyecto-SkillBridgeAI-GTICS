package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.Cargo;
import com.pucp.skillb_ia.repository.CargoRepository;
import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.*;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.col.ColaboradorPerfilService;
import com.pucp.skillb_ia.service.rm.RmCertificadoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@ActiveProfiles("test")
class RmCertificadoTests {
    @Autowired private WebApplicationContext context;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CargoRepository cargoRepository;
    @Autowired private CategoriaHabilidadRepository categoriaRepository;
    @Autowired private HabilidadRepository habilidadRepository;
    @Autowired private ColaboradorHabilidadRepository colaboradorHabilidadRepository;
    @Autowired private CertificadoRepository certificadoRepository;
    @Autowired private RmCertificadoService certificadoService;
    @Autowired private ColaboradorPerfilService colaboradorPerfilService;
    @Autowired private NotificacionRepository notificacionRepository;
    @Autowired private LogAuditoriaRepository logAuditoriaRepository;

    private MockMvc mockMvc;
    private Usuario rm;
    private Usuario colaborador;
    private Habilidad habilidad;
    private ColaboradorHabilidad perfilHabilidad;

    @BeforeEach
    void prepararDatos() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        certificadoRepository.deleteAll();
        rm = obtenerUsuario("rm.certificados@skillbridge.test", "Rosa", "RM",
                obtenerRol("RESOURCE_MANAGER"));
        colaborador = obtenerUsuario("col.certificados@skillbridge.test", "Carlos", "Prueba",
                obtenerRol("COLABORADOR"));
        colaborador.setCargo(cargoDePrueba("Backend Developer"));
        colaborador.setNivelExperiencia(NivelExperiencia.JUNIOR);
        colaborador = usuarioRepository.save(colaborador);

        CategoriaHabilidad categoria = categoriaRepository.findByActivaTrue().stream()
                .filter(item -> "Certificados Test".equals(item.getNombre()))
                .findFirst().orElseGet(() -> {
                    CategoriaHabilidad nueva = new CategoriaHabilidad();
                    nueva.setNombre("Certificados Test");
                    return categoriaRepository.save(nueva);
                });
        habilidad = habilidadRepository.findByActivaTrue().stream()
                .filter(item -> "Java Cert Test".equals(item.getNombre()))
                .findFirst().orElseGet(() -> {
                    Habilidad nueva = new Habilidad();
                    nueva.setNombre("Java Cert Test");
                    nueva.setCategoria(categoria);
                    return habilidadRepository.save(nueva);
                });
        perfilHabilidad = colaboradorHabilidadRepository
                .findByColaboradorAndHabilidad(colaborador, habilidad)
                .orElseGet(() -> {
                    ColaboradorHabilidad nueva = new ColaboradorHabilidad();
                    nueva.setColaborador(colaborador);
                    nueva.setHabilidad(habilidad);
                    nueva.setNivelDominio(NivelDominio.BASICO);
                    nueva.setEstadoValidacion(EstadoValidacion.PENDIENTE);
                    return colaboradorHabilidadRepository.save(nueva);
                });
        perfilHabilidad.setNivelDominio(NivelDominio.BASICO);
        perfilHabilidad.setEstadoValidacion(EstadoValidacion.PENDIENTE);
        colaboradorHabilidadRepository.save(perfilHabilidad);
    }

    @AfterEach
    void limpiarSesion() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aprobarValidaHabilidadYPuedeActualizarNiveles() {
        Certificado certificado = guardarPendiente("/uploads/certificados/java-test.pdf");

        certificadoService.aprobar(certificado.getId(), NivelDominio.AVANZADO,
                NivelExperiencia.SENIOR, rm.getId());

        Certificado revisado = certificadoRepository.findById(certificado.getId()).orElseThrow();
        ColaboradorHabilidad habilidadActualizada = colaboradorHabilidadRepository
                .findByColaboradorAndHabilidad(colaborador, habilidad).orElseThrow();
        assertEquals(EstadoCertificado.APROBADO, revisado.getEstado());
        assertNotNull(revisado.getFechaRevision());
        assertEquals(rm.getId(), revisado.getRevisadoPor().getId());
        assertEquals(EstadoValidacion.VALIDADA, habilidadActualizada.getEstadoValidacion());
        assertEquals(NivelDominio.AVANZADO, habilidadActualizada.getNivelDominio());
        assertEquals(NivelExperiencia.SENIOR,
                usuarioRepository.findById(colaborador.getId()).orElseThrow().getNivelExperiencia());
    }

    @Test
    void rechazoExigeMotivoYConservaElRegistro() {
        Certificado certificado = guardarPendiente("/uploads/certificados/rechazo-test.pdf");
        assertThrows(IllegalArgumentException.class,
                () -> certificadoService.rechazar(certificado.getId(), " ", rm.getId()));

        certificadoService.rechazar(certificado.getId(), "La evidencia es ilegible.", rm.getId());
        Certificado rechazado = certificadoRepository.findById(certificado.getId()).orElseThrow();
        assertEquals(EstadoCertificado.RECHAZADO, rechazado.getEstado());
        assertEquals("La evidencia es ilegible.", rechazado.getMotivoRechazo());
        assertEquals(EstadoValidacion.RECHAZADA, colaboradorHabilidadRepository
                .findByColaboradorAndHabilidad(colaborador, habilidad).orElseThrow().getEstadoValidacion());
    }

    @Test
    void nivelGeneralTambienSeActualizaIndependientemente() {
        certificadoService.actualizarNivelExperiencia(
                colaborador.getId(), NivelExperiencia.SEMI_SENIOR, rm.getId());
        assertEquals(NivelExperiencia.SEMI_SENIOR,
                usuarioRepository.findById(colaborador.getId()).orElseThrow().getNivelExperiencia());
    }

    @Test
    void colaboradorSubeCertificadoPendienteYNoDuplicaLaSolicitud() {
        MockMultipartFile archivo = new MockMultipartFile(
                "certificado", "java.pdf", "application/pdf", "%PDF-demo".getBytes());

        Certificado creado = colaboradorPerfilService.subirCertificado(
                colaborador, habilidad.getId(), NivelDominio.INTERMEDIO, archivo);

        assertNotNull(creado.getId());
        assertEquals(EstadoCertificado.PENDIENTE, creado.getEstado());
        assertTrue(creado.getArchivoUrl().startsWith("/uploads/certificados/certificado-"));
        assertEquals(1, colaboradorPerfilService.listarCertificados(colaborador).size());
        assertThrows(IllegalArgumentException.class,
                () -> colaboradorPerfilService.subirCertificado(
                        colaborador, habilidad.getId(), NivelDominio.INTERMEDIO, archivo));
    }

    @Test
    void renderizaBandejaRevisionEHistorial() throws Exception {
        Certificado certificado = guardarPendiente("/uploads/certificados/vista-test.pdf");

        mockMvc.perform(get("/rm/colaboradores/certificados"))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-certificados-pendientes"))
                .andExpect(model().attributeExists("certificados", "totalPendientes", "revisadosHoy"));
        mockMvc.perform(get("/rm/colaboradores/certificados/revision")
                        .param("id", certificado.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-revision-certificado"))
                .andExpect(model().attributeExists("certificado"));
        mockMvc.perform(get("/rm/colaboradores/historial-validaciones")
                        .param("id", colaborador.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-historial-validaciones"))
                .andExpect(model().attributeExists("colaborador", "certificados"));
    }

    @Test
    void colaboradorVeFormularioEHistorialDeCertificadosEnSuPerfil() throws Exception {
        guardarPendiente("/uploads/certificados/perfil-test.pdf");
        Usuario colaboradorConRol = usuarioRepository.findActivosByRolNombre("COLABORADOR").stream()
                .filter(usuario -> usuario.getId().equals(colaborador.getId()))
                .findFirst().orElseThrow();
        UsuarioDetails details = new UsuarioDetails(colaboradorConRol);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        details, null, details.getAuthorities()));
        try {
            mockMvc.perform(get("/colaborador/perfil"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("col/col-perfil"))
                    .andExpect(model().attributeExists(
                            "habilidadesColaborador", "certificadosColaborador"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void bandejaPendientesFiltraPorBusquedaYHabilidadSinDuplicarOpciones() throws Exception {
        Habilidad python = otraHabilidad("Python Cert Test");
        guardarPendiente("/uploads/certificados/java-uno.pdf");
        guardarPendiente("/uploads/certificados/java-dos.pdf");
        guardar("/uploads/certificados/python-uno.pdf", python, EstadoCertificado.PENDIENTE);

        MvcResult sinFiltros = mockMvc.perform(get("/rm/colaboradores/certificados"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalRegistros", 3L))
                .andExpect(model().attribute("totalPendientes", 3))
                .andExpect(model().attribute("habilidades", List.of("Java Cert Test", "Python Cert Test")))
                .andReturn();
        String html = sinFiltros.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(1, contar(html, "<option value=\"Java Cert Test\""));
        assertTrue(html.contains("<button type=\"submit\" class=\"btn btn-primary\">Filtrar</button>"));
        assertFalse(html.contains("rm-certificados-pendientes.js"));

        mockMvc.perform(get("/rm/colaboradores/certificados").param("habilidad", "python cert test"))
                .andExpect(model().attribute("habilidad", "Python Cert Test"))
                .andExpect(model().attribute("totalRegistros", 1L));
        mockMvc.perform(get("/rm/colaboradores/certificados").param("busqueda", "  JAVA-DOS "))
                .andExpect(model().attribute("busqueda", "JAVA-DOS"))
                .andExpect(model().attribute("totalRegistros", 1L));
        mockMvc.perform(get("/rm/colaboradores/certificados").param("habilidad", "Inexistente"))
                .andExpect(model().attribute("habilidad", (Object) null))
                .andExpect(model().attribute("totalRegistros", 3L))
                .andExpect(model().attribute("totalPendientes", 3));
    }

    @Test
    void bandejaPendientesPaginaDeSeisYNormalizaLaPagina() throws Exception {
        for (int i = 1; i <= 8; i++) guardarPendiente("/uploads/certificados/pagina-" + i + ".pdf");

        assertEquals(6, filas(mockMvc.perform(get("/rm/colaboradores/certificados"))
                .andExpect(model().attribute("paginaActual", 1))
                .andExpect(model().attribute("totalPaginas", 2))
                .andReturn()));
        MvcResult segunda = mockMvc.perform(get("/rm/colaboradores/certificados")
                        .param("pagina", "2").param("busqueda", "pagina"))
                .andExpect(model().attribute("paginaActual", 2))
                .andReturn();
        assertEquals(2, filas(segunda));
        String html = segunda.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("Mostrando 7-8 de 8 certificados"));
        String anterior = enlace(html, "Anterior");
        assertTrue(anterior.contains("busqueda=pagina"), anterior);
        assertTrue(anterior.contains("pagina=1"), anterior);

        mockMvc.perform(get("/rm/colaboradores/certificados").param("pagina", "99"))
                .andExpect(model().attribute("paginaActual", 2));
        mockMvc.perform(get("/rm/colaboradores/certificados").param("pagina", "0"))
                .andExpect(model().attribute("paginaActual", 1));
        mockMvc.perform(get("/rm/colaboradores/certificados").param("pagina", "abc"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("paginaActual", 1));
    }

    @Test
    void historialFiltraPorEstadoHabilidadYBusquedaYConservaElIdAlPaginar() throws Exception {
        Habilidad python = otraHabilidad("Python Cert Test");
        for (int i = 1; i <= 7; i++) {
            guardar("/uploads/certificados/aprobado-" + i + ".pdf", habilidad, EstadoCertificado.APROBADO);
        }
        guardar("/uploads/certificados/rechazado-1.pdf", python, EstadoCertificado.RECHAZADO);
        guardar("/uploads/certificados/rechazado-2.pdf", python, EstadoCertificado.RECHAZADO);
        guardarPendiente("/uploads/certificados/pendiente.pdf");
        String id = colaborador.getId().toString();

        mockMvc.perform(get("/rm/colaboradores/historial-validaciones").param("id", id))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalRegistros", 10L))
                .andExpect(model().attribute("totalDocumentos", 10))
                .andExpect(model().attribute("aprobados", 7L))
                .andExpect(model().attribute("habilidades", List.of("Java Cert Test", "Python Cert Test")));
        mockMvc.perform(get("/rm/colaboradores/historial-validaciones").param("id", id)
                        .param("estado", "rechazado"))
                .andExpect(model().attribute("estado", "RECHAZADO"))
                .andExpect(model().attribute("totalRegistros", 2L));
        mockMvc.perform(get("/rm/colaboradores/historial-validaciones").param("id", id)
                        .param("estado", "CUALQUIERA"))
                .andExpect(model().attribute("estado", (Object) null))
                .andExpect(model().attribute("totalRegistros", 10L));
        mockMvc.perform(get("/rm/colaboradores/historial-validaciones").param("id", id)
                        .param("habilidad", "Python Cert Test"))
                .andExpect(model().attribute("totalRegistros", 2L));
        mockMvc.perform(get("/rm/colaboradores/historial-validaciones").param("id", id)
                        .param("busqueda", "pendiente"))
                .andExpect(model().attribute("totalRegistros", 1L));

        MvcResult filtrado = mockMvc.perform(get("/rm/colaboradores/historial-validaciones").param("id", id)
                        .param("busqueda", "aprobado").param("estado", "APROBADO")
                        .param("habilidad", "Java Cert Test").param("pagina", "2"))
                .andExpect(model().attribute("paginaActual", 2))
                .andExpect(model().attribute("totalPaginas", 2))
                .andExpect(model().attribute("totalRegistros", 7L))
                .andReturn();
        assertEquals(1, filas(filtrado));
        String html = filtrado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("Mostrando 7-7 de 7 documentos"));
        assertEquals(1, contar(html, "<option value=\"Java Cert Test\""));
        assertTrue(html.contains("<input type=\"hidden\" name=\"id\" value=\"" + id + "\""));
        assertTrue(html.contains("<button type=\"submit\" class=\"btn btn-primary\">Filtrar</button>"));
        assertFalse(html.contains("rm-historial-validaciones.js"));
        String anterior = enlace(html, "Anterior");
        for (String parametro : List.of("id=" + id, "busqueda=aprobado", "estado=APROBADO",
                "habilidad=Java", "pagina=1")) {
            assertTrue(anterior.contains(parametro), anterior);
        }
    }

    @Test
    void revisionPendienteRechazaEnModalConFormularioPropio() throws Exception {
        Certificado certificado = guardarPendiente("/uploads/certificados/modal-test.pdf");
        MvcResult resultado = mockMvc.perform(get("/rm/colaboradores/certificados/revision")
                        .param("id", certificado.getId().toString()))
                .andExpect(status().isOk())
                .andReturn();
        String html = resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String codigo = ((com.pucp.skillb_ia.dto.RmCertificadoView) resultado.getModelAndView()
                .getModel().get("certificado")).getCodigo();
        String base = "/rm/colaboradores/certificados/" + certificado.getId();

        assertTrue(html.contains("<button type=\"button\" class=\"btn btn-outline-danger\" data-bs-toggle=\"modal\""
                + " data-bs-target=\"#rejectCertificateModal\">Rechazar certificado</button>"), html);

        String tarjeta = html.substring(html.indexOf("Decisión del Resource Manager"), html.indexOf("</main>"));
        assertEquals(1, contar(tarjeta, "<form"));
        assertTrue(tarjeta.contains("<form id=\"approveCertificateForm\" method=\"post\" action=\"" + base + "/aprobar\">"),
                tarjeta);
        assertTrue(tarjeta.contains("name=\"nivelHabilidad\""));
        assertTrue(tarjeta.contains("name=\"nivelGeneral\""));
        assertTrue(tarjeta.contains("<button type=\"button\" class=\"btn btn-success\" data-bs-toggle=\"modal\""
                + " data-bs-target=\"#approveCertificateModal\">Aprobar certificado</button>"), tarjeta);
        assertFalse(tarjeta.contains("type=\"submit\""));
        assertFalse(tarjeta.contains("<textarea"));
        assertFalse(tarjeta.contains("name=\"motivo\""));
        assertFalse(tarjeta.contains("formaction"));

        int inicioAprobacion = html.indexOf("id=\"approveCertificateModal\"");
        assertTrue(inicioAprobacion > html.indexOf("</main>"));
        String aprobacion = html.substring(inicioAprobacion, html.indexOf("id=\"rejectCertificateModal\""));
        assertFalse(aprobacion.contains("<form"));
        assertTrue(aprobacion.contains("Confirmar aprobación"));
        assertTrue(aprobacion.contains("#" + codigo));
        assertTrue(aprobacion.contains("data-bs-dismiss=\"modal\">Cancelar</button>"));
        assertTrue(aprobacion.contains("<button type=\"submit\" form=\"approveCertificateForm\" class=\"btn btn-success\">"
                + "Confirmar aprobación</button>"), aprobacion);

        int inicioModal = html.indexOf("id=\"rejectCertificateModal\"");
        assertTrue(inicioModal > html.indexOf("</main>"));
        String modal = html.substring(inicioModal, html.indexOf("<script", inicioModal));
        assertEquals(1, contar(modal, "<form"));
        assertTrue(modal.contains("<form method=\"post\" action=\"" + base + "/rechazar\">"), modal);
        assertTrue(modal.contains("#" + codigo));
        assertTrue(modal.contains("Java Cert Test"));
        assertTrue(modal.contains("Carlos Prueba"));
        assertTrue(modal.contains("El motivo se notificará al colaborador"));
        Matcher motivo = Pattern.compile("<textarea[^>]*>").matcher(modal);
        assertTrue(motivo.find());
        String textarea = motivo.group();
        assertTrue(textarea.contains("name=\"motivo\""), textarea);
        assertTrue(textarea.contains("required"), textarea);
        assertTrue(textarea.contains("maxlength=\"300\""), textarea);
        assertTrue(modal.contains("data-bs-dismiss=\"modal\">Cancelar</button>"));
        assertTrue(modal.contains("<button type=\"submit\" class=\"btn btn-danger\">Confirmar rechazo</button>"));
        assertEquals(1, contar(html, "name=\"motivo\""));
    }

    @Test
    void aprobarDesdeLaRevisionNoExigeMotivo() throws Exception {
        Certificado certificado = guardarPendiente("/uploads/certificados/aprobar-post.pdf");
        autenticarRm();

        mockMvc.perform(post("/rm/colaboradores/certificados/{id}/aprobar", certificado.getId())
                        .param("nivelHabilidad", "INTERMEDIO").param("nivelGeneral", ""))
                .andExpect(redirectedUrl("/rm/colaboradores/certificados"))
                .andExpect(flash().attribute("mensajeExito", "Certificado aprobado y perfil actualizado."));

        Certificado aprobado = certificadoRepository.findById(certificado.getId()).orElseThrow();
        assertEquals(EstadoCertificado.APROBADO, aprobado.getEstado());
        assertNull(aprobado.getMotivoRechazo());
        assertEquals(NivelDominio.INTERMEDIO, colaboradorHabilidadRepository
                .findByColaboradorAndHabilidad(colaborador, habilidad).orElseThrow().getNivelDominio());
    }

    @Test
    void rechazarSinMotivoLoRechazaElServidorYConservaPendiente() throws Exception {
        Certificado certificado = guardarPendiente("/uploads/certificados/sin-motivo.pdf");
        autenticarRm();

        mockMvc.perform(post("/rm/colaboradores/certificados/{id}/rechazar", certificado.getId())
                        .param("motivo", "   "))
                .andExpect(redirectedUrl("/rm/colaboradores/certificados/revision?id=" + certificado.getId()))
                .andExpect(flash().attribute("mensajeError", "Debes indicar el motivo del rechazo."));
        mockMvc.perform(post("/rm/colaboradores/certificados/{id}/rechazar", certificado.getId()))
                .andExpect(status().isBadRequest());

        Certificado pendiente = certificadoRepository.findById(certificado.getId()).orElseThrow();
        assertEquals(EstadoCertificado.PENDIENTE, pendiente.getEstado());
        assertNull(pendiente.getMotivoRechazo());
        assertNull(pendiente.getRevisadoPor());
        assertEquals(EstadoValidacion.PENDIENTE, colaboradorHabilidadRepository
                .findByColaboradorAndHabilidad(colaborador, habilidad).orElseThrow().getEstadoValidacion());
    }

    @Test
    void rechazarConMotivoConservaElComportamientoExistente() throws Exception {
        Certificado certificado = guardarPendiente("/uploads/certificados/con-motivo.pdf");
        autenticarRm();

        mockMvc.perform(post("/rm/colaboradores/certificados/{id}/rechazar", certificado.getId())
                        .param("motivo", "  La evidencia es ilegible.  "))
                .andExpect(redirectedUrl("/rm/colaboradores/certificados"))
                .andExpect(flash().attribute("mensajeExito", "Certificado rechazado; el motivo quedó guardado."));

        Certificado rechazado = certificadoRepository.findById(certificado.getId()).orElseThrow();
        assertEquals(EstadoCertificado.RECHAZADO, rechazado.getEstado());
        assertEquals("La evidencia es ilegible.", rechazado.getMotivoRechazo());
        assertEquals(rm.getId(), rechazado.getRevisadoPor().getId());
        assertNotNull(rechazado.getFechaRevision());
        assertEquals(EstadoValidacion.RECHAZADA, colaboradorHabilidadRepository
                .findByColaboradorAndHabilidad(colaborador, habilidad).orElseThrow().getEstadoValidacion());
        assertTrue(notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(colaborador).stream()
                .anyMatch(n -> "CERTIFICADO_RECHAZADO".equals(n.getTipo())
                        && certificado.getId().equals(n.getEntidadId())
                        && n.getDescripcion().contains("La evidencia es ilegible.")));
    }

    @Test
    void certificadoRevisadoNoMuestraDecisionNiModal() throws Exception {
        Certificado aprobado = guardar("/uploads/certificados/ya-aprobado.pdf", habilidad, EstadoCertificado.APROBADO);
        Certificado rechazado = guardar("/uploads/certificados/ya-rechazado.pdf", habilidad, EstadoCertificado.RECHAZADO);

        for (Certificado certificado : List.of(aprobado, rechazado)) {
            String html = mockMvc.perform(get("/rm/colaboradores/certificados/revision")
                            .param("id", certificado.getId().toString()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertFalse(html.contains("rejectCertificateModal"));
            assertFalse(html.contains("approveCertificateModal"));
            assertFalse(html.contains("Decisión del Resource Manager"));
            assertFalse(html.contains("name=\"motivo\""));
            assertFalse(html.contains("Confirmar rechazo"));
            assertTrue(html.contains(certificado == aprobado ? "alert-success" : "alert-danger"));
        }
    }

    @Test
    void historialConservaRevisarYVerConLaColumnaDeAccionAlineada() throws Exception {
        Certificado pendiente = guardarPendiente("/uploads/certificados/hist-pendiente.pdf");
        Certificado aprobado = guardar("/uploads/certificados/hist-aprobado.pdf", habilidad, EstadoCertificado.APROBADO);
        Certificado rechazado = guardar("/uploads/certificados/hist-rechazado.pdf", habilidad, EstadoCertificado.RECHAZADO);

        String html = mockMvc.perform(get("/rm/colaboradores/historial-validaciones")
                        .param("id", colaborador.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String revision = "href=\"/rm/colaboradores/certificados/revision?id=";
        assertTrue(html.contains(revision + pendiente.getId() + "\">Revisar</a>"), html);
        assertTrue(html.contains(revision + aprobado.getId() + "\">Ver</a>"));
        assertTrue(html.contains(revision + rechazado.getId() + "\">Ver</a>"));
        assertTrue(html.contains("<table class=\"table table-vcenter card-table mb-0\">"));
        assertTrue(html.contains("<th class=\"text-end history-action\">Acción</th>"));
        assertEquals(3, contar(html, "<td class=\"text-end history-action\"><a class=\"btn btn-outline-secondary btn-sm\""));
        assertEquals(3, contar(html, "<td class=\"observation\">"));
    }

    // ---- TASK-036: nivel general, mantener o subir, nunca bajar ----

    private static final String ERROR_BAJAR_SENIOR_A_JUNIOR = "No se puede bajar el nivel general de experiencia"
            + " de Senior a Junior. Solo se permite mantenerlo o subirlo.";

    @Test
    void perfilBajarNivelDevuelveErrorYNoCambiaNivelSueldoNiAuditoria() throws Exception {
        prepararNivel(NivelExperiencia.SENIOR, "1234.00");
        long auditorias = auditoriasNivel();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> certificadoService.actualizarNivelExperiencia(
                        colaborador.getId(), NivelExperiencia.JUNIOR, rm.getId()));
        assertEquals(ERROR_BAJAR_SENIOR_A_JUNIOR, error.getMessage());

        autenticarRm();
        mockMvc.perform(post("/rm/colaboradores/{id}/nivel-experiencia", colaborador.getId())
                        .param("nivel", "SEMI_SENIOR"))
                .andExpect(redirectedUrl("/rm/colaboradores/perfil?id=" + colaborador.getId()))
                .andExpect(flash().attribute("mensajeError", "No se puede bajar el nivel general de experiencia"
                        + " de Senior a Semi Senior. Solo se permite mantenerlo o subirlo."));

        assertNivelYSueldo(NivelExperiencia.SENIOR, new BigDecimal("1234.00"));
        assertEquals(auditorias, auditoriasNivel());
    }

    @Test
    void perfilMantenerNivelConservaNivelYSueldoSinAuditarCambio() throws Exception {
        prepararNivel(NivelExperiencia.SEMI_SENIOR, "1234.00");
        long auditorias = auditoriasNivel();

        certificadoService.actualizarNivelExperiencia(colaborador.getId(), NivelExperiencia.SEMI_SENIOR, rm.getId());
        autenticarRm();
        mockMvc.perform(post("/rm/colaboradores/{id}/nivel-experiencia", colaborador.getId())
                        .param("nivel", "SEMI_SENIOR"))
                .andExpect(redirectedUrl("/rm/colaboradores/perfil?id=" + colaborador.getId()))
                .andExpect(flash().attributeCount(1))
                .andExpect(flash().attributeExists("mensajeExito"));

        assertNivelYSueldo(NivelExperiencia.SEMI_SENIOR, new BigDecimal("1234.00"));
        assertEquals(auditorias, auditoriasNivel());
    }

    @Test
    void perfilSubirNivelRecalculaSueldoYAuditaAnteriorYNuevo() throws Exception {
        prepararNivel(NivelExperiencia.JUNIOR, "1234.00");
        long auditorias = auditoriasNivel();
        autenticarRm();

        mockMvc.perform(post("/rm/colaboradores/{id}/nivel-experiencia", colaborador.getId())
                        .param("nivel", "SEMI_SENIOR"))
                .andExpect(redirectedUrl("/rm/colaboradores/perfil?id=" + colaborador.getId()))
                .andExpect(flash().attribute("mensajeExito", "Nivel de experiencia actualizado."));

        assertNivelYSueldo(NivelExperiencia.SEMI_SENIOR, tarifa(NivelExperiencia.SEMI_SENIOR));
        assertEquals(auditorias + 1, auditoriasNivel());
        LogAuditoria log = ultimaAuditoriaNivel();
        assertEquals("JUNIOR", log.getValorAnterior());
        assertEquals("SEMI_SENIOR", log.getValorNuevo());
        assertEquals(rm.getId(), log.getUsuario().getId());
    }

    @Test
    void aprobarBajandoNivelAbortaTodaLaAprobacionYDejaPendiente() throws Exception {
        prepararNivel(NivelExperiencia.SENIOR, "1234.00");
        Certificado certificado = guardarPendiente("/uploads/certificados/bajar-nivel.pdf");
        long notificaciones = notificacionesDelColaborador();
        long auditoriasNivel = auditoriasNivel();
        long auditoriasAprobacion = auditoriasAprobacion(certificado.getId());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> certificadoService.aprobar(certificado.getId(), NivelDominio.AVANZADO,
                        NivelExperiencia.JUNIOR, rm.getId()));
        assertEquals(ERROR_BAJAR_SENIOR_A_JUNIOR, error.getMessage());

        autenticarRm();
        mockMvc.perform(post("/rm/colaboradores/certificados/{id}/aprobar", certificado.getId())
                        .param("nivelHabilidad", "AVANZADO").param("nivelGeneral", "JUNIOR"))
                .andExpect(redirectedUrl("/rm/colaboradores/certificados/revision?id=" + certificado.getId()))
                .andExpect(flash().attribute("mensajeError", ERROR_BAJAR_SENIOR_A_JUNIOR));

        Certificado pendiente = certificadoRepository.findById(certificado.getId()).orElseThrow();
        assertEquals(EstadoCertificado.PENDIENTE, pendiente.getEstado());
        assertNull(pendiente.getRevisadoPor());
        assertNull(pendiente.getFechaRevision());
        ColaboradorHabilidad perfil = colaboradorHabilidadRepository
                .findByColaboradorAndHabilidad(colaborador, habilidad).orElseThrow();
        assertEquals(NivelDominio.BASICO, perfil.getNivelDominio());
        assertEquals(EstadoValidacion.PENDIENTE, perfil.getEstadoValidacion());
        assertNull(pendiente.getNivelAprobado());
        assertNivelYSueldo(NivelExperiencia.SENIOR, new BigDecimal("1234.00"));
        assertEquals(notificaciones, notificacionesDelColaborador());
        assertEquals(auditoriasNivel, auditoriasNivel());
        assertEquals(auditoriasAprobacion, auditoriasAprobacion(certificado.getId()));
    }

    @Test
    void aprobarManteniendoNivelApruebaSinRecalcularSueldoNiAuditarCambio() {
        prepararNivel(NivelExperiencia.JUNIOR, "1234.00");
        Certificado certificado = guardarPendiente("/uploads/certificados/mantener-nivel.pdf");
        long notificaciones = notificacionesDelColaborador();
        long auditoriasNivel = auditoriasNivel();

        certificadoService.aprobar(certificado.getId(), null, NivelExperiencia.JUNIOR, rm.getId());

        assertEquals(EstadoCertificado.APROBADO,
                certificadoRepository.findById(certificado.getId()).orElseThrow().getEstado());
        assertEquals(NivelDominio.BASICO,
                certificadoRepository.findById(certificado.getId()).orElseThrow().getNivelAprobado());
        assertEquals(EstadoValidacion.VALIDADA, colaboradorHabilidadRepository
                .findByColaboradorAndHabilidad(colaborador, habilidad).orElseThrow().getEstadoValidacion());
        assertNivelYSueldo(NivelExperiencia.JUNIOR, new BigDecimal("1234.00"));
        assertEquals(auditoriasNivel, auditoriasNivel());
        assertEquals(1, auditoriasAprobacion(certificado.getId()));
        assertEquals(notificaciones + 1, notificacionesDelColaborador());
    }

    @Test
    void aprobarSubiendoNivelRecalculaSueldoYRegistraAmbasAuditorias() {
        prepararNivel(NivelExperiencia.JUNIOR, "1234.00");
        Certificado certificado = guardarPendiente("/uploads/certificados/subir-nivel.pdf");
        long auditoriasNivel = auditoriasNivel();

        certificadoService.aprobar(certificado.getId(), NivelDominio.INTERMEDIO,
                NivelExperiencia.SENIOR, rm.getId());

        assertEquals(EstadoCertificado.APROBADO,
                certificadoRepository.findById(certificado.getId()).orElseThrow().getEstado());
        assertEquals(NivelDominio.INTERMEDIO, colaboradorHabilidadRepository
                .findByColaboradorAndHabilidad(colaborador, habilidad).orElseThrow().getNivelDominio());
        assertEquals(NivelDominio.INTERMEDIO,
                certificadoRepository.findById(certificado.getId()).orElseThrow().getNivelAprobado());
        assertNivelYSueldo(NivelExperiencia.SENIOR, tarifa(NivelExperiencia.SENIOR));
        assertEquals(1, auditoriasAprobacion(certificado.getId()));
        assertEquals(auditoriasNivel + 1, auditoriasNivel());
        LogAuditoria log = ultimaAuditoriaNivel();
        assertEquals("JUNIOR", log.getValorAnterior());
        assertEquals("SENIOR", log.getValorNuevo());
    }

    @Test
    void aprobarConNivelGeneralVacioMantieneElNivel() throws Exception {
        prepararNivel(NivelExperiencia.SEMI_SENIOR, "1234.00");
        Certificado certificado = guardarPendiente("/uploads/certificados/nivel-vacio.pdf");
        long auditoriasNivel = auditoriasNivel();
        autenticarRm();

        mockMvc.perform(post("/rm/colaboradores/certificados/{id}/aprobar", certificado.getId())
                        .param("nivelHabilidad", "").param("nivelGeneral", ""))
                .andExpect(redirectedUrl("/rm/colaboradores/certificados"))
                .andExpect(flash().attribute("mensajeExito", "Certificado aprobado y perfil actualizado."));

        assertEquals(EstadoCertificado.APROBADO,
                certificadoRepository.findById(certificado.getId()).orElseThrow().getEstado());
        assertNivelYSueldo(NivelExperiencia.SEMI_SENIOR, new BigDecimal("1234.00"));
        assertEquals(auditoriasNivel, auditoriasNivel());
        assertEquals(1, auditoriasAprobacion(certificado.getId()));
    }

    @Test
    void modalesDeNivelMuestranActualSeleccionadoYAvisoSinTocarElRechazo() throws Exception {
        prepararNivel(NivelExperiencia.SEMI_SENIOR, "1234.00");

        String perfil = mockMvc.perform(get("/rm/colaboradores/perfil").param("id", colaborador.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        int inicioForm = perfil.indexOf("<form id=\"nivelExperienciaForm\"");
        assertTrue(inicioForm >= 0, perfil);
        String formNivel = perfil.substring(inicioForm, perfil.indexOf("</form>", inicioForm));
        assertFalse(formNivel.contains("type=\"submit\""));
        assertFalse(formNivel.contains("value=\"JUNIOR\""));
        assertTrue(formNivel.contains("value=\"SEMI_SENIOR\" selected"), formNivel);
        assertTrue(formNivel.contains("data-bs-target=\"#nivelExperienciaModal\">Guardar nivel</button>"));
        assertEquals(1, contar(perfil, "id=\"nivelExperienciaModal\""));
        int inicioModalPerfil = perfil.indexOf("id=\"nivelExperienciaModal\"");
        assertTrue(inicioModalPerfil > perfil.indexOf("</main>"));
        String modalPerfil = perfil.substring(inicioModalPerfil, perfil.indexOf("<script", inicioModalPerfil));
        assertFalse(modalPerfil.contains("<form"));
        assertTrue(modalPerfil.contains("Nivel actual: <strong>Semi Senior</strong>"), modalPerfil);
        assertTrue(modalPerfil.contains("Nivel seleccionado: <strong data-nivel-seleccionado>Semi Senior</strong>"));
        assertTrue(modalPerfil.contains("se recalculará el sueldo"));
        assertTrue(modalPerfil.contains("<button type=\"submit\" form=\"nivelExperienciaForm\" class=\"btn btn-primary\">"
                + "Confirmar nivel</button>"));
        assertTrue(perfil.contains("/js/rm-js/rm-nivel-general.js"));

        Certificado certificado = guardarPendiente("/uploads/certificados/modal-nivel.pdf");
        String revision = mockMvc.perform(get("/rm/colaboradores/certificados/revision")
                        .param("id", certificado.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String selector = revision.substring(revision.indexOf("<select id=\"nivelGeneral\""),
                revision.indexOf("</select>", revision.indexOf("<select id=\"nivelGeneral\"")));
        assertTrue(selector.contains("<option value=\"\">Mantener nivel actual</option>"));
        assertFalse(selector.contains("value=\"JUNIOR\""));
        assertTrue(selector.contains("value=\"SEMI_SENIOR\""));
        assertTrue(selector.contains("value=\"SENIOR\""));
        int inicioAprobacion = revision.indexOf("id=\"approveCertificateModal\"");
        String aprobacion = revision.substring(inicioAprobacion, revision.indexOf("id=\"rejectCertificateModal\""));
        assertTrue(aprobacion.contains("data-nivel-select=\"nivelGeneral\""));
        assertTrue(aprobacion.contains("Nivel general actual: <strong>Semi Senior</strong>"), aprobacion);
        assertTrue(aprobacion.contains("Nivel general seleccionado: <strong data-nivel-seleccionado>Sin cambios</strong>"));
        assertTrue(aprobacion.contains("se recalculará el sueldo"));
        assertEquals(1, contar(revision, "id=\"approveCertificateModal\""));
        assertTrue(revision.contains("/js/rm-js/rm-nivel-general.js"));

        String rechazo = revision.substring(revision.indexOf("id=\"rejectCertificateModal\""),
                revision.indexOf("<script", revision.indexOf("id=\"rejectCertificateModal\"")));
        assertEquals(1, contar(revision, "id=\"rejectCertificateModal\""));
        assertFalse(rechazo.contains("data-nivel"));
        assertFalse(rechazo.contains("sueldo"));
        assertTrue(rechazo.contains("name=\"motivo\""));
        assertTrue(rechazo.contains("<button type=\"submit\" class=\"btn btn-danger\">Confirmar rechazo</button>"));
    }

    // ---- TASK-042: nivel aprobado guardado en cada certificado ----

    @Test
    void aprobarConNivelExplicitoLoGuardaEnElCertificado() {
        Certificado certificado = guardarPendiente("/uploads/certificados/nivel-explicito.pdf");

        certificadoService.aprobar(certificado.getId(), NivelDominio.INTERMEDIO, null, rm.getId());

        Certificado aprobado = certificadoRepository.findById(certificado.getId()).orElseThrow();
        assertEquals(EstadoCertificado.APROBADO, aprobado.getEstado());
        assertEquals(NivelDominio.INTERMEDIO, aprobado.getNivelAprobado());
    }

    @Test
    void aprobarManteniendoNivelGuardaElNivelVigenteDeLaHabilidad() {
        perfilHabilidad.setNivelDominio(NivelDominio.AVANZADO);
        colaboradorHabilidadRepository.save(perfilHabilidad);
        Certificado certificado = guardarPendiente("/uploads/certificados/nivel-vigente.pdf");

        certificadoService.aprobar(certificado.getId(), null, null, rm.getId());

        assertEquals(NivelDominio.AVANZADO,
                certificadoRepository.findById(certificado.getId()).orElseThrow().getNivelAprobado());
        assertEquals(NivelDominio.AVANZADO, colaboradorHabilidadRepository
                .findByColaboradorAndHabilidad(colaborador, habilidad).orElseThrow().getNivelDominio());
    }

    @Test
    void cambioPosteriorDeLaHabilidadNoAlteraElCertificadoNiElHistorial() throws Exception {
        Certificado certificado = guardarPendiente("/uploads/certificados/nivel-historico.pdf");
        certificadoService.aprobar(certificado.getId(), NivelDominio.INTERMEDIO, null, rm.getId());

        ColaboradorHabilidad perfil = colaboradorHabilidadRepository
                .findByColaboradorAndHabilidad(colaborador, habilidad).orElseThrow();
        perfil.setNivelDominio(NivelDominio.AVANZADO);
        colaboradorHabilidadRepository.save(perfil);

        assertEquals(NivelDominio.INTERMEDIO,
                certificadoRepository.findById(certificado.getId()).orElseThrow().getNivelAprobado());
        String html = historial();
        assertTrue(html.contains("<td class=\"history-level\">Intermedio</td>"), html);
        assertFalse(html.contains("<td class=\"history-level\">Avanzado</td>"));
    }

    @Test
    void rechazoYPendienteNoTienenNivelAprobado() {
        Certificado pendiente = guardarPendiente("/uploads/certificados/sin-nivel-pendiente.pdf");
        Certificado rechazado = guardarPendiente("/uploads/certificados/sin-nivel-rechazado.pdf");

        certificadoService.rechazar(rechazado.getId(), "Documento incompleto.", rm.getId());

        assertNull(certificadoRepository.findById(rechazado.getId()).orElseThrow().getNivelAprobado());
        assertEquals(EstadoCertificado.RECHAZADO,
                certificadoRepository.findById(rechazado.getId()).orElseThrow().getEstado());
        assertNull(certificadoRepository.findById(pendiente.getId()).orElseThrow().getNivelAprobado());
    }

    @Test
    void historialMuestraColumnaNivelAprobadoConGuionYSinRegistro() throws Exception {
        guardarPendiente("/uploads/certificados/col-pendiente.pdf");
        guardar("/uploads/certificados/col-rechazado.pdf", habilidad, EstadoCertificado.RECHAZADO);
        guardar("/uploads/certificados/col-antiguo.pdf", habilidad, EstadoCertificado.APROBADO);
        Certificado nuevo = guardarPendiente("/uploads/certificados/col-nuevo.pdf");
        certificadoService.aprobar(nuevo.getId(), NivelDominio.BASICO, null, rm.getId());

        String html = historial();
        String thead = html.substring(html.indexOf("<thead>"), html.indexOf("</thead>"));
        assertEquals(8, contar(thead, "<th>") + contar(thead, "<th "));
        assertTrue(thead.contains("<th>Estado</th><th>Nivel aprobado</th><th>Revisado por</th>"), thead);
        assertTrue(thead.contains("<th class=\"text-end history-action\">Acción</th>"));
        assertEquals(4, contar(html, "<tr class=\"history-row\">"));
        assertEquals(4, contar(html, "<td class=\"history-level\">"));
        assertEquals(2, contar(html, "<td class=\"history-level\">—</td>"));
        assertEquals(1, contar(html, "<td class=\"history-level\">Sin registro</td>"));
        assertEquals(1, contar(html, "<td class=\"history-level\">Básico</td>"));
        assertEquals(4, contar(html, "<td class=\"text-end history-action\">"));
        assertFalse(html.contains("id=\"emptyHistory\""));
    }

    @Test
    void historialVacioOcupaLasOchoColumnas() throws Exception {
        guardarPendiente("/uploads/certificados/vacio.pdf");

        String html = mockMvc.perform(get("/rm/colaboradores/historial-validaciones")
                        .param("id", colaborador.getId().toString()).param("busqueda", "no-existe-xyz"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalRegistros", 0L))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(html.contains("<tr id=\"emptyHistory\"><td colspan=\"8\""), html);
        assertFalse(html.contains("colspan=\"7\""));
        assertEquals(0, contar(html, "<td class=\"history-level\">"));
        // TASK-031: la fila usa el estado vacío común.
        assertTrue(html.contains("<tr id=\"emptyHistory\"><td colspan=\"8\" class=\"empty-state-cell\"><div class=\"empty-state\">"));
        assertTrue(html.contains("<div class=\"empty-state-title\">Sin certificados</div>"));
        assertTrue(html.contains("<div class=\"empty-state-text\">No hay certificados con estos filtros.</div>"));
        assertFalse(html.contains("empty-state-action"));
    }

    // TASK-031: la bandeja vacía usa el fragmento común y conserva su id y las 7 columnas.
    @Test
    void bandejaSinCertificadosUsaElEstadoVacioComunConSuIdYColspan() throws Exception {
        String html = mockMvc.perform(get("/rm/colaboradores/certificados").param("busqueda", "sin-coincidencias-t031"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(html.contains("<tr id=\"emptyCertificates\"><td colspan=\"7\" class=\"empty-state-cell\">"
                + "<div class=\"empty-state\">"), html);
        assertEquals(1, contar(html, "class=\"empty-state\""));
        assertTrue(html.contains("<span class=\"empty-state-icon\" aria-hidden=\"true\">"));
        assertTrue(html.contains("<div class=\"empty-state-title\">Sin certificados pendientes</div>"));
        assertTrue(html.contains("No hay certificados pendientes con estos filtros."));
        assertFalse(html.contains("empty-state-action"));
        assertTrue(html.contains("value=\"sin-coincidencias-t031\""));
    }

    private String historial() throws Exception {
        return mockMvc.perform(get("/rm/colaboradores/historial-validaciones")
                        .param("id", colaborador.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private void prepararNivel(NivelExperiencia nivel, String sueldo) {
        colaborador.setNivelExperiencia(nivel);
        colaborador.setSueldoBase(new BigDecimal(sueldo));
        colaborador = usuarioRepository.save(colaborador);
    }

    private void assertNivelYSueldo(NivelExperiencia nivel, BigDecimal sueldo) {
        Usuario actual = usuarioRepository.findById(colaborador.getId()).orElseThrow();
        assertEquals(nivel, actual.getNivelExperiencia());
        assertEquals(0, sueldo.compareTo(actual.getSueldoBase()),
                "Sueldo esperado " + sueldo + ", obtenido " + actual.getSueldoBase());
    }

    private BigDecimal tarifa(NivelExperiencia nivel) {
        return cargoDePrueba("Backend Developer").sueldoPara(nivel);
    }

    private long auditoriasNivel() {
        return logAuditoriaRepository.findAll().stream()
                .filter(log -> "ACTUALIZACION_NIVEL_EXPERIENCIA".equals(log.getAccion())
                        && "USUARIO".equals(log.getEntidad())
                        && colaborador.getId().equals(log.getEntidadId()))
                .count();
    }

    private LogAuditoria ultimaAuditoriaNivel() {
        return logAuditoriaRepository.findAllConUsuario().stream()
                .filter(log -> "ACTUALIZACION_NIVEL_EXPERIENCIA".equals(log.getAccion())
                        && colaborador.getId().equals(log.getEntidadId()))
                .max(java.util.Comparator.comparing(LogAuditoria::getId))
                .orElseThrow();
    }

    private long auditoriasAprobacion(Long certificadoId) {
        return logAuditoriaRepository.findAll().stream()
                .filter(log -> "APROBACION_CERTIFICADO".equals(log.getAccion())
                        && "CERTIFICADO".equals(log.getEntidad())
                        && certificadoId.equals(log.getEntidadId()))
                .count();
    }

    private long notificacionesDelColaborador() {
        return notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(colaborador).size();
    }

    private void autenticarRm() {
        UsuarioDetails details = new UsuarioDetails(usuarioRepository.findActivosByRolNombre("RESOURCE_MANAGER")
                .stream().filter(u -> u.getId().equals(rm.getId())).findFirst().orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    private int filas(MvcResult resultado) {
        Object certificados = resultado.getModelAndView().getModel().get("certificados");
        return ((List<?>) certificados).size();
    }

    private int contar(String texto, String fragmento) {
        return texto.split(Pattern.quote(fragmento), -1).length - 1;
    }

    private String enlace(String html, String texto) {
        Matcher matcher = Pattern.compile("href=\"([^\"]*)\">" + texto + "<").matcher(html);
        assertTrue(matcher.find(), "No se encontró el enlace " + texto);
        return matcher.group(1);
    }

    private Habilidad otraHabilidad(String nombre) {
        return habilidadRepository.findByActivaTrue().stream()
                .filter(item -> nombre.equals(item.getNombre()))
                .findFirst().orElseGet(() -> {
                    Habilidad nueva = new Habilidad();
                    nueva.setNombre(nombre);
                    nueva.setCategoria(habilidad.getCategoria());
                    return habilidadRepository.save(nueva);
                });
    }

    private Certificado guardarPendiente(String archivo) {
        return guardar(archivo, habilidad, EstadoCertificado.PENDIENTE);
    }

    private Certificado guardar(String archivo, Habilidad habilidadCertificado, EstadoCertificado estado) {
        Certificado certificado = new Certificado();
        certificado.setColaborador(colaborador);
        certificado.setHabilidad(habilidadCertificado);
        certificado.setArchivoUrl(archivo);
        certificado.setEstado(estado);
        return certificadoRepository.save(certificado);
    }

    private Rol obtenerRol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol rol = new Rol(); rol.setNombre(nombre); return rolRepository.save(rol);
        });
    }

    private Usuario obtenerUsuario(String correo, String nombre, String apellido, Rol rol) {
        return usuarioRepository.findByCorreo(correo).orElseGet(() -> {
            Usuario usuario = new Usuario(); usuario.setCorreo(correo); usuario.setNombre(nombre);
            usuario.setApellido(apellido); usuario.setRol(rol); return usuarioRepository.save(usuario);
        });
    }

    private Cargo cargoDePrueba(String nombre) {
        return cargoRepository.findByNombre(nombre).orElseGet(() -> cargoRepository.save(
                new Cargo(nombre, new BigDecimal("2000"), new BigDecimal("3000"), new BigDecimal("4000"))));
    }
}
