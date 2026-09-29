package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.EstadoCertificado;
import com.pucp.skillb_ia.model.enums.NivelExperiencia;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.NotificacionService;
import com.pucp.skillb_ia.service.col.ColaboradorPerfilService;
import com.pucp.skillb_ia.service.rm.RmEducacionService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class RmEducacionTests {
    private static final String BANDEJA = "/rm/colaboradores/educacion";

    @Autowired private WebApplicationContext context;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CargoRepository cargoRepository;
    @Autowired private EducacionRepository educacionRepository;
    @Autowired private NotificacionRepository notificacionRepository;
    @Autowired private LogAuditoriaRepository logAuditoriaRepository;
    @Autowired private RmEducacionService educacionService;
    @Autowired private NotificacionService notificacionService;
    @Autowired private ColaboradorPerfilService colaboradorPerfilService;

    private MockMvc mockMvc;
    private Usuario rm;
    private Usuario colaborador;

    @BeforeEach
    void prepararDatos() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        educacionRepository.deleteAll();
        rm = obtenerUsuario("rm.educacion@skillbridge.test", "Rosa", "Revisora", obtenerRol("RESOURCE_MANAGER"));
        colaborador = obtenerUsuario("col.educacion@skillbridge.test", "Carla", "Estudiosa",
                obtenerRol("COLABORADOR"));
        colaborador.setCargo(cargoDePrueba("Analista Educacion Test"));
        colaborador.setNivelExperiencia(NivelExperiencia.JUNIOR);
        colaborador.setSueldoBase(new BigDecimal("2500.00"));
        colaborador = usuarioRepository.save(colaborador);
    }

    @AfterEach
    void limpiarSesion() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void bandejaListaSoloPendientesActivasDeLaMasAntiguaALaMasReciente() throws Exception {
        Educacion reciente = guardar("Ingeniería de Software", "PUCP", EstadoCertificado.PENDIENTE,
                LocalDateTime.now().minusDays(1));
        Educacion antigua = guardar("Maestría en Datos", "UNI", EstadoCertificado.PENDIENTE,
                LocalDateTime.now().minusDays(5));
        guardar("Aprobada", "PUCP", EstadoCertificado.APROBADO, LocalDateTime.now().minusDays(9));
        guardar("Rechazada", "PUCP", EstadoCertificado.RECHAZADO, LocalDateTime.now().minusDays(9));
        Educacion eliminada = guardar("Eliminada", "PUCP", EstadoCertificado.PENDIENTE,
                LocalDateTime.now().minusDays(9));
        eliminada.setActivo(false);
        educacionRepository.save(eliminada);

        MvcResult resultado = mockMvc.perform(get(BANDEJA))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-educacion-pendiente"))
                .andExpect(model().attribute("totalPendientes", 2))
                .andExpect(model().attribute("totalRegistros", 2L))
                .andReturn();
        assertEquals(List.of(antigua.getId(), reciente.getId()), ids(resultado));
        assertEquals(2L, educacionService.contarPendientes());
    }

    @Test
    void rmVeElDetalleYAccedeAlDocumentoLocalOS3() throws Exception {
        Educacion local = guardar("Ingeniería de Software", "PUCP", EstadoCertificado.PENDIENTE, null);

        MvcResult detalle = mockMvc.perform(get(BANDEJA + "/revision").param("id", local.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-revision-educacion"))
                .andExpect(model().attributeExists("formacion"))
                .andReturn();
        String html = detalle.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("Ingeniería de Software"));
        assertTrue(html.contains("Carla Estudiosa"));
        assertTrue(html.contains("href=\"" + BANDEJA + "/" + local.getId() + "/documento\""));

        mockMvc.perform(get(BANDEJA + "/" + local.getId() + "/documento"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(local.getArchivoUrl()));

        Educacion s3 = guardar("Bachiller", "UNI", EstadoCertificado.PENDIENTE, null);
        s3.setArchivoUrl("https://bucket-test.s3.us-east-1.amazonaws.com/certificados-educacion/doc.pdf");
        educacionRepository.save(s3);
        mockMvc.perform(get(BANDEJA + "/" + s3.getId() + "/documento"))
                .andExpect(redirectedUrl(s3.getArchivoUrl()));

        Educacion sinArchivo = guardar("Sin archivo", "UNI", EstadoCertificado.PENDIENTE, null);
        sinArchivo.setArchivoUrl(null);
        educacionRepository.save(sinArchivo);
        mockMvc.perform(get(BANDEJA + "/" + sinArchivo.getId() + "/documento"))
                .andExpect(redirectedUrl(BANDEJA + "/revision?id=" + sinArchivo.getId()))
                .andExpect(flash().attribute("mensajeError", "Esta formación académica no tiene un documento adjunto."));
        mockMvc.perform(get(BANDEJA + "/revision").param("id", "999999"))
                .andExpect(redirectedUrl(BANDEJA))
                .andExpect(flash().attribute("mensajeError", "No se encontró la formación académica solicitada."));
    }

    @Test
    void aprobarValidaRegistraRevisorNotificaYAuditaSinTocarNivelNiSueldo() throws Exception {
        Educacion educacion = guardar("Ingeniería de Software", "PUCP", EstadoCertificado.PENDIENTE, null);
        autenticar(rm);

        mockMvc.perform(post(BANDEJA + "/" + educacion.getId() + "/aprobar"))
                .andExpect(redirectedUrl(BANDEJA))
                .andExpect(flash().attribute("mensajeExito", "Formación académica aprobada."));

        Educacion aprobada = educacionRepository.findById(educacion.getId()).orElseThrow();
        assertEquals(EstadoCertificado.APROBADO, aprobada.getEstado());
        assertEquals(rm.getId(), aprobada.getRevisadoPor().getId());
        assertNotNull(aprobada.getFechaRevision());
        assertNull(aprobada.getMotivoRechazo());
        assertEquals(1, notificaciones("EDUCACION_APROBADA", educacion.getId()).size());
        assertEquals(1, auditorias("APROBACION_EDUCACION", educacion.getId()).size());
        assertEquals(rm.getId(), auditorias("APROBACION_EDUCACION", educacion.getId()).get(0).getUsuario().getId());

        Usuario despues = usuarioRepository.findById(colaborador.getId()).orElseThrow();
        assertEquals(NivelExperiencia.JUNIOR, despues.getNivelExperiencia());
        assertEquals(0, new BigDecimal("2500.00").compareTo(despues.getSueldoBase()));
    }

    @Test
    void rechazarConMotivoValidoRegistraMotivoRevisorNotificaYAudita() throws Exception {
        Educacion educacion = guardar("Ingeniería de Software", "PUCP", EstadoCertificado.PENDIENTE, null);
        autenticar(rm);

        mockMvc.perform(post(BANDEJA + "/" + educacion.getId() + "/rechazar")
                        .param("motivo", "  El documento no es legible.  "))
                .andExpect(redirectedUrl(BANDEJA))
                .andExpect(flash().attribute("mensajeExito",
                        "Formación académica rechazada; el motivo quedó guardado."));

        Educacion rechazada = educacionRepository.findById(educacion.getId()).orElseThrow();
        assertEquals(EstadoCertificado.RECHAZADO, rechazada.getEstado());
        assertEquals("El documento no es legible.", rechazada.getMotivoRechazo());
        assertEquals(rm.getId(), rechazada.getRevisadoPor().getId());
        assertNotNull(rechazada.getFechaRevision());
        List<Notificacion> avisos = notificaciones("EDUCACION_RECHAZADA", educacion.getId());
        assertEquals(1, avisos.size());
        assertTrue(avisos.get(0).getDescripcion().contains("El documento no es legible."));
        assertEquals(1, auditorias("RECHAZO_EDUCACION", educacion.getId()).size());

        Usuario despues = usuarioRepository.findById(colaborador.getId()).orElseThrow();
        assertEquals(NivelExperiencia.JUNIOR, despues.getNivelExperiencia());
        assertEquals(0, new BigDecimal("2500.00").compareTo(despues.getSueldoBase()));
    }

    @Test
    void rechazarSinMotivoConEspaciosOMasDeTrescientosCaracteresSeRechazaEnElServidor() throws Exception {
        Educacion educacion = guardar("Ingeniería de Software", "PUCP", EstadoCertificado.PENDIENTE, null);
        autenticar(rm);
        String revision = BANDEJA + "/revision?id=" + educacion.getId();

        mockMvc.perform(post(BANDEJA + "/" + educacion.getId() + "/rechazar"))
                .andExpect(redirectedUrl(revision))
                .andExpect(flash().attribute("mensajeError", "Debes indicar el motivo del rechazo."));
        mockMvc.perform(post(BANDEJA + "/" + educacion.getId() + "/rechazar").param("motivo", "    "))
                .andExpect(redirectedUrl(revision))
                .andExpect(flash().attribute("mensajeError", "Debes indicar el motivo del rechazo."));
        mockMvc.perform(post(BANDEJA + "/" + educacion.getId() + "/rechazar").param("motivo", "x".repeat(301)))
                .andExpect(redirectedUrl(revision))
                .andExpect(flash().attribute("mensajeError",
                        "El motivo del rechazo no puede superar los 300 caracteres."));
        assertThrows(IllegalArgumentException.class,
                () -> educacionService.rechazar(educacion.getId(), null, rm.getId()));

        Educacion sinCambios = educacionRepository.findById(educacion.getId()).orElseThrow();
        assertEquals(EstadoCertificado.PENDIENTE, sinCambios.getEstado());
        assertNull(sinCambios.getRevisadoPor());
        assertNull(sinCambios.getFechaRevision());
        assertTrue(notificaciones("EDUCACION_RECHAZADA", educacion.getId()).isEmpty());
        assertTrue(auditorias("RECHAZO_EDUCACION", educacion.getId()).isEmpty());

        educacionService.rechazar(educacion.getId(), "y".repeat(300), rm.getId());
        assertEquals(300, educacionRepository.findById(educacion.getId()).orElseThrow().getMotivoRechazo().length());
    }

    @Test
    void unaFormacionYaRevisadaNoPuedeVolverADecidirse() throws Exception {
        Educacion aprobada = guardar("Aprobada", "PUCP", EstadoCertificado.PENDIENTE, null);
        Educacion rechazada = guardar("Rechazada", "PUCP", EstadoCertificado.PENDIENTE, null);
        educacionService.aprobar(aprobada.getId(), rm.getId());
        educacionService.rechazar(rechazada.getId(), "Documento incompleto.", rm.getId());

        assertThrows(IllegalStateException.class, () -> educacionService.aprobar(aprobada.getId(), rm.getId()));
        assertThrows(IllegalStateException.class,
                () -> educacionService.rechazar(aprobada.getId(), "Otro motivo.", rm.getId()));
        assertThrows(IllegalStateException.class, () -> educacionService.aprobar(rechazada.getId(), rm.getId()));

        autenticar(rm);
        mockMvc.perform(post(BANDEJA + "/" + rechazada.getId() + "/rechazar").param("motivo", "Otro motivo."))
                .andExpect(redirectedUrl(BANDEJA + "/revision?id=" + rechazada.getId()))
                .andExpect(flash().attribute("mensajeError", "Esta formación académica ya fue revisada."));

        assertEquals(EstadoCertificado.APROBADO,
                educacionRepository.findById(aprobada.getId()).orElseThrow().getEstado());
        assertEquals("Documento incompleto.",
                educacionRepository.findById(rechazada.getId()).orElseThrow().getMotivoRechazo());
        assertEquals(1, notificaciones("EDUCACION_APROBADA", aprobada.getId()).size());
        assertEquals(1, auditorias("RECHAZO_EDUCACION", rechazada.getId()).size());

        String html = mockMvc.perform(get(BANDEJA + "/revision").param("id", aprobada.getId().toString()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(html.contains("/aprobar\""));
        assertFalse(html.contains("/rechazar\""));
    }

    @Test
    void unUsuarioQueNoEsRmNoPuedeRevisar() throws Exception {
        Educacion educacion = guardar("Ingeniería de Software", "PUCP", EstadoCertificado.PENDIENTE, null);
        assertThrows(IllegalStateException.class,
                () -> educacionService.aprobar(educacion.getId(), colaborador.getId()));
        assertThrows(IllegalStateException.class,
                () -> educacionService.rechazar(educacion.getId(), "Motivo.", colaborador.getId()));

        MockMvc mvcSeguro = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
        MockHttpSession sesionColaborador = sesion(colaborador);
        mvcSeguro.perform(post(BANDEJA + "/" + educacion.getId() + "/aprobar").session(sesionColaborador))
                .andExpect(status().isForbidden());
        mvcSeguro.perform(post(BANDEJA + "/" + educacion.getId() + "/rechazar")
                        .param("motivo", "Motivo.").session(sesionColaborador))
                .andExpect(status().isForbidden());
        mvcSeguro.perform(get(BANDEJA).session(sesionColaborador))
                .andExpect(status().isForbidden());
        mvcSeguro.perform(post(BANDEJA + "/" + educacion.getId() + "/aprobar"))
                .andExpect(status().is3xxRedirection());

        Educacion sinCambios = educacionRepository.findById(educacion.getId()).orElseThrow();
        assertEquals(EstadoCertificado.PENDIENTE, sinCambios.getEstado());
        assertNull(sinCambios.getRevisadoPor());

        mvcSeguro.perform(post(BANDEJA + "/" + educacion.getId() + "/aprobar").session(sesion(rm)))
                .andExpect(redirectedUrl(BANDEJA));
        assertEquals(EstadoCertificado.APROBADO,
                educacionRepository.findById(educacion.getId()).orElseThrow().getEstado());
    }

    @Test
    void bandejaFiltraYPaginaEnElServidorConservandoLosParametros() throws Exception {
        for (int i = 1; i <= 7; i++) {
            guardar("Curso técnico " + i, "Instituto Alfa", EstadoCertificado.PENDIENTE,
                    LocalDateTime.now().minusDays(20 - i));
        }
        guardar("Doctorado en Física", "Universidad Beta", EstadoCertificado.PENDIENTE, LocalDateTime.now());

        MvcResult sinFiltros = mockMvc.perform(get(BANDEJA))
                .andExpect(model().attribute("totalRegistros", 8L))
                .andExpect(model().attribute("paginaActual", 1))
                .andExpect(model().attribute("totalPaginas", 2))
                .andExpect(model().attribute("instituciones", List.of("Instituto Alfa", "Universidad Beta")))
                .andReturn();
        assertEquals(6, ids(sinFiltros).size());
        String html = sinFiltros.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("<button type=\"submit\" class=\"btn btn-primary\">Filtrar</button>"));

        mockMvc.perform(get(BANDEJA).param("institucion", "universidad beta"))
                .andExpect(model().attribute("institucion", "Universidad Beta"))
                .andExpect(model().attribute("totalRegistros", 1L));
        mockMvc.perform(get(BANDEJA).param("busqueda", "  FISICA "))
                .andExpect(model().attribute("busqueda", "FISICA"))
                .andExpect(model().attribute("totalRegistros", 1L));
        mockMvc.perform(get(BANDEJA).param("institucion", "Inexistente"))
                .andExpect(model().attribute("institucion", (Object) null))
                .andExpect(model().attribute("totalRegistros", 8L));

        MvcResult segunda = mockMvc.perform(get(BANDEJA).param("institucion", "Instituto Alfa")
                        .param("busqueda", "curso").param("pagina", "2"))
                .andExpect(model().attribute("paginaActual", 2))
                .andExpect(model().attribute("totalRegistros", 7L))
                .andReturn();
        assertEquals(1, ids(segunda).size());
        String htmlSegunda = segunda.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(htmlSegunda.contains("Mostrando 7-7 de 7 formaciones"));
        String anterior = enlace(htmlSegunda, "Anterior");
        for (String parametro : List.of("busqueda=curso", "institucion=Instituto", "pagina=1")) {
            assertTrue(anterior.contains(parametro), anterior);
        }

        mockMvc.perform(get(BANDEJA).param("pagina", "99")).andExpect(model().attribute("paginaActual", 2));
        mockMvc.perform(get(BANDEJA).param("pagina", "0")).andExpect(model().attribute("paginaActual", 1));
        mockMvc.perform(get(BANDEJA).param("pagina", "abc"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("paginaActual", 1));
    }

    @Test
    void notificacionPendienteLlevaAlRmALaRevision() {
        MockMultipartFile archivo = new MockMultipartFile(
                "certificado", "titulo.pdf", "application/pdf", "%PDF-demo".getBytes());
        colaboradorPerfilService.agregarEducacion(colaborador, "PUCP", "Ingeniería Informática",
                "2018-03-01", "2023-12-15", archivo);
        Educacion creada = educacionRepository.findByColaboradorAndActivoTrue(colaborador).get(0);
        assertEquals(EstadoCertificado.PENDIENTE, creada.getEstado());

        String url = "/rm/colaboradores/educacion/revision?id=" + creada.getId();
        assertTrue(notificacionService.listar(rm.getId()).stream().anyMatch(item -> url.equals(item.url())),
                "La notificación EDUCACION_PENDIENTE debe enlazar a " + url);

        educacionService.aprobar(creada.getId(), rm.getId());
        assertTrue(notificacionService.listar(colaborador.getId()).stream()
                .anyMatch(item -> "/colaborador/perfil".equals(item.url())));
    }

    @Test
    void perfilDelColaboradorMuestraElComentarioDelRmSoloEnLaRechazada() throws Exception {
        Educacion rechazada = guardar("Ingeniería rechazada", "PUCP", EstadoCertificado.PENDIENTE, null);
        Educacion aprobada = guardar("Ingeniería aprobada", "PUCP", EstadoCertificado.PENDIENTE, null);
        educacionService.rechazar(rechazada.getId(), "El documento no corresponde al título.", rm.getId());
        educacionService.aprobar(aprobada.getId(), rm.getId());
        autenticar(colaborador);

        String html = mockMvc.perform(get("/colaborador/perfil"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(1, html.split("class=\"edu-rm-comment\"", -1).length - 1);
        assertTrue(html.contains("Comentario del RM"));
        assertTrue(html.contains("El documento no corresponde al título."));
    }

    private List<Long> ids(MvcResult resultado) {
        Object formaciones = resultado.getModelAndView().getModel().get("formaciones");
        return ((List<?>) formaciones).stream()
                .map(item -> ((com.pucp.skillb_ia.dto.RmEducacionView) item).getEducacion().getId())
                .toList();
    }

    private String enlace(String html, String texto) {
        Matcher matcher = Pattern.compile("href=\"([^\"]*)\">" + texto + "<").matcher(html);
        assertTrue(matcher.find(), "No se encontró el enlace " + texto);
        return matcher.group(1);
    }

    private List<Notificacion> notificaciones(String tipo, Long educacionId) {
        return notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(colaborador).stream()
                .filter(item -> tipo.equals(item.getTipo()) && educacionId.equals(item.getEntidadId()))
                .toList();
    }

    private List<LogAuditoria> auditorias(String accion, Long educacionId) {
        return logAuditoriaRepository.findAll().stream()
                .filter(item -> accion.equals(item.getAccion()) && "EDUCACION".equals(item.getEntidad())
                        && educacionId.equals(item.getEntidadId()))
                .toList();
    }

    private Educacion guardar(String titulo, String institucion, EstadoCertificado estado, LocalDateTime creacion) {
        Educacion educacion = new Educacion();
        educacion.setColaborador(colaborador);
        educacion.setTitulo(titulo);
        educacion.setInstitucion(institucion);
        educacion.setFechaInicio(LocalDate.of(2018, 3, 1));
        educacion.setFechaFin(LocalDate.of(2023, 12, 15));
        educacion.setArchivoUrl("/uploads/certificados-educacion/educacion-test.pdf");
        educacion.setEstado(estado);
        educacion.setFechaCreacion(creacion);
        return educacionRepository.save(educacion);
    }

    private void autenticar(Usuario usuario) {
        UsuarioDetails details = new UsuarioDetails(conRol(usuario));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    private MockHttpSession sesion(Usuario usuario) {
        UsuarioDetails details = new UsuarioDetails(conRol(usuario));
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(new UsernamePasswordAuthenticationToken(
                        details, null, details.getAuthorities())));
        return sesion;
    }

    // Recarga el usuario con su rol inicializado (fuera de una transacción el rol es un proxy perezoso).
    private Usuario conRol(Usuario usuario) {
        String rol = usuario.getId().equals(rm.getId()) ? "RESOURCE_MANAGER" : "COLABORADOR";
        return usuarioRepository.findActivosByRolNombre(rol).stream()
                .filter(item -> item.getId().equals(usuario.getId()))
                .findFirst().orElseThrow();
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
