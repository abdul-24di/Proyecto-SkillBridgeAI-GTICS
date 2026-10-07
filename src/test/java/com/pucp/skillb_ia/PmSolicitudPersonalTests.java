package com.pucp.skillb_ia;

import com.pucp.skillb_ia.dto.RmSolicitudPersonalView;
import com.pucp.skillb_ia.dto.pm.PmSolicitudPersonalForm;
import com.pucp.skillb_ia.model.Asignacion;
import com.pucp.skillb_ia.model.CategoriaHabilidad;
import com.pucp.skillb_ia.model.Habilidad;
import com.pucp.skillb_ia.model.LogAuditoria;
import com.pucp.skillb_ia.model.Notificacion;
import com.pucp.skillb_ia.model.Proyecto;
import com.pucp.skillb_ia.model.ProyectoHabilidadRequerida;
import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.SolicitudPersonal;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.model.enums.EstadoAsignacion;
import com.pucp.skillb_ia.model.enums.EstadoProyecto;
import com.pucp.skillb_ia.model.enums.EstadoSolicitudPersonal;
import com.pucp.skillb_ia.model.enums.NivelDominio;
import com.pucp.skillb_ia.model.enums.OrigenAsignacion;
import com.pucp.skillb_ia.model.enums.Prioridad;
import com.pucp.skillb_ia.repository.AsignacionRepository;
import com.pucp.skillb_ia.repository.CategoriaHabilidadRepository;
import com.pucp.skillb_ia.repository.HabilidadRepository;
import com.pucp.skillb_ia.repository.LogAuditoriaRepository;
import com.pucp.skillb_ia.repository.NotificacionRepository;
import com.pucp.skillb_ia.repository.ProyectoHabilidadRequeridaRepository;
import com.pucp.skillb_ia.repository.ProyectoRepository;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.SolicitudPersonalRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

// TASK-058: el PM solicita personal al RM desde el detalle del proyecto.
@SpringBootTest
@ActiveProfiles("test")
class PmSolicitudPersonalTests {

    private static final String EXITO = "Solicitud de personal enviada. Los Resource Managers fueron notificados.";

    @Autowired private WebApplicationContext context;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProyectoRepository proyectoRepository;
    @Autowired private SolicitudPersonalRepository solicitudRepository;
    @Autowired private NotificacionRepository notificacionRepository;
    @Autowired private LogAuditoriaRepository logAuditoriaRepository;
    @Autowired private CategoriaHabilidadRepository categoriaRepository;
    @Autowired private HabilidadRepository habilidadRepository;
    @Autowired private ProyectoHabilidadRequeridaRepository habilidadRequeridaRepository;
    @Autowired private AsignacionRepository asignacionRepository;

    private MockMvc mockMvc;
    private Usuario pm;
    private Usuario otroPm;
    private Usuario rm;
    private Proyecto proyecto;

    @BeforeEach
    void prepararDatos() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        pm = obtenerUsuario("pm.solicitud.personal@skillbridge.test", "Patricia", "Paredes", obtenerRol("PROJECT_MANAGER"));
        otroPm = obtenerUsuario("pm.ajeno.solicitud@skillbridge.test", "Pedro", "Ajeno", obtenerRol("PROJECT_MANAGER"));
        rm = obtenerUsuario("rm.solicitud.personal@skillbridge.test", "Rita", "Ramos", obtenerRol("RESOURCE_MANAGER"));
        proyecto = proyecto(EstadoProyecto.ACTIVO);
        autenticar(pm, "PROJECT_MANAGER");
    }

    @AfterEach
    void limpiarSesion() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void creaLaSolicitudConPrgAuditoriaYNotificacionALosRm() throws Exception {
        mockMvc.perform(post(ruta(proyecto))
                        .param("cantidad", "2")
                        .param("perfiles", "  Backend Java y QA  ")
                        .param("mensaje", "Priorizar experiencia en Docker."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(detalle(proyecto)))
                .andExpect(flash().attribute("success", EXITO))
                .andExpect(flash().attributeCount(1));

        SolicitudPersonal solicitud = unicaSolicitud(proyecto);
        assertEquals(EstadoSolicitudPersonal.PENDIENTE, solicitud.getEstado());
        assertEquals(2, solicitud.getCantidadColaboradores());
        assertEquals("Backend Java y QA", solicitud.getPerfilesRequeridos());
        assertEquals("Priorizar experiencia en Docker.", solicitud.getMensajePm());
        assertNull(solicitud.getRmResponsable());

        List<LogAuditoria> auditorias = auditorias(solicitud);
        assertEquals(1, auditorias.size());
        assertEquals(pm.getId(), auditorias.get(0).getUsuario().getId());
        assertTrue(auditorias.get(0).getDetalle().contains("2 colaborador(es)"));

        List<Notificacion> notificaciones = notificacionesPendientes(rm, solicitud);
        assertEquals(1, notificaciones.size());
        assertEquals("SOLICITUD_PERSONAL", notificaciones.get(0).getEntidad());
        assertTrue(notificaciones.get(0).getDescripcion().contains(proyecto.getNombre()));
        assertTrue(notificacionesPendientes(pm, solicitud).isEmpty(), "El PM no recibe la notificación de los RM.");
    }

    @Test
    void mensajeOpcionalYLimitesExactosSeAceptan() throws Exception {
        mockMvc.perform(post(ruta(proyecto))
                        .param("cantidad", "1")
                        .param("perfiles", "p".repeat(1000))
                        .param("mensaje", ""))
                .andExpect(redirectedUrl(detalle(proyecto)))
                .andExpect(flash().attribute("success", EXITO));

        SolicitudPersonal solicitud = unicaSolicitud(proyecto);
        assertEquals(1000, solicitud.getPerfilesRequeridos().length());
        assertNull(solicitud.getMensajePm());

        Proyecto otro = proyecto(EstadoProyecto.EN_ESPERA);
        mockMvc.perform(post(ruta(otro))
                        .param("cantidad", "3")
                        .param("perfiles", "Analista")
                        .param("mensaje", "m".repeat(1000)))
                .andExpect(redirectedUrl(detalle(otro)))
                .andExpect(flash().attribute("success", EXITO));
        assertEquals(1000, unicaSolicitud(otro).getMensajePm().length());
    }

    @Test
    void validacionesDelFormularioNoCreanNadaYReabrenElModal() throws Exception {
        String[][] casos = {
                // cantidad, perfiles, mensaje, mensaje esperado
                {"0", "Backend", "", "La cantidad solicitada debe ser mayor que cero."},
                {"-3", "Backend", "", "La cantidad solicitada debe ser mayor que cero."},
                {"", "Backend", "", "La cantidad de colaboradores es obligatoria."},
                {"abc", "Backend", "", "La cantidad debe ser un número entero mayor que cero."},
                {"1.5", "Backend", "", "La cantidad debe ser un número entero mayor que cero."},
                // Sin desglose por habilidad, todo el total debe describirse en Perfiles requeridos.
                {"2", "", "", "Describe en Perfiles requeridos los 2 colaborador(es) que no salen de las habilidades del proyecto."},
                {"2", "   ", "", "Describe en Perfiles requeridos los 2 colaborador(es) que no salen de las habilidades del proyecto."},
                {"2", "p".repeat(1001), "", "Los perfiles requeridos no pueden exceder 1000 caracteres."},
                {"2", "Backend", "m".repeat(1001), "El mensaje no puede exceder 1000 caracteres."},
                {"0", "p".repeat(1001), "m".repeat(1001), "La cantidad solicitada debe ser mayor que cero. "
                        + "Los perfiles requeridos no pueden exceder 1000 caracteres. El mensaje no puede exceder 1000 caracteres."},
        };
        for (String[] caso : casos) {
            MvcResult resultado = mockMvc.perform(post(ruta(proyecto))
                            .param("cantidad", caso[0])
                            .param("perfiles", caso[1])
                            .param("mensaje", caso[2]))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl(detalle(proyecto) + "#requestStaffModal"))
                    .andExpect(flash().attribute("error", caso[3]))
                    .andReturn();
            PmSolicitudPersonalForm form = (PmSolicitudPersonalForm) resultado.getFlashMap().get("pmSolicitudPersonalForm");
            assertEquals(caso[1], form.getPerfiles(), "Conserva lo ingresado: " + caso[3]);
        }
        assertEquals(0, solicitudes(proyecto).size());
        assertTrue(notificacionesPendientes(rm, null).isEmpty());
    }

    @Test
    void errorDeValidacionSeMuestraConLosValoresIngresados() throws Exception {
        MvcResult resultado = mockMvc.perform(post(ruta(proyecto))
                        .param("cantidad", "0")
                        .param("perfiles", "Perfil <DevOps>")
                        .param("mensaje", "Mensaje conservado"))
                .andReturn();

        String html = html(mockMvc.perform(get(detalle(proyecto)).flashAttrs(resultado.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(view().name("pm/pm-detalle-proyecto"))
                .andReturn());
        assertTrue(html.contains("class=\"alert alert-danger alert-dismissible\""));
        assertTrue(html.contains("La cantidad solicitada debe ser mayor que cero."));
        assertTrue(html.contains("Perfil &lt;DevOps&gt;</textarea>"), "Escapa y conserva los perfiles.");
        assertTrue(html.contains("Mensaje conservado</textarea>"));
        assertTrue(html.contains("value=\"0\""));
    }

    @Test
    void elPmSaleDeLaSesionYNoDelFormulario() throws Exception {
        mockMvc.perform(post(ruta(proyecto))
                        .param("cantidad", "1")
                        .param("perfiles", "Frontend")
                        .param("pmId", otroPm.getId().toString()))
                .andExpect(flash().attribute("success", EXITO));

        assertEquals(pm.getId(), auditorias(unicaSolicitud(proyecto)).get(0).getUsuario().getId());
    }

    @Test
    void otroPmNoPuedeSolicitarPersonalParaUnProyectoAjeno() throws Exception {
        autenticar(otroPm, "PROJECT_MANAGER");

        mockMvc.perform(post(ruta(proyecto))
                        .param("cantidad", "1")
                        .param("perfiles", "Frontend")
                        .param("pmId", pm.getId().toString()))
                .andExpect(redirectedUrl("/pm/proyectos"))
                .andExpect(flash().attribute("error", "No tienes permiso para acceder a este proyecto."));

        // Tampoco con desglose: se rechaza antes de leer las habilidades del proyecto.
        Habilidad java = requisito(proyecto, "Java T058", NivelDominio.AVANZADO, 3);
        mockMvc.perform(post(ruta(proyecto))
                        .param("cantidad", "1")
                        .param("cantidadesPorHabilidad[" + java.getId() + "]", "1"))
                .andExpect(redirectedUrl("/pm/proyectos"))
                .andExpect(flash().attribute("error", "No tienes permiso para acceder a este proyecto."));

        assertEquals(0, solicitudes(proyecto).size());
        assertTrue(notificacionesPendientes(rm, null).isEmpty());
    }

    @Test
    void modalMuestraLasHabilidadesDelProyectoConSusCuposLibres() throws Exception {
        Habilidad java = requisito(proyecto, "Java T058", NivelDominio.AVANZADO, 3);
        Habilidad qa = requisito(proyecto, "QA T058", NivelDominio.INTERMEDIO, 1);
        asignacionActiva(java);
        asignacionActiva(qa);

        String html = html(mockMvc.perform(get(detalle(proyecto))).andExpect(status().isOk()).andReturn());

        assertTrue(html.contains(">Java T058</span>"));
        assertTrue(html.contains(">Avanzado</span>"));
        assertTrue(html.contains("Exigidos: 3 · Activos: 1 · Disponibles: 2"));
        assertTrue(html.contains("Exigidos: 1 · Activos: 1 · Disponibles: 0"));
        String compacto = html.replaceAll("\\s+", " ");
        assertTrue(compacto.contains("name=\"cantidadesPorHabilidad[" + java.getId() + "]\" value=\"0\" max=\"2\""));
        // Sin cupos: el campo queda en 0 y de solo lectura.
        assertTrue(compacto.contains("name=\"cantidadesPorHabilidad[" + qa.getId() + "]\" value=\"0\" max=\"0\" "
                + "aria-label=\"Colaboradores de QA T058\" readonly=\"readonly\""));
        assertTrue(html.indexOf("Java T058") < html.indexOf("QA T058"), "Orden alfabético.");
        assertFalse(html.contains("El proyecto no tiene habilidades registradas."));
        // La cantidad total tiene como tope los colaboradores requeridos del proyecto (5).
        String campoTotal = compacto.substring(compacto.indexOf("id=\"staffQuantity\""));
        assertTrue(campoTotal.substring(0, campoTotal.indexOf('>')).contains("max=\"5\""));
        assertTrue(html.contains("Máximo 5 (colaboradores requeridos del proyecto)."));
    }

    @Test
    void desglosePorHabilidadYOtrosPerfilesLlegaAlRmComoTexto() throws Exception {
        Habilidad java = requisito(proyecto, "Java T058", NivelDominio.AVANZADO, 3);
        Habilidad qa = requisito(proyecto, "QA T058", NivelDominio.INTERMEDIO, 1);
        asignacionActiva(java);

        mockMvc.perform(post(ruta(proyecto))
                        .param("cantidad", "4")
                        .param("cantidadesPorHabilidad[" + qa.getId() + "]", "1")
                        .param("cantidadesPorHabilidad[" + java.getId() + "]", "2")
                        .param("perfiles", "  Diseñador UX con Figma  "))
                .andExpect(redirectedUrl(detalle(proyecto)))
                .andExpect(flash().attribute("success", EXITO));

        SolicitudPersonal solicitud = unicaSolicitud(proyecto);
        assertEquals(4, solicitud.getCantidadColaboradores());
        String esperado = "Por habilidad del proyecto:\n"
                + "• Java T058 · Avanzado: 2 colaborador(es)\n"
                + "• QA T058 · Intermedio: 1 colaborador(es)\n"
                + "Otros perfiles (1):\n"
                + "Diseñador UX con Figma";
        assertEquals(esperado, solicitud.getPerfilesRequeridos());
        assertEquals(1, auditorias(solicitud).size());
        assertEquals(1, notificacionesPendientes(rm, solicitud).size());

        // El RM lo ve en "Perfiles requeridos" del detalle (con saltos de línea).
        autenticar(rm, "RESOURCE_MANAGER");
        String detalleRm = html(mockMvc.perform(get("/rm/asignaciones/solicitudes-colaboradores/detalle")
                        .param("id", solicitud.getId().toString()))
                .andExpect(status().isOk()).andReturn());
        assertTrue(detalleRm.contains("style=\"white-space: pre-line\">" + esperado + "</div>"));
    }

    @Test
    void desgloseQueCubreElTotalNoExigePerfiles() throws Exception {
        Habilidad java = requisito(proyecto, "Java T058", NivelDominio.BASICO, 2);

        mockMvc.perform(post(ruta(proyecto))
                        .param("cantidad", "2")
                        .param("cantidadesPorHabilidad[" + java.getId() + "]", "2")
                        .param("perfiles", ""))
                .andExpect(flash().attribute("success", EXITO));
        assertEquals("Por habilidad del proyecto:\n• Java T058 · Básico: 2 colaborador(es)",
                unicaSolicitud(proyecto).getPerfilesRequeridos());

        // Con perfiles escritos y sin cupo para "otros", se guardan como comentario.
        Proyecto otro = proyecto(EstadoProyecto.ACTIVO);
        Habilidad qa = requisito(otro, "QA T058", null, 1);
        mockMvc.perform(post(ruta(otro))
                        .param("cantidad", "1")
                        .param("cantidadesPorHabilidad[" + qa.getId() + "]", "1")
                        .param("perfiles", "Con experiencia en Selenium"))
                .andExpect(flash().attribute("success", EXITO));
        assertEquals("Por habilidad del proyecto:\n• QA T058 · Cualquier nivel: 1 colaborador(es)\n"
                + "Comentarios sobre los perfiles:\nCon experiencia en Selenium", unicaSolicitud(otro).getPerfilesRequeridos());
    }

    @Test
    void reglasDelDesgloseNoCreanNadaYReabrenElModal() throws Exception {
        Habilidad java = requisito(proyecto, "Java T058", NivelDominio.AVANZADO, 3);
        Habilidad qa = requisito(proyecto, "QA T058", NivelDominio.INTERMEDIO, 1);
        Habilidad ajena = habilidad("Go T058");
        asignacionActiva(java);
        asignacionActiva(qa);
        String j = "cantidadesPorHabilidad[" + java.getId() + "]";
        String q = "cantidadesPorHabilidad[" + qa.getId() + "]";
        String a = "cantidadesPorHabilidad[" + ajena.getId() + "]";

        Object[][] casos = {
                // cantidad, parámetros del desglose, perfiles, mensaje esperado
                {"5", new String[]{j, "3"}, "Otros", "Para Java T058 puedes solicitar como máximo 2 colaborador(es)."},
                {"5", new String[]{q, "1"}, "Otros", "Para QA T058 puedes solicitar como máximo 0 colaborador(es)."},
                {"1", new String[]{j, "2"}, "", "La suma por habilidad (2) no puede superar la cantidad total solicitada (1)."},
                {"3", new String[]{j, "2"}, "", "Describe en Perfiles requeridos los 1 colaborador(es) que no salen de las habilidades del proyecto."},
                {"6", new String[]{j, "1"}, "Otros", "La cantidad total no puede superar los 5 colaboradores requeridos por el proyecto."},
                {"2", new String[]{a, "1"}, "Otros", "Una de las habilidades indicadas no pertenece al proyecto."},
                {"2", new String[]{j, "-1"}, "Otros", "La cantidad por habilidad no puede ser negativa."},
                {"2", new String[]{j, "abc"}, "Otros", "La cantidad por habilidad debe ser un número entero mayor o igual que cero."},
                {"3", new String[]{j, "2"}, "p".repeat(990), "El detalle de perfiles supera los 1000 caracteres. "
                        + "Acorta la descripción de los otros perfiles."},
        };
        for (Object[] caso : casos) {
            String[] desglose = (String[]) caso[1];
            MvcResult resultado = mockMvc.perform(post(ruta(proyecto))
                            .param("cantidad", (String) caso[0])
                            .param(desglose[0], desglose[1])
                            .param("perfiles", (String) caso[2]))
                    .andExpect(redirectedUrl(detalle(proyecto) + "#requestStaffModal"))
                    .andExpect(flash().attribute("error", caso[3]))
                    .andExpect(flash().attribute("abrirSolicitudPersonal", true))
                    .andReturn();
            assertNotNull(resultado.getFlashMap().get("pmSolicitudPersonalForm"), "Conserva lo ingresado: " + caso[3]);
        }
        assertEquals(0, solicitudes(proyecto).size());
        assertTrue(notificacionesPendientes(rm, null).isEmpty());
    }

    @Test
    void errorDelDesgloseSeMuestraDentroDelModalConLosValores() throws Exception {
        Habilidad java = requisito(proyecto, "Java T058", NivelDominio.AVANZADO, 3);

        MvcResult resultado = mockMvc.perform(post(ruta(proyecto))
                        .param("cantidad", "1")
                        .param("cantidadesPorHabilidad[" + java.getId() + "]", "2"))
                .andReturn();

        String html = html(mockMvc.perform(get(detalle(proyecto)).flashAttrs(resultado.getFlashMap()))
                .andExpect(status().isOk()).andReturn());
        String mensaje = "La suma por habilidad (2) no puede superar la cantidad total solicitada (1).";
        assertTrue(html.contains("<div class=\"alert alert-danger\" role=\"alert\">" + mensaje + "</div>"),
                "El error también aparece dentro del modal.");
        assertTrue(html.replaceAll("\\s+", " ").contains("name=\"cantidadesPorHabilidad[" + java.getId() + "]\" value=\"2\""));
        assertTrue(html.contains("value=\"1\""));
    }

    @Test
    void solicitudAbiertaNoReabreElModal() throws Exception {
        crearSolicitud(EstadoSolicitudPersonal.PENDIENTE);

        MvcResult resultado = mockMvc.perform(post(ruta(proyecto)).param("cantidad", "1").param("perfiles", "QA"))
                .andExpect(redirectedUrl(detalle(proyecto)))
                .andReturn();
        assertNull(resultado.getFlashMap().get("abrirSolicitudPersonal"));
    }

    @ParameterizedTest
    @EnumSource(value = EstadoProyecto.class, names = {"EN_REVISION", "RECHAZADO", "CANCELADO", "FINALIZADO"})
    void proyectoQueNoEstaActivoNiEnEsperaSeRechaza(EstadoProyecto estado) throws Exception {
        Proyecto cerrado = proyecto(estado);

        mockMvc.perform(post(ruta(cerrado))
                        .param("cantidad", "1")
                        .param("perfiles", "Frontend"))
                .andExpect(redirectedUrl(detalle(cerrado)))
                .andExpect(flash().attribute("error", "El proyecto debe estar activo o en espera para solicitar personal."));
        assertEquals(0, solicitudes(cerrado).size());

        String html = html(mockMvc.perform(get(detalle(cerrado))).andExpect(status().isOk()).andReturn());
        assertFalse(html.contains("Solicitar personal"), "Sin botón ni modal para " + estado);
        assertFalse(html.contains("requestStaffModal"));
    }

    @Test
    void impideOtraSolicitudMientrasHayaUnaPendienteOEnAtencion() throws Exception {
        crearSolicitud(EstadoSolicitudPersonal.PENDIENTE);
        verificarDuplicadaRechazada();

        solicitudRepository.findAll().stream()
                .filter(s -> s.getProyecto().getId().equals(proyecto.getId()))
                .forEach(s -> { s.setEstado(EstadoSolicitudPersonal.EN_ATENCION); solicitudRepository.save(s); });
        verificarDuplicadaRechazada();

        // Una solicitud ya atendida no bloquea una nueva.
        solicitudRepository.findAll().stream()
                .filter(s -> s.getProyecto().getId().equals(proyecto.getId()))
                .forEach(s -> { s.setEstado(EstadoSolicitudPersonal.ATENDIDA); solicitudRepository.save(s); });
        mockMvc.perform(post(ruta(proyecto)).param("cantidad", "1").param("perfiles", "QA"))
                .andExpect(flash().attribute("success", EXITO));
        assertEquals(2, solicitudes(proyecto).size());
    }

    @Test
    void exitoSeMuestraTrasLaRedireccionYLaSolicitudLlegaAlRm() throws Exception {
        MvcResult resultado = mockMvc.perform(post(ruta(proyecto))
                        .param("cantidad", "2")
                        .param("perfiles", "Backend"))
                .andReturn();

        String html = html(mockMvc.perform(get(detalle(proyecto)).flashAttrs(resultado.getFlashMap()))
                .andExpect(status().isOk()).andReturn());
        assertTrue(html.contains("class=\"alert alert-success alert-dismissible\""));
        assertEquals(1, html.split(EXITO, -1).length - 1);
        assertFalse(html.contains("alert alert-danger"));

        // Visibilidad posterior en la bandeja del RM.
        Long solicitudId = unicaSolicitud(proyecto).getId();
        autenticar(rm, "RESOURCE_MANAGER");
        MvcResult bandeja = mockMvc.perform(get("/rm/asignaciones/solicitudes-colaboradores")
                        .param("busqueda", proyecto.getNombre()))
                .andExpect(status().isOk())
                .andExpect(view().name("rm/rm-solicitudes-colaboradores"))
                .andReturn();
        assertEquals(List.of(solicitudId), idsDe(bandeja));
        assertTrue(html(bandeja).contains(proyecto.getNombre()));

        mockMvc.perform(get("/rm/asignaciones/solicitudes-colaboradores/detalle").param("id", solicitudId.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void formularioFuncionaSinJavaScript() throws Exception {
        String html = html(mockMvc.perform(get(detalle(proyecto))).andExpect(status().isOk()).andReturn());

        // El botón es un enlace al fragmento: sin JS, el modal se abre con CSS :target.
        assertTrue(html.contains("href=\"#requestStaffModal\""));
        assertTrue(html.contains("id=\"requestStaffModal\""));
        assertTrue(html.contains("<form method=\"POST\" action=\"/pm/proyectos/" + proyecto.getId() + "/solicitudes-personal\""));
        assertTrue(html.contains("name=\"cantidad\""));
        assertTrue(html.contains("name=\"perfiles\""));
        assertTrue(html.contains("name=\"mensaje\""));
        assertTrue(html.contains("<button class=\"btn btn-primary\" type=\"submit\">Enviar solicitud</button>"));
        // Cerrar también es un enlace (vuelve a "#"), y el formulario no envía el PM.
        assertTrue(html.contains("<a href=\"#\" class=\"btn btn-outline-secondary\" data-bs-dismiss=\"modal\">Cerrar</a>"));
        assertFalse(html.contains("name=\"pmId\""));

        String css = new String(new ClassPathResource("static/css/pm-css/pm-detalle-proyecto.css")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(css.contains(".modal:target:not(.js-modal){display:block"));
        // Solo el cuerpo hace scroll: el botón "Enviar solicitud" del pie queda visible.
        assertTrue(html.contains("modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable"));
        assertTrue(css.contains("#requestStaffModal form{display:flex;flex-direction:column;flex:1 1 auto;min-height:0;overflow:hidden}"));
        assertTrue(css.contains("#requestStaffModal .modal-body{overflow-y:auto}"));
    }

    private void verificarDuplicadaRechazada() throws Exception {
        int antes = solicitudes(proyecto).size();
        mockMvc.perform(post(ruta(proyecto)).param("cantidad", "1").param("perfiles", "QA"))
                .andExpect(redirectedUrl(detalle(proyecto)))
                .andExpect(flash().attribute("error", "El proyecto ya tiene una solicitud de personal abierta."));
        assertEquals(antes, solicitudes(proyecto).size());
    }

    private String ruta(Proyecto destino) {
        return "/pm/proyectos/" + destino.getId() + "/solicitudes-personal";
    }

    private String detalle(Proyecto destino) {
        return "/pm/proyectos/detalle?id=" + destino.getId();
    }

    private List<SolicitudPersonal> solicitudes(Proyecto destino) {
        return solicitudRepository.findAll().stream()
                .filter(s -> s.getProyecto().getId().equals(destino.getId()))
                .toList();
    }

    private SolicitudPersonal unicaSolicitud(Proyecto destino) {
        List<SolicitudPersonal> lista = solicitudes(destino);
        assertEquals(1, lista.size());
        return lista.get(0);
    }

    private void crearSolicitud(EstadoSolicitudPersonal estado) {
        SolicitudPersonal solicitud = new SolicitudPersonal();
        solicitud.setProyecto(proyecto);
        solicitud.setCantidadColaboradores(1);
        solicitud.setPerfilesRequeridos("Backend");
        solicitud.setEstado(estado);
        solicitudRepository.save(solicitud);
    }

    private List<LogAuditoria> auditorias(SolicitudPersonal solicitud) {
        return logAuditoriaRepository.findAll().stream()
                .filter(l -> "CREACION_SOLICITUD_PERSONAL".equals(l.getAccion())
                        && "SOLICITUD_PERSONAL".equals(l.getEntidad())
                        && solicitud.getId().equals(l.getEntidadId()))
                .toList();
    }

    // Con solicitud nula, todas las notificaciones de solicitudes de este proyecto.
    private List<Notificacion> notificacionesPendientes(Usuario usuario, SolicitudPersonal solicitud) {
        return notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(usuario).stream()
                .filter(n -> "SOLICITUD_PERSONAL_PENDIENTE".equals(n.getTipo()))
                .filter(n -> solicitud != null
                        ? solicitud.getId().equals(n.getEntidadId())
                        : n.getDescripcion().contains(proyecto.getNombre()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<Long> idsDe(MvcResult resultado) {
        List<RmSolicitudPersonalView> filas =
                (List<RmSolicitudPersonalView>) resultado.getModelAndView().getModel().get("solicitudes");
        return filas.stream().map(item -> item.getSolicitud().getId()).toList();
    }

    private String html(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private void autenticar(Usuario usuario, String rol) {
        // findActivosByRolNombre trae el rol cargado, necesario para las autoridades.
        UsuarioDetails details = new UsuarioDetails(usuarioRepository.findActivosByRolNombre(rol)
                .stream().filter(u -> u.getId().equals(usuario.getId())).findFirst().orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    private Proyecto proyecto(EstadoProyecto estado) {
        Proyecto nuevo = new Proyecto();
        nuevo.setNombre("Proyecto solicitud PM " + System.nanoTime());
        nuevo.setDescripcion("Proyecto para probar la solicitud de personal del PM.");
        nuevo.setEstado(estado);
        nuevo.setPrioridad(Prioridad.MEDIA);
        nuevo.setJustificacionPrioridad("Prueba de solicitud de personal.");
        nuevo.setColaboradoresRequeridos(5);
        nuevo.setPm(pm);
        return proyectoRepository.save(nuevo);
    }

    private Habilidad requisito(Proyecto destino, String nombreHabilidad, NivelDominio nivel, int cantidadPersonas) {
        Habilidad habilidad = habilidad(nombreHabilidad);
        ProyectoHabilidadRequerida requisito = new ProyectoHabilidadRequerida();
        requisito.setProyecto(destino);
        requisito.setHabilidad(habilidad);
        requisito.setNivelRequerido(nivel);
        requisito.setCantidadPersonas(cantidadPersonas);
        habilidadRequeridaRepository.save(requisito);
        return habilidad;
    }

    private Habilidad habilidad(String nombre) {
        CategoriaHabilidad categoria = categoriaRepository.findByNombreIgnoreCase("Solicitudes PM T058")
                .orElseGet(() -> {
                    CategoriaHabilidad nueva = new CategoriaHabilidad();
                    nueva.setNombre("Solicitudes PM T058");
                    return categoriaRepository.save(nueva);
                });
        return habilidadRepository.findByNombreIgnoreCase(nombre).orElseGet(() -> {
            Habilidad nueva = new Habilidad();
            nueva.setNombre(nombre);
            nueva.setCategoria(categoria);
            return habilidadRepository.save(nueva);
        });
    }

    // Colaborador nuevo con asignación ACTIVA en el proyecto para esa habilidad (ocupa un cupo).
    private void asignacionActiva(Habilidad habilidad) {
        Usuario colaborador = obtenerUsuario(System.nanoTime() + ".col.t058@skillbridge.test", "Carla", "Colaboradora",
                obtenerRol("COLABORADOR"));
        Asignacion asignacion = new Asignacion();
        asignacion.setProyecto(proyecto);
        asignacion.setColaborador(colaborador);
        asignacion.setHabilidadSolicitada(habilidad);
        asignacion.setHorasSemanales(new BigDecimal("10"));
        asignacion.setOrigen(OrigenAsignacion.PROPUESTA_RM);
        asignacion.setEstado(EstadoAsignacion.ACTIVA);
        asignacion.setAprobadoPorRm(true);
        asignacion.setAprobadoPorPm(true);
        asignacion.setFechaActivacion(LocalDateTime.now());
        asignacionRepository.save(asignacion);
    }

    private Rol obtenerRol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol rol = new Rol();
            rol.setNombre(nombre);
            return rolRepository.save(rol);
        });
    }

    private Usuario obtenerUsuario(String correo, String nombre, String apellido, Rol rol) {
        return usuarioRepository.findByCorreo(correo).orElseGet(() -> {
            Usuario usuario = new Usuario();
            usuario.setCorreo(correo);
            usuario.setNombre(nombre);
            usuario.setApellido(apellido);
            usuario.setRol(rol);
            return usuarioRepository.save(usuario);
        });
    }
}
