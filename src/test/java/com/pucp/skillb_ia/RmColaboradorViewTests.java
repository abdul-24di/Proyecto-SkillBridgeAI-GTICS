package com.pucp.skillb_ia;

import com.pucp.skillb_ia.dto.RmColaboradorDetalle;
import com.pucp.skillb_ia.dto.RmColaboradorResumen;
import com.pucp.skillb_ia.model.CategoriaHabilidad;
import com.pucp.skillb_ia.model.ColaboradorHabilidad;
import com.pucp.skillb_ia.model.Habilidad;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.Cargo;
import com.pucp.skillb_ia.model.enums.EstadoValidacion;
import com.pucp.skillb_ia.model.enums.NivelDominio;
import com.pucp.skillb_ia.repository.CargoRepository;
import com.pucp.skillb_ia.repository.CategoriaHabilidadRepository;
import com.pucp.skillb_ia.repository.ColaboradorHabilidadRepository;
import com.pucp.skillb_ia.repository.HabilidadRepository;
import com.pucp.skillb_ia.model.enums.NivelExperiencia;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.service.rm.RmColaboradorConsultaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@ActiveProfiles("test")
class RmColaboradorViewTests {

    // Nombres únicos para que la única coincidencia posible sea la propia habilidad.
    private static final String VALIDADA = "Java Validada T015";
    private static final String PENDIENTE = "Go Pendiente T015";
    private static final String RECHAZADA = "Rust Rechazada T015";
    private static final String INACTIVA = "Cobol Inactiva T015";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired private CargoRepository cargoRepository;
    @Autowired private CategoriaHabilidadRepository categoriaRepository;
    @Autowired private HabilidadRepository habilidadRepository;
    @Autowired private ColaboradorHabilidadRepository colaboradorHabilidadRepository;
    @Autowired private RmColaboradorConsultaService consultaService;

    private MockMvc mockMvc;
    private Long colaboradorId;

    @BeforeEach
    void prepararDatos() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        Usuario colaborador = usuarioRepository.findByCorreo("colaborador.vista@skillbridge.test")
                .orElseGet(() -> {
                    Rol rol = rolRepository.findByNombre("COLABORADOR")
                            .orElseGet(() -> {
                                Rol nuevoRol = new Rol();
                                nuevoRol.setNombre("COLABORADOR");
                                return rolRepository.save(nuevoRol);
                            });

                    Usuario nuevoColaborador = new Usuario();
                    nuevoColaborador.setCorreo("colaborador.vista@skillbridge.test");
                    nuevoColaborador.setNombre("María");
                    nuevoColaborador.setApellido("Prueba");
                    nuevoColaborador.setCargo(cargoDePrueba("Backend Developer"));
                    nuevoColaborador.setRol(rol);
                    nuevoColaborador.setActivo(true);
                    nuevoColaborador.setNivelExperiencia(NivelExperiencia.SEMI_SENIOR);
                    nuevoColaborador.setHorasDisponibles(BigDecimal.valueOf(16));
                    nuevoColaborador.setAniosExperiencia(BigDecimal.valueOf(3));
                    return usuarioRepository.save(nuevoColaborador);
                });

        colaboradorId = colaborador.getId();

        asignarHabilidad(colaborador, VALIDADA, EstadoValidacion.VALIDADA, true);
        asignarHabilidad(colaborador, PENDIENTE, EstadoValidacion.PENDIENTE, true);
        asignarHabilidad(colaborador, RECHAZADA, EstadoValidacion.RECHAZADA, true);
        asignarHabilidad(colaborador, INACTIVA, EstadoValidacion.VALIDADA, false);
    }

    @Test
    void renderizaDirectorioConDatosReales() throws Exception {
        mockMvc.perform(get("/rm/colaboradores"))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-colaboradores"))
                .andExpect(model().attributeExists("colaboradores", "totalColaboradores"));
    }

    @Test
    void renderizaPerfilDelColaboradorSeleccionado() throws Exception {
        mockMvc.perform(get("/rm/colaboradores/perfil").param("id", colaboradorId.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-perfil-colaborador"))
                .andExpect(model().attributeExists("colaborador"));
    }

    @Test
    void resumenYTextoDeBusquedaUsanSoloHabilidadesValidadas() {
        RmColaboradorResumen resumen = consultaService.listarColaboradoresActivos().stream()
                .filter(item -> item.getId().equals(colaboradorId))
                .findFirst().orElseThrow();

        assertEquals(List.of(VALIDADA), resumen.getHabilidades());
        assertTrue(resumen.getTextoBusqueda().contains(VALIDADA));
        assertFalse(resumen.getTextoBusqueda().contains(PENDIENTE));
        assertFalse(resumen.getTextoBusqueda().contains(RECHAZADA));
        assertFalse(resumen.getTextoBusqueda().contains(INACTIVA));

        // Misma comparación que hace el filtro del directorio sobre data-search.
        assertTrue(coincideBusqueda(VALIDADA).contains(colaboradorId));
        assertFalse(coincideBusqueda(PENDIENTE).contains(colaboradorId));
        assertFalse(coincideBusqueda(RECHAZADA).contains(colaboradorId));
        assertFalse(coincideBusqueda(INACTIVA).contains(colaboradorId));
    }

    @Test
    void directorioMuestraSoloHabilidadesValidadasEnLasTarjetas() throws Exception {
        String html = mockMvc.perform(get("/rm/colaboradores"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(html.contains(VALIDADA));
        assertFalse(html.contains(PENDIENTE));
        assertFalse(html.contains(RECHAZADA));
        assertFalse(html.contains(INACTIVA));
    }

    @Test
    void perfilCompletoConservaTodasLasHabilidadesActivasConSuEstado() throws Exception {
        RmColaboradorDetalle detalle = consultaService.obtenerDetalle(colaboradorId);
        Map<String, String> estados = detalle.getHabilidades().stream()
                .collect(Collectors.toMap(RmColaboradorDetalle.HabilidadDetalle::getNombre,
                        RmColaboradorDetalle.HabilidadDetalle::getEstadoCodigo));
        assertEquals(Map.of(VALIDADA, "VALIDADA", PENDIENTE, "PENDIENTE", RECHAZADA, "RECHAZADA"), estados);
        // El resumen del mismo detalle (cabecera y propuesta) sigue siendo solo validado.
        assertEquals(List.of(VALIDADA), detalle.getResumen().getHabilidades());

        String html = mockMvc.perform(get("/rm/colaboradores/perfil").param("id", colaboradorId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(html.contains(VALIDADA));
        assertTrue(html.contains(PENDIENTE));
        assertTrue(html.contains(RECHAZADA));
        assertFalse(html.contains(INACTIVA));
        assertInsigniaDeEstado(html);
    }

    // Cada estado tiene su propia insignia (color y texto).
    static void assertInsigniaDeEstado(String html) {
        assertTrue(html.contains("class=\"badge bg-green-lt\" data-estado-habilidad=\"VALIDADA\">Validada<"));
        assertTrue(html.contains("class=\"badge bg-yellow-lt\" data-estado-habilidad=\"PENDIENTE\">Pendiente<"));
        assertTrue(html.contains("class=\"badge bg-red-lt\" data-estado-habilidad=\"RECHAZADA\">Rechazada<"));
    }

    private List<Long> coincideBusqueda(String termino) {
        String buscado = termino.toLowerCase();
        return consultaService.listarColaboradoresActivos().stream()
                .filter(item -> item.getTextoBusqueda().toLowerCase().contains(buscado))
                .map(RmColaboradorResumen::getId)
                .toList();
    }

    private void asignarHabilidad(Usuario colaborador, String nombre, EstadoValidacion estado, boolean activo) {
        CategoriaHabilidad categoria = categoriaRepository.findByNombreIgnoreCase("Habilidades T015")
                .orElseGet(() -> {
                    CategoriaHabilidad nueva = new CategoriaHabilidad();
                    nueva.setNombre("Habilidades T015");
                    return categoriaRepository.save(nueva);
                });
        Habilidad habilidad = habilidadRepository.findByNombreIgnoreCase(nombre).orElseGet(() -> {
            Habilidad nueva = new Habilidad();
            nueva.setNombre(nombre);
            nueva.setCategoria(categoria);
            return habilidadRepository.save(nueva);
        });
        ColaboradorHabilidad perfil = colaboradorHabilidadRepository
                .findByColaboradorAndHabilidad(colaborador, habilidad)
                .orElseGet(() -> {
                    ColaboradorHabilidad nueva = new ColaboradorHabilidad();
                    nueva.setColaborador(colaborador);
                    nueva.setHabilidad(habilidad);
                    return nueva;
                });
        perfil.setNivelDominio(NivelDominio.INTERMEDIO);
        perfil.setEstadoValidacion(estado);
        perfil.setActivo(activo);
        colaboradorHabilidadRepository.save(perfil);
    }

    private Cargo cargoDePrueba(String nombre) {
        return cargoRepository.findByNombre(nombre).orElseGet(() -> cargoRepository.save(
                new Cargo(nombre, new BigDecimal("2000"), new BigDecimal("3000"), new BigDecimal("4000"))));
    }
}
