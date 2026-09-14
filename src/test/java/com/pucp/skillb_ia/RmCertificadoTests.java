package com.pucp.skillb_ia;

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
        colaborador.setCargo("Backend Developer");
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
                colaborador, habilidad.getId(), archivo);

        assertNotNull(creado.getId());
        assertEquals(EstadoCertificado.PENDIENTE, creado.getEstado());
        assertTrue(creado.getArchivoUrl().startsWith("/uploads/certificados/certificado-"));
        assertEquals(1, colaboradorPerfilService.listarCertificados(colaborador).size());
        assertThrows(IllegalArgumentException.class,
                () -> colaboradorPerfilService.subirCertificado(
                        colaborador, habilidad.getId(), archivo));
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

    private Certificado guardarPendiente(String archivo) {
        Certificado certificado = new Certificado();
        certificado.setColaborador(colaborador);
        certificado.setHabilidad(habilidad);
        certificado.setArchivoUrl(archivo);
        certificado.setEstado(EstadoCertificado.PENDIENTE);
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
}
