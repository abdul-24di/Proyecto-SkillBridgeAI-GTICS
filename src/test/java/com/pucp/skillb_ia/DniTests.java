package com.pucp.skillb_ia;

import com.pucp.skillb_ia.model.Rol;
import com.pucp.skillb_ia.model.Usuario;
import com.pucp.skillb_ia.repository.RolRepository;
import com.pucp.skillb_ia.repository.UsuarioRepository;
import com.pucp.skillb_ia.security.UsuarioDetails;
import com.pucp.skillb_ia.repository.NotificacionRepository;
import com.pucp.skillb_ia.service.DniService;
import com.pucp.skillb_ia.service.DniVerificacionService;
import com.pucp.skillb_ia.service.NotificacionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.http.HttpMethod;
import static org.mockito.ArgumentMatchers.startsWith;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// DNI: formato, que no se repita, consulta a la API (simulada con Mockito, sin gastar consultas reales),
// registro una sola vez y vista en el perfil.
@SpringBootTest
@ActiveProfiles("test")
class DniTests {

    private static final String URL = "https://dniruc.apisperu.com/api/v1/dni";

    @Autowired private WebApplicationContext context;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private DniService dniServiceSinClave;
    @Autowired private NotificacionService notificacionService;
    @Autowired private NotificacionRepository notificacionRepository;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    // ---------- Validación ----------

    @Test
    void rechazaFormatosInvalidosYDniRepetido() {
        for (String malo : new String[]{null, "", "1234567", "123456789", "1234567a", "12 34567"}) {
            assertThatThrownBy(() -> dniServiceSinClave.validar(malo, "Ana Perez"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(DniService.MENSAJE_FORMATO);
        }
        Usuario existente = usuario("con.dni", "COLABORADOR");
        String dni = DniPrueba.unico();
        dniServiceSinClave.registrar(existente, dni, false);

        assertThatThrownBy(() -> dniServiceSinClave.validar(dni, "Ana Perez"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ese DNI ya está registrado en otra cuenta.");
    }

    @Test
    void sinApiKeyElDniSeAceptaComoSinVerificar() {
        assertThat(dniServiceSinClave.validar(DniPrueba.unico(), "Ana Perez")).isFalse();
    }

    @Test
    void conLaApiConfirmaElDniSiElNombreCoincide() {
        RestTemplate api = mock(RestTemplate.class);
        when(api.exchange(startsWith(URL + "/"), eq(HttpMethod.GET), any(), eq(Map.class))).thenReturn(ResponseEntity.ok(
                Map.of("success", true, "nombres", "LUIS ABDUL", "apellidoPaterno", "PAREDES", "apellidoMaterno", "DÍAZ")));

        DniService conApi = new DniService(usuarioRepository, api, "clave-de-prueba", URL);

        assertThat(conApi.validar(DniPrueba.unico(), "Luis Paredes")).isTrue();
        assertThat(conApi.validar(DniPrueba.unico(), "luis abdul paredes diaz")).isTrue();
        assertThatThrownBy(() -> conApi.validar(DniPrueba.unico(), "Luis Gomez"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no coincide");
        assertThatThrownBy(() -> conApi.validar(DniPrueba.unico(), "Luis"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no coincide");
    }

    @Test
    void siLaApiNoEncuentraElDniSeRechaza() {
        RestTemplate api = mock(RestTemplate.class);
        when(api.exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));
        DniService conApi = new DniService(usuarioRepository, api, "clave-de-prueba", URL);

        assertThatThrownBy(() -> conApi.validar(DniPrueba.unico(), "Ana Perez"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No encontramos ese DNI. Revisa que esté bien escrito.");
    }

    @Test
    void siLaApiFallaNoSeBloqueaElRegistro() {
        RestTemplate sinSaldo = mock(RestTemplate.class);
        when(sinSaldo.exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));
        RestTemplate caida = mock(RestTemplate.class);
        when(caida.exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("timeout"));

        assertThat(new DniService(usuarioRepository, sinSaldo, "clave", URL).validar(DniPrueba.unico(), "Ana Perez")).isFalse();
        assertThat(new DniService(usuarioRepository, caida, "clave", URL).validar(DniPrueba.unico(), "Ana Perez")).isFalse();
    }

    @Test
    void conSuccessFalseSoloSeRechazaSiElDniNoExiste() {
        RestTemplate noEncontrado = mock(RestTemplate.class);
        when(noEncontrado.exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("success", false, "message", "No se encontraron resultados.")));
        assertThatThrownBy(() -> new DniService(usuarioRepository, noEncontrado, "clave", URL)
                .validar(DniPrueba.unico(), "Ana Perez"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No encontramos ese DNI. Revisa que esté bien escrito.");

        for (Map<String, Object> cuerpo : java.util.List.<Map<String, Object>>of(
                Map.of("success", false, "message", "Token inválido"),
                Map.of("success", false, "message", "Servicio en mantenimiento"),
                Map.of("success", false))) {
            RestTemplate api = mock(RestTemplate.class);
            when(api.exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class)))
                    .thenReturn(ResponseEntity.ok(cuerpo));
            assertThat(new DniService(usuarioRepository, api, "clave", URL)
                    .validar(DniPrueba.unico(), "Ana Perez")).as(cuerpo.toString()).isFalse();
        }
    }

    // ---------- Registro desde el perfil ----------

    @Test
    void unDniSinVerificarSePuedeCorregirDesdeElPerfilYAvisaElMotivo() throws Exception {
        Usuario rm = usuario("rm", "RESOURCE_MANAGER");
        autenticar(rm);
        String dni = DniPrueba.unico();

        mvc.perform(get("/rm/perfil")).andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains("Registrar DNI"));

        mvc.perform(post("/perfil/dni").param("dni", dni).header("Referer", "http://localhost/rm/perfil"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("dniGuardado"));

        Usuario guardado = usuarioRepository.findById(rm.getId()).orElseThrow();
        assertThat(guardado.getDni()).isEqualTo(dni);
        assertThat(guardado.isDniVerificado()).isFalse();
        assertThat(tiposDeNotificacion(rm)).containsExactly(DniVerificacionService.TIPO_SIN_VERIFICAR);

        String html = mvc.perform(get("/rm/perfil")).andReturn().getResponse().getContentAsString();
        assertThat(html).contains(dni).contains("Sin verificar").contains("Verificar ahora")
                .doesNotContain("Registrar DNI");

        // Sin verificar se puede corregir (sin contar su propio DNI como repetido).
        String corregido = DniPrueba.unico();
        mvc.perform(post("/perfil/dni").param("dni", corregido))
                .andExpect(flash().attributeExists("dniGuardado"));
        assertThat(usuarioRepository.findById(rm.getId()).orElseThrow().getDni()).isEqualTo(corregido);
        mvc.perform(post("/perfil/dni").param("dni", corregido))
                .andExpect(flash().attributeExists("dniGuardado"));
        // Repetir "Verificar ahora" con el mismo DNI no vuelve a notificar.
        assertThat(tiposDeNotificacion(rm)).hasSize(2);
    }

    @Test
    void unDniVerificadoNoSePuedeCambiar() throws Exception {
        Usuario pm = usuario("pm.verificado", "PROJECT_MANAGER");
        String dni = DniPrueba.unico();
        pm.setDni(dni);
        pm.setDniVerificado(true);
        usuarioRepository.save(pm);
        autenticar(pm);

        mvc.perform(post("/perfil/dni").param("dni", DniPrueba.unico()))
                .andExpect(flash().attribute("dniError", DniService.MENSAJE_YA_VERIFICADO));
        assertThat(usuarioRepository.findById(pm.getId()).orElseThrow().getDni()).isEqualTo(dni);

        String html = mvc.perform(get("/pm/perfil")).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Verificado").contains("no se puede modificar").doesNotContain("Verificar ahora");
    }

    @Test
    void elReintentoVerificaOQuitaLosDniSinVerificarYNotificaElMotivo() {
        Usuario coincide = usuarioConDniSinVerificar("coincide");
        Usuario noExiste = usuarioConDniSinVerificar("noexiste");
        Usuario otroNombre = usuarioConDniSinVerificar("otronombre");

        RestTemplate api = mock(RestTemplate.class);
        when(api.exchange(eq(URL + "/" + coincide.getDni()), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("success", true, "nombres", "ANA MARIA",
                        "apellidoPaterno", "PEREZ", "apellidoMaterno", "SOTO")));
        when(api.exchange(eq(URL + "/" + noExiste.getDni()), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("success", false, "message", "No se encontraron resultados.")));
        when(api.exchange(eq(URL + "/" + otroNombre.getDni()), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("success", true, "nombres", "LUIS",
                        "apellidoPaterno", "GOMEZ", "apellidoMaterno", "RUIZ")));
        DniVerificacionService reintento = new DniVerificacionService(
                new DniService(usuarioRepository, api, "clave", URL), usuarioRepository, notificacionService);

        assertThat(reintento.reintentar(java.util.List.of(coincide, noExiste, otroNombre))).isEqualTo(1);

        Usuario verificado = usuarioRepository.findById(coincide.getId()).orElseThrow();
        assertThat(verificado.isDniVerificado()).isTrue();
        assertThat(tiposDeNotificacion(coincide)).containsExactly(DniVerificacionService.TIPO_VERIFICADO);

        for (Usuario rechazado : java.util.List.of(noExiste, otroNombre)) {
            Usuario guardado = usuarioRepository.findById(rechazado.getId()).orElseThrow();
            assertThat(guardado.getDni()).isNull();
            assertThat(guardado.isDniVerificado()).isFalse();
            assertThat(tiposDeNotificacion(rechazado)).containsExactly(DniVerificacionService.TIPO_NO_VERIFICADO);
        }
        assertThat(notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(noExiste).get(0).getDescripcion())
                .contains("no encontró el DNI");
        assertThat(notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(otroNombre).get(0).getDescripcion())
                .contains("no corresponde al nombre de tu cuenta");
    }

    @Test
    void siLaApiSigueCaidaElReintentoNoCambiaNadaYSeDetiene() {
        Usuario primero = usuarioConDniSinVerificar("caida1");
        Usuario segundo = usuarioConDniSinVerificar("caida2");
        RestTemplate caida = mock(RestTemplate.class);
        when(caida.exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("timeout"));
        DniVerificacionService reintento = new DniVerificacionService(
                new DniService(usuarioRepository, caida, "clave", URL), usuarioRepository, notificacionService);

        assertThat(reintento.reintentar(java.util.List.of(primero, segundo))).isZero();

        for (Usuario u : java.util.List.of(primero, segundo)) {
            Usuario guardado = usuarioRepository.findById(u.getId()).orElseThrow();
            assertThat(guardado.getDni()).isEqualTo(u.getDni());
            assertThat(guardado.isDniVerificado()).isFalse();
            assertThat(tiposDeNotificacion(u)).isEmpty();
        }
        org.mockito.Mockito.verify(caida, org.mockito.Mockito.times(1))
                .exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class));
    }

    @Test
    void lasNotificacionesDelDniLlevanAlPerfilDeCadaRol() {
        Usuario col = usuario("col.notif", "COLABORADOR");
        notificacionService.crear(col, DniVerificacionService.TIPO_NO_VERIFICADO,
                com.pucp.skillb_ia.model.enums.CategoriaNotificacion.SISTEMA, "t", "d", "USUARIO", col.getId());
        assertThat(notificacionService.listar(col.getId()).get(0).url()).isEqualTo("/colaborador/perfil#dni");
    }

    @Test
    void unDniInvalidoDesdeElPerfilMuestraElErrorYNoSeGuarda() throws Exception {
        Usuario pm = usuario("pm", "PROJECT_MANAGER");
        autenticar(pm);

        mvc.perform(post("/perfil/dni").param("dni", "12AB"))
                .andExpect(flash().attribute("dniError", DniService.MENSAJE_FORMATO))
                .andExpect(flash().attribute("dniIngresado", "12AB"));
        assertThat(usuarioRepository.findById(pm.getId()).orElseThrow().getDni()).isNull();
    }

    // ---------- Utilidades ----------

    private Usuario usuario(String prefijo, String nombreRol) {
        Usuario u = new Usuario();
        u.setCorreo(prefijo + "." + UUID.randomUUID() + "@test.local");
        u.setNombre("Ana");
        u.setApellido("Perez");
        u.setRol(rol(nombreRol));
        u.setActivo(true);
        u.setHorasContratadasSemana(new BigDecimal("40.00"));
        u.setHorasDisponibles(new BigDecimal("40.00"));
        return usuarioRepository.save(u);
    }

    private Usuario usuarioConDniSinVerificar(String prefijo) {
        Usuario u = usuario(prefijo, "COLABORADOR");
        u.setDni(DniPrueba.unico());
        u.setDniVerificado(false);
        return usuarioRepository.save(u);
    }

    private java.util.List<String> tiposDeNotificacion(Usuario usuario) {
        return notificacionRepository.findByUsuarioOrderByFechaCreacionDesc(usuario).stream()
                .map(n -> n.getTipo()).toList();
    }

    private Rol rol(String nombre) {
        return rolRepository.findByNombre(nombre).orElseGet(() -> {
            Rol r = new Rol();
            r.setNombre(nombre);
            return rolRepository.save(r);
        });
    }

    private void autenticar(Usuario usuario) {
        UsuarioDetails details = new UsuarioDetails(usuario);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }
}
