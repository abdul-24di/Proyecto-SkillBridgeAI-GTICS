package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.CategoriaDocumento;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoColaboradorCurso;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.NivelDominio;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.service.col.ColAsistenteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ColaboradorEndpointsPendientesTests {

    private static final byte[] PDF = "%PDF-1.4 contenido de prueba".getBytes();
    private static final String HISTORIAL = "ASISTENTE_HISTORIAL";

    @Autowired private WebApplicationContext context;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private AsignacionRepository asignacionRepository;
    @Autowired private DocumentoRepository documentoRepository;
    @Autowired private CertificadoRepository certificadoRepository;
    @Autowired private CategoriaHabilidadRepository categoriaHabilidadRepository;
    @Autowired private HabilidadRepository habilidadRepository;
    @Autowired private ColaboradorHabilidadRepository colaboradorHabilidadRepository;
    @Autowired private CursoRepository cursoRepository;
    @Autowired private ColaboradorCursoRepository colaboradorCursoRepository;
    @Autowired private LogAuditoriaRepository logAuditoriaRepository;
    @Autowired private ColAsistenteService asistenteService;

    private MockMvc mvc;
    private Usuario pm;
    private Usuario col;
    private Usuario otroCol;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        pm = usuario("pm", "PROJECT_MANAGER");
        col = usuario("col", "COLABORADOR");
        otroCol = usuario("col2", "COLABORADOR");
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    // ======================= Sin sesión =======================

    @Test
    void sinSesionLasAccionesPendientesRedirigenAlLogin() throws Exception {
        mvc.perform(multipart("/colaborador/proyectos/documentos/subir")
                        .file(archivo("archivo", "plano.pdf", "application/pdf"))
                        .param("proyectoId", "1").param("asignacionId", "1"))
                .andExpect(redirectedUrl("/login"));
        mvc.perform(post("/colaborador/proyectos/documentos/1/eliminar").param("asignacionId", "1"))
                .andExpect(redirectedUrl("/login"));
        mvc.perform(multipart("/colaborador/perfil/certificados")
                        .file(archivo("certificado", "cert.pdf", "application/pdf"))
                        .param("habilidadId", "1"))
                .andExpect(redirectedUrl("/login"));
        mvc.perform(post("/colaborador/perfil/habilidades/1/eliminar"))
                .andExpect(redirectedUrl("/login"));
    }

    // ======================= Documentos del proyecto: subir =======================

    @Test
    void subirUnDocumentoConAsignacionActivaLoGuardaYVuelveALaPestanaDeDocumentos() throws Exception {
        Proyecto proyecto = proyecto(pm);
        Asignacion asignacion = asignar(col, proyecto, EstadoAsignacion.ACTIVA);
        autenticar(col);

        mvc.perform(multipart("/colaborador/proyectos/documentos/subir")
                        .file(archivo("archivo", "plano.pdf", "application/pdf"))
                        .param("proyectoId", proyecto.getId().toString())
                        .param("asignacionId", asignacion.getId().toString()))
                .andExpect(redirectedUrl(destinoDocumentos(asignacion)))
                .andExpect(flash().attribute("mensajeExito", "Documento subido correctamente."));

        List<Documento> documentos = documentoRepository.findByProyectoAndActivoTrueOrderByFechaCreacionDesc(proyecto);
        assertThat(documentos).hasSize(1);
        assertThat(documentos.get(0).getNombre()).isEqualTo("plano.pdf");
        assertThat(documentos.get(0).getCategoria()).isEqualTo(CategoriaDocumento.PDF);
        assertThat(documentos.get(0).getSubidoPor().getId()).isEqualTo(col.getId());
    }

    @Test
    void subirUnArchivoConExtensionNoPermitidaReabreElModalYNoGuardaNada() throws Exception {
        Proyecto proyecto = proyecto(pm);
        Asignacion asignacion = asignar(col, proyecto, EstadoAsignacion.ACTIVA);
        autenticar(col);

        mvc.perform(multipart("/colaborador/proyectos/documentos/subir")
                        .file(archivo("archivo", "virus.exe", "application/octet-stream"))
                        .param("proyectoId", proyecto.getId().toString())
                        .param("asignacionId", asignacion.getId().toString()))
                .andExpect(redirectedUrl(destinoDocumentos(asignacion)))
                .andExpect(flash().attribute("abrirModalSubirDocumento", true));

        assertThat(documentoRepository.findByProyectoAndActivoTrueOrderByFechaCreacionDesc(proyecto)).isEmpty();
    }

    @Test
    void subirSinElegirArchivoReabreElModal() throws Exception {
        Proyecto proyecto = proyecto(pm);
        Asignacion asignacion = asignar(col, proyecto, EstadoAsignacion.ACTIVA);
        autenticar(col);

        mvc.perform(multipart("/colaborador/proyectos/documentos/subir")
                        .param("proyectoId", proyecto.getId().toString())
                        .param("asignacionId", asignacion.getId().toString()))
                .andExpect(redirectedUrl(destinoDocumentos(asignacion)))
                .andExpect(flash().attribute("abrirModalSubirDocumento", true));

        assertThat(documentoRepository.findByProyectoAndActivoTrueOrderByFechaCreacionDesc(proyecto)).isEmpty();
    }

    @Test
    void sinAsignacionActivaElServicioRechazaLaSubidaYElControladorMuestraElError() throws Exception {
        Proyecto proyecto = proyecto(pm);
        Asignacion finalizada = asignar(col, proyecto, EstadoAsignacion.FINALIZADA);
        autenticar(col);

        mvc.perform(multipart("/colaborador/proyectos/documentos/subir")
                        .file(archivo("archivo", "plano.pdf", "application/pdf"))
                        .param("proyectoId", proyecto.getId().toString())
                        .param("asignacionId", finalizada.getId().toString()))
                .andExpect(redirectedUrl(destinoDocumentos(finalizada)))
                .andExpect(flash().attribute("errorDocumento", "No tienes una asignación activa en este proyecto."))
                .andExpect(flash().attribute("abrirModalSubirDocumento", true));

        assertThat(documentoRepository.findByProyectoAndActivoTrueOrderByFechaCreacionDesc(proyecto)).isEmpty();
    }

    // ======================= Documentos del proyecto: eliminar =======================

    @Test
    void quienSubioElDocumentoPuedeEliminarloYQuedaInactivo() throws Exception {
        Proyecto proyecto = proyecto(pm);
        Asignacion asignacion = asignar(col, proyecto, EstadoAsignacion.ACTIVA);
        Documento documento = documento(proyecto, col);
        autenticar(col);

        mvc.perform(post("/colaborador/proyectos/documentos/{id}/eliminar", documento.getId())
                        .param("asignacionId", asignacion.getId().toString()))
                .andExpect(redirectedUrl(destinoDocumentos(asignacion)))
                .andExpect(flash().attribute("mensajeExito", "Documento eliminado."));

        assertThat(documentoRepository.findById(documento.getId()).orElseThrow().isActivo()).isFalse();
    }

    @Test
    void otroColaboradorNoPuedeEliminarElDocumentoYSeguiraActivo() throws Exception {
        Proyecto proyecto = proyecto(pm);
        Asignacion asignacionDelOtro = asignar(otroCol, proyecto, EstadoAsignacion.ACTIVA);
        Documento documento = documento(proyecto, col);
        autenticar(otroCol);

        mvc.perform(post("/colaborador/proyectos/documentos/{id}/eliminar", documento.getId())
                        .param("asignacionId", asignacionDelOtro.getId().toString()))
                .andExpect(redirectedUrl(destinoDocumentos(asignacionDelOtro)))
                .andExpect(flash().attribute("errorEliminarDoc",
                        "Solo quien subió el documento (o un Administrador) puede eliminarlo."))
                .andExpect(flash().attribute("abrirModalEliminarDocId", documento.getId()));

        assertThat(documentoRepository.findById(documento.getId()).orElseThrow().isActivo()).isTrue();
    }

    @Test
    void eliminarUnDocumentoQueNoExisteMuestraElError() throws Exception {
        Proyecto proyecto = proyecto(pm);
        Asignacion asignacion = asignar(col, proyecto, EstadoAsignacion.ACTIVA);
        autenticar(col);

        mvc.perform(post("/colaborador/proyectos/documentos/{id}/eliminar", 999999999L)
                        .param("asignacionId", asignacion.getId().toString()))
                .andExpect(redirectedUrl(destinoDocumentos(asignacion)))
                .andExpect(flash().attribute("errorEliminarDoc", "El documento no existe o ya fue eliminado."));
    }

    // ======================= Perfil: subir certificado =======================

    @Test
    void subirUnCertificadoDeUnaHabilidadDelPerfilLoDejaPendienteDeRevision() throws Exception {
        Habilidad habilidad = habilidadDelPerfil(col);
        autenticar(col);

        mvc.perform(multipart("/colaborador/perfil/certificados")
                        .file(archivo("certificado", "cert.pdf", "application/pdf"))
                        .param("habilidadId", habilidad.getId().toString()))
                .andExpect(redirectedUrl("/colaborador/perfil"))
                .andExpect(flash().attribute("mensajeExito",
                        "Certificado enviado. El Resource Manager podrá revisarlo desde su bandeja."));

        List<Certificado> certificados = certificadoRepository.findByColaborador(col);
        assertThat(certificados).hasSize(1);
        assertThat(certificados.get(0).getHabilidad().getId()).isEqualTo(habilidad.getId());
    }

    @Test
    void subirUnCertificadoConNivelNuevoActualizaElNivelDeLaHabilidad() throws Exception {
        Habilidad habilidad = habilidadDelPerfil(col);
        autenticar(col);

        mvc.perform(multipart("/colaborador/perfil/certificados")
                        .file(archivo("certificado", "cert.pdf", "application/pdf"))
                        .param("habilidadId", habilidad.getId().toString())
                        .param("nivel", "AVANZADO"))
                .andExpect(redirectedUrl("/colaborador/perfil"))
                .andExpect(flash().attributeExists("mensajeExito"));

        ColaboradorHabilidad perfil = colaboradorHabilidadRepository
                .findByColaboradorAndHabilidad(col, habilidad).orElseThrow();
        assertThat(perfil.getNivelDominio()).isEqualTo(NivelDominio.AVANZADO);
    }

    @Test
    void unNivelInvalidoMuestraElErrorYNoSubeElCertificado() throws Exception {
        Habilidad habilidad = habilidadDelPerfil(col);
        autenticar(col);

        mvc.perform(multipart("/colaborador/perfil/certificados")
                        .file(archivo("certificado", "cert.pdf", "application/pdf"))
                        .param("habilidadId", habilidad.getId().toString())
                        .param("nivel", "EXPERTO"))
                .andExpect(redirectedUrl("/colaborador/perfil"))
                .andExpect(flash().attribute("mensajeError", "El nivel seleccionado no es válido."));

        assertThat(certificadoRepository.findByColaborador(col)).isEmpty();
    }

    @Test
    void unCertificadoQueNoEsPdfNiImagenSeRechaza() throws Exception {
        Habilidad habilidad = habilidadDelPerfil(col);
        autenticar(col);

        mvc.perform(multipart("/colaborador/perfil/certificados")
                        .file(archivo("certificado", "notas.txt", "text/plain"))
                        .param("habilidadId", habilidad.getId().toString()))
                .andExpect(redirectedUrl("/colaborador/perfil"))
                .andExpect(flash().attribute("mensajeError", "El certificado debe estar en formato PDF, JPG o PNG."));

        assertThat(certificadoRepository.findByColaborador(col)).isEmpty();
    }

    @Test
    void noSePuedeCertificarUnaHabilidadQueNoEstaEnElPerfil() throws Exception {
        Habilidad ajena = habilidad();
        autenticar(col);

        mvc.perform(multipart("/colaborador/perfil/certificados")
                        .file(archivo("certificado", "cert.pdf", "application/pdf"))
                        .param("habilidadId", ajena.getId().toString()))
                .andExpect(redirectedUrl("/colaborador/perfil"))
                .andExpect(flash().attribute("mensajeError",
                        "Solo puedes certificar una habilidad activa de tu perfil."));

        assertThat(certificadoRepository.findByColaborador(col)).isEmpty();
    }

    @Test
    void noSePuedeTenerDosCertificadosPendientesDeLaMismaHabilidad() throws Exception {
        Habilidad habilidad = habilidadDelPerfil(col);
        autenticar(col);

        mvc.perform(multipart("/colaborador/perfil/certificados")
                        .file(archivo("certificado", "uno.pdf", "application/pdf"))
                        .param("habilidadId", habilidad.getId().toString()))
                .andExpect(flash().attributeExists("mensajeExito"));

        mvc.perform(multipart("/colaborador/perfil/certificados")
                        .file(archivo("certificado", "dos.pdf", "application/pdf"))
                        .param("habilidadId", habilidad.getId().toString()))
                .andExpect(flash().attribute("mensajeError",
                        "Ya tienes un certificado pendiente de revisión para esta habilidad."));

        assertThat(certificadoRepository.findByColaborador(col)).hasSize(1);
    }

    // ======================= Perfil: eliminar habilidad =======================

    @Test
    void eliminarUnaHabilidadLaDejaInactivaEnElPerfil() throws Exception {
        Habilidad habilidad = habilidadDelPerfil(col);
        autenticar(col);

        mvc.perform(post("/colaborador/perfil/habilidades/{id}/eliminar", habilidad.getId()))
                .andExpect(redirectedUrl("/colaborador/perfil"))
                .andExpect(flash().attribute("mensajeExito", "Habilidad eliminada de tu perfil."));

        ColaboradorHabilidad perfil = colaboradorHabilidadRepository
                .findByColaboradorAndHabilidad(col, habilidad).orElseThrow();
        assertThat(perfil.isActivo()).isFalse();
        assertThat(colaboradorHabilidadRepository.findByColaboradorAndActivoTrue(col)).isEmpty();
    }

    @Test
    void eliminarUnaHabilidadAjenaNoCambiaLaDeOtroColaborador() throws Exception {
        Habilidad habilidad = habilidadDelPerfil(col);
        autenticar(otroCol);

        mvc.perform(post("/colaborador/perfil/habilidades/{id}/eliminar", habilidad.getId()))
                .andExpect(redirectedUrl("/colaborador/perfil"));

        ColaboradorHabilidad perfil = colaboradorHabilidadRepository
                .findByColaboradorAndHabilidad(col, habilidad).orElseThrow();
        assertThat(perfil.isActivo()).isTrue();
    }

    // ======================= Asistente: historial (REST) =======================

    @Test
    void elHistorialDelAsistenteEmpiezaVacio() throws Exception {
        autenticar(col);

        mvc.perform(get("/colaborador/api/asistente/historial").session(new MockHttpSession()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void elHistorialDelAsistenteDevuelveLosMensajesGuardadosEnLaSesion() throws Exception {
        autenticar(col);
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(HISTORIAL, new ArrayList<>(List.of("¿Qué cursos hay?", "Estos son los cursos.")));

        mvc.perform(get("/colaborador/api/asistente/historial").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("¿Qué cursos hay?"))
                .andExpect(jsonPath("$[1]").value("Estos son los cursos."));
    }

    @Test
    void limpiarElHistorialBorraLaConversacionDeLaSesion() throws Exception {
        autenticar(col);
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(HISTORIAL, new ArrayList<>(List.of("hola", "respuesta")));

        mvc.perform(post("/colaborador/api/asistente/historial/limpiar").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Conversación reiniciada."));

        assertThat(sesion.getAttribute(HISTORIAL)).isNull();
        mvc.perform(get("/colaborador/api/asistente/historial").session(sesion))
                .andExpect(content().json("[]"));
    }

    // ======================= ColAsistenteService =======================

    @Test
    void obtenerHistorialDevuelveUnaCopiaYNoModificaLaSesion() {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(HISTORIAL, new ArrayList<>(List.of("a", "b")));

        List<String> copia = asistenteService.obtenerHistorial(sesion);
        copia.add("c");

        assertThat(asistenteService.obtenerHistorial(sesion)).containsExactly("a", "b");
    }

    @Test
    void obtenerHistorialIgnoraUnAtributoQueNoEsUnaLista() {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute(HISTORIAL, "no soy una lista");

        assertThat(asistenteService.obtenerHistorial(sesion)).isEmpty();
    }

    @Test
    void limpiarHistorialSinConversacionPreviaNoFalla() {
        MockHttpSession sesion = new MockHttpSession();

        asistenteService.limpiarHistorial(sesion);

        assertThat(sesion.getAttribute(HISTORIAL)).isNull();
        assertThat(asistenteService.obtenerHistorial(sesion)).isEmpty();
    }

    @Test
    void confirmarSolicitudDeCursoValidaElCursoYElUsuarioAntesDeContinuar() {
        assertThatThrownBy(() -> asistenteService.confirmarSolicitudCurso(col.getId(), null, "Lo necesito."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Debes seleccionar un curso.");
        assertThatThrownBy(() -> asistenteService.confirmarSolicitudCurso(999999999L, 1L, "Lo necesito."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No encontramos tu usuario.");
        assertThatThrownBy(() -> asistenteService.confirmarSolicitudCurso(col.getId(), 999999999L, "Lo necesito."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El curso seleccionado no existe.");
    }

    @Test
    void confirmarSolicitudDeCursoCreaLaSolicitudYDejaAuditoria() {
        Curso curso = curso(usuario("admin", "ADMINISTRADOR"));

        asistenteService.confirmarSolicitudCurso(col.getId(), curso.getId(), "Quiero reforzar esto.");

        List<ColaboradorCurso> solicitudes = colaboradorCursoRepository.findByColaborador(col);
        assertThat(solicitudes).hasSize(1);
        assertThat(solicitudes.get(0).getEstado()).isEqualTo(EstadoColaboradorCurso.SOLICITADO);
        assertThat(solicitudes.get(0).getCurso().getId()).isEqualTo(curso.getId());
        assertThat(logAuditoriaRepository.findAll()).anySatisfy(log -> {
            assertThat(log.getAccion()).isEqualTo("ASISTENTE_ACCION_CONFIRMADA");
            assertThat(log.getEntidad()).isEqualTo("CURSO");
            assertThat(log.getEntidadId()).isEqualTo(curso.getId());
        });
    }

    @Test
    void confirmarSolicitudDeCursoDosVecesSeRechazaLaSegunda() {
        Curso curso = curso(usuario("admin", "ADMINISTRADOR"));
        asistenteService.confirmarSolicitudCurso(col.getId(), curso.getId(), null);

        assertThatThrownBy(() -> asistenteService.confirmarSolicitudCurso(col.getId(), curso.getId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ya tienes una solicitud pendiente o una inscripción activa en este curso.");
        assertThat(colaboradorCursoRepository.findByColaborador(col)).hasSize(1);
    }

    @Test
    void confirmarSolicitudDeProyectoValidaProyectoUsuarioYEstado() {
        assertThatThrownBy(() -> asistenteService.confirmarSolicitudProyecto(col.getId(), null, 1L, "Hola"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Debes seleccionar un proyecto.");
        assertThatThrownBy(() -> asistenteService.confirmarSolicitudProyecto(999999999L, 1L, 1L, "Hola"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No encontramos tu usuario.");
        assertThatThrownBy(() -> asistenteService.confirmarSolicitudProyecto(col.getId(), 999999999L, 1L, "Hola"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El proyecto seleccionado no existe.");

        Proyecto enEspera = proyecto(pm);
        enEspera.setEstado(EstadoProyecto.EN_ESPERA);
        proyectoRepository.save(enEspera);
        assertThatThrownBy(() -> asistenteService.confirmarSolicitudProyecto(col.getId(), enEspera.getId(), 1L, "Hola"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Este proyecto ya no está activo.");
    }

    @Test
    void confirmarSolicitudDeProyectoExigeElPerfilRequerido() {
        Proyecto proyecto = proyecto(pm);

        assertThatThrownBy(() -> asistenteService.confirmarSolicitudProyecto(col.getId(), proyecto.getId(), null, "Hola"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selecciona el perfil al que deseas postularte.");
        assertThatThrownBy(() -> asistenteService.confirmarSolicitudProyecto(col.getId(), proyecto.getId(), 999999999L, "Hola"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El perfil seleccionado no es válido.");
        Habilidad noRequerida = habilidad();
        assertThatThrownBy(() -> asistenteService.confirmarSolicitudProyecto(
                col.getId(), proyecto.getId(), noRequerida.getId(), "Hola"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ese perfil no pertenece a este proyecto.");
    }

    // ======================= Ayudas =======================

    private String destinoDocumentos(Asignacion asignacion) {
        return "/colaborador/proyectos/detalle?asignacionId=" + asignacion.getId() + "&tab=documentos";
    }

    private MockMultipartFile archivo(String campo, String nombre, String tipo) {
        return new MockMultipartFile(campo, nombre, tipo, PDF);
    }

    private void autenticar(Usuario usuario) {
        UsuarioDetails details = new UsuarioDetails(usuario);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    private Documento documento(Proyecto proyecto, Usuario subidoPor) {
        Documento d = new Documento();
        d.setProyecto(proyecto);
        d.setSubidoPor(subidoPor);
        d.setNombre("manual.pdf");
        d.setCategoria(CategoriaDocumento.PDF);
        d.setArchivoUrl("documentos/prueba/manual-" + UUID.randomUUID() + ".pdf");
        return documentoRepository.save(d);
    }

    private Habilidad habilidad() {
        CategoriaHabilidad categoria = new CategoriaHabilidad();
        categoria.setNombre("cat-" + UUID.randomUUID().toString().substring(0, 8));
        categoria.setActiva(true);
        categoria = categoriaHabilidadRepository.save(categoria);

        Habilidad h = new Habilidad();
        h.setNombre("hab-" + UUID.randomUUID().toString().substring(0, 8));
        h.setCategoria(categoria);
        h.setActiva(true);
        return habilidadRepository.save(h);
    }

    private Habilidad habilidadDelPerfil(Usuario colaborador) {
        Habilidad habilidad = habilidad();
        ColaboradorHabilidad ch = new ColaboradorHabilidad();
        ch.setId(new ColaboradorHabilidadId(colaborador.getId(), habilidad.getId()));
        ch.setColaborador(colaborador);
        ch.setHabilidad(habilidad);
        ch.setNivelDominio(NivelDominio.INTERMEDIO);
        ch.setActivo(true);
        colaboradorHabilidadRepository.save(ch);
        return habilidad;
    }

    private Curso curso(Usuario creador) {
        Curso c = new Curso();
        c.setNombre("Curso asistente " + UUID.randomUUID().toString().substring(0, 8));
        c.setDescripcion("Curso de prueba.");
        c.setCategoria("Técnico");
        c.setHoras(new BigDecimal("8.00"));
        c.setActivo(true);
        c.setCreadoPor(creador);
        return cursoRepository.save(c);
    }

    private Asignacion asignar(Usuario colaborador, Proyecto proyecto, EstadoAsignacion estado) {
        Asignacion a = new Asignacion();
        a.setProyecto(proyecto);
        a.setColaborador(colaborador);
        a.setHorasSemanales(new BigDecimal("8"));
        a.setOrigen(OrigenAsignacion.PROPUESTA_PM);
        a.setEstado(estado);
        a.setAprobadoPorPm(true);
        a.setAprobadoPorRm(true);
        return asignacionRepository.save(a);
    }

    private Proyecto proyecto(Usuario pmProyecto) {
        Proyecto p = new Proyecto();
        p.setNombre("Proyecto pendientes " + System.nanoTime());
        p.setDescripcion("Proyecto de prueba.");
        p.setEstado(EstadoProyecto.ACTIVO);
        p.setPrioridad(Prioridad.MEDIA);
        p.setJustificacionPrioridad("Prueba.");
        p.setColaboradoresRequeridos(3);
        p.setPm(pmProyecto);
        p.setPresupuesto(new BigDecimal("10000.00"));
        p.setFechaInicio(LocalDate.now().minusMonths(1));
        p.setFechaFinEstimada(LocalDate.now().plusMonths(1));
        return proyectoRepository.save(p);
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
}