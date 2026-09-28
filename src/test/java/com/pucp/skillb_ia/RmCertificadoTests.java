package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.Cargo;
import com.pucp.skillb_ia.repository.CargoRepository;
import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.*;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.col.ColaboradorPerfilService;
import com.pucp.skillb_ia.service.rm.RmCertificadoService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
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
