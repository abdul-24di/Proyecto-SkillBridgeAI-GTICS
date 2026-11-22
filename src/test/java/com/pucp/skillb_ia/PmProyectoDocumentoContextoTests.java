package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.pm.PmProyectoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

// El documento de contexto que adjunta el PM al crear el proyecto se guarda y queda visible para el RM.
@SpringBootTest
@ActiveProfiles("test")
class PmProyectoDocumentoContextoTests {

    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private PmProyectoService pmProyectoService;

    private Usuario pm;
    private Usuario otroPm;
    private Proyecto proyecto;

    @BeforeEach
    void prepararDatos() {
        Rol rolPm = obtenerRol("PROJECT_MANAGER");
        pm = obtenerUsuario("pm.contexto@skillbridge.test", "Pia", rolPm);
        otroPm = obtenerUsuario("pm.otro.contexto@skillbridge.test", "Omar", rolPm);

        Proyecto nuevo = new Proyecto();
        nuevo.setNombre("Proyecto contexto " + System.nanoTime());
        nuevo.setDescripcion("Proyecto para probar el documento de contexto.");
        nuevo.setEstado(EstadoProyecto.EN_REVISION);
        nuevo.setPrioridad(Prioridad.MEDIA);
        nuevo.setJustificacionPrioridad("Prueba.");
        nuevo.setColaboradoresRequeridos(2);
        nuevo.setPm(pm);
        nuevo.setPresupuestoSolicitado(new BigDecimal("1000.00"));
        nuevo.setFechaInicio(LocalDate.now().plusDays(1));
        nuevo.setFechaFinEstimada(LocalDate.now().plusMonths(2));
        proyecto = proyectoRepository.save(nuevo);
    }

    @Test
    void guardaElDocumentoYRegistraSuUrl() {
        MockMultipartFile pdf = new MockMultipartFile("documentoProyecto", "contexto.pdf", "application/pdf",
                "%PDF-1.4 contenido".getBytes(StandardCharsets.UTF_8));

        pmProyectoService.guardarDocumentoContexto(proyecto.getId(), pdf, pm);

        String url = proyectoRepository.findById(proyecto.getId()).orElseThrow().getDocumentoContextoUrl();
        assertNotNull(url);
        assertTrue(url.startsWith("/uploads/documentos/proyecto-" + proyecto.getId() + "/contexto-"), url);
        assertTrue(url.endsWith(".pdf"), url);
    }

    @Test
    void sinArchivoNoCambiaNada() {
        MockMultipartFile vacio = new MockMultipartFile("documentoProyecto", "", "application/octet-stream", new byte[0]);

        assertNull(pmProyectoService.validarDocumentoContexto(vacio));
        pmProyectoService.guardarDocumentoContexto(proyecto.getId(), vacio, pm);

        assertNull(proyectoRepository.findById(proyecto.getId()).orElseThrow().getDocumentoContextoUrl());
    }

    @Test
    void rechazaExtensionNoPermitida() {
        MockMultipartFile exe = new MockMultipartFile("documentoProyecto", "programa.exe", "application/octet-stream",
                new byte[]{1, 2, 3});

        assertNotNull(pmProyectoService.validarDocumentoContexto(exe));
        assertThrows(IllegalArgumentException.class,
                () -> pmProyectoService.guardarDocumentoContexto(proyecto.getId(), exe, pm));
        assertNull(proyectoRepository.findById(proyecto.getId()).orElseThrow().getDocumentoContextoUrl());
    }

    @Test
    void otroPmNoPuedeAdjuntarDocumento() {
        MockMultipartFile pdf = new MockMultipartFile("documentoProyecto", "contexto.pdf", "application/pdf",
                "%PDF-1.4 contenido".getBytes(StandardCharsets.UTF_8));

        assertThrows(SecurityException.class,
                () -> pmProyectoService.guardarDocumentoContexto(proyecto.getId(), pdf, otroPm));
        assertNull(proyectoRepository.findById(proyecto.getId()).orElseThrow().getDocumentoContextoUrl());
    }

    private Rol obtenerRol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol rol = new Rol();
            rol.setNombre(nombre);
            return rolRepository.save(rol);
        });
    }

    private Usuario obtenerUsuario(String correo, String nombre, Rol rol) {
        return usuarioRepository.findByCorreo(correo).orElseGet(() -> {
            Usuario usuario = new Usuario();
            usuario.setCorreo(correo);
            usuario.setNombre(nombre);
            usuario.setApellido("PM");
            usuario.setRol(rol);
            return usuarioRepository.save(usuario);
        });
    }
}
