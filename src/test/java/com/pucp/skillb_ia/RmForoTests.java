package com.pucp.skillb_ia;

import com.pucp.skillb_ia.dto.RmForoView;
import com.pucp.skillb_ia.model.*;
import com.pucp.skillb_ia.model.enums.*;
import com.pucp.skillb_ia.repository.*;
import com.pucp.skillb_ia.service.rm.RmForoConsultaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class RmForoTests {
    @Autowired private WebApplicationContext context;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private ForoRepository foroRepository;
    @Autowired private EtiquetaRepository etiquetaRepository;
    @Autowired private PublicacionForoRepository publicacionRepository;
    @Autowired private RespuestaForoRepository respuestaRepository;
    @Autowired private VotoPublicacionRepository votoPublicacionRepository;
    @Autowired private VotoRespuestaRepository votoRespuestaRepository;
    @Autowired private RmForoConsultaService foroService;

    private MockMvc mockMvc;
    private Usuario pm;
    private Usuario colaborador;
    private Usuario votante;
    private Proyecto proyecto;
    private Etiqueta etiqueta;

    @BeforeEach
    void prepararDatos() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        votoRespuestaRepository.deleteAll();
        votoPublicacionRepository.deleteAll();
        respuestaRepository.deleteAll();
        publicacionRepository.deleteAll();
        foroRepository.deleteAll();

        pm = usuario("pm.foro@skillbridge.test", "Paula", "Mora", rol("PROJECT_MANAGER"));
        colaborador = usuario("col.foro@skillbridge.test", "Carlos", "Foro", rol("COLABORADOR"));
        colaborador.setCargo("Backend Developer");
        colaborador = usuarioRepository.save(colaborador);
        votante = usuario("votante.foro@skillbridge.test", "Valeria", "Voto", rol("COLABORADOR"));

        proyecto = proyectoRepository.findAll().stream()
                .filter(item -> "Foro Test".equals(item.getNombre()))
                .findFirst().orElseGet(Proyecto::new);
        proyecto.setNombre("Foro Test");
        proyecto.setDescripcion("Proyecto utilizado para probar la consulta de foros.");
        proyecto.setEstado(EstadoProyecto.ACTIVO);
        proyecto.setPrioridad(Prioridad.MEDIA);
        proyecto.setJustificacionPrioridad("Validación automática.");
        proyecto.setColaboradoresRequeridos(2);
        proyecto.setPm(pm);
        proyecto = proyectoRepository.save(proyecto);

        etiqueta = etiquetaRepository.findAll().stream()
                .filter(item -> "Pregunta Test".equals(item.getNombre()))
                .findFirst().orElseGet(() -> {
                    Etiqueta nueva = new Etiqueta();
                    nueva.setNombre("Pregunta Test");
                    return etiquetaRepository.save(nueva);
                });
    }

    @Test
    void rmPuedeConsultarForosPublicosYPrivadosSinAsignacion() {
        Foro publico = foro("Foro público test", true, proyecto);
        Foro privado = foro("Foro privado test", false, proyecto);

        List<RmForoView> foros = foroService.listar();

        assertTrue(foros.stream().anyMatch(item -> item.getId().equals(publico.getId())));
        assertTrue(foros.stream().anyMatch(item -> item.getId().equals(privado.getId())));
    }

    @Test
    void detalleIncluyeRespuestasSolucionVotosYOrdenamiento() {
        Foro foro = foro("Foro con actividad test", false, proyecto);
        PublicacionForo antigua = publicacion(foro, colaborador, "Publicación más votada",
                LocalDateTime.now().minusDays(2));
        PublicacionForo reciente = publicacion(foro, pm, "Publicación más reciente",
                LocalDateTime.now().minusHours(2));
        RespuestaForo respuesta = respuesta(antigua, pm, true,
                LocalDateTime.now().minusDays(1));
        voto(antigua, colaborador, TipoVoto.POSITIVO);
        voto(antigua, votante, TipoVoto.POSITIVO);
        votoRespuesta(respuesta, votante, TipoVoto.POSITIVO);

        RmForoView porVotos = foroService.obtener(foro.getId(), "votos");
        RmForoView porFecha = foroService.obtener(foro.getId(), "fecha");

        assertEquals("Publicación más votada", porVotos.getPublicaciones().get(0).getTitulo());
        assertEquals("Publicación más reciente", porFecha.getPublicaciones().get(0).getTitulo());
        RmForoView.Publicacion publicacion = porVotos.getPublicaciones().get(0);
        assertEquals(2, publicacion.getPuntaje());
        assertEquals(1, publicacion.getRespuestas().size());
        assertTrue(publicacion.getRespuestas().get(0).isSolucion());
        assertEquals(1, publicacion.getRespuestas().get(0).getPuntaje());
        assertEquals(2, porVotos.getParticipantes());
    }

    @Test
    void renderizaListadoYDetalleDeSoloLectura() throws Exception {
        Foro foro = foro("Foro render test", false, proyecto);
        publicacion(foro, colaborador, "Tema visible", LocalDateTime.now());

        mockMvc.perform(get("/rm/foros"))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-foros"))
                .andExpect(model().attributeExists(
                        "foros", "totalForos", "totalActivos", "totalPublicaciones"));

        mockMvc.perform(get("/rm/foros/detalle")
                        .param("id", foro.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-foro-detalle"))
                .andExpect(model().attributeExists("foro", "ordenActual"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("method=\"post\""))));
    }

    private Foro foro(String nombre, boolean publico, Proyecto proyecto) {
        Foro foro = new Foro();
        foro.setNombre(nombre);
        foro.setTipo(TipoForo.PROYECTO);
        foro.setEsPublico(publico);
        foro.setProyecto(proyecto);
        return foroRepository.save(foro);
    }

    private PublicacionForo publicacion(Foro foro, Usuario autor, String titulo,
                                        LocalDateTime fecha) {
        PublicacionForo publicacion = new PublicacionForo();
        publicacion.setForo(foro);
        publicacion.setAutor(autor);
        publicacion.setEtiqueta(etiqueta);
        publicacion.setTitulo(titulo);
        publicacion.setContenido("Contenido de prueba del foro.");
        publicacion.setFechaCreacion(fecha);
        return publicacionRepository.save(publicacion);
    }

    private RespuestaForo respuesta(PublicacionForo publicacion, Usuario autor,
                                     boolean solucion, LocalDateTime fecha) {
        RespuestaForo respuesta = new RespuestaForo();
        respuesta.setPublicacion(publicacion);
        respuesta.setAutor(autor);
        respuesta.setContenido("Respuesta de prueba.");
        respuesta.setEsSolucion(solucion);
        respuesta.setFechaCreacion(fecha);
        return respuestaRepository.save(respuesta);
    }

    private void voto(PublicacionForo publicacion, Usuario usuario, TipoVoto tipo) {
        VotoPublicacion voto = new VotoPublicacion();
        voto.setId(new VotoPublicacionId(usuario.getId(), publicacion.getId()));
        voto.setUsuario(usuario);
        voto.setPublicacion(publicacion);
        voto.setTipo(tipo);
        votoPublicacionRepository.save(voto);
    }

    private void votoRespuesta(RespuestaForo respuesta, Usuario usuario, TipoVoto tipo) {
        VotoRespuesta voto = new VotoRespuesta();
        voto.setId(new VotoRespuestaId(usuario.getId(), respuesta.getId()));
        voto.setUsuario(usuario);
        voto.setRespuesta(respuesta);
        voto.setTipo(tipo);
        votoRespuestaRepository.save(voto);
    }

    private Rol rol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol rol = new Rol();
            rol.setNombre(nombre);
            return rolRepository.save(rol);
        });
    }

    private Usuario usuario(String correo, String nombre, String apellido, Rol rol) {
        Usuario usuario = usuarioRepository.findByCorreo(correo).orElseGet(Usuario::new);
        usuario.setCorreo(correo);
        usuario.setNombre(nombre);
        usuario.setApellido(apellido);
        usuario.setRol(rol);
        usuario.setActivo(true);
        return usuarioRepository.save(usuario);
    }
}
